# Feature Backlog — Daily Reading Planner

> A running list of candidate features for **future** implementation. Items here are **not
> committed scope** and are **not specced** — each is a one-line definition plus rough notes.
> When an item is picked up, it graduates to a real spec in [`docs/features/`](features/) (or an
> exploration in [`docs/explorations/`](explorations/) if it needs product discovery first) and
> gets scheduled by Morgan as a sprint.
>
> **Companion docs:** [PRD.md](PRD.md) · [SPEC.md](SPEC.md) · [EXECUTION_PLAN.md](EXECUTION_PLAN.md) · [CLAUDE.md](../CLAUDE.md)

## How to use this file

- Add anything worth not-forgetting. Cheap to add, cheap to delete.
- Keep each entry to a **high-level definition** — what it is and why, not how. No design here.
- Don't estimate or schedule. Priority/size are rough hints only.
- When an item is committed, move it out of the backlog and link the spec/sprint it became.

**Status legend:** 🔵 Idea · 🟡 Discussed · 🟢 Ready to spec · ✅ Graduated (link the spec) · ❌ Declined (note why)

**Columns:** *Priority* = owner's gut feel (P1 high … P3 someday). *Size* = T-shirt (S/M/L/XL), rough.

---

## Backlog

| # | Feature | Definition (one line) | Priority | Size | Status | Notes |
|---|---------|-----------------------|----------|------|--------|-------|
| 1 | Downloadable version catalog | Let users download Bible translations on demand from a catalog rather than bundling every version in the APK, to keep install size small. | — | — | 🔵 Idea | Ties into V3 in-app text; implies a download manager + on-device store + per-version licensing. |
| 2 | Bible audio | Play audio narration of the day's readings (or any chapter). | — | — | 🔵 Idea | Source/licensing of audio TBD; likely also a downloadable/streamed asset (overlaps #1's catalog idea). |
| 3 | Alternate reading schedules | Support reading plans beyond the Bible Companion (e.g. other Christadelphian or general plans) the user can choose from. | — | — | 🔵 Idea | Core data model is currently single-plan + date-anchored; would generalize plan selection. |

---

## Notes / parking lot

Free-form space for half-formed thoughts that aren't yet a backlog row.

-
