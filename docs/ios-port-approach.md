# iOS port — the approach, for owner sign-off

> **Status:** proposal, awaiting owner decision · **Date:** 2026-08-08 · **Author:** Morgan (EM)
> **Synthesis of six independent specialist reviews** of `main` @ `1bcc98e` (Android 1.8.1 / 10801,
> live in production, 100% rollout, 177 countries).
>
> Inputs: [port-inventory.md](port-inventory.md) (Port Architect), [adr/](adr/) (14 draft ADRs),
> [RELEASING-IOS.md](RELEASING-IOS.md), [RELEASING.md](RELEASING.md),
> [task-briefs/ios-delivery-pipeline.md](task-briefs/ios-delivery-pipeline.md), and the reviews by
> the Senior Shared-Core, Senior Shared-UI, iOS Platform, Build & Release and Verification
> engineers.
>
> **Evidence discipline used throughout.** `VERIFIED` = someone read the code, ran the command, or
> got an HTTP 200 against the real artifact. `INFERRED` = a reasoned conclusion from documentation
> or API contracts that nobody on this team has executed. `UNVERIFIED` = nobody knows and nobody
> should pretend to. Several reviewers were careful about this distinction; that care is preserved
> here deliberately. **This machine has no Xcode and the owner has no Apple Developer account, so
> every iOS-runtime claim in this document is at best INFERRED.**

---

## 1. Executive summary

**The honest headline: this is a 4–7 month program that makes the Android app riskier before it
makes an iPhone app exist, and the iPhone app it produces is a smaller product than the Android
one.** Everything else in this document is detail around that sentence.

**What we propose.** Port the app to Kotlin Multiplatform with Compose Multiplatform: one shared
Kotlin core and one shared Compose UI compiled for both platforms, an Android host app that keeps
every feature it has today, and a thin SwiftUI host on iOS. Roughly 91 of 162 source files move to
shared code essentially unchanged, ~22 go behind platform interfaces that already exist, and ~20
stay Android-only.

**Why it is viable.** Not optimism — measurement. Only **22 of 162 main files import `android.*`**
(VERIFIED). `domain/` (39 files), `core/` (2) and `bible/domain/` (13) have **zero** `android.*`
and **zero** `androidx.*` imports (VERIFIED). Twelve platform capabilities are *already* interfaces
with hand-written fakes. There are **zero mocking libraries** in 940 tests — MockK's absent
Kotlin/Native support is the usual wall for a port this size and this repo walks straight past it.
There is **zero `TextField`, zero `SelectionContainer`, zero `AndroidView`** — which means the reader
dodges Compose Multiplatform's weakest iOS area almost entirely. And the version alignment is free:
Compose BOM 2026.05.01 resolves to Compose 1.11.2, CMP 1.11.1 is built on exactly 1.11.2, CMP 1.11.1
requires Kotlin 2.3, and this repo is on 2.3.21 (VERIFIED against real POMs by the Build & Release
engineer). No version has to move on either side. That removes the single most common KMP-adoption
blocker.

**What it costs.** **16–28 engineer-weeks**, i.e. **4–7 months** as a single stream. Three
Android-only Play releases whose only purpose is to make an iOS app possible — each carrying real
risk to shipped users. A near-total pause on Android feature work. **$99/yr** Apple Developer
Program plus macOS CI (GitHub Free gives ~200 macOS minutes/month, which is **less than one release
run**). And a meaningful block of *owner* hours that no engineer can absorb: enrolment, a privacy
policy that does not exist yet, an App Privacy answer about the translations proxy, an icon,
screenshots, a scope sign-off, and a first device pass estimated at **6–10 focused hours** plus
**3–4 hours** for a first VoiceOver pass.

**What iOS will not do, on day one:**

- **No home-screen widget.** Android has a five-tier responsive Glance widget with three rounds of
  owner feedback behind it. WidgetKit is a separate SwiftUI extension target — a sprint, not a task.
- **No always-present tray notification.** Android's is **ON by default**. iOS has no non-dismissible,
  app-refreshed notification and none should be faked. This is the single largest feature loss.
- Combined, those two mean **iOS users have no glanceable surface at all** — they open the app or they
  see nothing.
- **A thinner daily reminder** — see §4.1; the choice is genuinely binary and it is the owner's.
- **No in-app update prompt** (correct — the App Store owns updates), **no MySword**, **no Material
  You dynamic colour**, **no hardware-back exit from verse selection**, and date/time strings that
  will not be byte-identical to Android's.

**What we need from the owner right now:** twelve decisions (§10), of which four have multi-day
latency and should start today regardless of the go/no-go (§7).

**Recommendation: proceed — gated.** See §2.

---

## 2. Should we do this at all?

### The strongest argument against, stated fairly

The Android app is live, healthy, in 177 countries, with a fully automated `tag → alpha → promote`
pipeline that completed a production release in **39 seconds** and a 940-test suite that has caught
real P0s. This port takes that apart.

It requires **three Android-only Play releases** whose entire purpose is to make an *iOS* app
possible, and every one of them carries risk to users who get nothing from it. Two of the three
failure classes involved have **already bitten this codebase**: a Room asset schema that no test
opened (sprint-00F, every chapter showed "couldn't load this chapter") and a ViewModel initialisation
order that only R8 exposed (1.7.0, crash on every reading tap). The DI rewrite is exactly the second
class. The persistence relocation can crash-on-open or silently reset every user's settings.

It will pause Android feature work for months, on a project whose CLAUDE.md log is roughly 60%
owner-redirected feature sprints. ADR-0008 additionally forbids any `ProgressDatabase` schema change
for the duration, so any future feature needing one is blocked too.

And a large share of the work produces **zero user-visible progress**: 846 of 940 assertions go
through Google Truth (JVM-only) and must be rewritten; ~4,000 LOC of Robolectric-bound Compose tests
must be re-hosted on `runComposeUiTest`; ~80–90 iOS tests must be written from scratch. Work with no
visible output is the work that gets rushed, and rushing an assertion rewrite is how a test suite
gets silently weakened.

Finally: **waiting is a real option.** Room's `createFromAsset` has no common-source-set equivalent
today and Google lists it as future work. If it lands, cliff #1 disappears and cliff #2 gets cheaper.
A year of waiting costs nothing but time on a product with no competitive clock.

### The verdict

**Proceed — but gate it on three spikes, run the two that can kill the port *before* spending any
Android risk, and get the scope list signed first.**

Three reasons the argument above does not win:

1. **The coupling measurement is unusually good and it is not luck.** 22 of 162 files touching
   `android.*`, three whole packages at zero, twelve capabilities already behind interfaces, zero
   mocks, zero framework text selection — that is the accumulated product of 22 sprints of "one home,
   no drift" discipline. This codebase is closer to portable than almost any shipped single-platform
   app has a right to be, and that is exactly the condition under which a port is the right call
   rather than the romantic one.
2. **The alternative is worse in a specific, nameable way.** A native iOS rewrite reimplements
   `DayCompletionClassifier` (the one truth table every completion, streak, strip and picker dot
   flows through), `ReadingSegments`, `SegmentCheckPolicy`, `ReadingFormatter` (including the Psalm
   singular rule), `GlobalChapterIndex`/`ReadingPagerIndex` (including the sprint-00P P0 fix and the
   1.8.1 `PagerTarget` epoch fix), `ProviderUrlBuilder` (four URL shapes, 134/134 and 132/132
   live-verified), `VerseId`/`ReferenceResolver`/`PortionVerseBridge`, and `VerseClipboardFormatter`
   — every one mutation-pinned, most of them derived from owner feedback — and then keeps them in
   step forever. That does not just cost twice; it dismantles the discipline that is this repo's
   actual product.
3. **The falsifiers are cheap and can run first.** The two things that could kill this port — the
   Room identity hash and the reader gesture stack — can both be tested in under a week, before any
   production Android code changes. That converts an open-ended bet into a gated one.

### D-PORT-1 — the stop rule

**This is a correction to both proposed sequences.** The Build & Release engineer wants the four
Android-only rewrites shipped first; the Architect wants short spikes then the persistence
restructure. Neither puts the *port-killing* questions first.

> Run **Gate 0** — the V1 identity-hash spike and an on-device reader-gesture rig — **before any
> production Android file changes.** If the hash differs *and* ADR-0007 Stage 1b (drop Room for the
> read-only bible DB) is judged too large, **or** the gesture rig fails on physical hardware with no
> fix inside a week, **stop and reassess.**

The reason to sequence it this way is uncomfortable and should be said plainly: **the three
Android-only releases are not free option value.** If you stop after them you have a *slightly worse*
Android app — Koin loses Hilt's compile-time graph verification, kotlinx-datetime is neutral at best
on an Android-only product, and only the module split is genuinely positive. So do not spend that
risk before you know the port can finish.

---

## 3. iOS v1.0 scope — sign this before Phase 2, not at TestFlight

The Port Architect's warning is the most important sentence in his review and it is repeated here so
it cannot be missed:

> *"The port does not produce feature parity. iOS ships without a widget, without the persistent
> tray notification, without in-app updates, without MySword, and with a reminder that fires on
> completed days. That is a smaller product on iOS, and the owner should sign off on that list before
> Phase B rather than meet it at TestFlight."*

### Ships on iOS v1.0

| # | Capability | Notes |
|---|---|---|
| S1 | All three reading plans — Bible Companion, M'Cheyne, Chronological | Same gate-verified assets, one copy in git |
| S2 | The day screen: per-passage reading cards, partial checks, tap-to-mark-read | Sprint 00P/00O behaviour intact |
| S3 | Date picker with completion dots, day swiping, cross-year month swipe | |
| S4 | The in-app KJV reader: chapter swipe across the whole canon, combined portion pages | Gated on the reader gesture pass — §8 R5 |
| S5 | Verse selection, the verse action menu, clipboard with citation | Long-press behaviour is the open risk |
| S6 | Online NKJV / NASB with the offline-fallback banner | Ktor + Darwin engine; HTTPS only, no ATS exception |
| S7 | External Bible destinations: In-app, Blue Letter Bible, Bible Gateway, YouVersion | |
| S8 | Stats: streaks, year strips, legend | |
| S9 | Settings: plan, theme, font size, tracking start, reading destination | |
| S10 | Daily reminder — **with one behavioural divergence, §4.1** | Off by default, as on Android |
| S11 | Full offline operation for planner + progress + bundled KJV | |

### Does not ship on iOS v1.0

| # | Not shipping | Why | Recoverable? |
|---|---|---|---|
| N1 | **Home-screen widget** | WidgetKit extension = new target, App Group, `TimelineProvider`, SwiftUI views, its own device matrix. A sprint. | Yes — planned as iOS 1.1. **Storage location reserved in v1.0, §4.2.** |
| N2 | **Persistent ongoing tray notification** | No iOS mechanism exists. Live Activities are time-bounded event UI and using one here would read as abuse and plausibly draw an App Review objection. | No, ever. Its answer is N1. |
| N3 | **In-app update prompt** | The App Store owns updates. Correct, not a loss. | N/A |
| N4 | **MySword** | Android-only app with an explicit component intent. INFERRED (iOS Platform): no iOS counterpart exists. The enum value stays — it is a persisted id — and degrades to BLB. | No |
| N5 | **Material You dynamic colour** | No iOS system palette. | No |
| N6 | **Hardware-back exit from verse selection** | iOS has no back button. Exits become the X, deselecting the last verse, and Copy (P-Q-1). Do **not** invent a swipe-to-clear gesture Android lacks. | N/A |
| N7 | **`BOOT_COMPLETED` re-arming** | Not needed — repeating triggers survive reboot. | N/A |
| N8 | **Localization** | The app has never been localized (one `values/`, no `values-xx/`). The port does not change this. | Later, separately |

### Degrades — same feature, different behaviour

| # | Degradation | Detail |
|---|---|---|
| G1 | **Daily reminder** | See §4.1. Either the body is generic and never suppressed, or it is specific and silently stops after ~60 days of not opening the app. Owner's call. |
| G2 | **Date and time strings** | `java.time.format` and `NSDateFormatter` will not produce byte-identical output. Every test pinning a literal formatted date moves platform-side. |
| G3 | **Copy confirmation** | Android ≥33 relies on the OS toast; iOS shows none, so the app must always show its own. |
| G4 | **Font scaling** | The app's 0.85×–1.5× slider composes with Android's font scale on one platform and iOS Dynamic Type on the other. **iOS Dynamic Type reaches ~310% (AX5)** vs Android's typical 1.3–2×; composed with the slider that is an effective **~4.6×**. **Every one-screen-fit budget from sprints 16 / 18 / 20 and the N-stream fix was tuned on a Pixel 7 Pro and is invalid on iOS.** |
| G5 | **Navigation feel** | Shared Compose navigation means iOS gets Android-shaped navigation: no interactive swipe-back out of Settings, no large-title collapse, Compose rather than UIKit transitions. ADR-0003 calls this "the largest 'feels ported' surface in the app." |
| G6 | **Reading typography** | Compose text rendering, not UIKit text rendering. Add "reading feel on glass" to the owner's device pass, exactly as M-V3-2 did on Android. |
| G7 | **VoiceOver** | `Role.Button` on every verse means VoiceOver announces each verse of scripture as "button." Raised by the Shared-UI engineer; **needs an owner/accessibility decision, not a silent ship.** |
| G8 | **Long-press on scripture** | Collides with iOS's magnifier / grab-handles / system-Copy convention. Sprint 00Q built that gesture to solve a problem the owner personally raised, so this is a product question as much as a technical one. |

---

## 4. The four contradictions, resolved

### 4.1 Daily reminder parity — the platform engineer is technically right; the architect's design is still right for v1.0; the product choice escalates to the owner

**The dispute.** iOS Platform argues full parity is achievable: the reminder body is deterministic
from (date, plan) because plans are date-anchored bundled assets, and the one dynamic rule — suppress
if the day is already complete — can *only* change while the app is foregrounded, because marking is
in-app only (the widget is read-only by D-S7-4, there are no notification actions, and sprint-00O
tap-to-mark happens on the day screen). So you patch pending requests at exactly the moments iOS
permits it. The Architect (ADR-0005) states suppression is lost because it is decided at fire time
(D-S12-3) and iOS does not run your code when a local notification fires.

**Adjudication. The platform engineer's mechanism is sound and the architect's "suppression is lost"
is too strong.** Tracing the state transitions: today's progress can only reach "complete" through a
foreground interaction, and cancelling a pending `UNNotificationRequest` is a synchronous call that
persists across process death. So the suppression rule *is* patchable at every moment it can change.

**But it is not free, and the architect's design choice is still correct for v1.0**, because
suppression is only obtainable by abandoning the single repeating trigger in favour of ~60 dated
one-shot requests. That buys correct suppression *and* correct references, and pays for it with a
**silent horizon cliff**: a user who does not open the app for longer than the window simply stops
being reminded, with no signal. On a daily-habit app that user has probably churned — the architect's
"precisely the user the reminder exists for" is rhetorically strong and empirically weak — but the
failure is silent, and this project's standing discipline is to refuse silent failure.

**D-PORT-2 — a finding neither reviewer stated, and it removes an option from the table.** ADR-0005's
own compromise option (c) — "N explicit requests, falling back to a generic repeating request beyond
the horizon" — **does not work.** A `UNCalendarNotificationTrigger` has no start date and cannot be
suppressed on specific dates; inside the horizon the repeating request and each dated one-shot would
*both* fire, producing a duplicate notification every day. Two requests with different identifiers
are two notifications. **There is no hybrid.** (INFERRED from the UNNotification API contract —
nobody on this team has executed it. Verify in the Phase 4 spike before relying on it.)

**So the choice is binary**, and because it is a copy-and-behaviour choice on a product whose owner
signs off every user-visible string, it escalates:

> **OWNER DECISION (§10 #3).** iOS's daily reminder can be **(a) one repeating notification with a
> generic body** — "Today's readings are ready" — which is never wrong, never stale, costs one of the
> 64 notification slots, sidesteps Feb 29 entirely, and fires on days you have already finished; or
> **(b) ~60 dated notifications with the actual references**, which matches Android exactly — right
> text, correctly suppressed when you have finished the day, correctly skipped on Feb 29 — for as
> long as you open the app at least once every two months, and then silently stops reminding you at
> all until you next open it. There is no third option that gets both. Android's reminder is off by
> default, so this affects only users who deliberately turned it on.

**My recommendation: (a) for v1.0**, with (b) recorded as a designed, understood post-1.0 upgrade
whose mechanism the iOS Platform engineer has already worked out (rolling window, identifiers
`reminder-<planId>-<epochDay>`, a five-event invalidation set). Reason: (a) ships; (b) is more code
than the entire Android reminder feature and its failure mode is invisible.

**Both reviewers were right about their own claim and neither was wrong. Record it that way in
`docs/parity-matrix.md`** — writing "not achievable" into the record would be false.

### 4.2 Widget in v1.0 — split the decision: no widget in v1.0, but reserve the App Group now

**The dispute.** iOS Platform says IN v1.0, on two grounds: it is the only honest replacement for the
lost persistent notification, and the App Group storage decision it forces must be made before the
first build ships anyway — adding it later is a data-location migration on real devices. Architect
(ADR-0006) and Build & Release both say defer.

**Resolution — and yes, these are compatible.**

**D-PORT-3. No WidgetKit widget in iOS v1.0.** The scope argument wins: a new app-extension target,
an App Group entitlement, a `TimelineProvider`, SwiftUI views for three system size families plus
Lock Screen accessory families, and its own device-pass matrix. That is a sprint, and none of it is
on the path to "the app exists on the App Store."

**D-PORT-4. The storage decision is made in v1.0 and the App Group is reserved from the first
build.** `progress.db`, the DataStore settings file and the copied `bible.db` go into the App Group
container from day one. Cost: one entitlement and one path decision — hours. The iOS Platform
engineer's migration argument is correct and it is cheap to honour.

**This changes an owner action that is about to happen, and nobody connected the two documents.**
`RELEASING-IOS.md` Step 2 instructs the owner to register the bundle ID with **all capabilities off**,
warning that adding capabilities later invalidates provisioning profiles. App Groups is a capability.
**So the App Group must be enabled at bundle-ID registration time — the owner's very next Apple
action — or the profiles minted by `fastlane match` have to be regenerated later.** Amend Step 2.

**The consequence the owner must see, because it is the compound of D-PORT-3 and N2:** iOS v1.0 has
**no glanceable surface whatsoever**. Android users get a home-screen widget *and* an always-present
notification. iOS users open the app or they see nothing. This is the strongest argument for
scheduling WidgetKit as **iOS 1.1, not "someday"** — one WidgetKit sprint answers both losses at
once, and it is also the trigger that makes SKIE worth adopting (ADR-0002).

### 4.3 Which gates run on iOS — all three reviewers are partly right; the synthesis beats each of them

**The dispute.** Build & Release: keep all six byte-verification gates JVM-only (they verify bytes in
git, identical on both platforms; running them twice proves nothing and burns macOS minutes at 10×)
and add exactly two iOS gates — a Room-open test and a bundle-SHA integrity test. Verification: move
the four plan gates to `commonTest` and run them on iOS *because that additionally proves the assets
were packaged into the iOS bundle*. Architect (ADR-0010): plan gates to `commonTest`, BibleText stays
JVM.

**D-PORT-5 — the Verification engineer's stated reason is wrong, given ADR-0010's own design.** The
plan gates deliberately read the **source tree** — via the `planAssetsDir` system property today, via
a generated absolute-path constant and okio `FileSystem.SYSTEM` after the port — precisely because
"they read the exact files that ship, from the source tree" is what makes them trustworthy. A
Kotlin/Native test on a *simulator* can see that host path and will pass happily without any asset
ever entering an app bundle. **Running the plan gates on iOS proves nothing about packaging.** The
conclusion (move them to `commonTest`) is right; the justification is not, and shipping the wrong
justification into the record would licence a false claim later.

**But the packaging question is real, and Build & Release named the gate that answers it.** ADR-0011
flags a specific trap: iOS bundle resources are flat by default unless the directory is added as a
*folder reference* (blue) rather than a *group* (yellow) — get it wrong and `plans/mcheyne/plan.json`
becomes `plan.json`, colliding with two identically-named files. That is a 20-minute mistake that
presents as a mysterious "wrong plan loaded" bug. **A bundle-SHA integrity test catches it for
pennies.**

**Resolution — a five-tier ledger:**

| Tier | Gate | Assertions | Where | Runs on |
|---|---|---|---|---|
| 1 | 4 plan gates (`ReadingPlan` 11, `Mcheyne` 10, `Chronological` 8, `PlanSegment` 6) | 35 | `commonTest` | JVM/Android **every PR**; iOS targets **on the release pipeline only** |
| 2 | `BibleTextVerificationTest` | 18 | shared `jvmTest`, unchanged, on sqlite-jdbc | JVM only. The iOS release pipeline **depends on the task passing** and reports it explicitly |
| 3 | `BibleDatabaseRoomOpenTest` | 5 | `androidUnitTest`, unchanged, forever | Android only |
| 4 | **NEW** `BibleDatabaseOpenTest` (iOS) | 5 | `iosTest` | iOS. **Release-blocking. Not optional.** Gen 1:1, John 3:16, John 11:35, Ps 3 verse-0 superscription — the same four probes. This is the test that would have caught sprint-00F |
| 5 | **NEW** `BundleAssetIntegrityTest` (iOS) | 5 | `iosTest` | iOS. Resolves each of the five assets from `NSBundle` at its expected **nested** path and asserts SHA-256 against a generated constant |

Tier 1's split addresses Build & Release's cost objection without weakening anything: `commonTest`
means the gates compile for every target and run **free** on the Linux PR runner; the iOS-target
execution runs where a release is being cut, which is the only place "no silently skipped gate"
actually binds. Build & Release's "running them twice proves nothing" is right about *packaging* and
wrong about *Kotlin/Native* — a K/N JSON-parsing or arithmetic divergence would surface here and
nowhere else, which is cheap insurance for the project's core IP.

**D-PORT-6 — the reporting rule, non-negotiable.** The honest count is
**`common: 35 · jvm: 18 · android: 5 · ios: 10 (both new)`**. Never "all gates run everywhere." This
pre-empts the escalation the delivery brief anticipates, and it corrects that brief's acceptance
criterion 2 ("assertion counts match the Android run"), which the Build & Release engineer correctly
called not achievable as written and an invitation to a false claim.

**Also corrected: the brief says five gates. There are six.** `PlanSegmentGateTest` (6 assertions,
0 violations across 2,920 portions in all three plans) postdates the brief and is a gate by every
meaningful criterion. Report six.

### 4.4 What ships to Play first — three releases, not one, and not either proposed grouping

**The dispute.** Build & Release wants **one** Android-only release containing all four rewrites
(Hilt→Koin, java.time, Ktor, Truth) before any module is created — 3.5–4.5 weeks, no iOS binary.
Architect (ADR-0008) wants short spikes then an Android-only **persistence** restructure shipped
alone: `1.8.x → 1.9.0 "internal restructure, no user-visible change" → iOS 1.0.0`.

**Both are arguing "change one variable at a time," and both then group more than one variable.** The
framing that resolves it is not scope but **failure mode** — and this codebase has *already shipped*
two of the three failure classes involved:

| Change | Failure mode | Silent? | Realised here before? |
|---|---|---|---|
| Truth → assertk | A weakened assertion | **Yes** | — (but ships zero production bytes) |
| Ktor + URL encoding | Translations fail to fetch | No — the D-OT-2 fallback banner already exists | — |
| java.time → kotlinx-datetime | Wrong dates; `toEpochDays()` keys every progress row | No, and fully JVM-provable | — |
| **Hilt → Koin** | **Runtime crash under R8 that debug builds hide** | Loud when it happens, invisible until then | **Yes — 1.7.0** |
| **Persistence relocation** | **Crash-on-open, or silent settings reset** | **Yes, for DataStore** | **Yes — sprint-00F class** |

**D-PORT-7 — three Android-only Play releases, ordered by failure mode, each vitals-watched before
the next.**

| Release | Contains | Why grouped | Mandatory gate before tag |
|---|---|---|---|
| **1.9.0** | java.time → kotlinx-datetime · Ktor + `encodeURLParameter` · okio · `kotlin.uuid.Uuid` · the `Dispatchers.IO` sweep · test-framework conversion where already touched | Nothing here can brick an install. All failure modes are loud or already have a fallback. | The epoch-day equivalence pin (ADR-0009) over known dates incl. pre-1970 and leap days. `ProviderUrlBuilder` output **byte-identical** — `URLEncoder.encode` is form-encoding (space → `+`), Ktor's is path-encoding (space → `%20`); any diff is a bug in the port, not in the test. |
| **1.10.0** | Hilt → Koin only. Still one `:app` module. | Isolated because its failure mode is the 1.7.0 class. | Koin `checkModules()` as a CI unit test — **ADR-0012 says the decision is materially worse than Hilt without it.** A ViewModel-construction smoke test. **An `assembleRelease` on-device smoke, not `assembleDebug`.** Generalize `ReaderViewModelHandoffInitTest` into a rule for every ViewModel that collects in `init`, run under `StandardTestDispatcher` — the current `MainDispatcherRule` uses `Unconfined`, "the most forgiving possible scheduling," which is precisely what let the 1.7.1 ordering bug hide. |
| **1.11.0** | The module split (`shared/{domain,platform,data,ui}`) + the persistence relocation + the asset move to `shared/assets/` | Isolated because it is the one change that can destroy user data. | **PG-1** — a real `progress.db` captured from a shipped 1.8.1 device, committed as a fixture, opens and returns identical results. The existing `ProgressMigrationTest` **synthesizes** a v1 DB and therefore does **not** prove this (Shared-Core engineer's finding — correct and important). **PG-2** — a real `settings.preferences_pb` reads back identical for every key. **PG-3** — `app/schemas/…/ProgressDatabase/2.json` byte-identical, CI-asserted. If it changes at all, **stop and escalate; do not regenerate the baseline.** `fallbackToDestructiveMigration` **stays off — not negotiable, not "temporarily during development."** |

Three cycles is more owner babysitting than one. It is justified because the marginal cost of a
release here is genuinely small — the `tag → alpha → promote` path is proven and the last production
promote took **39 seconds** — while the marginal cost of bisecting a crash spike across four
simultaneous changes in production is very large. If the owner wants one fewer cycle, **merge 1.9.0
and 1.10.0. Never merge 1.10.0 and 1.11.0.**

**Also settled here:** Build & Release presents "Phase 1 alone is 3.5–4.5 weeks and produces no iOS
binary" as a warning. Given that Apple enrolment has unpublished multi-day latency and Xcode is a
17 GB install that has not started, **producing no iOS binary for the first two months is a feature,
not a defect.** It is the plan's main parallelism lever (§5).

---

## 5. The phase plan

One plan, reconciling the Architect's lettered phases, the Build & Release engineer's sequence and
the team roster's numbered model. **Note for whoever dispatches this: the roster README uses phases
0–5, the task briefs use phases 0 / A–E, and nothing maps them. Unify the vocabulary before
dispatching anything, or briefs will target phases that do not exist.** The naming below is
authoritative for this program.

**Legend:** ☐ = needs neither Xcode nor an Apple account · ⌘ = needs Xcode installed · **$** = needs
Apple Developer Program enrolment.

### Gate 0 — kill it or commit (☐ mostly; the gesture rig needs ⌘)

**~1 week. Nothing else starts until this closes.**

| Task | Owner | Detail |
|---|---|---|
| **V1 — the Room identity hash** ☐ | Sr Shared-Core | Throwaway KMP module, unchanged `VerseEntity`/`BibleDatabase`, generate under Room KMP, compare the identity hash against `8144e1bc57f05006d1a15856ac762552` (`tools/build_bible_db.py:44`). **Timebox one day.** Decides ADR-0007 Stage 1a (keep Room + a `BundledDatabaseProvider` seam) vs Stage 1b (drop Room for the read-only bible DB). |
| **V2 — the schema tripwire** ☐ | Sr Shared-Core | Is Room KMP's exported `ProgressDatabase/2.json` byte-identical to the committed one? Blocks the entire persistence phase if not. |
| **The reader gesture rig** ⌘ | iOS Platform + Sr Shared-UI | A ~200-line throwaway CMP app: `HorizontalPager` → `LazyColumn` → `combinedClickable` long-press, on a **physical iPhone**. **This does not need the $99 program** — Xcode free provisioning runs a build on your own device for 7 days (INFERRED, standard Xcode behaviour, unverified here). Weaker evidence than the real reader, and the only evidence available this early. |
| V3, V5, V7 + `SavedStateHandle` + epoch-day ☐ | Core / Build & Release | `kotlinx.datetime.YearMonth` adequacy · a CMP-resolvable `material-icons-core` (fallback: vendor the ~8 glyphs as vector XML and drop the dependency — this project has done exactly that twice) · the Android `assets.srcDir` redirect preserving `planAssetsDir` **and its `inputs.dir` up-to-date declaration** (lose that and asset edits are silently skipped as UP-TO-DATE — a bug this project already paid for once) · CMP ViewModel `SavedStateHandle` support · `toEpochDays()` semantics. |

**Exit:** ADR-0007 and ADR-0008 accepted or their fallbacks chosen; the gesture rig passes on hardware
or the program stops; the owner has signed §3 and answered §10.

### Phase 1 — Android-only, shipped to Play (☐ entirely)

**5–8 weeks. Runs completely in parallel with the owner's Apple critical path. This is the lever.**

Three releases per D-PORT-7: **1.9.0** (datetime + HTTP + IO) → **1.10.0** (DI) → **1.11.0** (modules
+ persistence + assets).

Ordering *within* 1.11.0 matters and no ADR states it: the **asset move** (ADR-0011 — repo-wide, one
atomic commit, byte-diff CI jobs re-verified at zero) goes **before** the module split, because the
gates must be proven still reading the right files before anything else moves. ADR-0009 and ADR-0011
both claim "first / early"; this is the tie-break.

**Exit:** 1.11.0 is live on Play from the new module structure with **zero migration-related crash
signal in vitals** after 24–72 hours. No iOS target exists yet.

### Phase 2 — the shared core compiles for iOS (⌘)

**3–5 weeks. Blocks on Xcode only — not on Apple enrolment.**

Room KMP with `AndroidSQLiteDriver` on Android (byte-parity for shipped users, zero size cost) and
`BundledSQLiteDriver` on iOS — the Shared-Core engineer's recommendation, and the right one: swapping
the SQLite engine under live production user data buys nothing and costs ~1.5–3 MB per ABI. The
`BundledDatabaseProvider` seam (~20 lines per platform) replaces `createFromAsset` entirely. The
DataStore path is pinned to `<filesDir>/datastore/settings.preferences_pb` — the format is identical,
**the path is the whole risk, and getting it wrong is silent.** The remaining ~15 platform interfaces
get iOS actuals.

**Exit:** `shared/domain` and `shared/data` compile for `iosArm64` and `iosSimulatorArm64`; tier-1
gates green in `commonTest` on iOS targets; **the new `BibleDatabaseOpenTest` and
`BundleAssetIntegrityTest` green on a simulator.**

### Phase 3 — the shared UI and the iOS shell (⌘)

**4–7 weeks. The largest phase, and the test harness is most of it.**

161 strings and 181 call sites to Compose Resources (~11 deliberately duplicated for notifications
and the widget, each named and commented). ~4,000 LOC of Robolectric-bound Compose tests re-hosted on
`runComposeUiTest`. The SwiftUI host wrapping one `ComposeUIViewController`.

**Note the good news buried in the UI review:** string *values* do not change, so ~338 literal
assertions survive verbatim; only ~19 genuinely break, and they are the locale-formatted ones. The
cost is the harness, not the strings.

**Exit criteria — all three are exit criteria, not discoveries:**
1. Every screen renders in the simulator.
2. **The reader gesture pass on physical hardware** — long-press inside a `LazyColumn` inside a
   `HorizontalPager`, plus per-verse tap, plus chapter swipe across a book boundary.
   `performTouchInput { longClick() }` is synthetic and is **not** evidence.
3. **One combined worst-case layout prototype** — §6, item 3, and R8.

### Phase 4 — platform seam, parity, delivery (⌘ + **$**)

**2–4 weeks. The first phase that genuinely blocks on Apple enrolment.**

`UNUserNotificationCenter` scheduling + the §4.1 decision · **provisional authorization**
(`.provisional`) — quiet delivery with no permission prompt at all, a strictly better UX than the
Android launch-time prompt that D-S22-5 needed · `SFSafariViewController` + `UIApplication.openURL` ·
`UIPasteboard` · `NSDateFormatter` · a `WidgetCenter` no-op · `ITSAppUsesNonExemptEncryption = false`
in `Info.plist` (without it every TestFlight upload stalls on export compliance) · **no
`UIBackgroundModes` declared at all** — the app needs none, and declaring an unused one is a
gratuitous App Review question · the three iOS workflows · `fastlane match` · `docs/parity-matrix.md`
complete.

**Exit:** a tagged commit puts a signed build on TestFlight internal with no local step.

### Phase 5 — hardening and App Review (⌘ + **$**)

**2–4 weeks, dominated by owner hours and review latency.**

Full device pass (**6–10 focused hours over ≥2 sessions** — notification timing forces overnight
waits) · **VoiceOver pass, 3–4 hours the first time** — and the Verification engineer's warning
stands: *"if nobody on the team can drive VoiceOver competently, that is a hiring/consulting line
item, not an afternoon"* · release-configuration smoke on physical hardware · store listing ·
App Review.

**D-PORT-8 — assume the first App Store submission is manual.** `RELEASING.md` records this project's
most expensive pipeline lesson: the Play Developer API cannot create the first release on a track
that has never had one, which forced a manual Console promotion for 1.5.1. The Build & Release
engineer correctly flagged that the iOS runbook fails to carry that lesson across. App Store Connect
has the same shape — the first submission requires completed metadata, screenshots, privacy answers,
age rating and export compliance in the Console. **Budget a manual first submission and a rejection
round trip (a day or more). Automate the second.**

**Exit:** parity matrix clean, zero blockers, build accepted, phased release started.

### The `ios-release-smoke` gate — required from Phase 4 onward

The Verification engineer's structural finding is the most important thing in his review and it must
survive into the plan: **you cannot run Kotlin/Native unit tests on a physical iPhone.** Every
automated iOS result is simulator + debug + Apple Silicon; the shipped artifact is device + release +
arm64. **There is no configuration in which the suite runs against what ships.** Android had one axis
of divergence (debug vs R8) and it still shipped the 1.7.0 P0. iOS has three.

Mandatory pre-tag gate, three parts:

1. **A release-configuration build on physical hardware**, running a scripted XCUITest of exactly the
   paths that broke before — the 1.7.0 reading-tap crash, the 1.8.1 picker jump, the 00F reader-load
   failure — in one ~90-second run.
2. **A DCE canary.** Kotlin/Native release links do aggressive dead-code elimination; anything
   reachable only through the Obj-C bridge can be present in debug and stripped from release, and it
   surfaces as a missing symbol or a nil — **not a stack trace**.
3. **The ViewModel-init rule**, generalized from `ReaderViewModelHandoffInitTest`, under
   `StandardTestDispatcher`.

---

## 6. Effort — order of magnitude only

The reviewers gave relative shapes and a few week-estimates. **I will not manufacture precision they
did not have.** What is defensible:

| Phase | Engineer-weeks | Basis |
|---|---|---|
| Gate 0 | 1 | V1 timeboxed at one day (ADR-0007); the gesture rig is 2–3 days |
| Phase 1 (three Play releases) | 5–8 | Build & Release: Hilt→Koin 1.5–2wk, java.time 1wk, Ktor 0.5–1wk, Truth 0.5wk = 3.5–4.5wk, plus the module split and the persistence fixtures |
| Phase 2 (core → iOS) | 3–5 | Shared-Core: core total ≈ 6–7× the java.time sweep, **half of it Room** |
| Phase 3 (UI + shell) | 4–7 | Shared-UI: 45 files / 6,640 LOC, ~30 mechanical + ~10 needing design work; **4,046 LOC of Compose tests** |
| Phase 4 (platform + delivery) | 2–4 | ~15 actuals, three workflows, signing |
| Phase 5 (hardening + review) | 2–4 | Verification: ≈80–90 net-new iOS tests, 6–10h device pass, 3–4h VoiceOver, App Review latency |
| **Total** | **16–28 engineer-weeks** | **≈ 4–7 months as one stream** |

Phase 1 and the owner's Apple critical path are fully parallel. Phases 2–5 are largely serial — the
Verification engineer is the binding constraint inside them, and the **owner is the binding
constraint across the whole program**.

### The three items most likely to blow this estimate

1. **The test-suite conversion.** **846 of 940 assertions go through Google Truth** (Guava-based,
   JVM-only) and every one needs rewriting; ~4,000 LOC of Robolectric-bound Compose tests need
   re-hosting; ~80–90 iOS tests need writing from scratch. **This was in nobody's original estimate.**
   It is the largest single mechanical item in the port, it produces zero user-visible progress, and
   that combination is precisely what gets under-resourced and then rushed. Rushing it silently
   weakens the thing that makes this codebase trustworthy. Mitigation: convert incrementally alongside
   the code each test covers (ADR-0010 explicitly rejects a big-bang); choose **assertk** over bare
   `kotlin.test`, which would throw away `assertWithMessage` — the mechanism the data gates use to
   produce diagnosable failures; and **mutation-verify all six data gates after conversion**, exactly
   as this project does for features.
2. **Room KMP and the bible.db identity hash.** The Shared-Core engineer rated Room at **2.5–3.5×**
   the java.time sweep and **half the entire core effort — and that is the good branch.** If V1 says
   the hash differs, ADR-0007 Stage 1b cascades into `BibleTextVerificationTest`,
   `BibleDatabaseRoomOpenTest`, `tools/build_bible_db.py`, the byte-diff reproduction job and its
   hard-won `LD_PRELOAD` SQLite-3.43.2 pinning. The Shared-Core engineer's cheap permanent fix — flip
   `exportSchema = true` on `BibleDatabase`, check in the schema, and CI-assert that **every target's**
   generated `identityHash` equals the Python constant — converts this from HIGH to LOW and should be
   adopted. **Note that it reverses a recorded sprint-00F decision** (`exportSchema` stays `false`;
   the hash is a pinned build artifact); record it as superseding, do not slip it in.
3. **The iOS device-pass ratio inversion.** *"Android's device-pass backlog was clearable because 940
   automated tests cleared everything else first. On iOS, day one, the automated suite covers less
   while the unprovable list is longer. The ratio inverts."* Three axes of divergence instead of one,
   VoiceOver competence unresolved, and an owner whose available hours are finite. This is the item
   that turns "done" into "done in three more weeks," repeatedly.

---

## 7. The owner's critical path

**Start items 1–4 today, regardless of the go/no-go decision.** They have multi-day latency, they
cost $99 and an evening, and nothing about them is wasted if the port is later cancelled — the App
Store name in particular is globally first-come.

| # | Action | Latency | Blocks | Start now? |
|---|---|---|---|---|
| **1** | **Install Xcode.** Mac App Store, ~17 GB, 30–60 min, then `sudo xcode-select -s …`, `sudo xcodebuild -license accept` (needs your admin password). Free — needs an Apple ID, **not** the $99 program. | 1 evening | **Everything iOS, including the Gate 0 gesture rig and all of Phase 2.** INFERRED: Kotlin/Native cannot compile Apple targets with Command Line Tools alone. | **YES** |
| **1a** | **Free ~50 GB first.** The Mac is at **94% used / 126 GB free**; Xcode is ~17 GB to download and ~40 GB installed, before `~/.konan` caches and DerivedData. | — | item 1 | **YES** |
| **2** | **Decide entity type.** Individual (**your legal name becomes publicly the seller on every listing**) vs Organization (needs a **D-U-N-S number**, a work email on an org domain, a public website, possibly notarised documents). Recommendation: Individual. | decision | item 3 | **YES** |
| **3** | **Enrol in the Apple Developer Program — via the *web* path, not the Apple Developer app.** $99/yr. The web path needs no photo ID; the app path *mandates* a driver's-licence/passport scan and bills as an auto-renewing subscription. Pay with **your own** credit card or enrolment is delayed pending photo ID. | **Apple publishes no timeline.** Often same-day for individuals. If no confirmation email within **24 hours**, contact Developer Support. | items 4–8 | **YES** |
| **4** | **Create the App Store Connect app record to reserve the name.** "Daily Bible Reading Planner" is 27/30 chars, globally unique, first-come, and is not reserved until a record exists. Do this the hour enrolment clears. Fallbacks in order: `Daily Bible Reading Plan`, `Bible Companion Readings`, `Daily Readings — Bible`. | 10 min | store listing | **YES, the hour item 3 clears** |
| **5** | **Register the bundle ID `com.jpillion.dailyreadingplanner` — and enable App Groups.** The runbook says leave all capabilities off; **§4.2 amends that.** Enabling App Groups later invalidates provisioning profiles. | 5 min | signing | after 3 |
| 6 | App Store Connect API key. Record Issuer ID, Key ID and the `.p8` — **downloadable exactly once.** Access level **App Manager**, not Admin. | 5 min | CI | after 3 |
| 7 | Certs repo + `MATCH_PASSWORD`; run `fastlane match appstore` **locally, once** — needs Xcode + enrolment, and the private key should originate on your machine. | 10 min | CI signing | after 1, 3 |
| 8 | The seven GitHub secrets. | 5 min | CI | after 6, 7 |
| **9** | **Choose the macOS CI cost model.** GitHub Free on a private repo = 2,000 min = **200 macOS minutes at the 10× multiplier — less than one release run.** Options: a self-hosted runner on your M3 Max for PR CI (2–4 min warm vs ~25 min cold hosted) with GitHub-hosted releases at ~$15–25 each; or pay for hosted minutes throughout. Caveats: disk is 94% full, and a self-hosted runner on a private repo is a supply-chain surface. **Build & Release must not choose this for you.** | decision | Phase 4 | soon |
| **10** | **Author and host a privacy policy URL.** **Mandatory for every App Store app, no exceptions. Nothing in this repo records one.** | your call | submission | soon |
| **11** | **Decide the App Privacy questionnaire answer.** Sprint 00R sends requests to `drp-bible-proxy…run.app`, and API.Bible obliges FUMS usage reporting. Whether that is "Data Not Collected" needs a deliberate answer, not a guess. | decision | submission | soon |
| **12** | **Decide the content-rights answer.** KJV is public domain; **NKJV and NASB are licensed** through API.Bible. Answer yes and be ready to describe the licence. | decision | submission | soon |
| 13 | Produce a **1024×1024** app icon from vector source. The current asset is a 2× upscale (a working stand-in, not a shipping asset), and its three dots signified the Bible Companion's three streams — mildly inaccurate since 1.5.0, when plans gained 1, 3 and 4 streams. Check fine detail at 60×60. | 1 hour | listing | later |
| 14 | Screenshots — at least one iPhone set at the largest supported display size. Recommendation: **iPhone-only for v1.** | 1 hour | listing | blocked on the app |
| **15** | **Sign off §3 — the ships / doesn't ship / degrades list.** Before Phase 2, not at TestFlight. | decision | Phase 2 | **YES** |
| 16 | Decide whether the standing string tone sign-off backlog (S11–S22, S-D, S-L, the alt-schedules set, the 5 caption strings, the external-app help caption) gets cleared, or ships as draft copy on a second platform too. | decision | Phase 5 | soon |

---

## 8. Risk register

| # | Risk | Impact / likelihood | Mitigation | Owner |
|---|---|---|---|---|
| **R1** | **Room KMP generates a different identity hash for `bible.db`.** The asset carries a hand-forged `room_master_table` row (`tools/build_bible_db.py:44`). A mismatch means every chapter fails to open, on **both** platforms. | HIGH / UNVERIFIED | **Spike it in one day, before any bible work** (V1). ADR-0007 Stage 1b (drop Room for a read-only two-table, four-query DB) is a designed fallback. Then adopt `exportSchema = true` plus a CI assertion that every target's hash equals the Python constant — the permanent fix. | Sr Shared-Core |
| **R2** | **`ProgressDatabase` relocation bricks or resets live Android users.** Real users at schema v2 with irreplaceable history; `fallbackToDestructiveMigration` deliberately off. DataStore resets are **silent** — theme reverts, tracking-start lost, plan reverts to Bible Companion, first-run dialogs re-fire. | HIGH / MEDIUM | Isolate in its own Play release (D-PORT-7). PG-1 fixture from a **real 1.8.1 device** — the existing test synthesizes a v1 DB and does not prove this. PG-2 for DataStore. PG-3 `2.json` byte-identical tripwire; if it changes, **stop and escalate.** | Sr Shared-Core |
| **R3** | **Hilt → Koin crashes only under R8.** Koin resolves lazily where Hilt resolved statically — exactly the class of change R8 exposes and debug builds hide. Realised here as the 1.7.0 P0. | HIGH / MEDIUM | Isolate in its own release. `checkModules()` as a CI unit test (**without it this decision is worse than Hilt**). A ViewModel-construction smoke test. **`assembleRelease` on-device smoke before every tag.** The ViewModel-init rule under `StandardTestDispatcher`. | Build & Release + Verification |
| **R4** | **The test-suite conversion silently weakens the gates.** 846 Truth assertions, 4,046 LOC of Compose tests, 80–90 new iOS tests. | HIGH / MEDIUM | Convert incrementally alongside the code each test covers. Choose **assertk** (bare `kotlin.test` throws away `assertWithMessage`). **Mutation-verify all six data gates post-conversion.** | Verification |
| **R5** | **The reader gesture stack fails on iOS hardware.** Long-press inside a `LazyColumn` inside a `HorizontalPager` is a three-way gesture conflict. ADR-0004: *"if requirement (1) fails on device, we have a real problem with no cheap fallback."* | HIGH / MEDIUM | **Spike it in 2–3 days, before anything else** — a throwaway CMP gesture rig on a physical device via free provisioning (no $99 needed). Re-verify on the real reader as a **Phase 3 exit criterion**. **A native SwiftUI reader is not the fallback** — §9. | iOS Platform + Sr Shared-UI |
| **R6** | **The owner meets the smaller iOS product at TestFlight.** | HIGH / HIGH if unmanaged | §3, signed before Phase 2. This risk is entirely avoidable and entirely a process failure if it happens. | Owner + EM |
| **R7** | **Owner hours are the binding constraint and nobody costed them.** Enrolment, privacy policy, two questionnaire decisions, icon, screenshots, CI cost model, scope sign-off, a 6–10h device pass, a 3–4h VoiceOver pass, and a 15-sprint string backlog. None of it is engineer-parallelizable. | HIGH / HIGH | Front-load everything with latency (§7 items 1–4, today). Accept that the string backlog may ship as draft on both platforms. | Owner |
| **R8** | **One-screen-fit budgets are invalid on iOS.** Every layout budget from sprints 16 / 18 / 20 and the N-stream fix was tuned on a Pixel 7 Pro. iOS Dynamic Type reaches **~310% (AX5)**; composed with the app's own 0.85–1.5× slider that is **~4.6×**. Plus the ~80dp bottom nav bar. | MEDIUM / HIGH | Prototype **one** worst case in Phase 3: 4-stream M'Cheyne day cards plus the stats panel, smallest iPhone, AX5, slider at 1.5×. Expect to re-tune; do not expect the Android budgets to hold. | Sr Shared-UI |
| **R9** | **macOS CI cost and disk.** 200 macOS minutes/month on Free is less than one release run. First-ever Kotlin/Native link 20–45 min; `linkReleaseFrameworkIosArm64` 6–15 min locally, **12–25 min on a hosted runner** (release builds support no compiler caches). Mac at 94% disk. | MEDIUM / HIGH | Owner picks the model (§7 #9). Free 50 GB before Xcode. | Build & Release |
| **R10** | **Assets silently flatten in the iOS bundle.** ADR-0011: folder reference (blue) vs group (yellow) — get it wrong and `plans/mcheyne/plan.json` becomes `plan.json`, colliding with two same-named files. Presents as "wrong plan loaded." | MEDIUM / MEDIUM | The new `BundleAssetIntegrityTest` (§4.3 tier 5) — SHA-256 per asset at its expected nested bundle path. | Build & Release + Verification |
| **R11** | **A WidgetKit extension reading shared progress via an App Group** stacks four unverified assumptions; the iOS Platform engineer bets a defect on the fourth — SQLite in a shared container under concurrent app+extension access, where *"a widget that reads correctly on an unlocked device and silently fails to refresh on a locked one"* is exactly what never shows up until a device sits locked overnight. | HIGH / MEDIUM | Deferred out of v1.0 (§4.2). When the widget sprint runs, this is its first spike, and the file-protection class is the first thing to pin. | iOS Platform |
| **R12** | **Android feature work stalls, and no progress-schema change can ship** for the duration (ADR-0008). | MEDIUM / HIGH | Owner decision (§10 #7). Say yes or say no; do not discover it. | Owner |
| **R13** | **App Check must now be configured twice**, and the proxy runs `POLICY_ON_ATTESTATION_FAIL=allow` today — publicly reachable, live in production since 1.8.0. iOS App Check is a different SDK from Android's. | MEDIUM / MEDIUM | Pre-existing; the port makes it more expensive. Worst case is a third party burning the Firestore budget guard; no API key or user data is exposed. The longer it stays open the more it costs. | Owner |
| **R14** | **No configuration runs the suite against what ships on iOS** — three axes of divergence (simulator/device, debug/release, host arch/arm64). | HIGH / CERTAIN | The mandatory `ios-release-smoke` gate (§5). This does not remove the risk; it is the only mitigation available. | Verification |
| **R15** | **The three vendor PDFs behind the plan assets** (edginet M'Cheyne, TGC/Carson, BLB Chronological) have **no immutable ref** — the SHA *is* the pin. If a publisher replaces one, that CI job goes permanently red. Pre-existing and unchanged by the port, but the port touches those jobs. | LOW / MEDIUM | Do not re-pin to whatever is served that day. Mirroring the pinned bytes is the fix and it is the owner's call (repo bloat + third-party copyright). **Do not move `data-rebuild` to a macOS runner** — §9. | Build & Release |

---

## 9. What we are explicitly NOT doing

This project's discipline is as much about refusals as decisions. Each of these is a refusal, not an
oversight.

1. **Not rewriting the reader in SwiftUI.** The Shared-UI engineer asked for this to be written down
   so it does not get proposed under schedule pressure, and he is right. It would duplicate ~1,900
   lines across 19 files — the product of sprints C, G, H, I, J, K, L, N and Q **plus a P0 hotfix** —
   and it is the single most likely place in the program to reintroduce a shipped P0. ADR-0004 is
   blunter: *"if the reader is native, there is no meaningful shared core left and the KMP decision
   collapses."*
2. **Not shipping a native iOS navigation shell.** You would still write every screen in Compose, pay
   the interop tax, get no code back, and lose `NavRegressionTest`. Three destinations do not justify
   it.
3. **Not shipping a WidgetKit widget in v1.0** — but reserving the App Group so that adding it later
   is not a user-data migration (§4.2).
4. **Not faking the persistent notification with a Live Activity.** Wrong semantics, an ~8–12h
   ceiling, a lifecycle designed for things that end, and a plausible App Review objection.
5. **Not adding Background App Refresh or silent remote push** to preserve reminder suppression.
   `BGAppRefreshTask` is opportunistic, not a schedule — building correctness on a best-effort budget
   produces a feature that works on the developer's phone and not the user's. Push needs a server,
   APNs, a capability and a privacy answer, for a marginal behaviour.
6. **Not declaring any `UIBackgroundModes`.** The app needs none. Declaring an unused one is a
   gratuitous App Review question.
7. **Not changing the `ProgressDatabase` schema during the port.** Any schema change ships in a
   separate release *after* the port.
8. **Not enabling `fallbackToDestructiveMigration`. Ever. Not "temporarily during development."** It
   converts a loud, diagnosable crash into the silent deletion of every user's reading history.
9. **Not refactoring during the port.** *"Refactor-during-port is how ports fail."* The one exception
   is ADR-0007 Stage 1b, which is a forced choice, not opportunism.
10. **Not moving the `data-rebuild` CI job to a macOS runner.** Its `LD_PRELOAD` of a self-compiled
    SQLite 3.43.2 has no macOS equivalent — `DYLD_INSERT_LIBRARIES` is SIP-blocked. Doing so reopens
    the defect that sat red on `main` for six weeks.
11. **Not duplicating assets.** Exactly one copy of the plan JSONs and `bible.db` in git;
    `find . -name bible.db -not -path './*/build/*'` returns one path.
12. **Not localizing.** The app has never been localized; the port does not change that, and ADR-0013
    is chosen partly so a future localization stays easy rather than being done now.
13. **Not adopting SKIE or Swift Export in v1.0.** No sealed type, generic, default argument or
    `suspend` function crosses into Swift. SKIE is adopted the moment WidgetKit is.
14. **Not reporting "all gates run everywhere."** §4.3, D-PORT-6.
15. **Not claiming `AccessibilityGateTest` green means iOS accessibility is verified.** It pins the
    *input* to CMP's UIAccessibility bridge, which is not at SwiftUI parity — **it will go green on
    iOS and prove strictly less there.** Related: 48dp is an Android convention (Apple's HIG minimum
    is 44pt); keep 48 as deliberately stricter, and record that it is deliberate.
16. **Not shipping MySword, an Apple Watch app, a Dynamic Island presence, or an iPad-optimised
    layout in v1.0.** iPhone-only.

---

## 10. Decisions we need from the owner

Each is answerable without engineering knowledge.

1. **Do we do this at all?** The alternatives are: proceed with the KMP port as described; stay
   Android-only; wait a year for Room and Compose Multiplatform to mature further; or commission a
   native iOS rewrite. *(Recommendation: proceed, gated on Gate 0.)*
2. **Do you sign off §3?** Specifically: iOS ships with **no home-screen widget, no always-present
   notification, no in-app update prompt, no MySword — and therefore no glanceable surface at all.**
   Yes or no, before Phase 2 begins.
3. **The iOS daily reminder: generic or specific?** *(a)* "Today's readings are ready" — never wrong,
   never stale, but it also fires on days you have already finished. *(b)* The actual references,
   correctly suppressed when you have finished the day — but if you do not open the app for about two
   months it silently stops reminding you until you do. There is no option that gets both.
   *(Recommendation: (a) for v1.0.)*
4. **Reserve the App Group container on iOS now**, even though the widget ships later?
   *(Recommendation: yes — it is nearly free now and a user-data migration later. It also changes what
   you tick when you register the bundle ID, so it needs answering before that step.)*
5. **Apple Developer Program: Individual or Organization?** Individual is faster and cheaper to set
   up, but **your personal legal name appears publicly as the seller on every App Store listing.**
   Organization needs a D-U-N-S number and takes longer. *(Recommendation: Individual.)*
6. **macOS CI: a self-hosted runner on your Mac for PR builds with GitHub-hosted releases (~$15–25
   each), or paid hosted minutes throughout?** GitHub's free allowance is less than one release run.
   *(Engineering must not choose this for you.)*
7. **Are you prepared for Android feature work to stop, or slow substantially, for 4–7 months** — and
   for no reading-progress schema change to ship until the port lands?
8. **Are three Android-only Play releases acceptable**, each with essentially empty release notes,
   each needing you to watch vitals for a day or two before the next? *(If you want one fewer, merge
   the first two — never the last two.)*
9. **Who writes and hosts the privacy policy, and by when?** It is mandatory for every App Store app
   and nothing in this repo records one. It blocks submission and nothing else unblocks it.
10. **Does the online-translations proxy plus API.Bible's FUMS usage reporting count as data
    collection** on Apple's App Privacy questionnaire? This needs a decision, not a guess, and it is
    the answer most likely to draw an App Review question.
11. **Do you want to consider shipping iOS v1.0 without the in-app Bible reader** — planner, schedule,
    stats and external Bible links only? It would remove the largest technical risk in the entire port
    (the 5.6 MB `bible.db`, the Room identity hash, the reader gesture stack, the Psalm-119 scroll
    profile) and reach the App Store materially faster. *(Recommendation: no — it ships a 2024-shaped
    product and abandons what V3 was for. But it is the single largest scope lever available and you
    should know it exists rather than have it withheld.)*
12. **Does the standing string tone sign-off backlog get cleared before iOS ships**, or does iOS ship
    draft copy too? Roughly 15 sprints' worth of user-visible strings still await your sign-off on
    Android.

---

## Appendix — corrections to the existing record

Errors and gaps found during synthesis. Each should be fixed in its source document, not just here.

| # | Where | Correction |
|---|---|---|
| A1 | `task-briefs/ios-delivery-pipeline.md`, ADR-0010, several reviews | **There are six data gates, not five.** `PlanSegmentGateTest` (6 assertions, 0 violations across 2,920 portions) postdates the brief. |
| A2 | Everywhere | **940 tests, not 936.** The 936 figure is one release stale. |
| A3 | ADR-0005, option (c) | **The hybrid reminder design is impossible.** A repeating `UNCalendarNotificationTrigger` has no start date and cannot be date-excluded; inside the horizon it would fire alongside every dated one-shot. The choice is binary. (INFERRED — verify in the Phase 4 spike.) |
| A4 | `RELEASING-IOS.md` Step 2 | **Enable App Groups at bundle-ID registration.** The current instruction ("leave everything off") is correct in isolation but wrong given §4.2, and adding the capability later invalidates provisioning profiles. |
| A5 | Verification review | **Running the plan gates on iOS does not prove bundle packaging** — ADR-0010 has them reading the source tree via an absolute path. The conclusion is right; the reason is not. Packaging needs the separate `BundleAssetIntegrityTest`. |
| A6 | `task-briefs/ios-delivery-pipeline.md` criterion 2 | **"Assertion counts match the Android run" is not achievable as written** and invites a false claim. Replace with the D-PORT-6 ledger. |
| A7 | `task-briefs/ios-delivery-pipeline.md` trigger paths | It hard-codes `app/src/main/assets/**` as a CI trigger path **that the port deletes** (assets move to `shared/assets/` per ADR-0011). |
| A8 | `RELEASING-IOS.md` | **Does not carry across `RELEASING.md`'s most expensive lesson** — that the Play API could not create the first release on an empty track, forcing a manual Console promotion for 1.5.1. App Store Connect has the same shape. D-PORT-8. |
| A9 | `teams/ios-port/README.md` | The roster uses **phases 0–5**; the briefs and ADRs use **phases 0 and A–E**; nothing maps them. Also, its Phase 4 exit criterion is *"TTS, notifications, background audio verified on physical hardware"* — **this app has no TTS and no audio** (D-V3-14: `ReaderAudioSlot` deliberately renders nothing; audio is deferred to V4). The roster template is mis-tuned to this product. |
| A10 | Sprint-00F record vs the Shared-Core review | The proposal to flip `exportSchema = true` on `BibleDatabase` **reverses a recorded decision.** It is the better decision — it converts a hand-copied constant into a CI-asserted invariant across N per-target generators — but record it as superseding sprint-00F; do not slip it in. |
| A11 | `docs/data/README.md` | Still records the bible asset SHA as `ce174e9…29da4909`; the committed asset is `ad46a777…9099` (regenerated in sprint-00F). Pre-existing; worth correcting before the port touches that pipeline. |
| A12 | Repo | `.github/workflows/zz-sqlite-probe.yml` ("TEMPORARY - delete before merge") is still on `main` as an active workflow. Delete it before adding three more. |
| A13 | ADR-0011 / delivery brief | The iOS bundle should **not** simply inherit Android's 12 MB gate. An `.ipa` with the same assets plus a Kotlin/Native framework, unsplit by ABI, will be larger than the 7.86 MB AAB. Nobody has estimated it. Set an iOS-specific number once one real archive exists. UNVERIFIED. |
| A14 | Nobody's review | **Nobody stated a program-level stop rule.** D-PORT-1 supplies one. |
