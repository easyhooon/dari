#!/usr/bin/env python3
"""Sample Scarf's public Dari badge and render its observed 30-day trend."""

import csv
import datetime as dt
import html
import re
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path


BADGE_URL = (
    "https://api.scarf.sh/v2/packages/Easyhooon/"
    "829e32f5-20a5-45b8-b758-fe71daf64cac/downloads-badge"
)
ROOT = Path(__file__).resolve().parents[1]
DATA_PATH = ROOT / "docs" / "downloads-30d-observed.csv"
SVG_PATH = ROOT / "docs" / "downloads-30d-observed.svg"
HEADER = ["observed_utc", "rolling_30d_downloads"]


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, new_url):
        return None


def parse_badge_redirect(location: str) -> int:
    url = urllib.parse.urlparse(location)
    if url.scheme != "https" or url.netloc != "img.shields.io" or url.path != "/static/v1":
        raise ValueError("unexpected badge redirect")
    query = urllib.parse.parse_qs(url.query)
    if query.get("label") != ["Scarf: Downloads"]:
        raise ValueError("unexpected badge label")
    message = query.get("message", [""])[0]
    match = re.fullmatch(r"(0|[1-9][0-9]*)/month", message)
    if not match:
        raise ValueError(f"badge value is not an exact integer: {message}")
    return int(match.group(1))


def fetch_count() -> int:
    request = urllib.request.Request(BADGE_URL, headers={"User-Agent": "dari-readme-chart/1.0"})
    try:
        urllib.request.build_opener(NoRedirect()).open(request, timeout=20)
    except urllib.error.HTTPError as error:
        if error.code not in (301, 302, 303, 307, 308):
            raise
        return parse_badge_redirect(error.headers.get("Location", ""))
    raise ValueError("expected a Scarf badge redirect")


def read_history(path: Path):
    if not path.exists():
        return []
    with path.open(newline="", encoding="utf-8") as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames != HEADER:
            raise ValueError("invalid history columns")
        history = []
        for row in reader:
            observed = dt.datetime.fromisoformat(row["observed_utc"].replace("Z", "+00:00"))
            if observed.utcoffset() != dt.timedelta(0):
                raise ValueError("history time must be UTC")
            value = row["rolling_30d_downloads"]
            if not value.isdecimal():
                raise ValueError("invalid history count")
            history.append((observed, int(value)))
    if history != sorted(history) or len({stamp.date() for stamp, _ in history}) != len(history):
        raise ValueError("history must contain one sorted sample per UTC date")
    return history


def write_history(path: Path, history):
    with path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.writer(stream)
        writer.writerow(HEADER)
        writer.writerows((stamp.isoformat().replace("+00:00", "Z"), value) for stamp, value in history)


def render_svg(history):
    if not history:
        raise ValueError("no observed samples")
    width, height = 960, 390
    left, right, top, bottom = 72, 28, 82, 78
    plot_width, plot_height = width - left - right, height - top - bottom
    maximum = max(1, max(value for _, value in history))
    points = []
    labels = []
    for index, (stamp, value) in enumerate(history):
        x = left + (plot_width / 2 if len(history) == 1 else plot_width * index / (len(history) - 1))
        y = top + plot_height * (1 - value / maximum)
        points.append((x, y))
        if index == 0 or index == len(history) - 1 or index % max(1, len(history) // 6) == 0:
            labels.append(
                f'<text x="{x:.1f}" y="337" text-anchor="middle" class="axis">'
                f'{stamp.strftime("%b %d")}</text>'
            )
    polyline = " ".join(f"{x:.1f},{y:.1f}" for x, y in points)
    circles = [
        f'<circle cx="{x:.1f}" cy="{y:.1f}" r="5" fill="#6574db">'
        f'<title>{stamp.date()}: {value:,} downloads in the preceding 30 days</title></circle>'
        for (stamp, value), (x, y) in zip(history, points)
    ]
    grid = []
    for step in range(5):
        count = round(maximum * step / 4)
        y = top + plot_height * (1 - step / 4)
        grid.append(
            f'<line x1="{left}" x2="{width - right}" y1="{y:.1f}" y2="{y:.1f}" '
            'stroke="#9aa4bb" stroke-opacity=".3"/>'
            f'<text x="{left - 12}" y="{y + 4:.1f}" text-anchor="end" '
            f'class="axis">{count:,}</text>'
        )
    first, last = history[0][0], history[-1][0]
    subtitle = f"Observed {first.date()} to {last.date()} UTC · updated {last.strftime('%Y-%m-%d %H:%M')} UTC"
    starting_note = (
        '<text x="502" y="205" text-anchor="middle" class="axis">'
        'Collection started; the trend appears as daily observations accumulate.</text>\n'
        if len(history) == 1 else ""
    )
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {height}" '
        'role="img" aria-labelledby="title desc">\n'
        '<title id="title">Dari rolling 30-day downloads</title>\n'
        '<desc id="desc">Daily observations of the public Scarf downloads badge for '
        'io.github.easyhooon:dari. Each point is a preceding 30-day artifact download count, '
        'not downloads on that day or unique users.</desc>\n'
        '<style>.background{fill:#fff}text{font:14px system-ui,sans-serif;fill:#253047}'
        '.axis{font-size:12px;fill:#58657b}'
        '@media(prefers-color-scheme:dark){.background{fill:#121a2b}'
        'text{fill:#e5eaf4}.axis{fill:#b4bed0}}</style>\n'
        '<rect class="background" width="960" height="390" rx="12"/>\n'
        '<text x="72" y="30" font-size="21" font-weight="700">Dari rolling 30-day downloads</text>\n'
        f'<text x="72" y="53" class="axis">{html.escape(subtitle)}</text>\n'
        + "\n".join(grid)
        + f'\n<polyline points="{polyline}" fill="none" stroke="#6574db" '
        'stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>\n'
        + "\n".join(circles + labels)
        + "\n" + starting_note
        + '\n<text x="72" y="372" class="axis">Source: Scarf public dari badge · '
        '30-day artifact requests, not unique users</text>\n</svg>\n'
    )


def update(now=None, count=None, data_path=DATA_PATH, svg_path=SVG_PATH):
    observed = now or dt.datetime.now(dt.timezone.utc)
    if observed.utcoffset() != dt.timedelta(0):
        raise ValueError("observation time must be UTC")
    value = fetch_count() if count is None else count
    if not isinstance(value, int) or value < 0:
        raise ValueError("invalid badge count")
    history = read_history(data_path)
    history = [(stamp, old) for stamp, old in history if stamp.date() != observed.date()]
    history.append((observed, value))
    history.sort()
    write_history(data_path, history)
    svg_path.write_text(render_svg(history), encoding="utf-8")


if __name__ == "__main__":
    update()
