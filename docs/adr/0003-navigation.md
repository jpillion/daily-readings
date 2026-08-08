# ADR-0003 — Navigation: shared Compose navigation vs a native iOS shell

**Status:** Draft, pending owner sign-off · **Date:** 2026-08-08 · **Author:** Staff / Port Architect

## Context

The navigation surface today (`ui/navigation/AppNavHost.kt`) is small and precisely specified:

- A `Scaffold` with a bottom `NavigationBar` carrying **two co-equal tabs**, Schedule and Bible
  (D-V3-16).
- Two nested graphs: `Graph.SCHEDULE` (start destination = the day pager; Settings is pushed on
  top) and `Graph.BIBLE` (the reader).
- Three destinations total: `today`, `settings`, `reader`.
- `switchTab` uses `popUpTo(startDestination) { saveState = true } / launchSingleTop /
  restoreState`, so **each tab preserves its own back stack across a switch** — the reader keeps
  its chapter, the schedule keeps its day (U18, D-D-3). This is pinned by a Robolectric
  nav-regression suite.
- A cross-graph handoff: tapping a Schedule reading publishes a portion to an
  `@ActivityRetainedScoped` `ReaderHandoff` and switches tabs; the reader consumes it. No route
  argument crosses the graph boundary (D-D-1).
- A `SnackbarHost` at the root for the update Restart snackbar.

Navigation Compose is multiplatform (the repo is already on `navigation-compose` 2.9.8), so
"shared" is technically available. The question is whether an iOS user *should* get Android
navigation semantics.

## Decision

**Shared Compose Multiplatform navigation. One `NavHost`, one `NavigationBar`, one set of
semantics, in `shared/ui`.** The iOS app is a SwiftUI `App` hosting a single
`ComposeUIViewController` for the whole screen; SwiftUI owns nothing below the window.

Two deliberate iOS adaptations, both recorded in `docs/parity-matrix.md`:

1. **The bottom bar is styled as an iOS tab bar** (the M3 `NavigationBar` is close enough; it is
   the same idiom in both design languages). Safe-area insets are handled by the shared scaffold
   reading platform window insets — not by an iOS-only branch.
2. **No interactive swipe-back edge gesture in v1.0.** Settings is the only pushed destination;
   it has a visible back affordance already.

## Alternatives rejected

**Native iOS shell: SwiftUI `TabView` + `NavigationStack`, each tab hosting its own
`ComposeUIViewController`.** This is the option that produces the most native-feeling iOS
navigation — real tab bar, real large titles, free interactive swipe-back. Rejected for three
reasons, in order of weight:

1. **It duplicates the navigation logic**, which is the one thing this project is explicitly not
   doing. And the logic is not trivial: the tab-state-preservation rule (U18) and the
   reading-tap cross-graph handoff (D-D-1, plus its D-I-2 refinement where tapping the Bible tab
   *resets* the reader to Browse while a reading tap does not) are subtle, test-pinned, and were
   each arrived at through an owner feedback loop. Reimplementing them in Swift means
   reimplementing the bugs.
2. **Multiple `ComposeUIViewController` instances is the expensive configuration.** Each hosts
   its own composition; state sharing between them goes back through the Kotlin layer anyway, so
   you pay the memory and get none of the simplicity.
3. The app has **three destinations**. The payoff for a native shell scales with navigation
   complexity, and here there is almost none to gain.

**Shared navigation, but with iOS-specific route definitions.** Rejected — that is invariant 2
(`if (isIOS)`) wearing a hat.

**Voyager / Decompose (third-party multiplatform navigation).** Rejected. Both are capable, but
the app already uses AndroidX Navigation with a working, test-pinned tab-state model, and
AndroidX Navigation now runs on iOS. Swapping navigation libraries during a port adds risk for
no capability we need.

## Consequences accepted

- **iOS users get Android-shaped navigation.** Specifically: no interactive swipe-back to leave
  Settings, no large-title collapse behaviour, and transitions that are Compose's rather than
  UIKit's. This is the largest "feels ported" surface in the app, and I want it stated plainly
  rather than discovered in review. It is acceptable because the app is two tabs and one pushed
  screen — there is very little navigation for the user to feel.
- The `@ActivityRetainedScoped` `ReaderHandoff` needs a new scope. On iOS there is no activity
  and no configuration change; the natural scope is "the lifetime of the shared root". ADR-0012
  covers the mechanism. **`ReaderHandoff` carries a one-shot pending value** — do not casually
  make it a process singleton without re-reading D-I-2, where a stale browse request superseding
  a reading tap is exactly the bug that was designed against.
- `BackHandler` (used for verse-selection exit, `ReaderScreen.kt:119`) has no iOS system back to
  hook. See ADR-0004.
- The bottom bar costs ~80dp on iOS as it does on Android (R-V3-1). The app's one-screen-fit
  budget work (S16, S18, the N-stream fix) was tuned against a Pixel 7 Pro; **iPhone screen
  heights differ and the one-screen fit must be re-verified on device.** Put it on the iOS
  device-pass list.

## Revisit when

- The app gains a genuine navigation hierarchy (modal flows, deep links, more than ~6
  destinations).
- Owner or App Review feedback specifically cites navigation feel.
- Compose Multiplatform ships first-class interactive-swipe-back support — at that point the
  main consequence above largely evaporates and this decision gets cheaper, not more expensive.
