# CLAUDE.md — Daily Readings

Context for any Claude Code session working in this repo. This file is the quick orientation +
handoff. The planning docs (written by the product/engineering team) live in `docs/`:

- [docs/SPEC.md](docs/SPEC.md) — product/build spec (concept, scope, roadmap).
- [docs/PRD.md](docs/PRD.md) — Product Requirements Document (Maya, PM): users, goals,
  scope by release, user stories, functional requirements, success metrics.
- [docs/ENGINEERING_SPEC.md](docs/ENGINEERING_SPEC.md) — Engineering Requirements &
  Architecture (Diego, staff architect): stack, module layout, data design, NFRs, decisions.
- [docs/EXECUTION_PLAN.md](docs/EXECUTION_PLAN.md) — V1 execution plan (Morgan, EM): up-front
  decisions, sprint sequence, ticketed first two sprints. **Start here to build.**

The agentic team lives at `.claude/agents/` (symlinked to the shared `../agents/android-team`).

## What this is

An Android app for the Christadelphian **"Bible Companion"** daily reading plan (Robert
Roberts). Three scripture readings per day, **date-anchored** (Jan 1 is always Genesis 1–2 /
Psalm 1–2 / Matthew 1–2), so all readers worldwide stay in sync.

**V1 is a digital reading planner**, not a Bible reader: show today's three readings, mark them
done, and tap any reading to open its chapter on Blue Letter Bible (KJV) in the browser. No
scripture text is bundled or rendered in-app in V1 — that's V3. The planner/tracker core works
offline; the text link needs network.

This is a **standalone repo**, deliberately separate from the unrelated `strikelog` project.
Do not reference or depend on strikelog.

## Current status (as of 2026-06-10)

- Repo on `main`; planning docs in `docs/`. Not pushed to a GitHub remote yet. **No app code yet.**
- ✅ **Sprint 1 (Phase 0) is DONE** (commits `803ad3a`, `e22a123`): `data/reading_plan.json`
  (365 days, Feb = 28, no Feb 29), `data/book_catalog.csv` (66 books, live-verified BLB
  abbrevs), independent second-source fixture, and a green verification gate
  (`cd verification && ./gradlew test` — 7 tests incl. day-by-day equality vs. second source).
  Handoff: [docs/sprints/sprint-0001-trusted-plan-data.md](docs/sprints/sprint-0001-trusted-plan-data.md);
  reconciliation log: [docs/data/README.md](docs/data/README.md).
- Next up: **Sprint 2 — scaffold + CI + DI + theme** (see [docs/EXECUTION_PLAN.md](docs/EXECUTION_PLAN.md) §4).
  Note for Sprint 2: a portion can span two books (Jun 19 / Dec 19 = 2 John + 3 John); the gate
  test takes `dataDir` as a system property for easy re-homing into `:app` (S2-T6).

## The reading plan

Three parallel streams through scripture, one portion each per day:
1. **Stream 1 — Law & History:** Genesis → Job
2. **Stream 2 — Psalms & Prophecy:** Psalms → Malachi
3. **Stream 3 — New Testament:** Matthew → Revelation

Over a year: **Old Testament once, New Testament twice.** ~3–4 chapters/day.

## Phase 0 (data foundation) — ✅ DONE (Sprint 1, 2026-06-10)

The reading plan schedule is the project's real IP and the **only** V1 data asset (no Bible
text is bundled in V1). It now exists and is gate-verified:

- `data/reading_plan.json` — 365 days (Feb = 28, **no Feb 29 entry** per D1), structured
  `{book, chapter}` refs (per D3 — not the string form once sketched here), `schemaVersion: 1`.
- `data/book_catalog.csv` — 66 books `(order, canonicalName, chapterCount, blbAbbrev)`,
  all abbrevs verified live against BLB (D2 resolved).
- `data/reading_plan_verify.json` — independent second-source fixture (antipas booklet;
  the pricejh PDF turned out byte-identical to the primary, so it was substituted).
- `verification/` — standalone Gradle JVM module; `cd verification && ./gradlew test` runs
  the 7-test gate incl. day-by-day equality vs. the second source. Re-home into `:app` in
  Sprint 2 (S2-T6; test takes `dataDir` as a system property).
- Sources, normalization rules, and the 7-conflict reconciliation log:
  [docs/data/README.md](docs/data/README.md).

> The KJV **text** dataset is **not** a Phase 0 item — it's deferred to V3 (in-app text).
> V1 reaches scripture via Blue Letter Bible links:
> `https://www.blueletterbible.org/kjv/<book>/<chapter>/` (3-letter book abbrev, e.g.
> `gen`, → `/kjv/gen/1/`).

Reference sources (extraction is done; kept for reconciliation/notes-feature reference):
- christadelphia.org (Excel/PDF): https://christadelphia.org/readplan.php
- Bible Companion booklet (PDF): https://antipas.org/library/Robert%20Roberts/Booklets/The%20Bible%20Companion.pdf
- Daily readings + study notes (model for a future notes feature): https://dailyreadings.org.uk/
- Background — Wikipedia: https://en.wikipedia.org/wiki/Bible_Companion
- Prior art (existing app): https://apps.apple.com/us/app/daily-bible-readings/id536687049

## Planned stack

**V1:** Kotlin · Jetpack Compose · Material 3 · single-activity · plan JSON in memory ·
small progress store (DataStore or tiny Room table) · DataStore (theme) · Glance widget ·
outbound Blue Letter Bible links · Hilt · GitHub Actions CI (build + tests + Kover).

**Later:** AlarmManager reminders (V2); Room + bundled read-only **SQLite KJV asset** for
in-app text and richer progress/streak schema (V2/V3).

## Open decisions

None. All product/owner decisions are resolved — see below and `docs/EXECUTION_PLAN.md` §2.
(The BLB abbreviation table landed in Sprint 1: `data/book_catalog.csv`, all 66 link-checked.)

## Decisions already made

- New, separate repo (not inside strikelog). ✅
- Spec drafted first before code. ✅
- **V1 = digital reading planner**; no in-app scripture text (deferred to V3). ✅
- Mark-as-read is **per reading** (3/day) + one-tap "whole day done"; **not** per-chapter. ✅
- Scripture reached via **Blue Letter Bible** KJV links, not bundled text, in V1. ✅
- Schedule keyed by (month, day); **progress keyed by full date** so marks don't repeat
  across years. ✅
- KJV is the default/v1 translation; multi-translation schema is moot for V1 (no text stored). ✅
- **App name = "Daily Reading Planner"**; package id = **`com.jpillion.dailyreadingplanner`**. ✅
- **Feb 29 = no scheduled readings.** Plan covers 365 days (Feb = 28 entries, no Feb 29 entry);
  in leap years the date shows **"No scheduled readings for Feb 29th"** (no readings/marks/
  tracking); non-leap years skip it. No fold/double-day logic. ✅
- **minSdk = 26**; targetSdk/compileSdk = latest stable. ✅
- **No analytics/telemetry in V1** (no networking dep). ✅
- **Distribution = Play Store.** ✅
