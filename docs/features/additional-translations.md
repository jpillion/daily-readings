# Feature: additional Bible translations

Owner-requested. Proposed sprint ids: `sprint-00R-translation-seam` (Phase 1),
`sprint-00S-licensed-translation` (Phase 2).

**Status:** spec drafted, not started. Phase 2 is blocked on nothing technical, but see
**OQ-3** (embedded API key) — that decision changes the Phase 2 architecture and should be
settled before implementation starts.

## The problem

The app ships exactly one translation (KJV, bundled offline since V3 Sprint A). The owner wants
more. Two different kinds of "more", with very different costs:

1. **Public-domain translations** — free, bundleable, offline, no runtime cost.
2. **Licensed translations** — cannot be bundled at all; only reachable live through a licensed
   API, with a quota, a network dependency, and reporting obligations.

The V3 schema anticipated this: `verse`'s primary key is already `(translation_id, verse_id)`, the
`translation` table already carries `code`/`name`/`language`/`is_public_domain`/`copyright`, and
Sprint 00N built (and tested) the multi-version dropdown in `ReaderVersionSelector` — dormant only
because there is one version to show. `selectVersion` is a no-op placeholder awaiting this work.

## What the research established

### Bundleable (public domain)

Verified present in **both** established source lineages — open-bibles/Haiola/eBible and
scrollmapper/e-Sword — so the two-independent-witness gate is satisfiable:

| Translation | Lineage A | Lineage B | Complete | Versification |
|---|---|---|---|---|
| **WEB** (World English Bible) | ✅ USFX | ✅ | ✅ | Majority text — KJV-compatible |
| **BSB** (Berean Standard Bible) | ✅ USFX | ✅ | ✅ | Critical text — has gaps |
| ASV 1901 | ✅ Zefania | ✅ | ✅ | Critical text — has gaps |
| BBE | ✅ USFX | ✅ | ✅ | KJV-ish |
| YLT | ⚠️ **NT only** | ✅ | ❌ | — |

BSB was placed in the public domain outright on 30 April 2023. YLT fails the pairing because the
lineage-A copy is New Testament only.

**Caveat on BSB:** its only authoritative source is its publisher, so both lineages may trace back
to the same upstream — the re-mirror trap hit during the Chronological plan work. If that proves
true, BSB needs the **D-ALT-24** treatment (designated single source + rigorous structural gate +
owner-accepted risk) rather than a true two-witness gate. **WEB does not have this problem** and is
the lower-risk first bundled addition.

### Licensed (API only)

Owner holds an **API.Bible Pro plan: 150,000 API calls/month**.

Verified live against the account's key on 2026-08-06:

- **NKJV is available and fully working** — id `63097d2a0a2f7db3-01`, full OT+NT, content fetches
  clean. Required attribution: *"Scripture taken from the New King James Version®. Copyright © 1982
  by Thomas Nelson. Used by permission. All rights reserved."*
- **NASB is NOT available to the key.** Absent from all 247 Bibles returned, across two clean
  cache-MISS requests, after the owner added it to the plan. ABS does carry NASB (they partner with
  Lockman), so this is an entitlement/approval matter, not a catalogue gap. Lockman is the most
  protective of the publishers — a per-application approval step is the likeliest explanation.

**NASB is out of scope for this spec** until the entitlement lands. See Phase 3.

## D-T-1 — NKJV needs no versification map

Measured directly against the API, not assumed:

| | KJV | **NKJV** | NASB |
|---|---|---|---|
| Matthew 17 | 27 verses (v21 present) | **27 — v21 present** | 26, jumps 20→22 |
| 3 John | 14 verses | **14** | 15 (v14 split) |

NKJV is Textus Receptus–based and aligns with KJV exactly. **The `versification_map` designed in
`bible-data-architecture.md` §6 stays deferred** (it was cut from the shipped DB by D-V3-2 and
remains cut). The existing `verse_id` scheme, the reading plan, Psalm 119 verse windows, per-verse
tap-out (Sprint H) and verse selection/copy (Sprint 00Q) all key identically across KJV and NKJV
with zero remapping.

This is the single largest cost saving available, and it is specific to NKJV. **NASB would require
the entire versification apparatus** — its gaps and splits are literally the worked examples in §6.

## D-T-2 — licensed text NEVER enters `bible.db`

`app/src/main/assets/bible/bible.db` is a read-only asset, committed to git and reproduced
byte-identically by the `data-rebuild` CI job. Putting licensed text there would be redistribution,
and would break the byte-diff gate.

- **Bundled translations** → `bible.db`, via the existing `tools/build_bible_db.py` pipeline,
  written as additional `translation_id` rows. No schema change (the PK is already composite).
- **Licensed translations** → a **separate, writable, gitignored** cache database. Never the asset,
  never committed, never in the rebuild gate.

## D-T-3 — one seam, two implementations

`BibleTextSource` is already the only thing the reader domain injects, and it is the correct seam.

```
BibleTextSource (interface, unchanged)
  ├── RoomBibleTextSource      — bundled: KJV, WEB, (BSB)   offline, free
  └── ApiBibleTextSource       — licensed: NKJV             network, quota, cached
```

`GetChapterUseCase`, `GetPortionTextUseCase`, `ReaderViewModel` and the whole reader UI are
unchanged. A dispatcher resolves which implementation serves a given `translation_id`.

## D-T-4 — fetch by passage, not by chapter

Verified: `/v1/bibles/{id}/passages/GEN.1-GEN.2` returns **both chapters in one call**, with one
FUMS token. Since the reader already renders a whole reading portion as one combined page
(Sprint I), a multi-chapter portion costs **one call, not N**.

Budget with this optimisation:

| | Calls/day | Calls/month |
|---|---|---|
| Bible Companion (3 portions) | 3 | ~90 |
| M'Cheyne (4 streams) | 4 | ~120 |
| Chronological (segmented) | ~2 | ~60 |
| Plus incidental Bible-tab browsing | | ~30 |

**≈ 120 calls per daily NKJV reader per month → ~1,250 daily readers within 150,000.**

Only users who actively switch to NKJV consume quota; KJV remains the bundled default.

## D-T-5 — the quota is a shared, exhaustible, global pool

This is the most important operational fact and it drives several rules:

- **Never prefetch.** No book pre-loading, no background warming, no "download for offline". Fetch
  strictly what the user is about to read.
- **Cache aggressively on device**, with a **30-day expiry** — required by API.Bible's terms
  ("cached content must be refreshed at least once every 30 days"), and it is also what keeps
  re-reads free.
- **Retry with strict bounds.** A retry loop is a quota incident, not an inconvenience.
- **Overage defaults to zero**, so exhaustion is a hard stop, not a surprise bill. At exhaustion
  NKJV stops working *for every user at once*.

## D-T-6 — degradation is to KJV, always

Any failure — offline, quota exhausted, API error, cache miss with no network — falls back to the
bundled KJV for that passage, with a quiet, non-alarming notice. The app must never show an error
state for scripture it already has offline.

The reading plan, marks, widget, reminders, stats and streaks **must never depend on the network**.
NFR-V3-A is narrowed, not abandoned: `INTERNET` becomes required, but only licensed *text* uses it.

## D-T-7 — FUMS reporting is implemented, not skipped

Every content response carries `meta.fumsToken`. FUMS is how ABS demonstrates usage value to
publishers, and it is the basis on which the licence exists. It gets implemented properly.

## Phases

### Phase 1 — `sprint-00R-translation-seam` (no network, no legal exposure)

De-risks the entire multi-translation surface using a bundled public-domain translation.

- Add **WEB** to `bible.db` as `translation_id = 2` via the existing importer.
- Its own verification gate, mirroring `BibleTextVerificationTest`: structural invariants,
  second-source verse-count equality, checksum-distinct lineages, famous-verse pins.
- Wire `ReaderVersionSelector`'s existing dropdown branch (built in 00N, currently unexercised).
- Persist the chosen translation in DataStore; `selectVersion` stops being a no-op.
- Reader header/title, verse labels and the Sprint 00Q clipboard citation all resolve per
  translation. **`ReadingFormatter.singularizeBookName` stays the one home** for the Psalm rule.
- Bundle-size gate: WEB is ~2 MB compressed against ~4.2 MB of headroom (AAB is 7.8 MB, ceiling
  12 MB). Fits. **BSB as well would not** — the ceiling needs raising first, and that is a
  deliberate decision, not a side effect.

**Ships standalone and is valuable on its own.** No `INTERNET`, no quota, no API key.

### Phase 2 — `sprint-00S-licensed-translation` (NKJV)

- `ApiBibleTextSource` + client, passage-granular (D-T-4).
- Writable cache DB with 30-day expiry (D-T-2, D-T-5).
- `INTERNET` permission, scoped narrowly; degradation to KJV (D-T-6).
- FUMS reporting (D-T-7).
- Attribution surfaced wherever NKJV text is shown, per the required copyright string.
- CI must assert the merged manifest gains **only** `INTERNET` — no other permission creep from the
  HTTP stack.

### Phase 3 — deferred: NASB + versification map

Only if the entitlement lands. Adds the full `versification_map` from
`bible-data-architecture.md` §6 (canonical spine, `exact`/`absent`/`merged`/`split`). Do **not**
build this speculatively.

## Open questions

**OQ-1 — Which bundled translation(s)?** Recommend **WEB** first (no re-mirror risk, KJV-compatible
versification). BSB is the more readable modern text but carries the single-source question and
would require raising the bundle ceiling.

**OQ-2 — Is NKJV worth Phase 2 at all?** Honest framing: NKJV costs a network dependency, a cache,
a quota to administer, an API key to protect, and an ongoing bill — to add a translation that is
*textually very close to the KJV you already ship*. WEB or BSB adds more genuine reading variety
for zero runtime cost. **Owner's call**, but Phase 1 should ship first regardless.

**OQ-3 — How is the API key protected? (decide before Phase 2 starts)**

An API key shipped inside an APK is extractable — obfuscation only raises the effort. Combined with
D-T-5 (a single shared exhaustible pool), an extracted key means someone can burn 150,000 calls and
kill NKJV for every user, with rotation requiring an app update.

Three options:

1. **Embed and accept.** Simplest. Rotate on abuse, which means shipping an update. Risk is real
   but the target is unglamorous.
2. **Thin pass-through proxy.** A small server holding the key, forwarding requests, **storing no
   scripture**. This protects the key, allows per-device rate limiting, and centralises FUMS.
   Note this is *categorically different* from the previously-rejected mirror: it redistributes
   nothing and creates no dataset. It is a legitimate and common architecture — the objection
   earlier was to caching the whole corpus, not to a proxy. Cost: infrastructure the project has
   never had.
3. **Drop Phase 2** and ship only bundled public-domain translations (see OQ-2).

**OQ-4 — NASB entitlement.** Check the dashboard for a pending/approval status on NASB and raise
with support@api.bible, quoting the `/v1/bibles` response as evidence.

## Explicitly out of scope

- Any mirroring, bulk download, or bundling of licensed text (prohibited; see the licensing
  discussion in `docs/data/README.md`).
- The `versification_map` (D-T-1 — unnecessary for NKJV).
- Audio, FTS search, and translation *comparison* views.
- Changing the reading plan data in any way. Plans are translation-independent.
