# Sprint 0022 — Persistent tray notification (owner request)

**Status: GOAL MET.** (Overnight unattended run. Numbered V2 track, owner-redirected from the
queued v2.x release prep. Working tree handed over **uncommitted**; version deliberately
untouched — the main session verifies the gate and commits. CLI sub-agent dispatch still down
on expired credentials — EM executed tickets directly under per-ticket verification.)

## Goal outcome

**Met.** A reader can turn on a persistent (ongoing) tray notification that always shows
today's three readings and refreshes itself to the new day at **01:00 local time**. It is a
SEPARATE feature from the S12 popup reminder: silent, non-dismissible while on, expandable to
the three references, one tap into the app on today. Off by default; the owner enables it.

## Current capability

- **Settings → Reminders → "Keep readings in the tray"** (switch, off by default). Enabling on
  API 33+ triggers the POST_NOTIFICATIONS prompt (shared with the popup reminder; the result
  routes to whichever toggle requested it — pinned). Disabling cancels the alarm and dismisses
  the notification immediately.
- **The ongoing notification:** channel `persistent_readings` (IMPORTANCE_LOW, silent),
  `setOngoing(true)` (non-dismissible, no foreground service), `PRIORITY_MAX` +
  `CATEGORY_STATUS`. Title "Today's readings"; BigText body = the three collapsed references
  ("Genesis 1–2 · Psalms 1–2 · Matthew 1–2"; Jun 19/Dec 19 = "… · 2 John 1; 3 John 1"). Feb 29
  shows "No readings scheduled today" and stays present. Tap opens MainActivity (today).
- **1:00 AM refresh:** a dedicated inexact alarm at the next 01:00 local rebuilds the
  notification with the new day's readings and re-arms itself. Correct on boot (BOOT_COMPLETED),
  on every app launch, and after each fire — content is always decided at fire time against the
  same `GetDayReadingsUseCase` the UI uses.
- **No completion suppression** (unlike the popup): a fully-read day still shows the readings —
  the owner wants them always visible.
- Verified: **542/542 tests** (net +22; the three data/Room gates untouched — plan 7,
  BibleTextVerificationTest 18, BibleDatabaseRoomOpenTest 5), **4 mutations killed**, each by
  its intended test, restored in place; **Kover 95.1%** on domain/data (floor 70%); full
  pipeline green (`spotlessCheck lintDebug assembleDebug testDebugUnitTest
  koverXmlReportAppDebug koverVerifyAppDebug`). No new permissions, no new receivers, no
  Room/manifest changes. Version untouched.

## What tray ranking we can and cannot guarantee

- **Can:** the notification is persistent (ongoing, non-dismissible while on), silent
  (IMPORTANCE_LOW + setSilent), expandable to all three readings, and a single tap into the app.
- **Cannot:** literal top-of-tray placement. On API 26+ the *channel importance* caps how high
  a notification can sit, and Android (plus OEM skins) own final ordering — conversations,
  calls, media, and higher-importance app notifications outrank us. `PRIORITY_MAX` +
  `CATEGORY_STATUS` is the strongest legitimate "bias high within a low channel" we can apply
  without raising the channel to a buzzing importance (which the owner explicitly does not want
  — it's standing info, not an alert). "Close to the top" is best-effort.

## Decisions & rationale (do not relitigate)

- **D-S22-1 — Ongoing, low-importance, biased high.** Own channel `persistent_readings`,
  IMPORTANCE_LOW (silent), `setOngoing(true)`, `PRIORITY_MAX` + `CATEGORY_STATUS`. See the
  ranking caveat above.
- **D-S22-2 — No foreground service.** An ongoing notification does not require an FGS;
  `setOngoing(true)` alone makes it non-dismissible. An FGS would add a visible-notification
  requirement and Doze/policy burden for zero benefit. No FOREGROUND_SERVICE permission, no
  service.
- **D-S22-3 — Dedicated 01:00 alarm, not the 00:00 widget rollover.** Owner specified 1am.
  Reusing the midnight alarm would refresh an hour early and couple two concerns. Request code
  2004, action `ACTION_REFRESH_PERSISTENT`, inexact `setAndAllowWhileIdle(RTC_WAKEUP)`
  (D-S12-1), strictly-after `AlarmTimes.nextOccurrence`, re-armed each fire + boot + launch.
- **D-S22-4 — Content at post/refresh time; no completion suppression.** `RefreshPersistent
  NotificationUseCase` reads today's `DayReadings` and posts for BOTH `Scheduled` (the three
  refs) and `NoScheduledReadings` (the no-readings line). Always about today.
- **D-S22-5 — Off by default.** A permanent non-dismissible notification is too strong a
  default for all users; mirrors R-REM-1. Owner enables it.
- **D-S22-6 — Shared permission prompt, routed by feature.** Both toggles use the one
  `permissionRequests` channel + rationale; `SettingsViewModel` tracks which toggle is pending
  (`PendingPermissionFeature`) so a grant enables the right one (pinned: a granted result for
  the persistent toggle enables persistent, not the reminder).
- **Single home for the rule.** `RescheduleAlarmsUseCase` and the Settings enable/disable path
  BOTH delegate the persistent concern to `RefreshPersistentNotificationUseCase` — no
  double-action, one place where the enabled/disabled/Feb-29 logic lives.

## User-visible strings — OWNER TONE SIGN-OFF NEEDED (PRD M8)

All in `app/src/main/res/values/strings.xml`:

| id | string |
|---|---|
| `persistent_channel_name` | "Today's readings (always shown)" |
| `persistent_channel_description` | "An ongoing notification that keeps the day's readings in your tray and refreshes each morning" |
| `persistent_notification_title` | "Today's readings" |
| `persistent_no_readings_body` | "No readings scheduled today" |
| `persistent_notification_toggle_title` | "Keep readings in the tray" |
| `persistent_notification_help` | "An ongoing notification that always shows the day's readings and refreshes at 1:00 AM. It stays in your notification tray and can't be swiped away while it's on." |

Tone notes: body references use "Psalms" (catalog canonical, app-wide) — same flag as S12. No
guilt/urgency vocabulary. Title duplicates the S12 reminder title intentionally (both are
"today's readings"); flag if a distinct title is wanted to tell them apart in the tray.

## State of the codebase (delta)

- **`reminders/PersistentNotifier.kt`** — `PersistentNotifier` interface +
  `SystemPersistentNotifier` (channel, ongoing/silent/priority, BigText, tap→MainActivity,
  belt-and-braces permission check, pure `persistentBody(context, DayReadings)` companion,
  `cancelPersistent()`). CHANNEL_ID `persistent_readings`, NOTIFICATION_ID 3002.
- **`reminders/ReminderScheduler.kt`** — `+ schedulePersistentRefresh()` /
  `cancelPersistentRefresh()`; impl adds request code 2004 + `persistentRefreshIntent()`.
- **`reminders/AlarmTimes.kt`** — `PERSISTENT_REFRESH_TIME = 01:00`.
- **`reminders/ReminderAlarmReceiver.kt`** — `ACTION_REFRESH_PERSISTENT` routed to the new use
  case (thin, goAsync).
- **`domain/RefreshPersistentNotificationUseCase.kt`** — the fire-time rule (disabled → cancel
  notification + alarm, no re-arm; else post today + re-arm 01:00). Injected into
  `RescheduleAlarmsUseCase` (boot/launch) and `SettingsViewModel` (enable/disable).
- **`data/prefs/SettingsRepository(.Impl)`** — `persistentNotificationEnabled` flow + setter;
  key `persistent_notification_enabled` (default false).
- **`di/ReminderModule.kt`** — binds `SystemPersistentNotifier`.
- **`ui/settings/SettingsViewModel.kt` / `SettingsScreen.kt`** — toggle state + handler;
  `PersistentNotificationToggleRow` (tag `persistent-notification-toggle`) + helper in the
  Reminders section.
- **Tests (+22):** `RefreshPersistentNotificationUseCaseTest` (4), `SystemPersistentNotifierTest`
  (6, ShadowNotificationManager), `AlarmManagerReminderSchedulerTest` (+2), `AlarmTimesTest`
  (+1), `RescheduleAlarmsUseCaseTest` (+2), `SettingsViewModelTest` (+4),
  `SettingsRepositoryImplTest` (+1), `SettingsScreenTest` (+2), `AccessibilityGateTest`
  (persistent toggle 48dp + switch semantics). New fake: `FakePersistentNotifier` +
  `FakeReminderScheduler` persistent counters + `FakeSettingsRepository.storedPersistentEnabled`.

## Needs the owner's device pass (not JVM-provable)

1. Notification refreshes at/near 01:00 on a real device (Doze/OEM batching) and survives a
   reboot.
2. Tray ranking observed — how high it actually sits among other notifications.
3. BigText expansion shows all three readings; collapsed shows the title; the channel appears
   under App info → Notifications as a separate "always shown" channel.
4. Ongoing/non-dismissible behavior (can't swipe away while on; vanishes on disable).
5. The new receiver action + the MainActivity/boot re-arm hooks on-device (thin, JVM-untested
   as wiring).

## Carryover & next goal

- **Next goal (Sprint 23): V2.x release prep** (`sprint-0023-v2x-release-prep`) — version bump,
  consolidated device pass (S9 + S12–S22 items, incl. the persistent-notification device pass
  above), S12–S22 string tone sign-offs (incl. the D-S20-1 "Missed"-vs-"Not read" flag and the
  S22 title-duplication flag), closed-track rollout via the tag-to-Play pipeline.
- **Queued/deferred (unchanged):** colorblind strip palette; second-wave web providers;
  Logos/Olive Tree install detection; toggle-from-widget; Psalm 119 verse-ranges; API 26–28
  scrim check; TIME_SET/TIMEZONE_CHANGED receiver (now also relevant to the 01:00 alarm after a
  zone change); `createComposeRule` v2 migration.
- **Scope protected OUT this sprint:** a configurable refresh time (owner fixed it at 1am); a
  "mark read from the persistent notification" action (parked with toggle-from-widget); showing
  per-reading read/unread state in the notification body; a foreground service.

## Next sprint

`next: sprint-0023-v2x-release-prep`

## Open questions & risks

- **Tray ranking** is best-effort only (see the can/cannot section) — set owner expectations.
- **Timezone/clock changes** don't proactively re-arm the 01:00 alarm (no TIME_SET receiver) —
  same standing limitation as S12; next fire/boot/launch corrects it.
- **Force-stop clears alarms** — the persistent notification then goes stale until the next app
  open re-arms it (no periodic backstop for this notification, unlike the widget). Acceptable
  for V2; flag for the owner.
- **Title duplication** with the S12 reminder ("Today's readings") — deliberate, flagged for
  tone sign-off.
- Standing debt unchanged: Robolectric pinned `@Config(sdk = [34])`; JVM-untested MainActivity/
  receiver hooks; CLI agent credentials expired.


---

## Amendment — persistent notification ON by default (owner request, 2026-06-15)

**Working tree (uncommitted) follow-up to S22; version untouched; the main session verifies + commits.**

**The change.** The owner asked for the persistent tray notification (Settings → "Keep readings
in the tray") to be **ON by default** instead of off. **D-S22-5 is amended: default flipped
off → on**, with a launch-time permission request so on-by-default actually posts.

**What changed:**

1. **Absent-key default flipped off → on.** `SettingsRepositoryImpl.persistentNotificationEnabled`
   now reads `?: true` (was `?: false`). A fresh / never-touched install is on; a device where the
   user explicitly toggled it keeps its stored value (normal DataStore behavior — an explicit OFF
   survives, never re-defaulted). This is the same absent-key-default idiom as the S18 `show_streaks`
   flip. Pinned by two tests in `SettingsRepositoryImplTest`: *"persistent notification defaults to on
   when nothing is stored"* and *"an explicitly stored false survives the S22 default flip"*.

2. **Launch-time POST_NOTIFICATIONS request (the permission wrinkle).** A default-on notification
   needs POST_NOTIFICATIONS (API 33+), which a fresh install hasn't granted. New
   `domain/ShouldRequestNotificationPermissionOnLaunchUseCase` owns the *should we ask* rule
   (JVM-tested): returns true iff `persistentNotificationEnabled` is on AND the runtime permission
   is missing (reusing the S12 `NotificationPermissionChecker` seam, which is always-granted below
   API 33 — so the use case returns false there and no prompt shows; the notification just posts).
   `MainActivity` hosts the actual `RequestPermission` launcher (the only place that can): in
   `onCreate`, after `rescheduleAlarms()`, if the use case says yes it launches the system prompt
   **once per launch**. On **grant** → MainActivity re-runs `rescheduleAlarms()` →
   `RefreshPersistentNotificationUseCase` posts the notification immediately. On **denial** → nothing
   changes: the setting stays on, simply can't post (no crash, no nag loop); the notification appears
   if/when the user later grants notifications. This mirrors how the S12 reminder handles denial
   (a denial never disables the feature).

3. **First-run sequencing decision.** The first run already shows the tracking-start prompt and the
   reading-destination prompt (both in-app Compose `Dialog`s rendered by the day screen's ViewModel,
   D-S19-2). The notification-permission request is the **OS's own surface**, not a third in-app
   dialog, so it does not visually stack with those two. Decision: fire it from `onCreate` **after**
   the alarm reschedule, as a single one-shot per launch — the standard first-launch notification
   prompt every app shows. We deliberately did **not** chain it behind the in-app dialogs
   (that would need cross-ViewModel coordination for no real benefit) and we never re-prompt within a
   session. The OS itself rate-limits repeat system prompts across launches.

4. **Unchanged:** disabling in Settings still cancels the 01:00 alarm + dismisses the notification;
   the 01:00 refresh, boot/launch re-arm, Feb-29 "No readings scheduled today" body, BigText three
   references, and the shared S12 permission prompt (`PendingPermissionFeature`) are all untouched.
   No new permissions (POST_NOTIFICATIONS already declared in S12), no new receivers, no Room/manifest
   changes. The `FakeSettingsRepository` in-memory default stays `false` (a neutral starting point for
   behavior tests; the real absent-key default is pinned by the repository test only).

**Verification.** 547/547 tests (net +5: removed 1 combined repo test, added 3 repo tests +
3 `ShouldRequest…` tests; the three data/Room gates untouched — plan 7, BibleTextVerificationTest 18,
BibleDatabaseRoomOpenTest 5). **4 mutations killed, each by its intended test, restored in place:**
(1) `?: true` → `?: false` → *"defaults to on"* RED; (2) read body → `true` (ignore stored) →
*"explicitly stored false survives"* RED; (3) drop the `persistentEnabled` gate → *"disabled - never
requests"* RED; (4) drop the `!granted` gate → *"granted - does not request"* RED. Full pipeline green
(`spotlessCheck lintDebug assembleDebug testDebugUnitTest koverXmlReportAppDebug koverVerifyAppDebug`);
Kover 95.1% instruction / 95.3% line on domain/data (floor 70%). Version untouched.

**Device-pass item added:** on a fresh API 33+ install, confirm the POST_NOTIFICATIONS prompt appears
at first launch and, after **granting**, the persistent notification posts (the day's three readings)
without revisiting Settings; after **denying**, the app does not crash and the Settings toggle still
reads on (and the notification appears later if the user grants notifications via system settings).
The MainActivity launcher + post-grant re-post path is thin and JVM-untested as wiring.

**Files touched:** `data/prefs/SettingsRepository.kt` (interface doc),
`data/prefs/SettingsRepositoryImpl.kt` (`?: true` + doc), new
`domain/ShouldRequestNotificationPermissionOnLaunchUseCase.kt`, `MainActivity.kt` (launcher + hook),
`data/prefs/SettingsRepositoryImplTest.kt` (rewritten persistent tests), new
`domain/ShouldRequestNotificationPermissionOnLaunchUseCaseTest.kt`.
