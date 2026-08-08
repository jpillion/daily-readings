# p1-08 — Capture the real 1.8.1 persistence fixtures (PG-1, PG-2), from a real device

> **Assignee:** Verification Engineer · **requires an owner device action**
> **Release:** none — this is preparation for **1.11.0**.
> **Merge order:** Group A. **START THIS FIRST, on day one of Phase 1.** It has owner latency and
> nothing else does.
> **Inherits:** [`p1-00-overview.md`](p1-00-overview.md) rules R1–R7.
> **Preconditions:** none beyond Gate 0. Deliberately.

---

## Objective

Obtain, commit and wire up **two fixture files captured from a device running the shipped 1.8.1
build** — a real `progress.db` and a real `settings.preferences_pb` — and the tests that read them.

These are the artifacts that make ADR-0008's **PG-1** and **PG-2** provable. Without them, the
1.11.0 persistence relocation ships on *reasoning about identity hashes*, which is exactly what
sprint-00F proved insufficient.

---

## Context

### Why the existing migration test does not cover this

`ProgressMigrationTest` uses `MigrationTestHelper` to **synthesize** a v1 database, run
`MIGRATION_1_2`, and assert zero loss. It is a good test and it stays.

**But a synthesized v1 database is not a shipped v2 database.** It proves the migration SQL is
correct; it does **not** prove that a database file *written by the shipped Room runtime on a real
device* opens against the *ported* Room runtime. That second question is the one that turns an app
update into crash-on-open for the entire installed base, and nothing in the repository currently
asks it. This finding is the Senior Shared-Core engineer's and it is correct.

### What is at stake

Daily Bible Reading Planner has been live since 2026-07-23 — 1.8.1 / 10801, 100% rollout, 177
countries. Real users have irreplaceable reading history in:

```sql
reading_progress(plan_id TEXT NOT NULL, dateEpochDay INTEGER NOT NULL,
                 stream INTEGER NOT NULL, readAtEpochMillis INTEGER NOT NULL,
                 PRIMARY KEY(plan_id, dateEpochDay, stream))
```

`fallbackToDestructiveMigration` is deliberately **off** (D-V3-15): a failed migration must be a
loud crash, never silent data loss.

And a second, **worse-because-silent** store: the DataStore preferences file
`settings.preferences_pb`, holding theme, font scale, tracking-start date and its initialised
marker, selected plan, reminder enablement and time, persistent-notification enablement, external
Bible app, reading-destination mode, show-streaks, the partial-segment token cache, the bible asset
content version, and every first-run marker.

**A renamed key does not crash. It silently resets the user**: theme reverts, tracking-start date is
lost, `selected_plan` reverts to Bible Companion (so a M'Cheyne reader's progress *appears* to
vanish, though it is still there), the show-streaks preference flips back to its absent-key default,
and **the first-run dialogs re-fire** — a shipped user is greeted by the tracking-start prompt and
the reading-destination question as though newly installed.

### Why day one

It needs the owner to install 1.8.1, use it, and pull files off a device. That is calendar latency
no engineer can absorb, and the whole point of Phase 1's parallelism is to spend such latency early.

---

## Contract

### Step 1 — the owner's part, and make it easy for them

Give the owner a **numbered, copy-pasteable** procedure. They should not have to think about
`run-as` or debug builds.

The device must be running a build **whose persistence layer is byte-identical to shipped 1.8.1**
(a debug build of the `v1.8.1` tag is fine — the databases do not differ by build type). Then:

1. **Choose the plan** (Settings → Reading plan) — mark readings across **at least two plans**, so
   `plan_id` isolation is exercised.
2. Mark readings across **at least two different years**, including a day in a **leap year near
   Feb 29** (2024-02-28 and 2024-03-01 are good; Feb 29 itself is unmarkable and that is the point).
3. Leave at least one day **partially** read (some streams marked, not all) and at least one
   **fully** read.
4. Mark **at least one segment partially** on a multi-segment reading (sprint 00P) — this populates
   the `partial_reading_segments` DataStore key.
5. **Change every setting away from its default**: theme → Dark; font size → not 1.0; tracking start
   → a specific non-Jan-1 date; reading destination → external; external app → Bible Gateway;
   reminder → on, at a non-08:00 time; persistent notification → off (its default is now **on**);
   show streaks → on (its default is **off**).
6. Force-stop the app so DataStore flushes.
7. Pull the files.

```bash
adb shell run-as com.jpillion.dailyreadingplanner \
  cat databases/progress.db > progress-1.8.1-fixture.db
adb shell run-as com.jpillion.dailyreadingplanner \
  cat files/datastore/settings.preferences_pb > settings-1.8.1-fixture.preferences_pb
```

Also capture `-wal` and `-shm` **if they exist** — and note that a checkpointed database may have
none, which is itself worth recording.

> Steps 5's "away from its default" instruction is deliberate: a fixture where every value happens
> to equal the default proves nothing, because a total reset would read back identically.

### Step 2 — record what is in them, in plain text

Commit a `README.md` beside the fixtures listing, in human-readable form, exactly what the owner
did: which plans, which dates, which settings values. **The fixture files are opaque binaries;
without this the tests are unmaintainable in six months.** Include the `sqlite3` row dump.

### Step 3 — the tests

**PG-1** — `ProgressFixtureOpenTest` (Robolectric, Android):
- Copy the fixture into place, open it through the **real** `ProgressDatabase` builder with
  `MIGRATION_1_2` registered and `fallbackToDestructiveMigration` **off**.
- Assert **every** `ReadingProgressDao` query returns the recorded results: `streamsRead`,
  `readCountsInRange`, `allReadCounts`, `streamCountsInRange`, `marksInRange`, `hasAnyMarks`.
- Assert the **exact row count** and that **every `(plan_id, dateEpochDay, stream,
  readAtEpochMillis)` tuple** is present.
- **Assert per-plan isolation**: plan A's counts are unaffected by plan B's rows.

**PG-2** — `SettingsFixtureReadTest`:
- Point a DataStore at the fixture and read **every** key through `SettingsRepository`.
- Assert each value equals what the owner set — **and assert the literal key strings**, the same
  discipline this project applies to plan-data literals. A renamed key must fail here and nowhere
  later.
- **Assert the first-run markers read as already-completed**, so the ported build does not re-fire
  the tracking-start prompt or the reading-destination question at a shipped user.

**PG-3** — a CI checksum assertion that
`app/schemas/…/ProgressDatabase/2.json` is byte-identical to its committed state.
**If it ever changes, the build fails and the correct response is to stop and escalate — not to
regenerate the baseline.**

### Step 4 — prove the tests can fail

A fixture test that passes against a broken build is worse than no test. Demonstrate each:

- **PG-1:** temporarily bump `ProgressDatabase` to `version = 3` with no migration → the test must
  fail with a migration error, **not** silently pass. Restore.
- **PG-2:** temporarily rename one DataStore key → the test must fail. Restore.
- **PG-3:** temporarily edit one byte of `2.json` → CI must fail. Restore.

**Restore byte-identically and verify with checksums.**

---

## Acceptance criteria

1. Both fixtures are committed under `app/src/test/resources/fixtures/1.8.1/`, with a `README.md`
   describing their exact contents.
2. The `progress.db` fixture contains rows for **≥2 plans**, **≥2 years**, at least one **partial**
   and one **complete** day, and a **leap-year-adjacent** date.
3. The `settings.preferences_pb` fixture has **every** setting at a **non-default** value, and the
   README lists each with its expected value.
4. `ProgressFixtureOpenTest` passes and asserts row count, every tuple, every DAO query, and
   per-plan isolation.
5. `SettingsFixtureReadTest` passes and asserts every key's **literal string** and value, including
   the first-run markers.
6. The `2.json` checksum assertion runs in CI.
7. **All three fail-demonstrations performed and recorded**, files restored byte-identically with
   checksums quoted.
8. Fixture sizes are reported. Both should be small (a few KB); if `progress.db` is large, the
   owner over-marked and it should be recaptured smaller.
9. Full pipeline green.
10. **The six data gates untouched, counts unchanged: 11 / 10 / 8 / 6 / 18 / 5.**
11. **No R8 device smoke required** — this task adds only tests and fixtures. Say so explicitly.

---

## Boundaries / write set

**Yours:**
- `app/src/test/resources/fixtures/1.8.1/**` **(new)**
- `app/src/test/kotlin/.../data/progress/ProgressFixtureOpenTest.kt` **(new)**
- `app/src/test/kotlin/.../data/prefs/SettingsFixtureReadTest.kt` **(new)**
- The owner-facing capture procedure (put it in the fixture `README.md`)

**Not yours:**
- **Anything under `app/src/main/`.** The fail-demonstrations are temporary, local and reverted;
  they are never committed.
- **`app/schemas/**`** — read and checksum only. **Never regenerate.**
- `app/build.gradle.kts`, `.github/workflows/**` — **Build & Release** wires the PG-3 CI assertion.
  Specify it; do not implement it.

---

## Escalation triggers

- **The owner cannot pull the files** (`run-as` denied on a non-debuggable build, no adb) →
  **Owner + Build & Release**, blocking 1.11.0 but **not** blocking 1.9.0 or 1.10.0. Say so, so it
  is not treated as more urgent than it is. Fallback: a debug build of the `v1.8.1` tag, used
  briefly.
- **`app/schemas/…/2.json` differs from its committed state before you change anything** →
  **Staff + Owner**, blocking, immediately. It would mean the shipped baseline has already drifted.
- **A fixture test cannot be made to fail in its demonstration** → **Staff**, blocking. A gate that
  cannot fail is not a gate.
- **The owner asks whether this can be skipped** → **EM**. The honest answer: ADR-0008 makes PG-1
  and PG-2 hard acceptance criteria for 1.11.0, the existing migration test **synthesizes** its v1
  database and therefore does not cover this, and the downside is the silent reset of every shipped
  user's settings. It cannot be skipped; it can be *scheduled*, which is why it is on day one.
