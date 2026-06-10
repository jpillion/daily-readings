# Daily Readings — Product Spec & Build Plan

> Offline-first Android app for the Christadelphian "Bible Companion" daily reading plan.

## 1. Concept

A **digital reading planner** for the Bible Companion. Open the app, see today's three
scripture readings, mark them done, and tap any reading to open its chapter on Blue Letter
Bible (KJV) in the browser. The plan is **date-anchored** (Jan 1 is always Genesis 1–2 /
Psalm 1–2 / Matthew 1–2), so every user worldwide is on the same readings each day.

V1 is a planner/tracker — it shows *what* to read and tracks *whether* you read it; it does
not bundle or render scripture text in-app (that's V3). The text is one tap away via Blue
Letter Bible.

**Working name:** *Daily Readings* (alt: *Bible Companion*). Final name TBD.

## 2. Background — the Bible Companion plan

A fixed calendar reading schedule devised by **Robert Roberts** (~1853, later refined) and
used worldwide by Christadelphians. Structure:

- **Three readings every day**, from three parallel streams through scripture:
  1. **Stream 1 — Law & History:** Genesis → Job
  2. **Stream 2 — Psalms & Prophecy:** Psalms → Malachi
  3. **Stream 3 — New Testament:** Matthew → Revelation
- ~3–4 chapters/day (~30 min). Over the year: **OT once, NT twice**.
- Date-anchored, not progress-anchored. Many ecclesias read the day's portions at the
  weekly Breaking of Bread.
- The bundled table is **366 days** (includes Feb 29); see §3 for the common-year rule.

### Reference sources
- Bible Companion — Wikipedia: https://en.wikipedia.org/wiki/Bible_Companion
- christadelphia.org — A Daily Bible Reading Plan: https://christadelphia.org/readplan.php
- The Bible Companion booklet (PDF): https://antipas.org/library/Robert%20Roberts/Booklets/The%20Bible%20Companion.pdf
- Full 12-month table (PDF): https://pricejh.com/readingplan/plans/roberts.pdf
- dailyreadings.org.uk (daily readings + study notes): https://dailyreadings.org.uk/
- Existing official-ish app (prior art): https://apps.apple.com/us/app/daily-bible-readings/id536687049

## 3. The data foundation (do this first)

V1 ships **one** bundled, read-only dataset — the reading plan. No scripture text is bundled
(deferred to V3); the text is reached via outbound links to Blue Letter Bible.

| Dataset | Shape | Source | Notes |
|---|---|---|---|
| **Reading plan** | 366 days × 3 portions, each portion = one or more chapter spans | christadelphia.org Excel / antipas & pricejh PDFs | Hand-tuned table; extract **and verify** against a second source. Includes Feb 29. The project's real IP and the only V1 data asset. |

Plan JSON sketch:

```json
{ "month": 1, "day": 1,
  "portions": [
    {"stream": 1, "refs": ["Genesis 1", "Genesis 2"]},
    {"stream": 2, "refs": ["Psalm 1", "Psalm 2"]},
    {"stream": 3, "refs": ["Matthew 1", "Matthew 2"]}
  ]}
```

### Two data concerns, kept separate
- **Schedule** — the plan above, keyed by **(month, day)**. Year-agnostic; the schedule
  repeats every calendar year. This is the bundled JSON.
- **Progress** — "marked read" records keyed by the **full date (including year)**, so a
  reading marked done on 1 Jan 2026 does not show as done on 1 Jan 2027. The schedule repeats;
  your history does not.

### Reference → Blue Letter Bible (KJV)
Tapping a reading opens that chapter on Blue Letter Bible. Confirmed URL pattern:

```
https://www.blueletterbible.org/kjv/<book>/<chapter>/    e.g. Genesis 1 → /kjv/gen/1/
```

`<book>` is a 3-letter abbreviation (`gen`, `exo`, … `rev`). The only "reference resolution"
V1 needs is a canonical **book-name → BLB abbreviation** table; no full-text dataset.

### Feb 29 in common (non-leap) years
The table includes a Feb 29 entry. In non-leap years the app must still account for those
readings — define the display rule (the Bible Companion booklet folds the Feb 29 portion into
late February so the plan still completes). **Resolve this rule before finalizing the JSON
format.**

## 4. Feature set

**V1 — digital reading planner (no in-app scripture text)**
- **Today screen (primary)** — open the app, see today's three readings as references
  (e.g. "Genesis 1–2 · Psalm 1–2 · Matthew 1–2").
- **Mark as read** — per reading (three toggles/day, *not* per-chapter), plus a one-tap
  **"mark whole day done"** that toggles all three at once.
- **Tap a reading → Blue Letter Bible (KJV)** for that chapter, opened in the browser.
- **Date picker** — calendar picker, month/day only (year irrelevant), to view any date's
  readings.
- **Android home-screen widget** — shows today's three readings; tapping it opens the app.
- **Settings** — minimal: light/dark/system theme.
- **Audience/UI** — general audience; comfortable (not tiny) default text size; simple,
  usable interface. Not specifically targeting low-vision/aging users.

**V2 — motivation**
- **Streaks & stats** — current/longest streak, % of year read, per-stream progress.
- **Reminders** for missed readings (notifications) and other motivational features.

**V3 — text & beyond**
- **In-app scripture text** — bundled, read-only **KJV** (public domain; ESV/NET/NIV need
  licensing). This is where the offline Bible dataset work lives.
- Study notes integration (dailyreadings.org.uk style); additional translations
  (licensing permitting); audio playback; verse sharing; cross-device backup.

## 5. Architecture

**V1 (planner only):**
- **Kotlin + Jetpack Compose**, single-activity, Material 3.
- **Plan data:** bundled JSON, parsed into memory (or a small Room table) on first launch;
  looked up by (month, day).
- **User data:** small store for progress — `reading_progress` (date incl. year, stream,
  readAt). DataStore or a tiny Room table is sufficient at V1 scope.
- **Prefs:** **DataStore** (theme).
- **Widget:** **Glance**, showing today's three readings; tap → opens the app.
- **Outbound links:** Blue Letter Bible KJV chapter URLs (see §3).
- **DI:** Hilt. **CI:** GitHub Actions (build + unit tests + Kover coverage).

**Deferred to later versions:** AlarmManager + notification reminders (V2); bundled read-only
**SQLite KJV asset via Room** for in-app text and the richer `reading_progress` /
streak-query schema (V2/V3).

## 6. Phased roadmap

- **Phase 0 — Data (blocking):** extract the 366-day plan → JSON and **verify vs. a second
  source**. Deliverable: one trusted bundled asset + a verification test. (KJV-text dataset is
  no longer part of Phase 0 — it moves to the V3 work.)
- **Phase 1 — V1 planner:** scaffold project; Today screen (references); per-reading +
  whole-day mark-as-read; tap-out to Blue Letter Bible; date picker; theme setting; **Glance
  widget** (today's readings, tap → app).
- **Phase 2 — Engagement:** streaks/stats; reminders for missed readings; other motivational
  features.
- **Phase 3 — Text:** acquire & validate KJV dataset; in-app scripture text; extra
  translations; study notes; audio.

## 7. Open decisions

1. **Final app name** and package id.
2. **Feb 29 common-year rule** — confirm how the booklet handles the leap-day portion in
   non-leap years (§3). Must be settled before the plan JSON format is final.
3. **Blue Letter Bible book-abbreviation table** — confirm the 3-letter abbreviation for every
   book (most are obvious; a few — Psalms, the Johannine/Pauline epistles — need checking).
4. **Min SDK / target devices.**
5. **Distribution:** Play Store, or sideload/community distribution?

*Resolved:* translation is **KJV-only**, and in-app text is **deferred to V3** — so the
multi-translation schema question does not affect V1 (no scripture is stored in V1).
