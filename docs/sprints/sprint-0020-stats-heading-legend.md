# Sprint 0020 — Stats heading + strip legend (owner request)

**Status: GOAL MET.** Closed 2026-06-12. (CLI sub-agent dispatch still down — EM executed
tickets directly under per-ticket verification discipline. Working tree handed over
**uncommitted**; version deliberately untouched at **1.3.4/10304**, no tags touched — the
main session handles the release.)

## Goal outcome

**Met.** The stats panel is now self-explanatory: a compact **"Year at a glance"** heading
names what the panel shows, and a small legend at the bottom keys the strip colors — red
swatch = "Missed", green swatch = "Completed" (the owner's own words). The S18 one-screen
fit holds: the panel grows ~56dp and stays under the 45% cap (budget below).

## Current capability

- A reader landing on the main screen sees the stats panel introduced by a one-line
  labelLarge heading ("Year at a glance", `stats-heading`, `heading()` semantics) and can
  decode the year strips without guesswork via the legend row (`stats-legend`): 10dp
  rounded swatches drawn from the SAME `StripColors` seam the strips paint with (legend and
  strips can never disagree), labels in bodySmall/onSurfaceVariant. Exactly two entries —
  the owner listed only red/green; no neutral entry.
- A11y: the legend is one merged-semantics row ("Missed, Completed"), spoken after the
  strips' existing summaries; swatches are decorative and announce nothing (pinned).
- Verified: **342/342 tests** (net +2: legend literal-labels pin incl. exactly-once counts,
  swatch-silence pin; heading pinned inside `rendersAllFourStatGroups`; 7-test Sprint 1
  gate untouched at 7/7), full pipeline green (`spotlessCheck lintDebug assembleDebug
  testDebugUnitTest koverXmlReportAppDebug koverVerifyAppDebug`), **Kover 96.2%** on
  domain/data (floor 70%). **4 mutations killed, each by its intended test, restored in
  place:** (1) heading text blanked → heading pin fails (1 test); (2) "Missed" label →
  "Not read" → legend literal pin fails (1 test); (3) guilt copy reintroduced OUTSIDE the
  legend ("This year" → "Missed days") → the ban still bites (6 tests fail); (4) swatch
  given a spoken description → speech ban + swatch-silence pin fail (5 tests).

## Decisions & rationale (do not relitigate)

- **D-S20-1 — Owner amendment narrowing the no-guilt copy ban (amends D-S17-1/D-S17-3).**
  The owner explicitly wrote "missed" for the legend; the owner is the tone authority. The
  exemption is EXACTLY the two literal legend labels — "Missed" and "Completed" — in the
  legend row only. Everything else stays banned, on screen and in speech: the
  `StatsContentTest` ban-scan exempts only the exact strings `"Missed"`/`"Completed"`
  (whole-node text equality, not substring), and mutation 3 proves the ban still bites
  elsewhere. The strip spoken summaries keep "not read" (D-S17-3 unchanged). *Team note
  for tone sign-off:* "Not read" would match the summaries' vocabulary; we ship the
  owner's wording and merely flag the inconsistency.
- **Heading choice:** "Year at a glance" over "Progress view" — it describes what the
  strips literally show (the shape of the year), not generic chrome; labelLarge/
  onSurfaceVariant keeps it a label, not a competing title (S18 "deliberate density").
- **Legend swatches via `defaultStripColors()`** — no new color constants; the future
  colorblind palette swap (queued since S17) re-keys the legend automatically.

## Vertical budget — S18 table updated (P7P class ~411x915dp, default font, dp)

| Stats panel item | S18 | S20 |
|---|---|---|
| Heading row (labelLarge 20) | — | 20 |
| Legend row (swatch 10 / bodySmall 16) | — | 16 |
| Extra spacedBy(10) gaps (x2) | — | 20 |
| Everything else | 290 / 222 | unchanged |
| **Panel total, streaks ON** | 290 | **346** (< ~360 cap → still no scroll) |
| **Panel total, streaks OFF (default)** | 222 | **278** |

Whole screen: readings ~324 + divider 1 + panel 346 = **~671 of ~828dp** (streaks on);
~603 streaks off. One-screen fit holds with margin; the 45% cap remains the
large-font/small-screen safety net. (Layout math, not device-verified — same caveat as
S15–S18; eyeball on the P7P during the consolidated device pass.)

## User-visible strings — OWNER TONE SIGN-OFF NEEDED (PRD M8)

| id | string | note |
|---|---|---|
| `stats_panel_heading` | "Year at a glance" | owner offered this or "Progress view" |
| `stats_legend_missed` | "Missed" | owner's verbatim wording (D-S20-1) |
| `stats_legend_completed` | "Completed" | owner's verbatim wording (D-S20-1); team flags "Not read" as the summary-consistent alternative |

(S12–S19 string tables still await sign-off — see those handoffs.)

## State of the codebase (delta)

- `ui/stats/StatsContent.kt` — heading Text at the top of the panel column; new private
  `StripLegend()` + `LegendEntry()` at the bottom; KDoc records D-S20-1.
- `app/src/main/res/values/strings.xml` — 3 new strings (table above).
- `app/src/test/.../ui/stats/StatsContentTest.kt` — heading pin; `legend_showsExactlyThe
  TwoOwnerLabels_atTheBottom`; `legendSwatches_announceNothing_summariesStillSpeak`;
  `textContains` matcher narrowed by `legendExemptions` (exact-string, D-S20-1).
- No domain/data/widget/DataStore/Room/manifest changes.

## Carryover & next goal

- **Next goal (Sprint 21): V2.x release prep** — version bump past 1.3.4/10304, the
  consolidated device pass (S9 + S12–S19 items + **S20: heading/legend look, legend
  legibility in dark mode, one-screen fit re-check**), string tone sign-offs (S12–S20,
  incl. the D-S20-1 "Missed"-vs-"Not read" flag), closed-track rollout via the tag-to-Play
  pipeline.
- **Queued/deferred (unchanged from S18/S19):** colorblind strip palette (now also re-keys
  the legend); second-wave web providers; Logos/Olive Tree install detection;
  toggle-from-widget; Psalm 119 verse-ranges; API 26–28 scrim check; TIME_SET/
  TIMEZONE_CHANGED receiver; `createComposeRule` v2 migration; public requests channel;
  Priya's S16 notes.
- **Scope protected out:** a neutral legend entry (owner listed only red/green); legend on
  the widget; any further panel restructuring.

## Next sprint

`next: sprint-0021-v2x-release-prep`

## Open questions & risks

- Legend/heading look (swatch legibility at 10dp, dark-mode reds/greens, density feel) is
  not JVM-provable — device-pass item.
- The D-S20-1 wording inconsistency ("Missed" legend vs "not read" spoken summaries) is
  deliberate (owner verbatim) but flagged for the tone sign-off.
- Standing debt unchanged: Robolectric pinned `@Config(sdk = [34])`; JVM-untested
  MainActivity hooks; CLI agent credentials expired; CI unexercised until commit;
  D-S15-1 MySword numeric form still device-unverified.
