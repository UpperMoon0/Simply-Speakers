#!/usr/bin/env python3
"""Launch a real dedicated server and auto-joining client for the join-race smoke test."""

from __future__ import annotations

import argparse
import os
import queue
import shutil
import signal
import subprocess
import sys
import threading
import time
from pathlib import Path


PASS_MARKER = "SIMPLYSPEAKERS_LIVE_JOIN_TEST_PASS"
SERVER_READY_MARKERS = ("Done (", "For help, type \"help\"")
DEFAULT_TIMEOUT = 360
TARGETS = {
    "fabric-1.20.1": "fabric-1.20.1",
    "forge-1.20.1": "forge-1.20.1",
    "fabric-1.21.1": "fabric-1.21.1",
    "neoforge-1.21.1": "neoforge-1.21.1",
    "neoforge-26.1.2": "neoforge-26.1.2",
}


class OutputPump:
    def __init__(self, process: subprocess.Popen[str], prefix: str) -> None:
        self.process = process
        self.prefix = prefix
        self.lines: queue.Queue[str] = queue.Queue()
        self.thread = threading.Thread(target=self._read, daemon=True)
        self.thread.start()

    def _read(self) -> None:
        assert self.process.stdout is not None
        for line in self.process.stdout:
            print(f"[{self.prefix}] {line}", end="", flush=True)
            self.lines.put(line)

    def wait_for(self, markers: tuple[str, ...], timeout: int) -> str | None:
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if self.process.poll() is not None and self.lines.empty():
                return None
            try:
                line = self.lines.get(timeout=min(1.0, deadline - time.monotonic()))
            except queue.Empty:
                continue
            if any(marker in line for marker in markers):
                return line
        return None


def command(root: Path, task: str) -> list[str]:
    wrapper = root / ("gradlew.bat" if os.name == "nt" else "gradlew")
    # The server and client builds overlap. Keep their supervising Gradle JVMs
    # small so they do not starve Minecraft's login/registry threads.
    return [
        str(wrapper),
        task,
        "--no-daemon",
        "--console=plain",
        "--max-workers=4",
        "-Dorg.gradle.jvmargs=-Xmx768m",
    ]


def popen(cmd: list[str], root: Path) -> subprocess.Popen[str]:
    kwargs: dict[str, object] = {
        "cwd": root,
        "stdin": subprocess.PIPE,
        "stdout": subprocess.PIPE,
        "stderr": subprocess.STDOUT,
        "text": True,
        "bufsize": 1,
    }
    if os.name == "nt":
        kwargs["creationflags"] = subprocess.CREATE_NEW_PROCESS_GROUP
    else:
        kwargs["start_new_session"] = True
    return subprocess.Popen(cmd, **kwargs)  # type: ignore[arg-type]


def stop_tree(process: subprocess.Popen[str], graceful_server: bool = False) -> None:
    if process.poll() is not None:
        return
    if graceful_server and process.stdin is not None:
        try:
            process.stdin.write("stop\n")
            process.stdin.flush()
            process.wait(timeout=15)
            return
        except (BrokenPipeError, subprocess.TimeoutExpired):
            pass
    if os.name == "nt":
        subprocess.run(
            ["taskkill", "/PID", str(process.pid), "/T", "/F"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            check=False,
        )
    else:
        try:
            os.killpg(process.pid, signal.SIGTERM)
            process.wait(timeout=10)
        except (ProcessLookupError, subprocess.TimeoutExpired):
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass


def prepare_server(module_dir: Path) -> None:
    server_dir = module_dir / "run" / "live-join" / "server"
    server_dir.mkdir(parents=True, exist_ok=True)
    (server_dir / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (server_dir / "server.properties").write_text(
        "online-mode=false\n"
        "server-port=25575\n"
        "level-name=live-join-world\n"
        "motd=Simply Speakers live join test\n"
        "spawn-protection=0\n",
        encoding="utf-8",
    )


def prepare_client(module_dir: Path) -> None:
    client_dir = module_dir / "run" / "live-join" / "client"
    client_dir.mkdir(parents=True, exist_ok=True)
    # A fresh Minecraft directory otherwise opens the accessibility/narrator
    # onboarding screen, which blocks quick-play and makes the test interactive.
    (client_dir / "options.txt").write_text(
        "narrator:0\n"
        "narratorHotkey:false\n"
        "onboardAccessibility:false\n"
        "skipMultiplayerWarning:true\n",
        encoding="utf-8",
    )


def run_target(root: Path, target: str, timeout: int) -> None:
    module = TARGETS[target]
    print(f"Preparing {target} live join test", flush=True)
    prepare_server(root / module)
    prepare_client(root / module)

    compile_cmd = command(root, f":{module}:classes")
    subprocess.run(compile_cmd, cwd=root, check=True)

    server = popen(command(root, f":{module}:runLiveJoinTestServer"), root)
    server_output = OutputPump(server, f"{target}/server")
    client: subprocess.Popen[str] | None = None
    try:
        if server_output.wait_for(SERVER_READY_MARKERS, timeout) is None:
            raise RuntimeError(f"{target}: server did not become ready")

        client_cmd = command(root, f":{module}:runLiveJoinTestClient")
        if os.name != "nt" and not os.environ.get("DISPLAY"):
            xvfb = shutil.which("xvfb-run")
            if xvfb is None:
                raise RuntimeError("DISPLAY is unset and xvfb-run is not installed")
            client_cmd = [xvfb, "-a", *client_cmd]

        client = popen(client_cmd, root)
        client_output = OutputPump(client, f"{target}/client")
        if client_output.wait_for((PASS_MARKER,), timeout) is None:
            raise RuntimeError(f"{target}: client did not report a successful live join")
        try:
            exit_code = client.wait(timeout=60)
        except subprocess.TimeoutExpired as exc:
            raise RuntimeError(f"{target}: client passed but did not exit") from exc
        if exit_code != 0:
            raise RuntimeError(f"{target}: client exited with code {exit_code} after passing")
        print(f"{target}: PASS", flush=True)
    finally:
        if client is not None:
            stop_tree(client)
        stop_tree(server, graceful_server=True)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--target", choices=TARGETS, action="append")
    parser.add_argument("--timeout", type=int, default=DEFAULT_TIMEOUT)
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[1]
    targets = args.target or list(TARGETS)
    for target in targets:
        run_target(root, target, args.timeout)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (RuntimeError, subprocess.CalledProcessError) as error:
        print(f"LIVE JOIN TEST FAILED: {error}", file=sys.stderr)
        raise SystemExit(1)
