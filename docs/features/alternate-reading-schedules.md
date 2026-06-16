# Daily Reading Planner — Product Spec: Alternate Reading Schedules (multiple plans)

> **Owner:** Maya (Product) · **Status:** Spec — owner moved this off the backlog (asked "what
> are some other popular ones"); awaiting owner sign-off on the open product questions in §11
> before an engineering spec is opened · **Last updated:** 2026-06-15
> **Backlog item:** [BACKLOG.md](../BACKLOG.md) #3 (Alternate reading schedules)
> **Companion docs:** [PRD.md](../PRD.md) (V1/V2 — this spec continues its U-/FR-/persona
> numbering), [PRD-v3.md](../PRD-v3.md) (V3 in-app reader), [docs/data/README.md](../data/README.md)
> (the Sprint-1 trusted-data gate this spec's correctness bar is modeled on), [CLAUDE.md](../../CLAUDE.md).
>
> **Supersedes** the discovery doc [explorations/alternate-reading-schedules.md](../explorations/alternate-reading-schedules.md).
> That exploration asked "do we even want this, and for whom?"; the owner has now signalled
> intent and asked for a landscape and a first cut, so this graduates the item to a real product
> definition. It carries the exploration's findings forward faithfully (the identity framing, the
> (A)/(B) split, the progress-on-switch question, the shape-generalization reality) and turns them
> into committed problem/goals/stories/requirements/scoping — it does **not** silently re-open the
> settled exploration conclusions, and it does not specify *how* to build (that is Diego's spec,
> opened only after the §11 owner questions are answered).
>
> This spec owns **what** we build and **why**. It hands the schema-generalization, the
> UI-adapts-to-N-streams work, and the progress storage mechanics to Diego (tech lead) and the
> sequencing to Morgan (EM). New ideas are flagged candidate/future, not committed scope.

---

## 0. The framing — read this first

Today the app is **"THE Bible Companion app," not "a reading-plan app."** Every layer is built
around the *shape* of one specific plan:

- the bundled `app/src/main/assets/reading_plan.json` — 365 date-anchored days × **exactly three**
  parallel streams;
- the three named streams baked into a fixed `enum Stream` (Law & History · Psalms & Prophecy ·
  New Testament), `STREAM_COUNT = 3`;
- progress keyed by full date, with completion meaning "all **three** readings done"
  (`DayCompletionClassifier`);
- the stats denominators (**1,095** = 365 × 3; "n of 365" per stream × 3);
- the year-at-a-glance **three** stream strips;
- the widget's **three** reading rows (across every responsive size tier);
- the reminder / persistent-notification copy ("the day's three references").

**This spec is a deliberate, owner-initiated identity evolution.** The Bible Companion stays the
flagship, the unchanged zero-setup default, and the identity anchor. The app *gains a plan
selector* — it becomes "a reading-plan app whose flagship plan is the Bible Companion," not "a
generic plan tracker." Being honest: **this is a meaningful product expansion, not a small
feature.** It touches the plan data model, progress storage, the stats visualizations, and the
widget. It is the most consequential thing we have shipped since the V3 reader, and it is bigger
than the V3 reader was in one specific way — V3 *added* a reader on top of the single-plan core
without questioning it; this feature questions the core's central assumption (that there is one
plan, of one shape). We do not pretend otherwise, and §4 / §11 carry that reality openly so it is
designed for, not discovered late.

The two pillars that do **not** change, and that this evolution is built to protect: **(a) the
app stays 100% offline — no networking, no telemetry, every plan is a bundled asset**; and **(b)
the respectful, non-gamified, KJV-anchored tone** for this Christadelphian audience. A plan
selector must never tax the reader who only ever wants the Bible Companion (PRD G1/M2: zero setup
to be correct).

---

## 1. The landscape (the owner asked "what are some other popular ones")

The owner asked what other plans exist; here is the landscape, grouped by **how hard a fit they
are to our existing machinery** — because "structural shape," not "popularity," is what
determines cost (§4). This is the menu a first cut (§9) is chosen from.

### 1a. Structurally similar — multi-stream, date-anchored (the easiest fit)

- **M'Cheyne plan** (Robert Murray M'Cheyne, 1842). ~4 chapters/day across **two or four
  streams** ("family" + "secret" readings); OT once, NT and Psalms twice; **date-anchored** like
  the Bible Companion (a given calendar date is the same readings for everyone). Large, active,
  cross-denominational audience; multiple public sources (incl. M'Cheyne's own text, the Gospel
  Coalition / "For the Love of God" Carson companion). **The closest structural cousin and the
  lowest-risk add** — same anchoring model, same "everyone in sync" value, differs only in stream
  count.
- **Discipleship Journal "Book-at-a-Time" / NavPress.** Multi-stream, **25 reading days per
  month** with built-in catch-up days (no calendar date carries a reading on days 26–31). A
  variant on the date-anchored model with deliberate slack — interesting because it *natively*
  solves the "I fell behind" problem the Bible Companion does not, but its "25 of N days" calendar
  semantics differ from a strict 365-day grid.

### 1b. Chronological variations (the owner's stated interest)

- **One-Year Chronological.** A **single stream** that reads scripture in approximate order of
  events: Job placed within Genesis, Psalms within David's life, the prophets interleaved with
  Kings/Chronicles, the Gospels harmonized. **The ordering is contested** — there is no one
  canonical chronological sequence; different publishers order it differently, and that ordering
  *is the IP* that must be sourced and verified. This is the **single biggest sourcing lift** in
  the landscape (§7) and the one that proves the **single-stream** generalization (§4).

### 1c. Pace / simplicity variations

- **Canonical straight-through** (Genesis → Revelation, ~3–4 ch/day, single stream). Trivial to
  source (it is just the canon in order); useful mostly as a low-cost proof of the single-stream
  shape.
- **5-day plan** (weekdays only) — readings only on Mon–Fri, weekends free.
- **90-day** (intense, ~12 ch/day) and **2-year** (gentle, ~1.5 ch/day) — different *lengths*,
  which reopen the year-strip and "% of year" denominators (§4).

### 1d. Intense / power-reader

- **Professor Horner's Bible Reading System.** **Ten** simultaneous lists, one chapter from each
  per day (10 chapters/day), each list cycling independently at its own length. Maximal
  stream-count and a fundamentally different (per-list-cyclic, **progress-anchored**) model.
  Listed for completeness; firmly out of any near cut.

### Product's read of the landscape

Two clusters matter for a first cut. **M'Cheyne** (1a) is the structurally-identical,
large-audience, low-risk add — it differs from the Bible Companion *only* in stream count and is
the right "prove the N-streams generalization on safe ground" first plan. **Chronological** (1b)
is the owner's stated interest and the right *second* plan — it proves the single-stream
generalization and the contested-ordering sourcing discipline, which is the real lift. Everything
in 1c/1d reopens a *length* or *anchoring* axis (§4) and is explicitly later (§9, §10).

---

## 2. Target users & the problem

### 2a. Who wants this, and the honest demand caveat

This feature has two candidate audiences, and the exploration's central finding holds: **there
are two very different features hiding under one backlog line.**

- **(A) "A few more plans, same community."** A Christadelphian — or any reader in our existing
  audience — who follows a plan *other than* the Bible Companion (a chronological track, M'Cheyne,
  a straight-through read). "This app is calm, offline, and exactly the kind of tracker I want —
  but it only knows the one plan I don't use." This is on-brand, bounded, and the realistic near
  candidate.
- **(B) "Any popular plan, a wider audience."** A general Bible reader who wants a clean,
  non-gamified tracker for whatever plan they follow. Real desire, but a **strategic
  repositioning** into a crowded category against well-funded incumbents (YouVersion et al.), not
  a feature increment.

**The owner's interest in chronological plans signals (A), broadened slightly** — chronological
is a *general* plan (not Christadelphian-specific), but adding it to *our* calm offline tracker
is still the (A) move ("a few good plans for our kind of reader"), not the (B) pivot ("be a
generic plan store"). This spec treats **(A)-broadened-to-include-a-couple-of-well-known-general-plans**
as the committed direction and flags where (B) would explode scope (§10, §11 Q2).

**The demand caveat (carried from the exploration, still true):** we have no analytics (settled,
PRD §12), so we cannot measure latent demand. The owner's tester circle / community is the only
signal. We do not need a formal demand gate to *start* (the owner has chosen to), but the first
cut is deliberately small (§9) so a "nobody switched off the Bible Companion" outcome is a cheap
finding, not an expensive mistake.

### 2b. The problem statement

- **For a reader who follows a different plan:** "The app's tracker, widget, stats, and offline
  reader are exactly what I want — but it's hard-wired to one plan I don't follow, so I can't use
  it." The product's value (calm, correct, offline, no-gamification, KJV-anchored) is plan-agnostic;
  the *plan* is the only thing locking them out.
- **For the Bible Companion reader (the core, must be protected):** "I don't want a plan I'll
  never use to add a single decision, screen, or tap to my morning." For this reader the feature
  must be **invisible until sought** — the default is pre-selected, the daily path is unchanged,
  and they should be able to use the app forever without knowing a selector exists.

---

## 3. Goals & non-goals

### Goals (continuing the PRD's G-numbering; V3 ended at G11)

- **G12 — Let a reader choose which plan they follow,** with the Bible Companion as the unchanged,
  zero-setup default. A Bible Companion reader never has to notice this feature exists.
- **G13 — Preserve correctness per plan.** Every shipped plan is verified against an independent
  second source exactly as the Bible Companion is today (PRD M1 / FR-7 is a release gate; it
  generalizes, never weakens). A plan we cannot verify, we do not ship.
- **G14 — Keep the core experience intact for whichever plan is active.** Today's readings, mark
  done, tap-to-text / in-app reader, date navigation, widget, stats — all still work and still
  feel calm and uncluttered, whatever the active plan's shape.
- **G15 — Don't tax the default user.** Plan selection lives where it belongs (Settings, and a
  light first-run option), never on the critical "what do I read today" path.
- **G16 — Generalize the app's shape honestly.** The 3-stream assumptions (schedule screen,
  per-stream progress, streaks, year strips, the widget's three rows, stats denominators) adapt to
  an active plan's *actual* stream count and length **truthfully** — no plan produces a misleading
  "100%," a broken strip, or guilt copy (PRD §13.0 holds for every plan).

### Non-goals (explicit — these keep the spec honest)

- **Not a user-authored / custom-plan builder.** Letting users *create* arbitrary plans is a
  separate, larger feature with a far bigger data-trust and UI problem. Out of scope.
- **Not plan *import* from a file or URL.** Same reason — it opens an untrusted-data and
  verification hole that contradicts the project's "trustworthy data" principle (PRD §10) and the
  Sprint-1 gate discipline. Out of scope.
- **Not running multiple plans simultaneously.** **One active plan at a time.** Multi-plan-at-once
  (§10) multiplies every reading, stats, and widget surface; it is a later, separate decision.
- **Not abandoning the Bible Companion's primacy.** It stays the default and the identity anchor.
  This is additive, never a downgrade of the flagship plan.
- **Not a general-purpose "any popular plan" store** as the goal. Interpretation (B), §2a, is **not**
  a goal of this work; the first cut is "a small, curated set of well-sourced plans," and growing
  that set into a generic store is a separate owner-level decision (§11 Q2).
- **Not changing KJV / Blue-Letter-Bible / provider / in-app-reader behavior.** Tap-to-text and the
  V3 reader are orthogonal to *which* plan you follow; a plan picks *what* to read, not *where* you
  read it.
- **Not progress-anchored ("day 1 = the day you start") plans in the first cut.** Strong product
  lean: date-anchored plans only to start (§4, §10) — progress-anchored breaks the Bible Companion's
  "everyone in sync" value and the calendar semantics of the date picker, Feb 29, and the year
  strip. Explicitly later and separately decided (§11 Q4).
- **Not per-plan reminders, per-plan widgets, or notification redesign.** One active plan drives the
  one reminder, the one persistent notification, and the one widget — they follow the active plan;
  they are not multiplied.

---

## 4. The structural reality (surfaced for Diego — not pre-deciding architecture)

This section frames the engineering reality the eng spec must solve. **It states the requirements
and flags the generalization work; it does not pick a schema or a UI mechanism — those are
Diego's.** Plans differ from the Bible Companion on three axes, in increasing order of how much
they reopen:

### 4a. Stream count (the axis a first cut must generalize)

Plans differ in **readings per day**: chronological = **1** stream, canonical straight-through =
**1**, Bible Companion = **3**, M'Cheyne = **2 or 4**, Horner = **10**. The app today hard-codes
3 in several concrete places — the eng spec must generalize each:

- The fixed `enum Stream` (three named values) and `Stream.fromNumber`.
- `DayCompletionClassifier.STREAM_COUNT = 3` (the single source of truth for "is the day
  complete?" — **R-STREAK-5 / D-S11-1**; it must become *per-plan* "this plan's reading count for
  this day," and **all completion logic must still flow through that one seam, never fork per
  plan**).
- The stats denominators: "n of 1,095" (= 365 × 3) and three "n of 365" per-stream rows.
- The **three** year-strip rows.
- The **three** widget rows (across the S9/S14 responsive size tiers).
- The reminder / persistent-notification copy ("the day's three references").

**Product requirement:** the schedule screen, per-stream progress, streaks, year strips, and the
widget must render the active plan's **actual** number of streams (1, 2, 3, 4 …) without looking
broken and without misleading numbers (G16). The Bible Companion's stream *names* (Law & History
etc.) are specific to it — other plans have their own stream labels (or a single unnamed stream
for chronological), so stream identity becomes **plan-supplied data**, not a hard-coded enum.

**Where this bites harder than it looks (flag for Diego):** the year strip is literally "three
rows of ~365 segments"; a 1-stream plan is one row, a 4-stream plan is four — fine in principle,
but the strip's height budget, legend, and a11y summaries are tuned to three. The widget's
responsive tiers (S9/S14) were tuned to fit exactly three rows at each size; a 4-stream plan
needs a row-count-aware tier policy, and a 1-stream plan reclaims space. These are real
generalization items, not a flag-flip.

### 4b. Anchoring — date-anchored vs progress-anchored (the deep divide)

- **Date-anchored** (Bible Companion, M'Cheyne): a given **calendar date** is the same readings
  for everyone, worldwide. Jan 1 is always Genesis 1–2 / Psalms 1–2 / Matthew 1–2. This is the
  Bible Companion's *defining* value and the reason "sync with the ecclesia" works (PRD G4). The
  date picker, the year strip, Feb-29 handling, and "everyone in sync" all assume it.
- **Progress-anchored** ("day 1 = the day you start," day-N-from-start): personal, like most
  general one-year plans when used casually. This **breaks** the date picker's meaning (a date no
  longer maps to a fixed reading), the "same for everyone" guarantee, Feb-29 handling, and the
  year-strip's calendar semantics.

**Strong product lean (carried from the exploration, restated as a scoping decision):** a first
cut supports **date-anchored plans only.** Supporting progress-anchored plans is a far bigger leap
than supporting more date-anchored ones — it is not "another plan," it is a second *anchoring
model* that re-foundations the date-keyed progress store, the picker, and the strip. **Flagged for
owner sign-off (§11 Q4); product recommends date-anchored-only for the first cut and treating
progress-anchored as a separate, later, explicit decision.** (Note: M'Cheyne and a *date-anchored*
chronological plan are both achievable date-anchored — a chronological plan can be published as a
365-day calendar table, which is how we would source it, sidestepping progress-anchoring.)

### 4c. Length (reopens denominators and the strip)

365-day, 90-day, 30-day, 2-year, 5-day-week. **Length** affects the stats denominators, the
year-strip (which is literally 365/366 calendar segments), and "% of year." **Product lean:** the
first cut is **365-day plans only** (M'Cheyne and a date-anchored chronological plan are both
365-day), which keeps the year strip and "% of year" intact. Non-365-day lengths are later (§10).

### 4d. The one seam that must not fork

Whatever the eng spec does, **`DayCompletionClassifier` stays the single source of truth for
completion** (R-STREAK-5, D-S11-1, D-S17-2). Generality flows *through* it (it learns each plan's
per-day reading count); it is never forked per plan. The picker dots, the stats, the streak walk,
the year strips, the reminder "skip when complete," and the persistent notification all consume
that one predicate today, and they must continue to. This is the anti-drift guardrail the whole
generalization hangs on.

> **For Diego:** the product requirement is "the app adapts to N streams / a plan's length
> truthfully, through the existing single completion seam." *How* plans are modeled (one schema
> with a `streams[]` array and a `planId`/`anchoring`/`length` descriptor? a per-plan asset set?),
> how N plans are bundled and validated, and how the UI lays out a variable stream count are your
> calls — see §11 Q5/Q6 and the "Note for engineering review," §12.

---

## 5. Personas & user stories (continuing the PRD's numbering; V3 ended at U18)

The existing personas extend; one need is genuinely new (a reader who follows a different plan).

- **Hannah / David / Ruth (PRD §3, PRD-v3 §4) — Bible Companion readers, UNAFFECTED.** They open
  the app, see today's three readings, mark them, read in-app or tap out, exactly as today. No new
  decision, no new screen, no migration they can see. The feature must be invisible to them.
- **A new facet — the reader on a different plan.** Not a new audience (still our calm,
  offline, KJV-anchored reader), just a reader whose plan the app didn't know. They want *their*
  plan's daily readings, tracked and shown with the same care the Bible Companion gets.

User stories (**U-ALT-** prefix to keep the alternate-plan set legible; they slot after U18):

- **U-ALT-1 — Bible Companion reader unaffected.** *As Hannah/David, I want the app to keep
  working exactly as it does today with no new decisions, so a plan feature never gets in my way.*
  - On a fresh install and on upgrade, the active plan is the Bible Companion with no prompt that
    forces a choice. **AC.**
  - The day screen, marks, widget, stats, reminders, and in-app reader behave identically to
    pre-feature for a user who never opens the plan selector. **AC.**
  - Upgrading users keep all existing progress and never see a migration. **AC.**

- **U-ALT-2 — Choose a chronological plan.** *As a reader who wants to read the Bible in roughly
  the order events happened, I want to select a chronological plan once, so the app shows that
  plan's daily reading instead of the Bible Companion's three streams.*
  - I can find and select a chronological plan in Settings (and, on first run, in the plan-choice
    option). **AC.**
  - After selecting it, the day screen shows that plan's reading for today; if it is single-stream,
    the screen shows **one** reading, not three empty stream rows. **AC.**
  - The widget, stats, year strip, and reminder reflect the chronological plan's single-stream
    shape truthfully. **AC (the year-strip/widget visual = device-pass; the count correctness =
    gate).**

- **U-ALT-3 — Choose M'Cheyne.** *As a reader who follows M'Cheyne, I want to select it, so the
  app shows M'Cheyne's daily readings.*
  - I can select M'Cheyne in Settings / first-run. **AC.**
  - The day screen shows M'Cheyne's readings for today, with the right number of streams (2 or 4,
    whichever published form we ship), each labeled with M'Cheyne's stream names, not the Bible
    Companion's. **AC.**
  - Completion = "all of this plan's readings for the day done"; the streak/stats/picker dots use
    that, through the one classifier seam. **AC (gate).**

- **U-ALT-4 — Switch plans, and understand what happens to my progress.** *As a curious reader, I
  want to switch plans and clearly understand what happens to my existing marks when I do, so I
  don't lose or confuse my history.*
  - When I switch plans, the app's behavior with my existing progress matches the chosen
    progress-on-switch rule (§6, FR-ALT-7) and is **explained at the moment of switching** —
    no silent data surprise. **AC.**
  - Switching back to a plan I used before restores that plan's history (under the recommended
    per-plan model, FR-ALT-7). **AC.**

- **U-ALT-5 — Trust every plan as much as the Bible Companion.** *As any reader, I want each plan's
  readings to be as accurate and verified as the Bible Companion's, so I can trust whichever one
  I'm following.*
  - Every shipped plan passes the same Sprint-1-style verification gate (independent second
    source, day-by-day equality, reconciliation log) before it ships. **AC (release gate).**

- **U-ALT-6 — See which plan I'm on.** *As any reader, I want the app to clearly show which plan is
  active, so I always know whose schedule I'm looking at.*
  - The active plan's name is visible (e.g. in Settings, and discoverable from the schedule
    screen) without cluttering the daily "what do I read today" path. **AC (placement = Priya's
    visual call; product requires it be discoverable but off the critical path).**

---

## 6. Functional requirements (product-level; no design)

Continuing the convention; **FR-ALT-** prefix to keep the alternate-plan set legible (the PRD's
V1/V2 ended at FR-23, V3 used FR-V3-*).

### P0 — must ship in the first cut

- **FR-ALT-1 — Plan selection.** The user can see the set of available plans and choose one. The
  chosen plan persists and drives **every** reading surface: the day screen, date navigation, the
  widget, reminders, the persistent notification, stats, and the in-app reader handoff. The Bible
  Companion is **pre-selected** for every user, old and new.
- **FR-ALT-2 — Default & upgrade safety.** Existing installs (all on the Bible Companion) continue
  *exactly* as today: no migration they can see, no forced choice, all progress preserved.
  "Multiple plans exist" is invisible until the user goes looking. (Mirrors the V3 D-S14-1 /
  upgrade-preservation discipline.)
- **FR-ALT-3 — Per-plan correctness (release gate).** Every shipped plan carries the same
  verification guarantee as the Bible Companion (PRD M1 / FR-7; §7 below): each plan's schedule is
  validated against an independent second source in CI, as a release gate. **A plan we cannot
  verify against an independent second source, we do not ship.**
- **FR-ALT-4 — Plans may differ in stream count, and the app adapts truthfully.** The active
  plan's **actual** number of streams (1, 2, 3, 4 …) drives the day screen, per-stream progress,
  streaks, year strips, the widget rows, and the stats denominators — through the single
  `DayCompletionClassifier` completion seam, never a per-plan fork (§4a, §4d). Stream **identity**
  (names/labels, or a single unnamed stream) is plan-supplied data, not a hard-coded enum. No plan
  produces a misleading "100%," a broken strip, or guilt copy (PRD §13.0, FR-16). *(OPEN as to the
  exact UI adaptation for non-3-stream plans — that is design, §11 Q6 + Priya; the product
  requirement is "right count, truthful, calm.")*
- **FR-ALT-5 — First cut is date-anchored, 365-day plans only.** The first increment supports
  **date-anchored** (a calendar date is the same readings for everyone) **365-day** plans only.
  Progress-anchored and non-365-day plans are explicitly out of the first cut (§4b/§4c, §10) and
  are separate later decisions (§11 Q4). This keeps the date picker, Feb-29 handling, the year
  strip, and "% of year" intact — the generalization is "more streams," not "a new calendar model."
- **FR-ALT-6 — The active plan's identity is visible.** The user can see which plan is active
  (FR-ALT-1's persistence has a visible name), discoverable from Settings and the schedule, **off**
  the critical daily path (G15). The Bible Companion reader who never looks still sees "today's
  three readings," unlabeled-by-plan, exactly as now.
- **FR-ALT-7 — Existing progress when switching plans (the #1 product decision; recommendation
  below, OPEN for owner sign-off).** Switching plans must have a defined, explained behavior for
  the marks already made. **Product recommendation: per-plan progress** — each plan keeps its own
  marks; switching shows the new plan's (possibly empty) history; switching back restores the
  prior plan's. **Rationale and the alternatives are in §8; this is a product-semantics decision,
  flagged for owner sign-off (§11 Q3), not an engineering call.** Whatever the rule, the switch
  must be **explained at the moment of switching** (U-ALT-4) — never a silent data surprise.
- **FR-ALT-8 — Stats / streaks / year-strip generalize honestly.** Whatever shape the active plan
  has (within FR-ALT-5's date-anchored/365-day bound), the stats surface stays correct and calm
  (PRD §13.0): the year-progress denominator is "365 × this plan's stream count," per-stream rows
  match the plan's streams, the year strip has the plan's number of rows, and the streak rules
  (R-STREAK-1…6) generalize through the one classifier seam (each plan's per-day reading count).
  No misleading 100%, no broken strip, no guilt copy — for every plan.
- **FR-ALT-9 — Widget generalizes.** The widget shows the **active plan's** reading rows (the right
  number, with the plan's stream labels) at every responsive size tier (S9/S14) without looking
  broken. A 1-stream plan shows one row; a 4-stream plan shows four (or the tier's truthful
  degradation). It still shows the active plan's day, opens the app, and degrades on Feb-29 / error.

### P1 — strongly desired in the first cut if cheap; otherwise first follow-up

- **FR-ALT-10 — First-run plan option.** New installs may be offered a plan choice at first run
  (alongside the existing Sprint-19 tracking-start and the V3 reading-destination first-run
  questions) — but the Bible Companion must be the clear, pre-selected, recommended default, and
  skipping the question must land on the Bible Companion (the question must **never** tax the
  zero-setup promise). *Product lean: keep it a light, skippable, recommended-default question, or
  defer it to Settings-only if it crowds first-run (§11 Q7).*
- **FR-ALT-11 — Tap-to-text / in-app reader unchanged per plan.** Whatever plan is active, tapping
  a reading resolves through the existing `ProviderUrlBuilder` / `BibleProvider` /
  `OpenReferenceUseCase` / V3 in-app-reader handoff with no new work — confirming only that the new
  plan's references resolve to valid provider URLs and valid verse-id ranges (a per-plan
  verification item, FR-ALT-3). Single-stream and N-stream portions flow through the existing
  Portion → verse-id bridge unchanged.
- **FR-ALT-12 — Reminder / persistent-notification copy generalizes.** Reminder and persistent-tray
  content is "the active plan's day references" (R-REM-3 / the S22 persistent body), which already
  generalizes as long as a plan yields a day's references; the "skip when complete" rule (R-REM-4)
  uses the per-plan completion definition (FR-ALT-8 / the one classifier seam).

### P2 — explicitly out of the first cut (tracked for later — §10)

- Non-3-stream-but-also-non-1/2/4 exotic counts beyond what the first shipped plans need; non-365-day
  lengths (90-day, 2-year, 5-day-week); progress-anchored plans; general "any popular plan" store
  (interpretation B); custom / user-authored plans; plan import (file/URL); multiple simultaneous
  plans; per-plan reminders/widgets.

---

## 7. Trusted-data requirement (hard requirement; the phasing driver)

**Every plan is a new bundled asset that MUST pass the Sprint-1-style verification gate before it
ships. This is non-negotiable and is the single biggest cost driver of this feature.** It is what
makes the app's data *trustworthy*, which is the product's credibility (PRD §10, the project's two
core IP assets — the plan and the KJV text — were both held to this bar).

For **each** plan we ship, the same discipline the Bible Companion got (see
[docs/data/README.md](../data/README.md)):

1. **Two genuinely independent sources** — a primary and a checksum-distinct second source (guard
   the Sprint-1 "same upstream re-mirrored" trap: the pricejh PDF was byte-identical to the chart
   and had to be replaced; the V3 KJV import rejected the eBible USFX as the same upstream as its
   primary). Two parsers, independently written.
2. **Day-by-day equality** — the canonical asset and the independent second-source fixture agree
   on every day (the gate's central invariant), with a **reconciliation log** documenting every
   conflict and how it was resolved on *evidence*, not source precedence (the Bible Companion had
   7 conflicts across 365 days, all logged).
3. **Coverage / structural invariants** appropriate to the plan (e.g. "every chapter of the canon
   is read the expected number of times" — OT once / NT twice for the Bible Companion; the right
   coverage for each new plan).
4. **A CI gate** that runs offline in `testDebugUnitTest` and **blocks release**, exactly as
   `ReadingPlanVerificationTest` does today (now 11 tests). Reproducible extraction scripts in
   `tools/`, committed second-source fixture, schema pinned.

**Chronological plans are the heaviest sourcing lift, and this drives phasing.** The
chronological *ordering* is contested — different publishers sequence it differently — so "an
independent second source that agrees day-by-day" is genuinely hard: two chronological plans from
two publishers may legitimately *disagree* on where Job or a Psalm sits, which is not a typo to
reconcile but a real editorial difference. **Product requirement:** we ship a *specific, named,
sourceable* chronological plan (e.g. a particular publisher's published one-year chronological
table that has a verifiable second witness), not "a chronological plan" in the abstract — and if
no two genuinely-independent sources agree on a given chronological ordering, **we do not ship
that plan** (FR-ALT-3). This is *why* M'Cheyne (well-documented, multiple agreeing public sources,
structurally simple) is the recommended first plan and chronological is the second (§9): the data
cost, not the code, sets the order.

> **Owner data commitment is a hard input, not an engineering detail.** Each plan is its own small
> data project (extraction + second source + reconciliation + gate). Without named,
> second-source-verifiable plans and someone to own that recurring per-plan work, FR-ALT-3 can't be
> met and the plan can't ship. This is flagged for the owner (§11 Q5).

---

## 8. Progress-on-switch — the open question and the recommendation

This is the hardest *product-semantics* decision and the one the single-plan app never had to
answer. Progress today is keyed by full date and is implicitly "Bible Companion progress" because
there is only one plan. Once plans differ in shape, "did I read today?" stops having one answer.
Three candidate models, each with a real cost:

- **(a) Per-plan progress (RECOMMENDED).** Each plan keeps its own marks; switching shows the new
  plan's (possibly empty) history; switching back restores the prior plan's. Stats / streaks /
  strips become *per active plan*.
  - **Pro:** mentally cleanest ("my Bible Companion year" and "my chronological year" are separate,
    true things); never produces a wrong completion (a 1-stream day is complete at 1, a 3-stream
    day at 3, and they never bleed into each other); experimentation is **safe** — try a plan,
    switch back, your real history is intact; matches the recommended "Bible Companion is the
    flagship and is protected" posture (their year is never disturbed by a side experiment).
  - **Con:** "my year" fragments across plans (no single cross-plan total); the progress store and
    the stats queries become plan-scoped (Diego's cost, §12); the streak walk is per-plan.
- **(b) Global "did I read today?".** A day is "done" independent of plan. Simple, but
  **semantically wrong** the moment plans differ in shape — a day complete on a 1-stream plan is
  not the same as a complete Bible Companion day, and a streak that spans a plan switch is
  comparing unlike days. Rejected: it produces exactly the misleading numbers PRD §13.0 forbids.
- **(c) Destructive / warned switch.** Changing plans clears or archives prior progress with an
  explicit warning. Honest and simple to build, but **punishes experimentation** — a reader who
  tries chronological for a day and switches back has lost their Bible Companion history, or is
  scared off trying at all. Contradicts the calm, low-stakes posture.

**Product recommendation: (a) per-plan progress.** It is the only model that keeps completion
correct across differing plan shapes, protects the flagship plan's history from experimentation,
and lets a reader explore without fear — at the cost of plan-scoped stats and a more involved
progress store. **The switch must be explained at the moment of switching either way** (U-ALT-4):
under (a), "Your [old plan] progress is saved and will be here if you switch back; [new plan]
starts fresh."

This cascades into stats, streaks, the year strip, and the widget (all become "for the active
plan"), so it must be decided before the eng spec is opened. **Flagged as the #1 open question for
owner sign-off (§11 Q3).** Diego's input on the cheapest *correct* implementation given the
current full-date-keyed Room schema is welcome and shapes the recommendation, but it must not
let an engineering preference silently decide the product semantics — this is Maya's call with the
owner.

---

## 9. Release scoping / phasing (recommended first cut)

**Recommended first cut: M'Cheyne first, then a date-anchored chronological plan.** The order is
set by **data-sourcing risk and structural risk**, not by code (§7).

### Phase 1 — the N-streams generalization, proven on safe ground (M'Cheyne)

**Ship:** the plan-selection mechanism (FR-ALT-1/2/6), the per-plan verification gate
generalization (FR-ALT-3), the stream-count generalization (FR-ALT-4/8/9 — day screen, per-stream
progress, streaks, year strips, widget, stats denominators all adapt to N streams through the one
classifier seam), the progress-on-switch rule (FR-ALT-7, recommended per-plan), and **M'Cheyne** as
the first alternate plan.

**Why M'Cheyne first.** It is the **structurally-identical, large-audience, low-risk** add — it
differs from the Bible Companion *only* in stream count (and uses the same date-anchored, 365-day
model), so it exercises the entire N-streams generalization while keeping anchoring and length
fixed. Its data is well-documented with multiple agreeing public sources, so the trusted-data gate
is achievable (unlike chronological). It proves the hardest *code* lift (variable stream count
across schedule/stats/strip/widget) on the *safest* data.

### Phase 2 — the single-stream generalization + the contested-ordering data lift (chronological)

**Ship:** a **specific, named, date-anchored** one-year chronological plan that has a verifiable
independent second source (§7), proving the **single-stream** end of the generalization (one
reading/day, one strip row, "n of 365" once, one widget row) and the contested-ordering sourcing
discipline.

**Why second.** It is the owner's stated interest, but it is the **biggest sourcing lift** (the
ordering is the contested IP) and it proves the *other* edge of the stream-count range (1, vs
M'Cheyne's 2–4). Doing it after M'Cheyne means the code generalization is already proven and the
sprint is mostly a data project — which is exactly where the risk lives.

### Later / separate decisions (each reopens a closed axis — §10)

Non-365-day lengths, progress-anchored plans, the general "any popular plan" store
(interpretation B), custom/imported plans, multiple simultaneous plans, per-plan reminders/widgets.
Each is its own owner-level decision; none is in the first cut.

> **Sequencing is Morgan's call.** Product's recommendation is the *plan order* (M'Cheyne →
> chronological) and that **the N-streams generalization + selector + progress-on-switch ship
> together with the first alternate plan** (M'Cheyne), because a selector with only one plan, or a
> generalization with no plan to exercise it, is not shippable value. How that splits across
> sprints — and whether the generalization is one big sprint or staged (data layer → stats → widget)
> — is Morgan + Diego.

---

## 10. Candidate / later (explicitly not in the first cut)

A menu of expansions, each of which reopens a layer the first cut keeps closed. **None is
committed; each is a separate, later owner decision.**

- **Non-365-day plans** (90-day, 30-day, 2-year, 5-day-week) — reopens the year-strip and "% of
  year" denominators (§4c).
- **Progress-anchored plans** ("day 1 = start day," day-N-from-start) — reopens the date-anchored
  "everyone in sync" value, the date picker's meaning, Feb-29 handling, and the year-strip's
  calendar semantics. The biggest single leap (§4b, §11 Q4).
- **General / popular (non-curated) plan store** — interpretation (B), §2a: the audience-broadening
  pivot into a crowded category. A strategic repositioning, not a feature (§11 Q2).
- **Custom / user-authored plans** and **plan import (file/URL)** — reopen the untrusted-data and
  verification hole (§3 non-goals).
- **Multiple simultaneous plans** — multiplies every reading, stats, and widget surface.
- **Per-plan reminders / per-plan widget instances** — follow-ons to multi-plan.
- **More plans within the curated set** (Discipleship Journal, canonical straight-through, etc.) —
  each a small data project once the mechanism exists; demand-ordered.

*New ideas above are candidates only and must not be read as committed scope.*

---

## 11. Open product questions, risks & accepted constraints

### Open product questions (need the owner or a teammate before the eng spec opens)

Ordered by how load-bearing they are.

- **OQ-1 — Owner (the curated set).** Which specific plans do we commit to, and in what order?
  Product recommends **M'Cheyne first** (structurally identical, large audience, well-sourced),
  **a named date-anchored chronological plan second** (your stated interest, biggest data lift).
  Confirm, and name the *specific* chronological plan/publisher we'd source (the ordering is the
  IP, §7). *[Owner + Maya.]*
- **OQ-2 — Owner (which app do we want to be).** Confirm this is **(A) a small curated set of
  well-sourced plans for our kind of reader**, with the Bible Companion as the unmistakable
  flagship — **not (B) a general "any popular plan" store** (a strategic pivot into a crowded
  category). These are different products; this spec assumes (A). *[Owner — strategic, not a
  feature call.]*
- **OQ-3 — Owner + Maya (progress-on-switch — the #1 semantics decision, §8).** Confirm
  **per-plan progress** (recommended): each plan keeps its own marks, switching back restores them,
  the switch is explained at the moment. The alternative (destructive-with-warning) is simpler to
  build but punishes experimentation; global "did I read today?" is rejected as semantically wrong.
  This cascades into stats/streaks/strips/widget and gates the eng spec. *[Owner + Maya; Diego
  advises on the cheapest correct implementation.]*
- **OQ-4 — Owner + Maya (anchoring scope).** Confirm the first cut is **date-anchored, 365-day
  plans only** (§4b/§4c), and that **progress-anchored plans are a separate, later, explicit
  decision** — not smuggled into the first cut. Product strongly recommends this; it preserves the
  Bible Companion's "everyone in sync" value and the calendar machinery. *[Owner + Maya.]*
- **OQ-5 — Owner (the recurring data cost).** Each plan is a fresh extraction + independent second
  source + reconciliation + CI gate (Sprint 1 took real effort for *one* plan, and the V3 KJV
  import was a whole sprint). Are you willing to own that recurring per-plan data work, and is each
  candidate plan genuinely second-source-verifiable? A plan without a trustworthy second source
  **cannot ship** (FR-ALT-3). *[Owner + data/QA.]*
- **OQ-6 — Diego + Priya (UI adaptation to N streams).** What does the day screen, year strip, and
  widget look like for 1, 2, and 4 streams (not just 3)? Product requires "right count, truthful,
  calm"; the visual design (strip height/legend/a11y for a variable row count; the widget's
  row-count-aware responsive tiers) is Priya's, and the layout mechanism is Diego's. *[Diego +
  Priya; Maya signs off it stays calm.]*
- **OQ-7 — Owner + Maya + Priya (selector placement & first-run).** Settings-only (keeps the
  zero-setup default pristine) vs. a light, skippable, recommended-default first-run plan question
  (alongside the existing tracking-start + reading-destination first-run dialogs — risks crowding
  first-run, PRD G1/M2). Product lean: Settings is mandatory; a first-run option only if it stays
  light and defaults cleanly to the Bible Companion. *[Owner + Maya + Priya.]*

### Risks

- **Identity-dilution risk (the headline).** We are "THE Bible Companion app." Becoming "a
  reading-plan app" trades a sharp, defensible identity for a broader one. For the curated-set cut
  (A) this is **mild and on-brand** (a flagship plan plus a couple of well-known plans, still calm
  and offline); for the general-store interpretation (B) it is **severe and largely irreversible**
  in users' minds. *Mitigation: keep the Bible Companion the unmistakable, pre-selected default
  (FR-ALT-2/6); gate (B) behind a deliberate owner decision (OQ-2).*
- **Per-plan data-sourcing & verification burden (the real recurring cost).** Each plan is a fresh
  two-source reconciliation + CI gate; chronological's contested ordering is the heaviest (§7).
  Plans without a trustworthy second source **cannot ship**. *Mitigation: scope to plans with
  credible independent public sources; treat each plan as its own small data project; M'Cheyne
  first because its data is the safest.*
- **Generalization debt across stats / streaks / widget / strip.** Today these are tuned to 3 ×
  365 (1,095; three strips; three widget rows; the classifier over "all three"). Generalizing to N
  reopens layout and a11y in each surface (§4a). *Mitigation: the date-anchored / 365-day bound
  (FR-ALT-5) keeps anchoring and length fixed so only the stream count moves; the one seam to
  preserve is `DayCompletionClassifier` — generality flows through it, never forks per plan
  (R-STREAK-5 / D-S11-1 / D-S17-2).*
- **Progress-semantics regression (§8).** Getting the switch-progress model wrong produces exactly
  the confusing/guilt-inducing stats PRD §13.0 forbids. *Mitigation: decide OQ-3 explicitly; test
  it like M6 (stats integrity) across plan-switch scenarios per plan.*
- **Setup-tax regression on the default user.** Any new choice on the daily path erodes the
  zero-setup "what's today" value (PRD G1/M2/M3). *Mitigation: FR-ALT-1/2/6 keep selection off the
  critical path and the default invisible; OQ-7 keeps first-run light or Settings-only.*
- **The "everyone in sync" value vs. start-relative plans.** The Bible Companion's defining value
  is that a date is the same readings for everyone (PRD G4). A progress-anchored plan breaks that
  for *its* readers. *Mitigation: first cut is date-anchored-only (FR-ALT-5 / OQ-4); a date-anchored
  chronological plan keeps the sync value; progress-anchored is a deliberate later decision that
  must reckon with this loss explicitly.*
- **Scope-creep into custom/imported plans.** "Multiple plans" pulls toward "let users add their
  own," reopening the untrusted-data hole. *Mitigation: hold the §3 non-goals firmly.*

### Accepted constraints (carried, not re-decided)

- **No networking / no analytics / no telemetry.** Every plan is a **bundled** asset; there is no
  plan download, no plan store fetch, no demand instrumentation. This is load-bearing identity
  (PRD §12, NFR-V3-A), unchanged.
- **KJV-anchored, respectful, non-gamified tone** holds for every plan and every generalized
  surface (PRD §13.0). The plural-plan world adds no gamification.
- **Sequencing relative to V3:** no technical dependency on V3 (a plan picks *what* to read; V3
  governs *where*), but **roadmap-wise this follows V3** — V3 is the committed reader and should
  finish/stabilize before the plan model is re-foundationed. Target track **post-V3** (Morgan
  confirms; this is the exploration's V4+ lean, now concrete as "after the V3.0 reader ships").

---

## 12. Note for engineering review (Diego / Morgan)

This is a **product** spec, deliberately stopping at *what* and *why*. It does **not** specify the
plan data model, the storage format, how N plans are bundled/validated, the migration mechanics,
or how a variable stream count is laid out — those are the tech lead's to design *after* the §11
owner questions (especially OQ-3 progress-on-switch and OQ-4 anchoring scope) are answered,
because the answers change the shape of what you'd build. What this spec asks of engineering
review:

- **A gut-check on the N-streams generalization (§4a).** Given the concrete 3-stream assumptions
  baked in (`enum Stream`, `STREAM_COUNT = 3`, the 1,095 denominator, three strips, three widget
  rows, the S9/S14 responsive tiers tuned to three), how big is "make the day screen / stats /
  strip / widget render the active plan's actual stream count, through the one classifier seam"?
  Where does even the *date-anchored, 365-day, variable-stream* generalization bite harder than it
  looks? (Product flagged the strip height/legend/a11y and the widget tier policy as the suspects,
  §4a.)
- **A read on FR-ALT-7 / OQ-3 (progress-on-switch).** Which model is cheapest to do *correctly*
  given the current full-date-keyed Room `ProgressDatabase`? Per-plan progress (recommended)
  implies a plan dimension on progress and on every stats query — what's the migration story for
  existing (Bible-Companion-only) marks, and does it stay a no-op for the default user? Your input
  shapes the recommendation; it must not silently *decide* the product semantics (Maya's call).
- **A read on the data model (§4d).** Is "one schema with a `planId` + a `streams[]` array + an
  `anchoring`/`length` descriptor, N validated assets" the right shape, or per-plan asset sets? The
  product requirement is only that **`DayCompletionClassifier` stays the single completion seam**
  and that each plan is independently second-source-verified (FR-ALT-3); the rest is yours.
- **A sequencing view (Morgan).** Confirm this is **post-V3** (after the V3.0 reader stabilizes),
  confirm the plan order **M'Cheyne → chronological** (data risk, §7/§9), and confirm the
  selector + N-streams generalization + progress-on-switch **ship together with the first alternate
  plan** (a selector with one plan, or a generalization with no plan, is not shippable value).

We should not open an engineering spec until **OQ-2 (which app)**, **OQ-3 (progress-on-switch)**,
and **OQ-4 (anchoring scope)** have owner answers — those three change the shape of what gets
built. Building the wrong interpretation of this item (the general-store pivot, the wrong
progress model, or progress-anchored plans smuggled into the first cut) is the most expensive
mistake available here.
