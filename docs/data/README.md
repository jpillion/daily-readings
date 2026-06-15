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

---

# KJV text — sources, provenance, reconciliation (V3 Sprint A)

The bundled KJV Bible text is the project's **second core-IP asset** (NFR-V3-B), built to the
same Sprint-1 standard as the plan data: two genuinely-independent public-domain sources, a
reproducible importer, a checked-in binary asset, and an offline release-gating verification
test.

- `app/src/main/assets/bible/bible.db` — read-only SQLite asset (3 tables: `translation`,
  `book`, `verse`). 66 books / 1,189 chapters / 31,102 body verses + 117 verse-0
  superscriptions. ~5.7 MB on disk; **~1.97 MB compressed** in the bundle (well under the
  +6 MB D-V3-20 budget). SHA-256 `ce174e925a15dff0b7802255e201cd3510ae4f86f7a4b3c1830a4e6729da4909`.
- `app/src/test/resources/bible/book_catalog_export.json` — 66-row `book` fixture, GENERATED
  from `BookCatalog.kt` by `tools/export_book_catalog.py` (never hand-authored; D-S9-1 anti-drift).
- `app/src/test/resources/bible/kjv_verse_counts.csv` — 1,189-row second-source verse-count
  witness.
- `app/src/test/resources/bible/kjv_superscriptions.csv` — 117-row second-source superscription
  witness.
- Importer: `tools/build_bible_db.py` (Python stdlib only; `tools/requirements.txt`). Gate:
  `app/src/test/.../bible/data/BibleTextVerificationTest.kt`. CI rebuild: the `data-rebuild` job.

## Sources

| Role | Source | URL | SHA-256 |
|---|---|---|---|
| **Primary** (markup-bearing: transChange added-words + separate Psalm superscriptions) | open-bibles `eng-kjv.osis.xml` (OSIS) | https://raw.githubusercontent.com/seven1m/open-bibles/master/eng-kjv.osis.xml | `eeeae647fc28360ce47f9c0d5cc3b397b7fdd9913fe53dc9f44eb6deee50e253` |
| **Second source** (independent count/text witness) | scrollmapper `bible_databases` `formats/csv/KJV.csv` | https://raw.githubusercontent.com/scrollmapper/bible_databases/master/formats/csv/KJV.csv | `9ba72c78bdac0e60bd37ee453e1ae787ea778297fb2b78b73edc0921a136ab09` |

**Checksum-distinctness (R-V3-3): asserted and recorded.** The two SHA-256s differ, and — more
importantly — the two corpora are of **different lineage**, not the same upstream re-mirrored:

- The primary OSIS header declares it was generated by **Haiola** from a USFX source for the
  **SWORD** project; electronic publisher **eBible.org** (`identifier eng-kjv`).
- The second source is the scrollmapper `bible_databases` "KJV (1769) with Strong's Numbers and
  Morphology" lineage (e-Sword / bible_databases tradition).

**Rejected pairing (the Sprint-1 trap, avoided):** the initially-considered second candidate,
eBible.org's `eng-kjv_usfx.zip`, is the **same upstream** as the primary OSIS (the OSIS *is*
Haiola's conversion of that eBible USFX). It was rejected as a non-independent witness, exactly
as the Sprint-1 pricejh==christadelphia re-mirror was. The structural divergences below confirm
the chosen pair is genuinely independent.

**Structural divergences proving independence:**
- The primary stores Psalm superscriptions as **separate** elements (→ verse 0); the second
  stores them inline / not as distinct rows.
- Small-caps divine name: primary preserves **"LORD"**; the second flattens to **"Lord"**
  (5,692 verses differ on this alone).
- Proper-name hyphenation: primary uses ASCII `-` (Beer-sheba); the second uses en-dash `–` or
  closed forms (Beer–sheba / Hazarmaveth).
- Editorial epistle subscriptions ("Written to the Romans from Corinthus…", 14 of them): the
  second source carries them; the primary correctly omits them as non-canonical. The shipped
  asset omits them (they are not scripture verses).

## OQ-4 finding (poetry / red-letter availability — gates P1 only)

The **primary** corpus carries, cleanly:
- **P0 (required, SHIPPED):** `transChange type="added"` added-words (24,070 occurrences) → `<a>`;
  Psalm superscriptions (117 chapter-level titles).
- **P1 (available, NOT shipped in V3.0):** poetry `<l>`/`<lg>` (2,314) and words-of-Christ
  `<q who="Jesus">` (2,021). These are reserved in the `BibleMarkup` contract (`<l/>`, `<w>`) and
  recognized by the Sprint-C renderer, but the V3.0 P0 importer emits **only `<a>`** (the OSIS
  itself warns its `<q>` boundaries are imprecise; poetry line-breaking is deferred). Enabling
  either later is an artifact-only change behind the same gate.

## Superscription reconciliation

- The shipped superscription set is the **117 chapter-level Psalm titles** — the standard KJV set.
  Pinned absences (no title): **Pss 1, 2, 10, 33** (spec's named exclusions) plus the rest of the
  standard untitled list (43, 71, 91, 93–97, 99, 104–107, 111–118, 135–137, 146–150).
- **Habakkuk 3 — deliberate finding (overrides the spec's "Hab 3 has a verse-0 title"
  expectation):** BOTH independent corpora encode "A prayer of Habakkuk the prophet upon
  Shigionoth" as **verse 1** of Hab 3 (Hab 3 = 19 verses), not as a verse-0 superscription.
  Forcing it to verse 0 would contradict the second-source verse-count witness. The asset follows
  both real corpora; Hab 3 carries no verse-0 title. Recorded as a versification finding, not a
  defect.

## Text reconciliation log (primary defects corrected against the independent witness)

Full-text diff of `strip(primary markup)` vs the second source across all 31,102 verses, after
normalizing known cross-edition typography (LORD/Lord casing, en-dash vs hyphen, open/closed
compounds, punctuation), left **2 residual word-level differences** — both **legitimate
inter-edition KJV variants**, deliberately NOT overridden (the primary's reading is a valid KJV
edition reading):

| Verse | Primary (kept) | Second | Note |
|---|---|---|---|
| 1 Sam 7:1 | "**brought** up the ark" | "fetched up" | both attested KJV editions |
| 2 Kgs 2:20 | "they **bring** it" | "they brought it" | edition variant |

Four genuine **primary-corpus defects** were corrected via documented `TEXT_OVERRIDES` in
`tools/build_bible_db.py` (Sprint-1 OVERRIDES discipline — re-running the importer reproduces the
correction), each where the independent source AND the known authentic KJV reading agree against
the primary:

| # | Verse | Primary defect | Corrected to | Evidence |
|---|---|---|---|---|
| 1 | 1 Chr 11:2 | DOUBLES "and thou shalt be ruler over my people Israel" | single clause | second source + KJV |
| 2 | Ezek 17:24 | DOUBLES "I the LORD have brought down the high tree … to flourish" | single clause | second source + KJV |
| 3 | Lev 17:8 | "burnt offering **of** sacrifice" | "burnt offering **or** sacrifice" | second source + KJV |
| 4 | Isa 47:11 | "put **if** off" (typo) | "put **it** off" | second source + KJV |
| 5 | Matt 5:30 | "cut **if** off" (typo) | "cut **it** off" | second source + KJV |

(Genuine threefold/twofold KJV repetitions — Jer 7:4 "The temple of the LORD" ×3, Isa 21:11
"Watchman, what of the night?" ×2, Exod 27:19 "and all the pins" — were verified against BOTH
sources and left intact; they are authentic text, not defects.)

## Determinism & gate

- The importer is byte-deterministic (fixed `verse_id` row order, pinned PRAGMAs, no timestamps,
  `VACUUM`). Re-running from the pinned sources reproduces `bible.db` **byte-identically** — the
  `data-rebuild` CI job asserts a `cmp` byte-diff of zero, blocking any hand-edited binary.
- `BibleTextVerificationTest` (offline, `sqlite-jdbc`, reads the exact shipped asset) is the
  release gate (FR-V3-12, M-V3-1): 18 assertions across structural invariants, `book`
  reconciliation, second-source verse-count equality, checksum-distinctness, superscriptions
  (both directions + Ps 3/51 text), the markup strip-invariant + added-word floor + closed
  vocabulary, and famous-verse exact-text pins. Mutation-verified (dropped superscription,
  stripped `<a>` floor, corrupted famous verse, dropped verse — each turns it red).
- `ASSET_CONTENT_VERSION` starting value: **1** (`BibleAssetVersion`).
