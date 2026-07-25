# Feature: reading card segments + partial checks

Owner-reported, two tickets, one sprint. Sprint id: `sprint-00P-reading-card-segments`.

## Ticket 1 (P0 bug) — Chronological reading tap opens Genesis 1

**Symptom (owner, on device):** on the Chronological plan, tapping today's reading card opens
Genesis 1 instead of the reading. Owner's suspicion — "two different passages (two different
books)" — is correct.

**Root cause (confirmed).** Chronological 07/25 is a single portion:
`Isaiah 37, Isaiah 38, Isaiah 39, Psalms 76`. `ReadingPagerIndex`'s `init`
(`bible/ui/reader/ReadingPagerIndex.kt`) requires a portion's refs to be a *contiguous ascending
global-chapter run*. Psalms 76's global index is **lower** than Isaiah 37's (Psalms precedes
Isaiah in canon order), so `require(portionLastGlobal >= portionFirstGlobal)` throws.
`ReaderViewModel.enterReading` swallows it (`runCatching { … }.getOrNull() ?: return`), the reader
never leaves the `Browse` context, and it opens at its default `GENESIS_1_PAGE`.

**Blast radius:** 83 of 365 Chronological days (63 cross-book + 24 non-consecutive-chapter, 4
overlapping). Bible Companion and M'Cheyne are unaffected today because their multi-ref portions
happen to be globally adjacent (2 John 1 → 3 John 1) or single-run.

Ticket 2 fixes this structurally: every card becomes a contiguous run, so the pager index can
never be handed a non-contiguous portion.

## Ticket 2 — one card per contiguous passage

Owner's rule, verbatim: *multiple different passages on a single day's reading → each one a
different card; one book + consecutive chapters → a single card; single book but non-consecutive
chapters → different cards (e.g. Gen 3–4 and Gen 8–10 = two cards).*

### D-SEG-1 — THE segmentation rule

A **segment** (= one card) is a maximal run of refs with **the same book and consecutive ascending
chapters**. A book change splits. A chapter gap splits. A verse window does **not** split.

This is **exactly** `bible/domain/ConsecutiveChapterRuns.group` — the existing grouper already used
by `ProviderUrlBuilder` for external URLs. **Reuse it. Do not write a second grouper** (one-home
discipline: card boundaries and external-URL grouping must not drift).

Note the deliberate distinction from `ReadingFormatter`'s *private* `consecutiveRuns`, which
additionally breaks on verse windows. That stays as-is: it governs **display within** a card, a
different concern. Consequence (correct, and matching the owner's literal rule): M'Cheyne 02/28
stream 1 `Exodus 11 + Exodus 12:1–21` is **one card** (same book, consecutive chapters) whose text
reads "Exodus 11; Exodus 12:1–21". 8 such windowed-adjacent pairs exist in M'Cheyne.

### D-SEG-2 — the invariant that fixes Ticket 1

Every D-SEG-1 segment is, by construction, a contiguous ascending global-chapter run, so
`ReadingPagerIndex(segment)` can never throw.

**Verified against the real bundled assets — 0 violations across all 2,920 portions in all three
plans.** This must be pinned as a data-gate-style test (see Gates below).

Segment-count distribution over the real assets:

| plan | portions | 1 seg | 2 | 3 | 4 | 5 | 6 |
|---|---|---|---|---|---|---|---|
| bible_companion | 1095 | 1093 | 2 | – | – | – | – |
| chronological | 365 | 282 | 57 | 13 | 7 | 5 | 1 |
| mcheyne | 1460 | 1459 | 1 | – | – | – | – |

### D-SEG-8 — split at the UI layer, NOT in `GetDayReadingsUseCase`

`GetDayReadingsUseCase` feeds the day screen **and** the Glance widget **and** the reminder /
persistent-notification bodies. Splitting there would give the widget 6 rows on a Chronological day
and break its row-count tier policy.

So `DayReadings` / `ReadingStatus` stay **per-portion, unchanged** — widget, reminders, persistent
notification, stats, streaks, strips, picker dots all keep their current inputs. The segment split
happens in `DayReadingsViewModel` when it builds `DayUiState.Scheduled`, which now carries a list of
segment-level card states rendered one-per-card by `DayContent`.

## Partial checks (owner design)

### D-SEG-3 — progress storage is UNCHANGED

The Room progress DB still stores exactly **one mark per (plan, date, stream)**. Stats, streaks,
year strips, picker dots, widget, reminders and day-completion all read that and are untouched.
**No Room migration, no schema change.** A partially-read reading counts as not-read everywhere
outside the day screen — which is the intent.

### D-SEG-4 — the partial-check cache

A cosmetic cache in **DataStore** (not Room): key `partial_reading_segments`, a `Set<String>` of
tokens `"<planId>|<epochDay>|<stream>|<segmentIndex>"`.

`ReadingCheckState` = `UNCHECKED` | `PARTIAL` | `COMPLETE`. Derivation for a portion with N segments:

- stream mark **true** → every segment `COMPLETE` (the full/green check).
- stream mark **false** → segment is `PARTIAL` if its token is present, else `UNCHECKED`.
- **N == 1 → `PARTIAL` is unreachable**; behaviour is byte-for-byte today's.

### THE transition policy — one pure home

Put it in a pure `SegmentCheckPolicy` (domain), the single mutation target. Applied by a use case
that performs the I/O.

| action | result |
|---|---|
| tap `UNCHECKED`, N>1, others not all checked | write token → `PARTIAL` |
| tap `UNCHECKED` that is the **last** one needed | clear all tokens for (plan,date,stream) **and** write the real stream mark → all `COMPLETE` |
| tap `PARTIAL` | remove token → `UNCHECKED` |
| tap `COMPLETE` (stream marked read) | clear the real mark; tapped segment → `UNCHECKED`, **all others → `PARTIAL`** |
| N == 1, any tap | toggle the real mark directly; never write a token |

**Tap-to-open (Sprint 00O semantics preserved — one-way SET, never unmarks):** tapping the card
*body* opens that segment and sets it read — `UNCHECKED` → `PARTIAL`, or completes the stream if it
is the last one; `PARTIAL`/`COMPLETE` are left alone. N == 1 sets the real mark exactly as today.

### D-SEG-5 — pruning

The partial set is a cache, not a record. On write, drop tokens whose epochDay is more than 400 days
before today (inject the existing `Clock` seam into the repository impl). Bounded growth; a token is
also cleared the moment its stream completes.

### D-SEG-6 — the tapped segment is what gets opened

The tapped **segment** (a `Portion` carrying the same `streamNumber` and that run's refs) is what
flows to `OpenReferenceUseCase` → `ReaderHandoff` / `ProviderUrlBuilder`. Two consequences:

1. The in-app reader opens exactly the tapped passage — **Ticket 1 fixed**.
2. An external URL now carries just that run (Bible Gateway "Isaiah 37-39") instead of the whole
   day — consistent with what the card itself says. This is an intentional, honest change.

### D-SEG-7 — defensive: never fall back to Genesis 1

Independently of segmentation, `ReaderViewModel.enterReading`'s silent
`runCatching { … }.getOrNull() ?: return` is a latent trap. Change the fallback so that if a
`ReadingPagerIndex` ever cannot be built, the reader opens the portion's **first ref's chapter** in
Browse context rather than Genesis 1. Belt-and-braces: a future plan-data edge case degrades to the
right neighbourhood instead of the wrong end of the Bible.

## UI / a11y

- One `ReadingCard` per segment; test tags become `reading-<stream>-<segIndex>` /
  `toggle-<stream>-<segIndex>` (update `AccessibilityGateTest` and the existing day-screen tests).
- Stream title repeats on each card of a multi-segment stream (rare: 3 portions total across BC +
  M'Cheyne; Chronological is single-stream so its title is null anyway).
- **Partial check visual:** a checkmark in a distinct colour — checked, but not the completed
  colour. Keep the `COMPLETE` appearance byte-for-byte as it is today. Put the partial colour behind
  a small seam/token (the `StripColors` precedent) so a future colourblind palette is a one-place swap.
- **Never colour alone:** the checkbox carries a `stateDescription` — "not read" / "partially read" /
  "read". ≥48dp targets preserved.
- **Known tension (device-pass item):** a 5- or 6-segment Chronological day now renders 5–6 cards, so
  the readings column may scroll where it previously did not. The column already scrolls; this is the
  accepted cost of the owner's explicit request. Only 6 days of 365 exceed 4 segments.

## Gates

- **New data gate:** for every portion of every bundled plan, every D-SEG-1 segment is a contiguous
  ascending global-chapter run **and** `ReadingPagerIndex` constructs successfully for it. This is the
  test that makes Ticket 1 non-recurring.
- **Regression pin:** Chronological 07/25 → exactly 2 segments (`Isaiah 37–39`, `Psalms 76`), each
  building a `ReadingPagerIndex`, and the tap handing the *segment* to the reader.
- **Pin the merge case:** M'Cheyne 02/28 stream 1 stays ONE card.
- `SegmentCheckPolicy` pure tests for every row of the transition table, plus the N==1 parity path.
- **The five data/Room gates must remain UNTOUCHED:** BC plan 11, M'Cheyne 10, Chronological 8,
  `BibleTextVerificationTest` 18, `BibleDatabaseRoomOpenTest` 5.
- Mutation-verify the load-bearing branches per repo discipline.

## Out of scope

- Any per-segment *persistent* progress record, Room migration, or change to stats/streak/widget
  denominators (explicitly rejected by the owner as too large).
- Changing `ReadingFormatter`'s display run logic.
- Version bump / release (main session).
