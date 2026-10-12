#!/usr/bin/env python3
"""Reject Hangul in new commit messages and pull request titles."""

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path


HANGUL = re.compile(r"[\u1100-\u11ff\u3130-\u318f\ua960-\ua97f\uac00-\ud7a3\ud7b0-\ud7ff\uffa0-\uffdc]")
SHA = re.compile(r"[0-9a-fA-F]{40}\Z")


def check(label: str, message: str) -> bool:
    if HANGUL.search(message):
        print(f"{label}: use English in the title and body (Hangul found)", file=sys.stderr)
        return False
    return True


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], text=True)


def check_pr(event_file: Path) -> bool:
    event = json.loads(event_file.read_text(encoding="utf-8"))
    pr = event["pull_request"]
    base, head = pr["base"]["sha"], pr["head"]["sha"]
    if not SHA.fullmatch(base) or not SHA.fullmatch(head):
        raise ValueError("invalid PR commit SHA")

    number = pr["number"]
    if not isinstance(number, int) or number < 1:
        raise ValueError("invalid PR number")
    if subprocess.run(["git", "cat-file", "-e", f"{head}^{{commit}}"], stderr=subprocess.DEVNULL, check=False).returncode != 0:
        subprocess.run(["git", "fetch", "--no-tags", "origin", f"pull/{number}/head"], check=True)
        if git("rev-parse", "FETCH_HEAD").strip() != head:
            raise ValueError("PR head changed during the check; rerun the workflow")

    passed = check("PR title", pr["title"])
    commits = git("rev-list", "--reverse", f"{base}..{head}").splitlines()
    for commit in commits:
        message = git("show", "-s", "--format=%B", commit)
        passed = check(f"commit {commit[:12]}", message) and passed
    return passed


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--message-file", type=Path)
    source.add_argument("--pr-event", type=Path)
    args = parser.parse_args()

    try:
        if args.message_file:
            return 0 if check("commit message", args.message_file.read_text(encoding="utf-8")) else 1
        return 0 if check_pr(args.pr_event) else 1
    except (OSError, KeyError, ValueError, subprocess.CalledProcessError) as error:
        print(f"message check failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
