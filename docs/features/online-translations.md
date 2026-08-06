# Online translations — NKJV + NASB

**Status:** spec, owner-approved on the load-bearing decisions. Not yet implemented.
**Scope:** NKJV **and** NASB 2020, served from API.Bible through the Cloud Run proxy
([backend/README.md](../../backend/README.md)). Bundled KJV is unchanged and stays the default.
**Out of scope (owner, explicitly next iteration):** background prefetch of adjoining chapters.

---

## Why this is smaller than it looks

Three findings from probing the live API (2026-08-06) collapsed most of the anticipated work.

**1. Every reading portion is exactly ONE API call.** Verified against the awkward cases:

| Portion shape | Request | Result |
|---|---|---|
| Multi-chapter (Genesis 1–2) | `GEN.1-GEN.2` | ✅ one call |
| Verse window (Psalm 119:1–40) | `PSA.119.1-PSA.119.40` | ✅ one call |
| Two-book (Jun 19 / Dec 19) | `2JN.1-3JN.1` | ✅ one call |
| 5-chapter span (prefetch shape) | `GEN.1-GEN.5` | ✅ one call |

So a day's reading in a non-KJV version costs **one** call, not one per chapter. This is what makes
the deferred prefetch cheap, and it is why the budget guard's 140K/month ceiling is comfortable.

**2. The reader already tolerates foreign versification.** D-V3-4 states the reader "MUST NOT assume
the displayed number equals `VerseId.verse(canonicalId)`", and `VerseText` carries `nativeLabel`
separately from `canonicalId`. That decision — made in Sprint B for exactly this eventuality — means
no numbering-remap layer is needed.

**3. The version-selector UI already exists.** Sprint 00N (D-N-3) built the dropdown branch and left
`ReaderViewModel.selectVersion` as a documented no-op. This feature exercises it for the first time.

---

## The versification problem, measured

Probed live rather than assumed:

| | KJV (bundled) | NKJV | NASB 2020 |
|---|---|---|---|
| Matt 17:21 | present | present | **404 — does not exist** |
| 3 John | 14 verses | 14 verses | **15 verses** |
| Mark 16:9 | present | present | present, `[[bracketed]]` |
| Psalm 3 superscription | verse 0 row | `para style="d"`, unnumbered | `para style="d"`, unnumbered |

**NKJV matches KJV verse-for-verse** (it is a KJV revision on the same Textus Receptus base).
**NASB genuinely diverges** — it lacks verses KJV has, and has verses KJV lacks.

### D-OT-1 — use API.Bible's *display* `verseId`, never `verseOrgIds`

API.Bible returns both. For NASB Psalm 3, display `PSA.3.1` carries `verseOrgIds: ["PSA.3.2"]` —
the org ids are **Hebrew** numbering, which counts the superscription as verse 1.

The **display** ids align with KJV numbering. So `canonicalId = VerseId.encode(...)` computed from
the display id lands on the app's existing KJV-based addressing for free.

Consequences, and both are acceptable without a mapping table:

- **A KJV verse absent from NASB** (Matt 17:21) simply yields no row. The verse-id-keyed
  `LazyColumn` renders what it is given, so the verse is silently absent — correct behaviour, since
  that verse genuinely is not in NASB.
- **A verse NASB has and KJV lacks** (3 John 1:15) yields an extra row with a valid canonical id.
  `VerseId` already supports it; nothing overflows.

**This replaces the "versification mapping layer" originally scoped.** No table, no lookup.

---

## Decisions

### D-OT-2 — offline behaviour (owner)

Ordered fallback when a non-KJV version is selected:

1. Network → render, write to cache.
2. No network, **cache hit** → render from cache, silently. No banner.
3. No network, **cache miss** → render **bundled KJV**, with a persistent banner:
   > **Unable to download content, displaying KJV**

The banner is mandatory in case 3. A silent translation swap in a Bible app is unacceptable — the
user must always know which text they are reading.

### D-OT-3 — KJV stays bundled, offline, and the default

The planner and the KJV reader keep working with no network. Only NKJV/NASB require it. KJV is also
the fallback target in D-OT-2, so it must remain unconditionally available.

### D-OT-4 — `INTERNET` is added to the manifest (owner-approved)

This **retires NFR-V3-A** (the app's documented no-network identity, verified as recently as Alt
Sprint E). The manifest line carries a comment recording what it retires and why, so nobody later
"restores" it as cleanup. The CI merged-manifest assertion that pins the absence of INTERNET must be
updated in the same commit, not deleted.

### D-OT-5 — two layers: `BibleTextSource` (per artifact) + a resolver (selection, cache, fallback)

> **Corrected during implementation.** This decision originally read "`BibleTextSource` stays exactly
> as-is". That is wrong, and working through D-OT-2 exposed why: the banner has to know **which
> version was actually served**, and `getVerses` returning a bare `List<VerseText>` cannot express
> "you asked for NKJV, this is KJV". Recorded rather than quietly changed.

`BibleTextSource` keeps its exact current shape and meaning: *"give me verses from **this**
artifact."* Two implementations — the existing `RoomBibleTextSource` (bundled KJV) and a new remote
source per online version.

A new **resolver** sits above it and is what the use cases inject: *"give me verses for the user's
**selected** version, with cache and fallback."* It returns the verses **plus the version actually
served**, so the D-OT-2 banner is derived (`served != selected` ⇒ banner) rather than tracked as
separate mutable state that could race across pager pages.

Ripple is contained: `GetChapterUseCase` and `GetPortionTextUseCase` inject the resolver instead of
the seam, and `ChapterContent` carries the served version. `RoomBibleTextSource`, the reader, the
renderer, `PortionVerseBridge` and the whole spine are untouched.

`translations()` becomes bundled ∪ remote rather than a single asset-table read.

### D-OT-6 — USX → `VerseText` transformer (the largest work item)

The proxy currently requests `content-type=html` (`backend/main.py:198`); it moves to
`content-type=json`, which returns a USX tree carrying per-verse `verseId` attrs.

Tag mapping into the **closed** `BibleMarkup` vocabulary:

| USX | → | Note |
|---|---|---|
| `char style="add"` | `<a>` | translator-added; already P0 |
| `char style="wj"` | `<w>` | activates a reserved P1 tag |
| `para style="q*"` | `<l/>` | activates a reserved P1 tag |
| `para style="d"` | verse 0, `isTitle=true` | matches the bundled asset's superscription convention |
| `char style="sc"` / `"nd"` | *(dropped, text kept)* | see below |
| `para style="s"` / `"ms*"` | **open — D-OT-7** | section headings |

**Fidelity note:** dropping `sc`/`nd` renders divine-name "Lord" without small caps, where the
bundled KJV shows "LORD". Text is preserved; only styling is lost. Extending `BibleMarkup` with a
small-caps tag is the alternative and is a versioned edit to that file, never a silent change.

### D-OT-7 — section headings: RENDER them, in italics (owner, resolved)

NKJV and NASB both carry editorial section headings (`para style="s"`, e.g. "Morning Prayer of Trust
in God."; `para style="ms2"`, e.g. "Psalm 3"). The bundled KJV has **none**. The owner chose to
render them, italicised to differentiate them from scripture. They are excluded from verse selection
and clipboard output — they are editorial matter, not scripture.

### D-OT-10 — a heading attaches to the verse it precedes; it is NOT its own row

The obvious implementation — emit a heading as its own `VerseText` — **crashes Compose**.
`ReaderScreen` keys the list by canonical id (`items(block.verses, key = { it.canonicalId })`,
D-V3-12) and verse ids are dense integers, so a mid-chapter heading has no id available between
verse 8 and verse 9. Sharing the next verse's id is a duplicate key.

So `VerseText` gains `heading: String? = null`, rendered as an italic block **above** the verse
inside that verse's existing list item. Consequences:

- No new list item, so no key collision and no change to D-V3-12.
- Defaults to `null`, so the bundled KJV source and every existing test are untouched.
- Selection and clipboard operate on verses, so headings are excluded from both **by
  construction** — D-OT-7's exclusion needs no separate enforcement.

A superscription stays what it already is: a verse-0 row with `isTitle = true`. The two mechanisms
are distinct and must not be conflated — a superscription *is* canonical text with a verse id; a
section heading is not.

### D-OT-11 — USX style inventory (measured, not assumed)

Enumerated across captured NKJV + NASB payloads (Psalm 3, Genesis 1, Matthew 17, 3 John):

| USX | meaning | → |
|---|---|---|
| `verse/v` | verse marker | verse boundary |
| `char/it` | italic — translator-added in NKJV/NASB | `<a>` (same semantic as KJV italics) |
| `char/wj` | words of Christ | `<w>` — activates a reserved P1 tag |
| `para/q`, `q1`, `q2` | poetry line | `<l/>` — activates a reserved P1 tag |
| `para/p` | prose paragraph | paragraph break |
| `para/d` | superscription | verse 0, `isTitle = true` |
| `para/s`, `ms2` | section heading | `heading` (D-OT-10) |
| `char/sc` | small caps (divine name) | dropped, text kept |
| `char/qs` | Selah | dropped, text kept |

Any style **outside** this table must fail loudly rather than silently dropping text — an unmapped
style means scripture goes missing on screen. The transformer therefore keeps unknown-style text as
plain text (production never loses a word) **and** reports the style in
`UsxParseResult.unmappedStyles`, which is where the failure becomes visible.

**Verified against real payloads (2026-08-06).** The transformer was run over live NKJV + NASB
responses — NKJV Genesis 1 and Psalm 3, NASB Psalm 3, Matthew 17 and 3 John:

| Payload | Verses | Titles | Headings | Empty bodies | Unmapped styles |
|---|---|---|---|---|---|
| NKJV Genesis 1 | 31 | 0 | 1 | 0 | none |
| NKJV Psalm 3 | 9 | 1 | 1 | 0 | none |
| NASB Psalm 3 | 9 | 1 | 1 | 0 | none |
| NASB Matthew 17 | **26** | 0 | 3 | 0 | none |
| NASB 3 John | **15** | 0 | 1 | 0 | none |

Zero unmapped styles, and **zero verses lost their text**. The two divergences land exactly as
D-OT-1 predicts without any mapping table: NASB Matthew 17 yields 26 verses where KJV has 27
(v21 correctly absent), and NASB 3 John yields 15 where KJV has 14.

Per repo discipline these payloads are **not** committed — the suite pins the same behaviour on
synthetic USX, so no licensed NKJV/NASB text enters the repository.

### D-OT-8 — on-device cache

Cache is required by D-OT-2 and is the only place caching happens (the proxy deliberately stores
nothing — see "Why not cache here" in the backend README).

Keyed by `(versionId, VerseRange)`. Eviction and size cap TBD at implementation. **Blocked on the
licensing question below.**

### D-OT-9 — FUMS and copyright

Both are licence obligations and neither exists in the app today.

- The `fumsToken` from each response must be reported.
- The `copyright` string must be displayed wherever non-KJV text is shown.

Neither is optional; they ship with the feature, not after it.

---

## Licensing confirmation — owner-owned, NOT blocking implementation

**On-device caching of NKJV/NASB text is not yet confirmed as permitted.** The owner is sending the
confirmation and has explicitly directed implementation to proceed ahead of the reply, accepting
that a "no" is handled by turning caching off and degrading via D-OT-2 (every offline read falls to
the KJV banner). **This is why the cache sits behind an interface — a "no" must be a config change,
never a rewrite.**

The owner is asking support@api.bible:

1. Is a server-side proxy holding the key acceptable? *(outstanding since the backend was built)*
2. Is on-device caching of returned text permitted, and is there a retention limit?
3. Is prefetching adjacent chapters acceptable? *(scopes the next iteration)*

If caching is **not** permitted, D-OT-2 case 2 disappears and every offline read falls to the KJV
banner. The feature still ships; it just degrades harder. **Design the cache behind an interface so
this answer cannot force a rewrite.**

---

## Deferred to the next iteration (owner)

**Background prefetch of adjoining chapters** — ±2/±3 in each direction. The owner's reasoning is
sound and now measured: because a multi-chapter span costs one call (`GEN.1-GEN.5` verified), a
prefetch window is *cheaper* than paging chapter-by-chapter. Gate on question 3 above.

---

## Sequencing

1. Proxy → `content-type=json`; allow NKJV + NASB.
2. USX → `VerseText` transformer (pure, JVM-testable — the bulk of the work; pin against real
   captured payloads incl. Psalm 3, Matt 17, 3 John).
3. Routing `BibleTextSource` + `translations()` merge.
4. Cache behind an interface (D-OT-8).
5. Fallback chain + banner (D-OT-2).
6. Wire `selectVersion`; persist the choice.
7. FUMS + copyright (D-OT-9).
8. `INTERNET` + CI manifest assertion update (D-OT-4).

Steps 1–2 unblock everything else and should go first.
