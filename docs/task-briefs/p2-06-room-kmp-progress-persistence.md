# p2-06 — `ProgressDatabase` on Room KMP. The one change that can destroy user data.

> **Assignee:** Senior Shared-Core Engineer (drives) + Verification Engineer (the PG gates)
> **Release:** 1.11.0 · **Merge order:** Tranche A, after `p2-04`/`p2-05`. **Parallel with `p2-07`**
> — disjoint write sets, but neither merges without its PG gate green.
> **Inherits:** [`p2-00-overview.md`](p2-00-overview.md) rules R1–R9, **especially R7**.
> **Preconditions:** **Gate 0 V2 returned `IDENTICAL`** (`gate0-schema-tripwire.md`).
> **`p1-08`'s fixtures are committed.**
> **Executes:** ADR-0008.

---

## Objective

Move `ProgressDatabase` into `shared/data` on Room KMP, **with the schema, the identity hash, the
migration and the observable behaviour bit-for-bit unchanged** — and prove it with **fixtures from
a real shipped device**, not with reasoning about hashes.

---

## Context

**Read this paragraph before writing any code.** Daily Bible Reading Planner has been live since
2026-07-23 — 1.8.1 / 10801, 100% rollout, 177 countries. Real users have irreplaceable reading
history in one table, at schema **version 2**, with `fallbackToDestructiveMigration` deliberately
**OFF** so that a failed migration is a **loud crash, never silent data loss**.

```sql
reading_progress(plan_id TEXT NOT NULL, dateEpochDay INTEGER NOT NULL,
                 stream INTEGER NOT NULL, readAtEpochMillis INTEGER NOT NULL,
                 PRIMARY KEY(plan_id, dateEpochDay, stream))
```

**The asymmetry that governs everything here: iOS starts empty. Every migration risk in this port
is an ANDROID risk. The port cannot make iOS worse; it can absolutely brick Android.**

The v1→v2 migration (`data/progress/ProgressMigrations.kt`) is hand-written recreate-and-copy —
SQLite cannot alter a primary key in place — stamping every existing row with `'bible_companion'`.
**Devices still exist in the wild at v1** (anyone who has not updated since before 1.5.0), so the
v1 path must keep working **forever**.

### The four hard rules (ADR-0008), restated because they are absolute

1. **`ProgressDatabase` stays at version 2.** The port introduces no schema change. A wanted schema
   change ships in a **separate release after** the port.
2. **`exportSchema = true` stays on, and `app/schemas/…/ProgressDatabase/2.json` stays
   byte-identical.** That file is the tripwire. **If it changes at all — stop and escalate. Do not
   regenerate the baseline.** A changed `2.json` means the identity hash changed, which means every
   existing user's database is rejected on open.
3. **`fallbackToDestructiveMigration` stays off.** Not negotiable, not "temporarily during
   development." It converts a loud, diagnosable crash into the silent deletion of every user's
   reading history — the opposite of a safety net.
4. **`MIGRATION_1_2` is preserved verbatim in behaviour.** Its SQL is rewritten *only* insofar as
   Room KMP's callback takes `SQLiteConnection` rather than `SupportSQLiteDatabase`. **The four
   statements and the `'bible_companion'` literal do not change.**

### The driver decision, and why it is not the obvious one

**`AndroidSQLiteDriver` on Android. `BundledSQLiteDriver` on iOS** (tranche B).

The tempting move is `BundledSQLiteDriver` everywhere for uniformity. **Reject it.** Swapping the
SQLite engine underneath live production user data buys nothing and costs ~1.5–3 MB per ABI. Android
users keep byte-identical behaviour on the engine their data was written by; iOS, which starts
empty, gets the bundled driver where uniformity actually helps.

---

## Contract

### 1. What moves and what does not

| Item | Destination |
|---|---|
| `ReadingProgressEntity`, `ProgressDatabase`, `ReadingProgressDao` (12 queries), `ProgressMigrations` | `shared/data/src/commonMain` |
| The Room builder + driver | `shared/data`, driver supplied per platform |
| The database file path | via **`AppFilePaths.databases`** (`p2-03` A) |
| Exported schemas `app/schemas/**`, wired as **debug-only assets** | **stays in `:app`.** Android-only mechanism, verified absent from the release AAB (0 entries) and present in the debug APK (2). **Keep that property.** |
| `ProgressMigrationTest`, `ProgressMigrationNoPerceptibleChangeTest` (`MigrationTestHelper`) | **stays `androidUnitTest`.** Migration is an Android-only concern and an Android-only migration test is **entirely correct** (PG-4), not a compromise. |
| `ProgressRepositoryImpl` and the ~10 use-case callers | `shared/data` / already-moved `shared/domain` |

**Entity, DAO and migration change ONLY in package declaration, imports, and the migration
callback's parameter type.** Nothing else. Prove it with `git diff -M`.

### 2. The `MIGRATION_1_2` translation

`SupportSQLiteDatabase.execSQL(...)` → `SQLiteConnection.execSQL(...)`. **The SQL strings are
copied, not retyped.** The literal must remain sourced from the shared
`ReadingProgressEntity.DEFAULT_PLAN_ID == PlanRegistry.DEFAULT_PLAN_ID` constant — that anti-drift
pin already exists and must survive.

### 3. The gates — none of these is optional, and none merges without the others

**PG-1 — `ProgressFixtureOpenTest`** (from `p1-08`). The **real 1.8.1 device** `progress.db` opens
on the ported build and returns identical results from **every** DAO query: `streamsRead`,
`readCountsInRange`, `allReadCounts`, `streamCountsInRange`, `marksInRange`, `hasAnyMarks`. Exact
row count; every `(plan_id, dateEpochDay, stream, readAtEpochMillis)` tuple; **per-plan isolation.**

> The existing `ProgressMigrationTest` **synthesizes** a v1 database and therefore does **not**
> prove this. That distinction is the reason `p1-08` exists.

**PG-3 — the `2.json` byte-identity assertion** in CI. If it fires, **stop and escalate.**

**PG-4 — `ProgressMigrationTest` and `ProgressMigrationNoPerceptibleChangeTest` pass**, on Android,
with `MigrationTestHelper` reading the real exported schemas from the debug-only assets.

**Plus: `hasAnyMarks()` stays GLOBAL.** `SELECT EXISTS(... reading_progress)`, **no plan filter** —
it is the per-device "fresh install" signal that gates the first-run tracking-start prompt and the
reading-destination question (D-ALT-15, D-V3-19). Scoping it to a plan would make an existing user
with M'Cheyne selected look like a fresh install and **re-fire the first-run dialogs.** There is a
mutation pinned on exactly this; re-verify it.

---

## Acceptance criteria

1. `app/schemas/…/ProgressDatabase/2.json` is **byte-identical.** `git status` clean; checksum
   quoted in the PR. Same for `1.json`.
2. `ProgressDatabase` is still `version = 2`, `exportSchema = true`.
3. `grep -rn "fallbackToDestructiveMigration" shared/ app/` shows it **only** where it is explicitly
   `false`.
4. `git diff -M` on the entity, DAO and migration shows **only** package/import changes and the
   migration callback's parameter type.
5. **PG-1 passes** against the real 1.8.1 fixture — every DAO query, exact row count, every tuple,
   per-plan isolation.
6. **PG-3 runs in CI** and has been demonstrated failing on a one-byte edit, then restored.
7. **PG-4 passes**, both migration tests, on Android.
8. **`hasAnyMarks()` is global**, and the mutation "scope it to the active plan" is killed by its
   intended test and restored byte-identically.
9. `AndroidSQLiteDriver` is used on Android. State it explicitly and say why (byte-parity for
   shipped users, zero size cost).
10. **≥4 killed mutations, each restored byte-identically:** (a) drop the row copy in
    `MIGRATION_1_2` → the migration tests red; (b) change the `'bible_companion'` stamp → red;
    (c) `hasAnyMarks` scoped instead of global → red; (d) a `ReadingProgressDao` query loses its
    `plan_id = :planId` clause → the plan-isolation tests red.
11. **Test count unchanged. Zero deletions.** State before/after.
12. Full pipeline green; Kover ≥ the current floor.
13. **The six data gates untouched: 11 / 10 / 8 / 6 / 18 / 5.**
14. `bundleRelease` clean; **AAB size reported** — the driver choice is a size decision and the
    number is the evidence. Confirm the debug-only schemas are still **absent from the release AAB**
    (0 entries) and **present in the debug APK** (2).
15. **R8 device smoke — with the upgrade-in-place step, which is the whole point (R8):**
    1. Install **1.10.0** from Play or a release APK. Mark readings across **two plans** and
       **two years**. Set several settings away from default.
    2. **Upgrade in place** to the 1.11.0 release build — do **not** uninstall.
    3. Confirm: **every mark survives**, on both plans; the stats panel shows the same streak and
       year numbers; the picker dots are unchanged; the year strips are unchanged; the widget shows
       the same state.
    4. Confirm the **first-run dialogs do NOT re-fire** (`hasAnyMarks` still global).
    5. Force-stop, relaunch, confirm again.
    **A fresh install proves nothing about a relocation. Do the upgrade.**
16. **Watch Play vitals for 24–72 h after the 1.11.0 rollout** before any tranche B work is tagged.

---

## Boundaries / write set

**Yours:**
- `shared/data/src/commonMain/.../progress/**` (created by `git mv`)
- `app/src/main/kotlin/.../data/progress/**` (emptied)
- `app/src/test/.../data/progress/**` — migration tests stay, fixture test lands
- The Koin declarations for the database, driver and DAO

**Not yours:**
- **`app/schemas/**` — read and checksum only. NEVER regenerate.** Point `room.schemaLocation`
  wherever the build needs, but the committed baselines are not writable.
- **`data/prefs/**`** — **`p2-07`**, running in parallel. Both stores must not move in one commit.
- `bible/data/**` — **`p2-05`**. Different database, different ADR, different risk.
- `app/build.gradle.kts` — **Build & Release** owns the `room.schemaLocation` and debug-asset
  wiring. Specify what you need; do not edit it.
- The `p1-08` fixture files — read-only.

---

## Escalation triggers

- **`2.json` changes in any way** → **Staff + Owner**, blocking, immediately. **Do not regenerate
  the baseline.** This is the tripwire doing its job.
- **PG-1 fails** → **Staff + Owner**, blocking. A real shipped database no longer opens. This is
  the highest-severity event available in the entire port.
- **`MIGRATION_1_2` cannot be expressed without changing its SQL** → **Staff**, blocking.
- **Anyone suggests `fallbackToDestructiveMigration`, for any reason, however temporary** →
  **Staff + EM**. Named as a refusal in the signed-off approach (§9 item 8). The correct response
  to being blocked is to escalate, not to disarm the guard.
- **Anyone suggests "taking the opportunity" to clean up the schema** — drop `readAtEpochMillis`,
  add an index, normalise `plan_id` → **refuse**, file the improvement, ship it later. Rejected
  firmly in ADR-0008. Refactor-during-port is how ports fail.
- **`BundledSQLiteDriver` is proposed for Android** → **Staff**. It swaps the engine under live user
  data for no benefit.
