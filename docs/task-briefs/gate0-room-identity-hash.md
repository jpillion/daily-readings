# Gate 0 · V1 — Does Room KMP produce identity hash `8144e1bc57f05006d1a15856ac762552`?

> **Brief type:** Gate 0 spike. Timeboxed, throwaway, output is an **answer**, not code.
> **Open questions are the point of this brief.** Phase 1 and Phase 2 briefs may not contain any.
>
> **Assignee:** Senior Shared-Core Engineer
> **Merge-order position:** **Gate 0, item 1 of 4. Runs FIRST, in parallel with the gesture rig.**
> Nothing in Phase 1 or Phase 2 starts until Gate 0 closes (D-PORT-1).
> **Timebox: ONE DAY.** If it has not produced an answer in a day, that itself is the escalation.
> **Decides:** ADR-0007 (Stage 1a vs Stage 1b), and by extension the whole bible/reader track.

---

## Objective

Determine, by generation rather than by argument, whether **Room KMP's code generator computes
the same schema identity hash for the unchanged `VerseEntity` / `BibleDatabase` as the Android
annotation processor did** — the value `8144e1bc57f05006d1a15856ac762552` that is hand-written
into the shipped `bible.db` asset.

Produce a one-page answer with the generated hash quoted verbatim, and a recommendation of
ADR-0007 Stage 1a or Stage 1b.

**This is the single highest-risk item in the program.** It is also the cheapest to settle.

---

## Context

The in-app KJV reader is backed by `app/src/main/assets/bible/bible.db` — 5,599,232 bytes,
66 books / 1,189 chapters / 31,102 verses / 117 verse-0 superscriptions. It is opened at runtime by:

```kotlin
// di/BibleModule.kt:52-56
Room.databaseBuilder(context, BibleDatabase::class.java, "bible.db")
    .createFromAsset("bible/bible.db")
    .fallbackToDestructiveMigration(false)
    .build()
```

Room validates a pre-packaged database against a **schema identity hash** on **first query**. The
asset carries that hash in a hand-forged `room_master_table` row, written by
`tools/build_bible_db.py:44`:

```python
ROOM_IDENTITY_HASH = "8144e1bc57f05006d1a15856ac762552"
```

A mismatch throws `IllegalStateException: Pre-packaged database has an invalid schema`.

**This exact failure has already shipped to users.** Sprint-00F: the asset had a foreign key and a
secondary index that `VerseEntity` did not declare, and no `room_master_table` at all. Every
chapter in the reader showed "couldn't load this chapter." Nothing caught it, because
`BibleTextVerificationTest` bypasses Room entirely (sqlite-jdbc) and every reader test fakes
`BibleTextSource` — **Room never opened the real asset in any test** until
`BibleDatabaseRoomOpenTest` was written as the fix.

Room KMP uses a **different code-generation path** from the Android annotation processor.
Whether it computes the same hash for the same entity **is unknown and cannot be determined by
reading this repository.**

Relevant files (read-only for this spike):
- `app/src/main/kotlin/com/jpillion/dailyreadingplanner/bible/data/VerseEntity.kt`
- `app/src/main/kotlin/com/jpillion/dailyreadingplanner/bible/data/BibleDatabase.kt`
- `app/src/main/kotlin/com/jpillion/dailyreadingplanner/bible/data/VerseDao.kt`
- `tools/build_bible_db.py` (lines 44 and ~316-320, where the row is written)

---

## Contract

### What you build

A **throwaway** Gradle module, outside the build of `:app`, containing **byte-identical copies**
of `VerseEntity`, `VerseDao` and `BibleDatabase` — same table name, same column names, same
types, same nullability, same primary key, same absence of indices and foreign keys.

> **Copy them, do not import them, and do not "tidy" them.** A single reordered column annotation
> or an added `@Index` invalidates the entire result. If you find yourself improving something,
> stop — that is the failure mode this spike exists to detect.

Apply the Room KMP Gradle plugin and KSP, add `androidx.room:room-runtime` at the KMP
coordinate, and configure at least the **JVM** and **`iosSimulatorArm64`** targets. Two targets,
not one — the answer we need is per-generator, and a divergence *between iOS targets* is exactly
the failure the port would otherwise discover on a device.

> ### ⛔ PREREQUISITE — this spike cannot be run today (added 2026-08-08)
>
> Configuring `iosSimulatorArm64` requires a **Kotlin/Native toolchain and Xcode**. As of
> 2026-08-08 this machine has **Command Line Tools only** (`xcode-select -p` →
> `/Library/Developer/CommandLineTools`), no `/Applications/Xcode*.app`, and no `~/.konan`.
> **Do not dispatch this brief until `RELEASING-IOS.md` Step 0 (Xcode) is complete.**
>
> The JVM half can be run without Xcode and is worth doing first — it answers "does Room KMP's
> generator agree with the shipped Android hash at all?" But it **cannot close the spike**,
> because the decisive branch is *the hash differs **between** the JVM and iOS targets*, which
> is unreachable without an Apple target. A JVM-only run that matches is **not** a green light.
>
> Xcode is Action 1 in `../ios-execution-plan.md` for exactly this reason: it is the long pole
> in front of the highest-risk item in the program.

### How to read the hash

Two independent routes. **Do both** and confirm they agree:

1. **`exportSchema = true`** on the copied `BibleDatabase`, build, and read
   `"identityHash"` out of the emitted `1.json`.
2. Read `identityHash` out of the generated `BibleDatabase_Impl` source (the same place the
   sprint-00F fix originally lifted the constant from).

Record the value **verbatim**, per target. Do not paraphrase, do not truncate.

### The second question, which matters almost as much

Confirm whether Room KMP's `Room.databaseBuilder<BibleDatabase>(path)` (path-based, no
`createFromAsset`) will **open an existing file** that already carries a matching
`room_master_table` — or whether it insists on creating the database itself.

If it will not open a pre-existing file at all, ADR-0007 Stage 1a is dead **regardless of the
hash**, and that is a different and larger finding. Say so loudly.

### The deliverable

A one-page answer appended to this file under `## Result`, containing:

1. The hash per target, verbatim.
2. `MATCH` or `MISMATCH` against `8144e1bc57f05006d1a15856ac762552`.
3. Whether the path-based builder opens a pre-existing file.
4. A recommendation: **Stage 1a** (keep Room + a `BundledDatabaseProvider` seam) or **Stage 1b**
   (drop Room for the read-only bible DB).
5. Anything you had to change to make it generate — **every** such change, however small.

---

## What answer would kill or reshape the port

| Outcome | Consequence |
|---|---|
| **MATCH on both targets** (expected, must be proven) | ADR-0007 **Stage 1a**. Keep Room. Adopt ADR-0007 Amendment A1 (`exportSchema = true` + a CI assertion that every target's hash equals the Python constant), which converts risk R1 from HIGH to LOW permanently. **The bible track is de-risked and the port's shape is unchanged.** |
| **MISMATCH, but stable and identical across targets** | The asset can be regenerated with the new hash — one constant in `tools/build_bible_db.py`, then re-run the `data-rebuild` byte-diff gate with its `LD_PRELOAD` SQLite-3.43.2 pinning, and confirm verse content is byte-identical. **Removes one instance of the problem, not the class.** Weigh against Stage 1b. Not fatal. |
| **MISMATCH that differs *between* targets** | **Reshapes the port.** No single hand-written hash can satisfy two generators, so Stage 1a is unavailable. Go to **Stage 1b**. |
| **Room KMP's path-based builder will not open a pre-existing file** | **Reshapes the port.** Stage 1a is dead independent of the hash. Go to Stage 1b. |
| **Stage 1b is chosen AND judged too large** | **This is the D-PORT-1 stop condition. Stop the program and reassess.** Do not proceed into Phase 1 to "keep momentum" — the three Android-only releases leave the Android app slightly *worse* if the port never lands (Koin loses Hilt's compile-time graph verification; kotlinx-datetime is neutral on an Android-only product). |

### The fallback, so nobody has to invent one under pressure

**ADR-0007 Stage 1b — drop Room for `BibleDatabase` only.** Read the asset through
`androidx.sqlite`'s KMP driver with hand-written queries. `ProgressDatabase` keeps Room (it earns
it — migrations, schema export, `MigrationTestHelper`).

Scope, so the size judgement is made from facts:
- **2 tables** (`verse`, `translation`), **4 queries** total across the reader.
- `VerseEntity` becomes a plain data class with a hand-written row mapper.
- **`BibleTextSource` — the seam everything above depends on — does not change at all.** The
  reader, the use cases, and all their tests are untouched.
- `RoomBibleTextSource.translations()`'s raw-`SimpleSQLiteQuery` workaround
  (`bible/data/RoomBibleTextSource.kt:44`) becomes an ordinary query, and the D-N-1 KDoc
  explaining why it exists should be **deleted, not left to confuse a future reader**.
- Cascades into: `BibleDatabaseRoomOpenTest` (rewritten), `tools/build_bible_db.py` (the
  `room_master_table` row and `ROOM_IDENTITY_HASH` are **deleted**), the `data-rebuild` byte-diff
  job (the asset changes once, deliberately), and ADR-0010's Tier 2 becomes cheap to move to
  `commonTest` because the KMP SQLite driver is then already in `shared/data`.

Stage 1b **deletes the class of problem permanently.** It is close to being the better option
even on a MATCH; it is not recommended unconditionally only because "port faithfully first,
improve separately" is the rule that keeps ports finishing.

---

## Acceptance criteria

1. The identity hash is quoted verbatim from **both** read routes, for **both** the JVM and
   `iosSimulatorArm64` targets, and the two routes agree.
2. The comparison against `8144e1bc57f05006d1a15856ac762552` is stated as `MATCH` or `MISMATCH`
   — not "appears to", not "should".
3. The pre-existing-file question is answered yes or no, with the observed behaviour described.
4. Every modification required to make generation succeed is listed. **"I had to add an
   `@Index`" is a MISMATCH result, not a footnote.**
5. A Stage 1a / Stage 1b recommendation with one paragraph of reasoning.
6. **The throwaway module is deleted, or lives on a branch that is never merged.** It must not
   appear in `settings.gradle.kts` on `main`.
7. **Zero files under `app/`, `tools/` or `gradle/` are modified.** This spike observes; it does
   not change the shipped app. If you believe you must change one, that is an escalation.

---

## Boundaries / write set

**You may write:**
- A throwaway module directory outside `app/` (suggested: `spikes/gate0-room-hash/`), on a
  branch that will never merge.
- The `## Result` section appended to **this file**.

**You may NOT write:**
- Anything under `app/`, `tools/`, `gradle/`, `.github/`.
- `gradle/libs.versions.toml` — if the spike needs a dependency coordinate or version,
  **escalate to Build & Release**; do not add it yourself (project invariant 6).
- Any ADR. If the result changes ADR-0007, **Staff amends it**, not you. Report the finding.

---

## Escalation triggers

Escalate immediately, in the standard format, and stop, if:

- **The timebox expires.** One day. A spike that is still going on day two is reporting something.
- The Room KMP plugin cannot be applied at the versions Build & Release has selected — that is a
  **Build & Release** escalation with the specific coordinate and error.
- The result is `MISMATCH` in any form — **Staff** decides Stage 1a vs 1b, not the spike.
- Generation requires *any* change to the copied entity — report it as a mismatch-class finding.
- The hash differs between the JVM and iOS targets — **stop immediately.** This is the
  reshape-the-port outcome and it should reach Staff within the hour.
- You find yourself wanting to modify `tools/build_bible_db.py` "just to test". That file is the
  source of a byte-reproducible asset guarded by a CI byte-diff gate that took six weeks of red
  builds to stabilise. **Escalate instead.**

---

## Result

*(To be completed by the assignee. Leave the structure; fill the values.)*

| Target | Hash (verbatim) | vs `8144e1bc…2552` |
|---|---|---|
| jvm | | |
| iosSimulatorArm64 | | |

- Path-based builder opens a pre-existing file: **YES / NO** — observed behaviour:
- Modifications required to generate:
- **Recommendation: Stage 1a / Stage 1b** —
