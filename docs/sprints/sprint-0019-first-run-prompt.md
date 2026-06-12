# Sprint 0019 — First-run tracking-start prompt (owner request)

**Status: GOAL MET.** Closed 2026-06-11. (Owner-redirected from `v2x-release-prep`, which
rolls forward to Sprint 20. CLI sub-agent dispatch still down — EM executed tickets directly
under per-ticket verification discipline. Working tree handed over **uncommitted** by
request; version deliberately untouched at **1.3.3/10303** — v1.3.3 was tagged/releasing
during this sprint; these changes ride the NEXT bump.)

## Goal outcome

**Met.** The first time the app is opened after a fresh install, the user is asked when
tracking should start — January 1 of the current year, today, or a custom date via the
existing full-calendar picker — with one plain sentence of meaning and the reassurance that
it's changeable in Settings. The answer is persisted and the question never returns. Your
phone, testers, and upgraders with marks are provably never asked. The silent D-S14-1
Jan-1 auto-write is gone as a first-run behavior; it survives only as the dismiss fallback.

## Current capability

- **Fresh install** → one M3 dialog over the day screen (`tracking-start-prompt`): three
  authored 48dp option rows ("Start from January 1, 2026" / "Start from today (Jun 10,
  2026)" / "Pick a date…"). Custom opens the shared S10 full-calendar
  `TrackingStartDatePickerDialog`; **canceling that picker is NOT an answer — it returns to
  the prompt**.
- **Dismiss (back/scrim)** → Jan-1 fallback applied silently, marker set, never re-shown.
- **Never shown** to already-initialized devices or upgraders with existing marks (the
  upgrader is marked initialized with the date left null = pre-S10 behavior).
- **Process death before answering re-asks next launch** — resolution persists nothing.
- No widget interplay (confirmed: the widget renders readings/completion, never missed
  state; normal refresh paths untouched).
- Verified: **340/340 tests** (net +12: 17 new, 5 retired with the old initializer; 7-test
  Sprint 1 gate untouched at 7/7), full pipeline green, **Kover 96.2%** on domain/data
  (floor 70%). **4 mutations killed, each by exactly its intended test, restored in
  place:** (1) initialized-marker guard dropped; (2) marks-exist guard dropped; (3) choice
  stops writing the marker; (4) dismiss applies today instead of Jan 1.

## Dialog strings — OWNER TONE SIGN-OFF NEEDED (PRD M8)

| id | string |
|---|---|
| `tracking_prompt_title` | "When should tracking start?" |
| `tracking_prompt_body` | "Days before your start date aren’t counted as missed. You can change this anytime in Settings." |
| `tracking_prompt_jan1` | "Start from January 1, %1$d" |
| `tracking_prompt_today` | "Start from today (%1$s)" |
| `tracking_prompt_custom` | "Pick a date…" |

(Title + body are LITERALLY pinned in `TrackingStartPromptDialogTest`. S12–S18 string
tables still await sign-off.)

## Decisions & rationale (do not relitigate)

- **D-S19-1 — First-run prompt replaces the silent initializer** (supersedes the auto-write
  half of D-S14-1; the Jan-1 default remains as the fallback). **Dismiss = Jan-1 applied
  silently + never re-shown** — least nagging, and a dismissive user gets exactly the
  superseded S14 behavior, so dismissal is never worse than before. An *unanswered* prompt
  (process death) re-asks; the marker is written only with an answer or a dismissal.
  Canceling the custom sub-picker is not an answer.
- **D-S19-2 — The gate lives in `DayReadingsViewModel`, not MainActivity.** Resolved once
  at VM creation (`ResolveTrackingStartPromptUseCase`), rendered by the day screen. The
  `MainActivity.onCreate` initializer hook is deleted — one less JVM-untested launch
  (`RescheduleAlarmsUseCase` remains the only domain hook there).

## State of the codebase (delta)

- **Domain:** `InitializeTrackingStartUseCase` DELETED; new
  `domain/ResolveTrackingStartPromptUseCase.kt` (pure gate, writes only the upgrader
  marker) + `domain/CompleteTrackingStartPromptUseCase.kt` (date + marker).
- **UI:** new `ui/day/TrackingStartPromptDialog.kt` (tags `tracking-start-prompt`,
  `tracking-prompt-jan1/-today/-custom`); `TrackingStartDatePickerDialog` extracted from
  `SettingsScreen.kt` to `ui/settings/TrackingStartDatePickerDialog.kt` (now `internal`,
  tags unchanged); `DayReadingsViewModel` gained `showTrackingStartPrompt: StateFlow<Boolean>`
  + `onTrackingStartChosen/onTrackingStartPromptDismissed`; `DayReadingsPagerScreen` gained
  three defaulted prompt params wired from `DayReadingsRoute`.
- **Tests:** `InitializeTrackingStartUseCaseTest` replaced by
  `domain/TrackingStartPromptUseCasesTest` (5); new `ui/day/TrackingStartPromptDialogTest`
  (5, incl. the literal copy pin); +4 VM prompt tests, +2 pager-screen tests, +1 a11y gate
  test (48dp option rows).
- DataStore keys, Room schema, manifest: all untouched.

## Carryover & next goal

- **Next goal (Sprint 20): V2.x release prep** — version bump past 1.3.3/10303, the
  consolidated device pass (everything queued since S9, now + **S19: fresh-install prompt
  appears once, dismiss behavior, upgrade-in-place never prompts**), string tone sign-offs
  (S12–S19), closed-track rollout via the tag-to-Play pipeline.
- **Queued/deferred (unchanged from S18):** colorblind-friendly strip palette; second-wave
  web providers; Logos/Olive Tree install detection; toggle-from-widget; Psalm 119
  verse-ranges; API 26–28 scrim check; TIME_SET/TIMEZONE_CHANGED receiver; deprecation
  housekeeping (`createComposeRule` v2); public requests channel; Priya's S16 notes.
- **Scope protected out:** any multi-screen onboarding; prompting existing devices;
  re-asking after dismissal; widget changes.

## Next sprint

`next: sprint-0020-v2x-release-prep`

## Open questions & risks

- The dismiss path (back/scrim → `onDismissRequest`) is pinned at the ViewModel level;
  the literal back-button gesture on glass is a device-pass item.
- Robolectric's en-US locale pins the "(Jun 10, 2026)" today-label format; other locales
  format via `ofLocalizedDate(MEDIUM)` (untested per-locale, same policy as Settings).
- Standing debt unchanged: Robolectric `@Config(sdk = [34])`; remaining JVM-untested
  MainActivity hooks (reschedule, edge-to-edge); CLI agent credentials expired
  (owner: `claude /login`); CI unexercised until commit.
