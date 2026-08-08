# iOS parity matrix

> **Owner:** Verification Engineer · **Created:** 2026-08-08 · **Status:** live artifact, populated at
> program start · **Reference platform:** Android 1.8.1 / 10801 (production, 100% rollout)
>
> Inputs: [ios-port-approach.md](ios-port-approach.md) (owner-signed) · [adr/](adr/) ·
> [port-inventory.md](port-inventory.md) · [test-port-strategy.md](test-port-strategy.md) ·
> `CLAUDE.md` (the sprint log — the source of most rows below)

**This document is the definition of done for the iOS port.** When it has no `UNVERIFIED` rows and
no `DEFECT` rows, the port is done. Nothing else defines done — not "all tests pass", not "it works
on my simulator", not an engineer's report.

**Its value depends entirely on the honesty of its worst row.** A matrix with 140 `MATCH` rows and
one dishonest `MATCH` is worse than no matrix, because it removes the owner's reason to look.

---

## 1. How to read a row

Every row is a **falsifiable statement about user-facing behavior on Android**. Android is the
oracle: where iOS differs, Android is presumed correct until an ADR says otherwise. "Reader" is not
a row — nobody can falsify it. "Swiping right at Genesis 50 lands on Exodus 1" is a row: it is
either true on the device in your hand or it is not.

| Column | Meaning |
|---|---|
| **ID** | Stable. Never renumber. A deleted row keeps its ID with status `WITHDRAWN` and a reason. |
| **Behavior (Android = the oracle)** | The falsifiable statement. Present tense, singular, observable. |
| **Tier** | The **minimum** tier at which this behavior can honestly be called verified. See §2. |
| **Status** | One of the five values in §3. |
| **Evidence** | A concrete artifact. See §4. `—` means none, which forces `UNVERIFIED`. |
| **Ref** | ADR / decision id / sprint / issue that governs the row. |

---

## 2. The four verification tiers

A tier is a claim about **what the run could have caught**, not about how much work it was.

| Tier | What it is | What it proves | What it cannot prove |
|---|---|---|---|
| **T1** | `commonTest` — runs on JVM, Android and iOS (Kotlin/Native) targets | Shared logic: arithmetic, parsing, date math, state reducers, formatters, invariants over the real assets | Anything about rendering, gestures, OS services, packaging, or the release binary |
| **T2** | Platform unit test — `androidUnitTest` (Robolectric) / `iosTest` (Kotlin/Native, **simulator**) | Platform binding: does storage open, does the bundle resolve a path, does the platform formatter emit what we expect | Real gesture handling, real rendering, real OS delivery, release-build behavior |
| **T3** | `runComposeUiTest` on a simulator | Composition, semantics tree, test tags, synthetic input, state wiring | **Real touch.** `performTouchInput { longClick() }` is synthetic and is not evidence about gesture conflict. Also: real accessibility services, real typography, real timing |
| **T4** | **Release-configuration build on physical hardware, human-observed** | Gestures, rendering, typography, notification delivery, VoiceOver, Dynamic Type, timing, R8/Kotlin-Native DCE, App Group storage under real file protection | Nothing above it. T4 is the ceiling and it is human-limited |

**The tier rule, and it is not negotiable:**

> **No row may be marked green on a tier lower than the behavior requires.**

If a row's Tier says T4, then a green `runComposeUiTest` run does **not** move it off `UNVERIFIED`.
If you believe the tier is wrong, **escalate and change the tier deliberately, in a commit, with a
reason.** Silently satisfying a T4 row with T3 evidence is the single failure mode this document
exists to prevent.

**Why iOS needs this harder than Android did.** You cannot run Kotlin/Native unit tests on a
physical iPhone. Every automated iOS result is **simulator + debug + Apple-Silicon host arch**; the
shipped artifact is **device + release + arm64**. There is no configuration in which the suite runs
against what ships. Android had one axis of divergence (debug vs R8) and it still shipped the 1.7.0
P0 — a crash on *every* reading tap that only R8 exposed. iOS has three axes. T4 is the only tier
that closes them, and it is bought with owner hours, not CI minutes.

---

## 3. Status vocabulary — exactly five values

| Status | Meaning | Mandatory alongside |
|---|---|---|
| **MATCH** | Observed on iOS **at the row's required tier** and it matches Android. | Evidence |
| **DIVERGENT** | Differs from Android **deliberately**, and the divergence itself has been observed at the required tier. | An ADR or a recorded owner decision **and** evidence |
| **DEFECT** | Differs from Android and the difference is not intended. | An issue id. A `DEFECT` row blocks release unless explicitly waived by the owner in writing |
| **UNVERIFIED** | Nobody has observed this at the required tier. **This is the default and the honest starting state.** | A reason **and** the required tier |
| **N/A-IOS** | The behavior does not exist on iOS by a signed scope decision. | The scope reference **and** a paired absence row wherever the absence is user-visible |

`UNVERIFIED` is first-class. An honest `UNVERIFIED` is useful — it tells the owner exactly where to
look. A false `MATCH` is worse than no row at all.

**A scope decision is evidence for `N/A-IOS`. It is never evidence for `MATCH` or `DIVERGENT`.**
"We decided the reminder body would be generic" makes the reminder row `N/A`-adjacent in *intent*;
it does not tell you the shipped app actually sends a generic body. Until someone watches a
notification arrive on a phone, that row is `UNVERIFIED` with the intended terminal status noted.
That is why several rows below read `UNVERIFIED (intended: DIVERGENT)`.

---

## 4. Rules for changing a row

1. **Only the Verification Engineer moves a status away from `UNVERIFIED`.** Any agent or human may
   move a status *to* `UNVERIFIED` or `DEFECT` at any time, with no ceremony. Downgrades are free;
   upgrades are gated. This asymmetry is deliberate.

2. **Evidence must be a concrete artifact.** One of:
   - a fully-qualified test id — `c.j.d.bible.domain.PortionVerseBridgeTest#windowedRefYieldsExactRange`
     — plus the source set it runs in (`common` / `android` / `ios` / `jvm`);
   - a CI run URL and job name;
   - a device-pass session entry: **date · device model · iOS version · build configuration
     (Debug/Release) · who observed it**. Anything less is not a device pass.

3. **These are not evidence:**
   - "the shared code is the same, so it must work" — shared code is an argument for *likelihood*,
     never a substitute for a T3 or T4 observation;
   - "it compiled";
   - "the Android test passes";
   - "an agent reported it done";
   - a green `AccessibilityGateTest` for any accessibility row (see §5.11 — that gate proves
     strictly less on iOS than it does on Android, and it will still go green).

4. **You may not resolve an `UNVERIFIED` row by narrowing it.** If the row says "long-press enters
   selection mode on physical hardware" you do not get to rewrite it as "the selection reducer
   enters selection mode" and mark it `MATCH` at T1. Rewording a row to fit the evidence you have
   is the most common way a parity matrix becomes decorative. If a row is genuinely mis-specified,
   **escalate**; the change goes in as a commit with a reason and the old wording preserved.

5. **A `DEFECT` row is closed by fixing the defect, never by deleting the row.**

6. **Ambiguity between `DEFECT` and "unrecorded intentional divergence" is an escalation**, not a
   judgement made quietly in this file. If nobody wrote the ADR, it is not a divergence yet.

7. **Release gate.** No iOS release ships while:
   - any row is `DEFECT` without a written owner waiver; **or**
   - any row whose feature is *shipping* is `UNVERIFIED` at a required tier of T4.
   `UNVERIFIED` rows at T1–T3 do not block, but they are reported to the owner by count and by ID at
   every release, never summarised as "some tests pending".

8. **Reporting rule (D-PORT-6).** Gate counts are always reported as
   `common: 35 · jvm: 18 · android: 5 · ios: 10 (both new)`. Never "all gates run everywhere."

---

## 5. The matrix

**Signature state (2026-08-08): 187 rows — `UNVERIFIED` 179 · `N/A-IOS` 8 · `MATCH` 0 ·
`DIVERGENT` 0 · `DEFECT` 0.**

Zero `MATCH` rows on day one is correct, not pessimistic. Nothing has been observed on iOS because
no iOS binary exists.

`(int: X)` in Notes means **intended terminal status**, recorded so the row's eventual resolution is
predicted in advance and a surprise is visible as a surprise.

---

### 5.1 Data gates and asset packaging

The project's core IP. Six gates today; two new iOS gates required.

| ID | Behavior (Android = the oracle) | Tier | Status | Evidence | Ref |
|---|---|---|---|---|---|
| GATE-01 | `ReadingPlanVerificationTest` (Bible Companion) — 11 assertions incl. day-by-day equality vs the independent second source — passes against the committed asset | T1 | UNVERIFIED | — | ADR-0010 T1 |
| GATE-02 | `McheynePlanVerificationTest` — 10 assertions incl. the verse-aware coverage invariant and Ps-119 tiling — passes | T1 | UNVERIFIED | — | ADR-0010 T1 |
| GATE-03 | `ChronologicalPlanVerificationTest` — 8 assertions incl. the exactly-once 1,189-chapter coverage invariant — passes | T1 | UNVERIFIED | — | ADR-0010 T1, D-ALT-24 |
| GATE-04 | `PlanSegmentGateTest` — 6 assertions, 0 segmentation violations across all 2,920 portions in all three plans | T1 | UNVERIFIED | — | ADR-0010 T1, D-SEG-2 |
| GATE-05 | `BibleTextVerificationTest` — 18 assertions over the committed `bible.db` — passes, and **the iOS release pipeline depends on the task and reports it explicitly** | T2 (JVM) | UNVERIFIED | — | ADR-0010 T2 |
| GATE-06 | `BibleDatabaseRoomOpenTest` — 5 assertions — still passes on Android after the port | T2 (android) | UNVERIFIED | — | ADR-0010 T3, sprint-00F |
| GATE-07 | **NEW iOS** `BibleDatabaseOpenTest` reads Gen 1:1, John 3:16, John 11:35 ("Jesus wept.") and the Ps 3 verse-0 superscription from the bundled DB through the shipping open path | T2 (ios) | UNVERIFIED | — | ADR-0010 T4 |
| GATE-08 | GATE-07 **fails** against a deliberately corrupted/mis-schema'd bundled DB — proven, not assumed | T2 (ios) | UNVERIFIED | — | sprint-00F precedent |
| GATE-09 | **NEW iOS** `BundleAssetIntegrityTest` resolves all five assets from `NSBundle` at their **nested** paths and matches SHA-256 against generated constants | T2 (ios) | UNVERIFIED | — | ADR-0011, R10 |
| GATE-10 | `plans/mcheyne/plan.json` resolves at its nested path and is **not** flattened to `plan.json` colliding with the other two plans | T2 (ios) | UNVERIFIED | — | ADR-0011 (folder ref vs group) |
| GATE-11 | Exactly one copy of each asset exists in git — `find . -name bible.db -not -path './*/build/*'` returns one path | T1 | UNVERIFIED | — | ADR-0011 |
| GATE-12 | The release report states the honest gate ledger, never "all gates run everywhere" | T2 | UNVERIFIED | — | D-PORT-6 |
| GATE-13 | The `bible.db` bytes inside the iOS bundle SHA-256-match the committed asset (`ad46a777…9099`) | T2 (ios) | UNVERIFIED | — | ADR-0011 |
| GATE-14 | Every one of the six gates has had its recorded mutation re-killed **after** the assertion-library conversion | T1/T2 | UNVERIFIED | — | R4, test-port-strategy §6 |
| GATE-15 | The three CI asset byte-diff reproduction jobs still reproduce zero after the asset move | T2 (JVM/Linux) | UNVERIFIED | — | ADR-0011 |

### 5.2 Plan data, plan switching

| ID | Behavior (Android = the oracle) | Tier | Status | Evidence | Ref |
|---|---|---|---|---|---|
| PLAN-01 | Jan 1 on Bible Companion shows Genesis 1–2 / Psalms 1–2 / Matthew 1–2 | T1 | UNVERIFIED | — | plan gate |
| PLAN-02 | Feb 29 in a leap year shows "No scheduled readings for Feb 29th" — no readings, no marks | T1 | UNVERIFIED | — | D1 |
| PLAN-03 | Swiping from Dec 31 lands on Jan 1 of the next year; progress keys to the full date | T1 | UNVERIFIED | — | D-S5-3 |
| PLAN-04 | Switching plan is non-destructive: switch away and back and every mark is still there | T1 | UNVERIFIED | — | D-ALT-19, Sprint alt-B |
| PLAN-05 | Selecting the already-active plan is a no-op; selecting a different one raises the switch dialog and writes nothing until confirm | T3 | UNVERIFIED | — | D-ALT-19 |
| PLAN-06 | Chronological (N=1) renders the reference with **no** stream label | T1 | UNVERIFIED | — | D-ALT-22/23 |
| PLAN-07 | M'Cheyne (N=4) renders four reading rows with the "Family —" / "Personal —" titles | T1 | UNVERIFIED | — | D-ALT-22 |
| PLAN-08 | An unknown persisted plan id degrades to `bible_companion` rather than failing | T1 | UNVERIFIED | — | `ActivePlanRepository` |
| PLAN-09 | The selected plan survives app kill and relaunch | T2 (ios) | UNVERIFIED | — | DataStore path, ADR-0008 |
| PLAN-10 | Mar 9–12 stream 2 render "Psalm 119:1–40" … "129–176" (singular Psalm, en dash) | T1 | UNVERIFIED | — | Sprint J, D-UI-2 |
| PLAN-11 | Jun 19 / Dec 19 stream 3 renders "2 John 1; 3 John 1" as one portion | T1 | UNVERIFIED | — | Sprint 1 |
| PLAN-12 | Only the **active** plan's asset is parsed; a Bible-Companion user never parses M'Cheyne | T2 (ios) | UNVERIFIED | — | Sprint alt-E |

### 5.3 Day screen — cards, segments, marking

| ID | Behavior (Android = the oracle) | Tier | Status | Evidence | Ref |
|---|---|---|---|---|---|
| DAY-01 | A Bible Companion day renders exactly three reading cards | T3 | UNVERIFIED | — | — |
| DAY-02 | Chronological 07/25 (`Isaiah 37, 38, 39, Psalms 76`) renders **two** cards — a book change splits | T1 | UNVERIFIED | — | D-SEG-1 |
| DAY-03 | M'Cheyne 02/28 (`Exodus 11` + `Exodus 12:1–21`) renders **one** card — a verse window does not split | T1 | UNVERIFIED | — | D-SEG-1 |
| DAY-04 | Segment boundaries are produced by `ConsecutiveChapterRuns`, not a second grouper — card boundaries and external-URL grouping cannot drift | T1 | UNVERIFIED | — | D-SEG-1, one-home rule |
| DAY-05 | Ticking a reading's checkbox marks it read and the mark survives relaunch | T2 (ios) | UNVERIFIED | — | — |
| DAY-06 | Tapping a card body opens the reading **and** marks it read | T3 | UNVERIFIED | — | Sprint 00O, D-O-1 |
| DAY-07 | Tapping an already-read reading never un-marks it (one-way SET) | T1 | UNVERIFIED | — | D-O-1 |
| DAY-08 | Tapping the last needed segment of a multi-segment reading clears the partial tokens and writes the real mark | T1 | UNVERIFIED | — | D-SEG-4, `SegmentCheckPolicy` |
| DAY-09 | Tapping a COMPLETE multi-segment reading clears the mark, sets the tapped segment UNCHECKED and **all others PARTIAL** | T1 | UNVERIFIED | — | `SegmentCheckPolicy` |
| DAY-10 | A single-segment reading behaves exactly as before — N==1 falls out of the general rules, not a special case | T1 | UNVERIFIED | — | D-SEG-4 |
| DAY-11 | Partial-check tokens survive app kill / force-quit | T4 | UNVERIFIED | — | D-SEG-4 |
| DAY-12 | Partial tokens older than 400 days are pruned on write | T1 | UNVERIFIED | — | D-SEG-5 |
| DAY-13 | **A partial tick is visually distinguishable from a complete tick** in light and dark, including under the platform's tinting | T4 | UNVERIFIED | — | sprint-00P known gap — **colour is not JVM-provable** |
| DAY-14 | The partial/complete distinction is carried by `stateDescription`, not colour alone | T3 | UNVERIFIED | — | sprint-00P |
| DAY-15 | Today's title reads "Today – June 10"; another day reads "Friday, June 13"; the year appears only when it differs from today's | T2 (ios) | UNVERIFIED | — | D-S16-1 — **locale formatting, platform-side** |
| DAY-16 | No "n of 3 readings done" progress line appears at any state | T3 | UNVERIFIED | — | D-S16-2 |
| DAY-17 | No whole-day button and no "All readings done" badge appear at any state | T3 | UNVERIFIED | — | Sprint 00H |
| DAY-18 | One list-level caption below the readings reflects the selected destination ("Tap a reading to open it in this app" / "…on Blue Letter Bible") and updates live when Settings changes | T3 | UNVERIFIED | — | 2026-06-16 one-screen-fit |
| DAY-19 | The day pager steps real calendar days across a ±10,000-day window with today at the centre | T3 | UNVERIFIED | — | Sprint 5 |
| DAY-20 | **The whole main screen fits one screen at default text size**, with the bottom nav bar present | T4 | UNVERIFIED | — | R8 — Android budgets tuned on a Pixel 7 Pro are **invalid** on iOS |
| DAY-21 | The 4-stream M'Cheyne day fits one screen at default text size | T4 | UNVERIFIED | — | R8 |
| DAY-22 | The 6-card Chronological 04/22 day is usable (scrolling accepted) with 48pt targets intact | T4 | UNVERIFIED | — | sprint-00P device-pass item |
| DAY-23 | The tracking-start first-run prompt appears on a fresh install only, never for an upgrader with marks | T3 | UNVERIFIED | — | D-S19-1 |
| DAY-24 | Dismissing the tracking-start prompt applies the Jan-1 fallback silently and never re-shows | T1 | UNVERIFIED | — | D-S19-1 |
| DAY-25 | The reading-destination first-run question shows for fresh installs; the one-time upgrade note shows instead when marks exist | T1 | UNVERIFIED | — | D-V3-19, D-D-4 |

### 5.4 Date picker

| ID | Behavior (Android = the oracle) | Tier | Status | Evidence | Ref |
|---|---|---|---|---|---|
| PICK-01 | Tapping a day cell selects that full date and closes the dialog in one tap | T3 | UNVERIFIED | — | Sprint 21 |
| PICK-02 | Month swipe crosses year boundaries freely — Dec 2026 → Jan 2027 and back | T3 | UNVERIFIED | — | D-S21-1 |
| PICK-03 | A day with all streams read shows a green dot (past, today or future) | T1 | UNVERIFIED | — | `DayCompletionClassifier` |
| PICK-04 | A past, post-tracking-start, incomplete day shows a red dot | T1 | UNVERIFIED | — | `DayCompletionClassifier` |
| PICK-05 | A day strictly before the tracking start date is never red | T1 | UNVERIFIED | — | R-STREAK-5 |
| PICK-06 | Feb 29 is selectable and carries no dot | T1 | UNVERIFIED | — | D-S8-2 |
| PICK-07 | Every day cell carries a spoken date + state — never colour alone | T3 | UNVERIFIED | — | D-S8-2 |
| PICK-08 | Every dot state flows through `DayCompletionClassifier`, never re-derived locally | T1 | UNVERIFIED | — | R-STREAK-5, D-S11-1 |
| PICK-09 | **Picking a chapter never snaps the reader to Genesis 1** — the 1.8.1 P1 | T4 | UNVERIFIED | — | 1.8.1 — reproduced only on an R8 release build |
| PICK-10 | Changing Bible version after a pick holds the reader's position | T4 | UNVERIFIED | — | 1.8.1 `PagerTarget` epoch |

### 5.5 Reader — navigation and rendering

| ID | Behavior (Android = the oracle) | Tier | Status | Evidence | Ref |
|---|---|---|---|---|---|
| READ-01 | **Every book and chapter opens and renders text** — no "couldn't load this chapter" | T4 | UNVERIFIED | — | sprint-00F P0 |
| READ-02 | Swiping right at Genesis 50 lands on Exodus 1 | T4 | UNVERIFIED | — | D-H-2 |
| READ-03 | Swiping left at Genesis 1 does nothing (bounded) | T4 | UNVERIFIED | — | D-H-2 |
| READ-04 | Swiping right at Revelation 22 does nothing (bounded) | T4 | UNVERIFIED | — | D-H-2 |
| READ-05 | `GlobalChapterIndex` adjacency equals `ChapterNavigator` at every one of the 1,189 indices | T1 | UNVERIFIED | — | D-H-1 |
| READ-06 | Verse numbers come from the row's `native_label`, never derived from the id | T1 | UNVERIFIED | — | D-V3-4 |
| READ-07 | The Psalm 3 superscription renders as an unnumbered italic heading, not a numbered verse | T3 | UNVERIFIED | — | D-V3-7 |
| READ-08 | `<a>` added-word markup renders italic; `<l/>` renders a line break; no raw tags reach the screen | T3 | UNVERIFIED | — | `VerseRenderer` |
| READ-09 | A single Psalms chapter titles "Psalm 23"; a multi-chapter run titles "Psalms 1–2" | T1 | UNVERIFIED | — | D-UI-2, sprint-00L |
| READ-10 | The singular/plural rule delegates to `ReadingFormatter.singularizeBookName` — one home, reader and Schedule cannot drift | T1 | UNVERIFIED | — | sprint-00L |
| READ-11 | Tapping a Schedule reading opens **that reading**, not Genesis 1 — including the 83 affected Chronological days | T4 | UNVERIFIED | — | sprint-00P P0, D-SEG-6 |
| READ-12 | A multi-chapter reading opens as ONE combined page | T3 | UNVERIFIED | — | D-I-1 |
| READ-13 | Swiping off a combined portion page and back returns the **same combined page**, never the portion's last chapter alone | T4 | UNVERIFIED | — | D-I-1 (the owner's core ask) |
| READ-14 | Tapping the Bible tab always resets to single-chapter Browse at the last-read chapter | T3 | UNVERIFIED | — | D-I-2 |
| READ-15 | The last-read chapter is restored within a session | T3 | UNVERIFIED | — | D-V3-13 |
| READ-16 | The reader footer hint reflects the selected external app and updates live | T3 | UNVERIFIED | — | D-K-HINT-1 |
| READ-17 | The footer hint is skipped by the screen reader | T3 | UNVERIFIED | — | D-K-HINT-3 |
| READ-18 | With one bundled version the top bar shows a static "KJV" title, not a control | T3 | UNVERIFIED | — | D-N-3 |
| READ-19 | The pencil opens the book/chapter picker; all 66 books fit one screen | T4 | UNVERIFIED | — | D-N-4, sprint-00G |
| READ-20 | Picker cells show abbreviations but speak full book names ("Genesis", "Genesis chapter 3") | T3 | UNVERIFIED | — | sprint-00G |
| READ-21 | **Reading feel and typography on glass** — line length, leading, markup weight | T4 | UNVERIFIED | — | G6, M-V3-2 precedent |
| READ-22 | Psalm 119 (176 verses) scrolls without stutter and opens without a perceptible delay | T4 | UNVERIFIED | — | sprint-00Q known gap — no profile exists |
| READ-23 | Chapter swipe across a book boundary feels continuous, not janky | T4 | UNVERIFIED | — | R5 |

### 5.6 Reader — verse selection, menu, clipboard

| ID | Behavior (Android = the oracle) | Tier | Status | Evidence | Ref |
|---|---|---|---|---|---|
| SEL-01 | **Long-press on a verse enters selection mode** — inside a `LazyColumn` inside a `HorizontalPager`, on physical hardware | T4 | UNVERIFIED | — | **R5 — the port's highest technical risk.** Synthetic `longClick()` is explicitly not evidence |
| SEL-02 | In selection mode, tapping a verse adds or removes it | T3 | UNVERIFIED | — | Sprint 00Q |
| SEL-03 | The X in the contextual bar exits selection | T3 | UNVERIFIED | — | Sprint 00Q |
| SEL-04 | Deselecting the last verse exits selection | T1 | UNVERIFIED | — | Sprint 00Q |
| SEL-05 | Copy exits selection, and the copy is taken from the selection captured **before** the clear | T1 | UNVERIFIED | — | P-Q-1 (owner, 2026-07-25) |
| SEL-06 | Hardware back exits selection | — | **N/A-IOS** | ios-port-approach §3 N6, owner-signed 2026-08-08 | iOS has no back button |
| SEL-06a | **No invented substitute gesture** for the missing back exit (no swipe-to-clear that Android lacks) | T3 | UNVERIFIED | — | §3 N6 |
| SEL-07 | Changing page clears the selection; in a Reading context the "page" is the whole combined portion | T1 | UNVERIFIED | — | D-Q-3 |
| SEL-08 | The clipboard payload is text first, citation last: `… — Genesis 1:1–2 (KJV)` | T1 | UNVERIFIED | — | D-Q-1 |
| SEL-09 | Contiguous verses collapse to a range; non-contiguous join with commas; cross-chapter groups by chapter; cross-book by book | T1 | UNVERIFIED | — | `VerseClipboardFormatter` |
| SEL-10 | Psalm 23 cites **singular** in the clipboard, via `singularizeBookName` — never re-derived | T1 | UNVERIFIED | — | D-UI-2 |
| SEL-11 | A superscription contributes its text but no verse number to the citation | T1 | UNVERIFIED | — | Sprint 00Q |
| SEL-12 | **The copied text actually pastes correctly into another app** | T4 | UNVERIFIED | — | sprint-00Q known gap — only the pure formatter is pinned |
| SEL-13 | A copy confirmation is shown | T4 | UNVERIFIED (int: DIVERGENT) | — | **G3** — Android ≥33 defers to the OS toast; iOS has none, so the app must always show its own |
| SEL-14 | A short tap opens a three-item verse menu: Open in `<app>` / Copy this verse / Select verses | T3 | UNVERIFIED | — | Sprint 00Q |
| SEL-15 | Every menu item dismisses the menu before acting | T3 | UNVERIFIED | — | sprint-00Q mutation finding |
| SEL-16 | "Select verses" is the screen-reader-reachable equivalent of long-press | T4 | UNVERIFIED | — | Sprint 00Q a11y build requirement |
| SEL-17 | **Long-press does not trigger the iOS system text magnifier / grab handles / system Copy** | T4 | UNVERIFIED | — | **G8 — a product question as much as a technical one** |
| SEL-18 | The verse spoken label does **not** begin "Open …" — the affordance lives in `onClickLabel` | T3 | UNVERIFIED | — | Sprint 00Q rewording |

### 5.7 External Bible destinations

| ID | Behavior (Android = the oracle) | Tier | Status | Evidence | Ref |
|---|---|---|---|---|---|
| LINK-01 | Blue Letter Bible chapter URLs match the live-verified shape (`/kjv/gen/1/`) | T1 | UNVERIFIED | — | Sprint 1 / 13 |
| LINK-02 | Bible Gateway carries the whole portion in one URL ("Genesis 1-2"; "2 John 1,3 John 1") | T1 | UNVERIFIED | — | D-S13-3 |
| LINK-03 | YouVersion uses the USFM form (`GEN.1.1.KJV`) | T1 | UNVERIFIED | — | D-S13-2 |
| LINK-04 | Per-verse URLs are correct for all four shapes, including Ps 119:176, Philemon, 2/3 John | T1 | UNVERIFIED | — | D-H-5 |
| LINK-05 | **Generated URLs are byte-identical to Android's**, including encoding (`%20` vs `+`) | T1 | UNVERIFIED | — | D-PORT-7 1.9.0 gate — *any* diff is a port bug |
| LINK-06 | The verse coordinate is the canonical decode of `canonicalId`, not the display label; verse 0 clamps to 1 | T1 | UNVERIFIED | — | D-H-3 |
| LINK-07 | Tapping a reading opens the destination in an in-app browser | T4 | UNVERIFIED | — | `SFSafariViewController` |
| LINK-08 | MySword opens via an explicit component intent | — | **N/A-IOS** | §3 N4, owner-signed 2026-08-08 | No iOS counterpart (V8) |
| LINK-09 | **MySword does not appear as a selectable option in iOS Settings**, and a persisted `MYSWORD` id degrades to BLB without rewriting the stored choice | T3 | UNVERIFIED | — | §3 N4 — the paired absence row for LINK-08 |

### 5.8 Stats and year strips

| ID | Behavior (Android = the oracle) | Tier | Status | Evidence | Ref |
|---|---|---|---|---|---|
| STAT-01 | Current streak: COMPLETE extends, MISSED resets, NONE is neutral; today is in grace | T1 | UNVERIFIED | — | D-S11-2 |
| STAT-02 | Longest streak is all-time and crosses year boundaries | T1 | UNVERIFIED | — | R-STREAK-4 |
| STAT-03 | Year progress uses **floor** rounding — 100% only at actual completion | T1 | UNVERIFIED | — | D-S11-4 |
| STAT-04 | Denominators come from the active plan descriptor (`dayCount × N`, `dayCount`) — 1,095/365 for BC, 365/365 for Chronological | T1 | UNVERIFIED | — | D-ALT-7/8 |
| STAT-05 | Year strips draw one segment per calendar day, 366 in a leap year, Feb 29 neutral | T1 | UNVERIFIED | — | D-S17-4 |
| STAT-06 | Consecutive same-state days coalesce into single rects; the first starts at 0 and the last ends at the full width | T1 | UNVERIFIED | — | D-S18-3 |
| STAT-07 | Per-(day,stream) strip state goes **through** `DayCompletionClassifier`, never re-derived | T1 | UNVERIFIED | — | D-S17-2 |
| STAT-08 | A today tick is drawn on every strip | T3 | UNVERIFIED | — | D-S17-1 |
| STAT-09 | Strips speak "not read", never "missed" — including in contentDescriptions | T3 | UNVERIFIED | — | D-S17-3 |
| STAT-10 | The **only** guilt copy anywhere is the two literal legend labels "Missed" / "Completed" | T3 | UNVERIFIED | — | D-S20-1 |
| STAT-11 | Legend swatches and strips draw from the same `StripColors` seam and cannot disagree | T1 | UNVERIFIED | — | D-S20-1 |
| STAT-12 | Streaks are hidden by default (`show_streaks` absent-key default false); a stored `true` survives | T1 | UNVERIFIED | — | D-S18-1 |
| STAT-13 | The stats panel fits without scrolling at default text size with streaks off | T4 | UNVERIFIED | — | R8 — Android budget invalid on iOS |
| STAT-14 | Strip texture is legible on glass in light and dark (~1dp segments, tick visibility) | T4 | UNVERIFIED | — | S17 device-pass item |

### 5.9 Settings

| ID | Behavior (Android = the oracle) | Tier | Status | Evidence | Ref |
|---|---|---|---|---|---|
| SET-01 | Theme Light/Dark/System persists and applies live app-wide | T3 | UNVERIFIED | — | Sprint 6 |
| SET-02 | Material You dynamic colour on API 31+ | — | **N/A-IOS** | §3 N5, owner-signed 2026-08-08 | No iOS system palette |
| SET-03 | The 0.85×–1.5× text-size slider applies live app-wide in 0.05 steps | T3 | UNVERIFIED | — | D-S8-5 |
| SET-04 | **The app slider composed with the platform's own scaling stays usable at the platform maximum** | T4 | UNVERIFIED (int: DIVERGENT) | — | **G4 — iOS AX5 ≈ 310%; composed ≈ 4.6×. Android budgets do not hold** |
| SET-05 | Tracking start date can be set from a full-calendar picker and cleared; a cleared date is never re-defaulted | T3 | UNVERIFIED | — | D-S10-1, D-S14-1 |
| SET-06 | Reset progress clears the current year only, for the active plan only, behind a confirm | T1 | UNVERIFIED | — | Sprint 8, alt-B |
| SET-07 | The plan selector shows the active plan's own descriptor name, never a second name table | T3 | UNVERIFIED | — | D-ALT-18 |
| SET-08 | Reading-destination mode (in-app vs external) persists and is read at tap time | T1 | UNVERIFIED | — | D-S13-4 |
| SET-09 | The external-app dropdown is visible and active in **both** destination modes, with its context caption | T3 | UNVERIFIED | — | 2026-06-17 |
| SET-10 | **Every setting survives app kill, relaunch and app update** — theme, font scale, tracking start, plan, destination, reminder | T4 | UNVERIFIED | — | **R2 — a DataStore path error is silent** |
| SET-11 | An in-app update prompt appears for MINOR/MAJOR bumps and stays silent for PATCH | — | **N/A-IOS** | §3 N3, owner-signed 2026-08-08 | The App Store owns updates |
| SET-12 | No orphaned in-app-update UI is reachable on iOS | T3 | UNVERIFIED | — | paired absence row for SET-11 |

### 5.10 Reminders and glanceable surfaces

| ID | Behavior (Android = the oracle) | Tier | Status | Evidence | Ref |
|---|---|---|---|---|---|
| REM-01 | The daily reminder is **off by default** | T1 | UNVERIFIED | — | R-REM-1 |
| REM-02 | With the reminder on, one notification arrives at the chosen local time | T4 | UNVERIFIED | — | requires an overnight device session |
| REM-03 | The notification body lists the day's collapsed references ("Genesis 1–2 · Psalms 1–2 · Matthew 1–2") | T4 | UNVERIFIED (int: **DIVERGENT**) | — | **§4.1 / owner decision (a): iOS uses a generic body** |
| REM-04 | The notification is suppressed when the day is already complete | T4 | UNVERIFIED (int: **DIVERGENT**) | — | **§4.1 (a): iOS fires on completed days.** Record as a deliberate v1.0 trade, **not** as "not achievable" — §4.1 adjudication |
| REM-05 | The notification is suppressed on Feb 29 | T4 | UNVERIFIED (int: **DIVERGENT**) | — | §4.1 (a): a generic repeating trigger sidesteps Feb 29 by having nothing to say |
| REM-06 | The standing reminder survives a device reboot | T4 | UNVERIFIED | — | §3 N7 — iOS repeating triggers survive reboot with no `BOOT_COMPLETED` equivalent |
| REM-07 | Turning the reminder off cancels it; a stale fire is a no-op | T1 | UNVERIFIED | — | Sprint 12 |
| REM-08 | Tapping the notification opens the app on today | T4 | UNVERIFIED | — | Sprint 12 |
| REM-09 | Enabling the reminder requests notification permission; denial keeps the setting off with an explanation and a settings path | T4 | UNVERIFIED (int: DIVERGENT) | — | iOS uses **provisional** authorization — quiet delivery, no prompt. **A strictly better UX, and still a divergence** |
| REM-10 | An always-present, non-dismissible tray notification shows the day's readings and refreshes at 01:00 | — | **N/A-IOS** | §3 N2, owner-signed 2026-08-08 | No iOS mechanism exists; a Live Activity substitute is explicitly refused (§9.4) |
| REM-11 | No Live Activity or other faked substitute for REM-10 exists in the iOS build | T4 | UNVERIFIED | — | paired absence row for REM-10 |
| REM-12 | A resizable home-screen widget shows the day's readings at five size tiers | — | **N/A-IOS** | §3 N1 / D-PORT-3, owner-signed 2026-08-08 | WidgetKit is iOS 1.1 |
| REM-13 | The widget refreshes after a mark and at midnight rollover | — | **N/A-IOS** | §3 N1 | Follows REM-12 |
| REM-14 | **iOS v1.0 has no glanceable surface at all** — the compound of REM-10 and REM-12 — and the owner has acknowledged it | T4 | UNVERIFIED (int: **DIVERGENT**) | — | §4.2 — recorded as its own row so it cannot be lost between two `N/A` rows |
| REM-15 | The reminder's pending state survives process death and is re-armed at launch | T4 | UNVERIFIED | — | Sprint 12 |

### 5.11 Accessibility

**Standing caveat, and it is written into this section deliberately:** `AccessibilityGateTest` will
go green on iOS and will prove **strictly less** there than on Android. It pins the *input* to
Compose Multiplatform's UIAccessibility bridge, and that bridge is not at SwiftUI parity. **A green
gate is never evidence for any row in this section.** Every A11Y row below that matters requires T4
with a human driving VoiceOver.

| ID | Behavior (Android = the oracle) | Tier | Status | Evidence | Ref |
|---|---|---|---|---|---|
| A11Y-01 | Every authored control has ≥48dp touch bounds (Apple's HIG minimum is 44pt — we keep 48 as **deliberately stricter**) | T3 | UNVERIFIED | — | `AccessibilityGateTest`, §9.15 |
| A11Y-02 | Every verse is reachable and readable by the screen reader, speaking stripped text, never raw markup | T4 | UNVERIFIED | — | NFR-V3-C |
| A11Y-03 | Verses are announced with a role appropriate to scripture | T4 | UNVERIFIED (int: **DIVERGENT or DEFECT — undecided**) | — | **G7 — `Role.Button` means VoiceOver says "button" after every verse. Needs an owner/accessibility decision, not a silent ship** |
| A11Y-04 | Date-picker day cells speak the full date and their completion state | T3 | UNVERIFIED | — | D-S8-2 |
| A11Y-05 | Year strips speak a summary; the legend is one merged row spoken after the strips; swatches announce nothing | T3 | UNVERIFIED | — | D-S17-3, D-S20-1 |
| A11Y-06 | Dropdown rows speak "label, value" with a list role | T3 | UNVERIFIED | — | S14 idiom |
| A11Y-07 | Disabled options report as disabled and never report a selection | T3 | UNVERIFIED | — | S14/S15 |
| A11Y-08 | **A real VoiceOver traversal of the day screen is coherent** — order, no traps, no unlabelled nodes | T4 | UNVERIFIED | — | 3–4h first pass; see test-port-strategy §8 |
| A11Y-09 | **A real VoiceOver traversal of the reader, the verse menu and selection mode is coherent** | T4 | UNVERIFIED | — | includes SEL-16 |
| A11Y-10 | The app is legible and operable at the platform's maximum Dynamic Type | T4 | UNVERIFIED | — | G4 / R8 |

### 5.12 Navigation, offline behavior, platform integration

| ID | Behavior (Android = the oracle) | Tier | Status | Evidence | Ref |
|---|---|---|---|---|---|
| NAV-01 | Schedule and Bible are co-equal tabs; Schedule is the start destination; every screen is reachable | T3 | UNVERIFIED | — | D-V3-16 |
| NAV-02 | Each tab's back-stack is preserved across a tab switch | T3 | UNVERIFIED | — | D-D-3 |
| NAV-03 | Navigation uses platform-conventional transitions and interactive swipe-back | T4 | UNVERIFIED (int: **DIVERGENT**) | — | **G5 — shared Compose navigation gives iOS Android-shaped navigation. ADR-0003 calls this "the largest 'feels ported' surface in the app"** |
| NAV-04 | Screen titles use the platform's large-title convention | T4 | UNVERIFIED (int: DIVERGENT) | — | G5 |
| OFF-01 | **Planner, progress and the bundled KJV work fully offline** (airplane mode, cold start) | T4 | UNVERIFIED | — | S11 |
| OFF-02 | Selecting NKJV or NASB fetches and renders the online text | T4 | UNVERIFIED | — | Sprint 00R |
| OFF-03 | With no network, the banner "Unable to download NKJV, displaying KJV" appears above KJV text | T4 | UNVERIFIED | — | D-OT-2 |
| OFF-04 | The publisher copyright line is shown for licensed translations | T3 | UNVERIFIED | — | API.Bible obligation |
| OFF-05 | FUMS usage reporting fires for licensed translations | T2 | UNVERIFIED | — | API.Bible obligation |
| OFF-06 | The 14-day cache freshness rule holds | T1 | UNVERIFIED | — | Sprint 00R |
| OFF-07 | All network traffic is HTTPS with **no ATS exception declared** | T2 | UNVERIFIED | — | §5 Phase 4 |
| SYS-01 | Progress, settings and the copied `bible.db` live in the App Group container from the first shipped build | T2 (ios) | UNVERIFIED | — | D-PORT-4 |
| SYS-02 | The settings store resolves at its pinned path; **no setting silently resets** across an app update | T4 | UNVERIFIED | — | **R2 — silent failure mode** |
| SYS-03 | The bundled `bible.db` is copied out of the bundle once and re-used | T2 (ios) | UNVERIFIED | — | ADR-0007 |
| SYS-04 | Bumping the asset content version re-copies the corrected DB | T4 | UNVERIFIED | — | D-V3-8 |
| SYS-05 | Reading history survives a build-to-build app update | T4 | UNVERIFIED | — | R2 |
| SYS-06 | Cold start is not perceptibly slower than Android's | T4 | UNVERIFIED | — | — |
| SYS-07 | No `UIBackgroundModes` are declared | T2 | UNVERIFIED | — | §9.6 |
| SYS-08 | `ITSAppUsesNonExemptEncryption = false` is set | T2 | UNVERIFIED | — | §5 Phase 4 |
| SYS-09 | **No symbol reachable only through the Obj-C bridge is stripped by release DCE** | T4 | UNVERIFIED | — | `ios-release-smoke` part 2 — failure mode is a nil, **not a stack trace** |
| SYS-10 | **No ViewModel that collects in `init` crashes when upstream state is populated before construction** | T4 | UNVERIFIED | — | **the 1.7.0 P0 class — reproduced only under R8** |
| SYS-11 | The app is available in languages other than English | — | **N/A-IOS** | §3 N8, owner-signed 2026-08-08 | Never localized on Android either — unchanged, not a regression |
| SYS-12 | The `.ipa` stays under an iOS-specific size ceiling | T2 | UNVERIFIED | — | A13 — **the Android 12 MB number does not transfer; set it from the first real archive** |

---

## 6. Signature-state summary

| Status | Rows |
|---|---|
| MATCH | **0** |
| DIVERGENT | **0** |
| DEFECT | **0** |
| UNVERIFIED | **179** — of which **10** carry a recorded *intended* terminal status of `DIVERGENT` |
| N/A-IOS | **8** |
| **Total** | **187** |

Rows by area: GATE 15 · PLAN 12 · DAY 25 · PICK 10 · READ 23 · SEL 19 · LINK 9 · STAT 14 · SET 12 ·
REM 15 · A11Y 10 · NAV 4 · OFF 7 · SYS 12.

**Required tier distribution of the 179 `UNVERIFIED` rows:** T1 = 61 · T2 = 20 · T3 = 45 ·
**T4 = 53**.

**Read that last number carefully.** Fifty-three rows — **30% of the matrix** — can only ever be
closed by a human holding a physical iPhone running a **release** build. No amount of CI closes
them. That is the verification cost of this port stated as a number, and §8 of
`test-port-strategy.md` converts it into hours.

For contrast: on Android, essentially every one of these behaviors had an automated test that could
fail, and the device pass existed to catch the residue. Here the ratio inverts — see
`test-port-strategy.md` §9.

The 8 `N/A-IOS` rows are SEL-06, LINK-08, SET-02, SET-11, REM-10, REM-12, REM-13, SYS-11. **Five
carry a paired absence row** (SEL-06a, LINK-09, SET-12, REM-11, REM-14) so that "we did not build
it" is itself verified rather than assumed. An `N/A-IOS` row without a paired absence row is only
acceptable where the absence is invisible to the user (REM-13, SYS-11).

---

## 7. Change log

| Date | Change | By |
|---|---|---|
| 2026-08-08 | Created at program start. 187 rows: 179 `UNVERIFIED`, 8 `N/A-IOS`, 0 green. Scope `N/A-IOS` rows written in from the owner-signed `ios-port-approach.md` §3 with paired absence rows. All intended divergences recorded as `UNVERIFIED (int: DIVERGENT)` — a scope decision is not evidence of shipped behavior. | Verification |
