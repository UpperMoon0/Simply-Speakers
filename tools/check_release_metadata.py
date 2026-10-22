"""Check required publication relations and the workflow OpenUI dependency contract."""
from pathlib import Path
import argparse
import json
import re
import release_artifacts

ROOT = Path(__file__).resolve().parents[1]
TARGETS = {
    "fabric-1.20.1": "src/main/resources/fabric.mod.json",
    "forge-1.20.1": "src/main/resources/META-INF/mods.toml",
    "fabric-1.21.1": "src/main/resources/fabric.mod.json",
    "neoforge-1.21.1": "src/main/resources/META-INF/neoforge.mods.toml",
    "neoforge-26.1.2": "src/main/templates/META-INF/neoforge.mods.toml",
}
BUILTINS = {"minecraft", "java", "fabricloader", "forge", "neoforge"}
SLUGS = {"architectury": "architectury-api", "fabric-api": "fabric-api", "openui_mc": "openui-mc", "patchouli": "patchouli", "packetweave": "packetweave"}

def property_value(path, name):
    match = re.search(r"^" + re.escape(name) + r"\s*=\s*(\S+)", path.read_text(encoding="utf-8"), re.M)
    if not match: raise ValueError(f"Missing {name} in {path}")
    return match[1]

def required_mods(path):
    if path.suffix == ".json": return set(json.loads(path.read_text())["depends"]) - BUILTINS
    required = set()
    for block in re.split(r"\[\[dependencies\.[^\]]+\]\]", path.read_text())[1:]:
        if not re.search(r'(?:mandatory\s*=\s*true|type\s*=\s*"required")', block): continue
        match = re.search(r'modId\s*=\s*"([^"\n]+)"', block)
        if match and match[1] not in BUILTINS: required.add(match[1])
    return required

def validate(root=ROOT):
    release = (root / ".github/workflows/release.yml").read_text()
    if 'tools/release_artifacts.py relations "$PLATFORM-$MINECRAFT_VERSION"' not in release:
        raise ValueError("Release must use target-aware publication relations")
    for target, path in TARGETS.items():
        relations = release_artifacts.relations(target, root)["projects"]
        published = {item["slug"] for item in relations if item["type"] == "requiredDependency"}
        metadata = root / target / path
        required = required_mods(metadata)
        if "patchouli" in required:
            raise ValueError(f"patchouli must be optional on {target}")
        if metadata.suffix == ".json":
            optional = "patchouli" in json.loads(metadata.read_text()).get("suggests", {})
        else:
            optional = bool(re.search(r'modId\s*=\s*"patchouli"\s*\n(?:mandatory\s*=\s*false|type\s*=\s*"optional")', metadata.read_text()))
        if not optional:
            raise ValueError(f"Missing optional patchouli declaration on {target}")
        if [item["type"] for item in relations if item["slug"] == "patchouli"] != ["optionalDependency"]:
            raise ValueError(f"patchouli publication relation must be optional on {target}")
        unknown = required - SLUGS.keys()
        if unknown: raise ValueError(f"Unmapped required mods on {target}: {sorted(unknown)}")
        missing = {SLUGS[mod] for mod in required} - published
        if missing: raise ValueError(f"Missing required publication dependencies on {target}: {sorted(missing)}")
    pins, caches = [], []
    for name in ("release.yml", "live-join-test.yml"):
        text = (root / ".github/workflows" / name).read_text()
        pins += re.findall(r"ref: ([0-9a-f]{40})\n\s+path: \.openui-mc", text)
        caches += re.findall(r"key: openui-maven-([0-9a-f]{40})", text)
    if len(pins) != 2 or len(set(pins)) != 1 or not caches or set(caches) != set(pins):
        raise ValueError("OpenUI checkout pins and all workflow cache keys must match")

def validate_openui(checkout, root=ROOT):
    required = property_value(root / "gradle.properties", "openui_version")
    actual = property_value(checkout / "gradle.properties", "mod_version")
    if actual != required: raise ValueError(f"OpenUI checkout version {actual} does not match required {required}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--openui-checkout", type=Path)
    args = parser.parse_args()
    validate()
    if args.openui_checkout: validate_openui(args.openui_checkout)
    print("Release dependencies and OpenUI contract validated")
