# Sprint 0013 — Bible app links

**Status: GOAL MET.** Closed 2026-06-11. (Unattended overnight run, sprint 4 of 4; working
tree handed over uncommitted by request. CLI sub-agent dispatch still down on expired
credentials — EM executed tickets directly under per-ticket verification discipline.)

## Goal outcome

**Met.** A reader can choose, in Settings, which KJV destination reading taps open —
**Blue Letter Bible (default, zero behavior change), Bible Gateway, or
YouVersion/Bible.com** — and the choice applies to the very next tap, everywhere a
reference opens (the whole day pager; the widget still opens the app, D-S7-4). Both new
providers passed the full spec §3 gate before shipping: **Bible Gateway 134/134, YouVersion
132/132** live HTTP checks (chapter 1 + last chapter of all 66 books + portion forms),
recorded in [docs/data/provider-link-checks.md](../data/provider-link-checks.md). A
"Request another app or site" row fires a prefilled mailto to jjpillion@gmail.com — the
same outbound-intent class as a reading link, no networking. The owner's three tier-2 apps
were researched and verified: **none ship** (details below).

## Current capability

- **Settings → "Open readings in":** radio list (theme-selector pattern) — Blue Letter
  Bible (default) first, then Bible Gateway "(website)", YouVersion / Bible.com. Persisted
  in DataStore (`bible_provider`); unknown stored ids degrade to BLB.
- **Tap semantics per provider (D-S13-3, pinned in tests):** BLB/YouVersion open the
  portion's first reference (shipped behavior). **Bible Gateway carries the whole portion
  in one URL** — "Genesis 1-2" for ranges, "2 John 1,3 John 1" for the Jun 19 / Dec 19
  two-book portion — an upgrade over first-chapter-only, live-verified.
- **OS routing unchanged in kind, generalized in target:** every provider is a plain https
  URL through the existing `CustomTabLauncher`; YouVersion app-links into the YouVersion
  app when installed, else browser — exactly the BLB behavior generalized.
- Verified: **279/279 tests** (19 net-new/rewritten; the 7-test Sprint 1 plan gate
  untouched at 7/7), **4 mutations killed**, each by exactly its intended test, in-place
  restores: (1) Philippians `PHP`→`PHM` token swap → UsfmCodeCatalogTest fails;
  (2) Bible Gateway demoted to first-ref-only → "carries the whole two-book portion" fails;
  (3) range end bound dropped → "no off-by-one" fails; (4) stored choice ignored (always
  BLB) → "chosen provider is read at tap time" fails. Full pipeline green; **Kover 96.5%**
  on domain/data (floor 70%). Version stays 1.0.0/10000. No new permissions, no manifest
  change, no Room change.

## Tier-2 verification (owner request) — go/no-go

All three researched + live-probed 2026-06-11; full findings in the feature spec §11
([docs/features/bible-app-links.md](../features/bible-app-links.md)). **All NO-GO** for the
same structural reason: no graceful path for a user without the app installed (spec §4
product rule).

| App | Finding | Go-path |
|---|---|---|
| **Logos (ref.ly)** | `;kjv` suffix pins KJV correctly (verified: `LLS:KJV1900` across awkward books), BUT bare links default to **ESV**, and the web landing (`app.logos.com`) is an **account wall with no scripture** for anonymous users. | Show only when the Logos app is detectably installed (`<queries>` + PackageManager); device pass required. |
| **Olive Tree** | `olivetree://` custom scheme only (vendor GitHub docs, iOS-centric). Not-installed tap = dead intent; nothing to curl-verify. | Same install-detection sprint. |
| **MySword** | Explicit-component intent (`com.riversoft.android.mysword/.MySwordLink`) wrapping `https://mysword.info/b?r=…`, but that URL serves a **stub page with no scripture** in a browser. | Same install-detection sprint. |

A future "installed-app providers" ticket could add all three behind one PackageManager
detection seam + BLB fallback; queued as a candidate, not scheduled.

## Decisions & rationale (do not relitigate)

- **D-S13-1 — One catalog, N token columns** (spec §3, extends D-S9-1): YouVersion's USFM
  codes are a new `Book.usfmCode` column, hand-pinned (NOT derived — `PHP/EZK/JOL/NAM/MRK/
  JUD/1JN` would defeat any derivation) in `UsfmCodeCatalogTest` against the live-verified
  values. Bible Gateway tokens ARE derived: URL-encoded `canonicalName`. No new tables.
- **D-S13-2 — `ProviderUrlBuilder` is the only URL home** (risk R2): `BlbUrlBuilder` was
  generalized, not wrapped; all three URL schemes live in that one file. The Sprint 1 CSV
  keeps its original four columns (the plan gate parses it); `BookCatalogTest` now
  reconciles the Sprint 1 fields field-by-field instead of whole-object equality.
- **D-S13-3 — Portion semantics per provider:** first-ref for single-chapter providers
  (spec §6 default); Bible Gateway flagged `multiRefCapable` carries the full portion. The
  run-grouping mirrors `ReadingFormatter`'s (kept separately — data must not depend on ui;
  ASCII hyphen vs the formatter's display en-dash).
- **D-S13-4 — Choice read at tap time** (`SettingsRepository.bibleProvider.first()` inside
  `OpenReferenceUseCase`): no caching, no plumb-through state — a Settings change affects
  the next tap with zero invalidation logic.
- **D-S13-5 — Tier-2 = install-detection or nothing** (spec §4 rule applied): a provider
  whose no-app fallback is a dead tap, a login wall, or a stub page does not ship.
- **D-S13-6 — Enum name is the persisted id:** `BibleProvider.fromStored` degrades unknown
  ids to BLB. Never rename a constant without a migration.

## User-visible strings — OWNER TONE SIGN-OFF NEEDED (PRD M8)

All in `app/src/main/res/values/strings.xml`:

| id | string |
|---|---|
| `provider_section_title` | "Open readings in" |
| `provider_blb` | "Blue Letter Bible (default)" |
| `provider_biblegateway` | "Bible Gateway (website)" |
| `provider_youversion` | "YouVersion / Bible.com" |
| `request_app_title` | "Request another app or site" |
| `request_app_subject` | "Daily Reading Planner — Bible app request" (mailto subject) |
| `request_app_body` | "I would like my readings to open in: " (mailto prefill) |

Tone notes: no marketing language, no logos (spec §2/§9); "(website)" on Bible Gateway so
app-expecting users aren't surprised; "(default)" labels BLB without nagging.

## State of the codebase

- **Domain:** `domain/model/BibleProvider.kt` (enum + `multiRefCapable` + `fromStored`);
  `OpenReferenceUseCase` is now `suspend`, injects `SettingsRepository` +
  `ProviderUrlBuilder`.
- **Data:** `data/reference/BookCatalog.kt` — `Book` gained `usfmCode` (66 pinned values);
  `data/reference/ProviderUrlBuilder.kt` replaces `BlbUrlBuilder.kt` (deleted);
  `SettingsRepository(.Impl)` gained `bibleProvider`/`setBibleProvider` (key
  `bible_provider`).
- **UI:** `SettingsScreen` — "Open readings in" section between Text size and Reminders
  (tags `provider-option-blb`/`-biblegateway`/`-youversion`, `request-app-row`);
  `SettingsViewModel.bibleProvider`/`onBibleProviderSelected`; the mailto side-effect lives
  in `SettingsRoute` (strings resolved via `stringResource` in composition — lint forbids
  `context.getString` there; `ActivityNotFoundException` = quiet logged no-op).
- **Tests (net +19):** `UsfmCodeCatalogTest` (66-token gate), `ProviderUrlBuilderTest`
  (supersedes BlbUrlBuilderTest, keeps its all-66 BLB pinning; YouVersion all-66; Bible
  Gateway shapes incl. range/multi-book/encoding/no-collapse), rewritten
  `OpenReferenceUseCaseTest`, +3 `SettingsRepositoryImplTest`, +1 `SettingsViewModelTest`,
  +4 `SettingsScreenTest`, a11y gate extended (provider rows + request row 48dp).
  `FakeSettingsRepository` gained the provider surface.
- **Docs:** `docs/data/provider-link-checks.md` (the S13 gate record);
  `docs/features/bible-app-links.md` §11 (tier-2 findings).
- The live link checks are a **one-time recorded verification, not a CI dependency** — the
  committed suite is fully offline.

## Needs the owner's device pass (genuinely not JVM-provable)

1. With YouVersion installed: a reading tap app-links straight into the YouVersion app at
   the right KJV chapter (and falls back to browser when not installed).
2. Bible Gateway pages render acceptably in the Custom Tab (cookie banners etc.).
3. The "Request another app or site" mailto opens the device's mail client prefilled.
4. Existing-user upgrade: provider selector shows BLB selected, taps behave exactly as
   before until changed.

## Carryover & next goal

- **Next goal (Sprint 14): V2 release prep** — version bump past 1.0.0/10000, the owner's
  consolidated device pass (Sprint 9 checklists + tracking-start + stats + S12 reminder
  items + the S13 items above), string tone sign-offs (S12 + S13 tables), upload key +
  Play listing if still pending, closed-track rollout.
- **Queued/deferred:** second-wave web providers (Bible Hub, BibleStudyTools — cheap now
  that the plumbing exists; add on request volume); installed-app tier-2 providers
  (Logos/Olive Tree/MySword behind install detection, D-S13-5); toggle-from-widget;
  Psalm 119 verse-ranges; API 26–28 scrim check; TIME_SET/TIMEZONE_CHANGED receiver;
  deprecation housekeeping; owner question spec §10.2 (public issues channel for requests).
- **Scope protected out this sprint:** per-stream/per-day provider choice (spec §9), logos/
  branding, translation choice (KJV settled), any remote/dynamic provider config.

## Next sprint

`next: sprint-0014-v2-release-prep`

## Open questions & risks

- **Owner tone sign-off pending** on the S13 strings above (and still the S12 table).
- **Provider URL stability:** outbound URL shapes can change under us (BG/YouVersion own
  their schemes). The pinned tests prevent *our* regressions only; a provider-side change
  surfaces as user reports → re-run the gate harness (method documented in
  docs/data/provider-link-checks.md).
- bible.com bot protection fingerprints curl's TLS stack — future link-check reruns should
  use Python urllib (or WebFetch), not curl.
- Standing debt unchanged: Robolectric pinned `@Config(sdk = [34])`; JVM-untested
  MainActivity hooks; widget ignores in-app font scale (by design); CLI agent credentials
  still expired (owner: `claude /login`); CI `release-bundle` unexercised this sprint (no
  commit per instructions).
