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
| 1 | Downloadable version catalog | Let users download Bible translations on demand from a catalog rather than bundling every version in the APK, to keep install size small. | — | — | 🟡 Discussed | PRD: [downloadable-version-catalog.md](explorations/downloadable-version-catalog.md). Depends on V3 text. Crux: introduces the app's **first network dependency**. Shares download infra with #2 — Maya recommends gating on #2 and building together, not standalone. |
| 2 | Bible audio | Play audio narration of the day's readings (or any chapter). | — | — | 🟡 Discussed | PRD: [bible-audio.md](explorations/bible-audio.md). Hard gate: can we lawfully ship + cache KJV *audio* (a recording is a separate copyright from the public-domain text). Shares the download/catalog mechanism with #1. |
| 3 | Alternate reading schedules | Support reading plans beyond the Bible Companion (e.g. other Christadelphian or general plans) the user can choose from. | — | — | 🟡 Discussed | PRD: [alternate-reading-schedules.md](explorations/alternate-reading-schedules.md). Identity shift ("THE Bible Companion app" → "a reading-plan app"). Splits into (A) more same-shape Christadelphian plans vs (B) any popular plan. Open: what happens to progress on plan switch. |
| 4 | New-version notice | Notify the user in-app when a new **minor-or-higher** version is available (1.x → 1.x+1 or 2.0), but stay silent for patch releases (1.1.x). | — | M | ✅ Done (Sprint 23) | Shipped **Option A** (Play In-App Updates, flexible flow): on launch the app checks Play for a newer build and `UpdatePromptDecision` gates the prompt (PATCH=silent, MINOR/MAJOR=prompt, via the D-S9-3 `/100` rule); a downloaded update raises a calm "Restart" snackbar in `RootScaffold`. **Verified posture finding (D-L-6):** `app-update`/`app-update-ktx` 2.1.0 add **NO** manifest permission (no INTERNET, no GMS perms) — only a `PlayCoreDialogWrapperActivity` + the `gms.version` meta-data; networking is brokered via the Play Store app/GMS, so the no-INTERNET offline identity (NFR-V3-A) holds. Handoff: [sprint-0023-in-app-update.md](sprints/sprint-0023-in-app-update.md). |
| 5 | Per-verse "open in external app" links | In the in-app reader pane, a small icon beside each verse that opens that exact verse location in the user's chosen external app/site. | — | — | 🟡 Discussed | PRD: [per-verse-external-links.md](explorations/per-verse-external-links.md). Depends on V3 reader. Extends `ProviderUrlBuilder`/`BibleProvider` from chapter→verse deep links. Feasibility gate: not every provider supports verse-level links — needs a per-provider link-check (mirrors existing gates). |
| 6 | Swipe the date-picker calendar | Let the user swipe left/right on the date-picker calendar grid to move between months, **crossing year boundaries** (Dec → Jan of the next year). | — | S | ✅ Done (Sprint 21) | Shipped: `DayDatePickerDialog` months ride a `HorizontalPager` (`monthForPage`, ±3,000-month window). Chevrons drive the same pager and no longer year-bound; completion dots key to full dates. Supersedes the pinned-year part of D-S5-3 *for the picker*. Handoff: [sprint-0021-date-picker-ux.md](sprints/sprint-0021-date-picker-ux.md). |
| 7 | One-tap date selection | Tapping a day in the date picker selects it immediately and closes the dialog — no separate "Select date" confirm tap. | — | S | ✅ Done (Sprint 21) | Shipped: tapping a day cell fires `onConfirm` and closes the dialog; confirm button + `date_picker_confirm` string removed; Cancel kept. Selection is non-destructive (navigates the day pager), so no confirm step is needed. Handoff: [sprint-0021-date-picker-ux.md](sprints/sprint-0021-date-picker-ux.md). |

---

## Notes / parking lot

Free-form space for half-formed thoughts that aren't yet a backlog row.

**Cross-cutting decision the PRDs surfaced — the networking posture.** The app is deliberately
offline-first with **no network dependency and no telemetry**. Items #1, #2, and (one branch of)
#4 each want to reach the network. That's not 3 separate calls — it's **one owner decision**:
is the app allowed to make anonymous, explicit, content-only network requests at all? Until
that's answered, #1 and #2 can't really proceed, and #4 collapses to its local-only branch.

-
