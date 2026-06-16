# Alternate-Schedules Sprint D — plan selector + whole-app integration

**Track:** Alternate Reading Schedules (multi-plan). **Sprint:** D (the selector + the live
active-plan wiring through every surface — peer of C downstream of B). **Status:** DONE, uncommitted
in the working tree (the main session independently verifies + commits). **Version:** untouched (no
bump — D ships with the alt-schedules release in Sprint E).
**Plan:** [docs/EXECUTION_PLAN-alternate-schedules.md](../EXECUTION_PLAN-alternate-schedules.md) §3 (SD sketch).
**Eng spec:** [docs/ENGINEERING_SPEC-alternate-schedules.md](../ENGINEERING_SPEC-alternate-schedules.md) §6 (D-ALT-16…19), §8 (D-ALT-22/23).
**Predecessors:** [sprint-alt-A-plan-foundation.md](sprint-alt-A-plan-foundation.md),
[sprint-alt-B-progress-migration.md](sprint-alt-B-progress-migration.md), Sprint C (committed `7e6e9a6`;
no separate handoff doc — its record is the CLAUDE.md Sprint C entry + the code).

## Goal outcome — MET

**Selecting M'Cheyne makes the WHOLE app show M'Cheyne, live; switching back restores the Bible
Companion view AND its progress, intact.** Plan choice is a Settings-only, off-the-daily-path
selection. A Bible Companion reader who never opens the selector gets the current app byte-for-byte
(default = `bible_companion`); every pre-existing pin passes UNCHANGED.

## Current capability (the headline)

A user can open Settings, pick **M'Cheyne** from the "Reading plan" dropdown, confirm a calm one-time
explanation, and watch the day screen go from 3 cards to **4** (the M'Cheyne streams), the stats panel
to **4 strips / 4 rows / 4-stream denominators**, and the launcher widget snap to the new plan — all
fresh (no M'Cheyne marks yet). Switching back to Bible Companion restores its 3-stream view and every
mark made before the switch. This is proven in working software, not asserted: `PlanSwitchIntegrationTest`
drives the real day-readings engine through the real `ActivePlanRepository` over the real bundled
BC + M'Cheyne assets, flipping `selected_plan` on a single open subscription.

## What landed (tickets)

| Ticket | What | Result |
|---|---|---|
| SD-T1 | Active plan + available plans in `SettingsViewModel` | `planSelector: StateFlow<PlanSelectorUiState>` (registry plans with **descriptor-sourced names** + live active id); injects `ActivePlanRepository`/`PlanRegistry`/`ReadingPlanRepository`. |
| SD-T2 | Settings "Reading plan" selector row (D-ALT-18) | `PlanDropdown` over the S14 `SettingsDropdownRow` idiom, top of Settings; tags `plan-dropdown`, `plan-option-<id>`; ≥48dp + spoken (a11y gate). |
| SD-T3 | The explained non-destructive switch dialog (D-ALT-19) | `plan-switch-dialog`; names both plans; writes NOTHING until confirm; same-plan = no-op. |
| SD-T4 | Widget refresh-on-switch (D-ALT-10 wiring half) | confirm fires `WidgetRefresher`. **The `@EntryPoint` needed NO read-side change** — it already resolves the active plan through `GetDayReadingsUseCase`. |
| SD-T5 | End-to-end live-switch gate + per-plan-progress-survives proof | `PlanSwitchIntegrationTest` (2 tests): live round-trip + marks isolation. |
| SD-T6 | Strings + a11y + mutation verification + docs | 6 new strings; 5 mutations killed; this handoff + CLAUDE.md. |

## OQ-7 — RESOLVED (D-D-ALT-1): Settings-only, no first-run plan question

**Default = Bible Companion; the selector lives ONLY in Settings; no first-run plan step.** Rationale:
first-run already has the tracking-start prompt and the reading-destination prompt; a third would
clutter the zero-setup promise (G1/M2). The selector row itself satisfies the "active plan is visible"
requirement (FR-ALT-6, U-ALT-6). A day-screen plan-label was deliberately NOT added (it would touch the
S16/S18 one-screen-fit budget) — queued as an owner candidate for Sprint E if wanted after the device
pass. The plan is never a silent non-BC default. **No first-run plan question is recommended.**

## How the live switch threads through (the load-bearing design)

The whole switch is **"write a key; everything re-emits."** No data operation, no per-surface plumbing
in D — because Sprint C already made the engine active-plan-driven:

- **Schedule / day pager / picker dots:** `GetDayReadingsUseCase` + `GetMonthCompletionUseCase`
  `flatMapLatest` on `ActivePlanRepository.activePlanId` and `combine` the `activeDescriptor`.
  `DayReadingsViewModel.uiStateFor`/`monthCompletionFor` derive from them — re-emit live, no VM change.
- **Stats / strips:** `GetReadingStatsUseCase` + `GetYearStripsUseCase` do the same;
  `DayReadingsViewModel.statsPanel` is a `combine` over them — N rows / N strips / `dayCount × N`
  denominators all follow the active descriptor.
- **Widget:** `TodayWidget.provideGlance` calls `getDayReadings()(today).first()`; the use case
  resolves `activePlanId` internally, so the widget reads the active plan automatically. D only fires
  `WidgetRefresher.refreshTodayWidget()` on confirm so the launcher re-renders promptly (the 30-min
  periodic backstop would otherwise lag).
- **Reminder / persistent notification:** content is built at fire/refresh time from
  `GetDayReadingsUseCase` (Sprint C), which reads the active plan — already correct, no D code.

So D's only production wiring is: (a) the selector reads/writes `selected_plan` + descriptor names;
(b) confirm fires the widget refresh. The switch's correctness is the use cases' liveness contract
(Room/DataStore invalidation), proven end-to-end by `PlanSwitchIntegrationTest`.

## Per-plan-progress-survives-a-switch — the proof

`PlanSwitchIntegrationTest` (in `data/plan/`) over the REAL `ActivePlanRepositoryImpl` + a per-plan
in-memory progress fake (keyed `(planId, date)` — the production store is per-plan from Sprint B):

1. Mark BC all-read on a day → switch to M'Cheyne (4 streams, fresh, day incomplete) → switch back to
   BC (3 streams, **still all-read**). One subscription throughout (live re-emit).
2. Mark M'Cheyne stream 1 only → BC sees NONE of it (isolation). The switch is non-destructive by
   construction: each plan's marks are a different `plan_id` partition (Sprint B's `(plan_id, …)` PK),
   untouched and restored on return. No copy/merge/clear runs on switch.

## Decisions & rationale

- **D-D-ALT-1 (OQ-7):** Settings-only, no first-run plan question, default Bible Companion (above).
- **The widget `@EntryPoint` is unchanged.** The task framed it as "resolve the active plan instead of
  the hard default" — there is NO hard default to replace; the entry point calls the use case, which
  already resolves `activePlanId`. D = the refresh trigger only. **Sprint E must not go looking for a
  phantom entry-point change.**
- **Plan display names come from the plan's own descriptor head** (`PlanDescriptor.name`), looked up by
  the selector via `ReadingPlanRepository.descriptor(id)` — never a second name table in the registry or
  the UI. The registry can't disagree with the plan about its name (mutation-pinned). The options list is
  memoized once (`cachedPlanOptions`).
- **The switch dialog writes nothing until confirm** (D-ALT-19). Selecting the active plan is a no-op
  (no dialog, no write). Dismiss writes nothing. The dialog is informational, zero data risk.
- **Selector placement:** top of Settings (the most consequential setting), above Theme. Off the daily
  path (G15). Reuses the exact S14 `SettingsDropdownRow`/`SelectableMenuItem` idiom + tags conventions.

## State of the codebase

- **New:** `ui/settings/PlanSelectorState.kt` — `PlanOption(id, name)`, `PlanSelectorUiState(options,
  activeId)` (+ `activeName`), `PendingPlanSwitch(toId, fromName, toName)`.
- **Changed:** `ui/settings/SettingsViewModel.kt` — injects `ActivePlanRepository`, `PlanRegistry`,
  `ReadingPlanRepository`; adds `planSelector`, `pendingPlanSwitch`, `loadPlanOptions()` (memoized),
  `onPlanSelected(id)`, `onPlanSwitchConfirmed()` (writes `selected_plan` + `WidgetRefresher`),
  `onPlanSwitchDismissed()`.
- **Changed:** `ui/settings/SettingsScreen.kt` — `SettingsRoute` collects/passes the selector state +
  callbacks; `SettingsScreen` gains `planSelector`/`pendingPlanSwitch` params + the three callbacks;
  new `PlanDropdown` composable; the plan section renders ABOVE Theme; the switch `AlertDialog` renders
  when `pendingPlanSwitch != null`.
- **Strings** (`res/values/strings.xml`): `plan_section_title` "Reading plan",
  `plan_dropdown_description` "Reading plan, %1$s", `plan_switch_dialog_title` "Switch to %1$s?",
  `plan_switch_dialog_body` "Your %1$s progress is saved — switch back any time and it'll be here.
  %2$s starts fresh.", `plan_switch_dialog_confirm` "Switch", `plan_switch_dialog_cancel` "Cancel".
- **Tests:** `SettingsViewModelTest` (+7: list-with-descriptor-names, live re-select, same-plan no-op,
  raise-dialog-writes-nothing, confirm-writes+refreshes, dismiss-writes-nothing) — now wires the REAL
  registry/plan-repo off `planAssetsDir` + a controllable active-plan fake. `SettingsScreenTest` (+6:
  row shows/speaks active, menu marks selected, pick reports callback, dialog copy+confirm, cancel
  routes dismiss, no-pending hides dialog). `PlanSwitchIntegrationTest` (NEW, 2). `AccessibilityGateTest`
  (count unchanged at 8 — the `plan-dropdown` ≥48dp + spoken assertion was added inline to the existing
  settings touch-target test).

## Verification

- **Full pipeline GREEN** (`spotlessCheck lintDebug assembleDebug testDebugUnitTest koverXmlReportAppDebug
  koverVerifyAppDebug`).
- **710 tests, 0 failures** (baseline 696 → +14 net). The four data/Room gates UNCHANGED:
  **ReadingPlanVerificationTest 11, McheynePlanVerificationTest 10, BibleTextVerificationTest 18,
  BibleDatabaseRoomOpenTest 5.** Nav-regression green (NavRegressionTest 5). a11y gate 8/8.
- **Kover 95.9%** on domain/data (floor 70%).
- **5 mutations killed**, each restored byte-identical (`diff -q` clean):
  1. confirm skips `setSelectedPlanId` → `SettingsViewModelTest` confirm test RED.
  2. confirm skips `WidgetRefresher` → same confirm test RED (asserts `refreshCount == 1`).
  3. `ActivePlanRepositoryImpl` ignores the stored id (always default) → `PlanSwitchIntegrationTest`
     (both) + `ActivePlanRepositoryTest` RED — proves the live switch is genuinely tested.
  4. `onPlanSelected` drops the same-plan guard → the no-op test RED.
  5. option name = id (not the descriptor) → the descriptor-name pins RED — proves the row/dialog show
     real plan names, never the raw id (no registry/plan name drift).

## Carryover & next goal — Alt Sprint E (chronological plan + hardening + release)

**What Sprint E needs to know:**

- **The selector + live switch are done and proven for N=3↔4.** E's single-stream (N=1) chronological
  plan exercises the *other* edge of the generalization; the selector lists whatever the registry
  declares, so adding `chronological` to `registry.json` + its asset + gate makes it appear in the
  dropdown for free — no selector code change.
- **No phantom widget `@EntryPoint` work.** It already resolves the active plan (above). Don't re-open it.
- **The S-D strings need owner tone sign-off** (`plan_section_title`, `plan_dropdown_description`, the
  four `plan_switch_dialog_*`) — fold into E's consolidated string/tone sign-off (SE-T5) alongside the
  M'Cheyne stream titles and the S-A..S-C copy.
- **Device-pass items for E (SE-T4):** the live switch on glass (day screen 3↔4 cards re-render); the
  widget showing M'Cheyne after a switch (the `WidgetRefresher` fire on a real launcher); the stats panel
  one-screen-fit at N=4 (the S15 45% cap + S18 budget were tuned to N=3 — confirm 4 strips/rows fit).

**Scope deliberately protected OUT of D** (queued, not absorbed):
- The chronological (N=1) plan + `ChronologicalPlanVerificationTest` + `chronological-rebuild` CI — Sprint E.
- A **day-screen plan-label** affordance beyond the Settings row — OQ-7-deferred (Priya's call); the
  Settings row already satisfies FR-ALT-6. Owner candidate for E after the device pass.
- The consolidated device pass at every N — Sprint E (SE-T4).

## Open questions & risks

- **R-ALT-2 (the N≠3 look, device-pass):** the day cards, stats strips/legend/a11y, and the widget tiers
  at N=4 are not JVM-provable. The DATA is N-correct (Sprint C gate); the *look* is E's device pass.
- **No new bugs/tech debt incurred.** No Room/schema/plan-data/manifest change, no new deps, no new
  permissions, no version bump. The `cachedPlanOptions` memo never invalidates (intentional — the
  registry/plan set is a build constant; a plan can't be added at runtime).

## Next sprint

next: sprint-alt-E-chronological-hardening-release
