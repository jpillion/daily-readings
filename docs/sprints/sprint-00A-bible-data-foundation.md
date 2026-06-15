# V3 Sprint A — Bible data foundation (the HARD GATE)

> **EM:** Morgan · **Status:** DONE (uncommitted; main session verifies the gate + commits) ·
> **Date:** 2026-06-14 · **Next:** `sprint-00B-spine-resolver-bridge`

## Goal outcome — MET

A committed, reproducible read-only KJV `bible.db` asset whose `book` table is generated from
and verified field-identical to `BookCatalog`, behind a read-only `BibleDatabase`, with
`BibleTextVerificationTest` GREEN as the release gate and a `data-rebuild` CI job. The project's
**second core-IP asset exists and is provably correct.** A developer can load any verse range
off-device on the JVM (via `sqlite-jdbc` in the gate). **No reader UI** (Sprints B–E). This is
the Sprint-1 of V3.

## Current capability (working software)

- The exact KJV text shipped in the APK is **66 books / 1,189 chapters / 31,102 verses /
  117 verse-0 superscriptions**, with translator-added words tagged `<a>` and Psalm titles at
  verse 0 (`is_title=1`) — all asserted against two independent corpora and pinned famous verses.
- The asset rebuilds **byte-identically** from two pinned PD sources via
  `tools/build_bible_db.py`; the `data-rebuild` CI job blocks any hand-edited binary
  (`cmp` byte-diff of zero — verified locally).
- The read-only `BibleDatabase` opens the asset via `createFromAsset`, isolated from the
  read-write `ProgressDatabase` (D-V3-15); `VerseDao.getVerses(start,end)` is a verse_id range
  query ready for the Sprint-B seam.

## Tickets (administrative record)

| Ticket | Status | Note |
|---|---|---|
| VA-T1 source selection + checksum-distinctness | ✅ | rejected eBible USFX (same upstream as primary); chose scrollmapper CSV |
| VA-T2 `exportBookCatalog` + `book_catalog_export.json` | ✅ | `tools/export_book_catalog.py` (see "open item" re: Gradle task) |
| VA-T3 `tools/build_bible_db.py` importer | ✅ | byte-deterministic; 5 documented `TEXT_OVERRIDES` |
| VA-T4 second-source witnesses (counts + superscriptions CSV) | ✅ | 1,189 / 117 rows |
| VA-T5 `BibleMarkup` contract + `MarkupStripper` (+tests) | ✅ | 7 stripper tests; mutations killed |
| VA-T6 `BibleDatabase`/`VerseEntity`/`VerseDao` + `createFromAsset` | ✅ | Hilt graph compiles |
| VA-T7 `BibleAssetVersion` re-copy compare logic (+tests) | ✅ | 7 tests; compare/delete mutations killed. **Startup hook deferred** (below) |
| VA-T8 `bible/` skeleton + `BibleModule` + `sqlite-jdbc` (test) | ✅ | seam bind lands in B (planned) |
| VA-T9 `BibleTextVerificationTest` — THE GATE | ✅ | 18 assertions, GREEN, 4 mutations killed |
| VA-T10 `data-rebuild` CI job | ✅ | re-derives + byte-diffs; pinned source SHAs |
| VA-T11 reconciliation log + provenance | ✅ | `docs/data/README.md` "KJV text" section |

375/375 tests (33 new), full standing pipeline green, Kover floor met, Sprint-1 7-test plan gate
untouched.

## Decisions & rationale (this sprint)

- **Sources (VA-T1, R-V3-3).** Primary = open-bibles `eng-kjv.osis.xml` (the only candidate
  carrying `transChange` added-words + separate Psalm superscriptions). Second = scrollmapper
  `formats/csv/KJV.csv`. **eBible's `eng-kjv_usfx.zip` was rejected** — the primary OSIS *is*
  Haiola's conversion of that same eBible USFX (same upstream, the Sprint-1 trap). Independence
  is proven structurally (LORD vs Lord, separate vs inline superscriptions, hyphen style,
  epistle subscriptions). SHA-256s + provenance in `docs/data/README.md`.
- **OQ-4 resolved mechanically.** P0 (added words 24,070 + superscriptions 117) present and
  shipped. P1 `<l>` poetry (2,314) and `<q who="Jesus">` red-letter (2,021) are available in the
  primary but **NOT emitted in V3.0** (P0 scope; reserved in `BibleMarkup` as `<l/>`/`<w>` for
  the Sprint-C renderer to recognize).
- **Hab 3 (finding, overrides spec expectation).** Both corpora encode "A prayer of Habakkuk…"
  as **verse 1**, not a verse-0 title. The asset follows both real sources; forcing verse 0
  would contradict the second-source count witness. Superscription set = the 117 Psalm titles.
- **5 primary defects corrected (VA-T3 `TEXT_OVERRIDES`).** 1 Chr 11:2 + Ezek 17:24 doubled
  clauses; Lev 17:8 of→or; Isa 47:11 + Matt 5:30 if→it typos. Each corrected only where the
  independent witness AND authentic KJV agree against the primary. 2 legitimate inter-edition
  variants (1 Sam 7:1, 2 Kgs 2:20) deliberately kept. Re-running the importer reproduces the
  corrections (they live in code, not hand-edits to the binary).
- **`ASSET_CONTENT_VERSION = 1`** (D-V3-8 starting value).

## State of the codebase

- **Importer:** `tools/build_bible_db.py` (Python stdlib only; `tools/requirements.txt` is
  deliberately dependency-free). `tools/export_book_catalog.py` regenerates the `book` fixture
  from `BookCatalog.kt`. Run:
  `python3 tools/export_book_catalog.py` then
  `python3 tools/build_bible_db.py --osis <eng-kjv.osis.xml> --second <scrollmapper_KJV.csv>`.
- **Asset:** `app/src/main/assets/bible/bible.db` (committed binary, no LFS). SHA-256
  `ce174e925a15dff0b7802255e201cd3510ae4f86f7a4b3c1830a4e6729da4909`.
- **Fixtures (test resources):** `app/src/test/resources/bible/` —
  `book_catalog_export.json` (66), `kjv_verse_counts.csv` (1,189), `kjv_superscriptions.csv` (117).
- **Kotlin (new):** `bible/data/BibleDatabase.kt`, `VerseEntity.kt`, `VerseDao.kt`,
  `BibleAssetVersion.kt`, `bible/data/markup/{BibleMarkup,MarkupStripper}.kt`;
  `di/BibleModule.kt`. Empty package dirs `bible/domain/`, `bible/ui/` ready for B/C.
- **Gate:** `app/src/test/.../bible/data/BibleTextVerificationTest.kt` reads the shipped asset
  via `sqlite-jdbc` (driver `org.sqlite.JDBC`) using the existing `planAssetsDir` system
  property → `src/main/assets`. Sibling tests: `MarkupStripperTest`, `BibleAssetVersionTest`,
  `BookCatalogExportTest`.
- **Build:** `sqlite-jdbc 3.50.1.0` in `gradle/libs.versions.toml` + `testImplementation` in
  `app/build.gradle.kts` (test scope only). No runtime deps added. No `INTERNET` permission.
- **CI:** `.github/workflows/ci.yml` gains the `data-rebuild` job (fetch pinned sources →
  verify SHAs → re-derive → `cmp` byte-diff zero).

## Carryover & next goal

- **Next: V3 Sprint B** (`sprint-00B-spine-resolver-bridge`) — verifiable the moment A lands.
  `VerseId` encode/decode; `BibleTextSource` seam + `RoomBibleTextSource` over `VerseDao`
  (reads `native_label` from the row, D-V3-4); `VerseRef` (verse ∈ [0,999] — NEVER
  `require(verse>=1)`, the Psalm-title trap); `ReferenceResolver` (clean-fail, malformed→null);
  the shared `ConsecutiveChapterRuns` helper; `PortionVerseBridge` + `GetChapterUseCase`/
  `GetPortionTextUseCase` (whole-portion incl. the Jun 19/Dec 19 two-book portion). Pure-JVM, no
  UI. The `BibleModule` seam bind (`RoomBibleTextSource → BibleTextSource`) is the first B task.
- **Deferred OUT of A (scope protected):** the `BibleAssetVersion` **startup hook** (the
  off-main delete-before-build call from app start, wired through a new
  `bible_asset_content_version` DataStore key) — the compare/delete logic is built and
  JVM-pinned, but the wiring is deferred to where it can be device-verified (the E device pass
  needs the real re-copy anyway). Keeping it out of A avoided expanding the `SettingsRepository`
  interface this sprint. The seam (`ensureCurrent(persist=…)`) is ready for it.
- **Owner OQs (OQ-1/2/3/5):** untouched here — they gate D/E only, not A/B.

## Open questions & risks / tech debt

- **`exportBookCatalog` is a Python script, not yet a wired Gradle task** (VA-T2 named a Gradle
  task). The fixture is generated, deterministic, and drift-gated by `BookCatalogExportTest`
  (the catalog can't silently diverge). Wrapping it as a `:app` `exportBookCatalog` Gradle task
  (so CI re-runs it) is a small Jordan follow-up — queued, low-risk.
- **Device-pass items (→ Sprint E VE-T1):** real `createFromAsset` copy of the 5.7 MB asset on a
  device; the asset-version re-copy on a bump (once the startup hook lands). Bundle-size CI gate
  (D-V3-20) lands in Sprint D; the headroom is large (~1.97 MB compressed vs +6 MB budget).
- **2 inter-edition text variants** (1 Sam 7:1, 2 Kgs 2:20) are documented and intentional — not
  bugs. If the owner prefers the second-source reading on either, it's a one-line `TEXT_OVERRIDE`
  + a reconciliation-log update.
- **No `INTERNET`, no runtime deps** — invariant held. The live source fetch is build/CI only;
  the committed gate is fully offline.

## Next sprint

`next: sprint-00B-spine-resolver-bridge`
