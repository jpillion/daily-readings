# CLAUDE.md — Daily Readings

Context for any Claude Code session working in this repo. Read [docs/SPEC.md](docs/SPEC.md)
for the full product spec; this file is the quick orientation + handoff.

## What this is

An **offline-first Android app** for the Christadelphian **"Bible Companion"** daily reading
plan (Robert Roberts). Three scripture portions per day, **date-anchored** (Jan 1 is always
Genesis 1–2 / Psalm 1–2 / Matthew 1–2), so all readers worldwide stay in sync.

This is a **standalone repo**, deliberately separate from the unrelated `strikelog` project.
Do not reference or depend on strikelog.

## Current status (as of 2026-06-10)

- Repo initialized on `main`. Committed: this file, `README.md`, `.gitignore`, `docs/SPEC.md`.
- **No app code yet.** Not pushed to a GitHub remote yet.
- Next up: **Phase 0 — the data foundation** (see below). Not started.

## The reading plan

Three parallel streams through scripture, one portion each per day:
1. **Stream 1 — Law & History:** Genesis → Job
2. **Stream 2 — Psalms & Prophecy:** Psalms → Malachi
3. **Stream 3 — New Testament:** Matthew → Revelation

Over a year: **Old Testament once, New Testament twice.** ~3–4 chapters/day.

## Immediate next step: Phase 0 (do before any app code)

The plan schedule and Bible text are the project's real IP and risk. Build them first:

1. **Reading plan → JSON.** Extract the full **366-day** table (includes Feb 29), each day
   with 3 portions of chapter spans. **Verify against a second source.** Suggested JSON shape:
   ```json
   { "month": 1, "day": 1,
     "portions": [
       {"stream": 1, "refs": ["Genesis 1", "Genesis 2"]},
       {"stream": 2, "refs": ["Psalm 1", "Psalm 2"]},
       {"stream": 3, "refs": ["Matthew 1", "Matthew 2"]}
     ]}
   ```
2. **Bible text dataset.** Acquire/validate a public-domain **KJV** (JSON or SQLite),
   books → chapters → verses.

Sources for the plan table:
- christadelphia.org (Excel/PDF): https://christadelphia.org/readplan.php
- Bible Companion booklet (PDF): https://antipas.org/library/Robert%20Roberts/Booklets/The%20Bible%20Companion.pdf
- Full 12-month table (PDF): https://pricejh.com/readingplan/plans/roberts.pdf
- Daily readings + study notes (model for a future notes feature): https://dailyreadings.org.uk/
- Background — Wikipedia: https://en.wikipedia.org/wiki/Bible_Companion
- Prior art (existing app): https://apps.apple.com/us/app/daily-bible-readings/id536687049

## Planned stack

Kotlin · Jetpack Compose · Material 3 · single-activity · Room (Bible text as read-only
bundled SQLite asset + user `reading_progress` table) · DataStore (prefs) · Hilt · AlarmManager
notifications · Glance widget · GitHub Actions CI (build + tests + Kover).

## Open decisions (resolve as they come up)

1. **Final app name** + package id. (Working name: *Daily Readings*; dir can be renamed.)
2. **Translations:** KJV-only for v1, or multi-translation schema from day one
   (recommended — cheap now, costly to retrofit). **Not yet decided.**
3. **Min SDK / target devices.**
4. **Distribution:** Play Store vs. sideload/community.

## Decisions already made

- New, separate repo (not inside strikelog). ✅
- Spec drafted first before code. ✅
- KJV is the default/v1 translation (public domain, traditional for this audience).
