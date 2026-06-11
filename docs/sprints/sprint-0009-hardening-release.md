# Sprint 0009 — Widget sizing refinement + hardening & release readiness

**Status: GOAL MET.** Closed 2026-06-10. (Commit to `main` performed by the main session per
protocol — working tree handed over uncommitted by request.)

## Goal outcome

Two-part goal, both delivered:

1. **Owner widget feedback (Part 1):** the widget now lists the **three readings at every
   size down to 1x1** — completion is never the focus. At 1x1/1x2 the references render
   abbreviated ("Gen 1–2", "Psa 1–2", "Mat 1–2"; the Jun 19/Dec 19 two-book day is
   "2Jo 1; 3Jo 1") with per-reading read/unread marks; the old "n/3" SMALL layout is gone.
   1x1 resize is enabled (`minResizeHeight=40dp`), a widget-picker preview image exists,
   and Feb 29 / load-failure states, the single tap target, and full-canonical-name
   TalkBack descriptions hold at all sizes.
2. **Release readiness (Part 2):** `./gradlew bundleRelease` produces an R8-minified,
   resource-shrunk `.aab` end to end (debug-signed until the owner provisions the upload
   key — zero build-script changes to swap the real key in), CI builds the release bundle
   on every push, the Room schema is exported and checked in, and the JVM-provable slice
   of the a11y gate (48dp touch targets, picker-grid semantics, slider range info) is
   pinned as tests. What remains for V1 ship is **owner-only work** (checklists below).

Proven by **160/160 tests** (13 net new; the 7-test Sprint 1 plan gate untouched and
passing), **3 mutations killed** (clean in-place reruns), full pipeline green incl. forced
rerun, **Kover 95.2%** on domain/data (floor 70%):

```
./gradlew spotlessCheck lintDebug assembleDebug testDebugUnitTest koverXmlReportAppDebug koverVerifyAppDebug
./gradlew bundleRelease   # -> app/build/outputs/bundle/release/app-release.aab
```

## Decisions & rationale (do not relitigate)

- **D-S9-1 — Display abbreviations are *derived* from the BLB URL tokens.**
  `Book.displayAbbrev` = the live-verified 3-char BLB abbrev with its first *letter*
  uppercased ("gen"→"Gen", "2jo"→"2Jo"). No second hand-authored 66-row table — the BLB
  tokens are the only dataset that is live-verified and test-pinned to the Sprint 1 CSV,
  and a parallel table would reintroduce drift risk. Accepted quirks (pinned in tests):
  "Jhn", "Sng", "Rth", "Phl", "Jde". A11y always speaks full canonical names. If the owner
  dislikes a form on device, a targeted override map is a trivial follow-up.
- **D-S9-2 — Readings listed at every widget size; 4th breakpoint.** `WidgetLayout` gains
  `TINY` (1x1, `57x48dp`); the chooser is width-first, then *height* splits SMALL (1x2,
  date header + abbreviated rows) from TINY (abbreviated rows only). Completion is a small
  "✓" badge beside the date (SMALL) or implicit in the marks (TINY); the "n/3" count is
  deliberately removed (owner feedback). `ReadingFormatter.formatAbbreviated` reuses the
  exact same run-collapse as `format` with abbreviated names.
- **D-S9-3 — Version scheme.** `versionCode = MAJOR*10000 + MINOR*100 + PATCH`; V1 ships as
  `versionName "1.0.0"` / `versionCode 10000`. Monotonic for Play, derivable by inspection.
- **D-S9-4 — Room schema exported now, not at V2.** `exportSchema = true` +
  `room.schemaLocation = app/schemas` (checked in:
  `app/schemas/...ProgressDatabase/1.json`) so the V2 streak migration has a pinned
  baseline to migrate *from*. Sprint 3 debt retired.
- **D-S9-5 — Release build & signing posture.** R8 (`isMinifyEnabled`) + `shrinkResources`
  ON for release; keep rules in `app/proguard-rules.pro` (libraries ship consumer rules;
  explicit defense-in-depth keeps for the @Serializable plan DTOs). Signing reads
  `keystore.properties` (gitignored) or `DRP_UPLOAD_*` env vars; **absent both, release
  debug-signs** so CI proves the R8 pipeline without any key material in the repo or CI.
  The owner generates and holds the upload key (instructions below).
  Also under this decision: the stock **M3 Slider is accepted at its 44dp handle-token
  touch height** (its semantics node is the inner handle container; outer padding cannot
  change it) — the a11y gate pins 44dp for it and **48dp for every control we author**.
- **ESpec deviations:** none new (picker-as-dialog D-S5-2 carried).

## State of the codebase

- **Widget:** `widget/WidgetContent.kt` (4 layouts, `layoutFor` now width+height),
  `widget/TodayWidget.kt` (responsive set + `TINY_SIZE`),
  `res/xml/today_widget_info.xml` (`minResizeHeight=40dp`, `previewImage`),
  `res/drawable/widget_preview.xml` (hand-authored vector sketch of the LARGE layout).
- **Formatting/data:** `data/reference/BookCatalog.kt` (`Book.displayAbbrev`),
  `ui/day/ReadingFormatter.kt` (`formatAbbreviated`, name-selector refactor).
- **Build/release:** `app/build.gradle.kts` (signingConfigs.release with properties/env
  fallback chain, release buildType minify+shrink, `ksp room.schemaLocation`, version
  1.0.0/10000), `app/proguard-rules.pro` (documented), `.gitignore` (+`keystore.properties`),
  `.github/workflows/ci.yml` (+`release-bundle` job uploading the `.aab` artifact),
  `app/schemas/` (new, checked in).
- **Tests (13 net new):** `data/reference/BookDisplayAbbrevTest.kt` (derivation + 66-book
  invariants + distinctness), `ui/day/ReadingFormatterTest.kt` (+3 abbreviated cases),
  `widget/WidgetContentSizesTest.kt` (rewritten: height-aware chooser bounds, SMALL/TINY
  list readings, two-book day, no-count assertions, states), `ui/AccessibilityGateTest.kt`
  (touch-bounds helper on `touchBoundsInRoot`; day toggles/whole-day, settings rows/reset/
  back, slider semantics, picker cells+nav+confirm/cancel, spoken full dates).
- **Mutations (all killed, in-place restore — do NOT use `git checkout` to restore during
  mutation passes; it reverts uncommitted sprint work):** displayAbbrev uppercase dropped;
  `layoutFor` SMALL/TINY height arm swapped; `formatAbbreviated` → canonical names.
- StrictMode review (JVM-provable): asset parse on injected `Dispatchers.IO`
  (`ReadingPlanAssetLoader`), Room via Flow on Room executors, DataStore async, no
  `allowMainThreadQueries`. Device logcat watch still owed (checklist below).

## Owner checklist 1 — create the upload key & first release (keep the key OUT of the repo)

1. Generate the upload keystore (anywhere safe; `*.jks` is gitignored if kept in-repo):
   `keytool -genkeypair -v -keystore upload-keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload`
2. Create `keystore.properties` at the **repo root** (gitignored):
   ```
   storeFile=upload-keystore.jks        # path relative to repo root
   storePassword=<store password>
   keyAlias=upload
   keyPassword=<key password>
   ```
   (Equivalent env vars: `DRP_UPLOAD_KEYSTORE_FILE`, `DRP_UPLOAD_STORE_PASSWORD`,
   `DRP_UPLOAD_KEY_ALIAS`, `DRP_UPLOAD_KEY_PASSWORD`.)
3. `./gradlew bundleRelease` → upload `app/build/outputs/bundle/release/app-release.aab`.
4. In Play Console enroll in **Play App Signing** (Google holds the app key; your keystore
   is only the upload key). Back up the keystore + passwords in a password manager.
5. Each later release: bump `versionName`/`versionCode` per D-S9-3, rebuild, upload.

## Owner checklist 2 — Play Store listing (Console prerequisites)

- App name "Daily Reading Planner"; package `com.jpillion.dailyreadingplanner` (final at
  first publish — unchangeable after).
- Short description (≤80 chars), full description (≤4000), category (Books & Reference
  or Lifestyle).
- Graphics: 512x512 icon, 1024x500 feature graphic, ≥2 phone screenshots (Today screen,
  date picker with indicators, widget on launcher recommended).
- Privacy policy URL (required even though the app collects nothing — a one-page static
  "no data collected" page suffices).
- Data safety form: no data collected, no data shared, no analytics (true per D — no
  networking dep). Content rating questionnaire (Everyone). Target audience (not
  child-directed). Contact email. Countries.
- Internal testing track first; promote to production after the device pass below.

## Owner checklist 3 — final device pass

- **Widget:** add at default 3x2; resize down through 2x2 → 1x2 → 1x1; verify three
  abbreviated readings legible at 1x1 on your launcher grid; Jun 19 (two-book day) fits or
  ellipsizes acceptably; picker shows the new preview image; marks update after marking
  in-app; date rolls over within 30 min of midnight (backstop).
- **Release build on device:** install from the bundle
  (`bundletool build-apks` + install, or a Play internal-test install) and smoke: plan
  loads, marks persist across restart, widget renders, BLB opens — this proves the R8
  keep rules on device, which JVM cannot.
- **TalkBack:** Today rows and widget rows speak full book names + state; picker cells
  speak "<full date>, All readings done / Readings missed"; dots legible in light/dark/
  dynamic color; text-size slider operable (volume-key value adjust) — stock M3 slider,
  44dp handle (D-S9-5); reset dialog reachable and confirm/cancel announced.
- **Font scale:** system scale × in-app slider compose sanely at extremes (0.85 in-app ×
  large system font, and 1.5 × 1.5).
- **Slider drag feel:** live preview writes DataStore per drag event — watch for jank.
- **Edge-to-edge scrim on API 26–28** (Sprint 6 debt): system-bar icon contrast after
  theme switches.
- **StrictMode:** `adb logcat -s StrictMode` (debug build) during launch, day swiping,
  settings, widget add/refresh, BLB tap — expect silence.

## Carryover & next goal

- **Next goal (Sprint 10): V1 release support** — react to device-pass findings (e.g.
  abbreviation overrides, 1x1 text fit on dense grids, scrim fix if 26–28 misbehaves),
  then tag/ship 1.0.0. If the pass is clean this sprint is small; V2 planning (streaks,
  AlarmManager reminders, in-app KJV text per roadmap) can start in the same session.
- **Queued/deferred (unchanged):** toggle-from-widget (V2), Psalm 119 verse-ranges
  (post-V1), deprecation housekeeping (`hiltViewModel` package move, `createComposeRule`
  v2), widget ignores the in-app font scale (D-S8-5, by design).
- **Scope protected out this sprint:** Glance preview *layout* (`previewLayout` API 31+
  live preview — static drawable shipped instead), per-book display-abbrev override map
  (only if the owner objects to a derived form), debouncing slider DataStore writes
  (device verdict first).

## Next sprint

`next: sprint-0010-v1-release`

## Open questions & risks

- 1x1 legibility is JVM-proven only by construction (11sp x 3 rows in ~57dp); real
  launcher grids vary per OEM — the device pass is the arbiter; fallback is bumping
  TINY's font or dropping marks at TINY.
- R8 keep rules are build-proven but not runtime-proven until the owner's release-build
  smoke (checklist 3, item 2).
- Known debt carried: Robolectric pinned `@Config(sdk = [34])`; `AppNavHost` push/pop
  untested on JVM; API 26–28 scrim unverified.
