# Bible Data Architecture

Scope now: ship KJV text, readable, on-device, offline.
Built so that **audio-per-verse follow-along** and **book/chapter/verse deep linking** drop in later with **zero schema rewrites** and **no re-import**.

---

## 1. The keystone: canonical verse addressing

Every verse gets one stable integer ID. This is the spine everything joins on — text, audio offsets, deep links, highlights, search, cross-references.

```
verse_id = book_no * 1_000_000 + chapter * 1_000 + verse

Genesis 1:1   -> 1_001_001
John 3:16     -> 43_003_016
Revelation 22:21 -> 66_022_021
```

Why integer-encoded (not just a "Gen.3.16" string):

- **Range queries are trivial and indexed.** Whole chapter = `WHERE verse_id BETWEEN 43_003_000 AND 43_003_999`. Whole book = `BETWEEN 43_000_000 AND 43_999_999`. A 3-verse passage = a `BETWEEN`. This single property is what makes deep linking and "read this range" cheap forever.
- **Sorts correctly** with no special collation.
- **Compact** join key for the audio offset table (below).

We *also* keep the human-readable OSIS string (`John.3.16`) on the book row + a resolver, because deep-link URIs and share text want it. Integer is the internal truth; OSIS is the external face.

Multipliers (1M / 1K) safely cover the real maxima (Psalm 119 = 176 verses; Psalms = 150 chapters).

---

## 2. Schema (SQLite — ships as a prebuilt Room asset)

```sql
-- A version of the text. Multi-translation = just more rows here. No schema change.
CREATE TABLE translation (
  id            INTEGER PRIMARY KEY,
  code          TEXT NOT NULL UNIQUE,      -- 'KJV', 'BSB', 'WEB'
  name          TEXT NOT NULL,             -- 'King James Version'
  language      TEXT NOT NULL DEFAULT 'en',
  versification TEXT NOT NULL DEFAULT 'KJV', -- see landmine §6
  is_public_domain INTEGER NOT NULL,
  copyright     TEXT,                       -- required attribution string, if any
  direction     TEXT NOT NULL DEFAULT 'ltr'
);

-- Canon reference. Mostly translation-independent for the 66-book Protestant canon.
CREATE TABLE book (
  book_no       INTEGER PRIMARY KEY,        -- 1..66 canonical order
  osis_code     TEXT NOT NULL UNIQUE,       -- 'Gen', 'John', 'Rev'
  name          TEXT NOT NULL,              -- 'Genesis'
  abbrev        TEXT NOT NULL,              -- 'Gen'
  testament     TEXT NOT NULL,              -- 'OT' | 'NT'
  chapter_count INTEGER NOT NULL
);

-- The content. One row per verse per translation.
CREATE TABLE verse (
  translation_id INTEGER NOT NULL REFERENCES translation(id),
  verse_id       INTEGER NOT NULL,          -- canonical, from §1
  book_no        INTEGER NOT NULL REFERENCES book(book_no),
  chapter        INTEGER NOT NULL,
  verse          INTEGER NOT NULL,
  text_plain     TEXT NOT NULL,             -- stripped: for TTS + search
  text_markup    TEXT NOT NULL,             -- display form, light inline tags (§5)
  PRIMARY KEY (translation_id, verse_id)
);
CREATE INDEX idx_verse_book_ch ON verse(translation_id, book_no, chapter);

-- ===== Forward-thinking: present in schema now, unused until audio ships =====

-- One narrated rendering (a voice / a translation). TTS run or human narrator.
CREATE TABLE audio_track (
  id             INTEGER PRIMARY KEY,
  translation_id INTEGER NOT NULL REFERENCES translation(id),
  voice          TEXT NOT NULL,             -- 'elevenlabs:rachel', 'narrator:smith'
  format         TEXT NOT NULL DEFAULT 'm4a'
);

-- Audio file granularity = one per chapter (good balance: few files, easy resume).
CREATE TABLE audio_file (
  id          INTEGER PRIMARY KEY,
  track_id    INTEGER NOT NULL REFERENCES audio_track(id),
  book_no     INTEGER NOT NULL,
  chapter     INTEGER NOT NULL,
  uri         TEXT NOT NULL,                -- local cache path or remote URL
  duration_ms INTEGER NOT NULL
);

-- THE follow-along table: every verse -> a time window in its chapter file.
CREATE TABLE audio_segment (
  audio_file_id INTEGER NOT NULL REFERENCES audio_file(id),
  verse_id      INTEGER NOT NULL,           -- joins to verse.verse_id
  start_ms      INTEGER NOT NULL,
  end_ms        INTEGER NOT NULL,
  PRIMARY KEY (audio_file_id, verse_id)
);

-- ===== Forward-thinking: user layer (highlights, notes, bookmarks) =====
-- Keyed on the CANONICAL verse id (§6), not native — so a highlight made in
-- one translation follows the user into another. The reader translates
-- canonical -> native at render time via versification_map.
CREATE TABLE user_mark (
  id                 INTEGER PRIMARY KEY,
  canonical_verse_id INTEGER NOT NULL,
  kind               TEXT NOT NULL,         -- 'highlight' | 'bookmark' | 'note'
  color              TEXT,
  note               TEXT,
  created_at         INTEGER NOT NULL
);

-- ===== Forward-thinking: cross-translation alignment (empty until translation #2) =====
-- Sparse: stores ONLY the exceptions. No row => native_verse_id == canonical_verse_id.
-- See §6 for the full design and the relation semantics.
CREATE TABLE versification_map (
  translation_id     INTEGER NOT NULL REFERENCES translation(id),
  native_verse_id    INTEGER,               -- this translation's id; NULL when 'absent'
  canonical_verse_id INTEGER NOT NULL,      -- the hub (KJV scheme)
  relation           TEXT NOT NULL          -- 'exact' | 'absent' | 'merged' | 'split'
);
```

Design choices that pay off later:

- **`translation_id` on every content row.** Adding BSB/WEB later is `INSERT`s, never `ALTER`. The app filters by active translation.
- **Audio is a separate table joined on `verse_id`.** Text ships now with the audio tables empty. When audio arrives it's pure additive data — no migration of the text.
- **`text_plain` + `text_markup` stored side by side.** TTS and full-text search consume plain; the UI renders markup. Storing both now means you never re-import to recover formatting you stripped (§5).
- **`verse.verse_id` is *native*; `user_mark` is *canonical*.** Text is stored and displayed in each translation's own numbering (faithful); user data is stored in one canonical numbering (portable). The two are bridged by `versification_map` (§6). For KJV alone the two are identical, so this is free until translation #2.

---

## 3. Audio follow-along — how the highlight tracks the voice

Playback loop, once audio exists:

1. User opens John 3. App loads `audio_file` for (track, John, 3) and its `audio_segment` rows into memory (~26 small rows).
2. Player reports position every ~100ms.
3. Find the active verse: the segment whose `start_ms <= pos < end_ms`. (In-memory list is tiny; a binary search or even linear scan is free.)
4. Highlight that verse; auto-scroll if it left the viewport.
5. Tapping a verse → seek player to that segment's `start_ms`. Same table, read the other direction.

The synergy with your TTS plan: **because you generate audio per verse, you get `start_ms`/`end_ms` for free.** Render each verse to its own clip, concatenate into the chapter file, and record the running offset as you concat. No forced-alignment tooling needed — that's the pain human-narrated apps deal with and you skip it entirely. (If you ever ingest a human narration, *that's* when you'd reach for forced alignment like `aeneas`.)

---

## 4. Deep linking — book/chapter/verse

Canonical reference resolves three ways, all backed by the same integer:

```
URI:        bibleapp://kjv/John/3/16        (Android App Link / deep link)
OSIS:       John.3.16                        (share text, cross-refs)
verse_id:   43_003_016                        (internal nav, audio join)
```

Build one `Reference` resolver module — the single place that parses/formats `"John 3:16"`, `"John 3:16-18"`, OSIS, and URIs into `verse_id` (or a `[start, end]` range). Every screen navigates by passing a reference; the resolver turns it into the `BETWEEN` query from §1. Wiring Android App Links later is then just: parse the path → `Reference` → existing nav. No data work.

Ranges fall out automatically: `bibleapp://kjv/John/3/16-18` → `verse_id BETWEEN 43_003_016 AND 43_003_018`.

---

## 5. Verse formatting — decide now, store now

KJV isn't plain text. It has *italicized supplied words* (words the translators added for English sense, e.g. "there `was` light"), and you may later want red-letter (words of Christ), poetry indentation, and section headings.

Recommendation: store a **minimal, stable inline markup** in `text_markup` and the stripped version in `text_plain`. Don't store raw OSIS XML (too heavy to render), don't store bare plain text (you lose the italics and have to re-import to get them back). A tiny tag set is enough:

```
text_markup: "In the beginning God created the heaven and the earth."
text_markup (Gen 1:2 fragment): "...the Spirit of God moved upon the <a>face</a> of the waters."
   where <a>..</a> = translator-added word  (render italic)
```

Keep the tag vocabulary tiny and closed: added-word, words-of-Christ, line-break/poetry. Render them to spans in the UI; strip them for `text_plain`. This is the cheap insurance against a full re-import six months from now.

---

## 6. Versification — cross-translation alignment

Translations don't agree on how the text is chopped into numbered verses. Same words, different (chapter, verse). Some verses exist in one translation and not another. Left unhandled, this breaks parallel/compare view, breaks highlights when a user switches translation, and makes audio offsets non-portable. KJV alone is internally consistent, so **none of this bites until translation #2** — but the *shape* below is reserved now so the fix is additive.

### 6.1 Model: one canonical spine, star topology

- Pick **one** versification as canonical truth. **Use KJV's scheme as canonical**, because KJV is effectively a *superset* of the Textus-Receptus-vs-critical-text differences — it contains the verses modern translations omit (Matt 17:21, 1 John 5:7, etc.). A superset hub means every other translation maps in cleanly as "absent here," rather than the hub itself lacking a verse some translation has. (Caveat: a purist might pick a published neutral standard, and KJV's Psalm/Malachi numbering differs from strict Hebrew numbering; for a set of *English* translations this is the pragmatic, clean choice.)
- Every translation maps **to the hub**, never to each other. Pairwise mapping is N×N and explodes; star topology is N.

### 6.2 The three rules

1. **Text is stored and displayed in each translation's *native* numbering.** A NASB reader sees NASB's numbers and NASB's gaps. Faithful display is non-negotiable. `verse.verse_id` is native.
2. **User and cross-reference data is keyed to the *canonical* id.** Highlights, bookmarks, last-read position, cross-references live in canonical space, so they survive a translation switch. `user_mark.canonical_verse_id`.
3. **The map stores only exceptions.** Absence of a row ⇒ identity (`native == canonical`), true for ~99% of verses. The table is a few hundred rows, not 31,000.

### 6.3 Relation semantics (the `relation` column)

- **`exact`** — a shifted 1:1 correspondence. The common case: a run of native verses lines up with a shifted run of canonical ones (e.g. KJV Malachi 4 = Hebrew-numbered Malachi 3 continued). A few rows express the offset.
- **`absent`** — canonical has a verse this translation omits. *Matthew 17:21*: KJV has it; NASB jumps 20 → 22. Row: `{NASB, native=NULL, canonical=40_017_021, 'absent'}`. A highlight on canonical 40_017_021 entering NASB attaches to the nearest present verse (v20) with a small "not in this translation" marker.
- **`merged`** — many canonical → one native (translation prints e.g. "4–5" as one block). Highlight lands on the combined verse.
- **`split`** — one canonical → many native (*3 John 14* split into 14/15). Highlight spans both.

### 6.4 Render-time flow

Reader is in NASB, user has a highlight on canonical 40_017_021:
1. Look up canonical → native for (NASB, 40_017_021) in `versification_map`.
2. Row says `absent` → render the marker on the nearest present verse.
   Otherwise the row (or identity default) gives the native `verse_id` → highlight that verse.

Compare view aligns two translations by walking both through their canonical ids rather than by row position.

---

## 7. Psalm titles (superscriptions) — the verse-0 convention

The superscription ("A Psalm of David, when he fled from Absalom…") is numbered differently across traditions: KJV prints it unnumbered before verse 1; Hebrew numbering counts it as verse 1, shifting the whole psalm by one.

**Spec: store every superscription as verse 0 of its psalm.**

```
Psalm 3 title   -> verse_id = 19_003_000   (book 19, ch 3, verse 0)
Psalm 3:1       -> verse_id = 19_003_001
```

This resolves four problems with one convention:

- **Sort order** — verse 0 precedes verse 1 naturally; no special casing.
- **Faithful display** — display rule "verse 0 ⇒ render as italic heading, no number" reproduces KJV's exact presentation.
- **Audio follow-along** — narrators read the superscription aloud, so it needs its own time window. As a real verse row it gets an `audio_segment` like any other; without verse-0 it would be orphan, un-highlightable text during playback.
- **Cross-translation numbering** — a Hebrew-numbered translation that counts the title as verse 1 maps cleanly: `{HebTr, native=19_003_001, canonical=19_003_000, 'exact'}`, then native 2→canonical 1, 3→canonical 2, cascading. The off-by-one resolves through `versification_map`, with no special-case code.

Edge cases (don't build for these yet, just know the convention extends): a few psalms have **postscripts** (Habakkuk 3 ends "To the chief singer on my stringed instruments") — reserve a high verse number (e.g. 999) at the other end, same idea.

**The one thing to get right *now*, KJV-alone:** the import script must place superscriptions at verse 0 rather than dropping them or folding them into verse 1. Fixing this later means re-importing the Psalms. `versification_map` itself stays empty until translation #2.

---

## 8. Licensing note

**KJV in the UK.** Public domain in the US; Crown copyright (Cambridge) in the UK. If you distribute there, that's a flag — not a blocker for a US launch.

---

## 9. Import pipeline (build-time, not on device)

```
public-domain KJV source  ->  Python parser  ->  bible.db (SQLite)  ->  ship as app asset
   (OSIS / USFX XML)            build script         prebuilt              Room createFromAsset
```

- **Source:** `open-bibles` repo (OSIS/USFX XML) or ebible.org KJV, or the free-use Bible API. All clean PD.
- **Parser:** Python script reads the XML, computes `verse_id`, emits `text_plain` + `text_markup` (preserving `transChange type="added"` → your added-word tag), writes `bible.db`.
- **Ship:** bundle `bible.db` as an Android asset; Room's `createFromAsset` copies it on first run. **Never parse XML on device** — it's slow and wasteful when you can ship the finished DB.
- Add an FTS5 virtual table over `text_plain` in the same build step when you want search; it's additive.

---

## 10. MVP cut line — what to actually build first

Build now:
- `translation`, `book`, `verse` tables, populated with KJV.
- The import step that produces `bible.db` — including **superscriptions placed at verse 0** (§7) and `transChange`-added words preserved into `text_markup` (§5). These two are the "get it right now or re-import later" items.
- `Reference` resolver (string/OSIS ↔ `verse_id` + range).
- Chapter reader screen: query `verse` by (translation, book, chapter), render `text_markup`.

Create the tables but leave empty (so no migration later):
- `audio_track`, `audio_file`, `audio_segment`, `user_mark`, `versification_map`.

Defer entirely:
- Audio generation + the playback/highlight loop (§3).
- Android App Links wiring (§4) — resolver already supports it.
- Second translation + the populated versification map (§6).
- FTS search (§9).

The forward-thinking is entirely in the *shape*: native verse_id spine, separated audio tables, dual text columns, canonical-keyed user data, verse-0 superscriptions, and a reserved (empty) versification map. The *work* stays scoped to "KJV text, readable."
