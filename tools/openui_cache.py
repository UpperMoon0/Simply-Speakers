#!/usr/bin/env python3
"""Integrity-checked OpenUI CI cache; deliberately separate from release caches."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import subprocess
import tomllib
import xml.etree.ElementTree as ET
import zipfile

from check_release_metadata import property_value, validate_openui

ROOT = Path(__file__).resolve().parents[1]
SOURCE_PIN = "cb4610a1598d476123b731f5eef4d8e569057ffa"
TARGETS = ("fabric-1.20.1", "forge-1.20.1", "fabric-1.21.1", "neoforge-1.21.1", "neoforge-26.1.2")
MANIFEST = "openui-ci-provenance.json"
INPUTS = ("tools/openui_cache.py", "tools/check_release_metadata.py",
          ".github/actions/restore-openui/action.yml", ".github/workflows/live-join-test.yml")


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def runtime_toolchain():
    """Read the JDK actually selected by setup-java, not a declared cache label."""
    java_home = os.environ.get("JAVA_HOME")
    if not java_home:
        raise ValueError("JAVA_HOME must identify the selected Temurin 21 JDK")
    release = (Path(java_home) / "release").read_text()
    fields = dict(re.findall(r'^([A-Z_]+)="([^"\n]+)"$', release, re.M))
    version, vendor, arch = (fields.get(name, "") for name in ("JAVA_VERSION", "IMPLEMENTOR", "OS_ARCH"))
    if version.split(".")[0] != "21" or vendor != "Eclipse Adoptium" or not arch:
        raise ValueError("OpenUI CI requires the actual Temurin 21 JDK identity")
    return {"distribution": "temurin", "java_major": 21, "java_version": version,
            "java_runtime_version": fields.get("JAVA_RUNTIME_VERSION", version),
            "vendor": vendor, "jvm_arch": arch}


def contract(root=ROOT):
    digest = hashlib.sha256()
    for name in INPUTS:
        digest.update(name.encode() + b"\0" + (root / name).read_bytes())
    return {"schema": 1, "source_commit": SOURCE_PIN,
            "version": property_value(root / "gradle.properties", "openui_version"),
            "toolchain": {"os": os.environ.get("RUNNER_OS", platform.system()),
                          "arch": os.environ.get("RUNNER_ARCH", platform.machine()),
                          **runtime_toolchain()},
            "inputs_sha256": digest.hexdigest(), "targets": list(TARGETS)}


def cache_key(root=ROOT):
    expected = contract(root)
    toolchain = expected["toolchain"]
    runtime_hash = hashlib.sha256(json.dumps(toolchain, sort_keys=True).encode()).hexdigest()[:16]
    return (f"openui-ci-v1-{toolchain['os']}-{toolchain['arch']}-temurin21-{runtime_hash}-"
            f"{SOURCE_PIN}-v{expected['version']}-{expected['inputs_sha256']}")


def inventory(directory):
    if not directory.is_dir() or directory.is_symlink():
        raise ValueError("OpenUI Maven directory is missing or symlinked")
    files = {}
    for path in sorted(directory.rglob("*")):
        if path.is_symlink():
            raise ValueError(f"OpenUI cache contains a symlink: {path}")
        if path.is_file() and path != directory / MANIFEST:
            files[path.relative_to(directory).as_posix()] = sha256(path)
    return files


def validate_artifacts(directory, version):
    for target in TARGETS:
        artifact = f"openui-mc-{target}"
        base = directory / artifact / version / f"{artifact}-{version}"
        pom = ET.parse(Path(str(base) + ".pom")).getroot()
        # Maven POMs may or may not use an XML namespace.
        fields = {node.tag.rsplit("}", 1)[-1]: node.text for node in pom}
        for key, value in (("groupId", "com.nstut"), ("artifactId", artifact), ("version", version)):
            if fields.get(key) != value:
                raise ValueError(f"Wrong OpenUI {key} for {target}: {fields.get(key)!r}")
        with zipfile.ZipFile(Path(str(base) + ".jar")) as jar:
            if jar.testzip() is not None or not jar.namelist():
                raise ValueError(f"Corrupt or empty OpenUI JAR for {target}")
            if target.startswith("fabric-"):
                metadata = json.loads(jar.read("fabric.mod.json"))
                actual_id, actual_version = metadata.get("id"), metadata.get("version")
            else:
                name = "META-INF/mods.toml" if target.startswith("forge-") else "META-INF/neoforge.mods.toml"
                metadata = tomllib.loads(jar.read(name).decode("utf-8"))
                mods = [mod for mod in metadata.get("mods", []) if mod.get("modId") == "openui_mc"]
                actual_id, actual_version = (mods[0].get("modId"), mods[0].get("version")) if len(mods) == 1 else (None, None)
            if (actual_id, actual_version) != ("openui_mc", version):
                raise ValueError(f"Wrong OpenUI JAR identity/version for {target}")


def create(directory, checkout, root=ROOT):
    expected = contract(root)
    actual = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=checkout, text=True).strip()
    if actual != SOURCE_PIN:
        raise ValueError("OpenUI checkout does not match the pinned source commit")
    # chmod +x on the wrapper is intentional; source changes are not.
    dirty = subprocess.check_output(["git", "-c", "core.fileMode=false", "status", "--porcelain"],
                                    cwd=checkout, text=True).strip()
    if dirty:
        raise ValueError("OpenUI provenance requires a clean source checkout")
    validate_openui(checkout, root)
    files = inventory(directory)
    validate_artifacts(directory, expected["version"])
    (directory / MANIFEST).write_text(json.dumps({**expected, "files": files}, indent=2) + "\n")
    validate(directory, root)


def validate(directory, root=ROOT):
    manifest_path = directory / MANIFEST
    if manifest_path.is_symlink():
        raise ValueError("OpenUI manifest must not be a symlink")
    manifest = json.loads(manifest_path.read_text())
    for field, value in contract(root).items():
        if manifest.get(field) != value:
            raise ValueError(f"OpenUI cache provenance mismatch: {field}")
    if manifest.get("files") != inventory(directory):
        raise ValueError("OpenUI cache file inventory or SHA-256 mismatch")
    validate_artifacts(directory, manifest["version"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("key", "create", "validate"))
    parser.add_argument("--directory", type=Path, default=Path.home() / ".m2/repository/com/nstut")
    parser.add_argument("--checkout", type=Path)
    args = parser.parse_args()
    if args.mode == "key":
        print(cache_key())
    else:
        if args.mode == "create":
            if args.checkout is None:
                parser.error("create requires --checkout")
            create(args.directory, args.checkout)
        else:
            validate(args.directory)
        print("Verified OpenUI source, version, toolchain, all targets and SHA-256 inventory")


if __name__ == "__main__":
    main()
