#!/usr/bin/env python3
"""Record daily repo stats and post a Dari stats summary to a Discord webhook."""

import argparse
import csv
import datetime as dt
import importlib.util
import json
import os
import urllib.request
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REPO = "easyhooon/dari"
HEADER = ["observed_utc", "stars", "installer_downloads"]
INSTALLER_SUFFIXES = (".dmg", ".exe", ".msi")
KST = dt.timezone(dt.timedelta(hours=9))
USER_AGENT = "dari-daily-stats/1.0"

_spec = importlib.util.spec_from_file_location("update_downloads", Path(__file__).with_name("update-downloads.py"))
update_downloads = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(update_downloads)


def github_get(path: str, token: str | None):
    headers = {"Accept": "application/vnd.github+json", "User-Agent": USER_AGENT}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(f"https://api.github.com{path}", headers=headers)
    with urllib.request.urlopen(request, timeout=20) as response:
        return json.load(response)


def installer_counts(releases):
    """Total .dmg/.exe/.msi downloads, plus the newest release that has installers."""
    total, latest = 0, None
    for release in releases:
        if release.get("draft"):
            continue
        count = sum(
            asset["download_count"]
            for asset in release.get("assets", [])
            if asset["name"].endswith(INSTALLER_SUFFIXES)
        )
        has_installers = any(a["name"].endswith(INSTALLER_SUFFIXES) for a in release.get("assets", []))
        total += count
        if latest is None and has_installers:
            latest = (release["tag_name"], count)
    return total, latest


def fetch_repo_stats(repo: str, token: str | None):
    stars = github_get(f"/repos/{repo}", token)["stargazers_count"]
    releases, page = [], 1
    while True:
        batch = github_get(f"/repos/{repo}/releases?per_page=100&page={page}", token)
        releases.extend(batch)
        if len(batch) < 100:
            break
        page += 1
    total, latest = installer_counts(releases)
    return stars, total, latest


def read_stats(path: Path):
    if not path.exists():
        return []
    with path.open(newline="", encoding="utf-8") as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames != HEADER:
            raise ValueError("invalid stats columns")
        return [
            (dt.datetime.fromisoformat(row["observed_utc"].replace("Z", "+00:00")),
             int(row["stars"]), int(row["installer_downloads"]))
            for row in reader
        ]


def write_stats(path: Path, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.writer(stream)
        writer.writerow(HEADER)
        writer.writerows((stamp.isoformat().replace("+00:00", "Z"), *values) for stamp, *values in rows)


def split_today(rows, today: dt.date):
    """Return (today's row or None, latest row from an earlier UTC date or None)."""
    current = next((row for row in rows if row[0].date() == today), None)
    earlier = [row for row in rows if row[0].date() < today]
    return current, (earlier[-1] if earlier else None)


def delta(value: int, previous, today: dt.date) -> str:
    if previous is None:
        return "첫 관측"
    stamp, old = previous
    label = "전일 대비" if stamp.date() == today - dt.timedelta(days=1) else f"{stamp:%m/%d} 관측 대비"
    return f"{label} {value - old:+,}"


def build_message(now, scarf_rows, stats_rows, latest, scarf_status=None):
    today = now.date()
    scarf_today, scarf_prev = split_today(scarf_rows, today)
    stats_today, stats_prev = split_today(stats_rows, today)
    _, stars, installers = stats_today
    star_prev = (stats_prev[0], stats_prev[1]) if stats_prev else None
    installer_prev = (stats_prev[0], stats_prev[2]) if stats_prev else None
    local = now.astimezone(KST)
    scarf_line = (
        f"• 30일 요청 합계: **{scarf_today[1]:,}건** ({delta(scarf_today[1], scarf_prev, today)})"
        if scarf_today else "• 30일 요청 합계: 오늘 샘플 실패"
    )
    if scarf_status == "rounded":
        scarf_line = "• 30일 요청 합계: 정확한 수치 없음 (배지 축약값으로 수집 건너뜀)"
    elif scarf_status == "failed":
        scarf_line = "• 30일 요청 합계: 오늘 샘플 실패"
    lines = [
        f"📊 **Dari 일일 통계 — {local.month}월 {local.day}일**",
        f"-# {local:%Y-%m-%d %H:%M} KST 관측",
        "",
        "**Maven Central (Scarf)**",
        scarf_line,
        "• 대상: `io.github.easyhooon:dari`",
        "",
        "**GitHub**",
        f"• Stars: **{stars:,}개** ({delta(stars, star_prev, today)})",
        "",
        "**데스크톱 앱 설치 파일**",
        f"• 누적 다운로드: **{installers:,}회** ({delta(installers, installer_prev, today)})",
    ]
    if latest:
        lines.append(f"• 최신 버전 {latest[0]}: **{latest[1]:,}회**")
    lines += [
        "",
        "_Scarf 수치는 POM·메타데이터·자동 도구 요청이 섞인 30일 이동 합계로, 사용자 수가 아니며 약 1주 늦게 반영됩니다. "
        "변화량은 그날 다운로드 수가 아닙니다. 설치 파일 횟수는 .dmg/.exe/.msi 기준이며 인앱 업데이터·재다운로드를 포함합니다._",
    ]
    return "\n".join(lines)


def post_discord(webhook: str, content: str):
    url = webhook + ("&" if "?" in webhook else "?") + "wait=true"
    body = json.dumps({"content": content, "allowed_mentions": {"parse": []}}).encode()
    request = urllib.request.Request(
        url, data=body, method="POST",
        headers={"Content-Type": "application/json", "User-Agent": USER_AGENT},
    )
    with urllib.request.urlopen(request, timeout=20) as response:
        return json.load(response)


def run(now, scarf_path: Path, stats_path: Path, repo_stats, webhook: str | None, scarf_status=None):
    stars, installers, latest = repo_stats
    rows = [row for row in read_stats(stats_path) if row[0].date() != now.date()]
    rows.append((now, stars, installers))
    rows.sort()
    write_stats(stats_path, rows)
    message = build_message(now, update_downloads.read_history(scarf_path), rows, latest, scarf_status)
    if webhook:
        sent = post_discord(webhook, message)
        print(f"Discord accepted message {sent['id']} at {sent['timestamp']}")
    else:
        print(message)
    return message


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scarf-path", type=Path, default=ROOT / "docs" / "downloads-30d-observed.csv")
    parser.add_argument("--stats-path", type=Path, default=ROOT / "docs" / "repo-stats-daily.csv")
    parser.add_argument("--repo", default=REPO)
    parser.add_argument("--scarf-status", choices=("observed", "rounded", "failed"))
    parser.add_argument("--dry-run", action="store_true", help="print the message instead of posting it")
    args = parser.parse_args()
    webhook = None if args.dry_run else os.environ.get("DISCORD_WEBHOOK_URL")
    if not args.dry_run and not webhook:
        raise SystemExit("DISCORD_WEBHOOK_URL is not set")
    run(
        dt.datetime.now(dt.timezone.utc), args.scarf_path, args.stats_path,
        fetch_repo_stats(args.repo, os.environ.get("GH_TOKEN")), webhook, args.scarf_status,
    )
