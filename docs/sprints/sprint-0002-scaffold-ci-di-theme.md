# Sprint 0002 — Scaffold + CI + DI + theme

**Status: GOAL MET.** Closed 2026-06-10. All changes left UNCOMMITTED on the working tree for
the main session to verify and commit (per session protocol). Sprint 1 commits remain the
last commits on `main`.

## Goal outcome

An installable, themed, Hilt-wired empty Daily Reading Planner app exists and is proven on a
live emulator, and the whole quality pipeline runs green in one command:

```
./gradlew spotlessCheck lintDebug assembleDebug testDebugUnitTest koverXmlReportAppDebug koverVerifyAppDebug
```

The Sprint 1 plan-data release gate now runs inside `:app` under `testDebugUnitTest` (7/7),
guarding the exact `reading_plan.json` bundled in the APK. The standalone `verification/`
module is retired (deleted).

## Current capability

- **An engineer can build and install a real app** (`./gradlew assembleDebug` →
  `app/build/outputs/apk/debug/app-debug.apk`). Verified on the `Medium_Phone_API_36.1` AVD:
  launches to a themed screen showing app name + today's date, light AND dark mode (screenshots
  reviewed), zero StrictMode violations, zero crashes. APK badging verified:
  `com.jpillion.dailyreadingplanner`, minSdk 26, targetSdk/compileSdk 37, versionName 0.1.0.
- **Hilt resolves real dependencies at runtime** — `MainActivity` injects `java.time.Clock`
  (from `AppModule`) and renders today's date from it. `DispatcherModule`
  (`@IoDispatcher`/`@DefaultDispatcher`), `DataModule`, `RepositoryModule` stubs are wired and
  compile through KSP + Hilt aggregation.
- **The data gate travels with the app:** mutation-tested in `:app` (edited the asset → gate
  red with the exact mismatch; restored → green). The asset dir is declared a test input
  (Sprint 1 UP-TO-DATE lesson carried over) via `testOptions.unitTests.all` in
  `app/build.gradle.kts`.
- **Coverage floor is real, not vacuous:** Kover measures the debug variant through a created
  report variant `appDebug`; rule = 70% line floor on `domain.*`/`data.*` only (UI excluded).
  Adversarially verified: widening the filter to all classes fails the build at 10.9%.
- **Quality gates run locally and are authored for CI:** `.github/workflows/ci.yml` (JDK 17,
  gradle/actions/setup-gradle cache, spotless → lint → assemble → test → kover, report
  artifacts). **CI status: authored + locally validated, NOT yet demonstrated green on GitHub**
  because the workflow file is uncommitted (no-commit protocol). It will run on the first push
  after the main session commits.
- **GitHub remote exists:** private repo `https://github.com/jpillion/daily-readings`
  (gh user `jpillion`), `main` pushed and tracking.

## Decisions & rationale (do not relitigate)

- **AGP 9.2.1 + Gradle 9.5.1 (wrapper) + built-in Kotlin.** Started on AGP 8.13.2, but
  current-stable androidx (core-ktx 1.19.0 etc.) requires AGP 9.1+/compileSdk 37. AGP 9 makes
  built-in Kotlin the default; the standalone `org.jetbrains.kotlin.android` plugin is
  incompatible with the new DSL, so we use built-in Kotlin (no kotlin-android plugin in
  `app/build.gradle.kts`; `kotlin {}`/compose/serialization plugins still applied, versions in
  the catalog: kotlin 2.3.21, KSP 2.3.9, Hilt 2.59.2, Compose BOM 2026.05.01, Kover 0.9.8 —
  0.9.1 cannot see AGP 9 variants).
- **compileSdk = targetSdk = 37** (current stable; AGP 8.13 max is 36 which is why AGP 9 was
  required). minSdk 26 per D4.
- **D8 resolved: dynamic color ON for API 31+** with a static green fallback palette
  (`ui/theme/Color.kt`); typography = M3 defaults (16sp body, sp-based → font scaling works).
- **Data artifacts MOVED, not copied** (Diego): canonical plan = `app/src/main/assets/reading_plan.json`;
  fixture + catalog = `app/src/test/resources/`. `tools/extract_*.py` re-pointed to the new
  paths; `docs/data/README.md` updated. No dual copies to drift; `data/` is gone.
- **Gate test ported to JUnit4 + Truth** (app test stack per Appendix A; was JUnit5) and now
  exercises the production DTOs `data/plan/dto/PlanDto.kt` + `PlanJson` (strict
  `ignoreUnknownKeys = false`) — which is also what makes the Kover domain/data floor
  non-vacuous this sprint.
- **ktlint via Spotless** with `.editorconfig`
  (`ktlint_function_naming_ignore_when_annotated_with = Composable, Preview`, max line 120).
- **No commit / no CLAUDE.md edits** in-sprint: main session owns both.

## State of the codebase

- Single `:app` module; root `settings.gradle.kts`, `build.gradle.kts` (spotless lives here),
  `gradle/libs.versions.toml` (Appendix A set, nothing speculative), wrapper 9.5.1.
- Package layout per ESpec §4.1 under `app/src/main/kotlin/com/jpillion/dailyreadingplanner/`;
  future-sprint package dirs exist with `.gitkeep` (core/, data/, domain/, ui/, widget/).
- Entry points: `DailyReadingsApp.kt` (`@HiltAndroidApp`, StrictMode in debug — S2-T7),
  `MainActivity.kt` (`@AndroidEntryPoint`, edge-to-edge, injects Clock),
  `ui/navigation/AppNavHost.kt` (routes object; `today` route renders `TodayPlaceholder` —
  Sprint 4 replaces it), `ui/theme/` (Color/Type/Shape/Theme).
- Gate: `app/src/test/kotlin/com/jpillion/dailyreadingplanner/data/plan/ReadingPlanVerificationTest.kt`
  reads the plan via the `planAssetsDir` system property (set in `app/build.gradle.kts`),
  fixtures from test resources/classpath.
- `local.properties` (gitignored) points at `~/Library/Android/sdk`; `gradle.properties` has
  configuration-cache + build cache on.
- Local AVD `Medium_Phone_API_36.1` available for smoke tests
  (`emulator -avd Medium_Phone_API_36.1 -no-window ...`).

## Carryover & next goal

- **Next goal (Sprint 3 per EXECUTION_PLAN §3):** data + domain layer — plan loader
  (asset → cached map), `ScheduleDateResolver` (Feb 29 no-readings rule), Room progress store
  (year-isolated), `BlbUrlBuilder`, theme repo, use cases; all behind interfaces, unit-tested.
  `DataModule`/`RepositoryModule` stubs and the DTOs/`PlanJson` are ready seams.
- **Notes for Sprint 3:** a portion can span two books (Jun 19 / Dec 19 = 2 John + 3 John) —
  no one-book-per-portion assumptions; BLB tap opens the FIRST ref. `book_catalog.csv` lives in
  test resources — Sprint 3's `BookCatalog`/`BlbAbbreviations` production code should become the
  runtime source of truth, with a test reconciling it against the CSV. Kover floor will start
  biting as real domain/data code lands (currently 100% on DTOs).
- **Queued/deferred:** demonstrate CI green on GitHub right after the main session pushes
  (one-click follow-up, Jordan); Kotlin 2.4.0 / future AGP bumps as routine maintenance
  tickets; verse-range field for Psalm 119 (post-V1); release signing (Sprint 8).
- **Scope protected out this sprint:** no real Today screen (Sprint 4), no Room schema, no
  widget, no settings — placeholders/stubs only.

## Next sprint

`next: sprint-0003-data-domain-layer`

## Open questions & risks

- CI has not yet executed on GitHub Actions infrastructure (workflow uncommitted by design).
  Expected risk: low — exact same Gradle invocations pass locally on JDK 17; ubuntu runners
  must auto-provision platform 37 (standard).
- AGP 9 built-in Kotlin is new; if a future tool (e.g. a compiler-plugin) misbehaves, the
  fallback `android.newDsl=false` exists but is deprecated — prefer fixing forward.
- Emulator AVD is API 36.1 — dynamic color path (API 31+) is what the screenshots verified;
  the static fallback palette below API 31 is code-reviewed but not visually verified
  (needs an API 26–30 image; cheap candidate for Sprint 8 a11y/QA pass).
