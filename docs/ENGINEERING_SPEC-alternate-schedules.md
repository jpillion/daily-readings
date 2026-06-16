# Daily Reading Planner — Engineering Spec: Alternate Reading Schedules (multi-plan support)

> **Owner:** Diego (Tech Lead / Android Architect) · **Status:** Draft for build · **Last updated:** 2026-06-16
> **Companion docs:** [features/alternate-reading-schedules.md](features/alternate-reading-schedules.md)
> (Maya's PRD + the **owner decisions resolved 2026-06-16** — this spec builds to those, authoritative),
> [ENGINEERING_SPEC.md](ENGINEERING_SPEC.md) / [ENGINEERING_SPEC-v3.md](ENGINEERING_SPEC-v3.md)
> (the V1/V2/V3 specs this continues — same voice, same `D-*` convention),
> [data/README.md](data/README.md) (the Sprint-1 trusted-data gate discipline this spec generalizes),
> [CLAUDE.md](../CLAUDE.md) (session handoff).
>
> This doc owns **how** we build multi-plan support: the plan data model + registry, de-baking the
> 3-stream assumption, the per-plan progress Room migration, the plan selector, the per-plan trusted-data
> gate, and a recommended sprint breakdown. Every product decision it builds on is settled in the PRD's
> owner-decisions block; this spec turns them into buildable interfaces, schema DDL, and decision records
> (`D-ALT-*`). Where a *product/UI* question is still open it is flagged **[OQ-n — not blocking the data
> foundation]** and never sits on the critical path.
>
> **Naming:** the existing user-facing word is "plan" (the Bible Companion *plan*). I use **plan** for the
> whole reading schedule and keep **stream** for a within-a-day parallel reading track, exactly as the code
> does today (`enum Stream`). M'Cheyne's two tracks are two *streams*; chronological is one *stream*.

---

## 1. Purpose & scope

The app is today **"THE Bible Companion app"** — one plan, of one fixed shape, baked into every layer
(PRD §0). This spec graduates it to **"a reading-plan app whose flagship plan is the Bible Companion"**:
the Bible Companion stays the pre-selected, zero-setup, byte-for-byte-unchanged default, and the app
*gains* a curated set of additional plans behind a selector.

The owner decisions (PRD, resolved 2026-06-16) fix the shape of what we build, so this is buildable now:

- **CURATED set, Bible Companion flagship** (OQ-2). A small hand-picked set, not a general plan store.
- **PER-PLAN progress, kept separately** (OQ-3). Each plan keeps its own marks; switching back restores
  where you were; stats/streaks/strips/widget become per-plan.
- **Date-anchored, 365-day plans only; M'Cheyne first, then a named chronological plan** (OQ-4 + OQ-1).
  Progress-anchored / start-relative / non-365-day plans are explicitly deferred.

This spec covers, at buildable detail:

- the multi-plan data model: multiple bundled plan assets + a plan registry/manifest; a plan that
  **declares** its streams (variable count + titles), day count, and anchoring (§3);
- **de-baking the 3-stream assumption** — the core code work — with every concrete touch point I found
  in the live tree and how each generalizes (§4);
- the **per-plan progress Room migration** (the high-risk item): `(plan_id, dateEpochDay, stream)`, the
  zero-loss migration of existing Bible-Companion marks, exported-schema rigor + a migration test (§5);
- the plan selector + the `selected_plan` setting that drives the whole app (§6);
- the **per-plan trusted-data gate** — each plan its own `*VerificationTest`, second source, reconciliation
  log; the chronological ordering as the heaviest lift (§7);
- stream-identity generalization — plan-supplied titles, the enum retired as the title source (§8);
- decisions (`D-ALT-*`) and a recommended A–E sprint breakdown (§9–§10);
- what still needs M'Cheyne data research before its asset can be built, and the owner calls (OQ-5/OQ-7)
  that gate later phases but not this spec (§11).

**Hard invariants carried unchanged from V1/V2/V3:** no networking, no analytics, every plan is a bundled
asset; KJV-anchored, respectful, non-gamified tone for every plan and every generalized surface; the
`DayCompletionClassifier` stays the *single* completion seam (R-STREAK-5 / D-S11-1 / D-S17-2) — generality
flows *through* it, it is never forked per plan. **A user who never opens the selector gets the current app,
unchanged.**

### 1.1 The non-negotiable invariant — Bible Companion parity

**The Bible Companion's behavior must be byte-for-byte identical for a user who never switches plans.**
This is the acceptance bar the whole design is built to, and it is testable: the existing test suite
(the 11-test plan gate, every day/stats/widget/reminder pin) must pass **unchanged** with the active plan
defaulted to `bible_companion`. Where this spec changes a signature (e.g. `ProgressRepository` gains a
`planId`), the change is additive with the Bible-Companion id supplied by default so the existing pins keep
their meaning. Any test that goes red on a no-op-for-the-default-user change is a design defect, not a test
to update.

---

## 2. Architecture overview

The pattern is unchanged from the V1 spec §2 and the V3 spec §2: single-activity Compose, MVVM + UDF,
repository pattern, Hilt DI, domain free of Android framework types. Multi-plan adds **no new layer** — it
adds a **dimension** (the active plan) to the existing schedule/progress/stats data flow, and it
generalizes a handful of hard-coded constants to plan-declared values. There is one genuinely new piece
(the plan **registry**) and one genuinely hard piece (the progress **migration**); everything else is
"thread the active plan id through, and read `N` from the plan instead of the constant `3`."

```
                  ┌──────────────────────────────────────────────────────────────┐
                  │                       UI (Compose)                            │
                  │  Day screen: N reading cards  ·  Stats: N strips + N rows      │
                  │  Settings: plan selector  ·  Widget: N rows (tier-aware)       │
                  └───────────────┬──────────────────────────────────────────────┘
                                  │ state (StateFlow) ▲ events
                                  ▼
                  ┌──────────────────────────────────────────────────────────────┐
                  │  Domain — generality flows through the ONE classifier seam     │
                  │  GetDayReadings · GetReadingStats · GetYearStrips · classifier │
                  │  every "N streams" comes from the ACTIVE PLAN, not a constant   │
                  └───────────────┬─────────────────────────────┬────────────────┘
                                  │                              │
                                  ▼                              ▼
              ┌────────────────────────────────┐  ┌────────────────────────────────────┐
              │  ActivePlanRepository (NEW)     │  │  ProgressRepository                  │
              │  selected_plan  +  PlanRegistry │  │  now keyed by (plan_id, date, stream)│
              └──────────────┬─────────────────┘  └───────────────┬─────────────────────┘
                             ▼                                     ▼
              ┌────────────────────────────────┐  ┌────────────────────────────────────┐
              │  assets/plans/<id>/plan.json    │  │  Room: progress.db (v1 → v2 MIGRATION)│
              │  + a plan MANIFEST (descriptor) │  │  reading_progress + plan_id column   │
              └────────────────────────────────┘  └────────────────────────────────────┘
```

The dashed-line equivalent of V3's `BibleTextSource` seam here is **the plan descriptor**: every surface
reads the active plan's `streams` (count + titles) and `dayCount` from one place, never a hard-coded `3` /
`1095` / `Stream` enum value. That seam is what makes "render the active plan's actual shape" a data read,
not a code fork.

---

## 3. The multi-plan data model (§1 of the prompt)

### 3.1 Today's single-plan shape (what we generalize)

The plan is **one asset** `app/src/main/assets/reading_plan.json` (`schemaVersion: 2`), parsed once into an
immutable `Map<ReadingDate, List<Portion>>` by `ReadingPlanAssetLoader`, memoized by
`ReadingPlanRepositoryImpl`. The loader **hard-asserts the Bible Companion's shape**:

```kotlin
check(dto.days.size == EXPECTED_DAYS)              // EXPECTED_DAYS = 365
check(day.portions.map { it.stream } == listOf(1, 2, 3))   // exactly streams 1,2,3, every day
Stream.fromNumber(portion.stream)                   // the fixed 3-value enum
```

Stream titles come from `ReadingFormatter.streamTitle(stream)` — a hard-coded `when` over the enum. Every
one of these is a place a second plan of a different shape would fail to load. The generalization is to make
the plan **declare its own shape** and the loader **validate against the plan's declaration**, not a constant.

### 3.2 The plan asset layout — one asset set per plan, a registry on top

**D-ALT-1 — one asset directory per plan, plus a single bundled plan registry.** Each plan gets its own
asset directory under `assets/plans/<plan_id>/`; a single top-level `assets/plans/registry.json` lists the
curated set and the default. This mirrors the V3 `bible/` discipline (a self-contained asset behind a seam)
and keeps each plan's data project (asset + second-source fixture + reconciliation log + gate) cleanly
isolated.

```
app/src/main/assets/plans/
├── registry.json                       # the curated set + default (D-ALT-1)
├── bible_companion/
│   └── plan.json                       # the existing reading_plan.json, MOVED here, + a descriptor head
├── mcheyne/
│   └── plan.json                       # Phase 1 (M'Cheyne) — the structural-cousin add
└── chronological/                      # Phase 2 — named publisher TBD at sourcing time
    └── plan.json
```

> **Migration of the existing asset path.** `assets/reading_plan.json` moves to
> `assets/plans/bible_companion/plan.json`. The `PlanJsonSource` (a `fun interface` provided in
> `DataModule`, reading `context.assets.open(PLAN_ASSET)`) becomes plan-id-parameterized (§3.5). The
> Sprint-1 second-source fixture `app/src/test/resources/reading_plan_verify.json` and the gate likewise
> move under a per-plan path (§7). **The asset *bytes* are unchanged** — only the path moves — so the
> data gate stays green on identical content (the move is a path edit, not a data edit).

**`registry.json`** is the only file the app reads to *enumerate* plans. It is intentionally thin — the
heavy descriptor lives in each plan's own `plan.json` head (§3.3) so a plan is self-describing and a
registry typo can't contradict the asset:

```json
{
  "registryVersion": 1,
  "defaultPlanId": "bible_companion",
  "plans": [
    { "id": "bible_companion", "asset": "plans/bible_companion/plan.json" },
    { "id": "mcheyne",         "asset": "plans/mcheyne/plan.json" }
  ]
}
```

- `defaultPlanId` is the flagship and the cold-start default (FR-ALT-2). It is a **constant of the build**,
  not a user choice — the selector's default selection reads it, but the app never ships without it being
  `bible_companion`.
- A plan present in `registry.json` but failing its gate (§7) is a release blocker — a plan only enters the
  registry once its asset and gate ship together. **There is no runtime "plan not found" path for a
  registry entry; a registry entry is a build guarantee.** (Defensive: an asset that fails to parse at
  runtime falls back to the default plan and surfaces the existing retryable-error state — same posture as
  the current single-asset load-failure handling, never a crash.)

### 3.3 The generalized plan schema — a plan declares its shape

**D-ALT-2 — `schemaVersion: 3`; the plan declares `streams[]`, `dayCount`, and `anchoring`; the Bible
Companion is just the first plan written in the new schema.** The day/portion/ref body is **unchanged** from
schema v2 (this is the load-bearing back-compat point — see D-ALT-3); a plan-descriptor head is added.

```json
{
  "schemaVersion": 3,
  "planId": "bible_companion",
  "name": "Bible Companion",
  "source": "christadelphia.org chart.pdf (Robert Roberts); verified vs antipas.org booklet",
  "anchoring": "DATE",
  "dayCount": 365,
  "streams": [
    { "number": 1, "title": "Law & History" },
    { "number": 2, "title": "Psalms & Prophecy" },
    { "number": 3, "title": "New Testament" }
  ],
  "days": [
    { "month": 1, "day": 1, "portions": [
      { "stream": 1, "refs": [ {"book": "Genesis", "chapter": 1}, {"book": "Genesis", "chapter": 2} ] },
      { "stream": 2, "refs": [ {"book": "Psalms",  "chapter": 1}, {"book": "Psalms",  "chapter": 2} ] },
      { "stream": 3, "refs": [ {"book": "Matthew", "chapter": 1}, {"book": "Matthew", "chapter": 2} ] }
    ]}
    // … dayCount entries; February = 28 day-entries, no Feb 29 (D1, all date-anchored plans)
  ]
}
```

New head fields (the rest of the body — `days`/`portions`/`refs`/the optional Psalm-119 verse window — is
identical to v2):

- **`planId`** (string) — must equal the registry `id` and the asset directory name; the loader asserts
  all three agree (anti-drift, mirrors D-S9-1).
- **`name`** (string) — the user-visible plan name (selector, "active plan" label). English-only like book
  names (V1/V3 precedent); revisit at localization.
- **`anchoring`** (enum, `"DATE"` only in this cut) — every shipped plan is `DATE`. The field exists so a
  later progress-anchored plan (deferred, OQ-4) is a schema-recognized value, not a schema break. **The
  loader rejects any value other than `"DATE"`** in this cut (a clean fail, never a silent
  mis-interpretation — the V3 resolver's clean-fail discipline).
- **`dayCount`** (int, `365` only in this cut) — the plan's scheduled-day count; replaces the hard-coded
  `EXPECTED_DAYS = 365`. The loader asserts `days.size == dayCount` and (for `dayCount == 365` + `DATE`)
  the Feb-29-absent / 28-Feb-entries invariant. **The loader rejects `dayCount != 365`** in this cut
  (non-365-day lengths are deferred, §11; the field exists so they don't break the schema later).
- **`streams[]`** (array of `{number, title}`) — **the core generalization.** Declares N streams, their
  canonical `number` (the progress/portion key), and their display `title`. Replaces the fixed `Stream`
  enum and `ReadingFormatter.streamTitle`. The loader asserts `streams[].number == 1..N` contiguous and that
  every day's `portions[].stream` set equals exactly `streams[].number` (the generalized form of the
  current `== listOf(1,2,3)` check). N is whatever the plan declares — M'Cheyne 2 (or 4), chronological 1.

### 3.4 Back-compat — the Bible Companion is "just the first plan"

**D-ALT-3 — the schema bump is additive; the v2 body is a strict subset of v3.** The day/portion/ref body
does not change at all; v3 only *adds* a descriptor head. The Bible Companion's existing 365-day body is
re-used verbatim — the migration of its asset is "add the head, move the file," not "rewrite the data," so
its byte-level reading data (and therefore its verification gate) is untouched. This is what makes
"the Bible Companion is just the first plan" literally true rather than a slogan:

- The `RefDto` (`{book, chapter, verseStart?, verseEnd?}`) is unchanged → the Psalm-119 windows, the
  two-book Jun 19/Dec 19 portion, and `PortionVerseBridge` (the V3 join) all work for every plan for free.
- `SUPPORTED_SCHEMA_VERSION` becomes `3`; the loader **does not** accept v2 at runtime (we re-author the
  bundled Bible Companion asset as v3 in the same sprint — there is no on-device v2 asset, the asset is
  bundled, so there is no "old asset in the wild" to read). The *body* compatibility is what matters, and
  it is total.

### 3.5 Loader / registry / repository generalization (the data-layer mechanics)

The concrete `data/plan/` changes:

- **`PlanDescriptor`** (new domain-ish model in `data/plan` or `domain/model`): `(planId, name, anchoring,
  dayCount, streams: List<StreamDescriptor>)`; `StreamDescriptor(number, title)`. Parsed from the plan
  head. This is the **stream-identity seam** — every surface that needs a stream title reads it from here
  (§8), not the enum.
- **`PlanDto`** gains the head fields (`planId`, `name`, `anchoring`, `dayCount`, `streams`); `PortionDto`
  / `RefDto` unchanged.
- **`PlanRegistry`** (new): reads `registry.json`, exposes `defaultPlanId` and the available `(id, asset)`
  list. Bundled, parsed once, memoized.
- **`PlanJsonSource`** becomes `PlanAssetSource { fun readText(assetPath: String): String }` (parameterized
  by the registry's `asset` path) — the existing single-asset reader generalized.
- **`ReadingPlanAssetLoader.load(planId)`** parameterized by plan id; validation reads `dayCount` /
  `streams` from the descriptor instead of the `365` / `listOf(1,2,3)` constants. The Bible-Companion path
  produces the identical map it does today.
- **`ReadingPlanRepository`** gains a plan dimension: `suspend fun portionsFor(planId, date): List<Portion>`
  and `suspend fun descriptor(planId): PlanDescriptor`. The memoization cache becomes a per-plan map (load
  a plan's asset on first touch, cache it; the default plan is touched at startup as today). A plan's asset
  is only parsed if/when it becomes active — switching to M'Cheyne is the first time its asset is read, so
  cold-start cost for the default user is unchanged (M2).
- **`ActivePlanRepository`** (new, §6): the active `planId` (`selected_plan` DataStore key) + the descriptor
  for it, as a `Flow`. Most use cases inject *this* (the active plan) rather than taking a `planId` param,
  so the UI layer rarely names a plan id explicitly — it just reads "the active plan."

> **Why a registry rather than "scan the assets dir."** Android asset enumeration is awkward and
> order-unstable, and we want the curated set, the default, and the per-plan asset path to be an explicit,
> reviewable, gate-able artifact — exactly the trustworthy-data posture the rest of the project holds. A
> registry file is one more thing a PR reviewer and the gate can see.

---

## 4. De-baking the 3-stream assumption (§2 of the prompt) — the core code work

This is the heart of the feature. I grepped the live tree for every hard-coded `3` / `1095` / `Stream`-enum
/ "Bible Companion" coupling. Here is the **complete enumerated set of touch points** and how each
generalizes. The organizing principle (D-ALT-4): **N comes from the active plan's `PlanDescriptor`
(`streams.size`), and stream titles come from the descriptor's `StreamDescriptor.title` — never from a
constant or the enum.** The single completion seam (`DayCompletionClassifier`) is preserved; only its
constant becomes a parameter.

### 4.1 `Stream` — the fixed 3-value enum (`domain/model/Stream.kt`)

```kotlin
enum class Stream(val number: Int) { LAW_AND_HISTORY(1), PSALMS_AND_PROPHECY(2), NEW_TESTAMENT(3) }
```

**D-ALT-5 — retire `Stream`-as-identity; keep a stream as a plain `Int` "stream number" (1..N) carried by
the active plan's descriptor.** The Bible-Companion-specific *names* (`LAW_AND_HISTORY` …) are exactly the
identity that does not generalize. The generalization:

- A within-day track is identified by its **`streamNumber: Int`** (1..N), which is already the persisted
  key (`Stream.number`, the progress `stream` column, the JSON `stream` field). The *display title* comes
  from the active plan's `StreamDescriptor.title`.
- `Portion.stream: Stream` becomes `Portion.streamNumber: Int` (or a thin value class `StreamNumber(Int)`
  for type safety — recommended, costs nothing, keeps `Map<StreamNumber, …>` legible). The loader stops
  calling `Stream.fromNumber`; it carries the int straight through (validated against the descriptor's
  `streams[].number`).
- **`Stream` the enum is deleted as a domain type.** Every `Stream.entries` iteration (stats, strips,
  whole-day mark) becomes "iterate the active plan's `streams`" or "iterate `1..N`". Every
  `Stream.fromNumber` call site (progress repo mapping) drops — the int is already the key.

> This is the single most invasive change (it touches the progress repo, three use cases, two UI surfaces,
> the widget, and the loader), but it is **mechanical and type-driven**: delete the enum, follow the
> compiler. The risk is not correctness (the compiler finds every site) — it is the surface area, which is
> why §10 scopes it to its own sprint (Phase C).

### 4.2 `DayCompletionClassifier.STREAM_COUNT = 3` — THE seam (`domain/DayCompletionClassifier.kt`)

```kotlin
readCount >= STREAM_COUNT -> DayCompletion.COMPLETE      // STREAM_COUNT = 3
```

This is the **one seam that must not fork** (R-STREAK-5, D-S11-1, D-S17-2). The picker dots, the streak
walk, the stats, the year strips, the reminder "skip when complete," and the persistent notification all
flow through `classify(...)`. **D-ALT-6 — `STREAM_COUNT` becomes a per-call `streamCount: Int` parameter
sourced from the active plan, NOT a per-plan classifier subclass.** Generality flows *through* the one
predicate:

```kotlin
fun classify(date, readCount, streamCount: Int, today, trackingStart): DayCompletion =
    when {
        resolver.resolve(date) is NoScheduledReadings -> NONE
        readCount >= streamCount -> COMPLETE           // was: readCount >= STREAM_COUNT
        trackingStart != null && date.isBefore(trackingStart) -> NONE
        date.isBefore(today) -> MISSED
        else -> NONE
    }
```

Every caller already has the active plan in scope (they read progress for the active plan), so they pass
`descriptor.streams.size`. **The truth-table ORDER is untouched** — only the completion threshold becomes a
parameter. The classifier stays the literal single source of truth; there is no per-plan branch anywhere
else. (The mutation tests that pin the truth-table order carry over verbatim; a new mutation — "ignore the
passed `streamCount`, hard-code 3" — should be added and killed by an M'Cheyne 2-stream completion test.)

### 4.3 Stats denominators — `1,095` and `365` (`domain/model/ReadingStats.kt`)

```kotlin
const val YEAR_TOTAL_READINGS = 1_095   // 365 × 3
const val STREAM_TOTAL_DAYS = 365
```

**D-ALT-7 — denominators become `dayCount × streamCount` and `dayCount`, computed from the active plan's
descriptor; the constants are deleted.** `ReadingStats` carries `yearTotalReadings` and `streamTotalDays`
as fields populated by `GetReadingStatsUseCase` from the descriptor (`dayCount * streams.size` and
`dayCount`). For the Bible Companion these are `1095` and `365` exactly, so the existing stats pins hold;
for chronological (1 stream) the year total is `365`, for M'Cheyne (2) it is `730`. The **floor-rounding**
percent rule (D-S11-4 — 100% only at completion) is unchanged; it just divides by the plan's real total.
`streamReadCounts: Map<Stream, Int>` becomes `Map<Int, Int>` keyed by stream number.

### 4.4 The three stat use cases — `Stream.entries` iteration

`GetReadingStatsUseCase`, `GetYearStripsUseCase`, `GetMonthCompletionUseCase` each iterate
`Stream.entries.associateWith { … }` and pass `DayCompletionClassifier.STREAM_COUNT`. **D-ALT-8 — each use
case takes the active plan's descriptor (via `ActivePlanRepository`) and iterates its `streams`, passing
`streams.size` to the classifier and scoping every progress query by the active `plan_id`** (§5). Concretely:

- **`GetReadingStatsUseCase`** — `streamReadCounts` over the descriptor's streams; `yearReadCount` sums the
  active plan's per-stream counts; the streak walk passes `streams.size` to `classify`. The walk logic
  (D-S11-2 forward pass) is otherwise unchanged.
- **`GetYearStripsUseCase`** — builds one strip per descriptor stream (not three); the synthetic-count
  trick (`streamCount if marked else 0`, D-S17-2) passes `streams.size` as the threshold so a single
  marked stream still reads COMPLETE-for-that-strip. `YearStrips.dayStates: Map<Int, List<StripDayState>>`
  keyed by stream number.
- **`GetMonthCompletionUseCase`** — passes `streams.size` to `classify`; otherwise unchanged. This is the
  picker-dot seam, so it inherits N-correctness for free.

### 4.5 The day screen — N reading cards (`ui/day/DayContent.kt`, `DayReadingsViewModel.kt`)

`DayContent` already does `state.readings.forEach { reading -> ReadingCard(...) }` — it is **loop-flexible
at the structural level** (renders whatever count the state carries). The couplings to break:

- The card's title is `ReadingFormatter.streamTitle(portion.stream)` (the enum `when`) → reads the active
  plan's `StreamDescriptor.title` for the portion's `streamNumber` (§8).
- `GetDayReadingsUseCase` builds `dayComplete = portions.all { it.stream in readStreams }` — already
  count-agnostic (it checks *all* portions, however many). It scopes progress by the active plan (§5) and
  otherwise needs no change. A 1-stream day's "all readings done" is one checkbox; a 4-stream day's is
  four — the existing `all { }` is already right.
- `MarkWholeDayUseCase` / `setWholeDay` mark "all streams for the date" — currently `Stream.entries.map`.
  Becomes "all `1..N` for the active plan." The whole-day seam survives (the widget still uses it); the
  UI button was removed in Sprint H, so this is the widget/whole-day-mark path only.

**Real UI work to flag (not a flag-flip):** a 1-stream plan shows a single card — the screen reclaims the
space the other two cards used. Priya owns whether that's centered, larger type, or just one card at the
top (OQ-7 / OQ-6). A 4-stream M'Cheyne plan shows four cards — the existing one-screen-fit budget (Sprints
16/18/20, re-confirmed with the V3 bottom bar) must be **re-confirmed at four cards** on the device pass.
The layout *mechanism* is sound (a `forEach`); the *visual* at N≠3 is a device-pass + Priya item.

### 4.6 The stats screen — N strips + N per-stream rows (`ui/stats/StatsContent.kt`)

`StatsContent` iterates `Stream.entries.forEach` for both the stacked year heat-strip and the per-stream
rows, and reads `ReadingStats.YEAR_TOTAL_READINGS` / `STREAM_TOTAL_DAYS`. **D-ALT-9 — the stats surface
iterates the active plan's streams and reads denominators from `ReadingStats`' now-instance fields (§4.3).**
The strip color seam (`StripColors`, the legend, the no-guilt copy ban incl. contentDescriptions D-S17-3 /
D-S20-1) is unchanged and applies per stream regardless of N. **Real UI work to flag:**

- The stacked "year" heat-strip is currently `Stream.entries.flatMap { dayStates }` (three rows stacked
  into one 3×6dp band). At N=1 it's one row; at N=4 it's four. The band's height and the panel's
  one-screen-fit budget (the S18/S20 ~290–346dp envelope under the 45% cap) are **tuned to three strips** —
  the strip-height/legend/a11y-summary work is a real generalization item (PRD §4a called this out), and is
  Priya's design + a device-pass for the look. The *data* (N strips of correct color) is JVM-provable in
  the gate; the *fit at N=4* is not.
- The per-stream rows' spoken summaries (`strip_stream_summary`, "Law & History: 120 read…") read the
  stream title from the descriptor; the no-"missed"-in-speech rule is unchanged.

### 4.7 The widget — N rows, tier-aware (`widget/WidgetContent.kt`)

The widget already does `day.readings.forEach { reading -> ReadingRow(...) }` — **loop-flexible**. The
couplings: `ReadingRow` reads `ReadingFormatter.streamTitle(...)` (→ descriptor title, §8); the
contentDescription likewise. **The real work is the responsive tier policy** (PRD §4a's flagged suspect):

- The S9/S14 size tiers (`TINY`/`SMALL`/`MEDIUM`/`LARGE`, the `RESPONSIVE_SIZES` size points, the
  `defaultWeight()` equal-share row distribution) were tuned to fit **exactly three rows** at each size.
  The rows distribute by weight, so N rows already *render* at any N — but a 4-row widget at TINY (1x1,
  57×48dp) crushes four rows where three were already tight, and a 1-row widget at LARGE wastes most of the
  card. **D-ALT-10 — the widget keeps its single-widget `SizeMode.Responsive` design but the tier→content
  policy becomes row-count-aware:** the chooser (`layoutFor` / `showsHeader` / `scaleFor`) factors in the
  active plan's `streamCount` so a high-N plan degrades earlier to abbreviated references and drops the
  stream title sooner, and a low-N plan reclaims the room (bigger type, the header survives at smaller
  sizes). The Feb-29/error states, the single tap target, the read/unread marks, the system-theme follow,
  and the full-name a11y all hold at every N (the S9 invariants generalize). This is genuine layout work,
  device-pass-heavy, and is scoped into the C/E sprints.
- The widget reads the **active plan** via its existing Hilt `@EntryPoint` (the S7 pattern) — it must read
  `selected_plan` + the descriptor the same way the app does, so the launcher widget follows a plan switch.
  This is a real wiring item: the widget's `GetDayReadingsUseCase` call must be active-plan-scoped, and a
  plan switch must trigger the existing `WidgetRefresher` so the widget snaps to the new plan's readings.

### 4.8 Reminder + persistent-notification copy (`reminders/`)

`ReminderNotifier.reminderBody(portions)` and `PersistentNotifier` already `joinToString` over the day's
`portions` (whatever the count) via `ReadingFormatter.format` — **already count-agnostic in code.** The only
coupling is the **doc/string copy** that says "the day's three references." **D-ALT-11 — the reminder and
persistent-tray bodies are already N-correct (they join the active plan's portions); only the static copy
and the dynamic active-plan scoping need attention.** The body must be built from the *active plan's*
`GetDayReadingsUseCase` (the S22 "decided at fire time" seam, now active-plan-scoped); "skip when complete"
(R-REM-4) uses the per-plan completion (the classifier with `streams.size`). No layout work; this is a
scoping + copy item (the strings already render whatever count exists).

### 4.9 Summary table — every touch point

| # | Touch point | File | Today | Generalizes to | Real work? |
|---|---|---|---|---|---|
| 1 | `Stream` enum (3 named values) | `domain/model/Stream.kt` | fixed 3 | retired; stream = `Int` 1..N + descriptor title | **invasive (compiler-driven)** |
| 2 | `STREAM_COUNT = 3` | `DayCompletionClassifier.kt` | const 3 | `streamCount: Int` param from active plan | the one seam — param, no fork |
| 3 | `YEAR_TOTAL_READINGS=1095`, `STREAM_TOTAL_DAYS=365` | `ReadingStats.kt` | consts | instance fields `dayCount × N`, `dayCount` | mechanical |
| 4 | `Stream.entries` ×3 + `STREAM_COUNT` | `GetReadingStats/YearStrips/MonthCompletion` | enum + const | iterate descriptor streams, pass N, scope by plan | mechanical |
| 5 | stream title `when` | `ReadingFormatter.streamTitle` | hardcoded BC titles | descriptor `StreamDescriptor.title` | mechanical (§8) |
| 6 | day cards | `ui/day/DayContent.kt` | `readings.forEach` (flexible) + enum title | descriptor title; reclaim/fit at N≠3 | **UI/device-pass at N≠3** |
| 7 | stats strips + rows | `ui/stats/StatsContent.kt` | `Stream.entries.forEach` + consts | iterate descriptor; strip-height/legend at N≠3 | **UI/device-pass at N≠3** |
| 8 | widget rows + tiers | `widget/WidgetContent.kt` | `readings.forEach` (flexible) + tier-tuned-to-3 | descriptor title; **row-count-aware tier policy** | **real layout (D-ALT-10)** |
| 9 | whole-day mark | `MarkWholeDayUseCase` / `setWholeDay` | `Stream.entries` | `1..N` for active plan | mechanical |
| 10 | reminder / persistent copy | `reminders/` | joins portions (flexible) + "three" copy | active-plan-scoped; copy fix | copy + scoping only |
| 11 | loader shape asserts | `ReadingPlanAssetLoader` | `==365`, `==listOf(1,2,3)`, `Stream.fromNumber` | `dayCount`/`streams` from descriptor | mechanical (§3) |
| 12 | the gate | `ReadingPlanVerificationTest` | BC-shape pins | per-plan gate (§7) | **per-plan data work** |

---

## 5. Per-plan progress storage + the migration (§3 of the prompt) — the HIGH-RISK item

The owner chose **per-plan progress** (OQ-3): each plan keeps its own marks; switching back restores them;
stats/streaks/strips/widget become per-plan. Today progress is `(dateEpochDay, stream)` with **no plan
dimension** — it is implicitly Bible-Companion progress because there is only one plan. Adding the plan
dimension is a **Room schema change with a real data migration**, and it is the single highest-risk piece of
this feature: it touches existing users' real reading history, and **zero loss is the bar** (FR-ALT-2,
U-ALT-1 "Upgrading users keep all existing progress").

### 5.1 The schema change

`reading_progress` today (`ProgressDatabase` v1, `exportSchema = true`, baseline at
`app/schemas/…ProgressDatabase/1.json`):

```
PRIMARY KEY (dateEpochDay, stream)
columns: dateEpochDay INTEGER, stream INTEGER, readAtEpochMillis INTEGER
```

**D-ALT-12 — add a `plan_id TEXT NOT NULL` column; the primary key becomes `(plan_id, dateEpochDay,
stream)`; bump `ProgressDatabase` to version 2 with an exported schema and a hand-written
`MIGRATION_1_2`.** The new key isolates each plan's marks; the existing year-isolation (full date incl.
year) is preserved within each plan.

```sql
-- v2 reading_progress
CREATE TABLE reading_progress (
  plan_id          TEXT    NOT NULL,
  dateEpochDay     INTEGER NOT NULL,
  stream           INTEGER NOT NULL,
  readAtEpochMillis INTEGER NOT NULL,
  PRIMARY KEY (plan_id, dateEpochDay, stream)
);
```

`ReadingProgressEntity` gains `val planId: String`; the `@Entity` `primaryKeys` gains `"plan_id"`. Every DAO
query gains a `plan_id = :planId` clause (or `WHERE plan_id = :planId AND …` on the ranged/grouped queries);
`ProgressRepository`'s every method gains a `planId` parameter (defaulted to the active plan in the impl, so
callers reading "the active plan" don't thread it manually). The grouped queries (`readCountsInRange`,
`allReadCounts`, `streamCountsInRange`, `marksInRange`) all filter by `plan_id`, so stats/streaks/strips/
picker become **per active plan** by construction. `clearYear` and the whole-day/single mutators likewise
scope to a plan.

### 5.2 The migration (the zero-loss requirement, spelled out)

**D-ALT-13 — `MIGRATION_1_2` stamps every existing row with `plan_id = "bible_companion"` (the flagship
id), losslessly.** Every mark in the wild today is, by definition, a Bible Companion mark (there is only one
plan). The migration is a pure column-add-with-default — no row is dropped, no value other than the new
column changes:

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. New table with the v2 schema (Room requires recreate to change the PK).
        db.execSQL("""
            CREATE TABLE reading_progress_new (
              plan_id TEXT NOT NULL,
              dateEpochDay INTEGER NOT NULL,
              stream INTEGER NOT NULL,
              readAtEpochMillis INTEGER NOT NULL,
              PRIMARY KEY (plan_id, dateEpochDay, stream)
            )
        """)
        // 2. Copy EVERY existing row, stamping the flagship plan id. Zero loss.
        db.execSQL("""
            INSERT INTO reading_progress_new (plan_id, dateEpochDay, stream, readAtEpochMillis)
            SELECT 'bible_companion', dateEpochDay, stream, readAtEpochMillis FROM reading_progress
        """)
        db.execSQL("DROP TABLE reading_progress")
        db.execSQL("ALTER TABLE reading_progress_new RENAME TO reading_progress")
    }
}
```

(The PK change forces the table-recreate idiom — SQLite cannot alter a primary key in place. The
recreate-and-copy is the standard, safe Room migration; it touches every row exactly once.)

**Why a literal default value and not a Room column default:** the flagship id is the *meaning* of the
existing data ("these are Bible Companion marks"), so it belongs in the migration as an explicit constant,
not as a schema-level `DEFAULT` that could drift. The constant `"bible_companion"` must equal the registry
`defaultPlanId` — a test asserts the two agree (anti-drift).

### 5.3 Exported-schema rigor + the migration test (the Sprint-3/9 discipline)

This is the part that earns the "high-risk done right" claim. **D-ALT-14 — the v2 schema is exported and
checked in at `app/schemas/…ProgressDatabase/2.json`, and a Room `MigrationTestHelper` test proves the
1→2 migration preserves data byte-for-byte.** Concretely (extending the existing Room-test discipline,
`BibleDatabaseRoomOpenTest`-style under Robolectric):

1. **Exported schema 2.json checked in** (the D-S9-4 rigor that retired the Sprint-3 `exportSchema=false`
   debt). The migration test runs against the real exported schemas, not a hand-typed DDL.
2. **`MigrationTestHelper` test:** create a v1 DB, insert a representative set of marks (multiple years to
   exercise year-isolation; whole-day and partial days; a Feb-29-adjacent day), run `MIGRATION_1_2`,
   assert: (a) **row count identical**; (b) **every** `(dateEpochDay, stream, readAtEpochMillis)` tuple
   present with `plan_id = "bible_companion"` and nothing else changed; (c) the v2 schema validates (Room's
   `validateMigration`). A mutation that drops the `INSERT … SELECT` (loses all rows) or stamps a wrong
   plan id must turn it red.
3. **End-to-end no-op-for-the-default-user assertion:** a test that opens a migrated v1 DB through the
   *new* `ProgressRepository` with the active plan defaulted to `bible_companion` and asserts the streams-
   read / stats / strips are identical to what the v1 repo returned — i.e., **an upgrading Bible Companion
   user sees their exact history, no migration they can perceive** (U-ALT-1 AC, FR-ALT-2). This is the
   single most important new test in the feature (it is to multi-plan what the day-by-day equality test is
   to the plan data).

### 5.4 Risk callout (for Morgan/owner — surfaced, not buried)

- **This is the only schema migration in the feature, and the only place existing user data is rewritten.**
  Everything else is additive read-side generalization. The migration is mechanically simple (a column add
  with a constant stamp), but its blast radius is "everyone's reading history," so it gets the heaviest
  test rigor and its own sprint (Phase B, §10), sequenced *before* the UI generalization so the data spine
  is proven before anything renders against it.
- **There is no down-migration and no need for one** — v2 is a strict superset; a user can only move
  forward. The `fallbackToDestructiveMigration` is **not** enabled on `ProgressDatabase` (it holds user
  data; we never destroy it — the V3 D-V3-15 rule). If the migration ever threw, the correct failure is a
  loud crash in QA, never silent data loss; the test gate exists so it never reaches a device.
- **`hasAnyMarks()`** (the S10 tracking-start first-run default seam) becomes "any marks for any plan" vs
  "for the active plan." **D-ALT-15 — `hasAnyMarks()` stays global (any plan)** for the tracking-start
  default's "this is a fresh install" meaning, but the tracking-start date itself is **global, not
  per-plan** in this cut (it is a calendar concept, not a plan concept — a user's "I started tracking on
  date X" applies to whatever plan they read). Flagged so it's a decision, not an accident; revisit if a
  per-plan tracking start is ever wanted (not in this cut).

---

## 6. The plan selector + active-plan setting (§4 of the prompt)

### 6.1 The `selected_plan` DataStore key

**D-ALT-16 — a `selected_plan` string key in the existing `SettingsRepository` DataStore; default =
`registry.defaultPlanId` (`bible_companion`); unknown stored ids degrade to the default.** This mirrors the
existing `bible_provider` / `reading_destination_mode` key discipline exactly (string enum-ish value,
absent ⇒ default, corrupt ⇒ default, never crash):

```kotlin
// SettingsRepository
val selectedPlanId: Flow<String>            // default = registry.defaultPlanId
suspend fun setSelectedPlanId(id: String)
// key: stringPreferencesKey("selected_plan")
```

- **Absent key ⇒ `bible_companion`** — every existing install (no key) is on the flagship, invisibly
  (FR-ALT-2). This is the same absent-key-default idiom as `show_streaks` (S18) and
  `persistent_notification_enabled` (S22).
- **Unknown id ⇒ default** — a stored id not in the registry (e.g. a plan removed in a later build) degrades
  to `bible_companion` rather than failing to load anything. Same posture as `BibleProvider.fromStored`.
- `SettingsRepository` stays the home (the S10 rename rationale holds — it is the app's settings store); no
  new DataStore file.

### 6.2 `ActivePlanRepository` — the active plan as a Flow

**D-ALT-17 — `ActivePlanRepository` joins `selected_plan` with the `PlanRegistry` + `ReadingPlanRepository`
to expose the active `PlanDescriptor` as a `Flow`.** This is the single place the rest of the app asks "what
plan am I on, and what's its shape?":

```kotlin
interface ActivePlanRepository {
    val activePlanId: Flow<String>
    val activeDescriptor: Flow<PlanDescriptor>     // selected_plan → registry → loaded descriptor
}
```

Use cases that today read "the schedule + progress" inject `ActivePlanRepository` and `combine` the active
descriptor into their flows, so **switching the plan re-emits everything** (the day screen, stats, strips,
picker dots, widget) live — the same Room/DataStore liveness contract the app already relies on
(FR-17-style). `GetDayReadingsUseCase`, the three stats use cases, the reminder content builder, and the
widget entry point all become active-plan-aware via this one repository.

### 6.3 The selector UI (Settings) — Priya owns visuals

**D-ALT-18 — the plan selector lives in Settings as a dropdown/list row (Priya's visual call, OQ-7),
following the established `SettingsDropdownRow` idiom (S14).** It shows the active plan's `name`, anchors a
menu of the registry's plans (each `name`), and writes `selected_plan` on selection. The Bible Companion is
first/recommended. This is off the critical daily path (G15, FR-ALT-6) — a Bible Companion reader never
opens it. The "active plan is visible" requirement (FR-ALT-6, U-ALT-6) is satisfied by the row showing the
current plan; whether the day screen also shows a small plan label is Priya's call (OQ-7) and must not
clutter the "what do I read today" path.

### 6.4 Switching plans — the explained, non-destructive switch

Because progress is **per-plan** (D-ALT-12), switching is **non-destructive by construction** — the new
plan's marks are simply a different `plan_id` partition; the old plan's rows are untouched and restored on
switching back. **D-ALT-19 — switching the plan writes `selected_plan` and shows a one-time explanation at
the moment of the switch** (U-ALT-4 AC, FR-ALT-7): *"Your [old plan] progress is saved — switch back any
time and it'll be here. [New plan] starts fresh."* No data operation runs on switch (no copy, no clear); the
explanation is informational. The wording awaits owner tone sign-off; the *mechanism* is "write a key, show
a dialog," with zero data risk.

> **What does NOT happen on switch (deliberately):** no progress is copied, merged, or cleared; the
> tracking-start date is global (D-ALT-15) and unchanged; reminders/persistent-notification re-point at the
> new active plan's content automatically (they read the active plan); the widget refreshes to the new
> plan. The switch is a pointer move, not a data migration — which is exactly why per-plan progress is the
> safe choice for experimentation (PRD §8 rationale).

---

## 7. Per-plan trusted-data gate (§5 of the prompt) — the release gate per plan

**Every plan is a new bundled asset that MUST pass a Sprint-1-style verification gate before it ships
(FR-ALT-3, the project's credibility, PRD §10).** This is non-negotiable and is the single biggest cost
driver (PRD §7) — it is a *data* cost, not a code cost, and it sets the phasing.

**D-ALT-20 — each plan gets its own `*VerificationTest`, its own independent second-source fixture, and its
own reconciliation log entry, exactly as the Bible Companion has today.** The existing
`ReadingPlanVerificationTest` (11 tests) is generalized in shape but the Bible Companion's gate stays
green on its unchanged data. Per plan:

```
app/src/test/resources/plans/<id>/plan_verify.json     # independent second-source fixture
app/src/test/kotlin/.../data/plan/<Id>PlanVerificationTest.kt
tools/extract_<id>_primary.py / extract_<id>_second.py # reproducible extraction (two parsers)
docs/data/README.md                                     # per-plan source table + reconciliation log
```

Each plan's gate asserts, against the *shipped asset*:

1. **Schema/structural invariants for that plan:** `schemaVersion == 3`; `dayCount` days, correct per-month
   counts, Feb-29-absent (date-anchored 365-day); `streams[].number == 1..N`; every day's portions =
   exactly the declared streams; every ref resolves in `BookCatalog` with chapter in range; every windowed
   ref (if any) well-formed against the KJV verse-count witness.
2. **Second-source day-by-day equality** — the canonical asset and an *independent* second-source fixture
   agree on **every day** (the gate's central invariant), with a **reconciliation log** documenting every
   conflict resolved on evidence (the Bible Companion had 7). Guard the Sprint-1 "same-upstream re-mirror"
   trap (the pricejh==christadelphia / eBible-USFX==OSIS lessons): the two sources must be genuinely
   independent, checksum-distinct, parsed by two independently-written parsers.
3. **Coverage invariant appropriate to the plan** — the Bible Companion's is "OT once / NT twice, every
   chapter read the expected number of times." Each plan declares its own coverage shape (M'Cheyne: OT
   once, NT + Psalms twice; chronological: every chapter once, in the publisher's order). The coverage
   invariant is the plan's load-bearing structural proof and is plan-specific.
4. **A CI gate** that runs offline in `testDebugUnitTest` and **blocks release**, with reproducible
   extraction scripts and a committed second-source fixture, exactly as the plan and KJV gates do today.

### 7.1 M'Cheyne (Phase 1) — the safe data lift

M'Cheyne is the **structurally-identical, well-documented** add (PRD §9): same date-anchored 365-day model,
multiple agreeing public sources (M'Cheyne's own text, the Gospel Coalition / "For the Love of God" Carson
companion). Its gate is achievable on safe ground. The one genuinely new shape question is **2 vs 4
streams** (the published M'Cheyne has "family" 2 readings/day + "secret" 2 readings/day = up to 4) — which
form we ship is a sourcing + product call (see §11; it does not change the *code* generalization, which is
N-agnostic). The second source must be a *different upstream* than the primary (the same trap the Bible
Companion and KJV both navigated).

### 7.2 The chronological plan (Phase 2) — the heaviest lift

The chronological *ordering is contested* (PRD §7): different publishers sequence it differently, and the
ordering *is the IP*. Two chronological plans from two publishers may *legitimately disagree* on where Job
or a Psalm sits — that is not a typo to reconcile but a real editorial difference, so "an independent second
source that agrees day-by-day" is genuinely hard. **D-ALT-21 — we ship a *specific, named, date-anchored*
chronological plan (a particular publisher's published one-year chronological table) that has a verifiable
independent second witness, never "a chronological plan" in the abstract; if no two genuinely-independent
sources agree on a given ordering, we do not ship that plan** (FR-ALT-3). This is *why* chronological is
Phase 2 — the code is already proven by M'Cheyne; the chronological sprint is almost entirely a data
project, which is where its risk lives. The single-stream shape (1 reading/day, 1 strip, "n of 365" once,
1 widget row) is the *easy* end of the code generalization and is exercised for free once N-generality
ships.

---

## 8. Stream identity generalization (§6 of the prompt)

The current stream *titles* ("Law & History" / "Psalms & Prophecy" / "New Testament") are
Bible-Companion-specific and live in one place: `ReadingFormatter.streamTitle(stream)`, a `when` over the
`Stream` enum. **D-ALT-22 — stream titles are plan-supplied data (`StreamDescriptor.title`), read from the
active plan's descriptor; `ReadingFormatter.streamTitle(stream)` is retired.** Every consumer — the day
card, the stats per-stream rows + spoken summaries, the year-strip a11y summaries, the widget rows + their
contentDescriptions — looks up the title by the portion's `streamNumber` against the active descriptor's
`streams`:

```kotlin
// was: ReadingFormatter.streamTitle(portion.stream)   // enum when, BC-specific
// now: descriptor.streams.first { it.number == portion.streamNumber }.title
```

- **M'Cheyne** supplies its own stream titles ("family" / "secret", or the four-track labels if we ship the
  4-stream form). **Chronological** supplies **one** stream with a title (or none — a single-stream plan's
  reading card can render *without* a stream title, since there's nothing to disambiguate). **D-ALT-23 —
  `StreamDescriptor.title` may be empty/absent for a single-stream plan; a 1-stream day's card and widget
  row render the reference alone, no stream label** (a single reading needs no "which stream" label — this
  is the truthful, calm rendering FR-ALT-4 requires, not a blank label).
- **`ReadingFormatter.format` / `formatAbbreviated`** (the reference collapsing — "Genesis 1–2", the
  Psalms singular/plural D-UI-2 rule, the two-book portion) are **book-name** formatters, not stream
  formatters — they are **completely plan-agnostic** and unchanged. Only the *stream title* generalizes;
  the *reference* formatting is shared across all plans for free.
- The Psalms singular/plural rule (D-UI-2/D-UI-3) and the verse-window rendering (schema v2) carry over to
  every plan automatically, since they live in the reference formatter, not the stream layer.

---

## 9. Decisions (`D-ALT-*`) — index

| ID | Decision |
|---|---|
| D-ALT-1 | One asset directory per plan under `assets/plans/<id>/` + a single bundled `registry.json` (default + curated set). The Bible Companion asset moves there, bytes unchanged. |
| D-ALT-2 | `schemaVersion: 3`; a plan declares `planId`/`name`/`anchoring`/`dayCount`/`streams[]`; the day/portion/ref body is unchanged from v2. |
| D-ALT-3 | The schema bump is additive — the v2 body is a strict subset of v3; the Bible Companion is "just the first plan," its reading data (and gate) untouched. |
| D-ALT-4 | N (stream count) and stream titles come from the active plan's `PlanDescriptor`, never a constant or the enum. |
| D-ALT-5 | Retire `Stream`-as-identity; a stream is a plain `Int` (1..N) keyed value; the enum is deleted (compiler-driven refactor). |
| D-ALT-6 | `DayCompletionClassifier.STREAM_COUNT` becomes a per-call `streamCount: Int` parameter from the active plan — the ONE seam is parameterized, never forked per plan (R-STREAK-5 preserved). |
| D-ALT-7 | Stats denominators become `dayCount × N` and `dayCount` from the descriptor; the `1095`/`365` constants are deleted. |
| D-ALT-8 | The three stats use cases iterate the descriptor's streams, pass `N` to the classifier, and scope every progress query by the active `plan_id`. |
| D-ALT-9 | The stats screen iterates the active plan's streams; strip-height/legend/a11y at N≠3 is a Priya + device-pass item. |
| D-ALT-10 | The widget keeps one `SizeMode.Responsive` design but the tier→content policy becomes row-count-aware (high-N degrades earlier, low-N reclaims room). |
| D-ALT-11 | Reminder + persistent-notification bodies are already N-correct (they join the active plan's portions); only active-plan scoping + static copy change. |
| D-ALT-12 | `reading_progress` gains `plan_id TEXT NOT NULL`; PK becomes `(plan_id, dateEpochDay, stream)`; `ProgressDatabase` v1→v2, exported schema. |
| D-ALT-13 | `MIGRATION_1_2` stamps every existing row `plan_id = "bible_companion"` losslessly (column add + constant stamp, no row dropped). |
| D-ALT-14 | v2 schema exported + checked in; a `MigrationTestHelper` test proves zero-loss 1→2 + an end-to-end "no migration the default user can perceive" assertion. |
| D-ALT-15 | `hasAnyMarks()` stays global; the tracking-start date stays global (a calendar concept, not per-plan) in this cut. |
| D-ALT-16 | `selected_plan` string DataStore key; default = `registry.defaultPlanId`; unknown/corrupt ⇒ default (the `bible_provider` idiom). |
| D-ALT-17 | `ActivePlanRepository` exposes the active `PlanDescriptor` as a `Flow` (selected_plan → registry → descriptor); use cases `combine` it so a switch re-emits everything live. |
| D-ALT-18 | The selector is a Settings dropdown (S14 `SettingsDropdownRow` idiom), off the daily path; Priya owns visuals (OQ-7). |
| D-ALT-19 | Switching writes `selected_plan` + shows a one-time explanation; NO data operation runs (per-plan progress makes the switch non-destructive by construction). |
| D-ALT-20 | Each plan gets its own `*VerificationTest` + independent second-source fixture + reconciliation log; the BC gate stays green on unchanged data. |
| D-ALT-21 | Ship a *specific, named* date-anchored chronological plan with a verifiable independent second witness; a plan without one does not ship. |
| D-ALT-22 | Stream titles are plan-supplied data (`StreamDescriptor.title`); `ReadingFormatter.streamTitle(enum)` is retired. |
| D-ALT-23 | A single-stream plan may have no stream title; a 1-stream card/widget row renders the reference alone, no stream label. |

### Open questions — NOT blocking this spec (resolve before the relevant build phase)

- **[OQ-5 — Owner]** The recurring per-plan data-sourcing burden + second-source availability — confirmed
  at *each* plan's sourcing (M'Cheyne first, then chronological). A plan without a trustworthy second source
  cannot ship (FR-ALT-3). Gates Phase A's M'Cheyne data and Phase E's chronological data; gates **neither**
  the schema/code generalization nor this spec.
- **[OQ-7 — Owner + Maya + Priya]** Selector placement (Settings-only vs a light first-run plan question)
  and the day-screen plan-label visibility. Settings is mandatory; a first-run option only if it stays light
  and defaults cleanly to the Bible Companion (the G1/M2 zero-setup promise). UI-time decision; does not
  gate the data model or the migration.
- **[OQ-MC — Owner + data]** Which published M'Cheyne form do we ship — **2-stream** ("family" only, or a
  combined 2-reading form) or **4-stream** (family + secret)? A *data + product* call made at M'Cheyne
  sourcing; the code is N-agnostic so it does not gate the generalization, only the M'Cheyne asset.

---

## 10. Recommended sprint-level breakdown (§7 of the prompt) — mirrors the V3 A–E shape

Sequenced by **dependency + risk**: the data foundation and the high-risk migration land *before* the UI
generalization renders against them, exactly as V3 put its data foundation (Sprint A) before its reader.
Five sprints. Morgan turns these into the execution plan + tickets.

**Sprint A — Plan-data model + schema generalization + M'Cheyne trusted-data asset + gate (the data
foundation; gating).**
The `schemaVersion: 3` schema (descriptor head, D-ALT-2/3); `registry.json` + `PlanRegistry`; the Bible
Companion asset moved + re-authored as v3 (bytes-of-data unchanged); `PlanDescriptor`/`StreamDescriptor`;
the generalized `ReadingPlanAssetLoader` (validate against `dayCount`/`streams`, not constants);
`ActivePlanRepository` + `selected_plan` key (D-ALT-16/17, default `bible_companion`). **And** the M'Cheyne
asset itself: two independent sources, reconciliation log, `McheynePlanVerificationTest` (D-ALT-20). **The
deliverable gate: the Bible Companion gate stays green unchanged AND the M'Cheyne gate is green.** Nothing
downstream renders a second plan until this lands. *This is the Sprint-1/Sprint-A of multi-plan.* Needs the
M'Cheyne data research (§11) done first.

**Sprint B — Per-plan progress Room migration (the high-risk item).**
`reading_progress` + `plan_id` (D-ALT-12); `ProgressDatabase` v1→v2; `MIGRATION_1_2` (D-ALT-13); exported
schema 2.json; the `MigrationTestHelper` zero-loss test + the end-to-end "no perceptible migration for the
default user" test (D-ALT-14); every `ProgressRepository`/DAO method gains a `planId` (defaulted to the
active plan). **No UI change yet** — the progress spine becomes per-plan, proven, before anything renders
against it. Sequenced second because it is the only place existing user data is rewritten; it must be
bulletproof before the N-stream UI work depends on it.

**Sprint C — N-stream UI generalization (schedule / stats / strips / widget).**
Retire the `Stream` enum (D-ALT-5, compiler-driven); parameterize the classifier (D-ALT-6); generalize the
stats denominators + the three stat use cases (D-ALT-7/8); stream titles from the descriptor (D-ALT-22/23);
the day cards, stats strips/rows, and the **row-count-aware widget tier policy** (D-ALT-9/10); the
whole-day-mark + reminder/persistent scoping (D-ALT-11). All N-correctness is JVM-gate-provable (the
M'Cheyne 2/4-stream and a synthetic 1-stream plan exercise both edges); the *look* at N≠3 (card fit, strip
height, widget tiers) is the device-pass set.

**Sprint D — Plan selector + integration.**
The Settings selector (D-ALT-18, S14 idiom); the explained non-destructive switch (D-ALT-19); wiring the
active plan through the day screen, stats, picker dots, reminder/persistent content, and the **widget**
(it must read `selected_plan` via its `@EntryPoint` and refresh on a switch); the "active plan visible"
affordance (FR-ALT-6). End-to-end: select M'Cheyne → the whole app (day/stats/strips/widget/reminder)
shows M'Cheyne; switch back → Bible Companion history intact. The first-run plan question if OQ-7 says so
(light, BC-default).

**Sprint E — The chronological plan + hardening + release.**
The named, date-anchored chronological plan: two independent sources (the contested-ordering data lift,
D-ALT-21), reconciliation log, `ChronologicalPlanVerificationTest` — proving the **single-stream** end of
the generalization. Consolidated device pass (N=1 / N=2 / N=4 day-screen fit, stats strip look, widget
tiers at every N, the switch on glass, the migrated history on a real upgrade); string/tone sign-offs
(stream titles, the switch explanation, the selector); the bundle-size check (the extra plan assets are
tiny — a few hundred KB of JSON each, well under the V3 budget); version bump + rollout.

> **Sequencing note for Morgan:** A is strictly first (no second plan exists, and the schema/registry/active-
> plan spine must land, before anything is shippable). B is the high-risk data migration and must land before
> C renders against per-plan progress — do **not** parallelize the migration against the UI generalization
> (the UI reads the per-plan store; the store must be correct first). C and D are peers downstream of B (C is
> the N-stream rendering, D is the selector + wiring) and can overlap across people once B is green. E is last
> and is mostly a *data* project (the chronological sourcing), gated on its second-source availability (OQ-5),
> not on more code. The M'Cheyne data research (§11) gates A; the chronological data research gates E; neither
> gates the code generalization, which is N-agnostic by construction.

---

## 11. What still needs research / owner calls (§8 of the prompt)

### Blocks the relevant build phase — running in parallel, does NOT block this spec

- **M'Cheyne data research (gates Sprint A's M'Cheyne asset, not the schema/code).** Before the M'Cheyne
  asset can be *built*, the data work must produce: (1) **two genuinely-independent sources** of the
  M'Cheyne calendar (a primary + a checksum-distinct second of different lineage — guard the
  pricejh/eBible re-mirror trap); (2) a decision on **which published form** (2-stream vs 4-stream,
  OQ-MC); (3) the **coverage invariant** for that form (M'Cheyne: OT once, NT + Psalms twice — the gate's
  structural proof); (4) any **reconciliation** of source disagreements, logged on evidence. This is a
  small data project exactly like Sprint 1; the code (schema, registry, loader, gate harness) does not wait
  on it and can land in Sprint A in parallel with the data sourcing. **The schema and the N-stream code are
  fully specifiable now (this spec) without the M'Cheyne data in hand** — the data fills the asset the gate
  validates.

- **Chronological data research (gates Sprint E, not earlier).** The contested-ordering sourcing (D-ALT-21)
  — name the specific publisher's date-anchored one-year chronological table, find a verifiable independent
  second witness, reconcile or do-not-ship. This is the heaviest lift in the feature and is deliberately
  last so the code generalization is already proven by M'Cheyne. It gates only Phase E.

### Owner product calls that gate later phases but NOT this spec

- **OQ-5 (the recurring per-plan data burden + second-source availability).** Confirmed at *each* plan's
  sourcing. A plan without a trustworthy second source cannot ship (FR-ALT-3). Gates the M'Cheyne asset
  (Phase A) and the chronological asset (Phase E); gates neither the schema nor the code.
- **OQ-7 (selector placement / first-run).** Settings-only (mandatory) vs a light first-run question;
  the day-screen plan-label visibility. A UI-time decision for Priya + the owner; gates the Phase D selector
  *presentation*, not the data model, the migration, or the N-stream generalization.
- **OQ-MC (M'Cheyne 2-stream vs 4-stream).** A data + product call at M'Cheyne sourcing; the code is
  N-agnostic, so it gates only the M'Cheyne asset, not the generalization.

### Carried, not re-decided (the identity invariants)

No networking / no analytics / every plan a bundled asset (load-bearing identity, PRD §12, NFR-V3-A,
unchanged). KJV-anchored, respectful, non-gamified tone for every plan and every generalized surface (PRD
§13.0 — the plural-plan world adds no gamification). **The Bible Companion stays the pre-selected,
zero-setup, byte-for-byte-unchanged default** — the §1.1 parity invariant is the acceptance bar for the
whole feature.

---

## 12. Net new dependencies, bundle, and the no-rework check

- **New runtime dependencies: zero.** Room is already present (`ProgressDatabase`); the migration uses
  `androidx.room` machinery already in the catalog. DataStore, kotlinx-serialization, Glance, Compose — all
  present. The plan registry + descriptor parse with the existing `kotlinx.serialization`.
- **New test dependency: one** — `androidx.room:room-testing` (`MigrationTestHelper`), for the migration
  test (D-ALT-14). (The Robolectric/Room test harness is already in place from `BibleDatabaseRoomOpenTest`.)
- **Bundle impact: trivial.** Each plan asset is a few hundred KB of JSON (the Bible Companion plan is
  ~tens of KB; M'Cheyne/chronological comparable). Far under the V3 `bible.db` budget; no re-budget needed.
  The bundle-size CI check (the S9/V3 `release-bundle` job) stays green.
- **No-rework check (the seams this design protects):** the V3 `PortionVerseBridge` / `BibleTextSource` /
  in-app reader all consume a `Portion`, which is plan-agnostic — every plan's readings open in the in-app
  reader and resolve to provider URLs (`ProviderUrlBuilder`) with **no new work** (FR-ALT-11); single-stream
  and N-stream portions flow through the existing bridge unchanged. The `DayCompletionClassifier` single
  seam (R-STREAK-5) is preserved as a parameter, so a future V2-streaks/V3-strip consumer never re-derives
  completion per plan. The tracking-start (S10/S19) and reminder (S12/S22) machinery generalize by reading
  the active plan, not by forking. **The only thing this feature re-foundations is the progress store's key
  (one migration); everything else is an additive read-side generalization through existing seams.**
