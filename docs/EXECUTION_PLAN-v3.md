# Daily Reading Planner — V3 Execution Plan: In-app KJV Bible text

> **Owner:** Morgan (Engineering Manager) · **Status:** Ready to execute (Sprint A first) · **Last updated:** 2026-06-14
> **Inputs (decided upstream, not re-decided here):** [PRD-v3.md](PRD-v3.md) (what/why, FR-V3-*/NFR-V3-*, AC tags, M-V3-*) ·
> [ENGINEERING_SPEC-v3.md](ENGINEERING_SPEC-v3.md) (how — module layout, the seam, schema DDL, D-V3-1…20, recommended Sprint A–E) ·
> [features/bible-data-architecture-review.md](features/bible-data-architecture-review.md) (settled owner decisions + the version-agnostic principle) ·
> [EXECUTION_PLAN.md](EXECUTION_PLAN.md) (V1 plan — this matches its structure/voice/format) · [CLAUDE.md](../CLAUDE.md) (current status).
>
> This doc owns **sequencing and decomposition** for V3.0. The product (PRD-v3) and the
> architecture (ESpec-v3) are settled; I do not re-decide them. My job: order the work into
> dependency-correct sprints, surface the decisions/owner questions that gate work before it
> starts, and break the immediate sprints into executable subtasks. **Progress is measured only
> in working software** — every sprint states the new capability it unlocks. The V3.0 cut line
> (PRD §9) is held exactly: highlights/bookmarks/search/App Links/audio are **out**; do not
> smuggle them in (OQ-6 — confirmed held).

---

## 1. Up-front decisions & sequencing principles

### 1.1 The one critical path (must be serial, never parallelized)

Per PRD §12 and ESpec §13, there is a **single ordered chain** the rest of V3 hangs off:

> **two genuinely-independent PD KJV sources (incl. the same-upstream checksum-distinctness check, R-V3-3)
> → reproducible `tools/build_bible_db.py` import → committed `bible.db` asset
> → `BibleTextVerificationTest` green + `data-rebuild` CI job.**

This chain **is Sprint A**, and it is **strictly first and strictly serial**. There is no asset
to render against and nothing to gate until it lands, so **no reader-UI work can be _verified_
before A completes** — the planner's anti-temptation rule: do not parallelize source-selection
against reader UI. Sprint A is the **Sprint-1 of V3**: a hard gate, exactly like the plan-data
gate (`ReadingPlanVerificationTest`) that made the schedule trustworthy. The KJV text is the
project's **second core IP asset** (NFR-V3-B).

**Gate-prerequisite-before-UI-verification.** Source selection + checksum-distinctness (R-V3-3)
is the gate prerequisite. Everything downstream (the spine, the reader, the nav restructure) is
buildable in principle but **not provably correct** until the gate exists and is green. So A
exits only on a green gate, and B–E build on a trusted asset.

### 1.2 What overlaps, what does not

- **A → B → {C, D} → E.** A is serial-first. **B is verifiable the moment A lands** (the spine
  is pure-JVM-testable against the now-trusted asset). **C and D are peers downstream of B** and
  overlap across people: C needs the seam + resolver + bridge (the reader); D needs the resolver
  + the handoff types (nav + integration). **E is last** (hardening + release).
- Within A, the two-source selection (VA-T1) is the long-pole and gates the importer (VA-T3);
  the `exportBookCatalog` task (VA-T2) and the `BibleDatabase`/asset-version plumbing (VA-T6/T7)
  and the markup contract (VA-T8) can proceed in parallel with source selection, since they do
  not depend on which corpus wins.

### 1.3 What the owner OQs do and do not block

OQ-1/2/3/5 are **product/string calls that gate D and E, never A or B** (ESpec §12 sequencing
note). They touch first-run emphasis, the upgrade banner, nav labels/icons, and the UK courtesy
filing — none touches the source→import→gate chain or the reader's internal structure. They must
be resolved **before D lands** (D wires the first-run question and the nav labels) and **before E
ships** (string/tone sign-offs). See §6 for the checkpoint schedule. **OQ-4 is a _data_ question
on the critical path** — it is resolved mechanically in Sprint A by the chosen corpus (D-V3-11):
added-word markup + superscriptions are **required regardless** (P0); poetry `<l/>` / red-letter
`<w>` ship **only if** the corpus carries them cleanly (P1, droppable — FR-V3-14/16). **OQ-6**
(cut-line confirmation) is **resolved — held**: V3.0 is the reader; everything additive waits.

### 1.4 Decision index (carried from ESpec — not re-decided)

All twenty `D-V3-*` decisions (ESpec §12) are **settled and final** for this plan. The ones that
most shape sequencing/tickets: **D-V3-3** (the seam is the swap unit), **D-V3-6** (store markup
only, derive plain), **D-V3-7** (`is_title` drives titles), **D-V3-9** (new `VerseRef`, verse ∈
[0,999]), **D-V3-11** (split witnesses, checksum-distinct), **D-V3-12** (verse-id-keyed LazyColumn
from day one), **D-V3-16** (co-equal bottom nav, Schedule start), **D-V3-17** (Robolectric
nav-regression coverage), **D-V3-18/19** (IN_APP promotion + first-run question), **D-V3-20**
(+6 MB bundle budget). Tickets cite the decision they implement; engineers receive the decision
verbatim, never an invitation to relitigate it.

### 1.5 Team roster (assign tickets by name)

| Name | Agent | Owns in this plan |
|---|---|---|
| **Diego** | `android-architect` | The spine (seam, `VerseId`, `VerseRef`, `ReferenceResolver`, `PortionVerseBridge`, use cases), `BibleDatabase`/Hilt wiring, the nav restructure architecture, final design word. |
| **Avery** | `android-platform-senior` | The read-only Room `BibleDatabase` + `createFromAsset`, asset-version re-copy-on-update, `sqlite-jdbc` gate driver wiring, two-DB coexistence, StrictMode/off-main asset copy. |
| **Priya** | `android-ui-senior` | The reader UI (`ReaderScreen`, `VerseRenderer`, superscription headings, picker), the empty audio seam, bottom-nav `RootScaffold` look, reader accessibility, faithful-presentation review. |
| **Sam** | `android-feature-eng` | Settings teaser→real `IN_APP` value, the first-run reading-destination step, scoped integration wiring within established patterns. |
| **Riley** | `android-qa-eng` | The **KJV verification gate** (`BibleTextVerificationTest`), second-source witnesses, the resolver pins, Robolectric nav-regression suite, the markup strip invariant, mutation verification. |
| **Jordan** | `devops-eng` | The `data-rebuild` CI job, the `exportBookCatalog` Gradle task, `tools/requirements.txt` pinning, the bundle-size CI assertion (extends `release-bundle`), version bump. |
| _Maya / Owner_ | `senior-pm` | Resolves OQ-1/2/3/5; M-V3-2 faithful-presentation sign-off; string/tone sign-offs. |

---

## 2. Sprint sequence overview

Ordered by the §1.1 critical path. Each sprint has **one outcome goal** and states the **new
capability it unlocks**. No sprint closes unless the project builds and its tests pass (§4).

| # | Sprint | Outcome goal (the deliverable) | New capability unlocked | Key owners | Depends on |
|---|---|---|---|---|---|
| **A** | **Bible data foundation** *(the critical path; HARD GATE)* | A committed, reproducible `assets/bible/bible.db` (66 books / 1,189 chapters / 31,102 verses; verse-0 superscriptions; `<a>` added-word markup), its `book` table **generated from `BookCatalog` and verified field-identical**, behind the read-only `BibleDatabase` (`createFromAsset` + asset-version re-copy), with **`BibleTextVerificationTest` green** and the `data-rebuild` CI job. | The project's **second core IP asset exists and is provably correct.** A developer can load any verse range off-device on the JVM. Nothing downstream is verifiable without this. **Hard gate.** | Riley (gate), Avery (DB/asset), Jordan (CI/codegen), Diego (review) | — |
| **B** | **The spine: seam, resolver, Portion bridge** | `BibleTextSource` + `RoomBibleTextSource` + `VerseDao`; `VerseId`; `VerseRef` (verse ∈ [0,999]); `ReferenceResolver` (clean-fail); the shared `consecutiveRuns` helper; `PortionVerseBridge`; `GetChapterUseCase`/`GetPortionTextUseCase`. Pure-JVM-testable, no UI. | "Given a Portion or a reference string, here are the verse ranges and the verse text" is callable and tested — the whole-portion render (incl. the Jun 19/Dec 19 two-book portion) resolves correctly. The reader has an engine. | Diego (lead), Riley (resolver/bridge pins) | A |
| **C** | **The reader UI** | `ReaderRoute`/`ReaderScreen`/`ReaderViewModel`; the verse-id-keyed `LazyColumn` (D-V3-12); `VerseRenderer` (closed-tag → `AnnotatedString`) + verse-0 superscription heading; two-step book→chapter picker; `HorizontalPager` chapter nav + Prev/Next; `fontScale` reuse; in-session last-read (`SavedStateHandle`); the **empty** audio seam (D-V3-14); accessibility-gate extension. | The user can **read faithfully-formatted KJV inside the app, fully offline**, browse any book/chapter, and the surface reads as calm prose with every verse individually addressable. | Priya (lead), Diego (VM/use-case wiring) | B |
| **D** | **Nav restructure + integration** | `RootScaffold` + co-equal `NavigationBar` (D-V3-16); nested Schedule/Bible graphs, Schedule as start; **Robolectric nav-regression coverage** (D-V3-17, R-V3-5); `BibleProvider.IN_APP` promotion; `OpenReferenceUseCase`/`ReadingDestination.InApp` + the `DayReadingsRoute` tap-handoff branch; Settings teaser→real value; the Sprint-19 first-run reading-destination question (D-V3-19); the bundle-size CI check (D-V3-20). | **Tapping today's reading opens it in the in-app reader** (the whole point); Schedule and Bible are two co-equal places; new installs choose their destination once; existing screens stay reachable (nav debt retired, not grown). | Diego (nav), Sam (IN_APP/first-run/Settings), Priya (nav look), Riley (nav-regression suite), Jordan (bundle CI) | B (peer of C) |
| **E** | **V3.0 hardening + release** | Consolidated device pass (all AC-device-pass tags); M-V3-2 faithful-presentation owner/design sign-off; OQ-3 nav label/icon + S-string tone sign-offs; version bump; AR-1 recorded; closed-track rollout via the tag-to-Play pipeline. | **V3.0 is releasable**: a tester can install the planner+reader, read offline, and the owner has signed off that the KJV reads faithfully. | Priya+Owner (presentation), Jordan (release), Riley (device pass) | C, D |

**Dependency notes**
- A blocks everything downstream that asserts correctness; it is the hard gate and is serial-first.
- B is verifiable the moment A lands; C and D are peers downstream of B and overlap across people.
- D carries the R-V3-5 nav-regression budget and the D-V3-20 bundle-size CI gate — both land **in D**, not deferred to E.
- E depends on C and D; it adds no new feature scope, only verification, sign-off, and release.

Each sprint is one session in the one-sprint-per-session rhythm (CLAUDE.md). C/D firm up into full
tickets at the start of their own sessions, once A/B land — exactly as the V1 plan ticketed only
its first two sprints up front.

---

## 3. Detailed ticket breakdown

### Sprint A — Bible data foundation  *(HARD GATE)*

**Outcome goal:** a committed, reproducible read-only KJV text asset (`app/src/main/assets/bible/bible.db`)
with verse-0 superscriptions and `<a>` added-word markup, its `book` table generated from and
verified field-identical to `BookCatalog`, opened behind `BibleDatabase` (`createFromAsset` +
content-version re-copy), and an **offline `BibleTextVerificationTest` that gates release** plus a
`data-rebuild` CI job that re-derives the asset and diffs it. **No reader UI in this sprint —
data, its plumbing, and its verification only.** This is the Sprint-1 of V3.

**Sprint-level acceptance:** `BibleTextVerificationTest` passes under `testDebugUnitTest`; the
`bible.db` asset and both source witnesses are checked in; the `data-rebuild` CI job re-derives
`bible.db` and asserts a byte-diff of zero; the standing pipeline is green; OQ-4's P0 items
(added words, superscriptions) are confirmed present in the chosen corpus.

> Like Sprint 1, the verification can run in `:app`'s `testDebugUnitTest` via the `sqlite-jdbc`
> test driver (no device, no Robolectric DB) — it reads the **exact asset shipped in the APK**.

#### Tickets

**VA-T1 — Select two genuinely-independent PD KJV sources + checksum-distinctness (R-V3-3, D-V3-11)**
- **Owner:** Riley (with Diego review). **Complexity:** L. **Dependencies:** none. *(critical-path long-pole — start first.)*
- **Scope:** Identify a **primary markup-bearing corpus** that carries `transChange`/added-word tagging and verse-0 superscriptions (candidates: open-bibles KJV OSIS, eBible engKJV USFX), and a **genuinely independent second corpus** for the count/text equality witness (a *different* upstream — Sword module / zefania XML / the other of open-bibles/eBible). Download both, record source URLs + SHA-256 of each raw file in `docs/data/README.md` (extending the Sprint-1 reconciliation log). Assert the two raw sources are **checksum-distinct** (not the same upstream re-mirrored — the Sprint-1 trap). Confirm OQ-4: does the primary carry poetry `<l>` and words-of-Christ `<w>` cleanly? Record the answer (gates the P1 items only; added words + superscriptions are required regardless).
- **Acceptance:** two sources chosen and committed-by-reference (URL + SHA-256) in `docs/data/README.md`; checksum-distinctness asserted and recorded; OQ-4 P0 presence (added words + superscriptions in the primary) **confirmed**; OQ-4 P1 availability (`<l>`/`<w>`) recorded as yes/no. **JVM-provable** (checksums) + documented finding.
- **Tests/mutation:** the checksum-distinctness assertion becomes part of the build/gate (VA-T9); a re-mirror (identical checksums) must fail it.

**VA-T2 — `exportBookCatalog` Gradle task + `book_catalog_export.json` (D-V3-5, §6)**
- **Owner:** Jordan (with Diego review). **Complexity:** M. **Dependencies:** none (parallel with VA-T1).
- **Scope:** A pinned `:app` Gradle task / codegen step that emits `app/src/test/resources/bible/book_catalog_export.json` from `BookCatalog.kt` — one row per book: `book_no=order`, `name=canonicalName`, `usfm_code=usfmCode`, `chapter_count=chapterCount`, `testament = if (order <= 39) "OT" else "NT"`. **No hand-authored book rows anywhere** (D-S9-1 anti-drift). The importer (VA-T3) and the gate (VA-T9) both read this fixture; the catalog stays the single home of book structure.
- **Acceptance:** running the task regenerates a deterministic 66-row JSON identical to the committed fixture; USFM codes (not OSIS) per D-V3-5; the task is wired so CI can re-run it. **JVM-provable.**
- **Tests:** a test asserts the exported JSON is field-identical to `BookCatalog` (66 rows, every column) — drift fails it.

**VA-T3 — `tools/build_bible_db.py` import pipeline (§7.1, D-V3-6/7)**
- **Owner:** Avery (with Riley on correctness rules; Diego review). **Complexity:** L. **Dependencies:** VA-T1, VA-T2.
- **Scope:** Deterministic Python that parses the **primary** corpus + reads `book_catalog_export.json`, computes `verse_id = VerseId.encode(book_no,chapter,verse)`, places superscriptions at **verse 0 with `is_title=1`**, preserves `transChange type="added"` → `<a>…</a>` in `text_markup` (the closed vocabulary, §4.4), and populates `book` from the export (never authored). Stores **`text_markup` only — no `text_plain` column** (D-V3-6). Output is **byte-deterministic**: fixed row order by `verse_id`, no timestamps, pinned `PRAGMA`, `VACUUM` at the end. Pin the primary source SHA + input checksum in `docs/data/README.md`. Python deps pinned in `tools/requirements.txt`. Emit the second-source witnesses (VA-T4) in the same run.
- **Acceptance:** running the script from the pinned sources produces a `bible.db` with exactly the three tables `translation`/`book`/`verse` (the audio/versification/user tables **cut**, D-V3-2); re-running is byte-identical; the script is committed and re-derives the checked-in asset. **JVM-provable** (determinism via the rebuild job).
- **Tests/mutation:** determinism is enforced by VA-T10 (`data-rebuild` byte-diff). Content correctness is the gate's job (VA-T9).

**VA-T4 — Second-source witnesses: `kjv_verse_counts.csv` + `kjv_superscriptions.csv` (§7.3, D-V3-11)**
- **Owner:** Riley. **Complexity:** M. **Dependencies:** VA-T1.
- **Scope:** From the **independent second corpus**, derive `app/src/test/resources/bible/kjv_verse_counts.csv` — 1,189 rows of `(book, chapter, verse_count)` — and `app/src/test/resources/bible/kjv_superscriptions.csv` — the **exact** set of titled chapters (most Psalms but **NOT** Pss 1, 2, 10, 33; Habakkuk 3 has one). These are the gate's equality and superscription witnesses, derived by a **different parser from a different upstream** than VA-T3's primary. Record any per-verse text differences reconciled in `docs/data/README.md` (the Sprint-1 7-conflict-table analog).
- **Acceptance:** both CSVs checked in; counts sum to 31,102 verses across 1,189 chapters; the superscription list is exact (both-direction coverage); a reconciliation log entry exists for every difference resolved. **JVM-provable** (used by VA-T9).
- **Tests:** consumed by VA-T9; a corrupted witness must make the gate red.

**VA-T5 — Closed markup contract + `MarkupStripper` (§4.4, D-V3-6)**
- **Owner:** Diego (contract) + Riley (stripper tests). **Complexity:** M. **Dependencies:** none (parallel).
- **Scope:** `bible/data/markup/BibleMarkup.kt` — the **single source of truth** closed tag vocabulary: `<a>` (added word, P0), `<w>` (words of Christ, P1, off by default), `<l/>` (poetic line break, P1). `MarkupStripper.strip(markup): String` — pure, total: drop tags, keep inner text, collapse `<l/>` → single space, normalize whitespace. This is the gate's reference implementation of the strip invariant; the renderer (Sprint C) recognizes all three from day one even though V3.0 P0 emits only `<a>`.
- **Acceptance:** `strip()` is pure and total; the contract is the only place the vocabulary is defined; the parser/gate/renderer all reference it. **JVM-provable.**
- **Tests/mutation:** `MarkupStripperTest` pins strip behavior per tag (drop-keep-inner, `<l/>`→space, whitespace normalize); a mutation that keeps a tag or drops inner text must be killed.

**VA-T6 — Read-only `BibleDatabase` + `VerseEntity`/`VerseDao` + `createFromAsset` (§3, §4.2, D-V3-15)**
- **Owner:** Avery. **Complexity:** M. **Dependencies:** VA-T3 (needs the schema shape; can stub the asset).
- **Scope:** `bible/data/BibleDatabase.kt` (`@Database`, read-only, `exportSchema = false`, **no migrations** — `fallbackToDestructiveMigration(false)`, re-copy instead), `VerseEntity.kt` (maps the `verse` row), `VerseDao.kt` (`getVerses(start,end)` range query + book/chapter convenience), wired via `di/BibleModule.kt` with `createFromAsset("bible/bible.db")`. The Room types never escape `bible/data`. Coexists with the existing RW `ProgressDatabase` — **no shared connection, DAO, or entity** (D-V3-15).
- **Acceptance:** the DB opens the asset read-only; `getVerses` returns rows for a known range under an instrumented/Robolectric read; the two DBs are isolated. **JVM-provable** (DAO query via Robolectric or sqlite-jdbc) + **device-pass** (real `createFromAsset` copy on a device confirmed in E).
- **Tests:** a DAO range-query test; an isolation test (the two databases do not collide on name/connection).

**VA-T7 — Asset content-version + re-copy-on-update (§4.3, D-V3-8)**
- **Owner:** Avery. **Complexity:** M. **Dependencies:** VA-T6.
- **Scope:** `BibleAssetVersion.ASSET_CONTENT_VERSION: Int` + a startup check (off the main thread) comparing it to `bible_asset_content_version` persisted in the existing DataStore `SettingsRepository`. If the constant is newer, delete the copied `bible.db`/`-wal`/`-shm` from `databasePath` **before** the Room builder runs (forcing re-copy), then write the new version. This is a content-driven re-copy, **not** a Room migration. Reinforces the converse rule: **no user-writable data may ever live in `bible.db`** (it gets wiped) — user data stays in `ProgressDatabase`.
- **Acceptance:** bumping the constant triggers a re-copy on next start; an unchanged constant never re-copies; the check runs off-main (StrictMode clean). **JVM-provable** (the version-compare logic + delete-before-build ordering) + **device-pass** (real re-copy on a device, E).
- **Tests/mutation:** pin the compare logic (newer→recopy, equal→no-op, never-set→copy); a mutation flipping the comparison or skipping the delete must be killed.

**VA-T8 — `bible/` package skeleton + `BibleModule` Hilt wiring (§3)**
- **Owner:** Diego. **Complexity:** S. **Dependencies:** VA-T6.
- **Scope:** Create the `bible/data`, `bible/domain`, `bible/ui` package tree (empty placeholders for B/C land here) and `di/BibleModule.kt` binding `RoomBibleTextSource → BibleTextSource` and providing `BibleDatabase`/`VerseDao` (per ESpec §3 snippet). Keeps the seam the only domain-visible type. Net new **runtime** deps: zero; net new **test** dep: `sqlite-jdbc` (added to the version catalog here).
- **Acceptance:** the module compiles with Hilt processing; `sqlite-jdbc` is in the catalog (test scope only); the package tree matches ESpec §3. **JVM-provable.**
- **Tests:** Hilt graph compiles; a trivial injection of `BibleTextSource` resolves (can be exercised once `RoomBibleTextSource` lands in B — placeholder bind acceptable in A).

**VA-T9 — `BibleTextVerificationTest` — THE RELEASE GATE (§7.3, FR-V3-12, M-V3-1)**
- **Owner:** Riley. **Complexity:** L. **Dependencies:** VA-T3, VA-T4, VA-T5, VA-T2.
- **Scope:** An offline test under `testDebugUnitTest`, reading the **shipped `bible.db`** via `sqlite-jdbc`, asserting the full §7.3 correctness bar:
  1. **Structural** (no second source): exactly 66 books / 1,189 chapters / 31,102 verses; no duplicate `verse_id`; **verse numbering contiguous within every chapter**; `encode(book_no,chapter,verse) == verse_id` every row; no empty `text_markup`; coverage matches `book_catalog_export.json` chapter counts.
  2. **`book` reconciliation:** field-identical to `book_catalog_export.json`, 66 rows.
  3. **Second-source equality:** chapter-by-chapter vs `kjv_verse_counts.csv`; build-time full-text diff vs the second corpus with the reconciliation log; the **checksum-distinctness** assertion on the two raw sources.
  4. **Superscriptions:** both-direction vs `kjv_superscriptions.csv`; spot-pinned title text for **Ps 3** and **Ps 51** (catches the "folded into verse 1" failure); confirm Pss 1/2/10/33 have **none**.
  5. **Markup:** `independentPlain == strip(text_markup)` for all 31,102 verses; only the closed vocabulary appears; an added-word **floor** (count > a pinned minimum — zero ⇒ the parser dropped `transChange`) + ~10 hand-pinned added-word verses.
  6. **Famous-verse pins:** Gen 1:1, John 3:16, Ps 23:1, Rev 22:21 exact text (human canary).
- **Acceptance:** the test exists and **passes** against the committed asset; editing the asset or a witness inconsistently makes it **red**; it runs offline on plain JVM in `testDebugUnitTest`. **JVM-provable / release-gating.**
- **Tests/mutation:** the gate *is* the test. Mutation-verify the gate's own invariants where feasible (e.g. a deliberately dropped superscription, a duplicated verse_id, a stripped `<a>` floor → each must turn the gate red).

**VA-T10 — `data-rebuild` CI job (§7.1, the protecting wrapper)**
- **Owner:** Jordan. **Complexity:** M. **Dependencies:** VA-T3, VA-T9.
- **Scope:** A CI job that re-derives `bible.db` from `tools/build_bible_db.py` (pinned sources, `tools/requirements.txt`) and asserts a **byte-diff of zero** against the committed `app/src/main/assets/bible/bible.db`, so a hand-edited asset can never sneak past the binary commit (mirrors the Sprint-1/Sprint-9 rebuild discipline). Runs alongside the standing pipeline; release is blocked on both this and VA-T9.
- **Acceptance:** CI re-derives and diffs; a hand-edit to the committed asset fails the job; the standing pipeline + this job are both green on the sprint PR. **CI-gating.**

**VA-T11 — Reconciliation log + Sprint-A handoff data record (§7.1, §7.2)**
- **Owner:** Riley + Diego. **Complexity:** S. **Dependencies:** VA-T9, VA-T10.
- **Scope:** Extend `docs/data/README.md` with: the two source URLs + SHA-256, the checksum-distinctness result, every per-verse text difference reconciled (Sprint-1 table style), the OQ-4 P1 finding, and the `ASSET_CONTENT_VERSION` starting value. This is the durable provenance record for the second core IP asset.
- **Acceptance:** `docs/data/README.md` records sources, checksums, reconciliations, and OQ-4 — enough that a future text correction can be reproduced and re-gated. **Documentation gate.**

#### Sprint A subtask decomposition (~2–5 min each)

> Diego (as tech lead) and Morgan sharpen these at dispatch; below is the starting decomposition.
> Each subtask states exact files and acceptance so the receiving engineer has zero open questions.

**VA-T1 — source selection**
- 1a. Download primary candidate (open-bibles KJV OSIS **or** eBible engKJV USFX); record URL + `sha256sum` in `docs/data/README.md`. *Deliverable: primary file + recorded SHA.*
- 1b. Inspect the primary for `transChange type="added"` tags and verse-0/`<title>` superscriptions; record P0 presence yes/no. *Deliverable: OQ-4 P0 finding.*
- 1c. Inspect for `<l>` (poetry) and `<w>`/words-of-Christ tagging; record P1 availability. *Deliverable: OQ-4 P1 finding.*
- 1d. Download an **independent** second corpus from a different upstream; record URL + SHA-256. *Deliverable: second file + recorded SHA.*
- 1e. Assert the two SHA-256s differ; if identical, reject the second and pick another upstream (the re-mirror trap). *Deliverable: checksum-distinctness result.*

**VA-T2 — exportBookCatalog**
- 2a. Add the `exportBookCatalog` Gradle task/codegen reading `BookCatalog.kt`. *Deliverable: task wired.*
- 2b. Emit `app/src/test/resources/bible/book_catalog_export.json` (66 rows, USFM, derived testament). *Deliverable: fixture file.*
- 2c. Add a test asserting the export is field-identical to `BookCatalog`. *Deliverable: drift test green.*

**VA-T3 — importer** (one subtask per stage; each ~5 min)
- 3a. `tools/build_bible_db.py` skeleton + `tools/requirements.txt` (pinned). *Deliverable: runnable stub, 3-table DDL.*
- 3b. Read `book_catalog_export.json`; populate `book` (never authored). *Deliverable: 66 book rows.*
- 3c. Parse the primary corpus → `verse` rows with `verse_id`, `native_label`, `text_markup`. *Deliverable: 31,102 verse rows.*
- 3d. Place superscriptions at verse 0 with `is_title=1`. *Deliverable: titled chapters at v0.*
- 3e. Map `transChange added` → `<a>…</a>` in `text_markup`. *Deliverable: added-word markup present.*
- 3f. Determinism pass: fixed row order, no timestamps, pinned PRAGMA, VACUUM. *Deliverable: byte-stable output.*
- 3g. Commit `app/src/main/assets/bible/bible.db`; record primary SHA/input checksum in `docs/data/README.md`. *Deliverable: committed asset.*

**VA-T4 — witnesses**
- 4a. Parse the second corpus → `kjv_verse_counts.csv` (1,189 rows). *Deliverable: counts CSV.*
- 4b. Derive `kjv_superscriptions.csv` (exact titled set; Pss 1/2/10/33 excluded; Hab 3 included). *Deliverable: superscriptions CSV.*

**VA-T5 — markup contract**
- 5a. `BibleMarkup.kt` closed vocabulary (`<a>`/`<w>`/`<l/>`). *Deliverable: contract file.*
- 5b. `MarkupStripper.strip` (pure/total). *Deliverable: stripper.*
- 5c. `MarkupStripperTest` per-tag pins. *Deliverable: tests green + mutations killed.*

**VA-T6/T7/T8 — DB + asset-version + wiring**
- 6a. `VerseEntity` + `VerseDao.getVerses(start,end)`. 6b. `BibleDatabase` (RO, no migration). 6c. `BibleModule` + `createFromAsset`. *Deliverables: opening DB + range query.*
- 7a. `BibleAssetVersion` constant + compare logic. 7b. delete-before-build re-copy + persist version off-main. 7c. compare-logic tests + mutations. *Deliverables: re-copy on bump.*
- 8a. `bible/` package tree. 8b. `sqlite-jdbc` in the catalog (test scope). *Deliverables: skeleton + dep.*

**VA-T9/T10/T11 — gate + CI + log**
- 9a–9f. one subtask per §7.3 group (structural / book-recon / second-source / superscriptions / markup / famous-verse). *Deliverable: each group green.*
- 10a. `data-rebuild` CI job re-derives + byte-diffs. *Deliverable: CI gate.*
- 11a. `docs/data/README.md` provenance + reconciliation entries. *Deliverable: durable record.*

---

### Sprint B — The spine: seam, resolver, Portion bridge

> Sketched here at ticket-title level; full decomposition happens at the start of B's session,
> once A's trusted asset exists. All of B is pure-JVM-testable, no UI.

**Outcome goal:** the version-agnostic spine — given a `Portion` or a reference string, the app
resolves verse ranges and returns verse text through the one seam, with the whole-portion render
(incl. the Jun 19/Dec 19 two-book portion) correct and the resolver clean-failing on garbage.

**Sprint-level acceptance:** the resolver gate pins pass (valid ↦ exact ranges, malformed ↦ `null`,
never plausible-but-wrong); `VerseRef` allows verse ∈ [0,999] (titles survive construction);
`GetPortionTextUseCase` returns the complete portion in order; standing pipeline green; Kover floor
held on the new domain/data.

**Candidate tickets (titles):**
- **VB-T1 — `VerseId` encode/decode + range helpers** (Diego) — `chapterRange` starts at verse 0; `Long` ids; maxima safe.
- **VB-T2 — `BibleTextSource` seam + `RoomBibleTextSource` + `VerseText`** (Diego) — the only domain-visible type; reads `native_label` from the row (D-V3-4, never derives display number from the id).
- **VB-T3 — `VerseRef` with the verse ∈ [0,999] invariant** (Diego) — pinned: a `require(verse >= 1)` regression drops every Psalm title and must fail the gate (FR-V3-3, the trap).
- **VB-T4 — `ReferenceResolver` (clean-fail) + resolver gate pins** (Diego + Riley) — "John" vs "1/2/3 John", ranges, cross-chapter, whole-book, verse-0; malformed ↦ `null`; `formatOsis` reserved for V3.x.
- **VB-T5 — shared `ConsecutiveChapterRuns` helper + `ProviderUrlBuilder` re-point** (Diego) — lift `consecutiveRuns` to `bible/domain`; both nav and egress call the one helper (D-V3-10); mirror the two-book-portion equivalence pin.
- **VB-T6 — `PortionVerseBridge` + `GetChapterUseCase`/`GetPortionTextUseCase`** (Diego + Riley) — `rangesFor(portion)` never assumes refs share a book; whole-portion render pinned by M-V3-4 (the two-book portion).

---

### Sprint C — The reader UI

**Outcome goal:** the user reads faithfully-formatted KJV inside the app, fully offline, and can browse any book/chapter.

**Candidate tickets (titles):**
- **VC-T1 — `ReaderRoute`/`ReaderScreen`/`ReaderViewModel` + `ReaderUiState`** (Priya/Diego) — stateless screen over sealed state, stateful route owns side-effects (the `TodayRoute` pattern).
- **VC-T2 — verse-id-keyed `LazyColumn` (D-V3-12)** (Priya) — every verse an item keyed by `canonicalId`; reads as prose but is individually addressable (the rewrite-forcing P0).
- **VC-T3 — `VerseRenderer` (closed-tag → `AnnotatedString`) + de-emphasized `nativeLabel`** (Priya) — `<a>`→italic; `<w>`/`<l/>` recognized (P1, off/conditional).
- **VC-T4 — verse-0 superscription as unnumbered italic heading + `heading()` semantics** (Priya) — driven by `is_title` (D-V3-7), never dropped (FR-V3-6, NFR-V3-C).
- **VC-T5 — two-step book→chapter picker (bottom sheet)** (Priya/Sam) — books grouped by testament; chapter grid sized to `chapterCount`.
- **VC-T6 — `HorizontalPager` chapter nav + visible Prev/Next** (Priya) — walks `BookCatalog` order; crosses book boundaries; portion-open shows the portion's chapters in sequence.
- **VC-T7 — in-session last-read (`SavedStateHandle`) + `fontScale` reuse** (Diego/Priya) — durable last-read is V3.x (D-V3-13); reader inherits app-wide `fontScale` (FR-V3-17).
- **VC-T8 — empty audio seam (D-V3-14)** (Priya) — `ReaderAudioSlot` bottomBar (renders nothing) + nullable `activeVerseId` (always null); ~10 lines, no player/network/table.
- **VC-T9 — accessibility-gate extension** (Riley) — TalkBack speaks `strip(markup)` not tags; heading semantics; 48dp authored controls; extends `AccessibilityGateTest` (M-V3-5).

---

### Sprint D — Nav restructure + integration

**Outcome goal:** tapping today's reading opens it in-app; Schedule and Bible are co-equal places; new installs choose once; existing screens stay reachable.

**Candidate tickets (titles):**
- **VD-T1 — `RootScaffold` + co-equal `NavigationBar` (Schedule | Bible), Schedule as start (D-V3-16)** (Diego/Priya).
- **VD-T2 — `AppNavHost` nested Schedule/Bible graphs + tab state preservation** (Diego) — `saveState`/`restoreState`/`popUpTo(graphRoot){saveState}` (U18).
- **VD-T3 — Robolectric nav-regression suite (D-V3-17, R-V3-5)** (Riley) — tab switching; every existing screen reachable (day-pager, Settings, reader, picker); Schedule is start; back-stack preserved across a tab switch. **Retires part of the Sprint-6 `AppNavHost` JVM debt.**
- **VD-T4 — `BibleProvider.IN_APP` promotion + `ReadingDestination.InApp(portion)`** (Sam) — `multiRefCapable=true`, `requiresApp=false`; `fromStored` unknown-id → BLB unchanged (D-V3-18).
- **VD-T5 — `OpenReferenceUseCase` in-app branch + `DayReadingsRoute` tap-handoff** (Sam/Diego) — `IN_APP → InApp(portion)`; the route navigates into the Bible graph instead of a Custom Tab; `Web`/`MySwordApp` paths byte-for-byte unchanged (R-V3-4).
- **VD-T6 — Settings teaser → real `IN_APP` value** (Sam) — `provider-option-inapp` disabled→enabled, top of the list (OQ-1 emphasis); external providers unchanged (FR-V3-8).
- **VD-T7 — first-run reading-destination question (extends Sprint-19, D-V3-19)** (Sam) — one-question step; in-app **never** a silent default; upgraders' explicit choice preserved + not re-prompted (M-V3-6, the D-S14-1 trap).
- **VD-T8 — bundle-size CI check (D-V3-20)** (Jordan) — extends the Sprint-9 `release-bundle` job; flags/fails if the text asset pushes the bundle past **+6 MB** budget (M-V3-7).
- **VD-T9 — re-confirm one-screen-fit *with the bottom bar present*** (Priya) — the Sprints 16/18/20 schedule fit, now with ~80dp spent on nav (R-V3-1) — JVM where provable, else device-pass.

---

### Sprint E — V3.0 hardening + release

**Outcome goal:** V3.0 is releasable — a tester installs the planner+reader, reads offline, and the owner has signed off that the KJV reads faithfully.

**Candidate tickets (titles):**
- **VE-T1 — consolidated device pass** (Riley + owner) — every AC-device-pass tag: offline read (M-V3-3), instant/no-spinner feel (U13), whole-portion render (M-V3-4), nav state preservation (U18), one-screen-fit-with-bottom-bar (R-V3-1), reader at large font; real `createFromAsset` copy + asset-version re-copy on device (VA-T6/T7).
- **VE-T2 — M-V3-2 faithful-presentation sign-off** (Priya + owner) — Gen 1, titled Ps 3/51, untitled Ps 1, the Jun 19 two-book portion.
- **VE-T3 — OQ-3 nav label/icon + S-string tone sign-offs** (owner/Maya) — "Bible" vs "Read" vs glyph; "Schedule" vs "Today" vs "Plan".
- **VE-T4 — version bump + AR-1 recorded + closed-track rollout** (Jordan) — tag-to-Play pipeline; UK accepted-risk recorded before ship; OQ-5 courtesy filing decision applied.

---

## 4. Quality gates per sprint

### 4.1 Standing pipeline (every sprint — the V1/V2 discipline, unchanged)
On the merge target, a sprint is **not done** until:
1. `./gradlew assembleDebug` succeeds.
2. `./gradlew testDebugUnitTest` passes — **including `ReadingPlanVerificationTest` (Sprint 1) and, from Sprint A onward, `BibleTextVerificationTest`.**
3. `spotlessCheck` + `lintDebug` are clean.
4. Kover floor met (≥70% on domain/data; the project runs ~96%); new domain/data carries tests + mutation verification per the project habit.
5. The sprint's acceptance is demonstrably met in **working software** — nothing closed on "should work."
6. Any blocking decision/OQ for the *next* sprint is resolved (§6).

Full local command (CLAUDE.md): `./gradlew spotlessCheck lintDebug assembleDebug testDebugUnitTest koverXmlReportAppDebug koverVerifyAppDebug`.

### 4.2 Sprint-specific gates
- **Sprint A (HARD GATE — the Sprint-1 standard):** `BibleTextVerificationTest` green against the **shipped asset** (66/1,189/31,102; second-source equality; superscriptions both-direction + Ps 3/51 text; strip invariant + added-word floor; resolver pins); the `data-rebuild` CI job re-derives + byte-diffs the asset; the two sources are **checksum-distinct**. **No downstream sprint may rely on the text asset until this is green.**
- **Sprint B:** resolver gate (valid↦exact range, malformed↦`null`); `VerseRef` verse ∈ [0,999] (titles survive); whole-portion render incl. the two-book portion (M-V3-4); the shared `consecutiveRuns` equivalence pin.
- **Sprint C:** Robolectric reader tests — verse-id-keyed list renders; `<a>`→italic; superscription = unnumbered heading with `heading()` semantics; TalkBack speaks plain text; the accessibility gate extension (M-V3-5).
- **Sprint D:** the **Robolectric nav-regression suite** (D-V3-17, R-V3-5) — tab switching, every screen reachable, Schedule = start, back-stack preserved; first-run choice integrity (M-V3-6); the **bundle-size CI gate** (D-V3-20, M-V3-7) lands here.
- **Sprint E:** the consolidated **device pass** (the AC-device-pass tags); M-V3-2 faithful-presentation owner sign-off; release-bundle green.

### 4.3 Device-pass items, collected per sprint (deferred to E's consolidated pass)
A (real `createFromAsset` copy, asset-version re-copy) · C (reader visual reading-feel U15, instant load U13, large-font) · D (one-screen-fit with the bottom bar R-V3-1, nav state preservation U18) — all rolled into VE-T1.

---

## 5. Risks & dependencies

| # | Risk / dependency | Impact | Mitigation | Owner |
|---|---|---|---|---|
| R-V3-1 | **Nav real-estate cost** | Bottom bar permanently spends ~80dp; gives back one-screen fit Sprints 16/18/20 reclaimed. **Owner-accepted** (decision #3). | Re-confirm schedule degrades gracefully with the bar present on a P7P at default font (VD-T9, device pass VE-T1). | Priya/Morgan |
| R-V3-2 | **Bundle size** | Text asset bloats the lean app past budget. | Markup-only storage (D-V3-6, no `text_plain`); **+6 MB budget**; CI size gate (VD-T8, D-V3-20). | Avery/Jordan |
| R-V3-3 | **KJV source provenance / same-upstream trap** | The gate is worthless if the two sources are one corpus re-mirrored. **Critical-path long-pole.** | Two genuinely-independent sources + **checksum-distinctness** asserted in the gate (VA-T1, VA-T9); reconciliation log (VA-T11). | Riley/Diego |
| R-V3-4 | **External-provider partial obsolescence** | In-app obsoletes much of "Open readings in." | **Demote, don't retire** — IN_APP added; all external providers stay, paths byte-for-byte unchanged (VD-T4/T5). | Sam |
| R-V3-5 | **Nav-restructure blast radius** | The restructure wraps **every** screen in a new scaffold and touches the JVM-untested `AppNavHost` (Sprint-6 debt). | **Budget nav-regression verification in D** — Robolectric suite (VD-T3, D-V3-17) asserting reachability + back-stack; retires part of the debt rather than growing it. | Riley/Diego |
| D-1 | **Critical path** (source→import→gate→reader) | Reader UI cannot be **verified** before A. | A serial-first; B verifiable the moment A lands; C/D peers downstream of B (§1). | Morgan |
| D-2 | **`data-rebuild` CI job** | A hand-edited binary asset sneaks past the commit. | VA-T10 re-derives + byte-diffs; release blocked on it + VA-T9. | Jordan |
| D-3 | **Two-DB coexistence** | RW user data wiped by a text re-copy if it ever lands in the asset. | D-V3-15 isolation (no shared connection/DAO/entity); D-V3-8 converse rule enforced (user data only in `ProgressDatabase`). | Avery |
| D-4 | **`AppNavHost` JVM debt** | Long-standing untested push/pop. | Retired in part by VD-T3's Robolectric coverage. | Diego |

---

## 6. Owner decision checkpoints

The open product questions are **not on the critical path** and must **not** block Sprints A or B
(ESpec §12). They gate the integration and release sprints. Resolve on this schedule:

| OQ | Question | Owner | Must be resolved before | Code impact |
|---|---|---|---|---|
| **OQ-1** | First-run emphasis: is in-app **pre-selected/recommended** in the question, or perfectly neutral? | Owner | **Sprint D** (VD-T7 first-run step) | One default-selection flag in the first-run dialog. |
| **OQ-2** | Upgrade path: leave external users untouched (recommended), or a one-time dismissible "in-app Bible is here" notice? | Owner | **Sprint D** (VD-T7 — decides whether an upgrade banner ships) | Whether a banner ships; default is leave-them-be. |
| **OQ-3** | Nav labels/icons: "Bible" vs "Read" vs glyph; "Schedule" vs "Today" vs "Plan". | Owner/Priya | **Sprint D** (placeholder strings) → **finalized in Sprint E** (VE-T3 tone sign-off) | String/icon only. |
| **OQ-5** | UK Cambridge courtesy free-use permission request (AR-1) — file, or leave as accepted risk. | Owner | **Sprint E** (VE-T4 — AR-1 recorded before ship) | No code impact. |
| **OQ-4** | *(Data, on the critical path — NOT a release-gating owner call.)* Poetry `<l>`/red-letter `<w>` availability in the chosen corpus. | Riley/Diego | **Sprint A** (VA-T1 — resolved mechanically by the corpus) | Gates P1 FR-V3-14/16 only; P0 (added words, superscriptions) required regardless. |
| **OQ-6** | Confirm the V3.0 cut line holds (highlights/bookmarks/search/App Links/audio = V3.x/V4). | Morgan/Maya | **Resolved — held** (this plan) | None — scope protection. |

> **Flagged for Maya/owner, not invented here:** OQ-1 and OQ-2 are genuine product calls (emphasis
> and upgrade-nag) I will not pre-decide — VD-T7 ships behind whichever the owner chooses, with
> "leave external users untouched" + "neutral-but-in-app-listed-first" as the recommended defaults
> if no answer arrives by Sprint D. OQ-3 nav strings ship as placeholders in D and are finalized in
> E. None of these touches Sprint A or B.

---

*End of V3 execution plan. Sprint A is fully ticketed above (the immediate next work); Sprint B is
ticketed at title level and Sprints C–E at goal+title level — each is decomposed into full tickets
at the start of its own session, once A/B land and any blocking OQ (§6) is resolved.*
