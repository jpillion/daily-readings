# Sprint 00F — KJV reader load failure (P0 fix)

**Type:** emergency P0 bug fix (not a feature sprint). Owner-reported on device.
**Status:** DONE. Uncommitted in the working tree — the main session verifies + commits. No version bump.

## Goal outcome

The V3 in-app Bible reader works on device again. **Met.** Opening any book+chapter now
loads the KJV text instead of showing "couldn't load this chapter". The headline V3 feature
is restored, and a previously-missing test now exercises the exact path that broke — so this
class of failure cannot ship silently again.

## Root cause (confirmed)

The prebuilt `app/src/main/assets/bible/bible.db` was **not Room-compatible**:
1. It had no `room_master_table`. Room's `createFromAsset` validates a bundled DB against the
   schema identity hash stored in `room_master_table`; absent ⇒ validation fails.
2. Its `verse` table DDL carried a foreign key (`REFERENCES translation/book`) and a secondary
   index (`idx_verse_book_ch`) that `VerseEntity` does not declare. Room's `onValidateSchema`
   compares the entity's `TableInfo` against the DB's; an unexpected FK/index also fails it.

Either alone makes Room throw `IllegalStateException: Pre-packaged database has an invalid
schema` on the FIRST query (`BibleModule` uses
`.createFromAsset("bible/bible.db").fallbackToDestructiveMigration(false)`), which the reader
surfaces as its load-failed state.

**Why no test caught it:** `BibleTextVerificationTest` reads the `.db` directly via the
sqlite-jdbc driver (bypassing Room entirely); the reader UI and use-case tests fake
`BibleTextSource`. Nothing ever exercised Room opening the real asset.

## The fix

`tools/build_bible_db.py` (the deterministic importer — the asset is a build output, never
hand-edited):
- The `verse` table is now created with the EXACT `CREATE TABLE` Room generates for
  `VerseEntity` — composite PK `(translation_id, verse_id)`, **no foreign keys, no secondary
  index**. Only `sqlite_autoindex_verse_1` (the implicit PK index) remains, which Room's
  `TableInfo.read` ignores. `translation`/`book` are not Room entities (Room never reads them),
  so their DDL is unchanged.
- A `room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)` is created and seeded
  `INSERT OR REPLACE ... VALUES (42, '8144e1bc57f05006d1a15856ac762552')`. The hash is the
  `ROOM_IDENTITY_HASH` constant, taken from the generated
  `BibleDatabase_Impl.createOpenDelegate()` (`RoomOpenDelegate(1, "<hash>", ...)`).
- The committed asset was **regenerated from the script** (from the two pinned PD sources,
  byte-verified against the recorded SHAs), so the `data-rebuild` CI byte-diff gate still
  reproduces it. Verse content is byte-identical before/after (31,102 body verses + 117
  superscriptions; book/translation tables unchanged — only Room metadata + the `verse` DDL
  shape changed).

### Decision: `exportSchema` stays `false` (Diego)

`BibleDatabase` keeps `exportSchema = false`. It is a read-only asset DB with no migration
history; the identity hash is a stable build artifact pinned in the importer with a documented
re-derivation procedure. Turning exportSchema on permanently would add a maintained
`BibleDatabase/1.json` + a schema-location config for zero migration value. **The drift guard
is the new Robolectric test, not a checked-in JSON:** if `VerseEntity` ever changes, the hash
stops matching and `BibleDatabaseRoomOpenTest` goes red, pointing you at the re-derivation note
in `build_bible_db.py`.

## Test gap closed (the non-negotiable part)

New `app/src/test/kotlin/.../bible/data/BibleDatabaseRoomOpenTest.kt` (Robolectric, real
SQLite, `@Config(sdk = [34])`). It opens the SAME `BibleDatabase` via the SAME
`Room.databaseBuilder(...).createFromAsset("bible/bible.db").fallbackToDestructiveMigration(false)`
builder as `BibleModule` (no jdbc, no fake), deletes the copied DB in `@Before` so every run
validates the committed asset, and reads through `RoomBibleTextSource`/`VerseDao`:
- Gen 1:1, John 3:16, John 11:35 verbatim (stripped markup).
- Ps 3 verse-0 superscription returns with `isTitle = true`.
- `getChapter(19, 3)` returns 9 rows (8 body + title); whole-chapter range includes verse 0.

**Proven fail-before / pass-after:** against the broken asset all 5 fail with
`IllegalStateException: Pre-packaged database has an invalid schema`; against the fixed asset
all 5 pass.

## Verification

- Full pipeline GREEN: `spotlessCheck lintDebug assembleDebug testDebugUnitTest
  koverXmlReportAppDebug koverVerifyAppDebug`.
- Both data gates untouched: Sprint-1 plan gate = **7**, `BibleTextVerificationTest` = **18**
  (re-confirmed against the regenerated asset — content unchanged).
- New `BibleDatabaseRoomOpenTest` = **5**. Suite total **495**, 0 failures / 0 errors.
- Kover **95.1%** line on domain/data (≥70 floor).
- `data-rebuild` reproducibility: a fresh `build_bible_db.py` run from the pinned sources
  `cmp`s byte-identical to the committed asset.
- Sources unchanged (SHAs match the pinned values) ⇒ `docs/data/README.md` provenance untouched.

## State of the codebase

- Changed: `tools/build_bible_db.py` (verse DDL + room_master_table + `ROOM_IDENTITY_HASH`),
  `app/src/main/assets/bible/bible.db` (regenerated), new
  `app/src/test/kotlin/.../bible/data/BibleDatabaseRoomOpenTest.kt`, `CLAUDE.md`.
- Unchanged: `VerseEntity`, `VerseDao`, `BibleDatabase` (still `exportSchema=false`),
  `BibleModule`, `RoomBibleTextSource` — the production code was already correct; only the
  asset's Room compatibility was wrong.

## Carryover & follow-ups (non-blocking)

- The `verse` table no longer has the `idx_verse_book_ch` index, so `VerseDao.getChapter`
  does a filtered scan over ~31k rows on chapter open. Acceptable for a one-shot read. If a
  device profile ever shows it matters, declare `@Index` on `VerseEntity` — which **re-derives
  the identity hash** (update `ROOM_IDENTITY_HASH` from the new `BibleDatabase_Impl`; the
  Room-open test will confirm). Queued, not scheduled.
- This fix does not touch the still-open V3.0 release blockers (owner device pass, M-V3-2
  presentation sign-off, string tone sign-offs, version bump) — see
  `sprint-00E-v3-hardening-release.md`. Add to the device pass: confirm a real device opens a
  chapter end to end (this fix is JVM-proven via Robolectric's real SQLite, but on-device
  `createFromAsset` copy is still worth a glance).

## Next sprint

next: **release cut** (owner device pass + sign-offs from the Sprint E handoff, then the
1.4.0/10400 bump + tag-to-Play rollout by the main session). This was a P0 hotfix inserted
ahead of that; it does not change the release sequencing.
