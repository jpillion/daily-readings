# ADR-0001 — Module topology and the `shared/platform` boundary

**Status:** Draft, pending owner sign-off · **Date:** 2026-08-08 · **Author:** Staff / Port Architect

## Context

The app is one Gradle module, `:app`, with 162 main-source Kotlin files under
`com.jpillion.dailyreadingplanner`. There is no `shared/` anything.

The survey in [../port-inventory.md](../port-inventory.md) found that only **22 of 162 files
import `android.*`**, and that `domain/` (39 files), `bible/domain/` (13) and `core/` (2) are
already entirely free of Android types. The platform surface is already expressed as interfaces
with fakes in tests: `WidgetRefresher`, `ReminderScheduler`, `ReminderNotifier`,
`PersistentNotifier`, `NotificationPermissionChecker`, `AppInstallChecker`, `BibleApiClient`,
`BibleTextCache`, `BibleTextSource`, `PlanAssetSource`, `BibleAssetVersionStore`,
`InAppUpdateManager`.

That is unusual and it is the single biggest reason this port is tractable. The topology should
preserve that discipline rather than invent a new one.

## Decision

Five source sets, with a hard rule about which way dependencies point.

```
shared/domain      pure Kotlin: models, use cases, policies, classifiers, formatters.
                   Depends on: kotlinx-datetime, kotlinx-coroutines, shared/platform interfaces.
                   FORBIDDEN: java.*, android.*, Room, DataStore, Compose, okio, Ktor.

shared/platform    capability INTERFACES, written to semantics. A handful of expect/actual
                   where an interface genuinely cannot express it (file paths, formatters).
                   Depends on: shared/domain models only.

shared/data        repositories and their storage implementations: Room KMP, DataStore,
                   the HTTP client, the asset readers, the caches.
                   Depends on: shared/domain, shared/platform.

shared/ui          Compose Multiplatform screens, ViewModels, navigation, theme, resources.
                   Depends on: shared/domain, shared/platform. NEVER shared/data types.

androidApp         MainActivity, Application, Glance widget, AlarmManager/Notification actuals,
                   Play In-App Updates, Custom Tabs, MySword intent, manifest, DI wiring.

iosApp             SwiftUI host, UNUserNotificationCenter / UIPasteboard /
                   SFSafariViewController actuals, Info.plist, (later) WidgetKit extension.
```

**The three rules that make this survive contact with implementers:**

1. `shared/domain` and `shared/ui/commonMain` contain **zero** `java.*` and **zero** `android.*`
   imports. Enforced in CI by a grep-based check, not by good intentions.
2. Platform-conditional behaviour lives at the `expect`/`actual` boundary or behind a
   `shared/platform` interface. **`if (isIOS)` in shared code is a build failure**, not a code
   review comment.
3. `shared/platform` interfaces are named for **what the app needs**, never for what either OS
   provides. `UrlOpener.open(url)`, not `CustomTabLauncher`. `ReminderScheduler.scheduleDaily(time)`,
   not `AlarmScheduler`. The KDoc on every method defines the semantics precisely enough that an
   engineer who has never seen the other platform can write the actual without asking a question.

**Staff holds the pen on `shared/platform`.** Implementers may not add, rename or widen an
interface there. If an actual cannot be written against the contract, that is an escalation, not
an edit.

## Alternatives rejected

**One `shared` module.** Simplest to set up, and plenty of KMP projects do it. Rejected because
this codebase's whole quality story rests on layers that cannot see each other — `shared/ui`
being *unable* to import a Room entity is worth more here than the convenience of one module. It
is also much harder to retro-fit the split later than to start with it.

**`shared/domain` + `shared/ui` only, with platform code inline via expect/actual.** Rejected:
it puts `expect` declarations next to business logic, which makes the platform surface invisible.
The current codebase's greatest asset is that you can list its entire platform dependency by
listing twelve interfaces. Keep that property.

**Feature modules (`shared/feature-reader`, `shared/feature-schedule`, …).** Rejected as
premature. 162 files across two features does not need it, and the two features already share
`BookCatalog`, `ReadingFormatter`, `ConsecutiveChapterRuns` and `DayCompletionClassifier` — the
project has repeatedly and correctly insisted on *one home* for each of those. Feature modules
would create pressure to duplicate them.

## Consequences accepted

- **A one-time cost of moving ~150 files** and rewriting their imports. Unavoidable in any
  topology.
- `shared/ui` cannot see `shared/data`, so every ViewModel must take domain interfaces. It
  already does — the current ViewModels inject use cases, not repositories. No rework expected.
- `shared/platform` will have ~15 interfaces. That is more than the "about three `expect`
  declarations" threshold I usually treat as a smell — but these are not three variants of one
  capability, they are fifteen genuinely distinct OS services (notifications, alarms, clipboard,
  browser, files, formatting, …). The smell test applies to a *single* abstraction, and none of
  these is doing that.
- CI gets a new lint step (the `java.*`/`android.*` grep). Cheap, and it is the only thing that
  actually prevents boundary erosion over months.

## Revisit when

- A third target appears (desktop, web). The topology already supports it; only the actuals
  multiply.
- `shared/platform` exceeds ~20 interfaces, or any single interface exceeds ~5 methods — either
  suggests a boundary drawn in the wrong place.
- `shared/domain` needs a dependency that is not kotlinx-*. That is the signal that something
  platform-shaped has leaked in.
