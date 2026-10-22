#!/usr/bin/env python3
"""Limit the previously approved isolated server to this owned port PR.

This is not standing EULA consent for other pull requests or repositories.
Manual runs still require an affirmative input on the same owned port branch.
"""
import argparse
import json
from pathlib import Path

REPOSITORY = 'UpperMoon0/Simply-Speakers'
BRANCH = 'port/requested-generations-20261021'
PR_NUMBER = 26


def mapping(value):
    return value if isinstance(value, dict) else {}


def owned_repository(value):
    value = mapping(value)
    return value.get('full_name') == REPOSITORY and value.get('fork') is False


def allowed(event_name, event, repository, ref):
    event = mapping(event)
    if repository != REPOSITORY or not owned_repository(event.get('repository')):
        return False
    if event_name == 'pull_request':
        pull = mapping(event.get('pull_request'))
        base, head = mapping(pull.get('base')), mapping(pull.get('head'))
        return (event.get('action') in ('opened', 'synchronize', 'reopened')
                and event.get('number') == PR_NUMBER and pull.get('number') == PR_NUMBER
                and pull.get('state') == 'open' and pull.get('merged') is False
                and ref == 'refs/pull/26/merge'
                and base.get('ref') == 'main' and owned_repository(base.get('repo'))
                and head.get('ref') == BRANCH and owned_repository(head.get('repo')))
    if event_name == 'workflow_dispatch':
        consent = mapping(event.get('inputs')).get('minecraft_eula_accepted')
        return ref == 'refs/heads/' + BRANCH and (consent is True or consent == 'true')
    return False


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--event-name', required=True)
    parser.add_argument('--event-path', type=Path, required=True)
    parser.add_argument('--repository', required=True)
    parser.add_argument('--ref', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        event = json.loads(args.event_path.read_text(encoding='utf-8'))
    except (OSError, ValueError):
        event = {}
    permitted = allowed(args.event_name, event, args.repository, args.ref)
    with args.output.open('a', encoding='utf-8') as output:
        output.write('accepted=' + str(permitted).lower() + '\n')
    print('Isolated Forge 1.12 acceptance scope: ' + ('permitted' if permitted else 'excluded'))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
