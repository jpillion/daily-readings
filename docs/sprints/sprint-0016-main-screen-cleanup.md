# Sprint 0016 — Main-screen space cleanup (owner feedback)

**Status: GOAL MET.** Closed 2026-06-12. (Owner-redirected again from `v2-release-prep`,
which rolls forward to Sprint 17. CLI sub-agent dispatch still down — EM executed tickets
directly under per-ticket verification discipline. Working tree handed over **uncommitted**;
version deliberately untouched at **1.3.0/10300** — these changes need a bump to ship.)

## Goal outcome

**Met (JVM-provable parts).** The main screen sheds the two-line heading, the
"n of 3 readings done" progress line, and the dead gap above the whole-day button, so the
readings + button + stats panel fit one screen at default font. The literal "fits the
owner's Pixel 7 Pro without scrolling" claim is a device-pass item (one line below).

Owner's words: "trying to clean it up, simplify it a bit, so it can all fit on a single
screen" — plus an explicit design review, which Priya performed (verdict below).

## Current capability

- **Single-line title (D-S16-1):** the top bar shows exactly one line. Today:
  **"Today – June 10"** (en dash, month + day — today's year is never ambiguous). Any
  other day: just the date, **"Friday, June 13"** — no "Readings" word; the year is
  appended **only** when the displayed year differs from today's (the Dec 31 → Jan 1
  swipe, D-S5-3 — pinned by test). `maxLines = 1` + ellipsis is a hard one-line guarantee.
- **No progress line (D-S16-2):** the "0 of 3 readings done" count is gone at every
  state. **"All readings done" stays** — it costs zero vertical space in the not-done
  state (the owner's screenshot case) and preserves the completion confirmation; it now
  renders *below* the button as a badge.
- **Button pulled up:** the whole-day button sits directly under the third reading card —
  the extra Spacer and the count row between them are gone; the column keeps its uniform
  12dp rhythm. The button keeps `fillMaxWidth` and its 48dp bounds
  (`AccessibilityGateTest` unchanged and green).
- Verified: **304/304 tests** (count unchanged — no coverage weakened: the two
  progress-line pins became explicit *absence* pins, the heading pins became **literal**
  single-line-title pins incl. the cross-year case — literal by rule, after a first
  attempt that computed expectations via the production formatter survived a mutation
  and was cut), full pipeline green
  (`spotlessCheck lintDebug assembleDebug testDebugUnitTest koverXmlReportAppDebug
  koverVerifyAppDebug`), **Kover 95.8%** on domain/data (floor 70%). 7-test Sprint 1
  gate untouched at 7/7. **3 mutations killed, each by exactly its intended test,
  restored in place:** (1) year-append dropped in `formatDayDate` → both cross-year
  title tests fail; (2) today-branch removed from the title → all three today-title
  tests fail; (3) complete badge gate forced true → the partial-day absence test fails.

## Priya's design review (S16-4, owner-required) — VERDICT: APPROVED, with notes

- **Hierarchy:** readings remain unambiguously the focus — first content under the bar,
  full-width cards; the date line reads as context, not a heading; the primary button
  directly under the cards is where the thumb expects the day-level action. Good.
- **Spacing rhythm:** uniform 12dp through cards → button (the old Spacer + count row
  made an inconsistent ~40dp band); the complete badge centers under the button with the
  same rhythm. Deliberate, not squeezed.
- **Completion state:** outlined "Unmark whole day" + primary-colored "All readings
  done" badge + secondary-container cards triple-signal completion; losing the count line
  loses nothing (checkboxes ARE the count).
- **Font scale:** title ellipsis only bites at extreme combined scale (in-app 1.5x ×
  large system scale) on the longest dates ("Wednesday, September 30, 2027"); today's
  title ("Today – September 30") fits well past default. Content already scrolls
  gracefully. Acceptable degradation per the owner's brief; default-font fit looks right
  in layout math but **must be eyeballed on the P7P** (weights/45% stats cap are not
  JVM-provable — same caveat as S15).
- **TalkBack:** the title now speaks the full date in one utterance (previously
  "Today" then a separate date node — arguably better now); cards still speak stream +
  reference + open-hint; checkbox toggles speak "Mark X as read" + state, so spoken
  progress survives the removed count line; the complete badge is spoken when present.
  No regression.
- **Notes (non-blocking, queued):** consider `titleMedium` weight bump or a subtle
  color split between "Today –" and the date; consider whether the badge should ever
  replace the button instead of following it. Neither blocks.

## User-visible strings — OWNER TONE SIGN-OFF NEEDED (PRD M8)

| id | string | change |
|---|---|---|
| `title_today_date` | "Today – %1$s" → "Today – June 10" | NEW (replaces the "Today" heading) |
| `today_title` ("Today"), `readings_title` ("Readings") | — | REMOVED |
| `day_progress` ("%1$d of %2$d readings done") | — | REMOVED |

(`day_complete` "All readings done" unchanged. S12–S15 string tables still await
sign-off — see those handoffs. The en dash vs the owner's typed hyphen is a deliberate
typographic choice — flag at sign-off.)

## State of the codebase

- `ui/day/DayReadingsScreen.kt` — single-`Text` top-bar title; new internal helpers
  `formatMonthDay(date)` and `formatDayDate(date, todayYear)` (the ONLY date-title
  formatting home, test-pinned from `DayReadingsPagerScreenTest`); `FormatStyle`/
  two-line `Column` title gone.
- `ui/day/DayContent.kt` — `CompletionIndicator` → `DayCompleteBadge` (complete-only,
  after `WholeDayButton`); Spacer removed from `ScheduledContent`.
- `app/src/main/res/values/strings.xml` — see table above.
- Tests touched: `DayContentTest` (absence pins for the count line at 0/3 and 2/3 +
  button-presence pins), `DayReadingsPagerScreenTest` (exact title pins: today form,
  no-"Readings", year-only-when-different on both the swipe and picker cross-year paths).
- Nothing else moved. No version/tag changes; nothing committed.

## Carryover & next goal

- **Next goal (Sprint 17): V2.x release prep** — version bump past 1.3.0/10300, the
  consolidated device pass (S9 + S12 + S13 + S14 + S15 MySword gate + stats-panel look
  + **S16: one-screen fit on the P7P at default font; title at large font scales**),
  string tone sign-offs (S12–S16), closed-track rollout via the tag-to-Play pipeline.
- **Queued/deferred (unchanged from S15):** second-wave web providers; Logos/Olive Tree
  behind install detection; toggle-from-widget; Psalm 119 verse-ranges; API 26–28 scrim
  check; TIME_SET/TIMEZONE_CHANGED receiver; deprecation housekeeping (note: Compose
  `createComposeRule` now warns deprecated — migrate to the v2 rule when touched);
  public requests channel; Priya's non-blocking polish notes above.
- **Scope protected out:** any stats-panel shrinking/collapse (the panel was NOT touched
  — the readings column alone covered the owner's ask); top-bar restyling beyond the
  title; removing "All readings done" (deliberate D-S16-2 call).

## Next sprint

`next: sprint-0017-v2-release-prep`

## Open questions & risks

- The one-screen-fit claim is layout-math + review, not device-verified — top of the
  device pass.
- Owner tone sign-off pending on `title_today_date` (en dash) plus the S12–S15 tables.
- Standing debt unchanged: Robolectric pinned `@Config(sdk = [34])`; JVM-untested
  MainActivity hooks; CLI agent credentials expired (owner: `claude /login`); CI
  unexercised until commit; D-S15-1 MySword numeric form still device-unverified.
