# ADR-0002 — SKIE vs Swift Export

**Status:** Draft, pending owner sign-off · **Date:** 2026-08-08 · **Author:** Staff / Port Architect

## Context

Kotlin/Native exports Kotlin APIs to Swift through an Objective-C bridge by default. That bridge
is lossy in ways that matter: sealed interfaces arrive as open classes with no exhaustive
`switch`, Kotlin enums lose their Swift-native form, default arguments disappear, `suspend`
functions arrive as completion handlers rather than `async`, and generics are erased.

Two tools improve this, and they are **mutually exclusive** — you cannot run both over the same
framework, so this is an architectural commitment, not a preference.

- **SKIE** (Touchlab) — post-processes the generated framework, adding Swift wrappers for sealed
  hierarchies (real exhaustive `switch`), enums, default arguments, `Flow` → `AsyncSequence`, and
  `suspend` → `async`. Mature, in production use, plugin-based.
- **Swift Export** (JetBrains) — generates Swift bindings directly from Kotlin without the
  Objective-C hop. Strategically the right long-term answer and the one JetBrains will invest in,
  but it has been shipping in stages and its coverage of the harder cases (generics, sealed
  hierarchies, coroutines) has been the last part to land.

**What this specific app actually needs from Swift.** This is the question that decides it, and
the answer is unusual: **almost nothing.**

The iOS app is a SwiftUI shell hosting a Compose root, plus actuals for ~15 platform interfaces.
The Swift↔Kotlin surface is:

1. `MainViewController()` — one function returning a `UIViewController`.
2. Actual implementations of `shared/platform` interfaces (notifications, clipboard, URL opener,
   file paths, date formatting). Those are *Swift implementing a Kotlin interface* — the
   direction where the Objective-C bridge is weakest for sealed types but where these particular
   interfaces are all simple: `String`, `LocalDate`, `Boolean`, `Unit`.
3. Later, if ADR-0006 goes ahead, a WidgetKit extension calling one `suspend` use case and
   reading a small data class. **That** is the case where `suspend` → `async` and sealed
   `DayReadings` exhaustiveness would genuinely be pleasant.

There is no Swift-authored UI reading Kotlin state, no Swift business logic, no large model
surface crossing the boundary.

## Decision

**Neither, for v1.0. Ship on the plain Objective-C interop and design the Swift-facing surface
to not need help.**

Concretely: every `shared/platform` interface that Swift implements must use only
bridge-friendly types — primitives, `String`, `List`, and data classes with primitive fields.
No sealed hierarchy, no generic, no default argument, and no `suspend` function crosses into
Swift in v1.0. That is a constraint on **my** interface design, not a limitation Swift imposes,
and it costs nothing because those interfaces are all "do this side effect" or "give me this
string".

**If and when ADR-0006 (WidgetKit) proceeds, adopt SKIE at that point** — that is the first task
with a genuine need for `suspend` → `async` and sealed exhaustiveness in Swift.

## Alternatives rejected

**Adopt SKIE now.** Rejected for v1.0 only, and not because SKIE is bad — it is the better tool
today. Rejected because it is a build-plugin dependency, a Gradle configuration surface, and a
thing that can break a release, added to solve a problem this app does not yet have. The whole
Swift surface is ~15 small interfaces and one view controller. Adding SKIE to make that nicer is
paying a permanent tax for a one-week convenience. **Reverse this immediately** if the Swift
surface grows beyond the shape above.

**Adopt Swift Export now.** Rejected. It is the strategically correct destination but it is the
one moving fastest, and this project has a documented pattern of getting hurt by unpinned moving
parts (the CI entry in CLAUDE.md: two source URLs on `/master/`, an unpinned SQLite, an unpinned
publishing action). Betting the Swift boundary on the tool that is still stabilising, in exchange
for ergonomics we do not need, is the wrong risk to take on the port's first release.

**Write the platform actuals in Kotlin/Native instead of Swift** (using cinterop to
`UserNotifications`, `UIKit`, etc.). Genuinely tempting — it would remove the Swift boundary
almost entirely, and Kotlin/Native's Apple framework bindings cover `UNUserNotificationCenter`,
`UIPasteboard` and `NSDateFormatter` directly. **Not rejected outright**; it is the fallback if
the Swift boundary proves annoying, and iOS Platform should evaluate it per-interface. But
defaulting to Swift for OS integration keeps the code idiomatic for anyone reading it as an iOS
app, and keeps the option of hiring/consulting help.

## Consequences accepted

- Swift code sees Kotlin sealed types as open classes with no exhaustiveness checking. **We
  avoid this by never passing one to Swift.** If that constraint is violated, this ADR is wrong
  and should be reopened rather than worked around.
- Kotlin `suspend` functions are not `async` in Swift. Same mitigation: none cross the boundary
  in v1.0. The platform interfaces Swift implements are synchronous or fire-and-forget.
- We will likely adopt SKIE later, which means a migration — but a small one, because the surface
  is small. Adopting it later against a small surface is cheaper than adopting it now and
  carrying it through a port.
- Anyone who assumes "we'll just call the ViewModel from SwiftUI" will find it unpleasant. That
  is intentional: ADR-0003 and ADR-0004 say the UI is Compose.

## Revisit when

- ADR-0006 (WidgetKit) is approved — **revisit immediately**, adopt SKIE.
- Any `shared/platform` interface needs to expose a sealed type, a generic, or a `suspend`
  function to Swift.
- Swift Export reaches a JetBrains-declared stable with sealed-type and coroutine coverage, and
  the project has a quiet release window.
