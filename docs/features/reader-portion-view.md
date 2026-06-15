# Feature spec: reading-portion view in the in-app reader

**Status:** Spec for review — not yet scheduled. **Author:** main session, from owner request 2026-06-15.
**Depends on:** the Sprint-H reader (`bible/ui/reader/`, `GlobalChapterIndex`, the chapter `HorizontalPager`),
the Sprint-B `GetPortionTextUseCase` (renders a whole `Portion` as ordered blocks — already built, currently
unused by the reader since D-H-7).

---

## 1. The request

When the user taps a reading that spans **multiple chapters** (e.g. "James 4–5") or, eventually, a **verse range**
(the owner's example: Psalm 119), the reader should open showing the **full reading** — both chapters together,
or only the included verses — not just the first chapter (the current D-H-7 behaviour). And the navigation must be
**consistent**: swiping away and back returns to the same reading view.

Meanwhile the **Bible tab** (bottom nav) stays plain **single-chapter-by-chapter** swiping.

## 2. What the plan data actually contains (ground truth)

- The plan is **chapter-level only**: every `ref` is `{book, chapter}` — **no verse ranges exist in the data.**
- **Portion sizes:** 776 readings are 1 chapter, 285 are 2 chapters, 31 are 3, 2 are 4, 1 is 5. So **319 readings
  are multi-chapter** — the James 4–5 case is common and real.
- **Multi-day splits:** most NT chapters appear on two days — that's the plan's "NT twice a year" design, **not** a
  split. **Psalm 119 is the ONLY genuine in-chapter multi-day split:** Mar 9/10/11/12, each stored as the whole
  "Psalms 119". The Bible Companion divides its 176 verses across those four days; **our data does not encode that
  division.** No other reading is split by verse.

**Consequence:** the multi-*chapter* behaviour (James 4–5) is fully buildable on existing data. The verse-range
behaviour (Psalm 119 "only the included verses") is **not representable** without adding verse data to the plan —
and it affects exactly **one** reading. So this splits cleanly into two phases.

## 3. The core model — two reader contexts

The reader operates in one of two **contexts**, each its own `HorizontalPager` configuration:

| Context | Entered by | Pager pages | Swipe |
|---|---|---|---|
| **Browse** | the Bible tab; the book/chapter picker | one chapter per page, all 1,189 (`GlobalChapterIndex`, unchanged) | single chapter ↔ chapter |
| **Reading** | tapping a reading on the Schedule (IN_APP) | the portion is **one atomic page**, flanked by single chapters | portion ↔ adjacent single chapters |

They are **separate pager instances** — they don't share a page-index space, so the same chapters can be "one
combined page" in a Reading context and "individual pages" in Browse without contradiction. This is what makes the
consistency guarantee clean.

## 4. The Reading context — the portion-anchored pager

For a portion spanning the contiguous global-chapter range `[first … last]` (e.g. James 4 = index g, James 5 =
g+1), the Reading pager's pages are:

```
… [Jas 2] [Jas 3] [ ★ Jas 4 + Jas 5 (one page) ★ ] [Jas 6] [1 Pet 1] …
   single  single        THE PORTION                 single   single
```

- The portion occupies **one page**, anchored at a fixed page index. Its content is the whole portion rendered as
  ordered blocks (chapter header + verses, then the next chapter header + verses) via the existing
  `GetPortionTextUseCase` — i.e. this **revives the pre-Sprint-H multi-block render**, but only in this context.
- Pages to the left are the single chapters **before** the portion's first chapter; pages to the right are the
  single chapters **after** the portion's last chapter. The portion's own chapters never appear as separate pages
  in this context.
- Entering opens **on the portion page**. The Gen-1 / Rev-22 bounds still hold (the pager can't scroll past them).
- Index model: a `ReadingPagerIndex(portion)` analogous to `GlobalChapterIndex`, with the portion's `[first…last]`
  span collapsed to a single slot. Pure / JVM-testable, pinned against `GlobalChapterIndex` for the flanking pages.
- Two-book portions (Jun 19 / Dec 19 = 2 John + 3 John) are globally adjacent chapters, so they form one
  contiguous span and render as one page for free (`GetPortionTextUseCase` already handles the two-book case).

### This answers the owner's questions directly

- **"James 4–5 opens both on one page. Swipe to chapter 6, then swipe back — what shows?"**
  → Swiping right from the portion goes to **James 6** (the next single chapter). Swiping back left returns to the
  **James 4 + 5 combined page** — *not* James 5 alone. The portion is one atomic page, so back-swipe is always
  consistent. ✓
- **"Same for Psalm 119?"** → In Phase 1 (current data) Psalm 119 is one whole chapter, so its portion page is just
  Psalm 119 (all 176 verses). Swipe right → Psalm 120, left → Psalm 118, back → Psalm 119. Same consistency rule.
  The "only the included verses" behaviour is Phase 2 (§6).

## 5. Context switching (the state machine) — one decision for the owner

- **Schedule reading tap** → Reading context (portion-anchored), Bible tab, open on the portion page.
- **Book/chapter picker** (jump to a chapter) → Browse context at that chapter.
- **Within a context**, swiping/state is preserved (incl. the portion page).

**OPEN DECISION OQ-A — what happens when the user taps the Bible *tab* while a Reading context is active?**
The owner said the Bible tab "should be single chapter by chapter swiping." Two readings:
- **(Recommended) Tab nav = Browse.** Tapping the Bible tab always means "browse single chapters," starting at the
  current/last-read chapter. Simple, matches the owner's words literally. Cost: a reading session is left by tapping
  the tab (re-enter it by tapping the reading again). 
- **(Alternative) Tab preserves state.** Tapping the Bible tab just shows the current reader (Reading or Browse,
  whatever you were in); you only get plain single-chapter browse via the picker or a fresh entry. Cost: "tap the
  tab" doesn't always mean single-chapter, slightly contradicting the owner's phrasing.

Recommend the first; flag for confirmation. (Either way, the Reading context itself is internally consistent — this
only governs the boundary between the two contexts.)

## 6. Verse ranges (Psalm 119) — Phase 2, needs a plan-data change

To show "only the included verses," the plan must *have* verse ranges. Today it doesn't, and only Psalm 119 needs
them. Phase 2:

1. **Extend the plan schema** with an optional verse range on a `ref`: `{book, chapter, verseStart?, verseEnd?}`
   (absent = whole chapter, so all 1,094 other readings are untouched and re-verify identically).
2. **Populate Psalm 119's four days** (Mar 9–12) with the Bible Companion's canonical verse divisions for the
   176-verse psalm, sourced and second-source-verified under the **Sprint-1 data-gate discipline** (this is trusted
   IP data — it gets the same gate + reconciliation log treatment, and an audit confirming Psalm 119 is the only
   in-chapter split). This is the real cost of Phase 2 and the reason it's separate: it's a *trusted-data* task, not
   a UI task.
3. **Reader filtering:** the portion page renders only the verses in range (the reader already addresses verses by
   `verse_id`; `VerseRange`/`GetPortionTextUseCase` extend from chapter spans to verse spans cleanly). The verse-0
   superscription and `nativeLabel` rules are unaffected.

Phase 2 is small in UI terms and entirely about getting the verse data right. **Recommendation: ship Phase 1
(multi-chapter combined page) first** — it delivers the owner's main ask for all 319 multi-chapter readings — and
schedule Phase 2 (Psalm 119 verse data) as a focused trusted-data follow-up.

## 7. Scope, risks, decisions

- **Reuses, doesn't reinvent:** `GetPortionTextUseCase` (Sprint B) is the portion renderer; `GlobalChapterIndex` is
  the flank model; the existing reading-tap handoff (`ReaderHandoff`, D-D-1) already delivers the `Portion` — today
  it's reduced to the first chapter (D-H-7); Phase 1 stops reducing it and builds the `ReadingPagerIndex` instead.
- **Supersedes D-H-7** (reading-tap lands on first chapter) for the Reading context.
- **Per-verse external tap-out (Sprint H)** works unchanged inside the portion page — every verse keeps its
  canonical id.
- **Device-pass items:** the combined-page scroll feel for 4–5-chapter portions; the swipe-out-and-back consistency
  on glass; the context-switch behaviour (OQ-A) once decided.
- **Decisions needing the owner:** OQ-A (tab-vs-reading context switch, §5); confirmation to phase it (ship
  multi-chapter first, verse-range/Psalm-119 as a data follow-up).
