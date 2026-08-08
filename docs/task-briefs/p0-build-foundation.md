# Task brief — P0: build foundation and the dependency contract

> **Assignee:** `kmp-build-release-eng` (singular — never parallelize this role)
> **Runs in:** Gate 0 → Phase 1 (per [../ios-port-approach.md](../ios-port-approach.md) §5, which is the
> authoritative phase vocabulary). **Not Phase 2, not Phase B.**
> **Blocks:** every other port task. Nothing that adds, moves or replaces a dependency may start
> until §3.B of this document is on disk and green.
> **Author:** Build & Release Engineer, 2026-08-08 · **Amended:** 2026-08-08 (§3.B.3.1 Truth ruling;
> §3.B.3.2 lifecycle/navigation evidence correction)
> **Companions:** [ios-delivery-pipeline.md](ios-delivery-pipeline.md) (Phase 4+; delivery only) ·
> [rel-1120-dependency-realignment.md](rel-1120-dependency-realignment.md) (release 1.12.0; executes
> §3.B.3's two coordinate swaps) · [rel-1120-vendor-icons.md](rel-1120-vendor-icons.md) (release
> 1.12.0; executes §3.B.7's nine glyphs)

---

## 1. Objective

Produce the **dependency contract** — a per-artifact, per-target classification that every other
agent can read and rely on **without asking me** — and the build foundation that contract implies:
target set, source-set hierarchy, framework shape, the Gradle→Xcode handoff, and the caching and
JVM configuration that make Kotlin/Native iteration survivable.

Done means: an agent asking "can I use X on iOS?" answers the question from §3.B, and an agent
asking "how does my Kotlin reach the iPhone?" answers it from §3.E — and a clean archive from Xcode
is **proven** to contain the Kotlin that is on disk right now, not a stale framework.

### Why this brief exists at all

The original delivery brief scheduled the dependency owner to arrive **after** the modules existed.
That is backwards. Every one of these is a Phase-1 decision with Phase-1 consequences:

- `docs/adr/0012` proposes replacing Hilt with Koin — that ships to Play as release **1.10.0**.
- `docs/adr/0014` proposes Ktor — that ships to Play as release **1.9.0**.
- `docs/adr/0009` proposes kotlinx-datetime — **1.9.0**.
- `docs/adr/0011` moves the assets — **1.11.0**.

All four ship to real Android users *before* an iOS target exists. If the dependency graph is
settled after the module split, three Play releases have already been cut against assumptions
nobody verified. This brief settles it first.

---

## 2. Context

- The repo is one Gradle module, `:app`, at Kotlin **2.3.21** / AGP **9.2.1** / compose-bom
  **2026.05.01** / minSdk **26** / Java **17**. See `gradle/libs.versions.toml`.
- The Android app is **live in production** (1.8.1 / 10801, 100 % rollout, 177 countries). The Play
  pipeline that ships it (`.github/workflows/release.yml` → `promote-production.yml`) is proven and
  is **not** in scope here. Do not touch it.
- `gradle.properties` today: `-Xmx4g -XX:MaxMetaspaceSize=1g`, `org.gradle.caching=true`,
  `org.gradle.configuration-cache=true`. All three matter below.
- **Three CI jobs re-derive the data assets byte-for-byte** (`data-rebuild`, `mcheyne-rebuild`,
  `chronological-rebuild` in `.github/workflows/ci.yml`). `data-rebuild` compiles SQLite 3.43.2 and
  `LD_PRELOAD`s it, because the committed `bible.db` bytes depend on the SQLite library version.
  **These never move to a macOS runner** — `DYLD_INSERT_LIBRARIES` is SIP-blocked on macOS and there
  is no equivalent. Moving them re-opens the defect that sat red on `main` for six weeks
  (CLAUDE.md, 2026-07-25). This is a hard rule, restated in §3.K.
- This machine has **no Xcode** (`xcode-select -p` → `/Library/Developer/CommandLineTools`) and
  `~/.konan` does not exist. Every claim in this brief about *runtime* iOS behaviour is therefore
  INFERRED at best. Claims about *artifact resolution* are VERIFIED — see §3.B.

### Evidence discipline

`VERIFIED` = I fetched the real artifact and got HTTP 200 (or a 404 where I say 404), on
2026-08-08. `INFERRED` = reasoned from documentation or tool contracts, not executed.
`UNVERIFIED` = nobody knows. Do not upgrade a label without doing the work.

---

## 3. Contract

### 3.A — Target set: `androidTarget`, `iosArm64`, `iosSimulatorArm64`. **No `iosX64`.**

```kotlin
androidTarget()
iosArm64()             // physical iPhone
iosSimulatorArm64()    // simulator on Apple Silicon
// iosX64() — deliberately absent. See below.
```

**This is not a cost optimisation. `iosX64` is not available.** VERIFIED 2026-08-08:

| Probe | Result |
|---|---|
| `org.jetbrains.compose.ui:ui-iosx64:1.11.1` | **404** |
| `org.jetbrains.compose.runtime:runtime-iosx64:1.11.1` | **404** |
| `org.jetbrains.compose.foundation:foundation-iosx64:1.11.1` | **404** |
| `org.jetbrains.compose.ui:ui-iosx64` maven-metadata, last published version | **1.11.0-alpha01** |
| `org.jetbrains.compose.ui:ui-iosarm64:1.11.1` | 200 |
| `org.jetbrains.compose.ui:ui-iossimulatorarm64:1.11.1` | 200 |

Compose Multiplatform **dropped `iosX64` after 1.11.0-alpha01**. Declaring the target would fail
dependency resolution, not merely waste build time.

**Consequences, stated so nobody discovers them:**

1. **The iOS simulator only runs on an Apple Silicon Mac.** An Intel Mac cannot build or run this
   app's simulator target. The owner's machine is an M3 Max, so this costs nothing today. It does
   mean an Intel-Mac contributor is unsupported. Record it in `docs/parity-matrix.md`.
2. **GitHub-hosted `macos-latest` is Apple Silicon** (has been since the `macos-14` image). INFERRED
   — confirm on the first hosted run and report the `uname -m`. If GitHub ever routes to an Intel
   image, iOS CI breaks with a resolution failure, not a compile error; the message will not say
   "wrong architecture."
3. Adding `iosX64` back later requires CMP to reinstate it. That is not our decision to make.

**Sub-targets are not a place for creativity.** Do not add `macosArm64`, `watchos*`, `tvos*`, `jvm`
or `js` "because they resolve." Each one multiplies link time, cache size and CI minutes for a
platform we do not ship. A `jvmTest`-only JVM target is the **one** permitted exception and it is
justified in §3.C.

---

### 3.B — The dependency contract

**This table is the answer. Do not ask me; read it.** Every "resolves" cell was checked by fetching
the artifact's POM on 2026-08-08 and recording the HTTP status. A cell marked ✅ means *I fetched a
200 for that exact coordinate at that exact version.*

#### B.1 — Build plugins

| Plugin | Version | Verdict |
|---|---|---|
| `com.android.application` | 9.2.1 | **Unchanged.** `androidApp` only. |
| `com.android.library` | 9.2.1 | **New usage** — the `shared/*` modules' Android side. |
| `org.jetbrains.kotlin.multiplatform` | **2.3.21** | **New.** Same version as the existing Kotlin plugin. Do not bump Kotlin in this task. |
| `org.jetbrains.kotlin.plugin.compose` | 2.3.21 | Unchanged; now applied to `shared/ui` too. |
| `org.jetbrains.kotlin.plugin.serialization` | 2.3.21 | Unchanged. |
| `org.jetbrains.compose` (CMP) | **1.11.1** ✅ | **New.** `compose-gradle-plugin:1.11.1` POM ✅. |
| `com.google.devtools.ksp` | 2.3.9 | Unchanged — still needed for the Room compiler. |
| `androidx.room` (`room-gradle-plugin`) | 2.8.4 ✅ | **New.** Room KMP wants the plugin for `schemaDirectory`; the `ksp { arg("room.schemaLocation", …) }` form in `app/build.gradle.kts:130` is Android-only. **The exported schemas must keep producing byte-identical `1.json`/`2.json` — see §3.J.** |
| `com.google.dagger.hilt.android` | 2.59.2 | **REMOVED** at release 1.10.0 (ADR-0012). |
| `org.jetbrains.kotlinx.kover` | 0.9.8 | Unchanged. **⟦VERIFY⟧ its KMP variant reporting** before claiming a coverage floor on `shared/*`; Kover's Android-variant plumbing in `app/build.gradle.kts:133-162` does not transfer as written. |
| `com.diffplug.spotless` | 7.0.4 | Unchanged. Extend its target globs to `shared/**` and `iosApp/**/*.swift`. |

#### B.2 — The critical version alignment: **no Compose version has to move**

This was the single most likely blocker and it is clear. VERIFIED:

- `compose-bom:2026.05.01` pins `androidx.compose.{runtime,ui,foundation}` → **1.11.2** and
  `androidx.compose.material3:material3` → **1.4.0**.
- `org.jetbrains.compose.ui:ui:1.11.1` publishes an `androidJvm` variant that resolves to the
  AndroidX 1.11.x line, plus `iosArm64` + `iosSimulatorArm64` variants. ✅
- The CMP plugin's `compose.material3` accessor resolves to
  **`org.jetbrains.compose.material3:material3:1.9.0`**, not 1.11.1 — extracted from the constants
  embedded in `compose-gradle-plugin-1.11.1.jar` (`ComposeBuildConfig`: `1.11.1`, `1.9.0`, `1.1.1`)
  and corroborated by the fact that **`org.jetbrains.compose.material3:material3:1.11.1` is a 404**
  and no stable `1.10.x`/`1.11.x` was ever published at that coordinate.
- `org.jetbrains.compose.material3:material3:1.9.0`'s `releaseRuntimeElements` requires
  **`androidx.compose.material3:material3:1.4.0`** — *exactly the version the BOM already pins.*

> 🚩 **Trap, and it will bite whoever writes the version catalog first.** Do **not** put
> `org.jetbrains.compose.material3:material3:1.11.1` in `libs.versions.toml`. It does not exist.
> Use the CMP plugin's `compose.material3` / `compose.runtime` / `compose.foundation` /
> `compose.ui` / `compose.components.resources` DSL accessors so the plugin supplies the coordinate
> **and** the version. Hard-coding CMP coordinates in the catalog is how a build breaks on the CMP
> upgrade after next.

**So: the Android app keeps compose-bom 2026.05.01 unchanged, and shipped Android users get the
same Compose bytes they have today.** That is the claim; it is verified at the dependency-graph
level and remains UNVERIFIED at the "same rendered pixels" level until Phase 3.

#### B.3 — Dependencies that change coordinate or version

| Today | Becomes | android | iosArm64 | iosSimArm64 | Evidence |
|---|---|---|---|---|---|
| `androidx.lifecycle:*:2.10.0` | **`org.jetbrains.androidx.lifecycle:*:2.11.0`** — **except `lifecycle-runtime-ktx`**, which has no JetBrains counterpart and stays `androidx.lifecycle`, Android-only | ✅ | ✅ | ✅ | Verdict unchanged; **the reason recorded here was WRONG and is corrected in §3.B.3.2.** `lifecycle-viewmodel-compose`, `lifecycle-viewmodel`, `lifecycle-runtime-compose`, `lifecycle-viewmodel-savedstate`, `lifecycle-common` all 200 at 2.11.0. |
| `androidx.navigation:navigation-compose:2.9.8` | **`org.jetbrains.androidx.navigation:navigation-compose:2.9.2`** | ✅ | ✅ | ✅ | Root POM 200. Per-target artifacts use JetBrains' legacy **`uikit*`** naming (`navigation-compose-uikitarm64`, `-uikitsimarm64`) — Gradle resolves them by KMP target *attributes*, not artifact name, so an `iosArm64` consumer gets the right one. `navigation-compose-iosarm64:2.9.2` is a 404 **and that is expected**; do not "fix" it. |
| `androidx.compose.material:material-icons-core:1.7.8` | **DROPPED — vendor the glyphs** | — | — | — | See B.5. |
| `com.google.dagger:hilt-*:2.59.2` | **`io.insert-koin:*:4.2.2`** | ✅ | ✅ | ✅ | `koin-bom`, `koin-core-iosarm64`, `koin-core-iossimulatorarm64`, `koin-android`, `koin-compose-viewmodel` (+`-iosarm64`), `koin-test-iosarm64` — all 200. **Use `koin-bom` and take versions from it.** |
| `com.google.truth:truth:1.4.4` | **`com.willowtreeapps.assertk:assertk:0.28.1`** everywhere; **Truth is deleted** | ✅ | ✅ | ✅ | `assertk-iosarm64` / `-iossimulatorarm64` 200. **Ruling amended 2026-08-08 — see §3.B.3.1 below. The earlier text ("Truth stays … do not delete it") is SUPERSEDED.** |
| `app.cash.turbine:turbine:1.2.0` | **1.2.1** (recommended) | ✅ | ✅ | ✅ | `turbine-iosarm64:1.2.0` is already 200, so 1.2.0 would work; 1.2.1 is current. A one-patch bump is cheaper now than mid-port. |

> **The first three rows now have a release and a brief.** All three ship in **`1.12.0 / 11200`**
> (**D-PORT-9**, `ios-execution-plan.md` §6): the two coordinate swaps in
> [`rel-1120-dependency-realignment.md`](rel-1120-dependency-realignment.md) (mine), the nine glyphs
> in [`rel-1120-vendor-icons.md`](rel-1120-vendor-icons.md) (Sr Shared-UI). **This section decides
> *what* the coordinates are; those briefs decide *when*, in *what order*, and with *what proof*.**

##### B.3.1 — Ruling: **Truth is deleted from the version catalog.** `p1-05` wins.

**This section exists because two documents on disk contradicted each other**, and a cold-start
agent would have hit the conflict with no way to resolve it:
[`p1-05-assertk-residual-sweep.md`](p1-05-assertk-residual-sweep.md) acceptance criterion 2 requires
Truth **removed** from `gradle/libs.versions.toml`, while this section previously said *"Truth
**stays** for tests that remain in `jvmTest`/`androidUnitTest`. Do not delete it."* Dependency
authority is mine (§3.B.8, §5), so the ruling is mine to make.

> **RULING. `p1-05` is correct and this brief was wrong. Truth is removed from
> `gradle/libs.versions.toml` and from `app/build.gradle.kts`.**

**Why the earlier sentence was wrong.** It conflated *"the test stays JVM-only"* with *"the
assertion library must stay JVM-only."* Those are different things. `BibleTextVerificationTest` (18)
does stay a **JVM-only gate** — it opens `bible.db` through `org.xerial:sqlite-jdbc`, which cannot
run on Kotlin/Native (§3.C) — but assertk publishes a JVM variant and runs there perfectly well. So
**no test anywhere in this codebase needs Truth once `p1-05` completes.** Keeping it would leave a
dead dependency in the catalog that a future test author can reach for, writing a JVM-only assertion
into a file that must later move to `commonTest` — which is the exact problem `p1-05` exists to
eliminate. A door propped open for no one.

**The condition for removal is mechanical, not calendar-based:**

1. **Truth stays in the catalog until `grep -rl "com.google.common.truth" app/src/test/kotlin`
   returns nothing** — i.e. `p1-05` acceptance criterion 1 is green. `p1-01` … `p1-04` convert as
   they go (`p1-00` R4), so the residual files still compile against Truth until `p1-05` closes.
   Removing it earlier is a red build.
2. **Then, and only then, I remove it** — Build & Release, on Verification's request, per `p1-05`'s
   own boundary line (*"Build & Release removes it — request the removal, do not edit the file"*).
3. **In a separate commit from `p1-05`'s conversion**, and the **last** commit of release **1.9.0**,
   so that a revert of the removal is one commit and does not drag ~92 files of conversion with it.
4. **If `p1-05` escalates on a file it cannot convert**, Truth stays and the removal is deferred with
   it. The gate is criterion 1 being green, not `p1-05` being declared finished.

**What is removed:** the `truth = "1.4.4"` version entry, the `truth = { group = "com.google.truth",
name = "truth", … }` library entry, and the `testImplementation(libs.truth)` line in
`app/build.gradle.kts`. Nothing else. **Note the load-bearing detail `p1-05` already names:**
`assertWithMessage` is the mechanism the six data gates use to produce diagnosable failures, and
those messages must survive the conversion — that is `p1-05`'s criterion 8, and it is the reason
assertk was chosen over bare `kotlin.test`. **Truth leaving the catalog must not be allowed to read
as "the diagnostics left with it."**

##### B.3.2 — Correction: the lifecycle and navigation evidence, re-probed 2026-08-08

I re-probed my own B.3 rows while writing
[`rel-1120-dependency-realignment.md`](rel-1120-dependency-realignment.md). **Two of the evidence
claims were wrong. The verdicts stand; the reasons did not.** Recording it here because a downstream
agent would otherwise have carried a false reason forward and used it to justify a version choice.

**(1) "2.11.0 is the floor — 2.10.0 has no iOS artifact" — WRONG.**
`org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0` **does** have an iOS artifact.
Its `.module` carries a `uikitArm64ApiElements-published` variant whose attributes include
`org.jetbrains.kotlin.native.target: ios_arm64`, redirecting to
`lifecycle-viewmodel-compose-uikitarm64:2.10.0` — Gradle resolves it for an `iosArm64` consumer **by
attribute**, exactly as the navigation row in the same table already explains. The 404 on the
lowercase `-iosarm64` form proves nothing. **I applied the `uikit*` rule to navigation and failed to
apply it to lifecycle.** §3.B.8 step 1 exists to prevent precisely this and I did not follow my own
step.

**2.11.0 is still the right version, for a corrected reason:** JB lifecycle **2.10.0** is compiled
against `org.jetbrains.compose.runtime:runtime:1.9.3` and `org.jetbrains.compose.ui:ui:1.9.3`, while
**2.11.0** is compiled against `runtime:1.11.0` — the line CMP **1.11.1** actually ships (§3.B.2).
On Kotlin/Native the klib is **linked**, not classpath-resolved, so a two-minor compose-runtime gap
is an ABI risk taken for nothing. Choosing 2.10.0 would buy a zero-delta Android side at the cost of
the substrate Phase 3 must link against.

**(2) Both swaps are facades on Android, not lineage changes.** VERIFIED from the `.module` files:

- `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose-android:2.11.0`'s
  `androidRuntimeElements-published` declares **`androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0`**;
  `lifecycle-viewmodel-android:2.11.0` declares **`androidx.lifecycle:lifecycle-viewmodel:2.11.0`**.
- `org.jetbrains.androidx.navigation:navigation-compose:2.9.2`'s `debugRuntimeElements-published`
  and `releaseRuntimeElements-published` declare **`androidx.navigation:navigation-compose:2.9.7`**.

So on **Android** the implementation stays AndroidX. The user-visible deltas are `androidx.lifecycle`
**2.10.0 → 2.11.0** (a minor upgrade) and `androidx.navigation` **2.9.8 → 2.9.7** (a one-patch
downgrade), behind two facades. **This makes the 1.12.0 risk smaller than `ios-execution-plan.md`
§6's table implies — it does not remove it**, because a facade is still new code on the resolution
path and both failure modes stay silent. Reported to the EM; **D-PORT-9 stands.**

**(3) Two artifacts have no JetBrains counterpart at any version.** VERIFIED against the group
listings on Maven Central:

| Missing | Consequence |
|---|---|
| `org.jetbrains.androidx.lifecycle:lifecycle-runtime-ktx` | Stays `androidx.lifecycle:lifecycle-runtime-ktx`, **Android-only, in `androidApp`.** Its sole consumer is `androidx.lifecycle.lifecycleScope` in `MainActivity.kt`. Bump it to 2.11.0 so the whole `androidx.lifecycle` graph resolves at one version rather than by Gradle conflict resolution. |
| `org.jetbrains.androidx.navigation:navigation-testing` | **`NavRegressionTest`'s `TestNavHostController` must keep coming from `androidx.navigation:navigation-testing`, and that test can never move to `commonTest`.** It also carries a real trap: that artifact currently shares `version.ref = "navigationCompose"`, so after the swap it would silently resolve the **unit-test** classpath to navigation **2.9.8** while the app ships **2.9.7** — the only automated guard on the swap testing a version nobody runs. Pin it to its own ref at **2.9.7**. Full argument and the required proof: `rel-1120-dependency-realignment.md` §3.2 and criterion 6. |

#### B.4 — Dependencies that keep their version and simply gain iOS targets

| Artifact | Version | android | iosArm64 | iosSimArm64 | Note |
|---|---|---|---|---|---|
| `androidx.room:room-runtime` | 2.8.4 | ✅ | ✅ | ✅ | Room KMP. `room-compiler:2.8.4` ✅ (KSP). |
| `androidx.sqlite:sqlite-bundled` | **2.6.2** | ✅ | ✅ | ✅ | **New, required.** Room 2.8.4 declares `androidx.sqlite:*:2.6.2`; use that version, not `sqlite`'s newer 2.7.0, unless Room is also bumped. Per ADR-0007 / the Shared-Core recommendation: `AndroidSQLiteDriver` on Android (byte-parity for live users, zero size cost), `BundledSQLiteDriver` on iOS. |
| `androidx.datastore:datastore-preferences` | 1.2.1 | ✅ | ✅ | ✅ | **No version change.** Both iOS variants 200. |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.9.0 | ✅ | ✅ | ✅ | No change. |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.10.2 | ✅ | ✅ | ✅ | No change. |

#### B.5 — New dependencies

| Artifact | Version | android | iosArm64 | iosSimArm64 | Why |
|---|---|---|---|---|---|
| `org.jetbrains.kotlinx:kotlinx-datetime` | **0.8.0** | ✅ (JVM variant) | ✅ | ✅ | ADR-0009. **There is no `kotlinx-datetime-android` artifact — a 404 is correct**; Android resolves the JVM variant. minSdk 26 means the `java.time` backing is present without desugaring. |
| `com.squareup.okio:okio` | **3.18.1** | ✅ | ✅ | ✅ | ADR-0014 (`BibleTextCache`), ADR-0010 (gate file reads). |
| `io.ktor:ktor-client-core` | **3.5.2** | ✅ | ✅ | ✅ | ADR-0014. |
| `io.ktor:ktor-client-okhttp` | 3.5.2 | ✅ | — | — | Android engine. |
| `io.ktor:ktor-client-darwin` | 3.5.2 | — | ✅ | ✅ | iOS engine. |
| CMP `compose.components.resources` | 1.11.1 | ✅ | ✅ | ✅ | ADR-0013 strings. Per-target artifacts use **camelCase** suffixes (`components-resources-iosArm64`) — `-iosarm64` lowercase is a 404. Another reason to use the DSL accessor, not a literal coordinate. |
| CMP `compose.uiTest` (`org.jetbrains.compose.ui:ui-test`) | 1.11.1 | ✅ | ✅ | ✅ | `runComposeUiTest` for the ported Compose tests. |

#### B.6 — Android-only, and they stay that way

These are **not** ports and must never appear in a `commonMain` dependency block. Any request to
move one is an automatic reject.

`androidx.browser:browser` (Custom Tabs) · `com.google.android.play:app-update{,-ktx}` ·
`androidx.glance:glance-{appwidget,material3,appwidget-testing}` · `androidx.core:core-ktx` ·
`androidx.activity:activity-compose` · `org.robolectric:robolectric` ·
`androidx.room:room-testing` · `junit:junit` · `androidx.navigation:navigation-testing`.

`org.xerial:sqlite-jdbc:3.50.1.0` stays **`jvmTest`-only** — it is what
`BibleTextVerificationTest` uses to open `bible.db` outside Room, and that is the entire reason a
JVM target exists (§3.C).

#### B.7 — `material-icons-core`: drop it and vendor 9 glyphs

`androidx.compose.material:material-icons-core:1.7.8` is Android-only. The JetBrains fork is
**frozen at 1.7.3** and publishes **no `-android` variant** and no `iosarm64` variant — only the
legacy `uikit*` set (`material-icons-core-android:1.7.3` → 404, `-iosarm64:1.7.3` → 404,
`-uikitarm64:1.7.3` → 200). Depending on a frozen fork at a version three releases behind the
Compose in use, for icons, is not a dependency I will accept.

**Verdict: vendor the glyphs as multiplatform `ImageVector`s in `shared/ui` and delete the
dependency.** This project has done exactly this before (`widget_preview.xml` in S9,
`ic_bible_book.xml`). The complete list — **9 glyphs**:

```
Icons.AutoMirrored.Filled.ArrowBack
Icons.AutoMirrored.Filled.KeyboardArrowLeft
Icons.AutoMirrored.Filled.KeyboardArrowRight
Icons.Filled.ArrowDropDown
Icons.Filled.Check
Icons.Filled.Close
Icons.Filled.DateRange
Icons.Filled.Edit
Icons.Filled.Settings
```

> **CORRECTED 2026-08-08 — this section said 10 and listed `Icons.Filled.ContentCopy`. It is 9.**
> `ContentCopy` occurs in exactly two places in the repo (`bible/ui/reader/VerseSelectionBar.kt:31`
> and `ui/AccessibilityGateTest.kt:468`) and **both are prose, both stating that the glyph does not
> exist in the frozen 1.7.8** — which is *why* Copy is a visible `TextButton` word rather than an
> icon (sprint 00Q, a11y-gate-pinned). The enumeration that produced "10" was a `grep` that matched
> comment text. Vendoring it would ship dead code and invite someone to reverse a deliberate
> accessibility decision. Re-verified by stripping comments before extracting: **9**. Caught by
> Sr Shared-UI reading the call sites.
>
> Also corrected: `ic_stats.xml` was cited here as a vendoring precedent. **It was deleted in
> sprint 15** and is not a live precedent.

Note `AutoMirrored` — three of the nine mirror under RTL. The app is not localized (ADR-0013), so
this is inert today, but vendor them as auto-mirroring anyway rather than silently dropping a
property. The vendoring itself is Shared-UI's task, not mine; **my contract is that the dependency
is gone and the list is exactly these nine.**

**Now owned by a release and a brief (D-PORT-9):** the vendoring is
[`rel-1120-vendor-icons.md`](rel-1120-vendor-icons.md) (Sr Shared-UI); removing the catalog and
build-file entries is mine, in [`rel-1120-dependency-realignment.md`](rel-1120-dependency-realignment.md)
§3.3. **Order matters: the glyphs merge first, the dependency removal second.** Removing it before
the glyphs exist is a red build; leaving it after they exist ships a dead dependency.

#### B.8 — The rule for anything not in this table

**There is no dependency budget beyond the one on this page.** If you need an artifact that is not
listed:

1. Check it yourself first: fetch
   `https://repo1.maven.org/maven2/<group-path>/<artifact>-iosarm64/<v>/<artifact>-iosarm64-<v>.pom`
   and the `-iossimulatorarm64` equivalent. A 404 on the lowercase form is **not** proof of absence
   — check the `.module` file's variant list for `uikit*` or camelCase naming before concluding
   anything.
2. Then escalate to me with: the coordinate, the exact HTTP results, the size impact, and what the
   code does without it.

I will reject a single-platform dependency in a shared module every time, and I will name the
alternative. The alternatives that have already worked on this project, in order of preference:
put it behind a `shared/platform` interface (12 already exist); vendor the small part you need;
or keep the feature Android-only and record it in `docs/parity-matrix.md`.

---

### 3.C — Source-set hierarchy

Per ADR-0001, five modules. The hierarchy inside each `shared/*`:

```
commonMain
├── androidMain          (Android actuals; may see android.*)
├── iosMain              (iOS actuals; sees platform.Foundation, platform.UIKit)
│   ├── iosArm64Main
│   └── iosSimulatorArm64Main
└── jvmMain              — ONLY in shared/data, and only for the reasons in the note below

commonTest               (assertk, kotlin.test, turbine, coroutines-test)
├── androidUnitTest      (Robolectric, junit4, Truth, room-testing, glance-testing)
├── iosTest
└── jvmTest              (sqlite-jdbc — BibleTextVerificationTest lives here forever)
```

Use the default hierarchy template. Declare `iosMain` via the `applyDefaultHierarchyTemplate()`
intermediate source set — **do not hand-roll `dependsOn` edges.** Hand-rolled hierarchies are the
most common cause of "the IDE resolves it but the compiler doesn't."

> **The one JVM target, justified.** `BibleTextVerificationTest` (18 assertions) opens the real
> `bible.db` through `org.xerial:sqlite-jdbc`, deliberately bypassing Room, and is the gate that
> protects the project's second core-IP asset. It cannot run on Kotlin/Native and it must not be
> weakened to fit. A `jvm()` target in `shared/data` whose *only* consumer is `jvmTest` is the
> cheapest way to keep it exactly as it is. It costs one extra compilation of `shared/data` and
> ships in nothing. If Staff prefers to keep that test in `:app`'s `androidUnitTest` instead, that
> is also acceptable and removes the JVM target — **that is Staff's call, not mine**; either way
> the 18 assertions run unchanged on every PR.

**The `shared/domain` purity rule from the constitution is enforced mechanically, by me, in CI**, as
ADR-0001 requires: a grep step that fails the build on `^import (java|android)\.` under
`shared/domain/src/commonMain` and `shared/ui/src/commonMain`, and on `\bisIOS\b` anywhere in a
`commonMain` tree. Good intentions do not survive twenty sprints; a failing job does.

---

### 3.D — Framework decision

```kotlin
listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
    target.binaries.framework {
        baseName = "Shared"
        isStatic = true
    }
}
```

**One umbrella framework named `Shared`, static, exported explicitly.**

**`isStatic = true`.** Reasons, in order:

1. A dynamic Kotlin/Native framework must be embedded *and signed* as an app-bundled framework, and
   the `embedAndSignAppleFrameworkForXcode` path exists precisely because that step is easy to get
   wrong. A static framework is linked into the app binary; there is nothing to sign and nothing to
   forget.
2. Apple's dynamic-framework startup cost is real on cold launch, and this app's cold start is
   already carrying a 5.6 MB asset copy on first run.
3. It matches every current CMP `iosApp` template, so we are on the well-trodden path.

**Cost accepted:** the app binary grows by the whole framework, and dead-code elimination happens at
link time — which is exactly the DCE hazard the `ios-release-smoke` gate (delivery brief §3.7)
exists to catch. Say that out loud rather than discovering it.

**One umbrella, not one framework per module.** Multiple frameworks from one Gradle build produce
duplicate Kotlin runtime symbols and an unpleasant class of link error. The umbrella is created in
whichever module sits at the top of the dependency graph (`shared/ui`), and it `export`s the others:

```kotlin
export(project(":shared:domain"))
export(project(":shared:platform"))
// shared/data is NOT exported — Swift must not see Room or Ktor types.
```

**The export list is a contract, not a convenience.** Only what Swift genuinely needs crosses:

| Exported | Why |
|---|---|
| `shared:ui` (the framework's own module) | `MainViewController()` |
| `shared:domain` | the models the platform actuals pass around |
| `shared:platform` | the ~15 interfaces Swift implements |
| **not** `shared:data` | Swift has no business seeing a Room entity or a Ktor client |
| **not** `kotlinx-datetime` | ⚠️ see below |

> ⚠️ **`kotlinx-datetime` and the export list — the trap I expect us to hit.** If any
> `shared/platform` interface method takes or returns a `LocalDate`, Swift needs that type, which
> means `export(libs.kotlinx.datetime)` — dragging a third-party module into our public ABI.
> ADR-0002 already forbids this from the other direction: *"every `shared/platform` interface that
> Swift implements must use only bridge-friendly types."* **Design the interfaces so no
> `kotlinx-datetime` type crosses into Swift** (pass an ISO-8601 `String` or an epoch-day `Int`, and
> convert in Kotlin). If that proves impossible for a specific interface, it is an ADR-0002
> escalation to Staff — not a quiet `export` line from me.

**No CocoaPods.** See §3.E.

---

### 3.E — The Gradle→Xcode handoff

**Mechanism: `embedAndSignAppleFrameworkForXcode`, invoked from an Xcode "Run Script" build phase
that runs *before* "Compile Sources". Not CocoaPods.**

Rejected alternatives, so nobody re-proposes them:

- **CocoaPods integration (`kotlin("native.cocoapods")`)** — adds Ruby, a `Podfile`, a
  `.xcworkspace`, a `pod install` step in CI, and a second dependency manager whose failures look
  nothing like Gradle's. This project has **zero** CocoaPods dependencies and needs none. The plugin
  exists for teams consuming Pods; we are not one.
- **A checked-in XCFramework** — correct for *publishing* a framework to third parties, wrong for a
  monorepo where the app and the framework ship together. It adds a build-and-commit step whose
  entire purpose is to let the two drift.
- **Manual `Framework Search Paths` + a hand-copied `.framework`** — this is the configuration that
  produces the failure mode below, on purpose.

#### The failure mode this section exists to prevent

`embedAndSignAppleFrameworkForXcode` **fails silently in the direction that matters.** If the run
script does not execute, or executes with the wrong `CONFIGURATION`/`SDK_NAME`/`ARCHS`, or executes
*after* compilation, Xcode does not go red — it links whatever `Shared.framework` is already in
`DerivedData` from a previous run. The symptom is a Kotlin change that "didn't take," or a missing
symbol at link time, or worse: an archive that ships month-old logic. There is **no error message
that says "your framework is stale."**

Every acceptance criterion in §4.E is designed around that single fact.

#### Required shape

1. `iosApp` has a Run Script phase, ordered **first**, before Compile Sources:
   ```sh
   cd "$SRCROOT/.."
   ./gradlew :shared:ui:embedAndSignAppleFrameworkForXcode
   ```
2. The phase declares **no** output files and **no** `$(SRCROOT)` input file list. Declaring them
   invites Xcode to skip the phase as up-to-date, which is precisely the silent staleness above.
   Accept the unconditional Gradle invocation; Gradle's own up-to-date checking makes it fast.
3. `CONFIGURATION`, `SDK_NAME`, `ARCHS`, `TARGET_BUILD_DIR` and `FRAMEWORKS_FOLDER_PATH` are read
   from the Xcode environment by the Gradle task. **Do not set them in the script.** A hard-coded
   `-Pkotlin.native.cocoapods…` or an `ARCHS` override is how a Debug-simulator framework ends up in
   a Release archive.
4. `Shared.framework`'s parent directory goes in `FRAMEWORK_SEARCH_PATHS`; because the framework is
   static there is **no** "Embed & Sign" entry in the General tab. If someone adds one, the archive
   will contain a framework that is also statically linked. Reject it.

---

### 3.F — `~/.konan` cache strategy

Kotlin/Native downloads a per-version toolchain into `~/.konan` (≈ 2–4 GB for one Kotlin version;
more once a second version is present). This is **not** covered by the Gradle cache and it is the
difference between a 25-minute cold CI run and a 4-minute warm one.

| Environment | Strategy |
|---|---|
| **Local (owner's Mac)** | Leave at `~/.konan`. Budget **4 GB** and put it in the disk-space prerequisite (see `RELEASING-IOS.md`). After a Kotlin upgrade, the old version's directory is dead weight — prune it deliberately; do not `rm -rf ~/.konan` reflexively, that costs a full re-download. |
| **GitHub-hosted runner** | `actions/cache` keyed on `konan-${{ runner.os }}-${{ hashFiles('gradle/libs.versions.toml') }}`, path `~/.konan`. Keying on the catalog means a Kotlin bump invalidates it, which is correct. **Report the cache hit rate and the cold-vs-warm wall clock in the first run** — if the cache save/restore costs more than the download, say so and drop it rather than keeping a ritual. |
| **Self-hosted runner** | Do **not** cache. The directory persists between jobs by definition. Instead add a scheduled prune, because a self-hosted `~/.konan` grows silently across Kotlin upgrades and this machine is at 94 % disk. |

Compiler caches (the `.konan/kotlin-native-prebuilt-*` per-target caches) apply to **debug** links
only. `linkReleaseFrameworkIosArm64` does not use them — that is not a misconfiguration to hunt, it
is how release links work, and it is why the release link is 6–15 min locally and materially longer
hosted. Budget for it; do not chase it.

---

### 3.G — JVM args

`gradle.properties` today: `-Xmx4g -XX:MaxMetaspaceSize=1g`.

**Raise to `-Xmx6g` when the KMP plugin lands**, and set the Kotlin daemon explicitly:

```properties
org.gradle.jvmargs=-Xmx6g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8
kotlin.daemon.jvmargs=-Xmx4g
```

Reasoning: the Kotlin/Native compiler runs in the Kotlin daemon and is memory-hungry on the
Compose-heavy `shared/ui` module. The failure mode when it is short is a `Daemon compilation failed`
or an OOM that reads like a compiler bug. `-Xmx6g` is a starting point on a machine with headroom,
**not a measurement** — record actual peak usage on the first full `linkRelease` and tune down if
the ceiling is never approached. On a GitHub-hosted `macos-latest` (currently 14 GB RAM, INFERRED)
6 GB + 4 GB is safe; on a 16 GB machine it is not, so the CI value may need to differ from local.

---

### 3.H — Configuration cache and `linkRelease*`

`org.gradle.configuration-cache=true` is on today and must **stay** on — it is a large part of why
this repo's Android builds are quick.

**Known caveat:** Kotlin/Native link tasks, and in particular `linkReleaseFramework*` and the
`embedAndSignAppleFrameworkForXcode` wrapper, have historically been the last KMP tasks to become
configuration-cache-compatible, and the `xcodeproj`-driven invocation passes properties in a way
that has produced "configuration cache problems found" reports. INFERRED — not reproduced here,
because there is no Xcode on this machine.

**Contract:**

1. Do **not** globally disable the configuration cache to make an iOS task pass. That penalises
   every Android build for an iOS problem.
2. If a link task is incompatible, the escape hatch is scoped: `--no-configuration-cache` on that
   one invocation inside the Xcode run script, with a comment naming the task and the date.
3. Report it as a **known defect with a revisit trigger** (next Kotlin bump), not as a
   configuration choice.
4. `org.gradle.caching=true` is unaffected and stays.

---

### 3.I — First task: the 20-minute asset-packaging experiment

**Do this before touching a single production file.** ADR-0011 identifies a specific, cheap,
high-consequence trap: **iOS bundle resources are flat by default.** A directory added to an Xcode
target as a *group* (yellow) contributes its files with their basenames; only a *folder reference*
(blue) preserves the directory structure. Get it wrong and:

```
shared/assets/plans/bible_companion/plan.json  →  Bundle/plan.json
shared/assets/plans/mcheyne/plan.json          →  Bundle/plan.json   ← collides
shared/assets/plans/chronological/plan.json    →  Bundle/plan.json   ← collides
```

Three files, one name, and whichever wins is nondeterministic. It presents as *"the wrong plan
loaded"* — a data bug, in the part of the app that is the project's core IP, with no build error.

**The experiment.** A throwaway Xcode project — **not** the real `iosApp`, and nothing committed:

1. Create an empty iOS app.
2. Add `docs/…/scratch/plans/` (a copy of the three `plan.json` files in their real subdirectories
   plus `registry.json`) as a **folder reference**.
3. Build for the simulator.
4. In the built `.app`, run `find . -name plan.json` and
   `NSBundle.mainBundle.pathForResource("plans/mcheyne/plan", ofType: "json")`.

**Pass:** three distinct paths, each at `plans/<planId>/plan.json`, and the `pathForResource` lookup
with the nested subdirectory resolves. **Fail:** anything else — including "it works but only via
`pathForResource(_:ofType:inDirectory:)`", which is a different API than the shared code will call.

**On fail, stop.** Do not proceed to the ADR-0011 asset move. The fallback is a Copy Files build
phase with an explicit destination subpath per plan, or an asset-name flattening that the loader
knows about — and *choosing between those is Staff's call*, because it changes what
`TextAssetSource.read("plans/mcheyne/plan.json")` means. Escalate.

**Deliverable regardless of outcome:** the exact recipe (which Xcode UI action, what it writes into
the project file, how to assert it) recorded in the delivery brief, plus the `BundleAssetIntegrityTest`
requirement handed to Verification with the five expected nested paths. A 20-minute experiment that
prevents a 20-minute mistake with a multi-day diagnosis is the best-value task in this brief.

---

### 3.J — `inputs.dir` preservation: non-negotiable

`app/build.gradle.kts:90-117` carries two things that **must survive the ADR-0011 asset move**:

```kotlin
it.systemProperty("planAssetsDir", …dir("src/main/assets").asFile.absolutePath)
it.inputs.dir(…dir("src/main/assets"))
    .withPropertyName("planAssets")
    .withPathSensitivity(PathSensitivity.RELATIVE)
```

The `inputs.dir` declaration is there because of a Sprint-1 lesson recorded in the file's own
comment: **without it, editing a bundled asset leaves the test task UP-TO-DATE and the gate never
re-runs.** A data-verification gate that silently does not run is worse than no gate — it is a green
check mark over an unverified asset, on the files that *are* this project's product.

**Contract:**

1. After the move, the equivalent declarations point at `shared/assets` and are present on **every**
   test task that runs a data gate — Android unit test, JVM test, and the iOS test tasks.
2. `PathSensitivity.RELATIVE` is preserved. `ABSOLUTE` would defeat the build cache across machines;
   `NAME_ONLY` would miss content changes.
3. The `schemas` `inputs.dir` (line 113) is preserved identically — it guards the Room migration
   fixtures.
4. **Prove it, don't claim it.** Acceptance criterion §4.J is a demonstration: touch a byte in a
   plan JSON, re-run the gate task with no other change, and show it *executed* rather than
   reporting UP-TO-DATE. Then restore the byte and show the gate is green.

---

### 3.K — Restated hard rules

1. **`data-rebuild`, `mcheyne-rebuild` and `chronological-rebuild` never move to a macOS runner.**
   `data-rebuild` `LD_PRELOAD`s a self-compiled SQLite 3.43.2 because the committed `bible.db` bytes
   depend on the SQLite library version; macOS has no equivalent (`DYLD_INSERT_LIBRARIES` is
   SIP-blocked for system binaries). They stay on `ubuntu-latest`, unchanged, forever. Their *paths*
   change with the ADR-0011 asset move and nothing else does.
2. **Exactly one copy of the plan JSONs and `bible.db` in git.**
   `find . -name bible.db -not -path './*/build/*'` returns one path.
3. **`fallbackToDestructiveMigration` stays off.** Not "temporarily during development."
4. **The Android release pipeline is not refactored to share steps with iOS.** Duplication between
   two release pipelines is correct; a shared abstraction that breaks a production Play release is
   not.

---

## 4. Acceptance criteria

Numbered to the contract section each one proves. **Report each as VERIFIED / INFERRED / UNVERIFIED
with the evidence, never as "done."**

**A. Target set**
1. `./gradlew :shared:domain:tasks` lists `compileKotlinIosArm64` and
   `compileKotlinIosSimulatorArm64` and **no** `…IosX64`. Paste the output.

**B. Dependency contract**
2. `./gradlew :shared:ui:dependencies --configuration iosArm64CompileKlibraries` resolves with zero
   `FAILED` lines, on a **clean** Gradle cache (`--refresh-dependencies`). Same for
   `iosSimulatorArm64` and for the Android release runtime classpath.
3. `libs.versions.toml` contains **no** literal `org.jetbrains.compose.*` coordinate. CMP artifacts
   come from the plugin DSL. (Guards against the §3.B.2 material3-1.11.1 trap.)
4. `androidx.compose.material:material-icons-core` appears nowhere in the build. `grep -r
   "material-icons" --include=*.kts --include=*.toml .` is empty.
5. The Android release APK's dependency set is **unchanged** for Compose: `androidx.compose.ui` at
   1.11.2 and `androidx.compose.material3` at 1.4.0, same as 1.8.1. Show the resolved versions
   before and after.

**C. Source sets**
6. The CI purity check fails a deliberately-introduced `import java.time.LocalDate` in
   `shared/domain/src/commonMain`, and passes once removed. **Demonstrate the failure** — an
   unproven guard is not a guard.

**D. Framework**
7. `./gradlew :shared:ui:linkDebugFrameworkIosSimulatorArm64` produces `Shared.framework`, and
   `nm -gU Shared.framework/Shared | grep MainViewController` finds the entry point.
8. The exported-module list in the build script matches §3.D exactly. No `shared:data`, no
   `kotlinx-datetime`.

**E. Gradle→Xcode handoff — the criteria that matter most**
9. **The staleness proof.** In one sitting, with no `clean` and no DerivedData deletion:
   a. Build and run the app in the simulator; observe some string rendered by shared Kotlin.
   b. Change that string in `shared/ui` Kotlin only.
   c. **Archive** (Product → Archive, Release configuration).
   d. Extract the string from the archived binary and show it is the **new** value.
   The point is (c): the criterion is a *Release archive*, not a Debug run, because the Debug path
   is the one that accidentally works.
10. **The negative proof.** Temporarily disable the Run Script phase, archive again, and show the
    build either fails or produces the **old** string. Then re-enable and re-verify. This is the
    only way to know the phase is load-bearing rather than incidental. Record both outcomes.
11. `Shared.framework` does **not** appear in the app bundle's `Frameworks/` directory (it is
    static). `ls "$APP/Frameworks"` shows no `Shared.framework`.
12. A **clean clone** into a temp directory, followed only by documented commands, produces a
    simulator build. Actually clone; do not reason about it.

**F/G/H. Caching, memory, configuration cache**
13. Report, as numbers: cold `linkDebugFrameworkIosSimulatorArm64` wall clock, warm wall clock,
    `~/.konan` size on disk, and peak Kotlin-daemon memory during a full `linkRelease`. **These are
    the baseline for the iteration-speed budget** — a later regression is a ticket, not a fact of
    life.
14. `./gradlew --configuration-cache` reports **zero** configuration-cache problems for the Android
    build. If any iOS link task is incompatible, name it, name the scoped workaround, and name the
    revisit trigger.

**I. Asset experiment**
15. Pass/fail per §3.I with the `find` output pasted, plus the recipe recorded.

**J. `inputs.dir`**
16. The touch-a-byte demonstration in §3.J.4, showing the task **executed** rather than
    `UP-TO-DATE`, on every test task that runs a data gate.

**Global**
17. `./gradlew build` on the Android side is green and the **six** data gates report their existing
    counts unchanged: plan 11 · M'Cheyne 10 · Chronological 8 · PlanSegment 6 · BibleText 18 ·
    RoomOpen 5. Any change to any of those numbers in this task is a stop-and-escalate.

---

## 5. Boundaries / write set

**Mine, exclusively — Constitution rule 6 binds everyone except me on these:**

```
**/build.gradle.kts
settings.gradle.kts
gradle/libs.versions.toml
gradle.properties
gradle/wrapper/**
iosApp/Configuration/**
iosApp/project.yml            (see the delivery brief §3.9 — XcodeGen)
fastlane/**
.github/workflows/**
.gitignore
docs/RELEASING-IOS.md
docs/task-briefs/p0-build-foundation.md
docs/task-briefs/ios-delivery-pipeline.md
docs/task-briefs/rel-1120-dependency-realignment.md
distribution/whatsnew/whatsnew-en-US
```

**Not mine — escalate, do not edit:**

- `shared/**` Kotlin sources → Shared-Core / Shared-UI. I create the modules' *build files*; I do
  not write their code. **I do not vendor the ten icons** (§3.B.7) — that is Shared-UI's task
  against my contract.
- `iosApp/**` Swift sources and `Info.plist` → iOS Platform (I may read them).
- `app/src/**` → the Android owners. The one exception is the `sourceSets`/`testOptions` block in
  `app/build.gradle.kts`, which is a build file and therefore mine.
- `tools/*.py` → Data. The ADR-0011 asset move changes their output paths; **I raise it, they
  change it, I re-verify the byte-diff jobs.** I do not edit a generator that produces
  gate-verified IP.
- `docs/adr/**`, `docs/parity-matrix.md`, `docs/test-port-strategy.md`,
  `docs/ios-execution-plan.md`, other `docs/task-briefs/*` → Staff / Verification / EM.
- `.github/workflows/{ci,release,promote-production,assign-track}.yml` — mine by path, but **the
  live Android pipeline ships production releases.** Touch only the ADR-0011 path changes in
  `ci.yml`, and re-verify all three byte-diff jobs reproduce zero afterwards.

**Dependency authority.** I am the only role that may add, remove or re-version a dependency, and
only after the checks in §3.B.8. That authority comes with an obligation: when I reject, I name the
alternatives.

---

## 6. Escalation triggers

Return the `ESCALATION:` block and stop. Do not improvise.

1. **The asset-packaging experiment (§3.I) fails.** The fallback changes what
   `TextAssetSource.read(path)` means, which is an interface-semantics question. → **Staff.**
   Blocking.
2. **A `shared/platform` interface needs a `kotlinx-datetime` type in its Swift-facing signature**
   (§3.D). Exporting a third-party module into our public ABI contradicts ADR-0002. → **Staff.**
   Blocking for that interface only.
3. **Any of the six data-gate assertion counts changes** during this task (§4.17). → **Staff.**
   Blocking, immediately.
4. **A dependency has no artifact for a declared target and the feature cannot go behind a
   `shared/platform` interface.** → **Staff**, with the alternatives named. Blocking for that
   feature.
5. **The Room KMP identity hash for `bible.db` differs from `8144e1bc57f05006d1a15856ac762552`**
   (Gate 0 / V1, `tools/build_bible_db.py:44`). Not my spike, but if I hit it first it is
   ADR-0007 Stage 1a-vs-1b. → **Staff.** Blocking the whole bible track.
6. **The exported Room `ProgressDatabase/2.json` is not byte-identical** after the Room-plugin
   change (§3.B.1). → **Staff.** Blocking, and **do not regenerate the baseline** — that is how a
   migration guard is silently deleted.
7. **The configuration cache cannot be kept on for the Android build** (§3.H). → **Staff**, because
   it is an Android-user-facing build-time regression, not an iOS detail. Blocking.
8. **Anyone proposes moving a `*-rebuild` job to macOS** (§3.K.1). Not an escalation — a **reject**,
   citing the six-week outage. Escalate only if overruled.
9. **Kotlin/Native link times exceed 20 minutes locally for a debug simulator link**, or a warm
   incremental shared-code change exceeds 3 minutes. That is an iteration-speed defect that
   throttles every other agent. → **EM**, as a ticket. Non-blocking but do not absorb it.

---

## 7. Report format

State plainly, in this order:

1. **The dependency contract as executed** — every row of §3.B with its final verdict, flagged where
   reality differed from this brief.
2. **Every acceptance criterion** with VERIFIED / INFERRED / UNVERIFIED and its evidence. Criterion
   9 and 10 (the archive staleness proof) get their literal output pasted; a summary is not
   acceptable for those two.
3. **The numbers** from §4.13 — cold link, warm link, `~/.konan` size, peak daemon memory. These
   become the baseline.
4. **What is UNVERIFIED and why**, especially anything blocked on Xcode not being installed.
5. **Every dependency request received and rejected**, with the alternative given.
6. **Any file outside my write set that needs to change**, named, with who owns it.
