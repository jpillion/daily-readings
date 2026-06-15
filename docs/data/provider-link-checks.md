# Provider link-check record (Sprint 13, 2026-06-11)

The spec §3 verification gate (docs/features/bible-app-links.md) for the two NEW providers,
run the Sprint 1 way: a one-time live HTTP pass at integration time, recorded here; the
committed test suite stays offline (UsfmCodeCatalogTest + ProviderUrlBuilderTest pin every
token and URL shape forever after). Blue Letter Bible's 66-book pass is the Sprint 1 record
(README.md in this directory) + the Sprint 8 on-device G-LINKS sign-off — not repeated.

Method: real HTTP GETs from a desktop curl (and the WebFetch tool where bible.com's bot
challenge required it), redirects followed. **A bare 200 is NOT a pass** — both sites can
200 on garbage:

- **Bible Gateway:** PASS = response contains at least one `class="passage-text"` div AND
  the `<title>` begins with the exact queried reference + " KJV". Negative control
  ("Asdfbook 1") correctly FAILS (0 passage divs).
- **YouVersion / Bible.com:** PASS = response contains the `ChapterContent` markers AND the
  canonical book name. Negative control (book code `XXX`) correctly fails.

## Bible Gateway — `https://www.biblegateway.com/passage/?search={ref}&version=KJV`

**RESULT: PASS, 134/134** — chapter 1 + last chapter of all 66 books, plus the two
portion forms the app emits:

- Range: `search=Genesis+1-2` → one passage div, title "Genesis 1-2 KJV" — **PASS**.
- Two-book portion (Jun 19 / Dec 19): `search=2+John+1%2C3+John+1` → **two** passage divs,
  title "2 John 1,3 John 1 KJV" — **PASS** (this is what makes the provider
  `multiRefCapable`).

KJV check (gate item 3): the version is pinned in the query string; every result title ends
" KJV" and renders KJV text with no user-side setup. Awkward books (gate item 4) are inside
the full 66: Psalms 1/150, Song of Solomon 1/8, Philemon 1, Philippians 1/4, 1–3 John,
Jude 1, Revelation 1/22 — all PASS.

Per-book results (chapter pair = first/last):

| Book | Chapters checked | Result |
|---|---|---|
| Genesis | 1/50 | PASS |
| Exodus | 1/40 | PASS |
| Leviticus | 1/27 | PASS |
| Numbers | 1/36 | PASS |
| Deuteronomy | 1/34 | PASS |
| Joshua | 1/24 | PASS |
| Judges | 1/21 | PASS |
| Ruth | 1/4 | PASS |
| 1 Samuel | 1/31 | PASS |
| 2 Samuel | 1/24 | PASS |
| 1 Kings | 1/22 | PASS |
| 2 Kings | 1/25 | PASS |
| 1 Chronicles | 1/29 | PASS |
| 2 Chronicles | 1/36 | PASS |
| Ezra | 1/10 | PASS |
| Nehemiah | 1/13 | PASS |
| Esther | 1/10 | PASS |
| Job | 1/42 | PASS |
| Psalms | 1/150 | PASS |
| Proverbs | 1/31 | PASS |
| Ecclesiastes | 1/12 | PASS |
| Song of Solomon | 1/8 | PASS |
| Isaiah | 1/66 | PASS |
| Jeremiah | 1/52 | PASS |
| Lamentations | 1/5 | PASS |
| Ezekiel | 1/48 | PASS |
| Daniel | 1/12 | PASS |
| Hosea | 1/14 | PASS |
| Joel | 1/3 | PASS |
| Amos | 1/9 | PASS |
| Obadiah | 1/1 | PASS |
| Jonah | 1/4 | PASS |
| Micah | 1/7 | PASS |
| Nahum | 1/3 | PASS |
| Habakkuk | 1/3 | PASS |
| Zephaniah | 1/3 | PASS |
| Haggai | 1/2 | PASS |
| Zechariah | 1/14 | PASS |
| Malachi | 1/4 | PASS |
| Matthew | 1/28 | PASS |
| Mark | 1/16 | PASS |
| Luke | 1/24 | PASS |
| John | 1/21 | PASS |
| Acts | 1/28 | PASS |
| Romans | 1/16 | PASS |
| 1 Corinthians | 1/16 | PASS |
| 2 Corinthians | 1/13 | PASS |
| Galatians | 1/6 | PASS |
| Ephesians | 1/6 | PASS |
| Philippians | 1/4 | PASS |
| Colossians | 1/4 | PASS |
| 1 Thessalonians | 1/5 | PASS |
| 2 Thessalonians | 1/3 | PASS |
| 1 Timothy | 1/6 | PASS |
| 2 Timothy | 1/4 | PASS |
| Titus | 1/3 | PASS |
| Philemon | 1/1 | PASS |
| Hebrews | 1/13 | PASS |
| James | 1/5 | PASS |
| 1 Peter | 1/5 | PASS |
| 2 Peter | 1/3 | PASS |
| 1 John | 1/5 | PASS |
| 2 John | 1/1 | PASS |
| 3 John | 1/1 | PASS |
| Jude | 1/1 | PASS |
| Revelation | 1/22 | PASS |
## YouVersion / Bible.com — `https://www.bible.com/bible/1/{USFM}.{chapter}.KJV`

(Version id 1 = KJV on bible.com; book tokens are the USFM codes, incl. the quirks a naive
derivation would get wrong: EZK, JOL, NAM, MRK, PHP, JUD, 1JN/2JN/3JN.)

**RESULT: PASS, 132/132** — chapter 1 + last chapter of all 66 books, real HTTP GETs
(Python urllib; bible.com's bot challenge fingerprints curl's TLS stack, so the curl pass
was re-run with urllib — zero challenges, every page content-asserted).

KJV check (gate item 3): version id 1 in the URL path is KJV; spot-read pages (Genesis 1,
Psalms 119, Philippians 4, Jude 1 via WebFetch) rendered KJV text with no user-side setup.
Awkward books (gate item 4) all inside the full 66 — note `PHP` (Philippians) vs `PHM`
(Philemon), `EZK`, `JOL`, `NAM`, `MRK`, `JUD`, `1JN/2JN/3JN`: exactly the tokens a naive
derivation from the BLB column would get wrong, which is why this column is hand-pinned
in UsfmCodeCatalogTest rather than derived.

Negative-control note: a bogus token (`XXX`) still serves a page with chapter-content
markup, so the **canonical-book-name assertion is the operative criterion** — it failed the
control and passed all 132 real checks (each page contained its own book's canonical name).

| Book | USFM | Chapters checked | Result |
|---|---|---|---|
| Genesis | `GEN` | 1/50 | PASS |
| Exodus | `EXO` | 1/40 | PASS |
| Leviticus | `LEV` | 1/27 | PASS |
| Numbers | `NUM` | 1/36 | PASS |
| Deuteronomy | `DEU` | 1/34 | PASS |
| Joshua | `JOS` | 1/24 | PASS |
| Judges | `JDG` | 1/21 | PASS |
| Ruth | `RUT` | 1/4 | PASS |
| 1 Samuel | `1SA` | 1/31 | PASS |
| 2 Samuel | `2SA` | 1/24 | PASS |
| 1 Kings | `1KI` | 1/22 | PASS |
| 2 Kings | `2KI` | 1/25 | PASS |
| 1 Chronicles | `1CH` | 1/29 | PASS |
| 2 Chronicles | `2CH` | 1/36 | PASS |
| Ezra | `EZR` | 1/10 | PASS |
| Nehemiah | `NEH` | 1/13 | PASS |
| Esther | `EST` | 1/10 | PASS |
| Job | `JOB` | 1/42 | PASS |
| Psalms | `PSA` | 1/150 | PASS |
| Proverbs | `PRO` | 1/31 | PASS |
| Ecclesiastes | `ECC` | 1/12 | PASS |
| Song of Solomon | `SNG` | 1/8 | PASS |
| Isaiah | `ISA` | 1/66 | PASS |
| Jeremiah | `JER` | 1/52 | PASS |
| Lamentations | `LAM` | 1/5 | PASS |
| Ezekiel | `EZK` | 1/48 | PASS |
| Daniel | `DAN` | 1/12 | PASS |
| Hosea | `HOS` | 1/14 | PASS |
| Joel | `JOL` | 1/3 | PASS |
| Amos | `AMO` | 1/9 | PASS |
| Obadiah | `OBA` | 1/1 | PASS |
| Jonah | `JON` | 1/4 | PASS |
| Micah | `MIC` | 1/7 | PASS |
| Nahum | `NAM` | 1/3 | PASS |
| Habakkuk | `HAB` | 1/3 | PASS |
| Zephaniah | `ZEP` | 1/3 | PASS |
| Haggai | `HAG` | 1/2 | PASS |
| Zechariah | `ZEC` | 1/14 | PASS |
| Malachi | `MAL` | 1/4 | PASS |
| Matthew | `MAT` | 1/28 | PASS |
| Mark | `MRK` | 1/16 | PASS |
| Luke | `LUK` | 1/24 | PASS |
| John | `JHN` | 1/21 | PASS |
| Acts | `ACT` | 1/28 | PASS |
| Romans | `ROM` | 1/16 | PASS |
| 1 Corinthians | `1CO` | 1/16 | PASS |
| 2 Corinthians | `2CO` | 1/13 | PASS |
| Galatians | `GAL` | 1/6 | PASS |
| Ephesians | `EPH` | 1/6 | PASS |
| Philippians | `PHP` | 1/4 | PASS |
| Colossians | `COL` | 1/4 | PASS |
| 1 Thessalonians | `1TH` | 1/5 | PASS |
| 2 Thessalonians | `2TH` | 1/3 | PASS |
| 1 Timothy | `1TI` | 1/6 | PASS |
| 2 Timothy | `2TI` | 1/4 | PASS |
| Titus | `TIT` | 1/3 | PASS |
| Philemon | `PHM` | 1/1 | PASS |
| Hebrews | `HEB` | 1/13 | PASS |
| James | `JAS` | 1/5 | PASS |
| 1 Peter | `1PE` | 1/5 | PASS |
| 2 Peter | `2PE` | 1/3 | PASS |
| 1 John | `1JN` | 1/5 | PASS |
| 2 John | `2JN` | 1/1 | PASS |
| 3 John | `3JN` | 1/1 | PASS |
| Jude | `JUD` | 1/1 | PASS |
| Revelation | `REV` | 1/22 | PASS |

## Multi-chapter / multi-book portion semantics shipped (D-S13-3)

| Provider | "Genesis 1–2" portion opens | Jun 19 / Dec 19 portion opens |
|---|---|---|
| Blue Letter Bible | Genesis 1 (first chapter — unchanged) | 2 John 1 (first book — unchanged) |
| YouVersion | GEN.1 (single-chapter provider) | 2JN.1 (first book) |
| Bible Gateway | Genesis 1-2 (full range, one URL) | 2 John 1,3 John 1 (both books, one URL) |


---

## Verse-level deep links (Sprint H / BACKLOG #5, 2026-06-15)

The in-app reader's per-verse tap-out (`ProviderUrlBuilder.buildVerse`, D-H-5). Verse forms,
live-checked the Sprint-13 way (real fetches, KJV verse text + reference confirmed; the committed
suite stays OFFLINE — `ProviderUrlBuilderTest`/`OpenVerseUseCaseTest` pin every shape forever).
A superscription tap (verse 0) clamps to verse 1.

| Provider | Verse form | Sample checked | Result |
|---|---|---|---|
| Blue Letter Bible | `/kjv/{blbAbbrev}/{ch}/{verse}/` | `psa/23/1` (anchored "The LORD is my shepherd"), `phm/1/25` (Philemon benediction) | PASS |
| Bible Gateway | `?search={Book} {ch}:{verse}&version=KJV` | `Philemon 1:25`, `3 John 1:14` (title "… 1:14 KJV", correct verse) | PASS |
| YouVersion / Bible.com | `/bible/1/{usfmCode}.{ch}.{verse}.KJV` | `2JN.1.1.KJV` (2 John 1:1), `PSA.119.176.KJV` (Ps 119:176) | PASS |
| MySword | `https://mysword.info/b?r={order}.{ch}.{verse}` | numeric `19.23.1` — derived from the pinned catalog order (D-S15-1) | App-only; owner on-device pass (as S15) |

Awkward books exercised: Psalms (incl. Ps 119:176, the longest chapter's last verse), Philemon
(one-chapter book), 2 John / 3 John (one-chapter Johannine epistles). Negative controls not
re-run for verse level — the chapter-level gates above already proved each provider rejects
garbage; the verse segment is an additive path coordinate on a verified template.
