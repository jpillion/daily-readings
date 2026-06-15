# Psalm singular/plural in the in-app reader (`sprint-00L-psalm-reader`)

Follow-up to the Schedule-side `sprint-00K-psalm-singular` (D-UI-2). Extends the same
singular/plural rule into the V3 in-app reader so the reader and the Schedule agree.

## The rule (one source of truth)

A single chapter of Psalms displays **"Psalm N"** (singular); a multi-chapter Psalms run
displays **"Psalms M–N"** (plural, en dash). Every other book is unchanged.

The rule has exactly ONE implementation:
`ReadingFormatter.singularizeBookName(canonicalName, singleChapter)` in
`app/src/main/kotlin/.../ui/day/ReadingFormatter.kt`
(`if (singleChapter && canonicalName == "Psalms") "Psalm" else canonicalName`).
`ReadingFormatter`'s private `displayBookName` now delegates to it, so the Schedule/widget/
notification path and the reader call the *same* function — they cannot drift. The
abbreviated form ("Psa") is untouched.

## Where it applies in the reader

- **In-page chapter header** (`ReaderScreen.ChapterHeader`) — always one chapter ⇒ "Psalm 23".
- **Top-bar single-chapter title** (`ReaderViewModel.loadPage`) — "Psalm 23".
- **Portion-page title** (`ReaderViewModel.portionTitle`):
  - single chapter (incl. a verse-windowed Psalm 119 day — a window doesn't change the
    chapter count) ⇒ singular "Psalm 119";
  - same-book multi-chapter span ⇒ plural "Psalms 1–2";
  - two-book portion (Jun 19 / Dec 19 = 2 John + 3 John) ⇒ shape unchanged, per-block singular.
- **Verse-tap spoken label** (`ReaderScreen.verseTapDescription`) — speaks "Open Psalm 23:1…",
  never "Psalms 23:1".

## Tests / verification

Display-only: no plan data, asset, Room, DataStore, manifest, version, or dependency change.
The three data/Room gates are byte-for-byte intact (plan = 11, BibleTextVerificationTest = 18,
BibleDatabaseRoomOpenTest = 5). New reader pins: `ReaderScreenTest` singular header,
`ReaderViewModelTest` singular single-chapter title + plural multi-chapter portion title; the
existing reader "Psalms 23" literals flipped to "Psalm 23". 599 tests green; Kover 95.4%.
Mutations killed: (1) disabling `singularizeBookName` reddens the reader's singular pins AND the
Schedule's `ReadingFormatter` pins (proving the single source of truth); (2) flipping the
multi-chapter portion branch to `singleChapter = true` reddens only the plural portion pin.
