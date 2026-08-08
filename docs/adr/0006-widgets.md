# ADR-0006 — Do Android widgets stay, and does iOS get one?

**Status:** **ACCEPTED** (owner sign-off 2026-08-08, via `ios-port-approach.md` §3 / §10 #2 and #4)
· **Date:** 2026-08-08 · **Author:** Staff / Port Architect
· **Amended:** 2026-08-08 — see [Amendment A1](#amendment-a1--the-app-group-is-reserved-in-v10-even-though-the-widget-is-not)

> **Read the amendment before acting on part 3.** "No widget in v1.0" is unchanged, but a
> **storage decision that this ADR did not make now has to be made in v1.0**, and it changes the
> owner's very next Apple action.

## Context

`widget/` is 4 files / 508 lines, of which `WidgetContent.kt` is 397. It is a Glance
`GlanceAppWidget` with `SizeMode.Responsive` and **five layout tiers** — TINY (1×1, 57×48dp),
SMALL, MEDIUM, MEDIUM_SHORT, LARGE, plus a wide-short size point — each with its own type scale
and insets (`WidgetScale`/`scaleFor`). Invariants held across every tier: **all readings shown at
every size**, marks rendered, one tap target for the whole surface, TalkBack speaks full
canonical book names, Feb 29 and load-failure states degrade gracefully, follows the **system**
theme not the in-app `ThemeMode` (D-S7-3), read-only (D-S7-4). It reads the same
`GetDayReadingsUseCase` as the app via a Hilt `@EntryPoint`, and is refreshed opportunistically
from `MainActivity.onResume`, after progress mutations, at midnight via a dedicated alarm, and by
a 30-minute `updatePeriodMillis` backstop.

It went through three rounds of owner feedback (S8, S9, S14) to reach that. It is a real feature
with real design investment.

Glance does not target iOS. WidgetKit is SwiftUI-only, runs in a **separate process** in an
**app extension**, has no access to the main app's memory, and reads shared state through an App
Group container.

## Decision

**Three parts.**

### 1. The Android widget stays exactly as it is, in `androidApp`. Unchanged.

No attempt to "share" it. Glance composables are Android-only by construction, and the widget
works and has owner sign-off. **Do not refactor it during the port.** The only change permitted
is import updates where it consumes shared types (`DayReadings`, `ReadingFormatter`,
`GetDayReadingsUseCase`) and the DI change from `@EntryPoint` to whatever ADR-0012 selects.

### 2. Extract the tier-selection and content-shaping logic into `shared/domain` — and only that.

`WidgetContent.kt` contains two separable things:
- **Glance rendering** (`GlanceModifier`, `Text`, `Column`, `defaultWeight()`) — Android-only,
  stays put.
- **Layout policy** — "given these pixel dimensions and this reading count, which tier?", the
  per-tier type scale and inset table, `showsHeader`, the LARGE-requires-both-axes rule, and the
  abbreviated reference strings. That is pure logic, it is JVM-tested, and it is exactly what a
  WidgetKit view would need.

Extract it as `shared/domain/WidgetLayoutPolicy`. This is not speculative sharing: if part 3
happens, it is the only part that would otherwise be duplicated, and it is the part with the
mutation-pinned invariants (the LARGE both-axes rule and the header height gate each have a
killed mutation recorded against them).

### 3. **iOS does NOT get a WidgetKit widget in v1.0.** Scope it as a distinct follow-on deliverable.

Recommended sequencing: **after the iOS app is in production and stable.**

## Alternatives rejected

**Ship a WidgetKit widget with iOS v1.0.** Rejected on scope, not on value. It requires: a new
Xcode app-extension target; an App Group entitlement (which changes the bundle ID's capability
set, invalidating provisioning profiles — RELEASING-IOS.md Step 2 deliberately leaves capabilities
empty); a mechanism for the extension to reach reading data, which means either linking the shared
KMP framework into the extension (doable — and the point at which ADR-0002's "adopt SKIE" trigger
fires) or writing a snapshot to the App Group container on every progress mutation; a
`TimelineProvider` producing entries across the day for date rollover; SwiftUI views for the iOS
size families (systemSmall/Medium/Large, plus accessory families for the Lock Screen); and its own
device-pass matrix. **That is a sprint, not a task**, and none of it is on the path to "the app
exists on the App Store". Ship the app first.

**Drop the Android widget to reduce the shared/unshared asymmetry.** Rejected outright. It is a
working, owner-approved feature with users. Removing a shipped feature to make a port look tidier
is unacceptable.

**Rewrite the Android widget in a "shared widget abstraction" that renders to Glance or
WidgetKit.** Rejected. This is the classic over-abstraction trap: two rendering systems with
different layout models (Glance's RemoteViews-backed subset vs SwiftUI), different size
vocabularies, different refresh models (broadcast-driven vs timeline-driven) and different
process models. An abstraction over them would need far more than three `expect` declarations to
express, which by my own test means the boundary is in the wrong place. **Share the policy, not
the rendering.** That is what part 2 does.

## Consequences accepted

- **Android has a home-screen widget; iOS does not.** Combined with ADR-0005 (no persistent
  notification on iOS), this means iOS users have **no glanceable surface at all** — they must
  open the app to see the day's readings. That is a genuine product asymmetry and the owner
  should decide with it in front of them, not discover it later. It is also the strongest
  argument for scheduling the WidgetKit work sooner rather than later: a WidgetKit widget
  answers *both* losses at once.
- Part 2's extraction is a change to a shipped, owner-signed-off Android file. It must be
  behaviour-preserving and the existing widget tests must pass unchanged. If they need editing,
  the extraction is wrong.
- The Android widget's midnight-refresh alarm and 30-minute backstop stay Android-only, as does
  `WidgetRefresher`'s Glance implementation. The `WidgetRefresher` **interface** moves to
  `shared/platform` with an iOS no-op actual, so the shared ViewModels that call it after a
  progress mutation need no branching.

## Revisit when

- **iOS v1.0 is in production and stable.** At that point, open a dedicated WidgetKit sprint and
  adopt SKIE (ADR-0002) as part of it.
- Owner feedback on iOS specifically asks for a home-screen or Lock Screen surface — expect this,
  since Android has one.
- Glance ever targets non-Android platforms (do not hold breath).

---

## Amendment A1 — the App Group is reserved in v1.0, even though the widget is not

**Date:** 2026-08-08 · **Author:** Staff / Port Architect
**Records:** D-PORT-3 and **D-PORT-4** from `ios-port-approach.md` §4.2, owner-signed.

### A1.1 The split decision

Part 3 of this ADR stands: **no WidgetKit widget in iOS v1.0.** The scope argument is unchanged.

But this ADR treated "widget" and "where iOS stores its data" as one decision, and they are not.
The iOS Platform engineer's objection was correct on that second point and I did not address it:

> **D-PORT-4. The App Group container is reserved from the first iOS build.** `progress.db`, the
> DataStore settings file, and the copied `bible.db` are written into the App Group container
> from day one — not into the app sandbox's default `Application Support` directory.

The reasoning is asymmetric cost, and that asymmetry is the whole argument:

| | Reserve now | Add later |
|---|---|---|
| Cost | One entitlement, one path constant in the iOS actual of the `AppFilePaths` seam. **Hours.** | A **user-data migration on real devices** — move `progress.db` (irreplaceable reading history) and `settings.preferences_pb` out of the sandbox into the container, on a shipped app, with a half-migrated failure mode |
| Risk if wrong | none | the ADR-0008 class of risk, on iOS this time |

We do not need a widget to justify the container. We need only to accept that we will probably
want one, and to notice that the cheap moment to decide is **before the first build ships**, not
after.

### A1.2 The owner action this changes — and nobody had connected the two documents

`docs/RELEASING-IOS.md` **Step 2** instructs the owner to register the bundle ID
`com.jpillion.dailyreadingplanner` with **all capabilities off**, and correctly warns that adding
a capability later invalidates provisioning profiles minted by `fastlane match`.

**App Groups is a capability.** So:

> **Enable App Groups at bundle-ID registration time — the owner's very next Apple action after
> enrolment clears.** Group identifier: `group.com.jpillion.dailyreadingplanner`.
>
> Doing this after `fastlane match appstore` has run means regenerating every profile.

`RELEASING-IOS.md` Step 2 must be amended to say so. **That file is Build & Release's write set,
not mine** — this amendment is the request, and it is logged as such in the Gate 0 overview
brief. It is a two-line edit and it is time-critical, because the owner's enrolment is on the
critical path and may clear at any moment.

### A1.3 What this obliges of the shared code

Nothing in `shared/domain` or `shared/ui`. The container path is a **platform detail behind one
seam** — the file-location interface in `shared/platform` (`AppFilePaths`, specified in
`docs/task-briefs/p2-03-platform-seam-interfaces.md`). Android's actual returns
`context.filesDir` / `context.getDatabasePath(...)`; iOS's actual returns paths under
`NSFileManager.containerURLForSecurityApplicationGroupIdentifier(...)`.

**This is exactly why that seam is written to semantics ("where this app keeps its private
data") and not to either platform's API.** A brief that says "use Application Support" has
already made the wrong decision.

### A1.4 The consequence the owner must keep in front of them

Stated once more because it is the compound of D-PORT-3 and the persistent-notification loss
(ADR-0005), and it is easy to lose between two documents:

> **iOS v1.0 has no glanceable surface at all.** Android users get a home-screen widget **and** an
> always-present tray notification (the latter **ON by default**). iOS users open the app, or they
> see nothing.

That is the strongest argument for scheduling WidgetKit as **iOS 1.1, not "someday"** — one
sprint answers both losses at once, and it is also the trigger that makes SKIE worth adopting
(ADR-0002). Reserving the App Group now is what keeps that sprint a *feature* sprint rather than
a *migration* sprint.
