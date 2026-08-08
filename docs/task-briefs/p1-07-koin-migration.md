# p1-07 — Hilt → Koin. Alone, in its own release.

> **Assignee:** Senior Shared-Core Engineer (drives) + Verification Engineer (the `checkModules`
> gate and the ViewModel-construction smoke)
> **Release:** **1.10.0 — this task is the entire release.** Still one `:app` module. No module
> split, no persistence change, no asset move.
> **Merge order:** Group D, **strictly after `p1-06`.**
> **Inherits:** [`p1-00-overview.md`](p1-00-overview.md) rules R1–R7.
> **Preconditions:** 1.9.0 live on Play with **zero related crash signal in vitals for 24–72 h**.
> `p1-06` merged. `gate0-minor-spikes.md` **M2 answered** (`SavedStateHandle` via `koinViewModel()`).
> **Executes:** ADR-0012.

---

## Objective

Replace Hilt with Koin across the whole app, preserving the object graph, every scope's observable
behaviour, and every existing test — and add the two mitigations without which **ADR-0012 states
this decision is materially worse than Hilt.**

---

## Context

### Why this is alone in a release

Its failure mode is **the 1.7.0 class**: a runtime crash that R8 exposes and debug builds hide.
Hilt resolves the graph **statically at compile time**; Koin resolves **lazily and reflectively at
first use**. A missing binding becomes a runtime crash at first resolution instead of a build
error, and the moment of first resolution is exactly the kind of timing R8 shifts.

Risk R3 rates it HIGH/MEDIUM. It is isolated for the same reason 1.9.0 and 1.11.0 are isolated: a
crash spike with one candidate cause is diagnosable in an afternoon; with four it is a week.

### The graph, measured on `main`

- **9 modules:** `AppModule`, `BibleModule`, `BibleRemoteModule`, `DataModule`, `DispatcherModule`,
  `ReminderModule`, `RepositoryModule`, `UpdateModule`, `WidgetModule`
- **67 `@Inject`** annotations · **14 `@Singleton`**
- **23 Android-specific sites**, of which the load-bearing ones are enumerated below
- `@ApplicationContext Context` injected in 16 files

### The three scopes that are load-bearing — get any of these wrong and you reintroduce a fixed bug

**1. `@Singleton` (14).** Both databases, the DataStore, `ReadingPlanRepository` (with its
**per-plan single-flight cache under a mutex** — a second instance means a second parse and a
second cache), `BibleAssetGate`, the FUMS identity. → Koin `single { }`. Straightforward.

**2. `@ActivityRetainedScoped ReaderHandoff`** (`ui/navigation/ReaderHandoff.kt:28`). **One
instance shared between `DayReadingsViewModel` (producer) and `ReaderViewModel` (consumer)**,
surviving configuration change. It carries a **one-shot pending value** with **mutually exclusive
signals** — `request(portion)` versus `requestBrowse()`, where each supersedes a stale other
(D-I-2, sprint 00I).

> **This is the object at the centre of the 1.7.0 crash and the sprint-00I tab-reset behaviour.
> Promoting it from activity-retained to a process `single { }` is a REAL behaviour change:** today
> a true process-level teardown loses the handoff; as a singleton it would persist.
>
> Given it carries a one-shot consumed on read, and given iOS has no activity concept at all, a
> singleton is the right *shape*. **But the consumption semantics must be re-verified against the
> D-I-2 tests, not assumed.** If a stale pending portion can survive into a later session and
> hijack the Bible tab, that is a regression. **Write the test that proves it cannot.**

**3. `@ActivityRetainedScoped InAppUpdateState`** (`update/InAppUpdateState.kt:26`) and
`PlayInAppUpdateManager` (`:52`). `InAppUpdateState` holds a **process-lifetime no-nag flag that is
deliberately NOT persisted** (D-L-5). → `single { }` in the **Android-only** module; Play In-App
Updates does not exist on iOS.

### The four framework-boundary sites

| Site | Today | Ported |
|---|---|---|
| `MainActivity` | `@AndroidEntryPoint` | Koin lookup / `koinViewModel()` |
| `ReminderAlarmReceiver`, `BootReceiver` | `@AndroidEntryPoint` | Koin lookup in `onReceive` |
| `widget/TodayWidget.kt:27,56` | `@EntryPoint` + `EntryPointAccessors.fromApplication` | Direct Koin lookup from the widget's Android context |
| `AppNavHost` ×4 | `hiltViewModel()` | `koinViewModel()` |

The widget's `@EntryPoint` reaches `GetDayReadingsUseCase` and `Clock`/`DateProvider` from a Glance
`GlanceAppWidget` — no Activity, no ViewModel. It gets a direct Koin lookup. Slightly less pretty,
functionally identical.

---

## Contract

### The rule that makes Koin safe here

> **No `by inject()` and no service location inside domain, data or UI classes. Everything is
> constructor injection, exactly as today.**

Koin's ability to do service location is the thing that makes it dangerous in a codebase this
disciplined. **Forbid it rather than rely on discipline.** `@Inject constructor` annotations are
simply deleted; Koin uses explicit factory lambdas. Koin's API appears in exactly two kinds of
file: the module declarations, and each platform's start-up.

### Scope mapping

| Hilt | Koin |
|---|---|
| `@Singleton` | `single { }` |
| `@ActivityRetainedScoped ReaderHandoff` | `single { }` **+ the explicit clearing test below** |
| `@ActivityRetainedScoped InAppUpdateState` / `PlayInAppUpdateManager` | `single { }`, Android-only module |
| `@HiltViewModel` | `viewModel { }` |
| `@AndroidEntryPoint` / `@EntryPoint` | Android module + a direct lookup at the framework boundary |
| `@IoDispatcher` qualifier | Koin named qualifier |

### Module organisation — write it for where it is going

Declare modules in files that mirror the eventual `shared/data`, `shared/ui` and `androidApp`
split, even though everything is still in `:app`. `p2-02` then moves files rather than re-deciding
ownership.

### The two mandatory mitigations — **ADR-0012 says this decision is worse than Hilt without them**

**(a) `checkModules()` / `verify()` as a CI unit test.** Every binding resolvable, every ViewModel
constructible. This restores automated graph verification — at test time rather than compile time —
and is the direct replacement for the thing Hilt was doing for free.

**(b) A ViewModel-construction smoke test.** Instantiate **every** ViewModel through Koin and
assert it constructs without throwing. This is the automated counterpart to the R8 device smoke and
it is where a `SavedStateHandle` wiring failure surfaces.

Neither is optional. **If either cannot be made to work, that is an escalation** — and the honest
fallback is kotlin-inject (ADR-0012's rejected-but-close alternative, KSP-based and compile-time
verified), not "ship Koin without the check."

---

## Acceptance criteria

1. `grep -rn "dagger\|javax.inject\|hilt" app/src/main/kotlin` returns **nothing**.
2. `grep -rn "by inject()\|KoinComponent\|getKoin()" app/src/main/kotlin` returns hits **only** in
   `MainActivity`, the two broadcast receivers, `widget/TodayWidget.kt`, and the Koin start-up file.
   **Zero** in `domain/`, `data/`, `bible/`, `ui/`.
3. **`checkModules()` runs as a CI unit test and passes.**
4. **A ViewModel-construction smoke test constructs every ViewModel through Koin**, including
   `ReaderViewModel` **with a real `SavedStateHandle`**.
5. **The `ReaderHandoff` singleton-promotion test exists and passes:** a pending portion consumed
   once is **not** re-consumable, and cannot survive to hijack the Bible tab in a later session.
   The D-I-2 mutually-exclusive-signal tests (`request` supersedes a stale `requestBrowse` and vice
   versa) pass unchanged.
6. **`p1-06`'s init-order tests pass**, and are re-verified by moving a `MutableStateFlow`
   declaration below `init` and confirming red. **Koin changes construction timing; this is the
   check that the detector still detects.**
7. `ReadingPlanRepository`'s single-flight cache is proven single-instance — the same instance
   resolves twice and the asset parses **once**.
8. **The widget still works.** `TodayWidget` resolves `GetDayReadingsUseCase` from Koin; its 8
   Glance unit-rig tests and 3 refresh-hook tests pass.
9. **≥4 killed mutations, each restored byte-identically:** (a) a binding removed → `checkModules()`
   red; (b) `ReaderHandoff` declared `factory { }` instead of `single { }` → the handoff tests red;
   (c) `ReadingPlanRepository` declared `factory { }` → the single-parse test red; (d) the
   `SavedStateHandle` wiring broken → the construction smoke red.
10. **Test count strictly increases. Zero deletions.** State before/after.
11. Full pipeline green with `--rerun-tasks`; Kover ≥ the current floor.
12. **The six data gates untouched, counts unchanged: 11 / 10 / 8 / 6 / 18 / 5.**
13. **`bundleRelease` builds clean**, R8 rules reviewed (**Koin uses reflection — confirm no
    consumer rule is needed, or add one and say why**), and the AAB reported against the 12 MB gate.
14. **R5 R8 release-build device smoke — MANDATORY, and this is the acceptance criterion that
    matters most in this brief.** `assembleRelease`, installed on a device, exercised by hand:
    1. Cold launch — no crash.
    2. **Tap a reading on the Schedule → the reader opens on the right chapter.** This is the exact
       1.7.0 crash path and the exact object (`ReaderHandoff`) whose scope changed.
    3. Bible tab → picker → pick a chapter → change version. No jump to Genesis 1.
    4. Long-press a verse → selection → Copy.
    5. Toggle a reading → **the home-screen widget updates** (proves the widget's Koin lookup).
    6. Settings → change plan → the whole app follows.
    7. Enable the reminder; set it two minutes out; **wait for it to fire** (proves the receiver's
       Koin lookup).
    8. Reboot the device; confirm the persistent tray notification returns (proves `BootReceiver`).
    **Steps 5, 7 and 8 exercise the three framework-boundary lookups that have no ViewModel and no
    JVM test. They are not optional.**
15. **Watch Play vitals for 24–72 h after the 1.10.0 rollout before `p2-*` work is tagged.**

---

## Boundaries / write set

**Yours:** all 9 files in `di/`; every file carrying a Hilt annotation (67 `@Inject` sites, plus
`@HiltViewModel`, `@AndroidEntryPoint`, `@EntryPoint`, `@ActivityRetainedScoped`); `MainActivity`;
`DailyReadingsApp`; `widget/TodayWidget.kt`; `ui/navigation/{AppNavHost,ReaderHandoff}.kt`;
`update/**`; `reminders/{ReminderAlarmReceiver,BootReceiver}.kt`; and the corresponding tests.

**Not yours:**
- **Any use-case, repository or composable body.** Deleting an `@Inject` annotation from a
  constructor is in scope; changing what the constructor takes is not.
- `app/src/main/assets/**`, `app/schemas/**`.
- `gradle/libs.versions.toml`, `app/build.gradle.kts` — **Build & Release.** Koin is a new
  dependency and Hilt/KSP removal is theirs to make.
- `AndroidManifest.xml` — receivers stay non-exported and no permission changes. If a manifest
  change seems needed, **escalate**.

---

## Escalation triggers

- **`checkModules()` cannot be made to work** → **Staff**, blocking. Without it this decision is
  worse than Hilt, and the fallback is kotlin-inject, not a weaker gate.
- **`koinViewModel()` cannot supply a `SavedStateHandle`** → **Staff**, blocking. M2 should have
  caught this; if it reappears here, the reader's last-read position (D-V3-13) is at stake and the
  alternatives change persistence semantics — not an implementer's call.
- **Any behaviour difference in `ReaderHandoff` consumption** → **Staff**, blocking. This object
  sits at the centre of a shipped P0 and a shipped feature.
- **The R8 device smoke crashes anywhere** → **Staff + EM**, blocking. Do not tag. Diagnose via
  `adb logcat -b crash` and retrace with `app/build/outputs/mapping/release/mapping.txt` —
  **verify `pg_map_id` matches the `r8-map-id` in the stack first**, then translate frames by
  grepping `^<orig> -> <obf>:`. That procedure is recorded in CLAUDE.md and it works.
- **Effort exceeds the estimate by more than half** (ADR-0012 budgets 1.5–2 weeks) → **EM + Staff**.
