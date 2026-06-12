# Sprint 0018 — Stats tightening + streak clarity (owner feedback on v1.3.2)

**Status: GOAL MET.** Closed 2026-06-11. (Owner-redirected again from `v2-release-prep`,
which rolls forward to Sprint 19. CLI sub-agent dispatch still down — EM executed tickets
directly under per-ticket verification discipline. Working tree handed over **uncommitted**
by request; version deliberately untouched at **1.3.2/10302**, no tags touched — these
changes need a bump to ship.)

## Goal outcome

**Met (JVM-provable parts).** On a Pixel 7 Pro-class screen (~411x915dp) at default font,
the readings and the FULL stats panel now fit one screen with no internal panel scroll —
the panel's intrinsic height dropped from ~400dp to ~290dp (streaks shown), well under the
S15 45% cap (~360dp) that previously forced the scroll. Streaks are opt-in (default off,
stored choices preserved), the Settings toggle explains exactly what a streak is, and the
year strips render adjacent same-state days as one continuous edge-to-edge band. The
literal on-device look (continuity at ~1dp segments, density feel) is the device-pass item.

## Current capability

- **One-screen stats (S18-1):** the stats panel no longer scrolls at default font on the
  P7P class. "This year" label + percent + count share ONE row; the "By stream" header is
  gone (stream names self-describe; absence pinned); outer insets 12→10dp vertical and
  24→16dp horizontal (now matching the readings column); inter-group gaps 12→10dp; streak
  values titleLarge→titleMedium.
- **Streaks opt-in (S18-2):** `showStreaks` DataStore default flipped true→false. A user
  who ever toggled the switch keeps their stored value — pinned by a stored-true-survives
  test; the old S15 default-mutation test now pins false.
- **Streak explainer (S18-3):** helper text under "Show streaks" states the D-S11-2 rule
  (verified in `GetReadingStatsUseCase`/`DayCompletionClassifier` before writing copy),
  pinned LITERALLY in `SettingsScreenTest` for tone sign-off.
- **Continuous strips (S18-4):** `YearStrip` coalesces consecutive same-state days into
  single rects (`coalesceRuns` — pure, internal): no per-day hairline seams, ~10x fewer
  draw calls; first run starts at exactly 0, last run ends at exactly `size.width`.
- Verified: **328/328 tests** (net +7: 5 `YearStripRunsTest`, 1 literal explainer pin,
  1 stored-true-survives-the-flip pin; 7-test Sprint 1 gate untouched at 7/7), full
  pipeline green (`spotlessCheck lintDebug assembleDebug testDebugUnitTest
  koverXmlReportAppDebug koverVerifyAppDebug`), **Kover 96.1%** on domain/data (floor
  70%). **4 mutations killed, each by its intended test, restored in place:** (1) default
  flipped back to true → both default tests fail; (2) coalescing made non-maximal (one
  run per day) → uniform-year/coalesce tests fail; (3) "By stream" header reintroduced →
  absence pin fails; (4) "all three" dropped from the explainer → literal pin fails.

## Vertical budget — before/after (P7P class, default font, M3 line heights, dp)

| Stats panel item | Before | After |
|---|---|---|
| Outer vertical insets | 24 (2x12) | 20 (2x10) |
| Inter-group gaps | 48 (4x12) | 30 (3x10) |
| Streak rows (x2, when shown) | 56 (titleLarge 28) | 48 (titleMedium 24) |
| Year group | 98 (label 24 + 8 + headline 36 + 8 + strips 22) | 58 (merged row 28 + 8 + strips 22) |
| Stream group | 174 (header 24 + 12 + 3x38 + 2x12) | 134 (3x38 + 2x10) |
| **Panel total, streaks ON** | **400** (> ~360 cap → scrolled) | **290** (no scroll) |
| **Panel total, streaks OFF (new default)** | 320 | **222** |

Whole screen (~828dp content under the top bar): readings ~324 + divider 1 + panel 290
(streaks on) = ~615dp → fits with slack; streaks off ≈ 547dp. Readings keep the visual
majority; the 45% cap stays as the large-font/small-screen safety net.

## User-visible strings — OWNER TONE SIGN-OFF NEEDED (PRD M8)

| id | string | change |
|---|---|---|
| `show_streaks_help` | "A streak counts consecutive days with all three readings done. Today doesn’t end a streak until the day is over, and days before your tracking start date don’t count against you. When this is off, streaks stay hidden—year and stream progress still show." | REPLACED (was the S15 hide-only wording) |
| `stats_stream_section` ("By stream") | — | REMOVED |

(S12–S17 string tables still await sign-off — see those handoffs.)

## Decisions & rationale (do not relitigate)

- **D-S18-1 — Streaks are opt-in (supersedes the D-S15-5 default).** Owner decision; the
  display gate itself is unchanged, only the absent-key default flipped. No migration:
  DataStore naturally preserves explicit choices.
- **D-S18-2 — Density over structure.** The one-screen fix tightens the panel's intrinsic
  height under the existing S15 45% cap rather than restructuring the pager/panel column;
  the cap is retained as the degradation path. Section labels merged/removed, not shrunk
  to unreadable sizes — "deliberate density, not squeeze" (owner, S14).
- **D-S18-3 — Strip continuity via run-coalescing,** not floor-snapped per-day rects:
  same-color adjacency becomes literally one rect (no seam is possible), and the partition
  invariants (start 0, shared edges, counts sum, maximality) are pure-function-testable.

## State of the codebase (delta)

- `ui/stats/StatsContent.kt` — tightened layout (see table); tags unchanged
  (`stats-year`, `stats-stream-N`, `strip-*`, streak tags).
- `ui/stats/YearStrip.kt` — `StripRun` + `internal fun coalesceRuns()`; draw loop now
  per-run with exact 0/`size.width` outer edges.
- `data/prefs/SettingsRepositoryImpl.kt` + interface KDoc — default flip.
- `app/src/main/res/values/strings.xml` — `show_streaks_help` replaced;
  `stats_stream_section` removed.
- Tests: new `ui/stats/YearStripRunsTest.kt`; adapted `SettingsRepositoryImplTest`,
  `SettingsViewModelTest`, `SettingsScreenTest` (+ literal explainer pin),
  `StatsContentTest` ("By stream" absence), `DayReadingsViewModelTest`,
  `DayReadingsPagerScreenTest`; `testing/FakeSettingsRepository` default mirrors prod.

## Carryover & next goal

- **Next goal (Sprint 19): V2.x release prep** — version bump past 1.3.2/10302, the
  consolidated device pass (S9 + S12 + S13 + S14 + S15 MySword gate + S16 one-screen fit
  + S17 strip look + **S18: strip continuity on glass, panel density feel, streaks-off
  default on a fresh install**), string tone sign-offs (S12–S18), closed-track rollout
  via the tag-to-Play pipeline.
- **Queued/deferred (unchanged from S17):** colorblind-friendly strip palette; second-wave
  web providers; Logos/Olive Tree install detection; toggle-from-widget; Psalm 119
  verse-ranges; API 26–28 scrim check; TIME_SET/TIMEZONE_CHANGED receiver; deprecation
  housekeeping (`createComposeRule` v2); public requests channel; Priya's S16 notes.
- **Scope protected out:** restructuring the pager/stats column (cap mechanism kept);
  raising/removing the 45% cap; strip touch interaction; any widget changes.

## Next sprint

`next: sprint-0019-v2x-release-prep`

## Open questions & risks

- The edge-to-edge right bound of the LAST run (`size.width`) and on-glass continuity are
  drawing behavior — not JVM-provable; top of the device pass. `coalesceRuns` itself is
  fully pinned.
- One-screen fit is layout math + the budget table above, not device-verified (same caveat
  as S15/S16).
- Owner tone sign-off pending on the new `show_streaks_help` plus the S12–S17 tables.
- Standing debt unchanged: Robolectric pinned `@Config(sdk = [34])`; JVM-untested
  MainActivity hooks; CLI agent credentials expired (owner: `claude /login`); CI
  unexercised until commit; D-S15-1 MySword numeric form still device-unverified.
