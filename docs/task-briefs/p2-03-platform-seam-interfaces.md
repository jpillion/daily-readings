# p2-03 — `shared/platform`: the capability contract. Staff-owned, and it does not get edited.

> **Assignee:** **Staff Engineer / Port Architect.** Not delegable.
> **Release:** 1.11.0 · **Merge order:** Tranche A, **third — with or immediately after `p2-02`.**
> Everything after it depends on these signatures existing.
> **Inherits:** [`p2-00-overview.md`](p2-00-overview.md) rules R1–R9.
> **Executes:** ADR-0001 rule 3, and the seam halves of ADR-0005/0006/0007/0009/0011/0014.

---

## Objective

Write the complete set of `shared/platform` capability interfaces, with KDoc precise enough that
**an engineer who has never seen the other platform can write the actual without asking a
question.** Land them with Android implementations (mostly lifted from Phase 1) and **hand-written
fakes for every one.**

**These signatures are the contract that makes parallel work safe.** `p2-04` … `p2-09` and all of
Phase 3 and 4 are written against them.

---

## Context

The reason this port is tractable is that the platform surface is **already** expressed as
interfaces with fakes — `WidgetRefresher`, `ReminderScheduler`, `ReminderNotifier`,
`PersistentNotifier`, `NotificationPermissionChecker`, `AppInstallChecker`, `BibleApiClient`,
`BibleTextCache`, `BibleTextSource`, `PlanAssetSource`, `BibleAssetVersionStore`,
`InAppUpdateManager`. Only 22 of 162 main files import `android.*` at all.

This task **preserves that discipline rather than inventing a new one**, with two corrections:

1. **Several existing interfaces are Android-shaped and must be redesigned before they move.**
   `ReminderScheduler`'s five methods include `scheduleMidnightRefresh` and
   `schedulePersistentRefresh` — those exist to serve the Glance widget and the ongoing tray
   notification, **neither of which exists on iOS.** Porting it as-is would put Android-only
   feature plumbing into the shared contract, which is how a shared layer becomes a layer with an
   Android-shaped hole in it.
2. **Some interfaces do not belong in `shared/platform` at all.** `InAppUpdateManager` takes
   `android.app.Activity` and `ActivityResultLauncher`; it stays entirely in `androidApp`, and
   only a tiny read-only signal crosses the boundary.

### The design test every interface here must pass

> **Design to semantics, not to either platform's API shape.**
>
> `setSpeechRate(rate)` leaks Android's 1.0-is-normal scale into a world where iOS treats ~0.5 as
> normal. `setSpeechRate(multiplier)` with KDoc defining 1.0 as "the platform's natural speaking
> pace" leaves each actual to map it.
>
> Anything named after `AlarmManager`, `UNNotificationRequest`, `CustomTab` or `NSBundle` has
> failed. `UrlOpener.open(url)`, not `CustomTabLauncher`.

### The rule that keeps this a contract

**Implementers may not add, rename or widen an interface in `shared/platform`.** If an actual
cannot be written against the contract, **that is an escalation, not an edit.** Contracts that
implementers can edit stop being contracts — they get edited to match whatever was already
written, and then the boundary is wherever the last person left it.

---

## Contract

### A. Lifted verbatim from Phase 1 — move the file, change the package, nothing else

| Interface | Specified in | Members |
|---|---|---|
| `DateProvider` | [`p1-02`](p1-02-kotlinx-datetime-sweep.md) | `today()`, `now()`, `timeZone` |
| `DateTextFormatter` | [`p1-01`](p1-01-date-text-formatter-seam.md) | 9 formatting methods |
| `AppFilePaths` | [`p1-04`](p1-04-okio-file-io-and-dispatchers.md) | `databases`, `cache`, `files` |

Their Android implementations move to `:app`. `DateTextFormatter` returns `java.time` types today
and **must be rewritten to kotlinx types by `p1-02` before this task** — verify that first.

---

### B. Promoted from an existing interface, renamed to semantics

#### B1. `TextAssetSource` — was `PlanAssetSource` (ADR-0011)

```kotlin
package com.jpillion.dailyreadingplanner.platform

/**
 * Reads a bundled text asset by its path relative to the app's asset root
 * (for example "plans/mcheyne/plan.json").
 *
 * The asset is **guaranteed to exist**: it ships inside the application and a missing one is a
 * packaging defect, not a runtime condition. Implementations therefore THROW rather than returning
 * null, so that a packaging mistake fails loudly at the first read instead of degrading into a
 * mysterious "wrong plan loaded".
 *
 * Paths are always nested and always forward-slash separated. An implementation that flattens
 * "plans/mcheyne/plan.json" to "plan.json" is wrong and will collide with two other assets of the
 * same name.
 *
 * Never called on the main thread.
 */
fun interface TextAssetSource {
    suspend fun read(assetPath: String): String
}
```

> The "always nested" sentence is not decoration. iOS bundle resources are **flat by default**
> unless the directory is added as a *folder reference* (blue) rather than a *group* (yellow) — a
> 20-minute mistake that presents as a mysterious bug. The KDoc is where a future implementer meets
> that fact.

#### B2. `UrlOpener` — was `CustomTabLauncher`

```kotlin
/**
 * Opens a web URL for the user.
 *
 * The user stays in the app's task: the page opens in an in-app browser surface that keeps the
 * app's context and offers a way back (Android Custom Tabs, iOS SFSafariViewController), NOT a
 * cold hand-off to a separate browser app. If no such surface is available, the implementation
 * falls back to the platform's ordinary "open this link" behaviour rather than failing.
 *
 * [url] is always absolute and always https. Opening never throws: a URL that cannot be opened is
 * a no-op, because a dead link must not crash a reader.
 */
interface UrlOpener {
    fun open(url: String)
}
```

#### B3. `ExternalAppLauncher` — was `AppInstallChecker` + the MySword branch of `CustomTabLauncher`

```kotlin
/**
 * Reaching a specific third-party app that the user may or may not have installed.
 *
 * This exists for the "open this passage in my Bible app" feature. It is deliberately expressed as
 * a capability question plus a best-effort launch, NOT as a package name or a URL scheme, because
 * the two platforms answer "is that app here?" in fundamentally different ways (Android inspects
 * the package manager against a manifest <queries> declaration; iOS asks whether a declared URL
 * scheme can be opened).
 *
 * A platform with no counterpart for [app] returns false from [isAvailable] forever. That is a
 * complete and correct answer, and callers already degrade gracefully on it — the app is filtered
 * out of the picker and the stored preference is never rewritten.
 */
interface ExternalAppLauncher {
    /** Whether [app] can be reached on this device right now. Cheap; safe to call per frame. */
    fun isAvailable(app: ExternalBibleApp): Boolean

    /**
     * Opens [reference] in [app]. Returns false if the app could not be reached, in which case the
     * caller falls back to the web URL. Never throws.
     */
    fun open(app: ExternalBibleApp, reference: String): Boolean
}
```

> **iOS returns `false` from `isAvailable` for `MYSWORD`, permanently.** MySword is an Android app
> reached by an explicit component intent; no iOS counterpart is known to this project. The enum
> value stays — **it is a persisted id and removing it would break stored settings** — and degrades
> to Blue Letter Bible through machinery that already exists. No new concept is needed.

#### B4. `Clipboard` — was `VerseClipboard`'s platform half

```kotlin
/**
 * The system clipboard, for the reader's verse-copy feature.
 */
interface Clipboard {
    /** Places [text] on the clipboard as plain text, replacing whatever was there. */
    fun copyPlainText(text: String)

    /**
     * Whether the app must show its own confirmation that a copy happened.
     *
     * Some platforms show a system-level confirmation themselves and a second, app-level one reads
     * as a bug; others show nothing and silence reads as a failure. Callers show their own
     * confirmation if and only if this is true.
     */
    val showsOwnCopyConfirmation: Boolean
}
```

> Android ≥33 returns **false** (the OS shows a toast); Android <33 and **iOS always return true**.
> This is the existing `shouldShowCopyConfirmation(sdkInt)` rule (`VerseClipboard.kt:42`),
> generalised. Note it as a **behaviour divergence for `docs/parity-matrix.md`**, and note that the
> Android side's `Toast` becomes an M3 `Snackbar` in shared UI — a small, defensible parity
> improvement that is *also* an Android behaviour change and therefore **needs owner sign-off, not
> a silent swap.**

#### B5. `WidgetRefresher` — interface survives, iOS actual is a no-op

```kotlin
/**
 * Asks the platform to refresh any glanceable surface showing today's readings.
 *
 * Called after anything that changes what such a surface would display. A platform with no
 * glanceable surface implements this as a no-op — that is a complete implementation, not a stub,
 * and callers must not branch on it.
 */
fun interface WidgetRefresher {
    fun refresh()
}
```

> The iOS actual is a no-op in v1.0 and becomes `WidgetCenter.shared.reloadAllTimelines()` when
> WidgetKit ships (iOS 1.1). **A no-op provider, never an `if (isIOS)` at the call site** —
> invariant 2.

---

### C. Redesigned. Do NOT port the current shape.

#### C1. `DailyReminderScheduler` — replaces `ReminderScheduler`'s five Android-shaped methods

The current interface carries `scheduleReminder`, `cancelReminder`, `scheduleMidnightRefresh`,
`schedulePersistentRefresh`, `cancelPersistentRefresh`. **Three of those exist to serve the Glance
widget and the ongoing tray notification — Android-only features (ADR-0005, ADR-0006).** They do
not belong in a shared contract.

```kotlin
/**
 * The user's standing daily reminder.
 *
 * Exactly one reminder exists at a time. [scheduleDaily] replaces any previously scheduled one, so
 * callers never need to cancel first, and calling it twice with the same time is a no-op from the
 * user's perspective.
 *
 * The reminder is a wall-clock commitment, not a timer: it fires at [time] in the user's current
 * local zone every day, and it survives both app termination and device restart without the app
 * running. Implementations that cannot honour that without app-side re-arming are responsible for
 * their own re-arming; callers do not do it for them.
 *
 * The reminder's *content* is fixed when it is scheduled. Implementations must not assume the app
 * will be running when it fires.
 */
interface DailyReminderScheduler {
    suspend fun scheduleDaily(time: LocalTime)
    suspend fun cancelDaily()
}
```

> **"Content is fixed when it is scheduled" is the load-bearing sentence**, and it is why iOS v1.0
> ships a **generic** reminder body (ADR-0005 A1, owner-signed): "Today's readings are ready" —
> never wrong, never stale, one of iOS's 64 pending-request slots, and **not** suppressed on
> completed days or on Feb 29. Android keeps its fire-time decision (D-S12-3) *inside its own
> actual*; that is an implementation freedom the contract permits, not a divergence the interface
> encodes.
>
> `scheduleMidnightRefresh` / `schedulePersistentRefresh` / `cancelPersistentRefresh` move to an
> **Android-only** interface in `:app`. They serve Android-only features and have no business in
> the shared contract.

#### C2. `NotificationPermission` — was `NotificationPermissionChecker`

```kotlin
/**
 * Permission to show the user notifications.
 *
 * On platforms where notifications need no grant, [isGranted] is always true and [request] returns
 * true without showing anything — a complete implementation, not a stub.
 */
interface NotificationPermission {
    fun isGranted(): Boolean

    /**
     * Asks the user, if the platform allows asking. Returns the resulting grant state.
     *
     * Callers must treat a denial as final for this session and must never ask again in the same
     * session: the app's existing behaviour is that a denial leaves the setting off, shows one
     * explanation, and offers a path to system settings (R-REM-7).
     */
    suspend fun request(): Boolean
}
```

#### C3. `PlatformCapabilities` — the answer to "should this Settings row exist?"

```kotlin
/**
 * What this platform can do, expressed as product capabilities rather than as OS names.
 *
 * This exists so that shared UI can omit a control for a capability the platform does not have,
 * WITHOUT branching on which platform it is running on. A composable asks "is there an ongoing
 * notification here?", never "am I on iOS?".
 *
 * Add a flag here only when its absence would leave a dead control on screen. A flag that merely
 * describes a platform is a disguised `if (isIOS)` and must be refused.
 */
interface PlatformCapabilities {
    /** An always-present, non-dismissible tray notification the app keeps up to date. */
    val hasOngoingNotification: Boolean

    /** A home-screen or lock-screen surface the app can populate. */
    val hasGlanceableSurface: Boolean

    /** An in-app update flow. False where the app store owns updates. */
    val hasInAppUpdates: Boolean

    /** A system-level "back" affordance that a modal state can intercept. */
    val hasSystemBackAffordance: Boolean
}
```

> Android: `true, true, true, true`. iOS v1.0: `false, false, false, false`.
> `hasGlanceableSurface` flips when WidgetKit ships. `hasSystemBackAffordance` is what removes the
> reader's `BackHandler` exit from verse selection on iOS — where the exits become the **X**,
> **deselecting the last verse**, and **Copy** (which the owner made an exit in P-Q-1).
> **Do not invent a swipe-to-clear gesture Android does not have.**

#### C4. `UpdateAvailability` — the only part of Play In-App Updates that crosses the boundary

```kotlin
/**
 * Whether a newer version of the app is ready to be installed.
 *
 * Permanently [UpdatePhase.Idle] on platforms where the app store owns updates. Shared UI observes
 * this and shows its restart affordance only when it is not Idle, so no platform branch is needed.
 */
interface UpdateAvailability {
    val phase: StateFlow<UpdatePhase>
    fun restartToInstall()
}
```

> **`InAppUpdateManager` itself stays entirely in `androidApp`** — it takes `android.app.Activity`
> and `ActivityResultLauncher` and cannot move as written. This is a **real interface change to a
> shipped file** and belongs in `p2-05`'s write set, flagged there.

#### C5. `Logger`

```kotlin
/** Diagnostic logging. Never user-facing; never on a hot path. */
interface Logger {
    fun debug(tag: String, message: String)
    fun warn(tag: String, message: String, cause: Throwable? = null)
}
```

Replaces three `android.util.Log` sites.

#### C6. `AttestationTokenProvider` (ADR-0014)

```kotlin
/**
 * Supplies a device-attestation token for the translations proxy, or null when attestation is not
 * configured on this platform. A null token is a valid state today and the proxy accepts it.
 */
fun interface AttestationTokenProvider {
    suspend fun token(): String?
}
```

> Today's `appCheckTokenProvider` lambda returns null and the proxy runs
> `POLICY_ON_ATTESTATION_FAIL=allow` — a **live, owner-accepted open security item.** The port does
> not close it; it makes it cost twice (iOS App Check is a different SDK). **Flag to owner. Do not
> implement it here.**

---

### D. Declared here, implemented in tranche B

#### D1. `BundledDatabaseProvider` (ADR-0007, and see its **Amendment A2**)

```kotlin
/**
 * Ensures the bundled, read-only database named [name] exists as a readable file on this device,
 * and returns its absolute path.
 *
 * The bundled database is a versioned data artifact, never user data. If a file already exists at
 * the destination and was produced by [contentVersion], it is left untouched and its path
 * returned. Otherwise the bundled copy replaces it — along with any sidecar journal files — so
 * that a shipped correction reaches existing installs.
 *
 * Runs off the main thread; the copy is on the order of several megabytes. Throws if the bundled
 * artifact is missing: that is a packaging defect, not a runtime condition.
 */
interface BundledDatabaseProvider {
    suspend fun materialise(name: String, contentVersion: Int): String
}
```

> **Declared in tranche A; NOT wired on Android until tranche B.** ADR-0007 A2: the Android bible
> DB open path does **not** change in 1.11.0 — `createFromAsset` stays in `shared/data`'s
> `androidMain` until iOS needs the seam. Sprint-00F is why.
>
> `BibleAssetGate`'s delete-on-version-bump logic folds into `materialise`, which is where it
> belonged — and its `runBlocking`-inside-a-DI-provider construction disappears with it. **That is
> a genuine improvement the port makes possible, and it is still a tranche-B change**, with the
> three Robolectric wiring tests and their two killed mutations preserved in spirit against the new
> seam.

---

### E. `expect`/`actual`, not interfaces — and only these two

Two cases genuinely cannot be an injected interface because they are Compose-shaped, and they live
in **`shared/ui`**, not `shared/platform`:

```kotlin
// shared/ui — returns null where the platform has no system-derived palette.
expect fun dynamicColorScheme(dark: Boolean): ColorScheme?

// shared/ui — a no-op where PlatformCapabilities.hasSystemBackAffordance is false.
@Composable expect fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit)
```

**Nothing else gets an `expect` declaration.** ADR-0001's revisit trigger stands: if
`shared/platform` exceeds ~20 interfaces or any single interface exceeds ~5 methods, the boundary
is drawn in the wrong place.

Current count: **15 interfaces + 2 `expect` declarations.** `DateTextFormatter`'s 9 methods exceed
the per-interface guideline and that is a **deliberate, argued exception** — they are nine variants
of one capability (localized text) with one implementation each, not nine capabilities.

---

## Acceptance criteria

1. All 15 interfaces exist in `shared/platform/src/commonMain`, with KDoc matching the source
   above **verbatim**.
2. **Every one has an Android implementation** in `:app` and **a hand-written fake** in a shared
   test-fixtures source set. There are **zero mocking libraries** in this repo's 940 tests — MockK's
   absent Kotlin/Native support is the usual wall for a port this size and this codebase walks
   straight past it. **Do not introduce one.**
3. **Zero `java.*`, `android.*`, `androidx.*` imports** in `shared/platform/src/commonMain`. The
   `p2-02` boundary check enforces it; confirm it is green.
4. `DailyReminderScheduler` has **exactly two** methods. The three Android-only scheduling methods
   live in an Android-only interface in `:app`.
5. `InAppUpdateManager` is **not** in `shared/platform`. Only `UpdateAvailability` crosses.
6. **The KDoc test, applied by a second reader:** hand `TextAssetSource`, `DailyReminderScheduler`
   and `Clipboard` to someone who has not read this brief and ask them to describe what an iOS
   implementation must do. **If they have a question, the KDoc is not finished** — and the fix is
   the KDoc, not the answer.
7. Compiles for `androidTarget()` and `jvm()`.
8. **The six data gates untouched: 11 / 10 / 8 / 6 / 18 / 5.**
9. Full pipeline green.
10. Every divergence named in the KDoc above is handed to **Verification** for
    `docs/parity-matrix.md`: the generic reminder body, no ongoing notification, no glanceable
    surface, no in-app updates, no system back, MySword unavailable, copy confirmation always
    shown, no dynamic colour.
11. **No R8 device smoke** — interfaces plus lifted Android implementations. `p2-04`/`p2-05` carry
    the smoke. Say so explicitly.

---

## Boundaries / write set

**Mine (Staff):**
- `shared/platform/src/commonMain/**` — all 15 interfaces
- `shared/ui/src/commonMain/**` — the two `expect` declarations **only**
- `shared/platform/src/androidMain/**` and `:app` — the Android implementations
- The shared test-fixtures source set — the fakes

**Not mine, and I do not touch it:**
- Any use case, repository, ViewModel or composable body — `p2-04`, `p2-05`, Phase 3.
- Any `build.gradle.kts` — **Build & Release**.
- `docs/parity-matrix.md` — **Verification**.
- The iOS actuals — **iOS Platform**, tranche B and Phase 4. **I write the contract; I do not write
  the implementations.** Once I start writing actuals I stop being able to hold the whole map.

---

## Escalation triggers

- **An implementer reports that an actual cannot be written against a contract** → that is *my*
  problem and I amend the interface. It is never theirs to edit.
- **A sixteenth interface is proposed** → I evaluate it against ADR-0001's ~20 ceiling and against
  whether it is a genuine OS service or a disguised platform branch.
- **A `PlatformCapabilities` flag is proposed that does not remove a control from the screen** →
  refuse it. That is an `if (isIOS)` wearing a hat.
- **`DateTextFormatter` is proposed to grow a tenth method** → I check whether a call site was
  missed or a formatting decision is being invented.
- **Anyone proposes a mocking library** → refuse and escalate to EM if pressed. It would not
  compile for Kotlin/Native and would take a chunk of the suite off the iOS targets.
