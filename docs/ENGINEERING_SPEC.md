# Daily Readings — Engineering Requirements & Architecture Spec

> **Owner:** Diego (Tech Lead / Android Architect) · **Status:** Draft for build · **Last updated:** 2026-06-10
> **Companion docs:** [PRD.md](PRD.md) (product requirements — owns *what/why*), [SPEC.md](SPEC.md) (product/build spec), [CLAUDE.md](../CLAUDE.md) (session handoff)
>
> This doc owns **how** we build V1: architecture, stack, data design, module layout, NFRs,
> testing, and CI. It builds on — and does not contradict — the product scope in PRD.md / SPEC.md.
> Where the PRD lists a *technical* open question, I give a recommendation here, flagged
> **[NEEDS SIGN-OFF]**.

---

## 1. Purpose & scope

### 1.1 Purpose
Give the team a buildable architecture so we can scaffold the project and start Phase 1 without
re-litigating design decisions. Every major choice is justified; deferred work is named and
placed in a version.

### 1.2 V1 boundary (the thing we are actually building)
V1 is a **digital reading planner/tracker** — not a Bible reader. Concretely, V1 contains:

- A bundled, read-only **reading-plan** dataset (365 days × 3 portions; February has 28 day-entries, no Feb 29 entry), keyed by `(month, day)`.
- A **canonical book-name → Blue Letter Bible (BLB) abbreviation** table, used to build outbound URLs.
- A local **progress** store ("marked read"), keyed by **full date including year**.
- Screens: **Today**, **date picker** (month/day), **Settings** (theme only).
- A **Glance** home-screen widget showing today's three readings.
- **Tap-out to BLB** (browser/Custom Tab) for the chapter text. No scripture text is bundled or rendered in-app.

Explicitly **out of V1** (and where it lands): in-app scripture text + bundled KJV SQLite (V3);
streaks/stats (V2); reminders/notifications + AlarmManager/WorkManager (V2); accounts/sync/cloud
backup (post-V3); multi-translation (settled: KJV-only, and no text in V1 so the schema question is moot).

This document covers V1 in build detail and sketches V2/V3 seams only enough to avoid V1 rework.

---

## 2. Architecture overview

**Pattern:** single-activity Jetpack Compose app, **MVVM + unidirectional data flow (UDF)**,
**repository pattern** over data sources. State flows down as immutable `UiState`; events flow up
as method calls / lambdas. No business logic in composables; no Android framework types in the
domain layer.

```
                         ┌───────────────────────────────────────────────┐
                         │                 UI (Compose)                   │
                         │  TodayScreen · DatePickerScreen · SettingsScreen│
                         │  stateless composables, render UiState          │
                         └───────────────┬───────────────────────────────┘
                          state (Flow)   │   ▲  events (lambdas)
                                         ▼   │
                         ┌───────────────────────────────────────────────┐
                         │              ViewModels (per screen)           │
                         │  expose StateFlow<UiState>; map domain→UI       │
                         └───────────────┬───────────────────────────────┘
                                         │ calls (suspend / Flow)
                                         ▼
                         ┌───────────────────────────────────────────────┐
                         │            Domain / Use cases (light)          │
                         │  GetDayReadings · ToggleReading · ScheduleDate │
                         │  pure Kotlin, no Android imports               │
                         └───────┬───────────────────────────┬───────────┘
                                 │                            │
                                 ▼                            ▼
                  ┌──────────────────────────┐   ┌────────────────────────────┐
                  │  ReadingPlanRepository    │   │   ProgressRepository        │
                  │  (read-only schedule)     │   │   (mutable "marked read")    │
                  └──────────────┬───────────┘   └───────────────┬────────────┘
                                 ▼                                ▼
                  ┌──────────────────────────┐   ┌────────────────────────────┐
                  │  assets/reading_plan.json │   │   Room: progress.db          │
                  │  (parsed once, cached)    │   │   (reading_progress table)   │
                  └──────────────────────────┘   └────────────────────────────┘

                  ┌──────────────────────────┐   ┌────────────────────────────┐
                  │  ThemeRepository           │   │  Glance widget (separate    │
                  │  (DataStore Preferences)   │   │  entry point; reads repos)   │
                  └──────────────────────────┘   └────────────────────────────┘
```

**Key principles**
- **Offline-first / local-only.** All planner functions work with no network. The only network
  touch is the outbound BLB link (a browser hand-off, not an in-app fetch).
- **Single source of truth per concern.** Schedule = bundled asset; progress = Room; prefs = DataStore.
- **No I/O on the main thread.** Asset parse and DB access run on `Dispatchers.IO`; the UI observes `Flow`.
- **The widget is a peer consumer**, not a fork of logic — it reads the same repositories.

---

## 3. Technology stack & justification

| Concern | Choice (V1) | Why | Deliberately NOT in V1 / when it arrives |
|---|---|---|---|
| Language | **Kotlin** | Decided; first-class Android, coroutines/Flow. | — |
| UI | **Jetpack Compose + Material 3** | Decided; less boilerplate, native theming, Glance shares mental model. | — |
| Activity model | **Single-activity** | Simplest nav surface for ~3 screens; standard for Compose. | — |
| Navigation | **Navigation-Compose** | Type-safe routes, back-stack handling for Today ↔ DatePicker ↔ Settings. | — |
| DI | **Hilt** | Decided; standard, generated components, easy `@HiltViewModel` + Glance receiver injection. | — |
| Prefs (theme) | **DataStore (Preferences)** | Async, Flow-based, no SharedPreferences foot-guns; tiny key set. | — |
| **Progress store** | **Room** (single small table) | See §5.3 — chosen over DataStore. Set-membership queries, indexing by date, and a clean V2 streak-query path. | Bundled **KJV SQLite via Room** deferred to **V3**. |
| Reading-plan asset | **Bundled JSON in `assets/`**, parsed with **kotlinx.serialization** | Tiny (365 entries), human-reviewable in PRs, diff-friendly, no migration machinery. | A Room/SQLite *schedule* is unnecessary at this size; revisit only if the table grows beyond plan data. |
| Async | **Coroutines + Flow** | Standard; UDF state streams; structured concurrency. | — |
| Widget | **Glance (Compose for AppWidgets)** | Decided; shares Compose idioms; far less RemoteViews pain. | — |
| Outbound text | **Custom Tabs (`androidx.browser`)**, fallback to `ACTION_VIEW` | In-app feel, keeps user in our task; graceful fallback if no Custom-Tabs provider. | — |
| Image/icon | Vector drawables / Material icons | No raster assets needed. | — |
| Build | **Gradle (Kotlin DSL) + Version Catalog (`libs.versions.toml`)** | Centralized, reproducible deps; standard for new projects. | — |
| Tests | **JUnit4, kotlinx-coroutines-test, Turbine, Robolectric, Compose UI test, Truth/assertk** | Layered testing (§11). | — |
| Coverage | **Kover** | Decided; Kotlin-native coverage in CI. | — |
| CI | **GitHub Actions** | Decided; build + tests + Kover + lint/format gates (§12). | — |

**What we deliberately avoid in V1:** WorkManager/AlarmManager (no reminders until V2), any
networking library (no in-app fetch — we hand off to a browser), a bundled Bible dataset (V3),
analytics SDKs (see §13 open question), and multi-module Gradle (single `:app` module is right
for this size; module seams noted in §4).

---

## 4. Module / package structure

**V1 is a single Gradle module `:app`.** Multi-module is premature at this size; §4.2 names the
seams where future modules split cleanly.

### 4.1 Package layout (under `com.jpillion.dailyreadingplanner` — resolved, see §13 R4)

```
com.jpillion.dailyreadingplanner
├── DailyReadingsApp.kt                 // @HiltAndroidApp Application
├── MainActivity.kt                     // single activity; hosts NavHost
│
├── di/                                 // Hilt modules (see §9)
│   ├── AppModule.kt
│   ├── DataModule.kt
│   ├── RepositoryModule.kt
│   └── DispatcherModule.kt
│
├── core/
│   ├── date/                           // ReadingDate, Feb 29 no-readings rule (§5.4)
│   │   ├── ReadingDate.kt              //   (month, day) value type + lookup key
│   │   └── ScheduleDateResolver.kt    //   maps a LocalDate → ReadingDate; no-readings state for Feb 29
│   ├── result/AppResult.kt            // sealed success/failure for asset load
│   └── time/Clock.kt                  // injectable clock (testability; "today")
│
├── data/
│   ├── plan/
│   │   ├── ReadingPlanRepository.kt   // interface
│   │   ├── ReadingPlanRepositoryImpl.kt
│   │   ├── ReadingPlanAssetLoader.kt  // reads assets/, parses, validates, caches
│   │   └── dto/PlanDto.kt             // serialization DTOs (PlanDayDto, PortionDto)
│   ├── reference/
│   │   ├── BookCatalog.kt             // 66 books, canonical names, ordering
│   │   ├── BlbAbbreviations.kt        // book → BLB 3-letter abbr (§5.2)
│   │   └── BlbUrlBuilder.kt           // ref → https://www.blueletterbible.org/kjv/<book>/<ch>/
│   ├── progress/
│   │   ├── ProgressRepository.kt      // interface
│   │   ├── ProgressRepositoryImpl.kt
│   │   ├── ProgressDatabase.kt        // Room DB
│   │   ├── ReadingProgressDao.kt
│   │   └── ReadingProgressEntity.kt
│   └── prefs/
│       ├── ThemeRepository.kt
│       └── ThemeRepositoryImpl.kt     // DataStore-backed
│
├── domain/                             // light use cases (pure Kotlin)
│   ├── model/                          // DayReadings, Portion, Reference, Stream, ThemeMode
│   ├── GetDayReadingsUseCase.kt        // schedule + progress → DayReadings (combined Flow)
│   ├── ToggleReadingUseCase.kt
│   ├── MarkWholeDayUseCase.kt
│   └── OpenReferenceUseCase.kt         // builds BLB url (no Android side-effect here)
│
├── ui/
│   ├── theme/                          // Material3 theme, color, type, light/dark/system
│   ├── navigation/                     // routes, NavHost wiring
│   ├── today/        { TodayScreen.kt, TodayViewModel.kt, TodayUiState.kt }
│   ├── datepicker/   { DatePickerScreen.kt, DatePickerViewModel.kt, ... }
│   ├── settings/     { SettingsScreen.kt, SettingsViewModel.kt, ... }
│   └── components/                     // shared composables (ReadingRow, StreamLabel, ...)
│
└── widget/                             // Glance
    ├── TodayWidget.kt                  // GlanceAppWidget
    ├── TodayWidgetReceiver.kt          // GlanceAppWidgetReceiver (Hilt-injected)
    └── WidgetContent.kt               // composable content
```

### 4.2 Future module seams (do NOT build now)
- `:core:data-plan` (schedule + reference) and `:core:data-progress` could split out if reused.
- `:feature:text` arrives in **V3** (bundled KJV via Room) — designed to plug behind the existing
  `OpenReferenceUseCase` so V1 callers don't change (a flag swaps "open BLB" for "open in-app reader").
- `:feature:motivation` (**V2**) adds streak/stats use cases reading the *same* `reading_progress` table.

---

## 5. Data design

### 5.1 Reading-plan asset

**Decision: bundle as JSON in `assets/reading_plan.json`, parse once, cache in memory.**

**Schema decision — store chapter spans as explicit per-chapter refs, not range strings.**
The PRD/SPEC sketch shows `"refs": ["Genesis 1", "Genesis 2"]` (one entry per chapter). We keep
that shape rather than a packed `"Genesis 1–2"` string. Rationale:
- The BLB deep link needs a **(book, chapter)** pair. Per-chapter refs make URL building trivial
  and unambiguous; we never parse en-dash ranges at runtime.
- Display ranges ("Genesis 1–2") are a *formatting* concern, derived from the list at render time
  (collapse consecutive same-book chapters). One canonical data form, two presentations.
- Verification against a second source is cleaner per-chapter (exact set comparison).

**Final JSON schema** (top-level is an object with a version + array, so we can evolve safely):

```json
{
  "schemaVersion": 1,
  "source": "christadelphia.org; verified vs pricejh roberts.pdf",
  "days": [
    {
      "month": 1,
      "day": 1,
      "portions": [
        { "stream": 1, "refs": [ {"book": "Genesis", "chapter": 1}, {"book": "Genesis", "chapter": 2} ] },
        { "stream": 2, "refs": [ {"book": "Psalms",  "chapter": 1}, {"book": "Psalms",  "chapter": 2} ] },
        { "stream": 3, "refs": [ {"book": "Matthew", "chapter": 1}, {"book": "Matthew", "chapter": 2} ] }
      ]
    }
    // ... 365 entries total; February has 28 day-entries, no {"month": 2, "day": 29}
  ]
}
```

- `book` uses **canonical full names** from `BookCatalog` (e.g. "Psalms", "1 John", "Revelation").
  Abbreviation for BLB is derived via the mapping table (§5.2) — the plan asset stays human-readable.
- `stream` ∈ {1,2,3}; exactly three portions per day.

> **Alternative considered:** keep the PRD's `"refs": ["Genesis 1", ...]` string form. Rejected
> because every consumer would re-parse "BookName + chapter" out of a string. Storing the structured
> pair is the same data, parsed once at authoring time. (If the data team strongly prefers the string
> form for hand-editing, a build-time transform can expand it — but the *runtime* asset should be structured.)

**Loading / parsing / caching.** `ReadingPlanAssetLoader` reads the asset on `Dispatchers.IO` via
`context.assets.open(...)`, decodes with kotlinx.serialization into an immutable `Map<ReadingDate, List<Portion>>`,
and the repository memoizes it (lazy, single-flight). Parse cost is trivial (~365 small objects);
it happens once per process and easily meets the ≤1s cold-start budget (M2).

**Runtime validation** (fail fast in debug; defensive in release): exactly 365 days; every
`(month, day)` valid and unique; February has 28 day-entries and there is no Feb 29 entry; each day
has exactly 3 portions with streams {1,2,3}; every `book` resolves in `BookCatalog`; every `chapter`
within that book's chapter count.
Validation failures throw in debug builds (caught by the data-verification test, §11) and degrade
gracefully in release (show an error state rather than crash).

### 5.2 Book-name → BLB abbreviation mapping

**Representation:** a single source-of-truth `BookCatalog` enum/list of all **66 books** carrying
`(canonicalName, order, chapterCount, blbAbbrev)`. The BLB abbreviation lives *with* the book, so
there is one place to audit.

**URL construction.** `BlbUrlBuilder.build(book, chapter)`:
```
https://www.blueletterbible.org/kjv/{blbAbbrev}/{chapter}/
e.g. (Genesis, 1) -> https://www.blueletterbible.org/kjv/gen/1/
```
For a multi-chapter portion, the tap opens the **first** chapter's URL (PRD flow U3 step 3).

**Non-obvious abbreviations to confirm** (engineering data task, no owner sign-off needed): `Psalms`
→ `psa`, the Johannine epistles (`1 John/2 John/3 John`), Pauline epistles, and books like
`Song of Solomon`, `Philemon`, `Philippians`. We treat the full 66-row table as a **QA-gated
artifact** (M5: manual link check across all 66 books at launch). A small unit test asserts the
table has 66 entries and every abbreviation is non-empty/lowercase/3-letter where applicable.

### 5.3 Progress store

**Decision: a small Room table, NOT DataStore.** [Recommended; lightweight]

Rationale (DataStore vs Room for V1):
- Progress is a **growing set of records** keyed by `(date, stream)`, queried by date and (in V2)
  aggregated for streaks. That is relational, not a handful of scalar prefs.
- DataStore (Preferences) would force us to invent a serialization scheme (e.g. a set of
  `"2026-01-01#1"` strings) and hand-roll set membership — exactly what a table + index does well.
- Room gives us indexing by date, straightforward `Flow` observation, and a **zero-rework path to
  V2** streak/stats queries (count by date range, gaps). The cost is one tiny DB and Room deps —
  acceptable; Room is already on the V3 roadmap, so the team learns it once.
- DataStore *is* still used — for **theme prefs** (§5.5), which genuinely are scalar prefs.

**Schema** (`reading_progress`):

| Column | Type | Notes |
|---|---|---|
| `dateEpochDay` | INTEGER (PK part) | `LocalDate.toEpochDay()` — full date incl. year; sorts/queries cheaply. |
| `stream` | INTEGER (PK part) | 1, 2, or 3. |
| `readAtEpochMillis` | INTEGER | When marked; supports later "recently read"/audit; nullable not needed (row exists ⇒ read). |

Primary key = `(dateEpochDay, stream)`. **Presence of a row means "read."** Unmarking deletes the
row (keeps the table sparse — only completed readings are stored). Index on `dateEpochDay`.

> Storing `dateEpochDay` (not a `(month, day)` key) is what satisfies FR-6 / U7: marks are tied to
> the *actual* calendar date including year, so 1 Jan 2026 ≠ 1 Jan 2027.

**Derivations:**
- *Reading marked?* — row exists for `(dateEpochDay, stream)`.
- *Whole-day "mark all"* — upsert (or delete) all three `(date, 1..3)` rows in one transaction.
- *Day complete?* — `COUNT(*) WHERE dateEpochDay = ? == 3` (exposed as a derived `Boolean` in the
  combined `Flow`, computed in the use case, not stored).

The repository exposes `Flow<Set<Int>>` (streams read) for a given date, combined with the schedule
in `GetDayReadingsUseCase` to produce `DayReadings` (refs + per-reading read flags + dayComplete).

### 5.4 Date model & Feb 29

**`(month, day)` lookup.** `ScheduleDateResolver` maps the device's **local** `LocalDate`
(via an injectable `Clock`) to a `ReadingDate(month, day)`, then the repo looks it up in the cached
map. No time zones/servers/accounts — the device's local date is the single input (PRD §11). The
date picker selects a `(month, day)` directly; "today" uses `Clock.now().toLocalDate()`.

**Feb 29 — no scheduled readings (RESOLVED).** The reading plan covers the **365 standard calendar
days**; February has **28 day-entries** and there is **no Feb 29 entry in the plan data**. Feb 29
only ever occurs in leap years, and when it does it carries **no readings**:

- **The rule:** in leap years, when the user views/lands on Feb 29, the app shows the date with the
  fixed message **"No scheduled readings for Feb 29th"** — no reading rows, no mark controls, and no
  progress tracked for that day. In non-leap years Feb 29 never occurs.
- **No fold logic, no "double day", no synthetic progress key.** Feb 29 is a **resolver/UI
  special-case**, not a data entry. `ScheduleDateResolver` returns a "no-readings" state for
  `(month = 2, day = 29)`; every other date resolves to a `ReadingDate` with exactly 3 portions.
- *Progress impact:* none — nothing is tracked for Feb 29, so there is no Feb 29 progress key.

This keeps date-anchoring intact (everyone worldwide stays in sync on the 365 scheduled days). The
plan asset is simpler (no leap-day entry), `GetDayReadingsUseCase` consumes whatever the resolver
returns (a normal day's 3 portions, or the no-readings state for Feb 29), and `DayReadings` carries
exactly the day's portions (0 for Feb 29, otherwise 3).

### 5.5 Theme prefs
`ThemeRepository` over **DataStore Preferences**: a single key `theme_mode` ∈ {LIGHT, DARK, SYSTEM},
exposed as `Flow<ThemeMode>`; default `SYSTEM`. (Genuinely scalar config — the right DataStore use.)

---

## 6. Presentation layer

**Pattern:** one `@HiltViewModel` per screen, exposing a single **immutable** `StateFlow<UiState>`
(`stateIn(viewModelScope, WhileSubscribed(5_000), initial)`). Composables are stateless, render
`UiState`, and raise events via lambdas. No domain/Android types leak into composables.

### 6.1 Screens

- **TodayScreen (landing, FR-1).** Renders today's `DayReadings`: three readings grouped/labeled by
  stream (Law & History / Psalms & Prophecy / New Testament), each as a formatted reference
  ("Genesis 1–2"). Each row: tap label → open BLB (FR-4); checkbox/toggle → mark read (FR-2). A
  **"Mark whole day done"** action toggles all three (FR-3) in ≤2 taps (M3). Completion state is
  visually indicated (FR-11). No setup precedes this screen (M2). A button opens the date picker.
- **DatePickerScreen (FR-5).** Month/day calendar (year hidden/irrelevant). Selecting a date shows
  that date's readings (same schedule source as Today). Includes **"jump to today"** (FR-12). When
  viewing a non-today date, marking still writes progress for that *actual* date (its year resolved
  from context — for past/future months we use the current year's occurrence; cross-year viewing is
  out of V1 since the picker is month/day only).
- **SettingsScreen (FR-9).** Theme selector (light/dark/system); persists via DataStore.

### 6.2 UI state modeling (sketch)

```kotlin
data class TodayUiState(
    val isLoading: Boolean = true,
    val dateLabel: String = "",
    val readings: List<ReadingRowUi> = emptyList(),   // 3 on a normal day; 0 + noReadingsMessage on Feb 29
    val dayComplete: Boolean = false,
    val noReadingsMessage: String? = null,            // e.g. "No scheduled readings for Feb 29th"
    val error: TodayError? = null,
)
data class ReadingRowUi(
    val streamLabel: String,
    val displayRef: String,      // "Genesis 1–2" (range-collapsed for display)
    val firstChapterUrl: String, // prebuilt BLB url for the tap
    val isRead: Boolean,
)
```

State is produced by combining the schedule `Flow` and progress `Flow` in the use case; the
ViewModel maps domain → UI and formats display ranges. **Range collapsing** (consecutive same-book
chapters → "Genesis 1–2"; book-name edge cases like Psalms/epistles, FR-13) is a pure formatter,
unit-tested.

### 6.3 Navigation
**Navigation-Compose**, single `NavHost` in `MainActivity`. Routes: `today` (start),
`date_picker`, `settings`. The widget and any deep links resolve to `today`. Back stack: Today is
root; DatePicker/Settings are pushed.

---

## 7. Widget architecture (Glance)

- **Type:** `GlanceAppWidget` + `GlanceAppWidgetReceiver` (Hilt-injected via
  `@AndroidEntryPoint`-style entry point for Glance, using `EntryPointAccessors` since Glance
  receivers aren't directly Hilt-injectable).
- **Data source:** the **same** `ReadingPlanRepository` + `ProgressRepository` (no duplicated
  logic). The widget computes "today" via the injected `Clock`, resolves the `ReadingDate`
  (including the Feb 29 no-readings rule), and renders the three references (or the no-readings note on Feb 29).
- **Update strategy / cadence:** Glance widgets are not real-time. Strategy:
  1. **On date rollover** — the dominant case. Since V1 has no background scheduler, we update
     opportunistically: refresh the widget whenever the app resumes/marks progress (call
     `TodayWidget().updateAll(context)`), and rely on the system's periodic widget update
     (`android:updatePeriodMillis`, min ~30 min) as a backstop. A precise midnight refresh would
     need an alarm — that's a **V2** concern (when AlarmManager/WorkManager land for reminders).
  2. **On progress change** — when readings are toggled in-app, push an `updateAll` so the widget's
     completion state stays consistent.
- **Tap → deep link:** tapping the widget launches `MainActivity` (Today screen) via an
  `actionStartActivity` Intent. V1 only needs "open the app" (FR-8); we still route through a
  deep-link-style intent so V2 can target specific dates without rework.
- **Read-only:** V1 widget does not toggle progress (keeps it simple and avoids Glance action
  complexity); marking happens in-app. (Toggle-from-widget is a candidate V2 enhancement.)

---

## 8. External integration: opening Blue Letter Bible

- **Primary:** **Chrome Custom Tabs** (`androidx.browser`) — opens the BLB chapter URL in an
  in-app browser tab (keeps the user in our task, faster return to mark read). Built URL from
  `BlbUrlBuilder` (§5.2).
- **Fallback:** if no Custom-Tabs-capable browser is available, fall back to a plain
  `Intent(ACTION_VIEW, uri)`; if *that* also resolves to nothing, surface a non-fatal message.
- **The side-effect lives in the UI/Activity layer**, not the domain. `OpenReferenceUseCase` only
  *builds* the URL (pure, testable); the screen performs the launch. This keeps the domain free of
  Android `Context`.
- **Offline behavior:** tapping a link with no network is allowed — we hand off to the browser,
  which shows its own offline page. We do **not** pre-check connectivity (the planner itself never
  needs the network; only the browser hand-off does, PRD FR-10). Optionally, a lightweight
  connectivity check could show a "you're offline — open anyway?" hint; **deferred** as polish, not
  required for V1.
- **BLB URL-scheme risk** is real (PRD §11 / §13): isolating URL construction in `BlbUrlBuilder`
  means a scheme change (or a fallback source, or the V3 in-app reader) is a one-file swap.

---

## 9. Dependency injection (Hilt)

`@HiltAndroidApp` on `DailyReadingsApp`. Modules (`@InstallIn(SingletonComponent::class)` unless noted):

- **DispatcherModule** — provides `@IoDispatcher` / `@DefaultDispatcher` `CoroutineDispatcher`
  qualifiers (keeps dispatchers injectable for tests).
- **AppModule** — `Clock` (injectable, swappable in tests), `ApplicationContext`-derived helpers.
- **DataModule** — Room `ProgressDatabase` + `ReadingProgressDao`; DataStore `<Preferences>`
  instance; `ReadingPlanAssetLoader`; `BookCatalog` / `BlbAbbreviations`.
- **RepositoryModule** — `@Binds` the repository **interfaces** to their `Impl`s
  (`ReadingPlanRepository`, `ProgressRepository`, `ThemeRepository`).
- ViewModels use `@HiltViewModel` (no module needed). Glance receiver obtains deps via an
  `EntryPoint` interface.

Binding interfaces (not impls) keeps the domain/UI testable with fakes and keeps the V3 text swap clean.

---

## 10. Cross-cutting concerns / NFRs

- **Offline-first (FR-10).** Every planner path (Today, picker, mark, widget, theme) works with no
  network. Only the BLB hand-off needs connectivity, and that's the browser's concern.
- **Performance / cold start (M2 ≤ ~1s).** Today must render without a setup screen. Asset parse +
  Room read happen off the main thread; the first frame can show a light loading state that resolves
  in well under a second for ~365 small objects. **No I/O on the main thread** (enforced via
  StrictMode in debug). Repository memoization avoids re-parsing the asset.
- **Accessibility** (general-audience baseline, PRD non-goal of *deep* low-vision tuning, but "don't
  break a11y"): respect system **font scaling** (use `sp`, no fixed text sizes), meaningful
  **content descriptions** on toggles/links/widget, **touch targets ≥ 48dp**, sufficient contrast
  via Material 3 color roles. We won't add a custom text-size setting in V1 (candidate item) but we
  won't fight the platform's scaling either.
- **Theming.** Material 3, light/dark/system driven by `ThemeMode` (DataStore). Dynamic color
  (Material You) **optional**; recommend honoring it on Android 12+ with a static fallback palette —
  cheap and on-brand. [Minor decision, tech-lead call.]
- **Min SDK (RESOLVED — `minSdk = 26`).** `minSdk = 26` (Android 8.0), `targetSdk` = latest stable,
  `compileSdk` = latest. Rationale: `minSdk 26` gives `java.time` (LocalDate/Clock — central to our
  date model) without desugaring complexity, modern notification channels for V2, and covers ~95%+ of
  active devices as of 2026 while dropping legacy baggage. (If broader reach were ever required we
  could drop to `minSdk 24` + **core library desugaring** for `java.time`, but `minSdk 26` is the
  accepted decision.)

---

## 11. Testing strategy

Layered, fast-first. Coverage tracked by **Kover**; a sensible floor enforced in CI (start ~70% on
domain/data, ratchet up — don't gate on UI coverage).

- **Unit (JVM, no Android)** — the bulk:
  - `ScheduleDateResolver` incl. **Feb 29 no-readings rule** (leap years return the no-readings state; non-leap years never hit Feb 29; all other dates resolve to a 3-portion `ReadingDate`).
  - `BlbUrlBuilder` + `BlbAbbreviations` (all 66 books present; spot-check non-obvious abbreviations).
  - Range-collapsing display formatter (single chapter, spans, book-name edge cases — FR-13).
  - Use cases (`GetDayReadings`, `Toggle`, `MarkWholeDay`) with **fake repositories**.
  - ViewModels with `kotlinx-coroutines-test` + **Turbine** asserting `UiState` emissions.
- **Plan-data verification test — REQUIRED RELEASE GATE (FR-7 / M1).** An automated test that loads
  `reading_plan.json` and asserts it **matches a second independent source** (a checked-in
  derived/fixture file extracted from a different source than the primary). Asserts: 365 days, no
  Feb 29 entry and February has 28 day-entries, correct per-month day counts, 3 valid portions/day,
  all books resolve, all chapters in range, **and** day-by-day equality vs the second source. **CI
  fails the build if this fails.** This is the single most
  important test in the suite — the product's credibility (PRD §10 "trustworthy data") rides on it.
- **Room tests (Robolectric or instrumented)** — DAO upsert/delete, day-complete count, year
  isolation (1 Jan 2026 ≠ 1 Jan 2027 — FR-6/U7).
- **Compose UI tests** — Today renders 3 readings, toggle updates state, "mark whole day" marks all
  three (M3 ≤2 taps), completion indicator (FR-11), navigation to picker/settings.
- **Manual QA owns (not automatable cheaply):** the **all-66-books BLB link check** (M5 — open each
  book's URL, confirm correct chapter loads), widget add/refresh on a real launcher, theme switching,
  and accessibility smoke (TalkBack pass on Today). QA signs off M5 (0 known broken books at launch).

---

## 12. CI/CD (GitHub Actions, high level)

A single `ci.yml` on PR + push to `main`:
1. **Setup** — JDK 17, Gradle cache.
2. **Format/lint gates** — **ktlint/spotless** (format) and **Android Lint** (`lintDebug`); fail on
   violations. Keeps the codebase template-clean so others follow obvious patterns.
3. **Build** — `assembleDebug`.
4. **Unit tests** — `testDebugUnitTest` (includes the **plan-data verification gate**, §11).
5. **Coverage** — **Kover** report; enforce coverage floor; upload report artifact.
6. **(Optional) Robolectric/Room tests** in the same job.

Instrumented Compose/widget tests can run on a later/optional matrix (emulator) job — kept off the
fast PR path initially. **Signing/release** (Play vs sideload) depends on the distribution decision
(PRD §12 Q5) — out of scope until decided; CI is build+test+quality only for now.

---

## 13. Risks & open technical decisions

| # | Item | Status / recommendation | Owner |
|---|---|---|---|
| R1 | **Feb 29 handling** (§5.4) | **RESOLVED — low risk.** Feb 29 has **no scheduled readings**: the plan covers 365 days (February = 28 day-entries, no Feb 29 entry); in leap years the resolver returns a no-readings state and the UI shows "No scheduled readings for Feb 29th". No fold/double-day/synthetic-key logic; resolver/UI special-case only. | Product + data |
| R2 | **BLB URL-scheme stability** (§8) | Real external risk. Mitigated by isolating `BlbUrlBuilder`; fallback source / V3 in-app reader are the contingency. Add the 66-book link check to launch QA (M5). | Tech lead + QA |
| R3 | **Min SDK / target devices** (§10) | **RESOLVED — `minSdk 26`** (clean `java.time`, ~95%+ device coverage); targetSdk/compileSdk = latest stable. | Tech lead |
| R4 | **Final app name + package id** (§4.1) | **RESOLVED** — app name = **"Daily Reading Planner"**; package id = **`com.jpillion.dailyreadingplanner`** (changeable before first Play publish). | Product |
| R5 | **Book-abbreviation table correctness** (§5.2) | Data-QA gated; confirm non-obvious (Psalms, Johannine/Pauline epistles, Song of Solomon, Philemon/Philippians). | Product / data |
| R6 | **Plan JSON authored form** (§5.1) | Recommend structured `{book, chapter}` runtime form; if data team prefers string form for editing, add a build-time expansion step. | Tech lead + data |
| R7 | **Widget midnight refresh** (§7) | V1 relies on opportunistic + periodic refresh (no exact midnight alarm). Precise rollover deferred to V2 when AlarmManager/WorkManager arrive. | Tech lead |
| R8 | **Analytics/telemetry & privacy** (PRD §12 Q6) | **RESOLVED** — **no analytics SDK in V1** and no networking dependency added. Privacy-respecting opt-in may be revisited post-V1. | Product + tech lead |
| R9 | **Distribution (Play vs sideload)** (PRD §12 Q5) | **RESOLVED** — **Play Store** (best update reach for shipping plan-data corrections). Drives signing/release CI in Sprint 8; does not block scaffolding. | Product + EM |
| R10 | **Multi-translation** | Settled: KJV-only, no text in V1 → no schema impact. Re-opens only at V3. | — (settled) |

---

### Appendix A — V1 dependency set (Version Catalog sketch)

Kotlin, Coroutines, Compose BOM + Material3, Navigation-Compose, Lifecycle/ViewModel-Compose,
Hilt (+ hilt-navigation-compose), Room (runtime/ktx/compiler), DataStore-preferences,
kotlinx-serialization-json, androidx.browser (Custom Tabs), Glance (glance-appwidget).
Test: JUnit4, kotlinx-coroutines-test, Turbine, Truth/assertk, Robolectric, Compose-ui-test, Kover.
*(Minimum set that does the job — nothing speculative.)*
