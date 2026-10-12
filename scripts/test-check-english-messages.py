#!/usr/bin/env python3
"""Tests for the local hook and PR message policy."""

import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path


CHECKER = Path(__file__).with_name("check-english-messages.py")


class MessagePolicyTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)

    def run_checker(self, *args):
        return subprocess.run(
            ["python3", str(CHECKER), *map(str, args)],
            cwd=self.root,
            capture_output=True,
            text=True,
        )

    def test_commit_message_subject_and_body(self):
        message = self.root / "message.txt"
        for content, expected in (
            ("feat: add bridge handler names\n\nPreserve API compatibility.\n", 0),
            ("feat: 한글 제목\n", 1),
            ("feat: add names\n\n한글 본문\n", 1),
            ("feat: add names\n\nFirst line.\nSecond 줄.\n", 1),
            ("feat: 한글 jamo\n", 1),
            ("feat: ㅎㅎ compatibility jamo\n", 1),
        ):
            with self.subTest(content=content):
                message.write_text(content, encoding="utf-8")
                self.assertEqual(self.run_checker("--message-file", message).returncode, expected)

    def test_pr_checks_title_and_only_new_commits(self):
        def git(*args):
            return subprocess.check_output(["git", *args], cwd=self.root, text=True).strip()

        git("init", "-q")
        git("config", "user.name", "Test")
        git("config", "user.email", "test@example.com")
        env = {**os.environ, "GIT_AUTHOR_DATE": "2026-01-01T00:00:00Z", "GIT_COMMITTER_DATE": "2026-01-01T00:00:00Z"}

        def commit(message, content=None):
            (self.root / "content.txt").write_text(content if content is not None else message, encoding="utf-8")
            subprocess.run(["git", "add", "content.txt"], cwd=self.root, check=True)
            subprocess.run(["git", "commit", "-q", "-m", message], cwd=self.root, env=env, check=True)
            return git("rev-parse", "HEAD")

        base = commit("docs: 기존 한국어 기록")
        head = commit("feat: add a setting", content="한글 문서 내용")
        event = self.root / "event.json"

        def check(title, sha):
            event.write_text(json.dumps({"pull_request": {"number": 1, "title": title, "base": {"sha": base}, "head": {"sha": sha}}}), encoding="utf-8")
            return self.run_checker("--pr-event", event).returncode

        self.assertEqual(check("feat: add a setting", head), 0)
        self.assertEqual(check("feat: 한글 제목", head), 1)
        self.assertEqual(check("feat: 한글 title", head), 1)
        korean_head = commit("fix: update setting\n\n본문 한글")
        self.assertEqual(check("fix: update setting", korean_head), 1)


if __name__ == "__main__":
    unittest.main()
