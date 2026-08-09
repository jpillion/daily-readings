# The dependency contract — per artifact, per target, per source set

> **Status:** AUTHORITATIVE and OPERATIVE. This document is the answer to *"can I use X on iOS?"*
> **Owner:** Build & Release Engineer (**singular**). I am the only role that may add, remove or
> re-version a dependency. **You do not need to ask me anything that is on this page.**
> **Date:** 2026-08-08 · **Executes:** [`task-briefs/p0-build-foundation.md`](task-briefs/p0-build-foundation.md)
> §3.B, dispatched alone per [`ios-execution-plan.md`](ios-execution-plan.md) §1 Action 3.
>
> **Relationship to `p0-build-foundation.md` §3.B.** p0 §3.B holds the *reasoning*. **This page holds
> the operative table**, re-probed end-to-end against the live repositories on 2026-08-08. Where the
> two disagree, **this page wins** and the divergence is listed in §9. Nothing else in p0 (§3.D
> framework shape, §3.E the Gradle→Xcode handoff, §3.F–§3.I) was executed — see §11.

## Evidence discipline

`VERIFIED` = I fetched the real artifact and recorded the HTTP status, or read the real bytes
(POM, Gradle `.module`, jar, klib manifest) on **2026-08-08**, or ran the command in this repo.
`INFERRED` = reasoned from documentation or module metadata; nobody executed it.
`UNVERIFIED` = nobody knows. **Labels are never upgraded without doing the work.**

Every "resolves" cell below is a real HTTP status. The full probe log is §8.

---

## 1. Rulings issued today

Four decisions. Each is mine to make (p0 §3.B.8, §5) and each is stated so that no downstream agent
has to re-open it.

### R1 — Google Truth is **deleted** from the version catalog. `p1-05` wins. (Contradiction closed.)

**This was already settled on disk before I started and the execution plan is stale about it.**
p0 §3.B.3.1 (lines 180–220) issues the ruling, and `p1-05` criterion 2 (lines 126–149) already
cites it. `ios-execution-plan.md` §12.B still describes it as an open "direct conflict" — **that
sentence is out of date; strike it.**

**The ruling, restated unambiguously:** `com.google.truth:truth:1.4.4` is removed from
`gradle/libs.versions.toml` and from `app/build.gradle.kts:206`. It is replaced by
`com.willowtreeapps.assertk:assertk` everywhere, including in tests that stay JVM-only.

**Why the earlier p0 sentence ("Truth stays … do not delete it") was wrong:** it conflated *the test
stays JVM-only* with *the assertion library must stay JVM-only*. `BibleTextVerificationTest` (18)
does stay a JVM-only gate — it opens `bible.db` through `org.xerial:sqlite-jdbc`, which cannot run on
Kotlin/Native — but **assertk publishes a JVM variant** (`assertk-jvm:0.28.1` jar, **200**,
VERIFIED) and runs there perfectly. Keeping Truth would leave a dead JVM-only assertion library in
the catalog for a future test author to reach for, writing a JVM-only assertion into a file that must
later move to `commonTest`. That is the exact defect `p1-05` exists to eliminate.

**The removal is mechanical, ordered, and mine to perform:**

1. **Condition:** `grep -rl "com.google.common.truth" app/src/test/kotlin` returns nothing
   (`p1-05` criterion 1). Removing it earlier is a red build.
2. **Who:** me, on Verification's request. `p1-05` must not edit the catalog.
3. **When:** a separate commit from the conversion, and the **last** commit of release 1.9.0, so a
   revert of the removal does not drag ~92 files of conversion with it.
4. **What:** the `truth = "1.4.4"` version entry, the `truth = { … }` library entry, and the
   `testImplementation(libs.truth)` line. Nothing else.
5. If `p1-05` escalates on a file it cannot convert, Truth stays and the removal defers with it.

**Truth leaving must not be allowed to read as "the diagnostics left with it."** `assertWithMessage`
is the mechanism all six data gates use to produce diagnosable failures. That is `p1-05` criterion 8
and it is the reason assertk was chosen over bare `kotlin.test`.

> ⚠️ **R1 carries a real, newly-identified risk. See §3.5 (assertk's klib ABI) before `p1-05`
> starts converting 1,444 call sites.** It does not change the ruling; it changes when we probe.

### R2 — The glyph count is **9**, not 10. The list is fixed and closed.

**VERIFIED by reading the code, not by grepping it** (`grep -rn "Icons\." app/src/main/kotlin`
cross-checked against `grep -rn "material\.icons" app/src/main/kotlin`, which returns imports only
and therefore cannot match a comment):

| # | Glyph | Call site |
|---|---|---|
| 1 | `Icons.AutoMirrored.Filled.ArrowBack` | `ui/settings/SettingsScreen.kt:220` |
| 2 | `Icons.AutoMirrored.Filled.KeyboardArrowLeft` | `ui/datepicker/DayDatePickerDialog.kt:165` |
| 3 | `Icons.AutoMirrored.Filled.KeyboardArrowRight` | `ui/datepicker/DayDatePickerDialog.kt:174` |
| 4 | `Icons.Filled.ArrowDropDown` | `ui/settings/SettingsScreen.kt:962`, `bible/ui/reader/ReaderVersionSelector.kt:88` |
| 5 | `Icons.Filled.Check` | `ui/settings/SettingsScreen.kt:985`, `bible/ui/reader/ReaderVersionSelector.kt:100` |
| 6 | `Icons.Filled.Close` | `bible/ui/reader/VerseSelectionBar.kt:52` |
| 7 | `Icons.Filled.DateRange` | `ui/day/DayReadingsScreen.kt:193`, `ui/navigation/AppNavHost.kt:88` |
| 8 | `Icons.Filled.Edit` | `bible/ui/reader/ReaderScreen.kt:142` |
| 9 | `Icons.Filled.Settings` | `ui/day/DayReadingsScreen.kt:202` |

**12 call sites, 9 distinct glyphs, 3 of them `AutoMirrored`** — that is the sum of the table above.
(A raw `grep -rn "Icons\." app/src/main/kotlin` returns **13** lines; the thirteenth is the prose
mention at `VerseSelectionBar.kt:31` that the next paragraph disowns. **12 is the count of real call
sites; 13 is the count of grep hits.** The whole reason this section exists is that a grep over
comment text produced the wrong answer once already, so the two numbers are stated separately rather
than collapsed.)

**No test source imports or constructs a glyph**, so the vendoring has no test-source consumer:
`grep -rn "material\.icons" app/src/test app/src/androidTest` is empty (VERIFIED). Note the precise
claim — there **is** one `Icons.*` string under `app/src/test/`, at `ui/AccessibilityGateTest.kt:468`,
and it is a **comment**. An absolute "no `Icons.*` anywhere under `app/src/test/`" is falsified by a
two-second grep; the load-bearing claim is about consumers, not about the characters appearing.

**`Icons.Filled.ContentCopy` is NOT a call site and must not be vendored.** It occurs twice
(`bible/ui/reader/VerseSelectionBar.kt:31` and `ui/AccessibilityGateTest.kt:468`) and **both are
prose stating that the glyph does not exist in the frozen 1.7.8** — which is *why* Copy is a visible
`TextButton` word rather than an icon (sprint 00Q, a11y-gate-pinned). Vendoring it would ship dead
code and invite someone to reverse a deliberate accessibility decision.

**Documents already correct (9):** `p0-build-foundation.md` §3.B.7 · `gate0-minor-spikes.md` V5
(CLOSED, "do not run") · `rel-1120-vendor-icons.md` (§ "Correction 1").
**Documents still stale (10) — see §9:** `ios-execution-plan.md` §6 gate 4 and §12.B ·
`rel-1120-dependency-realignment.md` (fixed by me today, it is in my write set).

**V5 in `gate0-minor-spikes.md` is closed, not open.** `ios-execution-plan.md` §12.B's instruction
to "strike V5" has already been carried out on disk.

### R3 — Ktor's Android engine is **`ktor-client-android`**, not `ktor-client-okhttp`.

This overrides the line in `p1-03-ktor-http-and-url-encoding.md` §3.1 ("Engine: **OkHttp on
Android**"). Engine selection is an artifact selection and artifact selection is mine.

**Evidence (VERIFIED, `-jvm` POMs at 3.5.2):**

| Engine | Transitive deps beyond ktor-client-core | Added jar bytes |
|---|---|---|
| `ktor-client-okhttp` | `com.squareup.okhttp3:okhttp-jvm:5.3.2`, `com.squareup.okio:okio-jvm:3.17.0`, slf4j | 868,403 (okhttp) + 58,981 (engine) = **~927 KB** |
| `ktor-client-android` | *none* — slf4j only, which `ktor-client-core` already requires | **25,868 = ~26 KB** |

**And `com.squareup.okhttp3` is not on the app's classpath today** (VERIFIED:
`./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep -i okhttp` is empty).
So OkHttp is genuinely net-new — ~900 KB of new artifact, for two GET requests, in an app whose
whole engineering culture is "zero net-new runtime deps" against a 12 MB CI gate.

**The decisive argument is not size, it is delta.** `ktor-client-android` is the
`HttpURLConnection`-backed engine. `bible/data/remote/BibleApiClient.kt:64` and
`HttpFumsReporter.kt:39` use `HttpURLConnection` **in production today**. Choosing that engine means
release 1.9.0 changes the *API* the app calls without changing the *transport* that reaches the
network — which is precisely the "smallest possible Android-user-facing delta" discipline the
four-release plan exists to enforce. OkHttp 5 would additionally introduce a new major version of a
new networking stack into the same release.

**Alternative named, with its trigger:** `ktor-client-okhttp` is the right choice the moment the app
needs HTTP/2, connection pooling across many requests, per-request proxy control, or interceptors.
It has none of those needs today (two GETs, one optional header, timeouts, and a status-code
mapping). **If any of those becomes a requirement, come back to me with the requirement — not with a
preference — and I will swap the engine.** p1-03 already mandates the engine be supplied at the DI
boundary rather than constructed inside the client, so the swap is a one-line change in
`di/BibleRemoteModule.kt` and no rewrite.

### R4 — `koin-android` is **rejected**. Take `koin-core` + `koin-compose` + `koin-compose-viewmodel`.

**Evidence (VERIFIED, `koin-android-4.2.2.pom`):** `io.insert-koin:koin-android` declares
`androidx.appcompat:appcompat:1.7.1` at compile scope. **AppCompat is not on this app's classpath
today** (VERIFIED — `grep -i appcompat` over the resolved `releaseRuntimeClasspath` is empty; the app
is a single-activity `ComponentActivity` + Compose app and has never had AppCompat). The
`appcompat-1.7.1.aar` is **1,148,656 bytes** and carries a large resource table.

Adding ~1.1 MB of AppCompat — plus `fragment-ktx` and `activity-ktx` upgrades — to obtain
`androidContext()` and `androidLogger()` is not a trade I will accept in release 1.10.0, whose entire
characterisation is "DI alone."

**What to use instead:**

- `koin-core` — the graph.
- `koin-compose` + `koin-compose-viewmodel` — `koinViewModel()`, the `hiltViewModel()` replacement.
  **VERIFIED: `koin-androidx-compose:4.2.2` pulls only `koin-compose` + `koin-compose-viewmodel` and
  does *not* pull `koin-android`** — so if p1-07 wants the `androidx` convenience artifact it is
  also AppCompat-free. Either is acceptable.
- **For the `Context`:** bind it explicitly — `startKoin { modules(module { single<Context> {
  applicationContext } }) }` — instead of `androidContext()`. Room's and DataStore's builders take
  the `Context` from that binding exactly as they take it from Hilt's `@ApplicationContext` today.

**If p1-07 finds a capability it genuinely cannot obtain without `koin-android`, escalate to me with
the capability named.** I will not accept "it is the documented way" as the requirement.

---

## 2. Repositories — **no new repository is required**

VERIFIED. `settings.gradle.kts:15-21` declares `google()` + `mavenCentral()` and nothing else. Every
artifact in this contract resolves from one of those two:

- `org.jetbrains.compose.*`, `org.jetbrains.androidx.*`, `io.insert-koin:*`, `io.ktor:*`,
  `com.squareup.okio:*`, `org.jetbrains.kotlinx:*`, `com.willowtreeapps.assertk:*`,
  `app.cash.turbine:*` → **Maven Central** (all probed at repo1.maven.org, §8).
- `androidx.*` (Room, sqlite, DataStore, lifecycle-runtime-ktx, navigation-testing) → **Google
  Maven** (all probed at dl.google.com, §8).

**Do not add a JetBrains Space / CMP dev repository.** CMP 1.11.1 is a stable release on Maven
Central. If anyone proposes `maven { url = "https://maven.pkg.jetbrains.space/public/p/compose/dev" }`,
that is a signal they are reaching for a pre-release, which is a separate conversation.

`pluginManagement` (`settings.gradle.kts:2-12`) filters `google()` to `com.android.*`,
`com.google.*`, `androidx.*`. **This already covers the Room Gradle plugin** (`androidx.room`), and
the CMP and KMP plugin markers come from `mavenCentral()`/`gradlePluginPortal()`. **No change to
`settings.gradle.kts` is needed to resolve any coordinate in this contract** — the only change it
needs is `include(":shared:…")` lines, which is `p2-02`'s trigger, not this one's.

---

## 3. The contract

Columns: **android** = the `androidTarget()` / `androidApp` compilation. **iosArm64** = physical
device. **iosSimArm64** = simulator on Apple Silicon. ✅ means *I fetched a 200 for that exact
coordinate at that exact version on 2026-08-08*; a dash means the artifact is not for that target and
must never appear in a source set that reaches it.

**Target set (from p0 §3.A, restated because the table is unreadable without it):**
`androidTarget()`, `iosArm64()`, `iosSimulatorArm64()`. **No `iosX64`** — see §4.

### 3.1 Build plugins

| Plugin id | Coordinate | Version | Verdict |
|---|---|---|---|
| `com.android.application` | `com.android.tools.build:gradle` | 9.2.1 | Unchanged. `androidApp` only. |
| `com.android.library` | same | 9.2.1 | **New usage** — the `shared/*` modules' Android side. |
| `org.jetbrains.kotlin.multiplatform` | marker ✅ 200 | **2.3.21** | **New.** Same Kotlin as today. **Do not bump Kotlin in any port task.** |
| `org.jetbrains.kotlin.plugin.compose` | — | 2.3.21 | Unchanged; also applied to `shared/ui`. |
| `org.jetbrains.kotlin.plugin.serialization` | — | 2.3.21 | Unchanged. |
| `org.jetbrains.compose` | `org.jetbrains.compose:org.jetbrains.compose.gradle.plugin` ✅ 200 | **1.11.1** | **New.** Newest **stable** (1.12.0 is beta-only — VERIFIED from maven-metadata). |
| `com.google.devtools.ksp` | marker ✅ 200 | 2.3.9 | Unchanged. Still needed for the Room compiler. **On KMP, KSP is configured per target** — `add("kspAndroid", …)`, `add("kspIosArm64", …)`, `add("kspIosSimulatorArm64", …)`. A bare `ksp(…)` will silently not run for the Apple targets (INFERRED — KSP KMP contract; Gate 0's Room spike is where this gets exercised). |
| `androidx.room` | `androidx.room:room-gradle-plugin` ✅ 200 | 2.8.4 | **New.** Room KMP wants the plugin for `schemaDirectory`; the `ksp { arg("room.schemaLocation", …) }` form at `app/build.gradle.kts:130` is Android-only. **Exported schemas must stay byte-identical — see §7.** |
| `com.google.dagger.hilt.android` | — | 2.59.2 | **REMOVED** at release 1.10.0 (ADR-0012). |
| `org.jetbrains.kotlinx.kover` | `org.jetbrains.kotlinx:kover-gradle-plugin` ✅ 200 | 0.9.8 | Unchanged. **⟦VERIFY⟧ answered — see §3.6.** |
| `com.diffplug.spotless` | — | 7.0.4 | Unchanged. Extend target globs to `shared/**` and `iosApp/**/*.swift`. |

### 3.2 Compose: **no Compose version has to move.** (VERIFIED, and stronger than p0 claimed)

| Fact | Evidence |
|---|---|
| The app resolves `androidx.compose.ui:ui` at **1.11.2** and `androidx.compose.material3:material3` at **1.4.0** today | `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`, run in this repo today. **This is the criterion-5 "before" baseline.** |
| `org.jetbrains.compose.ui:ui:1.11.1`'s `androidRuntimeElements-published` requires `androidx.compose.ui:ui:1.11.2` | its `.module`, read |
| `org.jetbrains.compose.runtime:runtime:1.11.1` → `androidx.compose.runtime:runtime:1.11.2` | its `.module`, read |
| `org.jetbrains.compose.foundation:foundation:1.11.1` → `androidx.compose.foundation:foundation:1.11.2` | its `.module`, read |
| The CMP plugin's `compose.material3` accessor resolves to **`org.jetbrains.compose.material3:material3:1.9.0`**, not 1.11.1 | `javap` of `ComposeBuildConfig.class` inside `compose-gradle-plugin-1.11.1.jar`: `composeVersion = "1.11.1"`, **`composeMaterial3Version = "1.9.0"`**, `composeHotReloadVersion = "1.1.1"`; and `ComposePlugin$Dependencies.getMaterial3()` emits `org.jetbrains.compose.material3:material3` through `composeMaterial3Dependency` |
| `org.jetbrains.compose.material3:material3:1.9.0`'s `releaseRuntimeElements-published` requires **`androidx.compose.material3:material3:1.4.0`** — exactly what the BOM pins | its `.module`, read |
| `org.jetbrains.compose.material3:material3:1.11.1` **does not exist** | 404. maven-metadata: the last **stable** material3 is **1.9.0**; everything after is `1.10.0-alpha*`…`1.12.0-alpha03` |

> 🚩 **The trap, and it will bite whoever writes the catalog first.** Do **not** put
> `org.jetbrains.compose.material3:material3:1.11.1` in `libs.versions.toml`. **It is a 404.** Use
> the plugin's `compose.material3` / `compose.runtime` / `compose.foundation` / `compose.ui` /
> `compose.components.resources` / `compose.uiTest` DSL accessors so the plugin supplies both the
> coordinate and the version. **This applies to the Gate 0 gesture-rig spike today** — see §10.

> ⚠️ **New finding, not in p0 §3.B.2, and it must not be "fixed."** JB material3 **1.9.0** is
> compiled against `org.jetbrains.compose.*:1.9.1`, while the plugin supplies
> `org.jetbrains.compose.*:1.11.1` for runtime/foundation/ui. On Android that is ordinary
> conflict resolution (1.11.1 wins). **On the Kotlin/Native klib link it is a two-minor
> compose-runtime gap that we take by construction, because no stable material3 exists on the 1.11
> line.** This is the *same shape* as the ABI-gap argument used in p0 §3.B.3.2 to reject JB
> lifecycle 2.10.0 — so note carefully: **that argument does not generalise into "every klib must be
> compiled against 1.11.x."** JetBrains ships 1.11.1 + material3 1.9.0 as the *default pairing* of
> their own plugin, which makes it the supported combination. **Anyone who proposes a material3
> alpha to close this gap is making things worse; reject it.** Revisit trigger: the first stable
> `org.jetbrains.compose.material3` on the 1.11/1.12 line.

### 3.3 Coordinate or version changes

| Today | Becomes | android | iosArm64 | iosSimArm64 | Release | Note |
|---|---|---|---|---|---|---|
| `androidx.lifecycle:{lifecycle-viewmodel-compose, lifecycle-runtime-compose}:2.10.0` | `org.jetbrains.androidx.lifecycle:*:2.11.0` | ✅ | ✅ | ✅ | 1.12.0 | Also declare `lifecycle-viewmodel-savedstate:2.11.0` ✅ explicitly — it owns the silent `SavedStateHandle` failure mode. `lifecycle-viewmodel` ✅ and `lifecycle-common` ✅ come transitively. |
| `androidx.lifecycle:lifecycle-runtime-ktx:2.10.0` | **stays `androidx.lifecycle`**, bumped to **2.11.0** ✅ (Google Maven) | ✅ | — | — | 1.12.0 | **There is no JetBrains `-ktx` at any version** (`org.jetbrains.androidx.lifecycle:lifecycle-runtime-ktx:2.11.0` → **404**). Sole consumer is `lifecycleScope` in `MainActivity.kt`. Android-only, in `androidApp`. |
| `androidx.navigation:navigation-compose:2.9.8` | `org.jetbrains.androidx.navigation:navigation-compose:2.9.2` | ✅ | ✅ | ✅ | 1.12.0 | 2.9.2 is the newest **stable** in the JB line (2.10.0 is alpha — VERIFIED from maven-metadata). |
| `androidx.navigation:navigation-testing` (shares `version.ref = "navigationCompose"`) | **stays `androidx.navigation`**, **own ref pinned to 2.9.7** ✅ | ✅ | — | — | 1.12.0 | **`org.jetbrains.androidx.navigation:navigation-testing` does not exist at any version** (404). See the trap in `rel-1120-dependency-realignment.md` §3.2 — leaving it on 2.9.8 makes the *only* guard on the swap test a version nobody ships. |
| `androidx.compose.material:material-icons-core:1.7.8` | **DROPPED — vendor 9 glyphs** | — | — | — | 1.12.0 | See R2 and §3.7. |
| `com.google.dagger:hilt-*:2.59.2`, `androidx.hilt:hilt-navigation-compose:1.3.0` | `io.insert-koin:*` per **R4** | ✅ | ✅ | ✅ | 1.10.0 | **Use `koin-bom:4.2.2` ✅ and take versions from it.** Note `hilt-navigation-compose` currently participates in navigation version resolution; its removal is why `rel-1120` can pin navigation freely. |
| `com.google.truth:truth:1.4.4` | `com.willowtreeapps.assertk:assertk:0.28.1` | ✅ | ✅ | ✅ | 1.9.0 | **R1.** ⚠️ **klib ABI risk — §3.5.** |
| `app.cash.turbine:turbine:1.2.0` | **1.2.1** | ✅ | ✅ | ✅ | 1.9.0 | 1.2.0's iOS artifacts already resolve; 1.2.1 is current. A one-patch bump is cheaper now than mid-port. |

**Verified `uikit*` naming — do not "fix" a 404 you do not understand.** JetBrains publishes some
KMP artifacts under legacy `uikit*` names. Gradle resolves them **by KMP target attribute**, not by
artifact name, so an `iosArm64` consumer gets the right file even though the `-iosarm64` coordinate
404s:

| Artifact | `-iosarm64` | `-uikitarm64` | Attribute proof |
|---|---|---|---|
| `org.jetbrains.androidx.navigation:navigation-compose:2.9.2` | **404** | **200** | expected; the JB navigation line is `uikit*` |
| `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0` | **404** | **200** | `uikitArm64ApiElements-published` carries `org.jetbrains.kotlin.native.target: ios_arm64` |
| `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:**2.11.0**` | **200** | — | **2.11.0 moved to modern naming** |
| `org.jetbrains.compose.material3:material3:1.9.0` | **404** | **200** (`material3-uikitarm64`) | same |
| `org.jetbrains.compose.components:components-resources:1.11.1` | **404** (lowercase) | — | **camelCase**: `components-resources-iosArm64` is a **200** |

**Corollary, and it corrects the reason p0 §3.B.3 originally gave:** *"2.11.0 is the floor because
2.10.0 has no iOS artifact"* is **false** — 2.10.0 has one, under `uikit*`. p0 §3.B.3.2 already
records this correction; I re-verified it. **2.11.0 is still the right version, for the corrected
reason:** JB lifecycle 2.10.0 is compiled against compose-runtime 1.9.3 while 2.11.0 is compiled
against 1.11.0 — the line CMP 1.11.1 ships. On Kotlin/Native the klib is *linked*, not
classpath-resolved.

### 3.4 Same version, gains iOS targets

| Artifact | Version | android | iosArm64 | iosSimArm64 | Note |
|---|---|---|---|---|---|
| `androidx.room:room-runtime` | 2.8.4 | ✅ | ✅ | ✅ | `room-compiler:2.8.4` ✅ via **per-target KSP**. Room's iOS variant requires `androidx.sqlite:{sqlite, sqlite-framework}:2.6.2` (read from `room-runtime-iosarm64-2.8.4.module`). |
| `androidx.sqlite:sqlite-bundled` | **2.6.2** | ✅ | ✅ | ✅ | **New, required.** Use **2.6.2**, the version Room 2.8.4 declares — **not** sqlite's newer 2.7.0, unless Room is bumped too. Per ADR-0007: `AndroidSQLiteDriver` on Android (byte-parity for live users, zero size cost), `BundledSQLiteDriver` on iOS. |
| `androidx.datastore:datastore-preferences` | 1.2.1 | ✅ | ✅ | ✅ | **No version change.** Its iOS path pulls `datastore-core-okio` → `com.squareup.okio:okio:3.9.1`. |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.9.0 | ✅ | ✅ | ✅ | No change. |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.10.2 → **1.11.0** | ✅ | ✅ | ✅ | **Forced — see §3.5's coroutines finding.** |

### 3.5 New dependencies

| Artifact | Version | android | iosArm64 | iosSimArm64 | Why / caveat |
|---|---|---|---|---|---|
| `org.jetbrains.kotlinx:kotlinx-datetime` | **0.8.0** | ✅ (jvm variant) | ✅ | ✅ | ADR-0009. **There is no `kotlinx-datetime-android` artifact — the 404 is correct**; Android resolves the JVM variant. minSdk 26 means the `java.time` backing exists without desugaring. **`kotlinx.datetime.YearMonth` is present at 0.8.0** — VERIFIED by listing `kotlinx-datetime-jvm-0.8.0.jar` (`kotlinx/datetime/YearMonth.class`). That answers half of Gate 0 M1's `⟦VERIFY⟧ V3`; whether it carries the API `GetMonthCompletionUseCase` needs is still the spike's job. |
| `com.squareup.okio:okio` | **3.18.1** | ✅ | ✅ | ✅ | ADR-0014, ADR-0010. **Not net-new on Android: `com.squareup.okio:okio:3.9.1` is ALREADY on the release runtime classpath today** via `androidx.datastore:datastore-core-okio` (VERIFIED). This is a version bump, not an added stack — it materially lowers `p1-04`'s stated risk. |
| `io.ktor:ktor-client-core` | **3.5.2** | ✅ | ✅ | ✅ | ADR-0014. |
| `io.ktor:ktor-client-android` | 3.5.2 | ✅ | — | — | **Android engine, per R3.** |
| `io.ktor:ktor-client-darwin` | 3.5.2 | — | ✅ | ✅ | iOS engine. |
| `io.ktor:ktor-client-mock` | 3.5.2 | ✅ | ✅ | ✅ | **Approved pre-emptively** for `commonTest`, so the two HTTP fakes can move to `commonTest` without a second request. Use it or don't; you do not need to ask. |
| `com.willowtreeapps.assertk:assertk` | 0.28.1 | ✅ | ✅ | ✅ | R1. ⚠️ **ABI, below.** |
| CMP `compose.components.resources` | 1.11.1 (plugin-supplied) | ✅ | ✅ | ✅ | ADR-0013 strings. **camelCase** per-target suffixes — another reason to use the DSL accessor. |
| CMP `compose.uiTest` (`org.jetbrains.compose.ui:ui-test`) | 1.11.1 (plugin-supplied) | ✅ | ✅ | ✅ | `runComposeUiTest` for ported Compose tests. VERIFIED: publishes `androidApiElements-published` **and** both iOS variants. |

> ⚠️ **THE ONE ABI OUTLIER IN THE ENTIRE SET: assertk 0.28.1.**
>
> I read the `default/manifest` inside every iOS klib in this contract. VERIFIED:
>
> | Artifact (iosArm64 klib) | `abi_version` | `compiler_version` |
> |---|---|---|
> | CMP `ui` 1.11.1 | 2.3.0 | 2.3.20 |
> | `ktor-client-core` 3.5.2 | 2.3.0 | 2.3.21 |
> | `koin-core` 4.2.2 | 2.3.0 | 2.3.20 |
> | `okio` 3.18.1 | 2.2.0 | 2.2.21 |
> | `kotest-assertions-core` 6.2.3 | 2.2.0 | 2.2.21 |
> | `turbine` 1.2.1 | 1.201.0 | 2.1.21 |
> | `kotlinx-datetime` 0.8.0 | 1.201.0 | 2.1.20 |
> | **`assertk` 0.28.1** | **1.8.0** | **1.9.21** |
>
> assertk's iOS klib was built by **Kotlin 1.9.21 — pre-2.0**, and it is the only artifact here on
> the old ABI numbering. **0.28.1 is the newest published version; there is no newer one to take**
> (maven-metadata: `0.26, 0.26.1, 0.27.0, 0.28.0, 0.28.1`). Whether Kotlin **2.3.21** links a
> 1.8.0-ABI klib is **UNVERIFIED here** and cannot be settled without a Kotlin/Native link.
>
> **Why this matters more than it looks.** In release 1.9.0 `p1-05` uses only the **JVM** variant,
> so there is **zero** klib risk in 1.9.0. The klib is not touched until tests reach `commonTest`
> in Phase 2 (`p2-08`). But by then `p1-05` will have rewritten **1,444 assertion call sites across
> ~92 files**. If the klib does not link, that work is redone.
>
> **Therefore: probe it before `p1-05` starts, not when Phase 2 discovers it.** The probe is one
> `iosSimulatorArm64Test` source file containing one `assertThat(1).isEqualTo(1)`, and it only needs
> to **compile and link** — **no simulator runtime is required** (none is installed on this machine;
> `xcrun simctl list runtimes` is empty). It should ride along immediately after iOS Platform's
> `~/.konan` bootstrap. **This is a recommendation to the EM, not a unilateral schedule change —
> see §12.**
>
> **Alternative already priced, if the probe fails:** `io.kotest:kotest-assertions-core` — latest
> stable **6.2.3**, klib `abi_version 2.2.0` / compiler 2.2.21, `-iosarm64` ✅ 200,
> actively released. It provides `withClue { }` for message-carrying assertions, which is what
> `p1-05` criterion 8 actually requires. Second fallback: `kotlin.test` plus a ~20-line in-house
> `assertWithMessage` helper — ugly, but it preserves the diagnostics, which is the load-bearing
> property.

> ⚠️ **Ktor forces a coroutines upgrade for production users, and nobody has recorded it.**
> VERIFIED: `ktor-client-core-jvm:3.5.2` requires `kotlinx-coroutines-core-jvm:1.11.0`. And VERIFIED
> in this repo today:
>
> - `releaseRuntimeClasspath` resolves `kotlinx-coroutines-core` at **1.9.0**
> - `debugUnitTestRuntimeClasspath` resolves it at **1.10.2** (dragged up by `coroutines-test`)
>
> So **the app already ships coroutines 1.9.0 while testing against 1.10.2** — a pre-existing
> production/test skew that no document mentions. Adding Ktor takes production **1.9.0 → 1.11.0**, a
> two-minor jump of the coroutine runtime, inside release **1.9.0**. That is a materially larger
> Android-user-facing change than "two GETs move to Ktor" sounds, and it belongs in that release's
> risk statement.
>
> **Contract:** when Ktor lands, `kotlinx-coroutines-test` moves to **1.11.0** ✅ in the same commit
> (it reaches into `core` internals and must track it), and `p1-03`'s R8 device smoke is the guard.
> `kotlinx-coroutines-test:1.11.0` publishes `-iosarm64` ✅ and `-iossimulatorarm64` ✅.
> **This is a report, not a veto** — ADR-0014 already chose Ktor and the drift argument for it is
> sound. It changes what the 1.9.0 PR must say, not whether it ships.

### 3.6 `⟦VERIFY⟧` from p0 §3.B.1 — Kover on KMP: **answered.**

**VERIFIED** from the upstream `kotlinx-kover` README: *"Collection of code coverage for JVM and
Android host tests (**JS and native targets are not supported yet**)"* and *"Works with
`kotlin("jvm")`, `kotlin("android")`, `kotlin("multiplatform")`…"*.

**Consequences, stated so no one claims a number they do not have:**

1. Kover **does** work with the KMP plugin, so a coverage floor on `shared/*` is achievable.
2. It measures only the **JVM/Android** compilation. Since `commonMain` is compiled for Android
   too, the domain/data floor is preserved in substance — the same source is measured.
3. **No coverage number will ever exist for `iosMain` actuals.** Kotlin/Native coverage is not
   supported. Any report claiming "shared/* is at 96%" is measuring common + Android and **must say
   so**. Do not let the existing 95–96% figure quietly become a claim about iOS code.
4. `app/build.gradle.kts:133-162`'s Android-variant plumbing (`createVariant("appDebug")`) does
   **not** transfer as written to a KMP module. Rewriting it is mine, at `p2-02`.

### 3.7 `material-icons-core` — the artifact analysis behind R2

VERIFIED: `androidx.compose.material:material-icons-core:1.7.8` resolves as
`material-icons-core-android:1.7.8` — **Android-only**. The JetBrains fork
`org.jetbrains.compose.material:material-icons-core` is **frozen at 1.7.3** (maven-metadata: last
five versions are `1.7.0-beta02, 1.7.0-rc01, 1.7.0, 1.7.1, 1.7.3`), publishes **no `-android`
variant** (404) and **no `-iosarm64`** (404) — only legacy `-uikitarm64` (200).

**Depending on a frozen fork, three releases behind the Compose in use, for nine icons, is not a
dependency I will accept.** Vendor them (R2). The precedent in this repo is
`res/drawable/ic_bible_book.xml`, which is live at `ui/navigation/AppNavHost.kt` today.
`ic_stats.xml` is **not** a live precedent — it was deleted in sprint 15.

**Ordering, because reversing it is a red build:** Sr Shared-UI's vendoring
(`rel-1120-vendor-icons.md`) merges **first**; my catalog/build-file removal merges **second**.

### 3.8 Android-only. These are rejects on sight in a shared source set.

`androidx.browser:browser` (Custom Tabs) · `com.google.android.play:app-update{,-ktx}` ·
`androidx.glance:glance-{appwidget,material3,appwidget-testing}` · `androidx.core:core-ktx` ·
`androidx.activity:activity-compose` · `androidx.appcompat:appcompat` (**and therefore
`io.insert-koin:koin-android` — R4**) · `org.robolectric:robolectric` · `junit:junit` ·
`androidx.navigation:navigation-testing` · `androidx.lifecycle:lifecycle-runtime-ktx` ·
`androidx.compose.ui:ui-test-junit4` · `androidx.compose.ui:ui-test-manifest`.

`org.xerial:sqlite-jdbc:3.50.1.0` stays **`jvmTest`-only**. It is what
`BibleTextVerificationTest` (18) uses to open `bible.db` outside Room, and it is the entire reason a
JVM target is on the table (p0 §3.C).

> **CORRECTION to p0 §3.B.6 — `androidx.room:room-testing` is NOT Android-only.**
> VERIFIED: `androidx.room:room-testing-iosarm64:2.8.4` is a **200**, and I read the klib: it
> contains `androidx.room.testing.MigrationTestHelper` with a **KMP-shaped** constructor
> (`schemaDirectoryPath: String`, `fileName: String`, `driver: SQLiteDriver`,
> `databaseClass: KClass<RoomDatabase>`, `databaseFactory`) plus `runMigrationsAndValidateCommon`.
> What is Android-only is **today's usage** — `ProgressMigrationTest` uses the Robolectric/
> instrumentation flavour. **This is good news for `gate0-schema-tripwire` and `p2-06`: the
> migration guard is a candidate for `commonTest`, not a forced `androidUnitTest` resident.**
> Whether the existing test converts without weakening is theirs to determine; the artifact does not
> block it.

### 3.9 Source-set placement (the "per-target" question, answered directly)

| Source set | May depend on |
|---|---|
| `shared/domain/commonMain` | `kotlinx-datetime`, `kotlinx-coroutines-core`, `kotlinx-serialization-json`, `shared/platform`. **Nothing else.** ADR-0001's escalation trigger is literally "`shared/domain` needs a dependency that is not kotlinx-*". |
| `shared/platform/commonMain` | `shared/domain` models only. Interfaces, no implementations. |
| `shared/data/commonMain` | `shared/domain`, `shared/platform`, `room-runtime`, `sqlite-bundled`, `datastore-preferences`, `ktor-client-core`, `okio`, `kotlinx-serialization-json` |
| `shared/data/androidMain` | + `ktor-client-android`, `androidx.sqlite` Android driver |
| `shared/data/iosMain` | + `ktor-client-darwin` |
| `shared/data/jvmMain` | **only if Staff keeps the JVM target** (p0 §3.C open call) |
| `shared/ui/commonMain` | `shared/domain`, `shared/platform`, CMP `compose.{runtime,foundation,material3,ui,components.resources}`, `org.jetbrains.androidx.lifecycle:*`, `org.jetbrains.androidx.navigation:navigation-compose`, `koin-compose{,-viewmodel}`. **Never `shared/data`.** |
| any `commonTest` | `kotlin.test`, `assertk`, `turbine`, `kotlinx-coroutines-test`, `koin-test`, `ktor-client-mock`, CMP `compose.uiTest` |
| `androidUnitTest` | + Robolectric, JUnit 4, `room-testing`, `glance-appwidget-testing`, `navigation-testing`, `ui-test-junit4/manifest` |
| `jvmTest` | + `org.xerial:sqlite-jdbc` (**`BibleTextVerificationTest` lives here forever**) |
| `iosTest` | the `commonTest` set only |
| `androidApp` | everything in §3.8 |

---

## 4. `iosX64` is **unavailable**. Confirmed, and one nuance corrected.

VERIFIED 2026-08-08:

| Probe | Result |
|---|---|
| `org.jetbrains.compose.ui:ui-iosx64:1.11.1` | **404** |
| `org.jetbrains.compose.runtime:runtime-iosx64:1.11.1` | **404** |
| `org.jetbrains.compose.foundation:foundation-iosx64:1.11.1` | **404** |
| `org.jetbrains.compose.ui:ui-iosx64:1.11.0-alpha01` | **200** — the last published |
| `ios_x64` occurrences in the `.module` of CMP ui / runtime / foundation 1.11.1 | **0** |
| `org.jetbrains.compose.ui:ui-iosarm64:1.11.1` / `-iossimulatorarm64:1.11.1` | 200 / 200 |

**The plan's claim is confirmed exactly as written.** Declaring `iosX64` fails dependency
resolution, not compilation. **An Intel Mac cannot build this app.**

> **Nuance worth recording so nobody is confused by a passing probe:** other artifacts in this
> contract *do* still publish `ios_x64` — `androidx.room:room-runtime-iosx64:2.8.4`,
> `androidx.datastore:datastore-preferences-iosx64:1.2.1`, and CMP's own
> `material3-uikitx64:1.9.0` all exist. **`iosX64` availability is not uniform across the set; it is
> the CMP 1.11.x core (`ui`/`runtime`/`foundation`) that dropped it.** A Gate 0 spike that uses
> Room but not Compose will happily configure `iosX64` and prove nothing about the app.

`macos-latest` being Apple Silicon remains **INFERRED** — confirm on the first hosted run and report
`uname -m`. If GitHub ever routes to Intel, iOS CI fails with a *resolution* error whose message will
not mention architecture.

**No creative sub-targets.** No `macosArm64`, `watchos*`, `tvos*`, `js`, `wasm`. Each multiplies link
time, cache size and CI minutes for a platform we do not ship. A `jvm()` target used only by
`jvmTest` is the one permitted exception and it is Staff's open call (p0 §3.C).

---

## 5. The rule for anything not on this page

**There is no dependency budget beyond this document.** If you need an artifact that is not listed:

1. **Check it yourself first.** Fetch
   `https://repo1.maven.org/maven2/<group-path>/<artifact>-iosarm64/<v>/<artifact>-iosarm64-<v>.pom`
   and the `-iossimulatorarm64` equivalent (Google Maven is
   `https://dl.google.com/dl/android/maven2/…`). **A 404 on the lowercase form is not proof of
   absence** — read the root `.module`'s variant list for `uikit*` or camelCase naming first (§3.3).
2. **Then escalate to me** with: the coordinate, the exact HTTP results, the size impact, and what
   the code does without it.

I will reject a single-platform dependency in a shared module every time, and **I will name the
alternative.** The alternatives that have already worked on this project, in order of preference:
put it behind a `shared/platform` interface (12 already exist); vendor the small part you need
(`ic_bible_book.xml`, and now 9 glyphs); or keep the feature Android-only and record it in
`docs/parity-matrix.md`.

---

## 6. Coordinates for the two Gate 0 spikes (so they can reconcile without waiting on me)

Both spikes run standalone builds outside the root `settings.gradle.kts` and were told to pin their
own versions. **These are the values to pin.** If a spike used something else, the reconciliation is
to move the spike to these — not to move this contract.

**`gate0-room-identity-hash.md` (Sr Shared-Core):**

```
kotlin("multiplatform")                = 2.3.21
com.google.devtools.ksp                = 2.3.9
androidx.room (room-gradle-plugin)     = 2.8.4
androidx.room:room-runtime             = 2.8.4
androidx.room:room-compiler            = 2.8.4   // per-target: kspIosSimulatorArm64, kspIosArm64, kspAndroid
androidx.sqlite:sqlite-bundled         = 2.6.2   // NOT 2.7.0 — match what Room 2.8.4 declares
androidx.room:room-testing             = 2.8.4   // resolves for iOS (§3.8) if the spike wants it
```
Targets: `iosSimulatorArm64` (+ `iosArm64` if cheap). **`iosX64` will resolve for Room** — do not
read that as evidence the app can use it (§4).

**`gate0-reader-gesture-rig.md` (iOS Platform + Sr Shared-UI):**

```
kotlin("multiplatform")     = 2.3.21
org.jetbrains.compose       = 1.11.1
```
and take **every** Compose artifact from the plugin's DSL accessors (`compose.runtime`,
`compose.foundation`, `compose.material3`, `compose.ui`, `compose.uiTest`).

> 🚩 **The single most likely way this spike wastes a day:** hard-coding
> `org.jetbrains.compose.material3:material3:1.11.1`. **That coordinate is a 404** (§3.2) and the
> failure will look like "CMP is broken." The accessor resolves material3 to **1.9.0** and that is
> correct, expected and supported.
>
> Targets: `iosSimulatorArm64` and `iosArm64` only. **Do not add `iosX64`** — it will 404 on
> `ui`/`runtime`/`foundation` and cost you a confusing hour.

**And for whoever bootstraps `~/.konan` first (iOS Platform):** please run the 5-minute assertk klib
probe described in §3.5 while the toolchain is warm. It needs compile + link only, no simulator
runtime, and it de-risks ~92 files of `p1-05` conversion.

---

## 7. What this contract must not break (§3.J, the part that bears on dependencies)

The Room Gradle plugin (§3.1) changes *how* the schema location is configured
(`app/build.gradle.kts:130` today). **The exported `app/schemas/.../ProgressDatabase/{1,2}.json` must
stay byte-identical.** If it does not, that is escalation trigger 6 in p0 §6 and **the baseline is
never regenerated** — regenerating it is how a migration guard is silently deleted.

Equally: `app/build.gradle.kts:100-117` declares `planAssetsDir`, `inputs.dir("src/main/assets")` and
`inputs.dir("schemas")` with `PathSensitivity.RELATIVE`. **No dependency change may drop those.**
Without them, editing a bundled asset leaves the test task `UP-TO-DATE` and the gate never runs — a
green check mark over an unverified asset, on the files that *are* this project's product.
**Executing and proving §3.J is out of scope for this dispatch** (§11); this section exists so the
constraint is visible to anyone who touches a build file before then.

**Verification's PG-3 CI assertion** (a byte-identical checksum gate on
`app/schemas/.../ProgressDatabase/2.json`, from `p1-08`) is **mine to wire** once they specify it.
It had not arrived when this was written.

---

## 8. Evidence log — every probe, 2026-08-08

All statuses are HTTP response codes for the `.pom` of the exact coordinate, `repo1.maven.org` unless
marked (G) for `dl.google.com/dl/android/maven2`.

**Compose Multiplatform**

| Coordinate | Status |
|---|---|
| `org.jetbrains.compose:compose-gradle-plugin:1.11.1` | 200 |
| `org.jetbrains.compose:org.jetbrains.compose.gradle.plugin:1.11.1` (marker) | 200 |
| `org.jetbrains.compose.ui:ui:1.11.1` | 200 |
| `org.jetbrains.compose.ui:ui-iosarm64:1.11.1` | 200 |
| `org.jetbrains.compose.ui:ui-iossimulatorarm64:1.11.1` | 200 |
| `org.jetbrains.compose.ui:ui-iosx64:1.11.1` | **404** |
| `org.jetbrains.compose.runtime:runtime-iosx64:1.11.1` | **404** |
| `org.jetbrains.compose.foundation:foundation-iosx64:1.11.1` | **404** |
| `org.jetbrains.compose.ui:ui-iosx64:1.11.0-alpha01` | 200 |
| `org.jetbrains.compose.material3:material3:1.11.1` | **404** |
| `org.jetbrains.compose.material3:material3:1.9.0` | 200 |
| `org.jetbrains.compose.material3:material3-iosarm64:1.9.0` | **404** (uikit naming) |
| `org.jetbrains.compose.material3:material3-uikitarm64:1.9.0` | 200 |
| `org.jetbrains.compose.ui:ui-test:1.11.1` · `ui-test-iosarm64:1.11.1` | 200 · 200 |
| `org.jetbrains.compose.components:components-resources:1.11.1` | 200 |
| `…:components-resources-iosArm64:1.11.1` (camelCase) | 200 |
| `…:components-resources-iosarm64:1.11.1` (lowercase) | **404** |
| `org.jetbrains.compose.material:material-icons-core:1.7.3` | 200 |
| `…:material-icons-core-android:1.7.3` · `-iosarm64:1.7.3` | **404** · **404** |
| `…:material-icons-core-uikitarm64:1.7.3` | 200 |

**Lifecycle / navigation**

| Coordinate | Status |
|---|---|
| `org.jetbrains.androidx.lifecycle:{lifecycle-viewmodel-compose, -runtime-compose, -viewmodel-savedstate, -viewmodel, -common, -runtime}:2.11.0` | 200 ×6 |
| `…:lifecycle-viewmodel-compose-iosarm64:2.11.0` · `-iossimulatorarm64:2.11.0` | 200 · 200 |
| `…:lifecycle-viewmodel-compose-iosarm64:2.10.0` | **404** |
| `…:lifecycle-viewmodel-compose-uikitarm64:2.10.0` | 200 |
| `org.jetbrains.androidx.lifecycle:lifecycle-runtime-ktx:2.11.0` | **404** (does not exist) |
| (G) `androidx.lifecycle:lifecycle-runtime-ktx:2.11.0` | 200 |
| `org.jetbrains.androidx.navigation:navigation-compose:2.9.2` | 200 |
| `…:navigation-compose-iosarm64:2.9.2` · `-uikitarm64:2.9.2` | **404** · 200 |
| `org.jetbrains.androidx.navigation:navigation-testing:2.9.2` | **404** (does not exist) |
| (G) `androidx.navigation:navigation-testing:2.9.7` · `navigation-compose:2.9.7` | 200 · 200 |

**DI / test / datetime / IO / HTTP**

| Coordinate | Status |
|---|---|
| `io.insert-koin:{koin-bom, koin-core, koin-core-iosarm64, koin-core-iossimulatorarm64, koin-android, koin-androidx-compose, koin-compose, koin-compose-viewmodel, koin-compose-viewmodel-iosarm64, koin-test, koin-test-iosarm64}:4.2.2` | 200 ×11 |
| `com.willowtreeapps.assertk:{assertk, -iosarm64, -iossimulatorarm64, -jvm}:0.28.1` | 200 ×4 |
| `app.cash.turbine:{turbine, -iosarm64, -iossimulatorarm64}:1.2.1` | 200 ×3 |
| `org.jetbrains.kotlinx:{kotlinx-datetime, -iosarm64, -iossimulatorarm64, -jvm}:0.8.0` | 200 ×4 |
| `org.jetbrains.kotlinx:kotlinx-datetime-android:0.8.0` | **404** (correct — no such artifact) |
| `com.squareup.okio:{okio, -iosarm64, -iossimulatorarm64}:3.18.1` | 200 ×3 |
| `io.ktor:{ktor-client-core, -iosarm64, -iossimulatorarm64, ktor-client-okhttp, ktor-client-android, ktor-client-darwin, ktor-client-darwin-iosarm64, ktor-client-darwin-iossimulatorarm64, ktor-client-mock, ktor-client-mock-iosarm64}:3.5.2` | 200 ×10 |
| `org.jetbrains.kotlinx:{kotlinx-coroutines-core, -core-iosarm64, -test, -test-iosarm64, -test-iossimulatorarm64}:1.11.0` | 200 ×5 |
| `org.jetbrains.kotlinx:{kotlinx-coroutines-test-iosarm64, -core-iosarm64}:1.10.2` | 200 ×2 |
| `org.jetbrains.kotlinx:{kotlinx-serialization-json-iosarm64, -iossimulatorarm64}:1.9.0` | 200 ×2 |
| `io.kotest:{kotest-assertions-core, -iosarm64}:6.0.4` (fallback probe) | 200 ×2 |

**Persistence (Google Maven)**

| Coordinate | Status |
|---|---|
| `androidx.room:{room-runtime, -iosarm64, -iossimulatorarm64, room-compiler, room-gradle-plugin, room-testing, room-testing-iosarm64, room-ktx, room-runtime-android}:2.8.4` | 200 ×9 |
| `androidx.room:androidx.room.gradle.plugin:2.8.4` (marker) | 200 |
| `androidx.sqlite:{sqlite-bundled, -iosarm64, -iossimulatorarm64, sqlite, sqlite-framework}:2.6.2` | 200 ×5 |
| `androidx.datastore:{datastore-preferences, -iosarm64, -iossimulatorarm64, -core}:1.2.1` | 200 ×4 |

**Plugin markers**

| Coordinate | Status |
|---|---|
| `com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.3.9` | 200 |
| `org.jetbrains.kotlin.multiplatform:org.jetbrains.kotlin.multiplatform.gradle.plugin:2.3.21` | 200 |
| `org.jetbrains.kotlinx:kover-gradle-plugin:0.9.8` | 200 |

**Bytes actually read (not just status codes)**

- `compose-gradle-plugin-1.11.1.jar` → `ComposeBuildConfig.class`: `composeVersion=1.11.1`,
  `composeMaterial3Version=1.9.0`, `composeHotReloadVersion=1.1.1`; `ComposePlugin$Dependencies`
  bytecode for `getMaterial3()`.
- `.module` metadata for CMP `ui`/`runtime`/`foundation`/`material3`/`ui-test`/`components-resources`,
  JB lifecycle 2.10.0 + 2.11.0, JB navigation 2.9.2, Room `room-runtime` + `room-runtime-iosarm64`,
  DataStore `datastore-preferences` + `-iosarm64` + `datastore-iosarm64` + `datastore-core-okio-iosarm64`,
  `sqlite-bundled-iosarm64`, `koin-compose-viewmodel`.
- `-jvm` POMs for `ktor-client-core`, `ktor-client-okhttp`, `ktor-client-android`; POMs for
  `koin-android`, `koin-androidx-compose`, `koin-compose`.
- `default/manifest` inside the iosArm64 klibs of assertk, turbine, okio, koin-core,
  ktor-client-core, kotlinx-datetime, CMP `ui`, kotest-assertions-core.
- `kotlinx-datetime-jvm-0.8.0.jar` listing (`kotlinx/datetime/YearMonth.class`).
- `room-testing-iosarm64-2.8.4.klib` IR string table (`MigrationTestHelper`).
- maven-metadata for CMP plugin, CMP material3, JB material-icons-core, assertk, turbine, koin-bom,
  okio, ktor, kotlinx-datetime, kotlinx-coroutines, JB lifecycle, JB navigation, kotest.
- Artifact sizes via `Content-Length`: `appcompat-1.7.1.aar` 1,148,656 · `okhttp-jvm-5.3.2.jar`
  868,403 · `ktor-client-okhttp-jvm-3.5.2.jar` 58,981 · `ktor-client-android-jvm-3.5.2.jar` 25,868 ·
  `ktor-client-core-jvm-3.5.2.jar` 941,648 · `okio-jvm-3.18.1.jar` 392,463.
- In this repo: `./gradlew :app:dependencies` for `releaseRuntimeClasspath` and
  `debugUnitTestRuntimeClasspath`.

---

## 9. Where a document and reality disagree (rule 0 drift), reported not followed

| # | Document | Says | Reality | Owner |
|---|---|---|---|---|
| 1 | `ios-execution-plan.md` §12.B | The Truth conflict is open and "`p1-05` cannot close until it is resolved" | **Resolved on disk before this dispatch.** p0 §3.B.3.1 issues the ruling and `p1-05` criterion 2 already cites it. | EM |
| 2 | `ios-execution-plan.md` §12.B | V5 "should be struck"; p0 §3.B.7 "enumerates 10" | **V5 is already CLOSED** in `gate0-minor-spikes.md`, and p0 §3.B.7 says **9**. | EM |
| 3 | `ios-execution-plan.md` §6 gate 4 | "All **10** vendored glyphs" | **9** (R2). §6's own table one screen earlier says 9. | EM |
| 4 | `ios-execution-plan.md` §12.B | "**No brief exists for release 1.12.0** … the single largest gap in the plan and it has no owner" | **Stale.** `rel-1120-dependency-realignment.md` (466 lines, mine) and `rel-1120-vendor-icons.md` (440 lines, Sr Shared-UI) both exist and are consistent with this contract. | EM |
| 5 | `rel-1120-dependency-realignment.md` | "**ten** vendored glyphs" | **9**. ✅ **FIXED 2026-08-08** (EM-authorized). Four places, not the three first reported — line 8 (header), **line 31 (§1)**, §3.3, §5. `grep -n "\bten\b"` on that file now returns nothing. | me — **done** |
| 6 | `p0-build-foundation.md` §3.B.6 | `androidx.room:room-testing` is Android-only "and stays that way" | **False.** `room-testing-iosarm64:2.8.4` is a 200 and the klib carries a KMP `MigrationTestHelper` (§3.8). ✅ **FIXED 2026-08-08** (EM-authorized): struck from the Android-only list with the evidence and the `gate0-schema-tripwire`/`p2-06` consequence recorded inline. Also fixed at p0 §5 line 774 ("the **ten** icons" → nine). | me — **done** |
| 7 | `p1-03-ktor-http-and-url-encoding.md` §3.1 | "Engine: **OkHttp on Android**" | **Overruled — `ktor-client-android` (R3).** Non-blocking: p1-03 already requires the engine be supplied at the DI boundary, so the brief's *design* is unaffected; only that one line is wrong. | Staff/EM to amend the brief |
| 8 | `CLAUDE.md` (V3 Sprint D entry) | "Nav glyph for Bible = `AutoMirrored.List` (MenuBook absent from icons-core; OQ-3 placeholder)" | **False today.** `ui/navigation/AppNavHost.kt:100-104` uses `painterResource(R.drawable.ic_bible_book)`. The vendored drawable replaced the icon at some point and CLAUDE.md was never updated. **This is load-bearing for R2** — had it still been `AutoMirrored.List`, the count would be 10. | EM / whoever owns CLAUDE.md |
| 9 | Everywhere | The coroutines version is never stated | The app **ships 1.9.0 and tests against 1.10.2 today** (§3.5). Undocumented pre-existing skew; Ktor will move production to 1.11.0. | me (recorded) |
| 10 | `p0-build-foundation.md` §3.B.2 | Presents the material3 alignment as clean | It is clean *for Android*. On the klib link there is an inherent **compose-runtime 1.9.1 vs 1.11.1** gap inside CMP's own stable material3 (§3.2). Unavoidable and supported — but it must not be "fixed" with an alpha. | me (recorded) |

---

## 10. What is UNVERIFIED, and why

| Claim | Label | What would settle it |
|---|---|---|
| assertk 0.28.1's `abi_version 1.8.0` klib links under Kotlin 2.3.21 | **UNVERIFIED** — the one real ABI risk in the set | one `iosSimulatorArm64Test` that compiles + links. §3.5 |
| Per-target KSP (`kspIosArm64`) actually runs the Room compiler for an Apple target | INFERRED (KSP KMP contract) | `gate0-room-identity-hash.md` |
| The Room Gradle plugin's `schemaDirectory` produces byte-identical `1.json`/`2.json` | INFERRED | the first build after the plugin is applied. §7 |
| `macos-latest` is Apple Silicon | INFERRED | one hosted run; report `uname -m`. Load-bearing because of §4 |
| Kover's KMP variant plumbing replaces `createVariant("appDebug")` cleanly | INFERRED | `p2-02` |
| Every ✅ in this document is a *resolution* claim, not a *runtime* claim | by construction | nothing here has been compiled for an Apple target. **No klib in this contract has been linked on this machine — `~/.konan` does not exist.** |

**Nothing in this contract has been compiled, linked or run for iOS.** Every ✅ means the artifact
exists and Gradle can resolve it for that target. That is exactly what §3.B was scoped to answer, and
it is not more than that.

---

## 11. What I did **not** do

Explicitly, so nobody assumes it is done:

- **§3.D — framework shape** (`Shared`, `isStatic = true`, the export list). Not started.
- **§3.E — the Gradle→Xcode handoff** (`embedAndSignAppleFrameworkForXcode`, the Run Script phase,
  the **staleness proof** and the **negative proof**, criteria 9–12). Not started. These are the
  criteria p0 itself calls "the ones that matter most" and they need Xcode + a linked framework.
- **§3.F — `~/.konan` cache strategy.** Not started. `~/.konan` does not exist on this machine and
  **iOS Platform owns the bootstrap** — a competing Kotlin/Native download is a corruption hazard.
- **§3.G — JVM args** (`-Xmx6g`, `kotlin.daemon.jvmargs`). **`gradle.properties` is untouched.**
- **§3.H — configuration cache vs `linkRelease*`.** Not started.
- **§3.I — the 20-minute asset-packaging experiment.** Not started (needs Xcode).
- **§3.J — the `inputs.dir` touch-a-byte demonstration.** Not performed. §7 records the constraint
  only.
- **Two documents WERE edited, on EM authorization after this contract was accepted** (§9 rows 5
  and 6): the glyph count in `rel-1120-dependency-realignment.md` and the `room-testing`
  classification in `p0-build-foundation.md` §3.B.6 + §5. Nothing else in either file.
- **`.github/workflows/ci.yml` was edited** in the same follow-on: the **PG-3** Room-schema baseline
  guard (run **twice** in the `build` job, before *and* after the Gradle build, because
  `app/build.gradle.kts:130` makes KSP write the schema into the working tree — proven), and the
  **SB-T3** gate asserting no exported Room schema reaches the release bundle. Both were
  demonstrated failing and restored. This is delivery wiring, not a dependency change.
- ~~**No build file was modified.**~~ **SUPERSEDED 2026-08-08 by §13** — the release-1.9.0
  coordinates have now landed in `gradle/libs.versions.toml` and `app/build.gradle.kts`.
  `settings.gradle.kts`, `gradle.properties` and `gradle/wrapper/**` remain **byte-identical**.
  The original reasoning still holds and still governs 1.10.0 / 1.11.0 / 1.12.0: each coordinate
  change belongs to its own Play release with its own brief, gates and soak. §13 is 1.9.0's.
- The **six data gates were not run** and not touched — no code, test or asset was modified, so their
  counts (11 / 10 / 8 / 6 / 18 / 5) are untouched by construction.

---

## 12. Open item for the EM

**Recommendation, not a decision:** move the assertk klib probe (§3.5) into Gate 0, attached to
whoever bootstraps `~/.konan`. It costs ~5 minutes once the toolchain exists and it de-risks
`p1-05`'s 1,444 call sites across ~92 files. If it fails after `p1-05` completes, that conversion is
done twice. Scheduling is yours.

---

## 13. Release 1.9.0 — the coordinates AS LANDED (measured, 2026-08-08)

Coordinates only. **No call site was converted** — `p1-01`…`p1-05` own those, each a separate
reviewable change. `versionCode`/`versionName` are **untouched** at 10801 / 1.8.1; 1.9.0 is tagged
only when its briefs are complete and verified.

Every version below is the version Gradle **actually resolved**, read from
`:app:dependencies`, not the version I intended.

### 13.1 What landed

| Coordinate | Version resolved | Configuration | For |
|---|---|---|---|
| `org.jetbrains.kotlinx:kotlinx-datetime` | **0.8.0** | `implementation` | `p1-02` |
| `com.squareup.okio:okio` | **3.18.1** (was 3.9.1 transitively) | `implementation` | `p1-04` |
| `io.ktor:ktor-client-core` | **3.5.2** | `implementation` | `p1-03` |
| `io.ktor:ktor-client-android` | **3.5.2** | `implementation` | `p1-03` engine, **R3** |
| `io.ktor:ktor-client-mock` | **3.5.2** | `testImplementation` | `p1-03` |
| `com.willowtreeapps.assertk:assertk` | **0.28.1** (jvm variant) | `testImplementation` | `p1-05` |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.10.2 → **1.11.0** | `testImplementation` | forced, §3.5 |
| `app.cash.turbine:turbine` | 1.2.0 → **1.2.1** | `testImplementation` | §3.3 |

**Not added, deliberately.** `ktor-client-content-negotiation` and `ktor-serialization-kotlinx-json`:
`BibleApiClient` parses with `Json.parseToJsonElement` **by hand** today, and `p1-03` preserves that
behaviour exactly (unknown keys ignored, any parse failure → `Unavailable`, which drives the D-OT-2
fallback). Adding negotiation would change parse semantics inside a release characterised as
transport-only. `ktor-client-darwin` is also absent: **no iOS target exists in this build yet**, so a
catalog entry for it would never resolve and never be verified. It lands with `p2-09`.

**Truth was NOT removed, per R1's own ordering.** `p1-05` has not run; ~1,444 call sites still import
`com.google.common.truth`. assertk landed *alongside* it. Removal stays the **last** commit of 1.9.0,
gated on `grep -rl "com.google.common.truth" app/src/test/kotlin` returning nothing.

### 13.2 The coroutines shift — production bytes moved (R10: measured, before and after)

| Configuration | Artifact | Before | After |
|---|---|---|---|
| `releaseRuntimeClasspath` | `kotlinx-coroutines-core` | **1.9.0** | **1.11.0** |
| `releaseRuntimeClasspath` | `kotlinx-coroutines-android` | **1.9.0** | **1.11.0** |
| `releaseRuntimeClasspath` | `kotlinx-coroutines-bom` | 1.9.0 | 1.11.0 |
| `releaseRuntimeClasspath` | `com.squareup.okio:okio` | **3.9.1** | **3.18.1** |
| `debugUnitTestRuntimeClasspath` | `kotlinx-coroutines-core` | **1.10.2** | **1.11.0** |
| `debugUnitTestRuntimeClasspath` | `kotlinx-coroutines-test` | 1.10.2 | 1.11.0 |

Nothing pins coroutines but coroutines' own BOM, so Ktor's `1.11.0` requirement simply wins; the
`okio` move is `datastore-core-okio`'s 3.9.1 being upgraded. **Both are real changes to shipped
bytes and belong in the 1.9.0 PR description, not a footnote.**

**One genuine improvement worth stating:** this release **closes the undocumented pre-existing skew**
recorded in §3.5 — the app shipped coroutines **1.9.0** while its tests ran against **1.10.2**. After
this change production and test are both **1.11.0**, so the coroutine runtime under test is finally
the coroutine runtime that ships. The guard on the two-minor production jump is `p1-03`'s **R8
release-build device smoke** (the standing 1.7.0 lesson: a debug device pass does not cover R8).

### 13.3 Size — and why today's number understates the real cost

Measured in a **throwaway `git worktree` at the committed HEAD** (`43e37fd`), because the shared
working tree contained another agent's in-flight edits (§13.5). Both builds therefore differ by
**exactly** `libs.versions.toml` + `app/build.gradle.kts` and nothing else.

| | bytes | vs CI ceiling `12,000,000` |
|---|---|---|
| Before | 8,224,361 | |
| After | **8,235,098** | headroom **3,764,902** (31.4%) — **PASS** |
| Delta | **+10,737** (+0.13%) | |

> ⚠️ **Do not read +10.5 KB as "Ktor costs 10 KB."** `unzip -Z1` over the AAB finds exactly **two**
> `ktor|okio` entries — a datastore LICENSE and `ktor-network/reflect-config.json`. **R8 stripped
> essentially all of Ktor, because no call site uses it yet.** The real shipped cost arrives with
> `p1-03`. `ktor-client-core-jvm` alone is a 941 KB jar; R8 will keep only the reachable slice
> (engine + `HttpClient` + `ktor-http`/`ktor-io`, not websockets/SSE/CIO). **Re-measure the AAB at
> `p1-03` merge and treat that as the Ktor size decision point**, not this one.

Ktor 3.5.2 pulled these transitive modules onto the release classpath (all 3.5.2):
`ktor-http`, `ktor-io`, `ktor-utils`, `ktor-events`, `ktor-network`, `ktor-http-cio`,
`ktor-sse`, `ktor-websockets`, `ktor-websocket-serialization`, `ktor-serialization`.

### 13.4 Gates — verified, not assumed

From one `--rerun-tasks` run of
`spotlessCheck lintDebug assembleDebug testDebugUnitTest koverXmlReportAppDebug koverVerifyAppDebug`
in the clean worktree: **BUILD SUCCESSFUL, `74 actionable tasks: 74 executed`** (nothing cached).

- **943 tests executed, 0 failures, 0 errors, 0 skipped** — unchanged from `p1-09`.
- **Six data gates: 11 / 10 / 8 / 6 / 18 / 5** — unchanged.
- **PG-3:** after a full KSP release build, `1.json` = `cf2a94ef…45d31b` and `2.json` =
  `16d1f6aa…0ac66a7`, byte-identical to the values pinned in `ci.yml`. **Room 2.8.4 / KSP 2.3.9 were
  not touched, and the exported schema did not move.**
- **SB-T3:** no schema assets in the release bundle.
- **`spotlessCheck` green**, so the edited build files are format-compliant.

### 13.5 Blocked: the shared working tree does not compile (not caused by this change)

`p1-01` is mid-edit **in the same working tree** — `platform/{DateTextFormatter,
AndroidDateTextFormatter}.kt` are new and untracked, and `DayReadingsScreen.kt` calls
`formatMonthDay(date, …)` / `formatDayDate(date, …)` with **more arguments than the functions
currently declare**. `:app:compileReleaseKotlin` fails there. That is an incomplete conversion, not a
missing coordinate — it does not resolve when these coordinates land. Every number in §13 was taken
in an isolated worktree for exactly this reason. **Escalated to the EM.**

---
