# Feature: reader verse selection + verse action menu

Owner-requested, queued. Sprint id: `sprint-00Q-reader-verse-selection`.

**Scheduling:** touches `bible/ui/reader/ReaderScreen.kt` and `ReaderViewModel.kt` — the same files as
`sprint-00P-reading-card-segments`. **Start only after 00P has landed and been committed.**

## The problem

Today a short tap on a verse in the in-app reader immediately fires the external Bible app
(Sprint H). That makes the tap gesture destructive of reading flow, and it means **there is no way
to copy a verse** — the only thing a tap can do is eject you from the reading. The owner wants to
copy text he is reading, and wants a place to hang future per-verse features (cross-references
first).

## Ticket 1 — long-press to select verses

- **Long-press** a verse → enters **selection mode** with that verse selected.
- While in selection mode a **short tap toggles** selection on any verse (select a second, a third,
  or deselect). This is the standard Android multi-select idiom.
- A **contextual action bar** replaces the reader top bar while selecting: selected-verse count, a
  **Copy** action, and a close (X) affordance.
- **Exit** selection via X, system back, or deselecting the last verse.
- **D-Q-3 — selection scope is the current page.** A page is one chapter (Browse) or one whole
  portion (Reading, incl. multi-chapter and the two-book Jun 19 / Dec 19 portion), so multi-chapter
  selection works naturally inside a portion. Swiping to another page **clears** the selection —
  predictable, and avoids an invisible off-screen selection. Cross-page selection is out of scope.

## Ticket 2 — short tap opens a verse action menu

A short tap (when **not** in selection mode) opens a small menu anchored at the verse. Owner-chosen
contents:

1. **Open in `<external app>`** — the existing Sprint-H behaviour, now behind one deliberate step.
   Reuses `OpenVerseUseCase` **unchanged**, including the MySword-app / BLB-fallback path.

   > **Corrected 2026-07-25 (this line was wrong as originally written).** An earlier draft said
   > this follows **D-H-4** ("when the reading destination is IN_APP the verse tap-out resolves to
   > BLB"). **D-H-4 is retired** — it was superseded by **D-23-1** in Sprint K. `OpenVerseUseCase`
   > resolves from the chosen **external app alone** and never reads `ReadingDestinationMode`; an
   > in-app-mode user keeps their remembered external app (BLB by default), and the persisted
   > choice is never rewritten. `grep -rn "D-H-4" app/src/` returns nothing. Do not re-introduce
   > the retired shim from this document.
2. **Copy this verse** — copies just that verse, without needing the long-press flow.
3. **Select verses** — enters selection mode with that verse selected (a discoverable alternative to
   long-press, and the TalkBack-reachable path).

Deliberately **not** included: Share (owner declined). The menu is built to grow — **cross-references
is the named next item** and must be a one-entry addition, not a restructure.

## D-Q-1 — clipboard format (owner-chosen: text first, reference at end)

```
In the beginning God created the heaven and the earth. And the earth was without form, and void...

— Genesis 1:1–2 (KJV)
```

Rules:

- Verse text is the **stripped** text (`MarkupStripper.strip`) — never raw `<a>`/`<w>`/`<l/>` markup.
- Multiple verses join in canonical order, separated by a single space, as running prose.
- The trailing reference collapses contiguous verses to a range; non-contiguous selections join with
  commas (`Genesis 1:1–2,5`); a selection spanning chapters groups by chapter
  (`Genesis 1:1–2; 2:3`).
- **The book name MUST come from `ReadingFormatter.singularizeBookName`** so D-UI-2 holds: a single
  Psalms chapter reads "Psalm 23:1", multi-chapter reads "Psalms". One home, no drift — do not
  re-derive the singular/plural rule here.
- The translation code comes from the existing `BibleTextSource.translations()` seam (Sprint 00N),
  not a hardcoded "KJV".
- **Superscriptions (verse 0):** a Psalm title has no verse number. Selected alone its reference is
  the chapter alone (`Psalm 3`); selected with verses it contributes its text but no verse number to
  the range.
- Range dash: en dash, matching the app's existing reference rendering. **Flag for owner sign-off** —
  a plain hyphen is the more common convention in pasted citations.

## Strings that MUST change (both currently lie once tap stops opening the app)

- **Reader footer hint** — currently "Tap a verse to open it on Blue Letter Bible"
  (`reader_verse_tap_hint_*`). Tap now opens a menu. Needs rewording; the per-app preposition mapping
  in `ui/day/DayContent.kt` is its single home (D-K-HINT-1) — keep it there.
- **Verse spoken label** — currently "Open `<Book> <ch>:<verse>`. `<text>`" (Sprint H). Tap no longer
  opens directly, so the label must stop promising that.

Both need owner tone sign-off.

## Accessibility (a first-class requirement, not a follow-up)

- **Long-press is not reliably reachable under TalkBack.** Provide an `onLongClickLabel` **and** the
  "Select verses" menu item as an equivalent path, so selection is reachable without the gesture.
- Selected verses must carry **`selected` semantics / a state description** — never colour alone.
- The contextual action bar and the selection count must be announced on entering selection mode and
  as the count changes.
- ≥48dp targets preserved for verses, menu items, and action-bar controls (`AccessibilityGateTest`
  must be extended to cover the menu and the action bar).

## Gates

- Pure, JVM-testable **clipboard-format builder** — the primary mutation target. Pin: single verse;
  contiguous range; non-contiguous commas; cross-chapter grouping; the Psalms singular rule
  delegating to `singularizeBookName`; superscription-alone and superscription-with-verses; markup
  stripped.
- Selection-state reducer pinned pure: long-press enters with one selected; tap toggles; deselecting
  the last exits; a page change clears.
- Menu wiring pins: "Open in X" resolves through the **unchanged** `OpenVerseUseCase` (incl. the
  IN_APP→BLB fallback and MySword path); "Copy this verse" produces the same string as a
  one-verse selection.
- **The five data/Room gates must remain UNTOUCHED:** BC plan 11, M'Cheyne 10, Chronological 8,
  `BibleTextVerificationTest` 18, `BibleDatabaseRoomOpenTest` 5.
- Mutation-verify the load-bearing branches.

## Out of scope

- Cross-references (this sprint only creates the menu slot for it).
- Share; highlighting, notes, or any persisted per-verse annotation.
- Cross-page / cross-portion selection.
- Version bump / release (main session).

## Device-pass items (not JVM-provable)

Long-press timing and feel on glass; menu anchor position near the top and bottom edges of a
chapter; selection highlight contrast in light and dark; the clipboard result actually pasting
correctly into a notes/messages app.
