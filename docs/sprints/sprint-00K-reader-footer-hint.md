# Sprint K addendum — Reader footer hint (the read-here / study-there bridge)

> **EM:** Morgan · **Status:** DONE (uncommitted; the main session verifies + commits) ·
> **Date:** 2026-06-15 · **Version:** unchanged at 1.4.0/10400 (do NOT bump here) ·
> **Scope:** the reader-footer-hint slice of Sprint K (the broader "settings split" —
> `BibleProvider` → `ExternalBibleApp` + `ReadingDestinationMode` — is the parent session's
> handoff; this records ONLY the footer hint that was missing from the first Sprint K pass).

## Goal outcome — MET

The owner's PRIMARY Sprint-K request was a hint at the **bottom of the reading pane (the reader)**
— not the day tile — in the empty band below the chapter text. The first pass shipped the day-tile
hint but omitted this. It now exists.

**What can be done now that couldn't before:** while reading a chapter in the in-app KJV reader, the
user sees an always-present footer line — "Tap a verse to open it on Blue Letter Bible" (or "…on
Bible Gateway" / "…on YouVersion" / "…in MySword") — that tells them where a verse tap-out will go.
This is the read-here / study-there bridge: it is shown **regardless of the reading destination**
(most useful precisely when the user reads in-app), and it updates live if they change the external
app in Settings.

## Current capability (working software)

- The reader's verse `LazyColumn` ends with an italic, de-emphasized footer hint
  (`bodySmall` / `FontStyle.Italic` / `onSurfaceVariant`), start-aligned under the 20dp verse
  content padding, ~24dp top padding (reads as a separated footer, not a trailing verse) + ~16dp
  bottom. On a short chapter it sits in the circled empty band; on a long chapter it's reached at
  the end of scroll. **Always shown** at every reachable chapter.
- Wording matches the day-tile prepositions per provider: "on Blue Letter Bible / on Bible Gateway /
  on YouVersion / in MySword". `%1$s` is the external-app display name.
- It is reactive: `ReaderViewModel.externalApp: StateFlow<ExternalBibleApp>` reads
  `SettingsRepository.externalBibleApp` `stateIn`'d on `viewModelScope`; the Route collects it and
  passes it to `ReaderScreen`. Changing "Open readings in" in Settings re-renders the hint live.

## Decisions & rationale

- **D-K-HINT-1 — one home, no drift.** The reader-hint and external-app-name `when(externalApp)`
  mappings live in `ui/day/DayContent.kt` right beside the day-tile `readingOpenHintRes`
  (`readerVerseTapHintRes(app)` + `externalBibleAppNameRes(app)`), so the two hint surfaces are
  reviewed together and their per-provider prepositions cannot drift. The reader hint is the
  external-app axis ALONE (no `ReadingDestinationMode` branch) — it deliberately ignores in-app vs
  external because the hint is most useful when reading IN_APP.
- **D-K-HINT-2 — footer is a list item, NOT the bottom bar.** The hint is the last `item {}` of the
  verse list. `bottomBar`/`ReaderAudioSlot` stays reserved for V4 audio (D-V3-14) and is untouched.
- **D-K-HINT-3 — TalkBack skips the footer.** `Modifier.clearAndSetSemantics { … }` removes its
  spoken text — every verse already speaks "Open <Book> <ch>:<verse>. <text>", so a second vague
  restatement is noise. The footer is not a tap target. NOTE for future editors: `clearAndSetSemantics`
  also drops the `testTag`, so the tag is re-declared *inside* the clear block
  (`clearAndSetSemantics { semanticsTestTag = "reader-footer-hint" }`) to stay test-findable while
  speaking nothing.

## State of the codebase

- **`bible/ui/reader/ReaderViewModel.kt`** — new `externalApp: StateFlow<ExternalBibleApp>`; gained a
  `SettingsRepository` constructor param (tests pass the existing `FakeSettingsRepository`).
- **`bible/ui/reader/ReaderRoute.kt`** — collects `viewModel.externalApp`, passes it to `ReaderScreen`.
- **`bible/ui/reader/ReaderScreen.kt`** — `ReaderScreen`/`ReaderPage` gained an `externalApp` param;
  new private `ReaderFooterHint(externalApp)` composable; footer added as the final list item.
- **`ui/day/DayContent.kt`** — new `readerVerseTapHintRes` + `externalBibleAppNameRes` mappings.
- **`res/values/strings.xml`** — new strings (below).
- Tests: `ReaderScreenTest` (literal-string wording pins for all 4 apps via resolved resources +
  a11y-silence + verse-keyed coexistence), `ReaderViewModelTest` (reactivity), `AccessibilityGateTest`
  (`ReaderScreen` call updated for the new param).

## Strings added — AWAIT OWNER TONE SIGN-OFF

| key | value |
|---|---|
| `reader_verse_tap_hint_blb` | "Tap a verse to open it on %1$s" |
| `reader_verse_tap_hint_gateway` | "Tap a verse to open it on %1$s" |
| `reader_verse_tap_hint_youversion` | "Tap a verse to open it on %1$s" |
| `reader_verse_tap_hint_mysword` | "Tap a verse to open it in %1$s" |
| `external_app_name_blb` | "Blue Letter Bible" |
| `external_app_name_gateway` | "Bible Gateway" |
| `external_app_name_youversion` | "YouVersion" |
| `external_app_name_mysword` | "MySword" |

## Verification

- **627 tests** (net +7 from this slice: 4 literal wording pins + 1 a11y/render pin + 1 verse-keyed
  coexistence pin in `ReaderScreenTest`, 1 reactivity pin in `ReaderViewModelTest`), full pipeline
  green (`spotlessCheck lintDebug assembleDebug testDebugUnitTest koverXmlReportAppDebug
  koverVerifyAppDebug`).
- **The three data/Room gates UNTOUCHED:** plan = 11, `BibleTextVerificationTest` = 18,
  `BibleDatabaseRoomOpenTest` = 5.
- **Kover 95.8%** on domain/data (≥70% floor).
- **3 load-bearing mutations killed**, each by its intended test, restored in place byte-identically:
  (1) external-app **name** mapping `BLB → gateway-name` → BLB wording pin reddens;
  (2) MySword **hint-template** mapping `mysword → blb` (would flip "in" to "on") → MySword wording
  pin reddens; (3) VM `externalApp` hardcoded to default (ignores the stored setting) → reactivity
  pin reddens. (Swapping the *template* among the three "on" providers is semantically equivalent —
  identical rendered string — so it is intentionally not a distinct mutation target; the observable
  differences, the name and the MySword preposition, are pinned.)

## Carryover & next goal

- Owner device-pass: footer placement in the circled empty band on a short chapter, end-of-scroll
  reach on a long chapter, live update when changing "Open readings in".
- S-K strings (above) join the Sprint-K tone sign-off list.
- **Next:** `sprint-0021-v2x-release-prep` (owner-scheduled) — unchanged.
