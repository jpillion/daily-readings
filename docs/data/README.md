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
2. **Psalm 119 verse parts → verse windows (schema v2, RESOLVED — was deferred).** Both sources
   split Psalm 119 over Mar 9-12 ("Psalms 119:1-40 / :41-80 / :81-128 / :129-176"; antipas
   "119,v.1-40" etc.). As of **schema v2** each of the four days carries a chapter-relative verse
   window — `{"book":"Psalms","chapter":119,"verseStart":1,"verseEnd":40}` and so on — instead of a
   bare `{"book":"Psalms","chapter":119}`. The extraction scripts STOP dropping the verse suffix for
   these four days and emit `verseStart`/`verseEnd`; every other reading is unchanged (no verse
   fields = whole chapter). See the Psalm-119 reconciliation entry below for provenance. This was
   the "deferred candidate enhancement" the V1 schema-v1 note recorded; it is now resolved.
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

## Psalm 119 verse division (schema v2, 2026-06-15) — the sub-chapter verse ranges

Psalm 119 (176 verses, the longest chapter in scripture) is read over **four days — Mar 9, 10, 11,
12 (stream 2)**. Schema v1 flattened all four to bare chapter 119 (normalization rule §2 above, then
deferred). Schema **v2** encodes the actual verse division as trusted IP under the same gate.

| Day | Stream | Verse window |
|---|---|---|
| Mar 9  | 2 | Psalms 119:1–40   |
| Mar 10 | 2 | Psalms 119:41–80  |
| Mar 11 | 2 | Psalms 119:81–128 |
| Mar 12 | 2 | Psalms 119:129–176 |

**Provenance & second-source agreement.** The four boundaries are the **owner-confirmed** division
from the Bible Companion booklet (the authority on the plan). Both established source PDFs print the
same division (primary chart.pdf "Psalms 119:1-40 / :41-80 / :81-128 / :129-176"; antipas booklet
"119,v.1-40" etc., recorded in normalization rule §2 since Sprint 1). The two sources **agree
day-by-day** on the ranges — the second-source equality gate (`Psalm 119 windows match the
independent second source`) pins canonical == verify fixture for all four days. **No conflict to
reconcile:** unlike the 7 chapter-level Sprint-1 conflicts, the two witnesses and the owner all
concur. The boundaries are multiples of 8 except where the psalm's structure dictates (the windows
are 40/40/48/48 verses = 5/5/6/6 of the 22 eight-verse acrostic stanzas), consistent with — but
sourced independently of — the stanza hypothesis flagged in the spec.

**Coverage invariant (the load-bearing proof).** The four windows **tile 1..176 exactly** —
contiguous, no gap, no overlap, every verse read exactly once: `[1..40][41..80][81..128][129..176]`,
lengths summing to 176. The gate's `the four Psalm 119 days tile verses 1 to 176 exactly` assertion
enforces this (the verse-level analogue of the chapter full-coverage / read-once invariant). The
`verseEnd <= chapterVerseCount` bound is checked against the committed KJV verse-count witness
(`bible/kjv_verse_counts.csv`, which records Psalms 119 = 176) — D-SCHEMA-3.

**"Only Psalm 119" audit (A2).** A re-scan of the plan confirms Psalm 119 is the **sole** in-chapter
verse split: the gate's `only the four Psalm 119 days carry verse windows` assertion pins that
exactly four refs in the whole year carry verse fields, all `{Psalms, 119}` on Mar 9-12 stream 2. Any
future windowed ref elsewhere fails the gate (audit pinned into the release gate, not just asserted).

**Reproducibility.** The asset stays script-generated: `tools/extract_primary.py` and
`tools/extract_antipas.py` now emit `verseStart`/`verseEnd` for the four Psalm-119 days (they
previously detected and discarded the verse suffix). Re-running the scripts against the pinned source
PDFs reproduces the windowed refs. `schemaVersion` is **2** in both `reading_plan.json` and
`reading_plan_verify.json`.

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

## AR-1 — UK KJV licensing (ACCEPTED RISK, recorded V3 Sprint E)

The King James (Authorized) Version text shipped in `bible.db` is **public domain worldwide
except the United Kingdom**, where it remains under perpetual **Crown copyright**, administered
by **Cambridge University Press / the King's Printer**.

**Owner decision (PRD-v3 §11, AR-1): ACCEPT the risk.** Do **not** geo-restrict the app and do
**not** alter the text. Rationale recorded for the durable provenance record:

- The risk is near-theoretical for a free, non-commercial app: Cambridge grants broad free use,
  and every free Bible app already serves the KJV to UK users without incident.
- The text is unaltered, faithfully presented (M-V3-2), and not monetized.

**Optional courtesy action (OQ-5, owner-deferred, NOT blocking ship):** file a free-use
permission request with Cambridge University Press for certainty. The owner may do this at any
time; it is not a release gate. If filed, record the outcome here.

This accepted risk is recorded **before ship**, as PRD-v3 §11 requires.

---

# M'Cheyne plan — sources, provenance & reconciliation (Alternate-Schedules Sprint A)

The second curated reading plan (after the Bible Companion). The classic **4-stream, date-anchored,
365-day** M'Cheyne calendar (OQ-MC resolved), bundled at `app/src/main/assets/plans/mcheyne/plan.json`
(schemaVersion 3). Built reproducibly by `tools/build_mcheyne_plan.py`; gate-verified by
`McheynePlanVerificationTest` (10 tests); byte-diff guarded by the `mcheyne-rebuild` CI job. Sourcing
feasibility study: [mcheyne-sourcing.md](mcheyne-sourcing.md).

## Streams (4 — classic M'Cheyne: Family ×2, Secret ×2)

| # | Title | Content |
|---|---|---|
| 1 | Family — Old Testament | Genesis → … (OT history, read once) |
| 2 | Family — Gospels | Matthew → … (Gospels/NT) |
| 3 | Secret — Psalms & Prophets | Ezra/Psalms → … (Psalms, Wisdom, Prophets) |
| 4 | Secret — Epistles | Acts → … (Epistles/NT) |

The "Family" vs "Secret" naming is M'Cheyne's own (Carson/TGC confirm: "secret" from Matthew 6:6,
the two columns for family vs private devotion). Awaiting owner tone sign-off on the exact labels.

## Two genuinely-independent lineages (the gate is satisfied)

| Role | Source | URL | SHA-256 |
|---|---|---|---|
| **Canonical** (verse-faithful) — the asset is built from this | edginet `year_classic_single_a4.pdf` (Ben Edgington's reformat of David Haslam's digitization of the original 1842 calendar) | http://www.edginet.org/mcheyne/year_classic_single_a4.pdf | `2a45dd9b7d9bd3ae213309ab44ec8c6599b382b7a11fedaf4ced7c82613406cb` |
| **Independent witness** — the gate's second source | The Gospel Coalition / D.A. Carson `RtB_Reading-Plan_2020.pdf` ("For the Love of God" companion) | https://media.thegospelcoalition.org/wp-content/uploads/2020/01/09151141/RtB_Reading-Plan_2020.pdf | `a3170fe52cbb5a54f9183d659c797bf9cfb3c9aca41a3d52286650b8640ec563` |

**Checksum-distinctness + lineage-independence (R-ALT-3): asserted.** The two SHA-256s differ, and —
more importantly — the lineages are genuinely independent: Edgington/Haslam descend from the original
1842 M'Cheyne calendar; Carson/TGC is a separate editorial line (Carson's "For the Love of God"). The
**re-mirror trap was explicitly checked**: `mcheyne.info/calendar.pdf` (Haslam) is the SAME lineage as
edginet (Edgington credits Haslam) and is therefore NOT used as the second witness — Carson is. The
bibleplan.org `paulyoder/plan.js` chapter-skeleton was **rejected** as a source for the asset because
its verse-windowed days are corrupt (Feb 28 `Exodus 11-12:20` and Mar 1 `Exodus 12:21-50` vs the
verse-faithful `Ex 11:1-12:21` / `Ex 12:22-51` — a documented off-by-one), exactly the
chapter-collapse trap the sourcing doc warned of.

The asset and the second-source fixture are parsed by **two independently-written parsers**
(`tools/build_mcheyne_plan.py` on the edginet fixed-column A4 layout; `tools/extract_mcheyne_second.py`
on the Carson/TGC single-day-per-line layout, different book-abbreviation vocabularies — `Gn`/`Mt`/`Eze`
vs `Gen.`/`Matt.`/`Ezek.`).

## Verse-window fidelity (the ~24 windowed days)

The asset carries **38 verse-windowed refs** (the ~24 distinct windowed days, several appearing twice
because Psalms/NT are read twice): Psalm 119 split into 7 windows tiling 1..176 (read twice — Family
Jun 22–28, Secret Oct 25–31), Psalm 78 split 1-37 / 38-72 (read twice), Luke 1 split 1-38 / 39-80
(read twice), and ~13 chapter-spanning ranges. **Cross-chapter ranges** are modeled faithfully as
multi-ref portions: e.g. `Ex 11:1-12:21` → whole `Ex 11` + `Ex 12:1-21`; `Is 9:8-10:4` → `Is 9:8-21` +
`Is 10:1-4`; `Dt 27:1-28:19` → whole `Dt 27` + `Dt 28:1-19`; `Zech 12:1-13:1` → whole `Zech 12` +
`Zech 13:1`. A window covering a whole chapter (verses 1..count) is collapsed to a plain whole-chapter
ref (no verse fields), so the "windowed only where intended" audit holds. The **non-adjacent
double-chapter slot** Aug 8 = `Jer 36,45` is one comma-joined portion (two chapters of the same book).

## Coverage invariant (verse-aware)

The gate proves, at the **verse** level: every OT (non-Psalms) verse read **exactly once**; every
Psalms and NT verse read **exactly twice**; every verse of all 66 books covered — so the verse windows
tile each split chapter with no gap or overlap. The named anchor: **Matthew 1 is read in Family on
Jan 1 AND in Secret on Jun 21** (the "NT twice" witness). The full asset vs the independent Carson/TGC
fixture agree on **every one of the 365 days** (THE GATE).

## Reconciliation log (M'Cheyne)

Both documented entries are `pdftotext` column-extraction artifacts in the edginet A4 calendar — NOT
plan disagreements. In each case the reading **content** is confirmed identical to the independent
Carson/TGC witness, and the fix is applied in `tools/build_mcheyne_plan.py` (`reconcile()`) so a
re-run reproduces the corrected data.

| # | Day | edginet extraction artifact | Corrected to | Evidence |
|---|---|---|---|---|
| 1 | Aug 29 | The August-column "29" lost its leading "2" at the fixed-width column boundary, extracting as `9 1Sa 21-22; …` — a day-number colliding with the real Aug 9 (Ruth 2), so the row was dropped (364 days). | Re-keyed to Aug 29 = `1Sa 21-22; 1Co 3; Eze 1; Ps 37`. | Carson/TGC Aug 29 = `1 Sam. 21-22; 1 Cor. 3; Ezek. 1; Ps. 37`; fits the 28→30→31 sequence. |
| 2 | Jun 28 | A trailing column-bleed digit `2` (the adjacent August column's day-2 number) appended to the 4th slot: `… Is 60; Mt 8 2`. | `… Is 60; Mt 8`. | Carson/TGC Jun 28 4th reading = `Matt. 8`. |

## Determinism & gate

- `python3 tools/build_mcheyne_plan.py <edginet.pdf>` re-derives `mcheyne/plan.json` **byte-identically**
  (fixed day order, `json.dump(indent=1)` + trailing newline, Python stdlib + `pdftotext` only).
- `python3 tools/extract_mcheyne_second.py <tgc.pdf>` re-derives the committed second-source fixture
  `app/src/test/resources/plans/mcheyne/plan_verify.json` byte-identically.
- `McheynePlanVerificationTest` (offline, `testDebugUnitTest`) is the release gate: structural
  (schema 3 / 365 days / Feb 29 absent / streams 1..4 / refs resolve / windows well-formed vs
  `bible/kjv_verse_counts.csv`), second-source day-by-day equality, the verse-aware coverage invariant,
  the Ps-119 tiling (both occurrences), and the spanning-range fidelity pins. Mutation-verified:
  a dropped window, a 4th-stream day reduced to 3, and a coverage double-count each red the gate.
- The `mcheyne-rebuild` CI job re-fetches the two pinned sources (SHA-verified), re-derives both files,
  and asserts a byte-diff of zero — a hand-edited M'Cheyne asset can never reach a release.

---

# Chronological plan — sourcing investigation (Alternate-Schedules Sprint E) — **NO-GO (do not ship)**

The third candidate curated plan was a **named, date-anchored, single-stream one-year chronological
plan** — the proof of the N=1 end of the multi-plan generalization. Per the project's two-independent-
source discipline (FR-ALT-3, D-ALT-21) a plan ships ONLY if a *specific, named* published ordering has
a **genuinely independent second witness that agrees day-by-day**. Sprint E's sourcing investigation
found that **no chronological ordering meets this bar**, so chronological is declared **NO-GO and is
NOT shipped**. The multi-plan feature ships complete and valuable with **two** gate-verified plans
(Bible Companion + M'Cheyne). A do-not-ship with a clear, recorded reason is the correct outcome of the
honesty gate (D-ALT-21), not a failure.

## Why NO-GO — the contested-ordering / re-mirror dilemma

A chronological ordering **is the IP**, and different publishers legitimately DISAGREE on it (where Job
sits relative to Genesis, how the Psalms interleave with Samuel/Kings, whether a long chapter is split
by verse). Two "chronological" sources from two publishers disagreeing is a **real editorial difference,
not a typo to reconcile** — so it is *not* second-source verification, it is two different plans. The
investigation hit both horns of the dilemma:

1. **The Blue Letter Bible "Chronological Plan"** (Nathan Gammie, blueletterbible.org, the natural
   canonical candidate — BLB is already the app's flagship reading destination). It is a specific,
   named, published, date-anchored 365-day plan (verified: Day 1 = Genesis 1-3 … Day 365 = Revelation
   19-22; full text extracted from the official PDF). **But every second source I could find for it is a
   verbatim re-host of BLB's own PDF** (Scribd, church bulletins) — the **re-mirror trap** (the exact
   Sprint-1 pricejh==christadelphia and the M'Cheyne Haslam→Edgington lesson). One lineage re-hosted is
   **one witness**, never two. No genuinely independent re-derivation of the BLB ordering exists.

2. **The widely-copied "2020 / Bible Study Tools" chronological lineage** (biblestudytools.com +
   numerous church PDFs, e.g. crossroadsbaptistbeggs.org "2020 CHRONOLOGICAL BIBLE READING PLAN",
   different authors/sites). These re-hosts agree with EACH OTHER, but (a) the lineage is **anonymous
   and untraceable** — the search could not establish a single named original publisher (unlike Robert
   Roberts' Bible Companion or M'Cheyne's 1842 calendar, which are *named, citable* IP), so there is no
   canonical source to ship as the authority; and (b) the multiple agreeing copies are themselves a
   **re-mirror set** of one propagated standard, not independent derivations.

3. **Critically, lineages (1) and (2) genuinely DISAGREE** — proving they are two DIFFERENT plans, so
   neither can witness the other. Cross-confirmed divergences (BLB PDF vs. the BST/2020 lineage, each
   verified from at least two independent fetches):

   | Day | BLB (Nathan Gammie / blueletterbible.org) | "2020 / Bible Study Tools" lineage | Nature of difference |
   |---|---|---|---|
   | 104 | 1 Samuel 21-24; **Psalm 91** | 1 Samuel 21-24 (no Psalm 91) | editorial: which Psalm sits with David's flight |
   | 121 | 2 Samuel 5; 1 Chronicles 11-12 | 2 Samuel **5:1-10** (verse-split across days) | granularity: whole chapter vs. verse split |
   | 150 | **Psalm 119** (whole) | Psalm **119:1-88** (split into two days) | granularity: long chapter split by verse |
   | 200 | 2 Kings 18; 2 Chron 29-31; Psalm 48 | 2 Kings **18:1-8**; 2 Chron 29-31; Psalm 48 | verse split |
   | 209 | 2 Kings 19; Psalms 46, 80, 135 | 2 Kings **18:9-37; 19:1-37**; Psalms 46, 80, 135 | verse split |

   (Other named plans confirm the contestation is structural, not noise: Guthrie/CSB "Reader's Guide to
   the Bible" interleaves the NT from Day 2; Tyndale's *NLT One Year Chronological Bible* is its own
   distinct ordering; the Bible Project, Heartlight, and Billy Graham chronological plans each differ.)

## The decision rule applied (D-ALT-21, the honesty gate)

> Ship a *specific, named, date-anchored* chronological plan with a verifiable independent second
> witness, OR do not ship that plan. Do not fabricate an ordering; do not ship a single-source
> unverified plan; a contested second "chronological" source that DISagrees is NOT a witness.

No candidate satisfied the rule: BLB has only re-mirror witnesses; the BST/2020 lineage is anonymous +
self-re-mirrored; the two top candidates disagree on real editorial choices. **Verdict: NO-GO.** No
`assets/plans/chronological/` asset, no `registry.json` entry, no `tools/build_chronological_*.py`, no
`ChronologicalPlanVerificationTest`, and no `chronological-rebuild` CI job were created. The four
standing data gates are unchanged (BC plan 11, McheynePlanVerificationTest 10, BibleTextVerificationTest
18, BibleDatabaseRoomOpenTest 5).

## Sources consulted (for the record; no asset built from any of them)

| Role | Source | URL |
|---|---|---|
| BLB chronological (named, but only re-mirror witnesses) | Blue Letter Bible "Daily Bible Reading Program — Chronological Plan" (PDF; Author: Nathan Gammie, 2024) | https://www.blueletterbible.org/assets/pdf/dbrp/1Yr_ChronologicalPlan.pdf |
| "2020 / BST" lineage (anonymous, self-re-mirrored, DISagrees with BLB) | biblestudytools.com chronological plan | https://www.biblestudytools.com/bible-reading-plan/chronological.html |
| Same lineage, different host/author (a re-mirror, confirms the lineage agrees with itself but not BLB) | crossroadsbaptistbeggs.org "2020 Chronological Bible Reading Plan" (PDF; Author: "Justin") | https://www.crossroadsbaptistbeggs.org/hp_wordpress/wp-content/uploads/2020/01/2020-Bible-Reading-Plan-Chronological.pdf |
| Distinct named plan (confirms contestation) | Guthrie/CSB "Reader's Guide to the Bible: A Chronological Reading Plan" | https://csbible.com/wp-content/uploads/2018/12/GuthrieChronologicalReadingPlan.pdf |
| Distinct named plan (confirms contestation) | Tyndale *NLT One Year Chronological Bible* | (print; ISBN 978-1496456854) |

## If chronological is ever revisited (future work, NOT this release)

A genuine GO would require ONE of: (a) a publisher's chronological table that **explicitly publishes**
its day-by-day ordering AND has a second house independently transcribe/derive the same ordering and
agree (e.g. an academic chronological harmonization with a corroborating independent edition); or (b)
the owner *designates* a single named plan (e.g. BLB's) as canonical and accepts a **rigorous
single-source structural gate** (whole-Bible coverage, every chapter exactly once, the publisher's
order pinned) IN PLACE OF a second-witness day-by-day gate — a deliberate, owner-signed relaxation of
FR-ALT-3 for this one plan, recorded as an accepted risk. Until then: NO-GO.
