# p2-04 — Move `domain/`, `core/` and `bible/domain/` (54 files) into `shared/domain`

> **Assignee:** Senior Shared-Core Engineer
> **Release:** 1.11.0 · **Merge order:** Tranche A, after `p2-03`. **Parallel with `p2-05`** —
> disjoint write sets.
> **Inherits:** [`p2-00-overview.md`](p2-00-overview.md) rules R1–R9.

---

## Objective

Move 54 files — `domain/` (39), `bible/domain/` (13), `core/` (2) — into `shared/domain`, together
with their tests into `shared/domain/src/commonTest`, converting JUnit 4 → `kotlin.test` as they
land.

**This should be the least eventful task in the port**, and if it is not, that is the finding.

---

## Context

**These 54 files already have zero `android.*` and zero `androidx.*` imports.** That was verified
across the whole tree and it is not luck — it is the accumulated product of 22 sprints of
"one home, no drift" discipline. `p1-02` already removed their `java.time` imports. So by the time
this task runs, they should compile in `commonMain` essentially as-is.

**The interesting content here is not the move. It is what moves with it**, because this is the
project's actual product:

- **`DayCompletionClassifier`** — THE truth table (D-S11-1, D-ALT-6). Every completion, streak,
  strip and picker dot flows through it. R-STREAK-5 says it is **never re-derived**, and a
  mutation-pinned test exists specifically to catch re-derivation.
- **`ReadingSegments.segmentsOf`** (D-SEG-1) — delegates to `bible/domain/ConsecutiveChapterRuns`
  so that card boundaries and external-URL grouping **cannot drift**. A "drifted second grouper"
  mutation is pinned in **both** directions.
- **`SegmentCheckPolicy`** (D-SEG-4/5) — the partial-check transition table, where **N == 1 is
  deliberately not a special case.**
- **`ReadingFormatter`** — including `singularizeBookName` (D-UI-2), the one home for the
  Psalm-singular rule that both the Schedule and the reader call so they cannot disagree.
- **`GlobalChapterIndex` / `ReadingPagerIndex`** — carrying the sprint-00P P0 fix (83 of 365
  Chronological days opened Genesis 1) and pinned field-by-field against `ChapterNavigator`.
- **`VerseId` / `ReferenceResolver` / `PortionVerseBridge`** — including the **verse ∈ [0, 999]**
  invariant (D-V3-9) whose `require(verse >= 1)` regression is mutation-pinned by a Psalm-title test.
- **`ProviderUrlBuilder`** — four URL shapes, live-verified 134/134 and 132/132, now on the
  in-house percent-encoder from `p1-03` (ADR-0014 A1).
- **`VerseClipboardFormatter`** — the citation format, which delegates to
  `ReadingFormatter.singularizeBookName` and is mutation-proven to do so rather than re-derive.

**Every one is mutation-pinned and most came from owner feedback.** A move that quietly loses one
of those pins is the single worst outcome available in this task, and it would not show up as a
failure — it would show up as a slightly smaller test count that nobody questioned.

---

## Contract

### 1. Move, do not edit (rule R5)

`git mv`, then fix package declarations and imports. **A file that changes content in the commit
that changes its path is unreviewable.** If a file genuinely needs a change, split the commit and
say why.

### 2. Tests move to `commonTest`, converting JUnit 4 → `kotlin.test`

They are already assertk (Phase 1 rules R4 / `p1-05`), so only the test-framework annotations
change: `org.junit.Test` → `kotlin.test.Test`, `@Before` → `@BeforeTest`, `@Rule` → restructured.

**Any test that cannot move to `commonTest` stays in `app/src/test/` and is listed with its
reason.** A Robolectric-bound domain test would be surprising and worth a comment; do not force it.

### 3. `ProviderUrlBuilder` and the boundary

It lands in `shared/domain`, which **forbids Ktor** (ADR-0001, and ADR-0014 A1 exists precisely
because of this). It must be on `p1-03`'s in-house `PercentEncoder`. If it still imports
`io.ktor`, `p1-03` is incomplete — **stop and escalate**, do not work around it.

### 4. The `:app` side

`:app` keeps importing these types from their new package. Import rewrites in `:app` are in your
write set **only** where the sole change is the import line.

---

## Acceptance criteria

1. **54 files** live under `shared/domain/src/commonMain`. State the exact count; if it is not 54,
   explain every difference.
2. `app/src/main/kotlin/.../{domain,core}/` and `.../bible/domain/` **no longer exist.**
3. The `p2-02` boundary check is green: **zero** `java.*`, `android.*`, `androidx.*`, `okio.*`,
   `io.ktor.*`, `androidx.room.*`, `androidx.datastore.*`, `androidx.compose.*` in
   `shared/domain/src/commonMain`.
4. **Test count is unchanged.** State before/after. **A dropped test is a failed task.**
5. **The mutation pins listed in Context are re-verified, not assumed.** Pick **five** and re-run
   them, restoring byte-identically:
   - `DayCompletionClassifier` — bypass it with a local re-derivation → the Feb-29 strip test reds
   - `ReadingSegments` — drift the grouper from `ConsecutiveChapterRuns` → reds in both directions
   - `VerseId` — reinstate `require(verse >= 1)` → the verse-0 Psalm-title test reds
   - `ReadingFormatter` — disable `singularizeBookName` → **both** the reader's singular pins **and**
     the Schedule's `ReadingFormatter` pins red, proving the single source of truth
   - `GlobalChapterIndex` — book-boundary off-by-one → the adjacency pins red
   **Quote each failure.** This is the acceptance criterion that proves the pins survived the move.
6. `git log --follow` works on at least `DayCompletionClassifier.kt`, `ReadingFormatter.kt` and
   `ProviderUrlBuilder.kt`.
7. `grep -rn "io.ktor" shared/domain/` returns **nothing**.
8. `ProviderUrlBuilderTest` passes with **zero** modified expectations.
9. Full pipeline green; **Kover ≥ the current floor (~96% on domain)** — and confirm the new module
   is actually inside the Kover report. **A module outside Kover reports nothing and looks perfect.**
10. **The six data gates untouched: 11 / 10 / 8 / 6 / 18 / 5.** They are still in `app/src/test/`
    at this point; `p2-08` moves them.
11. **R8 device smoke:** the shared domain is now behind an R8 module boundary that did not exist
    before. Cold launch; tap a reading (segmentation + the pager index); open the reader and swipe
    across a book boundary; long-press and Copy (the citation formatter); check the stats panel
    (the classifier); switch plan.

---

## Boundaries / write set

**Yours:**
- `shared/domain/src/commonMain/**` and `shared/domain/src/commonTest/**` (created by `git mv`)
- The `app/src/main/kotlin/.../{domain,core}/` and `.../bible/domain/` trees (emptied)
- The corresponding `app/src/test/` trees (emptied)
- Import-only edits elsewhere in `app/src/`

**Not yours:**
- `data/`, `bible/data/`, `di/` — **`p2-05`, `p2-06`, `p2-07`.**
- `ui/`, `bible/ui/`, `widget/` — Phase 3, except import-only edits.
- `shared/platform/**` — **Staff.**
- Any `build.gradle.kts` — **Build & Release.**
- **Any behaviour.** Not one line of logic changes.

---

## Escalation triggers

- **A domain file will not compile in `commonMain`** → **Staff**, blocking. Name the import. It
  means `p1-02`/`p1-03` missed something, or a platform dependency was hiding.
- **A mutation no longer kills its test** → **Staff**, blocking, immediately. A pin that stopped
  pinning during a file move is the worst outcome available here, and it is silent.
- **A test cannot move to `commonTest`** → **Staff**, non-blocking. Leaving it in `androidUnitTest`
  is an acceptable answer; **losing it is not.**
- **The file count is not 54** → **Staff**. Either the inventory is stale or something was missed.
- **`ProviderUrlBuilder` still imports Ktor** → **Staff**, blocking. `p1-03` is incomplete.
