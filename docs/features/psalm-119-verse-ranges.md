# Feature spec: sub-chapter verse ranges in the reading plan (Psalm 119)

**Status:** Spec for review — not yet scheduled. **Author:** Diego (staff architect), from owner request 2026-06-15.
**Phase:** This is **Phase 2** of the reading-portion work. **Phase 1 (multi-chapter combined reader page) is being
implemented in parallel by another engineer** — this spec is **doc-only** and deliberately touches **no code, no plan
data, and not `CLAUDE.md`**, to avoid colliding with that work. It defines the design + the task breakdown; the tickets
land in a later sprint after Phase 1.
**Companion spec:** [`reader-portion-view.md`](reader-portion-view.md) — its §6 is the Phase-2 sketch this document
expands to buildable detail.

---

## 0. The owner's framing (the spec is built to this)

> "The plan SHOULD include the verses. It's an important part of the program/schedule that each day a certain portion
> of verses is read. By having those days simply list '119' a portion of the schedule is lost. We need to support the
> sub-chapter verse breakdown for this, and then in the reading pane display that range of verses."

So the deliverable is two-track, exactly as the owner named:

- **(a) Data correction** — encode the Bible Companion's actual verse division of Psalm 119 across its four reading
  days, as **trusted IP data** under the Sprint-1 data-gate discipline.
- **(b) App support** — let a `ref` carry an optional verse range, render only that range in the reader, and show the
  collapsed reference ("Psalm 119:1–88") in the schedule UI.

---

## 1. Ground truth (verified against the repo, 2026-06-15)

- **The plan is chapter-level only.** `app/src/main/assets/reading_plan.json` is `schemaVersion: 1`; every `ref` is
  `{book, chapter}` — there is no verse field anywhere. DTO: `data/plan/dto/PlanDto.kt` (`RefDto(book, chapter)`).
- **Psalm 119 is the ONLY in-chapter multi-day split.** It is read across **four days — Mar 9, 10, 11, 12 (stream 2)**
  — each currently stored as the whole `{"book":"Psalms","chapter":119}` (verified at lines 2670/2711/2744/2777 of the
  asset). The reconciliation log (`docs/data/README.md`, normalization rule §2) records that **both** sources split it
  by verse ("Psalms 119:1-40 / :41-80 / :81-128 / :129-176"; antipas "119,v.1-40" etc.) and that schema-v1 flattened
  those four days to bare chapter 119 — **"verse-range fidelity is a deferred candidate enhancement."** This spec *is*
  that enhancement.
- **No other reading is an in-chapter split.** NT chapters appearing on two days are the "NT read twice a year" design,
  not a verse split (companion spec §2). An **audit task in this spec re-proves** that Psalm 119 is the only one, so we
  don't silently ship a plan that still loses other portions.
- **The verse machinery already exists** (Sprint-B spine, `bible/`):
  - `VerseId.encode(bookNo, chapter, verse)` → canonical `Long` id (`book*1_000_000 + chapter*1_000 + verse`); the
    multipliers explicitly cover **Ps 119 = 176 verses** (see the `VerseId` KDoc). `VerseId.chapterRange(b,c)` =
    `[encode(b,c,0) … encode(b,c,999)]`.
  - `VerseRange(startVerseId, endVerseId)` (inclusive, rejects reversed) — `bible/domain/model/VerseRange.kt`.
  - `VerseRef(book, chapter, verse)` with the **verse ∈ [0,999]** invariant (verse 0 = superscription) —
    `bible/domain/model/VerseRef.kt`.
  - `BibleTextSource.getVerses(VerseRange): List<VerseText>` (the seam) over `VerseDao.getVerses(startId, endId)` —
    **an arbitrary verse_id window already works**; the reader's per-page `getChapter` just doesn't use it yet.
  - `PortionVerseBridge.rangesFor(Portion): List<VerseRange>` maps each ref via `VerseId.chapterRange` today — the one
    place that turns chapter refs into verse_id ranges. **This is the natural extension point.**
- **The reader, as shipped (Sprint H), is single-chapter-per-page.** `ReaderViewModel.loadPage` calls
  `getChapter(book, chapter)` (DAO `getChapter(bookNo, chapter)` — a *whole-chapter* query, no verse window) and
  `ReaderScreen` renders the chapter as a verse-id-keyed `LazyColumn`. **Important consequence:** D-H-7 superseded the
  old multi-block "portion page" — a Schedule tap currently *lands on the portion's first chapter and you swipe*. So
  "show only the in-range verses" has to be expressed in whatever render path Phase 1 settles on. §4 handles both the
  current single-chapter pager and Phase 1's portion page, and pins the seam so Phase 2 is robust either way.
- **Schedule UI** renders the collapsed reference via `ui/day/ReadingFormatter.format(portion)` ("Genesis 1–2";
  "2 John 1; 3 John 1"). It currently collapses **chapter runs only** and has no verse awareness.

---

## 2. Scope

**In scope (Phase 2):**
- Optional verse-range on a plan `ref` (schema v1 → v2), and the matching DTO + domain-model change.
- Encoding Psalm 119's four-day verse division as trusted, second-source-verified data, with a reconciliation-log entry.
- The verification-gate extension (range validity, tiling, second-source equality, schemaVersion bump).
- Reader rendering of a verse-windowed portion; schedule-UI collapsed reference ("Psalm 119:1–88").

**Out of scope:**
- Phase 1 (multi-chapter combined page) — parallel work; this spec composes with it, does not implement it.
- Cross-chapter verse ranges in the plan (e.g. "Ps 119:170-Ps 120:3"). The Companion never does this; the plan's
  verse ranges are **always within one chapter**. The schema's optional verse fields live *inside a single
  `{book, chapter}` ref*, so cross-chapter ranges are structurally unrepresentable — which matches both the data and
  `ReferenceResolver`'s existing V3.0 "no cross-chapter ranges" stance.
- Per-verse mark-as-read. Marking stays at the portion grain (the whole day's stream). Splitting Psalm 119 into four
  *days* already gives four independently-markable portions; verses within a day are not separately marked.
- Re-deriving or storing the 22-stanza structure of Psalm 119. We encode the **Companion's day boundaries**, not the
  acrostic stanzas (they may or may not coincide — see §3).

---

## 3. The Psalm 119 verse division — the trusted-data task

This is the heart of Phase 2 and the part that must not be guessed. Psalm 119 has **176 verses** (the longest chapter
in scripture; `VerseId` already accounts for it). The Companion reads it over four days.

### 3.1 What we must NOT do

**Do not assert the verse boundaries from memory.** The reconciliation log's parenthetical
("119:1-40 / :41-80 / :81-128 / :129-176") was recorded during Sprint-1 extraction and is a **strong candidate**, but
it has **not** been put through the second-source gate as *range* data — it was captured as a note while flattening to
chapter 119. Phase 2 must **source and verify** the four boundaries fresh, to the same standard as the rest of the plan.

It is *plausible* the division is near-equal quarters (~44 verses each) or aligned to the psalm's 22 eight-verse
acrostic stanzas (176 = 22 × 8, so a stanza-aligned split would land on multiples of 8: e.g. 1–40 / 41–80 / 81–128 /
129–176 is 5+5+6+6 stanzas). **This is a hint for plausibility-checking the sourced numbers, NOT a substitute for
sourcing them.** Flag any boundary that is *not* a multiple of 8 for extra scrutiny (it would be unusual but is not
impossible — verify against the source, don't "correct" it to fit the hypothesis).

### 3.2 How to source it (Sprint-1 discipline, mirrored exactly)

The plan-data sources are already established in `docs/data/README.md`:

| Role | Source | Already in the repo's provenance record |
|---|---|---|
| **Primary** (canonical on conflict) | christadelphia.org Bible Companion chart PDF | yes — MD5 `d1e0121a…` |
| **Second source** (independent) | antipas.org "The Bible Companion" booklet PDF | yes — the existing second-source witness |
| Tie-breaker (logged on use) | dailyreadings.org.uk | yes — used once in Sprint 1 |

**Both PDFs already print the verse breakdown for Psalm 119** (the reconciliation log §2 confirms both do, in
different notation). So the two-independent-source rule is satisfiable from the *same documents already used for the
chapter data* — we are reading a column we previously discarded, with two independently-written parsers, exactly as
Sprint 1 did. The extraction scripts (`tools/extract_primary.py`, `tools/extract_antipas.py`) already locate the
Psalm-119 rows; they currently drop the verse suffix (normalization rule §2). Phase 2 **stops dropping it** for those
four days and emits `verseStart`/`verseEnd`.

**Procedure (one ticket, §5 D1):**
1. Re-extract the Psalm-119 verse boundaries from the **primary** PDF for Mar 9/10/11/12.
2. Independently extract the same four boundaries from the **second source** (antipas booklet).
3. **Second-source equality:** the four ranges must agree day-by-day. Any disagreement is reconciled **on evidence**
   (the tiling/coverage invariant in §3.3 + the third witness if needed), **logged** in `docs/data/README.md`, never
   resolved by source precedence alone — identical to the Sprint-1 7-conflict process.
4. Record the result: update the `docs/data/README.md` normalization rule §2 from "deferred" to the encoded ranges,
   and add a Psalm-119 row group to the reconciliation log (sources agreed / how any conflict was decided).

### 3.3 The data invariant the four ranges MUST satisfy (this is the gate, §3 below)

The four days must **tile 1..176 exactly** — contiguous, no gap, no overlap, no verse read twice or skipped:

- Day Mar 9  = `[1 .. e1]`
- Day Mar 10 = `[e1+1 .. e2]`
- Day Mar 11 = `[e2+1 .. e3]`
- Day Mar 12 = `[e3+1 .. 176]`

with `1 ≤ e1 < e2 < e3 < 176`. This is the verse-level analogue of the plan's existing **full-coverage / read-once**
chapter invariant (the one that caught 5 of 7 Sprint-1 conflicts). It is *self-validating*: if the sourced numbers
don't tile 1..176, the extraction is wrong and the gate fails. This is the load-bearing check.

> **Owner/product call to flag (D-DATA-1):** confirm the four boundaries once sourced. The author's working hypothesis
> (to be *verified*, not assumed) is **1–40 / 41–80 / 81–128 / 129–176**, matching the reconciliation-log note. The
> spec does **not** commit to these numbers — the ticket sources them and the gate enforces tiling regardless.

### 3.4 The "only Psalm 119" audit (so no other portion is silently lost)

A separate, cheap audit ticket (§5 D2) re-confirms — by re-scanning **both source PDFs** for any verse-suffixed
reading other than the four Psalm-119 days — that **no other reading in the year is an in-chapter verse split**. If
the audit finds one, it is *new scope*: stop and escalate to the owner (don't quietly encode it). The audit's negative
result is recorded in `docs/data/README.md` alongside the Psalm-119 entry, so the claim "Psalm 119 is the only one" is
evidenced, not asserted.

---

## 4. Schema, model, and rendering changes

### 4.1 Plan schema (v1 → v2)

Extend a `ref` with **two optional integer fields**; absent ⇒ whole chapter (backward-identical for the 1,090+ other
readings):

```jsonc
// schemaVersion: 2
{ "book": "Psalms", "chapter": 119, "verseStart": 1, "verseEnd": 88 }   // a verse-windowed ref
{ "book": "Genesis", "chapter": 1 }                                      // whole chapter — unchanged shape
```

**Decision D-SCHEMA-1 — shape: two optional fields `verseStart?`/`verseEnd?` on the existing `RefDto`** (not a nested
`verseRange` object, not a string "119:1-88"). Rationale: minimal diff; `kotlinx.serialization` with nullable
defaults makes absent ⇒ `null` ⇒ whole chapter for free; no parser change for the 1,090+ readings that omit them;
trivially gateable (two ints to range-check). A nested object adds a DTO type for one feature; a string re-introduces
parsing the planner deliberately pushed into `ReferenceResolver`.

**Validity rules (enforced at load AND in the gate):**
- Both present or both absent. (Reject `verseStart` without `verseEnd` and vice-versa — ambiguous.)
- `1 ≤ verseStart ≤ verseEnd`. Verse `0` (superscription) is **never** a plan boundary — the Companion reads verse
  ranges of *body* text; verse-0 titles are a reader-render concern, not a schedule boundary. (Psalm 119 has no
  superscription anyway.)
- `verseEnd ≤` the chapter's verse count. **This needs a verse-count source** — see D-SCHEMA-3.

**Decision D-SCHEMA-2 — schemaVersion bump 1 → 2, hard floor (no dual-version runtime support).** The loader
(`ReadingPlanAssetLoader`) currently `check(schemaVersion == 1)`. Bump the constant to `2`. The asset is bundled and
release-gated, so there is exactly one plan version in any build — we do **not** need to read both v1 and v2 at
runtime. Forward-compat: a v2 reader rejecting a v1 asset (or vice-versa) is a **build defect**, caught by the gate,
never a user condition. The DTO stays able to *parse* a v1-shaped ref (the new fields are optional), but the
`schemaVersion` header check is strict-equal to 2 after the bump. **The `schema header is correct` gate test asserts
`== 2`** (the bump is pinned, so a stray v1 asset fails the gate).

**Decision D-SCHEMA-3 — where the `verseEnd ≤ chapterVerseCount` upper bound comes from.** The plan gate runs as a
pure-JVM unit test reading text fixtures (`ReadingPlanVerificationTest`); the `book_catalog.csv` fixture has
`chapterCount` but **not** per-chapter verse counts. Two options:
- **(Recommended) Reuse the existing KJV verse-count witness.** Sprint-A already ships
  `app/src/test/resources/bible/kjv_verse_counts.csv` (1,189 rows, the second-source verse-count witness). The plan
  gate can load it to bound `verseEnd`. This is a *second independent witness* for the verse-count upper bound and
  reuses committed data — no new fixture. Cost: the plan gate gains a dependency on a bible-test fixture (a documented
  cross-reference, acceptable — both are committed trusted-data assets).
- **(Alternative) For Phase 2's tiny scope, hard-pin Psalm 119 = 176** in the gate (a single named constant) and only
  generalize the per-chapter bound if/when a second verse-windowed reading ever appears. Lower coupling, less general.

  Recommend the first (it generalizes the schema honestly and reuses a trusted asset); flag the choice for the
  implementer. Either way, **Ps 119 = 176** is the load-bearing number for the tiling check.

### 4.2 Domain model (`Reference` gains an optional `VerseRange`)

`domain.model.Reference` is chapter-level today (`Reference(book, chapter)` with `chapter in 1..book.chapterCount`).
Add an **optional verse window**, keeping all current invariants:

```kotlin
data class Reference(
    val book: Book,
    val chapter: Int,
    val verses: ReferenceVerses? = null,   // null = whole chapter (default; 1,090+ readings unchanged)
)
data class ReferenceVerses(val start: Int, val end: Int) { /* require 1 <= start <= end */ }
```

**Decision D-MODEL-1 — a NEW small `ReferenceVerses(start, end)` (chapter-relative 1-based ints), NOT the bible
spine's `VerseRange` (which is absolute `verse_id`s).** Rationale: `domain.model.Reference` lives in the **planner**
domain and must not depend on the `bible/` package (the planner ships in V1 without any bible code; the dependency
direction is planner → ` ` , bible → planner, never planner → bible). `VerseRange` is a `verse_id` pair meaningful
only with `VerseId` encoding; the plan's natural unit is chapter-relative verse numbers. The **conversion to a
`verse_id` `VerseRange` happens in the bible spine** (`PortionVerseBridge`, §4.4), which already depends on both. This
keeps `Reference` self-contained and pure.

- `Reference.init` keeps `require(chapter in 1..book.chapterCount)` and adds, when `verses != null`,
  `require(1 <= start <= end)`. The per-chapter `end ≤ verseCount` upper bound is **not** enforced in `Reference`
  (the planner domain has no verse-count table — that belongs to the bible asset and the gate); the gate is the place
  that proves it, exactly as the existing chapter-range bound is gate-proven, not model-proven, for the catalog.
- **`ReadingPlanAssetLoader`** maps `RefDto.verseStart/verseEnd` → `Reference.verses` (both-null ⇒ whole chapter).
  Its `validate()` gains the both-present-or-both-absent + `1 ≤ start ≤ end` checks (a malformed asset throws at
  load; the gate is the primary guard, this is defense-in-depth).
- **`Portion` is unchanged** — still `List<Reference>`. A verse-windowed portion is just a portion whose ref carries
  `verses`.

### 4.3 Schedule UI — the collapsed reference ("Psalm 119:1–88")

`ui/day/ReadingFormatter.format(portion)` extends so a ref with `verses` renders the verse window:

- Whole-chapter ref → unchanged ("Genesis 1–2", "Psalms 119").
- Verse-windowed ref → **"Psalms 119:1–88"** (book + chapter + ":" + start "–" end, en dash, matching the existing
  chapter-range en dash). A single-verse window (start == end) → "Psalms 119:1".
- **Run collapsing:** a verse-windowed ref **never merges** into a consecutive-chapter run (its `verses != null`
  breaks the "previous.chapter + 1 == ref.chapter" run, and even a same-chapter neighbour shouldn't merge). Each of
  the four Psalm-119 days is a single-ref portion anyway, so in practice each renders standalone as "Psalms 119:1–88"
  etc. Pin this: a windowed ref is its own run.

> **Owner/product call to flag (D-UI-1): how the split reference should read.** Options the spec surfaces (pick one):
> (a) **"Psalms 119:1–88"** (book singular/plural follows the catalog name "Psalms"; the canonical name is "Psalms",
> so it reads "Psalms 119:1–88"); (b) add a stanza/section hint ("Psalm 119:1–88 (Aleph–Daleth)") — *not recommended*,
> it requires encoding stanza names we deliberately scoped out; (c) plain "Psalm 119" unchanged in the schedule and
> show the range only in the reader. **Recommend (a)** — it directly answers the owner's "display that range of
> verses" and costs nothing. Note the catalog name is "**Psalms**" (plural); if the owner wants "Psalm 119:1–88"
> (singular, as the booklet prints it) that is a *display-only* formatter special-case, not a data change. Flag for
> the owner.

The same windowed-formatter logic should be available to **`formatAbbreviated`** (widget) and the **reminder/persistent
notifier** bodies, which also call `ReadingFormatter` — verify those surfaces render "Ps 119:1–88" acceptably (they
already handle the chapter-range form; the verse suffix is additive). The widget at its smallest tiers may want to
keep the abbreviated chapter-only form — flag as a device-pass look item, not a blocker.

### 4.4 Reader — render only the in-range verses

The verse machinery already addresses arbitrary windows; the only gap is that the reader's per-page path uses the
*whole-chapter* query. Two render paths exist depending on Phase 1's landing; the spec pins **one seam** that serves
both:

**The seam (D-READER-1): `PortionVerseBridge.rangesFor` maps a windowed ref to a verse-windowed `VerseRange`.**
Today it does `VerseId.chapterRange(book.order, chapter)` for every ref. Extend it so a ref carrying `verses` maps to
`VerseRange(encode(book, chapter, start), encode(book, chapter, end))` instead of the whole-chapter `[…,0 … …,999]`.
This is **one method change in the one place that turns chapter refs into verse_id ranges** — the bridge is the planner↔bible
join point and already depends on both. `GetPortionTextUseCase` (which calls `rangesFor` then `getVerses` per range)
then renders only the windowed verses **for free** — no use-case change. The verse-0 superscription and `nativeLabel`
rules are unaffected (Psalm 119 has no superscription; and a windowed body range `[1..88]` naturally excludes verse 0).

**Path A — Phase 1 ships a portion-anchored page (companion spec §4).** Then `GetPortionTextUseCase` is the renderer
and the bridge change above is the whole job: the portion page shows exactly verses 1–88. Nothing else to do.

**Path B — the reader stays single-chapter-per-page (current Sprint-H reality) for verse-windowed portions.** Then the
chapter page must learn to *open at and bound to* the window. Minimal approach:
- Add a verse-windowed chapter query: `VerseDao.getVerses(startId, endId)` already exists; add a use-case path
  `GetChapterUseCase(book, chapter, verses: ReferenceVerses?)` (or a sibling `GetChapterWindowUseCase`) that, when
  `verses != null`, calls `getVerses(encode(b,c,start), encode(b,c,end))` instead of the whole-chapter `getChapter`.
- The reader page for the Psalm-119 portion renders only verses 1–88. Swiping to the adjacent page (Psalm 120, or
  Psalm 119's *next* day's window) is a Phase-1/D-H-7 navigation question, not a Phase-2 one.

**Decision D-READER-2 — Phase 2 implements the bridge change (Path A's complete fix) and gates it at the use-case
level (`GetPortionTextUseCase` returns only the windowed verses), independent of which pager Phase 1 ships.** The
pager wiring (does the window appear as its own page, how swipe behaves out of it) is **owned by Phase 1 / the reader
navigation work** and is explicitly *deferred and flagged* here, not designed. Phase 2's provable deliverable is:
*given the Psalm-119 portion, the rendered content is exactly verses 1–88 (etc.)* — JVM-testable over a fake
`BibleTextSource`, no UI. This keeps Phase 2 shippable as a data+domain change even if the reader-nav details are
still settling.

**Verse-tap-out (Sprint H) is unaffected** — each rendered verse keeps its canonical `verse_id`, so tapping verse 88
opens that exact verse externally exactly as today.

---

## 5. Task breakdown

Two tracks, as the owner named. **Track A (data) and Track B (app) can proceed in parallel up to the gate; the gate is
the hard exit and depends on both.** All of this is **post-Phase-1** (the multi-chapter reader page is the parallel
engineer's; coordinate so the `Reference`/`ReadingFormatter`/`PortionVerseBridge` edits don't collide — Phase 2 should
rebase onto Phase 1's merged reader).

### Track A — data correction (trusted IP)

- **A1 — Source the Psalm-119 verse division (D1).** Re-extract the four-day boundaries (Mar 9/10/11/12) from the
  **primary** PDF; independently from the **second source**; reconcile any disagreement on evidence; record the result
  and update `docs/data/README.md` normalization rule §2 (from "deferred" to the encoded ranges) + a reconciliation-log
  entry. **Exit:** two independent sources agree (or a logged evidence-based resolution); the four ranges tile 1..176.
- **A2 — "Only Psalm 119" audit (D2).** Re-scan both source PDFs for any *other* verse-suffixed reading; record the
  negative result in `docs/data/README.md`. **Exit:** evidenced claim that Psalm 119 is the sole in-chapter split (or
  escalation to the owner if not). *Depends on nothing; do first or alongside A1.*
- **A3 — Encode the data + bump schemaVersion.** Update the **extraction scripts** (`tools/extract_*.py`) to stop
  dropping the Psalm-119 verse suffix and emit `verseStart`/`verseEnd` for those four refs; regenerate
  `reading_plan.json` and the second-source fixture `reading_plan_verify.json`; set both files'
  `schemaVersion` to **2**. **The asset is script-generated, never hand-edited** (Sprint-1 discipline — re-running the
  scripts must reproduce it byte-equivalently). **Exit:** both JSON files carry the four windowed refs and
  `schemaVersion: 2`. *Depends on A1.*

### Track B — app support (DTO / model / formatter / reader)

- **B1 — DTO.** `RefDto` gains `val verseStart: Int? = null`, `val verseEnd: Int? = null`. (Optional nullable
  defaults; `ignoreUnknownKeys = false` already in `PlanJson` — the new keys are *known*.) *Independent.*
- **B2 — Domain model.** `Reference` gains `verses: ReferenceVerses? = null` (+ the `ReferenceVerses` type, D-MODEL-1);
  `ReadingPlanAssetLoader` maps the DTO fields → `Reference.verses` and adds the both-present + `1 ≤ start ≤ end`
  load-time checks; bump `SUPPORTED_SCHEMA_VERSION` to 2. *Depends on B1.*
- **B3 — Schedule formatter.** `ReadingFormatter.format` (and `formatAbbreviated`) render the verse window
  ("Psalms 119:1–88"); a windowed ref is its own run. *Depends on B2; needs D-UI-1 owner call on the exact wording.*
- **B4 — Reader rendering (D-READER-1/2).** `PortionVerseBridge.rangesFor` maps a windowed ref to a windowed
  `VerseRange`; verify `GetPortionTextUseCase` then returns only the in-range verses (and, if Path B, the
  windowed-chapter use-case path). *Depends on B2; coordinate with Phase 1's reader.*

### The gate (hard exit) — depends on A3 + B1/B2

- **G1 — Extend `ReadingPlanVerificationTest`** (§6). The bump, range validity, the 1..176 tiling, and second-source
  range equality are all pinned. **No ship until green.** *Depends on A3 (the data) and B1 (the DTO that lets the test
  read `verseStart`/`verseEnd`).*

### Decision records (the non-obvious calls)

| ID | Decision | Why |
|---|---|---|
| **D-SCHEMA-1** | `ref` gains two optional ints `verseStart?`/`verseEnd?` (not a nested object, not a string). | Minimal diff; absent ⇒ whole chapter for free; trivially gateable; keeps parsing out of the DTO. |
| **D-SCHEMA-2** | `schemaVersion` 1 → 2, strict-equal floor (no dual-version runtime). | One plan version per release; mismatch = build defect caught by the gate, never a user condition. |
| **D-SCHEMA-3** | `verseEnd ≤ chapterVerseCount` bound sourced from the committed `kjv_verse_counts.csv` witness (recommended) or a pinned Ps 119 = 176 constant. | Reuses a trusted second-source asset; generalizes the schema honestly. |
| **D-MODEL-1** | New `ReferenceVerses(start,end)` (chapter-relative ints) on `Reference`, NOT the bible `VerseRange` (verse_ids). | Keeps the planner domain free of any `bible/` dependency; the verse_id conversion lives in `PortionVerseBridge`. |
| **D-READER-1** | `PortionVerseBridge.rangesFor` is the single seam that turns a windowed ref into a windowed `VerseRange`. | It's the one planner↔bible join that produces verse_id ranges; one change makes `GetPortionTextUseCase` render the window for free. |
| **D-READER-2** | Phase 2 proves "rendered content = exactly the windowed verses" at the use-case level (JVM); the pager/swipe wiring is deferred to Phase 1 / reader-nav. | Lets Phase 2 ship as a data+domain change without owning the reader-navigation details still in flight. |
| **D-DATA-1** | The four boundaries are **sourced + gate-verified**, not asserted from memory; working hypothesis 1–40/41–80/81–128/129–176 to be *verified*. | Trusted-IP discipline; the tiling gate enforces correctness regardless of the hypothesis. |
| **D-UI-1** | Schedule renders "Psalms 119:1–88" (owner to confirm "Psalms" vs "Psalm" and whether to show the range in-schedule at all). | Directly answers "display that range of verses"; the plural is a display-only formatter choice. |

---

## 6. The verification gate (the hard part — designed to Sprint-1 standard)

`ReadingPlanVerificationTest` is the release gate. Verse ranges are **trusted IP** and get the **same** treatment as
the chapter data. Extensions (each a discrete assertion, mutation-target in mind):

1. **Schema bump pinned.** `schema header is correct` asserts `plan.schemaVersion == 2` (was `== 1`). A stray v1 asset
   fails. *(Mutation target: flip to `!= 2` / leave at 1 → red.)*
2. **Range well-formedness (every windowed ref).** For every ref with verse fields: both present, `1 ≤ verseStart ≤
   verseEnd`, and `verseEnd ≤` the chapter's verse count (via the `kjv_verse_counts.csv` witness, D-SCHEMA-3 — Ps 119 =
   176). Whole-chapter refs (no verse fields) are unaffected and re-verify identically (proves the 1,090+ readings are
   untouched). *(Mutation: a range `1..200` on Ps 119 → red; a `verseStart > verseEnd` → red.)*
3. **Psalm-119 tiling — THE verse-level coverage invariant.** Collect the four Mar 9–12 stream-2 ranges; assert they
   **tile 1..176 exactly**: sorted by start, `first.start == 1`, `last.end == 176`, each `next.start == prev.end + 1`,
   no gap, no overlap. This is the verse analogue of the existing chapter full-coverage / read-once check (which caught
   5 of 7 Sprint-1 conflicts) and is the load-bearing correctness proof. *(Mutation: drop a verse at a boundary, or
   overlap two days → red.)*
4. **Second-source range equality.** The existing `THE GATE` test (`plan matches the independent second source day by
   day`) compares `day.portions` structurally. Once `RefDto` carries `verseStart`/`verseEnd` and `Portion`/`Reference`
   equality includes them, **the existing day-by-day equality automatically covers the ranges** — *provided the
   second-source fixture (`reading_plan_verify.json`) is regenerated to carry the same windowed refs* (A3). Verify the
   `data class` equality path includes the new fields (it will, as `data class` members) so the gate compares them
   without new code. Add an explicit assertion that the four Psalm-119 days' ranges match the fixture, so a silent
   fixture/asset divergence on the ranges can't pass. *(Mutation: change one boundary in the asset but not the fixture
   → the day-by-day gate goes red.)*
5. **"Only Psalm 119 is windowed" pin.** Assert that the **only** refs in the whole plan with verse fields are the four
   Mar 9–12 stream-2 Psalms-119 refs (count == 4, all `{Psalms, 119}` on those dates). This pins the A2 audit into the
   gate: if a future data change adds a windowed ref elsewhere without spec review, the gate flags it. *(Mutation: add
   a windowed ref on another day → red.)*

**Mutation discipline (Sprint-1 standard):** each of the five must be shown to fail under an intended mutation and pass
after restore. The existing 7-test gate is **untouched** in intent — these are additive assertions (or extensions of
the schema-header and day-by-day tests). Net: the plan gate proves the verse ranges are valid, tiling, second-source-
agreed, version-bumped, and scoped to Psalm 119 — to the same bar that proved the chapter data.

---

## 7. Risks, composition, and flags

- **Composition with Phase 1.** Phase 2 edits `Reference`, `ReadingFormatter`, and `PortionVerseBridge` — files Phase 1
  also touches (the multi-chapter reader page). **Sequence Phase 2 after Phase 1 merges** and rebase onto it; the
  reader-render decision (D-READER-2) is deliberately written to not depend on Phase 1's pager design. Coordinate the
  `Reference` constructor change (new optional param is source-compatible, but Phase 1's call sites should be checked).
- **The data is the real cost, not the UI.** As the companion spec says, Phase 2 is small in code and entirely about
  getting four verse boundaries right under the gate. Budget the trusted-data tickets (A1/A2) accordingly — they are
  the schedule risk, not B1–B4.
- **Verse-count witness coupling (D-SCHEMA-3).** Reusing `kjv_verse_counts.csv` in the plan gate couples a V1 plan test
  to a V3 bible fixture. Both are committed trusted-data; document the cross-reference. If the team prefers zero
  coupling, the pinned-176 alternative is acceptable for Phase 2's one-chapter scope.
- **Widget / notification surfaces.** `formatAbbreviated` and the reminder/persistent notifiers call `ReadingFormatter`;
  confirm "Ps 119:1–88" renders acceptably there (device-pass look item, not a blocker).
- **Owner/product calls to resolve before B3 ships:** **D-UI-1** (exact schedule wording, "Psalms" vs "Psalm", show
  range in-schedule at all) and **D-DATA-1** (confirm the sourced four boundaries). Both are flagged, neither blocks
  starting Track A/B.
- **Device-pass items:** the windowed reference's look in the schedule card, widget tiers, and notification; the
  reader showing exactly verses 1–88 on glass; swipe behaviour out of a windowed portion page (owned by Phase 1).
