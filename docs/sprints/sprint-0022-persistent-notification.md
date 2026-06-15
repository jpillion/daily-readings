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
