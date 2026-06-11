# Sprint 0001 — Phase 0: Trusted plan data

**Status: GOAL MET.** Closed 2026-06-10. Commit: `803ad3a` on `main`.

## Goal outcome

The hard gate is green: a canonical 365-day Bible Companion plan and a 66-book BLB catalog
exist in the repo, **proven correct by an automated JUnit gate** that asserts full day-by-day
equality against an independently extracted second source, plus structural invariants.
Run it yourself: `cd verification && ./gradlew test` → 7/7 PASSED, ~1s warm.

## Current capability

- **The project's core IP exists and is provably correct.** `data/reading_plan.json` —
  365 days (Feb = 28, no Feb 29 per D1), 3 portions/day, structured `{book, chapter}` refs
  per ESpec §5.1, `schemaVersion: 1`.
- **Every BLB link a V1 user can tap works today:** all 66 abbreviations in
  `data/book_catalog.csv` returned HTTP 200 from live blueletterbible.org (full sweep, not a
  sample; titles spot-checked for phl/phm/2jo/jde). Pattern: `/kjv/{abbrev}/{chapter}/`.
- **The gate is re-runnable in one command** and red on any inconsistent edit (mutation-tested;
  data files are declared Gradle test inputs — without that, edits were silently skipped as
  UP-TO-DATE).
- Extractions are reproducible: `tools/extract_primary.py` and `tools/extract_antipas.py`
  re-fetch + re-parse the sources and reproduce the reconciled artifacts byte-for-byte.

## Decisions & rationale (do not relitigate)

- **D2 resolved:** catalog authored + live-verified; `phl` = Philippians (BLB canonical;
  `php` is a 301 alias), `phm` = Philemon, `jde` = Jude, `rth` = Ruth, `sng` = Song of Solomon.
- **D3 resolved (Diego):** structured `{book, chapter}` is the only authored form; scripts emit
  it directly. No string form, no build-time expansion.
- **Second source substitution:** pricejh `roberts.pdf` is byte-identical (same MD5) to the
  primary christadelphia.org `chart.pdf` → replaced by the antipas.org booklet. Logged in
  `docs/data/README.md` and EXECUTION_PLAN S1-T3.
- **Psalm 119 normalization:** the plan splits Ps 119 over Mar 9–12 by verse ranges; schema v1
  has no verse support, so each day carries `{Psalms, 119}`. Verse fidelity = deferred
  candidate ticket (additive optional field), NOT in V1 scope.
- **Multi-book portion exists:** Jun 19 and Dec 19 stream 3 = `2 John 1` + `3 John 1` (one
  portion, two books). Downstream UI/URL code must not assume one book per portion.
  (Tap-to-BLB opens the FIRST ref's chapter per ESpec §5.2 — unaffected.)
- **Coverage invariant kept permanently:** every book's chapters 1..chapterCount must all be
  read. It caught 5/7 source conflicts and 4 typos; it also pins catalog chapterCounts.

## State of the codebase

- `data/` — the three artifacts (move `reading_plan.json` into `app/src/main/assets/` and the
  fixture into `app/src/test/resources/` during Sprint 2 S2-T6).
- `tools/` — extraction scripts (python3, need `pdftotext`/poppler). Reconciliation overrides
  live in each script as documented `OVERRIDES` dicts.
- `verification/` — standalone Gradle module (Kotlin 2.1.20, JUnit 5, kotlinx-serialization,
  jvmToolchain 17, wrapper 8.14.3 checked in). Test:
  `verification/src/test/kotlin/ReadingPlanVerificationTest.kt`. `dataDir` is passed as a
  system property — re-point it when re-homing into `:app`.
- `docs/data/README.md` — sources (URLs + MD5), layout notes, 7 normalization rules, full
  reconciliation log (7 conflicts, evidence-based resolutions, third witness
  dailyreadings.org.uk used once for Jan 12/13).
- No app code yet. No GitHub remote yet.
- Local toolchain installed this sprint: Homebrew `poppler` (pdftotext), `gradle` 9.5.1
  (used only to generate the checked-in 8.14.3 wrapper), JDK 17 at
  `/opt/homebrew/opt/openjdk@17`.

## Carryover & next goal

- **Next goal (Sprint 2 per EXECUTION_PLAN §4):** scaffold + CI + DI + theme — installable
  empty app (minSdk 26, package `com.jpillion.dailyreadingplanner`), Hilt, M3 theme,
  green GitHub Actions (needs the GitHub remote created — Jordan), and S2-T6 re-homing of
  this gate into `testDebugUnitTest`.
- **Queued/deferred:** optional verse-range field for Psalm 119 days (schema-additive, post-V1);
  Sprint 8 manual 66-book link check at release time (automated sweep already passed today —
  rerun near launch against the live site).
- **Scope protected out:** no Bible text data (V3); no per-chapter progress; no Feb 29 fold.

## Next sprint

`next: sprint-0002-scaffold-ci-di-theme`

## Open questions & risks

- BLB URL scheme is verified as of 2026-06-10; re-verify at release (R2/G-LINKS).
- Gradle wrapper is 8.14.3 (AGP-compatible); Sprint 2 should standardize the whole repo on one
  wrapper at the root and may absorb/replace the `verification/` module then.
- Both source PDFs contain genuine misprints (7 conflicts + 4 typos — see reconciliation log);
  if either site re-typesets its PDF, the extraction scripts may need layout adjustments
  (band line ranges / column offsets are hardcoded for the current antipas imposition).
