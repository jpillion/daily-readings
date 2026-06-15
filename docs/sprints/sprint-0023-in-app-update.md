# Sprint 23 — In-app update flow (Play In-App Updates, flexible)

> **EM:** Morgan · **Status:** DONE (uncommitted in the working tree; the main session bumps the
> version, commits, tags, and deploys) · **Date:** 2026-06-15 · **Version:** unchanged at
> **1.4.1/10401** (main session bumps to 1.4.2/10402 + tags) · **Track:** numbered V2 track
> (owner-redirected from the queued `sprint-0023-v2x-release-prep`). Closes **BACKLOG #4**.

## Goal outcome — MET

A single sprint goal: **the app tells the reader, in-app, when a meaningful new version is
available and offers a one-tap install — without ever nagging or blocking the readings.**

**What can be done now that couldn't before:** when Play has a newer build of the app, the running
app starts a *non-blocking* background download (the reader keeps using the planner the whole time);
when the update finishes downloading, a calm "Restart" snackbar appears, and one tap installs the
update and relaunches. A **patch-only** release (e.g. 1.4.1 → 1.4.2) is silent by design; only a
**minor-or-higher** bump (1.4.1 → 1.5.0, or 2.0.0) prompts. The flow is the **flexible** update type
only — never an immediate/forced update.

This shipped **Option A** from the new-version-notice exploration (Play In-App Updates), the
owner's choice over the local "what's new" card (Option C).

## Current capability (working software)

- On every launch, **once per process** (D-L-5), the app asks Play whether a newer version is
  available. The pure `UpdatePromptDecision.shouldPrompt` decides whether to act: PATCH=silent,
  MINOR/MAJOR=prompt. If warranted, the flexible flow starts and the update downloads in the
  background.
- A downloaded-but-not-installed update raises a **Restart snackbar** in `RootScaffold`
  ("An update is ready to install." + a "Restart" action + dismiss), indefinite duration. Tapping
  Restart installs the staged update and relaunches.
- A stalled download (app backgrounded mid/post-download) is re-surfaced on `onResume`
  (Play's recommended re-check), so the Restart snackbar reappears.
- The whole feature is **inert on Play-less devices or when the check fails** — no crash, no error
  surfaced; the readings are never gated.
- `bundleRelease` builds clean with the new dependency (R8 + Play Core consumer keep rules);
  the AAB is **8.07 MB** (was ~7.67 MB), under the 12 MB CI ceiling. No app-side proguard rule was
  needed — Play Core/GMS ship their own consumer rules.

## Decisions & rationale (D-L-1..D-L-8, all owner-approved as written)

- **D-L-1 — dependency.** `com.google.android.play:app-update:2.1.0` + `app-update-ktx:2.1.0`
  (version catalog `appUpdate = "2.1.0"`). Transitive: `core-common 2.0.3`,
  `play-services-basement 18.1.0`, `play-services-tasks 18.0.2`.
- **D-L-2 (LOAD-BEARING) — the minor-vs-patch pure rule.** `domain/UpdatePromptDecision` +
  `UpdateAvailabilitySignal`. `shouldPrompt(current, available, signal)` returns true iff Play
  offers an allowed flexible update AND `available > current` AND
  `available / 100 != current / 100` (the D-S9-3 `/100` drops the two PATCH digits). Pure, total,
  no Play/Android types — exhaustively unit-pinned. The Play→signal mapping lives in the data seam,
  never here.
- **D-L-4 — surface = Restart snackbar in `RootScaffold`.** The calm, least-intrusive surface over
  a banner/dialog, matching the app's sober tone. Extracted as the stateless
  `UpdateRestartSnackbarEffect(phase, host, onRestart)` so the wording/duration/action are pinnable
  without the Hilt nav graph.
- **D-L-5 — no new DataStore key.** No-nag is a **process-lifetime** flag (`InAppUpdateState`
  `markPrompted()` single-shot) + Play's own update staleness across processes. Nothing persisted.
- **D-L-6 (REQUIRED FINDING, VERIFIED) — merged-manifest posture.** The plan assumed the dep would
  add an INTERNET grant. **It does NOT.** Verified by building the merged manifest with and without
  the dependency and diffing: the Play Core In-App Updates dependency adds **ZERO** new
  `uses-permission` (no INTERNET, no GMS permissions). It adds only a
  `com.google.android.play.core.common.PlayCoreDialogWrapperActivity` (transparent dialog host) and
  the `com.google.android.gms.version` meta-data. The six permissions in the merged manifest
  (`ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `FOREGROUND_SERVICE` + the app's own
  `POST_NOTIFICATIONS`/`RECEIVE_BOOT_COMPLETED`/the dynamic-receiver perm) are byte-identical with
  and without the dep — they pre-date Sprint L (Glance/WorkManager merges, per Sprint E). **Net:
  the app's no-INTERNET offline identity (NFR-V3-A) is preserved.** Play's networking is brokered
  by the Play Store app/GMS, not an app-held INTERNET permission. This is materially more favorable
  than the plan's premise; the build.gradle.kts comment records the verified reality.

## State of the codebase

- **`domain/UpdatePromptDecision.kt`** — the pure gate + `UpdateAvailabilitySignal` enum
  (`UPDATE_AVAILABLE` / `NONE`). The single home of the minor-vs-patch rule.
- **`update/` (new package, mirrors `reminders/`):**
  - `InAppUpdateState.kt` — `@ActivityRetainedScoped` event/state seam (modelled on
    `ReaderHandoff`): `phase: StateFlow<UpdatePhase>` (`Idle` | `ReadyToRestart`), the
    single-shot `markPrompted()` no-nag flag, `onDownloaded()`/`clear()`. Pure, JVM-testable.
  - `InAppUpdateManager.kt` — the seam the Activity drives (`checkForUpdate(activity, launcher)`,
    `resume(activity)`, `completeUpdate()`, `unregister()`). Keeps Play SDK types out of MainActivity.
  - `PlayInAppUpdateManager.kt` — the real Play wiring. Maps Play's
    `updateAvailability()`/`isUpdateTypeAllowed(FLEXIBLE)` → `UpdateAvailabilitySignal`, calls
    `UpdatePromptDecision`, starts `startUpdateFlowForResult(info, launcher, FLEXIBLE)`, listens for
    `InstallStatus.DOWNLOADED` → `state.onDownloaded()`, and `requestCompleteUpdate()` on Restart.
    All Play calls run on `@DefaultDispatcher` and are `runCatching`-wrapped (StrictMode-clean,
    Play-less-safe). `@ActivityRetainedScoped`.
- **`di/UpdateModule.kt`** — `@Binds PlayInAppUpdateManager → InAppUpdateManager`, installed in
  `ActivityRetainedComponent`.
- **`MainActivity.kt`** — injects `InAppUpdateManager`; registers a
  `StartIntentSenderForResult` launcher (no-op callback — the install listener, not the result,
  drives the snackbar); `checkForUpdate(this, launcher)` after `setContent`; `resume(this)` in
  `onResume`; `unregister()` in `onDestroy`.
- **`ui/navigation/RootViewModel.kt`** — gained `updatePhase: StateFlow<UpdatePhase>` (from the
  shared `InAppUpdateState`) + `restartToInstallUpdate()` (→ manager `completeUpdate()`).
- **`ui/navigation/AppNavHost.kt`** — `RootScaffold` adds a `SnackbarHost`
  (tag `update-snackbar-host`) and calls the extracted internal
  `UpdateRestartSnackbarEffect(phase, snackbarHostState, onRestart)`.
- **`res/values/strings.xml`** — `update_downloaded_message`, `update_restart_action`
  (sober, await tone sign-off; table below).

## Verification

- **644 tests** (net +17 from 627), full pipeline green
  (`spotlessCheck lintDebug assembleDebug testDebugUnitTest koverXmlReportAppDebug
  koverVerifyAppDebug`). New: `UpdatePromptDecisionTest` (7), `InAppUpdateStateTest` (4),
  `RootViewModelTest` (+2 → 3), `UpdateRestartSnackbarTest` (3), `AccessibilityGateTest` (+1 → 8).
- **The three data/Room gates UNTOUCHED:** plan = 11, `BibleTextVerificationTest` = 18,
  `BibleDatabaseRoomOpenTest` = 5.
- **Kover 95.8%** on domain/data (≥70% floor).
- **`bundleRelease` builds** with the new dep: R8 minify clean, 777 Play Core entries survive R8
  (consumer rules work), `lintVitalRelease` green, AAB 8.07 MB < 12 MB ceiling. No app-side keep
  rule added.
- **a11y gate green:** the Restart snackbar action meets the 48dp touch target and is spoken
  (pinned in `AccessibilityGateTest`).
- **5 load-bearing mutations killed**, each by its intended test, restored byte-identically:
  1. `UpdatePromptDecision` minor-boundary `!=`→`==` → 4 fails (patch-silent, minor-prompt,
     major-prompt, boundary).
  2. signal gate `!=`→`==` → 4 fails (no-available-is-silent + the three prompt cases).
  3. removed the `available <= current` staleness guard → `an older available version is silent` fails.
  4. `markPrompted` second-call `return false`→`true` (drops no-nag) → single-shot test fails.
  5. snackbar `phase is ReadyToRestart`→`!is` → 3 snackbar fails (render + action callback).

## Strings — AWAIT OWNER TONE SIGN-OFF

| key | value |
|---|---|
| `update_downloaded_message` | "An update is ready to install." |
| `update_restart_action` | "Restart" |

## Device-pass items (NOT JVM-provable; require a real Play track)

The In-App Updates API only does real work for an app installed from Play with a higher version on
a track the device account can see. These need an internal-testing release:
1. A minor-bump on an internal track surfaces the flexible flow on launch; the download proceeds in
   the background without blocking the readings.
2. On DOWNLOADED, the Restart snackbar appears; tapping Restart installs and relaunches into the new
   version.
3. Backgrounding mid-download then resuming re-surfaces the Restart snackbar (the `onResume` re-check).
4. A **patch-only** track bump produces **no** prompt (the D-L-2 gate on a real device).
5. Play-less device / no-update case is inert (no crash, no error).

## Carryover, risks & next goal

- **No version bump applied** (stays 1.4.1/10401) — the main session bumps to **1.4.2/10402**
  (D-S9-3), commits, tags, deploys.
- **R1 — posture (resolved favorably):** D-L-6 verified the dep adds no INTERNET/GMS permission;
  the offline identity holds. The *transitive* dependency footprint (Play Core + 2 GMS libs) is the
  real cost, accepted as Option A's price.
- **Manual-updater gap (Option C's would-be strength) remains unaddressed** by design — Option A
  fires *before* update, so it serves the manual updater. But Option A only delivers the curated
  *what's new* via the Play listing, not in-app. If the owner later wants an in-app "what's new"
  card, that's the still-open Option C (BACKLOG candidate), composable with this.
- **Next goal (Sprint 24): V2.x release prep** (`sprint-0024-v2x-release-prep`) — the long-queued,
  owner-scheduled release-prep sprint (version bump cadence, whatsnew, tag-to-Play), now also
  carrying the V3.0 device-pass + string sign-offs from Sprint E and the S-L device-pass list above.

## Next sprint

`next: sprint-0024-v2x-release-prep`
