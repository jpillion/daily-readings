# Phase 1 — overview, merge order, and the rules every `p1-*` brief inherits

> **Staff-owned. Read this before any `p1-*` brief; each of them assumes it.**
>
> **Phase 1 is the three Android-only Play releases.** All of it happens **inside the existing
> single `:app` module**. It needs **no Xcode**, **no Apple Developer account**, and **no iOS
> target**. It therefore runs **completely in parallel with the owner's Apple critical path**,
> which is the plan's main parallelism lever.
>
> **Nothing in Phase 1 starts until Gate 0 closes.** That is D-PORT-1 and it is not negotiable —
> see [`gate0-room-identity-hash.md`](gate0-room-identity-hash.md) for why.

---

## Why the sequence is what it is

The three releases are grouped by **failure mode**, not by scope (D-PORT-7). Two of the three
failure classes involved have **already shipped to production in this codebase**:

| Change | Failure mode | Silent? | Realised here before? |
|---|---|---|---|
| Truth → assertk | A weakened assertion | **Yes** | — (but ships zero production bytes) |
| Ktor + URL encoding | Translations fail to fetch | No — the D-OT-2 fallback banner already exists | — |
| `java.time` → kotlinx-datetime | Wrong dates; `toEpochDays()` keys every progress row | No, and fully JVM-provable | — |
| **Hilt → Koin** | **Runtime crash under R8 that debug builds hide** | Loud when it happens, invisible until then | **Yes — 1.7.0** |
| **Persistence relocation** | **Crash-on-open, or silent settings reset** | **Yes, for DataStore** | **Yes — sprint-00F class** |

Three release cycles is more owner babysitting than one. It is justified because a release here is
genuinely cheap — `tag → internal+alpha → promote` is proven and the last production promote took
**39 seconds** — while bisecting a crash spike across four simultaneous changes in production is
very expensive.

> **If the owner wants one fewer cycle: merge 1.9.0 and 1.10.0. Never merge 1.10.0 and 1.11.0.**

---

## The three releases

| Release | Contains | Briefs |
|---|---|---|
| **1.9.0** | kotlinx-datetime · the localized-formatting seam · Ktor + URL encoding · okio · `kotlin.uuid.Uuid` · the `Dispatchers.IO` sweep · the assertk conversion | `p1-01` … `p1-05` |
| **1.10.0** | **Hilt → Koin, alone.** Still one `:app` module. | `p1-06`, `p1-07` |
| **1.11.0** | The module split + the persistence relocation + the asset move | **`p2-01` … `p2-08`** — see [`p2-00-overview.md`](p2-00-overview.md). 1.11.0 is a *release event*; its work packages are Phase 2 briefs. |

`p1-08` (persistence fixture capture) is **not in a release** — it is preparation for 1.11.0 with
multi-day owner latency, and it starts on day one.

---

## Merge order

**Group A — parallel, no shared files. Start all four together.**

```
p1-08  persistence fixture capture       ← START FIRST. Owner latency. Not in any release.
p1-03  Ktor + URL encoding               ← bible/data/remote/*, data/reference/*
p1-04  okio + Dispatchers.IO             ← bible/data/*, di/DispatcherModule.kt
p1-01  DateTextFormatter seam            ← the 7 localized-formatting call sites
```

**Group B — strictly serial after `p1-01`.**

```
p1-02  kotlinx-datetime sweep            ← 36 main + 38 test files. Overlaps p1-01's write set.
```

**Group C — after A and B, closing 1.9.0.**

```
p1-05  assertk residual sweep            ← whatever Truth remains. Write set defined by a command.
                                            ─── TAG 1.9.0 ───
```

**Group D — serial, and the order is the point.**

```
p1-06  ViewModel-init rule               ← builds the detector
p1-07  Hilt → Koin                       ← runs into the detector
                                            ─── TAG 1.10.0 ───
```

**`p1-06` before `p1-07` is deliberate.** `p1-06` generalises `ReaderViewModelHandoffInitTest` into
a rule and moves `MainDispatcherRule` off `UnconfinedTestDispatcher` — "the most forgiving possible
scheduling," which is precisely what let the 1.7.1 ordering bug hide. Doing it *before* the DI
rewrite means (a) the detector exists when the risky change lands, and (b) any tests that redden
from the stricter dispatcher redden in a commit that changed nothing else.

### Write-set conflict map

Only one real overlap exists, and it is handled by ordering rather than by coordination:

- **`p1-01` ∩ `p1-02`** — six UI/widget files. `p1-01` extracts the formatting seam *while both
  sides are still `java.time`*, so the extraction is provably behaviour-preserving (the Android
  actual literally is today's code). `p1-02` then swaps the value types. Two small verifiable
  steps instead of one large one. **Same assignee, strictly serial.**
- `p1-03` owns `bible/data/remote/BibleApiClient.kt` (including its `Dispatchers.IO`);
  `p1-04` owns everything else with `Dispatchers.IO`. Disjoint by file.
- `p1-03` owns `di/BibleRemoteModule.kt`; `p1-04` owns `di/BibleModule.kt` and
  `di/DispatcherModule.kt`. Disjoint.
- `p1-05` and `p1-07` touch test files broadly — hence Groups C and D, after everything else.

---

## Rules every `p1-*` brief inherits

### R1 — No behaviour change. None.

Phase 1 changes **how** things are expressed, never **what** the app does. If a task appears to
require a behaviour change, that is an **escalation**, not a judgement call.

The one sanctioned deviation is ADR-0009's `DateProvider` seam replacing 13 injected
`java.time.Clock` sites — a *shape* change with no behavioural difference, argued in that ADR.

> *"When the Android implementation is bad, port it faithfully first and file the improvement
> separately. Refactor-during-port is how ports fail."*

### R2 — New seam interfaces go in `com.jpillion.dailyreadingplanner.platform`, and **Staff holds the pen**

Phase 1 introduces interfaces destined for `shared/platform`. They live in a new
`platform/` package inside `:app`, which `p2-03` lifts to `shared/platform` **verbatim**.

- The **exact interface source** is given in each brief's Contract section. Copy it.
- **Implementers may not add, rename, widen or "improve" an interface in that package.** If an
  implementation cannot be written against the contract, **that is an escalation, not an edit.**
  Contracts that implementers can edit stop being contracts.
- Interfaces are named for **what the app needs**, never for what either OS provides.
  `UrlOpener.open(url)`, not `CustomTabLauncher`.

### R3 — The six data-verification gates are untouched, and the counts do not move

`ReadingPlanVerificationTest` **11** · `McheynePlanVerificationTest` **10** ·
`ChronologicalPlanVerificationTest` **8** · `PlanSegmentGateTest` **6** ·
`BibleTextVerificationTest` **18** · `BibleDatabaseRoomOpenTest` **5**.

**There are six, not five** (ADR-0010 A1). Every brief's acceptance criteria restate these counts.
A changed count is an escalation even if everything is green.

### R4 — assertk as you go, never as a big bang

**Every test file a brief touches is converted from Truth to assertk in the same commit.**
ADR-0010 explicitly rejects a big-bang conversion: 92 files of assertion churn with no behaviour
change is a large window in which to silently weaken something.

**assertk, not bare `kotlin.test`** — `kotlin.test` throws away `assertWithMessage`, which is the
mechanism the data gates use to produce diagnosable failures. Those messages must survive.

Scale, measured on `main`: **92 test files import Truth**, containing **846 of the 940 `@Test`
methods**, across **1,444 `assertThat(` call sites**.

> The signed-off approach says "846 assertions." **The honest figure is 846 `@Test` methods /
> 1,444 assertion call sites.** Correct it on sight.

### R5 — Every release gets an **R8 release-build device smoke before the tag**

Not `assembleDebug`. **`assembleRelease`, installed on a physical device or emulator, exercised by
hand.** This is in every brief's acceptance criteria, not in its prose.

The standing lesson, from the 1.7.0 P0: a `ReaderViewModel` field-initialisation-order bug
crashed **every reading tap**, reproduced **only** in the R8-minified release build, and passed
all 879 JVM tests because every test constructed the ViewModel before populating the handoff — the
safe order, which production does not use. The owner's device pass was on a debug build and did
not cover it.

**Koin is that same failure class**: lazy, reflective resolution where Hilt was static.

Minimum smoke path, every release:

1. Cold launch.
2. **Tap a reading on the Schedule** → the reader opens on the right chapter (the 1.7.0 crash).
3. Open the picker, pick a chapter, change version → no jump to Genesis 1 (the 1.8.1 bug).
4. Open a chapter → verses render (the sprint-00F failure).
5. Long-press a verse → selection; Copy.
6. Toggle a reading; confirm the widget updates.
7. Settings → change plan → the whole app follows.

### R6 — `fallbackToDestructiveMigration` stays off. Forever.

Not "temporarily during development." It converts a loud, diagnosable crash into the silent
deletion of every user's reading history.

### R7 — Report honestly

Test counts, gate counts and what was *not* proven. This project's record says "NOT JVM-provable"
where that is true, and that habit is worth more than a clean-looking report.

---

## Definition of done for Phase 1

- 1.9.0, 1.10.0 and 1.11.0 are each live on Play with **zero related crash signal in vitals** after
  24–72 hours before the next is tagged.
- The six gates are green with unchanged counts throughout.
- No iOS target exists. That is correct — producing no iOS binary for the first two months is a
  feature of the plan, not a defect.
