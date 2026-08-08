# p1-06 — Generalise the ViewModel-init rule, and move `MainDispatcherRule` off `Unconfined`

> **Assignee:** Verification Engineer
> **Release:** 1.10.0 · **Merge order:** Group D — **immediately BEFORE `p1-07` (Hilt → Koin).**
> The order is the whole point of this brief.
> **Inherits:** [`p1-00-overview.md`](p1-00-overview.md) rules R1–R7.
> **Preconditions:** `p1-05` merged (so the suite is already assertk and this commit's reddening is
> attributable to the dispatcher change alone).

---

## Objective

Build the detector **before** the change it is meant to detect.

Two things:

1. Generalise `ReaderViewModelHandoffInitTest` into a **rule applied to every ViewModel that
   collects a flow in its `init` block** — pinning the **construction order production actually
   uses**, not the safe order tests naturally use.
2. Move `MainDispatcherRule` from `UnconfinedTestDispatcher` to `StandardTestDispatcher`, and deal
   with whatever reddens.

**Zero production behaviour change.** This task ships test code and one test-infrastructure change.

---

## Context — the 1.7.0 P0, because this brief is entirely about not repeating it

On 2026-07-25, release 1.7.0 **crashed on every reading tap, on every day, on every plan.**

`ReaderViewModel.init` collects `ReaderHandoff.pending`. A Schedule reading tap populates that
handoff **before** the reader ViewModel is constructed, so the collect fires **during
construction** → `enterReading` → `switchContext`, which writes `_selection.value`. Sprint 00Q had
declared `_selection` **below** the `init` block, and **Kotlin initialises in declaration order**,
so the field was still null:

```
NullPointerException: Attempt to invoke virtual method
'void StateFlowImpl.setValue(Object)' on a null object reference
```

Three things missed it, and each is a lesson this brief encodes:

1. **It reproduced ONLY in the R8-minified release build.** The debug build dispatched the init
   collect late enough to miss the window. → rule R5, in every brief.
2. **The owner's device pass was on a debug build.** → rule R5 again.
3. **All 879 JVM tests passed, because every existing test constructed the ViewModel FIRST and only
   then set the handoff** — the safe order, which never touches the uninitialised field. **The bug
   lived entirely in the order production uses.** → this brief.

The fix was one declaration move. `ReaderViewModelHandoffInitTest` (3 tests) was written to pin
the **order**, not the outcome, and was proven to go red with the fix reverted.

### Why this must land before Koin

`p1-07` replaces Hilt's **static, compile-time-verified** graph with Koin's **lazy, reflective
resolution.** That changes *when* objects are constructed and *in what order* — which is exactly
the axis the 1.7.0 bug lived on. R3 in the risk register rates it HIGH/MEDIUM for precisely this
reason.

Building the detector first means (a) it exists when the risky change lands, and (b) if the
stricter dispatcher reddens existing tests, that reddening happens in a commit that changed
**nothing else**, so it is diagnosable.

### `UnconfinedTestDispatcher` — the second half

`app/src/test/.../testing/MainDispatcherRule.kt:15` defaults to `UnconfinedTestDispatcher`. That is
**the most forgiving possible scheduling**: coroutines run eagerly and inline at the launch point,
so ordering bugs that depend on real dispatch simply do not manifest. It is a large part of why the
1.7.1 ordering bug hid in a green suite.

`StandardTestDispatcher` queues work and requires explicit `advanceUntilIdle()` / `runCurrent()`,
which is closer to production and — more importantly — **makes ordering observable.**

Expect fallout. **The fallout is the finding.** Two files already use `StandardTestDispatcher`
deliberately (`HttpFumsReporterTest`, `SettingsRepositoryImplTest`), and
`SettingsViewModelTest:63` holds its own `UnconfinedTestDispatcher` — that one is a local choice and
may stay if its author's reason still holds; say which you did.

---

## Contract

### 1. The audit — do this first and report it before writing anything

List **every** ViewModel with an `init` block that collects, launches or otherwise touches
`MutableStateFlow` state. On `main` that is:

- `ui/day/DayReadingsViewModel.kt`
- `bible/ui/reader/ReaderViewModel.kt`

Regenerate the list rather than trusting this one. For **each**, record:

1. Which shared/injected object can be populated **before** construction (`ReaderHandoff` is the
   known one — it is `@ActivityRetainedScoped` with a **one-shot pending value**, produced by
   `DayReadingsViewModel` and consumed by `ReaderViewModel`).
2. Whether every `MutableStateFlow` the init path writes is declared **above** the `init` block.

> **The audit is a deliverable in its own right**, even where it finds nothing. "We checked every
> ViewModel and only two have init blocks" is a fact worth having in the record before the DI
> rewrite, not after.

### 2. The generalised rule

Extend `ReaderViewModelHandoffInitTest` into a rule — a shared test helper plus one test per
qualifying ViewModel — that pins **the production order**:

```
populate the shared/injected state  →  THEN construct the ViewModel  →  assert correct state
```

**Not** the reverse. Each test must be proven to go **red** when the corresponding
`MutableStateFlow` declaration is moved below `init`, and green when restored. **A test that passes
in both positions is not pinning anything** — that was the entire 1.7.0 gap.

Keep the existing three `ReaderViewModelHandoffInitTest` cases (portion, multi-chapter portion,
`requestBrowse`) exactly as they are; they are proven.

### 3. `MainDispatcherRule` → `StandardTestDispatcher`

Change the default. Fix the fallout by **adding explicit `advanceUntilIdle()` / `runCurrent()` at
the points the test actually needs them** — never by reverting a file to `Unconfined` to make it
pass.

If a test genuinely requires eager dispatch, it may pass `UnconfinedTestDispatcher` explicitly at
its own call site — **with a one-line comment saying why.** An explicit, justified exception is
fine; a silent global default is not.

---

## Acceptance criteria

1. The ViewModel audit is written into the PR description: every ViewModel, whether it has an
   `init` block, whether it collects, and whether its state fields precede `init`.
2. **Every qualifying ViewModel has an init-order test**, each proven red-then-green by moving its
   `MutableStateFlow` declaration below `init` and restoring it **byte-identically**. Record each
   mutation and its restored checksum.
3. `MainDispatcherRule` defaults to `StandardTestDispatcher`.
4. **Every test that reddened is listed**, with the fix applied to each. **Zero** files reverted to
   `Unconfined` without an explicit, commented, per-site justification — and every such
   justification enumerated in the PR.
5. **Test count strictly increases. Zero deletions.** State before/after.
6. `ReaderViewModelHandoffInitTest`'s original 3 cases still pass unchanged.
7. Full pipeline green, run with `--rerun-tasks`.
8. **The six data gates untouched, counts unchanged: 11 / 10 / 8 / 6 / 18 / 5.**
9. **No R8 device smoke required** — zero production bytes. Say so explicitly.
10. **Zero files under `app/src/main/` modified.** If a ViewModel genuinely has a latent
    declaration-order bug, **do not fix it here** — report it, and let it be its own commit with
    its own R8 smoke. Finding one would be an excellent outcome and it deserves its own release
    note.

---

## Boundaries / write set

**Yours:**
- `app/src/test/kotlin/.../testing/MainDispatcherRule.kt`
- `app/src/test/kotlin/.../testing/**` (a new shared init-order helper)
- `app/src/test/kotlin/.../bible/ui/reader/ReaderViewModelHandoffInitTest.kt`
- Any test file that reddens from the dispatcher change
- A new init-order test per qualifying ViewModel

**Not yours:**
- **Anything under `app/src/main/`.** Not one line.
- `app/src/main/assets/**`, `app/schemas/**`.
- `gradle/libs.versions.toml` — **Build & Release**.

---

## Escalation triggers

- **A ViewModel is found with a real declaration-order bug** → **Staff**, non-blocking but
  **immediately**. That is a latent P0 of the exact 1.7.0 class and it needs its own fix commit and
  its own R8 device smoke. **Do not fix it inside this task.**
- **More than ~10 test files redden from the dispatcher change** → **EM + Staff**, non-blocking.
  That is a larger finding about the suite's dependence on eager dispatch and it changes the
  estimate for `p1-07`.
- **A test cannot be made to pass under `StandardTestDispatcher`** → **Staff**. Bring the test.
  Reverting it to `Unconfined` without a stated reason is the outcome this brief exists to prevent.
- **An init-order test passes with the field moved below `init`** → **Staff**, blocking. The test
  is not pinning the order and needs redesigning before `p1-07` lands.
