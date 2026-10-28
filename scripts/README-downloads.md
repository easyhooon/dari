# Dari download observations

The README chart tracks the public Scarf monthly downloads badge for the
single Maven Central coordinate `io.github.easyhooon:dari`. Scarf's package
list labels the same value as events over 30 days. The badge URL is supplied
on the Maven Central package's Scarf detail page and works without login.
No token, paid plan, raw feed, or customer information is used.

`scripts/update-downloads.py` reads the badge's public redirect and accepts
only an exact integer `N/month` response. It appends one sample per UTC date
to `docs/downloads-30d-observed.csv` and renders
`docs/downloads-30d-observed.svg`. A recognized rounded value such as
`2K/month` or `1.5K/month` skips the sample successfully, with an Actions
warning and step summary. The CSV and SVG stay byte-for-byte unchanged;
no zero, estimate, duplicate of the last value, or blank count is appended.
The chart's observation timestamps remain the dates of exact measurements.
Malformed responses and network/HTTP errors still fail the sampling step.

`.github/workflows/downloads.yml` runs once a day and commits those two
public aggregate files to `dari-downloads-data` if they changed. The README
loads the SVG from that branch because `main` requires changes through a pull
request. `workflow_dispatch` can run the sampler on demand. GitHub Actions
scheduling is approximate, so the SVG shows the actual UTC observation times.
Failed badge requests leave the previous chart intact.

## Exact-count source review

The public badge currently redirects to a Shields static badge containing
`2K/month`; Shields receives that display string, not an underlying exact
count. The [documented alternative badge URLs](https://docs.scarf.sh/gateway/#badges)
and appearance options do not document a raw-count mode. The legacy
package-ID installs-badge URL redirected to Scarf's homepage when checked.
No usable unauthenticated exact-count route was found in this review.
The API's package overview and package-by-ID endpoints both returned HTTP
401 without credentials (checked 2026-10-28).

Scarf documents authenticated [event and aggregate exports](https://docs.scarf.sh/data-export/).
Exports require an API token and an eligible plan; neither is configured in
this workflow. A future export source must verify the single-coordinate
scope, rolling window boundaries, and request semantics against the badge
before mixing its counts with this series. Do not substitute company totals,
which can exclude unmatched events, or sum rounded display values.

This is an observed trend of **rolling 30-day artifact requests**, not a daily
download series. History begins when sampling begins; this source cannot
backfill older points. It excludes all other Dari coordinates and all other
projects. Artifact requests include metadata requests and do not represent
people or installs. Scarf typically receives Maven Central downloads with
about a week's delay.

Run `python3 scripts/test-update-downloads.py` for offline validation.

## Daily Discord summary

After sampling, `scripts/notify-discord.py` posts one message to the
`DISCORD_WEBHOOK_URL` secret: the Scarf 30-day total and its change since the
previous observed date, GitHub stars, and cumulative `.dmg`/`.exe`/`.msi` downloads
from GitHub Releases (checksum files excluded; in-app updater fetches
included). Stars and installer totals are appended to
`docs/repo-stats-daily.csv` on `dari-downloads-data` so the next run can show
the change. The public badge cannot separate POM or metadata requests from
artifact downloads, so the message says so instead of splitting them. Run
`python3 scripts/notify-discord.py --dry-run` to print the message locally.
The workflow passes the sampler's `observed`/`rounded` status to the summary;
a failed step is reported as `failed`. A rounded response is explicitly shown
as an unavailable exact count, while stars and installer counts still update.
On same-day retries, an earlier exact sample is retained but is not presented
as a newly collected count when the current attempt was rounded or failed.
