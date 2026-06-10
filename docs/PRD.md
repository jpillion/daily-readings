# Daily Readings — Product Requirements Document (PRD)

> **Owner:** Maya (Product) · **Status:** Draft for build · **Last updated:** 2026-06-10
> **Companion docs:** [docs/SPEC.md](SPEC.md) (full spec + build plan), [CLAUDE.md](../CLAUDE.md) (session handoff)
>
> This PRD owns **what** we're building and **why**. It defers **how to build** to the tech
> lead and **how to sequence** to the EM. It builds faithfully on decisions already recorded
> in SPEC.md and CLAUDE.md; anything new is flagged as candidate/future, and every settled
> open decision is carried to §12 rather than re-decided here.

---

## 1. Overview / vision

Daily Reading Planner is an offline-first Android app that turns the Christadelphian **"Bible
Companion"** daily reading plan (Robert Roberts) into a simple digital planner. You open the
app, see today's three scripture readings, mark them done, and tap any reading to read the
chapter on Blue Letter Bible (KJV). The plan is **date-anchored** — January 1 is always
Genesis 1–2 / Psalm 1–2 / Matthew 1–2 — so every reader worldwide is on the same portions
each day, in sync with each other and with their ecclesia. **V1 is a planner/tracker, not a
Bible reader:** it tells you *what* to read and tracks *whether* you read it; the text itself
is one tap away in the browser.

## 2. Problem statement & background

**The pain.** Christadelphians who follow the Bible Companion plan today have to *look up*
each day's three readings — flipping a printed booklet/card, recalling a memorized table, or
visiting a website — then separately open a Bible to the right chapters. The schedule is
fixed and identical for everyone, yet there's no frictionless, always-with-you way to answer
"what are today's readings, and have I done them?" People always have their phone; the plan
should be there too.

**The plan.** A fixed calendar reading schedule devised by Robert Roberts (~1853, later
refined) and used worldwide. Structure:

- **Three readings every day**, from three parallel streams through scripture:
  1. **Stream 1 — Law & History:** Genesis → Job
  2. **Stream 2 — Psalms & Prophecy:** Psalms → Malachi
  3. **Stream 3 — New Testament:** Matthew → Revelation
- ~3–4 chapters/day (~30 min). Over the year: **Old Testament once, New Testament twice.**
- **Date-anchored, not progress-anchored.** Readings are keyed to the calendar date, not to
  how far you've personally read. Many ecclesias read the day's portions at the weekly
  Breaking of Bread, so being in sync matters.
- The bundled table covers the **365 standard calendar days** (February has 28 day-entries; there
  is no Feb 29 entry — see the Feb 29 rule in §11/§12).

**Why an app, why now.** The plan data is small, public, and stable; the value is in
removing daily friction (lookup + tracking) for an audience that already carries a phone.
V1 deliberately ships the *planner* and links out for text, so we can deliver the core value
fast without taking on a bundled-Bible dataset.

## 3. Target users & personas

General Christadelphian audience following (or wanting to follow) the Bible Companion plan.
We are not targeting a niche within that audience; the UI should suit a broad range of ages
and comfort with technology.

- **Hannah — the daily reader.** Reads the three portions most mornings. Wants to open one
  app, see today's readings, tap to the text, and tick them off. Values speed and a clear
  "what's today" answer over configuration.
- **David — syncing with the ecclesia.** Wants to be on the same readings as his
  congregation's weekly Breaking of Bread and broader community. Cares that the plan is
  *correct* and *the same for everyone* (date-anchored). Will occasionally look ahead/back to
  a specific date.
- **Ruth — the returning/aspiring reader.** Has fallen behind or is new to the plan. Wants a
  low-friction way to start today without guilt or setup. (Catch-up motivation/streaks are
  **V2**; V1 must at least make "what do I read today" effortless.)

## 4. Goals & success metrics

**Product goals**
- G1 — Make "what are today's three readings?" instant (open app → answer on first screen).
- G2 — Let a reader track completion with near-zero friction (≤ a couple of taps for a full day).
- G3 — Get from a reading to its text in one tap (Blue Letter Bible, KJV).
- G4 — Keep everyone in sync: the schedule shown for a given date is correct and identical
  for all users worldwide.
- G5 — Be glanceable from the home screen (widget) so the app earns a daily-habit slot.

**Success metrics (observable)**
- **M1 — Plan accuracy:** 100% of the 365-day table matches a second independent source
  (verification test passes in CI). *This is a release gate, not a nice-to-have.*
- **M2 — Time-to-today:** today's three readings are visible on cold launch in ≤ ~1 second on
  a mid-range device, with no setup screen between launch and Today.
- **M3 — Completion ergonomics:** marking a whole day done is achievable in ≤ 2 taps from the
  Today screen.
- **M4 — Engagement (post-launch):** D1/D7 return rate of installs; share of active users who
  mark ≥1 reading on a given day; widget add rate. *(Targets to be set after baseline; we are
  not over-instrumenting V1.)*
- **M5 — Link success:** tapping a reading opens the correct BLB chapter (manual QA across
  all 66 books via the abbreviation table; 0 known broken books at launch).

> Analytics scope is intentionally light for V1. Exact instrumentation/telemetry approach
> (and any privacy stance/opt-in) is a tech-lead + privacy decision — see Open Questions.

## 5. Non-goals (explicit)

- **V1 is not a Bible reader.** No scripture text is bundled or rendered in-app. Text is
  reached by linking out to Blue Letter Bible. (In-app text is **V3**.)
- **No per-chapter tracking.** Tracking is per-reading (3 toggles/day), not per-chapter.
- **No streaks, stats, reminders, or notifications in V1.** (Motivation is **V2**.)
- **No accounts, login, sync, or cloud backup in V1.** Progress is local to the device.
- **No multi-translation support.** KJV only (decided). No translation picker.
- **No content/study-notes feature in V1.** (Study notes are **V3**.)
- **Not specifically optimized for low-vision/aging users.** General audience with a
  comfortable (not tiny) default text size; full accessibility tuning is out of V1 scope
  (though we won't actively break accessibility).
- **Not iOS/web.** Android only. (A prior-art iOS app exists; we are not cloning it.)

## 6. Scope by release

### V1 — Digital reading planner (no in-app scripture text) — **detailed**

The smallest lovable product: see today's readings, mark them, tap out to the text, look up
any date, and have it on the home screen.

- **Today screen (primary).** Open the app → see today's three readings as references
  (e.g. "Genesis 1–2 · Psalm 1–2 · Matthew 1–2"), labeled/grouped by stream. This is the
  default landing screen; no setup precedes it.
- **Mark as read.** Per-reading toggle (three toggles/day, *not* per-chapter) **plus** a
  one-tap **"mark whole day done"** that toggles all three at once. State persists locally.
- **Tap a reading → Blue Letter Bible (KJV).** Opens that chapter in the browser at
  `https://www.blueletterbible.org/kjv/<book>/<chapter>/`.
- **Date picker.** Calendar picker, **month/day only** (year is irrelevant — the schedule
  repeats yearly), to view any date's readings.
- **Home-screen widget.** Shows today's three readings; tapping it opens the app.
- **Settings (minimal).** Light / dark / system theme.
- **Audience/UI.** General audience; comfortable (not tiny) default text size; simple, usable
  interface.

**Data foundation (blocking, ships in V1):** one bundled, read-only **reading-plan** dataset
(365 days × 3 portions; February has 28 day-entries, no Feb 29 entry), extracted **and verified
against a second source**, plus a canonical
**book-name → BLB 3-letter abbreviation** table. No scripture text dataset in V1.

**Data model intent (product-level, not schema):**
- **Schedule** keyed by **(month, day)** — year-agnostic, repeats every year; this is the
  bundled JSON.
- **Progress** keyed by the **full date including year** — a reading marked done on
  1 Jan 2026 must *not* appear done on 1 Jan 2027. The schedule repeats; history does not.

### V2 — Motivation — *deferred*

- **Streaks & stats:** current/longest streak, % of year read, per-stream progress.
- **Reminders** for missed readings (notifications) and other motivational features.

**Why deferring is safe:** the core value (know today's readings, mark them, read the text)
is fully delivered by V1. Streaks/reminders amplify a habit that must first exist; shipping
them before the planner would be premature. V1's progress data is keyed by full date, which is
the foundation streak/stats queries need later, so V2 builds on V1 without rework.

### V3 — Text & beyond — *deferred*

- **In-app scripture text:** bundled, read-only **KJV** (public domain). This is where the
  offline Bible dataset work lives.
- Study-notes integration (dailyreadings.org.uk style); additional translations (licensing
  permitting); audio playback; verse sharing; cross-device backup.

**Why deferring is safe:** bundling and validating a full KJV dataset is the project's
largest data/risk item, and Blue Letter Bible already serves the text one tap away for free.
V1 proves the planner is wanted before we invest in carrying scripture in-app. Deferring also
keeps the V1 app small and avoids the offline-Bible storage/schema work entirely.

## 7. User stories / key use cases

- **U1.** As a daily reader, I want to open the app and immediately see today's three
  readings, so I can start reading without looking anything up.
- **U2.** As a daily reader, I want to mark each reading done (or the whole day at once), so I
  can track what I've completed.
- **U3.** As a reader, I want to tap a reading and land on that chapter's text, so I can read
  it without manually finding the passage.
- **U4.** As someone syncing with my ecclesia, I want to look up the readings for a specific
  date, so I can prepare ahead or check a day I missed.
- **U5.** As a habitual reader, I want today's readings on my home screen, so I'm reminded and
  can jump in with one tap.
- **U6.** As any user, I want a dark theme option, so the app is comfortable to read morning
  or night.
- **U7.** As any user, I want my completion marks to be specific to this year, so last year's
  history doesn't make today look already done.

## 8. Functional requirements (prioritized)

**P0 — must ship in V1**
- FR-1 (U1, G1) Today screen is the default landing screen and shows today's three readings as
  references, grouped/labeled by stream, on launch with no intervening setup.
- FR-2 (U2, G2) Each of the three readings has an independent mark-as-read toggle.
- FR-3 (U2, G2) A single "mark whole day done" action toggles all three readings for the day.
- FR-4 (U3, G3) Tapping a reading opens its chapter on Blue Letter Bible (KJV) in the browser
  using the confirmed URL pattern and the book-abbreviation table.
- FR-5 (U4) A month/day calendar picker lets the user view any date's three readings; the
  Today screen and picker render readings from the same bundled (month, day) schedule.
- FR-6 (U7) Progress is stored keyed by full date (incl. year) and persists locally across
  app restarts; marks do not carry across calendar years.
- FR-7 (G4, M1) The bundled 365-day plan is verified against a second independent source via an
  automated test that gates release.
- FR-8 (G5) A home-screen widget displays today's three readings and opens the app when tapped.
- FR-9 (U6) Settings offers light / dark / system theme; the choice persists.
- FR-10 The app works fully offline for all planner functions; only tapping out to the text
  requires the network/browser.

**P1 — strongly desired in V1 if cheap; otherwise first follow-up**
- FR-11 Today screen visually indicates completion state (e.g. which readings are marked).
- FR-12 Date picker offers a quick "jump to today" / reset to current date.
- FR-13 Reading references render readably across all books, including multi-chapter spans
  (e.g. "Genesis 1–2") and book-name edge cases (Psalms, Johannine/Pauline epistles).
- FR-14 Implement the **Feb 29 no-readings rule** (resolved; see §11/§12): the plan covers the 365
  standard calendar days and has no Feb 29 entry. In leap years, when the user views/lands on Feb 29
  the app displays the date with the message **"No scheduled readings for Feb 29th"** — no reading
  rows, no mark controls, no progress tracked for that day. In non-leap years Feb 29 never occurs.

**P2 — explicitly out of V1 (tracked for later)**
- Streaks, stats, reminders/notifications (V2). In-app text, study notes, extra translations,
  audio, sharing, backup (V3).

## 9. Key user flows

1. **Open → Today → mark.** Launch app → Today screen shows the three readings → tap a
   reading's toggle to mark it (or tap "mark whole day done" to mark all three) → completion
   state updates and persists.
2. **Pick a date.** From Today, open the date picker → choose a month/day → readings for that
   date display → (optional) jump back to today.
3. **Tap → Blue Letter Bible.** Tap a reading → browser opens to
   `https://www.blueletterbible.org/kjv/<book>/<chapter>/` for the first chapter of that
   portion → user reads, returns to the app to mark it done.
4. **Add the widget.** From the launcher's widget tray, add the Daily Reading Planner widget → it
   shows today's three readings → tapping it opens the app on the Today screen.

## 10. UX & content principles

- **Today-first.** The single most important answer — "what do I read today?" — is the
  landing screen, reachable with zero setup.
- **Low-friction tracking.** Marking should never feel like data entry: per-reading toggles
  plus a whole-day shortcut; no per-chapter granularity.
- **Comfortable, general-audience type.** Default text size is comfortable (not tiny) for a
  broad age range. Material 3 styling, light/dark/system theme.
- **Simple over powerful.** Few screens, obvious actions, minimal settings. No configuration
  required to get value on day one.
- **Trustworthy data.** The references must be exactly right — accuracy is the product's
  credibility. Verified plan data is a release gate.
- **Respect the source.** References use familiar book naming; the app links to a respected,
  free KJV source (Blue Letter Bible) rather than reinventing the text in V1.

## 11. Assumptions & dependencies

- **Reading-plan accuracy.** We can extract the full 365-day table (February has 28 day-entries;
  no Feb 29 entry) and verify it against a second independent source (christadelphia.org Excel/PDF,
  antipas booklet PDF, pricejh full table PDF). The verified plan is the only V1 data asset and the
  project's core IP/risk.
- **Blue Letter Bible availability & URL format.** We depend on BLB remaining available and on
  the confirmed pattern `https://www.blueletterbible.org/kjv/<book>/<chapter>/` (book = 3-letter
  abbreviation, e.g. `gen`, `rev`). A correct book-name → abbreviation table for all 66 books
  is required; a few (Psalms, the Johannine/Pauline epistles) need explicit confirmation.
  *Risk:* if BLB changes its URL scheme or goes down, tap-to-text breaks; mitigation options
  (fallback source, or accelerating V3 in-app text) are noted but not in V1 scope.
- **Date-anchored model.** The schedule is purely a function of (month, day); no time zones,
  servers, or accounts are needed to determine "today's" readings — the device's local date
  suffices.
- **Offline-first.** All planner functionality works without network; only the outbound text
  link needs connectivity.
- **Platform.** Android, Kotlin/Compose/Material 3 stack as recorded in SPEC.md; `minSdk = 26`
  (resolved, §12), targetSdk/compileSdk = latest stable.

## 12. Open questions & candidate/future ideas

**Open questions (carried from SPEC.md / CLAUDE.md — need a human or teammate to resolve):**
1. **Blue Letter Bible book-abbreviation table.** Confirm the 3-letter abbreviation for every
   book; verify the non-obvious ones (Psalms, Johannine/Pauline epistles). *Engineering data
   task (no product sign-off needed); owner: data/QA.*

**Settled (do not reopen):**
- **Distribution (resolved).** **Play Store** (best update reach for shipping plan-data
  corrections). Drives signing/release setup in Sprint 8.
- **Analytics/telemetry (resolved).** **None in V1** — no analytics SDK and no networking
  dependency added. Revisit a privacy-respecting opt-in post-V1.
- **Feb 29 rule (resolved).** Feb 29 has **no scheduled readings**. The plan covers the 365
  standard calendar days and has no Feb 29 entry. In leap years the date is shown with the message
  **"No scheduled readings for Feb 29th"** (no readings, no mark controls, no progress tracked); in
  non-leap years Feb 29 never occurs. There is no fold/double-day logic (drives FR-14).
- **App name + package id (resolved).** App name = **"Daily Reading Planner"**; package id =
  **`com.jpillion.dailyreadingplanner`** (changeable before first Play publish).
- **Min SDK (resolved).** `minSdk = 26`; targetSdk/compileSdk = latest stable.
- KJV-only translation; in-app scripture text deferred to V3 (no scripture stored in V1, so the
  multi-translation schema question does not affect V1); separate standalone repo; date-anchored
  schedule keyed by (month, day) with progress keyed by full date.

**Candidate / future ideas (not committed; parked for V2/V3 consideration):**
- "Behind by N days" gentle catch-up affordance distinct from a streak (helps Ruth persona).
- Optional configurable text size / accessibility pass beyond the comfortable default.
- Share today's readings (text snippet/deep link) with others or an ecclesia group.
- Fallback/secondary text source if BLB is unavailable.
- Surfacing study notes (dailyreadings.org.uk style) — formally a V3 item, listed here as the
  natural next content step once in-app text exists.

*New ideas above are candidates only and must not be read as committed scope.*
