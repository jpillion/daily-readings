# Plan data — sources, normalization & reconciliation

This documents the provenance of the V1 data artifacts (moved into `:app` in Sprint 2):

- `app/src/main/assets/reading_plan.json` — canonical 365-day Bible Companion plan (ESpec §5.1 schema).
- `app/src/test/resources/reading_plan_verify.json` — independent second-source comparison fixture (same schema).
- `app/src/test/resources/book_catalog.csv` — 66-book catalog: `order,canonicalName,chapterCount,blbAbbrev`.

Extraction scripts live in `/tools` (`extract_primary.py`, `extract_antipas.py`). The
verification gate lives in `app/src/test/.../data/plan/ReadingPlanVerificationTest.kt` and runs
under `./gradlew testDebugUnitTest` (re-homed from the retired standalone `/verification` module in Sprint 2).

## Sources

| Role | Source | URL | MD5 |
|---|---|---|---|
| Primary (canonical on conflict) | christadelphia.org Bible Companion chart (PDF) | https://christadelphia.org/chart.pdf (linked from https://christadelphia.org/readplan.php) | `d1e0121a1d5cc49696e03ec507b411c4` |
| Second source (independent) | antipas.org "The Bible Companion" booklet (PDF) | https://antipas.org/library/Robert%20Roberts/Booklets/The%20Bible%20Companion.pdf | (MD5 recorded at extraction: run `md5` on a fresh fetch; 60,865 bytes, PDF 1.5, 2 pages) |
| Tie-breaker (third witness, used only on conflict; usage logged) | dailyreadings.org.uk / general knowledge of the Bible Companion | https://dailyreadings.org.uk/ | — |

**Source substitution (2026-06-10):** the execution plan named pricejh.com `roberts.pdf` as the
second source. It is **byte-identical** (same MD5 `d1e0121a...`) to christadelphia.org's
`chart.pdf` — the same document mirrored, so it cannot serve as an independent witness.
The antipas.org booklet (different typesetting, different notation, different provenance) is the
second source instead. The two-source rule (risk R9) is satisfied: different documents, different
layouts, independently written parsers.

Source PDFs are **not** checked in (third-party documents); URLs + checksums above allow
re-fetching. `tools/` scripts download and parse them.

## Source layouts

**Primary (chart.pdf):** 2 pages, 6 bands of two side-by-side month columns
(Jan|Feb, Mar|Apr, May|Jun, Jul|Aug, Sep|Oct, Nov|Dec). One line per day:
`Mon D ... Stream1 ... Stream2 ... Stream3` with dot leaders. Full book names.
Chapter spans as hyphen ranges ("Genesis 1-2").

**Second source (antipas booklet PDF):** 2 pages, print-imposed booklet. Month bands of FOUR
month columns (page 2: JAN FEB MAY JUNE / MARCH APRIL JULY AUGUST; page 1: SEPT OCT NOV DEC,
sharing the page with cover/prose text). Each month column = day number + 3 stream sub-columns.
Abbreviated book names ("Gen.", "Psa.", "1 Chron.", "Phil.", "2Cor"), **ditto continuation**
(dots only = same book as the row above), chapters as comma lists ("1,2", "5,6,7") or hyphen
ranges ("48-50", "1-3").

## Normalization rules (applied identically to both extractions)

1. **Feb 29 dropped.** Primary contains a Feb 29 row duplicating Feb 28 (Leviticus 3-4 /
   Psalms 104 / 1 Corinthians 12-13); per decision D1 the plan has **no** Feb 29 entry, so the
   row is dropped at extraction. The antipas booklet has no Feb 29 row at all. 365 days total.
2. **Psalm 119 verse parts → chapter 119.** Both sources split Psalm 119 over Mar 9-12
   ("Psalms 119:1-40 / :41-80 / :81-128 / :129-176"; antipas "119,v.1-40" etc.). Schema v1 refs
   are `{book, chapter}` (no verse support), so each of the four days carries
   `{"book":"Psalms","chapter":119}`. Verse-range fidelity is a deferred candidate enhancement
   (additive optional field, post-V1 data sprint).
3. **Chapter-span expansion = min..max contiguous range.** "Genesis 1-2" → [1,2];
   antipas "5,6,7" → [5,6,7]; antipas comma pairs are range endpoints where the primary uses
   hyphens ("145,147" = Psalms 145-147). Every Bible Companion portion is a contiguous span of
   one book, so min..max expansion is exact (verified by the full-coverage invariant in the
   gate test: every book's chapters 1..chapterCount are read exactly).
4. **"2 John - 3 John" days (Jun 19, Dec 19).** Primary prints "2 John -3JO" (typesetting
   artifact); antipas prints "2,3 John". Both normalize to two refs:
   `{"book":"2 John","chapter":1}, {"book":"3 John","chapter":1}`.
5. **Single-chapter books.** "Jude 1" / "Jude" → {Jude, 1}; "Philemon 1" / "Philemon" → {Philemon, 1}.
6. **Primary spelling fixes:** "Zepheniah" → "Zephaniah", "Haggia" → "Haggai" (chart typos).
7. **Canonical book names** per `book_catalog.csv` (e.g. "Psalms", "Song of Solomon", "1 John").
   Antipas abbreviations are mapped via an explicit table in `tools/extract_antipas.py`
   ("Phil." = Philippians; "Philemon" always spelled out — no ambiguity).

## Reconciliation log

The two independent extractions disagreed on **7 days** (out of 365). Every conflict was
resolved on evidence, not source precedence. Corrections are applied as documented
`OVERRIDES` in the extraction scripts so re-running them reproduces the reconciled data.

| # | Day | Primary (chart.pdf) said | Second source (antipas) said | Winner | Rationale |
|---|---|---|---|---|---|
| 1 | Jan 12 | Psalms 26-29 | Psalms 26-28 | **antipas** | Third witness dailyreadings.org.uk: Jan 12 = "Psa 26-28". |
| 2 | Jan 13 | Psalms 30 | Psalms 29-30 | **antipas** | Third witness dailyreadings.org.uk: Jan 13 = "Psa 29-30". |
| 3 | Mar 23 | Proverbs 1 | Proverbs 11 | **primary** | Booklet typo: its own Mar 24 = Prov 2, and Prov 1 would otherwise never be read (coverage invariant). |
| 4 | Apr 26 | Deuteronomy 12-13 | Deuteronomy 12 | **antipas** | Both sources put Deut 13 in Apr 27; chart double-reads ch 13, violating read-once. |
| 5 | Sep 30 | 1 Chronicles 13-14 | 1 Chronicles 13 | **primary** | Booklet omits 1 Chron 14 entirely (coverage invariant). |
| 6 | Dec 18 | Haggai 1 ("Haggia 1") | Haggai 1-2 | **antipas** | Chart omits Haggai 2 entirely (coverage invariant). |
| 7 | Dec 28 | Zechariah 13 | Zechariah 13-14 | **antipas** | Chart omits Zechariah 14 entirely (coverage invariant). |

Additional primary-source spelling typos normalized (not conflicts): "Zepheniah" →
Zephaniah, "Haggia" → Haggai, "Obadia" → Obadiah, "Habbakuk" → Habakkuk. The chart also
prints a Feb 29 row duplicating Feb 28; dropped per decision D1.

**Third-witness usage (logged per policy):** dailyreadings.org.uk was consulted once, for
conflicts 1-2 (Jan 12/13), via its January month view. All other conflicts were decided by
internal evidence (full-coverage / read-once invariants + the other source's own adjacent
days). No reading was taken from memory.

**Precedence note:** the "primary is canonical on conflict" rule was never invoked — every
conflict had decisive evidence. The coverage invariant in the verification gate (every book's
chapters 1..chapterCount all read; OT once / NT twice by design) caught 5 of the 7 conflicts
and all 4 spelling typos.
