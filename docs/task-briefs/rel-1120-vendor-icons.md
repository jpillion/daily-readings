# rel-1120 — Vendor the Material glyphs, drop `material-icons-core`

**Release:** 1.12.0 / 11200 "UI dependency realignment" (D-PORT-9)
**Owner:** Senior Shared-UI Engineer
**Companion brief:** `rel-1120-dependency-realignment.md` (Build & Release — the lifecycle and
navigation artifact swaps, **and the actual removal of this dependency from the build**)
**Blocks:** Phase 3. Nothing in Phase 2 depends on this.

---

## 1. Objective

Replace every `androidx.compose.material.icons.Icons.*` reference in `app/src/main/kotlin` with
glyphs vendored into this repository as Kotlin `ImageVector`s, written in a form that Phase 3 moves
into `shared/ui/src/commonMain` **unmodified**, so that Build & Release can delete
`androidx.compose.material:material-icons-core:1.7.8` from the build with no source left referring
to it.

The user-visible result must be **nothing at all**: the same glyphs, at the same sizes, in the same
places, still mirroring under RTL.

---

## 2. Context

### 2.1 Why the dependency has to go

`androidx.compose.material:material-icons-core:1.7.8` is Android-only. The JetBrains multiplatform
fork is **frozen at 1.7.3** and publishes **no `-android` and no `-iosarm64` variant** — both 404;
only the legacy `uikit*` set resolves. Build & Release verified this and will not accept a frozen
fork three Compose releases behind, for icons. `p0-build-foundation.md` §3.B.7 records the verdict
and states plainly: *"the vendoring itself is Shared-UI's task, not mine; my contract is that the
dependency is gone."* This brief is that task.

Read `docs/ios-execution-plan.md` §6 for why 1.12.0 exists as a separate Play release, and §11 for
the standing rules. **§11 rule 0 governs this task in particular: the shipped Android code is the
spec.** Two of the three inputs to this brief were wrong when checked against the code — see §2.3.

### 2.2 The call sites — verified 2026-08-08 against `main`

Nine distinct glyphs, thirteen call sites. Every one is `Icon(imageVector = …)`; **not one is a
`painterResource` or a framework API that requires an Android resource.** That fact is what decides
the vendoring form in §3.1.

| Glyph | File : line | Enclosing control | testTag |
|---|---|---|---|
| `Icons.AutoMirrored.Filled.ArrowBack` | `app/src/main/kotlin/com/jpillion/dailyreadingplanner/ui/settings/SettingsScreen.kt:220` | `TopAppBar` `navigationIcon` `IconButton` | `settings-back` **(a11y gate)** |
| `Icons.AutoMirrored.Filled.KeyboardArrowLeft` | `app/src/main/kotlin/com/jpillion/dailyreadingplanner/ui/datepicker/DayDatePickerDialog.kt:165` | month-header `IconButton` | `picker-prev-month` **(a11y gate)** |
| `Icons.AutoMirrored.Filled.KeyboardArrowRight` | `app/src/main/kotlin/com/jpillion/dailyreadingplanner/ui/datepicker/DayDatePickerDialog.kt:174` | month-header `IconButton` | `picker-next-month` **(a11y gate)** |
| `Icons.Filled.ArrowDropDown` | `app/src/main/kotlin/com/jpillion/dailyreadingplanner/bible/ui/reader/ReaderVersionSelector.kt:88` | version row (`Role.DropdownList`) | `reader-version-dropdown` |
| `Icons.Filled.ArrowDropDown` | `app/src/main/kotlin/com/jpillion/dailyreadingplanner/ui/settings/SettingsScreen.kt:962` | `SettingsDropdownRow` | `theme-dropdown` / `provider-dropdown` / `plan-dropdown` |
| `Icons.Filled.Check` | `app/src/main/kotlin/com/jpillion/dailyreadingplanner/bible/ui/reader/ReaderVersionSelector.kt:100` | `DropdownMenuItem` `leadingIcon` | `reader-version-option-<code>` |
| `Icons.Filled.Check` | `app/src/main/kotlin/com/jpillion/dailyreadingplanner/ui/settings/SettingsScreen.kt:985` | `DropdownMenuItem` `leadingIcon` | `*-option-*` |
| `Icons.Filled.Close` | `app/src/main/kotlin/com/jpillion/dailyreadingplanner/bible/ui/reader/VerseSelectionBar.kt:52` | selection-bar `navigationIcon` `IconButton` | `verse-selection-close` **(a11y gate)** |
| `Icons.Filled.DateRange` | `app/src/main/kotlin/com/jpillion/dailyreadingplanner/ui/day/DayReadingsScreen.kt:193` | top-bar `IconButton` | `open-date-picker` **(a11y gate)** |
| `Icons.Filled.DateRange` | `app/src/main/kotlin/com/jpillion/dailyreadingplanner/ui/navigation/AppNavHost.kt:88` | `NavigationBarItem` icon | `nav-schedule` |
| `Icons.Filled.Edit` | `app/src/main/kotlin/com/jpillion/dailyreadingplanner/bible/ui/reader/ReaderScreen.kt:142` | title-slot `IconButton` (opens the picker) | `reader-open-picker` **(a11y gate)** |
| `Icons.Filled.Settings` | `app/src/main/kotlin/com/jpillion/dailyreadingplanner/ui/day/DayReadingsScreen.kt:202` | top-bar `IconButton` | `open-settings` **(a11y gate)** |

Imports to be removed: `SettingsScreen.kt:22-25`, `DayReadingsScreen.kt:13-15`,
`AppNavHost.kt:4-5`, `DayDatePickerDialog.kt:17-19`, `ReaderScreen.kt:22-23`,
`ReaderVersionSelector.kt:8-10`, `VerseSelectionBar.kt:4-5`.

### 2.3 Two corrections to the inputs — read these before you start

**Correction 1 — it is 9 glyphs, not 10. `Icons.Filled.ContentCopy` is not a call site.**
`p0-build-foundation.md` §3.B.7 and `ios-execution-plan.md` §6 both list ten, including
`ContentCopy`. `ContentCopy` appears in exactly two places in the repo and **both are prose in a
comment**:

- `bible/ui/reader/VerseSelectionBar.kt:32` — *"Copy is a **TextButton with a visible word**, not an
  icon. `Icons.Filled.ContentCopy` does not exist in the frozen `material-icons-core` 1.7.8…"*
- `app/src/test/kotlin/.../ui/AccessibilityGateTest.kt:468` — the same note.

The enumeration that produced the list of ten matched the comment text. **Do not vendor a
`ContentCopy` glyph, and do not turn Copy into an icon button.** The visible-word decision is a
deliberate accessibility choice (sprint 00Q, the `UpdateRestartSnackbarEffect` "Restart" precedent)
and it is pinned by `AccessibilityGateTest`. What you must do instead is **reword those two
comments** — their stated reason (*"does not exist in the frozen 1.7.8"*) stops being true the
moment the dependency is gone, and a comment that justifies a decision with a dead fact is how the
decision gets reversed by the next person. The replacement reason is: *a spoken word beats a
guessed glyph, and we deliberately did not vendor a tenth glyph for it.*

**Correction 2 — the vendoring precedent is `ic_bible_book.xml`, not `ic_stats.xml`.**
`p0` §3.B.7 cites *"`ic_stats.xml` in S11, `widget_preview.xml` in S9."* `ic_stats.xml` was
**deleted in sprint 15** when the stats screen moved inline; `app/src/main/res/drawable/` today
contains only `ic_bible_book.xml`, `ic_launcher_foreground.xml`, `ic_notification_reminder.xml`
and `widget_preview.xml`. This matters because §3.1 rejects that precedent on the grounds that the
two surviving cases are ones where Android *requires* a resource
(`NotificationCompat.setSmallIcon(R.drawable.…)`, the Glance widget preview) — which is not true of
any of the nine glyphs here.

### 2.4 RTL is the real risk in this task

The app declares `android:supportsRtl="true"`
(`app/src/main/AndroidManifest.xml:34`). Three of the nine glyphs are `AutoMirrored`, and **all
three sit inside IconButtons the a11y gate pins** — but the a11y gate measures touch bounds, not
mirroring, so it will stay green whether or not you preserve the property.

The app is not localized (ADR-0013), so mirroring is **inert for every current user**. That is
precisely why dropping it is dangerous: nothing on screen looks wrong in LTR, no test fails, no
user complains, and the property is silently gone by the time an RTL locale is added. §3.3 exists
to make that outcome impossible rather than unlikely.

---

## 3. Contract

### 3.1 Decision — vendor as Kotlin `ImageVector`s, not `res/drawable` vector XML

**Vendor the nine glyphs as Kotlin `ImageVector`s in a single `AppIcons` object.**

Reason, in the order that decides it:

1. **Every call site is already `Icon(imageVector = …)`.** Switching to a drawable means rewriting
   each one to `Icon(painter = painterResource(R.drawable.…))` — and
   `androidx.compose.ui.res.painterResource` and `R` are both **Android-only**, so Phase 3 would
   have to rewrite all thirteen call sites a **second** time onto a Compose Resources
   `Res.drawable.…` accessor. `ImageVector` call sites change **once**, now, and never again.
2. **`ImageVector.Builder` takes `autoMirror` as a first-class constructor parameter**, and that is
   the exact mechanism `Icons.AutoMirrored.*` uses today. Vendoring as `ImageVector` keeps the
   mirroring on the identical code path (`VectorPainter` consulting `LocalLayoutDirection`) rather
   than on a parallel one whose Compose Multiplatform behaviour we would have to establish.
   Whether `org.jetbrains.compose.resources`' XML vector parser honours `android:autoMirrored` is
   an open question nobody on this program has answered — this decision means we never have to.
3. **`material-icons-core` is itself a library of Kotlin `ImageVector`s**, not a resource library.
   Copying the same form is the highest-fidelity port, and it is what makes the equivalence proof
   in §3.4 possible at all.
4. The local `res/drawable` precedent does not apply: the two live vendored drawables exist because
   an Android framework API demanded a resource id (§2.3, correction 2). None of the nine does.

**Does it survive the Phase 3 move to `shared/ui`? Yes — as a file move and a package rename, with
no edit to the vector code and no edit to any call site.** All three of the file's imports
(`androidx.compose.ui.graphics.*`, `androidx.compose.ui.graphics.vector.*`,
`androidx.compose.ui.unit.dp`) are Compose Multiplatform APIs available in `commonMain`. Write the
file so that this is true by construction; §4 criterion 7 checks it mechanically.

### 3.2 Where it lives, what it is called

**At 1.12.0 (this task):**

```
app/src/main/kotlin/com/jpillion/dailyreadingplanner/ui/icons/AppIcons.kt
```

`ui/icons/` is the neutral home — the glyphs are used from both `ui/**` and `bible/ui/**` — and it
matches the existing `ui/theme`, `ui/navigation` package convention. One file, not nine: nine
files of six lines each is ceremony, and a single file makes the auto-mirrored set visible at a
glance.

**At Phase 3 (not this task, stated so nobody redoes it):**

```
shared/ui/src/commonMain/kotlin/com/jpillion/dailyreadingplanner/ui/icons/AppIcons.kt
```

**Shape and naming — mirror the upstream names exactly**, so that each call-site diff is a single
identifier and a reviewer can see at a glance that nothing was substituted:

```kotlin
object AppIcons {
    val ArrowDropDown: ImageVector get() = …
    val Check: ImageVector get() = …
    val Close: ImageVector get() = …
    val DateRange: ImageVector get() = …
    val Edit: ImageVector get() = …
    val Settings: ImageVector get() = …

    object AutoMirrored {
        val ArrowBack: ImageVector get() = …
        val KeyboardArrowLeft: ImageVector get() = …
        val KeyboardArrowRight: ImageVector get() = …
    }
}
```

**Keep the `AutoMirrored` nesting.** Upstream introduced that namespace for exactly the failure mode
in §2.4 — it puts the mirroring property in front of the reader at every call site and makes
"someone adds a tenth glyph and forgets `autoMirror`" a visible mistake rather than an invisible
one. `AppIcons.AutoMirrored.ArrowBack` is shorter than what it replaces.

Each vector must be **built once and cached** in a private backing field
(`private var _arrowBack: ImageVector? = null`), as upstream does. Do not rebuild the vector inside
the getter on every recomposition — the reader recomposes heavily (Psalm 119 is 176 verses).

### 3.3 The path data, and the visual-equivalence standard

**Standard: structural identity. Tolerance for visual difference: zero.** These glyphs replace
Material glyphs the user sees today, so "close enough" is not a judgement anyone should be making by
eye. Get there by **copying the path data verbatim** rather than redrawing it:

1. Take the path data from the **pinned artifact that is still in the build**, not from a web icon
   picker: `material-icons-core:1.7.8` sources, i.e.
   `https://repo1.maven.org/maven2/androidx/compose/material/material-icons-core/1.7.8/material-icons-core-1.7.8-sources.jar`,
   files `androidx/compose/material/icons/filled/*.kt` and
   `androidx/compose/material/icons/automirrored/filled/*.kt`.
2. **Record the SHA-256 of that jar and the upstream file path for each glyph in the `AppIcons.kt`
   KDoc.** This repo pins provenance everywhere else (`tools/build_*.py`, `docs/data/README.md`);
   vendored third-party art is held to the same standard.
3. **Reproduce the upstream builder parameters exactly**, or the equivalence harness in §3.4 will
   not pass. Upstream's private `materialIcon` / `materialPath` helpers are not accessible to you,
   so open-code them: `defaultWidth = 24.dp`, `defaultHeight = 24.dp`, `viewportWidth = 24f`,
   `viewportHeight = 24f`, `autoMirror` per glyph; and per path `fill = SolidColor(Color.Black)`,
   `fillAlpha = 1f`, `stroke = null`, `strokeAlpha = 1f`, `strokeLineWidth = 1f`,
   `strokeLineCap = StrokeCap.Butt`, **`strokeLineJoin = StrokeJoin.Bevel`** (upstream's non-obvious
   choice — Compose's `path {}` default is `Miter`), `strokeLineMiter = 1f`,
   `pathFillType = PathFillType.NonZero`. Set `name` to the upstream name string exactly
   (`"Filled.Settings"`, `"AutoMirrored.Filled.ArrowBack"`). **If the harness disagrees with any
   value above, the harness is authoritative — match what it reports, and note the correction in
   the PR.**
4. **Apache-2.0 attribution.** This is copied third-party source, unlike the two hand-authored
   brand drawables. The file header must carry the AndroidX copyright line and the Apache-2.0
   notice, plus a sentence saying what was copied and from where. The app has no
   third-party-licenses screen and this brief does not add one.

### 3.4 The equivalence proof — a temporary harness, run while the dependency is still present

This is the step that converts "visual equivalence" from an eyeball judgement into a fact, and it is
only possible **before** Build & Release removes the artifact. Do it in that window.

**Temporary** `app/src/test/kotlin/com/jpillion/dailyreadingplanner/ui/icons/AppIconsEquivalenceTest.kt`:
for each of the nine glyphs, assert the vendored `ImageVector` equals its upstream counterpart.
`ImageVector` implements structural equality over name, defaults, viewport, `autoMirror` and the
node tree, so with the names matched (§3.3 step 3) this is a direct `assertThat(AppIcons.Settings)
.isEqualTo(Icons.Filled.Settings)`. If structural equality turns out not to hold across the whole
tree, fall back to comparing field by field: `defaultWidth`, `defaultHeight`, `viewportWidth`,
`viewportHeight`, `autoMirror`, and the `root` flattened to its `VectorPath` list with each path's
`pathData`, `fill`, `fillAlpha`, `stroke`, `strokeLineWidth`, `strokeLineCap`, `strokeLineJoin` and
`pathFillType`. **Do not weaken it to "same number of path nodes."**

**Before deleting it, demonstrate it can fail** (§11 rule 11 — a check nobody has seen fail is not
known to work):

- drop one `lineTo` from one vendored glyph → harness red → restore **byte-identically,
  SHA-256-verified**;
- flip `autoMirror = true` → `false` on `ArrowBack` → harness red → restore byte-identically.

**Quote both failures and the full green run in the PR body.** Then delete the harness in the same
PR, with an explicit justified line naming §11 rule 13 and the reason (its subject — the upstream
artifact — is being removed from the build, and criterion 3 in §4 requires no source reference to
survive).

**Permanent replacement** `app/src/test/kotlin/com/jpillion/dailyreadingplanner/ui/icons/AppIconsTest.kt`,
which depends on nothing that is going away:

1. all nine glyphs are non-null, 24dp × 24dp, viewport 24 × 24, and have a non-empty node tree;
2. **exactly** `AutoMirrored.ArrowBack`, `AutoMirrored.KeyboardArrowLeft` and
   `AutoMirrored.KeyboardArrowRight` have `autoMirror == true`, and **all six others have
   `autoMirror == false`** — assert both halves, not just the true half;
3. mutation to re-kill: flip `autoMirror` on `ArrowBack` to `false`, observe test 2 red, restore
   byte-identically.

Net test count after the swap must be **strictly greater** than today's 940.

### 3.5 How an RTL mirroring regression gets caught

Three layers, and the brief is explicit about what each one does and does not prove.

| Layer | Catches | Where |
|---|---|---|
| Equivalence harness (§3.4) | the property differing from upstream at all — for all nine, in both directions | JVM, temporary |
| `AppIconsTest` criterion 2 (§3.4) | the property being dropped or added later, forever | JVM, permanent |
| Forced-RTL device pass (§3.6) | the property being set but not honoured at render time | device, `assembleRelease` |

**Why the JVM layers are the right guard and not a cop-out:** the mirroring *machinery* —
`VectorPainter` reading `LocalLayoutDirection` and flipping the canvas — is androidx code this task
does not touch and which already renders these same glyphs today. The only thing this task can
break is the **flag**. So pinning the flag is a genuine guard, not a proxy. What the JVM layers
cannot prove is that the machinery still fires for a locally-constructed vector, and that is what
the device pass is for.

**Robolectric cannot close that last gap here.** Asserting mirrored pixels needs
`captureToImage()` under Robolectric's native graphics mode, which this project does not configure;
turning it on is a build-configuration change and therefore outside this task's write set. **Do not
claim a test covers rendered mirroring.**

### 3.6 Device pass — the part that is not JVM-provable

All of it on an **`assembleRelease`** build (§11 rule 1 — the 1.7.0 P0 reproduced only under R8;
a debug device pass passed while the app crashed for every user).

**RTL, with a baseline:**

1. Enable Developer options → **Force RTL layout direction**
   (`adb shell settings put global debug.force_rtl 1`), relaunch the app.
2. **Run it first on the pre-change build (1.11.0), and record what you see.** Comparing against a
   remembered expectation is how a mirroring regression gets rationalised away; comparing against
   the previous build is how it gets caught.
3. Then on the 1.12.0 candidate, confirm the three mirror and match the baseline:
   Settings top bar back arrow points **right**; the date-picker month chevrons **swap sides** —
   the "previous month" chevron sits on the right and points right.
4. `adb shell settings put global debug.force_rtl 0`, relaunch, confirm all three are back to
   their LTR direction — i.e. that the flag did not get stuck on for LTR users.

**LTR visual sweep** of all nine, at every call site in the §2.2 table: Settings (back arrow, the
three dropdown rows' caret, the tick on the selected menu item); Schedule top bar (calendar,
gear); bottom nav (Schedule tab calendar); date-picker dialog (both chevrons); reader top bar
(pencil, version caret, version-menu tick); verse-selection bar (X). In **light and dark**, and
**under dynamic colour** (tint comes from `LocalContentColor` and is unchanged by this task, but
this is the pass where a wrong `fill` shows up).

**Font scale 1.5×** on the Schedule and reader top bars: icons do not scale with font, but those
bars are tight (the S16 single-line title), and this is where a glyph with a wrong intrinsic size
would show as a shifted or clipped title.

---

## 4. Acceptance criteria

1. `grep -rn "material\.icons" app/src/main/kotlin` returns **nothing**. `grep -rni "ContentCopy"
   app/src` returns nothing (the two comments of §2.3 are reworded).
2. Nine glyphs exist in `AppIcons.kt`, and **only** nine — no `ContentCopy`, and no glyph that has
   no call site.
3. `grep -rn "material-icons\|material\.icons" app/src` is empty **after** the harness deletion, so
   that Build & Release's removal has nothing left to break. (Their criterion — `grep -r
   "material-icons" --include=*.kts --include=*.toml .` empty — is discharged by their PR, not
   this one; see §5.)
4. The equivalence harness was green for all nine, **and was demonstrated red twice** (dropped
   `lineTo`, flipped `autoMirror`), with the output quoted in the PR and production restored
   byte-identically, SHA-256-verified.
5. `AppIconsTest` is permanent, pins the 3-true / 6-false `autoMirror` split in both directions, and
   its mutation was re-killed.
6. **Test count strictly increases from 940.** Zero net deletions other than the harness, which
   carries its explicit justified line (§11 rule 13).
7. `AppIcons.kt` is Phase-3-ready by construction: its imports are confined to
   `androidx.compose.ui.graphics.*`, `androidx.compose.ui.graphics.vector.*` and
   `androidx.compose.ui.unit.dp`; it contains no `android.*`, no `java.*`, no `R.`, no
   `androidx.compose.ui.res.*`, no `@Composable`, and no `if (isIOS)`-style branch. Check by grep
   and state the result.
8. **`AccessibilityGateTest` — 13 tests — green and unchanged.** Six of the thirteen call sites sit
   inside IconButtons it pins (`settings-back`, `picker-prev-month`, `picker-next-month`,
   `verse-selection-close`, `open-date-picker`, `open-settings`, `reader-open-picker`). You may not
   edit that file except for the `ContentCopy` comment rewording in §2.3, and that edit must not
   touch an assertion.
9. The full Compose UI suite green with **no test edited to accommodate a different glyph**. If a
   test asserts on a `contentDescription` it still passes untouched — the descriptions are
   `stringResource` lookups this task does not change.
10. **The six data gates report unchanged counts: 11 / 10 / 8 / 6 / 18 / 5.** This task touches no
    asset, plan, schema or tool, so they are unchanged by construction — report them anyway
    (§11 rule 5: any change is stop-and-escalate).
11. Full pipeline green from clean:
    `./gradlew spotlessCheck lintDebug assembleDebug testDebugUnitTest koverXmlReportAppDebug
    koverVerifyAppDebug`, plus `bundleRelease`.
12. **AAB size reported before and after.** Expect flat-to-smaller (R8 already strips unreferenced
    icon classes). The CI gate is 12 MB; 1.8.1 shipped at 7.75 MB. A size *increase* means
    something is being retained that should not be — investigate before shipping.
13. No new ProGuard/R8 keep rule was added, and none is needed (a Kotlin `object` with no
    reflection).
14. The device pass in §3.6 is executed and its results recorded — **including the pre-change RTL
    baseline** — before the 1.12.0 tag.

**Explicitly not claimed by any test in this task, and listed as such in the PR:** rendered RTL
mirroring; glyph appearance in light/dark/dynamic colour; top-bar layout at large font scale.

---

## 5. Boundaries and write set

### Write set

```
app/src/main/kotlin/com/jpillion/dailyreadingplanner/ui/icons/AppIcons.kt                  (new)
app/src/test/kotlin/com/jpillion/dailyreadingplanner/ui/icons/AppIconsTest.kt              (new)
app/src/test/kotlin/com/jpillion/dailyreadingplanner/ui/icons/AppIconsEquivalenceTest.kt   (new, then deleted in the same PR)
app/src/main/kotlin/com/jpillion/dailyreadingplanner/ui/settings/SettingsScreen.kt
app/src/main/kotlin/com/jpillion/dailyreadingplanner/ui/day/DayReadingsScreen.kt
app/src/main/kotlin/com/jpillion/dailyreadingplanner/ui/navigation/AppNavHost.kt
app/src/main/kotlin/com/jpillion/dailyreadingplanner/ui/datepicker/DayDatePickerDialog.kt
app/src/main/kotlin/com/jpillion/dailyreadingplanner/bible/ui/reader/ReaderScreen.kt
app/src/main/kotlin/com/jpillion/dailyreadingplanner/bible/ui/reader/ReaderVersionSelector.kt
app/src/main/kotlin/com/jpillion/dailyreadingplanner/bible/ui/reader/VerseSelectionBar.kt   (import + the §2.3 comment)
app/src/test/kotlin/com/jpillion/dailyreadingplanner/ui/AccessibilityGateTest.kt            (the §2.3 comment ONLY — no assertion)
```

In the seven screen files, the edit is **the import lines and the `imageVector =` argument, nothing
else.** No layout change, no modifier change, no `contentDescription` change, no size argument added
(the glyphs are already 24dp and `IconButton` handles the touch target). §11 rule 9 — no
refactoring during the port.

### Not yours

- **`gradle/libs.versions.toml` (lines 17 and 37) and `app/build.gradle.kts` (line 171)** — the
  dependency declaration itself. **Build & Release removes it**, in
  `rel-1120-dependency-realignment.md`, alongside the lifecycle and navigation swaps. Do not touch
  a build file. If you find yourself needing to, stop and escalate.
- `shared/**` — this release predates Phase 3; `shared/ui` is still empty and has no Compose
  content. The move described in §3.1 is a Phase 3 task with its own brief.
- `app/src/main/res/**` — no drawable is added, and none of the existing four is touched.
- Anything to do with the lifecycle or navigation artifact swaps that share this release.

### Ordering across the two owners

1. **You first.** Land `AppIcons.kt`, the harness, the call-site switch and the comment rewordings
   **with `material-icons-core` still declared** — the harness cannot compile otherwise, and it is
   the whole equivalence proof. End the PR with zero references to the artifact in `app/src`.
2. **Build & Release second.** They delete the catalog entry, the version line and the
   `implementation(…)`, and confirm `grep -r "material-icons" --include=*.kts --include=*.toml .` is
   empty.
3. Between (1) and (2) the build resolves an artifact nothing uses. That is harmless, and it is the
   only ordering in which the equivalence proof can be run at all.
4. **Then the release gates** in `ios-execution-plan.md` §6 — `NavRegressionTest`, the full Compose
   UI suite, the `p1-07` lifecycle re-run, the six data gates, and the `assembleRelease` on-device
   smoke that walks every destination and both tab back-stacks. Your §3.6 device pass folds into
   that same smoke; do not run the device twice.

### Coordination note (not an open question in this brief)

`p2-02-module-scaffolding.md` §3's boundary check forbids `import androidx.*` in
`shared/ui/src/commonMain`. Taken literally that forbids Compose Multiplatform itself, whose
packages are `androidx.compose.*` — so it blocks all of Phase 3, not just these glyphs. It has **no
effect on 1.12.0**, where the file lives in `:app`. Flagged for Staff to resolve before Phase 3
opens; do not resolve it from inside this task.

---

## 6. Escalation triggers

Stop and escalate — do not decide these yourself:

1. **A glyph cannot be made structurally equal to its upstream counterpart.** The standard is zero
   visual difference (§3.3); "it looks the same" is not a fallback you may take.
2. **`ImageVector` structural equality proves unusable even field-by-field**, so the equivalence
   harness cannot be written. That removes the only mechanical proof of visual equivalence in this
   task and the standard would have to be renegotiated.
3. **The sources jar is unavailable, or its contents do not match the 1.7.8 the build resolves.**
   Do not substitute a glyph from a web icon picker or a different Material version — the whole
   point is that these are the bytes users see today.
4. **You find a tenth `Icons.*` call site** that §2.2 missed, or one of the thirteen has moved.
   Vendor what the code says, then report the discrepancy (§11 rule 0) — do not quietly extend the
   list.
5. **Any existing test needs editing to pass**, beyond the two comment rewordings. A test that has
   to change means the glyph, the layout or the semantics changed, and this task changes none of
   them.
6. **Any of the six data gate counts moves off 11 / 10 / 8 / 6 / 18 / 5** (§11 rule 5).
7. **You need a build-file, `libs.versions.toml`, Robolectric-configuration or new-dependency
   change** — including turning on Robolectric native graphics to screenshot-test the mirroring.
   That goes to Build & Release with the artifact and the reason, per invariant 6.
8. **The forced-RTL device pass shows a glyph not mirroring**, and the cause is not the `autoMirror`
   flag. That means the render path differs from the assumption in §3.5 and the guard design has to
   change, not just the code.
