# Daily Readings — V1 Execution Plan

> **Owner:** Morgan (Engineering Manager) · **Status:** Ready to execute · **Last updated:** 2026-06-10
> **Inputs (decided upstream, not re-decided here):** [PRD.md](PRD.md) (what/why) · [ENGINEERING_SPEC.md](ENGINEERING_SPEC.md) (how) · [SPEC.md](SPEC.md) + [CLAUDE.md](../CLAUDE.md) (context)
>
> This doc owns **sequencing and decomposition**. The product (PRD) and the architecture
> (Engineering Spec) are settled. My job here is to order the work into dependency-correct
> sprints, surface the decisions that block work before it starts, and break the first sprints
> into executable subtasks. **Progress is measured only in working software** — every sprint
> states the new capability it unlocks.

---

## 1. Overview

### 1.1 What V1 is, in working-software terms

When V1 ships, a Christadelphian can install one Android app and:

- Open it and **immediately see today's three readings** (Today screen, no setup) — correct
  and identical for every user worldwide, because the schedule is date-anchored by `(month, day)`.
- **Mark each reading done, or the whole day at once**, with state that persists locally and
  is keyed to the real calendar date (this year's marks ≠ last year's).
- **Tap any reading** and land on that chapter's KJV text on Blue Letter Bible.
- **Look up any date** via a month/day picker.
- **Glance at today's readings on the home screen** via a widget.
- Pick **light / dark / system** theme.
- Do all of the above **fully offline** (only the outbound BLB link needs the network).

The release is gated on two hard quality bars: the **365-day plan verified against a second
independent source** (automated CI gate), and the **all-66-books BLB link check** (manual QA).

### 1.2 Team roster (assign tickets by name)

| Name | Agent | Role | Owns in this plan |
|---|---|---|---|
| **Diego** | `android-architect` | Senior eng / tech lead | Architecture, scaffold, DI, data/domain layer, date+Feb-29 resolver, plan loader, BLB URL builder. Final word on technical design. |
| **Avery** | `android-platform-senior` | Senior platform eng | Glance widget, Custom Tabs hand-off, storage/Room wiring, performance (cold start, StrictMode), API-level/OEM compatibility. |
| **Priya** | `android-ui-senior` | Senior UI eng | Design system / theme, Today screen UI, date picker UI, reading-row components, range-collapse formatter, accessibility. |
| **Sam** | `android-feature-eng` | Intermediate product eng | Settings screen, scoped screen/flow wiring within established patterns. |
| **Riley** | `android-qa-eng` | Intermediate QA / infra | Test suites, the **plan-data verification gate**, the **66-book link check**, lint/format gates, repro/verification. |
| **Jordan** | `devops-eng` | Senior DevOps | GitHub repo admin, GitHub Actions CI, Gradle build config + version catalog + caching, signing/release setup. |
| _Maya_ | `senior-pm` | PM (not an impl owner) | Resolves product/data decisions in §2 (Feb 29 rule, app name, abbrev table, distribution, analytics). |

---

## 2. Up-front decisions (resolve before the work they block)

Every open / `[NEEDS SIGN-OFF]` item from the PRD (§12) and Engineering Spec (§13) is
consolidated here. **A sprint cannot start until its blocking decisions are resolved.** Owners
must close these on the timeline shown in the "Blocks" column.

| # | Decision | Recommendation (from upstream docs) | Owner | Blocks |
|---|---|---|---|---|
| **D1** | **Feb 29 handling** (ESpec §5.4 / R1, PRD Q1/FR-14) | ✅ **RESOLVED (owner):** Feb 29 has **no scheduled readings**. In leap years the date is shown with the message **"No scheduled readings for Feb 29th"**; in non-leap years Feb 29 does not occur. No portions are assigned to Feb 29 and no progress is tracked for it — so **no fold logic and no synthetic progress key** (simpler than the prior recommendation). | Maya + data | **Sprint 1** (plan JSON omits Feb 29 readings) · **Sprint 3** (resolver returns a no-readings state for Feb 29) · **Sprint 4** (Today screen renders the note). |
| **D2** | **BLB book-abbreviation table (all 66)** (ESpec §5.2 / R5, PRD Q3) | ✅ **RESOLVED (S1-T1/T4):** authored as `data/book_catalog.csv` in Sprint 1; tricky abbreviations verified against live BLB URLs. Originally: **Not a product decision — an engineering data task** (no owner sign-off needed). Author + verify the full 66-row `(canonicalName → blbAbbrev)` table against BLB, confirming the non-obvious: `Psalms→psa`, `Song of Solomon`, `Philemon`/`Philippians`, `1/2/3 John`, Pauline epistles. QA-gated artifact. | data (Riley link-checks) | **Sprint 1** (authored) · **Sprint 4** (URL builder). 66-book link check required QA before V1 release. |
| **D3** | **Plan JSON authored form** (ESpec §5.1 / R6) | ✅ **RESOLVED (S1-T1, Diego):** structured `{book, chapter}` is the single authored+runtime form; extraction scripts emit it directly (no separate string form, no expansion step). Psalm 119 verse-parts normalize to chapter 119 (verse fidelity deferred, see docs/data/README.md). Originally: **Runtime asset is structured `{book, chapter}` per ESpec §5.1** (one canonical form). If the data team prefers string form for hand-editing, add a build-time expansion step that emits the structured runtime asset. Tech-lead call — no owner input needed. | Diego + data | **Sprint 1**. Decide before extraction begins so we extract into the right shape. |
| **D4** | **Min SDK / target / compile SDK** (ESpec §10 / R3, PRD Q4) | ✅ **RESOLVED (owner unopinionated → accept architect rec):** **`minSdk = 26`** (clean `java.time`, ~95%+ device coverage); `targetSdk`/`compileSdk` = latest stable. | Diego | **Sprint 2** (scaffold/Gradle config). |
| **D5** | **App name + package id** (ESpec §4.1 / R4, PRD Q2) | ✅ **RESOLVED:** app name = **"Daily Reading Planner"**; package id = **`com.jpillion.dailyreadingplanner`** (changeable before first Play publish). | Maya (name ✓) + Diego (package id) | **Sprint 2** (scaffold). |
| **D6** | **Analytics / telemetry & privacy** (ESpec §13 / R8, PRD Q6) | ✅ **RESOLVED (owner):** **no analytics SDK in V1** and no networking dependency added. Revisit a privacy-respecting opt-in post-V1. | Maya + Diego | None (now settled — no one adds a tracking dep). |
| **D7** | **Distribution: Play Store vs. sideload** (ESpec §13 / R9, PRD Q5) | ✅ **RESOLVED (owner):** **Play Store** (best update reach for shipping plan-data corrections). | Maya + Morgan | **Sprint 9** (release readiness / signing; renumbered from 8). Does not block earlier work. |
| **D8** | **Dynamic color (Material You)** (ESpec §10) | Honor dynamic color on Android 12+, static fallback palette below. Minor; tech-lead call. | Diego/Priya | **Sprint 2** (theme). Low risk; decide inline. |
| **D9** | **Widget midnight refresh** (ESpec §7 / R7) | V1 = opportunistic refresh (on app resume / progress change) + system periodic update backstop. **No exact midnight alarm in V1** (AlarmManager is V2). | Diego/Avery | **Sprint 7** (widget). Already settled by ESpec; carry as a known limitation. |

**Status:** ✅ **All product/owner decisions are resolved** (D1, D4, D5, D6, D7). D2 and D3 are
engineering tasks needing no owner sign-off (D2 = data-team artifact; D3 = tech-lead call); D8/D9
are tech-lead calls already settled by the ESpec. **Nothing is blocked** — Sprints 1 and 2 can
start immediately and run back-to-back. D7 (Play Store) only shapes Sprint 8's signing/release.

---

## 3. Sprint sequence overview

Ordered by dependency. Each sprint has **one outcome goal** and states the **new capability it
unlocks**. No sprint closes unless the project builds and its tests pass (see §5).

| # | Sprint | Outcome goal (the deliverable) | New capability unlocked | Key owners |
|---|---|---|---|---|
| **1** | **Phase 0 — Trusted plan data** | A bundled `reading_plan.json` (365 days × 3 portions; February = 28 day-entries, no Feb 29 entry) + 66-book BLB abbrev table, **verified against a second independent source by an automated test**. | The project's core IP exists and is provably correct. Nothing downstream is meaningful without this. **Hard gate.** | Riley (lead), Diego, Maya/data |
| **2** | **Scaffold + CI + DI + theme** | An installable empty app on `minSdk 26` with Hilt wired, Material 3 theme (light/dark/system), version catalog, and green GitHub Actions CI (build + lint/format + test + Kover). | The team can build, run, and test a real app shell; every later ticket lands on a clean, gated pipeline. | Jordan (CI), Diego (scaffold/DI), Priya (theme) |
| **3** | **Data + domain layer** | Plan loader (asset→cached map), `ScheduleDateResolver` (incl. Feb-29 rule), Room progress store (year-isolated), `BlbUrlBuilder`, theme repo, and the use cases — all unit-tested behind interfaces. | "Given a date, here are the (verified) readings, whether each is marked, and the BLB URL" is callable and tested — no UI yet, but the engine works. | Diego (lead), Avery (Room), Riley (tests) |
| **4** | **Today screen + mark-as-read + tap-to-BLB** | The **Today screen** renders today's three readings, per-reading + whole-day mark toggles persist, and tapping a reading opens BLB in a Custom Tab. | The primary user value (PRD G1/G2/G3) is live: see today, mark it, read the text. This is the smallest shippable core. | Priya (UI), Diego (VM/wiring), Avery (Custom Tabs) |
| **5** | **Date picker** | A month/day picker that shows any date's readings (same schedule source) with "jump to today". | Users can look ahead/back (David/Ruth personas, PRD U4). | Sam (screen), Priya (picker UI), Diego (VM) |
| **6** | **Settings (theme)** | Settings screen with light/dark/system selector, persisted, driving the app theme live. | Users control appearance (PRD U6/FR-9). Small; can run parallel to 5. | Sam (lead), Priya (review) |
| **7** | **Glance home-screen widget** | A widget showing today's three readings, tap → opens app on Today, refreshing on resume/progress-change + periodic backstop. | The app earns a home-screen habit slot (PRD G5/FR-8). | Avery (lead), Diego (shared repo access) |
| **8** | **Owner-feedback features** *(inserted 2026-06-10 after the owner's partial device pass)* | ✅ DONE — responsive widget (3x2/2x2/1x2, D-S8-1), date-picker per-day completion indicators on a custom calendar grid (D-S8-2/3), confirm-gated year-scoped Reset progress, persisted text-size slider (D-S8-5). | The widget is useful at any size; the calendar shows how the reader is tracking; a year can be restarted safely; text size is user-controlled. | Avery (widget), Priya (picker UI), Diego (domain seams), Sam (settings), Riley (gate) |
| **9** | **Hardening, a11y & release readiness** *(renumbered from 8; minus items the owner's 2026-06-10 device pass retired: widget-on-launcher ✅, 66-book BLB link check ✅ G-LINKS, performance ✅ G-PERF)* | Remaining gates green: accessibility smoke (TalkBack incl. the S8 picker grid, font scaling, 48dp targets, contrast), StrictMode clean, edge-to-edge scrim API 26–28, widget resize/refresh on launcher, signing + release pipeline ready per D7. | V1 is releasable: a11y baseline verified and a build that can ship to Play. | Riley (QA gates), Avery (device pass), Jordan (signing/release), Priya (a11y) |

**Dependency notes:**
- Sprint 1 blocks everything downstream that asserts correctness (3, and the data-verification gate carried through 8).
- Sprint 2 blocks all app-code sprints (3–8) — nothing builds without the scaffold + CI.
- Sprint 3 blocks 4, 5, 7 (they consume the data/domain layer).
- Sprints 5 and 6 can run in parallel after 4. Sprint 7 depends on 3 (repos) and benefits from 4 (Today route to deep-link into).
- Sprint 9 (formerly 8) depends on everything. The 66-book link check was executed live and signed off during the owner's 2026-06-10 device pass (G-LINKS ✅), as were widget-on-launcher and performance (G-PERF ✅).

---

## 4. Detailed ticket breakdown — first two sprints

### Sprint 1 — Phase 0: Trusted plan data  *(HARD GATE)*

**Outcome goal:** a bundled, read-only `reading_plan.json` (365 days × 3 portions; February has
28 day-entries, no Feb 29 entry) and a canonical 66-book BLB abbreviation table, with an
**automated test that proves the plan matches a second independent source**. No app code in this sprint — data and its
verification only. **This is the blocking first sprint: no UI is meaningful without correct,
verified data.**

**Sprint-level acceptance:** the verification test exists and passes; both data artifacts are
checked into the repo; D1/D2/D3 are resolved.

> Sprint 1 can run in a lightweight harness (a small Kotlin/JVM test module or a standalone
> script + a JUnit test) without the full Android scaffold, so it does **not** block on Sprint 2.
> The verification test will be re-homed into `:app`'s `testDebugUnitTest` during Sprint 2/3.

#### Tickets

**S1-T1 — Resolve data-shaping decisions (D1, D2, D3)**
- **Goal:** Confirm the resolved Feb 29 rule (no Feb 29 entry; plan has 365 days), the plan JSON authored form, and the approach to the abbrev table before extraction.
- **Acceptance:** D1, D2, D3 in §2 marked resolved with a one-line decision each, recorded in this doc / a decision note.
- **Owner:** Maya (D1/D2 product+data), Diego (D3 + sign-off on D1 progress-key handling).
- **Dependencies:** none. **Complexity:** S.

**S1-T2 — Extract the 365-day plan from the primary source into structured JSON**
- **Goal:** Produce `reading_plan.json` per ESpec §5.1 schema from the primary source (christadelphia.org table).
- **Acceptance:** Valid JSON; `schemaVersion: 1`; `source` field set; exactly 365 day entries with correct per-month day counts (February = 28; no `{month:2,day:29}` entry); every day has exactly 3 portions with streams {1,2,3}; each ref is `{book, chapter}` with canonical book names.
- **Owner:** Riley (with data). **Dependencies:** S1-T1. **Complexity:** L.

**S1-T3 — Extract a second, independent source into a comparison fixture**
- **Goal:** Independently derive the same 365×3 schedule from a *different* source (pricejh roberts.pdf or antipas booklet) into a checked-in fixture.
- **Acceptance:** Fixture covers all 365 days (February = 28, no Feb 29 entry); derived from a genuinely different source than S1-T2; same structured shape (or a documented normalization).
- **Owner:** Riley (with data). **Dependencies:** S1-T1. **Complexity:** L.
- **Source substitution (2026-06-10):** pricejh `roberts.pdf` proved byte-identical to the primary (same MD5) — not independent. Second source = **antipas.org Bible Companion booklet**. See `docs/data/README.md`.

**S1-T4 — Author the 66-book BLB abbreviation table (data side)**
- **Goal:** Canonical `(book order, canonicalName, chapterCount, blbAbbrev)` for all 66 books, with non-obvious abbreviations explicitly confirmed.
- **Acceptance:** 66 rows; every `blbAbbrev` non-empty/lowercase; Psalms, Song of Solomon, Philemon, Philippians, 1/2/3 John, Pauline epistles confirmed against live BLB.
- **Owner:** Maya/data (Riley link-checks a sample). **Dependencies:** S1-T1 (D2). **Complexity:** M.

**S1-T5 — Plan-data verification test (THE RELEASE GATE)**
- **Goal:** An automated test loading `reading_plan.json` and asserting day-by-day equality vs. the S1-T3 fixture, plus structural invariants.
- **Acceptance:** Asserts 365 days with correct per-month day counts (February = 28; no Feb 29 entry), 3 valid portions/day, all books resolve in the catalog, all chapters in range, **and full day-by-day equality vs. second source**. Test is red if either source is edited inconsistently; passes on the reconciled data. Runs on plain JVM.
- **Owner:** Riley. **Dependencies:** S1-T2, S1-T3, S1-T4. **Complexity:** M.

**S1-T6 — Reconcile discrepancies & confirm Feb 29 absence in the data**
- **Goal:** Resolve every mismatch the verification test surfaces; confirm the plan has no Feb 29 entry and February has exactly 28 day-entries (Feb 29's no-readings behavior is a Sprint 3 resolver concern, not a data concern).
- **Acceptance:** S1-T5 passes with zero discrepancies; a short reconciliation note records any source disagreements and which source won and why.
- **Owner:** Riley + Maya/data. **Dependencies:** S1-T5. **Complexity:** M.

#### Sprint 1 subtask decomposition (~2–5 min each, exact deliverables)

**S1-T1 — Resolve data-shaping decisions**
- 1a. In `docs/EXECUTION_PLAN.md` §2, confirm D1 status resolved with the chosen rule ("Feb 29 has no scheduled readings; plan has no Feb 29 entry; resolver returns a no-readings state in leap years"); note that there is no fold logic and no progress key for Feb 29. *Deliverable: edited D1 row.*
- 1b. Same for D3: confirm "structured `{book,chapter}` runtime asset" (+ whether a build-time expansion step is needed). *Deliverable: edited D3 row.*
- 1c. Same for D2: confirm the abbrev table is a QA-gated artifact authored in S1-T4. *Deliverable: edited D2 row.*
- 1d. Create `docs/data/README.md` stub naming the primary source (S1-T2) and the second source (S1-T3) and which is canonical on conflict. *Deliverable: new stub file.*

**S1-T2 — Extract primary plan → JSON**
- 2a. Create `app/src/main/assets/reading_plan.json` skeleton: `{ "schemaVersion":1, "source":"...", "days":[] }`. *Deliverable: file with header, empty `days`.* (Path may be a temp `data/` dir until `:app` exists; moved in Sprint 2.)
- 2b. Fetch/open the primary source table (christadelphia.org Excel/PDF) and confirm its column structure (date → 3 streams). *Deliverable: a note of the source layout in `docs/data/README.md`.*
- 2c. Extract January (31 days) into `days[]` as structured `{book,chapter}` refs; verify Jan 1 = Genesis 1–2 / Psalm 1–2 / Matthew 1–2 (the anchor). *Deliverable: 31 day entries.*
- 2d. Extract Feb–Apr into `days[]` (February = 28 day-entries; no `{month:2,day:29}`). *Deliverable: through day ~119.*
- 2e. Extract May–Aug into `days[]`. *Deliverable: through ~day 243.*
- 2f. Extract Sep–Dec into `days[]`. *Deliverable: all 365 entries present.*
- 2g. Run a quick local JSON parse + count check (365 days with correct per-month counts incl. February = 28, 3 portions each). *Deliverable: passing ad-hoc count.*

**S1-T3 — Second source → comparison fixture**
- 3a. Create `app/src/test/resources/reading_plan_verify.json` (or `.csv`) skeleton. *Deliverable: empty fixture file.*
- 3b. Open the second source (pricejh roberts.pdf) and confirm its layout differs from S1-T2's source. *Deliverable: layout note.*
- 3c. Transcribe months 1–6 from the second source into the fixture (independently — do not copy from S1-T2). *Deliverable: half the fixture.*
- 3d. Transcribe months 7–12 into the fixture. *Deliverable: complete 365-day fixture (February = 28, no Feb 29 entry).*
- 3e. Document any normalization (e.g. "Psalm" vs "Psalms", chapter-span notation) needed to compare the two. *Deliverable: normalization note in `docs/data/README.md`.*

**S1-T4 — 66-book BLB abbreviation table**
- 4a. Create `BookCatalog` data file/list (temp location, e.g. `data/book_catalog.csv` or a Kotlin source stub) with columns `order, canonicalName, chapterCount, blbAbbrev`. *Deliverable: header + first 5 books.*
- 4b. Fill OT Law/History → Job (books 1–18). *Deliverable: 18 rows.*
- 4c. Fill OT Psalms → Malachi (books 19–39), confirming `Psalms→psa`, `Song of Solomon`. *Deliverable: 39 rows total.*
- 4d. Fill NT Matthew → Revelation (books 40–66), confirming Pauline epistles, `Philemon`/`Philippians`, `1/2/3 John`. *Deliverable: 66 rows total.*
- 4e. Manually open BLB for the 6 confirmed-tricky books (`psa`, `sng`/Song, `phm`, `php`, `1jo/2jo/3jo`) and verify the chapter loads. *Deliverable: 6 verified abbreviations + note.*
- 4f. Add `chapterCount` per book (used later by plan validation chapter-range check). *Deliverable: all 66 with chapter counts.*

**S1-T5 — Verification test**
- 5a. Create a minimal JVM test setup (standalone Gradle test module or script) able to run JUnit without the Android app. *Deliverable: runnable empty test.*
- 5b. Write a loader in the test that parses `reading_plan.json` into the structured model. *Deliverable: parse step green.*
- 5c. Assert structural invariants: 365 days, unique valid `(month,day)`, correct per-month day counts (February = 28; no Feb 29 entry), 3 portions/day with streams {1,2,3}. *Deliverable: invariant assertions.*
- 5d. Assert every `book` exists in `BookCatalog` and every `chapter` ≤ that book's `chapterCount`. *Deliverable: catalog/range assertions.*
- 5e. Load the second-source fixture and assert **day-by-day equality** (after documented normalization). *Deliverable: equality assertion.*
- 5f. Add a focused test on the abbrev table: 66 entries, all lowercase/non-empty. *Deliverable: table test.*

**S1-T6 — Reconcile & finalize**
- 6a. Run S1-T5; capture the list of mismatches. *Deliverable: mismatch list.*
- 6b. For each mismatch, check both sources + a third (antipas booklet) to decide the correct value; edit the canonical asset. *Deliverable: corrected `reading_plan.json`.*
- 6c. Record each resolved conflict (day, sources, decision) in `docs/data/README.md`. *Deliverable: reconciliation log.*
- 6d. Re-run S1-T5 until green; confirm the asset has no Feb 29 entry and February has 28 day-entries (Feb 29's no-readings behavior is Sprint 3's resolver). *Deliverable: green verification test.*

---

### Sprint 2 — Scaffold + CI + DI + theme

**Outcome goal:** an installable empty Daily Reading Planner app (`minSdk 26`, single-activity Compose,
Material 3 light/dark/system theme) with Hilt wired and a **green GitHub Actions pipeline**
(build + lint/format + unit test + Kover). The Sprint 1 verification test is re-homed into
`:app`'s unit tests so the data gate runs on every PR.

**Sprint-level acceptance:** `assembleDebug` produces an installable APK that launches to a
themed empty `MainActivity`; CI is green on a PR with build, ktlint/spotless, Android Lint,
`testDebugUnitTest` (including the migrated plan-verification gate), and Kover report; D4/D5/D6/D8 resolved.

> Depends on Sprint 1 only insofar as the verification test moves into `:app` here; the scaffold
> work (T1–T4) can begin as soon as D4/D5 land.

#### Tickets

**S2-T1 — Project scaffold (`:app` single module)**
- **Goal:** Create the Gradle Kotlin-DSL `:app` module with the package layout from ESpec §4.1, `DailyReadingsApp` (`@HiltAndroidApp`), and `MainActivity` hosting an empty `NavHost`.
- **Acceptance:** `assembleDebug` succeeds; app launches to an empty themed screen; package id = D5; `minSdk`/`targetSdk`/`compileSdk` = D4; empty package dirs created per §4.1.
- **Owner:** Diego. **Dependencies:** D4, D5. **Complexity:** M.

**S2-T2 — Version catalog + dependency set**
- **Goal:** `gradle/libs.versions.toml` with the ESpec Appendix A dependency set (Compose BOM+M3, Navigation-Compose, Lifecycle/VM-Compose, Hilt+hilt-navigation-compose, Room, DataStore, kotlinx-serialization-json, androidx.browser, Glance, and the test stack incl. Kover).
- **Acceptance:** All deps resolve; build green; no speculative deps beyond Appendix A.
- **Owner:** Jordan (catalog) + Diego (review). **Dependencies:** S2-T1. **Complexity:** M.

**S2-T3 — Hilt DI skeleton**
- **Goal:** Empty-but-wired Hilt modules: `DispatcherModule` (`@IoDispatcher`/`@DefaultDispatcher`), `AppModule` (`Clock`), `DataModule`, `RepositoryModule` (`@Binds` stubs ready), per ESpec §9.
- **Acceptance:** App compiles with Hilt processing; a trivial injected dependency (e.g. `Clock`) resolves at runtime; modules `@InstallIn(SingletonComponent::class)`.
- **Owner:** Diego. **Dependencies:** S2-T1, S2-T2. **Complexity:** S.

**S2-T4 — Material 3 theme (light/dark/system + optional dynamic color)**
- **Goal:** `ui/theme/` with color/type/shape, `DailyReadingsTheme` honoring light/dark/system and dynamic color on API 31+ (D8), static fallback below.
- **Acceptance:** Empty `MainActivity` renders themed; manual toggle of system dark mode flips the app; comfortable (not tiny) default type scale per PRD.
- **Owner:** Priya. **Dependencies:** S2-T1. **Complexity:** M.

**S2-T5 — GitHub Actions CI**
- **Goal:** `ci.yml` on PR + push to `main`: JDK 17 + Gradle cache; ktlint/spotless; Android Lint (`lintDebug`); `assembleDebug`; `testDebugUnitTest`; Kover report + coverage floor; upload report artifact (ESpec §12).
- **Acceptance:** CI green on a scaffold PR; format/lint violations fail the build; the migrated plan-verification test runs in `testDebugUnitTest`; Kover floor enforced (start ~70% domain/data, not gated on UI).
- **Owner:** Jordan. **Dependencies:** S2-T1, S2-T2, and S1 test to migrate (S2-T6). **Complexity:** M.

**S2-T6 — Re-home the plan-verification gate into `:app`**
- **Goal:** Move the Sprint 1 verification test + fixtures into `app/src/test/` and `app/src/main/assets/reading_plan.json`; ensure it runs under `testDebugUnitTest`.
- **Acceptance:** `./gradlew testDebugUnitTest` runs and passes the plan-data verification gate; asset is bundled in the APK.
- **Owner:** Riley + Diego. **Dependencies:** S1-T5/T6, S2-T1. **Complexity:** S.

**S2-T7 — StrictMode + base config (debug)**
- **Goal:** Enable StrictMode (no main-thread I/O) in debug builds; set up basic `Application` config.
- **Acceptance:** Debug build runs with StrictMode on; no violations on cold launch of the empty app.
- **Owner:** Avery. **Dependencies:** S2-T1. **Complexity:** S.

---

## 5. Quality gates & definition of done

### 5.1 Per-sprint gate (every sprint)
A sprint is **not done** until, on `main` (or the merge target):
1. `./gradlew assembleDebug` succeeds.
2. `./gradlew testDebugUnitTest` passes (**including the plan-data verification gate** from Sprint 1 onward).
3. ktlint/spotless + Android Lint are clean.
4. Kover coverage floor is met (domain/data ~70%+, ratcheting; UI not gated).
5. The sprint's acceptance criteria are demonstrably met in **working software** — nothing closed on "should work."
6. Any blocking decisions for the *next* sprint are resolved (see §2).

### 5.2 Sprint-specific gates
- **Sprint 1 (hard gate):** plan-data verification test passes with day-by-day equality vs. a second independent source; abbrev table has 66 rows; 6 tricky abbreviations link-checked. **No downstream sprint may rely on plan data until this is green.**
- **Sprint 3:** `ScheduleDateResolver` Feb-29 no-readings rule unit-tested (leap years return the no-readings state; non-leap years never hit Feb 29; all other dates resolve to a 3-portion `ReadingDate`); Room year-isolation test passes (1 Jan 2026 ≠ 1 Jan 2027); `BlbUrlBuilder` tested for all 66 books present.
- **Sprint 4:** Compose UI test — Today renders 3 readings, toggle persists, "mark whole day" marks all three in ≤2 taps (M3), completion indicator shown (FR-11); tap opens a Custom Tab.
- **Sprint 7:** widget verified on a real launcher (add, render, tap→Today, refresh on resume/progress).
- **Sprint 8 (owner features):** indicator classification (COMPLETE/MISSED/NONE incl. Feb-29-never-missed), year-scoped reset bounds, and the widget breakpoint chooser are unit-tested and mutation-verified; picker keeps the Sprint 5 dialog contract.
- **Sprint 9 (V1 release gates, renumbered from 8):** see below — G-LINKS and G-PERF already signed off (owner device pass, 2026-06-10).

### 5.3 V1 definition of done (release gates)
V1 is releasable only when **all** hold:
- **G-DATA (M1, hard):** plan-data verification test green in CI — 100% match vs. second source.
- **G-LINKS (M5, hard):** ✅ SIGNED OFF (owner device pass, 2026-06-10) — all 66 books verified live.
- **G-PERF (M2):** ✅ Performance verified good on device (owner device pass, 2026-06-10); StrictMode clean run still owed in Sprint 9.
- **G-ERGO (M3):** whole-day mark in ≤2 taps from Today.
- **G-A11Y:** TalkBack smoke pass on Today; system font scaling respected; touch targets ≥48dp; Material 3 contrast.
- **G-OFFLINE (FR-10):** all planner functions work in airplane mode; only BLB hand-off needs network.
- **G-BUILD:** CI green (build + lint/format + tests + Kover floor); signing + release pipeline ready per D7.

---

## 6. Risks & dependencies (carried from upstream)

| # | Risk / dependency | Impact | Mitigation | Owner |
|---|---|---|---|---|
| R1 | **Feb 29 handling** (ESpec R1, D1) | **RESOLVED — low risk.** Feb 29 has no scheduled readings; plan has no Feb 29 entry (February = 28 day-entries); in leap years the resolver returns a no-readings state and the UI shows "No scheduled readings for Feb 29th". No fold/double-day/synthetic-key logic — resolver/UI special-case only. | Maya + Diego |
| R2 | **BLB URL-scheme change / outage** (ESpec R2) | Tap-to-text breaks — a core V1 flow. | Isolate `BlbUrlBuilder` (one-file swap); 66-book link check at launch (G-LINKS); fallback source / V3 in-app reader are contingencies. | Diego + Riley |
| R3 | **Plan-data extraction accuracy** (PRD §11, M1) | The product's credibility; a wrong reference erodes trust. | Two-source extraction + automated equality gate (S1) treated as a hard release gate; reconciliation log. | Riley + Maya/data |
| R4 | **Abbreviation-table errors** (ESpec R5, D2) | Wrong book → wrong/broken BLB page. | QA-gated artifact; unit test (66 rows, format); manual 66-book link check (G-LINKS). | Maya/data + Riley |
| R5 | **Min SDK / package id** (D4, D5) | **RESOLVED.** `minSdk = 26`; app name = "Daily Reading Planner"; package id = `com.jpillion.dailyreadingplanner`. Sprint 2 is unblocked; package id changeable before first Play publish. | Diego + Maya |
| R6 | **Widget refresh imprecision** (ESpec R7, D9) | Widget may show stale "today" until next periodic update. | Accepted V1 limitation (opportunistic + periodic backstop); deep-link intent ready so V2 AlarmManager refresh needs no rework. | Avery |
| R7 | **Cold-start budget** (M2) | A setup/loading stall on launch hurts the "today-first" promise. | Asset parse + Room read off main thread; repo memoization; StrictMode in debug; verify in Sprint 8 G-PERF. | Avery + Diego |
| R8 | **Distribution** (D7) | **RESOLVED — Play Store.** Shapes Sprint 8 signing/release CI; does not block scaffolding/features. | Maya + Morgan |
| R9 | **Single-source extraction temptation** (cost pressure on S1) | Skipping the second source defeats the M1 gate. | Two-source extraction is non-negotiable; the gate test fails without a second fixture. | Morgan (enforces), Riley |

---

*End of V1 execution plan. Sprints 3–8 are listed at goal+owner+complexity level in §3; they
are decomposed into full tickets at the start of each respective sprint, once Sprint 1/2 land
and any of their blocking decisions (§2) are resolved.*
