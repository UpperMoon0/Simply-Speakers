#!/usr/bin/env python3
"""Layered Simply Speakers verification: cheap checks first, one boot per live target."""
from __future__ import annotations

import argparse
from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import threading
import time

ROOT = Path(__file__).resolve().parents[1]
TARGETS = ("fabric-1.20.1", "forge-1.20.1", "fabric-1.21.1", "neoforge-1.21.1", "neoforge-26.1.2")
ADAPTER_TASKS = (":common-1.20.1:test", ":common-1.21.1:test", ":neoforge-26.1.2:test")
MODES = ("core", "adapters", "build", "live", "cc", "full", "gate")


def checkout_identity(root: Path = ROOT) -> dict:
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
    # Content identity includes untracked source files and local edits. Generated evidence is ignored.
    paths = subprocess.check_output(["git", "ls-files", "-z", "--cached", "--others", "--exclude-standard"], cwd=root)
    digest = hashlib.sha256()
    for name in sorted(set(paths.decode("utf-8").split("\0")) - {""}):
        path = root / name
        digest.update(name.encode("utf-8"))
        digest.update(b"\0")
        digest.update(path.read_bytes() if path.is_file() else b"<missing>")
    dirty = bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=root, text=True).strip())
    return {"commit": commit, "source_sha256": digest.hexdigest(), "dirty": dirty}


@contextmanager
def checkout_lock(root: Path = ROOT, filename: str = "verification.lock"):
    path = root / "build" / filename
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a+b") as lock:
        lock.write(b"0"); lock.flush(); lock.seek(0)
        try:
            if os.name == "nt":
                import msvcrt
                msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError as exc:
            raise RuntimeError("Another verification run owns this checkout") from exc
        try:
            yield
        finally:
            if os.name == "nt":
                lock.seek(0); msvcrt.locking(lock.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                fcntl.flock(lock, fcntl.LOCK_UN)


def gradle_command(tasks: list[str], root: Path = ROOT) -> list[str]:
    return [str(root / ("gradlew.bat" if os.name == "nt" else "gradlew")),
            *tasks, "--console=plain", "--max-workers=4"]


def write_receipt(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    temporary.replace(path)


def validate_receipts(directory: Path, identity: dict, required: list[str], *, release: bool = False) -> None:
    for name in required:
        path = directory / f"{name}.json"
        if not path.is_file():
            raise RuntimeError(f"Missing verification receipt: {name}")
        receipt = json.loads(path.read_text(encoding="utf-8"))
        if receipt.get("layer") != name or receipt.get("status") != "passed":
            raise RuntimeError(f"Failed or mismatched verification receipt: {name}")
        for field in ("commit", "source_sha256"):
            if receipt.get(field) != identity[field]:
                raise RuntimeError(f"Stale verification receipt: {name} ({field})")
        if release and (receipt.get("dirty") or identity.get("dirty")):
            raise RuntimeError("Release verification requires a clean checkout")


def layer_plan(mode: str, targets: list[str]) -> list[tuple[str, list[str]]]:
    plan = []
    if mode in ("core", "full"):
        plan.append(("harness", [sys.executable, "-m", "unittest", "discover", "-s", "tools", "-p", "test_*.py", "-v"]))
        plan.append(("core", gradle_command([":common:test"])))
    if mode in ("adapters", "full"):
        plan.append(("adapters", gradle_command(list(ADAPTER_TASKS))))
    if mode in ("build", "full"):
        plan.append(("build", gradle_command([f":{target}:build" for target in targets])))
    if mode in ("live", "full"):
        plan.extend((f"live-{target}", [sys.executable, "tools/live_join_test.py", "--target", target]) for target in targets)
    if mode in ("cc", "full"):
        plan.extend((f"cc-{target}", [sys.executable, "tools/cc_lua_test.py", "--target", target]) for target in targets if target!="neoforge-26.1.2")
    return plan


def run_layer(name: str, command: list[str], identity: dict, directory: Path, *, timeout: float | None = None) -> None:
    started = time.monotonic()
    receipt = {**identity, "layer": name, "status": "failed", "command": command}
    # Invalidate old success before starting; failures retain logs and a failed receipt.
    write_receipt(directory / f"{name}.json", receipt)
    log_path = directory / f"{name}.log"
    process = None
    timer = None
    expired = threading.Event()
    try:
        with log_path.open("w", encoding="utf-8") as log:
            options = {"creationflags": subprocess.CREATE_NEW_PROCESS_GROUP} if os.name == "nt" else {"start_new_session": True}
            process = subprocess.Popen(command, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                    text=True, encoding="utf-8", errors="replace", env={**os.environ, "PYTHONIOENCODING": "utf-8"}, **options)
            def abort():
                from live_join_test import stop_tree
                expired.set()
                stop_tree(process)
            timer = threading.Timer(timeout if timeout is not None else (1200 if name.startswith(("live-", "build")) else 900), abort)
            timer.daemon = True
            timer.start()
            assert process.stdout is not None
            for line in process.stdout:
                print(line, end="", flush=True); log.write(line)
            if expired.is_set():
                raise RuntimeError(f"Verification layer timed out: {name}; see {log_path}")
            if process.wait() != 0:
                raise RuntimeError(f"Verification layer failed: {name}; see {log_path}")
        if checkout_identity() != identity:
            raise RuntimeError("Sources changed during verification; rerun against the final checkout")
        receipt["status"] = "passed"
    finally:
        if timer is not None:
            timer.cancel()
            timer.join(timeout=30)
        if process is not None:
            if process.poll() is None:
                from live_join_test import stop_tree
                stop_tree(process)
            if process.stdout is not None: process.stdout.close()
        receipt["elapsed_seconds"] = round(time.monotonic() - started, 3)
        write_receipt(directory / f"{name}.json", receipt)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=MODES, nargs="?", default="core")
    parser.add_argument("--target", choices=TARGETS, action="append")
    parser.add_argument("--release", action="store_true", help="Reject modified-checkout receipts")
    parser.add_argument("--core-only", action="store_true", help="Gate cheap layers on documentation-only PRs")
    parser.add_argument("--matrix-json", action="store_true")
    args = parser.parse_args()
    if args.matrix_json:
        print(json.dumps({"include": [{"target": target} for target in TARGETS]})); return 0
    targets = list(dict.fromkeys(args.target or TARGETS))
    directory = ROOT / "build" / "verification"
    with checkout_lock():
        identity = checkout_identity()
        if args.mode == "gate":
            if args.core_only and args.release: raise RuntimeError("Release verification cannot skip runtime layers")
            required = ["harness", "core", "adapters"]
            if not args.core_only:
                required += [f"build-{target}" for target in targets] + [f"live-{target}" for target in targets] + [f"cc-{target}" for target in targets if target!="neoforge-26.1.2"]
            validate_receipts(directory, identity, required, release=args.release)
        else:
            for name, command in layer_plan(args.mode, targets):
                print(f"VERIFY {name}", flush=True)
                run_layer(name, command, identity, directory)
                if name == "build":
                    receipt = json.loads((directory / "build.json").read_text(encoding="utf-8"))
                    for target in targets:
                        write_receipt(directory / f"build-{target}.json", {**receipt, "layer": f"build-{target}"})
                        (directory / f"build-{target}.log").write_bytes((directory / "build.log").read_bytes())
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (RuntimeError, subprocess.CalledProcessError, OSError, ValueError) as error:
        print(f"VERIFICATION FAILED: {error}", file=sys.stderr); raise SystemExit(1)
