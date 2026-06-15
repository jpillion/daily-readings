# Daily Reading Planner — Engineering Spec V3: In-app KJV Bible text

> **Owner:** Diego (Tech Lead / Android Architect) · **Status:** Draft for build · **Last updated:** 2026-06-14
> **Companion docs:** [PRD-v3.md](PRD-v3.md) (product — owns *what/why*),
> [features/bible-data-architecture.md](features/bible-data-architecture.md) (owner's architecture spec),
> [features/bible-data-architecture-review.md](features/bible-data-architecture-review.md) (the
> team review + settled owner decisions + the version-agnostic-artifact principle),
> [ENGINEERING_SPEC.md](ENGINEERING_SPEC.md) (the V1/V2 spec this continues — same voice, same D-* convention),
> [CLAUDE.md](../CLAUDE.md) (session handoff).
>
> This doc owns **how** we build V3.0: module layout, the text-artifact seam, the verse_id spine,
> the import pipeline + verification gate, the reader UI, the nav restructure, and the integration
> with the existing planner. Every product decision it builds on is settled in the PRD/review; this
> spec turns them into buildable interfaces, schema DDL, and decision records (`D-V3-*`). Where a
> *product* question is still open it is flagged **[OQ-n — not blocking data-foundation work]** and
> never sits on the critical path.

---

## 1. Purpose & scope

V3.0 graduates the app from a planner to a planner **and** a KJV reader: bundled, offline, no
account, nothing leaves the device (PRD §0–§3). This spec covers, at buildable detail:

- the new `bible/` feature area (data/domain/ui) and how it composes with the existing packages;
- the swappable text artifact + the `BibleTextSource` seam, the read-only Room `bible.db`, and the
  closed markup contract;
- the verse_id spine, the verse-level `Reference`, the resolver, and the `Portion → verse_id` bridge;
- `BookCatalog` reconciliation (artifact book table generated from the catalog, verified field-identical);
- the import pipeline + the release-blocking verification gate (Sprint-1 standard);
- the reader UI (LazyColumn keyed by verse_id, closed-tag rendering, verse-0 superscriptions, pickers, nav);
- the co-equal Schedule|Bible bottom-nav restructure and its regression mitigation;
- the reading-tap → in-app handoff and the first-run reader-choice question;
- two-Room-DB coexistence, the bundle budget, new deps, decisions, and a recommended sprint breakdown.

**Out of V3.0** (placed, not built): audio (V4, gated on the network decision — §11 below repeats the
identity boundary), multi-translation/versification *app* logic (the seam stays version-agnostic; no
second text), highlights/bookmarks/durable last-read/FTS/App Links (V3.x). The empty
`audio_*`/`versification_map`/`user_mark` tables from the owner's original spec are **cut** from the
shipped artifact (review CHANGE #CUT; D-V3-2).

**Hard invariant carried from V1/V2:** no networking. V3 adds no `INTERNET` permission, no HTTP stack,
no analytics. The read path is 100% local (NFR-V3-A, FR-V3-11).

---

## 2. Architecture overview

Unchanged pattern from the V1 spec §2: single-activity Compose, MVVM + UDF, repository pattern,
Hilt DI, domain layer free of Android framework types. V3 adds one new feature area and one
top-level structural change (bottom nav). Nothing in the existing planner's domain/data contracts
changes except two narrow, additive seams (`OpenReferenceUseCase` gains an `InApp` destination; the
provider enum gains `IN_APP`).

```
              ┌──────────────────────────────────────────────────────────────┐
              │                         UI (Compose)                          │
              │  RootScaffold + NavigationBar (Schedule | Bible)              │
              │   ├─ Schedule graph  : DayReadingsRoute, SettingsRoute (today)│
              │   └─ Bible graph     : ReaderRoute, BookChapterPickerSheet    │
              └───────────────┬──────────────────────────────────────────────┘
                              │ state (StateFlow) ▲ events (lambdas)
                              ▼                   │
              ┌──────────────────────────────────────────────────────────────┐
              │                ViewModels (ReaderViewModel, …)                │
              └───────────────┬──────────────────────────────────────────────┘
                              │ suspend / Flow
                              ▼
              ┌──────────────────────────────────────────────────────────────┐
              │  Domain (version-agnostic spine — knows NOTHING of the text)  │
              │  ReferenceResolver · PortionVerseBridge · GetChapterUseCase   │
              │  GetVerseRangeUseCase · verse_id encoding                     │
              └───────────────┬──────────────────────────────────────────────┘
                              │ BibleTextSource.getVerses(range) → List<VerseText>
                              ▼
              ┌──────────────────────────────────────────────────────────────┐
              │  Data — the SWAPPABLE artifact behind one seam                │
              │  RoomBibleTextSource → BibleDatabase (read-only, createFromAsset)│
              │  bible.db asset  (translation · book · verse)                 │
              └──────────────────────────────────────────────────────────────┘
```

The dashed line at `BibleTextSource` is the seam the whole durability argument (NFR-V3-E) rests on:
everything above it is version-agnostic; everything below it is an encapsulated, swappable file.

---

## 3. Module / package layout (§1 of the prompt)

V3 is a single Gradle module still (`:app`) — a multi-module split is not justified for a 7-person
team at this size and would fight the existing Hilt/Robolectric setup. The new code lives in a
`bible/` package tree mirroring the existing `data`/`domain`/`ui` split, plus small, named edits to
existing packages.

```
com.jpillion.dailyreadingplanner
├─ bible/
│  ├─ data/
│  │  ├─ BibleDatabase.kt                 # @Database(read-only), createFromAsset, fallbackToDestructiveMigration
│  │  ├─ VerseDao.kt                      # getVerses(start,end) range query; book/chapter convenience
│  │  ├─ VerseEntity.kt                   # maps the `verse` table row
│  │  ├─ RoomBibleTextSource.kt           # implements BibleTextSource over VerseDao
│  │  ├─ BibleAssetVersion.kt             # asset content-version + re-copy-on-update trigger
│  │  └─ markup/
│  │     ├─ BibleMarkup.kt                # the CLOSED tag vocabulary contract (single source of truth)
│  │     ├─ MarkupParser.kt               # markup string → List<MarkupSpan> (render side)
│  │     └─ MarkupStripper.kt             # strip(markup) → plain   (gate + a11y side)
│  ├─ domain/
│  │  ├─ BibleTextSource.kt               # THE seam: getVerses(range) → List<VerseText>
│  │  ├─ model/
│  │  │  ├─ VerseId.kt                    # encode/decode (book*1_000_000 + ch*1_000 + verse)
│  │  │  ├─ VerseRef.kt                   # verse-level reference (verse ∈ [0,999]) — NOT domain.model.Reference
│  │  │  ├─ VerseRange.kt                 # inclusive [startVerseId, endVerseId]
│  │  │  └─ VerseText.kt                  # (canonicalId, nativeLabel, markup) — seam return type
│  │  ├─ ReferenceResolver.kt             # parse/format string/OSIS/range ↔ VerseRange; clean-fail
│  │  ├─ PortionVerseBridge.kt            # Portion (chapter-keyed plan) → List<VerseRange>
│  │  ├─ GetChapterUseCase.kt             # (book, chapter) → ChapterContent (verses + superscription)
│  │  └─ GetPortionTextUseCase.kt         # Portion → ordered List<ChapterContent>
│  └─ ui/
│     ├─ reader/
│     │  ├─ ReaderRoute.kt                # stateful: owns ReaderViewModel + side-effects
│     │  ├─ ReaderScreen.kt               # stateless: LazyColumn keyed by verse_id
│     │  ├─ ReaderViewModel.kt
│     │  ├─ ReaderUiState.kt              # sealed: Loading | Chapter(...) | Error(retry)
│     │  ├─ VerseRenderer.kt              # markup → AnnotatedString over the closed tag set
│     │  └─ ReaderAudioSlot.kt            # reserved bottomBar + activeVerseId seam (built EMPTY)
│     └─ picker/
│        ├─ BookChapterPickerSheet.kt     # two-step book→chapter, bottom sheet
│        └─ BookChapterPickerViewModel.kt
├─ ui/navigation/
│  ├─ RootScaffold.kt                     # NEW: NavigationBar host (Schedule | Bible)
│  └─ AppNavHost.kt                       # EDITED: nested graphs, Schedule = start
├─ domain/model/BibleProvider.kt          # EDITED: + IN_APP value
├─ domain/OpenReferenceUseCase.kt         # EDITED: + ReadingDestination.InApp
├─ domain/model/ReadingDestination.kt     # EDITED: + InApp(verseRange, portion)
├─ ui/settings/SettingsScreen.kt          # EDITED: provider-option-inapp teaser → real value
└─ di/BibleModule.kt                       # NEW: Hilt wiring for the bible/ area
```

**Why `bible/` is a sibling of `data`/`domain`/`ui`, not nested inside them.** The reader is a
self-contained feature area with its own data source, its own domain, and its own UI. Grouping by
feature (not by layer) keeps the whole Bible surface discoverable and keeps Sam/Priya from hunting
across three top-level packages to follow one flow — the same template logic Sprints 4–5 used when
`ui/today/` became `ui/day/`. The version-agnostic spine types (`VerseId`, `ReferenceResolver`,
`PortionVerseBridge`) live in `bible/domain` because they are *Bible* concepts; the existing
chapter-level `domain/model/Reference.kt` and `Portion.kt` stay where they are (the planner owns
them) and the bridge depends *on* them, not the reverse.

**Hilt wiring (`di/BibleModule.kt`).**

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class BibleModule {
    @Binds @Singleton
    abstract fun bindBibleTextSource(impl: RoomBibleTextSource): BibleTextSource

    companion object {
        @Provides @Singleton
        fun provideBibleDatabase(@ApplicationContext ctx: Context): BibleDatabase =
            Room.databaseBuilder(ctx, BibleDatabase::class.java, "bible.db")
                .createFromAsset("bible/bible.db")          // packaged asset (see §2 seam, §9)
                .fallbackToDestructiveMigration(false)       // read-only; never migrate, re-copy instead
                .build()

        @Provides fun provideVerseDao(db: BibleDatabase): VerseDao = db.verseDao()
    }
}
```

`BibleTextSource` is the *only* type the domain layer injects; the Room types never escape
`bible/data`. `ReferenceResolver`, `PortionVerseBridge`, and the use cases are `@Inject constructor`
classes (no module entries needed), consistent with the existing `OpenReferenceUseCase`/`ProviderUrlBuilder`
style. The widget already reaches the planner use cases via a Hilt `@EntryPoint` (Sprint 7); the
reader is all UI-scoped, so no `@EntryPoint` is needed.

---

## 4. The swappable text artifact + the seam (§2 of the prompt)

### 4.1 The seam — `BibleTextSource`

This is the load-bearing abstraction for NFR-V3-E. One method, range-addressed, version-agnostic:

```kotlin
package com.jpillion.dailyreadingplanner.bible.domain

/** The single seam between the version-agnostic spine and whatever text artifact is loaded.
 *  Everything above this interface is indifferent to the text version (D-V3-3). */
interface BibleTextSource {
    /** Verses whose canonical id ∈ [range.startVerseId, range.endVerseId], in ascending id order.
     *  Empty list for a range with no rows (the resolver guarantees only valid ranges reach here). */
    suspend fun getVerses(range: VerseRange): List<VerseText>
}

/** One verse as the seam hands it up. The reader renders [markup] and labels the verse with
 *  [nativeLabel] — it MUST NOT assume the displayed number equals decode(canonicalId).verse,
 *  so a future differently-numbered artifact still displays faithfully (NFR-V3-E/F, D-V3-4). */
data class VerseText(
    val canonicalId: Long,      // the spine id everything joins on
    val nativeLabel: String,    // the version's own display label ("3", "3a", "title") — from the artifact
    val markup: String,         // closed-tag-set display form (§4.4)
)
```

**Decision D-V3-3 — the seam is the swap unit, the format is encapsulated.** Swappability comes from
the interface, not from the storage format. A corrected KJV, or someday a differently-numbered
artifact, is a new `bible.db` behind the same `getVerses`. No app logic changes. The reader reads
`nativeLabel` from the seam and never derives the display number from the verse_id (for KJV they are
equal, so this costs nothing today — but the rule is enforced now so V4/translation-2 is free).

### 4.2 The artifact — read-only Room `bible.db`

The artifact is a **single self-contained SQLite file** shipped as an app asset (`assets/bible/bible.db`),
opened read-only via Room `createFromAsset`. Three tables only — `translation`, `book`, `verse` —
the cut audio/versification/user tables from the owner's spec are **not** present (D-V3-2).

```sql
-- A text version. KJV is the only row in V3.0. Multi-translation = more rows, never a schema change.
CREATE TABLE translation (
  id               INTEGER PRIMARY KEY,
  code             TEXT NOT NULL UNIQUE,        -- 'KJV'
  name             TEXT NOT NULL,               -- 'King James Version'
  language         TEXT NOT NULL DEFAULT 'en',
  is_public_domain INTEGER NOT NULL DEFAULT 1,
  copyright        TEXT                         -- attribution string; NULL for PD KJV
);

-- GENERATED from BookCatalog.kt at build time (§6). Not authored. Verified field-identical.
CREATE TABLE book (
  book_no       INTEGER PRIMARY KEY,            -- 1..66 == Book.order
  name          TEXT NOT NULL,                  -- == Book.canonicalName
  usfm_code     TEXT NOT NULL UNIQUE,           -- == Book.usfmCode (USFM, NOT OSIS — D-V3-5)
  testament     TEXT NOT NULL,                  -- 'OT' | 'NT' (derived: book_no <= 39)
  chapter_count INTEGER NOT NULL                -- == Book.chapterCount
);

-- The content. One row per verse. verse=0 ⇒ superscription (§5, §7 of owner spec).
CREATE TABLE verse (
  translation_id INTEGER NOT NULL REFERENCES translation(id),
  verse_id       INTEGER NOT NULL,              -- canonical, §5.1
  book_no        INTEGER NOT NULL REFERENCES book(book_no),
  chapter        INTEGER NOT NULL,
  verse          INTEGER NOT NULL,              -- 0 = superscription
  native_label   TEXT NOT NULL,                 -- display label; KJV: "" for v0, else verse-as-string
  is_title       INTEGER NOT NULL DEFAULT 0,    -- 1 ⇒ render unnumbered italic heading (drives render, not magic v==0)
  text_markup    TEXT NOT NULL,                 -- closed-tag display form (§4.4)
  PRIMARY KEY (translation_id, verse_id)
);
CREATE INDEX idx_verse_book_ch ON verse(translation_id, book_no, chapter);
```

**Decision D-V3-6 — store `text_markup` only; derive plain at read time.** We do **not** store a
`text_plain` column. Plain text is `MarkupStripper.strip(text_markup)`, computed on demand for
TalkBack (NFR-V3-C) and for any future FTS build step. This saves ~order-of-a-few-MB of duplicate
text in the asset (R-V3-2) and makes the strip invariant (`plain == strip(markup)`) *structurally
unfalsifiable at rest* — there is no second column to drift. The gate (§7) still pins `strip(markup)`
against an independent plain witness so a parser bug in the stripper itself is caught. *(If FTS5
lands in V3.x and indexing-at-build is preferred, an FTS virtual table over `strip(markup)` is added
in the import step — additive, no schema change to `verse`.)*

**Decision D-V3-7 — `is_title` flag drives superscription rendering, not the magic `verse == 0`.**
Verse 0 is the canonical *position*; `is_title = 1` is the *render instruction* (review CHANGE #5).
Keeping them separate means the renderer never re-derives intent from an id, and a postscript
(Habakkuk 3's tail) could later be `is_title=1` at a high verse number without touching the
verse-0 convention.

### 4.3 Asset content-version + re-copy-on-update

`createFromAsset` copies the asset **once**, on first DB open, and never again while the file
exists — so a shipped text correction (e.g. a folded-superscription fix) would never reach existing
users without an explicit trigger (PRD §7, review CHANGE #4).

**Decision D-V3-8 — version the asset and re-copy on bump.** A `BibleAssetVersion` constant
(`ASSET_CONTENT_VERSION: Int`) is bumped whenever `bible.db` content changes. On app start (off the
main thread), `BibleAssetVersion` compares the constant against a value persisted in the existing
DataStore `SettingsRepository` (`bible_asset_content_version`). If the constant is newer, it deletes
the copied `bible.db` (and `-wal`/`-shm`) from `databasePath` *before* the Room builder runs, forcing
`createFromAsset` to re-copy, then writes the new version. This is a deliberate, content-driven
re-copy — **not** a Room schema migration (the schema never changes for a text correction).

> **The converse rule, enforced by this design (PRD §7):** because the asset DB is wiped on every
> content bump, **no user-writable data may ever live in `bible.db`.** All V3.x user data
> (highlights, bookmarks, durable last-read) keys to the canonical verse_id and lives in the
> read-write `ProgressDatabase` (§9, D-V3-15). The in-session last-read position (FR-V3-15) is
> ViewModel/SavedStateHandle state, not asset state.

### 4.4 The closed markup tag-set contract

The markup vocabulary is a **closed, versioned contract** (FR-V3-6) defined in exactly one place,
`bible/data/markup/BibleMarkup.kt`, and consumed by three parties: the import pipeline (emits only
these tags), the renderer (`VerseRenderer`), and the gate's strip-invariant. Adding a tag is a
versioned edit to this contract, never a silent artifact change.

| Tag (V3.0)        | Meaning                              | Render mapping                                  | Strip rule        |
|-------------------|--------------------------------------|-------------------------------------------------|-------------------|
| `<a>…</a>`        | translator-added word (KJV italics)  | `FontStyle.Italic` span                          | keep inner text   |
| `<w>…</w>`        | words of Christ (red-letter) — P1    | theme-aware color span, **off by default**       | keep inner text   |
| `<l/>`            | poetic line break — P1               | newline + hanging indent                         | → single space    |

- **V3.0 P0 ships only `<a>`.** `<w>` and `<l/>` are reserved in the contract but emitted by the
  importer **only if** the chosen source carries them cleanly (OQ-4 — P1, droppable). The renderer
  recognizes all three from day one so enabling them later is artifact-only.
- **Strip rule** (`MarkupStripper`): drop tags, keep inner text, collapse `<l/>` to a space, then
  normalize whitespace. `strip()` is pure, total, and the gate's reference implementation.
- **Closed-vocabulary invariant** (gate): every `text_markup` value, when tokenized, contains only
  tags in this table. An artifact that introduces an unknown tag fails the gate (FR-V3-6: a swappable
  artifact may not introduce a tag the reader does not render).

`VerseRenderer.render(markup): AnnotatedString` is the single render mapping; it is pure and
unit-testable on the JVM (no Compose runtime needed for the span assertions).

---

## 5. The verse_id spine + Reference / Portion resolver (§3 of the prompt)

### 5.1 verse_id encoding (`VerseId`)

```kotlin
object VerseId {
    fun encode(bookNo: Int, chapter: Int, verse: Int): Long =
        bookNo * 1_000_000L + chapter * 1_000L + verse        // Gen 1:1 = 1_001_001; John 3:16 = 43_003_016
    fun book(id: Long): Int = (id / 1_000_000L).toInt()
    fun chapter(id: Long): Int = ((id / 1_000L) % 1_000L).toInt()
    fun verse(id: Long): Int = (id % 1_000L).toInt()
    fun chapterRange(bookNo: Int, ch: Int) = VerseRange(encode(bookNo, ch, 0), encode(bookNo, ch, 999))
    fun bookRange(bookNo: Int) = VerseRange(encode(bookNo, 0, 0), encode(bookNo, 999, 999))
}
```

`Long` (not `Int`) — `66_022_021` fits in `Int`, but `Long` removes any doubt and matches Room's
native integer affinity. Multipliers safely cover the maxima (Ps 119 = 176 verses, Psalms = 150
chapters). Verse 0 (superscription) sorts before verse 1 naturally; whole-chapter range deliberately
starts at verse 0 so titles are included.

### 5.2 The verse-level reference — `VerseRef` (NOT the existing `Reference`)

**Decision D-V3-9 — a NEW `bible.domain.model.VerseRef`, distinct from `domain.model.Reference`.** The
existing chapter-level `Reference(book, chapter)` carries `require(chapter in 1..book.chapterCount)`
and has no verse component; it stays exactly as-is (the planner depends on it). The reader needs a
**verse-level** reference with the verse-0 superscription invariant:

```kotlin
data class VerseRef(val book: Book, val chapter: Int, val verse: Int) {
    init {
        require(chapter in 1..book.chapterCount) { "${book.canonicalName} ch $chapter out of range" }
        require(verse in 0..999) { "verse $verse out of [0,999]" }   // 0 = superscription. NEVER require(verse >= 1)
    }
    val verseId: Long get() = VerseId.encode(book.order, chapter, verse)
}
```

> **The trap, called out explicitly (FR-V3-3, review CHANGE #5):** the live `Reference` has a
> `require(chapter in 1..chapterCount)` lower-bound guard at chapter granularity. The analogous
> verse guard MUST be `verse in 0..999`, **not** `verse >= 1` — a `>= 1` guard silently drops every
> Psalm title at construction. This is pinned by the resolver gate (§7).

### 5.3 The resolver — `ReferenceResolver` (internal nav, NOT egress)

```kotlin
class ReferenceResolver @Inject constructor() {
    /** Parse a human/OSIS reference to a verse_id range. Returns null on ANY malformed input —
     *  never a plausible-but-wrong range (FR-V3-3, the worst failure for a scripture app). */
    fun resolve(input: String): VerseRange?       // "John 3:16", "John 3:16-18", "John 3", "John", "John.3.16"
    fun resolveChapter(book: Book, chapter: Int): VerseRange   // whole chapter incl. verse 0
    fun format(range: VerseRange): String          // canonical display string
    fun formatOsis(range: VerseRange): String      // "John.3.16" — reserved for V3.x App Links
}
```

- **Clean-fail is a release-gated invariant.** Unknown book, out-of-range chapter, reversed range,
  garbage — all return `null`. The resolver never guesses. Gate (§7) pins a corpus of malformed
  inputs ↦ `null` and a corpus of valid inputs ↦ exact ranges, including "John" vs "1/2/3 John"
  disambiguation, cross-chapter ranges, whole-book, and verse-0.
- **Book-name lookup reuses `BookCatalog`** (`findByName`) plus a small alias table for the numbered
  Johns / common abbreviations; there is no second book table.

**Decision D-V3-10 — the resolver is strictly distinct from `ProviderUrlBuilder`.** `ReferenceResolver`
is **internal navigation** (string/OSIS ↔ verse_id range, for the reader and for V3.x deep links).
`ProviderUrlBuilder` is **external egress** (portion → a third party's URL scheme). They share the
*idea* of grouping consecutive chapters but live on opposite sides of the app boundary and must not
be merged: egress carries provider quirks (BLB trailing slash, Bible Gateway passage search), nav
carries verse_id ranges. The `consecutiveRuns` grouping logic *is* reused by value (see §5.4).

### 5.4 The Portion → verse_id bridge — `PortionVerseBridge`

This is the MVP connective tissue (FR-V3-2, review CHANGE #2): the plan is chapter-keyed
(`Portion = List<Reference>`), the text is verse-keyed. The bridge maps a `Portion` to an ordered
list of verse ranges the reader renders in sequence.

```kotlin
class PortionVerseBridge @Inject constructor() {
    /** Each Reference (book, chapter) → its whole-chapter verse range, in portion order.
     *  Multi-chapter portions yield multiple ranges; the two-book portion (Jun 19 / Dec 19 =
     *  2 John + 3 John) yields two ranges across two books — NEVER assume refs share a book. */
    fun rangesFor(portion: Portion): List<VerseRange> =
        portion.refs.map { VerseId.chapterRange(it.book.order, it.chapter) }
}
```

- **Reuse of `consecutiveRuns`:** the bridge groups consecutive same-book chapters for *display
  grouping* (the reader shows "Genesis 1–2" as one header span) using the **same grouping algorithm**
  as `ProviderUrlBuilder.consecutiveRuns`. Per D-V3-10, the data layer cannot depend on UI and the
  reader cannot depend on `data/reference`, so the algorithm is lifted into a tiny pure helper in
  `bible/domain` (`ConsecutiveChapterRuns`) and **both** call sites use it. The existing
  `ProviderUrlBuilderTest` equivalence pin on the two-book portion is mirrored for the bridge.
- **`GetPortionTextUseCase`** consumes the ranges, calls `BibleTextSource.getVerses` per range, and
  returns ordered `ChapterContent` blocks (book header + verses + superscription) the reader renders
  top-to-bottom. The whole-portion render (every chapter, both books of Jun 19/Dec 19) is pinned by
  M-V3-4 tests.

---

## 6. BookCatalog reconciliation (§4 of the prompt)

**The artifact `book` table is GENERATED from `BookCatalog.kt` at build time and verified
field-identical** (PRD §7, review CHANGE #1, D-S9-1 anti-drift). There is **no** parallel, no
hand-authored book table, and no OSIS-vs-USFM drift.

- **Generation:** `tools/build_bible_db.py` does not author book rows. The build emits a machine
  fixture `app/src/test/resources/bible/book_catalog_export.json` from `BookCatalog.kt` (a tiny
  `:app` Gradle task / pinned codegen step `exportBookCatalog`), and the Python importer reads that
  fixture to populate `book`: `book_no = order`, `name = canonicalName`, `usfm_code = usfmCode`,
  `chapter_count = chapterCount`, `testament = if (order <= 39) "OT" else "NT"`.
- **Decision D-V3-5 — the artifact uses USFM codes, matching `BookCatalog`, never OSIS.** The owner's
  original spec used OSIS (`Gen`, `John`); we store USFM (`GEN`, `JHN`) because that is what
  `Book.usfmCode` already holds and what every existing URL builder uses. OSIS and USFM are *not*
  string-equal for Joel/Nahum/Mark/Ezekiel/Philippians/Jude, so picking USFM eliminates a real drift
  source. An `osisCode` token column is added to `BookCatalog` **only if/when** V3.x App Links need
  it (deferred — not now), as an Nth catalog column, never a second table.
- **Build-time verification (the reconciliation gate):** `BibleTextVerificationTest` reads
  `book_catalog_export.json` (the catalog's own truth) and asserts the shipped `bible.db` `book`
  table is field-identical row-for-row (66 rows, every column), AND that `verse` coverage matches
  the catalog's chapter counts (every (book, chapter) in `1..chapterCount` present, none extra). The
  catalog stays the single home of book structure; the artifact is checked *against* it.

---

## 7. Import pipeline + verification gate (§5 of the prompt) — the release blocker

This is the tail of the one critical path (PRD §12) and is built to **Sprint-1 standard**: a
committed, reproducible build script; a checked-in binary asset; and an offline CI-gating test that
runs against the **exact asset shipped in the APK**. It is the project's *second* core-IP gate
(the plan was the first, `ReadingPlanVerificationTest`).

### 7.1 `tools/build_bible_db.py` — the import pipeline

```
PD KJV source (OSIS/USFX XML)  +  book_catalog_export.json
        │   pinned source SHA + input checksum (committed)
        ▼
   Python parser  ──►  computes verse_id, places superscriptions at verse 0 (is_title=1),
        │              preserves transChange-added words → <a>…</a>, emits text_markup
        ▼
   bible.db (SQLite, deterministic)  ──►  committed as app/src/main/assets/bible/bible.db
```

- **Pinned + deterministic.** The script records the source's SHA-256 and the input file checksum in
  `docs/data/README.md` (extending the Sprint-1 reconciliation log). Output is byte-deterministic
  (fixed row order by verse_id, no timestamps, `PRAGMA` settings pinned, VACUUM at the end) so the
  CI `data-rebuild` job can re-derive `bible.db` and assert a byte-diff of zero against the committed
  asset (mirrors the Sprint-1/Sprint-9 rebuild discipline).
- **Content correctness done once (re-import-or-nothing items — PRD §9):** verse-0 superscriptions
  with `is_title=1`; `transChange type="added"` → `<a>` added-word markup preserved. These are the
  two items that, if wrong, force a re-import; they are P0.
- **Checked-in binary, no LFS.** `bible.db` is a plain ~4–5 MB binary committed to git (review:
  "no LFS"). It is regenerated by the script, not edited.

### 7.2 Candidate PD sources + second-source strategy (resolves OQ-4 mechanism, R-V3-3)

- **Primary corpus (markup-bearing — must carry `transChange` added words):** an OSIS/USFX KJV that
  preserves translator-added-word tagging. Candidates: the **`open-bibles`** repo's KJV OSIS, or
  **eBible.org's** KJV (engKJV) USFX — both PD, both carry `transChange`/added-word markup, which is
  required for the `<a>` floor. Poetry (`<l>`) and words-of-Christ (`<w>`) availability in the chosen
  corpus settles OQ-4's P1 items; added words + superscriptions are required regardless.
- **Decision D-V3-11 — split the witnesses: markup from one corpus, count/text witness from a
  genuinely independent second (resolves OQ-4's "one source vs two").** The added-word markup and
  superscription placement come from the **primary** corpus (the only one that needs rich tagging).
  The **second-source equality witness** (per-chapter verse counts + a full-text diff) comes from a
  **different upstream** — e.g. eBible if open-bibles is primary, or a third independent PD KJV
  (Sword module / zefania XML) — chosen so the two are **not the same upstream re-mirrored**. The
  importer emits both, and the build checksums the two raw sources and asserts they are
  **distinct** (the Sprint-1 "same-upstream re-mirror" trap, R-V3-3). The reconciliation log records
  every per-verse text difference resolved, exactly like Sprint 1's 7-conflict table.

### 7.3 `BibleTextVerificationTest` — the offline gate

Runs in `testDebugUnitTest` against the shipped `bible.db` via the **`sqlite-jdbc` test driver**
(adds ~1–2 s; no device, no Robolectric DB needed for the structural reads). The correctness bar
(PRD §8, review gate):

1. **Structural invariants (no second source):** exactly **66 books / 1,189 chapters / 31,102
   verses**; no duplicate `verse_id`; **verse numbering contiguous within every chapter** (the
   Sprint-1 coverage analog — catches a verse dropped at an XML boundary); `verse_id` internally
   consistent (`encode(book_no,chapter,verse) == verse_id` every row); no empty `text_markup`;
   coverage matches `book_catalog_export.json` chapter counts (§6).
2. **`book` table reconciliation:** field-identical to `book_catalog_export.json`, 66 rows (§6).
3. **Second-source equality:** a checked-in `kjv_verse_counts.csv` (1,189 rows of
   `(book, chapter, verse_count)` from the **independent** witness) asserted chapter-by-chapter; plus
   the build-time full-text diff vs the second corpus with the reconciliation log; plus the
   **checksum-distinctness** assertion on the two raw sources (R-V3-3).
4. **Superscriptions:** a checked-in `kjv_superscriptions.csv` of the **exact** titled-chapter set
   (most Psalms, but NOT Pss 1, 2, 10, 33; Habakkuk 3 has one), asserted **both directions**
   (every listed chapter has `is_title=1` at verse 0; no unlisted chapter does), with **spot-pinned
   title text** for Ps 3 and Ps 51 to catch the "folded into verse 1" failure.
5. **Markup:** strip invariant `independentPlain == strip(text_markup)` for all 31,102 verses; only
   the closed tag vocabulary appears (§4.4); an added-word **floor** (added-word count > a pinned
   minimum — zero ⇒ the parser silently dropped `transChange`) plus ~10 hand-pinned added-word verses.
6. **Famous-verse pins:** a handful of exact-text pins (Gen 1:1, John 3:16, Ps 23:1, Rev 22:21) as
   a fast human-legible canary.
7. **Reference resolver:** exhaustive pure-logic pins — "John" vs "1/2/3 John", ranges, cross-chapter,
   whole-book, verse-0 — and **malformed input ↦ `null`, never a plausible-but-wrong range**.

The `data-rebuild` CI job (§7.1) is the protecting wrapper: it re-derives the asset and diffs, so a
hand-edited `bible.db` can never sneak past the binary commit. Release is blocked on both
(FR-V3-12, M-V3-1).

---

## 8. The reader UI (§6 of the prompt)

`ReaderScreen` is stateless over `ReaderUiState`; `ReaderRoute` owns the `ReaderViewModel` and
side-effects, mirroring the established `TodayRoute`/`DayReadingsRoute` pattern.

### 8.1 The verse-keyed `LazyColumn` (the rewrite-forcing P0)

```kotlin
LazyColumn(state = listState) {
    chapterContent.blocks.forEach { block ->                 // one block per chapter in the portion
        item(key = "hdr-${block.bookNo}-${block.chapter}") { ChapterHeader(block) }
        items(block.verses, key = { it.canonicalId }) { v -> VerseItem(v) }   // KEYED BY verse_id
    }
}
```

**Decision D-V3-12 — every verse is a `LazyColumn` item keyed by `canonicalId` from day one
(FR-V3-1, review).** This is the *one* structural choice that, gotten wrong, forces a rewrite when
the V4 audio verse-highlight (and V3.x verse highlights / deep links) land — you cannot retrofit
per-verse addressability/scroll-to into a single run-on `Text`. The surface still *reads* as flowing
prose (verses flow visually, de-emphasized numbers), but each verse is individually addressable and
`scrollToItem`-locatable. This is a P0 requirement, not polish.

### 8.2 Rendering — markup, verse numbers, superscriptions

- **`VerseRenderer.render(markup)` → `AnnotatedString`** over the closed tag set (§4.4): `<a>` →
  italic, `<w>` → theme-aware color (off by default, P1), `<l/>` → line break + indent (P1). A verse
  item prepends a small, de-emphasized **`nativeLabel`** (from the seam — never derived from the id,
  D-V3-4) as a superscript-style span; the body reads as prose (U15).
- **Verse-0 superscription** (`is_title = 1`): rendered as an **unnumbered italic heading** before
  verse 1, with **`heading()` semantics** for TalkBack (NFR-V3-C), never as a numbered verse, never
  dropped (FR-V3-6).
- **`fontScale` reuse (FR-V3-17):** the reader inherits the app-wide `fontScale` (Sprint 8) — it's
  applied at the theme `LocalDensity` level already, so the reader's type scales for free; the reader
  adds no separate size control.
- **Accessibility (NFR-V3-C, M-V3-5):** TalkBack speaks **plain text** (`strip(markup)`), never the
  markup tags; headings/superscriptions carry heading semantics; 48dp touch targets on authored
  controls (Prev/Next, picker entry) — extends `AccessibilityGateTest`.

### 8.3 Book→chapter picker + chapter navigation

- **`BookChapterPickerSheet`** — two-step bottom sheet (FR-V3-4): step 1 = 66 books grouped by
  testament (OT/NT, derived from `book_no <= 39`); step 2 = a chapter grid sized to the book's
  `chapterCount` (from `BookCatalog`). Selecting (book, chapter) navigates the reader.
- **Chapter navigation (FR-V3-5):** the reader hosts a `HorizontalPager` over chapters within the
  *browse* flow (swipe between adjacent chapters) **plus** visible Prev/Next controls — both compute
  the adjacent (book, chapter) by walking `BookCatalog` order (Rev 22 → end; Gen 1 → start; chapter
  rollover crosses book boundaries). *Within a portion open* (the U13 tap-in flow), the reader shows
  the portion's chapters in sequence as a single scroll (§5.4); Prev/Next then steps to the
  chapters adjacent to the portion's bounds.

### 8.4 Last-read position (FR-V3-15, in-session only for V3.0)

**Decision D-V3-13 — in-session last-read is ViewModel/`SavedStateHandle` state; durable
cross-session is V3.x.** Returning to the Bible destination within a session restores the last
(book, chapter) and scroll anchor via `SavedStateHandle` (survives config change + process death
within the session). **Durable** cross-session last-read keys the canonical verse_id into the
**read-write `ProgressDatabase`** (§9) and is explicitly V3.x (PRD FR-V3-15 note) — it is **never**
written to the read-only asset.

### 8.5 The reserved audio seam (built EMPTY)

**Decision D-V3-14 — reserve the audio shape in the reader, build nothing (V4 gate).** Per the
review (Priya), the reader exposes two empty seams so V4 audio is additive:
- a `bottomBar` slot in the reader `Scaffold` (`ReaderAudioSlot`, renders nothing in V3.0);
- a single nullable `activeVerseId: Long?` in `ReaderUiState` (always `null` in V3.0) that the
  verse item already reads for a highlight background.

No player dependency, no `INTERNET`, no audio table, no service ships. The seams cost ~10 lines and
make the "yield-to-user autoscroll + tap-to-seek" follow-along a contained V4 addition. Priya owns
final visual polish; this section sets the structural contract she builds within.

---

## 9. Bottom-nav restructure (§7 of the prompt)

**Decision D-V3-16 — co-equal Schedule | Bible `NavigationBar`, Schedule as start (owner decision
#3).** This overrides Priya's pushed-destination recommendation per the owner; it reframes the app
as planner + reader and spends ~80dp permanently (R-V3-1, accepted).

```kotlin
@Composable
fun RootScaffold() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination
    Scaffold(bottomBar = {
        NavigationBar {
            NavigationBarItem(
                selected = current.isIn(Graph.SCHEDULE), onClick = { nav.switchTab(Graph.SCHEDULE) },
                icon = { Icon(Icons.Filled.DateRange, null) }, label = { Text(/* OQ-3 */ "Schedule") })
            NavigationBarItem(
                selected = current.isIn(Graph.BIBLE), onClick = { nav.switchTab(Graph.BIBLE) },
                icon = { Icon(/* book glyph — OQ-3 */ ...) }, label = { Text(/* OQ-3 */ "Bible") })
        }
    }) { padding -> AppNavHost(nav, Modifier.padding(padding)) }
}
```

- **AppNavHost becomes two nested graphs**, `startDestination = Graph.SCHEDULE`:
  - `Graph.SCHEDULE` wraps the existing `DayReadingsRoute` (start) + `SettingsRoute` (pushed within
    the Schedule graph — unchanged behavior; the gear still pushes Settings).
  - `Graph.BIBLE` wraps `ReaderRoute` (start) + the picker (a sheet, not a route). The reading-tap
    in-app handoff (§10) navigates *into* the Bible graph with a portion argument.
- **State preservation across tabs** (U18): `switchTab` uses `saveState = true` /
  `restoreState = true` with `popUpTo(graphRoot){ saveState = true }` so the reader remembers its
  chapter and the schedule remembers its day within a session.
- **The Schedule top bar's existing actions stay** (Today jump, gear → Settings, stats inline). The
  stats panel and one-screen-fit work of Sprints 16/18/20 must be re-confirmed *with the bottom bar
  present* on the device pass (R-V3-1).

**Decision D-V3-17 — mitigate the `AppNavHost` JVM-untested debt with Robolectric nav coverage
(R-V3-5).** The restructure wraps every existing screen in a new scaffold and touches the
historically JVM-untested `AppNavHost` (Sprint 6 debt). The restructure sprint **budgets nav-regression
verification**: Robolectric tests (under `testDebugUnitTest`, the existing Compose-UI-test harness)
that assert tab switching, that every existing screen stays reachable (Schedule day-pager, Settings,
the reader, the picker), that Schedule is the start destination, and that back-stack state is
preserved across a tab switch. This retires part of the long-standing nav debt rather than growing it.

---

## 10. Integration: reading-tap handoff + first-run choice (§8 of the prompt)

### 10.1 `BibleProvider.IN_APP` promotion + the `OpenReferenceUseCase` seam

- **Decision D-V3-18 — promote the `provider-option-inapp` teaser to a real
  `BibleProvider.IN_APP`.** The Sprint-14 disabled teaser becomes a real, selectable enum value.
  `IN_APP` is `multiRefCapable = true` (it renders the *whole* portion natively, §5.4) and
  `requiresApp = false`. The enum NAME `IN_APP` is the persisted id (existing
  `BibleProvider.fromStored` contract; unknown ids still degrade to BLB).
- **`OpenReferenceUseCase` gains an in-app branch** and `ReadingDestination` gains `InApp`:

```kotlin
sealed interface ReadingDestination {
    data class Web(val url: String) : ReadingDestination
    data class MySwordApp(val url: String, val fallbackUrl: String) : ReadingDestination
    data class InApp(val portion: Portion) : ReadingDestination   // NEW — carries the portion, not a URL
}
```

  `OpenReferenceUseCase` reads the provider at tap time (unchanged): `IN_APP → InApp(portion)` (no
  URL built); everything else unchanged. The UI side (the existing `CustomTabLauncher` call site in
  `DayReadingsRoute`) gains one branch: `InApp` navigates into the Bible graph with the portion
  (§9) instead of launching a Custom Tab. `Web`/`MySwordApp` paths are byte-for-byte unchanged
  (BLB/BG/YouVersion/MySword still work — FR-V3-8, R-V3-4 "demote, don't retire").

### 10.2 First-run reading-destination question (extends Sprint-19)

- **Decision D-V3-19 — extend the Sprint-19 first-run prompt flow with a reading-destination
  question; in-app is NEVER a silent default (owner decision #4, FR-V3-7/9).** The existing
  Sprint-19 first-run gate (`ResolveTrackingStartPromptUseCase`/`CompleteTrackingStartPromptUseCase`,
  shown over the day screen) is extended with a second one-question step: *"Read in the in-app Bible,
  or open an external app?"* The answer writes the `bible_provider` preference (`IN_APP` or keeps the
  external default). If unanswered (process death), it re-asks — it is the gate; the app must not have
  silently chosen `IN_APP`.
- **Upgrade path (FR-V3-9):** existing users with an explicit external provider choice are **not**
  re-prompted and **not** switched — same D-S14-1 indistinguishability discipline (a stored provider
  value means the question was already effectively answered). New installs answer the question; the
  marker that the first-run flow already completed gates re-prompting. Pinned by M-V3-6 tests.

### 10.3 Settings (FR-V3-8)

`SettingsScreen.kt`'s "Open readings in" dropdown: the disabled `provider-option-inapp` teaser row
becomes a real selectable `IN_APP` item (top of the list per OQ-1 emphasis question). All external
providers stay, unchanged. The choice persists and is read at tap time (existing seam). No new
chooser UI — one row changes from disabled to enabled.

---

## 11. Two-Room-DB coexistence, bundle budget, dependencies (§9 of the prompt)

### 11.1 Two Room databases — deliberate and isolated (D-V3-15)

- **`ProgressDatabase`** (existing): read-**write**, migrations, `exportSchema = true`, `app/schemas/`.
  The home for ALL user data — marks today, and any V3.x highlights/bookmarks/durable-last-read,
  keyed by canonical verse_id (PRD §7, review CHANGE #3). Never holds text.
- **`BibleDatabase`** (new): read-**only**, `createFromAsset`, **no migrations** (re-copy-on-update
  instead, §4.3), `exportSchema = false` (an asset DB has no migration history to export; the gate
  validates structure instead). Never holds user data.

The two never share a connection, a DAO, or an entity. This is the clean reconciliation of "user
data survives a text correction" (it lives in the RW DB) with "text is a swappable wiped-on-update
asset" (the RO DB).

### 11.2 Bundle-size budget (NFR-V3-D, M-V3-7, R-V3-2)

**Decision D-V3-20 — set the V3.0 bundle budget at +6 MB for the text asset; build fails/flags
above it.** Expected `bible.db` size with markup-only storage (D-V3-6, no `text_plain` column) is
~4.5 MB; the +6 MB budget leaves headroom for the index and a future FTS table without a re-budget,
while staying far under the Play bundle ceiling and keeping the app lean (the current app is ~5.7 MB).
A CI check (`bundleRelease` size assertion, extending the Sprint-9 `release-bundle` job) flags or
fails if the text asset pushes the bundle past budget. **This budget is precisely why audio
(~1.6–2 GB) cannot be bundled and is V4** (PRD §3) — the number makes the boundary concrete.

### 11.3 New dependencies (version catalog)

| Dependency | Scope | Why |
|---|---|---|
| `androidx.room:room-runtime` / `room-ktx` / `room-compiler` | main | **already present** (ProgressDatabase) — `BibleDatabase` reuses it, no new dep |
| `org.xerial:sqlite-jdbc` | **test** | the offline gate driver (§7.3), reads the shipped `bible.db` on the JVM in `testDebugUnitTest` |
| `androidx.compose.foundation` (LazyColumn/HorizontalPager) | main | **already present** (the day pager uses HorizontalPager) — no new dep |

Net new runtime dependencies: **zero**. Net new test dependency: **one** (`sqlite-jdbc`). The
importer's Python deps (`tools/`) are build-time only, pinned in `tools/requirements.txt`, not shipped.
No `INTERNET`, no media, no networking dependency of any kind (NFR-V3-A).

---

## 12. Decisions (D-V3-*) — index

| ID | Decision |
|---|---|
| D-V3-1 | V3.0 stays a single `:app` module; new code in a feature-grouped `bible/` tree (data/domain/ui). |
| D-V3-2 | Ship only `translation`/`book`/`verse`; cut the empty `audio_*`/`versification_map`/`user_mark` tables (re-ship ≠ migration). |
| D-V3-3 | The `BibleTextSource` seam is the swap unit; the SQLite format is an encapsulated detail. |
| D-V3-4 | The reader reads `nativeLabel` from the seam, never derives the display number from the verse_id. |
| D-V3-5 | The artifact `book` table uses USFM codes (matching `BookCatalog`), never OSIS; OSIS column deferred to V3.x App Links. |
| D-V3-6 | Store `text_markup` only; derive plain via `MarkupStripper` (saves a column, makes the strip invariant unfalsifiable at rest). |
| D-V3-7 | `is_title` flag drives superscription rendering, not the magic `verse == 0`. |
| D-V3-8 | Asset content-version constant + re-copy-on-update; user data therefore never lives in the asset. |
| D-V3-9 | New verse-level `bible.domain.model.VerseRef` (verse ∈ [0,999]); the chapter-level `Reference` is untouched. |
| D-V3-10 | `ReferenceResolver` (internal nav) is strictly distinct from `ProviderUrlBuilder` (external egress); `consecutiveRuns` lifted to a shared pure helper. |
| D-V3-11 | Markup from the primary corpus; the count/text equality witness from a genuinely independent second corpus, checksum-distinct (resolves OQ-4 mechanism, R-V3-3). |
| D-V3-12 | The reader is a `LazyColumn` keyed by verse_id from day one (the rewrite-forcing P0). |
| D-V3-13 | In-session last-read = `SavedStateHandle`; durable cross-session last-read = V3.x in the RW DB. |
| D-V3-14 | Reserve the audio seam (empty `bottomBar` slot + `activeVerseId`); build no player/audio/network in V3. |
| D-V3-15 | Two isolated Room DBs: RW `ProgressDatabase` (user data), RO `BibleDatabase` (text asset). |
| D-V3-16 | Co-equal Schedule | Bible `NavigationBar`, Schedule as start (owner decision #3). |
| D-V3-17 | Mitigate the `AppNavHost` JVM-untested debt with Robolectric nav-regression coverage (R-V3-5). |
| D-V3-18 | Promote the `provider-option-inapp` teaser to a real `BibleProvider.IN_APP` (multiRefCapable, no app required). |
| D-V3-19 | Extend the Sprint-19 first-run flow with the reading-destination question; in-app never a silent default; upgraders' explicit choices preserved. |
| D-V3-20 | Bundle budget = +6 MB for the text asset; CI flags/fails above it. |

### Open product questions — NOT blocking the data-foundation work

These are routed to the owner/Priya and sit **off** the critical path (PRD §12). None gates the
source-selection → import → asset → gate chain or the reader's internal structure.

- **[OQ-1 — Owner]** First-run emphasis: is in-app *pre-selected/recommended* within the question, or
  perfectly neutral? (Affects one default-selection flag in the first-run dialog, §10.2.)
- **[OQ-2 — Owner]** Upgrade path: leave external users untouched (recommended), or show a one-time
  dismissible "in-app Bible is here" notice? (Affects whether an upgrade banner ships, §10.2.)
- **[OQ-3 — Owner/Priya]** Nav labels/icons: "Bible" vs "Read" vs glyph-only; "Schedule" vs "Today"
  vs "Plan". (String/icon sign-off only, §9.)
- **[OQ-5 — Owner]** The courtesy UK Cambridge free-use permission request (AR-1, accepted risk) —
  file for certainty or leave as-is. (No code impact.)

OQ-4 (poetry/red-letter markup availability) is a **data** question and IS on the data path — it is
resolved mechanically by D-V3-11/§7.2 (the chosen corpus determines whether `<w>`/`<l/>` ship; added
words + superscriptions are required regardless). It gates only the P1 items FR-V3-14/16, never P0.

---

## 13. Recommended sprint-level breakdown (§10 of the prompt)

Sequenced from the **one critical path** (PRD §12): source → import → asset → gate must land before
any reader UI can be *verified*. Five sprints. Morgan turns these into the execution plan + tickets.

**Sprint A — Bible data foundation (the critical path; gating).**
Select the two genuinely-independent PD KJV sources + checksum-distinctness (R-V3-3); build
`tools/build_bible_db.py` (pinned SHA, deterministic, verse-0 superscriptions, `<a>` added-word
markup); the `exportBookCatalog` task + `book_catalog_export.json`; the generated-and-verified `book`
table (§6); commit `bible.db`; the read-only `BibleDatabase` + `createFromAsset` + asset-version
re-copy (§4.2–4.3); the closed markup contract + `MarkupStripper`. **Deliverable gate:
`BibleTextVerificationTest` green (§7.3) + the `data-rebuild` CI job.** Nothing downstream is
verifiable until this ships. *This is the Sprint-1 of V3.*

**Sprint B — The spine: seam, resolver, Portion bridge.**
`BibleTextSource` + `RoomBibleTextSource` + `VerseDao`; `VerseId`; `VerseRef` (the `verse ∈ [0,999]`
invariant); `ReferenceResolver` (clean-fail) + the resolver gate pins; the shared `consecutiveRuns`
helper; `PortionVerseBridge`; `GetChapterUseCase`/`GetPortionTextUseCase`. Pure-JVM-testable, no UI.
Peers downstream of A.

**Sprint C — The reader UI.**
`ReaderRoute`/`ReaderScreen`/`ReaderViewModel`; the verse-id-keyed `LazyColumn` (D-V3-12);
`VerseRenderer` (closed-tag → AnnotatedString) + verse-0 superscription heading; the two-step
book→chapter picker; `HorizontalPager` chapter nav + Prev/Next; `fontScale` reuse; in-session
last-read (`SavedStateHandle`); the **empty** audio seam (D-V3-14); accessibility-gate extension.

**Sprint D — Nav restructure + integration.**
`RootScaffold` + co-equal `NavigationBar` (D-V3-16); nested Schedule/Bible graphs, Schedule as start;
the Robolectric nav-regression coverage (D-V3-17, R-V3-5); `BibleProvider.IN_APP` promotion;
`OpenReferenceUseCase`/`ReadingDestination.InApp` + the `DayReadingsRoute` tap-handoff branch;
Settings teaser → real value; extend the Sprint-19 first-run flow with the reading-destination
question (D-V3-19); bundle-size CI check (D-V3-20).

**Sprint E — V3.0 hardening + release.**
Consolidated device pass (the AC-device-pass tags: offline read, instant feel, whole-portion render,
nav state preservation, one-screen-fit-with-bottom-bar, reader on a real device at large font);
M-V3-2 faithful-presentation owner/design sign-off (Gen 1, titled Psalm 3/51, untitled Psalm 1, the
Jun 19 two-book portion); string/tone sign-offs (OQ-3 nav labels); version bump; AR-1 recorded;
closed-track rollout via the tag-to-Play pipeline.

> **Sequencing note for Morgan:** A is strictly first and strictly serial — do **not** parallelize
> source-selection against reader UI (there is no asset to render or gate against until A completes,
> PRD §12). B is verifiable the moment A lands. C/D are peers downstream of B (C needs the seam; D
> needs the resolver + the handoff types) and can overlap across people. E is last. The OQ-1/2/3/5
> owner questions can be answered any time before D/E and never block A/B.
