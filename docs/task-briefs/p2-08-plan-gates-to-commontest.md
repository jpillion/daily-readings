# p2-08 — The four plan gates to `commonTest` (35 assertions), and the honest reporting ledger

> **Assignee:** Verification Engineer
> **Release:** 1.11.0 · **Merge order:** Tranche A, last before the tag.
> **Inherits:** [`p2-00-overview.md`](p2-00-overview.md) rules R1–R9.
> **Preconditions:** `p2-01` (assets moved, probe proven), `p2-05` (data layer moved).
> **Executes:** ADR-0010 **and its Amendment A1**, which you must read — it corrects the
> justification everyone downstream inherited.

---

## Objective

Move the four plan gates — **35 assertions** — into `shared/data/src/commonTest`, so they compile
for every target and run free on the Linux PR runner, and establish the **honest reporting ledger**
that replaces "all gates run everywhere."

---

## Context

### The six gates (there are six, not five)

| Gate | Assertions | Today |
|---|---|---|
| `ReadingPlanVerificationTest` (Bible Companion) | **11** | source-tree read via `planAssetsDir` |
| `McheynePlanVerificationTest` | **10** | same, + the verse-aware coverage invariant |
| `ChronologicalPlanVerificationTest` | **8** | same, single-source structural gate (D-ALT-24) |
| `PlanSegmentGateTest` | **6** | same; 0 violations across 2,920 portions |
| `BibleTextVerificationTest` | 18 | sqlite-jdbc — **stays JVM** |
| `BibleDatabaseRoomOpenTest` | 5 | Robolectric — **stays Android, forever** |

`PlanSegmentGateTest` postdates the delivery brief that said five. It is a gate by every meaningful
criterion — it proves the D-SEG-2 invariant that makes the sprint-00P P0 non-recurring, across all
three real assets. **Report six.**

### The correction you must not undo — ADR-0010 A1.3

The reason originally given for running these on iOS was: *"it additionally proves the assets were
packaged into the iOS bundle."*

**That is false, and it is false because of these gates' own design.** They deliberately read the
**source tree** — that is what makes them trustworthy: *they read the exact files that ship*, not a
packaged copy. A Kotlin/Native test on a **simulator** shares the host filesystem, sees that
absolute path, and passes happily **with no asset in any app bundle.**

**The conclusion — move them to `commonTest` — is right. The reason is different and narrower:**

> Running them on Kotlin/Native proves that **the project's core-IP parsing and arithmetic behave
> identically on Kotlin/Native** — JSON decoding, the `Int`/`Long` arithmetic in the coverage
> invariants, string comparison in the second-source equality checks, and the Ps-119 verse-tiling
> maths. A K/N divergence would surface here and nowhere else. It says nothing about packaging.

Packaging needs a **separate** gate — `BundleAssetIntegrityTest`, which you specify here and which
`p2-09` implements.

---

## Contract

### 1. The move

Into `shared/data/src/commonTest`, with:

- **JUnit 4 → `kotlin.test`.** (Already assertk from Phase 1.)
- **The `planAssetsDir` system property → a generated constant.** A Gradle task emits a tiny
  `TestAssetPaths.kt` into the common test source set carrying the absolute source-tree path; the
  gates read through **okio `FileSystem.SYSTEM`**. This preserves the property that makes them
  trustworthy — they read the exact files that ship, from the source tree.
- Fixtures (`plan_verify.json`, `plans/mcheyne/plan_verify.json`, `book_catalog.csv`,
  `bible/kjv_verse_counts.csv`) move to the common test resources and are read the same way.

**The generated-constant work touches `app/build.gradle.kts` / the shared module's build file,
which is Build & Release's exclusive write set. Specify it; do not edit it.**

### 2. Preserve the failure messages — this is the substance, not the housekeeping

The plan gates use `assertWithMessage` to produce diagnosable failures:

```
day 173: BC portion [Jas 1, Jas 2] segments to 2 cards, expected 1
```

Under a naive conversion that becomes `expected true, got false`, and the gate is **materially
weaker** even though it is still red at the same moment. **Every message-carrying assertion keeps
its message.** Verify by making each gate fail once and reading the output.

### 3. The tier-1 execution split

| Where | When |
|---|---|
| JVM / Android targets | **every PR** — free on the Linux runner |
| iOS targets | **release pipeline only** (tranche B onward) |

macOS minutes cost **10×**, and GitHub Free on a private repo gives ~200 macOS minutes/month —
**less than one release run.** Spending them on every PR to re-prove arithmetic is a bad trade;
spending zero on release runs is not defensible either. This split is the answer to both.

### 4. Specify `BundleAssetIntegrityTest` (implemented in `p2-09`)

ADR-0011 flags the trap: iOS bundle resources are **flat by default** unless the directory is added
as a **folder reference** (blue) rather than a **group** (yellow). Get it wrong and
`plans/mcheyne/plan.json` becomes `plan.json`, colliding with two identically-named files. It
presents as a mysterious "wrong plan loaded" bug and it is a 20-minute mistake.

> **5 assertions.** For each of `plans/registry.json`, the three `plans/<id>/plan.json`, and
> `bible/bible.db`: resolve it from `NSBundle` **at its expected nested path** and assert SHA-256
> against a generated constant.
>
> **It must resolve by nested path, never by filename.** A test that finds `plan.json` anywhere in
> the bundle **passes under exactly the defect it exists to catch.** Write that sentence into the
> test's KDoc.

The SHA-256 constants are generated by the same build step that generates `TestAssetPaths.kt`, from
the one copy in `shared/assets/`. **Never hand-typed.**

### 5. The reporting ledger — D-PORT-6

```
common: 35  ·  jvm: 18  ·  android: 5  ·  ios: 10 (both new)
```

**Never "all gates run everywhere."** This replaces the delivery brief's acceptance criterion 2
("assertion counts match the Android run"), which is **not achievable as written** and invites a
false claim. Put the ledger in the pipeline output, verbatim.

---

## Acceptance criteria

1. The four plan gates live in `shared/data/src/commonTest`, on `kotlin.test` + assertk, reading
   through okio `FileSystem.SYSTEM`.
2. **Assertion counts unchanged: 11 / 10 / 8 / 6.** State each explicitly.
3. `BibleTextVerificationTest` (18) still passes from `shared/data/src/jvmTest`;
   `BibleDatabaseRoomOpenTest` (5) still passes from `androidUnitTest`. **Neither moves.**
4. **The `planAssetsDir` system property is gone**, replaced by the generated constant.
   `grep -rn "planAssetsDir" .` returns nothing outside historical docs.
5. **The corrupt-a-byte probe still works after the move**, for both a plan JSON and `bible.db`,
   demonstrated with console output and restored. **`p2-01` proved the wiring; you must prove the
   move did not undo it.** If the generated-constant approach broke the `inputs.dir` up-to-date
   declaration, the gates are asleep and the build is green — **this is the criterion that catches
   that.**
6. **All four gates mutation-verified after conversion**, each restored byte-identically, **each
   failure message quoted in the PR** to show the diagnostics survived:
   - `ReadingPlanVerificationTest` — a changed chapter number
   - `McheynePlanVerificationTest` — a dropped Ps-119 verse window
   - `ChronologicalPlanVerificationTest` — a duplicated chapter (breaks exactly-once coverage)
   - `PlanSegmentGateTest` — a portion made non-contiguous
7. **`BundleAssetIntegrityTest` is fully specified** — assertions, resolution mechanism, the
   nested-path requirement, and where the SHA constants come from — in a form `p2-09` can implement
   without a question.
8. **The ledger appears in the pipeline output** in the D-PORT-6 form.
9. Test count unchanged. Zero deletions. State before/after.
10. Full pipeline green, run with `--rerun-tasks`. Report how many test tasks actually executed.
11. **`AccessibilityGateTest` moves toward `commonTest`** where it can — its 48dp-touch-target and
    spoken-label assertions matter **more** on iOS, not less.
    **But record the caveat in `docs/parity-matrix.md`:** it pins the *input* to Compose's
    UIAccessibility bridge, which is not at SwiftUI parity — **it will go green on iOS and prove
    strictly less there.** Do not let its greenness be reported as "iOS accessibility verified."
    Related: **48dp is an Android convention** (Apple's HIG minimum is 44pt); keep 48 as
    deliberately stricter and record that it is deliberate.
12. **No R8 device smoke required** — tests only. Say so explicitly.

---

## Boundaries / write set

**Yours:**
- `shared/data/src/commonTest/**` (created by `git mv`)
- `app/src/test/.../data/plan/**` (emptied)
- Test resources moved to the common test resource set
- The `BundleAssetIntegrityTest` specification (a doc or a KDoc'd stub in `p2-09`'s brief)

**Not yours:**
- **`shared/assets/**`** — read-only. Mutate copies in the probes and restore.
- `app/build.gradle.kts` and the shared build files — **Build & Release** implements the
  generated-constant task. **Specify it.**
- `BibleTextVerificationTest`, `BibleDatabaseRoomOpenTest` — neither moves.
- `docs/parity-matrix.md` — yours as Verification, but note that its content here is a
  **caveat**, not a claim.

---

## Escalation triggers

- **A gate's assertion count changes** → **Staff**, blocking, even if green. A gate that got
  shorter during a move is the failure mode nobody notices.
- **The corrupt-a-byte probe stops working** → **Staff**, blocking. The generated constant broke the
  up-to-date wiring and the gates have gone silent.
- **A failure message is less informative after conversion** → that gate's conversion is not done.
  If it cannot be preserved, escalate to **Staff** rather than accepting a weaker message.
- **okio `FileSystem.SYSTEM` cannot read the source tree on some target** → **Staff**. It is the
  mechanism the whole tier-1 decision rests on.
- **Anyone proposes running the plan gates on iOS "to prove packaging"** → **correct them, citing
  ADR-0010 A1.3.** The claim is false and shipping it into the record would licence a later false
  claim about the shipped app.
