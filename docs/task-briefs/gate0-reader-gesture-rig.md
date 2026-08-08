# Gate 0 · The reader gesture rig — long-press inside a lazy list inside a pager, on real iPhone hardware

> **Brief type:** Gate 0 spike. Timeboxed, throwaway, output is an **answer**, not code.
> **Open questions are the point of this brief.**
>
> **Assignee:** iOS Platform Engineer (drives) + Senior Shared-UI Engineer (writes the Compose)
> **Merge-order position:** **Gate 0, item 2 of 4. Runs in parallel with the identity-hash spike.**
> **Timebox: 3 days**, of which ~1 is Xcode/toolchain setup on a machine that has never built for
> iOS.
> **Decides:** ADR-0004 (the reader stays Compose Multiplatform) and risk R5.

---

## Objective

Prove — **on a physical iPhone**, not a simulator — that a **long-press target inside a
vertically-scrolling `LazyColumn` inside a horizontally-paging `HorizontalPager`** behaves
correctly under Compose Multiplatform on iOS.

That three-way gesture stack is the interaction design of the app's highest-value screen. If it
does not work and there is no fix inside a week, **the program stops** (D-PORT-1).

---

## Context

`bible/ui/reader/` is ~1,900 lines across 19 files and is the product of sprints C, G, H, I, J, K,
L, N and Q **plus a P0 hotfix**. Its structure:

- A `HorizontalPager` over all **1,189 chapters** (`GlobalChapterIndex`, D-H-2), or over a
  portion-anchored index in Reading context (`ReadingPagerIndex`, D-I-1).
- Each page is a `LazyColumn`, **one item per verse**, keyed by `VerseText.canonicalId` (D-V3-12).
- Each verse is a `combinedClickable` ≥48dp target: **short tap** opens a 3-item action menu,
  **long press** enters multi-select; further taps add and remove verses
  (`bible/ui/reader/ReaderScreen.kt:410`).

**Nobody has ever proven this works on iOS, and nobody can prove it in a test.** CLAUDE.md records
the Android position explicitly: `performTouchInput { longClick() }` is **synthetic and is not
evidence**; it says nothing about whether the gesture fights the pager's horizontal drag or the
list's vertical scroll. On Android the owner's device pass is what confirmed it — and it did
confirm it, on 2026-07-25. iOS has had no equivalent.

Two things make this genuinely uncertain on iOS rather than merely unverified:

1. **iOS has a system long-press convention on text** — magnifier, grab handles, the system
   Copy/Look Up menu. The app deliberately avoids framework text selection (zero `TextField`,
   zero `SelectionContainer`, zero `AndroidView` — verified across all 162 files), which is why
   ADR-0004 concludes Compose's weakest iOS area is largely dodged. But **avoiding the framework
   widget is not the same as avoiding the platform gesture convention**, and G8 in the signed-off
   approach flags exactly this.
2. **iOS's interactive edge-swipe-back** belongs to navigation and competes with horizontal drag
   near the screen edge.

ADR-0004 is blunt about the stakes: *"if requirement (1) fails on device, we have a real problem
with no cheap fallback."* And §9 item 1 of the approach records the refusal that must survive
schedule pressure: **a native SwiftUI reader is not the fallback** — it would duplicate ~1,900
lines and is the single most likely place in the program to reintroduce a shipped P0.

### Provisioning — read this before asking the owner for anything

**This spike does NOT need the $99 Apple Developer Program.** Xcode **free provisioning** signs a
build onto your own device with a free Apple ID; the install expires after 7 days, which is far
longer than this spike lives.

> **Evidence status: INFERRED** — standard Xcode behaviour, unverified on this machine. If free
> provisioning turns out to be blocked, say so *immediately*; it converts this spike from
> "unblocked today" to "blocked on the owner's enrolment," which changes the program's critical
> path. **Do not silently wait for enrolment.**

It **does** need Xcode installed (~17 GB, and the machine is at 94% disk — see the owner critical
path, §7 items 1 and 1a). That is the real prerequisite.

---

## Contract

### What you build

A **~200-line throwaway Compose Multiplatform app.** Not the real reader. Not a port of
`ReaderScreen`. A rig.

```
HorizontalPager(state = rememberPagerState { 20 })       // ≥20 pages: real fling, real edges
  └── LazyColumn                                          // ≥60 items per page: real scroll
        └── items(60) { i ->
              Text(
                "Verse $i — <a sentence of ~25 words so lines wrap at least twice>",
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(min = 48.dp)
                  .background(if (i in selected) HIGHLIGHT else Color.Transparent)
                  .combinedClickable(
                    onClick    = { log("TAP page=$page item=$i") },
                    onLongClick= { log("LONGPRESS page=$page item=$i"); selected += i },
                  )
              )
            }
```

Requirements on the rig itself:

- **An on-screen event log** — the last ~8 events, visible without a debugger cable. You will be
  poking at a phone with two hands; a Xcode console you cannot see is useless.
- **Wrapping text**, not one-word rows. Line-breaking interacts with hit-testing.
- **≥48dp row height**, matching the app's own target (deliberately stricter than Apple's 44pt
  HIG minimum — keep 48 and record that it is deliberate).
- **A visible selection highlight**, so multi-select is observable.
- **Nothing else.** No navigation, no DI, no theme work, no resources. Every line you add is a
  line that could explain a failure that is really about your rig.

### Test on

- **A physical iPhone. This is the entire point of the spike.** A simulator result is
  categorically not evidence here — trackpad-synthesised long-press has different timing and no
  contact geometry.
- If two devices are available, use the **oldest** supported one as well; gesture arbitration is
  frame-timing sensitive.
- **A release-configuration build if you can get one**, given the standing lesson from 1.7.0 that
  a debug device pass does not cover R8/DCE. If free provisioning makes that awkward, debug is
  acceptable for this spike — record which you used.

---

## Acceptance criteria — six, each pass/fail

These are the Senior Shared-UI engineer's six requirements, restated as things you either observe
or do not. **No criterion may be reported as "mostly", "usually" or "feels fine".** Each is
"observed in ≥9 of 10 attempts" or it fails.

| # | Criterion | PASS means | FAIL means |
|---|---|---|---|
| **G1** | **Long-press registers** | A press-and-hold on a verse row fires `onLongClick` in **≥9 of 10** attempts, at a hold duration a normal user would produce (~0.5 s). | It fires intermittently, needs an unnaturally long hold, or needs an unnaturally still finger. |
| **G2** | **Long-press does not fight the horizontal pager** | Holding still fires `onLongClick`; a horizontal drag from the same start point pages instead and fires **nothing**. Neither ever fires both. Test **mid-screen and within 20pt of the left edge**, where iOS's interactive back-swipe lives. | A long-press cancels because the pager claimed the pointer, or a page change also emits a long-press. |
| **G3** | **Long-press does not fight the vertical list scroll** | A vertical drag scrolls and fires **nothing**; a stationary hold fires `onLongClick` and does **not** scroll. Test both mid-list and while the list is settling from a fling. | A long-press fires during a scroll, or a scroll is swallowed by the press handler. |
| **G4** | **Tap and long-press are distinguishable** | A quick tap fires **only** `onClick`; a hold fires **only** `onLongClick`. **Zero** doubles across ≥20 mixed attempts. | Any attempt produces both, or a tap that lands slightly long produces neither. |
| **G5** | **No iOS system text UI appears** | Long-pressing a verse shows **no** magnifier, **no** grab handles, and **no** system Copy / Look Up / Translate menu. Only the rig's own highlight appears. | Any system text affordance appears — the app's own selection model then competes with the OS's, which is the G8 risk. |
| **G6** | **Multi-select survives a page change and back** | Long-press to select on page 3, tap two more rows to add, swipe to page 4 and back. **Selection behaves deterministically and identically on every repeat** — whether it clears (the D-Q-3 rule) or persists is not what is being tested; *that it is the same every time* is. | Selection is non-deterministic, or state leaks between pages. |

**Also record, as observations rather than pass/fail** (they inform Phase 3, they do not gate it):

- **VoiceOver.** Turn it on. Can you reach a row and activate it? Does it announce anything?
  Long-press is unreliable under VoiceOver **on both platforms** — which is exactly why the real
  app carries a "Select verses" menu item as the equivalent path (that design already exists and
  carries over). You are not proving VoiceOver here; you are finding out whether it is a surprise
  later.
- **Scroll smoothness** with 60 wrapping text items. The real Psalm 119 is 176 verses and has
  **never been profiled on either platform** (ADR-0004, consequence 3).
- **Page-fling feel** across 20 pages versus the Android build in your other hand.

---

## What answer would kill or reshape the port

| Outcome | Consequence |
|---|---|
| **All six PASS** | ADR-0004 is confirmed. The reader stays Compose Multiplatform. **Re-verify on the real reader as a Phase 3 exit criterion** — this rig is weaker evidence than the real screen, and it is the only evidence available this early. |
| **G5 FAILS** (system text UI appears) | **Reshapes, does not kill.** Likely fixable by suppressing the platform text-interaction on the Compose view. Investigate for **up to 2 days**, then escalate with what you found. This is a product question as much as a technical one — sprint 00Q built that gesture to solve a problem the owner personally raised. |
| **G2 or G3 FAILS** (gesture arbitration) | **Serious.** Investigate for up to **one week** (the D-PORT-1 window): custom `pointerInput` arbitration, `awaitPointerEventScope` with explicit consumption, or a nested-scroll adjustment. If no fix inside a week — **STOP THE PROGRAM AND REASSESS.** |
| **G1 or G4 FAILS** and cannot be fixed in a week | **Same stop rule.** A reader where long-press is unreliable is not the app the owner signed off. |
| **G6 FAILS** | Lowest severity — likely a rig bug or a `PagerState`/`key` issue, and this project has already been bitten once by exactly that (the 1.8.1 `PagerTarget` epoch fix). Fix the rig and retest before reporting. |

**The refusal to hold under pressure:** if the gestures fail, the answer is **not** "write the
reader in SwiftUI." ADR-0004 §"Alternatives rejected" is explicit — *"if the reader is native,
there is no meaningful shared core left and the KMP decision collapses."* The honest answers are
(a) fix the arbitration, (b) reshape the interaction on **both** platforms, or (c) stop.

---

## Boundaries / write set

**You may write:**
- A throwaway CMP project outside this repo, or under `spikes/gate0-gesture-rig/` on a branch
  that never merges.
- The `## Result` section appended to **this file**.

**You may NOT write:**
- Anything under `app/`. **The real reader is not touched by this spike, at all.**
- `gradle/libs.versions.toml` or any `build.gradle.kts` in this repo — the rig is a separate
  project. Dependency questions go to Build & Release.
- Any ADR. If the result changes ADR-0004, **Staff amends it.**
- `iosApp/Configuration/**` — it does not exist yet, and when it does it is Build & Release's.

---

## Escalation triggers

- **Xcode is not installed or the machine cannot free the disk for it** → **Owner**, blocking,
  immediately. This blocks all of Phase 2 as well, so it is not a private problem.
- **Free provisioning does not work** → **Owner + Build & Release**, blocking. It means this spike
  waits on the $99 enrolment, which changes the program's critical path.
- **Any of G1–G4 fails** → **Staff**, blocking, **the same day**. Do not spend a week quietly
  fighting it; the one-week investigation window is a decision Staff makes with the failure in
  front of them, not a budget you grant yourself.
- **G5 fails** → **Staff**, non-blocking, within a day.
- The rig starts growing past ~300 lines, or you find yourself porting `ReaderScreen` into it →
  stop and re-read this brief. A rig that is a copy of the app tells you about your copy.

---

## Result

*(To be completed by the assignee.)*

- Device(s) / iOS version(s):
- Build configuration (debug / release):
- Xcode + Compose Multiplatform versions:

| # | Criterion | PASS / FAIL | Attempts | Notes |
|---|---|---|---|---|
| G1 | Long-press registers | | /10 | |
| G2 | vs horizontal pager (mid-screen) | | /10 | |
| G2e | vs horizontal pager (≤20pt from left edge) | | /10 | |
| G3 | vs vertical list scroll | | /10 | |
| G4 | Tap vs long-press distinguishable | | /20 | |
| G5 | No iOS system text UI | | /10 | |
| G6 | Multi-select deterministic across pages | | /10 | |

Observations — VoiceOver:
Observations — scroll smoothness:
Observations — page-fling feel vs Android:

**Verdict: ADR-0004 CONFIRMED / RESHAPE / STOP** —
