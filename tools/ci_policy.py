"""Only known prose changes can skip expensive game boots; unknown paths require them."""
import argparse
import os
from pathlib import Path
import subprocess
from verify import TARGETS
import json


def docs_only(paths):
    return bool(paths) and all(path in {"README.md", "TESTING.md", "CHANGELOG.md"}
            or (path.startswith(("docs/", "changelogs/")) and path.endswith(".md")) for path in paths)


def requires_live(event, paths):
    return event != "pull_request" or not docs_only(paths)


def main():
    parser = argparse.ArgumentParser(); parser.add_argument("--event", required=True)
    parser.add_argument("--base", default=""); parser.add_argument("--head", default="HEAD")
    args = parser.parse_args()
    paths = []
    if args.event == "pull_request":
        if not args.base: raise RuntimeError("PR verification requires an explicit base commit")
        paths = subprocess.check_output(["git", "diff", "--name-only", args.base, args.head], text=True).splitlines()
    live = requires_live(args.event, paths)
    result = {"live_required": str(live).lower(), "matrix": json.dumps({"include": [{"target": t} for t in TARGETS]})}
    if os.environ.get("GITHUB_OUTPUT"):
        with Path(os.environ["GITHUB_OUTPUT"]).open("a", encoding="utf-8") as output:
            for key, value in result.items(): output.write(f"{key}={value}\n")
    print(json.dumps(result))


if __name__ == "__main__": main()
