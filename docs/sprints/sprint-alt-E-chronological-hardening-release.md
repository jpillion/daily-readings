# Alternate-Schedules Sprint E — chronological GO/NO-GO + hardening + release readiness

**Track:** Alternate Reading Schedules (multi-plan). **Sprint:** E (the FINAL sprint — the chronological
single-stream proof, hardening, and release assembly). **Status:** DONE, uncommitted in the working
tree (only `docs/data/README.md` + `distribution/whatsnew/whatsnew-en-US` changed — NO code/asset/test/
version change; the main session/owner verifies, commits, and cuts the release). **Version:** untouched
at 1.4.3/10403 — **recommended bump 1.5.0/10500**, NOT applied.
**Plan:** [docs/EXECUTION_PLAN-alternate-schedules.md](../EXECUTION_PLAN-alternate-schedules.md) §3 (SE sketch), §4.2 (Sprint E gate).
**Eng spec:** [docs/ENGINEERING_SPEC-alternate-schedules.md](../ENGINEERING_SPEC-alternate-schedules.md) §7.2 (D-ALT-21).
**Predecessors:** [sprint-alt-A-plan-foundation.md](sprint-alt-A-plan-foundation.md),
[sprint-alt-B-progress-migration.md](sprint-alt-B-progress-migration.md), Sprint C (committed `7e6e9a6`,
no separate doc), [sprint-alt-D-plan-selector-integration.md](sprint-alt-D-plan-selector-integration.md).

## Goal outcome — MET

**The alternate-schedules epic is COMPLETE and release-ready** (pending the owner's device pass +
string sign-offs). It ships valuably with **TWO** gate-verified, live-switchable, per-plan-progress
plans — **Bible Companion** (flagship default) + the classic **4-stream M'Cheyne**. **Track 1
(chronological) is declared NO-GO** — the correct, principled honesty-gate outcome (D-ALT-21), not a
failure: no *named* chronological ordering has two genuinely-independent agreeing sources. **Track 2
(hardening + release readiness) landed in full** and proceeded regardless of Track 1.

## Current capability (the headline)

Nothing in the *software* changed this sprint (Track 1 added no chronological asset; Track 2 was a
verification + audit + documentation pass). The capability statement is the epic's, now hardened and
release-confirmed: **a user picks Bible Companion or M'Cheyne in Settings and the WHOLE app — day
cards (3↔4), stats (3↔4 strips/rows/denominators), year strips, picker dots, the launcher widget, and
the reminder/persistent-tray content — follows the chosen plan live; switching back restores the other
plan's view AND its progress intact; a Bible Companion reader who never opens the selector sees the app
byte-for-byte as before.** This sprint proved that surface is releasable: offline identity holds (no
INTERNET), the bundle is within budget, the new plan-load paths are StrictMode-clean, and the data
gates are green.

---

## Track 1 — Chronological plan: **NO-GO (do not ship)** — D-ALT-21 honesty gate

**The full finding (sources, independence analysis, the decision rule) is in
[docs/data/README.md](../data/README.md) → "Chronological plan — sourcing investigation … NO-GO".**
Summary:

- A *named, published, date-anchored 365-day* chronological plan exists — **Blue Letter Bible
  "Chronological Plan"** (Nathan Gammie, blueletterbible.org; full text extracted from the official
  PDF: Day 1 Genesis 1-3 … Day 365 Revelation 19-22). BLB is already the app's flagship reading
  destination, so it was the natural canonical candidate.
- **But it has no genuinely independent second witness.** Every second source for the BLB ordering is a
  verbatim **re-host of BLB's own PDF** (the re-mirror trap — the Sprint-1 pricejh==christadelphia and
  the M'Cheyne Haslam→Edgington lesson). One lineage re-hosted is ONE witness.
- The only other wide chronological lineage ("2020 / Bible Study Tools", on biblestudytools.com + many
  church PDFs) is **anonymous and untraceable** (no named original publisher to cite as canonical IP)
  AND **genuinely DISAGREES** with BLB on real editorial choices — cross-confirmed from the live PDFs:

  | Day | BLB (Nathan Gammie) | "2020/BST" lineage | Difference |
  |---|---|---|---|
  | 104 | 1 Samuel 21-24; **Psalm 91** | 1 Samuel 21-24 (no Psalm 91) | editorial Psalm placement |
  | 121 | 2 Samuel 5; 1 Chronicles 11-12 | 2 Samuel **5:1-10** | verse split |
  | 150 | **Psalm 119** (whole) | Psalm **119:1-88** | verse split |
  | 200 | 2 Kings 18; 2 Chron 29-31; Ps 48 | 2 Kings **18:1-8**; 2 Chron 29-31; Ps 48 | verse split |
  | 209 | 2 Kings 19; Pss 46, 80, 135 | 2 Kings **18:9-37; 19:1-37**; Pss 46, 80, 135 | verse split |

- A chronological ordering **IS the IP**, and two "chronological" sources from different publishers
  disagreeing is a **real editorial difference, NOT second-source verification** — exactly the
  contested-ordering risk D-ALT-21 names. Other named plans (Guthrie/CSB; Tyndale's *NLT One Year
  Chronological Bible*; Bible Project; Heartlight) each differ again, confirming the contestation is
  structural, not noise.

**Decision (D-ALT-21):** no candidate satisfies "a specific named plan with a real independent witness
that agrees day-by-day," so **chronological does NOT ship.** No `assets/plans/chronological/` asset, no
`registry.json` entry, no `tools/build_chronological_*.py`, no `ChronologicalPlanVerificationTest`, no
`chronological-rebuild` CI job were created. **The four standing data gates are UNCHANGED.** A
do-not-ship with a clear recorded reason is the success the honesty gate intends.

**If ever revisited (future, owner-gated):** either find a publisher table whose ordering a *second
house* independently derives and agrees with, OR the owner *designates* BLB's plan canonical and signs
off on a **rigorous single-source structural gate** (whole-Bible coverage, every chapter exactly once,
the publisher's order pinned) as a deliberate FR-ALT-3 relaxation for that one plan, recorded as an
accepted risk. Until then: NO-GO. (Recorded in data/README.md.)

---

## Track 2 — Hardening + release readiness (landed)

### JVM-provable hardening — all green

| Check | Result |
|---|---|
| **No INTERNET / no new permissions** | Merged RELEASE manifest carries exactly **6** perms — `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, the `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` signature perm. **NO `INTERNET`.** All 6 are pre-alt-schedules library merges (Glance/WorkManager + the S12 reminders); the epic added **zero** new permissions. Offline identity (NFR-V3-A) holds. |
| **Four data gates green** | ReadingPlanVerificationTest **11**, McheynePlanVerificationTest **10**, BibleTextVerificationTest **18**, BibleDatabaseRoomOpenTest **5** — UNCHANGED (no chronological gate, since NO-GO). |
| **Bundle-size check** | `bundleRelease` clean = **8,120,930 B (8.12 MB) AAB** < the 12,000,000 B CI ceiling (D-V3-20). Both plan assets packaged: `bible_companion/plan.json` 171,844 B, `mcheyne/plan.json` 203,388 B, `registry.json` 228 B (uncompressed; ~8–9 KB gzipped each). M'Cheyne adds ~9 KB compressed to the bundle (dominated by the 1.97 MB compressed bible.db). ~3.88 MB headroom. |
| **a11y gate** | `AccessibilityGateTest` **8/8** — includes the `plan-dropdown` ≥48dp + spoken "Reading plan, <value>" assertion (Sprint D) at the selector. The N-stream surfaces inherit the no-guilt-copy + per-control touch-target pins. |
| **StrictMode / off-main review of the NEW plan-load paths** | CLEAN (detail below). |

### StrictMode / off-main review (the platform read)

StrictMode in debug is `ThreadPolicy.detectAll().penaltyLog()` (`DailyReadingsApp`). The new plan-load
paths all read the bundled asset (`context.assets.open(assetPath).bufferedReader().use { readText() }`,
in `DataModule.providePlanAssetSource`) **off the main thread** — and, critically, **multi-plan changed
only the asset PATH, not the threading** (the pre-alt single BC plan used the identical
`combine`-over-a-Room-Flow structure and was StrictMode-clean since the S2 scaffold; the new path
inherits that cleanliness):

- **Day-screen path** (`DayReadingsViewModel.uiStateFor` → `GetDayReadingsUseCase`): the blocking
  `portionsFor(...)` runs **inside a `combine { descriptor, readStreams -> … }`** where one upstream is
  `progressRepository.streamsRead(...)` — a Room `@Query`-backed Flow. Room dispatches its Flow
  emissions on its background query executor; the `combine` transform runs in that upstream emission
  context (there is NO `flowOn` re-confining it to Main; `stateIn(viewModelScope, …)` governs only the
  terminal state container, not where the upstream transform executes). → **off-main.**
- **Settings plan-name path** (`SettingsViewModel.planSelector` → `loadPlanOptions()` →
  `readingPlanRepository.descriptor(id).name`): runs inside `activePlanRepository.activePlanId
  .map { … loadPlanOptions() }`, whose root upstream is `settingsRepository.selectedPlanId` — a
  **DataStore-backed Flow** that emits on `Dispatchers.IO` (DataStore's default). No `flowOn` back to
  Main. → **off-main.** (Also memoized once via `cachedPlanOptions`.)
- **Active-descriptor path** (`ActivePlanRepositoryImpl.activeDescriptor = activePlanId.map { descriptor(id) }`):
  same DataStore-Flow IO context. → **off-main.**
- **Single-flight + cold-start budget:** `ReadingPlanRepositoryImpl` parses each plan's
  descriptor+schedule **per plan**, single-flight under a `Mutex`, memoized. **Only the ACTIVE plan's
  asset is parsed** — the default (Bible Companion) user never parses M'Cheyne, so the M2 cold-start
  budget is unchanged.

**Verdict: no plan-load path reads the asset on the main thread in production; StrictMode-clean; the
multi-plan change is path-only, inheriting the pre-existing BC cleanliness.** (Real on-glass StrictMode
confirmation is in the consolidated device pass below, as a sanity check — but the reasoning above is
load-bearing and JVM/code-provable.)

### Full pipeline + tests

- **Full pipeline GREEN:** `spotlessCheck lintDebug assembleDebug testDebugUnitTest koverXmlReportAppDebug
  koverVerifyAppDebug` → BUILD SUCCESSFUL; `bundleRelease` clean.
- **710 tests, 0 failures** (unchanged from Sprint D — no tests added; chronological NO-GO adds no gate).
- **Kover 95.9%** on domain/data (floor 70%).
- **No mutations to verify this sprint** — no new production code was written (the chronological gate,
  the only candidate for new mutation-verification, was not built per D-ALT-21). The existing alt-schedules
  mutation evidence stands in the A/B/C/D handoffs (A: 4, B: 6, C: in the CLAUDE.md C entry, D: 5).

---

## Consolidated owner device-pass checklist (owner-run; NOT JVM-provable)

Every N≠3 / on-glass item collected across Alt Sprints B/C/D into one runnable list. Run on a real
device (the P7P primary). **The DATA is N-correct and gate-proven; only the LOOK and the live behaviour
need a human eye.**

**A. The live plan switch (Settings → Reading plan)**
1. Default install shows **Bible Companion**, 3 cards — identical to the current app (parity).
2. Open Settings → "Reading plan" → pick **M'Cheyne** → the switch dialog names both plans → confirm.
3. The day screen re-renders to **4 cards** (the M'Cheyne streams) live, no restart.
4. Switch back to Bible Companion → 3 cards return AND every mark made before the switch is intact
   (non-destructive per-plan progress).

**B. Day screen one-screen-fit at N=4 (R-ALT-2, the budget was tuned to N=3)**
5. On M'Cheyne, confirm all **4 reading cards fit on one screen at default font WITH the ~80dp bottom
   nav bar** (Schedule|Bible) — no scroll at default font. Note behaviour at the largest font scale.
6. The 4 cards read as a calm list (M'Cheyne stream titles "Family — Old Testament" etc. shown);
   marking each of the 4 checkboxes works; the whole-day widget seam still marks all 4.

**C. Stats / year strips at N=4**
7. On M'Cheyne, the stats panel shows **4 strips + 4 per-stream rows + 4-stream denominators**
   (dayCount×4 = 1,460 year total; 365 per stream). Confirm it still fits the S15 45% cap / S18 one-screen
   budget (tuned to 3 strips — does the 4th strip overflow?).
8. The strip legend + a11y summaries read correctly at 4 strips; the no-guilt copy ban holds (no
   "missed" in copy or contentDescriptions except the legend's "Missed"/"Completed" per D-S20-1).

**D. Widget tiers at N=4 (D-ALT-10, the row-count-aware policy)**
9. Place the widget on the launcher while M'Cheyne is active; at **every size** (TINY 1x1 → LARGE) the
   row-count-aware tier policy renders the **4** readings legibly (or degrades to abbreviated refs /
   drops the stream title sooner at high N, per D-ALT-10) — never a crushed 4-row TINY.
10. The S9 invariants hold at N=4: Feb-29/error states, the single tap target, read/unread marks,
    system-theme follow, full-name TalkBack.
11. **The widget snaps to the switched plan:** with the widget on the launcher, switch plans in the app
    → the widget re-renders to the new plan's readings (the `WidgetRefresher` fire on confirm).

**E. The real migrated-history upgrade (R-ALT-1, the one un-JVM-provable migration item)**
12. Install the LAST pre-alt build (schema v1 progress), make several marks across days/years, then
    upgrade to this build (schema v2) → every mark survives, stats/strips/streaks identical, no
    perceptible migration (the `MIGRATION_1_2` zero-loss + no-perceptible tests cover the logic; this is
    the on-glass confirmation).

**F. StrictMode sanity (debug build)**
13. Run the debug build with StrictMode logging on; switch plans + open stats + scroll the day pager →
    confirm **no disk-read-on-main StrictMode violation** is logged for the plan-load paths.

---

## Strings — full alt-schedules tone sign-off table (owner)

ALL new user-visible alt-schedules strings, for the owner's tone sign-off in one place (the S-A..S-D
handoffs deferred these to E). None are blocking the *code*; they block the *release presentation*.

| Source | Key / location | Current text | Note |
|---|---|---|---|
| Plan name (BC) | `bible_companion/plan.json` `name` | **Bible Companion** | unchanged (flagship); shown in the selector. |
| Plan name (M'Cheyne) | `mcheyne/plan.json` `name` | **M'Cheyne** | the selector + switch-dialog name. Confirm the apostrophe form. |
| M'Cheyne stream 1 | `mcheyne/plan.json` streams[0].title | **Family — Old Testament** | em dash. Day card / stats row / widget / a11y. |
| M'Cheyne stream 2 | streams[1].title | **Family — Gospels** | |
| M'Cheyne stream 3 | streams[2].title | **Secret — Psalms & Prophets** | "Secret" is M'Cheyne's own term (Matthew 6:6, private devotion). Confirm it reads well to a modern user vs. e.g. "Private". |
| M'Cheyne stream 4 | streams[3].title | **Secret — Epistles** | |
| Selector section title | `strings.xml` `plan_section_title` | **Reading plan** | top of Settings. |
| Selector row a11y | `plan_dropdown_description` | **Reading plan, %1$s** | spoken label (value = active plan name). |
| Switch dialog title | `plan_switch_dialog_title` | **Switch to %1$s?** | |
| Switch dialog body | `plan_switch_dialog_body` | **Your %1$s progress is saved — switch back any time and it'll be here. %2$s starts fresh.** | the non-destructive-switch explanation (D-ALT-19). The load-bearing reassurance copy — review tone. |
| Switch dialog confirm | `plan_switch_dialog_confirm` | **Switch** | |
| Switch dialog cancel | `plan_switch_dialog_cancel` | **Cancel** | |

(The BC stream titles "Law & History" / "Psalms & Prophecy" / "New Testament" are unchanged and already
shipped — not part of this sign-off.)

---

## Release readiness

- **Recommended version bump: 1.5.0 / 10500** — a MINOR bump per D-S9-3 (`MAJOR*10000 + MINOR*100 + PATCH`),
  appropriate for a significant new user-facing feature (multi-plan selection). **NOT applied** — current
  is 1.4.3/10403; the main session/owner bumps `versionName`/`versionCode` in `app/build.gradle.kts`,
  tags, and runs the closed-track rollout.
- **`bundleRelease` builds clean** with both plan assets + the registry (verified: 8.12 MB AAB, plan
  assets present in `base/assets/plans/`, within the 12 MB CI gate).
- **whatsnew draft updated** for the alt-schedules feature (`distribution/whatsnew/whatsnew-en-US`):
  the plan selector, per-plan progress, and the stats/strips/widget following the chosen plan (283 B,
  under Play's limit). Owner to finalize tone alongside the strings table.
- **Blocking the release:** (1) the owner's consolidated device pass (above); (2) the strings tone
  sign-off (above); then (3) the 1.5.0/10500 bump + the closed-track tag-to-Play rollout.

---

## State of the codebase (what changed this sprint)

- **`docs/data/README.md`** — appended the "Chronological plan — sourcing investigation … NO-GO"
  section (sources, independence analysis, the divergence table, the decision rule, future-revisit note).
- **`distribution/whatsnew/whatsnew-en-US`** — replaced with the alt-schedules release note.
- **`CLAUDE.md`** — added the Alt Sprint E "Current status" entry; marked the epic DONE; "Next up" = the
  alt-schedules release cut.
- **`docs/BACKLOG.md`** — marked **#3 (Alternate reading schedules) ✅ Done (Alt Sprints A–E)**.
- **NO code, asset, test, schema, manifest, dependency, or version change.** The four data gates are
  byte-for-byte untouched; 710 tests still green.

## Carryover & next goal

**Next goal: the alt-schedules RELEASE CUT** (owner + main session) — run the device pass, the string
sign-offs, then bump to **1.5.0/10500**, tag, and roll out on the closed track. No further engineering
sprint is queued for the alt-schedules epic; it is complete with two plans.

**Scope deliberately NOT done (recorded, not absorbed):**
- The **chronological plan** — NO-GO (D-ALT-21), recorded; not shipped. Revisitable only under the
  future-work conditions in data/README.md (owner-gated).
- A **day-screen plan-label** affordance beyond the Settings row — OQ-7-deferred (Priya's call); the
  Settings selector row already satisfies FR-ALT-6. Owner candidate after the device pass if wanted.
- **Localization** of plan names / stream titles (English-only, V1/V3 precedent) — out of scope.
- Progress-anchored / non-365-day / custom-imported plans, multiple-plans-at-once — deferred (PRD §10),
  out of this whole epic.

## Open questions & risks

- **R-ALT-2 (the N≠3 look, device-pass):** day cards at N=4 + one-screen-fit with the bottom bar, stats
  4-strip fit, widget tiers at N=4 — DATA is gate-correct; the LOOK is the owner device pass (above).
- **R-ALT-1 (migration on a real upgrade):** the JVM gate (`MigrationTestHelper` + no-perceptible test)
  covers the logic; the on-glass migrated-history upgrade is device-pass item E.
- **No new bugs / tech debt incurred this sprint** (a verification + documentation pass; no code touched).
  No new deps, no new permissions, no INTERNET, no Room/schema/manifest/version change.

## Next sprint

next: (none — alternate-schedules epic COMPLETE; next is the owner-run release cut, not an engineering
sprint. The next *engineering* session would pick from the backlog, e.g. #1 downloadable catalog / #2
audio, owner-scheduled.)
