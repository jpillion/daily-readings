# Daily Readings — Product Spec & Build Plan

> Offline-first Android app for the Christadelphian "Bible Companion" daily reading plan.

## 1. Concept

Open the app, see today's three scripture portions, read the text inline, mark them done,
keep a streak. The Bible Companion plan is **date-anchored** (Jan 1 is always Genesis 1–2 /
Psalm 1–2 / Matthew 1–2), so every user worldwide is on the same readings each day.

**Working name:** *Daily Readings* (alt: *Bible Companion*). Final name TBD.

## 2. Background — the Bible Companion plan

A fixed calendar reading schedule devised by **Robert Roberts** (~1853, later refined) and
used worldwide by Christadelphians. Structure:

- **Three readings every day**, from three parallel streams through scripture:
  1. **Stream 1 — Law & History:** Genesis → Job
  2. **Stream 2 — Psalms & Prophecy:** Psalms → Malachi
  3. **Stream 3 — New Testament:** Matthew → Revelation
- ~3–4 chapters/day (~30 min). Over 365 days: **OT once, NT twice**.
- Date-anchored, not progress-anchored. Many ecclesias read the day's portions at the
  weekly Breaking of Bread.

### Reference sources
- Bible Companion — Wikipedia: https://en.wikipedia.org/wiki/Bible_Companion
- christadelphia.org — A Daily Bible Reading Plan: https://christadelphia.org/readplan.php
- The Bible Companion booklet (PDF): https://antipas.org/library/Robert%20Roberts/Booklets/The%20Bible%20Companion.pdf
- Full 12-month table (PDF): https://pricejh.com/readingplan/plans/roberts.pdf
- dailyreadings.org.uk (daily readings + study notes): https://dailyreadings.org.uk/
- Existing official-ish app (prior art): https://apps.apple.com/us/app/daily-bible-readings/id536687049

## 3. The data foundation (do this first)

Two bundled, read-only datasets — the project's real IP and risk:

| Dataset | Shape | Source | Notes |
|---|---|---|---|
| **Reading plan** | 366 days × 3 portions, each portion = one or more chapter spans | christadelphia.org Excel / antipas & pricejh PDFs | Hand-tuned table; extract **and verify** against a second source. Includes Feb 29. |
| **Bible text** | Books → chapters → verses | Public-domain **KJV** | KJV is public domain and traditional for this audience → safe default. ESV/NET/NIV need licensing — defer. |

Plan JSON sketch:

```json
{ "month": 1, "day": 1,
  "portions": [
    {"stream": 1, "refs": ["Genesis 1", "Genesis 2"]},
    {"stream": 2, "refs": ["Psalm 1", "Psalm 2"]},
    {"stream": 3, "refs": ["Matthew 1", "Matthew 2"]}
  ]}
```

## 4. Feature set

**MVP (v1)**
- **Today screen** — three portions with full scripture text, collapsible per portion.
- **Date navigation** — prev/next day, "jump to today," calendar picker for any day.
- **Mark as read** — per portion + auto "day complete" when all three done.
- **Offline KJV** bundled; no network required.
- **Settings** — theme (light/dark/system), font size, reminder time.

**v2**
- **Streaks & stats** — current/longest streak, % of year read, per-stream progress.
- **Daily reminder** notification at a chosen time.
- **Home-screen widget** (Glance) showing today's three references + read state.

**v3+**
- Study notes integration (dailyreadings.org.uk style).
- Additional translations (licensing permitting).
- Audio playback; verse sharing; cross-device backup.

## 5. Architecture

- **Kotlin + Jetpack Compose**, single-activity, Material 3.
- **Bible text:** bundled **read-only SQLite asset** accessed via Room (fast verse lookup, small APK).
- **User data:** **Room** — `reading_progress` (date, stream, readAt); derived streak queries.
- **Plan data:** bundled JSON parsed into memory / a Room table on first launch.
- **Prefs:** **DataStore** (theme, font size, reminder time).
- **DI:** Hilt. **Reminders:** AlarmManager + notification. **Widget:** Glance.
- **CI:** GitHub Actions (build + unit tests + Kover coverage).

## 6. Phased roadmap

- **Phase 0 — Data (blocking):** extract 366-day plan → JSON, verify vs. a second source,
  acquire & validate KJV dataset. Deliverable: two trusted bundled assets + verification test.
- **Phase 1 — MVP:** scaffold project; Today screen with text; date nav; mark-as-read;
  offline KJV; basic settings.
- **Phase 2 — Engagement:** streaks/stats; daily reminder; calendar view.
- **Phase 3 — Platform polish:** Glance widget; extra translations; study notes; audio.

## 7. Open decisions

1. **Final app name** and package id.
2. **Translation:** KJV-only for v1, or design schema for multi-translation from day one
   (recommended — cheap early, expensive to retrofit).
3. **Min SDK / target devices.**
4. **Distribution:** Play Store, or sideload/community distribution?
