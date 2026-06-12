# Sprint 0015 — MySword provider (install-detected) + inline stats

**Status: GOAL MET.** Closed 2026-06-11. (Owner-redirected again from `v2-release-prep`,
which rolls forward. CLI sub-agent dispatch still down on credentials — EM executed tickets
directly under per-ticket verification discipline. Working tree handed over **uncommitted**;
version deliberately untouched at 1.2.0/10200 — tag v1.2.0 was mid-release on the new
tag-to-Play pipeline and the owner decides the next cut.)

## Goal outcome

**Met.** A reading tap can now open the MySword app at the right chapter when it's
installed — with an honest, install-detected Settings UX and a guaranteed BLB browser
fallback if the app disappears — and the year's progress (streaks, year %, per-stream)
lives directly on the main screen under the readings, with a Settings switch to hide the
streak rows. The stats icon and pushed Stats route are gone.

## Current capability

- **Settings → "Open readings in" → MySword:** disabled with "MySword (app not installed)"
  when absent (the S14 teaser idiom — discoverable, never a dead tap); selectable when
  installed. Selection persists as a real `BibleProvider.MYSWORD`.
- **Tap behavior with MySword chosen:** explicit-component intent
  `com.riversoft.android.mysword/.MySwordLink` with
  `https://mysword.info/b?r={bookNumber}.{chapter}`; `ActivityNotFoundException` → Custom
  Tab on the BLB URL (never the mysword.info stub). The stored choice is not rewritten —
  reinstalling restores it; meanwhile every tap behaves like BLB.
- **Main screen:** below the day pager, a divider then the S11 stat groups — current
  streak, longest streak, year progress, per-stream progress — rendered ONCE (year-level
  stats are identical for every paged day), live via Room invalidation. The panel is
  height-capped at 45% of the screen and internally scrollable; readings always keep the
  majority. The guilt-ban (no red, no missed-day copy) holds and stays pinned.
- **Settings → Stats → "Show streaks"** (on by default): off hides only the two streak
  rows; year + stream progress remain. Display-only — derivation untouched.
- Verified: **304/304 tests** (net +19; the 7-test Sprint 1 plan gate untouched at 7/7),
  full pipeline green (`spotlessCheck lintDebug assembleDebug testDebugUnitTest
  koverXmlReportAppDebug koverVerifyAppDebug`), **Kover 95.8%** on domain/data (floor 70%).
  **5 mutations killed**, each by exactly its intended test, restored in place:
  (1) book-number off-by-one → MySword URL pins fail; (2) MySword resolves to Web/no
  fallback → use-case + ViewModel destination tests fail; (3) streak gate ignored →
  StatsContentTest + pager panel test fail; (4) MySword menu item enabled when absent →
  disabled-and-silent test fails; (5) show-streaks default flipped → repository default
  test fails.

## Decisions & rationale (do not relitigate)

- **D-S15-1 — MySword URL = numeric vendor form** `r={bookNumber}.{chapter}`.
  Research (mysword-bible.info "Link or open MySword from other apps"): documented forms
  are `Psa_37_3[-6]` and numeric `19.37.3-6`; **no abbreviation list is published**, and
  the mysword.info stub does not echo the parsed reference, so abbreviations for
  numbered/multi-word books (1Sa? 2Jn? Son?) would be 66 unverifiable guesses. The numeric
  example pins 19 = Psalms = standard canon order = our `Book.order`, leaving zero guesses
  and no second token table (D-S9-1 by construction). Hand-pinned 66-row table in
  `MySwordTokenCatalogTest`. **Documented fallback if the device pass fails** on
  chapter-only numeric: append `.1` (verse form, `9.1.1`) or switch to the abbreviation
  form per spot-check results — one-file change in `ProviderUrlBuilder`.
- **D-S15-2 — `AppInstallChecker` seam** (`data/apps/`, D-S12-6 pattern) +
  manifest `<queries>` for `com.riversoft.android.mysword`. Checked once per Settings
  entry (ViewModel init) — an install while Settings is open shows on the next visit.
- **D-S15-3 — Fallback semantics (pinned):** `OpenReferenceUseCase` returns a
  `ReadingDestination` (`Web(url)` | `MySwordApp(url, fallbackUrl)`), fallback = BLB built
  for the same portion. Launch failure degrades at tap time in `launchReadingDestination`
  (ui/browser); the **persisted choice is never rewritten**.
- **D-S15-4 — Stats render once below the pager; STATS route removed.** Year-level stats
  are identical on every page, so the panel sits under the pager, not per page. Layout:
  the panel is measured first with `heightIn(max = 45% of available)` + internal scroll;
  the weighted pager gets all the rest (≥55%) — graceful at any screen size/font scale.
  Routes.STATS, StatsRoute/StatsScreen/StatsViewModel, `ic_stats.xml`, and the top-bar
  action are deleted (owner default; no deep links existed).
- **D-S15-5 — "Show streaks" switch** (`show_streaks`, default true) in a new Settings
  "Stats" section. Hides only the streak *display*; `GetReadingStatsUseCase` and the
  R-STREAK seam are untouched.

## User-visible strings — OWNER TONE SIGN-OFF NEEDED (PRD M8)

| id | string | where |
|---|---|---|
| `provider_mysword` | "MySword" | provider menu item / row value |
| `provider_mysword_not_installed` | "MySword (app not installed)" | disabled menu item when absent |
| `stats_settings_section_title` | "Stats" | Settings section header |
| `show_streaks_title` | "Show streaks" | switch row |
| `show_streaks_help` | "When off, the current and longest streak are hidden. Year and per-stream progress still show." | helper text |

Removed: `stats_title` ("Stats"), `open_stats` ("Open stats") — the route is gone.
(S12/S13/S14 string tables still await sign-off — see those handoffs.)

## Owner device pass — MySword 66-book gate (spec §3, cannot be HTTP-verified)

With MySword installed (KJV module active), spot-check via adb — each must open the right
book/chapter **in KJV** inside MySword:

```
adb shell am start -a android.intent.action.VIEW \
  -n com.riversoft.android.mysword/.MySwordLink -d "https://mysword.info/b?r=1.1"      # Genesis 1
adb shell am start -a android.intent.action.VIEW -n com.riversoft.android.mysword/.MySwordLink -d "https://mysword.info/b?r=9.1"      # 1 Samuel 1
adb shell am start -a android.intent.action.VIEW -n com.riversoft.android.mysword/.MySwordLink -d "https://mysword.info/b?r=10.24"    # 2 Samuel 24 (last ch)
adb shell am start -a android.intent.action.VIEW -n com.riversoft.android.mysword/.MySwordLink -d "https://mysword.info/b?r=19.119"   # Psalms 119
adb shell am start -a android.intent.action.VIEW -n com.riversoft.android.mysword/.MySwordLink -d "https://mysword.info/b?r=22.8"     # Song of Solomon 8
adb shell am start -a android.intent.action.VIEW -n com.riversoft.android.mysword/.MySwordLink -d "https://mysword.info/b?r=50.4"     # Philippians 4
adb shell am start -a android.intent.action.VIEW -n com.riversoft.android.mysword/.MySwordLink -d "https://mysword.info/b?r=57.1"     # Philemon
adb shell am start -a android.intent.action.VIEW -n com.riversoft.android.mysword/.MySwordLink -d "https://mysword.info/b?r=63.1"     # 2 John
adb shell am start -a android.intent.action.VIEW -n com.riversoft.android.mysword/.MySwordLink -d "https://mysword.info/b?r=64.1"     # 3 John
adb shell am start -a android.intent.action.VIEW -n com.riversoft.android.mysword/.MySwordLink -d "https://mysword.info/b?r=65.1"     # Jude
adb shell am start -a android.intent.action.VIEW -n com.riversoft.android.mysword/.MySwordLink -d "https://mysword.info/b?r=66.22"    # Revelation 22 (last ch)
```

Also in-app: choose MySword in Settings (only offered when installed), tap a reading
(opens in MySword), uninstall MySword, tap again (opens BLB in browser, not the stub),
reinstall (choice restored). Plus: stats panel look on the P7P at default/large font;
"Show streaks" off hides only the two streak rows; the S14 device-pass list still stands.

## State of the codebase

- **Domain:** `domain/model/ReadingDestination.kt` (new — also holds the MySword
  package/activity constants), `domain/model/BibleProvider.kt` (+`MYSWORD`,
  +`requiresApp`), `domain/OpenReferenceUseCase.kt` (returns `ReadingDestination`).
- **Data:** `data/apps/AppInstallChecker.kt` (new seam + PackageManager impl, bound in
  `di/RepositoryModule`), `data/reference/ProviderUrlBuilder.kt` (+`mySwordUrl`),
  `data/prefs/SettingsRepository(+Impl)` (+`showStreaks`).
- **UI:** `ui/browser/CustomTabLauncher.kt` (+`launchReadingDestination`),
  `ui/stats/StatsContent.kt` (new — `StatsPanelUiState` + the four stat groups; the old
  route files are deleted), `ui/day/DayReadingsViewModel.kt` (`openDestinationEvents`,
  `statsPanel`), `ui/day/DayReadingsScreen.kt` (BoxWithConstraints layout, no stats icon),
  `ui/navigation/AppNavHost.kt` (STATS gone), `ui/settings/*` (MySword item, Stats
  section). Manifest: `<queries>` only.
- **Tests:** `MySwordTokenCatalogTest` (new), `StatsContentTest` (replaces
  StatsScreenTest, keeps the guilt-ban), MySword/url/destination/panel/toggle additions
  across the existing suites. Tags: `provider-option-mysword`, `show-streaks-toggle`,
  `stats-panel`.
- **Version: 1.2.0 (10200), unchanged — these changes need a bump (1.3.0/10300 per
  D-S9-3) before shipping.** Nothing committed this sprint.

## Carryover & next goal

- **Next goal (Sprint 16): V2.x release prep** — version bump, the consolidated device
  pass (S9 + S12 + S13 + S14 lists + the S15 MySword gate + stats panel look), string
  tone sign-offs (S12/S13/S14/S15), closed-track rollout via the tag-to-Play pipeline.
- **Queued/deferred (unchanged):** second-wave web providers (Bible Hub,
  BibleStudyTools); Logos/Olive Tree behind the same install detection (seam now exists —
  much cheaper); toggle-from-widget; Psalm 119 verse-ranges; API 26–28 scrim check;
  TIME_SET/TIMEZONE_CHANGED receiver; deprecation housekeeping; public requests channel;
  `docs/explorations/social-shared-progress.md` if promoted.
- **Scope protected out:** stats panel collapse/expand or customization; per-day stats;
  re-adding any stats route/deep link; auto-switching the persisted provider on uninstall
  (deliberate D-S15-3 call).

## Next sprint

`next: sprint-0016-v2-release-prep`

## Open questions & risks

- **D-S15-1 is unverified on a device** — the one real risk. If chapter-only numeric
  fails, the documented fallback is a one-file change; if the numbering scheme itself were
  wrong (believed impossible given the vendor's `19.37` example), the same file fixes it.
- Owner tone sign-off pending on the S15 strings above (plus S12/S13/S14 tables).
- Stats panel aesthetics (45% cap, divider, spacing) are JVM-pinned for structure only —
  the look needs the device pass; the cap constant in `DayReadingsScreen.kt` is the single
  tuning point.
- Standing debt unchanged: Robolectric pinned `@Config(sdk = [34])`; JVM-untested
  MainActivity hooks; CLI agent credentials expired (owner: `claude /login`); CI
  unexercised until commit.
