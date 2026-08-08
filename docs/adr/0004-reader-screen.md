# ADR-0004 — The reader screen: Compose Multiplatform vs native SwiftUI

**Status:** Draft, pending owner sign-off · **Date:** 2026-08-08 · **Author:** Staff / Port Architect

## Context

The in-app KJV reader is the app's highest-value screen and its most gesture-heavy. Current
implementation, `bible/ui/reader/` (~1,900 lines across 19 files):

- A `HorizontalPager` over **all 1,189 chapters** (`GlobalChapterIndex`, D-H-2), or over a
  portion-anchored index in Reading context (`ReadingPagerIndex`, D-I-1).
- Each page is a `LazyColumn`, **one item per verse, keyed by `VerseText.canonicalId`** (D-V3-12).
- Each verse is a `combinedClickable` ≥48dp target: **short tap** opens a 3-item action menu,
  **long press** enters multi-select; further taps add/remove verses.
- Selection mode replaces the top bar with a contextual bar (count, Copy, X) and installs a
  `BackHandler`.
- Copy writes a formatted citation to the system clipboard.
- Markup rendering: `<a>` added-word tags → italic spans via `VerseRenderer` →
  `AnnotatedString`; `<l/>` → newline; superscriptions render as unnumbered italic headings.
- TalkBack semantics per verse: `selected`, `stateDescription`, `onClickLabel`,
  `onLongClickLabel`.

The brief flags this as "precisely Compose's weakest text area on iOS". **Having read the code, I
do not think that is true of this screen**, and the reason matters.

**The app contains zero `TextField`, zero `BasicTextField`, and zero `SelectionContainer`.**
Verified by grep across all 162 files. Verse selection is *entirely app-implemented* over
`combinedClickable` and a custom `VerseSelection` model. There is no caret, no selection handle,
no IME, no system text context menu, no text magnifier.

Those are exactly the components that constitute Compose's iOS text weakness. This screen uses
none of them. What it needs from Compose's iOS text stack is: correct line breaking and
justification, italic spans within a paragraph, and font scaling. Those are the mature parts, and
Compose Multiplatform declared iOS stable in 1.8 with further native text work in 1.11 (May 2026).

## Decision

**Compose Multiplatform, shared, in `shared/ui`. No native SwiftUI reader.**

With three explicit build requirements, because the risk that *does* exist here is gestural, not
textual:

1. **The gesture stack must be verified on a device, early.** A long-press target inside a
   vertically-scrolling `LazyColumn` inside a horizontally-paging `HorizontalPager` is a
   three-way gesture conflict. The Android team could not prove this in JVM tests either —
   CLAUDE.md records `performTouchInput { longClick() }` as explicitly not evidence, and the
   owner's device pass is what confirmed it worked. **iOS needs the same device pass, and it is a
   Phase C exit criterion, not a nice-to-have.** If long-press-inside-pager does not work on
   iOS, that is discovered in Phase C, not in TestFlight.
2. **`BackHandler` is Android-only** (`ReaderScreen.kt:119`). iOS has no system back button, and
   the interactive edge-swipe belongs to navigation, not to a modal selection state. On iOS the
   exits from selection mode are the **X in the contextual bar**, **deselecting the last verse**,
   and **Copy** (which the owner made an exit in P-Q-1). Record in `docs/parity-matrix.md`. Do
   **not** invent a swipe-to-clear gesture Android does not have.
3. **Performance on Psalm 119 (176 verses, one `VerseActionMenu` per verse) has never been
   profiled** — CLAUDE.md lists it as an open unknown on Android. Profile it on iOS during Phase
   C. If it is a problem, the fix (hoist a single menu keyed by the tapped verse) is a
   contained refactor and should be filed as a separate improvement for **both** platforms, not
   an iOS special case.

## Alternatives rejected

**Native SwiftUI reader.** Rejected, decisively. This screen is ~1,900 lines of behaviour that
took sprints C, G, H, I, J, K, L, N, Q and a P0 hotfix to reach its current state — global
chapter indexing, portion-collapsing page indices, verse-id-keyed rendering, markup, per-verse
external links with four provider URL shapes, verse-range clipboard formatting that delegates to
`ReadingFormatter.singularizeBookName` so "Psalm 23" is singular in both places. Reimplementing
that in Swift means reimplementing every one of those decisions and then keeping two
implementations in step forever. **If the reader is native, there is no meaningful shared core
left and the KMP decision collapses** — the reader plus the schedule *is* the app.

**Hybrid: Compose for the verse list, native `UITextView` for text rendering via
`UIKitView`.** Rejected. It would trade a small typographic gain for the loss of the per-verse
tap/long-press model, which is the screen's entire interaction design. It also introduces
`UIViewController` interop into the hottest scrolling surface in the app.

**Compose, but with `SelectionContainer` for copy instead of the custom model.** Rejected —
that would *introduce* the iOS text-selection weakness this screen currently avoids, and would
break verse-granular copy (the citation format needs to know which verses, not which
characters).

## Consequences accepted

- **Typography will not be UIKit typography.** Compose does its own text layout; line breaking,
  hyphenation and font metrics will differ subtly from a native iOS reading app. For a
  scripture reader with a fixed serif-ish body style this is a small aesthetic delta, but it is
  real and the owner should see it on a device before sign-off. Add "reading feel on glass" to
  the iOS device pass, exactly as M-V3-2 did on Android.
- **Dynamic Type interaction is a parity question.** The app has its own 0.85×–1.5× font slider
  that multiplies `LocalDensity.fontScale`. On Android that composes with the system font scale;
  on iOS it composes with Dynamic Type, which has a different curve and a different accessibility
  ceiling. Expect the same slider position to produce different text sizes on the two platforms.
  Record in the parity matrix.
- **VoiceOver behaviour is unproven.** Compose maps its semantics to iOS accessibility elements
  automatically, and the reader's semantics are unusually rich (`selected`, `stateDescription`,
  `onClickLabel`, `onLongClickLabel`, `clearAndSetSemantics` on the footer hint). This mapping
  must be checked with VoiceOver on a device — it is the a11y equivalent of the gesture pass.
  Also note: **long-press is unreliable under VoiceOver on both platforms**, which is why the
  action menu carries a "Select verses" item as the equivalent path. That design already exists
  and carries over.
- We accept that if requirement (1) fails on device, we have a real problem with no cheap
  fallback. That is why it is a Phase C exit criterion and not a Phase E discovery.

## Revisit when

- The device gesture pass fails, or Psalm 119 profiling shows unacceptable scroll cost.
- The reader gains real text *input* (notes, highlights with text annotation). That would
  introduce `TextField` and change this analysis materially.
- Owner feedback specifically cites reading typography.
