# Gate 0 · Minor spikes — four small questions that each unblock a Phase 1 or Phase 2 brief

> **Brief type:** Gate 0 spike. Timeboxed, throwaway, output is **answers**, not code.
> **Open questions are the point of this brief.**
>
> **Assignee:** Senior Shared-Core Engineer (M1–M3) · Build & Release Engineer (M4, and the
> appendix)
> **Merge-order position:** **Gate 0, item 4 of 4. Fully parallel with the other three.**
> **Timebox: 1 day total across all four**, and M4 is the one worth spending most of it on.
> **Decides:** ADR-0009 (M1, M3), the shape of `p1-07` (M2), ADR-0011 (M4).

None of these can stop the port. Each of them, left open, makes a Phase 1 or Phase 2 brief
**unclosable** — and a brief for an intermediate engineer that still contains an open question is
a briefing defect, not an agent failure. That is why they are Gate 0 and not "we'll find out."

---

# M1 — Is `kotlinx.datetime.YearMonth` adequate? (⟦VERIFY⟧ V3)

**Unblocks:** `p1-02-kotlinx-datetime-sweep.md` · **Decides:** ADR-0009 §3 · **Timebox: 2 hours**

## Objective

Determine whether `kotlinx.datetime.YearMonth` (added in kotlinx-datetime **0.7.0**) carries
everything the **four** current `java.time.YearMonth` call sites need — or whether we write a
~40-line domain type instead.

## Context — the exact usage, so this is a checklist and not a survey

Four files, verified against `main`:

| File | Usage |
|---|---|
| `domain/GetMonthCompletionUseCase.kt:37` | `operator fun invoke(month: YearMonth): Flow<Map<LocalDate, DayCompletion>>` — **it is in a public domain signature** |
| `ui/day/DayReadingsViewModel.kt:218,221` | `mutableMapOf<YearMonth, StateFlow<…>>` — used as a **map key**, so `equals`/`hashCode` must be sane |
| `ui/day/DayReadingsScreen.kt:131` | `(YearMonth) -> StateFlow<…>` parameter |
| `ui/datepicker/DayDatePickerDialog.kt` | `YearMonth.from(date)` (:87), `initialMonth.plusMonths(n)` (:319), and the grid needs first-day / length-of-month |

So the required surface is exactly: **construct from a `LocalDate`**, **`atDay(n)`**,
**length of month**, **add/subtract months (signed, and across year boundaries)**, **`equals` /
`hashCode` / `compareTo`**.

The month arithmetic must cross years cleanly: sprint 21 (D-S21-1) deliberately un-anchored the
picker from a single year and rides months on a `HorizontalPager` with a **±3,000-month window**,
so `plusMonths(-1500)` from any starting month is a real call.

## Contract

A scratch Kotlin file exercising each of the six operations above, plus a pinned check that
`YearMonth.from(2026-12-15).plusMonths(1)` is `2027-01` and `.plusMonths(-1500)` lands where
`java.time` puts it. Report per-operation: **present / absent / different name**.

## What answer would reshape the work

| Outcome | Consequence |
|---|---|
| All six present | Use `kotlinx.datetime.YearMonth`. `p1-02` is a rename. |
| Any absent or awkward | **Do not fight it — write `shared/domain/PlanMonth`, ~40 lines.** A month-of-year with day arithmetic is genuinely trivial, and a type we own is *preferable* to leaking a partial library type through `GetMonthCompletionUseCase`'s public signature. The picker's `monthForPage` already does its own offset arithmetic and is unaffected either way. This is a **cheap** outcome, not a bad one. |
| The type exists but is `@ExperimentalTime`/opt-in | Report the exact opt-in annotation. Staff decides whether an opt-in in `shared/domain` is acceptable. |

## Escalation triggers

- The version Build & Release selected is **below 0.7.0** → **Build & Release**, non-blocking.
- You start extending `PlanMonth` past ~60 lines → **Staff**. That is a signal the boundary is
  wrong, not that the class needs more methods.

---

# M2 — Does the Compose Multiplatform ViewModel support `SavedStateHandle`?

**Unblocks:** `p1-07-koin-migration.md` (its `koinViewModel()` acceptance criterion) ·
**Timebox: 2 hours**

## Objective

Confirm that `androidx.lifecycle` ViewModel + `SavedStateHandle` work in common code at the
version Build & Release has selected, and that whatever ViewModel-resolution helper the DI
decision brings (`koinViewModel()`) can supply one.

## Context

Exactly **one** ViewModel depends on it, and its dependency is real user-facing behaviour:

```kotlin
// bible/ui/reader/ReaderViewModel.kt:68
private val savedStateHandle: SavedStateHandle,
```

It stores `reader_book_no` / `reader_chapter` — **D-V3-13, the reader's in-session last-read
position.** Lose it and the reader silently reopens at Genesis 1 instead of where the user was.
That is a **user-visible regression that no compile error will announce**, which is exactly why it
is a Gate 0 question rather than a Phase 3 discovery.

Note the shape of the risk: this is not "does the API exist" (it does — ViewModel and SavedState
are multiplatform). It is "does it still work once the DI framework is doing the constructing."

## Contract

A minimal CMP scratch app with one ViewModel taking a `SavedStateHandle`, resolved through
`koinViewModel()`. Write a value, force the platform's state-restoration path, read it back.
Report **works / does not work / works only on Android**.

## What answer would reshape the work

| Outcome | Consequence |
|---|---|
| Works on both | `p1-07` proceeds as written. |
| Works on Android only | **Not fatal, and possibly not even a problem.** `SavedStateHandle` exists to survive process death and configuration change; iOS has no configuration change. If iOS gives no restoration, the reader opens at the last-read chapter within a session and at Genesis 1 after a cold start — **which is what D-V3-13 already specifies** ("in-session"). Record it in `docs/parity-matrix.md` and move on. |
| Does not work with the DI helper at all | **Reshapes `p1-07`.** Options: a custom ViewModel factory, or moving the reader's last-read position into the settings store. **Staff decides** — the second option changes persistence semantics and is not the implementer's call. |

## Escalation triggers

- The DI helper cannot supply a `SavedStateHandle` → **Staff**, blocking `p1-07`.
- You find yourself proposing to persist `reader_book_no` in DataStore → **Staff**. That is a
  behaviour change (in-session → permanent) and needs an owner decision, not a workaround.

---

# M3 — Is `LocalDate.toEpochDays()` identical to `java.time.LocalDate.toEpochDay()`?

**Unblocks:** `p1-02-kotlinx-datetime-sweep.md` · **Decides:** ADR-0009 §1 · **Timebox: 1 hour**

## Objective

Prove, over a pinned set of dates, that `kotlinx.datetime.LocalDate.toEpochDays()` returns the
same number as `java.time.LocalDate.toEpochDay()` — and that the inverse round-trips.

## Context — why an hour of arithmetic is worth its own spike

**Every progress row a shipped user owns is keyed on that number.**

```sql
PRIMARY KEY(plan_id, dateEpochDay, stream)
```

Nineteen call sites convert in and out (`data/progress/ProgressRepositoryImpl.kt` ×10,
`data/prefs/SettingsRepositoryImpl.kt` ×2 for the tracking-start date,
`data/prefs/PartialReadingRepositoryImpl.kt` ×2, `domain/GetPartialSegmentsUseCase.kt` ×2,
`ui/day/DayReadingsScreen.kt:69` for the day-pager page index).

If the two functions disagree by even one day, then after the port **every user's reading history
silently shifts by a day.** No crash, no exception, no red test unless someone wrote this one.
A settings key would be worse still: `TRACKING_START_EPOCH_DAY_KEY` round-trips through the same
conversion, so a user's tracking-start date would move too.

They *should* both be days since 1970-01-01. "Should" is not a gate.

## Contract

A JVM scratch test comparing both functions over, at minimum:

- `1970-01-01` (0) and `1969-12-31` (−1) — **the sign boundary**
- `2000-02-29`, `2024-02-29` — **leap days**, and Feb 29 is a first-class case in this product
- `1900-03-01` — the **non-leap century** (1900 is not a leap year; 2000 is)
- `2026-08-08` (today), `2026-12-31` / `2027-01-01` — the **year boundary** the day pager crosses
- A **pre-1970 spread**: `1900-01-01`, `1955-06-15`
- The **exact endpoints of every bundled plan year** the app can display

And the inverse: `fromEpochDays(toEpochDays(d)) == d` for all of the above.

## What answer would reshape the work

| Outcome | Consequence |
|---|---|
| Identical for every date | Proceed. **Keep this test.** It is not scaffolding — it becomes a permanent pin in `commonTest`, exactly as ADR-0009 requires. |
| Differs anywhere | **STOP and escalate to Staff.** Do not add an offset to "fix" it. An offset in the conversion is a silent data corruption waiting for the one code path that forgot it. |
| The kotlinx function is named differently or returns `Int` vs `Long` | Report the exact signature. `dateEpochDay` is stored as SQLite `INTEGER` (64-bit) and Room maps it to `Long`; a narrowing to `Int` must be flagged, not quietly widened at the call site. |

## Escalation triggers

- Any mismatch → **Staff**, blocking.
- A type change (`Long` → `Int`) → **Staff**, non-blocking but must be recorded before `p1-02`.

---

# M4 — Does the Android `assets.srcDir` redirect preserve `planAssetsDir` **and** its `inputs.dir` up-to-date declaration?

**Unblocks:** `p2-01-asset-move.md` · **Decides:** ADR-0011 · **Timebox: half a day** —
**spend the time here.**

## Objective

Prove that after the assets move to a shared directory, **both** halves of the gate wiring still
work:

1. the `planAssetsDir` system property resolves to the new location and all six data-verification
   gates still read the exact files that ship; **and**
2. the `inputs.dir` up-to-date declaration still causes the test task to **re-run** when an asset
   changes.

## Context — and part 2 is a bug this project has already paid for

`app/build.gradle.kts:90-118` carries both halves, and the comment on the second is the record of
the lesson:

```kotlin
it.systemProperty(
    "planAssetsDir",
    layout.projectDirectory.dir("src/main/assets").asFile.absolutePath,
)
// Sprint 1 lesson: declare the asset dir as a test input, otherwise edits to a bundled
// asset are silently skipped as UP-TO-DATE and the gate never re-runs.
it.inputs
    .dir(layout.projectDirectory.dir("src/main/assets"))
    .withPropertyName("planAssets")
    .withPathSensitivity(PathSensitivity.RELATIVE)
```

**Half of this spike is about a comment.** The `systemProperty` half fails loudly — a wrong path
throws `FileNotFoundException` and the gates go red. The `inputs.dir` half fails **silently**: the
gates pass because Gradle never runs them, and you get a green build over corrupted core IP.

Six gates depend on this root: `ReadingPlanVerificationTest` (11), `McheynePlanVerificationTest`
(10), `ChronologicalPlanVerificationTest` (8), `PlanSegmentGateTest` (6),
`BibleTextVerificationTest` (18), `BibleDatabaseRoomOpenTest` (5). The `assets` root covers **both**
the plan assets and `bible/bible.db`, deliberately — one root, not two.

Note that `app/build.gradle.kts` already does this exact trick for the Room schemas
(`getByName("debug").assets.srcDir(layout.projectDirectory.dir("schemas"))`, line 36), and that
wiring is verified absent from the release AAB and present in the debug APK. So the mechanism is
known-good in this build; what is unproven is the *relative-path* redirect out of `app/`.

## Contract

On a scratch branch:

1. Move `app/src/main/assets/` → `shared/assets/` (git mv; **do not copy** — the one-copy rule).
2. Add `android.sourceSets["main"].assets.srcDir("../shared/assets")`.
3. Repoint `planAssetsDir` and **both** `inputs.dir` declarations.
4. Run `./gradlew testDebugUnitTest` — **all six gates green.**
5. **The silent-failure probe, which is the actual deliverable:** run the tests again (expect
   `UP-TO-DATE`), then **corrupt one byte** of `shared/assets/plans/bible_companion/plan.json` —
   e.g. change one chapter number — and run again **without** `--rerun-tasks`.
   **The task must re-run and the gate must go RED.**
   If it reports `UP-TO-DATE`, the `inputs.dir` wiring is broken and the port has silently
   disarmed the project's core-IP protection.
6. Restore the byte; confirm green.
7. Confirm `./gradlew assembleDebug` still packages the assets at the same in-APK paths — unzip
   the APK and list `assets/plans/` and `assets/bible/`. **The runtime path must not change**;
   `PlanAssetSource` opens `plans/mcheyne/plan.json` and must continue to.

## What answer would reshape the work

| Outcome | Consequence |
|---|---|
| Both halves work; APK layout unchanged | ADR-0011 confirmed. `p2-01` proceeds. |
| `planAssetsDir` works, `inputs.dir` does not re-run on change | **Blocking.** Find the fix (absolute path via `rootProject.layout`, or an explicit `outputs.upToDateWhen { false }` as a last resort) **before** `p2-01`. Do not ship a green build whose gates are asleep. |
| The relative `srcDir("../shared/assets")` does not resolve | Try `rootProject.layout.projectDirectory.dir("shared/assets")`. If nothing works, escalate — the fallback (leave assets under `app/` and have iOS reference that path) is explicitly **rejected** by ADR-0011 as sloppy, and reversing that is Staff's call, not a workaround. |
| APK asset paths change | **Blocking.** Every `PlanAssetSource` path and the `createFromAsset("bible/bible.db")` string depend on them. |

## Escalation triggers

- The corrupt-a-byte probe reports `UP-TO-DATE` and you cannot fix it in the timebox →
  **Staff + Build & Release**, blocking `p2-01`.
- APK asset layout changes → **Staff**, blocking.
- You are tempted to skip step 5 because steps 1–4 passed → **that step is the spike.** Steps 1–4
  are the part that fails loudly and therefore the part that does not need proving.

---

# Appendix — two dependency questions to close while you are here

**Assignee: Build & Release.** Neither can reshape the port; both make a Phase 2/3 brief closable.

- **~~⟦VERIFY⟧ V5 — a CMP-resolvable `material-icons-core`.~~ CLOSED 2026-08-08 — do not run.**
  Answered by `p0-build-foundation.md` §3.B.7 before this brief was dispatched: the JetBrains fork
  is **frozen at 1.7.3 with no `-android` and no `-iosarm64` variant** (both 404), so there is no
  resolvable coordinate and the dependency is **dropped, not ported**. The glyphs are vendored —
  the fallback this project has taken before (`ic_bible_book.xml`; note `ic_stats.xml` is **not** a
  live precedent, it was deleted in sprint 15).

  **The count is 9** — not the "~6" this brief originally guessed, and **not the 10 briefly
  recorded earlier on 2026-08-08**:

  | | Glyph |
  |---|---|
  | 1–3 (`AutoMirrored.Filled`) | `ArrowBack` · `KeyboardArrowLeft` · `KeyboardArrowRight` |
  | 4–9 (`Filled`) | `ArrowDropDown` · `Check` · `Close` · `DateRange` · `Edit` · `Settings` |

  > **How the "10" happened — recorded so it is not repeated.** A `grep` for `Icons\.` over
  > `app/src/main/kotlin` matched **comment text**, picking up `Icons.Filled.ContentCopy`, which
  > occurs in exactly two places (`bible/ui/reader/VerseSelectionBar.kt:31` and
  > `ui/AccessibilityGateTest.kt:468`) — **both prose, and both stating that the glyph does not
  > exist in the frozen 1.7.8.** That absence is precisely *why* Copy is a visible `TextButton`
  > word rather than an icon (sprint 00Q, a11y-gate-pinned). Vendoring it would have shipped dead
  > code and invited someone to reverse a deliberate accessibility decision. Re-verified by
  > stripping comments before extracting: **9**. Caught by Sr Shared-UI reading the call sites,
  > not by the grep — §11 rule 0 working as intended.

  The vendoring is a **1.12.0 task owned by Sr Shared-UI**
  ([rel-1120-vendor-icons.md](rel-1120-vendor-icons.md)), not a spike. **If any document still
  says "ten", it is stale — 9 is the verified number.**
- **⟦VERIFY⟧ V6 — is `androidx.navigation:navigation-testing` multiplatform?** It backs
  `NavRegressionTest`, which is the only automated proof that both tabs are reachable and that
  per-tab back-stacks survive a switch. If it is Android-only, `NavRegressionTest` stays in
  `androidUnitTest` — **acceptable**, and it should be recorded rather than worked around.

---

## Results

*(To be completed by the assignees.)*

| # | Question | Answer | Consequence chosen |
|---|---|---|---|
| M1 | `kotlinx.datetime.YearMonth` adequate? | | library type / `PlanMonth` |
| M2 | `SavedStateHandle` via `koinViewModel()`? | | |
| M3 | `toEpochDays()` == `toEpochDay()`? | | |
| M4a | `planAssetsDir` resolves after the move? | | |
| M4b | **Corrupt-a-byte probe re-runs the gate?** | | |
| M4c | APK asset paths unchanged? | | |
| V5 | CMP `material-icons-core` coordinate | | resolve / vendor |
| V6 | `navigation-testing` multiplatform? | | common / androidUnitTest |
