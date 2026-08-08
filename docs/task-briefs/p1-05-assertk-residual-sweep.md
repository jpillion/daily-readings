# p1-05 — Truth → assertk: the residual sweep, and mutation-verifying the six gates afterwards

> **Assignee:** Verification Engineer
> **Release:** 1.9.0 · **Merge order:** Group C — **last in 1.9.0**, after `p1-01` … `p1-04`.
> May slip into 1.10.0 without risk; it ships **zero production bytes**.
> **Inherits:** [`p1-00-overview.md`](p1-00-overview.md) rules R1–R7.
> **Preconditions:** `p1-01` … `p1-04` merged. **Build & Release has added assertk.**

---

## Objective

Convert **every remaining** Google Truth assertion to **assertk**, so that no test file blocks a
move to `commonTest`, and then **prove the six data-verification gates still fail when they
should** by re-killing their mutations after conversion.

The second half is the point. The first half is typing.

---

## Context

### Scale, measured on `main` at `1bcc98e`

- **92 test files** import `com.google.common.truth`.
- Those files contain **846 of the app's 940 `@Test` methods.**
- **1,444 `assertThat(` call sites** across the suite.

> The signed-off approach says "846 of 940 assertions." **The honest figure is 846 `@Test` methods
> / 1,444 assertion call sites.** Use the accurate one in every report.

Google Truth is Guava-based and **JVM-only**. Neither it nor JUnit 4 works in `commonTest`. Every
one of these has to move eventually, and doing it while the code is still one Android module — with
Robolectric, the full pipeline and the real gates all working — is the cheapest moment there will
ever be.

### Why assertk, specifically

**Not bare `kotlin.test`.** `kotlin.test` has no message-carrying assertion equivalent to
`assertWithMessage`, and `assertWithMessage` is the mechanism the plan gates use to produce
diagnosable failures. A gate that fails with `expected true, got false` instead of
`day 173: BC portion [Jas 1, Jas 2] segments to 2 cards, expected 1` is a **materially weaker
gate**, even though it is technically red at the same moment. Those messages are load-bearing and
**must survive**.

assertk gives `assertThat(x).isEqualTo(y)` with the same shape as Truth, plus lambda-based failure
messages, and it is multiplatform.

### Why this is the highest-silent-risk task in Phase 1

It produces **zero user-visible progress** across ~92 files of mechanical churn. That combination
is precisely what gets under-resourced and then rushed, and rushing an assertion rewrite silently
weakens the thing that makes this codebase trustworthy.

The specific failure mode to watch for, because it is easy and it is invisible:

```kotlin
// Truth — reports what differed
assertThat(actual).isEqualTo(expected)
// A "conversion" that is not one — reports only "false"
assertTrue(actual == expected)
```

Also watch: `containsExactly` losing its order semantics; `isNotNull()` followed by `!!` where
Truth's smart-cast did the work; `assertThrows` shape changes; and a `@Test(expected = …)` silently
becoming a test that passes when nothing is thrown.

---

## Contract

### 1. The write set is defined by a command, not a list

```
grep -rl "com.google.common.truth" app/src/test/kotlin
```

Whatever that returns when you start is your write set. `p1-01`…`p1-04` have already converted the
files they touched (rule R4), so expect **fewer than 92**. Record the number you actually found.

### 2. Convert mechanically, file by file, and keep the diagnostics

- `assertThat(x).isEqualTo(y)` → same shape in assertk.
- **`assertWithMessage("…").that(x)` → assertk's message-carrying form. Never drop the message.**
  Where a gate builds its message from loop state (`"day $d: …"`), the converted assertion must
  still produce that same string on failure. **Verify by making it fail once and reading the
  output.**
- `containsExactly(…).inOrder()` → the order-preserving assertk equivalent. Silently losing
  `inOrder()` is a real weakening: several plan-gate assertions depend on sequence.
- JUnit 4 `@Test` / `@Before` / `@Rule` **stay** for now. This task is Truth → assertk **only**;
  the JUnit → `kotlin.test` move happens per-module in Phase 2 as code relocates.

### 3. Then mutation-verify all six gates

**This is the acceptance-critical half.** After conversion, for **each** of the six gates,
introduce one mutation, confirm the gate goes **red with a diagnosable message**, and restore the
file **byte-identically** (verify with `git diff` and a checksum).

| Gate | Mutation to apply |
|---|---|
| `ReadingPlanVerificationTest` (11) | Change one chapter number in `plans/bible_companion/plan.json` |
| `McheynePlanVerificationTest` (10) | Drop one verse window from a Ps-119 day |
| `ChronologicalPlanVerificationTest` (8) | Duplicate one chapter across two days (breaks exactly-once coverage) |
| `PlanSegmentGateTest` (6) | Make one portion's refs non-contiguous in a way the segmenter must split |
| `BibleTextVerificationTest` (18) | Corrupt John 11:35 in a **scratch copy** of `bible.db` — **never the committed asset** |
| `BibleDatabaseRoomOpenTest` (5) | Point the builder at a nonexistent asset path |

**For each, record the failure message text.** If a message is less informative than before
conversion, **that file's conversion is not done.**

> **`bible.db` is byte-reproducible and guarded by a CI byte-diff job whose stability took six
> weeks of red builds to achieve** (the `LD_PRELOAD` SQLite-3.43.2 pinning). Mutate a **copy**.
> Confirm `git status` on `app/src/main/assets/` is clean before you finish.

### 4. Keep `MainDispatcherRule` alone

It moves to `StandardTestDispatcher` in **`p1-06`**, deliberately in its own commit. If you change
it here, the tests that redden will be attributed to the assertion rewrite and the real signal is
lost.

---

## Acceptance criteria

1. `grep -rl "com.google.common.truth" app/src/test/kotlin` returns **nothing**.
2. **Truth is removed from `gradle/libs.versions.toml` and from `app/build.gradle.kts`.** Build &
   Release has ruled on this — see [`p0-build-foundation.md`](p0-build-foundation.md) **§3.B.3.1**,
   which supersedes p0's earlier *"Truth stays … do not delete it."* The ruling is that `p1-05` is
   correct: assertk publishes a JVM variant, so a test staying JVM-only
   (`BibleTextVerificationTest`, 18, on sqlite-jdbc) does **not** require a JVM-only *assertion
   library*, and leaving Truth in the catalog only invites a future JVM-only assertion into a file
   that must later move to `commonTest` — the exact problem this task exists to eliminate.

   **The removal is conditional, ordered, and not yours to perform:**
   - **Condition:** criterion 1 above is green — `grep -rl "com.google.common.truth"
     app/src/test/kotlin` returns nothing. Until then Truth stays; removing it earlier is a red
     build. If you escalate on a file you cannot convert, the removal is deferred with it. The gate
     is criterion 1 being green, not this task being declared finished.
   - **Who:** Build & Release, on your request. **Do not edit `gradle/libs.versions.toml` or
     `app/build.gradle.kts`.**
   - **When:** a **separate commit** from your conversion, and the **last** commit of release
     1.9.0, so that reverting the removal does not drag ~92 files of conversion with it.
   - **What:** the `truth = "1.4.4"` version entry, the `truth = { group = "com.google.truth",
     name = "truth", … }` library entry, and the `testImplementation(libs.truth)` line. Nothing
     else.

   **Truth leaving the catalog must not be allowed to read as "the diagnostics left with it."**
   Criterion 8 below is the guard: `assertWithMessage` is the mechanism the six data gates use to
   produce diagnosable failures, and it is the reason assertk was chosen over bare `kotlin.test`.
3. **Test count is unchanged: 940 (plus whatever `p1-01`…`p1-04` added).** State the exact
   before/after. **A dropped test is a failed task**, not a tidy-up.
4. **The six gates report their exact original counts: 11 / 10 / 8 / 6 / 18 / 5.**
5. **All six gate mutations killed, each restored byte-identically, each failure message recorded
   in the PR.** This is the deliverable.
6. `AccessibilityGateTest` (13 as of sprint 00Q) converted and green.
7. **Zero `assertTrue(a == b)` introduced.** Prove it:
   `grep -rn "assertTrue(.*==\|assertFalse(.*==" app/src/test/kotlin` returns nothing new.
8. **Zero `assertWithMessage` equivalents dropped.** Compare the count of message-carrying
   assertions before and after; it must not fall.
9. Full pipeline green, and **run the tests with `--rerun-tasks`** so nothing is served from cache.
   Report the number of test tasks actually executed.
10. `git status` on `app/src/main/assets/` and `app/schemas/` is **clean**.
11. **No R8 device smoke required** — this task ships zero production bytes. Say so explicitly
    rather than omitting it; an unexplained missing gate reads like an oversight.

---

## Boundaries / write set

**Yours:** every file returned by `grep -rl "com.google.common.truth" app/src/test/kotlin` at the
time you start, plus any shared test helper under `app/src/test/.../testing/` **except**
`MainDispatcherRule.kt`.

**Not yours:**
- **Anything under `app/src/main/`.** If a test only passes after a production change, that is an
  escalation — the conversion changed behaviour, or found a real bug that predates you. Either way
  Staff decides.
- **`app/src/test/.../testing/MainDispatcherRule.kt`** — **`p1-06`**.
- **`app/src/main/assets/**` and `app/schemas/**`** — mutate copies only.
- `gradle/libs.versions.toml` — **Build & Release**.

---

## Escalation triggers

- **A test fails after conversion** → **Staff**, blocking. Do not adjust the assertion until the
  cause is known. Either the conversion is wrong or Truth was hiding something, and both matter.
- **A gate mutation does NOT kill its gate** → **Staff**, blocking, immediately. That is a gate
  that has stopped protecting the project's core IP, and it outranks the rest of this task.
- **A test cannot be converted without changing what it asserts** → **Staff**. Bring the before and
  after.
- **The residual file count is materially higher than expected** (i.e. `p1-01`…`p1-04` did not
  honour rule R4) → **EM**, non-blocking, so the estimate is corrected rather than absorbed.
