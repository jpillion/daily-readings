# Bible Data Architecture — team review & iteration

> Companion to [bible-data-architecture.md](bible-data-architecture.md) (the owner's V3 spec).
> Reviewers: Diego (architecture), Avery (platform/audio), Priya (UI), Riley (QA/data gate),
> Maya (product). Status: **review complete — text phase endorsed with changes; audio lifted
> out to a separate product decision.** Nothing built yet.

---

## Verdict in one paragraph

The **verse_id spine, the dual-text/markup choice, the verse-0 superscription convention, and
the "ship a prebuilt DB, never parse XML on device" approach are all sound** — keep them. The
spec was, however, written as if this were a greenfield Bible app: it ignores three assets we
already have (`BookCatalog`, `ProviderUrlBuilder`, the existing Room DB + exported schema), it
**buries an app-identity-changing decision (audio requires networking) inside a column
comment**, and it has **almost no verification story** for what would be our second core IP
asset. The text phase (V3.0) is a clean, offline, no-posture-change fit and is endorsed with the
changes below. Audio is a different, larger product and is lifted out to **V4, gated on an
explicit no-network decision**.

---

## The single most important separation: text (V3) vs audio (V4)

Avery and Maya independently flagged the same buried lede. Full KJV narration is **~1.6–2 GB**
(75 h × 48–64 kbps mono). That **cannot be bundled** (Play's ~200 MB cap; and it's absurd for a
5.7 MB lean app). Therefore audio **must be networked** — streamed/downloaded + cached — which
adds the **first network dependency this app has ever had**: manifest `INTERNET`, an HTTP stack,
a CDN bill, a download/cache subsystem, a foreground media service, and a Play data-safety
disclosure change. "No network, nothing leaves the device" is a load-bearing identity pillar for
a faith-community app.

**Decision:** the schema may *reserve the shape* cheaply, but reserving the shape ≠ committing
the roadmap. Audio is **V4**, and shipping it requires the owner to consciously answer: *are we
willing to become a networked app?* The V3 text release stays 100% offline and keeps `INTERNET`
out of the manifest entirely.

---

## Consolidated findings (severity-ranked) — keep / change / cut

### CHANGE (do these in the V3.0 build)

1. **[Critical] The `book` table duplicates `BookCatalog` — generate it, don't author it twice.**
   We already have a single, test-pinned 66-book catalog (`data/reference/BookCatalog.kt`) with
   `order` (= `book_no`), `canonicalName`, `chapterCount`, `usfmCode`, `blbAbbrev`. The spec's
   `book` table is ~80% the same data in a second home — exactly the parallel-table drift
   Sprints 1/9/13 stamped out (D-S9-1). Worse, codes disagree: spec uses **OSIS** (`Gen`,
   `John`), we store **USFM** (`GEN`, `JHN`) — not string-equal for Joel/Nahum/Mark/Ezekiel/
   Philippians/Jude. **Fix:** the import script reads `BookCatalog` and *generates* the asset
   `book` table, build-time-verified field-identical. Add `osisCode` as an Nth catalog token
   column only if/when App Links actually need it (they're deferred — so not yet).

2. **[Critical] The `Portion → verse_id` reader bridge is missing — and it IS the MVP.**
   Our reading plan is keyed at **(book, chapter)**; the spec's spine is **verse-level**.
   Nothing connects them, yet "tap today's reading → open the in-app text" is the whole point
   for *this* app. The reader must consume a `Portion` (existing type — a `List<Reference>`),
   mapping each chapter to a `verse_id BETWEEN` range and rendering in sequence. This handles
   multi-chapter portions (most days) and the two-book portion (Jun 19 / Dec 19 = 2 John +
   3 John). Add this as an explicit MVP section; it is not deferred.

3. **[Critical/Major] `user_mark` must move OUT of the read-only asset DB.**
   The spec puts highlights/bookmarks in `bible.db`, which `createFromAsset` **wipes on every
   asset re-ship** (e.g. when we fix a superscription bug). User data belongs in the existing
   **read-write `ProgressDatabase`**, keyed by canonical `verse_id`. This is a real bug in the
   spec as written.

4. **[Major] Two Room DBs is right — say so, and version the asset.** Keep `ProgressDatabase`
   (RW, migrations, `app/schemas/`) and a new read-only `BibleDatabase` (`createFromAsset`,
   `fallbackToDestructiveMigration`) separate. Add an explicit **asset content-version + re-copy
   -on-update** rule so a corrected `bible.db` actually reaches users.

5. **[Major] Superscription display should key off a stored `is_title` flag, not magic
   verse==0.** Keep verse-0 superscriptions (good call, get it right at import). But state the
   `verse ∈ [0, 999]` invariant explicitly (so a future verse-level `Reference` doesn't
   `require(verse >= 1)` and drop every Psalm title), and drive the italic-unnumbered-heading
   render rule off a stored flag rather than overloading the magic number into the markup layer.

6. **[Major] `text_plain` derivation — pin the strip invariant.** Whether we store `text_plain`
   or derive it from `text_markup` (eng decision — Diego leans derive-to-save-~4MB; Riley/Priya
   need plain for TalkBack + search, which a pure derivation satisfies either way), the
   non-negotiable is the test `text_plain == strip(text_markup)` for all 31,102 verses.

7. **[Major] Add the Sprint-1-style verification gate (see next section) — currently absent.**

### KEEP (the spine is right — these are the genuine "get-it-right-now" items)

- `verse_id = book*1M + ch*1K + verse` — sound, indexed, canonical-sorting; maxima safe
  (Ps 119:176, Ps 150). One noted limit: no room for verse-letter subdivisions (fine for KJV).
- Separate prebuilt `bible.db` via `createFromAsset`.
- **Verse-0 superscriptions** and **`transChange`-added-word → markup**, done correctly at
  import. These two touch the *content* and are genuinely re-import-or-nothing.
- "Never parse XML on device; ship the finished DB," FTS5 as an additive build step.

### CUT from the V3 build (premature reservation → demote to design appendices)

- **Empty `audio_track` / `audio_file` / `audio_segment` / `versification_map` tables.** The
  spec's justification is "create empty now to avoid migration." But (a) we already run clean
  Room migrations with exported schemas, and (b) these live in a *re-shippable asset*, so "add
  later" is a **re-ship, not a migration** — the avoid-migration argument is weakest exactly
  where the spec applies it. Carrying dead schema/DAOs/tests now, against a guessed future
  requirement, is the cost. **Build only `translation`, `book`, `verse` for V3.** Keep the audio
  model (§2/§3) and the versification star-topology (§6) as *documented future design* — they're
  well-designed, just not built.
- **Multi-translation / versification** is **speculative, not planned** — the app is
  KJV-anchored for a specific community and nobody has asked for translation #2. Keep
  `translation_id` on content rows (free, lets us display "KJV" attribution), build nothing else.

---

## The verification gate (Riley) — a release blocker, currently near-absent

KJV text is our **second core IP asset** and deserves the Sprint-1 discipline. Proposed
`BibleTextVerificationTest.kt`, offline, running against **the exact `bible.db` shipped in the
APK**, in `testDebugUnitTest` (adds <1–2 s via the `sqlite-jdbc` test driver):

- **Structural invariants** (no second source needed): exactly 66 books / 1,189 chapters /
  31,102 verses; no duplicate `verse_id`; **verse numbering contiguous within every chapter**
  (the analog of Sprint 1's coverage check — catches a dropped verse at an XML boundary, the
  likeliest defect); `verse_id` encoding internally consistent; no empty text.
- **Second-source equality (the real gate):** a checked-in `kjv_verse_counts.csv` — 1,189 rows
  of `(book, chapter, verse_count)` from an **independent** witness (build from one PD source,
  verify counts against another, independent parsers) — asserted chapter-by-chapter. Plus a
  build-time full-text diff vs a second corpus with a reconciliation log in `docs/data/README.md`
  (mirroring Sprint 1's 7-conflict table). **Watch the Sprint-1 trap:** confirm the two sources
  aren't the same upstream re-mirrored (checksum them).
- **Superscriptions:** a checked-in `kjv_superscriptions.csv` of the **exact** set of titled
  chapters (most Psalms but NOT all — Pss 1, 2, 10, 33… have none; Habakkuk 3 does), asserted
  both directions. Spot-pin Ps 3/51 title text to catch the fold-into-verse-1 failure.
- **Markup:** the strip invariant (above); only the closed tag vocabulary appears; a coverage
  **floor** on added-word count (if it's 0, the parser silently dropped `transChange`) plus ~10
  hand-pinned added-word verses.
- **Reference resolver:** exhaustive pure-logic pins — "John" vs "1/2/3 John", ranges,
  cross-chapter, verse-0, and **malformed input must fail cleanly, never silently mis-resolve**
  (returning a plausible-but-wrong verse_id is the worst failure for this app).
- **Reproducible import:** committed `tools/build_bible_db.py` with pinned source SHA + input
  checksum; deterministic output; the **checked-in `bible.db`** (plain binary, ~4.5 MB, no LFS)
  protected by a CI `data-rebuild` job that re-derives it and diffs.

---

## Navigation & reader UX (Priya) — countering the two-tab idea

**The owner's instinct (two co-present modes at the top) is right; the specific control is
wrong, twice over:**

- **Top tabs** (`TabRow`) are for *peer views of one dataset* (Photos/Albums over one library) —
  not two distinct destinations with different content, state, and back behavior. Misusing them
  fights TalkBack and forces an awkward shared app bar.
- **Bottom nav** is for *co-equal* top-level destinations. The owner has repeatedly said the
  Schedule is **primary** and the Bible reader **secondary**. Bottom nav makes them visually
  equal, permanently spends ~80dp advertising the secondary mode, reframes the app as "a Bible
  app with a planner tab," and gives back the one-screen real estate that Sprints 16/18/20 spent
  four sprints reclaiming.

**Recommendation:** ship the Bible as a **pushed top-level destination**, opened from a new
book-icon action in the Schedule top bar (alongside the existing Today/Settings actions) **and**
from every reading card. Frame to the owner: *"you get your top-of-screen Bible button — as an
icon, so the Schedule stays the front door and the readings don't shrink."* Reversible: if the
reader proves to be where users live, promoting it to a bottom-nav peer is a contained refactor.

**The one nav question whose wrong answer forces a redesign:** *is the Bible truly secondary, or
will in-app text quietly promote it to a co-equal daily surface?* Answer before building.

**Reader essentials (build the structure right; defer the rest):**
- **The reader MUST be a `LazyColumn` keyed by `verse_id` from day one** — flowing prose, but
  each verse individually addressable + locatable. This is the *one* thing that, gotten wrong,
  forces a rewrite when audio's verse-highlight lands.
- Two-step book→chapter picker (list grouped by testament → chapter grid) in a bottom sheet;
  `AnnotatedString` rendering of the closed tag set (italic added-words, optional theme-aware
  red-letter **off by default**, poetry indent, heading semantics, verse-0 as unnumbered italic
  heading); de-emphasized verse numbers; `HorizontalPager` for chapter swipe + visible Prev/Next;
  reuse the existing `fontScale`; persist last-read canonical `verse_id`.
- **TalkBack speaks `text_plain`, not markup**; section headings + superscriptions carry
  `heading()` semantics; reader stays a calm reading surface (no streaks/tracking bolted on).
- **Audio seam, reserved now / built later:** a `bottomBar` slot for a play bar; a single
  `activeVerseId` highlight state; **yield-to-user autoscroll** with a "resume" chip (the #1
  follow-along UX failure is fighting the user's scroll); tap-verse-to-seek routed by player
  state.

---

## External providers (Priya + Maya converge exactly)

In-app text partially obsoletes the V2 "Open readings in" feature — but **demote, don't retire**:
- **Promote the existing disabled "Read in this app (coming soon)" teaser** (already in the
  provider dropdown, `provider-option-inapp`) to a real `BibleProvider.IN_APP`, and make it the
  **default for new installs**. This is the feature text exists for — it should be front-door,
  not opt-in-buried.
- **Keep all external providers** in the same dropdown (power users have YouVersion notes /
  MySword libraries; the feature was recently owner-requested).
- **Preserve existing users' explicit choices** — same indistinguishability trap as D-S14-1
  (can't tell a deliberate "BLB" from a leftover default post-hoc). New installs → in-app;
  existing explicit external choices untouched. One setting, no new chooser UI.

---

## Audio phase (V4) — when/if the owner greenlights networking

Documented so it's ready, explicitly not built:
- **Stack:** Media3 ExoPlayer + `MediaSession` + a `mediaPlayback` foreground
  `MediaSessionService`; audio acquired via **WorkManager-driven Media3 `DownloadManager` into
  app-private `filesDir`** (no storage permission, no SAF), **opt-in per book, plays offline
  thereafter** (preserves offline-as-outcome). The player alone is the largest dependency the
  app would ever take.
- **Follow-along is two lifecycles, not one:** service-owned playback that survives
  backgrounding; a **foreground-only**, lifecycle-scoped polling coroutine that maps position →
  verse id with `distinctUntilChanged` so only the two changing verses recompose — never the
  list at 10 Hz, never while backgrounded.
- **TTS pipeline reality:** four-figure generation cost at 31,102 verses; **check vendor ToS for
  redistribution of generated audio** (can invalidate the approach); archaic-word pronunciation
  needs an SSML/lexicon pass; and the offset-accuracy landmine — **concatenate in PCM, encode
  the chapter once** (concatenating pre-encoded m4a clips drifts `start_ms` from true position
  via encoder priming). Reverence risk for a faith audience if the voice reads flat.

---

## Owner decisions (resolved 2026-06-14)

1. **Audio — NO, not in V3.** Future version. Keep the shape in mind, build nothing. V3 text
   stays 100% offline; `INTERNET` stays out of the manifest.
2. **UK licensing — accept the risk; do not geo-restrict, do not change the text.** KJV is PD
   worldwide except the UK (Crown copyright, Cambridge), where it's a near-theoretical risk for
   a free non-commercial app (Cambridge grants broad free use; every free Bible app serves KJV
   in the UK). Optionally file a courtesy free-use permission request with Cambridge / the
   King's Printer for certainty. Record the accepted risk in the spec before ship.
3. **Nav — Bible reader is CO-EQUAL: bottom navigation.** Owner override of the pushed-
   destination recommendation. Two co-equal top-level destinations (Schedule, Bible) via an M3
   `NavigationBar`. This reframes the app as planner + reader (a deliberate graduation), and
   takes back ~80dp of the Schedule surface — accept the one-screen-fit cost. Schedule remains
   the start destination.
4. **In-app is NOT the silent default.** Make it a **first-run setup question** (extend the
   Sprint-19 first-run flow): *"Read in the in-app Bible, or open an external app?"* The answer
   sets the "Open readings in" preference. External providers stay; the existing
   `provider-option-inapp` teaser becomes a real `BibleProvider.IN_APP`.

## Architecture principle (owner directive, 2026-06-14): version-agnostic spine + swappable text artifact

**All logic is built around the verse_id spine and reference ids, and is indifferent to which
text version is loaded. The text is a self-contained, swappable, encapsulated artifact keyed by
verse_id.** Swap the artifact → different version, zero logic changes.

- **Spine / logic layer (version-agnostic, in the app):** verse_id encoding, the `Reference`
  resolver, `Portion → verse_id` range mapping, navigation, the reader (renders whatever markup
  it's handed), and any future audio-offset join. None of it knows the version.
- **Text artifact (swappable, encapsulated):** a single self-contained file keyed by canonical
  verse_id, behind a one-method seam — `BibleTextSource.getVerses(idRange) → List<VerseText>`.
  Recommended format: a SQLite `bible.db` (one file, but indexed for the `verse_id BETWEEN`
  range queries the spine needs — a flat file would force 31k verses into memory). The format is
  an encapsulated detail behind the seam; swappability comes from the interface, not the format.
- **Book structure stays in the app** (`BookCatalog`: 66 books, chapter counts, verse_id scheme),
  NOT in the artifact. The artifact carries only `verse_id → markup text`. This is the clean
  reconciliation of "no duplicate book table" with "swappable encapsulated text."
- **This retires versification *logic* from the app — NOT the capability.** Two things must
  not regress, and neither does:
  - **Psalm titles are preserved** — they're a *spine* feature (verse ∈ [0,999], superscription
    at verse 0), independent of versioning. Unaffected.
  - **Different versification stays fully mappable** — what we cut is the `versification_map`
    *app table* (which would have made app logic version-aware); the mapping data **relocates
    into the artifact**. A differently-versified artifact presents its text keyed to the
    canonical verse_id spine *and* carries a **native display label per verse**, so the app
    stays version-agnostic (addresses by canonical id) while the reader still shows that
    translation's **native numbering and gaps** (faithful display, Rule 1, preserved).
- **The one door we keep open for this:** the seam returns text **plus a native display label** —
  `getVerses(range) → List<VerseText(canonicalId, nativeLabel, markup)>`. For KJV `nativeLabel`
  is derived from the verse_id (native == canonical), costing nothing now; a future
  differently-versified artifact populates it from its own internal mapping. Don't hardcode
  "display number == the verse_id's verse component" — read it from the seam.
- **Simultaneous multi-translation (compare/parallel view), if ever:** each artifact
  self-describes its delta from the canonical spine; the app aligns two artifacts by walking
  canonical ids — still zero versification logic in the app. Appendix B (§6) becomes "each
  artifact's internal concern," never a central app table.
- **Verification gate** checks the loaded artifact against the spine's expected structure
  (BookCatalog chapter counts → expected verse_id coverage), so any artifact is verifiable the
  same way regardless of version.

## Recommended phasing

- **V3.0 (smallest lovable):** bundled KJV text + chapter reader (`LazyColumn`/verse_id) +
  `Reference` resolver + Portion bridge + in-app as the default reading destination. Import done
  right once (verse-0 superscriptions, added-word markup, verse_id spine). Verification gate.
  External providers demoted to opt-out. UK decision recorded.
- **V3.x (real candidates, demand-ordered):** user highlights/bookmarks + last-read position;
  FTS search; Android App Links.
- **V4 / speculative:** audio follow-along (gated on the network decision); multi-translation +
  versification (no demand; KJV-anchored).
