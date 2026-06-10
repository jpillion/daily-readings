# Daily Readings

A **digital reading planner** (Android) for the Christadelphian **"Bible Companion"** daily
reading plan (Robert Roberts). Three scripture readings a day, date-anchored so readers
worldwide stay in sync. See today's readings, mark them done, and tap through to the text on
Blue Letter Bible (KJV).

> Working name — final name TBD. See [docs/SPEC.md](docs/SPEC.md) for the full product spec and roadmap.

## Status

Early. Currently at **Phase 0 — building the data foundation** (extracting and verifying the
366-day plan). No app code yet. V1 ships the plan only; in-app scripture text comes in V3.

## The plan

- **Stream 1 — Law & History:** Genesis → Job
- **Stream 2 — Psalms & Prophecy:** Psalms → Malachi
- **Stream 3 — New Testament:** Matthew → Revelation

Over the year (366-day table, incl. Feb 29): Old Testament once, New Testament twice.

## Planned stack

Kotlin · Jetpack Compose · Material 3 · DataStore · Hilt · Glance widget · GitHub Actions CI.
(In-app Bible text via Room + bundled SQLite arrives in V3.)
