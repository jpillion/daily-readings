# Daily Readings

An offline-first Android app for the Christadelphian **"Bible Companion"** daily reading plan
(Robert Roberts). Three scripture portions a day, date-anchored so readers worldwide stay in sync.

> Working name — final name TBD. See [docs/SPEC.md](docs/SPEC.md) for the full product spec and roadmap.

## Status

Early. Currently at **Phase 0 — building the data foundation** (extracting the 366-day plan
and bundling Bible text). No app code yet.

## The plan

- **Stream 1 — Law & History:** Genesis → Job
- **Stream 2 — Psalms & Prophecy:** Psalms → Malachi
- **Stream 3 — New Testament:** Matthew → Revelation

Over a year: Old Testament once, New Testament twice.

## Planned stack

Kotlin · Jetpack Compose · Material 3 · Room · DataStore · Hilt · Glance widget · GitHub Actions CI.
