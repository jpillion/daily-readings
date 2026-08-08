# p2-01 — Move the five core-IP assets to `shared/assets/`. One atomic commit. Nothing else in it.

> **Assignee:** Build & Release Engineer (owns `app/build.gradle.kts` and the CI jobs)
> **Release:** 1.11.0 · **Merge order:** Tranche A, **FIRST. Before the module split.**
> **Inherits:** [`p2-00-overview.md`](p2-00-overview.md) rules R1–R9.
> **Preconditions:** 1.10.0 live, vitals clean. **`gate0-minor-spikes.md` M4 answered — including
> M4b, the corrupt-a-byte probe.**
> **Executes:** ADR-0011.

---

## Objective

Move the five bundled data assets out of `app/src/main/assets/` into a shared, platform-neutral
`shared/assets/`, wire Android to read them from there, and **prove the six data-verification gates
still work — including that they still re-run when an asset changes.**

**One atomic commit. No module creation, no code moves, no anything else.**

---

## Context

These five files are the project's core IP. Each is byte-reproducible from a SHA-pinned source by a
script in `tools/`, guarded by a CI job asserting a byte-diff of **zero**, and verified by a gate
test that reads it **from the source tree**:

| Asset | Size | Gate |
|---|---|---|
| `plans/registry.json` | 307 B | `PlanRegistry` + all plan gates |
| `plans/bible_companion/plan.json` | 172 KB | `ReadingPlanVerificationTest` (11) |
| `plans/mcheyne/plan.json` | 203 KB | `McheynePlanVerificationTest` (10) |
| `plans/chronological/plan.json` | 117 KB | `ChronologicalPlanVerificationTest` (8) |
| `bible/bible.db` | **5,599,232 B** | `BibleTextVerificationTest` (18), `BibleDatabaseRoomOpenTest` (5) |

`PlanSegmentGateTest` (6) reads all three plans.

### The two things that can go wrong, and only one of them is loud

**Loud:** `planAssetsDir` points at the wrong place → `FileNotFoundException`, gates red, obvious.

**Silent — and this project has already paid for it:** the `inputs.dir` up-to-date declaration
stops working. Then Gradle marks the test task `UP-TO-DATE`, **the gates never run**, and a
corrupted asset ships behind a green build. The comment at `app/build.gradle.kts:107` is the record
of that lesson:

```kotlin
// Sprint 1 lesson: declare the asset dir as a test input, otherwise edits to a bundled
// asset are silently skipped as UP-TO-DATE and the gate never re-runs.
```

**Half of this task's acceptance criteria are about that second failure.**

### Why the assets must move at all

Android reaches them via `context.assets.open(path)`. **iOS has no `assets/`** — bundled files live
in `NSBundle`. Leaving them under `app/` would make the Android module the owner of shared data, so
the iOS build breaks the moment anyone reorganises `app/`. And **exactly one copy in git** is a hard
rule: this project's core IP is data, and a second copy is a silent drift vector.

---

## Contract

### 1. The move

```bash
git mv app/src/main/assets shared/assets
```

**`git mv`, not copy-then-delete** — the history must follow the files, especially `bible.db`'s.

Resulting layout:

```
shared/assets/
  plans/registry.json
  plans/{bible_companion,mcheyne,chronological}/plan.json
  bible/bible.db
```

### 2. Android wiring

In `app/build.gradle.kts`:

- `android.sourceSets["main"].assets.srcDir(<shared/assets>)` — use whichever form M4 proved
  works; prefer a `rootProject`-anchored path over a `../` relative one.
- Repoint the `planAssetsDir` system property.
- **Repoint the `inputs.dir` declaration** (`withPropertyName("planAssets")`,
  `PathSensitivity.RELATIVE`). **Do not lose it. Do not lose its comment** — the comment is the
  record of why it exists.
- Leave the **debug-only Room schemas** wiring (line 36) alone. Different mechanism, different
  purpose, and it is verified absent from the release AAB (0 entries) and present in the debug APK
  (2). Keep that property.

### 3. The rest of the repository

- **`tools/*.py` output paths** — `build_bible_db.py`, `build_mcheyne_plan.py`,
  `build_chronological_plan.py`, `export_book_catalog.py`, `extract_*.py`. Every one that writes
  into the old location.
- **The CI byte-diff jobs** — `data-rebuild`, `mcheyne-rebuild`, `chronological-rebuild`. Each must
  still re-derive from its SHA-pinned source and assert a byte-diff of **zero**.
  **`data-rebuild` keeps its `LD_PRELOAD` of the self-compiled SQLite 3.43.2 and its assertion that
  the preload took effect.** That pinning cost six weeks of red builds; do not disturb it, and
  **do not move that job to a macOS runner** — `DYLD_INSERT_LIBRARIES` is SIP-blocked.
- `tools/compare_bible_db.py`'s paths.
- `.github/workflows/*` trigger paths — `app/src/main/assets/**` becomes `shared/assets/**`. **The
  delivery brief hard-codes the old path and it will silently stop triggering.**

### 4. The proof — this is the deliverable

1. `./gradlew testDebugUnitTest` → **all six gates green, counts 11 / 10 / 8 / 6 / 18 / 5.**
2. Run again → `UP-TO-DATE`.
3. **The silent-failure probe.** Corrupt one byte of `shared/assets/plans/bible_companion/plan.json`
   (change a chapter number) and run again **without `--rerun-tasks`**.
   **The task must re-run and `ReadingPlanVerificationTest` must go RED.**
   If it reports `UP-TO-DATE`, **stop** — the core-IP protection is disarmed.
4. Restore; confirm green and `git status` clean.
5. Repeat 3–4 for **`bible/bible.db`** on a scratch copy — the same `assets` root covers both, and
   only doing the plan JSON leaves half the wiring unproven.
6. `./gradlew assembleDebug`, unzip the APK, and list `assets/plans/` and `assets/bible/`.
   **The in-APK paths must be byte-for-byte what they were.** `PlanAssetSource` opens
   `plans/mcheyne/plan.json` and `createFromAsset("bible/bible.db")` is a literal string.
7. `./gradlew bundleRelease` → AAB builds, size reported against the **12 MB** gate.
8. All three CI byte-diff jobs green.

---

## Acceptance criteria

1. `find . -name bible.db -not -path './*/build/*'` returns **exactly one path**, under
   `shared/assets/`.
2. `app/src/main/assets/` **does not exist.**
3. All six gates green, **counts 11 / 10 / 8 / 6 / 18 / 5.**
4. **The corrupt-a-byte probe re-runs the gate and goes red — demonstrated for BOTH a plan JSON and
   `bible.db`, with the console output quoted in the PR.** Both restored; `git status` clean.
5. In-APK asset paths **unchanged**, proven by an `unzip -l` listing in the PR.
6. All three byte-diff CI jobs green, `data-rebuild`'s `LD_PRELOAD` assertion intact.
7. Every `.github/workflows/**` trigger path updated; **`grep -rn "app/src/main/assets" .` returns
   nothing** outside historical docs.
8. `git log --follow shared/assets/bible/bible.db` shows the pre-move history.
9. `bundleRelease` clean, AAB size reported.
10. **The commit contains the move and its wiring, and nothing else.** No module creation, no import
    rewrites, no code changes.
11. **R8 device smoke:** install the release build; open the reader (KJV renders from `bible.db`);
    switch plan to M'Cheyne and back (all three plan JSONs load); confirm the widget shows today's
    readings.

---

## Boundaries / write set

**Yours:**
- `shared/assets/**` (created by `git mv`)
- `app/build.gradle.kts` — **the `assets.srcDir`, `planAssetsDir` and `inputs.dir` lines only**
- `tools/*.py` — **output paths only**
- `tools/compare_bible_db.py` — paths only
- `.github/workflows/*.yml` — asset paths and trigger paths only

**Not yours:**
- **The asset bytes.** Not one. Any content change here is a data decision requiring the full
  provenance-and-reconciliation process in `docs/data/README.md`.
- Any `.kt` file. The runtime asset paths do not change, so no source change is needed. **If you
  find yourself editing Kotlin, the wiring is wrong.**
- `docs/data/README.md` — record-keeping is a data decision, not a build one.
- `gradle/libs.versions.toml` — no dependency changes here.

---

## Escalation triggers

- **The corrupt-a-byte probe reports `UP-TO-DATE`** → **Staff**, blocking. Do not merge. The gates
  are asleep.
- **In-APK asset paths change** → **Staff**, blocking. Every `PlanAssetSource` path and the
  `createFromAsset` literal depend on them.
- **A byte-diff CI job stops reproducing zero** → **Staff**, blocking. Run
  `tools/compare_bible_db.py`, which distinguishes **CONTENT drift** (real, exit 1) from
  **ENCODING drift** (toolchain, exit 0). That tool exists because `cmp`'s "byte 99 differs" let a
  defect sit red on `main` for six weeks.
- **A vendor source SHA no longer matches** → **Owner**, non-blocking here. The three vendor PDFs
  (edginet M'Cheyne, TGC/Carson, BLB Chronological) have **no immutable ref** — the SHA *is* the
  pin. **Do not re-pin to whatever is served that day.** Mirroring the pinned bytes is the fix and
  it is the owner's call (repo bloat + third-party copyright).
- **The `srcDir` redirect cannot be made to work** → **Staff**. ADR-0011 explicitly **rejects** the
  fallback of leaving assets under `app/`; reversing that is Staff's decision.
