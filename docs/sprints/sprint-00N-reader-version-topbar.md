# Sprint N — Reader top-bar redesign (version label + pencil picker)

Owner UI request, lettered V3 track. Uncommitted in the working tree; the main session verifies +
commits. No version bump (stays 1.4.2/10402). Display/structure-only — Priya led (UI).

## Goal outcome — MET

The in-app reader top bar now reads the way the owner asked: **the book+chapter heading on the
left with a PENCIL beside it that opens the book/chapter picker, and the bundled version ("KJV") on
the right as a title — structurally ready to become a version dropdown the moment a second
translation is bundled.** The old bulleted-list icon is gone.

## Current capability (what moved)

- **Left of the reader top bar:** a pencil (`Icons.Filled.Edit`) sits INLINE to the left of the
  chapter heading ("Genesis 1" / "Psalm 23"). Tapping the pencil opens the same book/chapter picker
  the list icon used to (same `onOpenPicker`, same `reader-open-picker` testTag, same
  `reader_pick_chapter` "Choose a book and chapter" contentDescription, still a ≥48dp target). The
  `Icons.AutoMirrored.Filled.List` action is removed.
- **Right of the reader top bar:** the bundled Bible version. With ONE version (today) it renders as
  a static title — the version **code "KJV"** in `labelLarge`/onSurfaceVariant, not a control. With
  MORE than one version it renders as an M3 dropdown to switch — that branch is built and tested but
  unexercised in production (no second version exists; version-switching machinery itself is NOT
  built — that is the deferred V4 multi-translation work).
- **The version label is sourced from the asset, not hardcoded:** the seam reads the bundled
  `bible.db` `translation` table; today's single row is KJV / "King James Version".

## Decisions & rationale

- **D-N-1 (version sourced from data, no Room-schema change):** `BibleTextSource` gained
  `suspend fun translations(): List<BibleTranslation>`. `RoomBibleTextSource` implements it by
  reading the asset's existing `translation` table via a raw `SimpleSQLiteQuery`
  ("SELECT code, name FROM translation ORDER BY id") on `database.openHelper.readableDatabase`
  (off-main, `Dispatchers.IO`) — deliberately NOT a Room `@Entity`, so it does NOT alter Room's
  schema validation or the carefully-pinned `room_master_table` identity hash from the
  `sprint-00F-kjv-load-fix`. `RoomBibleTextSource` now also injects `BibleDatabase` (the only
  constructor change). New `GetTranslationsUseCase` mirrors `GetChapterUseCase`/`GetPortionTextUseCase`.
- **D-N-2 (label = code on screen, name to TalkBack):** the inline label is the compact CODE
  ("KJV") — what the owner asked for and what fits a top bar; the static title carries the
  unabbreviated NAME ("King James Version") as its contentDescription so TalkBack announces the full
  translation. Recorded choice; awaiting owner tone confirmation only if he wants "KJV" spoken
  literally instead.
- **D-N-3 (count drives the shape):** `ReaderVersionSelector(versions, selected, onSelect)`:
  `versions.size <= 1` → static title; `> 1` → dropdown (the Settings `DropdownMenu` idiom). The VM
  exposes `versionState: StateFlow<ReaderVersionState>` (`available` + `selected`), loaded once in
  `init` from the seam; `selectVersion(...)` is a no-op placeholder (copies the selected value) until
  a second version exists. Both directions of the size split are mutation-pinned.
- **D-N-4 (pencil icon in the frozen artifact):** `Icons.Filled.Edit` IS present in the frozen
  `material-icons-core` 1.7.8 (`filled/EditKt.class` verified) — NO custom drawable needed (unlike
  the Bible-tab `ic_bible_book` or `ic_stats`).

## State of the codebase

New files:
- `bible/domain/model/BibleTranslation.kt` (`code`, `name`).
- `bible/domain/GetTranslationsUseCase.kt`.
- `bible/ui/reader/ReaderVersionState.kt` (`available`, `selected`).
- `bible/ui/reader/ReaderVersionSelector.kt` — the single-vs-dropdown top-bar control (the one home
  for the shape rule). Tags: `reader-version-title` (single), `reader-version-dropdown` +
  `reader-version-option-<code>` (multi).
- `app/src/test/.../bible/data/RoomBibleTextSourceTranslationsTest.kt` — opens the REAL asset via the
  SAME Room `createFromAsset` builder as `BibleModule` and asserts `translations()` returns the KJV
  row (proves the label is read from data). SEPARATE from the 5-test `BibleDatabaseRoomOpenTest` gate
  (that gate's count + content are unchanged).

Changed:
- `bible/domain/BibleTextSource.kt` (+`translations()`), `bible/data/RoomBibleTextSource.kt`
  (+impl, +`BibleDatabase` ctor arg), `bible/ui/reader/ReaderViewModel.kt`
  (+`getTranslations`, +`versionState`, +`selectVersion`), `ReaderScreen.kt` (top bar: pencil in the
  `title` Row, `ReaderVersionSelector` in `actions`; new `versionState`/`onSelectVersion` params),
  `ReaderRoute.kt` (collects + passes `versionState`/`onSelectVersion`).
- `res/values/strings.xml` (+`reader_version_dropdown_description` "Bible version, %1$s", used only by
  the future multi-version dropdown).
- Test ctor/call-site updates: `FakeBibleTextSource` (+`translations()`, `translationRows` override),
  `ReaderViewModelTest` (+`GetTranslationsUseCase`), `ReaderScreenTest`/`AccessibilityGateTest`
  (+`versionState`/`onSelectVersion` on `ReaderScreen` calls), `BibleDatabaseRoomOpenTest`
  (`RoomBibleTextSource(dao, db)` ctor arg only — its 5 assertions untouched).

## Verification

651 tests (net +7), full pipeline green
(`spotlessCheck lintDebug assembleDebug testDebugUnitTest koverXmlReportAppDebug koverVerifyAppDebug`),
Kover 95.8% on domain/data. **All three data/Room gates intact: plan = 11,
BibleTextVerificationTest = 18, BibleDatabaseRoomOpenTest = 5.** A11y gate green (8/8; the pencil is
the same `reader-open-picker` 48dp target it pins). **5 mutations killed**, each restored in place:
(1) disable the static-title branch → single-version pins red; (2) force the static-title branch
always → multi-version dropdown pin red; (3) pencil `onClick` no-op → pencil-opens-picker pin red;
(4) `translations()` returns a wrong code → real-asset pin red; (5) VM drops the `available` list →
versionState pin red.

No manifest / Room-schema / asset / plan-data / DataStore / dependency change.

## Carryover & next goal

- **Next goal candidate:** the queued v2.x release prep (owner-scheduled independently) and the V3.0
  release cut (owner device pass + sign-offs per the Sprint E handoff), unchanged.
- **Queued / deferred (protected out of this sprint):** V4 multi-translation — the second text
  artifact, versification, and actual version-switching. `ReaderVersionSelector`'s dropdown branch +
  `selectVersion` + `reader_version_dropdown_description` are the seams already in place; do NOT
  build the switching machinery until a real second version is bundled.

## Open questions & risks

- **String sign-off:** `reader_version_dropdown_description` ("Bible version, %1$s"). The visible
  "KJV" / spoken "King James Version" choice (D-N-2) — owner to confirm if he wants "KJV" spoken
  literally.
- **Device-pass (NOT JVM-provable):** the pencil + heading + version on one top-bar line at default
  and large font (no overflow); pencil tap accuracy on glass; the version title's right-alignment /
  contrast in light + dark.

## Next sprint

next: sprint-00O-<slug>
