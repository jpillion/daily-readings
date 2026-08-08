# ADR-0010 — The **six** data-verification gates in a multiplatform test suite

**Status:** Draft, pending owner sign-off · **Date:** 2026-08-08 · **Author:** Staff / Port Architect
· **Amended:** 2026-08-08 — see [Amendment A1](#amendment-a1--six-gates-not-five-and-a-corrected-justification-for-tier-1)

> **Title corrected from "five" to "six."** `PlanSegmentGateTest` (6 assertions, 0 violations
> across 2,920 portions in all three plans) postdates the delivery brief that said five. It is a
> gate by every meaningful criterion. **Report six.**
>
> **The Tier 1 justification in the body is wrong.** The conclusion is right; the reason is not.
> Amendment A1 replaces it. Do not quote the body's reasoning.

## Context

CLAUDE.md calls the data-verification gates the project's core IP protection, and the delivery
brief makes it a hard rule: *"a release that skips the gates is not a release."* They are also the
tests that have repeatedly caught real defects — five text corrections in the KJV corpus, the
Chronological coverage invariant, the M'Cheyne second-source reconciliation, the sprint-00F
Room-open P0.

Current mechanics:

| Gate | Assertions | Mechanism |
|---|---|---|
| `ReadingPlanVerificationTest` (Bible Companion) | 11 | reads the **source-tree** asset via the `planAssetsDir` system property; day-by-day equality vs an independent second-source fixture |
| `McheynePlanVerificationTest` | 10 | same, + verse-aware coverage invariant |
| `ChronologicalPlanVerificationTest` | 8 | same, single-source structural gate (D-ALT-24) |
| `PlanSegmentGateTest` | 6 | same; 0 segmentation violations across 2,920 portions |
| `BibleTextVerificationTest` | 18 | **`org.xerial:sqlite-jdbc`** — `DriverManager.getConnection("jdbc:sqlite:…")` |
| `BibleDatabaseRoomOpenTest` | 5 | **Robolectric** + real Android SQLite + `createFromAsset` |

**There are six.** The delivery brief says five; `PlanSegmentGateTest` (sprint 00P) is the sixth
and is not optional — it proves the D-SEG-2 invariant that makes the sprint-00P P0 non-recurring.
Every count in this document and downstream of it is **six**.

Two blockers to a naive "move them all to `commonTest`":

1. **All 115 test files use JUnit 4 + Truth.** Neither works in `commonTest`.
2. **sqlite-jdbc is JVM-only** and **Robolectric is Android-only**.

## Decision

**Four tiers, chosen by what each gate is actually asking.** The distinction that resolves this
is between an *asset-correctness* gate (is the committed data right?) and an *integration* gate
(does this platform's storage layer open it?).

### Tier 1 — `commonTest`: the four plan gates (35 assertions)

`ReadingPlanVerificationTest`, `McheynePlanVerificationTest`, `ChronologicalPlanVerificationTest`,
`PlanSegmentGateTest` move to `shared/data/src/commonTest`. They are JSON parsing plus
arithmetic; there is nothing platform-specific in them.

Two mechanical changes:
- JUnit 4 + Truth → `kotlin.test` (`assertEquals`, `assertTrue`, `fail`).
- The `planAssetsDir` **system property** → a generated constant. A Gradle task emits a tiny
  `TestAssetPaths.kt` into the common test source set with the absolute source-tree path; the
  gates read plan JSON through **okio `FileSystem.SYSTEM`**. This preserves the property that
  makes these gates trustworthy: **they read the exact files that ship, from the source tree**,
  not a packaged copy.

**⚠️ The sentence that follows was wrong and is replaced by Amendment A1.3. It is left here so
the correction is visible rather than quietly overwritten.**

> ~~These then run on every target, which is a genuine gain — a Kotlin/Native JSON or arithmetic
> difference would surface immediately.~~

The gain is real but it is **not** what the original sentence implied to readers downstream, who
took it to mean that running the plan gates on an iOS target proves the assets were packaged into
the app bundle. **It does not.** See A1.3.

### Tier 2 — shared `jvmTest`: `BibleTextVerificationTest` (18 assertions), unchanged

It stays on sqlite-jdbc, in a JVM test source set of the shared module. It asks *"is the committed
`bible.db` the right data?"* — verse counts, superscriptions, second-source equality,
checksum-distinctness, famous-verse pins. **That question is platform-independent and needs
answering once per commit, not once per target.** Running it on a simulator would tell us nothing
new.

**The iOS release pipeline must depend on this task passing** and report it explicitly:
`asset gate (JVM): 18 assertions, passed`. That satisfies the delivery brief's intent — no
silently skipped gate — without pretending JDBC runs on arm64. Anything else is dishonest
reporting, which this project has correctly refused elsewhere.

### Tier 3 — `androidUnitTest`: `BibleDatabaseRoomOpenTest` (5), unchanged, forever

It asks *"does Android's Room actually open the committed asset?"* — inherently per-platform, and
it exists because that exact thing failed in production (sprint-00F).

### Tier 4 — **NEW, required: `iosTest` `BibleDatabaseOpenTest`**

An iOS-side equivalent, opening the bundled asset through whatever mechanism ADR-0007 selects and
reading the same four probes: **Genesis 1:1, John 3:16, John 11:35 ("Jesus wept."), and the
Psalm 3 verse-0 superscription.**

**This is required work, not optional.** It is the only thing that will catch the iOS equivalent
of the sprint-00F P0 — an asset that parses fine on the JVM and fails to open on the device —
before a user does. Its absence on Android is precisely why that P0 shipped.

### The wider suite

The remaining ~109 test files convert to `kotlin.test` opportunistically, module by module, as
their production code moves. Compose UI tests convert from `ui-test-junit4` to CMP's
`runComposeUiTest` in `commonTest`. Robolectric-only tests (29 files) stay in `androidUnitTest`.
**The `AccessibilityGateTest` should become common** — its 48dp-touch-target and spoken-label
assertions matter more on iOS, not less.

## Alternatives rejected

**Move `BibleTextVerificationTest` to `commonTest` by rewriting it against the KMP SQLite
driver.** Genuinely possible — `androidx.sqlite`'s bundled driver runs on Native. Rejected for
now on cost/benefit: 18 careful assertions including SHA-256 checksums (`java.security.MessageDigest`
would also need replacing) rewritten to answer a question whose answer cannot vary by platform.
**Reconsider if ADR-0007 drops Room for the bible DB** — at that point the KMP SQLite driver is
already in `shared/data`, and the rewrite becomes nearly free. Note it as a follow-up rather than
a never.

**Keep the whole suite JVM/Android-only and run no tests on iOS.** Rejected. It would mean the
iOS build has no test gate at all, which fails delivery-brief criterion 2 and abandons the
project's core discipline at exactly the moment a new platform makes it most valuable.

**Rewrite everything to `kotlin.test` in one pass, up front.** Rejected as a big-bang. 115 files
of assertion churn with no behaviour change is a large window in which to introduce a silent
weakening (e.g. an `assertTrue(a == b)` that no longer reports what differed). Convert
incrementally, alongside the code each test covers, so each conversion is reviewed in context.

**Drop `PlanSegmentGateTest` from the "gates" list because the brief says five.** Rejected — it
is a gate by every meaningful criterion (it proves the D-SEG-2 invariant that makes the sprint-00P
P0 non-recurring, over all three real assets). The brief's "five" predates it. Report six.

## Consequences accepted

- **The honest gate count is `common: 35 · jvm: 18 · android: 5 · ios: 10 (both new)`** — see
  Amendment A1.4 (D-PORT-6), which supersedes the earlier "ios: 5" figure now that a second
  required iOS gate exists. Never "all gates run everywhere". Build & Release must report it that
  way. Pre-empt the escalation their brief anticipates ("any data-verification gate cannot run on
  iOS") — this ADR *is* that answer.
- **Tier 4 is new code with no Android counterpart to port from.** Budget it as a real task in
  Phase C, not a footnote.
- The `planAssetsDir` → generated-constant change touches `app/build.gradle.kts`, which is Build &
  Release's exclusive write set. Coordinate; do not edit it from a Core task.
- Truth's failure messages are better than `kotlin.test`'s. Some diagnostic quality is lost on
  conversion. Mitigate on the highest-value gates by writing explicit failure messages
  (`assertTrue(cond) { "day $d: expected …, got …" }`) rather than relying on the assertion
  library — the plan gates already do this via `assertWithMessage` and those messages must survive.
- `kotlinx-serialization` is already in use for plan parsing, so the plan gates need no new
  parsing dependency in `commonTest`. Good.

## Revisit when

- ADR-0007 resolves — if Room is dropped for the bible DB, revisit moving
  `BibleTextVerificationTest` to `commonTest`.
- A sixth or seventh plan is added — the gate pattern is already parameterised per plan and
  should stay that way.
- Any gate is ever proposed to be skipped for a release. The answer is no; the correct response is
  to fix the gate or the data.

---

## Amendment A1 — six gates, not five; and a corrected justification for Tier 1

**Date:** 2026-08-08 · **Author:** Staff / Port Architect
**Records:** D-PORT-5 and **D-PORT-6** from `ios-port-approach.md` §4.3.

### A1.1 The count is six, everywhere

| # | Gate | Assertions |
|---|---|---|
| 1 | `ReadingPlanVerificationTest` (Bible Companion) | 11 |
| 2 | `McheynePlanVerificationTest` | 10 |
| 3 | `ChronologicalPlanVerificationTest` | 8 |
| 4 | **`PlanSegmentGateTest`** | **6** |
| 5 | `BibleTextVerificationTest` | 18 |
| 6 | `BibleDatabaseRoomOpenTest` | 5 |

Every downstream document that says "five data-verification gates" is stale, including
`docs/task-briefs/ios-delivery-pipeline.md`. Correct on sight.

### A1.2 The two NEW iOS gates, so the total is eight gates across four hosts

| # | Gate | Assertions | Host |
|---|---|---|---|
| 7 | **`BibleDatabaseOpenTest`** (new, iOS) | 5 | `iosTest` |
| 8 | **`BundleAssetIntegrityTest`** (new, iOS) | 5 | `iosTest` |

Gate 7 was already required by this ADR (Tier 4). **Gate 8 is new to this amendment** and exists
because of the correction in A1.3.

### A1.3 The correction — running the plan gates on iOS proves **nothing** about packaging

The Verification engineer proposed moving the four plan gates to `commonTest` **because running
them on an iOS target would additionally prove the assets were packaged into the iOS bundle.**

**That justification is false, and it is false because of this ADR's own design.**

The plan gates deliberately read the **source tree** — via the `planAssetsDir` system property
today (`app/build.gradle.kts:97-105`), via a generated absolute-path constant and okio
`FileSystem.SYSTEM` after the port. That is the property that makes them trustworthy:
*they read the exact files that ship, from the source tree*, not a packaged copy.

A Kotlin/Native test running on a **simulator** shares the host filesystem. It can see that
absolute source-tree path and will pass happily **with no asset having entered an app bundle at
all.** On a physical device the path would not resolve — but Kotlin/Native unit tests do not run
on physical devices (the `ios-release-smoke` finding), so that case never arises.

**Shipping the wrong justification into the record would licence a false claim later** — someone
would eventually write "the plan gates run on iOS, therefore the assets are packaged correctly,"
and it would be believed.

**The conclusion — move the four plan gates to `commonTest` — is unchanged and correct.** The
correct justification is narrower:

> Running the plan gates on Kotlin/Native targets proves that **the project's core-IP parsing and
> arithmetic behave identically on Kotlin/Native** — JSON decoding, `Int`/`Long` arithmetic in the
> coverage invariants, string comparison in the second-source equality checks, and the
> verse-tiling maths. A K/N divergence in any of those would surface here and nowhere else.
> That is cheap insurance on the project's most valuable asset. It says nothing about packaging.

### A1.4 Packaging needs its own gate — `BundleAssetIntegrityTest`

ADR-0011 flags a specific, cheap, high-likelihood mistake: iOS bundle resources are **flat by
default** unless the directory is added as a **folder reference** (blue) rather than a **group**
(yellow). Get it wrong and `plans/mcheyne/plan.json` becomes `plan.json`, colliding with two
identically-named files. It presents as a mysterious "wrong plan loaded" bug and it is a
20-minute mistake.

> **`BundleAssetIntegrityTest` (iOS, 5 assertions, release-blocking).** For each of the five
> shipped assets — `plans/registry.json`, the three `plans/<id>/plan.json`, and `bible/bible.db` —
> resolve it from `NSBundle` at its expected **nested** path and assert its SHA-256 against a
> generated constant.
>
> It must resolve by nested path, not by filename. A test that finds `plan.json` anywhere in the
> bundle passes under exactly the defect it exists to catch.

Verification owns the specification of this test. This amendment establishes only that it is
required and why.

### A1.5 D-PORT-6 — the reporting rule, non-negotiable

**The honest ledger is:**

```
common: 35  ·  jvm: 18  ·  android: 5  ·  ios: 10 (both new)
```

Never "all gates run everywhere." This corrects `docs/task-briefs/ios-delivery-pipeline.md`
acceptance criterion 2 ("assertion counts match the Android run"), which is **not achievable as
written** and is an invitation to a false claim.

**Where each tier runs:**

| Tier | Gate(s) | Source set | Runs on |
|---|---|---|---|
| 1 | 4 plan gates (35) | `commonTest` | JVM/Android **every PR**; iOS targets **on the release pipeline only** |
| 2 | `BibleTextVerificationTest` (18) | shared `jvmTest`, sqlite-jdbc, unchanged | JVM only. The iOS release pipeline **depends on the task passing** and reports it explicitly |
| 3 | `BibleDatabaseRoomOpenTest` (5) | `androidUnitTest`, unchanged, forever | Android only |
| 4 | `BibleDatabaseOpenTest` (5) | `iosTest` | iOS. **Release-blocking.** The test that would have caught sprint-00F |
| 5 | `BundleAssetIntegrityTest` (5) | `iosTest` | iOS. **Release-blocking.** |

Tier 1's split answers Build & Release's cost objection — `commonTest` compiles for every target
and runs **free** on the Linux PR runner, while iOS-target execution runs only where a release is
being cut, which is the only place "no silently skipped gate" actually binds. macOS minutes cost
10×; spending them on every PR to re-prove arithmetic is not a good trade, and spending zero on
release runs is not defensible either.
