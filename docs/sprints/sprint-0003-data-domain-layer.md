# Sprint 0003 — Data + domain layer

**Status: GOAL MET.** Closed 2026-06-10. All changes left UNCOMMITTED on the working tree for
the main session to verify and commit (per session protocol). Sprint 2 commit (`26014ba`) is
the last commit on `main`.

## Goal outcome

The engine works: given any `LocalDate`, the app can return that day's verified readings,
each reading's read/unread state, and its BLB URL — all behind Hilt-bound interfaces, with
no UI. Proven by 45/45 unit tests (38 new + the untouched 7-test Sprint 1 release gate) and
the full quality pipeline green in one command:

```
./gradlew spotlessCheck lintDebug assembleDebug testDebugUnitTest koverXmlReportAppDebug koverVerifyAppDebug
```

## Current capability

- **Resolve any date:** `ScheduleDateResolver.resolve(LocalDate)` → sealed `ResolvedDate`:
  `Scheduled(ReadingDate)` or `NoScheduledReadings` (Feb 29 only, decision D1). Whole-year
  sweeps tested for leap (2028) and non-leap (2026) years. `ReadingDate` cannot even
  represent Feb 29 (init check).
- **Serve the verified plan from memory:** `ReadingPlanRepository.portionsFor(ReadingDate)`
  returns the day's 3 `Portion`s as domain models (resolved `Book` refs). Asset parsed +
  validated once per process (single-flight mutex; proven read-exactly-once across 20
  concurrent lookups). Multi-book portions work: Jun 19 / Dec 19 NT portion = 2 John 1 +
  3 John 1 in ONE portion — never assume refs share a book.
- **Track progress keyed by full date:** `ProgressRepository` over Room (`progress.db`,
  table `reading_progress`, PK = `(dateEpochDay, stream)`, row-presence = read). Year
  isolation proven: 1 Jan 2026 marks ≠ 1 Jan 2027 (Robolectric, real Room). Whole-day
  mark/unmark is one atomic upsert/delete.
- **Build BLB URLs for all 66 books:** production `BookCatalog` (object, 66 `Book`s) +
  `BlbUrlBuilder` → `https://www.blueletterbible.org/kjv/<abbrev>/<chapter>/`. Catalog is
  pinned field-by-field to the link-verified Sprint 1 CSV by `BookCatalogTest` (no drift).
- **Persist theme:** `ThemeRepository` over DataStore; default SYSTEM; unknown stored value
  degrades to SYSTEM.
- **Use cases ready for Sprint 4 ViewModels:** `GetDayReadingsUseCase(date): Flow<DayReadings>`
  (sealed: `Scheduled(readings, dayComplete)` / `NoScheduledReadings`), `ToggleReadingUseCase`,
  `MarkWholeDayUseCase`, `OpenReferenceUseCase(portion): String` (URL only — the Custom-Tab
  launch is Sprint 4 UI work). All injectable; app assembles with the full Hilt graph.
- **Adversarially verified (Riley):** mutation 1 (delete the Feb-29 resolver branch) → 3
  tests fail; mutation 2 (year-blind progress key) → year-isolation test fails. Restored →
  green. Kover line coverage on the domain/data filter: **93.9% (278/296)** vs the 70% floor.

## Decisions & rationale (do not relitigate)

- **Feb 29 is a sealed-type state**, never an empty list: `ResolvedDate.NoScheduledReadings`
  and `DayReadings.NoScheduledReadings`. Progress is never queried for it (tested).
- **Domain `Reference` carries a resolved `Book`** (catalog entry, not a string): unknown
  book names fail at plan-load time; `Reference` init range-checks the chapter.
- **Loader validation throws** (schemaVersion, 365 days, unique keys, streams exactly 1,2,3,
  catalog resolution): the asset is gate-verified at build time, so runtime invalidity is a
  build defect. Graceful release degradation is Sprint 4 UI error-state work if wanted.
- **`exportSchema = false`** on `ProgressDatabase` (v1, no migrations yet). Revisit with the
  Room Gradle plugin when the V2 streak schema forces a migration. Logged as debt.
- **Kover excludes generated DI code** (`annotatedBy("dagger.internal.DaggerGenerated",
  "javax.annotation.processing.Generated")`) so the floor measures code we wrote; Room's
  generated DAO/DB impls fall under `@Generated` too but ARE exercised by the Robolectric
  tests regardless.
- **Robolectric tests pin `@Config(sdk = [34])`** (Robolectric 4.15.1 doesn't support 37).
- **`PlanJsonSource` fun interface** abstracts the asset read so the loader is JVM-testable
  against the real `reading_plan.json` (same `planAssetsDir` mechanism as the release gate).

## State of the codebase

- New code (all under `app/src/main/kotlin/com/jpillion/dailyreadingplanner/`):
  `core/date/` (ReadingDate, ScheduleDateResolver), `data/plan/` (PlanJsonSource,
  ReadingPlanAssetLoader, ReadingPlanRepository(+Impl)), `data/reference/` (BookCatalog,
  BlbUrlBuilder), `data/progress/` (entity/DAO/DB, ProgressRepository(+Impl)), `data/prefs/`
  (ThemeRepository(+Impl)), `domain/model/` (Stream, Reference, Portion, DayReadings,
  ReadingStatus, ThemeMode), `domain/` (4 use cases).
- DI: `di/DataModule.kt` provides Room DB/DAO, DataStore (`settings` store), PlanJsonSource;
  `di/RepositoryModule.kt` `@Binds` the three repositories. `AppModule` Clock feeds
  `readAtEpochMillis`.
- Tests mirror the package layout under `app/src/test/kotlin/...`; shared fakes in
  `domain/Fakes.kt` (FakeReadingPlanRepository, FakeProgressRepository, `portion()` helper).
- Conventions: sealed interfaces for day states; repositories expose `Flow` for observed
  state and `suspend` for writes; `Stream` enum carries the wire number (1/2/3).

## Carryover & next goal

- **Next goal (Sprint 4 per EXECUTION_PLAN §3):** Today screen + mark-as-read + tap-to-BLB —
  TodayViewModel over `GetDayReadingsUseCase`, per-reading + whole-day toggles (≤2 taps, M3),
  Custom Tabs launch (Avery), range-collapse display formatter ("Genesis 1–2"; multi-book
  "2 John 1; 3 John 1" — formatter is Priya's, FR-13), completion indicator (FR-11),
  Compose UI tests (§5.2 Sprint 4 gate).
- **Queued/deferred:** demonstrate CI green on GitHub after main session pushes (Jordan,
  one-click); `exportSchema`/Room Gradle plugin with V2 schema work; API 26–30 static-palette
  visual check (Sprint 8); 66-book live link check (Sprint 8, G-LINKS); verse-range for
  Psalm 119 (post-V1).
- **Scope protected out this sprint:** no ViewModels/screens, no Custom-Tab side-effect, no
  widget, no display formatter (it's presentation, Sprint 4), no streak schema.

## Next sprint

`next: sprint-0004-today-screen`

## Open questions & risks

- `GetDayReadingsUseCase` calls `planRepository.portionsFor` inside the progress `Flow.map` —
  fine (memoized map hit after first load), but first collection does the asset parse on the
  collector's dispatcher via the loader's IO dispatcher. No issue observed; StrictMode will
  confirm in Sprint 4 when a ViewModel collects it.
- DatePicker year semantics (ESpec §6.1: non-today dates write progress for the current
  year's occurrence) will need a small decision note in Sprint 5 when wiring the picker.
- Robolectric pinned to sdk 34 — bump when Robolectric supports 37.
