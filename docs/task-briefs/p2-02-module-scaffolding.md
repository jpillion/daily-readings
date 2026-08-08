# p2-02 — Create `shared/{domain,platform,data,ui}` and the CI boundary check. Empty modules first.

> **Assignee:** Build & Release Engineer
> **Release:** 1.11.0 · **Merge order:** Tranche A, **second — after `p2-01`.**
> **Inherits:** [`p2-00-overview.md`](p2-00-overview.md) rules R1–R9.
> **Executes:** ADR-0001.

---

## Objective

Create the four `shared/` Kotlin Multiplatform modules with **`androidTarget()` and `jvm()` only**,
wire `:app` to depend on them, and land **the CI boundary check that makes invariant 1 enforceable**
— all **before a single production file moves into them.**

Empty modules, real dependency arrows, a working boundary check, a green build.

---

## Context

The app is one Gradle module, `:app`, with 162 main-source Kotlin files. There is no `shared/`
anything.

### The topology (ADR-0001)

```
shared/domain      pure Kotlin: models, use cases, policies, classifiers, formatters.
                   Depends on: kotlinx-datetime, kotlinx-coroutines, shared/platform.
                   FORBIDDEN: java.*, android.*, Room, DataStore, Compose, okio, Ktor.

shared/platform    capability INTERFACES, written to semantics. Staff-owned.
                   Depends on: shared/domain models only.

shared/data        repositories and storage: Room KMP, DataStore, Ktor, asset readers, caches.
                   Depends on: shared/domain, shared/platform.

shared/ui          Compose Multiplatform screens, ViewModels, navigation, theme, resources.
                   Depends on: shared/domain, shared/platform. **NEVER shared/data.**

androidApp (:app)  MainActivity, Application, Glance widget, AlarmManager/Notification impls,
                   Play In-App Updates, Custom Tabs, MySword intent, manifest, DI wiring.
```

### Why empty modules first

Creating modules and moving 150 files in one commit produces an unreviewable diff in which a
dependency-direction mistake is invisible. Create the skeleton, **prove the arrows compile and the
boundary check fires**, then move files into a structure that is already policed.

The single most valuable output of this task is not the modules — it is the **boundary check**.
Without it, invariant 1 lasts about three weeks.

### Why Android + JVM targets only

Tranche A ships to Play as 1.11.0. Adding iOS targets in the same release means shipped Android
users bear the risk of a change that only iOS needs, and a crash has two candidate causes. iOS
targets are `p2-09`.

The `jvm()` target is worth having now: it is where `BibleTextVerificationTest` (sqlite-jdbc, 18)
will live as a shared `jvmTest`, and it is a free second compilation of `commonMain` that catches
accidental Android dependencies immediately.

---

## Contract

### 1. The modules

Four KMP modules, each with `androidTarget()` and `jvm()`, `commonMain` / `commonTest` source sets,
registered in `settings.gradle.kts`. `:app` depends on all four.

Where practical, put shared build configuration in a convention plugin rather than copying four
near-identical build files — but **do not spend this task building a build-logic framework.** Four
readable build files beat a clever one.

### 2. The dependency arrows, enforced by the build

```
:app          → shared/ui, shared/domain, shared/platform, shared/data
shared/ui     → shared/domain, shared/platform            ← and NOT shared/data
shared/data   → shared/domain, shared/platform
shared/platform → shared/domain
shared/domain → (kotlinx only)
```

**`shared/ui` must not declare a dependency on `shared/data`.** Gradle enforces this for free once
the arrow is absent — that is the point of four modules rather than one.

### 3. The CI boundary check — the deliverable that matters

A Gradle task, wired into `check`, that **fails the build** on:

| Rule | Scope |
|---|---|
| No `import java.*` | `shared/domain/src/commonMain`, `shared/ui/src/commonMain`, `shared/platform/src/commonMain` |
| No `import android.*` | `shared/domain`, `shared/ui`, `shared/platform` commonMain |
| No `import androidx.*` **except `androidx.compose.*` in `shared/ui/src/commonMain`** | same |
| No `import okio.*`, `io.ktor.*`, `androidx.room.*`, `androidx.datastore.*`, `androidx.compose.*` | `shared/domain/src/commonMain` **only** |
| No `if (isIOS)` / `Platform.isIOS` / `isAndroid` conditionals | all `shared/*/src/commonMain` |

`kotlin.*`, `kotlinx.*` and `kotlin.time.*` are allowed everywhere in shared code.

> **Correction, 2026-08-08 (integrator; Staff to confirm the exact allow-list).** Rows 97–98 were
> previously one row reading *"No `import android.*` / `androidx.*`"* across all three modules.
> **As written that forbade Compose Multiplatform itself** — CMP's packages are `androidx.compose.*`
> — so the check would have failed every shared screen in Phase 3, not merely a stray import. It
> also contradicted row 99, which bans `androidx.compose.*` in `shared/domain` **only**, implying
> it is permitted in `shared/ui`.
>
> The carve-out is deliberately **narrow**: `androidx.compose.*` in `shared/ui/src/commonMain`
> and nowhere else. `shared/domain` and `shared/platform` stay absolute — the invariant's real
> intent is that domain logic and capability contracts carry no framework, and that is unchanged.
> Any *other* `androidx.*` package appearing in `shared/ui` (`androidx.activity`, `androidx.core`,
> `androidx.navigation` at the Google coordinate) is still a violation and still fails the check.
>
> Raised by Sr Shared-UI while confirming that `AppIcons.kt` survives the Phase 3 move unmodified.
> Non-blocking for Gate 0 / Phase 1 / 1.12.0; **blocking for Phase 3**, which is why it is fixed
> here rather than left to be discovered as a build failure.

Requirements on the check itself:

- **It must fail the build**, not warn. A warning is a comment.
- Its failure message must name the file, the line and the offending import.
- **It must be proven to fail.** Add a deliberate `import java.util.Date` to a scratch file in
  `shared/domain/src/commonMain`, confirm the build fails with a useful message, and remove it.
  **A check nobody has seen fail is not known to work.**

The `if (isIOS)` rule is deliberately strict. Invariant 2 says such a branch is a **build failure,
not a code review comment** — and a grep-based check is the only thing that actually holds that
line over months.

### 4. What does NOT happen in this task

- **No production file moves.** `p2-04` and `p2-05` do that.
- **No interfaces are written.** `p2-03` (Staff) does that.
- No Room KMP, no DataStore, no Compose Multiplatform wiring beyond what an empty module needs.
- **No iOS targets.**

A "hello world" file per module to prove compilation is fine, and is deleted by `p2-04`/`p2-05`.

---

## Acceptance criteria

1. Four modules exist, are in `settings.gradle.kts`, and build with `androidTarget()` and `jvm()`.
2. The dependency arrows are exactly as listed. **`shared/ui` does not depend on `shared/data`** —
   prove it by adding the dependency temporarily, showing it compiles, removing it, and stating in
   the PR that the arrow's absence is deliberate and load-bearing.
3. The boundary check is wired into `check`, **fails the build**, names file + line + import, and
   **has been demonstrated failing** (output quoted in the PR) and then restored.
4. `./gradlew build` green from clean.
5. Full pipeline green:
   `./gradlew spotlessCheck lintDebug assembleDebug testDebugUnitTest koverXmlReportAppDebug koverVerifyAppDebug`
6. **Spotless and Kover cover the new modules.** A module outside Kover reports 100% by reporting
   nothing — say explicitly how the new modules are included and what the floor is.
7. **The six data gates untouched, counts unchanged: 11 / 10 / 8 / 6 / 18 / 5.**
   They still live in `app/src/test/` at this point. That is correct.
8. Test count unchanged: 940 + whatever Phase 1 added. **No test moves in this task.**
9. `bundleRelease` builds; **AAB size reported and materially unchanged** — empty modules must not
   move it. A jump means something was pulled in that should not have been.
10. **CI wall-clock time before and after is reported.** Four modules mean four compilations; if
    PR CI slows materially, the EM needs to know now.
11. **R8 device smoke** — light, since nothing moved: cold launch, tap a reading, open the reader,
    toggle a mark. Confirms the multi-module release build works at all.

---

## Boundaries / write set

**Yours:**
- `settings.gradle.kts`
- `shared/domain/build.gradle.kts`, `shared/platform/build.gradle.kts`,
  `shared/data/build.gradle.kts`, `shared/ui/build.gradle.kts` **(new)**
- `app/build.gradle.kts` — **the `dependencies` block only**
- `build-logic/**` or the root `build.gradle.kts`, for the boundary check
- `.github/workflows/ci.yml` — to run the check
- Temporary `Placeholder.kt` files, deleted by `p2-04`/`p2-05`

**Not yours:**
- **Any file under `app/src/`.** Not one production or test file moves here.
- `shared/assets/**` — `p2-01` owns it and it is already done.
- Any `shared/platform` interface — **Staff (`p2-03`)**.

---

## Escalation triggers

- **Compose Multiplatform, Room KMP or the AndroidX BOM cannot be resolved** at the versions
  selected → **Staff + EM**. The signed-off approach records that the version alignment is free
  (Compose BOM 2026.05.01 → Compose 1.11.2; CMP 1.11.1 is built on 1.11.2; CMP 1.11.1 requires
  Kotlin 2.3; this repo is on 2.3.21 — **verified against real POMs**). If that no longer holds,
  it removes the single biggest reason the port was judged tractable and the EM must know
  immediately.
- **The boundary check cannot be made to fail the build** → **Staff**, blocking. Without it,
  invariant 1 is a suggestion.
- **CI time increases by more than ~50%** → **EM**, non-blocking, but say it now rather than after
  four more modules of content.
- **The four-module split proves impractical** for a reason you can name → **Staff**. ADR-0001
  considered and rejected one `shared` module; reversing that is Staff's call, not a build
  convenience.
