# Sprint 21 — Date-picker UX (one-tap select + cross-year month swipe)

**Owner-redirected** from the planned `sprint-0021-v2x-release-prep` (release prep re-queues as
the next sprint). Uncommitted in the working tree by request — the main session verifies + commits.
Version untouched (1.3.5/10305). Two backlog items, both picker-only.

## Goal outcome — MET

The date picker is faster and unbounded by year:

1. **One-tap date selection (BACKLOG #7).** Tapping any day cell selects that full date and
   closes the dialog in a single tap. The separate "Go to date" confirm button is gone
   (string `date_picker_confirm` removed); Cancel/scrim-dismiss still backs out without
   selecting. Verified safe to remove: selecting a date is non-destructive — it only navigates
   the day pager to that day.
2. **Swipe the calendar across months and years (BACKLOG #6).** The months now ride a
   `HorizontalPager`; the user swipes left/right (or taps the chevrons) to move month-by-month
   freely across year boundaries — Dec 2026 → Jan 2027 forward, Jan 2026 → Dec 2025 backward.
   The chevrons are no longer disabled at January/December. Completion dots (green complete /
   red past-missed / neutral), Feb-29 handling, the today ring, and a11y carry over at every
   reachable month because they key to full dates.

## Current capability

A user opening the date picker can flick through any month in any year and tap once to jump the
readings pager there — no Jan/Dec dead-ends, no confirm tap.

## Decisions & rationale

- **D-S21-1 (supersedes the pinned-year part of D-S5-3 *for the picker only*).** The picker is
  no longer year-anchored. The `year` parameter and the `withYear()` leap-day anchoring are
  removed; the dialog opens on `initialDate` directly (the call site passes the *displayed*
  date, `currentDate`, whatever year the pager reached). The day pager and all progress logic
  are unchanged — D-S5-3's full-date progress keying still holds; only the picker's navigation
  is freed.
- **D-S21-2 (month-pager geometry).** Mirrors the day-pager idiom (D-S5-4): a window of
  `MONTH_PAGE_COUNT` (= 2*3,000+1) months centered at `MONTH_CENTER_PAGE`, with
  `monthForPage(initialMonth, page) = initialMonth.plusMonths(page - center)`. ±3,000 months
  ≈ 250 years each way — effectively unbounded for this app.
- **One-tap is non-destructive, so no confirm.** Selecting only scrolls the pager; an
  accidental tap is trivially undone by another tap or "Today". This was the explicit check
  BACKLOG #7 asked for.

## State of the codebase

- `ui/datepicker/DayDatePickerDialog.kt` — rewritten. Signature is now
  `DayDatePickerDialog(today, initialDate, completionFor, onConfirm, onDismiss)` (no `year`).
  `onConfirm` fires from the day cell's `onClick` (one tap). New internal pure helper
  `monthForPage` + constants `MONTH_WINDOW/MONTH_CENTER_PAGE/MONTH_PAGE_COUNT`. `DayCell` lost
  its `selected` param (no persistent selection state — select-and-close). Tags unchanged
  except: **`date-picker-confirm` removed**, **`picker-month-pager` added**;
  `picker-month-title`, `picker-prev-month`, `picker-next-month`, `picker-day-N`,
  `date-picker-cancel` all retained. `leadingEmptyCells`/`weekdayOrder` unchanged.
- `ui/day/DayReadingsScreen.kt` — call site updated: drops `year`, passes
  `initialDate = currentDate`, comments updated. No other change.
- `res/values/strings.xml` — `date_picker_confirm` deleted (was unused after the change).
- Tests updated: `DayDatePickerDialogTest` (one-tap, cross-year chevrons, swipe gestures,
  `monthForPage` mapping), `DayReadingsPagerScreenTest` (one-tap navigates + opens-on-displayed-
  date across year boundary), `AccessibilityGateTest` (dropped `year` arg + the
  `date-picker-confirm` touch-target assertion; Cancel target retained).

## Quality gate

Full pipeline green: `spotlessCheck lintDebug assembleDebug testDebugUnitTest
koverXmlReportAppDebug koverVerifyAppDebug`. **520/520 tests** (net +3 vs 517). The three
data/Room gates are untouched — plan **7**, BibleTextVerificationTest **18**,
BibleDatabaseRoomOpenTest **5**. Kover ≥70% floor holds (app-wide LINE 95.2%). a11y gate green.

**Mutations killed (2 load-bearing paths):**
- `monthForPage` offset zeroed (`plusMonths(0)`) → 5 tests failed (both chevron-crosses-year,
  both swipe-crosses-month, and the `monthForPage` unit test). Restored in place.
- Day-cell `onClick` no-op (don't call `onSelect`) → 5 tests failed (one-tap return, Feb-29
  one-tap, no-confirm, and both pager one-tap-navigation tests). Restored in place.

## Device-pass items (not JVM-provable)

- **Swipe feel** of the month pager on glass (fling, settle, neighbor pre-render mid-swipe).
- **One-tap accuracy** on a real 48dp grid cell — no accidental adjacent-day picks; dialog
  dismiss timing feels immediate.
- Chevron-to-pager animation smoothness across a year boundary.

## New strings for sign-off

None added. One **removed**: `date_picker_confirm` ("Go to date"). No new user-visible copy.

## Carryover & next goal

Release prep was deferred again. Next sprint picks up the queued **V2.x release prep**:
version bump past 1.3.5/10305, consolidated device pass (S9 + S12–S21 items incl. the two
above), S12–S20 string tone sign-offs (incl. the D-S20-1 "Missed"-vs-"Not read" flag),
closed-track rollout via the tag-to-Play pipeline.

**next: sprint-0022-v2x-release-prep**

## Open questions & risks

- None new. The picker's month-pager window (±250 yr) is far beyond any realistic use; no risk.
- The D-S5-3 pinned-year contract is now explicitly *picker-scoped-out* (D-S21-1) — the day
  pager and progress keying are unaffected and still test-pinned.
