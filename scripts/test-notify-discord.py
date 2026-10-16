#!/usr/bin/env python3
"""Offline tests for the daily Discord stats summary."""

import datetime as dt
import importlib.util
import tempfile
import unittest
from pathlib import Path


spec = importlib.util.spec_from_file_location("notify_discord", Path(__file__).with_name("notify-discord.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

RELEASES = [
    {"tag_name": "2.1.4", "draft": False, "assets": []},
    {"tag_name": "2.1.3", "draft": False, "assets": [
        {"name": "Dari-2.1.3-arm64.dmg", "download_count": 2},
        {"name": "Dari-2.1.3-arm64.dmg.sha256", "download_count": 2},
        {"name": "Dari-2.1.3-x64.exe", "download_count": 1},
        {"name": "Dari-2.1.3-x64.msi", "download_count": 1},
    ]},
    {"tag_name": "2.1.2", "draft": False, "assets": [{"name": "Dari-2.1.2-arm64.dmg", "download_count": 3}]},
    {"tag_name": "2.2.0", "draft": True, "assets": [{"name": "Dari-2.2.0-arm64.dmg", "download_count": 9}]},
]


class NotifyDiscordTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.scarf = Path(self.tmp.name) / "scarf.csv"
        self.stats = Path(self.tmp.name) / "stats.csv"
        self.now = dt.datetime(2026, 10, 15, 0, 7, tzinfo=dt.timezone.utc)

    def test_installer_counts_skip_checksums_and_drafts(self):
        self.assertEqual(module.installer_counts(RELEASES), (7, ("2.1.3", 4)))

    def test_first_run_has_no_baseline(self):
        message = module.run(self.now, self.scarf, self.stats, (35, 6, ("2.1.3", 3)), None)
        self.assertIn("오늘 샘플 실패", message)
        self.assertIn("• Stars: **35개** (첫 관측", message)
        self.assertIn("Dari 일일 통계 — 10월 15일", message)
        self.assertIn("2026-10-15 09:07 KST", message)

    def test_deltas_against_previous_dates(self):
        self.scarf.write_text("observed_utc,rolling_30d_downloads\n2026-10-13T06:17:00Z,182\n"
                              "2026-10-15T00:07:30Z,190\n", encoding="utf-8")
        self.stats.write_text("observed_utc,stars,installer_downloads\n2026-10-14T00:07:00Z,34,5\n"
                              "2026-10-15T00:01:00Z,99,99\n", encoding="utf-8")
        message = module.run(self.now, self.scarf, self.stats, (35, 6, ("2.1.3", 3)), None)
        self.assertIn("• 30일 요청 합계: **190건** (10/13 관측 대비 +8)", message)
        self.assertIn("• Stars: **35개** (전일 대비 +1)", message)
        self.assertIn("• 누적 다운로드: **6회** (전일 대비 +1)", message)
        self.assertIn("• 최신 버전 2.1.3: **3회**", message)
        self.assertIn("사용자 수가 아니며", message)
        self.assertEqual(len(module.read_stats(self.stats)), 2)


if __name__ == "__main__":
    unittest.main()
