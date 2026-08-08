# ADR-0012 — Dependency injection after Hilt

**Status:** Draft, pending owner sign-off · **Date:** 2026-08-08 · **Author:** Staff / Port Architect

## Context

The app is fully wired with Hilt: **9 modules**, 61 `@Inject constructor`, 14 `@Singleton`, and 23
Android-specific annotation sites (`@HiltViewModel` ×several, `@AndroidEntryPoint` on
`MainActivity` and both broadcast receivers, `@EntryPoint` for the Glance widget,
`@ActivityRetainedScoped` on `ReaderHandoff` and `InAppUpdateState`, `hiltViewModel()` at 4 nav
call sites).

**Hilt is Android-only and will not gain multiplatform support** — it is built on Dagger's
Android-specific component hierarchy (Application / Activity / ViewModel scopes).

Three scoping behaviours in the current graph are load-bearing and must survive:

1. **`@Singleton`** — the two databases, the DataStore, the plan repository (with its per-plan
   single-flight cache), `BibleAssetGate`, the FUMS identity. Straightforward.
2. **`@ActivityRetainedScoped` `ReaderHandoff`** — one instance shared between
   `DayReadingsViewModel` (producer) and `ReaderViewModel` (consumer), surviving configuration
   change. It carries a **one-shot pending value** with mutually-exclusive signals
   (`request(portion)` vs `requestBrowse()`, where each supersedes a stale other — D-I-2). Getting
   its lifetime wrong reintroduces a bug the project already fixed.
3. **`@ActivityRetainedScoped` `InAppUpdateState`** — same pattern, and it holds a
   **process-lifetime no-nag flag** deliberately *not* persisted (D-L-5).

## Decision

**Koin, with one constraint: the object graph is assembled in exactly one place per platform,
and `shared/domain` / `shared/ui` never reference Koin's API directly.**

Concretely:
- Modules are declared in `shared/data` and `shared/ui` as Koin module definitions.
- ViewModels are obtained through the multiplatform `koinViewModel()` helper at the same 4 nav
  call sites `hiltViewModel()` occupies today.
- `androidApp` and `iosApp` each start Koin once with the shared modules plus their own platform
  module (the actuals for the ~15 `shared/platform` interfaces).
- **No `by inject()` / service-location inside domain or UI classes.** Everything is constructor
  injection, exactly as today. Koin's ability to do service location is the thing that makes it
  dangerous; forbid it rather than rely on discipline.

**Scope mapping:**

| Today | Ported |
|---|---|
| `@Singleton` | Koin `single { }` |
| `@ActivityRetainedScoped ReaderHandoff` | Koin `single { }`, with **explicit clearing** |
| `@ActivityRetainedScoped InAppUpdateState` | `androidApp`-only `single { }` (Play In-App Updates does not exist on iOS — see port-inventory §3.15) |
| `@HiltViewModel` | Koin `viewModel { }` |
| `@AndroidEntryPoint`, `@EntryPoint` | `androidApp` module + direct Koin lookup at the framework boundary (Activity, BroadcastReceiver, Glance widget) |

**The `ReaderHandoff` scope change needs care and is called out as its own task.** Promoting it
from activity-retained to a process singleton is *behaviourally different*: today, if the activity
is destroyed and recreated (not a config change — a true process-level teardown), the handoff is
gone. As a singleton it would persist. Given it carries a one-shot pending value that is consumed
on read, and given iOS has no activity concept at all, a singleton is the right shape — **but
the consumption semantics must be re-verified against the D-I-2 tests, not assumed.** If a stale
pending portion can survive into a later session and hijack the Bible tab, that is a regression.
Write the test that proves it cannot.

## Alternatives rejected

**kotlin-inject (+ kotlin-inject-anvil).** Genuinely the closest philosophical match: KSP-based,
**compile-time verified**, multiplatform, constructor-injection-first. For a codebase this
test-disciplined, losing Dagger's compile-time graph verification is a real cost, and
kotlin-inject would keep it. Rejected on ecosystem risk rather than merit: it is a much smaller
project with fewer users, less documentation, and a smaller pool of examples for the
Compose-Multiplatform-ViewModel integration this app needs at 4 nav call sites. **This is the
alternative I would most readily be argued back into**, and if the team has kotlin-inject
experience it is the better technical choice.

**Manual constructor wiring in a composition root.** Zero dependencies, zero magic, fully
compile-time-checked, and honestly viable at this size — the graph is maybe 60 objects. Rejected
because ViewModel creation across a `NavHost` needs a factory mechanism anyway, and hand-rolling
one that handles `SavedStateHandle` (which `ReaderViewModel` uses) is reinventing the part of the
framework that is actually hard. Reconsider if Koin proves troublesome.

**Keep Hilt on Android and use something else on iOS.** Rejected outright — two graphs, two sets
of module definitions for the same shared objects, guaranteed to drift. This is exactly the
duplication the port exists to avoid.

## Consequences accepted

- **Compile-time graph verification is lost.** A missing binding becomes a runtime crash at
  first resolution instead of a build error. Mitigations, both mandatory: (a) Koin's
  `checkModules()` / `verify()` run as a **unit test** in CI, so the graph is still checked
  automatically, just at test time rather than compile time; (b) a smoke test that constructs
  every ViewModel. Without (a) this decision is materially worse than Hilt and should not be
  taken.
- **All 9 DI modules are rewritten** and 23 annotation sites change. Mechanical but broad.
  `@Inject constructor` annotations can simply be deleted — Koin uses explicit factory lambdas.
- **Koin is a new runtime dependency** on a project that has held near-zero net-new deps. Small,
  but it must go through Build & Release.
- **`hiltViewModel()` → `koinViewModel()`** at 4 sites in `AppNavHost.kt`. Confirm the
  multiplatform ViewModel integration handles `SavedStateHandle` — `ReaderViewModel` stores
  `reader_book_no` / `reader_chapter` in it (D-V3-13). ⟦VERIFY⟧ this before Phase C.
- The Glance widget's `@EntryPoint` becomes a direct Koin lookup from the widget's Android
  context. Slightly less pretty, functionally identical.
- Broadcast receivers (`ReminderAlarmReceiver`, `BootReceiver`) lose `@AndroidEntryPoint` and
  resolve their use cases from Koin in `onReceive`. Same shape.

## Revisit when

- Koin's runtime resolution causes a production crash that compile-time verification would have
  caught. That is the trigger to move to kotlin-inject, and it is a contained migration because
  the graph is declared in one place per module.
- kotlin-inject's Compose Multiplatform ViewModel story matures materially.
- The graph grows past ~150 objects, at which point manual wiring stops being a fallback.
