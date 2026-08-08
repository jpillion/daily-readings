# Test port strategy — 940 tests to a multiplatform suite

> **Owner:** Verification Engineer · **Date:** 2026-08-08 · **Status:** plan, for the EM's execution
> plan and the per-phase task briefs
>
> Companion to [parity-matrix.md](parity-matrix.md) (what "done" means) ·
> [ios-port-approach.md](ios-port-approach.md) (owner-signed scope and sequence) ·
> [adr/0010-data-verification-gates.md](adr/0010-data-verification-gates.md) (the gate tiering)

**The one-sentence version.** 940 tests, 846 of them assert through a JVM-only library, 201 of them
run on a JVM-only Compose harness, and six of them are the gates that protect this project's core
IP — and the entire conversion produces **zero user-visible progress**, which is precisely the
condition under which a test suite gets silently weakened. This document is how it does not.

**The rule that governs everything below:** *a ported test is not ported until its original
mutation has been re-killed.* §6.

---

## 1. Where the numbers come from

Every figure here was re-derived from `main` on 2026-08-08, not copied from an earlier document.
Two prior figures were wrong and are corrected:

| Figure | Prior claim | Re-derived | How |
|---|---|---|---|
| Total tests | 936 | **940** | `grep -rh "@Test" app/src/test --include="*.kt" \| wc -l` |
| Test files | — | **115** (105 test-bearing, **10** fixtures/fakes, 749 LOC) | per-file `@Test` counts |
| Data gates | 5 | **6** | `PlanSegmentGateTest` postdates the brief |
| Truth-borne tests | 846 | **846** ✓ | `@Test` count in the 92 files importing `com.google.common.truth` |
| `assertThat` sites | — | **1,444** | |
| `assertWithMessage` sites | — | **111**, of which **102 (92%) are inside the six gate files** | this is the whole argument for assertk — §3 |
| Robolectric files / tests | 29 files | **29 files / 278 tests** | `^import org.robolectric` |
| Compose-harness tests | 201 | **201** ✓ (17 files, all also Robolectric) | `createComposeRule` |
| Reach `commonTest` | 855 (91%) | **842 (89.6%)**, of which 18 are contingent | §2 — **the earlier 855 was a coarse estimate; this is the file-level number** |

Two greps that look right and are not, recorded so nobody repeats them: `grep -l "Robolectric"`
matches KDoc prose (`VerseSelectionTest` says "pinned pure (no Robolectric)") — match
`^import org.robolectric`. And `grep --include=*.kt` needs the pattern quoted under zsh or it
silently matches nothing and reports zero.

---

## 2. The categorization, with real counts

| # | Category | Files | Tests | Becomes |
|---|---|---|---|---|
| **A** | **Fixtures and fakes** — no `@Test` | 10 | 0 | `commonTest` in `shared/*`. **These port first.** §4 |
| **B** | Pure domain / data, plain JUnit4 + Truth, no Android | ~70 | **623** | `commonTest`. Mechanical: JUnit4→`kotlin.test`, Truth→assertk, `java.time`→`kotlinx-datetime` |
| **C** | Compose UI, Robolectric-hosted | 17 | **201** | `commonTest` via `runComposeUiTest`. Harness rewrite, not assertion rewrite. §5 |
| **D** | Room/DataStore integration, Robolectric-hosted | 2 | **18** | `commonTest` **iff** Room KMP lands (ADR-0007/V2). Otherwise stays `androidUnitTest` + a new iOS twin. **Contingent** |
| **E** | Android platform integration — Room asset open, asset gate, migration | 4 | **11** | Stays `androidUnitTest` forever. iOS gets *new* equivalents (§6, GATE-07) |
| **F** | Android notification platform — scheduler, two notifiers | 3 | **18** | Stays `androidUnitTest`. iOS gets new `UNUserNotificationCenter` tests |
| **G** | Glance widget + midnight rollover | 3 | **25** | Stays `androidUnitTest`. **No iOS counterpart in v1.0** (parity N/A-IOS REM-12/13) |
| **H** | In-app updates | 2 | **11** | Moves to `androidApp` with its production code. **No iOS counterpart** (N/A-IOS SET-11) |
| **I** | Persistent-notification domain | 2 | **7** | Moves to `androidApp`. **No iOS counterpart** (N/A-IOS REM-10) |
| **J** | DI wiring (`BibleRemoteModuleTest`) | 1 | **6** | Replaced by Koin `checkModules()` + a ViewModel-construction smoke, both platforms |
| **K** | Clipboard SDK-33 confirmation rule | 1 | **2** | Stays Android. iOS gets a *different* rule test (always confirm — G3 / parity SEL-13) |
| **L** | `BibleTextVerificationTest` (sqlite-jdbc) | 1 | **18** | Shared **`jvmTest`**, unchanged. Not common, not iOS. ADR-0010 tier 2 |
| **M** | **New iOS tests with no Android original** | — | **≈80–90** | §7 |

**Arithmetic:** A+B+C+D = **842 reach `commonTest`** (89.6%), of which **18 are contingent** on Room
KMP. E–L = **98 tests stay platform-side or JVM-side**. 623 + 201 + 18 + 98 = 940. ✓

**Nothing in this table moves for free.** Category B is the closest to mechanical and it is still
*three* simultaneous rewrites per file (assertion library, test framework, date library) on tests
that currently pass — i.e. the highest-risk-of-silent-weakening shape there is.

---

## 3. Truth → assertk

**Recommendation: assertk. Not bare `kotlin.test`.**

The argument is not taste. **102 of the repo's 111 `assertWithMessage(...)` calls live inside the
six data gates**, distributed:

| Gate | `@Test` | `assertWithMessage` |
|---|---|---|
| `ReadingPlanVerificationTest` | 11 | 34 |
| `McheynePlanVerificationTest` | 10 | 22 |
| `ChronologicalPlanVerificationTest` | 8 | 12 |
| `PlanSegmentGateTest` | 6 | 15 |
| `BibleTextVerificationTest` | 18 | 17 |
| `BibleDatabaseRoomOpenTest` | 5 | 2 |

Those messages are **load-bearing**. A gate over 2,920 portions or 31,102 verses that fails with
`expected: true but was: false` is a gate nobody can act on. `kotlin.test` gives you a lazy message
lambda, which is *technically* sufficient — but it makes the diagnostic optional at every one of
102 sites, and "optional, under schedule pressure, on work with no visible output" is how you get a
gate that still goes red and no longer tells you why.

### What is mechanically rewritable

| Truth | assertk | Rewritable by regex? |
|---|---|---|
| `assertThat(x).isEqualTo(y)` | `assertThat(x).isEqualTo(y)` | **Import change only** — the shape is identical |
| `.isTrue()` / `.isFalse()` / `.isNull()` / `.isNotNull()` | same names | **Yes** |
| `.isEmpty()` / `.hasSize(n)` / `.contains(x)` / `.containsExactly(…)` | same names | **Yes** |
| `.containsExactly(…).inOrder()` | `.containsExactly(…)` (assertk is ordered by default) | **No — semantic change.** Every `.inOrder()` and every bare `containsExactly` must be read individually, because Truth's `containsExactly` is *unordered* and assertk's is *ordered*. Getting this backwards **strengthens or weakens** an assertion silently in both directions |
| `assertWithMessage(m).that(x).isEqualTo(y)` | `assertThat(x, m).isEqualTo(y)` | Regex-able **only** where `m` is a single expression on one line. The gates' messages are multi-line interpolated strings; those are hand work |
| `.isAtLeast()` / `.isGreaterThan()` | `.isGreaterThanOrEqualTo()` / `.isGreaterThan()` | Partially — names differ |
| `assertThat(map).containsEntry(k, v)` | `assertThat(map).key(k).isEqualTo(v)` | **No** |
| `assertThat(throwable).hasMessageThat().contains(s)` | `assertThat(t).hasMessage(...)` / `messageContains` | **No** |

**Procedure, and it is deliberately slow at the gates:**

1. Convert **file by file, alongside the production code that file covers** (ADR-0010 rejects a
   big-bang and it is right). Never convert a file whose production code is not moving in the same
   commit.
2. For each converted file, run it **before** and **after** and diff the *test count*. A dropped
   `@Test` is invisible in a green run.
3. **Deliberately break the assertion and read the failure message.** For every one of the 102 gate
   messages, the reviewer's job is not "does it compile" but "would this message let me find the
   bad day/verse/portion in a 2,920-item corpus?" If not, the conversion is not done.
4. `containsExactly` sites get a written note in the commit message stating whether order was
   significant in the original. This is the single highest-risk mechanical class in the whole
   conversion.
5. Mutation-verify per §6.

**Do not write a mass `sed`.** The temptation is enormous — 1,444 call sites — and the classes above
that are *not* regex-able are exactly the ones where a bad rewrite still compiles and still passes.

---

## 4. The 10 files that port first

Everything else in the suite depends on these. They contain **no `@Test`**, so porting them produces
a green build with zero test signal — which is fine, because their correctness is proven by the 940
tests that follow.

| File | LOC | Notes for the port |
|---|---|---|
| `domain/Fakes.kt` | 195 | The largest. Fakes for progress, plan, settings, clock. Everything in category B depends on it |
| `testing/FakeSettingsRepository.kt` | 164 | Every settings-driven flow |
| `testing/FakePartialReadingRepository.kt` | 89 | Sprint-00P partial checks |
| `testing/ReminderFakes.kt` | 78 | Scheduler/notifier fakes |
| `bible/domain/FakeBibleTextSource.kt` | 64 | **Window-aware** — Sprint J relies on it returning only in-range verses |
| `testing/StatsFixtures.kt` | 53 | |
| `testing/SegmentFixtures.kt` | 38 | |
| `bible/domain/BundledResolver.kt` | 31 | |
| **`testing/MainDispatcherRule.kt`** | 24 | **This one is not a port, it is a redesign — see below** |
| `testing/FakeWidgetRefresher.kt` | 13 | Android-only after the split, but referenced from shared tests today |

**`MainDispatcherRule` must not be ported as-is, and this is the most consequential line in this
section.** It is a JUnit 4 `TestWatcher` (no `commonTest` equivalent) and it defaults to
`UnconfinedTestDispatcher` — **the most forgiving possible scheduling**. That default is precisely
what let the 1.7.0 ordering bug hide: every existing test constructs the ViewModel *first* and only
then populates upstream state, which is the safe order, and `Unconfined` never surfaces the
difference.

Replacement: a `commonTest` helper that sets `Dispatchers.Main` to a **`StandardTestDispatcher` by
default**, with `Unconfined` available only by explicit opt-in and a comment saying why. Expect
this to turn some currently-green tests red. **Those reds are findings, not conversion damage** —
triage each one before touching it.

---

## 5. The 201 Compose tests: Robolectric → `runComposeUiTest`

17 files. The change is **the harness, not the assertions** — and that is the good news buried in
the UI review: string *values* do not change, so the ~338 literal string assertions survive
verbatim.

| Today | Becomes |
|---|---|
| `@RunWith(RobolectricTestRunner)` + `@Config(sdk = [34])` | deleted |
| `@get:Rule val composeRule = createComposeRule()` | `runComposeUiTest { … }` |
| `composeRule.onNodeWithTag("x")` | `onNodeWithTag("x")` inside the lambda |
| `composeRule.setContent { AppTheme { … } }` | `setContent { … }` |
| `stringResource(R.string.x)` in the test | Compose Resources accessor (ADR-0013) |
| `ApplicationProvider.getApplicationContext()` | gone |
| `@get:Rule val mainDispatcherRule = MainDispatcherRule()` | the §4 replacement |

**Everything is `@OptIn(ExperimentalTestApi::class)`.** `runComposeUiTest` is still experimental in
CMP; expect it to be a source of churn across CMP upgrades, and pin the CMP version deliberately.

### The ~19 that genuinely break

**23 assertion sites across 5 files (≈19 distinct `@Test` methods) pin locale-formatted output** and
cannot survive a move to `commonTest`, because `java.time.format` and `NSDateFormatter` do not
produce byte-identical strings (G2):

| File | Sites | What is pinned |
|---|---|---|
| `ui/day/DayReadingsPagerScreenTest` | 11 | `"Today – June 10"`, `"Thursday, June 11"`, `"Friday, January 1, 2027"`, `"Wednesday, March 1"` |
| `ui/datepicker/DayDatePickerDialogTest` | 7 | `"June 2026"`, `"January 2027"`, and four spoken dates like `"Monday, June 1, 2026, All readings done"` |
| `ui/day/TrackingStartPromptDialogTest` | 2 | `"Start from January 1, 2026"`, `"Start from today (Jun 10, 2026)"` |
| `ui/settings/SettingsScreenTest` | 2 | `"Jun 3, 2026"`; the reminder-time row |
| `ui/AccessibilityGateTest` | 1 | `"Wednesday, June 10, 2026"` |
| *(`widget/*` — 2 more)* | 2 | Android-only, stays |

**Do not "fix" these by loosening them to `contains("June")`.** That is the narrowing prohibited by
parity-matrix rule 4. The correct split:

- The **date-arithmetic** half (does Jan 1 follow Dec 31? does the year appear only when it
  differs?) stays in `commonTest` asserting on a **structured** value, not a rendered string.
- The **rendered-string** half moves to a per-platform test with per-platform expected literals,
  and the iOS literals are captured from a real device once and pinned. Parity row **DAY-15** is
  tiered T2 for exactly this reason.

One subtlety worth flagging: `SettingsScreenTest:273` computes its expected value using *the same
formatter the production code uses*. That is self-referential — it will not catch a formatter
change at all, on either platform. Flag it during conversion; it is a pre-existing weak pin, not a
port defect.

### What `runComposeUiTest` still cannot do

It is a T3 tier by definition (parity §2). `performTouchInput { longClick() }` is synthetic input
into the composition; it says **nothing** about whether a long-press fights the `HorizontalPager`'s
horizontal drag or the `LazyColumn`'s vertical scroll on glass. Sprint 00Q shipped that gesture with
exactly this gap and the owner's device pass closed it on Android. On iOS it re-opens, and it is
parity row **SEL-01** at T4.

---

## 6. Gate-by-gate plan (all six, plus two new)

The gates are not "tests that happen to be important." They are the reason the plan data and the
KJV corpus can be trusted at all. **A release that skips a gate is not a release**, and a gate that
is converted without being re-mutated is a gate that is *asserted* to still work.

### Gate 1–4 — the four plan gates (35 assertions) → `commonTest`

**What changes:** JUnit4 + Truth → `kotlin.test` + assertk; the `planAssetsDir` **system property**
→ a generated `TestAssetPaths.kt` constant emitted by a Gradle task; file reads through
**okio `FileSystem.SYSTEM`**; `kotlinx-serialization` already in place, so no new parser.

**What must not change:** they read the **source tree**, not a packaged copy. That is what makes
them trustworthy — they verify the exact bytes that ship.

**The correction to my own earlier reasoning, recorded so it does not propagate (D-PORT-5 / A5):**
I previously justified running these on iOS on the grounds that it *additionally proves the assets
were packaged into the iOS bundle*. **That is false.** A Kotlin/Native test on a simulator can read
the host's absolute source path and pass happily with no asset ever entering an app bundle. The
conclusion (move them to `commonTest`) stands; the justification does not. The real reasons are:
(a) a Kotlin/Native JSON-parsing or arithmetic divergence would surface here and nowhere else, and
(b) they then compile for every target and run free on the Linux PR runner.

**Packaging is proven by a different gate. That gate does not exist yet and is specified below.**

**Where they run:** JVM/Android on **every PR**; iOS targets **on the release pipeline** (macOS
minutes are 10× and this is where "no silently skipped gate" actually binds).

**Preserve the `inputs.dir` up-to-date declaration** when the asset path moves. Without it, edits
to a bundled asset are silently skipped as `UP-TO-DATE` and the gate never re-runs — a defect this
project has already paid for once.

### Gate 5 — `BibleTextVerificationTest` (18) → shared `jvmTest`, unchanged

Stays on `sqlite-jdbc`, JVM only. Its question — "is the committed `bible.db` the right data?" —
cannot vary by platform. `java.security.MessageDigest` stays with it.

**The iOS release pipeline must depend on the task and report it explicitly**:
`asset gate (JVM): 18 assertions, passed`. That satisfies "no silently skipped gate" without
pretending JDBC runs on arm64.

Revisit **only** if ADR-0007 drops Room for the bible DB — at that point the KMP SQLite driver is
already in `shared/data` and the rewrite is nearly free.

### Gate 6 — `BibleDatabaseRoomOpenTest` (5) → `androidUnitTest`, unchanged, forever

It exists because that exact thing failed in production (sprint-00F: every chapter showed "couldn't
load this chapter", because the asset's `verse` DDL carried an FK and an index Room did not declare
and had no `room_master_table`). It is per-platform by nature. Do not touch it during the port
beyond keeping it green.

### NEW Gate 7 — `BibleDatabaseOpenTest` (iOS, `iosTest`) — **release-blocking**

The single most important new test in the port. It is the only thing that will catch the iOS
equivalent of sprint-00F before a user does.

**Probes — the same four, deliberately:** Genesis 1:1 · John 3:16 · John 11:35 (`"Jesus wept."`) ·
the Psalm 3 verse-0 superscription (present, `isTitle`, correct text).

**It must go through the shipping open path** — the same `BundledDatabaseProvider` / driver
configuration production uses, resolved from `NSBundle`, copied to the App Group container. A test
that opens the file by a hard-coded simulator path proves nothing about the app.

**It must be proven to fail.** Exactly as the original was: the sprint-00F record says
*"proven to FAIL against the broken asset ('invalid schema') and PASS after the fix."* Reproduce
that discipline —

1. Build a deliberately corrupted copy of `bible.db` (drop `room_master_table`, or re-add the
   foreign key, or truncate the file).
2. Point the test at it. **Watch it go red, and record the failure text in the commit message.**
3. Restore the real asset byte-identically (SHA-256-verified) and watch it go green.

A gate that has never been observed failing is a gate nobody has verified. This is parity row
**GATE-08**, and it is a row of its own precisely so the proof cannot be skipped.

### NEW Gate 8 — `BundleAssetIntegrityTest` (iOS, `iosTest`) — the packaging gate

**This is the gate that answers the packaging question the plan gates do not.**

ADR-0011 names the trap: iOS bundle resources are **flat by default** unless the directory is added
as a *folder reference* (blue) rather than a *group* (yellow). Get it wrong and
`plans/mcheyne/plan.json` becomes `plan.json`, colliding with two identically-named files. It is a
20-minute mistake that presents as a mysterious "wrong plan loaded" bug, and no other test in the
suite can see it.

**Specification:**

```
For each of the five assets:
    plans/registry.json
    plans/bible_companion/plan.json
    plans/mcheyne/plan.json
    plans/chronological/plan.json
    bible/bible.db

  1. Resolve it from NSBundle at its expected NESTED path.
     - Assert resolution succeeds.
     - Assert the resolved URL's last two path components are the expected
       directory + filename (this is what catches flattening).
  2. Read the bytes and assert SHA-256 equals a GENERATED constant.
     - The constant is emitted at build time by the same Gradle task that emits
       TestAssetPaths.kt, computed from the source-tree file.
     - It is NEVER hand-copied. A hand-copied hash is a hash somebody will
       "fix" when it goes red.
  3. Additionally assert that resolving the BARE filename ("plan.json") returns
     either nothing or exactly one known file — a positive check that the
     flattening did not happen.
```

Point 3 matters: with three files named `plan.json`, flattening may still let *one* of them resolve,
so a naive "the file loads" assertion passes while two plans silently point at the third.

**5 assertions.** Release-blocking. Parity rows **GATE-09 / GATE-10 / GATE-13**.

### The honest ledger

> **`common: 35 · jvm: 18 · android: 5 · ios: 10 (both new)`**

Never "all gates run everywhere." (D-PORT-6.) And it is **six** gates today, not five — the brief's
"five" predates `PlanSegmentGateTest`.

---

## 7. Mutation testing through the port

### The rule

> **A ported test is not ported until its original mutation has been re-killed.**

Not "the test passes." Not "the test compiles." The mutation that the test was written to catch is
re-applied to the *ported* production code, the *ported* test is observed going **red**, and the
production code is restored **byte-identically** (SHA-256-verified, as this project already does).

### The regression list already exists

`CLAUDE.md` records **160 named mutations across 34 sprint entries**, each with the specific
production edit and the specific test that caught it. That is not a nice-to-have; it is a
ready-made, ordered, zero-authoring-cost regression suite for the entire port. Nobody has to invent
what to mutate.

It also records **two mutations that SURVIVED** (sprint 00Q: deleting the `BackHandler` entirely,
and Copy firing without dismissing the menu) — both exposed real coverage gaps that were then
closed. Those two are worth re-running first, because a surviving mutation is the shape of finding
this exercise exists to produce.

### Where mutation testing earns its cost in *this* port

Prioritise these five. They are the places where a mechanically-correct rewrite is most likely to be
behaviourally wrong while staying green:

1. **The `java.time` → `kotlinx-datetime` translation.** `toEpochDays()` keys **every progress row
   in the database**. Mutate: shift the epoch-day conversion by one; flip a `<` to `<=` at a
   `DayCompletionClassifier` boundary; move a date arithmetic call across the local-timezone
   boundary. Required kills: the classifier truth-table tests, the streak walk (pre-start, Feb 29,
   year boundary), `ProgressRepository` year isolation. **Include pre-1970 and leap-day dates** —
   the ADR-0009 equivalence pin.

2. **The Room identity hash and the bible DB open path.** Mutate: drop the `room_master_table` row;
   re-add the foreign key; change one byte of the hash constant. Required kill: GATE-07 on iOS and
   `BibleDatabaseRoomOpenTest` on Android. This is the sprint-00F failure re-armed on a second
   platform.

3. **Every `expect`/`actual` boundary.** The failure mode is unique to KMP: the `expect` declaration
   compiles, one `actual` is correct, the other is a stub or an inverted default, and every test
   that runs on the *first* platform stays green. Mutate: make the iOS `actual` return the opposite
   / an empty value / a fixed constant, and confirm at least one test that runs on the iOS target
   goes red. **An `expect`/`actual` pair with no test executing on the iOS target is an untested
   platform, however green the suite looks.**

4. **`ReadingSegments` → `ConsecutiveChapterRuns` delegation.** Named here because a mechanical
   rewrite would *naturally* inline it. `ReadingSegments.segmentsOf` is four lines that delegate to
   the grouper `ProviderUrlBuilder` also uses — one home, so card boundaries and external-URL
   grouping cannot drift. A porter reading it in isolation will very reasonably re-implement the
   run-grouping locally, all tests will stay green, and a **drifted second grouper** will exist. The
   sprint-00P mutation pins this **in both directions** — re-run both. (It is also worth noting that
   `ReadingFormatter`'s *private* `consecutiveRuns` is a deliberately different rule — it also
   breaks on verse windows — so "there are already two groupers" is a trap for the unwary.)

5. **The assertion-library conversion itself.** After each file converts, mutate the production code
   that file covers and confirm the converted test still goes red **with a message you can act on**.
   For the six gates this is mandatory (parity row GATE-14); for category B it is
   sampled — every file whose production code has a recorded mutation in `CLAUDE.md`.

### What this costs

Roughly **0.5–1 engineer-week per phase**, spread, not batched. Batching it to the end guarantees it
gets cut.

---

## 8. The `ios-release-smoke` gate

**Mandatory before every iOS tag, from Phase 4 onward. Not optional, not "when we have time."**

**Why it exists.** You cannot run Kotlin/Native unit tests on a physical iPhone. Every automated iOS
result is **simulator + debug + host arch**; the shipped artifact is **device + release + arm64**.
There is no configuration in which the suite runs against what ships. Android had **one** axis of
divergence and it still shipped 1.7.0 — a crash on every reading tap, invisible in `assembleDebug`,
guaranteed under R8. iOS has **three** axes.

### Part 1 — scripted XCUITest, release configuration, physical device (~90 seconds)

Replays exactly the paths this codebase has already broken. Each step maps to a parity row.

| # | Step | Replays | Parity row |
|---|---|---|---|
| 1 | Launch cold. Assert the day screen renders three reading cards. | — | DAY-01 |
| 2 | **Tap a reading card.** Assert the reader opens on that reading and the app does not terminate. | **1.7.0** — `ReaderViewModel.init` collected a handoff populated *before* construction; `_selection` was declared *below* `init`; NPE, R8-only | READ-11, SYS-10 |
| 3 | Switch to the Chronological plan; navigate to **07/25**; tap the second card. Assert it opens **Isaiah 37**, not Genesis 1. | **sprint-00P** — 83 of 365 days opened Genesis 1 | READ-11 |
| 4 | Open the picker; pick **Psalm 23**. Assert the reader shows Psalm 23. | **1.8.1** — picker jump snapped to Genesis 1; reproduced only on R8 release | PICK-09 |
| 5 | Change version KJV → NKJV → NASB. Assert position held. | **1.8.1** — the untested direction was NASB | PICK-10 |
| 6 | Swipe the reader forward across a book boundary (Genesis 50 → Exodus 1). | D-H-2 | READ-02 |
| 7 | Open **each of 6 spot-check chapters** across the canon; assert non-empty text. | **sprint-00F** — every chapter failed to load; no test opened the real asset through Room | READ-01, GATE-07 |
| 8 | Long-press a verse; assert selection mode. | sprint-00Q; the R5 gesture risk | SEL-01 |
| 9 | Assert no crash log was produced during the run. | — | SYS-10 |

Step 7 is the cheap insurance: the sprint-00F P0 shipped because `BibleTextVerificationTest` opens
the DB via a JDBC driver (bypassing Room) and every reader test faked `BibleTextSource` — **the real
asset was never opened by the real code in any test.** Six chapters on a real device costs seconds.

### Part 2 — the DCE canary

Kotlin/Native release links do aggressive dead-code elimination. Anything reachable **only** through
the Obj-C bridge can be present in debug and stripped from release, and it surfaces as a **missing
symbol or a nil — not a stack trace.** That is worse than the R8 case, because there is nothing to
retrace.

**Mechanism:** a small `DceCanary` object in shared code, exported to Swift, whose members span the
shapes actually crossing the bridge (an object accessor, a `suspend` function via its completion
form, a sealed-type instance, an enum value, a `Flow` collector adapter). The SwiftUI host calls
every member during the smoke run and asserts each returns its known sentinel. If any returns nil or
fails to link, **the release build stripped something that debug kept** — and you learn it in the
smoke run, not from a 1-star review.

Grow the canary whenever a new shape starts crossing the bridge. It is deliberately not clever; it
is a tripwire.

### Part 3 — the ViewModel-init rule

Generalize `ReaderViewModelHandoffInitTest` (3 tests) from "a regression test for one bug" into **a
rule applied to every ViewModel that collects a flow in `init`**:

> For every ViewModel that collects in `init`: there is a test that **populates the upstream state
> BEFORE the ViewModel is constructed**, and it runs under **`StandardTestDispatcher`**.

Why it must be stated as a rule and not a test: all 879 tests passed against the 1.7.0 crash because
**every existing test constructed the ViewModel first and only then set the handoff** — the safe
order. The bug lived entirely in the order *production* uses. And `MainDispatcherRule`'s
`UnconfinedTestDispatcher` default is the most forgiving scheduling available; under it, the
difference is invisible.

**Enforcement:** an audit at the end of every phase — `grep` for `init {` blocks containing
`.collect`/`launchIn` in ViewModel classes, and check each against the rule's test list. (Sprint
1.7.1 already ran this audit once and found no other instances; the rule is to keep it true, not to
find today's bugs.) Additionally: any `MutableStateFlow` declared *below* an `init` block in a
ViewModel is a **lint-level finding** — that declaration-order hazard is the exact mechanism of the
1.7.0 NPE.

### Failure policy

**A red `ios-release-smoke` blocks the tag.** No "we know what that is." The whole point of the
three parts is that each one replays a failure this project has already shipped over a green suite.

---

## 9. The honest verification cost

### Hardware

| Item | Why | Note |
|---|---|---|
| **≥1 physical iPhone** | 53 parity rows are T4-only. Simulators close none of them | The gesture rig (Gate 0, R5) needs one **before** the $99 enrolment — Xcode free provisioning runs a build on your own device for 7 days |
| **A second, older/smaller iPhone** | R8: one-screen-fit budgets were tuned on a Pixel 7 Pro and are invalid here. The smallest supported display is the worst case | Strongly recommended, not strictly required |
| **A Mac with Xcode** | ~17 GB download, ~40 GB installed, before `~/.konan` and DerivedData. The Mac is at 94% used | Free ~50 GB first |
| **macOS CI minutes** | GitHub Free = 200 macOS minutes/month at the 10× multiplier — **less than one release run** | Owner decision, §7 #9 of the approach doc |

### Human time — the numbers that are not engineer-parallelizable

| Activity | First time | Recurring | Notes |
|---|---|---|---|
| **First full device pass** | **6–10 focused hours over ≥2 sessions** | — | Two sessions minimum because reminder rows (REM-02/06/15) require **overnight waits** — a reminder that fires tomorrow morning cannot be verified this afternoon |
| **Per-release device pass** | — | **1.5–3 hours** | Shrinks only if `ios-release-smoke` is actually built; without it, every release pays the full pass |
| **First VoiceOver pass** | **3–4 hours** | ~1 hour | See the warning below |
| **Mutation re-kill sweep** | 0.5–1 engineer-week per phase | — | Spread, never batched |
| **First App Store submission** | **a day or more**, plus a rejection round trip | — | D-PORT-8: assume manual. `RELEASING.md` records the Play equivalent — the API could not create the first release on an empty track |

**The VoiceOver warning, stated plainly because it keeps getting read as an afternoon:** driving
VoiceOver competently is a skill, not a checkbox. Someone who has not used it before will spend the
first two hours learning the rotor, the gesture set and what "correct" even sounds like, and will
then produce a pass that says "it works" — which is exactly the false-green this document exists to
prevent. **If nobody on the team can drive VoiceOver competently, that is a hiring or consulting
line item, not an afternoon.** Parity rows A11Y-08 and A11Y-09 stay `UNVERIFIED` until someone
qualified drives them, and A11Y-03 (`Role.Button` making VoiceOver say "button" after every verse of
scripture) is an **owner decision**, not an engineering one.

### The three axes, restated as a cost

| Axis | Automated coverage | Closes only at |
|---|---|---|
| Simulator vs device | none | T4 |
| Debug vs release | none | T4 |
| Host arch vs arm64 | none | T4 |

---

## 10. The structural warning

**Android's device-pass backlog was clearable *because* 940 automated tests cleared everything else
first.** The owner's 1.6.0 device pass was a short, targeted session — the partial-tick colour, the
long-press-in-a-pager gesture, a 6-card layout — precisely because the automated suite had already
answered every question that could be answered without glass. The device pass was the *residue*.

**On iOS, day one, the automated suite covers less while the unprovable list is longer. The ratio
inverts.**

Concretely, from the two documents together:

- Of 187 parity rows, **53 (30%) are T4-only** — closable only by a human with a release build on a
  physical device.
- **98 of 940 tests do not travel** to `commonTest` at all, and **≈80–90 iOS tests must be written
  from scratch** with no Android original to port from — i.e. a meaningful slice of the iOS suite has
  never been reviewed against a working reference implementation.
- **Three axes of divergence** between what CI runs and what ships, versus Android's one — and
  Android's one axis still shipped a P0 (1.7.0) and a P1 (1.8.1), both reproducible **only** on a
  release build.
- `AccessibilityGateTest` **will go green on iOS and prove strictly less there.** It pins the input
  to Compose Multiplatform's UIAccessibility bridge, which is not at SwiftUI parity. It is the most
  likely single source of a false-green claim in the whole program, which is why parity §4 rule 3
  names it explicitly as non-evidence.

**What follows from this, and it should be said to the owner in these words:** the iOS device pass
is not a smaller version of the Android one. It is a **larger** one, run by the same person, on a
platform where fewer questions have been pre-answered, and it recurs at **every release** rather
than occasionally. Budgeting it as "the Android device pass, but for iPhone" will under-resource it
by a factor of two or more, and the first symptom will not be a missed bug — it will be a row in
`parity-matrix.md` quietly marked green on T3 evidence because the T4 session never got scheduled.

That is the failure mode. The tier rule in parity §2 and the evidence rule in parity §4 exist to
make it *visible* when it happens. They cannot prevent it. Only scheduled owner hours can.

---

## 11. Sequencing summary

| Phase | Test work | Gate |
|---|---|---|
| **Gate 0** | Nothing. Spikes only — but the gesture rig result is recorded against parity **SEL-01** as the first real evidence in the matrix | R5 pass/fail |
| **Phase 1** (Android-only, 3 Play releases) | The `java.time` → `kotlinx-datetime` mutation sweep (§7 item 1). The `MainDispatcherRule` redesign (§4) and its fallout triage. Koin `checkModules()` + the ViewModel-init rule (§8 part 3). PG-1/PG-2/PG-3 persistence fixtures | Each release's own gate, per D-PORT-7 |
| **Phase 2** (core → iOS) | Category A ports first, then B incrementally. The four plan gates → `commonTest`. **GATE-07 and GATE-08 written, and GATE-08 proven to fail** | Tier-1 gates green on iOS targets; GATE-07/09 green on simulator |
| **Phase 3** (UI + shell) | Category C: 201 tests → `runComposeUiTest`. The ~19 locale-formatted pins split (§5) | Every screen renders; **the reader gesture pass on hardware** |
| **Phase 4** (platform + delivery) | ≈80–90 net-new iOS tests. `ios-release-smoke` built and wired | A tagged commit reaches TestFlight with no local step |
| **Phase 5** (hardening) | Full device pass, VoiceOver pass, mutation re-kill sweep completed | **Parity matrix clean** — no `DEFECT`, no shipping-feature row `UNVERIFIED` at T4 |
