# Port inventory — Daily Readings, Android → Kotlin Multiplatform

> **Phase 0 deliverable.** Owner: Staff Engineer / Port Architect. This file is Staff-locked:
> other roles read it, nobody else edits it. Every downstream task brief is written from this
> document.
>
> **Status:** first pass, 2026-08-08, against `main` at `1bcc98e` (app version 1.8.1 / 10801).
> Source surveyed: 162 files in `app/src/main/kotlin/`, 115 in `app/src/test/`,
> `app/build.gradle.kts`, `gradle/libs.versions.toml`, `app/src/main/AndroidManifest.xml`.
>
> Items marked **⟦VERIFY⟧** are claims I could not settle by reading this repo. They are
> assigned, and each one must be closed by a spike before the phase that depends on it.

---

## 0. How to read this

Section 1 is the honest list of cliffs — read it before anything else. Section 2 is the target
topology every later section refers to. Section 3 is the exhaustive API-by-API inventory.
Section 5 covers the five data gates. Section 6 states plainly what dies.

**Multiplatform status vocabulary**, used consistently:

| Status | Meaning |
|---|---|
| `COMMON` | Compiles unchanged in `commonMain`; no platform type involved. |
| `KMP-LIB` | An equivalent multiplatform library exists and is named. Mechanical swap. |
| `EXPECT` | Needs an `expect`/`actual` pair, or an interface in `shared/platform` with two impls. |
| `ANDROID-ONLY` | Stays in `androidApp`. No iOS counterpart is attempted. |
| `NATIVE-IOS` | Dies as shared code; a separate SwiftUI/Swift implementation is required work. |
| `NO EQUIVALENT` | Nothing on iOS does this. The behaviour changes or is dropped. |

**Risk vocabulary:** LOW = mechanical. MEDIUM = design work, known solution. HIGH = a decision
with real consequences and a real chance of rework. **UNSOLVED** = I do not currently know a
path that preserves the current behaviour.

---

## 1. The cliffs

Ranked by how much of the project they can cost. Detail in the referenced sections.

| # | Cliff | Risk | Where | Why it hurts |
|---|---|---|---|---|
| 1 | **`Room.createFromAsset` is Android-only in Room KMP** | **UNSOLVED** (as a like-for-like) | `di/BibleModule.kt:54`, `bible/data/BibleDatabase.kt` | The entire in-app KJV reader is a 5.6 MB pre-packaged read-only SQLite asset opened via `createFromAsset`. That API, plus `createFromFile`/`createFromInputStream`/`PrepackagedDatabaseCallback`, is documented as not available in Room's common source set. There is no iOS `assets/` concept either. See §3.3. |
| 2 | **The pinned Room identity hash** | HIGH | `tools/build_bible_db.py:44` (`ROOM_IDENTITY_HASH = "8144e1bc…2552"`), `app/src/test/.../BibleDatabaseRoomOpenTest.kt` | The committed `bible.db` carries a hand-written `room_master_table` row matching the hash Room's Android annotation processor generated for `BibleDatabase` v1. Room validates it on first query. If Room-KMP's generator produces a different hash — or if the entity moves package/module — the asset is rejected at runtime with "Pre-packaged database has an invalid schema". This exact failure already shipped once (sprint-00F P0). See §3.3. |
| 3 | **`ProgressDatabase` migration integrity for live users** | HIGH | `data/progress/ProgressMigrations.kt`, `app/schemas/…/ProgressDatabase/{1,2}.json`, `di/DataModule.kt:36-41` | There are real users on Play at schema v2 with real reading history and `fallbackToDestructiveMigration` deliberately **off**. Any change to entity package, column order, or Room version that alters the generated v2 DDL/identity hash turns an app update into a crash-on-open for every existing Android user. iOS starts empty and is trivial; **Android is the fragile side of this port.** See §3.2. |
| 4 | **`java.time` → `kotlinx-datetime`, 36 files** | HIGH | 36 of 162 main files import `java.time.*` (§3.1) | Date anchoring *is* the product. `LocalDate` ×33, `Clock` ×13, `LocalTime` ×6, `YearMonth` ×4, `ZonedDateTime` ×2, plus `DateTimeFormatter`/`FormatStyle`/`WeekFields`. `kotlinx.datetime.Clock` is not a `java.time.Clock` (no `ofFixed`, different testing idiom) and `DateTimeFormatter.ofLocalized*` has **no kotlinx equivalent at all**. This touches every use case, every repository and most of the UI. |
| 5 | **Notifications: no AlarmManager, no BOOT_COMPLETED, 64-notification cap** | HIGH (reminder), **NO EQUIVALENT** (persistent tray) | `reminders/` (8 files), `AndroidManifest.xml` boot receiver | The daily reminder maps acceptably onto `UNCalendarNotificationTrigger` (repeating, survives reboot, no boot receiver needed). The **always-on ongoing tray notification** (`setOngoing(true)`, refreshed at 01:00 daily, non-dismissible, ON by default) has no iOS counterpart — iOS has no persistent non-dismissible notification and no way to run at 01:00 to refresh content. See §3.11 and ADR-0005. |
| 6 | **Glance widget** | **NO EQUIVALENT** as shared code | `widget/` (4 files, 508 lines) | Glance does not target iOS. WidgetKit is SwiftUI-only, runs in a separate process, and cannot call into a Kotlin `@HiltViewModel` graph. The `WidgetContent.kt` responsive-tier layout logic (397 lines, JVM-tested) is genuinely reusable *as logic*; the rendering is not. See §3.14 and ADR-0006. |
| 7 | **Hilt is Android-only, 23 annotation sites + 9 modules** | MEDIUM | `di/` (9 files), `@HiltViewModel`/`@AndroidEntryPoint`/`@EntryPoint` ×23 | Hilt has no KMP support and will not get one. The graph must be rebuilt — Koin is the pragmatic choice, manual construction the boring-and-safe one. This is mechanical but touches every ViewModel and every construction site. |
| 8 | **`BibleTextVerificationTest` runs on `sqlite-jdbc`** | MEDIUM | `app/src/test/.../BibleTextVerificationTest.kt:44` (`DriverManager.getConnection("jdbc:sqlite:…")`) | 18 assertions over the raw asset via JDBC — a JVM-only driver. It cannot move to `commonTest` as written. `BibleDatabaseRoomOpenTest` (Robolectric, 5) has the same problem for a different reason. See §5. |

**Two things that are much better than expected**, and they change the shape of the answer:

- **Only 22 of 162 main-source files import `android.*` at all.** `domain/` (39 files),
  `bible/domain/` (13) and `core/` (2) are already 100% free of Android types. The platform
  surface is already behind interfaces — `WidgetRefresher`, `ReminderScheduler`,
  `ReminderNotifier`, `PersistentNotifier`, `NotificationPermissionChecker`, `AppInstallChecker`,
  `BibleApiClient`, `BibleTextCache`, `BibleTextSource`, `InAppUpdateManager`,
  `BibleAssetVersionStore`, `PlanAssetSource`. That is the `shared/platform` layer, already
  designed, already named, already faked in tests.
- **The UI has zero text inputs, zero `SelectionContainer`, and zero `AndroidView`.** Verse
  selection is app-implemented over `combinedClickable` + its own `VerseSelection` model
  (`bible/ui/reader/`), not framework text selection. This dodges Compose Multiplatform's
  single weakest iOS area almost entirely. See §3.8 and ADR-0004.

---

## 2. Target module topology

```
shared/
  domain/        pure Kotlin. models, use cases, policies, formatters, classifiers.
                 ZERO java.*, ZERO android.*, ZERO Room, ZERO DataStore, ZERO Compose.
  data/          repositories + their storage impls (Room KMP, DataStore KMP, Ktor client,
                 asset readers). Depends on shared/domain and shared/platform.
  platform/      capability INTERFACES only, plus expect/actual for the small handful that
                 cannot be an interface. This is the Staff-owned boundary.
  ui/            Compose Multiplatform screens + ViewModels. Depends on domain (+ platform
                 interfaces for side effects). No Room, no DataStore types.
androidApp/      MainActivity, Application, Hilt-or-Koin Android wiring, the Glance widget,
                 AlarmManager/NotificationManager actuals, Play In-App Updates, Custom Tabs,
                 the MySword intent, the Android manifest.
iosApp/          SwiftUI entry point hosting the shared Compose root, UNUserNotificationCenter
                 actuals, UIPasteboard, SFSafariViewController, WidgetKit (separate target),
                 Info.plist.
```

**Rules that follow from this and are not negotiable:**

1. `shared/domain` and `shared/ui/commonMain` contain zero `java.*` and zero `android.*`.
2. Anything platform-conditional lives at the `expect`/`actual` boundary or behind a
   `shared/platform` interface. No `if (isIOS)` in shared code.
3. `shared/platform` interfaces are written to **semantics**, not to either platform's API
   shape. `ReminderScheduler.scheduleReminder(time: LocalTime)` is fine. Anything named after
   `AlarmManager` or `UNNotificationRequest` is not.

---

## 3. Inventory by area

### 3.1 Date and time — `java.time`

**Blast radius: 36 of 162 main files, plus ~40 test files.** This is the single largest
mechanical change in the port.

| API | Count | Call sites (representative; full list below) | Status | Destination | Risk |
|---|---|---|---|---|---|
| `java.time.LocalDate` | 33 files | `core/date/ScheduleDateResolver.kt`, `domain/*` (17 files), `data/progress/ProgressRepository.kt`, `ui/day/*`, `widget/*` | KMP-LIB → `kotlinx.datetime.LocalDate` | `shared/domain` | LOW per-site, MEDIUM in aggregate |
| `java.time.Clock` | 13 files | `di/AppModule.kt`, `domain/GetReadingStatsUseCase.kt`, `domain/GetYearStripsUseCase.kt`, `domain/DeliverDueReminderUseCase.kt`, `reminders/ReminderScheduler.kt`, `ui/day/DayReadingsViewModel.kt`, `widget/TodayWidget.kt` | KMP-LIB → `kotlin.time.Clock` + a `TimeZone` | `shared/domain` | **MEDIUM** — see note |
| `java.time.LocalTime` | 6 files | `reminders/AlarmTimes.kt`, `reminders/ReminderScheduler.kt`, `data/prefs/SettingsRepository*.kt`, `ui/settings/*` | KMP-LIB → `kotlinx.datetime.LocalTime` | `shared/domain` | LOW |
| `java.time.YearMonth` | 4 files | `domain/GetMonthCompletionUseCase.kt`, `ui/datepicker/DayDatePickerDialog.kt`, `ui/day/DayReadingsScreen.kt`, `ui/day/DayReadingsViewModel.kt` | KMP-LIB → `kotlinx.datetime.YearMonth` (**added in kotlinx-datetime 0.7.0**) | `shared/domain` | LOW–MEDIUM ⟦VERIFY⟧ |
| `java.time.ZonedDateTime` | 2 files | `reminders/AlarmTimes.kt`, `reminders/ReminderScheduler.kt` | KMP-LIB → `LocalDateTime` + `TimeZone` explicitly | `shared/domain` | LOW |
| `java.time.Instant` / `ZoneOffset` / `DayOfWeek` | 3 | `data/progress/ProgressRepositoryImpl.kt`, `domain/*` | KMP-LIB → `kotlin.time.Instant`, `kotlinx.datetime.DayOfWeek` | `shared/domain` | LOW |
| `java.time.temporal.WeekFields` | 1 | `ui/datepicker/DayDatePickerDialog.kt:114` (`WeekFields.of(locale).firstDayOfWeek`) | **EXPECT** — no kotlinx equivalent | `shared/platform` | MEDIUM |
| `java.time.format.DateTimeFormatter` + `FormatStyle` | 5 files | `ui/day/DayReadingsScreen.kt:297,307`, `ui/settings/SettingsScreen.kt:563,638`, `ui/datepicker/DayDatePickerDialog.kt:257,304`, `ui/day/TrackingStartPromptDialog.kt:72`, `widget/WidgetContent.kt:95-96` | **EXPECT** — `ofLocalizedDate/Time` has **no kotlinx equivalent** | `shared/platform` | **MEDIUM–HIGH** |
| `java.time.format.TextStyle` | 1 | `ui/datepicker/DayDatePickerDialog.kt` | EXPECT (with the above) | `shared/platform` | LOW |
| `java.text.NumberFormat` | 1 | `ui/stats/StatsContent.kt:186` (`getIntegerInstance()` — thousands separators on "n of 1,095") | EXPECT | `shared/platform` | LOW |
| `java.util.Locale` | 2 | `ui/datepicker/DayDatePickerDialog.kt`, `widget/WidgetContent.kt` | EXPECT / drop | `shared/platform` | LOW |
| `android.text.format.DateFormat.is24HourFormat` | 1 | `ui/settings/SettingsScreen.kt:605` | EXPECT | `shared/platform` | LOW |
| `java.util.UUID` | 1 | `bible/data/remote/FumsIdentity.kt:40,44` | KMP-LIB → `kotlin.uuid.Uuid` | `shared/data` | LOW |

**The `Clock` note (MEDIUM, and it is not just a rename).** `java.time.Clock` bundles an
instant source *and a zone*; `LocalDate.now(clock)` reads both. `kotlin.time.Clock` gives you an
`Instant` only — every `LocalDate.now(clock)` becomes
`clock.now().toLocalDateTime(timeZone).date`, and the zone has to come from somewhere explicit.
13 files and a large share of the test suite construct fixed clocks. **Recommendation: do not
propagate `Clock` + `TimeZone` as two parameters through 13 use cases.** Introduce one
`shared/platform` interface — `interface DateProvider { fun today(): LocalDate; fun now():
Instant; val timeZone: TimeZone }` — port the call sites onto that, and let the test fake be a
fixed `DateProvider`. That is one seam instead of two parameters, and it makes the eventual
"what happens when the user crosses a timezone" question answerable in one place.

**The localized-formatting note (MEDIUM–HIGH).** `DateTimeFormatter.ofLocalizedDate(FULL)`,
`ofLocalizedTime(SHORT)` and `ofPattern("EEEE, MMMM d")` produce user-visible strings on the
day screen title, the picker, the settings rows, the first-run prompt and the widget. kotlinx-
datetime's formatting is pattern-based and **not locale-aware**. There is no shared substitute
that respects the user's locale and 12/24-hour preference. This must become a
`shared/platform` interface (`DateTextFormatter`) with a `java.time.format` actual on Android
and an `NSDateFormatter` actual on iOS — and the two will not produce byte-identical output.
Every test that pins one of these strings literally (there are several — `DayReadingsScreenTest`
pins `"Today – June 10"`) must move to the platform side or be rewritten against the interface.
Record the divergence in `docs/parity-matrix.md`.

⟦VERIFY⟧ — assigned to **Core/Data**: confirm `kotlinx.datetime.YearMonth` exists at the
version the Build & Release Engineer selects, and that it carries `atDay`, `lengthOfMonth`,
`plusMonths`-equivalents. `GetMonthCompletionUseCase` and the custom calendar grid in
`DayDatePickerDialog` both need month arithmetic. If it is thin, a 30-line
`shared/domain/YearMonth` value class is preferable to leaking a partial library type.

### 3.2 Persistence — `ProgressDatabase` (read-write Room)

| Item | Call sites | Status | Destination | Risk |
|---|---|---|---|---|
| `@Database(version = 2, exportSchema = true)` | `data/progress/ProgressDatabase.kt` | KMP-LIB — Room supports KMP | `shared/data` | **HIGH** |
| `@Entity` `reading_progress`, PK `(plan_id, dateEpochDay, stream)` | `data/progress/ReadingProgressEntity.kt` | KMP-LIB | `shared/data` | HIGH |
| `@Dao` — 12 queries incl. grouped `readCounts`, `allReadCounts`, `streamCountsInRange`, `marksInRange`, `hasAnyMarks` | `data/progress/ReadingProgressDao.kt` | KMP-LIB | `shared/data` | MEDIUM |
| Hand-written `MIGRATION_1_2` (`SupportSQLiteDatabase`) | `data/progress/ProgressMigrations.kt` | **EXPECT-ish** — `SupportSQLiteDatabase` is the Android artifact; Room KMP migrations use `androidx.sqlite.SQLiteConnection` | `shared/data` | **HIGH** |
| Exported schemas `1.json` / `2.json`, wired as **debug-only assets** | `app/build.gradle.kts:21`, `app/schemas/` | ANDROID-ONLY mechanism | `androidApp` | MEDIUM |
| `MigrationTestHelper` zero-loss gate | `app/src/test/.../ProgressMigrationTest.kt` | ANDROID-ONLY (Robolectric + exported schema assets) | `androidApp` test | MEDIUM |
| Room builder + `addMigrations`, `fallbackToDestructiveMigration` **off** | `di/DataModule.kt:32-41` | EXPECT — the builder differs (`Room.databaseBuilder(context,…)` vs `Room.databaseBuilder<T>(path)` + a driver) | `shared/data` + `shared/platform` (db path) | HIGH |

**My explicit read on migration integrity.** This is the item most likely to cause real user
harm, and it is an **Android** risk, not an iOS one. iOS installs start at v2 with an empty
table; nothing to migrate. Android has shipped users at v2 whose database must continue to
open.

Room validates an opened database against a schema **identity hash** derived from the entity
definitions. Moving `ReadingProgressEntity` from `com.jpillion.dailyreadingplanner.data.progress`
into `shared/data` does not by itself change the hash — the hash is computed from table name,
column names/types/nullability/defaults, primary key, indices and foreign keys, none of which
are package-sensitive. But **changing the Room version, the KSP processor, or any column
attribute does.** And this project has already been bitten once by a hash mismatch on the
*other* database (sprint-00F).

Therefore the non-negotiable acceptance criterion for the persistence phase:

> **PG-1.** A `ProgressDatabase` file produced by the currently-shipped 1.8.1 Android build,
> containing marks across multiple years and multiple plans, must open on the ported Android
> build and return byte-identical query results. Prove it with a fixture database committed to
> the repo, not by reasoning about hashes. Keep `ProgressMigrationTest` and
> `ProgressMigrationNoPerceptibleChangeTest` alive on the Android side for as long as
> `MigrationTestHelper` has no common equivalent.

Also: `exportSchema = true` must stay on, and `app/schemas/2.json` must remain byte-identical
across the port. A diff in that file is the tripwire. If the generated `2.json` changes at all,
stop and escalate — do not "regenerate the baseline".

⟦VERIFY⟧ — assigned to **Core/Data**: whether Room KMP's schema export and identity-hash
computation are byte-compatible with the AGP+KSP Room 2.8.4 output currently in
`app/schemas/…/ProgressDatabase/2.json`. Spike this **before** any other persistence work; it
gates everything.

### 3.3 Persistence — `BibleDatabase` (read-only pre-packaged asset)

This is cliff #1 and #2. Detail matters here.

| Item | Call sites | Status | Destination | Risk |
|---|---|---|---|---|
| `Room.databaseBuilder(...).createFromAsset("bible/bible.db")` | `di/BibleModule.kt:52-56` | **NO EQUIVALENT in Room commonMain.** `createFromAsset`/`createFromFile`/`createFromInputStream`/`PrepackagedDatabaseCallback` are documented as Android-only in Room KMP, with common support listed as future work. | `shared/data` + `shared/platform` | **UNSOLVED as like-for-like** |
| The asset itself, `app/src/main/assets/bible/bible.db`, 5,599,232 bytes | committed | EXPECT (packaging) — Android `assets/` has no iOS analogue; the iOS side needs it in the app bundle | one copy in git | **HIGH** |
| `room_master_table` with hand-written identity hash `8144e1bc57f05006d1a15856ac762552` | `tools/build_bible_db.py:44,316-320` | **HIGH RISK** — see below | `shared/data` | **HIGH** |
| `@Entity(tableName="verse", primaryKeys=["translation_id","verse_id"])`, 8 columns | `bible/data/VerseEntity.kt` | KMP-LIB | `shared/data` | MEDIUM |
| `VerseDao.getVerses(start,end)` | `bible/data/VerseDao.kt` | KMP-LIB | `shared/data` | LOW |
| Raw `SimpleSQLiteQuery` on `database.openHelper.readableDatabase` to read the `translation` table | `bible/data/RoomBibleTextSource.kt:44-46` | **EXPECT** — `openHelper`/`SupportSQLiteDatabase` do not exist in Room KMP; the common equivalent is `RoomRawQuery` / `useReaderConnection` | `shared/data` | MEDIUM |
| `BibleAssetGate` — deletes copied `bible.db`/`-wal`/`-shm` on a content-version bump | `bible/data/BibleAssetGate.kt`, `bible/data/BibleAssetVersion.kt` (`java.io.File`) | EXPECT — file paths + deletion; `java.io.File` → okio | `shared/data` + `shared/platform` | MEDIUM |
| `context.getDatabasePath(name)` | `bible/data/BibleAssetGate.kt:47` | EXPECT | `shared/platform` | LOW |

**The `createFromAsset` problem, stated plainly.** Room KMP does not offer pre-packaged database
creation in common code. The consequence is that "open the bundled bible.db" cannot be a single
shared call. Three paths, and I have a recommendation (ADR-0007):

1. **Keep Room, add an `expect fun copyBundledBibleDatabaseIfNeeded(): String` in
   `shared/platform`** that returns an absolute file path. Android's actual copies from
   `assets/` (or keeps using `createFromAsset` on the Android builder); iOS's actual copies from
   `NSBundle.mainBundle.pathForResource` into `NSSearchPathForDirectoriesInDomains(.applicationSupport)`
   using okio. Then `Room.databaseBuilder<BibleDatabase>(path)` opens the *copied file* on both
   platforms, with `createFromAsset` no longer in the picture at all. **Recommended.** It moves
   the Android-only API out of the data layer and into one 20-line platform function per target.
2. **Drop Room for the bible DB entirely** and read it through a thin SQL layer (SQLDelight, or
   `androidx.sqlite` KMP directly). This deletes cliff #2 — no Room, no identity hash, no
   `room_master_table` requirement — at the cost of hand-writing the four queries the reader
   uses and rewriting `BibleDatabaseRoomOpenTest`. Genuinely attractive; the bible DB is
   read-only with 2 tables and ~4 queries. Considered seriously in ADR-0007.
3. Ship the verses as something other than SQLite. **Rejected** — 31,102 rows, range queries by
   `verse_id`, and a gate built on the SQLite file. Not worth reopening.

**The identity-hash problem.** `tools/build_bible_db.py` writes `room_master_table` by hand with
a hash lifted from the generated Android `BibleDatabase_Impl`. Under Room KMP that generated
hash may differ — different code generator, different Room version. **If it differs, the
committed asset is rejected at runtime on *both* platforms**, and regenerating the asset means
regenerating a byte-reproducible artifact whose byte-diff CI gate (`data-rebuild`, and the
SQLite-version pinning work recorded in CLAUDE.md) was hard-won. Path 2 above makes this
problem vanish entirely, which is a large part of why it is on the table.

⟦VERIFY⟧ — assigned to **Core/Data**, **before Phase B**: generate `BibleDatabase_Impl` under
Room KMP with the unchanged `VerseEntity` and compare its identity hash against
`8144e1bc57f05006d1a15856ac762552`. This single 2-hour spike decides ADR-0007.

### 3.4 Persistence — DataStore Preferences

| Item | Call sites | Status | Destination | Risk |
|---|---|---|---|---|
| `PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }` | `di/DataModule.kt:48-54` | KMP-LIB — `androidx.datastore:datastore-preferences-core` is multiplatform; the factory takes an okio path | `shared/data` + `shared/platform` (path) | LOW |
| ~15 preference keys read/written | `data/prefs/SettingsRepositoryImpl.kt` | KMP-LIB | `shared/data` | LOW |
| Partial-segment token cache (`partial_reading_segments`) | `data/prefs/PartialReadingRepositoryImpl.kt` | KMP-LIB | `shared/data` | LOW |
| Bible asset content version key | `bible/data/DataStoreBibleAssetVersionStore.kt` | KMP-LIB | `shared/data` | LOW |

**Keys and file name must not change.** The Android store file is `settings.preferences_pb` and
holds every shipped user's theme, font scale, tracking start date, plan selection, reminder
time, provider choice and first-run markers. The port must produce the identical file name and
identical key strings, or every existing user is silently reset to defaults — which would also
re-fire the first-run dialogs and re-default `selected_plan`. Add an acceptance criterion:

> **PG-2.** A `settings.preferences_pb` written by 1.8.1 is read back with identical values by
> the ported Android build. Fixture-based, committed.

### 3.5 Assets and the plan loader

| Item | Call sites | Status | Destination | Risk |
|---|---|---|---|---|
| `PlanAssetSource` — a `fun interface` taking an asset path, returning text | `data/plan/PlanAssetSource.kt`, implemented in `di/DataModule.kt:58-66` via `context.assets.open(...)` | **EXPECT** — already the right seam; only the impl is Android | `shared/platform` (interface) + both apps (impl) | LOW |
| 4 plan assets: `plans/registry.json` (307 B), `bible_companion/plan.json` (172 KB), `mcheyne/plan.json` (203 KB), `chronological/plan.json` (117 KB) | `app/src/main/assets/plans/` | packaging EXPECT | one copy in git | MEDIUM |
| `bible/bible.db` 5.6 MB | `app/src/main/assets/bible/` | packaging EXPECT | one copy in git | MEDIUM |
| kotlinx-serialization DTOs + validation | `data/plan/dto/*.kt`, `data/plan/ReadingPlanAssetLoader.kt` | COMMON — already kotlinx-serialization | `shared/data` | LOW |
| Single-flight per-plan cache under a mutex | `data/plan/ReadingPlanRepositoryImpl.kt` | COMMON | `shared/data` | LOW |

**The one-copy-in-git rule.** The delivery brief already asserts this
(`docs/task-briefs/ios-delivery-pipeline.md` §4.6). Architecturally it means the assets must
move out of `app/src/main/assets/` into a shared location — `shared/src/commonMain/composeResources/files/`
or a plain `shared/assets/` directory wired into both the Android `sourceSets.assets.srcDir` and
the iOS bundle's Copy Bundle Resources phase. Whichever mechanism is chosen, **`bible.db` must
not be read through Compose Resources' loader** — it needs a real filesystem path for SQLite,
not a byte array; a 5.6 MB `ByteArray` round-trip on startup is not acceptable. Expect a split:
plan JSON via a shared resource loader, `bible.db` via bundle-path + copy. That split is
ADR-0011.

⟦VERIFY⟧ — assigned to **Build & Release**: whether the Android `assets/` dir can be re-pointed
at a shared directory without breaking the `planAssetsDir` system property that all four plan
gates and the bible gate read (`app/build.gradle.kts:86-95`). Those gates read the *source
tree*, deliberately, so the path indirection has to survive.

### 3.6 Dependency injection

| Item | Count | Status | Destination | Risk |
|---|---|---|---|---|
| `@Inject constructor` | 61 | COMMON if the framework is KMP-capable | shared | MEDIUM |
| `@Singleton` | 14 | ditto | shared | LOW |
| `@Module`/`@Provides`/`@Binds` | 9 modules | **ANDROID-ONLY** (Hilt) | rewrite | MEDIUM |
| `@HiltViewModel` / `hiltViewModel()` / `@AndroidEntryPoint` / `@EntryPoint` / `@ActivityRetainedScoped` | 23 sites | **ANDROID-ONLY** | rewrite | MEDIUM |
| `@ApplicationContext Context` injection | 16 files | ANDROID-ONLY by definition | becomes platform-module wiring | LOW |
| Custom `@IoDispatcher` qualifier | `di/DispatcherModule.kt` | COMMON pattern | `shared/data` | LOW |

Hilt has no multiplatform story. The options are Koin (works, runtime resolution, loses
compile-time verification — a real loss for a codebase this test-disciplined), kotlin-inject
(compile-time, KSP, KMP-capable, smaller ecosystem), or manual constructor wiring in a
composition root (zero dependency, verbose, and honestly viable at 162 files). ADR-0012.

Note that `@ActivityRetainedScoped` is load-bearing in two places — `ReaderHandoff`
(`ui/navigation/ReaderHandoff.kt`) and `InAppUpdateState` (`update/InAppUpdateState.kt`) — both
of which rely on one instance shared between a producer and a Compose consumer, surviving
configuration change. iOS has no configuration change and no activity; the replacement is a
scope tied to the shared root composable's lifetime. Do not paper over this with a singleton
without thinking about it: `ReaderHandoff` carries a *pending* one-shot.

### 3.7 Networking (online NKJV/NASB, sprint 00R)

| Item | Call sites | Status | Destination | Risk |
|---|---|---|---|---|
| `HttpURLConnection` + `java.net.URL` | `bible/data/remote/BibleApiClient.kt`, `bible/data/remote/HttpFumsReporter.kt` | KMP-LIB → Ktor client (`Darwin` engine on iOS, `OkHttp`/`Android` on Android) | `shared/data` | LOW–MEDIUM |
| `java.net.URLEncoder` | `HttpFumsReporter.kt`, `data/reference/ProviderUrlBuilder.kt` | needs a small common percent-encoder or Ktor's `encodeURLParameter` | `shared/domain` (for `ProviderUrlBuilder`) | LOW |
| `android.util.Log` | `BibleApiClient.kt`, `HttpFumsReporter.kt`, `ui/browser/CustomTabLauncher.kt` | EXPECT — trivial logger interface | `shared/platform` | LOW |
| `java.io.File`-backed verse cache | `bible/data/remote/BibleTextCache.kt` | KMP-LIB → okio `Path`/`FileSystem` | `shared/data` + `shared/platform` (cache dir) | LOW |
| `java.util.UUID` for FUMS device/session ids | `bible/data/remote/FumsIdentity.kt` | KMP-LIB → `kotlin.uuid.Uuid` | `shared/data` | LOW |
| JSON parsing | already kotlinx-serialization | COMMON | `shared/data` | LOW |

This is the cleanest area in the port. `BibleApiClient` and `BibleTextCache` are already
interfaces with no-op implementations; only the concrete HTTP class changes. **Adding Ktor is a
new runtime dependency** — it must go through Build & Release, and it lands on a project that has
held "zero net-new runtime deps" for most of its life and enforces a 12 MB bundle gate. Ktor
core + a platform engine is not free. ADR-0014 argues it anyway: hand-rolling
`NSURLSession` + `HttpURLConnection` actuals for two GET calls is *also* defensible and cheaper
in binary size. This is a genuine judgement call, not a foregone conclusion.

**App Check note.** `di/BibleRemoteModule.kt` supplies `appCheckTokenProvider` as a lambda that
currently returns null, and the proxy runs `POLICY_ON_ATTESTATION_FAIL=allow` (the open security
item in CLAUDE.md). iOS App Check is a different SDK from Android's. Whoever closes that
security item now has to close it twice. Flag to owner; not a port blocker.

### 3.8 Compose UI

**446 `androidx.compose.*` imports across the UI.** Every Material 3 component in use exists in
Compose Multiplatform:

`AlertDialog, Button, Card, Checkbox, CircularProgressIndicator, DatePicker, DatePickerDialog,
DropdownMenu, DropdownMenuItem, HorizontalDivider, Icon, IconButton, MaterialTheme,
ModalBottomSheet, NavigationBar, NavigationBarItem, Scaffold, SegmentedButton, Slider,
SnackbarHost, Surface, Switch, Text, TextButton, TimePicker, TopAppBar` — plus
`HorizontalPager`, `LazyColumn`, `LazyVerticalGrid`, `Canvas`, `combinedClickable`,
`BoxWithConstraints`.

| Item | Call sites | Status | Destination | Risk |
|---|---|---|---|---|
| All the above M3 + foundation components | `ui/**`, `bible/ui/**` | COMMON (Compose Multiplatform material3) | `shared/ui` | LOW |
| `Canvas` + `drawRect` year strips | `ui/stats/YearStrip.kt` | COMMON | `shared/ui` | LOW |
| `HorizontalPager` ×3 (day pager, reader chapter pager, picker month pager) | `ui/day/DayReadingsScreen.kt`, `bible/ui/reader/ReaderRoute.kt`, `ui/datepicker/DayDatePickerDialog.kt` | COMMON | `shared/ui` | MEDIUM — gesture feel on iOS is a device-pass item, not a compile item |
| `combinedClickable` long-press verse selection | `bible/ui/reader/ReaderScreen.kt:410` | COMMON | `shared/ui` | MEDIUM — long-press *inside a pager inside a lazy list* is exactly the gesture the Android device pass had to verify by hand (CLAUDE.md, sprint 00Q). It will need the same on iOS. |
| `dynamicDarkColorScheme` / `dynamicLightColorScheme` (Material You) | `ui/theme/Theme.kt:77-80`, guarded by `Build.VERSION.SDK_INT >= S` | **ANDROID-ONLY** | `shared/ui` with an `expect fun dynamicColorScheme(dark: Boolean): ColorScheme?` returning null on iOS | LOW |
| `isSystemInDarkTheme()` | `ui/theme/Theme.kt` | COMMON | `shared/ui` | LOW |
| `LocalDensity.fontScale` multiplication for the font-size slider | `ui/theme/Theme.kt` | COMMON | `shared/ui` | LOW — but iOS Dynamic Type is a separate system setting; the composition rule needs a parity decision |
| `enableEdgeToEdge` / `SystemBarStyle` / `android.graphics.Color` | `MainActivity.kt:89-105` | ANDROID-ONLY | `androidApp` | LOW |
| `BackHandler` (selection-mode exit) | `bible/ui/reader/ReaderScreen.kt:119` | **EXPECT** — `androidx.activity.compose.BackHandler` is Android; iOS has no system back | `shared/platform` | MEDIUM — see below |
| `LocalContext.current` | 5 non-widget sites (`ReaderRoute`, `SettingsScreen` ×2, `Theme`, `DayReadingsScreen`) | EXPECT / eliminate | `shared/ui` | LOW |
| `AndroidView` | **zero** | — | — | — |
| Text input (`TextField`/`BasicTextField`) | **zero** | — | — | — |
| `SelectionContainer` | **zero** | — | — | — |

**Why the "Compose text on iOS is weak" worry mostly does not apply here.** The reader does not
use framework text selection. It renders each verse as its own `LazyColumn` item keyed by
`canonicalId`, wraps it in `combinedClickable`, and maintains selection in its own
`VerseSelection` model with `selected`/`stateDescription` semantics. There are no selection
handles, no caret, no IME, no `TextField` anywhere in the app. What the port actually needs from
Compose's iOS text stack is: correct line breaking, italic spans (`<a>` added words → italic in
`VerseRenderer.kt`), and font scaling. Those are the mature parts. Compose Multiplatform 1.8
declared iOS stable and 1.11 (May 2026) improved native selection/gestures further; none of that
is even on the critical path for this screen. This substantially de-risks ADR-0004.

**`BackHandler` is a real divergence, not a shim.** It is the spec-named exit from verse
selection mode and is pinned by `ReaderSelectionBackTest`. iOS has no back button; the
equivalent gestures are the interactive swipe-back edge gesture (which belongs to navigation,
not to a modal selection state) and the visible X in the contextual bar. **Recommendation:** on
iOS the X and "deselect the last verse" are the exits, and the `BackHandler` is Android-only.
Record in the parity matrix. Do **not** invent a swipe-to-clear gesture that Android does not
have.

### 3.9 Navigation

| Item | Call sites | Status | Destination | Risk |
|---|---|---|---|---|
| `NavHost` + 2 nested `navigation{}` graphs, 3 routes | `ui/navigation/AppNavHost.kt` | KMP-LIB — Navigation Compose is multiplatform (the repo is already on `navigation-compose` 2.9.8) | `shared/ui` | MEDIUM |
| `NavigationBar` bottom bar with per-tab back-stack preservation (`popUpTo(saveState) / restoreState`) | `AppNavHost.kt:switchTab` | KMP-LIB | `shared/ui` | MEDIUM |
| `hiltViewModel()` at 4 sites | `AppNavHost.kt`, routes | rewrite with the chosen DI | `shared/ui` | LOW |
| `SavedStateHandle` (reader last-read chapter) | `bible/ui/reader/ReaderViewModel.kt` | KMP-LIB — `androidx.lifecycle` ViewModel/SavedState are multiplatform | `shared/ui` | LOW |
| `androidx.navigation:navigation-testing` | `app/src/test/.../NavRegressionTest` | ⟦VERIFY⟧ multiplatform availability | test | LOW |

The whole navigation surface is two tabs and three destinations. The question is not *can*
Compose navigation run on iOS (it can) but whether an iOS user should get a bottom
`NavigationBar` with Android back-stack semantics or a native `TabView` + `NavigationStack` with
interactive swipe-back. ADR-0003.

### 3.10 Strings and resources

| Item | Count | Status | Destination | Risk |
|---|---|---|---|---|
| `strings.xml` entries | 161 | KMP-LIB → Compose Resources (`stringResource(Res.string.…)`) or moko-resources | `shared/ui` | MEDIUM |
| `stringResource(...)` / `R.string.…` call sites | 181 | mechanical rewrite | `shared/ui` | MEDIUM |
| Vector drawables (`ic_bible_book`, `ic_notification_reminder`, `widget_preview`, `ic_launcher_foreground`) | 4 | Compose Resources handles XML vectors; the widget preview + launcher icon stay Android | split | LOW |
| `material-icons-core` 1.7.8 (frozen artifact outside the BOM) | ~6 icons | ⟦VERIFY⟧ CMP-compatible artifact/version | `shared/ui` | LOW |
| `res/xml/today_widget_info.xml` | 1 | ANDROID-ONLY | `androidApp` | — |
| Notification channel name/description strings | 4 | split — the notifier is platform-side | both apps | LOW |

161 strings × 181 call sites is the single largest *count* of edits in the port, though each
edit is trivial. It is also the moment to notice that **the app has never been localized** —
one `values/` folder, no `values-xx/`. So there is no translation matrix to preserve, which
makes this much cheaper than it looks. ADR-0013.

### 3.11 Notifications and scheduling

This is cliff #5. Two distinct features with very different iOS answers.

| Item | Call sites | Status | Destination | Risk |
|---|---|---|---|---|
| `ReminderScheduler` interface (5 methods) | `reminders/ReminderScheduler.kt` | **EXPECT** — already an interface, already the right seam | `shared/platform` | MEDIUM |
| `AlarmTimes` pure next-occurrence math | `reminders/AlarmTimes.kt` | COMMON | `shared/domain` | LOW |
| `AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP)` ×3 alarms (reminder / midnight widget refresh / 01:00 persistent refresh) | `reminders/ReminderScheduler.kt:53-88` | **ANDROID-ONLY** | `androidApp` | — |
| `PendingIntent` + `ReminderAlarmReceiver` 3-action dispatch | `reminders/ReminderAlarmReceiver.kt` | ANDROID-ONLY | `androidApp` | — |
| `BootReceiver` / `RECEIVE_BOOT_COMPLETED` | `reminders/BootReceiver.kt`, manifest | **NO EQUIVALENT** — and **not needed**: `UNCalendarNotificationTrigger(repeats: true)` survives reboot by construction | `androidApp` only | LOW |
| `RescheduleAlarmsUseCase` fired on every launch + boot | `domain/RescheduleAlarmsUseCase.kt`, `MainActivity.kt:84` | COMMON logic, EXPECT effect | `shared/domain` | LOW |
| `ReminderNotifier` — daily reminder, suppressed when day complete or Feb 29, **decided at fire time** (D-S12-3) | `reminders/ReminderNotifier.kt`, `domain/DeliverDueReminderUseCase.kt` | **HIGH** — see below | `shared/platform` + `androidApp`/`iosApp` | **HIGH** |
| `PersistentNotifier` — ongoing, non-dismissible, `IMPORTANCE_LOW`, `PRIORITY_MAX`, refreshed 01:00 | `reminders/PersistentNotifier.kt` | **NO EQUIVALENT** | dies on iOS | **UNSOLVED / drop** |
| `NotificationPermissionChecker` (API 33+ POST_NOTIFICATIONS) | `reminders/NotificationPermissionChecker.kt` | **EXPECT** — maps to `UNUserNotificationCenter.requestAuthorization` | `shared/platform` | LOW |
| `NotificationChannel` ×2 | `ReminderNotifier`, `PersistentNotifier` | ANDROID-ONLY | `androidApp` | — |

**The fire-time decision is the hard part.** Android's design (D-S12-3) is: at the alarm's fire
time, run `GetDayReadingsUseCase`, look at today's progress, and *then* decide whether to post
and what text to use. That requires code execution at notification time. **iOS does not run your
code at notification time** for local notifications — the content is fixed when the request is
scheduled. Options:

- **Schedule N days ahead with pre-computed content**, refreshing the queue on every app
  foreground. The plan is date-anchored and fully deterministic, so the *text* is computable
  arbitrarily far ahead — that part is easy. What is *not* computable ahead is "suppress if the
  user has already finished today's readings", because that depends on state at 08:00 tomorrow.
- **Accept the divergence:** the iOS reminder fires regardless of completion. The copy is already
  gentle ("Today's readings" + references, no streaks, no guilt — PRD §13.0), so a reminder on a
  completed day is mildly redundant rather than harmful.
- **`UNNotificationServiceExtension`** does not help — it only modifies *remote* push payloads.

**The 64-notification cap is real and it binds.** iOS keeps at most 64 pending local
notifications per app. A repeating `UNCalendarNotificationTrigger` with `dateComponents(hour,
minute)` costs exactly **one** slot and repeats forever — that is the right primitive here and it
leaves 63 slots free. Do not schedule 64 one-shot notifications to get per-day content; it
caps the horizon at ~2 months and re-introduces exactly the drift the Android app designed
against. ADR-0005 recommends: one repeating trigger, static title, body listing today's
references **regenerated on every app foreground** by cancelling and re-adding the single
request. Suppression-on-completion is dropped on iOS and recorded in the parity matrix.

**The persistent tray notification has no iOS equivalent and I am not going to pretend
otherwise.** iOS has no ongoing/non-dismissible notification, no notification an app can keep
updated in the background, and no 01:00 wake-up to refresh one. The nearest analogues are Live
Activities (time-bounded, ActivityKit, designed for events with an end, ~8–12h ceiling, and
would be a fresh native SwiftUI/WidgetKit feature) or simply a WidgetKit widget on the home/lock
screen. **Recommendation: the persistent notification is Android-only.** Its iOS answer is the
lock-screen widget, which is the same work as ADR-0006. This is a user-visible divergence in a
feature that is **ON by default** on Android, so it must be recorded prominently in the parity
matrix and reflected in iOS Settings (the toggle simply does not appear).

### 3.12 OS launch-out integrations

| Item | Call sites | Status | Destination | Risk |
|---|---|---|---|---|
| Chrome Custom Tabs (`androidx.browser` 1.10.0) | `ui/browser/CustomTabLauncher.kt:26-32` | **EXPECT** — iOS equivalent is `SFSafariViewController` (in-app browser, same UX intent) | `shared/platform` interface `UrlOpener` | LOW |
| `ACTION_VIEW` fallback | same file | EXPECT → `UIApplication.openURL` | `shared/platform` | LOW |
| Explicit MySword component intent (`com.riversoft.android.mysword/.MySwordLink`) | `CustomTabLauncher.kt:75-88`, `domain/model/ReadingDestination.kt` | **NO EQUIVALENT** — MySword's Android app is not the iOS app; iOS apps are reached by custom URL scheme + `LSApplicationQueriesSchemes`, and MySword's iOS scheme is not known to this repo | `androidApp` | **MEDIUM / probably drop on iOS** |
| `PackageManager.getPackageInfo` install detection + manifest `<queries>` | `data/apps/AppInstallChecker.kt` | **EXPECT** — iOS equivalent is `UIApplication.canOpenURL` with declared schemes | `shared/platform` | MEDIUM |
| `ActivityNotFoundException` fallback chain | `CustomTabLauncher.kt` | EXPECT (the *semantics* — "if the app isn't there, fall back to the web URL") | `shared/platform` | LOW |

**The MySword provider is the honest casualty here.** It exists on Android because MySword is an
Android app with a documented `MySwordLink` activity and a numeric URL form
(`https://mysword.info/b?r={order}.{chapter}`, D-S15-1). On iOS the analogous ecosystem is
different apps entirely (Olive Tree, Logos, Accordance, YouVersion), each with its own scheme,
and this project has already recorded Logos and Olive Tree as NO-GO on Android for exactly the
reasons that would apply again. **Recommendation: iOS ships the four web providers (In-app,
BLB, Bible Gateway, YouVersion) and omits MySword.** `ExternalBibleApp.MYSWORD` stays in the
shared enum (it is persisted; removing it would break stored settings) but is filtered out of
the iOS picker by the existing `requiresApp` + `AppInstallChecker` mechanism, which returns
false on iOS. That is a clean, already-designed degradation — no new concept needed.

### 3.13 Clipboard

| Item | Call sites | Status | Destination | Risk |
|---|---|---|---|---|
| `ClipboardManager.setPrimaryClip(ClipData.newPlainText(...))` | `bible/ui/reader/VerseClipboard.kt:32-35` | **EXPECT** → `UIPasteboard.generalPasteboard.string` | `shared/platform` | LOW |
| `shouldShowCopyConfirmation(sdkInt)` — API 33+ suppresses the app's own toast because the OS shows one | `VerseClipboard.kt:42` | **EXPECT** — iOS shows **no** system copy confirmation, so iOS always returns `true` | `shared/platform` | LOW |
| `android.widget.Toast` | `bible/ui/reader/ReaderRoute.kt` | EXPECT — iOS has no toast; use an M3 `Snackbar` in the shared UI | `shared/ui` | LOW |

Note the file's own comment: platform `ClipboardManager` was chosen *deliberately over*
`LocalClipboardManager` because Compose's clipboard API had churned. On CMP that trade reverses —
`LocalClipboard` is available in common code. Either is fine; the interface is 2 lines.
Replacing the toast with a snackbar is a small, defensible parity improvement, and it is
simultaneously an Android behaviour change — so it goes in the parity matrix and needs owner
sign-off, not a silent swap.

### 3.14 Home-screen widget

| Item | Call sites | Status | Destination | Risk |
|---|---|---|---|---|
| `TodayWidget` (GlanceAppWidget), `TodayWidgetReceiver` | `widget/TodayWidget.kt`, `widget/TodayWidgetReceiver.kt` | **ANDROID-ONLY** | `androidApp` | — |
| `WidgetContent.kt` — 397 lines: 5 responsive tiers (TINY/SMALL/MEDIUM/LARGE + wide-short), per-tier type scale, row weighting, Feb-29/error states, a11y descriptions | `widget/WidgetContent.kt` | Glance composables are ANDROID-ONLY; **the tier-selection and content logic is portable** | split | MEDIUM |
| `WidgetRefresher` interface + `GlanceWidgetRefresher` | `widget/WidgetRefresher.kt` | EXPECT — the interface survives, iOS's actual calls `WidgetCenter.shared.reloadAllTimelines()` | `shared/platform` | LOW |
| Hilt `@EntryPoint` into `GetDayReadingsUseCase` from the widget | `widget/TodayWidget.kt` | ANDROID-ONLY | `androidApp` | — |
| `res/xml/today_widget_info.xml`, `widget_preview.xml` | resources | ANDROID-ONLY | `androidApp` | — |

**Android widgets stay, unchanged, on Android.** The interesting question is whether iOS gets a
WidgetKit counterpart, and that is genuinely separate work in Swift: a new app extension target,
a `TimelineProvider`, an App Group container to share data with the main app, and a SwiftUI
view. It cannot call the Kotlin ViewModel graph — but it *can* link the shared framework and
call a `suspend` use case, which is the interesting design question in ADR-0006. Given that the
iOS persistent notification also dies (§3.11), a lock-screen/home-screen widget is the natural
home for both. **Recommendation: not in v1.0 of the iOS app. Scope it as a follow-on.**

### 3.15 Play In-App Updates

| Item | Call sites | Status | Destination | Risk |
|---|---|---|---|---|
| `InAppUpdateManager` interface (takes `Activity` + `ActivityResultLauncher`) | `update/InAppUpdateManager.kt` | **ANDROID-ONLY** — the interface itself leaks Android types | `androidApp` | LOW |
| `PlayInAppUpdateManager` (Play Core `app-update` 2.1.0) | `update/PlayInAppUpdateManager.kt` | ANDROID-ONLY | `androidApp` | — |
| `InAppUpdateState` + `UpdatePhase` (`@ActivityRetainedScoped`) | `update/InAppUpdateState.kt` | pure Kotlin, but pointless on iOS | `androidApp` | LOW |
| `UpdatePromptDecision.shouldPrompt` (the `/100` PATCH-vs-MINOR rule) | `domain/UpdatePromptDecision.kt` | COMMON but unused on iOS | `shared/domain` or `androidApp` | LOW |
| The Restart snackbar in the root scaffold | `ui/navigation/AppNavHost.kt:UpdateRestartSnackbarEffect` | shared composable, but the phase is always `Idle` on iOS | `shared/ui` | LOW |

**This feature dies on iOS and that is correct, not a loss.** The App Store has no in-app update
API; apps update through the store, and Apple would reject an in-app "restart to install" flow
because there is nothing to install. The shared root scaffold keeps the snackbar effect, driven
by a `StateFlow<UpdatePhase>` that on iOS is permanently `Idle`. **Do this by binding a no-op
provider, not by an `if (isIOS)`** — that is exactly the invariant-2 case.

One cleanup this port forces: `InAppUpdateManager` takes `android.app.Activity` and
`ActivityResultLauncher` in its signature, so it cannot move to `shared/platform` as written. It
stays entirely in `androidApp`, and `RootViewModel` gets its `updatePhase` from a small
`shared/platform` interface (`UpdateAvailability`) with an Android impl and an iOS no-op. That
is a real interface change to a shipped file — flag it in the task brief.

### 3.16 App lifecycle / system chrome

| Item | Call sites | Status | Destination | Risk |
|---|---|---|---|---|
| `Application` subclass + `StrictMode` (debug) | `DailyReadingsApp.kt` | ANDROID-ONLY | `androidApp` | — |
| `ComponentActivity` + `setContent` + `enableEdgeToEdge` + `SystemBarStyle` | `MainActivity.kt` | ANDROID-ONLY; iOS gets a `UIViewController` from `ComposeUIViewController` hosted in SwiftUI | both apps | LOW |
| `onResume` widget refresh + update resume | `MainActivity.kt:62-70` | EXPECT — iOS uses `scenePhase`/`applicationDidBecomeActive`; the *logic* (refresh on foreground) is shared | `shared/platform` lifecycle signal | LOW |
| `registerForActivityResult` ×2 (permission, update flow) | `MainActivity.kt` | ANDROID-ONLY | `androidApp` | — |

The launch sequence in `MainActivity.onCreate` (reschedule alarms → maybe request notification
permission → set content → check for update) is genuine app logic that currently lives in an
Activity. It should become an `AppStartUseCase` in `shared/domain` firing against
`shared/platform` interfaces, with each platform's entry point calling it once. Otherwise iOS
re-implements the ordering by hand and the two drift — and the ordering here is load-bearing
(the permission prompt is deliberately fired *after* the reschedule and deliberately *not*
chained behind the first-run dialogs).

### 3.17 Miscellaneous JVM

| Item | Call sites | Status | Destination | Risk |
|---|---|---|---|---|
| `java.io.File` | `bible/data/BibleAssetVersion.kt`, `bible/data/remote/BibleTextCache.kt` | KMP-LIB → okio `Path`/`FileSystem` | `shared/data` | LOW |
| `java.io.BufferedReader` | `bible/data/remote/BibleApiClient.kt` | subsumed by the HTTP client choice | `shared/data` | LOW |
| `kotlinx.coroutines.runBlocking` inside a DI provider | `bible/data/BibleAssetGate.kt:44` | exists on Native but the surrounding design should change anyway | `shared/data` | LOW |
| `Dispatchers.IO` | several | **`Dispatchers.IO` does not exist on Kotlin/Native.** Use the existing `@IoDispatcher` qualifier everywhere and provide it per platform | `shared/data` | **MEDIUM** — easy to miss, compiles on Android, fails on iOS |

`Dispatchers.IO` deserves the MEDIUM: it appears in `RoomBibleTextSource.translations()`,
`BibleApiClient`, `BibleTextCache` and `di/DataModule`. The project already has an
`@IoDispatcher` qualifier (`di/DispatcherModule.kt`); the port should route **every** IO
dispatch through it rather than referencing `Dispatchers.IO` directly.

---

## 4. Third-party dependency disposition

| Dependency | Version | Status | Notes |
|---|---|---|---|
| Kotlin | 2.3.21 | ✅ | already KMP-capable |
| kotlinx-serialization-json | 1.9.0 | ✅ COMMON | no change |
| kotlinx-coroutines | 1.10.2 (test) | ✅ COMMON | watch `Dispatchers.IO` |
| Compose BOM / Compose UI / Material 3 | 2026.05.01 | ⚠️ replaced by the **Compose Multiplatform** BOM | Build & Release decision; the AndroidX BOM does not govern CMP |
| `material-icons-core` | 1.7.8 (frozen) | ⟦VERIFY⟧ | outside the BOM already; confirm a CMP-resolvable coordinate |
| androidx.navigation-compose | 2.9.8 | ✅ KMP | verify the multiplatform artifact + `navigation-testing` |
| androidx.lifecycle (runtime/viewmodel/compose) | 2.10.0 | ✅ KMP | ViewModel + SavedState are multiplatform |
| androidx.room + room-ktx + room-compiler | 2.8.4 | ⚠️ KMP **except** `createFromAsset` | cliffs #1/#2 |
| androidx.room-testing (`MigrationTestHelper`) | 2.8.4 | ⚠️ ⟦VERIFY⟧ common availability | if Android-only, the migration gate stays Android-only |
| androidx.datastore-preferences | 1.2.1 | ✅ KMP (use `-core` + okio path) | |
| androidx.sqlite (`SimpleSQLiteQuery`, `SupportSQLiteDatabase`) | via Room | ⚠️ different API in KMP | `RoomRawQuery` / `SQLiteConnection` |
| Hilt + hilt-navigation-compose | 2.59.2 / 1.3.0 | ❌ ANDROID-ONLY | ADR-0012 |
| androidx.core-ktx | 1.19.0 | ❌ ANDROID-ONLY | small usage |
| androidx.activity-compose | 1.13.0 | ❌ ANDROID-ONLY | `BackHandler`, `enableEdgeToEdge`, `setContent` |
| androidx.browser (Custom Tabs) | 1.10.0 | ❌ ANDROID-ONLY | §3.12 |
| androidx.glance-appwidget + glance-material3 | 1.1.1 | ❌ ANDROID-ONLY | §3.14 |
| com.google.android.play:app-update(+ktx) | 2.1.0 | ❌ ANDROID-ONLY | §3.15 |
| **(new)** Ktor client + engine | — | ➕ proposed | ADR-0014; Build & Release approves |
| **(new)** okio | — | ➕ required | file paths, cache, asset copy |
| **(new)** kotlinx-datetime | — | ➕ required | §3.1 |
| **(new)** DI framework (Koin / kotlin-inject / none) | — | ➕ ADR-0012 | |
| junit4 | 4.13.2 | ❌ JVM-only | `kotlin.test` in `commonTest` |
| Truth | 1.4.4 | ❌ JVM-only | ~all 115 test files use `assertThat` |
| Turbine | 1.2.0 | ✅ KMP | |
| Robolectric | 4.15.1 | ❌ ANDROID-ONLY | 29 test files |
| Compose `ui-test-junit4` | BOM | ⚠️ CMP has `runComposeUiTest` in common | large migration |
| **sqlite-jdbc** | 3.50.1.0 | ❌ JVM-only | `BibleTextVerificationTest` — §5 |
| Kover / Spotless | 0.9.8 / 7.0.4 | ✅ | multiplatform-capable |

**The junit4 + Truth line is the quiet giant.** All 115 test files use JUnit 4 `@Test` and
Truth's `assertThat`. Neither works in `commonTest`. Moving the suite means rewriting every
assertion to `kotlin.test`. That is mechanical and safe, but it is ~115 files of churn on the
project's most valuable asset. Options: (a) rewrite to `kotlin.test`, (b) keep tests JVM-only
in an `androidUnitTest`/`jvmTest` source set and accept that they never run on iOS. Only (a)
satisfies "the gates run in the iOS pipeline". §5 and ADR-0010.

---

## 5. The five data-verification gates

CLAUDE.md calls these the project's core IP protection, and the delivery brief makes "a release
that skips the gates is not a release" a hard rule. Their current mechanics:

| Gate | Assertions | Mechanism | Can it move to `commonTest`? |
|---|---|---|---|
| `ReadingPlanVerificationTest` (Bible Companion) | 11 | reads `plans/bible_companion/plan.json` from the **source tree** via the `planAssetsDir` system property; compares against a second-source fixture in `src/test/resources/` | **Yes, with work.** Needs (a) `kotlin.test` instead of junit4+Truth, (b) a multiplatform way to read a file at a known path — okio `FileSystem.SYSTEM` works on both JVM and Native, (c) the `planAssetsDir` property replaced by a generated constant or a `BuildConfig`-style generated Kotlin file. |
| `McheynePlanVerificationTest` | 10 | same shape | Yes, same work |
| `ChronologicalPlanVerificationTest` | 8 | same shape (single-source structural gate, D-ALT-24) | Yes, same work |
| `PlanSegmentGateTest` | 6 | same shape (0 violations across 2,920 portions) | Yes, same work |
| `BibleTextVerificationTest` | 18 | **`org.xerial:sqlite-jdbc`** — `Class.forName("org.sqlite.JDBC")`, `DriverManager.getConnection("jdbc:sqlite:…")` | **Not as written.** JDBC does not exist on Kotlin/Native. |
| `BibleDatabaseRoomOpenTest` | 5 | **Robolectric** + real Android SQLite + `createFromAsset` | **No.** Robolectric is Android-only, and `createFromAsset` is the very API that does not exist in common. |

**My read on the two bible gates — and I do not think this is a problem, provided we are
explicit about what each one is for.**

`BibleTextVerificationTest` is an **asset-correctness** gate. It asks "is the committed
`bible.db` the right data?" — verse counts, superscriptions, second-source equality,
checksum-distinctness, famous-verse pins. That question is platform-independent and only needs
answering **once per commit**, not once per target. It is legitimately a JVM test.

`BibleDatabaseRoomOpenTest` is an **integration** gate. It asks "does *this platform's* database
layer actually open the committed asset?" — the exact question that failed in sprint-00F. That
question is inherently per-platform, and the iOS answer must be a *new, equivalent* iOS test,
not a moved one.

**Recommendation (ADR-0010):**

1. Move the four plan gates (11 + 10 + 8 + 6 = 35 assertions) to `commonTest` over okio. They
   are pure JSON + arithmetic and they genuinely benefit from running on every target.
2. Keep `BibleTextVerificationTest` as a **JVM-only gate in a shared `jvmTest` source set**, and
   make the iOS release pipeline **depend on the shared `jvmTest` task passing**, not on
   executing it on a simulator. Report it honestly in the pipeline as "asset gate: JVM, 18
   assertions, passed" — that satisfies the delivery brief's intent ("no silently-skipped
   gate") without the fiction that JDBC runs on arm64.
3. Write a **new iOS-side `BibleDatabaseOpenTest`** in `iosTest` that opens the bundled asset
   through whatever mechanism ADR-0007 selects and reads Gen 1:1, John 3:16, John 11:35 and the
   Ps 3 verse-0 superscription — the same four probes as the Android test. **This is required
   work, not optional.** It is the thing that catches the iOS equivalent of the sprint-00F P0
   before a user does.
4. `BibleDatabaseRoomOpenTest` stays on Android, unchanged, forever.

Escalate if any of this changes: the Build & Release Engineer's acceptance criterion 2 asks for
observed counts, and the honest answer will be "common: 35, jvm: 18, android: 5, ios: 5 (new)",
not "all five gates run everywhere".

---

## 6. What dies, what degrades, what needs native work

Stated plainly, because hand-waving here is how ports lose credibility.

**Dies on iOS (no counterpart, and none should be faked):**

- The **persistent ongoing tray notification** (`reminders/PersistentNotifier.kt`). On by
  default on Android. iOS has no non-dismissible, app-refreshed notification. **This is the most
  user-visible loss in the port.**
- **Play In-App Updates** (`update/`). Correct — the App Store owns updates.
- The **MySword provider** (`ReadingDestination.MySwordApp`, the explicit component intent).
- **`BOOT_COMPLETED` rearming** — not needed; iOS repeating triggers survive reboot.
- **Material You dynamic colour** (`ui/theme/Theme.kt:77`). iOS has no system palette to adopt.
- The **Android back button** exit from verse-selection mode.

**Degrades (feature survives, behaviour differs — every one goes in `docs/parity-matrix.md`):**

- **Daily reminder:** on Android it is decided at fire time and suppressed when the day is
  complete or on Feb 29. On iOS the content is fixed at schedule time; suppression-on-completion
  is lost unless the app has been foregrounded since. Feb 29 *can* be handled (it is
  deterministic).
- **Localized date/time strings:** `java.time.format` and `NSDateFormatter` will not produce
  byte-identical output. Literal string pins must move platform-side.
- **Copy confirmation:** Android ≥33 relies on the OS toast; iOS must always show its own.
- **Font scaling:** the app's own 0.85×–1.5× slider composes with Android's system font scale;
  on iOS it composes with Dynamic Type, which has a different curve.

**Needs native work, scoped as separate deliverables (not part of the shared core):**

- **WidgetKit widget** — new app-extension target, `TimelineProvider`, App Group container,
  SwiftUI views. Recommended as post-1.0. It is also the natural iOS answer to the lost
  persistent notification, so the two should be scheduled together.
- **`UNUserNotificationCenter` scheduling + authorization actual** — small, but it is the actual
  behind `ReminderScheduler` and `NotificationPermissionChecker`.
- **`SFSafariViewController` / `UIApplication.openURL` actual** for `UrlOpener`.
- **`UIPasteboard` actual** for the clipboard.
- **Bundle→Application Support copy of `bible.db`** plus the iOS open test (§5.3).
- **`NSDateFormatter` actual** for localized date/time text.
- **The SwiftUI host** — `ComposeUIViewController` wrapped in a SwiftUI `App`, scene-phase
  plumbing, `Info.plist`.

---

## 7. File-level disposition summary

| Current package | Files | Destination | Notes |
|---|---|---|---|
| `domain/` | 39 | `shared/domain` | already 100% Android-free; only `java.time` changes |
| `bible/domain/` | 13 | `shared/domain` | 100% Android-free |
| `core/` | 2 | `shared/domain` | 100% Android-free |
| `data/` | 22 | `shared/data` (+1 to `shared/platform`) | only `data/apps/AppInstallChecker.kt` touches `android.*` |
| `bible/data/` | 15 | `shared/data` (+ 2 platform seams) | cliffs #1/#2 live here |
| `ui/` | 26 | `shared/ui` (− `ui/browser/` → platform) | |
| `bible/ui/` | 19 | `shared/ui` (− `VerseClipboard.kt` → platform) | |
| `di/` | 9 | rewritten; split shared/androidApp/iosApp | |
| `reminders/` | 8 | 2 → `shared/domain`+`shared/platform`, 6 → `androidApp` | |
| `widget/` | 4 | 1 → `shared/platform`, 3 → `androidApp` | tier logic extractable |
| `update/` | 3 | `androidApp` (+ 1 tiny `shared/platform` interface) | |
| root (`MainActivity`, `DailyReadingsApp`) | 2 | `androidApp` | launch sequence extracted to `shared/domain` |

**162 files. ~91 move to `shared/` essentially unchanged apart from imports. ~22 need platform
seams. ~20 stay Android-only. ~29 (UI) move with a strings/DI rewrite.**

---

## 8. Open ⟦VERIFY⟧ register

| # | Question | Owner | Blocks |
|---|---|---|---|
| V1 | Does Room KMP generate identity hash `8144e1bc57f05006d1a15856ac762552` for the unchanged `VerseEntity`? | Core/Data | ADR-0007, all bible work |
| V2 | Is Room KMP's exported `ProgressDatabase/2.json` byte-identical to the committed one? | Core/Data | ADR-0008, all progress work |
| V3 | Does `kotlinx.datetime.YearMonth` (0.7.0+) cover the month arithmetic in `GetMonthCompletionUseCase` + `DayDatePickerDialog`? | Core/Data | ADR-0009 |
| V4 | Is `androidx.room:room-testing` `MigrationTestHelper` available outside Android? | Core/Data | §5 |
| V5 | Is there a CMP-resolvable `material-icons-core` coordinate at a compatible version? | Build & Release | ADR-0013 |
| V6 | Is `androidx.navigation:navigation-testing` multiplatform? | Build & Release | §3.9 |
| V7 | Can the Android `assets/` source dir be re-pointed at a shared directory without breaking `planAssetsDir` in `app/build.gradle.kts`? | Build & Release | ADR-0011 |
| V8 | Does MySword publish an iOS URL scheme? (Expected: no / not applicable.) | iOS Platform | §3.12 — confirms the drop |

---

## Change log

- **2026-08-08** — initial inventory (Staff). Surveyed `main` @ `1bcc98e`, app 1.8.1 / 10801.
