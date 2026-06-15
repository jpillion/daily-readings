# Schedule reading-tile hint — reflect the selected provider

**Status:** done (`sprint-00M-tile-hint-provider`, owner UI fix). Display-only, no version bump.

## Problem

The small hint line under each Schedule reading tile was hardcoded
`reading_open_hint` = "Opens %1$s on Blue Letter Bible", regardless of the user's chosen
**Settings → Open readings in** provider. The owner had the in-app reader selected, but the
tiles still read "Blue Letter Bible" — the dynamic wiring was missed when the provider feature
(S13) and the in-app reader (V3) landed.

## Change

The hint reflects the **currently selected provider**, reactively: changing it in Settings
updates the tiles live.

### Reactive threading

- `DayReadingsViewModel.selectedProvider: StateFlow<BibleProvider>` =
  `settingsRepository.bibleProvider` `stateIn`'d on `viewModelScope`
  (`WhileSubscribed(5_000)`, seeded `BibleProvider.DEFAULT` so the hint never flickers through
  a wrong provider before the first emission).
- `DayReadingsRoute` collects it (`collectAsStateWithLifecycle`) and passes it as the new
  `selectedProvider` param of `DayReadingsPagerScreen` (default `BLB` so non-prompt/test
  callers are unchanged).
- `DayReadingsPagerScreen` passes it as `provider` into `DayContent` → `ReadingCard`.

Display copy only — the actual tap-time destination is still resolved by
`OpenReferenceUseCase` (which re-reads the setting and applies the MySword-not-installed
fallback). The hint and the tap share the same stored setting, never drift.

### Per-provider hint strings (natural prepositions)

The single `reading_open_hint` is replaced by five strings; `%1$s` is the reference text
(e.g. "Genesis 1–2"):

| Provider | String | Hint |
| --- | --- | --- |
| `IN_APP` | `reading_open_hint_inapp` | Opens %1$s **in this app** |
| `BLB` | `reading_open_hint_blb` | Opens %1$s on Blue Letter Bible |
| `BIBLE_GATEWAY` | `reading_open_hint_gateway` | Opens %1$s on Bible Gateway |
| `YOUVERSION` | `reading_open_hint_youversion` | Opens %1$s on YouVersion |
| `MYSWORD` | `reading_open_hint_mysword` | Opens %1$s **in** MySword |

A single `@StringRes` mapping `readingOpenHintRes(provider)` lives in `DayContent.kt` (one
home, no second enum). The provider display strings in `strings.xml` (`provider_blb` etc.)
have suffixes like "(default)"/"(website)" and are NOT reused here.

## Decisions

- **IN_APP wording = "in this app"** ("Opens Genesis 1–2 in this app"). Owner tone sign-off
  noted (alternative considered: "Reads … in this app").
- **MySword-not-installed:** the hint mirrors the **setting**, not install-aware tap-time
  resolution. When MySword is selected but not installed, the tap falls back to BLB (in
  `OpenReferenceUseCase`), but the hint still reads "…in MySword". Deliberately not
  over-engineered — the hint reflects the chosen setting.

## Tests / gates

- 605 tests (net +6 over the 599 baseline): 5 per-provider hint UI pins in `DayContentTest`
  (LITERAL expected strings, never computed via the production mapping) + 1
  `selectedProvider`-reactivity pin in `DayReadingsViewModelTest`.
- 3 mutations killed: IN_APP→BLB hint (reddens the in-app pin), MYSWORD→YouVersion hint
  (reddens the MySword pin), VM `selectedProvider` ignoring the setting (reddens the
  reactivity pin) — each restored in place.
- All three data/Room gates untouched (plan = 11, BibleTextVerificationTest = 18,
  BibleDatabaseRoomOpenTest = 5). Full pipeline green, Kover 95.4% on domain/data, a11y gate 7/7.
- No new deps/permissions, no Room/manifest/DataStore change. Tile layout/spacing and a11y
  (the hint is supplementary text) unchanged.

## Open / device-pass

- Owner tone sign-off on the five hint strings (esp. "in this app").
- Device-pass: the live update when switching provider in Settings.
