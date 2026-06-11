# Feature spec: Choose your Bible app/site ("Bible app links")

**Status:** Candidate — owner-requested, **not scheduled** (not part of V2 Sprints 10–11).
**Author:** Maya (Product). **Needs before build:** Diego's technical spec + Morgan's sizing;
owner decisions in §10.
**Companion docs:** [docs/PRD.md](../PRD.md) (§12 candidate list),
[docs/data/README.md](../data/README.md) (Sprint 1 catalog/verification pattern).

---

## 1. Problem & user story

Today every reading tap builds a Blue Letter Bible (KJV) https URL; the OS routes it to the
BLB app if installed, else the browser (Custom Tab). That's a good default, but readers
already live in *their* Bible app or site — YouVersion installs are enormous, others prefer
Bible Gateway or Bible Hub — and being bounced to an unfamiliar destination adds friction to
the one tap that matters most (PRD G3).

- **U-BAL-1.** As a reader with a preferred Bible app/site, I want reading taps to open my
  chosen destination at the right chapter, so I read where I'm comfortable.
- **U-BAL-2.** As any user, I want a sensible default with zero setup (BLB, unchanged), so
  choosing is optional.
- **U-BAL-3.** As a user whose app isn't listed, I want a way to request it, so the list can
  grow — without the app phoning home.

## 2. Constraints (settled posture — design within these)

- **KJV-anchored.** Every offered destination must serve KJV; a provider that would silently
  switch translations is disqualified (this excludes ESV.org).
- **No analytics, no in-app networking dependency.** Links remain outbound intents only. The
  "request an app" path must also be an outbound intent (§7).
- **Accuracy is credibility.** A destination ships only when all 66 books are link-verified,
  same bar as the Sprint 1 BLB table (PRD M1/M5 spirit).
- **Offline posture unchanged.** Planner works offline; reading taps need network. No copy or
  behavior change here.
- **Respectful tone.** The picker is a quiet settings list, not a marketplace. No logos
  required for V-first-cut; plain text names suffice (logo licensing is its own question).

## 3. Provider model (product-level)

Each provider is a static, bundled definition — no remote config:

| Field | Meaning |
|---|---|
| `id` | Stable internal key (e.g. `blb`, `youversion`, `biblegateway`). Persisted as the user's choice. |
| `displayName` | Settings label, e.g. "Blue Letter Bible (default)", "YouVersion / Bible.com". |
| `urlTemplate` | https template producing one chapter URL, e.g. `https://www.bible.com/bible/1/{book}.{chapter}.KJV`. |
| `bookTokens` | Per-provider 66-entry book-token mapping (see below). |
| `multiRefCapable` | Whether one URL can carry a two-reference portion (§6). |

**Book tokens — extend the Sprint 1 catalog pattern.** The BLB 3-letter abbrevs are the
proven model: one canonical 66-book catalog, each provider adds a token column (BLB `gen`,
YouVersion/OSIS-style `GEN`, Bible Hub `genesis`, Bible Gateway URL-encoded full names, …).
**One catalog, N token columns — never N separate tables** (same anti-drift reasoning as
D-S9-1). Token derivation rules per provider are fine where they hold (e.g. "lowercase full
name, spaces → underscores"), but every token is still individually pinned and verified —
derivation is a convenience, not the gate.

**Verification gate (per provider, blocking — the provider does not ship without it):**
1. **Live link check, all 66 books** (chapter 1 of each at minimum; plus each book's last
   chapter to catch off-by-one chapter handling), performed and *recorded* like Sprint 1's
   BLB pass (see docs/data/README.md) — a script/manual pass at integration time, not a CI
   network dependency.
2. **Offline pinned test** asserting the 66 tokens and the URL builder output
   field-by-field, running in `testDebugUnitTest` forever after (the live check proves the
   tokens once; the pinned test prevents regression).
3. **KJV check**: the landed page/app view is KJV without user-side translation setup.
4. Spot-check the known-awkward books: Psalms, Song of Solomon, Philemon vs Philippians,
   1/2/3 John, Jude, Revelation.

## 4. Candidate providers

### Tier 1 — https templates, KJV confirmed at research time (high confidence; still run the §3 gate before shipping)

| Provider | Template sketch | Notes |
|---|---|---|
| **Blue Letter Bible** | `blueletterbible.org/kjv/{book}/{chapter}/` | Current default. **Stays the default.** Already live-verified (Sprint 1 + G-LINKS). |
| **YouVersion / Bible.com** | `bible.com/bible/1/{BOOK}.{chapter}.KJV` | Version 1 = KJV; OSIS-style book codes. App-links into the YouVersion app when installed — the single biggest "my app" win. |
| **Bible Gateway** | `biblegateway.com/passage/?search={Book}+{chapter}&version=KJV` | Full book names in the query — most readable URLs; likely native multi-ref support (§6). |
| **Bible Hub** | `biblehub.com/kjv/{book}/{chapter}.htm` | Web only (no app routing). Lowercase book slugs. |
| **BibleStudyTools** | `biblestudytools.com/kjv/{book}/{chapter}.html` | Web only. Book slugs. |

### Tier 2 — needs live verification before even being scheduled

| Provider | Open question |
|---|---|
| **Logos** (`ref.ly`) | Does the short-link scheme deep-link reliably without a Logos account, and can it pin KJV? |
| **Olive Tree** | Custom URL scheme — behavior when the app isn't installed (dead tap?) must be defined; custom schemes can't fall back to web like https can. |
| **And Bible** | Open source, offline-first — culturally a great fit for this audience; verify an intent/URL surface exists and is stable. |
| **MySword** | Intent-based; same not-installed fallback question as Olive Tree. |

**Product rule for custom-scheme/intent providers (tier 2):** an https-template provider
degrades gracefully (browser) when no app is installed; a custom-scheme provider does not.
If any tier-2 provider ships, it must either be shown only when its app is detectably
installed, or fall back to BLB on failure. This is extra complexity — a good reason tier 2
waits.

### Excluded (recorded so we don't re-research)

- **ESV.org** — no KJV; would silently switch translations. Disqualified by §2.
- **Accordance** — not Android-centric.

## 5. Settings UI

- New Settings row/section: **"Open readings in"** — a radio list, exactly the theme-selector
  pattern (one choice, persisted via DataStore, applied immediately).
- **Blue Letter Bible is the default**; existing users see zero behavior change on upgrade.
- Order: BLB first (default), then alphabetical. Each row: display name only; web-only
  providers may note "(website)" so app-expecting users aren't surprised.
- Below the list: the "Request another app or site" affordance (§7).
- The choice affects in-app reading taps everywhere a reference opens (Today/day pager).
  The **widget is unaffected** — it opens the app, not a URL (D-S7-4 holds).

## 6. Multi-book portions (Jun 19 / Dec 19)

Two days in the plan have a two-book portion in one reading (2 John 1; 3 John 1). BLB
URLs carry one chapter, and today's behavior opens the **first** reference of the portion.

- **Rule (default for every provider): open the first reference.** It matches the shipped
  behavior, keeps one tap = one destination, and the second book is adjacent (the user is
  reading 2 John; 3 John is one page away in any Bible).
- **Optional per-provider enhancement:** a provider flagged `multiRefCapable` (likely Bible
  Gateway via `?search=2+John+1;3+John+1` — verify live) may open both references in one URL.
- **Rejected:** two stacked links / a chooser dialog for these two days — a year-round UI
  complication for a twice-a-year case, and it breaks the one-tap contract (G3).

## 7. "Request an app" — with no in-app networking

- A "Request another app or site" row under the provider list fires a **`mailto:`** intent to
  **jjpillion@gmail.com** with a prefilled subject ("Daily Reading Planner — Bible app
  request") and a short prefilled body prompting for the app/site name. A mailto intent is
  the same outbound-intent class as the BLB link — no networking dependency, no telemetry,
  nothing collected.
- **GitHub issues is NOT viable today**: the repo is private. If the owner ever makes it
  public (or stands up a public issues-only repo), add the link alongside mailto. Owner
  decision (§10).
- Fulfillment is an app update: providers are bundled definitions, each gated by §3. Set
  that expectation nowhere in-app (no promises); the email reply can.

## 8. Rollout recommendation

- **First cut (smallest lovable): BLB (default) + YouVersion + Bible Gateway.** YouVersion
  is the highest-demand "my app" destination; Bible Gateway is the strongest web alternative
  and the multi-ref candidate. Two §3 verification gates of new work.
- **Second wave:** Bible Hub + BibleStudyTools — web-only and largely overlapping value;
  add cheaply once the provider plumbing exists, or on request volume via §7.
- **Tier 2: unscheduled** until live verification answers §4's questions, given the
  not-installed fallback complexity.

## 9. Non-goals

- No translation picker — KJV only (settled; PRD §5). Provider choice ≠ translation choice.
- No remote/dynamic provider list, no remote config (no networking dependency).
- No per-reading or per-stream provider choice — one global preference.
- No in-app browser rendering of provider content beyond the existing Custom Tab.
- No provider logos/branding in V-first-cut (licensing review not worth it for a radio list).

## 10. Open questions (owner / teammates)

1. **Owner:** approve the first-cut provider set (§8) when this is scheduled.
2. **Owner:** mailto-only for requests, or also make a public issues channel (§7)?
3. **Owner:** any provider on the tier-2 list you'd promote on personal/community knowledge
   (e.g. is And Bible actually used in the community)?
4. **Diego:** verify tier-1 templates + token schemes live (the §4 sketches are research,
   not verified); confirm Bible Gateway multi-ref; define the URL-builder seam relative to
   the existing `BlbUrlBuilder`/`OpenReferenceUseCase`.
5. **Morgan:** sizing + where this lands relative to V2/V3 (product suggestion: after
   Sprint 11, before V3 in-app text — V3 reduces but does not eliminate its value, since
   link-out remains the no-storage path and some users will always prefer their own app).
