# rel-1120 — UI dependency realignment: the lifecycle and navigation coordinate swaps

> **Assignee:** Build & Release Engineer (**singular — never parallelize this role**)
> **Release:** **1.12.0 / 11200** — per **D-PORT-9**
> ([`../ios-execution-plan.md`](../ios-execution-plan.md) §6). Appended **after 1.11.0**, tagged
> **before Phase 3 begins**.
> **Sibling brief, same release:** [`rel-1120-vendor-icons.md`](rel-1120-vendor-icons.md)
> (Sr Shared-UI) — the ten vendored glyphs. **The two briefs ship together and neither ships alone.**
> **Contract of record:** [`p0-build-foundation.md`](p0-build-foundation.md) §3.B.3 and §3.B.7.
> **Inherits:** [`p1-00-overview.md`](p1-00-overview.md) rules R1–R7 (no behaviour change; the six
> gates; the R8 device smoke) and [`p2-00-overview.md`](p2-00-overview.md) R6.
> **Preconditions:** **1.11.0 live on Play with 24–72 h of clean vitals** (D-PORT-10 condition 3 —
> which for 1.11.0 explicitly includes an upgrade-in-place check and reading the reviews, not just
> the vitals dashboard). Hilt is gone (1.10.0 / `p1-07`), so `androidx.hilt:hilt-navigation-compose`
> no longer pins a navigation version.
> **Author:** Build & Release Engineer, 2026-08-08

---

## 1. Objective

Move the Android app onto the **final** lifecycle and navigation dependency substrate that
`shared/ui` will compile against on iOS — as its own Play release, with its own soak, **before**
Phase 3 writes a single line of Compose into `shared/ui`.

Done means: the two coordinate swaps below are live on Play with no related crash or complaint
signal; `NavRegressionTest` has been extended to actually guard what the navigation swap can break;
and the `SavedStateHandle` answer that Gate 0's M2 spike and `p1-07` obtained **on the old lifecycle
artifact** has been re-obtained on the new one.

This release is **two coordinate swaps and ten vendored glyphs, and nothing else.** Nothing merges
into it from 1.11.0, and nothing merges out of it into Phase 3.

### Why this release exists at all — transcribed, not re-argued

`p0-build-foundation.md` §3.B decided all three changes but assigned them to no release, and no
`p1-*` or `p2-*` brief claimed them. The EM resolved that as **D-PORT-9** and the owner signed the
approach. The reasoning is recorded in `ios-execution-plan.md` §6 and is **not re-litigated here**:

- **Not 1.11.0** — its failure mode is silent data loss, and adding a navigation change destroys the
  bisection value that justified isolating it. Generalised: *nothing merges into 1.11.0.*
- **Not 1.9.0** — 1.9.0's whole characterisation is *"nothing here can brick an install; all failure
  modes are loud or already have a fallback."* Neither of these swaps is loud-with-a-fallback (§3.4).
- **Not inserted before 1.10.0**, despite a real coupling (`koinViewModel()` resolves against the
  lifecycle artifact). Renumbering 1.10.0 and 1.11.0 across ~20 briefs risks a brief targeting a
  release that does not exist. **The coupling is discharged as acceptance criterion 9 instead, and
  that criterion must not be dropped as redundant.**
- **Placement before Phase 3** is where it is forced: `p2-02` creates `shared/ui` *empty*, so nothing
  needs these artifacts until Phase 3 — but both are Android-user-facing the moment they land, and
  letting them drift into Phase 3 means they reach real users bundled with the largest UI churn in
  the program and **with no Play release boundary at all**.

---

## 2. Context

### 2.1 Corrections to `p0-build-foundation.md` §3.B.3 — read these before writing the catalog

Three claims in my own §3.B.3 were re-probed against Maven Central on **2026-08-08** while writing
this brief. **Two of them were wrong, and one of the two would have been copied forward as a reason.**
§3.B.3 has been amended; the corrected findings are restated here so this brief is self-contained.

| Claim in `p0` §3.B.3 | Status | What is actually true |
|---|---|---|
| *"`lifecycle-viewmodel-compose-iosarm64:2.10.0` → **404** … **2.11.0 is the floor — 2.10.0 has no iOS artifact**"* | ❌ **WRONG** | **VERIFIED 2026-08-08:** `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0` **does** carry an iOS variant. It uses the legacy `uikit*` artifact naming, and its `uikitArm64ApiElements-published` variant carries `org.jetbrains.kotlin.native.target: ios_arm64` — so Gradle resolves it for an `iosArm64` consumer **by attribute**. The lowercase `-iosarm64` 404 proves nothing. **This is exactly the trap `p0` §3.B.3 itself warns about for navigation** (*"`navigation-compose-iosarm64:2.9.2` is a 404 and that is expected; do not 'fix' it"*) — I applied the rule to navigation and failed to apply it to lifecycle. |
| The navigation swap is a *"different artifact lineage"* | ⚠️ **MISLEADING** | **VERIFIED:** on **Android**, `org.jetbrains.androidx.navigation:navigation-compose:2.9.2` is a **facade**. Its `debugRuntimeElements-published` and `releaseRuntimeElements-published` variants declare `androidx.navigation:navigation-compose:2.9.7` as a dependency. The Android implementation stays AndroidX; the visible delta is **`androidx.navigation` 2.9.8 → 2.9.7**, a one-patch downgrade *of the same lineage*, plus a facade layer. |
| The lifecycle swap changes the ViewModel implementation | ⚠️ **MISLEADING** | Same shape. `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose-android:2.11.0`'s `androidRuntimeElements-published` declares `androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0`; `lifecycle-viewmodel-android:2.11.0` declares `androidx.lifecycle:lifecycle-viewmodel:2.11.0`. The Android implementation stays AndroidX; the visible delta is **`androidx.lifecycle` 2.10.0 → 2.11.0**, an upgrade *of the same lineage*, plus KMP facades. |

**This does not overturn D-PORT-9, and 2.11.0 remains the right version — for a corrected reason.**
JB lifecycle **2.10.0** is compiled against `org.jetbrains.compose.runtime:runtime:1.9.3` and
`org.jetbrains.compose.ui:ui:1.9.3`; JB lifecycle **2.11.0** is compiled against
`org.jetbrains.compose.runtime:runtime:1.11.0`, which is the line CMP **1.11.1** actually ships
(`p0` §3.B.2). **On Kotlin/Native the klib is linked, not classpath-resolved, so a two-minor
compose-runtime gap is an ABI risk we take on iOS and get nothing for.** Choosing 2.10.0 would buy a
zero-delta Android side at the cost of the substrate Phase 3 has to link against — the opposite of
this release's purpose.

**The honest consequence for the owner:** the Android-user-facing risk in this release is *smaller*
than `ios-execution-plan.md` §6's table implies — an AndroidX lifecycle minor bump and an AndroidX
navigation patch downgrade, behind two facades. **The release still stands**, because a facade is
still new code on the resolution path, because the JB `navigation-compose` common API surface is not
identical to AndroidX's, and because the failure modes in §2.2 remain silent. The soak is cheap; the
diagnosis of a nav defect buried in Phase 3 is not.

### 2.2 The two failure modes, and why neither is loud

| Swap | Failure mode | Why it is not loud |
|---|---|---|
| **Navigation** | A destination becomes unreachable, or a tab back-stack stops preserving state (D-V3-16, D-D-3, U18, sprint 00D). | The app launches, the tab bar renders, nothing crashes. The user finds it, not the build. |
| **Lifecycle** | `SavedStateHandle` silently stops restoring. | **No crash and no compile error.** `ReaderViewModel.restoredBrowsePage()` (`ReaderViewModel.kt:517-521`) reads `savedStateHandle.get<Int>("reader_page")` and, on `null`, **returns `GENESIS_1_PAGE`**. A user who was reading Psalm 23 reopens the Bible tab at Genesis 1. That is the D-V3-13 last-read behaviour degrading into the exact symptom of the 1.8.1 picker-jump P1 — which was a real, owner-reported defect. |

**The existing JVM test cannot catch the lifecycle one, and this is the whole reason criterion 9
exists.** `ReaderViewModelTest`'s *"Browse in-session restore reopens the saved page"* constructs
`SavedStateHandle(mapOf("reader_page" to psalms23Page))` **by hand** and passes it to the
constructor. It proves the ViewModel *reads* the handle. It never involves the lifecycle artifact's
saved-state machinery, so **swapping that artifact cannot redden it.** Same for
`ReaderViewModelHandoffInitTest`, which passes a bare `SavedStateHandle()`.

### 2.3 What `NavRegressionTest` proves — and what it does not

`app/src/test/kotlin/com/jpillion/dailyreadingplanner/ui/navigation/NavRegressionTest.kt`
(5 tests, Robolectric `@Config(sdk = [34])`, `TestNavHostController`). **It is the only automated
guard on the navigation swap.** Read the file before relying on it; the honest inventory is:

**It proves:**

1. `Graph.SCHEDULE` is the start destination and the day pager shows first.
2. `Routes.SETTINGS` stays reachable inside the Schedule graph.
3. The Bible tab is reachable via the production `switchTab` helper and lands on `Routes.READER`.
4. **A tab switch preserves the Schedule back-stack** — drill into Settings, switch to Bible, switch
   back, and Settings (not the day pager) is current.
5. The production `isInGraph` helper distinguishes the two graphs across `NavDestination.hierarchy`.

**It does NOT prove — do not let anyone report it as if it does:**

- **It does not drive `RootScaffold` or `AppNavHost`.** It builds its own `TestGraph()` that
  *mirrors* the production topology with **tagged stand-in leaf screens** (`Text("today")`, etc.).
  The production composables, their ViewModels and their DI resolution are never constructed.
- Therefore it says nothing about **ViewModel resolution at a destination** — the `koinViewModel()`
  path, which is exactly what the lifecycle swap touches.
- It says nothing about the **bottom `NavigationBar` click wiring** (it calls `switchTab`
  programmatically), about **system/predictive back**, about **deep links or arguments**, or about
  **state inside a leaf screen** surviving a tab switch (the stand-ins hold no state — it asserts the
  restored *route*, not restored scroll/pager/selection state).
- It runs on Robolectric SDK 34 only, and it uses `androidx.navigation:navigation-testing` — see
  §3.2, which is a trap in its own right.

**Consequence, and it is a contract item, not advice:** per `ios-execution-plan.md` §6 gate 1,
*"If it does not currently cover a destination or a back-stack, extend it **before** the swap, not
after."* It does not cover the bottom-bar click path or the Bible-tab back-stack in the reverse
direction. **That extension is a Verification task and it lands and goes green on the pre-swap tree
first** (§5).

---

## 3. Contract

### 3.1 The lifecycle swap — every affected artifact, named

`gradle/libs.versions.toml` today: `lifecycle = "2.10.0"`, three `androidx.lifecycle` entries, all
declared at `app/build.gradle.kts:172-174`.

| Today | Becomes | Verdict |
|---|---|---|
| `androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0` | **`org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0`** | Swap. Root POM **200**; iOS via proper `iosArm64` / `iosSimulatorArm64` variants at 2.11.0. Android delegates to `androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0`. |
| `androidx.lifecycle:lifecycle-runtime-compose:2.10.0` | **`org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.11.0`** | Swap. POM **200**. This is what `collectAsStateWithLifecycle` comes from — **6 call sites** in `app/src/main/kotlin`. |
| *(transitive, but declare it — `p1-07`'s Koin modules and `ReaderViewModel` both depend on it)* | **`org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-savedstate:2.11.0`** | POM **200**. **This is the artifact that owns the failure mode in §2.2.** Declare it explicitly rather than inheriting it, so a future transitive bump is visible in the catalog diff. |
| *(transitive)* | **`org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.11.0`** · **`lifecycle-common:2.11.0`** | POMs **200**. Pulled by the two above. Do not declare unless a resolution conflict forces it; if it does, say so. |
| **`androidx.lifecycle:lifecycle-runtime-ktx:2.10.0`** | **stays `androidx.lifecycle:lifecycle-runtime-ktx`, bumped to `2.11.0`** | ⚠️ **There is no JetBrains counterpart.** VERIFIED: the `org.jetbrains.androidx.lifecycle` group publishes `lifecycle-common`, `-runtime`, `-runtime-compose`, `-viewmodel`, `-viewmodel-compose`, `-viewmodel-navigation3`, `-viewmodel-savedstate` — **no `-ktx`**. Its only consumer is `androidx.lifecycle.lifecycleScope` in **`MainActivity.kt` alone**, which is Android-only by definition and stays in `androidApp`. Bump it to 2.11.0 so the whole `androidx.lifecycle` graph resolves at one version rather than by Gradle conflict resolution. `androidx.lifecycle:lifecycle-runtime-ktx:2.11.0` VERIFIED **200** on Google Maven. |

**Do not** introduce a `lifecycle-bom`. Two version refs (`jbLifecycle = "2.11.0"`,
`androidxLifecycle = "2.11.0"`) that must be kept equal is worse than one ref used by both groups —
use **one** ref and reference it from both, with a comment saying why the two coordinates share it.

### 3.2 The navigation swap — and the trap in the test classpath

| Today | Becomes | Verdict |
|---|---|---|
| `androidx.navigation:navigation-compose:2.9.8` (`app/build.gradle.kts:175`) | **`org.jetbrains.androidx.navigation:navigation-compose:2.9.2`** | Swap. Root POM **200**; the per-target artifacts use the legacy `uikit*` naming (`navigation-compose-uikitarm64`, `-uikitsimarm64`) and resolve by KMP target attribute. **`navigation-compose-iosarm64:2.9.2` is a 404 and that is expected — do not "fix" it.** 2.9.2 is the newest stable in the JB line (2.10.0 is alpha only). |
| `androidx.navigation:navigation-testing`, `version.ref = "navigationCompose"` (= 2.9.8) (`app/build.gradle.kts:202`) | **stays `androidx.navigation:navigation-testing`, pinned to its OWN ref at `2.9.7`** | ⚠️ **See below. This is the most consequential line in this brief.** |

> 🚩 **The trap: after the swap, the only guard on the swap would silently test a different version
> than ships.**
>
> VERIFIED: **`org.jetbrains.androidx.navigation:navigation-testing` does not exist at any version**
> — the JB navigation group publishes only `navigation-common`, `navigation-compose` and
> `navigation-runtime` (plus per-target). So `NavRegressionTest`'s `TestNavHostController` must keep
> coming from `androidx.navigation:navigation-testing`, which is fine — on Android the JB facade
> resolves to AndroidX navigation anyway.
>
> **But the versions must be made to agree by hand.** After the swap the main runtime classpath
> resolves `androidx.navigation:navigation-compose` to **2.9.7** (the version the JB facade
> requires). `androidx.navigation:navigation-testing` is a `testImplementation`; the unit-test
> classpath is main + test, so leaving it at **2.9.8** makes Gradle resolve the *whole*
> `androidx.navigation` graph up to **2.9.8 in the unit-test classpath only**. `NavRegressionTest`
> would then be green against navigation 2.9.8 while users run 2.9.7. Today the two share
> `version.ref = "navigationCompose"` and therefore agree; the swap breaks that agreement **silently,
> in the one test that exists to catch the swap**.
>
> **Contract:** give `navigation-testing` its own version ref pinned to **2.9.7**
> (`androidx.navigation:navigation-testing:2.9.7` VERIFIED **200** on Google Maven), and prove the
> agreement mechanically — acceptance criterion 6.

**Version alignment note (check, do not assume):** JB `navigation-compose:2.9.2`'s common metadata
requires `org.jetbrains.androidx.lifecycle:*:2.9.6` and `org.jetbrains.compose.runtime:runtime:1.9.3`.
We supply lifecycle **2.11.0** and CMP **1.11.1**, so Gradle upgrades both. That upgrade is expected
and correct on Android; **on the iOS klib link it is the same ABI-gap shape described in §2.1 and it
is not exercised until Phase 2B / Phase 3.** Record it as a known, dated caveat with a revisit
trigger (the next JB navigation stable), not as a resolved question.

### 3.3 The glyph drop — the half of it that is mine

`androidx.compose.material:material-icons-core:1.7.8` is removed from
`gradle/libs.versions.toml:17,37` and `app/build.gradle.kts:171`. **The vendoring itself is not mine
and is not specified here** — see [`rel-1120-vendor-icons.md`](rel-1120-vendor-icons.md) (Sr
Shared-UI), whose contract is `p0` §3.B.7's enumerated **ten** glyphs, three of them `AutoMirrored`.

**Ordering, because getting it backwards breaks the build:** Sr Shared-UI's vendoring merges
**first**; my catalog/build-file removal merges **second**, in its own commit, and
`grep -r "material-icons" --include=*.kts --include=*.toml .` is empty afterwards (criterion 12).
Removing the dependency before the glyphs exist is a red build; leaving it after they exist is a
dead dependency that ships.

### 3.4 What does **not** change in 1.12.0

State these explicitly in the PR, because an unexplained absence reads like an oversight:

- **`compose-bom` stays at 2026.05.01.** `p0` §3.B.2's whole finding is that no Compose version has
  to move; this release does not move one. `androidx.compose.ui` stays 1.11.2, `material3` stays
  1.4.0.
- **No `shared/*` module is created and no KMP plugin is applied.** That is `p2-02`, already shipped
  in 1.11.0; this release changes coordinates inside the module structure that 1.11.0 established.
- **No iOS target is declared.** Tranche B (`p2-09`) is gated on 1.11.0 vitals *and* Xcode, and is a
  separate brief that ships nothing.
- **No behaviour change** (`p1-00` R1). If the swap appears to require one, that is an escalation.
- **No production Kotlin edits by me.** If a JB API differs from the AndroidX one at a call site, I
  do not "fix" the call site — see §5.

### 3.5 Merge order and release mechanics

```
1.11.0 live ── 24-72 h vitals + the D-PORT-10 upgrade-in-place check ───┐
                                                                       ▼
  (Verification)  extend NavRegressionTest on the PRE-SWAP tree, green  │  ← criterion 5
                                                                       ▼
  (Sr Shared-UI)  rel-1120-vendor-icons: vendor the 9 glyphs           │
                                                                       ▼
  (me) commit 1: lifecycle swap        ─ criteria 1,2,3,9,10
  (me) commit 2: navigation swap       ─ criteria 4,5,6
  (me) commit 3: drop material-icons   ─ criterion 12
  (me) commit 4: versionCode 11200 / versionName "1.12.0"
                                                                       ▼
                 assembleRelease on-device smoke  ─ criteria 10,11      │
                                                                       ▼
                 tag v1.12.0 -> internal+alpha -> promote to production │
                                                                       ▼
                 24-72 h vitals soak, and reviews, before Phase 3 opens ┘
```

**Three separate commits for the three dependency changes**, not one. If the soak turns up a nav
defect, the revert is one commit and the bisection is already done. This is the same argument that
justified four releases.

**The version bump is `app/build.gradle.kts:45-46`** — `versionCode = 11200`, `versionName =
"1.12.0"`, per D-S9-3 (`MAJOR*10000 + MINOR*100 + PATCH`). MINOR, not PATCH: `material-icons-core`
leaving means every glyph in the app is redrawn from vendored vectors, which is user-visible.

**Release path:** the proven `tag → internal+alpha → promote-production` pipeline
([`../RELEASING.md`](../RELEASING.md)). Do **not** hand-promote in the Console; that route exists
only for a track's first-ever release. `distribution/whatsnew/whatsnew-en-US` needs a line — this
release has no user-facing feature, so say something true and small rather than inventing one.

---

## 4. Acceptance criteria

**Report each as VERIFIED / INFERRED / UNVERIFIED with its evidence, never as "done."**

**Resolution**

1. `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` resolves with **zero
   `FAILED`** lines on a clean cache (`--refresh-dependencies`). Paste the `org.jetbrains.androidx.*`
   and `androidx.{lifecycle,navigation}` subtrees.
2. **The resolved AndroidX versions are stated, before and after**, and match §2.1's prediction:
   `androidx.lifecycle:*` **2.10.0 → 2.11.0**; `androidx.navigation:navigation-compose`
   **2.9.8 → 2.9.7**. **If either differs from this prediction, stop and report it** — it means the
   facade resolved differently than the module metadata says, and the risk assessment in §2.1 is
   void.
3. `androidx.compose.ui` resolves at **1.11.2** and `androidx.compose.material3` at **1.4.0**,
   unchanged from 1.11.0. Show before and after (`p0` criterion 5).

**Navigation guard**

4. **`NavRegressionTest` green**, 5 tests minimum plus whatever criterion 5 adds. It is the only
   automated guard on this swap.
5. **`NavRegressionTest` extended BEFORE the swap, and demonstrated green on the pre-swap tree
   first.** Minimum additions, from §2.3's honest inventory: the **bottom `NavigationBar` click
   path** (not just a programmatic `switchTab`), and the **Bible-tab back-stack in the reverse
   direction** (drill somewhere on Bible, switch to Schedule, switch back, land where you left).
   *Verification writes this; I do not (§5). It is a hard precondition on my commit 2, not a
   parallel nicety.*
6. **The test classpath and the runtime classpath resolve the same `androidx.navigation` version.**
   Show `./gradlew :app:dependencies --configuration debugUnitTestRuntimeClasspath` and
   `--configuration releaseRuntimeClasspath` side by side, both reporting **2.9.7**. This is §3.2's
   trap and it is invisible without the diff.
7. **The full Compose UI suite is green, test count unchanged, zero deletions.** State before/after.
   A dropped test is a failed task (`p1-00` R7).

**Compose / glyphs — joint with the sibling brief**

8. All nine glyphs render, the three `AutoMirrored` ones vendored **as auto-mirroring**. *Owned and
   evidenced by [`rel-1120-vendor-icons.md`](rel-1120-vendor-icons.md); referenced here because
   1.12.0 does not tag until it is met.* The app is not localized (ADR-0013), so a lost
   auto-mirroring property is inert today — **which is exactly why nobody would notice**.

**The lifecycle coupling — EM's binding criterion, D-PORT-9 gate 3**

9. **Re-run `p1-07`'s ViewModel-construction smoke and its `ReaderViewModel`-with-a-real-
   `SavedStateHandle` criterion against the NEW lifecycle artifact, and report them as re-run rather
   than inherited.** Specifically: `p1-07` acceptance criteria **3** (`checkModules()` as a CI unit
   test) and **4** (every ViewModel constructed through Koin, including `ReaderViewModel` with a real
   `SavedStateHandle`) both green on this tree.

   **Why this exists, restated so it is not dropped as redundant:** `koinViewModel()` resolves
   against the lifecycle artifact. Gate 0's **M2** spike answered *"does `SavedStateHandle` survive
   Koin construction"* on `androidx.lifecycle 2.10.0`, and `p1-07` shipped on that answer. **This
   release changes the substrate underneath it.** The EM chose this criterion over resequencing the
   release specifically so that re-running one smoke replaces renumbering twenty briefs — **it is the
   entire consideration paid for the cheaper option.**

   **And note the criterion is necessary but not sufficient on its own:** `p1-07` criterion 4
   constructs the ViewModel with a `SavedStateHandle`; it does not prove the framework *populates*
   one. That is criterion 11.

**The R8 on-device smoke — mandatory (`p1-00` R5), and this is the criterion that matters most**

10. **`assembleRelease`, installed on a physical device or emulator, exercised by hand. Not
    `assembleDebug`.** The standing rule exists because the 1.7.0 P0 — a crash on every reading tap —
    reproduced **only under R8**, passed 879 JVM tests, and passed an owner device pass on a debug
    build. **This release changes a DI-adjacent artifact and the navigation graph: exactly that
    profile.**

    Walk **every** navigation destination and **both** tab back-stacks:

    1. Cold launch — no crash.
    2. Tap a reading on the Schedule → the reader opens on the right chapter (the 1.7.0 path).
    3. Schedule → Settings → back. Every Settings row renders; the plan dropdown and the switch
       dialog open (they are `DropdownMenu`s over the swapped Compose stack).
    4. Bible tab → picker → pick a chapter → change version → **no jump to Genesis 1** (the 1.8.1
       path).
    5. Long-press a verse → selection → Copy → selection exits (P-Q-1).
    6. **Tab switch, both directions, with a back-stack on each side:** drill to Settings on
       Schedule, switch to Bible, navigate somewhere, switch back to Schedule → **Settings, not the
       day pager**; switch to Bible again → where you left it.
    7. System back from a drilled-in destination on each tab.
    8. Toggle a reading → the home-screen widget updates.
    9. Confirm **all nine glyphs** render on-screen at the destinations that use them.

11. **The `SavedStateHandle` process-death check — and it is a DIFFERENTIAL, not an absolute.**

    The failure this catches is silent: `ReaderViewModel.restoredBrowsePage()`
    (`ReaderViewModel.kt:517-521`) falls back to `GENESIS_1_PAGE` when
    `savedStateHandle.get<Int>("reader_page")` returns null. **"The app launches" is not evidence.
    "The reader opens" is not evidence. You must check the reader's position specifically.**

    **Do it as a differential, because nobody has established what 1.11.0 actually does here.**
    D-V3-13 describes an *in-session* last-read; whether it survives process death today is
    **UNVERIFIED**, and asserting "it must restore" would be asserting a behaviour the app may not
    have. So:

    a. On the **1.11.0** release build (pre-swap): Bible tab → open **Psalm 23** → home →
       `adb shell am kill com.jpillion.dailyreadingplanner` → relaunch from Recents. **Record the
       chapter it opens on. That is the baseline.**
    b. Repeat verbatim on the **1.12.0** release build. **It must match the baseline.** A baseline of
       "restores Psalm 23" that becomes "opens Genesis 1" is the defect; a baseline of "opens Genesis
       1" that stays "opens Genesis 1" is not this release's problem (**file it, do not fix it here —
       `p1-00` R1**).
    c. Also run the cheap always-exercised path on both builds: open Psalm 23, then force an activity
       recreation (Developer Options → **Don't keep activities**, or a rotation), and confirm
       identical behaviour.

    > **Use `am kill`, not `am force-stop`.** `force-stop` clears the task's saved instance state, so
    > a fresh-looking relaunch proves nothing. `am kill` requires the app to be backgrounded and
    > preserves the saved-state bundle — which is the thing under test.

**Global**

12. `grep -r "material-icons" --include=*.kts --include=*.toml .` is **empty** (D-PORT-9 gate 5).
13. **The six data gates report unchanged counts: 11 / 10 / 8 / 6 / 18 / 5.** Any change is
    stop-and-escalate (`p1-00` R3, `p2-00` R6). `git status` on the assets and `app/schemas/` is
    clean.
14. Full pipeline green **from clean**, run with `--rerun-tasks`; report the number of test tasks
    actually executed. Kover at or above the current floor.
15. `./gradlew bundleRelease` clean; **AAB size reported against the 12 MB CI gate** (7.8 MB at
    1.6.0; the vendored vectors should be a rounding error against a dropped artifact — **if the AAB
    grows, say by how much and why**).
16. `./gradlew --configuration-cache` reports **zero** configuration-cache problems (`p0` §3.H).
17. **24–72 h vitals soak after the 1.12.0 rollout before any Phase 3 work is merged**, and per
    D-PORT-10 condition 3 the soak reads **reviews and support mail**, not only the crash dashboard:
    "I can't get to Settings" and "it keeps opening at Genesis" are review text, not crash clusters.

---

## 5. Boundaries / write set

**Mine, exclusively** (a subset of `p0` §5, scoped to this release):

```
gradle/libs.versions.toml
app/build.gradle.kts            (dependency block + versionCode/versionName)
gradle.properties               (only if resolution forces it — say why)
.github/workflows/**            (only if a version pin must move; re-verify all three byte-diff jobs)
distribution/whatsnew/whatsnew-en-US
docs/task-briefs/rel-1120-dependency-realignment.md
docs/task-briefs/p0-build-foundation.md
```

**Not mine — request it, do not edit it:**

- **`app/src/test/**` — Verification.** Including the `NavRegressionTest` extension (criterion 5) and
  its Truth→assertk state, which `p1-05` already settled. I state what the guard must cover; I do not
  write it.
- **`app/src/main/**` — the Android owners.** If a JB API differs from AndroidX at a call site, that
  is an escalation, not a quiet edit (`p1-00` R1). The only file I touch under `app/` is
  `build.gradle.kts`, which is a build file.
- **The ten vendored glyphs — Sr Shared-UI**, per `rel-1120-vendor-icons.md`. `p0` §3.B.7 is
  explicit: *"my contract is that the dependency is gone and the list is exactly these nine."*
- `docs/ios-execution-plan.md` (EM) · `docs/adr/**`, `docs/parity-matrix.md`, other
  `docs/task-briefs/*` (Staff / Verification).

**Dependency authority.** I am the only role that may add, remove or re-version a dependency
(`p0` §3.B.8, §5). That authority comes with the obligation to name alternatives when I reject.

---

## 6. Escalation triggers

Return the `ESCALATION:` block and **stop**. Do not improvise.

1. **The resolved AndroidX versions do not match criterion 2's prediction.** The whole risk
   assessment in §2.1 rests on the facade delegating as its module metadata says. → **Staff.**
   Blocking.
2. **`NavRegressionTest` reddens after the swap.** → **Staff**, blocking, immediately. Do not adjust
   the test to make it pass. It is the only guard, and a guard edited to fit the result is not a
   guard.
3. **Criterion 6 cannot be satisfied — the test and runtime classpaths cannot be made to resolve the
   same `androidx.navigation` version.** → **Staff.** Blocking. The alternative is a Gradle
   resolution constraint or a `resolutionStrategy.force`, both of which change what the shipped app
   resolves, which is not mine to decide alone.
4. **`p1-07` criterion 3 or 4 reddens on the new lifecycle artifact** (criterion 9). → **Staff**,
   blocking. `p1-07`'s own escalation trigger applies verbatim: *"if it reappears here, the reader's
   last-read position (D-V3-13) is at stake and the alternatives change persistence semantics — not
   an implementer's call."*
5. **The differential in criterion 11 shows a regression** — 1.11.0 restored the chapter and 1.12.0
   does not. → **Staff + EM.** Blocking. **Do not tag.**
6. **The R8 device smoke crashes anywhere.** → **Staff + EM.** Blocking. Diagnose via
   `adb logcat -b crash`, retrace with `app/build/outputs/mapping/release/mapping.txt`, and
   **verify `pg_map_id` matches the `r8-map-id` in the stack first** — that procedure is recorded in
   CLAUDE.md and it works.
7. **Any of the six data-gate counts changes** (criterion 13). → **Staff.** Blocking, immediately.
   Nothing in this release should touch a data gate; if one moves, something is very wrong.
8. **A JB API differs from the AndroidX one at a production call site**, so the swap cannot be
   dependency-only. → **Staff.** Blocking. `p1-00` R1 says no behaviour change; a required source
   edit means this is not the mechanical swap D-PORT-9 scoped, and the release's shape changes.
9. **Someone proposes merging this release into 1.11.0 or into Phase 3 to save a cycle.** Not an
   escalation — a **reject**, citing D-PORT-9 and D-PORT-10. Escalate only if overruled.
10. **The AAB crosses the 12 MB CI gate.** → **EM**, non-blocking unless CI is red, in which case
    blocking. Do not raise the gate to make a build pass.

---

## 7. Report format

1. **Every row of §3.1 and §3.2 with its final verdict**, flagged where reality differed from this
   brief — especially criterion 2.
2. **Every acceptance criterion** with VERIFIED / INFERRED / UNVERIFIED and its evidence. Criteria
   **9**, **10** and **11** get their literal output pasted; a summary is not acceptable for those
   three.
3. **The criterion-11 differential as two recorded baselines**, 1.11.0 and 1.12.0, stated even when
   they match.
4. **What is UNVERIFIED and why.** The iOS half of both swaps is UNVERIFIED by construction — no iOS
   target exists in 1.12.0, and the ABI-gap caveat in §3.2 is not exercised until Phase 2B/3. **Say
   so; do not let a green Android release be reported as verification of the iOS substrate.**
5. **Any dependency request received and rejected**, with the alternative named.
6. **The recorded caveat and its revisit trigger** for the JB navigation ↔ compose-runtime version
   gap (§3.2).
