# Sprint 00O — Tap-to-mark-read (owner feature)

**Status:** ✅ DONE (uncommitted in the working tree at handoff time; the main session/owner
commits + ships). No version bump (stays 1.4.3/10403 — V2 WIP).

**Owner request:** "When you click to open a reading, it marks the reading as checked."

**Team:** Maya (PM) framed; Morgan (EM) planned; Sam implemented (T1–T3); Riley verified
(T4 gate + mutation pass). Orchestrated end-to-end with the PM/EM/eng team.

## What's true in working software now

On the Schedule, tapping a reading card to open it — in the in-app reader, on any external
Bible site (Blue Letter Bible / Bible Gateway / YouVersion), or in the MySword app — now
**marks that reading read for the displayed date** as a side-effect of opening it, and the
home-screen widget reflects it immediately. The user no longer makes a second trip to the
checkbox for the common "I opened it, I'm reading it" path. The checkbox remains the only
**un-mark** affordance. Everything that derives from per-reading marks — whole-day completion,
the widget, date-picker dots, streaks/stats, year strips — reflects auto-marks for free
through the existing seams (no new state model).

## Product decisions (Maya, owner-confirmed)

- **One-way SET, not toggle** — opening is a "read" signal, never "un-read". Tapping an
  already-read reading re-opens it and it stays read. The checkbox is the only un-mark.
- **All destinations** — in-app reader, external app, website. The intent is the same wherever
  it opens.
- **Mark on tap (when the open is initiated)**, not gated on a confirmed/successful open —
  external launches don't reliably report back and the in-app path is a tab switch; "intent to
  read" is captured by the tap itself. The MySword→BLB / app-not-installed fallback still
  results in an open, so mark-on-tap is correct there too. Accepted edge: a launch that throws
  with no fallback marks the reading though nothing opened (rare, harmless — un-check fixes it).
- **No new Settings off-switch** — the on-card checkbox is the visible one-tap undo. Owner
  confirmed "build as planned". (An opt-out toggle is queued as a future candidate only if a
  device pass surfaces real surprise.)

## Up-front decisions (Morgan)

- **D-O-1 — dedicated `MarkReadOnOpenUseCase`, not reuse of `ToggleReadingUseCase`.** The
  behavior is a one-way *set to read* (idempotent), semantically distinct from the toggle's
  flip. A dedicated use case makes the call site read truthfully (no `markRead` boolean to get
  wrong), gives mutation testing a clean target, and is the single seam a future opt-out setting
  would gate. Thin wrapper over `ProgressRepository.setRead(..., isRead = true, planId)` +
  active-plan resolution — same shape as `ToggleReadingUseCase`, zero new architectural risk.
- **D-O-2 — mark happens in `onReadingTapped` BEFORE destination resolution**, in the same
  `viewModelScope.launch`, so it lands uniformly for InApp / Web / MySwordApp and is never lost
  on an early-returning branch. The open is not blocked on the mark (both suspend in one
  coroutine; the mark is a fast local Room write).
- **D-O-3 — `onReadingTapped` receives the displayed `date`**, threaded exactly like
  `onToggleReading`. The card (`DayContent`/`ReadingCard`) keeps its `onReadingTapped:
  (Portion) -> Unit` signature — stateless and date-agnostic; the date is bound only at the
  pager page level in `DayReadingsScreen`.
- **D-O-4 — widget refresh after the mark**, reusing the existing
  `widgetRefresher.refreshTodayWidget()` already used by toggle / whole-day.
- **D-O-5 — Feb 29 / NoScheduledReadings unaffected** — those states render no cards, so
  `onReadingTapped` is unreachable; no guard needed.

## Implementation note (forced, mechanical)

The ticket prose said `setRead(..., markRead = true, ...)`, but the real
`ProgressRepository.setRead` parameter is named **`isRead`** (`ToggleReadingUseCase` compiles
only because it passes a local *variable* named `markRead` positionally into `isRead`). Sam
matched the real signature: `setRead(date, streamNumber, isRead = true, planId)`. Behaviorally
identical.

## Files

- **New** `app/src/main/kotlin/.../domain/MarkReadOnOpenUseCase.kt` — `@Inject(ProgressRepository,
  ActivePlanRepository)`; `suspend operator fun invoke(date, streamNumber)` → active plan id +
  `setRead(..., isRead = true, planId)`. No Hilt module change (constructor injection).
- `app/src/main/kotlin/.../ui/day/DayReadingsViewModel.kt` — new ctor dep `markReadOnOpen`;
  `onReadingTapped(date: LocalDate, portion: Portion)` marks + refreshes the widget before the
  unchanged `when (destination)` block.
- `app/src/main/kotlin/.../ui/day/DayReadingsScreen.kt` — `DayReadingsPagerScreen.onReadingTapped`
  is `(LocalDate, Portion) -> Unit`; the pager binds `{ portion -> onReadingTapped(date, portion) }`
  (mirrors the toggle). `DayContent`/`ReadingCard` unchanged.
- **Tests:** `DayReadingsViewModelTest.kt` (extended), `DayReadingsPagerScreenTest.kt`
  (extended), **new** `domain/MarkReadOnOpenUseCaseTest.kt`.

## Verification (Riley, re-verified by the main session)

- Full pipeline green from clean:
  `./gradlew spotlessCheck lintDebug assembleDebug testDebugUnitTest koverXmlReportAppDebug koverVerifyAppDebug`.
- **710 → 719 tests, net +9, 0 failures.** (The intentional behavior-change test was *renamed*,
  not added — see below.) Independently re-confirmed by the main session: 719 / 0 failures.
- **The three data/Room gates UNTOUCHED in count:** `ReadingPlanVerificationTest` = 11,
  `BibleTextVerificationTest` = 18, `BibleDatabaseRoomOpenTest` = 5 (M'Cheyne = 10).
- `AccessibilityGateTest` green (8/8) — card/checkbox targets and semantics untouched.
- Kover floor (70% on domain/data) holds — actual **96.0% line** on the scoped classes.
- **Intentional behavior change pinned honestly:** the old test "opening a reading on BLB does
  NOT refresh the widget" is now wrong (opening DOES refresh — mark-on-open side effect). It was
  renamed to "opening a reading on BLB now refreshes the widget — intended D-O-4 behavior
  change", asserting `refreshCount == 1`, not silently flipped.
- **Turbine note:** the mark-on-open re-emits a new `Scheduled` state, so five pre-existing
  destination/widget tests using nested turbines were resolved with
  `cancelAndIgnoreRemainingEvents()` (the destination is what they assert; the marking is
  asserted by the new 3a/3c/end-to-end tests). Correct production behavior surfacing in tests,
  not a bug.

### Mutations killed (introduced → observed red → restored byte-identical)

| # | Mutation | Killed by |
|---|----------|-----------|
| M1 | `MarkReadOnOpenUseCase`: `isRead = true` → `false` | MarkReadOnOpenUseCaseTest ×3, VM 3a/3b/3c, pager end-to-end |
| M2 | `onReadingTapped`: remove `markReadOnOpen(...)` | 3a, 3c, end-to-end |
| M3 | `onReadingTapped`: remove `widgetRefresher.refreshTodayWidget()` | 3d (clean single-test kill) |
| M4 | `onReadingTapped`: `date` → `today` | 3c (clean single-test kill) |
| M5 | `DayReadingsScreen` wrapper: `onReadingTapped(date, …)` → `(today, …)` | swipe-to-next-day pager pin |
| M6 | move `markReadOnOpen` + refresh inside the in-app branch only | 3a, 3c, 3d, end-to-end (proves mark fires for ALL destinations) |

## Scope guard / queued out

- **"Mark read when opening" opt-out Setting** — DataStore key + gate + reactive VM threading.
  Queued as a future candidate only if a device pass shows real surprise; NOT absorbed here.
- **Mark-only-on-successful-open** (don't mark if the external launch fails) — materially
  harder, racy (OS launch result isn't reliably observable); flagged, not planned.
- **The in-app reader's per-verse external tap-out (Sprint H)** is unrelated and untouched —
  this sprint is the *Schedule* reading-card tap only.

## Device-pass items (NOT JVM-provable)

- The on-glass feel of a single tap that both opens the destination and checks the box (no
  double-tap perception, no flicker).
- The home-screen widget actually refreshing read-state on a tap, on a real launcher.

No new strings, dependencies, permissions, Room/DataStore/manifest changes, or version bump.
