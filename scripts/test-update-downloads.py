#!/usr/bin/env python3
"""Tests for the public-badge sampling boundary."""

import datetime as dt
import contextlib
import importlib.util
import io
import os
import tempfile
import unittest
import urllib.error
import urllib.parse
import xml.etree.ElementTree as ET
from pathlib import Path
from unittest.mock import patch


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

    def redirect(self, message):
        return "https://img.shields.io/static/v1?" + urllib.parse.urlencode({
            "label": "Scarf: Downloads", "message": message,
        })

    def badge_response(self, message):
        return urllib.error.HTTPError(module.BADGE_URL, 302, "Found",
                                      {"Location": self.redirect(message)}, None)

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

    def test_next_exact_sample_preserves_prior_observations(self):
        module.update(self.now, 179, self.csv, self.svg)
        later = self.now + dt.timedelta(days=3)
        module.update(later, 2001, self.csv, self.svg)
        self.assertEqual(module.read_history(self.csv), [(self.now, 179), (later, 2001)])
        root = ET.fromstring(self.svg.read_text())
        circles = root.findall("{http://www.w3.org/2000/svg}circle")
        self.assertEqual(len(circles), 2)
        self.assertIn("2026-10-16: 2,001 downloads", self.svg.read_text())

    def test_exact_counts_through_http_boundary(self):
        for value in (0, 179, 2000, 1234567):
            with self.subTest(value=value), patch.object(module.urllib.request, "build_opener") as build:
                build.return_value.open.side_effect = self.badge_response(f"{value}/month")
                self.assertEqual(module.update(self.now, data_path=self.csv, svg_path=self.svg), "observed")
                self.assertEqual(module.read_history(self.csv), [(self.now, value)])
                root = ET.fromstring(self.svg.read_text())
                titles = [node.text for node in root.iter("{http://www.w3.org/2000/svg}title")]
                self.assertIn(f"2026-10-13: {value:,} downloads in the preceding 30 days", titles)
                self.assertEqual(build.return_value.open.call_args.kwargs["timeout"], 20)

    def test_rounded_values_preserve_existing_files(self):
        module.update(self.now, 179, self.csv, self.svg)
        before = self.csv.read_bytes(), self.svg.read_bytes()
        for message in ("2K/month", "1.5K/month", "2k/month", "1.2M/month", "3G/month"):
            for now in (self.now, self.now + dt.timedelta(days=1)):
                with self.subTest(message=message, now=now), patch.object(module.urllib.request, "build_opener") as build:
                    build.return_value.open.side_effect = self.badge_response(message)
                    self.assertEqual(module.update(now, data_path=self.csv, svg_path=self.svg), "rounded")
                    self.assertEqual((self.csv.read_bytes(), self.svg.read_bytes()), before)

    def test_rounded_first_sample_does_not_create_files(self):
        with patch.object(module, "fetch_count", side_effect=module.RoundedBadgeValue("2K/month")):
            self.assertEqual(module.update(self.now, data_path=self.csv, svg_path=self.svg), "rounded")
        self.assertFalse(self.csv.exists())
        self.assertFalse(self.svg.exists())

    def test_malformed_responses_are_errors_and_preserve_files(self):
        module.update(self.now, 179, self.csv, self.svg)
        before = self.csv.read_bytes(), self.svg.read_bytes()
        locations = [self.redirect(m) for m in (
            "", "-1/month", "01/month", "2.5/month", "2Q/month", "2K/day", "unknown", "NaN/month",
        )] + [
            "", self.redirect("2K/month").replace("https:", "http:"),
            self.redirect("2K/month").replace("img.shields.io", "example.com"),
            self.redirect("2K/month").replace("/static/v1", "/other"),
            self.redirect("2K/month").replace("Scarf", "Other"),
            self.redirect("179/month") + "&message=2K%2Fmonth",
            self.redirect("2K/month") + "&message=",
            self.redirect("2K/month") + "&label=other",
        ]
        for location in locations:
            with self.subTest(location=location), patch.object(module.urllib.request, "build_opener") as build:
                build.return_value.open.side_effect = urllib.error.HTTPError(
                    module.BADGE_URL, 302, "Found", {"Location": location}, None)
                with self.assertRaises(ValueError) as raised:
                    module.update(self.now, data_path=self.csv, svg_path=self.svg)
                self.assertNotIsInstance(raised.exception, module.RoundedBadgeValue)
                self.assertEqual((self.csv.read_bytes(), self.svg.read_bytes()), before)

    def test_network_and_http_errors_preserve_files(self):
        module.update(self.now, 179, self.csv, self.svg)
        before = self.csv.read_bytes(), self.svg.read_bytes()
        for error in (urllib.error.URLError("offline"), TimeoutError("timed out"),
                      *(urllib.error.HTTPError(module.BADGE_URL, code, "error", {}, None)
                        for code in (403, 429, 500))):
            with self.subTest(error=error), patch.object(module.urllib.request, "build_opener") as build:
                build.return_value.open.side_effect = error
                with self.assertRaises(type(error)):
                    module.update(self.now, data_path=self.csv, svg_path=self.svg)
                self.assertEqual((self.csv.read_bytes(), self.svg.read_bytes()), before)

    def test_non_redirect_response_is_error(self):
        with patch.object(module.urllib.request, "build_opener"):
            with self.assertRaisesRegex(ValueError, "expected a Scarf badge redirect"):
                module.update(self.now, data_path=self.csv, svg_path=self.svg)
        self.assertFalse(self.csv.exists())
        self.assertFalse(self.svg.exists())

    def test_cli_reports_observed_and_rounded_status(self):
        output = Path(self.tmp.name) / "output"
        summary = Path(self.tmp.name) / "summary"
        for value, expected in ((179, "observed"), (module.RoundedBadgeValue("2K/month"), "rounded")):
            with patch.dict(os.environ, {"GITHUB_OUTPUT": str(output), "GITHUB_STEP_SUMMARY": str(summary)}), \
                 patch.object(module, "fetch_count", side_effect=[value]), contextlib.redirect_stdout(io.StringIO()):
                module.main(["--data-path", str(self.csv), "--svg-path", str(self.svg)])
            self.assertTrue(output.read_text().endswith(f"status={expected}\n"))
        self.assertIn("exact count unavailable", summary.read_text())

    def test_bad_history_is_rejected(self):
        self.csv.write_text("observed_utc,rolling_30d_downloads\n2026-10-13T00:00:00Z,10\n"
                            "2026-10-13T01:00:00Z,11\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "one sorted sample"):
            module.read_history(self.csv)


if __name__ == "__main__":
    unittest.main()
