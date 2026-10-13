#!/usr/bin/env python3
"""Tests for the public-badge sampling boundary."""

import datetime as dt
import importlib.util
import tempfile
import unittest
from pathlib import Path


spec = importlib.util.spec_from_file_location(
    "update_downloads", Path(__file__).with_name("update-downloads.py")
)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class UpdateDownloadsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.csv = Path(self.tmp.name) / "history.csv"
        self.svg = Path(self.tmp.name) / "history.svg"
        self.now = dt.datetime(2026, 10, 13, 4, 50, tzinfo=dt.timezone.utc)

    def test_public_badge_redirect(self):
        self.assertEqual(
            module.parse_badge_redirect(
                "https://img.shields.io/static/v1?label=Scarf%3A%20Downloads&message=179%2Fmonth"
            ),
            179,
        )
        with self.assertRaisesRegex(ValueError, "exact integer"):
            module.parse_badge_redirect(
                "https://img.shields.io/static/v1?label=Scarf%3A%20Downloads&message=1.5K%2Fmonth"
            )

    def test_sample_and_replace_same_day(self):
        module.update(self.now, 179, self.csv, self.svg)
        module.update(self.now + dt.timedelta(hours=1), 180, self.csv, self.svg)
        self.assertEqual(len(module.read_history(self.csv)), 1)
        self.assertIn("30-day artifact requests, not unique users", self.svg.read_text())
        self.assertIn("2026-10-13: 180 downloads", self.svg.read_text())

    def test_bad_history_is_rejected(self):
        self.csv.write_text("observed_utc,rolling_30d_downloads\n2026-10-13T00:00:00Z,10\n"
                            "2026-10-13T01:00:00Z,11\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "one sorted sample"):
            module.read_history(self.csv)


if __name__ == "__main__":
    unittest.main()
