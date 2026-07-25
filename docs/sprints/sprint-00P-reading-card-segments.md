# Sprint 00P — reading card segments + partial checks

**Goal:** *Tapping any reading on the Schedule opens exactly that passage — never Genesis 1 —
because a reading is now one card per contiguous passage, each independently checkable.*

**Outcome: MET.** Two owner tickets (a P0 bug and a feature) delivered as one sprint, because
ticket 2 fixes ticket 1 structurally. Spec: [docs/features/reading-card-segments.md](../features/reading-card-segments.md)
(owner-approved and locked before implementation; D-SEG-1 … D-SEG-8 implemented as written, no deviations).

Uncommitted in the working tree by request. **No version bump** (stays 1.5.1 / 10501), no tag, no commit.

---

## Current capability — what can now be done that could not before

- **The P0 is closed on the owner's path.** On the Chronological plan, tapping a reading opened
  **Genesis 1** for **83 of 365 days**. A reading whose passages jump backwards in canon order
  (07/25 = `Isaiah 37, 38, 39, Psalms 76`) now renders as two cards and each opens its own passage.
  Root cause was executable, not theoretical: `ReadingPagerIndex.init` threw
  `IllegalArgumentException: portion global span is reversed: 715..553` (Isaiah 37 is global chapter
  715, Psalms 76 is 553), `ReaderViewModel.enterReading` swallowed it with `runCatching{}.getOrNull() ?: return`,
  and the reader stayed in Browse at its Genesis-1 default.
- **A reading is now one card per contiguous passage.** Same book + consecutive chapters = one card;
  a book change or a chapter gap splits. Segmentation adds **132 cards** across the three bundled plans.
- **Multi-card readings can be part-completed.** A distinct-colour "partial" tick marks progress
  through a multi-passage reading and converts to the real completed mark only once every segment
  is checked. Single-segment readings (2,834 of 2,920 portions) behave byte-for-byte as before.
- **External links got more honest.** Tapping a card opens *that run* (Bible Gateway shows
  "Isaiah 37-39"), not the whole day — matching what the card itself says. Falls out of D-SEG-6
  with no change to `ProviderUrlBuilder`, which already groups through the same seam.
- **The bug cannot silently return.** A new data gate sweeps all 2,920 portions of all three
  bundled plans and proves every segment is a contiguous ascending global-chapter run that
  `ReadingPagerIndex` accepts.

Administrative footnote: 735 → **818 tests**, 0 failures; full pipeline green from clean.

---

## Decisions & rationale

All D-SEG-* decisions are the owner-approved spec; recorded here so the next session does not relitigate.

- **D-SEG-1 — THE segmentation rule.** A segment is a maximal run of refs with the same book and
  consecutive ascending chapters. **A verse window does NOT split.** It is *exactly*
  `bible/domain/ConsecutiveChapterRuns.group` — the grouper already used by `ProviderUrlBuilder`.
  **One home, no second grouper**: card boundaries and external-URL grouping are the same question
  and must never drift. Deliberately distinct from `ReadingFormatter`'s *private* `consecutiveRuns`,
  which additionally breaks on verse windows — that governs display *within* a card and is untouched.
  Consequence, pinned: M'Cheyne 02/28 stream 1 (`Exodus 11` + `Exodus 12:1–21`) is ONE card reading
  `Exodus 11; Exodus 12:1–21`. 8 such windowed-adjacent pairs exist.
- **D-SEG-2 — the invariant that fixes ticket 1.** Every segment is by construction a contiguous
  global-chapter run, so `ReadingPagerIndex(segment)` can never throw. Gate-proven: **0 violations
  across all 2,920 portions**.
- **D-SEG-3 — progress storage UNCHANGED.** Room still stores one mark per (plan, date, stream).
  **No migration, no schema change.** A partially-read reading counts as not-read in stats, streaks,
  strips, picker dots, widget and reminders — the intent.
- **D-SEG-4 — the partial cache is DataStore, not Room.** Key `partial_reading_segments`, a
  `Set<String>` of `"<planId>|<epochDay>|<stream>|<segmentIndex>"`. `PARTIAL` is unreachable at N==1
  (enforced defensively in `stateFor`, so a stale token from an earlier plan revision cannot surface).
- **D-SEG-5 — pruning.** Whole-set prune on every write; keep iff `epochDay >= today - 400`.
  Unparseable tokens dropped (self-healing). Future-dated tokens kept (users swipe ahead).
- **D-SEG-6 — the tapped SEGMENT is what gets opened.** `onSegmentTapped` passes `segment.portion`
  to `OpenReferenceUseCase`/`ReaderHandoff`. This is the line that actually delivers the P0 fix.
- **D-SEG-7 — never fall back to Genesis 1.** If a `ReadingPagerIndex` ever cannot be built,
  `enterReading` now opens the portion's **first ref's chapter** in Browse. Belt-and-braces; unreachable
  for today's data, but a future edge case degrades to the right neighbourhood instead of the wrong
  end of the Bible.
- **D-SEG-8 — split at the UI layer, NOT in `GetDayReadingsUseCase`.** That use case also feeds the
  Glance widget and the reminder / persistent-notification bodies; splitting there would give the
  widget 6 rows on a Chronological day and break its row-count tier policy. `DayReadings` /
  `ReadingStatus` stay per-portion and untouched; the split happens in `DayReadingsViewModel`.

Implementation decisions taken during the sprint (not in the spec):

- **P-1 — `SegmentCheckOutcome` is the full desired state, not a delta.** The policy returns the
  complete `(streamMarkRead, partialSegments)` for that (plan, date, stream); the use case replaces
  that reading's token subset wholesale. Un-ticking a COMPLETE 6-segment Chronological reading writes
  5 tokens in one interaction — correct per the table, bounded by D-SEG-5.
- **P-2 — write ORDER is load-bearing.** `stateFor` is *mark-dominant* (a set mark shows COMPLETE
  regardless of tokens) and the two writes hit different stores, so the intermediate frame is visible.
  When the outcome **sets** the mark → write the **mark first**; when it **clears** the mark → write the
  **tokens first**. Either opposite order produces a one-frame "all unchecked" flash. Pinned by two tests.
- **P-3 — no `n == 1` special case in `onToggle`.** The general rules already degrade to the plain
  mark/unmark, so single-segment parity holds by construction rather than by a parallel branch that
  could drift. Pinned by dedicated parity tests.
- **P-4 — PARTIAL keeps the *unread* card background.** Only `COMPLETE` gets `secondaryContainer`;
  `UNCHECKED` and `PARTIAL` both get `surfaceVariant`. Partial means not done; the tick colour carries
  the distinction. `COMPLETE`/`UNCHECKED` render byte-for-byte as before.
- **P-5 — a dedicated `PartialReadingRepository`, not `SettingsRepository`.** Keeps the `Clock`
  injection scoped and leaves the many existing `FakeSettingsRepository` call sites untouched.

---

## State of the codebase

New production files (all under `app/src/main/kotlin/com/jpillion/dailyreadingplanner/`):

| file | role |
|---|---|
| `domain/ReadingSegments.kt` | `segmentsOf(portion): List<Portion>` — pure delegation to `ConsecutiveChapterRuns` |
| `domain/model/ReadingCheckState.kt` | `UNCHECKED / PARTIAL / COMPLETE` |
| `domain/SegmentCheckPolicy.kt` | THE transition table + `SegmentCheckOutcome`. Pure. The primary mutation target |
| `domain/GetPartialSegmentsUseCase.kt` | active-plan-scoped `Flow<Map<stream, Set<segIndex>>>`; also hosts the shared decode/filter helpers |
| `domain/ToggleSegmentCheckUseCase.kt` | checkbox path; delegates the Room write to `ToggleReadingUseCase` |
| `domain/MarkSegmentReadOnOpenUseCase.kt` | card-body path; delegates to `MarkReadOnOpenUseCase`, and **only** when the outcome sets the mark |
| `data/prefs/PartialSegmentToken.kt` | total codec — `parse` never throws |
| `data/prefs/PartialReadingRepository{,Impl}.kt` | DataStore cache + 400-day prune (`RETENTION_DAYS`) |
| `ui/day/SegmentCheckColors.kt` | THE colour seam (the `StripColors` precedent) |
| `ui/day/SegmentCheck.kt` | `SegmentCheckbox` — owns `stateDescription` + ≥48dp; caller supplies the `testTag` |

Changed: `ui/day/DayUiState.kt` (`Scheduled.readings` → `Scheduled.segments: List<ReadingSegmentUiState>`),
`ui/day/DayReadingsViewModel.kt`, `ui/day/DayContent.kt`, `ui/day/DayReadingsScreen.kt`,
`bible/ui/reader/ReaderViewModel.kt` (D-SEG-7), `di/RepositoryModule.kt` (one `@Binds`),
`ui/theme/Color.kt` (+4 vals, append only), `res/values/strings.xml` (+3, append only).

Conventions established:
- **Test tags are now `reading-<stream>-<segIndex>` / `toggle-<stream>-<segIndex>`.** The old
  `reading-<stream>` / `toggle-<stream>` forms are gone everywhere (verified: no stale references).
- `DayUiState.Scheduled` is type-level incapable of carrying a whole multi-run portion to the reader —
  it holds `segments` only. That is what makes the P0 non-recurring at the UI layer.
- `ReadingSegmentUiState` carries `segmentCount`, so the policy is always given the right N.
- The mapping helper is `internal fun segmentUiStates(readings, partials)` (top-level, matching the
  repo's `partialSegmentsByStream` / `formatMonthDay` idiom) so it is directly unit-testable.

### Gates

- **NEW: `data/plan/PlanSegmentGateTest.kt` (6 tests)** — reads the SHIPPED assets via `planAssetsDir`,
  sweeps all 2,920 portions, with anti-vacuity guards (each sweep asserts it inspected 2,920 portions).
  Pins the contiguity invariant, `ReadingPagerIndex`-constructs, the partition property, the
  distribution table, the Ticket-1 regression (**including that the *undivided* 07/25 portion MUST
  fail to build an index** — the root cause recorded executably), and the M'Cheyne merge case.
- **The five pre-existing data/Room gates are UNTOUCHED**: Bible Companion plan **11**, M'Cheyne **10**,
  Chronological **8**, `BibleTextVerificationTest` **18**, `BibleDatabaseRoomOpenTest` **5**
  (`git diff` on all five files is empty).

Verified segment-count distribution (independently reproduced three times — by me, by Diego from the
assets, and by Riley via an independent Python re-implementation):

| plan | portions | 1 | 2 | 3 | 4 | 5 | 6 |
|---|---|---|---|---|---|---|---|
| bible_companion | 1095 | 1093 | 2 | – | – | – | – |
| chronological | 365 | 282 | 57 | 13 | 7 | 5 | 1 |
| mcheyne | 1460 | 1459 | 1 | – | – | – | – |

Max cards on one day = **6**, Chronological **04/22** (`Psalm 6 | Psalms 8–10 | Psalm 14 | Psalm 16 |
Psalm 19 | Psalm 21`). Per plan: BC 4 (06/19, 12/19), M'Cheyne 5 (08/08), Chronological 6.
Six Chronological days exceed 4 cards.

---

## Carryover & next goal

**Nothing was deliberately scoped out mid-sprint** — the spec's own "Out of scope" list was respected:
no per-segment *persistent* progress record, no Room migration, no change to stats/streak/widget
denominators, no change to `ReadingFormatter`'s display run logic.

Queued candidate tickets (NOT absorbed):
1. **Equality guard on the no-op partial write.** `MarkSegmentReadOnOpenUseCase` issues a
   `setPartialSegments` write even when `onOpen` returns the token set unchanged (tapping an
   already-PARTIAL card), so every such tap costs a DataStore write plus a prune pass. Harmless,
   trivially avoidable.
2. **Fold `SwitchableActivePlanRepository` into the shared `FakeActivePlanRepository`.** The
   plan-switch test uses a file-local fake because the shared one fixes its plan id at construction;
   deliberately not changed mid-sprint to avoid churn on a file other tickets were editing.
3. **Colourblind palette.** Now a one-place swap for BOTH strips (`StripColors`) and the partial
   tick (`SegmentCheckColors`) — still owner-deferred.
4. **CI actions Node 24 bump** (`ci/actions-node24-bump`, unmerged) — pre-existing, unrelated.

**Next goal is already specified by the owner:**
[docs/features/reader-verse-selection.md](../features/reader-verse-selection.md) — reader verse
selection + a verse action menu (today a short tap on a verse immediately ejects you to the external
Bible app, and there is no way to copy a verse). That spec carries a **hard scheduling dependency on
this sprint**: it touches `bible/ui/reader/ReaderScreen.kt` and `ReaderViewModel.kt`, the same files
00P modified (D-SEG-7), and states it must start only after 00P has landed and been committed.

**Release note for whoever cuts it:** this sprint is a user-visible feature change (segmented cards +
partial checks) on top of a P0 fix, so per D-S9-3 a **MINOR bump to 1.6.0 / 10600** is the honest
choice rather than a patch — the owner may prefer 1.5.2 if they weigh it as bug-fix-led. Either way
it should clear the accumulated string/tone sign-off backlog below. Not applied here.

**Next sprint: `next: sprint-00Q-reader-verse-selection`.**

---

## Open questions & risks

**Strings awaiting owner tone sign-off** (new this sprint; spoken state descriptions replacing
TalkBack's generic "ticked / not ticked", so they read as "<reference>, partially read"):

| key | value |
|---|---|
| `segment_state_not_read` | "not read" |
| `segment_state_partially_read` | "partially read" |
| `segment_state_read` | "read" |

Sentence-case ("Partially read") is a one-line change plus the literal test pins. These join the
standing sign-off backlog (M'Cheyne titles, the Chronological plan name, the caption strings).

**Device-pass items — NOT JVM-provable, do not claim these work:**

1. **The P0 on glass.** JVM proves the correct segment reaches the reader; only a device proves the
   reader renders Isaiah 37 (and Psalm 76 from the second card) on Chronological 07/25.
2. **The 6-card day scrolling.** Chronological 04/22 now renders 6 cards; M'Cheyne 08/08 renders 5.
   The readings column may scroll where it previously did not — the accepted cost of the owner's
   explicit request, worth confirming against the ~80dp bottom nav bar given the one-screen-fit history.
3. **The PARTIAL amber hue.** `IndicatorAmberLight 0xFFE65100` / `IndicatorAmberDark 0xFFFFB74D` with
   inverted ticks. Legibility and distinctness from the COMPLETE green in light AND dark — and
   **especially under dynamic colour (API 31+, on by default)**, where `primary` is wallpaper-derived
   and an orange-primary device could reduce the hue separation. **Mutation-proven un-provable on the
   JVM:** making the partial colour identical to the complete colour keeps all 10 component tests
   green. The mandatory `stateDescription` is the mitigation, not decoration.
4. **48dp targets at real density on a real 6-card layout** (proven at component level only).
5. **DataStore token durability across process death / force-stop mid-partial.**

**Known behaviour, documented not accidental:** because pruning applies to the whole set on every
write, part-checking a multi-segment reading more than 400 days in the past is pruned by that same
write — partial checks simply do not persist that far back. Correct for a cache; the real mark still
goes to Room.

**Minor risk, cosmetic only:** `streamMarked` is passed from the UI snapshot while `partials` is read
fresh inside the use case. This can never unmark (structurally proven — `MarkSegmentReadOnOpenUseCase`
cannot emit `setRead(false)` on any input), but a stale snapshot could write tokens against an already-
marked stream. Display is mark-dominant so it is invisible, and tokens clear on completion.

**Sprint infrastructure note for the next session:** concurrent Gradle runs in one shared checkout
produce phantom `EOFException` / `NoSuchFileException` / `MissingFileSnapshot` failures, and flipping a
source file back and forth during mutation testing poisons `.kotlin`/`.gradle` incremental state so the
*mutant* class keeps executing after restore. Serialise Gradle, and wipe `app/build` + `.kotlin` +
`.gradle` between mutation iterations. Also: a "BUILD SUCCESSFUL" whose `:app:testDebugUnitTest` is
`FROM-CACHE` has not executed the tests — use `--rerun-tasks` for a real gate run.
