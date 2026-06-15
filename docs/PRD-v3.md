# Daily Reading Planner — PRD V3: In-app KJV Bible text

> **Owner:** Maya (Product) · **Status:** V3 scope defined — owner-greenlit, awaiting EM
> (Morgan) + staff-eng (Diego) review · **Last updated:** 2026-06-14
> **Companion docs:** [docs/PRD.md](PRD.md) (V1/V2 PRD — this doc continues its numbering),
> [docs/features/bible-data-architecture.md](features/bible-data-architecture.md) (owner's
> technical architecture spec), [docs/features/bible-data-architecture-review.md](features/bible-data-architecture-review.md)
> (the five-lens team review **and the authoritative owner decisions** this PRD is built on),
> [docs/features/bible-app-links.md](features/bible-app-links.md) (the V2 external-provider
> feature V3 partially obsoletes), [CLAUDE.md](../CLAUDE.md) (session handoff).
>
> This document owns **what** V3 is and **why**. It defers **how to build** to Diego and
> **how to sequence** to Morgan. Every product decision it relies on is already settled in the
> architecture-review doc's "Owner decisions" and "Architecture principle" sections; this PRD
> translates those into a reviewable product definition, it does not re-decide them. New ideas
> are flagged candidate/future and are not committed scope.

---

## 0. The framing shift — read this first

V1 was defined, deliberately and repeatedly, as **"a digital reading planner, NOT a Bible
reader; scripture text is one tap away in the browser; in-app text is deferred to V3"**
(PRD §1, §5, §6). That framing did its job: it let us ship the planner fast without taking on
the project's largest data asset.

**V3 graduates the product.** Daily Reading Planner becomes a **planner *and* a reader** — it
still tells you what to read and tracks whether you read it, and now it also *holds the text*,
on the device, offline, with no account and nothing leaving the phone. This is a deliberate,
owner-approved identity change, not a feature bolt-on. Where V1's PRD says "not a Bible reader"
and "text deferred to V3," **this document supersedes that framing.** Those statements remain
true *as a record of V1*; they are retired as a description of the product going forward.

The two pillars that do **not** change, and that this graduation is explicitly built to
protect: **(a) the app stays 100% offline — no networking, nothing leaves the device**
(audio, which would require networking, is explicitly *not* V3 — see §3); and **(b) the
respectful, non-gamified, KJV-anchored tone** for this Christadelphian audience. The reader is
a calm reading surface, not a study console or an engagement engine.

---

## 1. Overview / vision

Open the app to your three readings for today, as you always have. Now, tapping a reading can
open **the text itself, inside the app** — faithful KJV, formatted the way the King James Bible
is meant to be read (translator-supplied words in italic, Psalm titles as headings, verse
numbers quietly present but not shouting). No browser bounce, no second app, no network. A new
**Bible** destination in the bottom navigation lets you browse to any book and chapter directly,
not only the day's portions. Your reading stays between you and God, on your device.

The schedule stays the front door — Daily Reading Planner is still, first, the answer to "what
are today's three readings, and have I done them?" The Bible reader is its companion: the place
the readings *lead to*, finally living inside the same app instead of outside it.

---

## 2. Problem & motivation

**The friction today.** Every reading tap in V1/V2 leaves the app. The OS routes the chapter
URL to Blue Letter Bible (or the user's chosen provider — YouVersion, Bible Gateway, MySword)
or to a browser Custom Tab. That one tap — the single most important interaction in the app
(PRD G3) — has three costs:

1. **It bounces you out.** You leave the planner, land in an unfamiliar (or at least separate)
   surface, read, then have to navigate *back* to mark the reading done. The reading and the
   tracking of it live in two different apps.
2. **It needs the network.** The planner is offline-first and proud of it (PRD FR-10), but the
   one thing a reader most wants to *do* — read — silently requires connectivity. On a plane,
   in a basement hall, on a metered connection, the readings are visible but the text is not.
3. **It loses context, and it leaves the device.** The chapter opens in a web view or a third
   party app with its own chrome, its own translation defaults (BLB's link is fine; not every
   provider's bare link is — see the Logos/ESV trap recorded in the app-links spec §11), and
   its own data posture. For a faith-community app whose quiet promise is *nothing leaves your
   phone*, sending every reading tap out to a website is the one place that promise bends.

**Why now.** The planner has proven itself — V1 shipped, V2 added sober motivation (stats,
streaks, reminders) and provider choice. The remaining gap between "tells you what to read" and
"lets you read it" is the text, and closing it is the natural next graduation. The data is
public-domain KJV; the architecture to carry it faithfully and durably is settled (the owner's
spec + the team review); and bundling it costs a few megabytes, not a network dependency.

**The angle for this audience.** Christadelphians read these same three portions daily,
worldwide, in sync with their ecclesia. A reading app for them should feel like opening a
Bible, not opening a website. *Your reading stays between you and God, on your device* —
offline, no account, no telemetry — is not a marketing line here; it is the product's existing
identity, and in-app text is what finally makes it true of the reading itself, not just the
tracking of it.

---

## 3. Goals & non-goals

### Goals (continuing PRD §4's G-numbering)

- **G6 — Read in the app.** Tapping a reading can open its text *inside the app*, faithfully
  formatted KJV, with no browser bounce and no second app.
- **G7 — Stay offline.** Reading the text works with no network — the planner's offline-first
  posture (FR-10) now covers the text too. V3 adds **zero** networking; `INTERNET` stays out of
  the manifest.
- **G8 — Browse freely.** A reader can navigate to any book and chapter directly, not only the
  day's three portions.
- **G9 — Faithful presentation.** The KJV renders the way it reads on the page: italic
  translator-supplied words, Psalm superscriptions as unnumbered headings, poetry, quiet verse
  numbers. Accuracy of the *text* is held to the same release-gate bar as accuracy of the
  *plan* (PRD M1).
- **G10 — Durable by design.** The reader is built on a version-agnostic verse-id spine behind a
  swappable text seam, so the *what we display* can change (a corrected KJV, or someday a
  differently-numbered translation) without rewriting *how the app works*. This is a
  product-durability goal, not only an engineering one: it is how we keep faith with "get the
  text right" over years of corrections.
- **G11 — Let the reader choose their destination, once, kindly.** A first-run question sets
  whether readings open in the in-app Bible or an external app; the existing external providers
  remain for those who live in them.

### Non-goals (explicit — do not build in V3)

- **No audio.** Full KJV narration is ~1.6–2 GB and therefore *cannot be bundled* — it would
  require streaming/download, i.e. the **first network dependency this app has ever had**. That
  is an identity-level decision the owner has deliberately deferred. **Audio is V4, gated on a
  separate, explicit "are we willing to become a networked app?" decision.** V3 stays 100%
  offline. (The data *shape* may cheaply accommodate audio later — see §11 — but no audio
  schema, tables, code, or assets ship in V3.)
- **No multi-translation.** The app is KJV-anchored for a specific community; no one has asked
  for translation #2. The architecture *stays version-agnostic* (G10) so a second translation
  would be additive, but **no translation picker, no second text, no versification-mapping app
  logic** ships in V3. (Psalm titles and faithful numbering are preserved — those are
  *spine* features, not multi-translation features. See §11.)
- **No study tools.** No cross-references, commentary, study notes (dailyreadings.org.uk-style
  notes remain a parked V3.x/future content idea), Strong's numbers, maps, or dictionaries.
- **No social / sharing / accounts / cloud.** Unchanged from V1: local-only, no account, no
  backup, no sharing surface. Verse sharing is a parked candidate, not V3.0.
- **No highlights, bookmarks, last-read, or search in V3.0.** These are real and wanted, but
  they are **V3.x**, demand-ordered, after the reader itself proves out (§9). V3.0 is the
  smallest lovable reader.
- **No gamification creep into the reader.** The reader carries no streaks, stats, progress
  bars, or tracking chrome. Marking a reading done stays where it is (the schedule); the reader
  is for reading.

---

## 4. Target users & personas

V3 serves the **same** general Christadelphian audience as V1/V2 (PRD §3) — we are not
narrowing. The existing personas extend cleanly, and in-app text resolves a real gap each one
felt:

- **Hannah — the daily reader (zero-friction).** Reads the three portions most mornings, values
  speed. Today she taps a reading, waits for a browser, reads, then navigates back to tick it
  off — two apps for one habit. *In-app text closes the loop:* tap → read → back → mark, all in
  one place, instantly, with no load spinner. She is the primary beneficiary of V3.0.
- **David — syncing with the ecclesia (correctness).** Cares that the plan is exactly right and
  the same for everyone. He extends naturally to caring that the *text* is exactly right — which
  is why the KJV verification gate (§8, FR-V3-12) matters to him as much as the plan-accuracy
  gate did. He also benefits from free browsing (G8): looking up a cross-referenced passage the
  Sunday exhortation mentioned, without leaving the app.
- **Ruth — the returning/aspiring reader (low friction, no guilt).** Wants to start today
  without setup or shame. In-app text removes one more barrier (no "which Bible app do I even
  have?" moment) and keeps the calm, non-gamified surface she needs — the reader has no streak
  or completion chrome to greet a returning reader with.
- **New facet introduced by V3 — the offline reader and the privacy-minded reader.** Both
  already live inside the personas above rather than being a new audience: the reader on a plane
  / in a hall with no signal (offline), and the reader for whom "nothing leaves my phone" is
  part of why they chose this app over a big-platform Bible app (privacy-minded). V3 is the
  release that makes both of those promises true of the *reading*, not just the tracking. No new
  persona is needed; these are needs the existing audience already has.

---

## 5. User stories & acceptance criteria

User stories continue the PRD's U-numbering (V1/V2 ended at U12); V3 uses **U13…**. Acceptance
criteria are written to be testable — the verification gate (§8) and the existing accessibility
gate are the backstops for the ones that cannot be eyeballed.

### U13 — Open today's reading in the app

*As Hannah, I want tapping today's reading to open its text inside the app, so I can read
without leaving the planner or going online.*

- Given my reading destination is set to in-app, when I tap a reading on the day screen, the
  in-app reader opens to the start of that portion's text. **AC.**
- The reader renders the **entire portion**, not just the first chapter: a multi-chapter portion
  (e.g. "Genesis 1–2") shows both chapters in sequence; the two-book portion (Jun 19 / Dec 19 =
  2 John + 3 John) shows both books in sequence. **AC.**
- No network is used or required; the text appears whether or not the device is online (no
  `INTERNET`, no network call on the read path). **AC (offline).** The "no loading spinner /
  instant" perf *feel* of that load is **AC (device-pass)** — the offline architecture is
  JVM/gate-provable; the perceived snappiness is not.
- After reading, returning to the schedule and marking the reading done is unchanged — the
  marking and the reading now live in the same app. **AC.**

### U14 — Browse to any book and chapter

*As David, I want to open the Bible to any book and chapter directly, so I can look something
up without it being one of today's readings.*

- Given the app is open, when I select the **Bible** destination in the bottom navigation, the
  reader opens. **AC.**
- From the reader I can pick any of the 66 books and any chapter within it via a two-step
  book → chapter picker. **AC.**
- I can move between adjacent chapters (Prev/Next and/or swipe) without returning to the picker.
  **AC.**

### U15 — Read with faithful KJV formatting

*As David/Ruth, I want the text to look like the King James Bible, so reading in the app feels
like reading scripture, not a stripped data dump.*

- Translator-supplied words (KJV's italicized added words, e.g. "there **was** light") render in
  *italic*. **AC** (pinned by the markup gate, §8).
- Psalm superscriptions ("A Psalm of David, when he fled…") render as an unnumbered italic
  heading **before** verse 1, never as verse 1 and never dropped. **AC** (pinned by the
  superscription gate, §8).
- Verse numbers are present but de-emphasized; the surface reads as flowing prose, not a table.
  **AC (device-pass)** (visual reading-feel — design review; the markup *content* that drives it
  stays gate-provable, §8).
- Poetry/line structure where the source provides it renders as lines, not run-on prose. **AC**
  (where source markup supports it; see Open Question OQ-4 on poetry-markup availability).

### U16 — Choose my reading destination at first run

*As any new user, I want to be asked once whether to read in the app or in an external app, so
the choice is mine and I am not silently defaulted.*

- On first run (extending the Sprint-19 first-run flow), I am asked a single question:
  *"Read in the in-app Bible, or open an external app?"* My answer sets the "Open readings in"
  preference. **AC.**
- The in-app option is **not a silent default** — if I never answer, the app must not have
  quietly chosen in-app for me (the question is the gate). **AC** (per owner decision #4).
- Existing users upgrading to V3 keep their previously-chosen external provider; they are **not**
  re-prompted and not silently switched to in-app. **AC** (the D-S14-1 indistinguishability
  trap — preserve explicit prior choices).

### U17 — Switch my reading destination later

*As any user, I want to change where readings open, in Settings, so my first-run answer isn't
permanent.*

- The existing Settings → "Open readings in" control now offers **In-app Bible** as a real,
  selectable destination (the former disabled `provider-option-inapp` teaser becomes a real
  `BibleProvider.IN_APP`). **AC.**
- All external providers (Blue Letter Bible, Bible Gateway, YouVersion, MySword) remain in the
  same control, unchanged. **AC.**
- Changing the destination takes effect on the next reading tap; no restart. **AC.**

### U18 — Move between the planner and the Bible

*As any user, I want the planner and the Bible to be two clear, co-equal places in the app, so I
always know where I am and can get to either.*

- The app presents a bottom navigation with two co-equal destinations: **Schedule** and
  **Bible**. **AC** (owner decision #3 — co-equal, bottom nav).
- **Schedule** is the start destination — the app still opens to today's readings. **AC.**
- Switching destinations preserves each destination's state (the reader remembers where I was;
  the schedule remembers the day I was on) within a session. **AC (device-pass)** (nav
  back-stack state preservation — `AppNavHost` push/pop is historically JVM-untested, CLAUDE.md
  Sprint 6 debt; see R-V3-5).

---

## 6. Functional requirements

Continuing the PRD's FR-numbering (V1/V2 ended at FR-23). V3 requirements are **FR-V3-1…** to
keep the V3 set legible at a glance and avoid collision as the V1/V2 list evolves.

### P0 — must ship in V3.0 (the smallest lovable reader)

- **FR-V3-1 (U13, U14, G6)** An **in-app chapter reader** renders the KJV text of a given
  book/chapter, as a list keyed per verse, addressable by verse-id. *(The verse-keyed structure
  is a P0 requirement, not a polish item — it is the seam audio and verse-level features later
  depend on, and retrofitting it is a rewrite; see §7/NFR.)*
- **FR-V3-2 (U13, G6)** Tapping a reading whose destination is **in-app** opens the reader to
  that reading's portion and renders the **whole portion** in sequence — every chapter of a
  multi-chapter portion, and both books of the two-book portion (Jun 19 / Dec 19). This requires
  a **Portion → verse-id bridge** that maps each `Reference` in a `Portion` to its verse-id
  range. *(This bridge is the MVP, not a detail — it is the only thing connecting our
  chapter-keyed plan to the verse-keyed text.)*
- **FR-V3-3 (U14, G8)** A **Reference resolver** parses and formats references
  (e.g. "John 3:16", a range, a whole chapter, a whole book, verse-0 superscriptions) to and
  from verse-id ranges. Malformed input must **fail cleanly** — it must never silently resolve
  to a plausible-but-wrong verse (the worst failure mode for a scripture app). **AC: pinned by
  the resolver gate, §8.** **Invariant:** a verse-level `Reference` must allow **verse ∈ [0, 999]**
  (verse 0 = superscription) — it must **not** carry a `require(verse >= 1)` guard, which would
  silently drop every Psalm title. (The live `Reference` already has the analogous
  `require(chapter in 1..chapterCount)` guard at chapter granularity, so this lower-bound trap is
  real; the lower bound must be **0**, not 1.)
- **FR-V3-4 (U14, G8)** A **two-step book → chapter picker** (books grouped by testament →
  chapter selection) lets the reader open any of the 66 books and any chapter.
- **FR-V3-5 (U14)** The reader supports moving between adjacent chapters (Prev/Next, and/or
  chapter swipe) without reopening the picker.
- **FR-V3-6 (U15, G9)** The reader renders the closed KJV markup vocabulary faithfully:
  **translator-added words in italic**, and **Psalm superscriptions as unnumbered italic
  headings at verse 0** (before verse 1). Verse numbers are present but visually de-emphasized.
  **P0 contract:** the markup tag vocabulary is a **closed, versioned contract** that the
  renderer and the §8 strip-invariant are both defined against — the gate asserts "only the
  closed vocabulary appears," and a future swappable artifact (NFR-V3-E) **may not introduce a
  tag the reader does not render.** Adding a tag is a versioned change to this contract, not a
  silent artifact edit.
- **FR-V3-7 (U16, G11)** A **first-run reading-destination question** (extending the Sprint-19
  first-run flow) asks the user to read in the in-app Bible or an external app, and sets the
  "Open readings in" preference accordingly. In-app is **not** applied as a silent default.
- **FR-V3-8 (U17, G11)** Settings → "Open readings in" offers **In-app Bible** as a real
  selectable `BibleProvider.IN_APP` value (promoting the existing teaser), alongside the
  unchanged external providers; the choice persists and is read at tap time.
- **FR-V3-9 (U16, U17)** Upgrading users' existing explicit external-provider choice is
  **preserved** — they are not re-prompted and not silently switched. (New installs answer the
  first-run question.)
- **FR-V3-10 (U18, G6)** The app presents a **bottom navigation** with two **co-equal**
  destinations, **Schedule** and **Bible**; **Schedule is the start destination**.
- **FR-V3-11 (G7)** The reader and all V3 functionality work **fully offline**. V3 adds no
  networking dependency; `INTERNET` is not in the manifest. The KJV text ships **bundled** as a
  read-only on-device asset.
- **FR-V3-12 (G9, M-V3-1)** The bundled KJV text passes an automated **verification gate**
  (Sprint-1-style, run against the exact asset shipped in the APK) that gates release: it
  asserts the structural and content invariants of §8. *This is a release blocker, not a
  nice-to-have* (per the team review — Riley).
- **FR-V3-13 (U13, U14)** The reader is a **calm reading surface**: no streaks, stats, progress
  chrome, or tracking controls appear in the reader. (Marking stays on the schedule.)

### P1 — strongly desired in V3.0 if cheap; otherwise first follow-up

- **FR-V3-14 (U15)** Poetry/line-structure renders as lines where the source markup provides it
  (gated on source-markup availability — OQ-4).
- **FR-V3-15 (U13, U18)** The reader persists the last-read position (canonical verse-id) within
  a session so returning to the Bible destination resumes where the reader left off. *(Durable
  cross-session last-read is a V3.x candidate — §9; the in-session restore here is the cheap
  subset.)*
- **FR-V3-16 (U15)** Theme-aware optional **red-letter** (words of Christ), **off by default**.
  *(Only if the source markup carries words-of-Christ tagging cheaply; otherwise defer.)*
- **FR-V3-17 (U13, U14)** The reader honors the existing app-wide **text-size** setting
  (`fontScale`, Sprint 8) so the reader's type scales with the rest of the app.

### P2 — explicitly out of V3.0 (tracked for V3.x / V4 — see §9)

- Highlights, bookmarks, durable cross-session last-read (V3.x).
- Full-text search over the KJV (V3.x, FTS5 — additive build step).
- Android App Links / deep-linking into the reader (V3.x — the resolver already supports it; the
  remaining work is wiring).
- Audio follow-along (V4 — gated on the network decision).
- Multi-translation + versification-mapping app logic (V4/speculative — no demand).

### Non-functional requirements (V3-relevant)

- **NFR-V3-A — Offline / no-network.** V3 introduces no networking and no analytics. The Play
  data-safety disclosure ("no data collected") is unchanged. (Reinforces FR-V3-11, FR-V3-7's
  no-network posture.) *This is load-bearing identity, not a preference.*
- **NFR-V3-B — Verification gate as a correctness requirement.** The KJV text is the project's
  **second core IP asset** (the plan was the first) and is held to the same discipline: an
  offline, CI-gating verification test against the shipped asset (FR-V3-12; detail in §8).
  Release is blocked on it.
- **NFR-V3-C — Accessibility parity.** The reader meets the existing accessibility bar (the
  `AccessibilityGateTest` line — 48dp touch targets on authored controls, meaningful TalkBack).
  Specifically: **TalkBack speaks the plain text, not the markup**; superscriptions and any
  section headings carry heading semantics; the reader remains usable at large font scales.
- **NFR-V3-D — Bundle-size budget.** The KJV text asset is expected to add a few megabytes
  (~4–5 MB order of magnitude) to the APK/bundle. The exact budget and the plain-vs-derived
  text-storage tradeoff are Diego's call; the **product constraint** is that V3 stays well under
  the Play bundle ceiling and does not bloat the lean app character — and that **audio's
  ~1.6–2 GB is precisely why audio cannot be bundled and is V4** (§3).
- **NFR-V3-E — Version-agnostic durability (product requirement, not only architectural).** App
  logic operates on verse-ids and references and is **indifferent to which text version is
  loaded**; the text is a swappable, encapsulated artifact behind a single seam
  (`getVerses(range) → List<VerseText(canonicalId, nativeLabel, markup)>`). Book structure stays
  in `BookCatalog`. This is required because it is how we keep the promise to "get the text
  right" over years: a corrected KJV is an artifact swap with zero logic change, and the reader
  must read each verse's **native display label from the seam**, never assume display-number ==
  the verse-id's verse component (so a future differently-numbered artifact still displays
  faithfully). Per the owner's architecture principle (review doc).
- **NFR-V3-F — Faithful display is non-negotiable.** Text is displayed in the version's own
  native numbering and with its own gaps (Rule 1 of the versification model). For KJV-alone,
  native == canonical, so this is free today; the seam (NFR-V3-E) is what keeps it true if the
  artifact ever changes.

---

## 7. Product-level data & architecture intent (not a schema)

This is product-level intent only — Diego owns the schema, storage format, and module layout.
It is recorded here because three of these are **product-durability** decisions the PRD must
hold the line on:

- **The verse-id spine is the product's addressing scheme.** Every verse has one stable id;
  references and portions resolve to verse-id ranges; the reader is keyed by verse-id. The
  reader being **verse-addressable from day one** is a P0 product requirement (FR-V3-1), not an
  optimization, because it is the seam later features (audio highlight, verse highlights,
  deep links) require and cannot be retrofitted cheaply.
- **Book structure lives in the app (`BookCatalog`), not in the text artifact.** We already have
  one test-pinned 66-book catalog; the text artifact carries only verse-id → markup (+ native
  label). This is the clean reconciliation of "no duplicate book table" (the team's anti-drift
  finding) with "swappable encapsulated text" (the owner's principle). The verification gate
  checks the artifact against `BookCatalog`'s expected coverage. The artifact's book structure is
  therefore **generated from `BookCatalog` at build time and verified field-identical**, not
  authored separately — so the tech spec can lean on that guarantee rather than re-assert it.
- **User data is keyed canonically and lives in the read-write store, not the text asset.** When
  highlights/bookmarks/last-read arrive (V3.x), they key to the canonical verse-id and live in
  the existing read-write `ProgressDatabase` — **never** in the read-only text asset, which is
  re-shipped (and would be wiped) whenever we correct the text. (A real bug the team review
  caught in the source spec; recording it here so V3.x doesn't reintroduce it.) **And the
  converse rule:** `createFromAsset` does **not** re-copy once the DB exists, so a shipped text
  correction (e.g. a superscription fix) would never reach existing users — therefore **shipped
  text corrections must reach existing users via an asset content-version bump that triggers
  re-copy-on-update.** This re-copy wipes the asset DB, which is exactly *why* user marks/
  highlights must live in the read-write store and never in the asset.
- **The text artifact is swappable behind one seam.** `getVerses(range)` returns text plus a
  **native display label** per verse. For KJV the label is free (native == canonical); the door
  is kept open for a future differently-numbered artifact without any app-logic change
  (NFR-V3-E).

What the product explicitly does **not** carry in V3: audio tables, a versification-mapping app
table, or a second translation. These are documented future *design* (well-formed, just not
built) — see §11.

---

## 8. The KJV verification gate (release blocker)

Mirroring the Sprint-1 plan-data discipline that made the plan trustworthy, the KJV text gate
is a **release blocker** (FR-V3-12, NFR-V3-B). It runs offline, in `testDebugUnitTest`, against
**the exact text asset shipped in the APK**. Product owns *that it must pass to ship* and *what
correctness means*; Diego owns the mechanism. The correctness bar:

- **Structural invariants:** exactly 66 books / 1,189 chapters / 31,102 verses; no duplicate
  verse-id; **verse numbering contiguous within every chapter** (the analog of Sprint 1's
  coverage check — catches a verse dropped at an XML boundary, the likeliest defect); verse-id
  encoding internally consistent; no empty verse text; coverage matches `BookCatalog`'s
  chapter counts.
- **Independent second-source equality:** a checked-in independent witness of per-chapter verse
  counts, asserted chapter-by-chapter, plus a build-time full-text diff against a **second,
  genuinely independent** corpus with a reconciliation log (mirroring Sprint 1's 7-conflict
  table in `docs/data/README.md`). **Guard the Sprint-1 trap:** confirm the two sources are not
  the same upstream re-mirrored (checksum them).
- **Superscriptions:** a checked-in list of the **exact** set of titled chapters (most Psalms
  but not all — Pss 1, 2, 10, 33 have none; Habakkuk 3 does), asserted both directions, with
  spot-pinned title text (Ps 3, Ps 51) to catch the "folded into verse 1" failure.
- **Markup:** the strip invariant (`plain == strip(markup)`) for all verses; only the closed tag
  vocabulary appears; a coverage **floor** on added-word count (zero added words ⇒ the parser
  silently dropped them) plus hand-pinned added-word verses.
- **Reference resolver:** exhaustive pure-logic pins — "John" vs "1/2/3 John", ranges,
  cross-chapter, verse-0 — and **malformed input fails cleanly, never silently mis-resolves**.
- **Reproducible import:** a committed build script with pinned source SHA / input checksum,
  deterministic output, and the checked-in text asset protected by a CI job that re-derives and
  diffs it.

> *This is the §8 of a PRD, not an engineering spec: the list above is the **product
> correctness contract** — "what 'the text is right' means." Diego's engineering spec turns it
> into the actual test file(s) and CI jobs.*

---

## 9. Release scoping (product narrative)

### V3.0 — the smallest lovable reader (this release)

**Ship:** bundled KJV text + the chapter reader (verse-id-keyed) + the Reference resolver +
the Portion → verse-id bridge + the first-run reading-destination question + the bottom-nav
restructure (Schedule + Bible, co-equal, Schedule as start) + reading-tap → in-app handoff +
`BibleProvider.IN_APP` promoted in Settings + the import done right once (verse-0
superscriptions, added-word markup, verse-id spine) + the **verification gate**. External
providers demoted to opt-out (kept, not retired). UK licensing risk recorded as accepted.

**Why this cut.** This is the complete arc from "tap a reading" to "read it in the app and come
back to mark it," plus free browsing — the whole reason in-app text exists. Everything that is
*additive on top of a working reader* (highlights, search, deep links) is deferred so the reader
itself ships first and proves out. The two "get-it-right-now-or-re-import-later" content items
(verse-0 superscriptions, added-word markup) are **in** V3.0 because deferring them means a
painful re-import; everything genuinely additive is **out**.

### V3.x — real candidates, demand-ordered (not committed)

- **Highlights / bookmarks / durable last-read position** — the most-requested reader
  enhancement once people live in the reader; keyed canonically in the read-write store (§7).
- **Full-text search** over the KJV (FTS5 — an additive build step on the same asset).
- **Android App Links / deep links into the reader** — the resolver already supports references
  and URIs; the remaining work is wiring, with no data work.
- **Study notes** (dailyreadings.org.uk-style) — a content feature, naturally enabled now that
  text lives in-app; parked, not committed.

These are sequenced by observed demand (§10), not pre-committed.

### V4 / speculative (explicitly gated)

- **Audio follow-along** — verse-synced narration. **Blocked on an explicit owner decision to
  become a networked app** (the ~1.6–2 GB asset cannot be bundled). A larger product than all of
  V3. The verse-id spine keeps the door open; nothing is built until that decision is made.
- **Multi-translation + versification mapping** — no community demand; the app is KJV-anchored.
  The version-agnostic seam (NFR-V3-E) makes a second translation additive *if* it is ever
  wanted; the **mapping data would ride inside the artifact**, not in app logic.

---

## 10. Success metrics (within the no-analytics reality)

The app has **no analytics or telemetry** (settled, V1) and V3 does not change that. So V3's
metrics are **release gates and owner-observable/qualitative signals**, not dashboards —
consistent with how V1/V2 metrics (M1, M8) work.

- **M-V3-1 — Text accuracy (release gate).** The KJV verification gate (§8) passes in CI: 66
  books / 1,189 chapters / 31,102 verses, second-source equality, superscriptions, markup, and
  resolver invariants all green. *This is a hard gate, like M1 was for the plan.*
- **M-V3-2 — Faithful presentation (qualitative gate).** Owner (and design review, per the
  Sprint-16 precedent) sign off that the rendered KJV reads faithfully — italic added words,
  Psalm titles as headings, de-emphasized verse numbers, calm surface — across a spot set of
  chapters incl. Genesis 1, a titled Psalm (3/51), an untitled Psalm (1), and the Jun 19
  two-book portion. *Explicit sign-off, since "faithful/reverent" is the owner's bar, mirroring
  M8.*
- **M-V3-3 — Offline correctness (device-pass).** With the device offline, tapping a reading
  opens the in-app text with no spinner and no failure; the Bible destination browses any
  book/chapter offline. Device-pass checklist item.
- **M-V3-4 — Whole-portion correctness (gate + device-pass).** Multi-chapter portions and the
  two-book portion (Jun 19 / Dec 19) render the complete portion in sequence — pinned by the
  Portion-bridge tests and confirmed on-device.
- **M-V3-5 — Accessibility parity (gate).** The reader passes the accessibility gate: TalkBack
  speaks plain text not markup, headings carry heading semantics, touch targets and font scaling
  hold. Extends the existing `AccessibilityGateTest`.
- **M-V3-6 — First-run choice integrity (gate).** New installs are asked the reading-destination
  question and in-app is never applied as a silent default; upgraders' explicit prior choices
  survive. Pinned by tests (the D-S14-1 indistinguishability discipline).
- **M-V3-7 — Bundle budget (gate).** The release bundle stays within the agreed size budget
  (NFR-V3-D) — the build fails or flags if the text asset pushes it past budget.
- **M-V3-8 — Adoption signal (owner-observable, no telemetry).** Learning comes from the owner's
  circle of testers, as with V2's M4: at first run, do people choose in-app or stay external? Do
  they use the Bible destination to browse beyond the day's readings? Worth asking testers
  directly — there is no instrumentation and we are not adding any.

---

## 11. Open questions, risks & accepted risks

### Accepted risks (recorded, not blocking — owner-decided)

- **AR-1 — UK KJV licensing (ACCEPTED).** KJV is public domain worldwide **except the UK**,
  where it is under Crown copyright (Cambridge / the King's Printer). The owner has decided to
  **accept this risk: do not geo-restrict, do not alter the text.** It is a near-theoretical risk
  for a free, non-commercial app (Cambridge grants broad free use; every free Bible app serves
  KJV in the UK). **Optional courtesy action:** file a free-use permission request with Cambridge
  for certainty. *This accepted risk is recorded here as required before ship.*

### Risks (need attention, not yet decided)

- **R-V3-1 — Nav real-estate cost.** Bottom navigation permanently spends ~80dp and reframes the
  app as "planner + reader." Sprints 16/18/20 spent four sprints reclaiming one-screen fit on the
  schedule; the bottom bar gives some of that back. The owner has **decided co-equal bottom nav
  anyway** (decision #3) and **accepted the one-screen-fit cost** — this risk is therefore
  *accepted*, but Morgan/Priya should confirm the schedule still degrades gracefully on a P7P at
  default font with the bottom bar present, on the device pass.
- **R-V3-2 — Bundle size.** The text asset's exact size and the plain-vs-derived storage tradeoff
  (Diego's call) determine whether we stay comfortably under budget (NFR-V3-D, M-V3-7).
- **R-V3-3 — KJV source provenance & the "same upstream" trap.** The verification gate's value
  depends on the two sources being **genuinely independent** (not the same corpus re-mirrored).
  Source selection and checksum-distinctness is a data task (Diego/Riley) and a gate
  prerequisite.
- **R-V3-4 — External-provider feature partial obsolescence.** In-app text obsoletes much of the
  V2 "Open readings in" feature but the decision is **demote, not retire** — external providers
  stay for power users (YouVersion notes, MySword libraries). The only product change is adding
  in-app as a real option and as the first-run-offered default-leaning choice; no chooser-UI
  redesign.

- **R-V3-5 — Nav-restructure blast radius (delivery/regression).** The co-equal bottom-nav
  restructure (FR-V3-10) wraps **every existing screen** in a new M3 `NavigationBar` scaffold and
  touches the `AppNavHost` that CLAUDE.md records as JVM-untested (Sprint 6 debt, never retired).
  This is a *delivery/regression* risk distinct from R-V3-1's real-estate cost: the restructure
  sprint must **budget for nav-regression verification** — either Robolectric nav coverage built
  as part of the work, or an expanded device-pass confirming every existing screen stays
  reachable (Settings, Stats, the day-pager) and back-stack behavior is intact. Ties to the U18
  **AC (device-pass)** above.

### Open product questions (need the owner or a teammate)

- **OQ-1 — Owner:** at first run, when the user is asked "in-app Bible or external app?", should
  in-app be the **pre-selected / recommended** option (clearly the app's intended path) while
  still being an explicit answer — or should the two be presented perfectly neutrally? (Decision
  #4 settled that in-app is *not a silent default*; this is the softer question of emphasis
  within the question itself.)
- **OQ-2 — Owner:** for the **upgrade** path, is leaving existing external-provider users exactly
  as they are (no prompt, no banner) the right call — or do you want a one-time, dismissible
  "the in-app Bible is here, switch in Settings" notice? (Recommendation: leave them be; it
  respects their explicit choice and avoids a nag. Flagged because it is a judgment call.)
- **OQ-3 — Owner / Priya:** the **Bible bottom-nav label and icon** — "Bible" vs "Read" vs a book
  glyph only; and whether the schedule destination is labeled "Schedule," "Today," or "Plan."
  (Tone/wording sign-off, like the V2 M8 string reviews.)
- **OQ-4 — Diego/Riley (data):** does the chosen public-domain KJV source carry **poetry/line
  structure** and **words-of-Christ** markup cleanly? This gates FR-V3-14 (poetry) and FR-V3-16
  (red-letter) — both P1, both droppable if the source doesn't supply them cheaply. Added-word
  markup and superscriptions are **required regardless** (P0, FR-V3-6).
- **OQ-5 — Owner (tone):** the courtesy UK free-use permission request to Cambridge (AR-1) —
  worth filing for certainty, or leave the accepted risk as-is?
- **OQ-6 — Morgan/Diego (scope confirmation):** confirm the V3.0 cut line (§9) holds — in
  particular that **highlights/bookmarks/search/App Links are V3.x**, not smuggled into V3.0.
  Product position: V3.0 is the reader; everything additive waits.

---

## 12. Dependencies

> **The one critical path (must be sequenced, not parallelized).** There is a single ordered
> chain the rest of V3 hangs off: **KJV PD source selection (incl. the same-upstream-remirror
> checksum-distinctness check, R-V3-3) → reproducible import pipeline → committed `bible.db`
> asset → verification gate (§8).** Source-selection + checksum-distinctness is a **gate
> prerequisite that must land before any reader-UI work can be *verified*** — a planner must not
> naively parallelize source selection against reader UI, because there is no asset to render or
> gate against until this chain completes. (The asset's book structure is **generated from and
> verified field-identical against `BookCatalog`** as part of this chain — see §7 and the
> `BookCatalog` dependency below.) The remaining dependencies are peers of each other but
> downstream of this chain.

- **The KJV verification gate (§8)** — a release blocker (FR-V3-12). Depends on selecting two
  genuinely-independent PD KJV sources (R-V3-3) and a reproducible import. *This is the tail of
  the critical path above.*
- **The data import** — a build-time pipeline producing the bundled text asset with verse-0
  superscriptions and added-word markup done correctly *the first time* (re-import is the
  expensive failure). Owned by Diego/Riley; product owns the correctness bar (§8).
- **`BookCatalog` reconciliation** — the text artifact's structure is verified against the
  existing test-pinned 66-book `BookCatalog` (chapter counts → expected verse-id coverage). The
  catalog stays the single home of book structure (§7); the artifact carries verse text only.
- **The Sprint-19 first-run flow** — FR-V3-7's reading-destination question extends it; depends
  on that flow being the integration point.
- **The existing `BibleProvider` / "Open readings in" preference + `OpenReferenceUseCase`** —
  FR-V3-8 promotes the `provider-option-inapp` teaser to a real value and routes the in-app
  destination through the existing tap-time provider read.
- **The existing read-write `ProgressDatabase`** — the home for any V3.x user data (highlights /
  bookmarks / last-read), keyed canonically; **not** the read-only text asset (§7).
- **The `fontScale` setting (Sprint 8)** — the reader honors it (FR-V3-17).
- **The accessibility gate (`AccessibilityGateTest`, Sprint 9)** — extended to cover the reader
  (NFR-V3-C, M-V3-5).

---

> **Review note (for Morgan + Diego):** this PRD is intentionally written to be reviewable
> against the architecture-review doc — every settled decision it builds on (no audio / V4;
> accepted UK risk; co-equal bottom nav; first-run choice; version-agnostic swappable artifact;
> the V3.0 MVP cut; the verification gate as blocker) traces to that doc's "Owner decisions" and
> "Architecture principle" sections. The open product questions are collected in §11; the
> engineering and sequencing questions (storage format, plain-vs-derived text, test mechanics,
> sprint breakdown) are deliberately **left to your specs** and not pre-decided here.
