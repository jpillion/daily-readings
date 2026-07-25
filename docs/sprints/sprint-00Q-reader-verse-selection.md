# Sprint 00Q — reader verse selection + verse action menu

**Goal:** *A reader can copy the verses he is reading — a long-press (or a menu item) selects
verses, Copy puts them on the clipboard with a proper citation, and the short tap that used to
eject him to another app now opens a deliberate menu that has room to grow.*

**Outcome: MET.** Spec: [docs/features/reader-verse-selection.md](../features/reader-verse-selection.md)
(owner-approved and locked before implementation). D-Q-1 … D-Q-3 implemented as written; no deviations.

Uncommitted in the working tree by request. **No version bump** (stays 1.6.0 / 10600), no tag, no commit.

---

## Current capability — what can now be done that could not before

- **You can copy scripture out of the app.** The owner's complaint was *"there are times I want to
  copy a verse from what I'm reading, and there is currently no way to do that because the click
  takes you out."* There are now two copy paths: `Copy this verse` in the tap menu, and a
  multi-verse selection with a Copy action.
- **A tap on a verse is no longer destructive of reading flow.** It opens a small menu; the external
  Bible app is one deliberate step behind it, not the only thing a touch can do.
- **The clipboard produces a citation you can paste into a message unedited** — text first,
  reference last, e.g. `In the beginning… \n\n— Genesis 1:1–2 (KJV)`. Contiguous verses collapse to
  a range, non-contiguous join with commas, a cross-chapter selection groups by chapter
  (`Genesis 1:1–2; 2:3`), a two-book selection groups by book, and Psalm 23 cites as **"Psalm"**
  singular because the rule is delegated to `ReadingFormatter.singularizeBookName`, not re-derived.
- **Multi-chapter selection works inside a reading portion**, because a "page" in the Reading
  context is the whole combined portion (D-Q-3) — so a James 1–2 reading can be selected across both
  chapters and cited as `James 1:1; 2:3`.
- **Selection is reachable without the long-press gesture.** `Select verses` in the menu is the same
  entry point, and it is the TalkBack path — long-press is not reliably reachable under TalkBack.
- **The menu has a slot for cross-references.** The owner's stated reason for wanting a menu.
  Its items are a flat ordered list of `VerseActionItem` calls; adding cross-references is one line
  plus one string plus one callback.

Administrative footnote: 818 → **878 tests**, 0 failures; full pipeline green from clean.

---

## Decisions & rationale

Owner-approved spec decisions (do not relitigate):

- **D-Q-1 — clipboard format is text first, reference at the end**, `"<text>\n\n— <ref> (<CODE>)"`.
  Text is `MarkupStripper.strip` output, never raw `<a>`/`<w>`/`<l/>`. The translation code comes
  from the Sprint-00N `BibleTextSource.translations()` seam, never a hardcoded "KJV"; a null/blank
  code omits the parenthetical. **The book name MUST come from
  `ReadingFormatter.singularizeBookName`** so D-UI-2 holds — one home, mutation-pinned.
- **D-Q-3 — selection scope is the current page.** A page is one chapter (Browse) or one whole
  portion (Reading). Swiping to another page clears the selection rather than leaving an invisible
  off-screen selection alive. Cross-page selection is out of scope, which is why `page` is part of
  the state.
- **The menu holds exactly three items** — Open in `<app>` / Copy this verse / Select verses.
  **Share was explicitly declined by the owner. Do not add it.**

Decisions taken during the sprint (new, recorded so the next session does not relitigate):

- **D-Q-2 — the verse action menu is ephemeral state of the reader PAGE, not the ViewModel.**
  `ReaderPage` owns `menuVerseId` via `remember`. Rationale: it is dismissed by every action and
  means nothing across a page change, and keeping it out of the VM keeps `ReaderScreen` directly
  testable — `AccessibilityGateTest` composes the screen, not the route, and must be able to open
  the menu by tapping a verse. **Selection state, by contrast, is hoisted to the ViewModel**: it must
  survive recomposition, drive the top bar, and feed the copy action.
- **D-Q-4 — copy is a ViewModel-produced string, a Route-performed side effect.** The VM builds the
  string (it owns the page-content cache and the translation code) and emits it on a one-shot
  `copyEvents: Flow<String>`; the Route writes the clipboard. Mirrors the existing
  `openDestinationEvents` → `launchReadingDestination` idiom exactly. `versesOn(page, ids)` reads the
  **already-rendered page content**, so a copy never re-queries the database and can only ever copy
  what the user is actually looking at.
- **D-Q-5 — the copy confirmation toast fires only below API 33.** Android 13+ posts its own system
  clipboard confirmation; a second toast would double up. Pinned by the pure
  `shouldShowCopyConfirmation(sdkInt)` boundary test.
- **P-Q-1 — Copy does NOT exit selection mode.** The spec enumerates the exits as "X, system back,
  or deselecting the last verse"; Copy is not among them, so this is the spec-literal reading. A
  copy leaves the selection intact (extend and copy again; a mis-tap costs nothing). Pinned by test.
  **Flagged for owner confirmation — copy-then-exit is the other common Android idiom.**
- **`onVerseTapped` renamed to `openVerseExternally`.** A tap no longer opens anything directly, so
  the old name had become a lie. Body unchanged.
- **`OpenVerseUseCase` is untouched**, so its Sprint-K **D-23-1** contract is preserved exactly: a
  verse tap is always external by definition and resolves from the chosen `ExternalBibleApp`
  **alone**, independent of `ReadingDestinationMode`; MySword takes the app-intent with a BLB-verse
  fallback; the persisted choice is never rewritten.

---

## State of the codebase

New production files (all under `app/src/main/kotlin/com/jpillion/dailyreadingplanner/bible/ui/reader/`):

| file | role |
|---|---|
| `VerseSelection.kt` | pure state `(page, verseIds)` + `start` / `toggle` / `onPageChanged` / `cleared`. `VerseSelection.NONE` is the ONE representation of "not selecting" |
| `VerseClipboardFormatter.kt` | THE clipboard builder. Pure. **The sprint's primary mutation target** |
| `VerseActionMenu.kt` | the 3-item `DropdownMenu`, built as a flat ordered list so cross-references is a one-entry addition |
| `VerseSelectionBar.kt` | the contextual action bar (count as a polite live region, `Copy` text button, `Close` icon) |
| `VerseClipboard.kt` | `copyVerseTextToClipboard(context, text)` + the pure `shouldShowCopyConfirmation(sdkInt)` |

Changed: `ReaderScreen.kt` (new signature, `combinedClickable`, selected semantics, `BackHandler`),
`ReaderViewModel.kt` (selection state + copy events + D-Q-3 in `onPageSettled` + selection reset in
`switchContext`), `ReaderRoute.kt` (wiring + the clipboard side effect), `res/values/strings.xml`
(4 reworded, 11 appended), `ui/day/DayContent.kt` (KDoc only — the hint mapping stays its single home,
D-K-HINT-1), `data/reference/ProviderUrlBuilder.kt` (KDoc only).

Conventions established:
- **The verse interaction modifier is built ONCE** and shared by the numbered-verse and the
  superscription branch, so the two can never diverge.
- **Selected state is `selected` + `stateDescription`, set only while selecting** (outside selection
  mode they would be noise on every verse of every chapter). The `secondaryContainer` background is
  decoration; the semantics are the mechanism.
- Test tags added: `verse-menu-open` / `verse-menu-copy` / `verse-menu-select`,
  `verse-selection-close` / `verse-selection-copy` / `verse-selection-count`.

### Gates

- **The five data/Room gates are UNTOUCHED** — Bible Companion plan **11**, M'Cheyne **10**,
  Chronological **8**, `BibleTextVerificationTest` **18**, `BibleDatabaseRoomOpenTest` **5**.
  Verified by `git diff` (empty) *and* by SHA against the sprint-start baseline.
- `AccessibilityGateTest` grew 8 → 13 and now covers the menu and the action bar end-to-end,
  including that **"Select verses" genuinely enters selection mode** (not merely that a callback fires).

---

## Carryover & next goal

**Nothing was absorbed beyond the goal.** The spec's own "Out of scope" list was respected: no
cross-references, no Share, no highlighting/notes/persisted annotation, no cross-page selection.

Queued candidate tickets (NOT absorbed):
1. **Cross-references in the verse menu** — the owner's named next feature, and the reason the menu
   exists. A one-entry addition by construction.
2. **P-Q-1 revisit** — if the device pass shows the lingering action bar after Copy is surprising,
   flipping to copy-then-exit is a two-line change plus one test.
3. **Colourblind palette** — still owner-deferred; the selection highlight would join `StripColors`
   and `SegmentCheckColors` as a third colour seam if it is ever taken up.
4. **Equality guard on the no-op partial write** (carried over from 00P, untouched).
5. **CI actions Node 24 bump** — pre-existing, unrelated.

**Next sprint: `next: sprint-00R-verse-cross-references`** (provisional — the owner's named next
per-verse feature; he may redirect as he has most sprints).

---

## Open questions & risks

**Spec defect for the owner to amend — do not silently "fix" it in code.**
`docs/features/reader-verse-selection.md:35` states the verse tap-out follows **D-H-4** ("when the
reading destination is IN_APP the verse tap-out still resolves to BLB"). That rule was **superseded
by D-23-1** in Sprint K: `OpenVerseUseCase` resolves from the chosen external app alone and never
consults the mode. The implementation preserves the *current* behaviour by leaving the use case
untouched, which is the stronger commitment — but the spec line should be amended or a future
session will re-introduce the retired shim from it. Three KDoc blocks that had already drifted the
same way (`ReaderViewModel` ×2, `ProviderUrlBuilder`) were corrected this sprint; `grep -rn "D-H-4"
app/src/` now returns nothing.

**Strings awaiting owner tone sign-off** (11 new, 4 reworded):

| key | value |
|---|---|
| `reader_verse_tap_hint_blb` / `_gateway` / `_youversion` | "Tap a verse to copy it or open it on %1$s" *(reworded)* |
| `reader_verse_tap_hint_mysword` | "Tap a verse to copy it or open it in %1$s" *(reworded)* |
| `verse_menu_open_in` | "Open in %1$s" |
| `verse_menu_copy` | "Copy this verse" |
| `verse_menu_select` | "Select verses" |
| `verse_actions_label` | "Open verse actions" *(spoken only)* |
| `verse_selection_toggle_label` | "Change selection" *(spoken only)* |
| `verse_selection_count` | "%1$d selected" |
| `verse_selection_copy` | "Copy" |
| `verse_selection_close` | "Exit selection" |
| `verse_state_selected` / `verse_state_not_selected` | "selected" / "not selected" *(spoken only)* |
| `verse_copied` | "Copied" |

Also for sign-off: **the en dash in `Genesis 1:1–2`.** The spec flags it — a plain hyphen is the more
common convention in pasted citations. It is a one-character change (`EN_DASH` in
`VerseClipboardFormatter`) plus 6 expected-string updates. These join the standing backlog
(M'Cheyne titles, the Chronological plan name, the caption strings, the 00P segment states).

**Device-pass items — NOT JVM-provable, do not claim these work:**

1. **Long-press inside a `HorizontalPager` — the biggest unproven risk.** `performTouchInput { longClick() }`
   is a synthetic event. Whether the real gesture is comfortable, and whether it fights the pager's
   horizontal drag or the `LazyColumn`'s vertical scroll, is device-only.
2. **The real clipboard write and the paste.** `copyVerseTextToClipboard` is executed by no test —
   only the pure formatter and the API-33 rule are pinned. Whether the string lands in the system
   clipboard and pastes correctly into a notes/messages app is unverified.
3. **The toast.** Never exercised. That sub-33 shows one confirmation and 33+ shows the *system* one
   (not two) has never run on a device at either level.
4. **Menu anchoring at the top and bottom edges of a chapter.** Robolectric does not lay popups out
   against real screen bounds; a verse at the very edge may anchor off-screen or flip awkwardly.
5. **Selection highlight contrast** (`secondaryContainer` behind body text) in light and dark, and
   under dynamic colour. **Colour-only and not JVM-provable** — the `selected` + `stateDescription`
   semantics are the accessibility mechanism and *are* pinned; the colour is not.
6. **Real TalkBack.** Semantics *properties* are pinned; traversal order with the action bar swapped
   in, and whether the polite live region actually announces the count, are not.
7. **Predictive back / gesture navigation on API 33+.** The new back tests drive
   `OnBackPressedDispatcher` directly under Robolectric.
8. **Per-verse menu instantiation cost.** One `VerseActionMenu` is composed inside every verse's
   `Box`. With `expanded = false` M3 composes no popup, so this should be near-free — but Psalm 119
   is 176 of them and there is no profile. Worth a glance.

**Sprint infrastructure note for the next session** (still true, cost time again): serialise Gradle
— concurrent runs in one checkout produce phantom `NoSuchFileException` failures. And a
"BUILD SUCCESSFUL" whose `:app:testDebugUnitTest` is `FROM-CACHE` has **not** executed the tests;
use `--rerun-tasks` for a real gate run. This bit the final pipeline run once and was caught.
