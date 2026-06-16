# Alternate-Schedules Sprint A — plan-data model + active-plan spine + M'Cheyne asset & gate

**Track:** Alternate Reading Schedules (multi-plan). **Sprint:** A (the HARD GATE — Sprint-1 of multi-plan).
**Status:** DONE, uncommitted in the working tree (the main session independently verifies the gate +
commits). **Version:** untouched (no bump — Sprint A renders no second-plan UI).
**Plan:** [docs/EXECUTION_PLAN-alternate-schedules.md](../EXECUTION_PLAN-alternate-schedules.md) §3 (SA-T1…T11).
**Eng spec:** [docs/ENGINEERING_SPEC-alternate-schedules.md](../ENGINEERING_SPEC-alternate-schedules.md) (D-ALT-1…23).

## Goal outcome — MET

**A second trustworthy, gate-verified reading plan (M'Cheyne) now exists, and the app can name an
active plan.** A developer can load the M'Cheyne 4-stream schedule off-device on the JVM and prove it
correct against an independent second source; the schemaVersion-3 descriptor spine makes "render the
active plan's actual shape" a data read. No UI renders a second plan yet (that is Sprint C/D). The
Bible Companion's behavior is byte-for-byte unchanged for a user who never opens a selector.

## Current capability (the headline)

- **The M'Cheyne plan is bundled, faithful, and provably correct.** `assets/plans/mcheyne/plan.json`
  (schema 3, 365 days, Feb=28, 4 streams, **38 verse-windowed refs** — Ps 119 ×7 ×2, Ps 78 ×2 ×2,
  Luke 1 ×2 ×2, ~13 chapter-spanning ranges as multi-ref portions, Aug 8 `Jer 36,45`). Built
  reproducibly from the verse-faithful edginet source; gate-verified day-by-day against the
  independent Carson/TGC witness (**zero mismatches across all 365 days**); verse-aware coverage proven
  (OT verses once, Psalms+NT verses twice, every verse covered).
- **A plan declares its own shape.** schemaVersion-3 descriptor head (`planId`/`name`/`anchoring`/
  `dayCount`/`streams[]`) + a descriptor-driven loader that validates against the declaration, not the
  old `365`/`listOf(1,2,3)` constants.
- **The app can name an active plan.** `registry.json` + `PlanRegistry` (default pinned
  `bible_companion`), `selected_plan` DataStore key, `ActivePlanRepository` (absent ⇒ default,
  unknown id ⇒ default). No use case consumes it yet — stood up and proven.

## What landed (tickets)

| Ticket | What | Result |
|---|---|---|
| SA-T1 | schemaVersion-3 DTOs + `StreamDto` head | `PlanDto` gains `planId/name/anchoring/dayCount/streams`; `PortionDto`/`RefDto` unchanged (v2 body = strict subset). |
| SA-T2 | `PlanDescriptor`/`StreamDescriptor` + descriptor-driven loader | Validates anchoring==DATE, dayCount==365, Feb-29-absent, streams 1..N contiguous, every day's stream-set == declared, planId==expected (anti-drift). `loadFull()` parses descriptor+schedule once. 4-stream descriptor validates (N-agnostic). |
| SA-T3 | `registry.json` + `PlanRegistry` | Enumerates `bible_companion`+`mcheyne`; `DEFAULT_PLAN_ID="bible_companion"` build constant asserted == registry default. |
| SA-T4 | `PlanAssetSource(assetPath)` + per-plan `ReadingPlanRepository` | `portionsFor(planId,date)`+`descriptor(planId)`, per-plan caches. BC asset MOVED to `plans/bible_companion/plan.json`, re-authored v3 (days body **byte-identical**, proven). |
| SA-T5 | `selected_plan` key + `ActivePlanRepository` | `SettingsRepository.selectedPlanId` (absent⇒default); `ActivePlanRepositoryImpl` (selected→registry→descriptor; unknown⇒default). |
| SA-T6 | build test-input wiring | `planAssetsDir`=`src/main/assets` (covers plans/ AND bible/); `planAssets` input = whole assets tree so editing any asset re-runs the gates. |
| SA-T7 | M'Cheyne sourcing confirmation | Two distinct-lineage sources pinned by SHA; OQ-MC locked = classic 4-stream; coverage invariant stated; OQ-5 accepted. |
| SA-T8 | `tools/build_mcheyne_plan.py` → asset | Byte-deterministic; verse windows overlaid from edginet (NOT the corrupt bibleplan.org skeleton). |
| SA-T9 | `McheynePlanVerificationTest` — THE GATE | 10 tests; structural + 2nd-source day-by-day + verse-aware coverage + Ps-119 tiling + spanning-range fidelity. |
| SA-T10 | re-home + bump `ReadingPlanVerificationTest` | BC gate reads moved path, schema pin 2→3, head asserted; **11 day/coverage/window tests pass UNCHANGED** (parity). |
| SA-T11 | `mcheyne-rebuild` CI job + reconciliation log | Re-derives asset+fixture from pinned sources, byte-diff of zero; provenance/reconciliation in docs/data/README.md. |

## Decisions & rationale

- **OQ-MC = classic 4-stream M'Cheyne** (Family OT, Family Gospels, Secret Psalms/Prophets, Secret
  Epistles). The sourcing-doc recommendation; the owner authorized proceeding.
- **Canonical source = edginet (Edgington/Haslam, verse-faithful); 2nd witness = Carson/TGC.** Haslam's
  `mcheyne.info/calendar.pdf` is the SAME lineage as edginet (NOT a 2nd witness). bibleplan.org
  `plan.js` was REJECTED as a source — its verse-windowed days are corrupt (the documented Feb-28
  off-by-one). **Asset built from edginet; gate verifies against Carson/TGC.** Lineages checksum-distinct.
- **Cross-chapter ranges modeled as multi-ref portions** (e.g. `Ex 11:1-12:21` → whole `Ex 11` +
  `Ex 12:1-21`). A window covering a whole chapter (1..count) is collapsed to a plain whole-chapter ref
  (no verse fields) so the "windowed only where intended" audit holds.
- **Coverage invariant is VERSE-aware** (not chapter-aware): split chapters appear on >1 day, so the
  gate checks per-verse read counts — OT once, Psalms+NT twice, every verse covered.
- **Sprint A is descriptor-ADDITIVE.** The `Stream` enum is NOT retired here (Sprint C, D-ALT-5). The
  loader still maps BC portions through `Stream.fromNumber` (1..3); a 4-stream plan's **descriptor** is
  reachable but its domain `Portion` map is NOT (the repo caches descriptor+schedule separately so
  `descriptor("mcheyne")` works without hitting `Stream.fromNumber(4)`). The BC output map is byte-identical.
- **Two reconciled extraction artifacts** (NOT plan disagreements): Aug 29 (clipped leading "2") and
  Jun 28 (trailing column-bleed "2"), both confirmed against Carson/TGC, fixed in `reconcile()`.

## State of the codebase

- **New runtime classes** (`data/plan/`): `PlanAssetSource` (replaces `PlanJsonSource`, now removed),
  `PlanRegistry` (+`RegisteredPlan`), `ActivePlanRepository`(+Impl). **Changed:** `ReadingPlanAssetLoader`
  (descriptor-driven, `loadFull`/`descriptor`/`load` by `(assetPath, expectedPlanId)`),
  `ReadingPlanRepository`(+Impl) (per-plan, separate descriptor/schedule caches), `PlanDto` (v3 head +
  `StreamDto`), new `RegistryDto`. **New domain model:** `PlanDescriptor`/`StreamDescriptor`.
- **DI:** `DataModule.providePlanAssetSource` (path-parameterized `context.assets.open(assetPath)`);
  `RepositoryModule` binds `ActivePlanRepositoryImpl`. `SettingsRepository(Impl)` gains `selectedPlanId`
  (`selected_plan` key, absent⇒`PlanRegistry.DEFAULT_PLAN_ID`).
- **Consumer thread:** `GetDayReadingsUseCase` passes `PlanRegistry.DEFAULT_PLAN_ID` to `portionsFor`
  (additive; parity-preserving — the ONLY existing-use-case touch).
- **Assets:** `assets/plans/registry.json`, `assets/plans/bible_companion/plan.json` (moved from
  `assets/reading_plan.json`, re-authored v3, days byte-identical), `assets/plans/mcheyne/plan.json` (new).
- **Tools:** `tools/build_mcheyne_plan.py` (edginet → asset), `tools/extract_mcheyne_second.py`
  (Carson/TGC → fixture). `extract_primary.py`/`extract_antipas.py` updated to emit v3 heads + the new
  BC path. `tools/requirements.txt` unchanged (stdlib + `pdftotext` only).
- **Tests:** new `McheynePlanVerificationTest` (10), `PlanRegistryTest` (3), `ActivePlanRepositoryTest`
  (3); expanded `ReadingPlanAssetLoaderValidationTest` (15), `ReadingPlanRepositoryTest` (6),
  `SettingsRepositoryImplTest` (+2). New committed fixture `app/src/test/resources/plans/mcheyne/plan_verify.json`.
  The BC 2nd-source fixture `reading_plan_verify.json` re-authored to a v3 head (days byte-identical).
- **CI:** `.github/workflows/ci.yml` gains the `mcheyne-rebuild` job (mirrors `data-rebuild`).
- **Build:** `app/build.gradle.kts` `planAssetsDir`/`planAssets` cover the whole `src/main/assets` tree.

## Verification

- **Full pipeline GREEN from clean:** `spotlessCheck lintDebug assembleDebug testDebugUnitTest
  koverXmlReportAppDebug koverVerifyAppDebug` → BUILD SUCCESSFUL.
- **677 tests, 0 failures** (baseline 651 → +26 net). The three standing data/Room gates intact:
  **ReadingPlanVerificationTest 11 (UNCHANGED parity)**, **McheynePlanVerificationTest 10 (NEW)**,
  BibleTextVerificationTest 18, BibleDatabaseRoomOpenTest 5.
- **Kover 95.7%** on domain/data (floor 70%).
- **4 mutations killed** (each restored in place): (1) dropped a Ps-119 window from the asset → 3 gate
  tests red; (2) reduced a 4-stream day to 3 → 3 red; (3) coverage double-count (Secret Matthew 1→2) →
  3 red; (4) gate-code coverage expectation made wrong → 1 red (proves the coverage check is load-bearing).
- **Byte-deterministic rebuild confirmed:** `build_mcheyne_plan.py` and `extract_mcheyne_second.py` each
  reproduce their committed file byte-identically; the BC days body is byte-identical pre/post move.

## Carryover & next goal — Sprint B (the migration)

**Next goal: per-plan progress Room migration** (high-risk, isolated). `reading_progress` gains
`plan_id TEXT NOT NULL`; PK → `(plan_id, dateEpochDay, stream)`; `ProgressDatabase` v1→v2 via a
hand-written zero-loss `MIGRATION_1_2` stamping every existing row `bible_companion`; exported `2.json`;
`MigrationTestHelper` zero-loss test + "no perceptible migration for the default user" test; every
`ProgressRepository`/DAO method gains a `planId` (defaulted to the active plan). **No UI change.**

**Sprint B needs to know:**
- The `bible_companion` stamp constant is `PlanRegistry.DEFAULT_PLAN_ID` — SB-T1's shared constant must
  equal it (the anti-drift pin is already half-set; assert `== PlanRegistry.DEFAULT_PLAN_ID`).
- `ActivePlanRepository` (from A) is the active-plan default source for SB-T5's repo-method defaults.
- New test dep needed: `androidx.room:room-testing` (test scope, via the catalog) — NOT added this sprint.
- `fallbackToDestructiveMigration` stays OFF (D-V3-15 rule — never destroy user data).
- `hasAnyMarks()` stays GLOBAL (any plan) and the tracking-start date stays global (D-ALT-15).

**Scope deliberately protected OUT of A** (queued, not absorbed): retiring the `Stream` enum +
parameterizing `DayCompletionClassifier` + N-stream UI (Sprint C); the selector/switch-dialog/active-plan
threading/widget refresh (Sprint D); the chronological plan (Sprint E).

## Open questions & risks

- **M'Cheyne stream titles** ("Family — Old Testament" etc.) await owner tone sign-off (Sprint E / Maya).
- **Sprint A loads M'Cheyne's BODY through no live path** — the domain `Portion` map for a 4-stream plan
  cannot be built until the `Stream` enum retires (Sprint C). If anything tries `portionsFor("mcheyne",…)`
  before C, it throws `invalid stream number 4` (by design — guarded; the live app only loads the default).
- **The stale `reading_plan.json`** is `git rm`-pending in the working tree (the move). The main session's
  commit must include the deletion of `app/src/main/assets/reading_plan.json`.
- No new permissions, no INTERNET, zero net-new runtime deps. The two M'Cheyne sources are build-time only.

## Next sprint

next: sprint-alt-B-progress-migration
