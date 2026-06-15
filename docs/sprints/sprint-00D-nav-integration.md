# V3 Sprint D — Nav restructure + integration

> **EM:** Morgan · **Status:** DONE (uncommitted; main session verifies + commits) ·
> **Date:** 2026-06-14 · **Next:** `sprint-00E-v3-hardening-release`

## Goal outcome — MET

**Tapping today's reading opens it in the in-app KJV reader, and Schedule + Bible are two
co-equal places in the app.** New installs are asked once where to read; existing users keep
their external choice and get a one-time "the in-app Bible is here" note. Every pre-existing
screen stays reachable, and part of the long-standing `AppNavHost` JVM-untested debt is retired.

## Current capability (working software)

- The app opens on a **co-equal bottom nav**: `Schedule` (the day pager, the start tab) and
  `Bible` (the in-app reader). Switching tabs preserves each tab's back-stack within a session
  (drill into Settings, switch to Bible and back — you land on Settings, not the pager).
- With **"Read in this app"** chosen (Settings → "Open readings in", or the first-run question),
  tapping any of today's three readings **switches to the Bible tab and renders that whole portion
  natively** (incl. the Jun 19 / Dec 19 two-book portion) — fully offline, no browser. Every other
  provider (BLB, Bible Gateway, YouVersion, MySword) behaves exactly as before.
- A **fresh install** is asked once, "Where would you like to read?" — in-app vs. external, both
  equal explicit choices (in-app is never silently pre-selected). An **existing user** (any prior
  marks) instead sees a one-time note that the in-app reader exists; their external destination is
  untouched unless they tap "Use the in-app reader".
- CI now **fails a release bundle that exceeds the +6 MB text-asset budget** (12 MB ceiling).

## Tickets (administrative record)

| Ticket | Status | What it delivered |
|---|---|---|
| VD-T1 `RootScaffold` + co-equal `NavigationBar`, Schedule start (D-V3-16) | ✅ | The two-tab root |
| VD-T2 nested Schedule/Bible graphs + tab state preservation (U18) | ✅ | `switchTab` saveState/restoreState |
| VD-T3 Robolectric nav-regression suite (D-V3-17, R-V3-5) | ✅ | 5 tests; retires part of Sprint-6 debt |
| VD-T4 `BibleProvider.IN_APP` + `ReadingDestination.InApp(portion)` (D-V3-18) | ✅ | Real provider + destination |
| VD-T5 `OpenReferenceUseCase` IN_APP branch + cross-graph tap-handoff (D-D-1) | ✅ | `ReaderHandoff` seam |
| VD-T6 Settings teaser → real `IN_APP` value | ✅ | Top-of-list selectable option |
| VD-T7 first-run reading-destination question (D-V3-19) | ✅ | Fresh-install one-time question |
| VD-T8 bundle-size CI gate (D-V3-20) | ✅ | 12 MB ceiling on `release-bundle` |
| VD-T9 one-screen-fit with the bottom bar (R-V3-1) | ✅ (device-pass) | Not JVM-provable; flagged for VE-T1 |
| VD-T10 one-time upgrade note (OQ-2) | ✅ | Existing-user informational note |
| VD-T11 CLAUDE.md + this handoff | ✅ | — |

487 tests (net +34; **both data gates untouched — plan gate = 7, `BibleTextVerificationTest` =
18**), full standing pipeline green from a clean build, Kover **95.1%** on domain/data (≥70%
floor), **4 load-bearing mutations killed**, each restored in place.

## Decisions & rationale (this sprint)

- **D-D-1 — `ReaderHandoff` (`@ActivityRetainedScoped`) is the cross-graph tap seam.** Schedule and
  Bible are separate nested graphs with separate ViewModel stores, so a tapped portion can't ride a
  route argument cleanly. `DayReadingsViewModel.onReadingTapped` resolves `InApp` → publishes the
  portion to `ReaderHandoff.request(portion)` + raises a one-shot `openReaderEvents`; the Route
  switches to the Bible tab; `ReaderViewModel` collects `handoff.pending`, consumes it (single-shot
  so a later config change doesn't re-open), and calls `openPortion`. The OS-launch channel
  (`openDestinationEvents`) never sees `InApp` — pinned.
- **D-D-2 — `InApp` is a navigation target, not an OS launch.** `launchReadingDestination` keeps a
  guarded `is InApp ->` log-only branch as a never-reached safety net; `Web`/`MySwordApp` paths are
  byte-for-byte unchanged.
- **D-D-3 — nested graphs, Schedule as start.** `Graph.SCHEDULE` (day-pager start + pushed Settings)
  and `Graph.BIBLE` (reader). `switchTab` uses `popUpTo(graph.findStartDestination().id){saveState}`
  + `restoreState` + `launchSingleTop` (U18). `isInGraph` drives tab selection via the destination
  hierarchy.
- **D-D-4 — extended first-run + a SEPARATE upgrade-note gate.** Two mutually-exclusive domain gates
  split on `ProgressRepository.hasAnyMarks()`: a fresh install (no marks) gets the reading-
  destination question; an upgrader (marks) gets the one-time note; never both. In-app is never a
  silent default — the question persists nothing until answered; dismiss re-asks next launch
  (D-V3-19). The note preserves the external provider unless the user opts in.
- **Nav glyph (OQ-3 placeholder):** Bible tab uses `Icons.AutoMirrored.Filled.List` — `MenuBook` is
  not in the frozen `material-icons-core` artifact. Finalized at owner sign-off in Sprint E (custom
  drawable if a book glyph is wanted, the `ic_stats.xml` pattern).
- **Nav-regression suite is structural, not Hilt-driven.** The project has no Hilt-under-Robolectric
  infra; standing it up was out of scope. The suite drives a `TestNavHostController` over a graph
  mirroring the production `Graph`/`Routes` constants and the production `switchTab`/`isInGraph`
  helpers (made `internal`), with tagged stand-in screen bodies. It proves topology + tab semantics
  + back-stack preservation without a VM graph.

## State of the codebase

New (under `app/src/main/kotlin/.../`):
- `ui/navigation/RootScaffold` (the two-tab root) + the rewritten `AppNavHost(navController, modifier)`
  (nested graphs) + `Graph` object — all in `ui/navigation/AppNavHost.kt`. `switchTab`/`isInGraph`
  are `internal` (the nav-regression suite drives them).
- `ui/navigation/ReaderHandoff.kt` — `@ActivityRetainedScoped` `request`/`consume`/`pending`.
- `domain/ResolveReadingDestinationPromptUseCase` + `CompleteReadingDestinationPromptUseCase` (fresh
  install), `domain/ResolveUpgradeNoteUseCase` + `CompleteUpgradeNoteUseCase` (existing user).
- `ui/day/ReadingDestinationPromptDialog.kt` + `ui/day/UpgradeNoteDialog.kt` (reuse the S19
  `PromptOptionRow`, now `internal`).

Edited:
- `MainActivity` calls `RootScaffold()` (was `AppNavHost()`).
- `domain/model/BibleProvider` (+`IN_APP`, first; `DEFAULT` still BLB; `fromStored` unchanged),
  `domain/model/ReadingDestination` (+`InApp(portion)`), `domain/OpenReferenceUseCase` (IN_APP
  branch BEFORE URL build), `data/reference/ProviderUrlBuilder` (IN_APP → `error(...)`, never
  reached).
- `data/prefs/SettingsRepository`(+Impl) + `FakeSettingsRepository`: markers
  `reading_destination_prompt_completed`, `upgrade_note_shown` (DataStore booleans; **no Room/schema
  change**).
- `ui/day/DayReadingsViewModel` (+`ReaderHandoff`, the InApp tap branch, `openReaderEvents`, the two
  prompt state-flows + handlers, prompt sequencing: tracking → reading-destination → upgrade-note),
  `ui/day/DayReadingsScreen` (Route collects the new events/states; PagerScreen renders the two new
  dialogs; the temp `open-reader-dev` action + `onOpenReader` params deleted).
- `ui/settings/SettingsScreen` (the `provider-option-inapp` teaser is now an enabled, top-of-list
  `IN_APP` option; the disabled teaser block removed).
- `ui/browser/CustomTabLauncher` (guarded `is InApp ->` safety branch).
- `res/values/strings.xml` (nav labels, first-run question, upgrade note — S-D, tone sign-off
  pending), `gradle/libs.versions.toml` + `app/build.gradle.kts` (`navigation-testing` test dep),
  `.github/workflows/ci.yml` (bundle-size gate).

Test tags introduced: `nav-schedule`, `nav-bible`, `reading-destination-prompt(-inapp/-external)`,
`upgrade-note(-use-it-now/-dismiss)`. Nav-regression stand-in tags: `screen-today/-settings/-reader`.

New tests: `domain/model/BibleProviderInAppTest` (6), `domain/ReadingDestinationPromptUseCasesTest`
(15), `ui/navigation/NavRegressionTest` (5, Robolectric), `ui/day/ReadingDestinationPromptDialogTest`
(3) + `UpgradeNoteDialogTest` (3), 2 new VM tests (InApp handoff), 1 new OpenReference test;
1 settings test rewritten (teaser→selectable).

## Strings for owner tone sign-off (S-D)

| Key | Current value |
|---|---|
| `nav_schedule` / `nav_bible` | "Schedule" / "Bible" (OQ-3 — also revisit "Today"/"Plan", "Read") |
| `provider_inapp` | "Read in this app" |
| `reading_destination_prompt_title` | "Where would you like to read?" |
| `reading_destination_prompt_body` | "You can read each day's chapters right here in the app, or open them in a Bible website or app. You can change this anytime in Settings." |
| `reading_destination_prompt_inapp` | "Read in this app" |
| `reading_destination_prompt_external` | "Open in a Bible website or app" |
| `upgrade_note_title` | "Read the Bible in the app" |
| `upgrade_note_body` | "You can now read each day's chapters right inside the app, fully offline. Your current reading destination is unchanged — switch anytime in Settings." |
| `upgrade_note_use_it_now` | "Use the in-app reader" |
| `upgrade_note_keep_current` | "Keep my current choice" |

## Carryover & next goal

Next goal: **V3.0 hardening + release** (`sprint-00E-v3-hardening-release`). Carryover into E:
- **VD-T9 device-pass:** one-screen-fit on a P7P at default font WITH the ~80dp bottom bar present
  (R-V3-1, owner-accepted cost). Not JVM-provable; rolled into VE-T1's consolidated device pass.
- The consolidated device pass also inherits A/C items (real `createFromAsset` copy + re-copy;
  reader reading-feel U15, instant load U13, large font; markup look M-V3-2) plus the new
  tab-state-preservation and reading-tap→reader-handoff on-glass checks.
- OQ-3 nav label/icon finalization (incl. the Bible glyph) + all S-A..S-D string tone sign-offs.
- Version bump past 1.3.5/10305, UK accepted-risk recorded, closed-track rollout.

## Open questions & risks / tech debt

- **`openPortion` doesn't update the reader's browse cursor** (the SavedStateHandle (book, chapter)),
  so after a portion open, Prev/Next resumes from the last *chapter* cursor, not the portion. This
  is pre-existing Sprint-C behavior, acceptable for V3.0; a contained V3.x polish if owner wants
  Prev/Next to continue from the portion's last chapter.
- **`hiltViewModel` deprecation warnings** persist (pre-existing, matched intentionally across the
  Routes; not new debt).
- The nav-regression suite proves graph topology/semantics but not the real Hilt-backed Routes
  end-to-end (no Hilt-Robolectric infra). The reading-tap→reader handoff *logic* is unit-pinned in
  the VM; the *visual* hop is a device-pass item.

## Next sprint

`next: sprint-00E-v3-hardening-release`
