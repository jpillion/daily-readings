# ADR-0007 — Room, the pre-packaged `bible.db`, and the identity hash

**Status:** Draft, pending owner sign-off **and** the V1 spike · **Date:** 2026-08-08
**Author:** Staff / Port Architect
· **Amended:** 2026-08-08 — see [Amendment A1](#amendment-a1--exportschema--true-on-bibledatabase-supersedes-the-sprint-00f-decision)
and [Amendment A2](#amendment-a2--sequencing-the-android-open-path-does-not-change-in-1110)

> This is the highest-risk technical decision in the port. It is also the one most likely to be
> resolved cheaply by a two-hour spike, so **do not proceed on any bible-related task until V1
> below is closed.**

## Context

The in-app KJV reader is backed by a committed, byte-reproducible, read-only SQLite asset:

- `app/src/main/assets/bible/bible.db` — **5,599,232 bytes**, 66 books / 1,189 chapters /
  31,102 verses / 117 verse-0 superscriptions.
- Built by `tools/build_bible_db.py` from two SHA-pinned independent PD KJV corpora, with a CI
  job (`data-rebuild`) that re-derives it and asserts a **byte-diff of zero**. Getting that gate
  stable required pinning the SQLite library version and `SECURE_DELETE` (see the CI entry in
  CLAUDE.md — six weeks of red build).
- Verified by `BibleTextVerificationTest` (18 assertions, sqlite-jdbc) and opened by
  `BibleDatabaseRoomOpenTest` (5 assertions, Robolectric).
- Opened at runtime by
  `Room.databaseBuilder(context, BibleDatabase::class.java, "bible.db").createFromAsset("bible/bible.db").fallbackToDestructiveMigration(false).build()`
  (`di/BibleModule.kt:52-56`).

Two facts make this hard.

**Fact 1 — `createFromAsset` is Android-only in Room KMP.** `createFromAsset`, `createFromFile`,
`createFromInputStream` and `PrepackagedDatabaseCallback` are not available in Room's common
source set; common support is listed as future work. iOS also has no `assets/` concept — bundled
files live in `NSBundle`.

**Fact 2 — the asset carries a hand-written Room identity hash.** `tools/build_bible_db.py:44`
sets `ROOM_IDENTITY_HASH = "8144e1bc57f05006d1a15856ac762552"` and writes a `room_master_table`
row with it. Room validates that hash against the one its code generator computed from
`VerseEntity` on **first query**. A mismatch throws
`IllegalStateException: Pre-packaged database has an invalid schema`.

**This exact failure has already shipped to users once.** Sprint-00F: the asset had a foreign key
and a secondary index that `VerseEntity` did not declare, and no `room_master_table` at all. Every
chapter in the reader showed "couldn't load this chapter". Nothing caught it because
`BibleTextVerificationTest` bypasses Room (JDBC) and every reader test fakes `BibleTextSource` —
Room never opened the real asset in any test until `BibleDatabaseRoomOpenTest` was written as the
fix.

Room KMP uses a different code-generation path from the Android annotation processor. **Whether
it computes the same identity hash for the same entity is unknown to me and cannot be determined
by reading this repo.**

## Decision

**Two-stage, gated on a spike.**

### Stage 0 (blocking) — the spike

**V1 ⟦VERIFY⟧:** stand up a throwaway KMP module with `VerseEntity` and `BibleDatabase`
unchanged, generate under Room KMP, and read the identity hash out of the generated
`BibleDatabase_Impl` (or set `exportSchema = true` once and read `identityHash` from the emitted
`1.json`). Compare against `8144e1bc57f05006d1a15856ac762552`.

Owner: **Core/Data**. Timebox: **one day**. Output: the hash, and whether Room KMP's
`Room.databaseBuilder<BibleDatabase>(path)` can open an existing file with a matching
`room_master_table`.

### Stage 1 — the decision, branching on the spike

**If the hash matches (expected, but must be proven):**

**Keep Room. Remove `createFromAsset` from the picture entirely** by introducing one platform
function that materialises the asset as a real file, and then opening that file with the
platform-neutral builder.

```kotlin
// shared/platform
/**
 * Ensures the bundled Bible database exists as a readable file on this device and returns its
 * absolute path.
 *
 * The bundled database is a read-only, versioned data artifact — never user data. If a file
 * already exists at the destination and was produced by [contentVersion], it is left untouched
 * and its path returned. Otherwise the bundled copy replaces it (along with any sidecar
 * journal files), so a shipped correction reaches existing installs.
 *
 * Runs off the main thread. Throws if the bundled artifact is missing — that is a packaging
 * defect, not a runtime condition.
 */
interface BundledDatabaseProvider {
    suspend fun materialise(name: String, contentVersion: Int): String
}
```

Android's actual copies from `context.assets` into `context.getDatabasePath(name)`. iOS's actual
copies from `NSBundle.mainBundle.pathForResource` into Application Support, using okio. Both then
feed `Room.databaseBuilder<BibleDatabase>(path)` with the KMP bundled SQLite driver.

Note what this does to the existing `BibleAssetGate` (D-V3-8): its delete-on-version-bump logic
**folds into `materialise`**, which is where it belonged all along. `BibleAssetGate`'s
`runBlocking`-inside-a-DI-provider construction disappears with it. That is a genuine improvement
the port makes possible — but implement it as a faithful behavioural port first, and keep the
three Robolectric wiring tests green.

**If the hash does NOT match:**

**Drop Room for `BibleDatabase` and read the asset directly through `androidx.sqlite`'s KMP
driver** (`BundledSQLiteDriver`), with hand-written queries.

This deletes the identity-hash problem permanently — no `room_master_table` requirement, no
`ROOM_IDENTITY_HASH` constant in the build script, no coupling between a Python asset builder and
a Kotlin annotation processor. The cost is small and known: the reader uses **four** queries
(`getVerses(start,end)`, the raw `translation` read, and two the DAO exposes). `VerseEntity`
becomes a plain data class with a hand-written row mapper. `BibleTextSource` — the seam everything
above depends on — does not change at all.

Honestly: **this is close to being the better option even if the hash matches.** Room earns its
keep on the read-write `ProgressDatabase` (migrations, schema export, `MigrationTestHelper`). On
a read-only two-table asset with four queries it provides schema validation we did not ask for
and a hash we have to hand-forge in Python. I am not recommending it unconditionally only
because "port faithfully first, improve separately" is the rule that keeps ports finishing.

## Alternatives rejected

**Ship the verses as JSON / a binary blob / Compose Resources.** Rejected. 31,102 rows with
range queries by `verse_id`; the whole `BibleTextVerificationTest` gate is built on the SQLite
file; the `data-rebuild` byte-diff CI gate is built on reproducing that file. Re-opening the
storage format re-opens the project's second core-IP asset. Not worth it.

**Keep `createFromAsset` on Android and write a separate iOS-only open path.** Rejected — it
leaves an Android-only Room API inside `shared/data`, which means `shared/data` is not actually
shared, and it means two code paths for "open the bible" that can drift. The
`BundledDatabaseProvider` seam costs 20 lines per platform and puts the platform-ness in
`shared/platform` where it belongs.

**Copy `bible.db` into the iOS bundle as a second file in git.** Rejected — the one-copy rule
(ADR-0011). It is also 5.6 MB of drift risk.

**Regenerate the asset with whatever hash Room KMP produces, if they differ.** Not rejected, but
not free either, and it should be a conscious step rather than a reflex: it means editing
`tools/build_bible_db.py`, regenerating, re-running the byte-diff gate (with its SQLite pinning),
and confirming the verse content is byte-identical. If the spike shows a mismatch, weigh this
against dropping Room — dropping Room removes the *class* of problem, regenerating removes one
instance of it.

## Consequences accepted

- **`BibleAssetGate` and `BibleAssetVersion` are restructured**, not merely moved. Their three
  Robolectric wiring tests and two killed mutations (comparison flip, skipped delete) must be
  preserved in spirit against the new seam. Write the replacement tests in the same task.
- The 5.6 MB asset must reach the iOS bundle. It compresses to ~2 MB, well inside the 12 MB
  bundle gate the CI already enforces, but the iOS `.ipa` gains ~2 MB. Acceptable.
- **iOS gets a first-run copy cost** on the order of a few hundred milliseconds for 5.6 MB.
  Android already pays this via `createFromAsset`. It must happen off the main thread — the same
  StrictMode discipline the Android side applies. Verify on the oldest supported device.
- **A new iOS-side open test is mandatory** (see ADR-0010). It reads Gen 1:1, John 3:16,
  John 11:35 and the Ps 3 verse-0 superscription through the real bundled asset. This is the test
  that would have caught sprint-00F. Not optional.
- `RoomBibleTextSource.translations()` currently reaches through `database.openHelper.readableDatabase`
  with a `SimpleSQLiteQuery` (`bible/data/RoomBibleTextSource.kt:44`) precisely *because* adding a
  `translation` `@Entity` would change the identity hash (D-N-1). If Room is dropped, that
  workaround becomes an ordinary query and the KDoc rationale should be deleted, not left to
  confuse a future reader.

## Revisit when

- **Immediately after the V1 spike.** This ADR is not accepted until then.
- Room KMP adds common pre-packaged database support — at which point `BundledDatabaseProvider`
  could be retired, though there is no urgency to.
- A shipped text correction requires an asset content-version bump; that exercises the whole
  re-copy path on both platforms for the first time and is the moment to confirm it works.

---

## Amendment A1 — `exportSchema = true` on `BibleDatabase` **supersedes the sprint-00F decision**

**Date:** 2026-08-08 · **Author:** Staff / Port Architect
**Supersedes:** the sprint-00F record in `CLAUDE.md` and
[docs/sprints/sprint-00F-kjv-load-fix.md](../sprints/sprint-00F-kjv-load-fix.md):
> *"**`exportSchema` stays `false`** on `BibleDatabase` (read-only asset DB; the hash is a pinned
> build artifact, the new test is the drift guard — no checked-in schema JSON)."*

**This amendment reverses that. It is recorded as a reversal, deliberately and visibly, because
slipping a reversed decision in as if it were new is how a project loses its record.**

### A1.1 The change

Set `exportSchema = true` on `BibleDatabase`, check in the exported schema JSON, and add a CI
assertion that the exported `identityHash` equals `ROOM_IDENTITY_HASH` in
`tools/build_bible_db.py:44`.

```
assert exported_schema["database"]["identityHash"] == ROOM_IDENTITY_HASH
```

For every target that has a Room generator — today Android/JVM, tomorrow also
`iosArm64`/`iosSimulatorArm64` under Room KMP.

### A1.2 Why sprint-00F's reasoning was right then and is wrong now

Sprint-00F's argument was sound **under a single generator**. With one Room annotation processor,
`8144e1bc57f05006d1a15856ac762552` is a build artifact that changes only when `VerseEntity`
changes, and `BibleDatabaseRoomOpenTest` catches the mismatch by actually opening the asset. A
checked-in schema JSON would have been a second copy of a fact already guarded.

**The port breaks that premise in two ways:**

1. **N generators, not one.** Room KMP generates per target. There is now a real possibility of a
   hash that is correct on Android and wrong on iOS — which is precisely the sprint-00F failure
   mode, resurrected on the platform with no shipped-user feedback loop.
2. **The guard is asymmetric.** `BibleDatabaseRoomOpenTest` runs on Android only and always will
   (Robolectric). The iOS equivalent (`BibleDatabaseOpenTest`, ADR-0010 tier 4) does not exist
   yet. Until it does, there is **no** guard on the iOS side at all, and even after it exists it
   catches the mismatch by failing to open — late, and only if someone runs the iOS test.

`exportSchema = true` converts a **hand-copied constant in a Python file** into a **CI-asserted
invariant** that is checked at build time, on every target, without opening anything.

This is the cheap permanent fix the Senior Shared-Core engineer identified, and it converts
risk R1 from HIGH to LOW **on the good branch of the V1 spike**. It does not help on the bad
branch — if the hashes differ, the assertion simply tells you so, loudly and immediately, which
is the point.

### A1.3 What it costs, honestly

- One JSON file (~4 KB) enters the repo. It is a **generated build artifact under version
  control** — the thing sprint-00F was avoiding. Accepted: it is guarded by the assertion, so a
  stale copy is a red build, not a silent lie.
- The exported schema for `BibleDatabase` must be wired into the **debug-only** assets set the
  same way `ProgressDatabase`'s already is (`app/build.gradle.kts:36`), or excluded from packaging
  entirely — it is a build-time artifact and **must not ship in the release AAB or the `.ipa`**.
  The existing wiring is already verified absent from the release AAB (Alt Sprint B: 0 entries);
  hold that property and re-verify it.
- `BibleDatabase` gains a schema directory. It does **not** gain a migration path, a version bump,
  or `fallbackToDestructiveMigration`. It remains a read-only, version-1, never-migrated asset DB.

### A1.4 What it does not change

- `ROOM_IDENTITY_HASH` stays in `tools/build_bible_db.py` and stays the source of the value
  written into `room_master_table`. The exported schema is the **check**, not the source.
- `BibleDatabaseRoomOpenTest` (5 assertions) stays exactly as it is, on Android, forever. It
  proves a different thing — that Room actually opens the real bytes — and A1 does not replace it.
- The `data-rebuild` byte-diff CI job and its `LD_PRELOAD` SQLite-3.43.2 pinning are untouched.

---

## Amendment A2 — sequencing: the Android open path does **not** change in 1.11.0

**Date:** 2026-08-08 · **Author:** Staff / Port Architect

The Stage 1 decision above replaces `createFromAsset` with `BundledDatabaseProvider` on **both**
platforms. Read literally, that would land in the 1.11.0 Android-only restructure release —
changing how the bible database opens for every shipped Android user, in the same release that
relocates the progress database.

**Do not do that.** Sequence it as:

| Release / tranche | Bible DB open path on Android |
|---|---|
| **1.11.0** (Phase 2 tranche A — Android target only) | **Unchanged.** `createFromAsset` stays, living in `shared/data`'s `androidMain` source set. `shared/data` is a KMP module with one target, so an Android-only API in `androidMain` compiles and is honest about what it is. |
| **Phase 2 tranche B** (iOS targets added; no Play release) | `BundledDatabaseProvider` is introduced with both actuals, `createFromAsset` is deleted, and **both** `BibleDatabaseRoomOpenTest` (Android) and the new `BibleDatabaseOpenTest` (iOS) must pass against the new path before the change merges. |

**Why:** the one thing sprint-00F proved is that this open path fails in a way no JVM test sees
and every user sees. There is no reason to put that change in front of shipped Android users in a
release whose purpose is a database relocation — you would have two candidate causes for one
crash report. Moving it to tranche B costs nothing (no Play release rides on tranche B) and buys
a clean bisect.

This is the same "change one variable at a time" reasoning as D-PORT-7, applied one level down.
