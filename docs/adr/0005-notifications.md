# ADR-0005 — Notification and scheduling strategy on iOS

**Status:** **ACCEPTED** (owner sign-off 2026-08-08, via `ios-port-approach.md` §10 #3)
· **Date:** 2026-08-08 · **Author:** Staff / Port Architect
· **Amended:** 2026-08-08 — see [Amendment A1](#amendment-a1--the-reminder-body-is-generic-owner-decision)

> **Read the amendment before the body.** The escalation at the foot of this ADR is **closed**,
> and option (c) — which the body offers as a compromise — has been found **impossible**.

## Context

Android ships **two separate notification features** and **three standing alarms**.

**Feature 1 — the daily reminder** (sprint 12, FR-19…23). Off by default. User picks a time
(default 08:00). At that time one notification posts: "Today's readings" + the day's collapsed
references. Key design point **D-S12-3: the decision is made at fire time** — the alarm wakes
`DeliverDueReminderUseCase`, which runs the same `GetDayReadingsUseCase` the UI uses and
**suppresses the notification if the day is already complete** (R-REM-4) or if it is Feb 29
(R-REM-5).

**Feature 2 — the persistent tray notification** (sprint 22). **ON by default** (amended
D-S22-5). An `setOngoing(true)`, `IMPORTANCE_LOW`, `PRIORITY_MAX`, `CATEGORY_STATUS`
notification that is always present, non-dismissible, lists the day's readings in `BigTextStyle`,
and **refreshes at 01:00 local** via its own dedicated alarm so it rolls over to the new day.

**Three alarms**, all inexact `setAndAllowWhileIdle(RTC_WAKEUP)` (D-S12-1): the reminder, a
midnight widget refresh (FR-23), and the 01:00 persistent refresh. All three are re-armed after
each fire, on `BOOT_COMPLETED`, and on every app launch (`RescheduleAlarmsUseCase` from
`MainActivity.onCreate`).

**What iOS provides.** `UNUserNotificationCenter` with local notification requests.
`UNCalendarNotificationTrigger(dateMatching:repeats:)` fires on a wall-clock match and survives
reboot with no boot receiver. **A hard cap of 64 pending notification requests per app.** And the
constraint that decides everything here: **iOS does not execute your code when a local
notification fires.** Content is fixed at scheduling time.

There is no `AlarmManager`, no `BOOT_COMPLETED`, no background wake at 01:00 for a
non-time-sensitive purpose, and no ongoing/non-dismissible notification.

## Decision

### The daily reminder — **ships on iOS, with one behaviour divergence**

**One** `UNCalendarNotificationTrigger` with `dateComponents(hour:minute:)` and
`repeats: true`. That costs **exactly one of the 64 slots** and repeats forever.

- **Content is regenerated whenever the app can run**: on foreground, and after any progress
  mutation. The regeneration removes the pending request and re-adds it with today's (and, if we
  choose, tomorrow's) reference text. Because the plan is date-anchored and fully deterministic,
  the reference text for any future date is computable — that half is free.
- **Suppression-when-complete (R-REM-4) is dropped on iOS.** It cannot be honoured: at 08:00
  tomorrow the app is not running and cannot consult progress. This is a real divergence and it
  goes in `docs/parity-matrix.md`. It is acceptable because the copy is already deliberately
  gentle (PRD §13.0: no emoji, no urgency, no streaks) — a reminder on a finished day is
  redundant, not shaming.
- **Feb 29 suppression (R-REM-5) is honoured**, because it is deterministic. In a leap year,
  skip Feb 29 by scheduling around it (see consequences).
- Authorization maps `NotificationPermissionChecker` onto
  `UNUserNotificationCenter.requestAuthorization`. The existing denial handling (setting stays
  off, explanation dialog, path to system settings — R-REM-7) transfers unchanged.

### The persistent tray notification — **does not ship on iOS**

There is no iOS mechanism for a non-dismissible, always-present, app-refreshed notification.
Live Activities are time-bounded event UI with an ~8–12 hour ceiling and an ActivityKit lifecycle
designed for things that end; using one for "here are today's readings, forever" is an abuse that
will read as one. **On iOS, the Settings toggle simply does not exist**, and the shared
`SettingsUiState` omits the row via a `shared/platform` capability flag — not an `if (isIOS)` in
the composable.

Its iOS answer is a **WidgetKit widget** (ADR-0006), which delivers the same user value
— today's readings glanceable without opening the app — through the mechanism iOS actually
provides.

### The three alarms

| Android alarm | iOS |
|---|---|
| Daily reminder | one repeating `UNCalendarNotificationTrigger` |
| Midnight widget refresh (FR-23) | **not needed** — WidgetKit timelines handle date rollover natively via `TimelineProvider` entries; if no widget ships, nothing to refresh |
| 01:00 persistent refresh | **dies with the feature** |
| `BOOT_COMPLETED` re-arm | **not needed** — repeating triggers survive reboot |
| Launch re-arm (`RescheduleAlarmsUseCase`) | **kept and reused** — this is where iOS regenerates content |

`RescheduleAlarmsUseCase` therefore stays in `shared/domain` and gains real importance on iOS: it
is the only moment content can be refreshed. Rename it in the port to something honest —
`SyncScheduledRemindersUseCase` — since "alarms" is an Android word and this interface is now the
contract both platforms implement.

## Alternatives rejected

**Schedule 64 one-shot notifications with pre-computed per-day content, refreshed on
foreground.** This is the standard workaround and it would preserve per-day reference text
without needing a foreground. Rejected: 64 requests is a ~2 month horizon; a user who does not
open the app for two months silently stops being reminded — which is precisely the user the
reminder exists for. It also consumes the entire notification budget for one feature and
reintroduces exactly the staleness the Android design engineered away. The single repeating
trigger never expires.

**Background App Refresh (`BGAppRefreshTask`) to update content and honour completion
suppression.** Rejected. iOS grants background refresh opportunistically based on usage
heuristics — it is explicitly not a schedule. Building a correctness-relevant behaviour on a
best-effort budget produces a feature that works on the developer's phone and not the user's.
It also requires the Background Modes entitlement, which the owner's bundle-ID setup
deliberately leaves empty (RELEASING-IOS.md Step 2).

**Silent remote push to refresh notification content.** Rejected outright: requires a push
server, an APNs certificate, the Push Notifications capability, and a privacy-questionnaire
answer about a network service that currently does not exist. The app has no backend beyond the
read-only Bible proxy. Enormous cost for a marginal behaviour.

**Live Activity for the persistent notification.** Rejected — see above. Also requires
`ActivityKit`, a widget extension, and it appears on the Lock Screen and Dynamic Island in a form
that implies an in-progress event.

## Consequences accepted

- **iOS reminders fire on days already completed.** Documented divergence. Mitigated by gentle
  copy. If the owner objects, the only honest fix is to also drop the reminder on iOS, which is
  worse.
- **Reference text can go stale** if the app is not opened for a long time. Mitigation: schedule
  the reminder body generically ("Today's readings — open to see today's chapters") rather than
  with specific references, **or** accept a staleness window. **This is an owner decision I am
  not making alone** — see escalation below.
- **Feb 29 handling costs complexity.** A single repeating trigger fires on Feb 29 like any other
  day. Options: accept one wrong reminder every four years, or in leap years switch to explicit
  per-day requests for a short window around Feb 29. Recommendation: **accept it**, and make the
  body generic on that day if the app happens to be foregrounded. Four-year cadence, zero user
  harm, and a leap-year special case is exactly the kind of complexity this project has correctly
  refused elsewhere.
- **The persistent notification is the single largest feature loss of the port**, and it is ON by
  default on Android, so it is a feature a majority of Android users currently have. iOS users
  will not.
- `ReminderScheduler`'s current 5-method interface is Android-shaped
  (`scheduleMidnightRefresh`, `schedulePersistentRefresh`, `cancelPersistentRefresh`). It must be
  redesigned to semantics before it moves to `shared/platform`. Staff writes the replacement; do
  not port it as-is.

---

**ESCALATION: iOS reminder body — specific references (can go stale) or generic text (never
wrong)?**
Role to resolve: Owner (product), via Maya if she is engaged
Blocking: no — Phase D, not Phase B
Context: Android's reminder body lists the day's actual references ("Genesis 1–2 · Psalm 1–2 ·
Matthew 1–2"), decided at fire time. iOS fixes content at schedule time, so the body is only
correct until the calendar moves past the scheduled day. Refreshing on every foreground keeps it
correct for regular users and wrong for lapsed ones — who are exactly the users a reminder targets.
Options considered:
(a) **Generic body** — "Today's readings are ready" — never wrong, less useful, diverges from
Android's copy. (b) **Specific references, refreshed on foreground** — matches Android when the
app is used, silently wrong for a lapsed user. (c) **Specific references + a scheduled horizon of
N explicit requests** (N ≪ 64, say 14) refreshed on foreground, falling back to a generic
repeating request beyond the horizon — correct for two weeks, correct-but-generic after, costs 15
of 64 slots and real complexity.
Recommendation: **(a) generic body for v1.0.** It is the only option that is never wrong, it costs
one notification slot, and it sidesteps Feb 29 entirely. Revisit with (c) if the owner finds it
too thin.

**RESOLVED — see Amendment A1. The owner chose (a). Option (c) does not exist.**

---

## Amendment A1 — the reminder body is generic (owner decision)

**Date:** 2026-08-08 · **Author:** Staff / Port Architect
**Supersedes:** the escalation above, and the "Reference text can go stale" consequence.

### A1.1 The decision

**iOS v1.0 ships option (a): ONE repeating `UNCalendarNotificationTrigger` with a generic body.**
The owner signed this off against `ios-port-approach.md` §10 #3.

Consequences of that choice, stated as behaviour rather than as caveats — these are now the
specification, and they belong verbatim in `docs/parity-matrix.md`:

| Behaviour | Android | iOS v1.0 |
|---|---|---|
| Notification body | The day's collapsed references — "Genesis 1–2 · Psalms 1–2 · Matthew 1–2" | A fixed, reference-free line ("Today's readings are ready" — final wording is owner tone sign-off, not an engineering choice) |
| Decided at | Fire time (D-S12-3) | Schedule time, once |
| Suppressed when the day is already complete (R-REM-4) | Yes | **No** |
| Suppressed on Feb 29 (R-REM-5) | Yes | **No** — and it does not matter, because a generic body is not wrong on Feb 29 |
| Goes stale if the app is not opened | N/A | **Never.** This is the whole point of the choice |
| Pending-request budget | N/A | **1 of 64** |
| Survives reboot | via `BOOT_COMPLETED` re-arm | natively; no boot receiver |

Because the body carries no date-specific content, **the entire content-regeneration machinery in
the body of this ADR is not needed for v1.0.** `RescheduleAlarmsUseCase` (renamed
`SyncScheduledRemindersUseCase`) still runs on launch, but on iOS its only job is to reconcile the
*enablement* and the *time* with the single pending request — not to refresh text. Do not build
the refresh path "because we will want it later"; it is dead code until option (b) is scheduled.

**The Feb 29 complexity in "Consequences accepted" is now moot** and should be treated as
withdrawn. There is no leap-year special case on iOS. Delete it from any downstream brief that
inherited it.

### A1.2 The finding that removes option (c) from the table — D-PORT-2

**Option (c) in the "Alternatives rejected" section — "N explicit dated requests, falling back to
a generic repeating request beyond the horizon" — is not implementable. It was my error to offer
it as a compromise.**

`UNCalendarNotificationTrigger` has **no start date and no end date**. It matches
`DateComponents` and fires every time the wall clock matches, forever. There is no API to
exclude specific dates from a repeating trigger, and there is no "begins on" parameter.

Therefore, inside the horizon, the repeating request and each dated one-shot request are **two
distinct `UNNotificationRequest`s with two distinct identifiers**, and iOS delivers both. The user
gets **two notifications every morning** for the length of the horizon, then one after it. That
is not a graceful degradation; it is a duplicate-notification bug with a scheduled start date.

**There is no hybrid. The choice is binary: (a) or (b).**

> **Evidence status: INFERRED.** This conclusion is read off the `UNNotificationTrigger` /
> `UNCalendarNotificationTrigger` API contract. **Nobody on this team has executed it.** No one
> here has an Apple Developer account or Xcode at the time of writing.
>
> **It must be confirmed by a spike in Phase 4** before it is cited as a reason for anything. The
> spike is three lines: schedule one repeating calendar trigger for `hour:minute` and one dated
> one-shot `UNCalendarNotificationTrigger` for tomorrow at the same `hour:minute`, both with
> distinct identifiers, and observe how many notifications arrive. If **one** arrives, this
> amendment is wrong, option (c) is live again, and ADR-0005 is reopened.
>
> Recorded in `docs/parity-matrix.md` as INFERRED, not as fact.

### A1.3 Option (b) is not rejected — it is deferred with a known design

The iOS Platform engineer's mechanism for (b) is sound and was adjudicated as such: today's
progress can only reach "complete" through a foreground interaction (marking is in-app only — the
widget is read-only per D-S7-4, there are no notification actions, and sprint-00O tap-to-mark
happens on the day screen), and cancelling a pending `UNNotificationRequest` is synchronous and
persists across process death. So suppression **is** patchable at every moment it can change.

**"Suppression is lost on iOS" — as this ADR's body states it — is too strong, and the record
should not say it.** What is true is narrower and is the reason (a) wins for v1.0:

> Suppression is obtainable only by abandoning the single repeating trigger for a rolling window
> of ~60 dated one-shot requests, which introduces a **silent horizon cliff**: a user who does not
> open the app for longer than the window stops being reminded, with no signal. This project's
> standing discipline is to refuse silent failure.

If option (b) is ever scheduled, its design is already known: identifiers
`reminder-<planId>-<epochDay>`, a rolling window refreshed on foreground, and a five-event
invalidation set (enable, time change, plan change, progress mutation, day rollover). Record it as
a designed post-1.0 upgrade, not as an impossibility.

### A1.4 What this changes downstream

- `docs/parity-matrix.md` gains the A1.1 table, plus the INFERRED marker from A1.2.
- The Phase 4 notification brief gains the A1.2 spike as its **first** acceptance criterion.
- Any brief inheriting "regenerate the notification body on foreground" — **delete that
  requirement.** It is not v1.0 work.
- The reminder body string is a **new user-visible string** and joins the owner's tone sign-off
  backlog. It is not the engineer's to choose.
