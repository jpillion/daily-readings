# Gate 0 · V2 — Is Room KMP's exported `ProgressDatabase/2.json` byte-identical to the committed one?

> **Brief type:** Gate 0 spike. Timeboxed, throwaway, output is an **answer**, not code.
> **Open questions are the point of this brief.**
>
> **Assignee:** Senior Shared-Core Engineer
> **Merge-order position:** **Gate 0, item 3 of 4.** Runs immediately after — or alongside — the
> identity-hash spike; it is the same throwaway module, a second entity.
> **Timebox: half a day** on top of the V1 module.
> **Decides:** ADR-0008. **Blocks the entire persistence phase (`p2-06`, `p2-07`, release 1.11.0)
> if it fails.**

---

## Objective

Determine whether the Room KMP code generator, given the **unchanged** `ReadingProgressEntity`
and `ProgressDatabase`, exports a schema JSON **byte-identical** to the one committed at
`app/schemas/com.jpillion.dailyreadingplanner.data.progress.ProgressDatabase/2.json`.

Answer `IDENTICAL` or `DIFFERS`, and if it differs, say **exactly what differs and whether the
`identityHash` field is among the differences.**

---

## Context

**This is the only database in the app that holds irreplaceable user data.** Daily Bible Reading
Planner has been live in production since 2026-07-23 (1.8.1 / 10801, 100% rollout, 177 countries).
Real users have real reading history in one table:

```sql
reading_progress(plan_id TEXT NOT NULL, dateEpochDay INTEGER NOT NULL,
                 stream INTEGER NOT NULL, readAtEpochMillis INTEGER NOT NULL,
                 PRIMARY KEY(plan_id, dateEpochDay, stream))
```

`ProgressDatabase` is at **version 2**, `exportSchema = true`, schemas checked in at
`app/schemas/…/ProgressDatabase/{1,2}.json`, and **`fallbackToDestructiveMigration` is
deliberately OFF** (D-V3-15): a failed migration must be a loud crash, never silent data loss.

Room validates an opened database against a schema **identity hash** derived from the entity
definitions. **If that hash changes, every existing user's database is rejected on open** — the
app update becomes crash-on-open for the entire installed base.

Moving `ReadingProgressEntity` into a `shared/data` module does **not by itself** change the hash:
it is computed from table name, column names/types/nullability/defaults, primary key, indices and
foreign keys — **none of which are package-sensitive.** But changing the Room version, the
processor, or any column attribute **does**. And this project has already been bitten once by a
hash mismatch on the *other* database (sprint-00F, shipped to production).

ADR-0008 makes `2.json` the **tripwire**:

> **PG-3.** `app/schemas/…/ProgressDatabase/2.json` is byte-identical before and after the port.
> **If it changes at all, stop and escalate — do not regenerate the baseline.**

This spike runs that tripwire *before* anyone writes persistence code, so a failure costs half a
day instead of a phase.

Read-only inputs:
- `app/src/main/kotlin/com/jpillion/dailyreadingplanner/data/progress/ReadingProgressEntity.kt`
- `.../data/progress/ProgressDatabase.kt`
- `.../data/progress/ReadingProgressDao.kt` (12 queries)
- `.../data/progress/ProgressMigrations.kt` (`MIGRATION_1_2`)
- `app/schemas/…/ProgressDatabase/{1,2}.json` — **the baseline. Never edit these.**
- `app/build.gradle.kts:130` (`room.schemaLocation`) and `:36` (debug-only schema assets)

---

## Contract

### What you build

Extend the **same throwaway module** from `gate0-room-identity-hash.md` with **byte-identical
copies** of `ReadingProgressEntity`, `ReadingProgressDao` and `ProgressDatabase` — same table
name, same column names and `@ColumnInfo` values, same types, same nullability, same composite
primary key **in the same order**, same `version = 2`, same `exportSchema = true`.

> **Copy, do not import, do not tidy.** The composite PK order `(plan_id, dateEpochDay, stream)`
> is part of the hash. A "cleaner" ordering is a different database.

Configure `room.schemaLocation` to a scratch directory. Build for **JVM** and
**`iosSimulatorArm64`**, so you learn whether the export is stable *across generators* and not
merely different-from-Android.

### The comparison

1. `diff` the generated `2.json` against the committed one. Report the diff verbatim if any.
2. **Separately and explicitly**, compare the `identityHash` field. This is the field that decides
   whether shipped users crash; a diff in formatting or in `"formatVersion"` is a completely
   different severity from a diff in `identityHash`, and the report must not blur them.
3. Do the same for `1.json`. **Devices still exist in the wild at v1** — a user who has not
   updated since before 1.5.0 — and the v1→v2 path must keep working forever (ADR-0008 rule 4).

### Also answer, while you are here

- **⟦VERIFY⟧ V4: is `androidx.room:room-testing`'s `MigrationTestHelper` available outside
  Android?** `ProgressMigrationTest` and `ProgressMigrationNoPerceptibleChangeTest` depend on it
  and on the exported schemas being readable as debug-only assets.
  **A "no" here is a perfectly good answer** — migration is an Android-only concern, and an
  Android-only migration test is entirely correct (ADR-0008, PG-4). Do not treat "no" as a
  problem to solve.
- Does Room KMP's migration callback take `SQLiteConnection` in place of `SupportSQLiteDatabase`,
  and do `MIGRATION_1_2`'s four statements translate **without changing a single SQL string**
  (including the `'bible_companion'` literal)?

### The deliverable

A `## Result` section appended to this file:

1. `IDENTICAL` or `DIFFERS`, per target, for `2.json` and `1.json`.
2. If `DIFFERS`: the full diff, and a **separate** yes/no on whether `identityHash` is among the
   differences.
3. `MigrationTestHelper` availability outside Android: yes / no.
4. Whether `MIGRATION_1_2`'s SQL survives verbatim.

---

## What answer would kill or reshape the port

| Outcome | Consequence |
|---|---|
| **IDENTICAL on both targets** | ADR-0008 is accepted. The persistence phase proceeds with `2.json` as a **CI-asserted byte-level tripwire** for the rest of the program. |
| **DIFFERS, but `identityHash` is UNCHANGED** (e.g. `formatVersion`, key ordering, whitespace) | **Not fatal, and not to be waved through either.** Shipped users still open their database. But `2.json` can no longer be a byte-diff tripwire, and something weaker has to replace it — **assert the `identityHash` field alone**, which is the value that actually protects users. **Staff decides**, not the spike. |
| **DIFFERS and `identityHash` CHANGED** | **STOP. This blocks the entire persistence phase and the 1.11.0 release.** It means every shipped Android user's database would be rejected on open. Do not proceed, do not "regenerate the baseline", do not add `fallbackToDestructiveMigration` (see below). Staff and the owner decide the path. |
| **`identityHash` differs between JVM and iOS targets** | Odd but not user-affecting on its own — iOS starts with an empty database and nothing to migrate. It **does** mean the exported schema cannot be a single checked-in artifact, and it is a warning sign about the same generator that ADR-0007 depends on. Report loudly. |

### The anti-pattern that must not be reached for

> **Do not add `fallbackToDestructiveMigration` "just for the port" or "temporarily during
> development."** It converts a loud, diagnosable crash into the **silent deletion of every user's
> reading history.** It is named as a refusal in the signed-off approach (§9 item 8) and in
> ADR-0008. If a spike or a task is blocked such that this looks attractive, that is precisely the
> moment to escalate instead.

### The asymmetry worth remembering

**Every migration risk in this port is an Android risk.** iOS starts empty; there is nothing to
migrate and nothing to lose. The port cannot make iOS worse; it can absolutely brick Android.

---

## Acceptance criteria

1. `IDENTICAL` / `DIFFERS` stated for `2.json` **and** `1.json`, for **both** targets.
2. If `DIFFERS`, the full diff is quoted, and the `identityHash` question is answered **separately
   and explicitly** — not left to be inferred from the diff.
3. `MigrationTestHelper` availability outside Android answered yes / no, with the coordinate tried.
4. `MIGRATION_1_2` SQL-survives-verbatim answered yes / no.
5. **`app/schemas/**` is byte-identical to its state before this spike.** Verify with `git status`
   and say so in the report.
6. **Zero files under `app/` are modified.**
7. The throwaway module is deleted or lives on a never-merged branch.

---

## Boundaries / write set

**You may write:**
- The throwaway module from `gate0-room-identity-hash.md` (extended), on a never-merged branch.
- A scratch schema-output directory **outside** `app/schemas/`.
- The `## Result` section of **this file**.

**You may NOT write:**
- **`app/schemas/**` — under any circumstance.** These files are the baseline the tripwire
  compares against. Overwriting them destroys the only evidence that anything changed. Point
  `room.schemaLocation` somewhere else.
- Anything else under `app/`, `tools/`, `gradle/`, `.github/`.
- `gradle/libs.versions.toml` — dependency questions go to **Build & Release**.
- Any ADR.

---

## Escalation triggers

- **`identityHash` differs for `ProgressDatabase`** → **Staff + Owner**, blocking, immediately.
  This is a "shipped users crash on open" finding and it outranks everything else in Gate 0.
- **`2.json` differs in any way** → **Staff**, blocking. Staff decides whether the tripwire is
  relaxed to `identityHash`-only.
- **`MIGRATION_1_2` cannot be expressed without changing its SQL** → **Staff**, blocking. ADR-0008
  rule 4 says the four statements and the `'bible_companion'` literal do not change.
- **You are tempted to regenerate the committed baseline** → escalate instead. That instinct is the
  exact failure this brief exists to prevent.
- **Timebox exceeded (half a day on top of V1)** → report what you have.

---

## Result

*(To be completed by the assignee.)*

| File | Target | IDENTICAL / DIFFERS | `identityHash` changed? |
|---|---|---|---|
| `2.json` | jvm | | |
| `2.json` | iosSimulatorArm64 | | |
| `1.json` | jvm | | |
| `1.json` | iosSimulatorArm64 | | |

Diff (if any):

- `MigrationTestHelper` outside Android: **YES / NO** — coordinate tried:
- `MIGRATION_1_2` SQL survives verbatim: **YES / NO** —
- `git status` on `app/schemas/`: **clean / dirty** —
