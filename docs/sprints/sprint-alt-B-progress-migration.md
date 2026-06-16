# Alternate-Schedules Sprint B — per-plan progress Room migration

**Track:** Alternate Reading Schedules (multi-plan). **Sprint:** B (the HIGH-RISK, isolated sprint —
the only place existing user data is rewritten). **Status:** DONE, uncommitted in the working tree
(the main session independently verifies the migration test + commits). **Version:** untouched (no
bump — Sprint B renders no UI and changes no user-facing behavior).
**Plan:** [docs/EXECUTION_PLAN-alternate-schedules.md](../EXECUTION_PLAN-alternate-schedules.md) §3 (SB-T1…T6).
**Eng spec:** [docs/ENGINEERING_SPEC-alternate-schedules.md](../ENGINEERING_SPEC-alternate-schedules.md) §5 (D-ALT-12…15).
**Predecessor:** [sprint-alt-A-plan-foundation.md](sprint-alt-A-plan-foundation.md).

## Goal outcome — MET

**The progress store is per-plan, proven lossless.** `reading_progress` gained a `plan_id`
dimension via a non-destructive, exported, migration-tested `MIGRATION_1_2` that stamps every
existing mark `bible_companion`; every `ProgressRepository`/DAO method is plan-scoped, defaulted to
the active plan. NO UI, NO completion/stats/streak logic, NO `Stream`-enum change (all Sprint C).

## Current capability (the headline)

**An upgrading Bible Companion user keeps every mark, perceives no migration, and the store can now
isolate a second plan's marks** — the per-plan plumbing is correct and bulletproof before anything
renders against it. A developer can prove on the JVM (Robolectric) that the 1→2 migration loses zero
rows and that two plans' marks never bleed together.

## What landed (tickets)

| Ticket | What | Result |
|---|---|---|
| SB-T1 | `plan_id` column + v2 PK + version bump + shared constant | `ReadingProgressEntity` gains `@ColumnInfo(name="plan_id") val planId`; PK `(plan_id, dateEpochDay, stream)`; `ProgressDatabase version=2`; `ReadingProgressEntity.DEFAULT_PLAN_ID == PlanRegistry.DEFAULT_PLAN_ID` (anti-drift pin). |
| SB-T2 | `MIGRATION_1_2` recreate-and-copy | `data/progress/ProgressMigrations.kt`; zero-loss column-add-with-stamp; registered on the `DataModule` builder; `fallbackToDestructiveMigration` OFF. |
| SB-T3 | Exported `2.json` + test wiring | `2.json` checked in; `androidx.room:room-testing` added to the catalog (test scope); schemas wired onto the Robolectric unit-test asset path as DEBUG-only assets. |
| SB-T4 | THE zero-loss `MigrationTestHelper` test | `ProgressMigrationTest` (2 tests): real exported schemas, multi-year/whole+partial/Feb-29-adjacent seed; row-count + every-tuple + stamp + `validateMigration`. 3 migration mutations killed. |
| SB-T5 | Plan-scope every repo/DAO method | every DAO query `WHERE plan_id = :planId`; every repo method `planId` defaulted to `DEFAULT_PLAN_ID`; `hasAnyMarks()` + tracking-start GLOBAL (D-ALT-15). `ProgressRepositoryPlanScopeTest` (4): isolation, per-plan counts/clearYear, GLOBAL-hasAnyMarks, default-arg parity. 3 scope/parity mutations killed. |
| SB-T6 | "No perceptible migration for the default user" | `ProgressMigrationNoPerceptibleChangeTest` (1): migrated v1 DB read through the NEW repo (default plan) == v1 result. |

## The migration design (D-ALT-13) — the exact `MIGRATION_1_2`

`data/progress/ProgressMigrations.kt`. The PK change forces recreate-and-copy (SQLite cannot alter a
PK in place). Every row is touched exactly once and stamped the flagship id — zero loss.

```sql
-- 1. v2 table (DDL byte-matches Room's generated v2 createSql so validateMigration passes).
CREATE TABLE IF NOT EXISTS `reading_progress_new` (
  `plan_id` TEXT NOT NULL,
  `dateEpochDay` INTEGER NOT NULL,
  `stream` INTEGER NOT NULL,
  `readAtEpochMillis` INTEGER NOT NULL,
  PRIMARY KEY(`plan_id`, `dateEpochDay`, `stream`));
-- 2. Copy EVERY existing row, stamping the flagship id (the literal is the MEANING of the data).
INSERT INTO `reading_progress_new` (`plan_id`, `dateEpochDay`, `stream`, `readAtEpochMillis`)
  SELECT 'bible_companion', `dateEpochDay`, `stream`, `readAtEpochMillis` FROM `reading_progress`;
-- 3. Replace.
DROP TABLE `reading_progress`;
ALTER TABLE `reading_progress_new` RENAME TO `reading_progress`;
```

- The `'bible_companion'` literal is built from `ReadingProgressEntity.DEFAULT_PLAN_ID`
  (`== PlanRegistry.DEFAULT_PLAN_ID`), NOT a schema-level column `DEFAULT` — it is the meaning of the
  existing data, asserted equal to the registry default by `ReadingProgressEntityConstantTest`.
- Registered: `Room.databaseBuilder(...).addMigrations(ProgressMigrations.MIGRATION_1_2).build()` in
  `DataModule.provideProgressDatabase`. **`fallbackToDestructiveMigration` is NOT enabled** (D-V3-15).

## The schema v2 diff (`app/schemas/.../ProgressDatabase/2.json` vs `1.json`)

- `version`: 1 → 2; new `identityHash`.
- `reading_progress.createSql`: gains a leading `\`plan_id\` TEXT NOT NULL,` column and
  `PRIMARY KEY(\`plan_id\`, \`dateEpochDay\`, \`stream\`)` (was `PRIMARY KEY(\`dateEpochDay\`, \`stream\`)`).
- new field `{ fieldPath: "planId", columnName: "plan_id", affinity: "TEXT", notNull: true }`.
- `primaryKey.columnNames`: `["plan_id","dateEpochDay","stream"]`.
- Every other column/affinity is unchanged. **2.json is checked in** (the build writes it; it MUST be
  committed — it is the baseline a future v3 migration migrates from, and the migration test reads it).

## The zero-loss migration test + mutation evidence

`ProgressMigrationTest` uses Room's `MigrationTestHelper` against the **real exported schemas** (not a
hand-typed DDL): seeds a v1 DB (2026-01-01 whole day, 2026-06-10 partial, 2024-02-28 Feb-29-adjacent
whole day, 2027-12-31 cross-year partial), runs `MIGRATION_1_2`, asserts (a) row count identical,
(b) every `(dateEpochDay, stream, readAtEpochMillis)` tuple preserved with `plan_id='bible_companion'`,
(c) Room's `validateMigration` passes for v2. **Proven to FAIL if the migration is broken:**

- Drop the `INSERT … SELECT` (`if (false) …`) → all rows lost → zero-loss + no-perceptible tests RED.
- Stamp `'wrong_plan'` → the `plan_id='bible_companion'` assertion RED.
- Copy `readAtEpochMillis` as `0` → the tuple-preservation assertion RED.

Each mutation was applied, the test reddened, and `ProgressMigrations.kt` was restored byte-identically
(`diff -q` clean).

## The plan-scoping of the repo (D-ALT-12) + the GLOBAL decision (D-ALT-15)

- **DAO** (`ReadingProgressDao`): `streamsRead`/`readCountsInRange`/`allReadCounts`/`streamCountsInRange`/
  `marksInRange`/`delete`/`deleteDay`/`deleteRange` each gained a `planId: String` first parameter and a
  `WHERE plan_id = :planId AND …` clause. `upsert` carries `planId` in the entity.
- **Interface** (`ProgressRepository`): every method gained `planId: String = PlanRegistry.DEFAULT_PLAN_ID`
  — **additive with a default**, so the ~10 use-case callers (`GetDayReadingsUseCase`,
  `GetReadingStatsUseCase`, `GetYearStripsUseCase`, `GetMonthCompletionUseCase`, `ToggleReadingUseCase`,
  `MarkWholeDayUseCase`, `ResetYearProgressUseCase`, the three prompt/upgrade use cases) and the 13-test
  `ProgressRepositoryTest` compile and behave byte-identically. The interface STILL speaks the `Stream`
  enum — retiring it is Sprint C (D-ALT-5).
- **D-ALT-15 (confirmed, pinned):** `hasAnyMarks()` stays GLOBAL — `SELECT EXISTS(SELECT 1 FROM
  reading_progress LIMIT 1)`, **no `plan_id` filter** (a mark under *any* plan means "not a fresh
  install"; the tracking-start default reads this). The tracking-start date stays global (a
  `SettingsRepository`/DataStore calendar concept, untouched). `ProgressRepositoryPlanScopeTest` pins
  this with a NON-default-plan mark making `hasAnyMarks()` true.
- **Scope mutations killed:** the impl hard-coding the plan id (ignoring the param → isolation test RED);
  `hasAnyRows` scoped to the flagship (breaks GLOBAL → GLOBAL test RED); the interface default flipped to
  a bogus id (no-`planId` callers read the wrong partition → SB-T6 + parity RED).

## The schema-asset wiring (the one build subtlety worth knowing)

Room's `MigrationTestHelper` (Robolectric) loads the exported schema JSONs from the **debug unit-test
resource APK** (Robolectric reads `test_config.properties`'s `android_resource_apk`, built from
`mergeDebugAssets`). Neither the `test` source set's `assets.srcDir` nor a `doFirst` copy into
`mergeDebugAssets` works (the resource APK is packaged before, and a separate copy task into that
shared dir trips Gradle 9's producer/consumer validation + would pollute the real APK). **Solution:
the exported `app/schemas` are added as DEBUG-only assets** —
`android.sourceSets.getByName("debug").assets.srcDir(layout.projectDirectory.dir("schemas"))`. The
`debug` source set feeds the debug build (and thus Robolectric's debug resource APK) but is **excluded
from the release AAB**. Verified: **release AAB = 0 `ProgressDatabase` schema entries; debug APK = 2**
(`1.json`, `2.json`). The ~3 KB of schema JSON never ships to users. The `schemas` dir is also declared
a `roomSchemas` test input (Sprint-1 lesson: undeclared inputs are skipped UP-TO-DATE; a schema regen
must re-run the gate).

## State of the codebase

- **Changed (data/progress):** `ReadingProgressEntity` (+`planId`, v2 PK, `DEFAULT_PLAN_ID` constant),
  `ProgressDatabase` (`version=2`), `ReadingProgressDao` (plan-scoped queries; `hasAnyRows` GLOBAL),
  `ProgressRepository` (+`planId` default param per method; `hasAnyMarks` global), `ProgressRepositoryImpl`
  (plan-scoped DAO calls). **New:** `ProgressMigrations.kt` (`MIGRATION_1_2`).
- **DI:** `DataModule.provideProgressDatabase` registers the migration (no destructive fallback).
- **Schema:** `app/schemas/.../ProgressDatabase/2.json` checked in.
- **Tests:** new `ProgressMigrationTest` (2), `ProgressRepositoryPlanScopeTest` (4),
  `ProgressMigrationNoPerceptibleChangeTest` (1), `ReadingProgressEntityConstantTest` (1). The
  `FakeProgressRepository` in `domain/Fakes.kt` gained `planId` params on every override (single-plan
  model, ignores `planId` — every use-case test reads the default plan; two-plan isolation is proven
  against real Room, not the fake). `ProgressRepositoryTest` (13) UNCHANGED (parity).
- **Catalog:** `androidx-room-testing` (version.ref `room`); `app/build.gradle.kts`
  `testImplementation(libs.androidx.room.testing)` + the debug-assets schema wiring + the `roomSchemas`
  test input.

## Verification

- **Full pipeline GREEN from clean** (`spotlessCheck lintDebug assembleDebug testDebugUnitTest
  koverXmlReportAppDebug koverVerifyAppDebug`) and `bundleRelease` clean.
- **685 tests, 0 failures** (`--rerun-tasks`; baseline 677 → +8 net). The four data/Room gates
  UNCHANGED: **ReadingPlanVerificationTest 11, McheynePlanVerificationTest 10, BibleTextVerificationTest
  18, BibleDatabaseRoomOpenTest 5**. `ProgressRepositoryTest` 13 parity-green.
- **Kover 95.8%** on domain/data (floor 70%).
- **6 mutations killed**, each restored in place (3 migration: drop row copy / wrong stamp / corrupt
  millis; 3 scope+parity: impl hard-codes id / hasAnyRows scoped / default planId bogus).
- Release AAB verified free of schema JSON (0 entries); debug APK carries it (2) for the test.

## Carryover & next goal — Alt Sprint C (N-stream UI generalization)

**Sprint C consumes B's per-plan store. What C needs to know:**

- **The per-plan store is ready and defaulted.** `ProgressRepository` methods take `planId` (defaulted
  to `bible_companion`). In C/D, thread the **live** active plan id — `ActivePlanRepository.activePlanId`
  (from Sprint A) — into the stat use cases' repo calls (`GetReadingStatsUseCase`,
  `GetYearStripsUseCase`, `GetMonthCompletionUseCase`, `GetDayReadingsUseCase`) so a plan switch re-emits
  per-plan progress live. The plumbing is in place; C just passes the id (or `combine`s the active flow).
- **`Stream` enum retirement (D-ALT-5) is C, and it touches this store.** The `ProgressRepository`
  interface + impl + `FakeProgressRepository` currently map the `stream` int through `Stream.fromNumber`.
  When the enum retires, the repo's `Stream` types become `Int`/`StreamNumber`; the `plan_id` scoping is
  orthogonal and unaffected. The `stream` COLUMN is unchanged (already an int) — no further schema change.
- **`DayCompletionClassifier.STREAM_COUNT` → `streamCount: Int` param (D-ALT-6)** flows the active plan's
  `descriptor.streams.size`; the per-plan `readCounts`/`allReadCounts`/`streamCounts`/`streamMarks` are
  already the per-plan inputs the classifier-driven use cases read.
- **No further Room/schema/migration work in C/D/E** beyond consuming this store. The only down-stream
  schema risk is retired — this was the one migration.

**Scope deliberately protected OUT of B** (queued, not absorbed): the `Stream`-enum retirement +
classifier parameterization + N-stream UI (Sprint C); the selector / switch dialog / live active-plan
threading / widget refresh (Sprint D); the chronological plan (Sprint E). B added the `ActivePlanRepository`
default seam but deliberately did NOT thread the live active id into use cases (that is C/D — B is the
storage spine + migration only; resolving the live id here would be a no-op suspend surface, since no UI
selects a non-default plan yet).

## Open questions & risks

- **The real migrated-history upgrade on a device** is the one un-JVM-provable item — rolled into the Alt
  Sprint E consolidated device pass (R-ALT-1). The JVM gate (`MigrationTestHelper` + the no-perceptible
  test) covers the logic; only the on-glass upgrade remains.
- **No M'Cheyne marks can be written through a live path yet** (D-ALT-15 carryover from A): the M'Cheyne
  `Portion` body still can't be built until the `Stream` enum retires (C). The per-plan STORE accepts any
  `planId` string; nothing in B writes a non-default plan in production (no UI). The two-plan isolation
  test exercises the store directly with `planId = "mcheyne"`.
- No new permissions, no INTERNET, no manifest change, zero net-new RUNTIME deps (room-testing is TEST
  scope). No version bump.

## Next sprint

next: sprint-alt-C-n-stream-ui
