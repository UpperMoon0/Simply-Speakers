"""Plan a version-changing push without ever reusing an existing release tag."""
import os
import json
from pathlib import Path
import re
import subprocess


def version(text):
    match = re.search(r'^mod_version\s*=\s*([0-9A-Za-z][0-9A-Za-z._+-]*)\s*$', text, re.MULTILINE)
    if not match:
        raise ValueError('Missing or invalid mod_version')
    return match.group(1)


def plan(current, previous, tags, *, before=None, recovery=None):
    value = version(current)
    if previous is not None and version(previous) == value:
        # Explicit, source-bound recovery of a verified pre-publication failure.
        # An absent tag alone cannot prove CurseForge has not published assets.
        recover = (recovery is not None and recovery.get('version') == value
                   and recovery.get('before_sha') == before and before is not None
                   and isinstance(recovery.get('failed_run_id'), int)
                   and recovery['failed_run_id'] > 0)
        return value, recover and 'v' + value not in tags
    if 'v' + value in tags:
        raise ValueError('Version tag already exists; refusing to replace released artifacts')
    return value, True


def main():
    before = os.environ.get('BEFORE_SHA', '')
    previous = None
    if before and set(before) != {'0'}:
        previous = subprocess.check_output(['git', 'show', before + ':gradle.properties'], text=True)
    tags = subprocess.check_output(['git', 'tag', '--list'], text=True).splitlines()
    recovery_path = Path('.github/release-recovery.json')
    recovery = json.loads(recovery_path.read_text()) if recovery_path.exists() else None
    value, publish = plan(Path('gradle.properties').read_text(), previous, tags,
                          before=before, recovery=recovery)
    output = f'version={value}\npublish={str(publish).lower()}\n'
    print(output, end='')
    if os.environ.get('GITHUB_OUTPUT'):
        with open(os.environ['GITHUB_OUTPUT'], 'a') as handle:
            handle.write(output)


if __name__ == '__main__':
    main()
