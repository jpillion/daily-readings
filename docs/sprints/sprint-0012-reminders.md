# Sprint 0012 — Reading reminders

**Status: GOAL MET.** Closed 2026-06-11. (Unattended overnight run, sprint 3 of 4; working
tree handed over uncommitted by request. CLI sub-agent dispatch still down on expired
credentials — EM executed tickets directly under per-ticket verification discipline.)

## Goal outcome

**Met.** A reader can opt into one daily reminder at a time they choose in Settings (off by
default, R-REM-1). At that time a plain notification — "Today's readings" + the day's three
collapsed references — appears and opens the app when tapped. It is quietly suppressed when
the day is already fully marked (R-REM-4) and on Feb 29 (R-REM-5), never retroactive
(R-REM-6), and survives reboot (R-REM-8). The same alarm infrastructure gives the widget a
**precise midnight refresh** (FR-23) — the "stale until the next 30-min update" gap (D9
risk R6) is retired.

## Current capability

- **Settings → Reminders (FR-19/22):** "Daily reminder" switch (off by default) + a
  "Reminder time" row (stock M3 TimePicker dialog, 12/24-h follows the device). Enabling on
  API 33+ triggers the POST_NOTIFICATIONS prompt *at that moment only*; denial leaves the
  toggle off and shows an explanation dialog with an "Open settings" path (R-REM-7 — the
  setting only ever reflects reality). Time changes while enabled re-arm the alarm at once.
- **The notification (FR-20/21):** channel "Daily reminder" (IMPORTANCE_DEFAULT); title
  "Today's readings"; body = the same collapsed references the app renders, joined with
  " · " (e.g. "Genesis 1–2 · Psalms 1–2 · Matthew 1–2"; Jun 19/Dec 19 renders
  "… · 2 John 1; 3 John 1"). Tap opens MainActivity (always lands on today); auto-cancels;
  fires at most once per day. Suppression is decided **at fire time** against the same
  `GetDayReadingsUseCase` the UI uses.
- **Reliability:** the standing alarms are re-armed after every fire, on BOOT_COMPLETED,
  and on every app launch (idempotent `RescheduleAlarmsUseCase` hook in
  `MainActivity.onCreate` — covers app update and post-force-stop relaunch).
- **Midnight widget refresh (FR-23):** an independent alarm at next local midnight snaps
  the widget to the new day and re-arms itself; runs regardless of the reminder toggle.
  The 30-min `updatePeriodMillis` backstop is kept (alarms do not survive force-stop).
- Verified: **260/260 tests** (46 new; the 7-test Sprint 1 plan gate untouched), **4
  mutations killed** — each by exactly its intended test, in-place restores:
  (1) complete-day guard dropped → "complete day quietly skipped" fails;
  (2) Feb-29/Scheduled guard dropped → "Feb 29 quietly skipped" fails;
  (3) `AlarmTimes` `isAfter` → `!isBefore` → "exactly-now schedules tomorrow" fails
  (the no-refire-loop boundary); (4) disable-cancel branch dropped in
  `RescheduleAlarmsUseCase` → "stale alarm cannot outlive a disable" fails.
  Full pipeline green; **Kover 96.4%** on domain/data (floor 70%). No Room schema change.
  Version stays 1.0.0/10000. New manifest permissions: POST_NOTIFICATIONS,
  RECEIVE_BOOT_COMPLETED — nothing else.

## Decisions & rationale (do not relitigate)

- **D-S12-1 — Inexact alarms.** `setAndAllowWhileIdle(RTC_WAKEUP)` for both alarms. No
  `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`: avoids the Play exact-alarm policy burden and
  the API 31+ permission churn; product tolerance is "within a few minutes" (R-REM-8), and
  Doze batching is acceptable for a devotional nudge and a midnight widget rollover.
- **D-S12-2 — Midnight refresh rides the same infra, independently.** Own PendingIntent
  (request code 2002 vs 2001), always armed (boot + launch + self-re-arm), ignores the
  reminder toggle. R6/D9 retired; `updatePeriodMillis` stays as the force-stop backstop.
- **D-S12-3 — Suppression at fire time; one standing alarm, always next-occurrence.** The
  alarm always fires; `DeliverDueReminderUseCase` decides show/skip *then* re-arms for
  tomorrow (skips still re-arm; a stale alarm after a disable does nothing and does not
  re-arm). `AlarmTimes.nextOccurrence` is strictly-after — at the exact fire instant the
  next occurrence is tomorrow, never an immediate refire.
- **D-S12-4 — Seams.** `ReminderScheduler` + `ReminderNotifier` interfaces (the
  WidgetRefresher pattern) and the pure `AlarmTimes` object keep every rule JVM-testable;
  receivers are thin action-routers (`goAsync` + coroutine) with zero logic.
- **D-S12-5 — Default reminder time 08:00.** Only meaningful once enabled; pre-fills the
  picker, one tap to change. Stored as minute-of-day (wall-clock semantics: "8:00 wherever
  the device is"); corrupt stored values degrade to the default.
- **D-S12-6 — Permission gate in the ViewModel** behind `NotificationPermissionChecker`
  (always-granted below API 33). The Route owns only the ActivityResult launcher; the
  enable/deny flow is unit-tested with a fake.

## User-visible strings — OWNER TONE SIGN-OFF NEEDED (PRD M8)

All in `app/src/main/res/values/strings.xml` unless noted:

| id | string |
|---|---|
| `reminders_section_title` | "Reminders" |
| `reminder_toggle_title` | "Daily reminder" |
| `reminder_help` | "One notification a day with that day's readings. It isn't sent if the readings are already done." |
| `reminder_time_title` | "Reminder time" |
| `reminder_time_row_description` (a11y) | "Reminder time, %1$s" |
| `reminder_time_dialog_title` | "Set reminder time" |
| `reminder_time_dialog_confirm` | "Set time" |
| `reminder_time_dialog_cancel` | "Cancel" |
| `reminder_permission_title` | "Notifications are turned off" |
| `reminder_permission_body` | "Reminders arrive as a notification, and notifications aren't allowed for this app right now. You can allow them in system settings." |
| `reminder_permission_settings` | "Open settings" |
| `reminder_permission_dismiss` | "Not now" |
| `reminder_channel_name` | "Daily reminder" (system settings surface) |
| `reminder_channel_description` | "A daily notification listing the day's readings" |
| `reminder_notification_title` | "Today's readings" |
| *(notification body — data-derived)* | e.g. "Genesis 1–2 · Psalms 1–2 · Matthew 1–2" |

Tone note for the owner: the body says "**Psalms** 1–2" (the catalog's canonical book
name, used app-wide) where PRD §13.2's example said "Psalm 1–2" — flag if you want the
singular. No guilt/urgency vocabulary anywhere; suppressed days produce *nothing*, not a
congratulation (§13.0).

## State of the codebase

- **New package `reminders/`:** `AlarmTimes` (pure next-occurrence/next-midnight math),
  `ReminderScheduler` + `AlarmManagerReminderScheduler` (D-S12-1), `ReminderNotifier` +
  `SystemReminderNotifier` (channel, copy, `reminderBody()` pure companion, content intent
  → MainActivity), `NotificationPermissionChecker` (+Android impl),
  `MidnightRefreshHandler`, `ReminderAlarmReceiver` (actions
  `…action.SHOW_REMINDER`/`…action.MIDNIGHT_REFRESH`), `BootReceiver`.
- **Domain:** `DeliverDueReminderUseCase` (fire-time rules), `RescheduleAlarmsUseCase`
  (boot/launch re-arm). **DI:** `di/ReminderModule.kt`. **Data:**
  `SettingsRepository(.Impl)` gained `reminderEnabled`/`reminderTime` (+setters; keys
  `reminder_enabled`, `reminder_minute_of_day`).
- **UI:** `SettingsScreen` Reminders section (tags: `reminder-toggle`, `reminder-time-row`,
  `reminder-time-value`, `reminder-time-dialog`/`-confirm`/`-cancel`,
  `reminder-permission-dialog`/`-settings`/`-dismiss`); `SettingsViewModel` owns the
  toggle/permission/time logic (one-shot `permissionRequests` flow, `showPermissionRationale`
  state); `SettingsRoute` owns the launcher + the notification-settings intent.
  `MainActivity.onCreate` launches `rescheduleAlarms()` alongside the S10 hook.
- **Manifest:** the two permissions; both receivers `exported="false"` (BOOT_COMPLETED is
  a protected broadcast). New asset: `res/drawable/ic_notification_reminder.xml` (white
  book glyph, alpha-only as the platform requires).
- **Tests (46 new):** `AlarmTimesTest` (7), `DeliverDueReminderUseCaseTest` (6),
  `RescheduleAlarmsUseCaseTest` (3), `MidnightRefreshHandlerTest` (1),
  `AlarmManagerReminderSchedulerTest` (5, ShadowAlarmManager),
  `SystemReminderNotifierTest` (5, ShadowNotificationManager incl. the no-permission
  silence and the content-intent target), `SettingsRepositoryImplTest` (+4),
  `SettingsViewModelTest` (+8), `SettingsScreenTest` (+7), `AccessibilityGateTest`
  (reminder rows: 48dp + switch semantics + spoken label/value). New fakes in
  `testing/ReminderFakes.kt` (scheduler/notifier/permission checker, ordered call log).

## Needs the owner's device pass (genuinely not JVM-provable)

1. Notification fires at/near the chosen time on a real device (Doze/OEM batching), and
   after a reboot (M7). Tap lands in the app on today.
2. The API 33+ permission prompt flow end to end, including the "don't ask again" path →
   rationale dialog → "Open settings" deep link into the app's notification settings.
3. Midnight widget rollover observed on a real launcher.
4. Notification look: small-icon rendering in the status bar, BigText expansion, channel
   listed under App info → Notifications.
5. The Hilt receivers on-device (JVM-untested as classes: `ReminderAlarmReceiver`,
   `BootReceiver`, the Route's permission launcher, the MainActivity hook — all thin).

## Carryover & next goal

- **Next goal (Sprint 13): Bible-app links** — the specced, owner-requested provider
  choice ([docs/features/bible-app-links.md](../features/bible-app-links.md)): reading taps
  open the user's chosen KJV destination (BLB default, zero setup; YouVersion/Bible
  Gateway/Bible Hub candidates), still outbound-intent-only, no networking. What Sprint 13
  needs to know: `OpenReferenceUseCase` currently returns a **BLB URL only** and
  `ui/browser/CustomTabLauncher` is the single side-effect seam — generalize there;
  `BookCatalog.blbAbbrev` tokens are BLB-specific, so each provider needs its own verified
  book-token mapping (follow the Sprint 1 live-verification pattern; D-S9-1 derived
  `displayAbbrev` from BLB tokens — do not let provider tables drift); the new Settings
  section pattern (radio group / row + dialog) is established. Owner decisions in spec §10
  may need answers before build.
- **Queued/deferred (unchanged):** V1 ship (owner checklists: Sprint 9 + tracking-start +
  stats + now the reminder device pass above), toggle-from-widget, Psalm 119 verse-ranges,
  API 26–28 scrim check, deprecation housekeeping. New candidate: a
  TIME_SET/TIMEZONE_CHANGED receiver (see risks).
- **Scope protected out this sprint:** multiple reminders/day, per-weekday schedules,
  missed-day or streak-risk notifications, notification mark-as-read actions (parked with
  toggle-from-widget), exact alarms.

## Next sprint

`next: sprint-0013-bible-app-links`

## Open questions & risks

- **Owner tone sign-off pending** on the strings table above (incl. the "Psalms" vs
  "Psalm" body note) — all cheap `strings.xml` changes.
- **Timezone/clock changes** don't proactively re-arm alarms (no TIME_SET/TIMEZONE_CHANGED
  receiver): after a zone change the next fire can be off until the alarm next fires, the
  device reboots, or the app opens. Judged acceptable for V2; queued as a candidate ticket.
- **Force-stop clears alarms** (platform behavior): reminders resume on next app open
  (launch re-arm); the widget falls back to the 30-min periodic update meanwhile.
- Receiver work runs on `goAsync` + `Dispatchers.Default` with a ~10s broadcast budget —
  fine for a few DataStore/Room reads; do not grow logic inside receivers.
- Standing debt unchanged: Robolectric pinned `@Config(sdk = [34])`; JVM-untested
  MainActivity hooks; widget ignores in-app font scale (by design); CLI agent credentials
  still expired (owner: `claude /login` before the next unattended run). CI
  `release-bundle` not exercised (no commit per instructions).
