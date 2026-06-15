# V3 Sprint B — The spine: seam, resolver, Portion bridge

> **EM:** Morgan · **Status:** DONE (uncommitted; main session verifies + commits) ·
> **Date:** 2026-06-14 · **Next:** `sprint-00C-reader-ui`

## Goal outcome — MET

The version-agnostic spine over the now-trusted Sprint-A asset. "Given a `Portion` or a
reference string, here are the verse_id ranges and the verse text" is callable through ONE
seam and exhaustively tested — the whole-portion render (incl. the Jun 19/Dec 19 two-book
portion) is correct, and the resolver clean-fails on garbage. Pure-JVM, **no UI**.

## Current capability (working software)

- A caller can resolve a human/OSIS reference string to a canonical verse_id range and pull the
  verse text through `BibleTextSource.getVerses` — `"John 3:16"`, `"John 3"`, `"John"`,
  `"John 3:16-18"`, `"John.3.16"`, `"1 John 2"`, `"Ps 23"` all resolve to exact ranges;
  anything malformed (unknown book, out-of-range chapter/verse, reversed range, cross-chapter
  range, garbage) returns `null` — **never a plausible-but-wrong verse.**
- A caller can hand a reading-plan `Portion` to `GetPortionTextUseCase` and get back ordered
  `PortionContent` blocks ready to render top-to-bottom — multi-chapter portions and the
  two-book Jun 19/Dec 19 portion (2 John + 3 John) both render in order, with Psalm
  superscriptions surfacing as the first verse (`isTitle = true`).
- The seam is bound in Hilt (`RoomBibleTextSource → BibleTextSource`); the domain injects the
  seam alone, Room types stay encapsulated in `bible/data`.

## Tickets (administrative record)

| Ticket | Status | Note |
|---|---|---|
| VB-T1 `VerseId` encode/decode + `VerseRange` + range helpers | ✅ | `Long` ids; `chapterRange` starts at v0 |
| VB-T2 `VerseText` + `BibleTextSource` seam + `RoomBibleTextSource` + bind | ✅ | reads `native_label` (D-V3-4); `BibleBindsModule` added |
| VB-T3 `VerseRef` (verse ∈ [0,999]) | ✅ | Psalm-title trap mutation-pinned |
| VB-T4 `ReferenceResolver` (clean-fail) + resolver gate | ✅ | alias table; bare "John" = Gospel; cross-chapter → null |
| VB-T5 shared `ConsecutiveChapterRuns` + `ProviderUrlBuilder` re-point | ✅ | private `consecutiveRuns` deleted; cross-book guard pinned |
| VB-T6 `PortionVerseBridge` + content models + use cases | ✅ | two-book portion (M-V3-4) pinned |

421/421 tests (46 new), full standing pipeline green, Kover 96.2% on domain/data (≥70% floor),
**5 mutations killed**, **both data gates untouched (Sprint-1 plan gate = 7, `BibleTextVerificationTest` = 18).**

## Decisions & rationale (this sprint)

- **Cross-chapter ranges NOT supported in V3.0 (clean-fail to `null`).** `"John 3:16-4:2"` →
  `null`. No V3.0 consumer needs them (the reader browses whole chapters and opens whole
  portions); a verse-count-aware end-of-chapter resolution is scope the goal doesn't need.
  Same-chapter ranges (`"John 3:16-18"`) ARE supported. Pinned.
- **Book-name parsing = `BookCatalog` + an alias map** (`ReferenceResolver.ALIASES`, built once):
  lowercased canonical names, numbered-book forms (`1john`, `i john`), `Psalm`/`Ps`, and common
  USFM/abbrev tokens. **Ambiguity rule: bare "John" is ALWAYS the Gospel (order 43), never a
  numbered John;** "1/2/3 John" resolve to 62/63/64. Unknown → `null`. There is no second book
  table — the catalog stays the single home of book structure.
- **`PortionVerseBridge` maps each `Reference` independently** via `VerseId.chapterRange(book.order,
  chapter)` — it never assumes refs share a book, so the two-book portion falls out for free
  (no special case).
- **`ConsecutiveChapterRuns` lifted to `bible/domain` (D-V3-10).** `ProviderUrlBuilder`'s private
  `consecutiveRuns` is deleted and the builder delegates to the shared helper, so external egress
  (Bible Gateway URL) and internal nav share ONE grouping and cannot drift. Lives in
  `bible/domain` (no Android, no UI, no `data/reference` egress) so both data and reader can
  depend on it.
- **Seam bind via a separate abstract `BibleBindsModule`** because the existing `BibleModule` is
  an `object` (provides) and `@Binds` needs an abstract method. Both `@InstallIn(SingletonComponent)`.

## State of the codebase

New code (all under `app/src/main/kotlin/com/jpillion/dailyreadingplanner/bible/`):
- `domain/model/` — `VerseId.kt`, `VerseRange.kt`, `VerseRef.kt`, `VerseText.kt`,
  `ChapterContent.kt` (defines `ChapterContent` + `PortionContent`).
- `domain/` — `BibleTextSource.kt` (the seam), `ReferenceResolver.kt`, `ConsecutiveChapterRuns.kt`,
  `PortionVerseBridge.kt`, `GetChapterUseCase.kt`, `GetPortionTextUseCase.kt`.
- `data/` — `RoomBibleTextSource.kt`.
- Edited: `di/BibleModule.kt` (+`BibleBindsModule`), `data/reference/ProviderUrlBuilder.kt`
  (delegates to `ConsecutiveChapterRuns`, private helper removed).

Tests (under `app/src/test/.../bible/domain/`): `model/VerseIdTest`, `model/VerseRangeTest`,
`model/VerseRefTest`, `ReferenceResolverTest`, `ConsecutiveChapterRunsTest`,
`PortionVerseBridgeTest`, `GetChapterUseCaseTest`, `GetPortionTextUseCaseTest`, plus the
`FakeBibleTextSource` test double (synthetic verses + Psalms superscription; the real asset is
the Sprint-A gate's job, not re-tested here).

Conventions established: use cases are `@Inject constructor` suspend operators returning
`ChapterContent`/`PortionContent`; the seam is the only domain-visible data type; the resolver's
clean-fail contract (`null` on malformed) is release-gated and mutation-pinned.

## Seam / use-case surface for Sprint C (the reader UI consumes this)

- **Inject** `GetChapterUseCase` and `GetPortionTextUseCase` (`@Inject constructor`, no module
  entry needed) into `ReaderViewModel`. Both are `suspend operator fun invoke(...)`.
  - `GetChapterUseCase(book: Book, chapter: Int): ChapterContent` — requires the chapter in
    range (throws otherwise; the picker must only offer valid chapters).
  - `GetPortionTextUseCase(portion: Portion): PortionContent` — for the tap-in flow (Sprint D
    hands a `Portion`).
- **`ChapterContent`** = `(bookNo, bookName, chapter, verses: List<VerseText>)`.
  **`PortionContent`** = `(blocks: List<ChapterContent>)` — one block per chapter in portion
  order; render top-to-bottom.
- **`VerseText`** = `(canonicalId: Long, nativeLabel: String, isTitle: Boolean, markup: String)`:
  - **Key the `LazyColumn` items by `canonicalId`** (D-V3-12).
  - **Render `markup`** via the Sprint-C `VerseRenderer` over the closed vocabulary
    (`bible/data/markup/BibleMarkup`: `<a>` italic ships; `<w>`/`<l/>` recognized, off/conditional);
    TalkBack speaks `MarkupStripper.strip(markup)`, never the tags.
  - **Label the verse with `nativeLabel`** (de-emphasized) — NEVER derive the number from
    `canonicalId` (D-V3-4).
  - **Branch the superscription on `isTitle`** (D-V3-7) — render an unnumbered italic heading with
    `heading()` semantics, never a numbered verse. (KJV: superscriptions arrive as the first
    `VerseText` of the chapter with `verse == 0`, `nativeLabel == ""`, `isTitle == true`.)
- For chapter nav (Prev/Next, picker), use `BookCatalog` order + `Book.chapterCount`. The
  resolver's `resolveChapter(book, chapter)` and `VerseId.chapterRange` are available if needed,
  but the use cases already wrap the seam for the common cases.

## Carryover & next goal

- **Next: V3 Sprint C** (`sprint-00C-reader-ui`) — Priya leads (reader UI), Diego on VM/use-case
  wiring, Riley on the accessibility-gate extension. Verifiable now that the spine lands.
- **Deferred OUT of B (scope protected, unchanged from A):** the `BibleAssetVersion` startup hook
  (off-main delete-before-build + `bible_asset_content_version` DataStore key) → Sprint E device
  pass, where the real re-copy is verifiable. The `exportBookCatalog` Gradle-task wrapping →
  Jordan follow-up (the fixture is generated + drift-gated already).
- **`formatOsis`** is a reserved V3.x stub (App Links) — minimal, not exercised by V3.0 consumers.
- **Owner OQs (OQ-1/2/3/5):** untouched here — they gate D/E, not B/C.

## Open questions & risks / tech debt

- The resolver's alias table is hand-curated; it is exercised by an all-66-canonical-names test
  plus targeted alias pins, but is intentionally a convenience layer for V3.x deep links — V3.0
  reader nav goes through `BookCatalog`/the use cases, not free-text parsing, so a missing alias
  cannot mis-resolve a reading.
- No `INTERNET`, no runtime deps added — invariant held. The seam reads the local asset only.

## Next sprint

`next: sprint-00C-reader-ui`
