# Sprint J — Psalm 119 sub-chapter verse ranges (Phase 2 of the reading-portion work)

> **EM:** Morgan · **Status:** DONE (uncommitted; main session verifies + commits) ·
> **Date:** 2026-06-15 · **Next:** `sprint-0021-v2x-release-prep`

## Goal outcome — MET

**The reading plan now encodes Psalm 119's four-day verse division as gate-verified trusted IP, and
the app renders those ranges everywhere.** What can be done now that couldn't before:

- **The Schedule (and widget, reminder, persistent notification) show the actual portion** on the
  four Psalm-119 days: "Psalms 119:1–40" / ":41–80" / ":81–128" / ":129–176" instead of a bare
  "Psalms 119" — the part of the schedule the owner said was being lost is restored.
- **The in-app reader shows ONLY the in-range verses** for a windowed portion: tapping the Mar 9
  reading renders Psalm 119 verses 1–40 and no others (verses 1–40 only; 41+ excluded; no
  superscription pulled into a body window). JVM-proven at the use-case level over a window-aware
  fake `BibleTextSource`, independent of the reader's pager wiring.
- The four windows are **provably correct trusted data**: they tile verses 1..176 exactly (no gap,
  no overlap), the two independent plan sources agree on them, and the gate enforces all of it.

This is the full two-track deliverable the owner named (data correction + app support) per
`docs/features/psalm-119-verse-ranges.md`. Schema v1 → **v2**.

## Current capability (working software)

- Plan asset is `schemaVersion: 2`; a `ref` may carry an optional `verseStart`/`verseEnd` window.
  Only the four Mar 9-12 stream-2 Psalm-119 refs do; the other ~1,090 readings are byte-identical.
- `ReadingFormatter.format` → "Psalms 119:1–40"; `formatAbbreviated` → "Psa 119:1–40"; a
  single-verse window → "Psalms 119:7". A windowed ref is its own run (never merges with a chapter
  neighbour). All four collapsed-reference surfaces (Schedule card, widget tiers, reminder,
  persistent tray) flow through `ReadingFormatter`, so they render the window with no per-surface
  code change.
- `PortionVerseBridge.rangesFor` maps a windowed ref to the precise verse_id `VerseRange`
  `[encode(b,c,start) … encode(b,c,end)]`; `GetPortionTextUseCase` then returns only those verses.
- The release gate (`ReadingPlanVerificationTest`) proves the verse data to the Sprint-1 standard.

## Tickets (administrative record)

| Ticket | Owner | Status |
|---|---|---|
| A (data) — encode the four Psalm-119 windows in both JSONs + bump schemaVersion → 2; update extraction scripts to emit the suffix; README provenance/reconciliation + "deferred" → resolved | Riley | ✅ |
| B1 (DTO) — `RefDto.verseStart?/verseEnd?` | Diego | ✅ |
| B2 (model) — `ReferenceVerses(start,end)` + `Reference.verses`; loader maps + validates (both-present, 1≤start≤end); `SUPPORTED_SCHEMA_VERSION` → 2 | Diego | ✅ |
| B3 (formatter) — `ReadingFormatter` renders the window; windowed ref = its own run | Sam | ✅ |
| B4 (reader) — `PortionVerseBridge` windowing; `GetPortionTextUseCase` renders only in-range verses | Sam/Diego | ✅ |
| G1 (gate) — extend `ReadingPlanVerificationTest`: schema pin = 2, well-formedness, tiling, second-source equality, "only Ps 119" pin | Riley | ✅ |

590 tests (net +22 over Sprint I's 568). **Plan gate 7 → 11.** The other two data/Room gates
UNTOUCHED: `BibleTextVerificationTest` = 18, `BibleDatabaseRoomOpenTest` = 5. Full pipeline green
(`spotlessCheck lintDebug assembleDebug testDebugUnitTest koverXmlReportAppDebug koverVerifyAppDebug`),
Kover 95.4% on domain/data, a11y gate 7/7. **8 mutations killed** (4 data + 4 code), each restored
in place. No version bump (stays 1.3.5/10305). No new permissions, no Room/manifest/DataStore change.

## The four new gate assertions (plan gate 7 → 11)

1. **`every windowed ref is well-formed within its chapter`** — both fields present, 1 ≤ start ≤ end,
   end ≤ chapter verse count (D-SCHEMA-3: via the committed `bible/kjv_verse_counts.csv` witness,
   Ps 119 = 176). *Mutation: verseEnd 176→200 → red.*
2. **`the four Psalm 119 days tile verses 1 to 176 exactly`** — THE verse-level coverage invariant:
   sorted windows start at 1, end at 176, each starts exactly one verse after the prior ends, lengths
   sum to 176. *Mutation: Mar 10 start 41→42 (gap at v41) → red.*
3. **`Psalm 119 windows match the independent second source`** — canonical verseStart/verseEnd ==
   verify-fixture for all four days (the existing day-by-day THE GATE already covers it structurally
   now that `RefDto` carries the fields; this is the explicit belt-and-braces pin). *Mutation: change
   one boundary in the asset but not the fixture → red.*
4. **`only the four Psalm 119 days carry verse windows`** — pins the A2 audit: exactly 4 windowed
   refs in the whole plan, all `{Psalms,119}` on Mar 9-12 stream 2. *Mutation: window a non-Ps119 ref
   (Leviticus 16) → red.*

Plus the existing **`schema header is correct`** pin flipped to `== 2` (D-SCHEMA-2). *Mutation:
revert asset to schemaVersion 1 → red.*

## Mutations killed (8, each restored byte-clean in place)

- Data (asset): tiling gap (Mar 10 41→42); range over-bound (Mar 12 176→200); window a non-Ps119 ref;
  revert schemaVersion 2→1.
- Code: drop `ReferenceVerses` `require(start in 1..end)` (model + loader reversed-window red); loader
  accepts a lone bound instead of erroring; `PortionVerseBridge` ignores the window (bridge + use-case
  windowing red); `ReadingFormatter` lets a windowed ref merge into a run.

## Decisions & rationale (as implemented; see the spec's full decision table)

- **D-SCHEMA-1** — two optional ints `verseStart?`/`verseEnd?` on `RefDto` (not a nested object, not a
  string). Absent ⇒ whole chapter for free; trivially gateable.
- **D-SCHEMA-2** — `schemaVersion` 1 → 2, strict-equal floor; loader `SUPPORTED_SCHEMA_VERSION = 2`.
- **D-SCHEMA-3** — `verseEnd ≤ chapterVerseCount` bound from the committed `bible/kjv_verse_counts.csv`
  witness (the recommended option; reuses a trusted asset, generalizes the schema). Documented
  coupling: the V1 plan gate now reads a V3 bible test fixture — both are committed trusted data.
- **D-MODEL-1** — new `ReferenceVerses(start, end)` (chapter-relative 1-based ints) on `Reference`,
  NOT the bible `VerseRange`. The planner domain stays free of any `bible/` dependency; the verse_id
  conversion lives in `PortionVerseBridge` (the planner↔bible join). The per-chapter upper bound is
  NOT model-enforced (planner has no verse-count table) — the gate proves it, exactly as the
  chapter-range bound is gate-proven, not model-proven.
- **D-READER-1/2** — `PortionVerseBridge.rangesFor` is the single seam; one change makes
  `GetPortionTextUseCase` render the window for free. Phase 2 proves "rendered content = exactly the
  windowed verses" at the use-case level (JVM, over a window-aware fake); the pager/swipe-out
  behaviour is owned by the reader-nav work (Phase 1 / Sprint I), not redesigned here.
- **D-DATA-1** — the four boundaries are owner-confirmed (Bible Companion booklet) and gate-verified
  (tiling), not asserted from memory. Both source PDFs print the same division; the two plan sources
  agree day-by-day. No conflict to reconcile.
- **D-UI-1** — Schedule renders **"Psalms 119:1–40"** (catalog name "Psalms" plural; en dash U+2013
  matching the chapter-range form). **STRING/TONE SIGN-OFF FLAG:** the booklet prints "Psalm 119"
  (singular); rendering singular would be a display-only formatter special-case, NOT a data change —
  owner to confirm "Psalms" vs "Psalm" and the en-dash choice. The rendered references are *computed*
  (no static string resource); the en-dash + plural decision is the only thing to sign off.

## State of the codebase

Changed (main):
- `data/plan/dto/PlanDto.kt` — `RefDto` + `verseStart: Int? = null`, `verseEnd: Int? = null`.
- `domain/model/Reference.kt` — `Reference.verses: ReferenceVerses? = null` + the new
  `ReferenceVerses(start, end)` data class (`require(start in 1..end)`). Third defaulted param is
  source-compatible with all existing `Reference(book, chapter)` call sites.
- `data/plan/ReadingPlanAssetLoader.kt` — maps `RefDto` → `Reference.verses` via a private
  `RefDto.toVerses()` (both-present-or-both-absent + reversed-window rejection); bumped
  `SUPPORTED_SCHEMA_VERSION` to 2.
- `bible/domain/PortionVerseBridge.kt` — `rangeFor(ref)`: windowed ref →
  `VerseRange(encode(b,c,start), encode(b,c,end))`, else whole-chapter. The one planner↔bible seam.
- `ui/day/ReadingFormatter.kt` — `formatRun` renders the window; `consecutiveRuns` never merges a
  ref whose `verses != null` (nor merges into one). Literal U+2013 en dash, matching convention.

Data:
- `app/src/main/assets/reading_plan.json` + `app/src/test/resources/reading_plan_verify.json` —
  `schemaVersion: 2`, four Psalm-119 windowed refs (identical in both). Surgical diff: ONLY the
  schemaVersion line + the four refs changed; no reformatting of any other line.
- `tools/extract_primary.py` + `tools/extract_antipas.py` — emit `verseStart`/`verseEnd` for the
  four Psalm-119 days (previously detected and discarded the suffix); schemaVersion 2.

Tests (net +22):
- `ReadingPlanVerificationTest` (7 → 11) — the four new gate assertions + schema pin = 2.
- `ReadingPlanAssetLoaderValidationTest` (+3) — windowed-ref loads; lone-bound rejected;
  reversed-window rejected. (The schema-reject test now flips 2→3, since 2 is the new floor; the
  day()/plan() helpers default schemaVersion 2.)
- `domain/model/ReferenceVersesTest` (new, 5) — window invariants + Reference-carries-window.
- `PortionVerseBridgeTest` (+3) — windowed ref → exact verse_id window; four windows contiguous;
  whole-chapter unchanged.
- `GetPortionTextUseCaseTest` (+2) — windowed portion renders ONLY verses 1–40; days 2-4 render
  their own windows only (the reader-windowing proof).
- `ReadingFormatterTest` (+5) — "Psalms 119:1–40", all four days, single-verse window, abbreviated
  "Psa 119:1–40", windowed-ref-is-its-own-run.
- `FakeBibleTextSource` — made **window-aware** (returns only verses inside the requested range; v0
  title only when the range includes verse 0; synthetic chapter length 176). This let the use-case
  windowing be genuinely proved. `GetChapterUseCaseTest`'s whole-chapter assertion changed from
  exact-size-3 to order-of-first-three (the fake now models a full chapter) — intent preserved.
- `domain/Fakes.kt` — added `windowedPortion(...)` test helper.

Conventions reaffirmed: asset is script-generated, never hand-edited (re-running the extraction
scripts reproduces the windowed refs); the gate is the place that proves the per-chapter verse-count
bound, not the model; the planner domain stays free of any `bible/` dependency.

## New strings (for owner tone sign-off)

**None static** — the windowed reference is *computed* by `ReadingFormatter` ("Psalms 119:1–40",
abbreviated "Psa 119:1–40"). Two display decisions need sign-off (D-UI-1):
- **"Psalms" (plural, catalog name) vs "Psalm" (singular, as the booklet prints).** Currently
  "Psalms 119:1–40". Switching to singular is a display-only formatter special-case if wanted.
- **En dash (U+2013) between start and end** ("1–40"), matching the existing chapter-range form.

## Carryover & next goal

Next: **`sprint-0021-v2x-release-prep`** (the long-queued release track) — version bump past
1.3.5/10305 (recommend 1.4.0/10400 per D-S9-3 now that V3 + both reading-portion phases have landed),
the consolidated owner device pass, string tone sign-offs, closed-track rollout.

Queued / deferred (protected OUT of this sprint):
- Tier-2 install-detected providers (Logos/Olive Tree, BACKLOG); colorblind strip palette; removing
  the orphaned whole-day strings (`mark_whole_day_done`/`unmark_whole_day`/`day_progress`).
- Cross-chapter verse ranges in the plan — out of scope by design (the Companion never does this; the
  schema's verse fields live inside one `{book, chapter}` ref, structurally single-chapter).
- Per-verse mark-as-read — out of scope; marking stays at the portion grain.

## Open questions & risks

- **Device-pass items (NOT JVM-provable):** the windowed reference's look in the Schedule card, the
  widget tiers (smallest tiers use `formatAbbreviated` → "Psa 119:1–40"; spec flagged tiny-tier look
  as a non-blocker), and the notification/tray; the reader showing exactly verses 1–40 on glass for
  the Mar 9 portion; swipe behaviour out of a windowed portion page (owned by the reader-nav work).
- **D-UI-1 tone sign-off** (Psalms vs Psalm; en dash) — see "New strings".
- **D-SCHEMA-3 coupling** — the V1 plan gate now reads the V3 `bible/kjv_verse_counts.csv` witness.
  Documented and accepted (both committed trusted data). If zero-coupling is ever preferred, a pinned
  `Ps 119 = 176` constant is the fallback (lower generality).
- No new runtime deps, no manifest/Room/DataStore change, no version bump.

## Next sprint

`next: sprint-0021-v2x-release-prep`
