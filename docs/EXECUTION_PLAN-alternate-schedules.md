# Daily Reading Planner — Execution Plan: Alternate Reading Schedules (multi-plan support)

> **Owner:** Morgan (Engineering Manager) · **Status:** Ready to execute (Sprint A first) · **Last updated:** 2026-06-16
> **Inputs (decided upstream, not re-decided here):**
> [features/alternate-reading-schedules.md](features/alternate-reading-schedules.md) (Maya's PRD + the **owner decisions resolved 2026-06-16** — curated set; per-plan progress; date-anchored 365-day; M'Cheyne first, then a named chronological plan) ·
> [ENGINEERING_SPEC-alternate-schedules.md](ENGINEERING_SPEC-alternate-schedules.md) (Diego's eng spec — `D-ALT-1…23`, the 12 de-baking touch points, the migration risk, the recommended A–E breakdown — PRIMARY input) ·
> [data/mcheyne-sourcing.md](data/mcheyne-sourcing.md) (the M'Cheyne sourcing research — **GO**; canonical Edgington/Haslam + independent Carson/TGC witness; **4 streams**; **~24 verse-range days**; the build path) ·
> [EXECUTION_PLAN.md](EXECUTION_PLAN.md) / [EXECUTION_PLAN-v3.md](EXECUTION_PLAN-v3.md) (structure/voice/ticket format this matches) · [CLAUDE.md](../CLAUDE.md) (current status).
>
> This doc owns **sequencing and decomposition**. The product (PRD) and the architecture (ESpec,
> `D-ALT-*`) are settled; I do not re-decide them. My job: order the work into dependency-correct
> sprints, surface the owner checkpoints that gate work before it starts, and break the immediate
> sprints into executable subtasks. **Progress is measured only in working software** — every
> sprint states the new capability it unlocks. The locked owner decisions are held exactly:
> curated set (not a store), per-plan progress, date-anchored 365-day only, M'Cheyne → chronological.
> Do not smuggle in progress-anchored plans, non-365-day lengths, a general plan store, custom/
> imported plans, or multiple-plans-at-once — those are deferred (PRD §10) and out of this cut.

---

## 1. Up-front sequencing principles

### 1.1 The critical path — what gates what

There is **one ordered chain** the rest of the feature hangs off, set by **dependency + data-risk**,
exactly as V3 put its data foundation before its reader:

> **plan-data model + registry + active-plan spine + the M'Cheyne trusted-data asset & gate (Sprint A)
> → the per-plan progress Room migration (Sprint B)
> → the N-stream UI generalization that reads the per-plan store (Sprint C)
> → the plan selector + whole-app integration (Sprint D)
> → the chronological plan + hardening + release (Sprint E).**

Three hard constraints, taken verbatim from Diego's spec (§5.4, §10 sequencing note), bound the order:

1. **A is strictly first.** No second plan exists, nothing renders a non-Bible-Companion shape, and
   nothing can be *verified* against a second plan, until the `schemaVersion: 3` descriptor + the
   registry + `ActivePlanRepository` spine land **and** the M'Cheyne asset passes its gate. A is the
   **Sprint-1/Sprint-A of multi-plan**: a hard data gate, like `ReadingPlanVerificationTest` and
   `BibleTextVerificationTest` before it.
2. **B (the migration) must land BEFORE C.** C's N-stream UI reads per-plan progress; the per-plan
   progress store must be correct, exported, and migration-tested *first*. **Do not parallelize the
   migration against the UI generalization** — the UI reads the store; the store must be bulletproof
   before anything depends on it. B is the only place existing user data is rewritten, so it is
   isolated in its own sprint and gets the heaviest test rigor.
3. **C and D are peers downstream of B** and can overlap across people once B is green (C = the
   N-stream rendering through the one classifier seam; D = the selector + active-plan wiring through
   every surface incl. the widget). **E is last** and is mostly a *data* project (the contested-
   ordering chronological sourcing), gated on its second-source availability (OQ-5), not on more code.

```
A ──▶ B ──▶ { C , D } ──▶ E
│           (peers, overlap once B is green)
└─ HARD GATE: M'Cheyne verification green + Bible Companion gate green UNCHANGED
   B HARD GATE: zero-loss MIGRATION_1_2 test + the "no perceptible migration for the default user" test
```

### 1.2 The cross-cutting acceptance gate — Bible Companion parity (every sprint)

**Diego's §1.1 non-negotiable invariant is the acceptance bar on *every* sprint, not just A:**

> The existing test suite — the **651 tests** at the start of this epic, incl. the three standing
> data/Room gates (plan = **11**, `BibleTextVerificationTest` = **18**, `BibleDatabaseRoomOpenTest`
> = **5**) and every day/stats/widget/reminder pin — must pass **UNCHANGED** with the active plan
> defaulted to `bible_companion`. A test that goes red on a no-op-for-the-default-user change is a
> **design defect, not a test to update** (additive signatures take the Bible-Companion id by
> default so existing pins keep their meaning). The Bible Companion reader who never opens the
> selector must get the current app, byte-for-byte.

This is testable and is the headline acceptance criterion of each sprint below. Where a sprint changes
a signature (`ProgressRepository` gains `planId`; `ReadingFormatter.streamTitle` retires; the
classifier's `STREAM_COUNT` becomes a parameter), the parity gate is the proof the change was additive.

### 1.3 What the owner OQs do and do not block

The remaining owner calls (OQ-5, OQ-7, OQ-MC) are **not on the critical path** and **do not block A,
B, or C** (ESpec §11). OQ-5 (per-plan data burden + second-source availability) is re-confirmed at
*each plan's sourcing* — it gates the M'Cheyne **asset** (A's data track) and the chronological
**asset** (E), never the schema/code. OQ-7 (selector placement / first-run) gates only the **D
presentation**. OQ-MC (M'Cheyne 2-stream vs 4-stream form) is a data+product call at M'Cheyne
sourcing — **the sourcing research recommends the classic 4-stream date-anchored form**
(mcheyne-sourcing.md); the code is N-agnostic, so it gates only the M'Cheyne asset. See §6 for the
checkpoint schedule.

### 1.4 Decision index (carried from the ESpec — not re-decided)

All twenty-three `D-ALT-*` decisions (ESpec §9) are **settled and final** for this plan. The ones
that most shape sequencing/tickets: **D-ALT-2/3** (schemaVersion 3, additive head, v2 body a strict
subset), **D-ALT-4/5/6** (N + titles from the descriptor; retire the `Stream` enum; the classifier
seam is parameterized never forked), **D-ALT-12/13/14** (the `plan_id` PK change + the zero-loss
`MIGRATION_1_2` + the migration-test rigor), **D-ALT-16/17** (the `selected_plan` key +
`ActivePlanRepository`), **D-ALT-20** (per-plan verification gate), **D-ALT-21** (a *specific named*
chronological plan with a real second witness, or do-not-ship). Tickets cite the decision they
implement; engineers receive the decision verbatim, never an invitation to relitigate it.

### 1.5 Team roster (assign tickets by name)

| Name | Agent | Owns in this plan |
|---|---|---|
| **Diego** | `android-architect` | The data-model spine (`PlanDescriptor`/`StreamDescriptor`, `PlanRegistry`, the generalized loader, `ActivePlanRepository`), the schema-v3 design, the `Stream`-enum retirement (compiler-driven), the classifier parameterization, final design word. |
| **Avery** | `android-platform-senior` | The `ProgressDatabase` v1→v2 migration (`MIGRATION_1_2`, exported schema, the `MigrationTestHelper` test), the per-plan DAO/repo, the widget `@EntryPoint` active-plan wiring, StrictMode/off-main. |
| **Priya** | `android-ui-senior` | The N-stream UI look (day cards at N≠3, stats strips/legend/a11y at variable rows, the row-count-aware widget tier policy), the selector visuals (S14 idiom), the switch-explanation dialog, faithful-presentation review. |
| **Sam** | `android-feature-eng` | Scoped integration wiring within established patterns: threading the active plan through use cases/routes, the selector row, the switch dialog wiring, the first-run plan question (if OQ-7 says so). |
| **Riley** | `android-qa-eng` | The **M'Cheyne verification gate** (`McheynePlanVerificationTest`), the second-source fixture + reconciliation, the generalized plan gate, the **migration test**, the parity-regression discipline, mutation verification. |
| **Jordan** | `devops-eng` | The M'Cheyne `mcheyne-rebuild` CI job (re-derive + byte-diff, mirroring `data-rebuild`), the asset-path move in the build (the `planAssetsDir` / test-input wiring), the bundle-size check, version bump + rollout. |
| _Maya / Owner_ | `senior-pm` | Resolves OQ-5 (per-plan data burden, per plan), OQ-7 (selector placement/first-run), OQ-MC (M'Cheyne form); the switch-explanation + selector + stream-title tone sign-offs. |

---

## 2. Sprint sequence overview

Ordered by the §1.1 critical path. Each sprint has **one outcome goal**, states the **new capability
it unlocks**, and is an independently-shippable, green increment. No sprint closes unless the project
builds, its tests pass, **and the Bible-Companion-parity regression gate (§1.2) is green** (§4).

| # | Sprint | Outcome goal (the deliverable) | New capability unlocked | Key owners | Depends on | The hard gate |
|---|---|---|---|---|---|---|
| **A** | **Plan-data model + active-plan spine + M'Cheyne asset & gate** *(the data foundation; HARD GATE)* | `schemaVersion: 3` (descriptor head: `planId`/`name`/`anchoring`/`dayCount`/`streams[]`, D-ALT-2/3); `registry.json` + `PlanRegistry`; the Bible Companion asset **moved** to `assets/plans/bible_companion/plan.json` + re-authored v3 (reading bytes unchanged); `PlanDescriptor`/`StreamDescriptor`; the generalized `ReadingPlanAssetLoader` (validate against `dayCount`/`streams`, not constants); `ActivePlanRepository` + the `selected_plan` key (default `bible_companion`, D-ALT-16/17); **the M'Cheyne asset** (4 streams, ~24 verse-range days, two independent sources, reconciliation log) + **`McheynePlanVerificationTest`** + the `mcheyne-rebuild` CI job (D-ALT-20). | **A second trustworthy, gate-verified plan exists, and the app can name an active plan.** A developer can load the M'Cheyne schedule off-device on the JVM and prove it correct against a second source; the descriptor spine makes "render the active plan's actual shape" a data read. Nothing downstream renders a second plan until this lands. | Riley (gate), Diego (model/loader/spine), Jordan (CI/asset move), Avery (DataStore key) | — | **M'Cheyne verification gate green (Sprint-1 standard) AND the Bible Companion plan gate green UNCHANGED.** |
| **B** | **Per-plan progress Room migration** *(high-risk, isolated)* | `reading_progress` gains `plan_id TEXT NOT NULL`; PK → `(plan_id, dateEpochDay, stream)` (D-ALT-12); `ProgressDatabase` v1→v2; hand-written `MIGRATION_1_2` stamping every existing row `bible_companion` losslessly (D-ALT-13); exported schema `2.json` checked in; the `MigrationTestHelper` zero-loss test + the end-to-end "no migration the default user can perceive" test (D-ALT-14); every `ProgressRepository`/DAO method gains a `planId` (defaulted to the active plan). **No UI change.** | **The progress spine is per-plan and proven lossless** — an upgrading Bible Companion user keeps every mark, sees no migration, and the store can now isolate a second plan's marks. The plumbing is correct before anything renders against it. | Avery (lead), Riley (migration test) | A | **The `MigrationTestHelper` 1→2 zero-loss test + the "no perceptible migration for the default user" test green; exported `2.json` checked in; `fallbackToDestructiveMigration` NOT enabled.** |
| **C** | **N-stream UI generalization** *(schedule / stats / strips / widget)* | Retire the `Stream` enum (D-ALT-5, compiler-driven); parameterize the classifier (`streamCount: Int`, D-ALT-6); generalize the stats denominators (`dayCount × N`, `dayCount`) + the three stat use cases (D-ALT-7/8); stream titles from the descriptor, the `streamTitle` enum-`when` retired (D-ALT-22/23, single-stream renders no label); the day cards, stats strips/rows, and the **row-count-aware widget tier policy** (D-ALT-9/10); the whole-day-mark + reminder/persistent scoping (D-ALT-11). | **The whole app renders the active plan's *actual* stream count truthfully** — 1, 2, 3, or 4 cards/strips/rows/denominators, all completion flowing through the ONE classifier seam, never forked. N-correctness is JVM-gate-provable (M'Cheyne 4-stream + a synthetic 1-stream plan exercise both edges); the *look* at N≠3 is the device-pass set. | Diego (enum retire + classifier), Priya (N≠3 look + widget tiers), Avery (widget) | B | **The classifier-parameterized completion is correct for N=2/4 (M'Cheyne) AND a synthetic N=1, through the single seam; the Bible-Companion stats/strip/widget pins pass UNCHANGED.** |
| **D** | **Plan selector + whole-app integration** | The Settings selector (S14 `SettingsDropdownRow` idiom, D-ALT-18); the explained non-destructive switch dialog (D-ALT-19); wiring `ActivePlanRepository` through the day screen, stats, picker dots, reminder/persistent content, and the **widget** (its `@EntryPoint` reads `selected_plan` + refreshes on a switch); the "active plan visible" affordance (FR-ALT-6); the first-run plan question **iff** OQ-7 says so (light, BC-default). | **A user can select M'Cheyne and the *entire* app — day screen, stats, strips, widget, reminders, in-app reader handoff — shows M'Cheyne; switch back and the Bible Companion history is intact, restored from its own `plan_id` partition.** Plan choice is live and off the daily path. | Diego (active-plan wiring), Sam (selector/switch/first-run), Priya (selector + dialog look), Avery (widget refresh) | B (peer of C) | **End-to-end: select M'Cheyne → every surface shows M'Cheyne; switch back → BC marks restored intact (non-destructive switch proven); the selector default = `registry.defaultPlanId`.** |
| **E** | **The chronological plan + hardening + release** | A **specific, named, date-anchored** one-year chronological plan: two genuinely-independent sources (the contested-ordering data lift, D-ALT-21), reconciliation log, `ChronologicalPlanVerificationTest` — proving the **single-stream** end of the generalization. Consolidated device pass (N=1/2/4 day-screen fit, stats strip look, widget tiers at every N, the switch on glass, a real migrated-history upgrade); string/tone sign-offs (stream titles, the switch explanation, the selector); the bundle-size check (the extra plan assets are tiny); version bump + closed-track rollout. | **A reader can choose a chronological plan, single-stream, verified as rigorously as the Bible Companion; the feature is releasable, device-confirmed at every N, with the migration proven on a real upgrade.** | Riley (chrono gate + device pass), Owner/Maya (sourcing + sign-offs), Priya (presentation), Jordan (release) | C, D | **`ChronologicalPlanVerificationTest` green (a real independent second witness exists — or the plan does NOT ship, D-ALT-21); consolidated device pass passed; all three plan gates green.** |

**Dependency notes**
- A blocks everything downstream; it is the hard data gate and is serial-first. Within A, the
  **code track** (schema-v3, registry, loader, `ActivePlanRepository`, the gate harness) is fully
  specifiable now and does **not** wait on the M'Cheyne **data track** (sourcing + extraction + the
  asset) — they run in parallel and converge on the green M'Cheyne gate (ESpec §11).
- B is verifiable the moment A lands; it is isolated because it is the only sprint that rewrites
  existing user data. C must not start rendering against per-plan progress until B is green.
- C and D are peers downstream of B and overlap across people. The **widget** is the one surface that
  spans both: its N-row rendering is C (D-ALT-10 tier policy), its active-plan `@EntryPoint` wiring +
  refresh-on-switch is D — coordinate so the widget lands whole.
- E adds no new *code* feature scope beyond the single-stream exercise; it is the chronological data
  project + verification + sign-off + release.

Each sprint is one session in the one-sprint-per-session rhythm (CLAUDE.md). C/D/E firm up into full
tickets at the start of their own sessions, once A/B land — exactly as the V1/V3 plans ticketed only
their immediate sprints up front.

---

## 3. Detailed ticket breakdown

### Sprint A — Plan-data model + active-plan spine + M'Cheyne asset & gate  *(HARD GATE)*

**Outcome goal:** a `schemaVersion: 3` plan model where a plan *declares* its shape (`streams[]`,
`dayCount`, `anchoring`); a bundled `registry.json` + `PlanRegistry`; the Bible Companion asset moved
under `assets/plans/bible_companion/` and re-authored v3 with its **reading bytes unchanged**; an
`ActivePlanRepository` + `selected_plan` key that names the active plan (default `bible_companion`);
**and** the M'Cheyne plan asset (4 streams, ~24 verse-range days) verified against an independent
second source by `McheynePlanVerificationTest` and re-derivable in CI. **No UI renders a second plan
in this sprint — the data, its spine, and its verification only.** This is the Sprint-1 of multi-plan.

**Sprint-level acceptance:**
- The **Bible Companion plan gate (`ReadingPlanVerificationTest`, 11 tests) passes UNCHANGED** against
  the moved+re-authored v3 asset (the move is a path edit + an additive head, not a data edit).
- **`McheynePlanVerificationTest` is green** to the Sprint-1 standard: independent second source,
  day-by-day equality, the coverage invariant (OT once / NT + Psalms twice), the verse-window +
  4-stream invariants, a reconciliation log entry for every conflict.
- The `mcheyne-rebuild` CI job re-derives the M'Cheyne asset from pinned sources and asserts a
  **byte-diff of zero**.
- `ActivePlanRepository.activePlanId` defaults to `bible_companion`; the standing pipeline is green;
  the full existing suite (parity, §1.2) is green.

> Like Sprint 1 and V3 Sprint A, the verification runs in `:app`'s `testDebugUnitTest` straight off
> the bundled asset (no device) — it guards the exact files shipped in the APK.

#### Code-track tickets (do NOT wait on the M'Cheyne data)

**SA-T1 — `schemaVersion: 3` DTOs + the descriptor head (D-ALT-2/3)**
- **Owner:** Diego. **Complexity:** M. **Dependencies:** none.
- **Scope:** Extend `data/plan/dto/PlanDto.kt`: `PlanDto` gains `planId: String`, `name: String`,
  `anchoring: String`, `dayCount: Int`, `streams: List<StreamDto>` (`StreamDto(number: Int, title:
  String)`); `schemaVersion` becomes `3`. **`PortionDto`/`RefDto` are UNCHANGED** (the v2 body is a
  strict subset — D-ALT-3; the Psalm-119 window + two-book portion keep working for free). The
  existing `source` field stays. `PlanJson.decode` unchanged (`ignoreUnknownKeys = false` holds).
- **Acceptance:** the v3 head parses; an asset missing a head field fails to decode (strict).
  **JVM-provable.**
- **Tests/mutation:** a round-trip decode test of a v3 head; a mutation removing a required head field
  must red a decode test.

**SA-T2 — `PlanDescriptor`/`StreamDescriptor` + the loader generalization (D-ALT-2/4)**
- **Owner:** Diego. **Complexity:** L. **Dependencies:** SA-T1.
- **Scope:** Add `PlanDescriptor(planId, name, anchoring, dayCount, streams: List<StreamDescriptor>)`
  + `StreamDescriptor(number: Int, title: String)` (in `data/plan` or `domain/model`). Generalize
  `ReadingPlanAssetLoader`: replace the constants `SUPPORTED_SCHEMA_VERSION = 2`/`EXPECTED_DAYS =
  365`/`listOf(1, 2, 3)`/`Stream.fromNumber` with **validation against the descriptor**:
  `schemaVersion == 3`; `anchoring == "DATE"` (reject anything else — clean fail); `dayCount == 365`
  (reject otherwise — this cut); `days.size == dayCount`; the Feb-29-absent / 28-Feb invariant for
  365+DATE; `streams[].number == 1..N` contiguous; **every day's `portions[].stream` set == exactly
  `streams[].number`** (the generalized form of the `== listOf(1,2,3)` check); `planId` equals the
  registry id and the asset dir name (anti-drift, mirrors D-S9-1). The loader carries the stream
  **number** through unchanged (it already is the key) and exposes `descriptor(): PlanDescriptor`.
  **NOTE:** the loader still produces `Portion(stream = Stream.fromNumber(...))` in this sprint — the
  `Stream` enum is NOT retired here (that is C, D-ALT-5). A is descriptor-additive only, so the
  Bible-Companion-path output map is byte-identical to today.
- **Acceptance:** the Bible-Companion v3 asset loads to the **identical** `Map<ReadingDate,
  List<Portion>>` it does today; a plan declaring N≠3 streams loads (proven against a tiny synthetic
  fixture); `anchoring != "DATE"` / `dayCount != 365` / a stream-set mismatch each clean-fail.
  **JVM-provable.**
- **Tests/mutation:** the existing loader tests pass unchanged (parity); new tests for the descriptor
  validation; mutations — drop the `streams`-set check, accept a non-DATE anchoring, accept
  `dayCount != 365` — each killed by an intended test.

**SA-T3 — `registry.json` + `PlanRegistry` (D-ALT-1)**
- **Owner:** Diego. **Complexity:** M. **Dependencies:** SA-T1.
- **Scope:** Author `app/src/main/assets/plans/registry.json` (`registryVersion`, `defaultPlanId:
  "bible_companion"`, `plans: [{id, asset}]` — `bible_companion` + `mcheyne`). Add `PlanRegistry`
  (reads `registry.json`, exposes `defaultPlanId` + the `(id, asset)` list; bundled, parsed once,
  memoized) and a registry DTO. `defaultPlanId` is a **build constant**, asserted `== "bible_companion"`
  by a test (the app never ships without it). A registry entry is a build guarantee — no runtime
  "plan not found" path; a runtime parse failure of a plan asset falls back to the default + the
  existing retryable-error state (never a crash).
- **Acceptance:** `PlanRegistry` enumerates the two plans + the default; `defaultPlanId ==
  "bible_companion"` (test-pinned). **JVM-provable.**
- **Tests/mutation:** a registry-parse test; a `defaultPlanId == "bible_companion"` pin; a mutation
  changing the default reds it.

**SA-T4 — `PlanAssetSource` (path-parameterized) + per-plan `ReadingPlanRepository` (D-ALT-1, §3.5)**
- **Owner:** Diego (with Avery review). **Complexity:** M. **Dependencies:** SA-T2, SA-T3.
- **Scope:** Generalize `PlanJsonSource` → `PlanAssetSource { fun readText(assetPath: String): String }`
  (parameterized by the registry's `asset` path; the `DataModule` provider reads
  `context.assets.open(assetPath)`). `ReadingPlanRepository` gains a plan dimension:
  `portionsFor(planId, date)` + `descriptor(planId)`; the memo cache becomes a **per-plan** map (a
  plan's asset is parsed only on first touch; the default plan is touched at startup as today, so
  cold-start cost for the default user is unchanged — M2). Move the Bible Companion asset
  `assets/reading_plan.json` → `assets/plans/bible_companion/plan.json` and re-author its head to v3
  (**reading body bytes unchanged**, D-ALT-3).
- **Acceptance:** the repo loads `bible_companion` from the new path to the identical map; loading
  `mcheyne` reads its own asset; only the active plan's asset is parsed. **JVM-provable.**
- **Tests/mutation:** repo tests for both plans; a per-plan-cache-isolation test.

**SA-T5 — `selected_plan` DataStore key + `ActivePlanRepository` (D-ALT-16/17)**
- **Owner:** Avery (with Diego review). **Complexity:** M. **Dependencies:** SA-T3, SA-T4.
- **Scope:** Add `selectedPlanId: Flow<String>` + `setSelectedPlanId(id)` to `SettingsRepository`
  (key `stringPreferencesKey("selected_plan")`, **absent ⇒ `registry.defaultPlanId`**, **unknown
  stored id ⇒ default** — the exact `bible_provider`/`reading_destination_mode` idiom: degrade,
  never crash). Add `ActivePlanRepository { val activePlanId: Flow<String>; val activeDescriptor:
  Flow<PlanDescriptor> }` joining `selected_plan` → `PlanRegistry` → `ReadingPlanRepository.descriptor`.
  **No use case consumes it yet** (that is C/D) — this sprint stands it up and proves the default.
- **Acceptance:** absent key ⇒ `activePlanId == "bible_companion"`; an unknown stored id ⇒ default;
  `activeDescriptor` emits the BC descriptor by default. **JVM-provable.**
- **Tests/mutation:** absent-key default; unknown-id-degrades; descriptor-for-default; mutations on
  each `?:` default killed.

**SA-T6 — Build/CI: asset-path move + the generalized gate harness (Jordan)**
- **Owner:** Jordan. **Complexity:** M. **Dependencies:** SA-T4.
- **Scope:** Update `app/build.gradle.kts`: the `planAssetsDir` system property + the `planAssets`
  test-input declaration (Sprint-1 lesson: an asset not declared as a test input is silently skipped
  as UP-TO-DATE) point at `src/main/assets/plans/` so **both** plan assets are gate inputs and edits
  re-run the gate. The Bible Companion gate reads from the moved path. (The M'Cheyne `mcheyne-rebuild`
  CI job is SA-T11.)
- **Acceptance:** editing either plan asset re-runs the gate (not UP-TO-DATE-skipped); the BC gate
  reads the moved asset. **CI/build-provable.**

#### Data-track tickets (the M'Cheyne asset — gated on OQ-MC + OQ-5, run in parallel)

**SA-T7 — M'Cheyne sourcing confirmation + form decision (OQ-MC, OQ-5)**
- **Owner:** Riley (with owner/Maya on OQ-MC + OQ-5). **Complexity:** M. **Dependencies:** none.
  *(data-track long-pole — start first, in parallel with the code track.)*
- **Scope:** Confirm the two genuinely-independent lineages from `data/mcheyne-sourcing.md` —
  **canonical = Edgington/Haslam** (verse-faithful), **independent witness = Carson/TGC + bibleplan.org**
  — are checksum-distinct and of different lineage (the re-mirror trap: Haslam→Edgington is the SAME
  lineage = ONE witness; Carson is the genuine second). Pin source SHAs (edginet classic PDF,
  mcheyne.info calendar.pdf, TGC plan PDF, paulyoder `plan.js` blob). Lock OQ-MC: the sourcing doc
  recommends the **classic 4-stream date-anchored 365-day form** — confirm with owner. Record the
  **coverage invariant** for that form (OT once; NT + Psalms twice). Confirm OQ-5 (owner owns the
  recurring per-plan data work; the second source is verifiable).
- **Acceptance:** two distinct-lineage sources pinned by SHA; OQ-MC form locked (recommend 4-stream);
  the coverage invariant stated; OQ-5 confirmed. **Documented finding + owner sign-off.**

**SA-T8 — `tools/build_mcheyne_db.py` extraction → `mcheyne` asset (sourcing-doc build path)**
- **Owner:** Riley (with Avery on determinism; Diego review). **Complexity:** L. **Dependencies:**
  SA-T7, SA-T2 (the v3 schema shape).
- **Scope:** Reproducible extraction per the sourcing doc's build path: (1) parse bibleplan.org
  `plan.js` for the chapter skeleton (`{month, day → 4 refs}`); (2) **overlay the ~24 verse-windowed
  days from the Edgington/Haslam PDF** (Psalm 119 ×7, Psalm 78 ×2, ~13 chapter-spanning ranges, the
  non-adjacent double-chapter slots) as `verseStart`/`verseEnd` windows + multi-ref portions — **do
  NOT use the chapter-collapsed bibleplan.org data for these days** (it loses the windows; one
  off-by-one already found, Feb 28); (3) emit `assets/plans/mcheyne/plan.json` in **schemaVersion 3**
  (365 entries, Feb = 28 no Feb 29, the 4-stream descriptor head with M'Cheyne's stream titles, the
  `{book, chapter, verseStart?, verseEnd?}` body). Byte-deterministic (fixed order, no timestamps),
  Python stdlib, deps pinned in `tools/requirements.txt`. Record provenance + SHAs in
  `docs/data/README.md` (the Bible-Companion-style source table).
- **Acceptance:** the script re-derives `mcheyne/plan.json` byte-identically; the asset is schema-v3,
  365-day, 4-stream, with the ~24 verse-windowed days encoded faithfully (not chapter-collapsed).
  **JVM-provable** (determinism via SA-T11).
- **Tests/mutation:** content correctness is the gate's job (SA-T9); determinism is SA-T11.

**SA-T9 — `McheynePlanVerificationTest` — THE M'CHEYNE GATE (D-ALT-20, FR-ALT-3)**
- **Owner:** Riley. **Complexity:** L. **Dependencies:** SA-T8, SA-T2.
- **Scope:** A per-plan gate (offline, `testDebugUnitTest`) reading the **shipped M'Cheyne asset**,
  asserting the full §7 bar against the committed asset:
  1. **Structural for M'Cheyne:** `schemaVersion == 3`; 365 days, correct per-month counts, Feb-29
     absent; `streams[].number == 1..4`; every day's portions = exactly the 4 declared streams; every
     ref resolves in `BookCatalog` with chapter in range; **every verse-windowed ref well-formed**
     against the committed `bible/kjv_verse_counts.csv` witness (`1 <= start <= end <= chapterVerseCount`).
  2. **Second-source day-by-day equality:** the canonical Edgington/Haslam-derived asset vs the
     independent Carson/TGC fixture, agreeing on **every day**, with a **reconciliation log** entry
     for every conflict resolved on evidence. Guard the re-mirror trap (checksum-distinct, two parsers).
  3. **Coverage invariant (M'Cheyne-specific):** OT once; **NT + Psalms twice** (e.g. Matthew 1 on
     Jan 1 Family AND Jun 21 Secret per the sourcing doc) — the plan's load-bearing structural proof.
  4. **The verse-window fidelity invariant:** the ~24 windowed days are present and faithful — e.g.
     the Psalm 119 windows tile their covered verses with no gap/overlap (the Sprint-J tiling-invariant
     analog), and the chapter-spanning ranges match the verse-faithful source, not the collapsed one.
- **Acceptance:** the test exists and **passes** against the committed M'Cheyne asset; editing the
  asset or the fixture inconsistently reds it; it runs offline on plain JVM. **JVM-provable /
  release-gating.**
- **Tests/mutation:** the gate *is* the test. Mutation-verify its own invariants where feasible (a
  dropped verse window, a 4th-stream day reduced to 3, a coverage double-count masked) → each must
  red the gate.

**SA-T10 — Generalize `ReadingPlanVerificationTest` shape; keep the BC gate green UNCHANGED (D-ALT-20)**
- **Owner:** Riley. **Complexity:** M. **Dependencies:** SA-T4, SA-T9.
- **Scope:** Re-home the Bible Companion gate to read from the moved path
  (`plans/bible_companion/plan.json`) and accept the additive v3 head (the schema pin moves 2 → 3,
  the day-body assertions UNCHANGED). Extract the shared gate scaffolding (catalog load, verse-count
  witness, second-source day-by-day equality) so `McheynePlanVerificationTest` reuses it without
  forking the discipline. **The Bible Companion's 11 day/coverage/window assertions must read
  identically and pass UNCHANGED** — only the asset path + the schema-version literal change.
- **Acceptance:** `ReadingPlanVerificationTest` is green against the moved+re-authored v3 BC asset,
  its day-by-day + coverage + Psalm-119 invariants intact; the shared scaffolding is reused, not
  duplicated. **JVM-provable / parity gate.**
- **Tests/mutation:** the BC gate's existing 11 tests pass; a mutation re-pinning schema to 2 reds the
  schema-header test (proves the bump is asserted).

**SA-T11 — `mcheyne-rebuild` CI job + reconciliation log (D-ALT-20, the protecting wrapper)**
- **Owner:** Jordan (job) + Riley (log). **Complexity:** M. **Dependencies:** SA-T8, SA-T9.
- **Scope:** A CI job mirroring the existing `data-rebuild`: re-derive `mcheyne/plan.json` from
  `tools/build_mcheyne_db.py` (pinned sources, `tools/requirements.txt`) and assert a **byte-diff of
  zero** against the committed asset, so a hand-edited M'Cheyne asset can never sneak past the commit.
  Extend `docs/data/README.md` with the M'Cheyne source table (two URLs + SHA-256, checksum-distinctness
  result, the OQ-MC form decision, the coverage invariant, every reconciled conflict).
- **Acceptance:** CI re-derives + byte-diffs the M'Cheyne asset; a hand-edit fails the job; the
  reconciliation log records sources/checksums/conflicts/coverage. **CI-gating + documentation gate.**

#### Sprint A subtask decomposition (~2–5 min each)

> Diego (tech lead) + Morgan sharpen these at dispatch; below is the starting decomposition. Each
> subtask names exact files and acceptance so the receiving engineer has zero open questions.

- **SA-T1:** 1a `PlanDto` head fields + `StreamDto`. 1b `schemaVersion` literal → 3. 1c head round-trip decode test + strict-missing-field mutation.
- **SA-T2:** 2a `PlanDescriptor`/`StreamDescriptor`. 2b loader: descriptor-driven `dayCount`/streams-set validation (replace the constants). 2c `anchoring == "DATE"` + `dayCount == 365` clean-fail guards. 2d `planId`/dir/registry agreement assert. 2e parity: BC loads to the identical map (existing loader tests unchanged). 2f synthetic N≠3 fixture loads.
- **SA-T3:** 3a author `registry.json`. 3b `PlanRegistry` + DTO (parse-once). 3c `defaultPlanId == "bible_companion"` pin + mutation.
- **SA-T4:** 4a `PlanAssetSource(assetPath)` + `DataModule` provider. 4b repo `portionsFor(planId,…)`/`descriptor(planId)` + per-plan cache. 4c move BC asset → `plans/bible_companion/plan.json`, re-author head to v3 (body bytes unchanged). 4d repo tests (both plans, cache isolation).
- **SA-T5:** 5a `selected_plan` key in `SettingsRepository(Impl)` (absent⇒default, unknown⇒default). 5b `ActivePlanRepository` (selected → registry → descriptor). 5c default + unknown-id + descriptor tests + `?:` mutations.
- **SA-T6:** 6a `planAssetsDir`/test-input → `plans/`. 6b BC gate reads the moved path.
- **SA-T7:** 7a pin the 4 source SHAs + checksum-distinctness. 7b lock OQ-MC (recommend 4-stream) with owner. 7c state the coverage invariant; confirm OQ-5.
- **SA-T8:** 8a `build_mcheyne_db.py` skeleton + `requirements.txt`. 8b parse `plan.js` chapter skeleton. 8c overlay the ~24 verse-windowed days from the Edgington/Haslam PDF. 8d emit schema-v3 head (4-stream descriptor + titles) + body. 8e determinism pass. 8f commit `mcheyne/plan.json` + record SHAs.
- **SA-T9:** 9a structural (schema/days/streams 1..4/refs/windows). 9b second-source day-by-day. 9c coverage (OT once / NT+Psalms twice). 9d verse-window fidelity + mutations.
- **SA-T10:** 10a re-home BC gate to the moved path + schema 3. 10b extract shared scaffolding. 10c confirm BC's 11 tests pass unchanged.
- **SA-T11:** 11a `mcheyne-rebuild` CI job (re-derive + byte-diff). 11b `docs/data/README.md` M'Cheyne provenance + reconciliation.

---

### Sprint B — Per-plan progress Room migration  *(high-risk, isolated)*

**Outcome goal:** the progress store gains a `plan_id` dimension — PK `(plan_id, dateEpochDay,
stream)` — via a hand-written, exported, zero-loss `MIGRATION_1_2` that stamps every existing mark
`bible_companion`; every `ProgressRepository`/DAO method is plan-scoped (defaulted to the active
plan). **No UI change** — the spine becomes per-plan and is proven lossless before anything renders
against it. This is the only sprint that rewrites existing user data.

**Sprint-level acceptance:**
- The **`MigrationTestHelper` 1→2 zero-loss test** is green: row count identical, every
  `(dateEpochDay, stream, readAtEpochMillis)` tuple preserved with `plan_id = "bible_companion"` and
  nothing else changed, the v2 schema validates.
- The **end-to-end "no migration the default user can perceive" test** is green: a migrated v1 DB,
  read through the *new* `ProgressRepository` with the active plan defaulted to `bible_companion`,
  returns streams-read / stats / strips **identical** to what v1 returned (U-ALT-1, FR-ALT-2).
- The exported **`app/schemas/…ProgressDatabase/2.json`** is checked in; `fallbackToDestructiveMigration`
  is **NOT** enabled (D-V3-15 rule — user data is never destroyed).
- The full existing suite passes UNCHANGED (parity, §1.2): every day/stats/strip/picker/widget pin
  reads `bible_companion` progress by default and is byte-for-byte unchanged.

> The migration test extends the existing Room-test discipline (`BibleDatabaseRoomOpenTest` opens a
> real DB under Robolectric); `MigrationTestHelper` runs the real exported schemas, not a hand-typed DDL.

#### Tickets

**SB-T1 — `plan_id` on `ReadingProgressEntity` + the v2 PK (D-ALT-12)**
- **Owner:** Avery. **Complexity:** S. **Dependencies:** A (the `bible_companion` id constant).
- **Scope:** `ReadingProgressEntity` gains `val planId: String`; `@Entity primaryKeys` becomes
  `["plan_id", "dateEpochDay", "stream"]` (with the `@ColumnInfo(name = "plan_id")` mapping). Bump
  `ProgressDatabase` `version = 2`. The `bible_companion` literal lives in a single shared constant
  asserted `== registry.defaultPlanId` (anti-drift, D-ALT-13).
- **Acceptance:** the entity/DB compile at v2 with the new PK; the shared constant equals the registry
  default (test-pinned). **JVM-provable.**
- **Tests/mutation:** a constant-equals-registry-default pin; a mutation changing the literal reds it.

**SB-T2 — `MIGRATION_1_2` recreate-and-copy (D-ALT-13)**
- **Owner:** Avery. **Complexity:** M. **Dependencies:** SB-T1.
- **Scope:** The hand-written `MIGRATION_1_2` exactly per ESpec §5.2: create `reading_progress_new`
  with the v2 schema; `INSERT … SELECT 'bible_companion', dateEpochDay, stream, readAtEpochMillis FROM
  reading_progress` (touch every row once, zero loss); drop the old; rename. (The PK change forces the
  recreate idiom — SQLite cannot alter a PK in place.) The `'bible_companion'` stamp is the literal
  meaning of the existing data, NOT a schema-level column default. Register the migration on the
  `ProgressDatabase` builder.
- **Acceptance:** the migration runs 1→2; no `fallbackToDestructiveMigration`. **JVM-provable via
  SB-T4** (+ device-pass on a real upgrade in E).
- **Tests/mutation:** correctness is SB-T4; a mutation dropping the `INSERT … SELECT` or stamping a
  wrong id must red SB-T4.

**SB-T3 — Exported `2.json` schema + the test wiring (D-ALT-14)**
- **Owner:** Avery (with Jordan on Gradle). **Complexity:** S. **Dependencies:** SB-T1.
- **Scope:** Regenerate + check in `app/schemas/…ProgressDatabase/2.json` (KSP `room.schemaLocation`
  already wired). Add the `androidx.room:room-testing` dep (the one new test dep, ESpec §12) to the
  version catalog (test scope). Wire the `MigrationTestHelper` against the exported `app/schemas`.
- **Acceptance:** `2.json` checked in; `room-testing` in the catalog (test only); the helper finds the
  schemas. **JVM/CI-provable.**

**SB-T4 — `MigrationTestHelper` zero-loss test (D-ALT-14, the single most important new test)**
- **Owner:** Riley (with Avery). **Complexity:** L. **Dependencies:** SB-T2, SB-T3.
- **Scope:** A Robolectric `MigrationTestHelper` test: create a v1 DB, insert a representative set of
  marks (multiple years to exercise year-isolation; whole-day + partial days; a Feb-29-adjacent day),
  run `MIGRATION_1_2`, assert: (a) **row count identical**; (b) **every** `(dateEpochDay, stream,
  readAtEpochMillis)` tuple present with `plan_id = "bible_companion"` and nothing else changed; (c)
  Room's `validateMigration` passes for v2.
- **Acceptance:** the test passes; a dropped `INSERT … SELECT` or a wrong stamp makes it red. **JVM/
  Robolectric-provable.** *(Real-device upgrade = E device pass.)*
- **Tests/mutation:** mutations — drop the row copy (lose all rows), stamp a wrong plan id, change a
  `readAtEpochMillis` — each killed.

**SB-T5 — Plan-scope every `ProgressRepository`/DAO method (D-ALT-12)**
- **Owner:** Avery. **Complexity:** L. **Dependencies:** SB-T1.
- **Scope:** Every DAO query gains a `plan_id = :planId` clause (the grouped/ranged queries —
  `readCountsInRange`, `allReadCounts`, `streamCountsInRange`, `marksInRange` — and the mutators +
  `deleteRange`/`deleteDay`/`delete` + `hasAnyRows`). Every `ProgressRepository` method gains a
  `planId` parameter **defaulted to the active plan in the impl** so callers reading "the active
  plan" don't thread it manually. **D-ALT-15:** `hasAnyMarks()` stays **global** (any plan — the
  "fresh install" meaning for the tracking-start default); the tracking-start date stays **global**
  (a calendar concept, not per-plan) this cut. The repo impl injects `ActivePlanRepository` (from A)
  for the default.
- **Acceptance:** every query/mutator is plan-scoped; the default-plan path returns exactly what it
  did pre-change (parity); `hasAnyMarks()` is global. **JVM-provable.**
- **Tests/mutation:** existing progress-repo tests pass UNCHANGED with the default plan; a new
  two-plan isolation test (marks under plan A invisible to plan B); a mutation dropping a `plan_id`
  filter (bleeds plans together) killed; `hasAnyMarks()`-stays-global pinned.

**SB-T6 — End-to-end "no perceptible migration for the default user" test (D-ALT-14)**
- **Owner:** Riley. **Complexity:** M. **Dependencies:** SB-T2, SB-T5.
- **Scope:** Open a migrated v1 DB **through the new `ProgressRepository`** with the active plan
  defaulted to `bible_companion`; assert `streamsRead` / the stat counts / the strip marks are
  **identical** to what the v1 repo returned for the same inserted history (U-ALT-1 AC). This is to
  multi-plan what the day-by-day equality test is to the plan data.
- **Acceptance:** the migrated-then-read result is identical to the pre-migration result for the
  default user. **JVM/Robolectric-provable.**
- **Tests/mutation:** a mutation that drops the default-plan scoping (so the read returns nothing /
  cross-plan) must red it.

#### Sprint B subtask decomposition (~2–5 min each)

- **SB-T1:** 1a `planId` field + `@ColumnInfo`. 1b v2 PK. 1c `version = 2`. 1d shared `bible_companion` constant + registry-equality pin.
- **SB-T2:** 2a `MIGRATION_1_2` recreate. 2b `INSERT … SELECT` stamp. 2c drop+rename. 2d register on the builder (no destructive fallback).
- **SB-T3:** 3a regenerate `2.json`. 3b `room-testing` in the catalog. 3c helper schema wiring.
- **SB-T4:** 4a seed a v1 DB (multi-year, whole+partial, Feb-29-adjacent). 4b run `MIGRATION_1_2`. 4c assert row-count + tuple preservation + stamp. 4d `validateMigration`. 4e mutations.
- **SB-T5:** 5a `plan_id` clause on every DAO query. 5b `planId` param on every repo method (active-plan default). 5c `hasAnyMarks()` global + tracking-start global (D-ALT-15). 5d two-plan isolation test + parity pass + filter-drop mutation.
- **SB-T6:** 6a seed + migrate + read-through-new-repo. 6b assert identical-to-v1. 6c default-scope-drop mutation.

---

### Sprint C — N-stream UI generalization  *(sketch — full tickets at C's session start)*

**Outcome goal:** the whole app renders the active plan's actual stream count (1/2/3/4) truthfully,
all completion flowing through the ONE `DayCompletionClassifier` seam, never forked — N-correctness
JVM-gate-provable, the look at N≠3 the device-pass set.

**Candidate tickets (titles):**
- **SC-T1 — Retire the `Stream` enum; `Portion.streamNumber: Int` (D-ALT-5)** (Diego) — compiler-driven; delete `Stream`/`fromNumber`, follow the compiler; the int is already the persisted key.
- **SC-T2 — Parameterize `DayCompletionClassifier` (`streamCount: Int`, D-ALT-6)** (Diego) — the one seam becomes a param, truth-table order untouched; mutation "ignore the passed N, hard-code 3" killed by an M'Cheyne completion test.
- **SC-T3 — Stats denominators → `dayCount × N` / `dayCount` (D-ALT-7)** (Diego) — delete the 1095/365 consts; `ReadingStats` instance fields; floor-rounding rule unchanged.
- **SC-T4 — The three stat use cases iterate the descriptor + scope by active plan (D-ALT-8)** (Diego/Sam) — `GetReadingStats`/`GetYearStrips`/`GetMonthCompletion`; pass N to the classifier; per-plan queries (from B).
- **SC-T5 — Stream titles from the descriptor; retire `streamTitle` enum-`when` (D-ALT-22/23)** (Sam/Priya) — single-stream renders no label.
- **SC-T6 — Day cards at N≠3 + the one-screen-fit re-confirm (D-ALT-9, FR-ALT-4)** (Priya) — 1 card reclaims space, 4 cards fit; device-pass-heavy.
- **SC-T7 — Stats strips/rows at variable N: strip height, legend, a11y summaries (D-ALT-9)** (Priya) — N rows in the stacked band; the no-guilt copy ban incl. contentDescriptions unchanged.
- **SC-T8 — Row-count-aware widget tier policy (D-ALT-10, FR-ALT-9)** (Priya/Avery) — the chooser factors `streamCount`; high-N degrades earlier, low-N reclaims room; S9 invariants hold at every N.
- **SC-T9 — Whole-day mark + reminder/persistent scoping (D-ALT-11)** (Avery) — `1..N` for the active plan; the bodies already join the active plan's portions (copy + scoping only).
- **SC-T10 — N-correctness gate + mutation pins** (Riley) — M'Cheyne 4-stream + a synthetic 1-stream exercise both edges through the one seam; the BC stats/strip/widget pins pass UNCHANGED.

---

### Sprint D — Plan selector + whole-app integration  *(sketch — full tickets at D's session start)*

**Outcome goal:** select M'Cheyne and the entire app shows M'Cheyne; switch back and the Bible
Companion history is intact (non-destructive switch by construction); plan choice is live and off the
daily path.

**Candidate tickets (titles):**
- **SD-T1 — Settings plan selector row (S14 `SettingsDropdownRow` idiom, D-ALT-18)** (Sam/Priya) — shows the active plan `name`, menu of registry plans, writes `selected_plan`; off the daily path (G15).
- **SD-T2 — The explained non-destructive switch dialog (D-ALT-19)** (Sam/Priya) — one-time explanation at the switch ("your [old] progress is saved… [new] starts fresh"); NO data operation runs; tone sign-off.
- **SD-T3 — Thread `ActivePlanRepository` through the day screen / stats / picker dots (D-ALT-17)** (Diego/Sam) — use cases `combine` the active descriptor so a switch re-emits everything live.
- **SD-T4 — Active-plan reminder + persistent content (D-ALT-11/17)** (Avery) — fire-time content reads the active plan; "skip when complete" uses the per-plan classifier.
- **SD-T5 — Widget active-plan `@EntryPoint` + refresh-on-switch (D-ALT-10/17)** (Avery) — the widget reads `selected_plan` + descriptor; a switch triggers `WidgetRefresher` so the launcher snaps to the new plan.
- **SD-T6 — "Active plan visible" affordance (FR-ALT-6)** (Priya) — discoverable from Settings + schedule, off the critical path; OQ-7 placement.
- **SD-T7 — First-run plan question, iff OQ-7 says so (FR-ALT-10)** (Sam) — light, skippable, BC-default; plan never a silent non-BC default; only if it stays light.
- **SD-T8 — End-to-end switch integration gate** (Riley) — select M'Cheyne → every surface shows M'Cheyne; switch back → BC marks restored intact; Robolectric where provable.

---

### Sprint E — The chronological plan + hardening + release  *(sketch — full tickets at E's session start)*

**Outcome goal:** a chronological plan (single-stream, verified as rigorously as the Bible Companion)
ships; the feature is releasable, device-confirmed at every N, the migration proven on a real upgrade.

**Candidate tickets (titles):**
- **SE-T1 — Name + source the chronological plan; two independent witnesses or do-not-ship (D-ALT-21, OQ-5)** (Riley + owner) — a *specific, named, date-anchored* one-year chronological table with a verifiable independent second witness; the contested-ordering data lift.
- **SE-T2 — `tools/build_chronological_db.py` + the `chronological` asset** (Riley/Avery) — schema-v3, single-stream descriptor, reproducible + byte-deterministic.
- **SE-T3 — `ChronologicalPlanVerificationTest` (D-ALT-20)** (Riley) — structural + second-source day-by-day + the single-stream coverage invariant (every chapter once, in the publisher's order); `chronological-rebuild` CI job.
- **SE-T4 — Consolidated device pass at every N** (Riley + owner) — N=1/2/4 day-screen fit, stats strip look, widget tiers at every size, the switch on glass, a real migrated-history upgrade (SB-T2 on a device).
- **SE-T5 — String/tone sign-offs** (owner/Maya/Priya) — stream titles, the switch explanation, the selector copy; the M'Cheyne stream-title labels.
- **SE-T6 — Bundle-size check + version bump + closed-track rollout** (Jordan) — the extra plan assets are a few hundred KB each (well under budget); the `release-bundle` size gate stays green; version bump + tag-to-Play.

---

## 4. Quality gates per sprint

### 4.1 Standing pipeline (every sprint — the V1/V2/V3 discipline, unchanged)
On the merge target, a sprint is **not done** until:
1. `./gradlew assembleDebug` succeeds.
2. `./gradlew testDebugUnitTest` passes — **including the three standing data/Room gates: the plan
   gate (11, → Sprint A re-homed + the M'Cheyne gate added), `BibleTextVerificationTest` (18), and
   `BibleDatabaseRoomOpenTest` (5).**
3. `spotlessCheck` + `lintDebug` are clean.
4. Kover floor met (≥70% on domain/data; the project runs ~95–96%); new domain/data carries tests +
   mutation verification per the project habit (each load-bearing mutation killed by exactly its
   intended test, restored in place).
5. The sprint's acceptance is demonstrably met in **working software** — nothing closed on "should
   work."
6. **The Bible-Companion-parity regression gate (§1.2) is green** — the full existing suite passes
   UNCHANGED with the active plan defaulted to `bible_companion`.

Full local command (CLAUDE.md):
`./gradlew spotlessCheck lintDebug assembleDebug testDebugUnitTest koverXmlReportAppDebug koverVerifyAppDebug`.

### 4.2 Sprint-specific gates
- **Sprint A (HARD GATE — the Sprint-1 standard):** `McheynePlanVerificationTest` green against the
  **shipped asset** (structural; independent second-source day-by-day equality; the OT-once/NT+Psalms-
  twice coverage invariant; the verse-window + 4-stream invariants; reconciliation log); the
  `mcheyne-rebuild` CI job re-derives + byte-diffs the asset; the two M'Cheyne sources are
  **checksum-distinct**; **`ReadingPlanVerificationTest` (Bible Companion) green UNCHANGED** against
  the moved+re-authored v3 asset. **No downstream sprint may render a second plan until this is green.**
- **Sprint B (the migration gate):** the `MigrationTestHelper` 1→2 **zero-loss** test green; the
  end-to-end **"no perceptible migration for the default user"** test green; exported `2.json` checked
  in; `fallbackToDestructiveMigration` NOT enabled; a two-plan isolation test green; the full existing
  progress/stats/strip/picker/widget suite UNCHANGED (parity).
- **Sprint C:** N-correctness through the **single classifier seam** for N=2/4 (M'Cheyne) AND a
  synthetic N=1, JVM-provable in the gate (the "hard-code 3" mutation killed by an M'Cheyne test); the
  Bible-Companion stats/strip/widget/day pins pass UNCHANGED. The look at N≠3 (card fit, strip height,
  widget tiers) is collected for E's device pass.
- **Sprint D:** end-to-end — select M'Cheyne → every surface (day/stats/strips/widget/reminder) shows
  M'Cheyne; switch back → Bible Companion marks restored intact (the non-destructive switch proven);
  the selector default reads `registry.defaultPlanId`; Robolectric where provable.
- **Sprint E:** `ChronologicalPlanVerificationTest` green (a real independent second witness exists, or
  the plan does NOT ship — D-ALT-21); the consolidated **device pass** at every N; the bundle-size CI
  gate green; all three plan gates green.

### 4.3 Device-pass items, collected per sprint (deferred to E's consolidated pass)
C (day cards at N=1/4 + one-screen-fit re-confirm with N≠3; stats strip height/legend/a11y at variable
rows; the row-count-aware widget tiers at every size) · D (the plan switch on glass — the widget snaps
to the new plan; the active-plan affordance) · B (the real migrated-history upgrade on a device) — all
rolled into SE-T4.

---

## 5. Risks & dependencies

| # | Risk / dependency | Impact | Mitigation | Owner |
|---|---|---|---|---|
| R-ALT-1 | **The progress migration (the worst case = existing-user data loss).** The only place existing user data is rewritten — blast radius is "everyone's reading history." | A botched `MIGRATION_1_2` loses or corrupts real marks. | Isolated in its own sprint (B), sequenced **before** any UI reads the per-plan store; recreate-and-copy touches every row once; exported `2.json` + the `MigrationTestHelper` zero-loss test + the "no perceptible migration" test; `fallbackToDestructiveMigration` NOT enabled (a failed migration is a loud QA crash, never silent loss); proven on a real-device upgrade in E. | Avery / Riley |
| R-ALT-2 | **The N-stream UI device-pass surface.** The S9/S14 widget tiers, the year-strip height/legend/a11y, and the one-screen-fit budget were all tuned to exactly 3; a 4-stream M'Cheyne plan and a 1-stream chronological plan stress both edges, and the *look* is not JVM-provable. | A 4-row widget crushes / a 1-card screen looks empty / a 4-strip stats panel overflows the one-screen budget. | D-ALT-9/10 make the data N-correct (gate-provable) and the tier policy row-count-aware; Priya owns the visual design at N=1/2/4; the look is collected per sprint and confirmed on a device at every N in E (SE-T4). | Priya / Morgan |
| R-ALT-3 | **M'Cheyne verse-window fidelity.** ~24 verse-range days (Psalm 119 ×7, Psalm 78 ×2, ~13 chapter-spanning ranges, non-adjacent double-chapter slots) MUST be encoded; the chapter-collapsed bibleplan.org data loses them (one off-by-one already found, Feb 28). | A "trustworthy" plan silently drops the verse fidelity that makes it M'Cheyne. | The canonical source is the **verse-faithful Edgington/Haslam** PDF (NOT the collapsed skeleton for these days, per the sourcing doc); the gate (SA-T9) pins every windowed day's fidelity (the Psalm-119 tiling-invariant analog); the `mcheyne-rebuild` byte-diff stops a hand-edit. | Riley |
| R-ALT-4 | **The chronological ordering sourcing (the heaviest lift).** The ordering is contested IP — two publishers may legitimately disagree, so "an independent second source that agrees day-by-day" is genuinely hard. | No two genuinely-independent sources agree → no second witness → the plan can't be gate-verified. | D-ALT-21: ship a **specific, named** date-anchored chronological table that *has* a verifiable independent witness, or **do not ship that plan**; E is deliberately last so the code generalization is already proven and the sprint is a pure data project; gated on OQ-5 second-source availability. | Riley / Owner |
| R-ALT-5 | **Bible Companion parity (the cross-cutting bar).** Every sprint changes shared signatures (the loader, the classifier, the progress repo, the formatter); a regression for the default user is the highest-frequency risk. | A no-op-for-the-default-user change silently alters the Bible Companion experience. | §1.2 parity gate on **every** sprint: the full existing suite (651 + the three standing gates) passes UNCHANGED with the active plan defaulted to `bible_companion`; a red pin on such a change is a **design defect**, not a test to update; additive signatures default to the BC id. | All / Morgan |
| D-ALT-A | **A is serial-first** (no second plan, no active-plan spine, until A lands). | Nothing downstream is verifiable against a second plan before A. | A's code track + data track run in parallel and converge on the green gate; B/C/D/E build on a trusted second plan. | Morgan |
| D-ALT-B | **The M'Cheyne / chronological `*-rebuild` CI jobs.** A hand-edited binary/JSON asset sneaks past the commit. | A drifted plan asset ships. | `mcheyne-rebuild` (SA-T11) / `chronological-rebuild` (SE-T3) re-derive + byte-diff, mirroring `data-rebuild`; release blocked on them + the gates. | Jordan |

---

## 6. Owner decision checkpoints

The remaining owner calls are **not on the critical path** and must **not** block Sprints A, B, or C
(ESpec §11). The three shape-determining questions (OQ-2 which app / OQ-3 progress-on-switch / OQ-4
anchoring) are **already resolved** (PRD owner decisions, 2026-06-16). The rest gate the *data assets*
and the *D presentation*. Resolve on this schedule:

| OQ | Question | Owner | Must be resolved before | Code impact |
|---|---|---|---|---|
| **OQ-5** | **The recurring per-plan data burden + second-source availability**, re-confirmed at *each* plan's sourcing. A plan without a trustworthy second source **cannot ship** (FR-ALT-3). | Owner + data/QA | **Sprint A** (M'Cheyne asset, SA-T7) **and re-confirmed at Sprint E** (the chronological asset, SE-T1) | None on the schema/code; gates whether the *asset* exists. |
| **OQ-MC** | **Which published M'Cheyne form** — 2-stream or 4-stream. The sourcing doc recommends the **classic 4-stream date-anchored 365-day form**. | Owner + data | **Sprint A** (gates only the M'Cheyne asset shape, SA-T7) | None — the code is N-agnostic; it fixes the M'Cheyne descriptor's `streams[]`. |
| **OQ-7** | **Selector placement / first-run + the day-screen plan-label visibility.** Settings is mandatory; a first-run plan question only if it stays light + defaults cleanly to the Bible Companion (the G1/M2 zero-setup promise). | Owner + Maya + Priya | **Sprint D** (gates the selector *presentation* — SD-T1/T6/T7) | Whether a first-run plan step ships; the day-screen label affordance. Does **not** gate the data model, the migration, or the N-stream generalization. |

> **Flagged for the owner, not invented here:** OQ-MC and OQ-5 are genuine data/product calls resolved
> at M'Cheyne sourcing (Sprint A's data track) — the recommended **4-stream classic form** + the
> confirmed Carson/TGC second witness are the proposed defaults if no other answer arrives. OQ-7
> (first-run vs Settings-only) is Priya + owner at D's UI time, with **Settings-only + a discoverable
> active-plan label** as the recommended default. None of these touches Sprint A's code track, Sprint
> B, or Sprint C.

---

*End of the alternate-schedules execution plan. Sprint A is fully ticketed above (the immediate next
work) and Sprint B is fully ticketed (the high-risk next); Sprints C–E are sketched at goal + ticket-
title level — each is decomposed into full tickets at the start of its own session, once A/B land and
any blocking OQ (§6) is resolved.*
