# Daily Reading Planner — Execution Plan: Read aloud (audio)

> **Owner:** Morgan (Engineering Manager) · **Status:** Ready to execute (Sprint AUD-A first) · **Last updated:** 2026-07-25
> **Planning branch:** `claude/audio-read-aloud-options-vgb793` · **Epic integration branch (proposed):** `feature/read-aloud` — see **§9**
>
> ### Amendment 1 — 2026-07-25 (three owner decisions)
>
> 1. **`OQ-AUD-1` is RESOLVED: ElevenLabs.** The owner auditioned the field and chose on voice realism.
>    Sprint E collapses from a bake-off to a pilot; **the critical path re-computes** (§1.1) and the
>    dominant scheduling constraint is now **Sprint A's two Play review cycles**, not the render.
> 2. **NEW REQUIREMENT — audio packs must be plug-and-play**, like translations: *"If they download
>    different packs, the audio assets just get plugged in. There shouldn't need to be logic that is
>    dependent on which asset is used."* Diego is speccing a self-describing pack manifest + registry
>    concurrently. The **sequencing** answer is **`D-M-AUD-7`** (§1.5): generality lands **inside** the
>    Phase-2 sprints from their first line, its shape is validated on placeholder bytes in **Sprint A**,
>    and it is *proven* in Sprint F the way Alt Sprint C proved N-streams — with a synthetic second
>    artifact and zero production code change.
> 3. **NEW — the whole epic ships on a separate long-lived branch** (*"depending on how long it takes, I
>    may not want to roll this out immediately"*). Strategy, merge cadence, CI and release mechanics are
>    **§9**, recorded as **`D-M-AUD-8`**. A complementary feature flag is **recommended, not assumed**
>    (`D-M-AUD-9`).
>
> Amended text below is marked **[A1]** where it replaces something a reader may remember differently.
>
> **Inputs (decided upstream, not re-decided here):**
> [PRD-audio.md](PRD-audio.md) (Maya — what/why; `FR-AUD-*`, `NFR-AUD-*`, `D-AUD-1…16`, `M-AUD-1…11`, `R-AUD-1…10`, `OQ-AUD-1…9`) ·
> [ENGINEERING_SPEC-audio.md](ENGINEERING_SPEC-audio.md) (Diego — how; `D-AUD-E-1…19`, the §0 verification table, **§16's eight pushbacks**, the recommended AUD-A…E breakdown) ·
> [features/audio-read-aloud-design.md](features/audio-read-aloud-design.md) (Priya — `D-AUD-UI-1…13`, the string table, the device-pass list) ·
> [EXECUTION_PLAN-v3.md](EXECUTION_PLAN-v3.md) / [EXECUTION_PLAN-alternate-schedules.md](EXECUTION_PLAN-alternate-schedules.md) (structure/voice/ticket format this matches) ·
> [CLAUDE.md](../CLAUDE.md) (current status — **1.5.1/10501 is live in production at 100%**).
>
> This doc owns **sequencing and decomposition**. The product, the architecture and the design are
> settled; I do not re-decide them. My job: order the work into dependency-correct sprints, put the
> irreversible spend behind the evidence that prices it, surface the owner checkpoints that gate work
> *before* it starts, and break the immediate sprints into executable tickets. **Progress is measured
> only in working software** — every sprint states the new capability it unlocks.
>
> **Where the PRD and ESpec §16 disagree, §16 is the corrected reality** and this plan is built on it.
> Maya is applying the amendments concurrently; the four that change *sequencing* are called out in
> §1.6 so no ticket is written against a superseded number.

---

## 1. Up-front decisions & sequencing principles

### 1.1 This feature has TWO critical paths, and they are different  **[A1 — recomputed]**

Every previous epic here had one ordered chain (V3: sources → import → gate → reader; Alt: plan model
→ migration → N-stream UI → selector). Audio has two, and confusing them is the single easiest way to
plan this badly:

```
PRODUCT TRACK (Phase 1 — $0, 0 bytes, no artifact dependency at all)
   SA-T0 manifest go/no-go ──▶ B: player spine + M-AUD-1 gate ──▶ C: Phase 1 complete ──▶ D: SHIP 1.6.0
                                     │
                                     │ B green = the executable spec for F's timing index
                                     ▼
DATA TRACK (Phase 2 — the spend path)
   A: 66 placeholder packs + the pack manifest, on the internal track ──▶ E: pilot + lexicon + ──▶ F: THE RENDER ──▶ G ──▶ H: SHIP 1.7.0
      ⟵⟵ TWO SERIAL PLAY REVIEW CYCLES — THE DOMINANT POLE ⟶⟶            spend gate (the LAST
                                                                          reversible point)
```

**[A1] What the ElevenLabs resolution changed.** `OQ-AUD-1` was the epic's longest pole: an unbounded
owner question that, if it had gone the LibriVox way, would have added a forced-alignment data project
of roughly Sprint-A-of-V3 size (PRD §11). It is answered, so:

- **Sprint E collapses** from "bake-off + pilot" to "pilot, lexicon, calibration, spend gate" — from M
  to **S–M**, and its only remaining calendar cost is the owner's listening turnaround.
- **The timing index gets cheap.** ElevenLabs supplies character/word timestamps from the API, so
  `FR-AUD-10` is derivation, not alignment. That in turn means **E no longer needs Phase 1 *shipped* as
  its executable specification — it needs Sprint B's queue gate green.** Checklist row 11 (§4) is
  re-scoped accordingly.
- **Therefore the two tracks now run genuinely in parallel from the end of B**, converging at G. E and
  F can overlap C and D. F is a data project on a render machine; it barely touches app code.
- **The dominant scheduling constraint is now Sprint A's two Play review cycles**, which are *serial*
  (cycle 2 needs a device that already holds packs from cycle 1) and are wall-clock nobody here owns.
  Everything else in the epic is effort, which is compressible; review turnaround is not.

**This promotes `D-M-AUD-1` from prudent to load-bearing.** Diego's `AUD-C-1` moves from his Sprint C
to my Sprint A (his `OQ-AUD-E-4`, answered: yes, budgeted, and first), and **`SA-T5`/`SA-T6` are now
the first things dispatched in the epic** — before, or at worst alongside, `SB-T1`. A day lost getting
the spike bundle into Play's queue is a day added to the whole Phase-2 path; a day lost on `SB-T5` is
absorbed by the parallel track.

**The consequence that has not changed:** the Phase-2 path does not run *through* the player. B/C/D are
evidence for F, not prerequisites of it — which is why Phase 1 can ship on its own schedule.

### 1.2 The hard gate is not one thing — it is three "prove it before you spend" gates

The repo's pattern (Sprint 1's plan data, V3 Sprint A's `bible.db`, Alt Sprint A's M'Cheyne) is
*risky-verifiable-asset-first*. Audio's risky asset costs **$250–800 and cannot be un-paid**, and a
pronunciation defect means paying again (R-AUD-3). So the pattern inverts: the gates come first and the
asset comes fifth.

| Gate | Sprint | What it proves | Cost if it fails |
|---|---|---|---|
| **G-POSTURE** — the merged-manifest delta matches ESpec §12 exactly | **A** (`SA-T0` = Diego's `AUD-A-0`) | No `INTERNET`; exactly two new foreground-service permissions; three exported components accounted for. Diego **could not build this** (`platforms;android-37` unpublished) — it is an unverified claim until a build machine runs it. | Re-plan the delivery mechanism. Cheap now, catastrophic after F. |
| **G-DELIVERY** — Play accepts 66 asset packs and an audio correction's real byte cost is measured | **A** (`SA-T6`/`SA-T7`, two internal-track cycles) | `requestFetch`/`assetsPath()` work; eviction and sideload behave; and **what a re-render actually pushes to existing users on an app update** (V12/§7.5 — the real price of D-AUD-3). | Re-group to 8 section packs (a one-function edit, D-AUD-E-5) — free *before* F, a re-render *after* it. |
| **G-VOICE** — the pronunciation pilot is in the owner's ears and signed off | **E** (`SE-T3`, M-AUD-6) | The **ElevenLabs** voice is fit for scripture; the proper-noun lexicon is built from what the pilot exposed; the ASR thresholds are calibrated on real output. | R-AUD-3 realised: the whole corpus re-rendered. **This is the gate the money is on.** |
| **[A1] G-PACKSHAPE** — a pack describes itself, and the app plugs it in without knowing which pack it is | **A** (`SA-T9`, on placeholder bytes) → *proven* **F** (`SF-T5`, synthetic second voice) | The pack manifest's shape survives a real Play cycle and a real `assetsPath()` resolve **before** a byte of audio is rendered against it. | Getting the manifest shape wrong after F means **re-packaging, possibly re-rendering, the corpus** — the shape is baked into ~850 MB of artifact. Free to validate now. |

`AudioTimingVerificationTest` (M-AUD-2, Sprint F) is the *release* gate in the lineage of the plan, text
and M'Cheyne gates — it becomes the project's **sixth** standing data gate. But it cannot exist before
the artifact does, which is exactly why the three gates above have to carry the risk instead.

### 1.3 The parity bar — this app is in production (cross-cutting, every sprint)

**1.5.1/10501 is live to 177 countries at 100%.** Adapting the alt-schedules §1.2 invariant:

> The existing suite — **735 tests** at the start of this epic, including the **five standing data/Room
> gates** (Bible Companion plan **11**, M'Cheyne **10**, Chronological **8**, `BibleTextVerificationTest`
> **18**, `BibleDatabaseRoomOpenTest` **5**) and every reader/schedule/stats/widget/reminder pin — must
> pass **UNCHANGED** with no session live. A shipped-behaviour pin that goes red on an audio change is a
> **design defect, not a test to update.** `ListenState.Idle` composes nothing (D-AUD-UI-1), so the
> resting app is byte-for-byte what shipped.

The four shipped surfaces this epic touches, and what protects each:

| Shipped surface | Sprint | What protects it |
|---|---|---|
| Reader verse tap → external app (Sprint H, taught by the footer hint) | C | The mode is **session-scoped** in one place (`VerseItem`, D-AUD-E-14); outside a session the behaviour is pinned byte-for-byte; the two named TalkBack custom actions mean screen-reader users gain, never lose (D-AUD-UI-4). **Owner-gated on OQ-AUD-4.** |
| Schedule reading card tap → open + mark (Sprint 00O) | C | The ▶ consumes its own click and never reaches `Card(onClick)`; card-tap semantics pinned unchanged; the ▶ is a trailing element in the existing ≥48 dp row, so height is **structurally** unchanged (D-AUD-UI-7, FR-AUD-17). |
| The Schedule's one-screen fit + 45 % stats cap (S15/S16/S18/S20 + the N-stream fix) | C | Idle costs 0 dp; the cap swap (45 % → 30 %, D-AUD-UI-3) is a **cap** change only, applies solely while a session is live, and returns more height than the bar takes (Priya §9: +103 dp vs +53 dp at N=4). **Owner-gated.** |
| `ReaderAudioSlot` (reserved by D-V3-14) | C | Retired as a bottom bar; its *intent* is honoured at the root (D-AUD-UI-2). **Owner-gated.** |
| The 12 MB bundle CI gate (D-V3-20) | A | The number stays **verbatim** on an audio-less PR build; the release job adds a structural "**zero audio bytes under `base/`**" assertion so the guarantee cannot be satisfied by accident (D-AUD-E-7). |
| `MarkReadOnOpenUseCase` (D-O-1) | C | Reused **unchanged**. No new marking rule enters the codebase (FR-AUD-15, D-AUD-E-17). |

### 1.4 Phase 1 is a release, not a scaffold — and here is the honest read

Phase 1 (Sprints B–D) ships the entire player and the entire follow-along experience on
`android.speech.tts`: **$0, zero bytes, zero new exported Play components, one new permission**
(`FOREGROUND_SERVICE_MEDIA_PLAYBACK`, ours). Because `SimpleBasePlayer` lives in `media3-common`
(Diego's V9), Phase 1 is a *real* media3 player — lock screen, notification, headset, Bluetooth, focus
all work in Phase 1 and are unchanged in Phase 2. Phase 2 adds **one class** (`FileVersePlayer`).

**Is it good enough to ship alone? My recommendation: yes — ship it, on the honest name.**

- It is genuinely useful today for the commuter, the kitchen and the tired-eyes case. Stock TTS on a
  modern Pixel/Samsung reads prose acceptably.
- It is **permanent, not throwaway**: it is Phase 2's fallback for undownloaded passages, evicted packs
  and sideloaded installs (D-AUD-12), so none of it is discarded.
- Shipping it buys the **M-AUD-11 adoption signal before the spend** — the owner learns whether people
  actually press play, on the Schedule or in the Bible tab, before four figures leave his account.
- The honest limits are real and must be stated to users only by naming the voice truthfully ("Device
  voice", D-AUD-5) and never promising quality: proper-noun pronunciation in the KJV will be visibly
  imperfect (R-AUD-7), quality varies by OEM, and some devices have no usable engine at all.

**The owner's decision point is `SD-T3`** and it is explicit: ship Phase 1 standalone now, or hold it
and ship it with Phase 2. If the answer is "hold", B and C still happen in this order and for these
reasons — only D moves. Nothing about the plan changes except when users see it.

### 1.5 Planning decisions (`D-M-AUD-n`) — mine, and flagged as such

These are **sequencing** calls, not product or architecture calls. Each is mine to make and reversible.

- **D-M-AUD-1 — `AUD-C-1` (the placeholder internal-track upload) moves to Sprint A and is the first
  calendar-bound activity.** It needs **two** Play review cycles (one to prove packs work, one to
  measure the update-patch delta of a changed pack), a real device, and the owner's hands on the Play
  Console. Two cycles started in Sprint A finish while B and C are being built. Resolves Diego's
  `OQ-AUD-E-4`.
- **D-M-AUD-2 — the pack spike is built from a throwaway branch (`spike/aud-pack-probe`) that is NEVER
  merged, and is uploaded to the Play `internal` track only.** Production and alpha are untouched;
  `release.yml` (tag → alpha) is untouched; the spike builds burn two throwaway version codes
  (10502/10503) and are **never promoted**. Only durable artifacts merge to `main`: the pack-module
  generator, `AudioPackPlan`, the CI gate split, and the written findings. Rationale: the probe needs a
  release-signed bundle to exercise PAD at all, and a debug-only probe cannot ride a release build —
  so the probe is a spike, is labelled a spike, and dies as one.
- **D-M-AUD-3 — `com.google.android.play:asset-delivery` does NOT enter a production release until
  Phase 2.** It contributes `FOREGROUND_SERVICE_DATA_SYNC` plus two exported Play components (§12.2)
  for functionality Phase 1 does not use. Phase 1 ships **media3 only** — one new permission, zero new
  exported Play components. The dep is present in the Sprint-A spike branch and returns for real in
  Sprint G. *(Consequence: `SA-T0` runs the manifest diff **twice** — media3-only, and media3 +
  asset-delivery — so both release postures are evidenced. Both numbers go in the handoff.)*
- **D-M-AUD-4 — Phase 1 and Phase 2 are two MINOR releases.** Phase 1 = **1.6.0 / 10600**, Phase 2 =
  **1.7.0 / 10700** (D-S9-3). Both go tag → alpha → device pass → **staged** production rollout, not
  straight to 100 %: Phase 1 changes a shipped gesture and adds a persistent bar to a live app.
  D-AUD-E-19 (audio content never changes in a PATCH) becomes a standing release rule from 1.7.0.
- **D-M-AUD-5 — whole-day playback SHIPS in Phase 1** (Maya's `OQ-AUD-8`, pointed at me). It is ~a day
  of work (one `PlayUnit` arm + a top-bar action), it costs 0 dp, and it is the commuter's natural
  unit. It is also the cleanly severable piece: if the device pass shows the Schedule top bar crowded
  at large font (Priya §6, device-pass item 3), deleting `listen-day` removes it with **no other
  change**. Owner may overrule at C's start; the fallback is free either way.
- **D-M-AUD-6 — a temporary dev entry point in Sprint B, comment-marked and deleted in C.** B has no
  designed UI, and a sprint that cannot be demonstrated cannot be judged. B ships a
  `SPRINT-B TEMPORARY` action (tag `listen-dev`) exactly as V3 Sprint C shipped `open-reader-dev`.
  Its deletion is an acceptance criterion of C.
- **[A1] D-M-AUD-7 — pack plug-and-play generality lands INSIDE the Phase-2 sprints from their first
  line; it does NOT get its own sprint; and its *shape* is validated in Sprint A on placeholder bytes.**
  This repo has learned the lesson twice and both times the same way round: V3's `BibleTextSource` seam
  (D-V3-3) made the text source the swap unit before a second source existed, and **Alt Sprint C
  generalized every surface to N streams before a fourth-stream plan shipped — after which the
  Chronological plan (N=1) shipped with *zero production code change*.** The generalization here is
  **shape, not volume**: a self-describing pack manifest, a registry that mirrors
  `PlanRegistry`/`PlanDescriptor`, and every consumer routed through one resolver. Written that way from
  the start it is nearly free; retrofitted after Sprint G it is a rewrite of `AudioPackPlan`,
  `PackFileLocator`, `PackVerseTimingSource`, `ResolveAudioAvailabilityUseCase`, the Settings voice row
  and the Downloads screen. And the part that is genuinely expensive to change late is the **on-disk /
  pack layout**, because it is baked into ~850 MB of rendered artifact — which is exactly why the shape
  goes into the Sprint-A placeholder payload and through a real Play review cycle *before* the render.
  **Acceptance, in the Alt-Sprint-F idiom: adding a second voice must be data + an asset pack and
  nothing else.** Proven in `SF-T5` by a synthetic second-voice fixture, mutation-pinned. Tickets
  touched: `SA-T2`, `SA-T4`, `SA-T5`, `SA-T6`, **`SA-T9` (new)**, `SB-T7`, `SF-T1`, `SF-T4`, `SF-T5`,
  `SG-T2`, `SG-T3`, `SG-T5`, `SG-T6`, `SG-T7`. *(Diego owns the manifest/registry design; this decision
  owns only where it lands.)*
- **[A1] D-M-AUD-8 — the epic lives on a long-lived integration branch, `feature/read-aloud`; sprint
  branches merge into it; nothing merges to `main` until a phase ships.** Full mechanics — merge
  cadence, the conflict-prone file list, CI, and what "ship Phase 1" actually consists of — are **§9**.
  The one-line consequence for sequencing: **the merge to `main` at Sprint D *is* the Phase-1 go/no-go
  moment** (`SD-T3`), because `release.yml` is tag-triggered from `main`.
- **[A1] D-M-AUD-9 — RECOMMENDED, not decided: a minimal entry-point feature flag, added in Sprint C.**
  The owner asked for a branch and the branch is the decision; this is not a counter-proposal. But the
  two are not exclusive, and a flag becomes *actively wanted* the moment Phase 1 ships at 1.6.0 while
  Phase 2 is still in flight — because from that point `main` carries half-built Phase-2 code in
  **released** builds. See §9.5 for the recommendation, its scope (one boolean at the composition root,
  gating entry points only — not the service, not the domain) and its honest cost.

### 1.6 Where ESpec §16 supersedes the PRD (sequencing-relevant only)

Tickets are written against the **right-hand** column. Maya is applying these concurrently.

| PRD says | §16 corrects it to | Sprint affected |
|---|---|---|
| §8's unit table incl. "Today's readings ~2.4 MB" | An asset pack is the **atomic** unit — today's readings span 3–4 books ≈ 40–120 MB. Units become **book / testament / everything / "the books your next 30 days need"**; the "today's readings" row is deleted. | **G** (the downloads screen's unit list). `OQ-AUD-E-1` must be closed before G's tickets are written. |
| M-AUD-3 "no new permission" | **"No `INTERNET`; exactly two new foreground-service permissions, both required by shipped functionality; no data-safety change."** | **A** — this *is* `SA-T0`'s pass condition. `OQ-AUD-E-3` therefore blocks the **first** sprint. |
| R-AUD-4's witness = Phase-1 TTS boundaries | That compares boundaries in **different audio** and proves nothing. The witness is **ASR alignment over the same audio** (D-AUD-E-8). | **F** — this is the corpus gate's second-source discipline. |
| FR-AUD-10 "31,102 verses (+117)" | The index covers **31,219 rows**; the gate asserts 31,219 or superscriptions become forgettable. | **F**. |
| FR-AUD-2 "previous/next verse-or-chapter" | Transport buttons = **chapter**; verse movement is **in-app only** (D-AUD-E-4). Visible behaviour, not an internal detail. | **B/C** (transport semantics + Priya's sheet labels, which currently read "Previous verse"). |
| D-AUD-3 priced as "slow but free" | Not free: an audio change rides the **automatic app update with no consent prompt** (§7.5). D-AUD-E-19 should become a product commitment. | **A** measures it (`G-DELIVERY`); **E** confirms it before the spend (`OQ-AUD-E-2`). |

### 1.7 Team roster + the work that has no owner

| Name | Agent | Owns in this plan |
|---|---|---|
| **Diego** | `android-architect` | The two seams (`AudioReadingController`, the media3 `Player`), `PlaybackService`/`MediaSession`, the queue + `PlayUnit` stop rules, `TtsVersePlayer`, `activeVerseId` combine, final design word. |
| **Avery** | `android-platform-senior` | `AudioFocusCoordinator`, the foreground-service lifecycle + notification channel 3003, `PlayAssetPackRepository` + `PackFileLocator`, `FileVersePlayer`, StrictMode/off-main, the `SettingsRepository` keys. |
| **Priya** | `android-ui-senior` | The Listen bar + sheet, the stats-cap swap, the verse highlight + yielding autoscroll + follow chip, the three entry points, Settings → Read aloud, the Downloads screen, the a11y semantics contract, faithful-presentation review. |
| **Sam** | `android-feature-eng` | `StartReadAloudUseCase` + marking wiring, the session-scoped verse-tap mode, the reactive footer hint, speed + sleep timer plumbing, the download-consent dialogs. |
| **Riley** | `android-qa-eng` | The **M-AUD-1 queue gate**, the **`AudioTimingVerificationTest`** corpus gate, the a11y-gate + guilt-copy ban-scan extensions, the M-AUD-5 zero-unconsented-bytes test, mutation verification, the consolidated device passes. |
| **Jordan** | `devops-eng` | `SA-T0` on a real build machine, the CI gate split, the `audio-bundle` release job + timeout raise + dropping the AAB artifact upload, the corpus-asset fetch + SHA verification, the `ci/actions-node24-bump` PR, version bumps + rollouts. |
| _Maya / Owner_ | `senior-pm` | The §16 amendments; `OQ-AUD-1/3/4/5/6/7/8/9`; `OQ-AUD-E-1/2/3`; M-AUD-6 voice sign-off; the §11 string tone sign-off; the Play Console cycles. |
| **⚠ UNASSIGNED** | — | **The render machine, the vendor account and the ~$250–800.** See §8. |

---

## 2. Sprint sequence overview

Each sprint has **one owner-visible outcome**, states the capability it unlocks, and is an
independently-green increment. Sizes are **relative engineering effort** (S/M/L/XL); items whose real
cost is **calendar we do not control** are flagged separately, because no amount of effort shortens a
Play review.

> **[A1] Every sprint below is executed on a branch off `feature/read-aloud` and merges back into it,
> never into `main`, until a phase ships (§9).** The sprint sequence itself is unchanged by that; only
> where the commits land is.

| # | Sprint | Owner-visible outcome (one line) | New capability unlocked | Size | Calendar | Owners | Depends on | Gate to exit |
|---|---|---|---|---|---|---|---|---|
| **A** | **Posture proof + delivery plumbing on self-describing placeholder packs** *(cheap, reversible, **starts the epic's longest clock**)* | "We now know, with evidence, that audio delivery costs us no `INTERNET`, that Play accepts our pack layout, that a pack tells the app what it is without any per-pack code, and what an audio correction actually pushes to a user's phone." | The three facts the whole Phase-2 architecture rests on are **measured, not assumed**; the 12 MB gate is re-pointed without losing its meaning; the voice-scoped pack mapper and the **pack manifest shape** exist and have survived a real Play cycle. | **S–M** | **L — two serial Play review cycles; [A1] now the epic's dominant constraint** | Jordan (lead), Avery, Owner (Console) | — | **G-POSTURE** + **G-DELIVERY** + **G-PACKSHAPE** (findings `SA-F1`/`SA-F2`/`SA-F3` recorded) |
| **B** | **The player spine on the device voice** *(cheap, reversible)* | "Press the dev button and the phone reads Genesis 1–2 aloud — screen off, lock-screen controls, and Psalm 119 days play exactly their verses." | A real media3 player exists behind one app-facing seam; the queue is built once from `PortionVerseBridge`; the FR-AUD-4 stop rules are one `when`. **~80 % of the feature's engineering, at $0.** | **L** | — | Diego (lead), Avery, Riley | A's `SA-T0` only | **M-AUD-1 queue gate green with zero audio bytes in the repo** |
| **C** | **Phase 1 complete: Listen bar, follow-along, entry points, marking** | "Read aloud is a finished feature: play from a reading, the day, or the reader; the verse being spoken is highlighted and the page follows; the reading is marked read." | The whole designed UX (D-AUD-UI-1…13) on the device voice. **This is the Phase-1 release candidate.** | **XL** | — | Priya (lead), Sam, Diego, Riley | B; **OQ-AUD-4/5/7/8 + D-AUD-UI-2/3 answered** | A11y gate + ban-scan extended and green; parity suite UNCHANGED; `listen-dev` deleted |
| **D** | **Phase 1 device pass + release → 1.6.0** *(the owner's ship/hold decision)* | **"Read aloud is live for users, at $0 spend."** | The feature is in the world; the M-AUD-11 adoption signal starts accruing **before** any money is committed. | **S** | **M — owner listening + staged rollout** | Riley (pass), Owner, Jordan (release) | C; **OQ-AUD-3** | M-AUD-9 hands-free pass + the two-voice TalkBack test (Priya's #12) + one-screen fit at N=4 |
| **E** | **[A1] Pronunciation pilot + the spend decision** *(**the last reversible point**; no longer a bake-off — `OQ-AUD-1` is answered)* | "The owner has heard the ElevenLabs voice reading Mahershalalhashbaz and has said go or no-go, with a number attached." | The pronunciation lexicon exists; the ASR thresholds are calibrated on real output; the timestamp contract is confirmed; AR-AUD-1 is recorded. **The render is authorised or it is not.** | **S–M** (+ a small, reversible pilot spend) | **S–M — owner listening turnaround** | Owner (decides), Riley, Diego | A's findings; **B green** (no longer C/D — see §1.1) | **G-VOICE**: M-AUD-6 sign-off + the §4 checklist all ✅ |
| **F** | **The corpus render + the offline audio gate** *(**the one-way door — ~$250–800**)* | "Every one of the 1,189 chapters has audio and a per-verse timing index, a test proves it without downloading a byte, **and a synthetic second voice proves a new one is data, not code.**" | The project's **third core content asset** exists and is provably correct; `AudioTimingVerificationTest` becomes the **sixth** standing data gate; **[A1] the plug-and-play claim is proven, not asserted.** | **XL** | **M — render + triage wall time** | Riley (gate), the render-machine owner (§8), Jordan (CI/assets) | **E signed off — no exceptions** | `AudioTimingVerificationTest` green + every over-threshold chapter triaged + **the synthetic second voice resolves end-to-end with zero production code change** |
| **G** | **Phase 2 playback + the download surface** | "Choose the high-quality voice, download by book or in bulk, see exactly what it costs, delete it in two taps — and Psalm 119:1–40 plays exactly its verses out of a whole-chapter file." | `FileVersePlayer` + clipping; the full download product surface; the degradation ladder resolving to `HighQuality`. | **L** | — | Avery (player/packs), Priya (downloads UI), Sam, Riley | F; **OQ-AUD-E-1** | M-AUD-5 (zero unconsented bytes) JVM-pinned; the degradation ladder green at every rung |
| **H** | **Phase 2 hardening + release → 1.7.0** | "The good voice is live, the install is still 8 MB, and the manifest still has no `INTERNET`." | Phase 2 in the world; the release pipeline carries ~850 MB without carrying it on every PR. | **M** | **M — device pass + rollout** | Riley (pass), Jordan (release), Owner | G | Base-module ≤ 12 MB + **zero audio bytes under `base/`** + no `INTERNET`; consolidated device pass |

**Dependency notes**

- **A is first but is not a blocker for B beyond `SA-T0`.** B needs the manifest go/no-go and nothing
  else; A's two Play cycles run *underneath* B and C. **[A1] Dispatch `SA-T5`/`SA-T6` before `SB-T1`** —
  they start the epic's longest clock (§1.1).
- **B → C → D is strictly serial.** C is the whole design; D is its verification and release.
- **[A1] E and F now overlap C and D.** With `OQ-AUD-1` answered and the timings coming from the API,
  E's entry condition is **B green**, not D shipped. The data track (A→E→F) and the product track
  (B→C→D) converge at **G**, which needs both.
- **E is the wall between reversible and irreversible.** Nothing in F may start before E exits.
  A sprint that "just gets a head start on the render" defeats the entire point of the sequence.
- **G is peer-free** — it depends on F absolutely (there is nothing to download or clip without the
  artifact) and it is the only sprint where the download UX becomes verifiable.
- **H adds no feature scope**, only verification, sign-off and release.
- Each sprint is one session in the one-sprint-per-session rhythm. **A and B are fully ticketed below**;
  C is ticketed at title level with its owner gates named; D–H are goal + ticket-title level and are
  decomposed at the start of their own sessions — exactly as the V1/V3/Alt plans did.

---

## 3. Detailed ticket breakdown

### Sprint AUD-A — Posture proof + delivery plumbing on placeholders  *(G-POSTURE + G-DELIVERY)*

**Outcome goal:** the two unverified claims the Phase-2 architecture rests on become measured facts, and
the Play review clock starts. Small code, large calendar. Nothing user-visible ships.

**Sprint-level acceptance:**
- `SA-T0` run on a real build machine; the delta matches ESpec §12 **exactly**, for **both** postures
  (media3-only and media3 + asset-delivery) per D-M-AUD-3. **Anything else = stop and re-plan.**
- The PR/push `release-bundle` job is unchanged in command and **unchanged at `CEILING=12000000`**, and
  is now provably audio-less.
- `AudioPackPlan.packsFor` exists as a pure, JVM-tested function generated from `BookCatalog` — **no
  second book table** (D-S9-1 / D-S13-1 / Sprint G discipline) — and is **[A1] voice-scoped** from its
  first line (D-M-AUD-7).
- **[A1]** Every placeholder pack carries a **self-describing manifest**, and the on-device probe
  resolves what a pack is **by reading it**, never by knowing its name (`SA-T9`, finding `SA-F3`).
- Internal-track upload #1 submitted; findings `SA-F1` (packs work), `SA-F2` (the real update-patch
  size of a changed pack) and `SA-F3` (manifest discovery works on a real device) recorded in the
  handoff **before Sprint E exits**.
- The full parity suite (735 tests, five data gates) green UNCHANGED — **[A1] on the integration
  branch, after a fresh `main` merge** (§9.2).

#### Tickets

**SA-T0 — The merged-manifest diff, on a build machine (Diego's `AUD-A-0`) — GO/NO-GO**
- **Owner:** Jordan (with Diego). **Size:** M. **Deps:** none. *(The first ticket of the epic.)*
- **Scope:** Resolve the environment blocker first — Diego could not build the merged manifest because
  `platforms;android-37` is not published to the SDK repository (`sdkmanager` fails), so `compileSdk = 37`
  is unsatisfiable on his machine. Find a machine/CI image where it resolves; **if none exists, that is
  itself a finding** and the `compileSdk` pin goes back to Diego before anything else in this epic
  proceeds. Then run the §0 ritual twice: (a) baseline vs **+ media3** (the Phase-1 posture, D-M-AUD-3);
  (b) baseline vs **+ media3 + asset-delivery** (the Phase-2 posture). Diff `uses-permission`,
  `<service>`, `<receiver>`, `<activity>`, `<provider>`.
- **Acceptance:** posture (a) adds **`FOREGROUND_SERVICE_MEDIA_PLAYBACK` only** (ours, for
  `PlaybackService`) and **zero** components beyond ours; posture (b) additionally adds
  `FOREGROUND_SERVICE_DATA_SYNC` + Play's three components; **no `INTERNET` in either**; the forced
  version bumps (`work-runtime 2.7.1→2.9.1`, `basement`, `tasks`, `core-common`) are manifest-neutral
  as Diego's V6 claims. Both diffs pasted verbatim into the handoff. **CI/build-provable.**
- **Blocked by:** `OQ-AUD-E-3` — we cannot run a go/no-go until "go" is defined. Get the restated
  M-AUD-3 confirmed **before** this ticket runs.

**SA-T1 — Land the `ci/actions-node24-bump` PR**
- **Owner:** Jordan. **Size:** S. **Deps:** none.
- **Scope:** The standing CI debt (branch `ci/actions-node24-bump`, commit `3753b08`): GitHub forced
  Node 20 → 24 from 2026-06-16 and our actions still warn. Diego's §9.3 note is right — a release job
  that is about to grow to ~25 minutes of downloading is the worst possible place to discover a broken
  action. Land it now, while the release job is still 5 minutes.
- **Acceptance:** PR reviewed and merged; a tagless CI run green on the bumped actions. **CI-provable.**

**SA-T2 — `AudioPackPlan` + the generated pack modules (D-AUD-E-5, D-AUD-E-1) — [A1] now voice-scoped**
- **Owner:** Avery (with Jordan on Gradle). **Size:** M. **Deps:** SA-T0.
- **Scope:** `audio/domain/AudioPackPlan.kt` — a **pure** mapper: **(voiceId, verse range / book /
  chapter) → pack name(s)**, where the name is `audio_<voiceId>_<usfmCode lowercased>` derived from
  `BookCatalog` **and the voice registry**. Plus `audio-packs/gen/build-packs.gradle.kts` generating the
  code-free module dirs from `book_catalog_export.json` (the V3 export, already committed) **× the
  registered voices**. `audio-packs/.gitignore` excludes `src/main/assets/**` **permanently**
  (D-AUD-E-6 — blobs never enter git). `settings.gradle.kts` includes `audio-packs/*` **only when
  `-PwithAudio=true`**.
- **[A1] Why the voice dimension is here and not in G (D-M-AUD-7):** this function is the *single* home
  of pack naming. If it is written with a voice parameter now, a second voice is a registry row; if it
  is written without one, adding it later changes every caller *and* the generated module set *and*
  potentially the shipped pack names — i.e. it re-packages the corpus. It costs one parameter today.
- **Acceptance:** `packsFor(voiceId, …)` resolves all 1,189 chapters to exactly one existing pack name
  per registered voice, and every name is derivable from a `usfmCode` + a registry voice id; a default
  `./gradlew assembleDebug`/`bundleRelease` sees no audio-pack module at all. **JVM-provable.**
- **Tests/mutation:** pack-mapping totality (the Sprint-F gate's assertion 10, landed early), asserted
  over **two** registered voices (one real, one synthetic) so the dimension is exercised from day one; a
  mutation renaming a pack, dropping a book, or **collapsing the voice dimension** must red it. *This
  function is the reason re-grouping to 8 section packs later is a config edit — do not let any caller
  compute a pack name itself.*

**SA-T3 — The CI gate split (D-AUD-E-7)**
- **Owner:** Jordan. **Size:** M. **Deps:** SA-T2.
- **Scope:** (a) PR/push `release-bundle` unchanged: same command, **`CEILING=12000000` verbatim**, now
  guaranteed audio-less by the conditional include. (b) A new tags-only `audio-bundle` job skeleton with
  the three assertions — base-module ≤ 12 MB (`unzip -v` over ` base/`), **zero `.opus|.ogg|.mp3|.m4a`
  under `base/`** (a *structural invariant*, not a size heuristic), total ≤ 1.2 GB — running against the
  placeholder packs for now. `timeout-minutes: 45`; **no AAB artifact upload** on release runs.
- **Acceptance:** the everyday gate keeps its number and its history; the new job passes on placeholders
  and **fails** if a fake `.opus` is planted under `base/`. **CI-gating.**

**SA-T4 — Four `SettingsRepository` keys + the fakes (D-AUD-E-15)**
- **Owner:** Avery. **Size:** S. **Deps:** none.
- **[A1] Note (D-M-AUD-7):** `audio_voice_source` stores a **voice id**, not a two-value
  device/high-quality enum, and an **unknown stored id degrades to `DEVICE`** — the exact
  `bible_provider` / `selected_plan` idiom (degrade, never crash). This is the difference between
  "a second voice is a registry row" and "a second voice is an enum change plus a migration."
- **Scope:** `audio_voice_source` (string, default `DEVICE`), `audio_wifi_only` (bool, default **true**),
  `audio_speed` (float, default 1.0, clamped 0.75–2.0), `audio_sleep_timer_minutes` (int, 0 = off). Same
  absent-key idiom as `show_streaks`/`persistent_notification_enabled`: an explicitly stored value always
  survives. **No Room schema change, no new database, no new DataStore file.** Add all four to
  `testing/FakeSettingsRepository` in the same commit.
- **Acceptance:** absent-key defaults pinned; the clamp pinned at both ends; stored values survive.
  **JVM-provable.**
- **Tests/mutation:** each `?:` default and the clamp bounds mutated and killed.

**[A1] SA-T9 — The self-describing pack manifest, validated on placeholder bytes (NEW; D-M-AUD-7, G-PACKSHAPE)**
- **Owner:** Avery (implementing **Diego's** manifest/registry design — this ticket owns the *validation*,
  not the schema). **Size:** S. **Deps:** SA-T2. *(Sequenced **before** SA-T5 — the spike payload must
  contain it.)*
- **Scope:** Every generated pack's placeholder payload carries a small manifest — the pack's own
  statement of *what it is*: voice id, book, chapter range, codec/sample rate, artifact version, and the
  location of its timing sidecar. The shape mirrors the two idioms this codebase already trusts:
  `registry.json` + `PlanDescriptor` (a plan declares its own shape, D-ALT-2/3) and the `bible.db`
  `translation` table read raw behind `ReaderVersionSelector` (D-N-3 — one artifact renders a static
  label, more than one renders a switcher, with **no per-artifact code**). App-side, a **voice registry**
  enumerates installed voices from the manifests of whatever packs are present; nothing in the app
  branches on a pack's identity.
- **Why in Sprint A and not G:** the manifest is a JSON file next to bytes that are already being
  uploaded. Putting it in costs almost nothing here, and it buys the one thing that cannot be bought
  later — **the shape gets exercised through a real Play review cycle and a real `assetsPath()` resolve
  on a real device, before ~850 MB is rendered against it** (G-PACKSHAPE, §1.2).
- **Acceptance:** the probe (`SA-T6`) identifies a downloaded pack **solely from its manifest**; renaming
  a pack's payload directory without changing its manifest does not change what the app believes it is;
  two synthetic "voices" of placeholder packs coexist and are enumerated. Finding **`SA-F3`** recorded.
  **JVM-provable** (the registry + manifest parsing) **+ device-pass** (discovery after a real fetch).
- **Tests/mutation:** absent manifest ⇒ the pack is reported unusable, never guessed at; unknown
  manifest fields ⇒ clean-fail, not crash; a mutation that derives the voice from the **pack name**
  instead of the manifest must be killed — that mutation is precisely the "logic dependent on which
  asset is used" the owner ruled out.

**SA-T5 — The throwaway pack probe (spike branch only, D-M-AUD-2)**
- **Owner:** Avery. **Size:** M. **Deps:** SA-T2, **SA-T9**. **Branch:** `spike/aud-pack-probe` — **never merged.**
- **Scope:** On the spike branch only: add `asset-delivery` + `asset-delivery-ktx` 2.3.0, fill each pack
  module with a small deterministic placeholder payload **plus its `SA-T9` manifest**, and add a crude
  debug surface that calls `requestFetch`, reports `AssetPackStates`, resolves
  `getPackLocation()!!.assetsPath()`, **reads the manifest off disk and reports what it says**, and
  exercises `removePack`. Comment-marked `SPIKE — DO NOT MERGE` at the top of every file.
- **Acceptance:** a release-signed `.aab` builds with the packs at `-PwithAudio=true`. **Build-provable.**
- **[A1] Note:** carry **two synthetic voices' worth** of packs in the spike if the pack count permits —
  it turns `SA-F1`'s pack-count answer into an answer about the multi-voice case (RM-14), which is the
  case we will actually be in.

**SA-T6 — Internal-track upload #1 + on-device findings (`SA-F1`) — Diego's `AUD-C-1`, part 1**
- **Owner:** Owner (Play Console) + Avery (device). **Size:** S effort / **L calendar**. **Deps:** SA-T5.
- **Scope:** Upload the spike bundle (throwaway versionCode 10502) to the **`internal`** track — never
  alpha, never production, never promoted. When it clears review, install on a real device and record:
  does Play accept the pack count at all (**[A1] at the multi-voice count if the spike carries two
  synthetic voices**); does `requestFetch` succeed; does `assetsPath()` resolve; **[A1] does the app
  identify a fetched pack purely from its manifest (`SA-F3`)**; what does a **sideloaded** copy of the
  same bundle report (`REQUIRES_USER_CONFIRMATION`? failure?); can a pack be removed and re-fetched;
  and — since our largest pack is ~47 MB, well under Play's 200 MB threshold (V11) — confirm Play's own
  `WAITING_FOR_WIFI` gate **never fires**, i.e. **Wi-Fi-only is ours to enforce** (§7.4 edge 2).
- **Acceptance:** findings **`SA-F1`** and **`SA-F3`** written into the handoff with each answer. If Play
  rejects the pack count, `AudioPackPlan` re-groups to 8 section packs (16 for two voices) — **and that
  change costs nothing today and a re-render after Sprint F.** **Device-pass; not JVM-provable.**

**SA-T7 — Internal-track upload #2: measure the real update-patch cost (`SA-F2`) — `AUD-C-1`, part 2**
- **Owner:** Owner (Console) + Avery (device). **Size:** S effort / **L calendar**. **Deps:** SA-T6.
- **Scope:** The ticket that prices D-AUD-3. On a device that has packs downloaded from upload #1, change
  **one** pack's placeholder payload, upload as versionCode 10503 to `internal`, let the app auto-update,
  and **measure the bytes actually transferred**. Play documents (V12) that all previously-downloaded
  packs are *invalidated* and then patched locally — Diego's honest reading is "one changed book ≈ that
  book's delta, pushed **without a consent prompt**". Confirm or refute it with a number.
- **Acceptance:** finding **`SA-F2`** recorded with the observed size. **If the observed behaviour is a
  full re-download rather than a delta, D-AUD-3 goes back to the owner BEFORE the spend** (RE-AUD-3) —
  ~850 MB pushed silently on every audio correction is a different product decision than the one that was
  made. **Device-pass; not JVM-provable.**

**SA-T8 — Handoff: `docs/sprints/sprint-aud-A-posture-delivery.md`**
- **Owner:** Jordan + Morgan. **Size:** S. **Deps:** SA-T0, SA-T6, SA-T7, **SA-T9**.
- **Scope:** Both verbatim manifest diffs; `SA-F1`/`SA-F2`/**`SA-F3`** with numbers; the pack-count
  verdict (per-book or re-group to sections, **at the multi-voice count**); the confirmation that the
  12 MB gate kept its number; **[A1] the frozen pack-manifest shape**, as validated; and the
  **G-POSTURE / G-DELIVERY / G-PACKSHAPE rows of the §4 spend checklist ticked or not ticked**. This
  document is an input to Sprint E, not a formality.

#### Sprint A subtask decomposition (~2–5 min each)

- **SA-T0:** 0a resolve `platforms;android-37` (or escalate the `compileSdk` pin). 0b baseline manifest capture. 0c media3-only diff. 0d media3+asset-delivery diff. 0e paste both into the handoff + verdict.
- **SA-T1:** 1a review + merge `ci/actions-node24-bump`. 1b confirm a green run.
- **SA-T2:** 2a `AudioPackPlan.packsFor(voiceId, …)` (pure). 2b totality test over 1,189 chapters **× two registered voices** + pack-rename and collapse-the-voice-dimension mutations. 2c `build-packs.gradle.kts` generator from `book_catalog_export.json` × voices. 2d `.gitignore` for pack assets. 2e `-PwithAudio` conditional include.
- **SA-T3:** 3a confirm PR gate unchanged + audio-less. 3b `audio-bundle` job skeleton. 3c the three assertions. 3d plant a fake `.opus` under `base/` and prove the job fails. 3e timeout 45 + drop the artifact upload. 3f **[A1]** `workflow_dispatch` trigger so the job is runnable on the integration branch (§9.3).
- **SA-T4:** 4a four keys + defaults (**voice id, not enum; unknown ⇒ `DEVICE`**). 4b clamp. 4c fakes. 4d default/clamp/stored-survives/unknown-id tests + mutations.
- **[A1] SA-T9:** 9a manifest emitted into every generated pack payload. 9b app-side voice registry enumerating installed voices from manifests. 9c absent/unknown-field clean-fail tests. 9d the derive-voice-from-pack-name mutation, killed. 9e two synthetic voices coexist.
- **SA-T5:** 5a spike branch + asset-delivery deps. 5b placeholder payloads + manifests (two synthetic voices if the count permits). 5c probe surface incl. manifest read-back. 5d release-signed bundle builds.
- **SA-T6:** 6a upload 10502 → internal. 6b install; requestFetch/assetsPath/remove. 6c **manifest discovery on device** (`SA-F3`). 6d sideload check. 6e Wi-Fi-gate-never-fires check. 6f write `SA-F1`.
- **SA-T7:** 7a change one pack. 7b upload 10503 → internal. 7c measure the update transfer. 7d write `SA-F2` + the D-AUD-3 verdict.

---

### Sprint AUD-B — The player spine on the device voice  *(the M-AUD-1 correctness gate)*

**Outcome goal:** a real media3 player, behind one app-facing seam, that reads a `Portion` aloud with
the device voice — screen off, lock-screen controls, correct stop rules — and **plays exactly the verses
the plan assigns**. No designed UI; one temporary dev button (D-M-AUD-6).

**Sprint-level acceptance:**
- **M-AUD-1 is green with zero audio bytes in the repo**: the Psalm 119 windows (Mar 9–12 = 1–40 /
  41–80 / 81–128 / 129–176 and *nothing else*), the two-book Jun 19 / Dec 19 portion, the 117
  superscriptions present-and-unnumbered, and stripped text only.
- The temporary `listen-dev` action demonstrably plays Genesis 1–2 with the screen off and lock-screen
  transport (device-pass, recorded for D's consolidated pass).
- Kover's filter is extended to `…audio.domain.*` (Diego §13.1) — the queue builder, availability
  resolver, pack plan and sleep timer are exactly the pure logic the floor exists for.
- Parity suite green UNCHANGED.

#### Tickets

**SB-T1 — media3 deps + `audio/` package skeleton + `di/AudioModule` (D-AUD-E-1, D-AUD-E-18)**
- **Owner:** Diego. **Size:** S. **Deps:** SA-T0.
- **Scope:** `media3-exoplayer` / `media3-session` / `media3-common` 1.10.1 in the version catalog; the
  `audio/{domain,data,ui}` tree as a **sibling of `bible/`**; `AudioModule` binding the controller
  `@Singleton` (**not** `@ActivityRetainedScoped` — playback outlives the Activity, D-AUD-E-18). No
  `asset-delivery` (D-M-AUD-3).
- **Acceptance:** Hilt graph compiles; PR bundle-size gate still green at 12 MB with media3 in
  (Diego prices it ~+1.5–2.5 MB post-R8 against ~3.9 MB headroom — **if it does not fit, that is a
  finding for Diego, not a ceiling to raise**). **JVM/CI-provable.**

**SB-T2 — The queue: `AudioQueue` + `BuildAudioQueueUseCase` (D-AUD-E-6a) — the correctness core**
- **Owner:** Diego (with Riley). **Size:** L. **Deps:** SB-T1.
- **Scope:** `AudioQueue` / `QueueChapter` / `SpokenVerse` and the **single constructor**:
  ranges from `PortionVerseBridge.rangesFor(portion)`, text from `BibleTextSource` +
  `MarkupStripper.strip`. **Never re-derives a range; never uses a chapter file's bounds.** Windows,
  superscriptions (`chapterRange` starts at verse 0), the two-book portion and N-stream plans all fall
  out of this rather than being special-cased. Feb 29 yields an empty day queue and needs no guard
  (the D-O-5 pattern).
- **Acceptance:** M-AUD-1's assertions pass. **JVM-provable.**
- **Tests/mutation:** the four load-bearing mutations Riley must kill, each by exactly its intended test:
  (i) build the range from the chapter instead of `rangesFor` → the Psalm-119 test reds; (ii) start the
  range at verse 1 → the superscription test reds; (iii) assume refs share a book → the Jun 19 test reds;
  (iv) pass `markup` instead of `strip(markup)` → the spoken-text test reds.

**SB-T3 — `PlayUnit` + the FR-AUD-4 stop rules (D-AUD-9)**
- **Owner:** Diego. **Size:** M. **Deps:** SB-T2.
- **Scope:** sealed `PlayUnit`: `PortionUnit` → stop at end of portion; `DayUnit` → advance through the
  **active plan's** N streams in order then stop; `BrowseChapterUnit` → append the next chapter via
  `GlobalChapterIndex` (already crosses book boundaries and is bounded at Gen 1 / Rev 22, D-H-2) and
  continue. One `when`, one home, fully JVM-testable.
- **Acceptance:** each arm's stop point pinned, including Revelation 22 terminating Browse and a
  1-stream plan terminating `DayUnit` after one portion. **JVM-provable.**
- **Tests/mutation:** flip Portion→continue and Browse→stop; each killed by its intended test.

**SB-T4 — `AudioReadingController` (seam 1) + `PlaybackService` + `MediaSession` (D-AUD-E-2)**
- **Owner:** Diego (controller) + Avery (service). **Size:** L. **Deps:** SB-T1.
- **Scope:** The one type the UI injects: `play(AudioQueue, PlayUnit)`, `pause`, `stop`, `seekToVerse`,
  `speed`, `sleepTimer`, and `StateFlow<PlaybackState>` carrying `activeVerseId`.
  `PlaybackService : MediaSessionService`, `@AndroidEntryPoint`, `exported="true"` with the media3
  intent filter (**the app's first exported component other than `MainActivity`** — flag it in the
  security review), `foregroundServiceType="mediaPlayback"`, notification channel `read_aloud` id
  **3003** (3001/3002 taken), `IMPORTANCE_LOW`, `CATEGORY_TRANSPORT`. Metadata title comes from the
  **existing** `ReadingFormatter` — no second formatter, so D-UI-2's singular-Psalm rule is inherited
  free. `POST_NOTIFICATIONS` reuses the S12/S22 `NotificationPermissionChecker` + launcher pattern; **a
  denial does not block playback** (in-app transport only).
- **Acceptance:** the seam is the only audio type any UI file imports; the service starts on play and
  stops itself on `Ended`/`Idle`. **JVM-provable** (state machine) **+ device-pass** (session/notification).

**SB-T5 — `TtsVersePlayer` (seam 2a) over `SimpleBasePlayer` (D-AUD-E-9)**
- **Owner:** Diego. **Size:** L. **Deps:** SB-T4.
- **Scope:** One TTS utterance per verse, `utteranceId = canonicalId.toString()`;
  `UtteranceProgressListener.onStart` **is** the verse boundary (no timing index exists or is needed);
  `onDone(lastVerseOfChapter)` advances the media item or stops per `PlayUnit`. Media items are
  **chapters**, never verses (D-AUD-E-4) — so transport next/prev = chapter, and verse movement is
  in-app only. Duration is honestly `C.TIME_UNSET` and `COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM` is **not**
  advertised, so the notification shows no scrubber in Phase 1 — a recorded decision, not a bug.
  `onRangeStart` is deliberately unused. Speed = `setSpeechRate`, clamped 0.75–2.0.
- **Acceptance:** verse-start events map 1:1 to queue verses in order; engine `ERROR` /
  `isLanguageAvailable < LANG_AVAILABLE` resolves to `Unavailable(NoEngine)` and **never throws**.
  **JVM-provable** (with a faked TTS) **+ device-pass** (real engines).

**SB-T6 — `AudioFocusCoordinator` — one home for focus + becoming-noisy (D-AUD-E-11)**
- **Owner:** Avery. **Size:** M. **Deps:** SB-T4.
- **Scope:** One `AudioFocusRequest` (API 26 = our minSdk exactly, no legacy branch) +
  `ACTION_AUDIO_BECOMING_NOISY`, used by **both** players. ExoPlayer's built-in focus/noisy handling
  stays **OFF** in Sprint G — two implementations of "duck for the sat-nav" is exactly the drift this
  codebase kills on sight (R-STREAK-5, D-S17-2, D-K-HINT-1). Attributes: `USAGE_MEDIA` +
  `CONTENT_TYPE_SPEECH`.
- **Acceptance:** duck/pause/resume transitions pinned as pure state logic. **JVM-provable** (transitions)
  **+ device-pass** (a real call, a real sat-nav).

**SB-T7 — `ResolveAudioAvailabilityUseCase` — the degradation ladder, one home (D-AUD-E-16)**
- **Owner:** Diego. **Size:** M. **Deps:** SB-T5.
- **Scope:** The single home of FR-AUD-22's order. **[A1] Stated voice-generically from day one
  (D-M-AUD-7):** *the selected voice, if its packs are present* → *any other installed voice* → *the
  device voice* → *an honest message*. In Phase 1 the first two rungs are structurally absent (no
  registry entries), so the use case resolves `DeviceVoice` or `Unavailable(reason)` — but the ladder's
  **shape** is already right, so Sprint G adds registry data, not a new branch. Every entry point
  consumes it; nobody branches on availability themselves.
- **Acceptance:** every rung reachable and pinned, including the "selected voice absent, a different one
  installed" rung exercised against a **synthetic** registry in Phase 1. Adding a real voice in G must
  require no new `when` arm. **JVM-provable.**
- **Tests/mutation:** a mutation that hard-codes the ladder to two rungs (high-quality/device) must be
  killed by the synthetic-second-voice test.

**SB-T8 — `SleepTimer` + persisted speed**
- **Owner:** Sam. **Size:** S. **Deps:** SB-T4, SA-T4.
- **Scope:** `SleepTimer` = a pure countdown → stop reason, driven by a coroutine delay, **never an
  `AlarmManager`** (D-S12-1's inexact-alarm posture is for *scheduling*; playback is not scheduled).
  Speed reads the SA-T4 key and clamps; the same normalized factor drives `setPlaybackSpeed` in G, so
  the user's setting survives the phase change (§16.7).
- **Acceptance:** timer arms/fires/cancels; end-of-chapter and end-of-portion modes resolve to the right
  stop point. **JVM-provable.**

**SB-T9 — Temporary dev entry point (D-M-AUD-6) + Kover filter extension**
- **Owner:** Sam (entry) + Jordan (Kover). **Size:** S. **Deps:** SB-T4.
- **Scope:** A `SPRINT-B TEMPORARY` action (tag `listen-dev`) that plays today's stream-1 portion, so B
  is demonstrable. Comment-marked exactly as V3's `open-reader-dev` was. Extend Kover's filter to
  `…audio.domain.*` (Diego §13.1); `audio.data.playback` stays out, like `bible/` and `ui/`.
- **Acceptance:** the button plays; **its deletion is an acceptance criterion of Sprint C.** Kover floor
  held with the new package included.

**SB-T10 — M-AUD-1: the audio queue gate (Riley)**
- **Owner:** Riley. **Size:** L. **Deps:** SB-T2, SB-T3.
- **Scope:** The offline gate, `testDebugUnitTest`, zero audio bytes: the four Psalm 119 days cover
  exactly 1–40 / 41–80 / 81–128 / 129–176; every other day covers exactly its portion's chapters; all
  117 superscriptions appear at verse 0 and are **spoken but never numbered**; the Jun 19 / Dec 19
  portion yields two books in order; spoken text is `strip(markup)` for every verse; Feb 29 yields an
  empty queue; and the stop rules terminate where D-AUD-9 says.
- **Acceptance:** green against the shipped `bible.db` + plan assets, offline, in ~1 s. **JVM-provable /
  release-gating.** *This is Phase 1's hard gate and it survives unchanged into Phase 2 — the same
  queue drives both players.*

#### Sprint B subtask decomposition (~2–5 min each)

- **SB-T1:** 1a catalog deps. 1b package tree. 1c `AudioModule` + `@Singleton` binding. 1d confirm the 12 MB PR gate still green.
- **SB-T2:** 2a models. 2b `forPortion` via `rangesFor`. 2c `forChapter` via `chapterRange` (verse 0). 2d `strip` at construction. 2e the four mutations.
- **SB-T3:** 3a sealed `PlayUnit`. 3b Portion stop. 3c Day advance-through-N-then-stop. 3d Browse continue + Rev 22 bound. 3e mutations.
- **SB-T4:** 4a controller interface + `PlaybackState`. 4b `PlaybackService` + manifest entry. 4c channel 3003. 4d `ReadingFormatter` metadata. 4e `POST_NOTIFICATIONS` reuse + denial-still-plays.
- **SB-T5:** 5a `SimpleBasePlayer` skeleton. 5b per-verse `QUEUE_ADD` + utterance ids. 5c `onStart` → `activeVerseId`. 5d `onDone` → advance/stop. 5e no-engine state. 5f speed clamp.
- **SB-T6:** 6a `AudioFocusRequest`. 6b becoming-noisy receiver. 6c duck/pause/resume transitions + tests.
- **SB-T7:** 7a the ladder. 7b Phase-1 rungs. 7c consumers.
- **SB-T8:** 8a countdown. 8b end-of-chapter/portion modes. 8c persisted speed.
- **SB-T9:** 9a `listen-dev`. 9b Kover filter.
- **SB-T10:** 10a Psalm-119 four days. 10b whole-portion coverage. 10c superscriptions both directions. 10d two-book portion. 10e strip invariant. 10f Feb 29 + stop rules.

---

### Sprint AUD-C — Phase 1 complete: Listen bar, follow-along, entry points, marking  *(the release candidate)*

> Ticketed at title level with acceptance notes; fully decomposed at C's session start, **once the five
> owner gates below are answered**. C is the largest single sprint in the epic and the only one that
> touches four shipped surfaces (§1.3).

**Owner gates that must be answered before C starts:** `OQ-AUD-4` (verse-tap gesture), `OQ-AUD-5`
(announce the reference? speak verse numbers? — it changes what B's queue enqueues, so answer it with B
if possible), `OQ-AUD-7` (the feature noun), `OQ-AUD-8` (whole-day — my recommendation is ship, D-M-AUD-5),
and Priya's `D-AUD-UI-2` (retire `ReaderAudioSlot`) + `D-AUD-UI-3` (the stats-cap swap).

**Outcome goal:** the designed feature, complete, on the device voice.

**Candidate tickets (titles):**
- **SC-T1 — `ListenState` + the Listen bar in `RootScaffold` (D-AUD-UI-1)** (Priya) — 56 dp, docked above
  the `NavigationBar`, **composes nothing when `Idle`** (the resting-state guarantee, pinned as an
  *absence* in the S16 idiom); never swipe-dismissible; ✕ is the only in-app stop.
- **SC-T2 — The expanded `ModalBottomSheet` transport** (Priya) — the `BookChapterPickerSheet` idiom;
  speed + sleep timer reuse `SettingsDropdownRow` verbatim; per §16.6 the prev/next labels are
  **chapter**, not verse.
- **SC-T3 — Retire `ReaderAudioSlot`; stats-cap swap 45 % → 30 % while live (D-AUD-UI-2/3)** (Priya) —
  both owner-gated; the cap swap is a cap change only, no stat/strip/legend removed, and idle is
  byte-for-byte unchanged.
- **SC-T4 — `activeVerseId` combine + the verse highlight (D-AUD-E-13, D-AUD-UI-5)** (Diego + Priya) —
  `combine` into `uiStateForPage`; the reader never holds a player reference; the highlight is
  `secondaryContainer` fill + a 3 dp `primary` leading rule + `stateDescription = "Now playing"` —
  **never colour alone**, replacing today's unpaired `onPrimaryContainer`-on-`surface` contrast defect.
- **SC-T5 — Yielding autoscroll + the "Follow along" chip (D-AUD-UI-6, FR-AUD-12)** (Priya) — target
  one-third from top; yield on **`DragInteraction.Start`** (not `isScrollInProgress`, which our own
  animation trips); re-arm via the overlaid ≥48 dp chip (0 dp of layout), the sheet switch, or a chapter
  advance.
- **SC-T6 — Session-scoped verse tap + the two named custom actions (D-AUD-E-14, D-AUD-UI-4)** (Sam) —
  one place (`VerseItem`); **the `CustomAccessibilityAction`s are mandatory, not optional** — long-press
  alone would remove an affordance from TalkBack users, which NFR-AUD-C forbids; outside a session,
  Sprint H behaviour pinned byte-for-byte.
- **SC-T7 — Reactive footer hint while listening** (Sam) — five new `reader_verse_tap_hint_listening_*`
  variants through the existing `D-K-HINT-1` one-home mapping; still `clearAndSetSemantics {}`.
- **SC-T8 — The three entry points (D-AUD-UI-7/8/9)** (Priya) — the card's trailing 48 dp ▶ (0 dp height,
  consumes its own click, becomes ⏸ when live), the Schedule top-bar `listen-day` (D-M-AUD-5; hidden on
  Feb 29 / load-failed), and the reader top-bar ▶ whose unit follows `ReaderContext`.
- **SC-T9 — `StartReadAloudUseCase` + marking (FR-AUD-15, D-AUD-E-17)** (Sam) — calls the **existing**
  `MarkReadOnOpenUseCase` then `WidgetRefresher`, byte-identical to `onReadingTapped`'s D-O-1/D-O-2 path;
  `DayUnit` marks each stream **as its portion begins**; Browse marks nothing. **No new marking rule.**
- **SC-T10 — Settings → Read aloud, Phase-1 shape (D-AUD-UI-10)** (Priya) — Wi-Fi-only + speed only; the
  voice row and downloads row are **absent** (one voice, nothing to choose); the section never renders empty.
- **SC-T11 — The `NeedsSource` / no-engine moments (D-AUD-UI-12)** (Sam) — in Phase 1 only the no-engine
  dialog is reachable; ▶ stays **enabled** (a permanently dead button teaches nothing) and opens
  `audio-no-engine-dialog` with the device-settings intent idiom.
- **SC-T12 — TalkBack self-ducking + the semantics contract (D-AUD-UI-13, NFR-AUD-C)** (Priya) — never
  silence TalkBack; duck **ourselves** to ~25 % for ~4 s when touch exploration is on and a transport
  control takes focus; the highlight is **not** a live region (announcing each verse would read the whole
  chapter over the audio — the worst outcome available); exactly one polite live region, the bar status.
- **SC-T13 — A11y gate + guilt-copy ban-scan extension (M-AUD-7, M-AUD-8)** (Riley) — every tag in
  Priya §10 pinned at ≥48 dp with its spoken label, **plus the pinned absence of any `liveRegion` on a
  verse node**; the ban-scan extends over all audio strings (the D-S20-1 legend exemption does **not**
  extend here); a test pins that no surface distinguishes a heard reading from a read one (FR-AUD-16).
- **SC-T14 — Delete `listen-dev`** (Sam) — an acceptance criterion, not a cleanup task.

---

### Sprint AUD-D — Phase 1 device pass + release → 1.6.0  *(the ship/hold decision)*

**Outcome goal:** read aloud is live for users, at $0 spend.

**Candidate tickets (titles):**
- **SD-T1 — Consolidated Phase-1 device pass** (Riley + owner) — Priya §13 items 1–5 (one-screen fit at
  N=4 with a session live, at default and 1.5× font; the ▶/checkbox pair; the four-action top bar; the
  reader viewport; bar slide-in jank), 6–11 (highlight contrast in light/dark/dynamic, autoscroll cadence,
  chip placement, highlight accuracy at 2.0× and on the Psalm 119 days, long-press feel, TalkBack action
  order), **12 — the two-voice test (the single most important item in the feature)**, 13–14 (live
  regions; lock screen / notification / headset / Bluetooth / a real phone call = M-AUD-9). Plus device
  TTS on at least two OEMs and one API 26/27 device.
- **SD-T2 — String tone sign-off** (Owner/Maya) — Priya §11's transport/entry/degradation tables; joins
  the standing S12–S20 + alt-schedules sign-off queue rather than starting a new one.
- **SD-T3 — THE DECISION: ship Phase 1 standalone, or hold for Phase 2** (Owner) — `OQ-AUD-3`. My
  recommendation and its reasoning are §1.4. A "hold" costs nothing structurally; it only delays users.
- **SD-T4 — 1.6.0 / 10600, staged rollout** (Jordan) — tag → alpha via `release.yml`, device pass, then
  a **staged** production rollout (not straight to 100 %), because this release changes a shipped gesture
  and adds a persistent bar to a live app. whatsnew updated.

---

### Sprint AUD-E — [A1] Pronunciation pilot + the spend decision  *(the last reversible point)*

**Outcome goal:** the owner has heard the ElevenLabs voice on the hardest passages in the KJV and has
authorised — or refused — the corpus render.

**[A1] What `OQ-AUD-1`'s resolution removed from this sprint.** The owner auditioned the field himself
and chose **ElevenLabs** on voice realism, so the bake-off is gone: no LibriVox completeness survey, no
forced-alignment spike, no blind listening test. `SE-T1` is **closed on arrival**. Three knock-on
simplifications, recorded so nobody re-adds them:
- **Timings are derivation, not alignment** — the API supplies character/word timestamps, so `FR-AUD-10`
  stops being the requirement that prices the feature and becomes a parsing step. *(The ASR round-trip
  in `SE-T5`/`SF-T2` stays — vendor timestamps still cannot check themselves, §16.4.)*
- **E's entry condition drops from "C green" to "B green"** (§1.1), so E and F overlap C and D.
- **The rights layer is settled by the plan**: ElevenLabs grants commercial rights on paid plans, so
  `SE-T7`'s AR-AUD-1 entry records *one* second layer, not a menu.
- **Corrections are per-chapter re-renders** — cheap in dollars, slow in calendar (R-AUD-1). That is the
  posture `SF-T1` must preserve by keeping the renderer per-chapter and reproducible.

**Candidate tickets (titles):**
- **SE-T1 — ~~Close `OQ-AUD-1` on evidence~~ — [A1] CLOSED. Owner chose ElevenLabs.** No work. Retained
  as a numbered stub so `SE-T2…T9` are not renumbered and so the resolution is visible where the
  question used to be.
- **SE-T2 — Confirm ElevenLabs' timestamp contract** (Diego) — character- or word-level? Do they survive
  the Opus encode step, or are they reported against pre-encode PCM? One API call settles it and it
  decides how `render_audio.py` derives verse boundaries. **Do not commission a render without this
  answer** — it is now the only remaining unknown in the render pipeline's design.
- **SE-T3 — The pronunciation pilot + M-AUD-6 sign-off (G-VOICE)** (Owner + Riley) — render the
  proper-noun-heavy set (1 Chr 1, Num 26, Ezra 2) plus the tone set (Ps 23, Isa 53, John 11, Gen 1,
  Matt 5, Ps 119:1–40). A small, reversible spend. Owner listens on a phone speaker and on earbuds and
  signs off, or does not.
- **SE-T4 — Build the pronunciation lexicon from what the pilot exposed** (Riley) — pinned, versioned,
  an input to `render_audio.py`. This is the artifact that stops R-AUD-3 from being paid twice.
- **SE-T5 — Calibrate the ASR thresholds on the pilot (RE-AUD-5)** (Riley) — WER ≤ 5 % hard-fail /
  ≤ 2 % expected is Diego's **starting proposal**, not a measurement; KJV archaisms against a modern ASR
  model may make it unachievable. Measure it, record the distribution, and set the real numbers here —
  before they can wave a bad corpus through or block a good one.
- **SE-T6 — Settle `OQ-AUD-6` (24 vs 32 kbps)** (Owner) — a listening check on a phone speaker. It
  prices the corpus (~853 MB vs ~1.14 GB) and both remain legal under every Play limit.
- **SE-T7 — Record AR-AUD-1 in `docs/data/README.md` alongside AR-1** (Owner/Maya) — **before the spend,
  not before ship.** The recording is a derivative work carrying the same UK Crown-copyright position as
  the text, **[A1] plus exactly one second layer: the ElevenLabs paid-plan commercial grant.** Record the
  plan tier and the grant's terms, because the corpus's redistributability depends on them.
  `OQ-AUD-9`.
- **SE-T8 — Confirm `OQ-AUD-E-2` (D-AUD-E-19)** (Owner) — given `SA-F2`'s measured number, commit to
  "audio content never changes in a PATCH release" as a **product** commitment, and confirm D-AUD-3 still
  stands at the price we actually measured.
- **SE-T9 — The go/no-go, against the §4 checklist** (Morgan + Owner) — every row ticked, or the render
  does not start.

---

### Sprint AUD-F — The corpus render + the offline audio gate  *(the one-way door — ~$250–800)*

**Outcome goal:** the project's third core content asset exists and is provably correct.

**Candidate tickets (titles):**
- **SF-T1 — `tools/render_audio.py`** (render-machine owner + Riley) — pinned voice id, model, params
  and lexicon; per chapter, `strip(text_markup)` joined; vendor TTS with timestamps; Opus 24 kbps mono;
  derive `[verseId, startMs, endMs]`. `tools/requirements.txt` **stops claiming stdlib-only** and says so
  explicitly (Whisper + ffmpeg, render-machine only, never shipped).
- **SF-T2 — `tools/verify_audio_asr.py` — the independent witness (D-AUD-E-8)** (Riley) — local Whisper,
  pinned model + revision, offline: WER vs the source text, and **ASR-derived verse boundaries compared
  against the vendor's** (≤ 300 ms). Per §16.4 this is the *only* check on the timing index, because the
  vendor's timestamps cannot check themselves.
- **SF-T3 — Render the corpus + triage** (render-machine owner) — every over-threshold chapter
  human-triaged and either re-rendered or recorded as an accepted variance with a reason, in the posture
  of Sprint A's five `TEXT_OVERRIDES` and Sprint 1's seven reconciled conflicts.
- **SF-T4 — Commit the small half; publish the large half (D-AUD-E-3, D-AUD-E-6)** (Jordan) —
  `audio/timings/<usfm>.json` ×66 (~0.9 MB, human-readable, diffable) + `audio/audio_manifest.json`
  (1,189 rows of sha256/bytes/durationMs/WER) **to git**; the `.opus` blobs to a GitHub Release on an
  `audio-corpus-v1` tag, **never to git, never to LFS**. Write the **runbook** (§8, item 2).
- **SF-T5 — `AudioTimingVerificationTest` — the sixth standing data gate (M-AUD-2)** (Riley) — Diego's
  ten assertions, offline, ~1 s, **no audio bytes needed**; note assertion 2 is the day-by-day-equality
  analogue and assertion 5 (`endMs[last] == durationMs ± 250 ms`) is the truncation guard. Mutations to
  kill, each by exactly its intended test: drop a verse (→2), delete a superscription (→3), shorten a
  `durationMs` by 20 % (→5 **and not 6**), shift a `startMs` past the next (→4), widen a Psalm 119 window
  by one verse (→9), rename a pack (→10).
- **SF-T6 — `AUD-VERIFY`: SHA-256 verification in the release job** (Jordan) — every blob checked against
  the committed manifest **before** it populates a pack module. Release assets are maintainer-mutable;
  this pin is the guard.
- **SF-T7 — Reconciliation log in `docs/data/README.md`** (Riley) — sources, pinned vendor/model/params,
  the lexicon, every triaged chapter, the measured WER and boundary distributions. The durable provenance
  record for the third core asset.

---

### Sprint AUD-G — Phase 2 playback + the download surface

**Outcome goal:** the good voice, downloadable by unit, honest about every byte.

**Blocked on `OQ-AUD-E-1`** (the §16.1 unit-table rewrite) before tickets are written.

**Candidate tickets (titles):**
- **SG-T1 — `asset-delivery` returns to `:app`** (Avery) — D-M-AUD-3's Phase-2 half; re-run `SA-T0`'s
  posture-(b) diff on the real branch and confirm it still matches.
- **SG-T2 — `FileVersePlayer` (seam 2b)** (Avery) — ExoPlayer + a `ClippingMediaSource` per queue chapter
  clipped to the window's timings — **this is the mechanism that makes Psalm 119:1–40 play exactly its
  verses out of a whole-chapter file**; `activeVerseId` from a 200 ms poll through
  `VerseTimingIndex.verseAt(ms)` (binary search, pure, JVM-tested); the scrubber turns on by advertising
  the seek command.
- **SG-T3 — `PlayAssetPackRepository` + `PackFileLocator` + `AssetPackAvailability`** (Avery) — sizes are
  **Play's** (`totalBytesToDownload()`), never ours; locations re-resolved every launch, **never cached**;
  eviction and sideload are normal states, not errors.
- **SG-T4 — App-enforced Wi-Fi-only** (Avery) — `SA-F1` confirms Play's own `WAITING_FOR_WIFI` never fires
  at our pack sizes, so `request()` checks the transport itself; we still surface
  `showConfirmationDialog()` when Play reports `REQUIRES_USER_CONFIRMATION` (that is how sideload
  manifests, and it must not be swallowed).
- **SG-T5 — `PackVerseTimingSource`** (Avery) — per-book sidecar in the same pack as its audio; a `null`
  return at any moment (pack evicted mid-session) degrades to the device voice and **suppresses the
  highlight rather than guessing** (FR-AUD-11's "correct or absent").
- **SG-T6 — Settings → Read aloud gains voice + downloads rows (D-AUD-UI-10)** (Priya) — undownloaded
  high-quality voice = **visible-but-disabled** (the S14/S15 teaser idiom); the no-Play-Store sentence
  said **once, in Settings, never as a popup**.
- **SG-T7 — The "Downloaded audio" pushed screen (D-AUD-UI-11)** (Priya) — over `BookCatalog` (no second
  book table); one ≥48 dp state-button per row across the six states; delete-all is the **second** tap
  from Settings (NFR-AUD-E, literally).
- **SG-T8 — Cellular consent + delete confirmations** (Sam) — per-download opt-in that **never** edits the
  standing Wi-Fi-only setting; every size stated before commitment.
- **SG-T9 — Wire `NeedsSource`'s two-choice sheet to real downloads (D-AUD-UI-12)** (Sam) — play never
  dead-ends and **never silently substitutes a worse voice**; mark-on-press still fires (FR-AUD-15 is
  about the press, not about audio arriving).
- **SG-T10 — M-AUD-5: zero unconsented bytes, JVM-pinned** (Riley) — the only path reaching
  `AudioDownloadRepository.request` originates in an explicit user intent; construction/observation alone
  never calls `requestFetch`.
- **SG-T11 — FR-AUD-26 plan-window download** (Sam) — "the books your next 30 days need", computed from
  the **active** plan; droppable if G runs long.

---

### Sprint AUD-H — Phase 2 hardening + release → 1.7.0

**Outcome goal:** the good voice is live, the install is still ~8 MB, and the manifest still has no `INTERNET`.

**Candidate tickets (titles):**
- **SH-T1 — Consolidated Phase-2 device pass** (Riley + owner) — a real pack download / eviction /
  re-download; storage figures matching the OS's own number; the cellular dialog on a real metered
  connection; cancel responsiveness on a 700 MB unit; sideload; **Ogg-Opus decode on a real API 26/27
  device** (RE-AUD-7 — if it gaps, `media3-exoplayer-opus` is the known, priced fallback at ~1 MB/ABI,
  which spends a third of our base-module headroom; do not ship it pre-emptively); highlight accuracy at
  2.0× and on the Psalm 119 days against the *real* audio.
- **SH-T2 — The `audio-bundle` job end-to-end on real payloads** (Jordan) — fetch → SHA-verify → populate
  → `bundleRelease -PwithAudio=true` → the three assertions. Expect ~5 → **~25–30 min**; the timeout is
  already 45.
- **SH-T3 — Posture re-verification (M-AUD-3 as restated, M-AUD-4)** (Jordan) — merged **release**
  manifest: no `INTERNET`, exactly the two new foreground-service permissions; base module ≤ 12 MB; zero
  audio bytes under `base/`.
- **SH-T4 — Security review of the exported components** (Diego) — `PlaybackService` is the app's first
  exported component other than `MainActivity`; no custom actions accepted from external controllers
  beyond media3's standard set (RE-AUD-11).
- **SH-T5 — 1.7.0 / 10700, staged rollout, whatsnew** (Jordan) — D-AUD-E-19 becomes a standing release
  rule from this version.

---

## 4. The render-spend gate — the checklist that authorises Sprint F

**Sprint F does not start until every row is ✅.** This is the whole reason the sprints are ordered the
way they are, and it is the artifact I most want the owner to hold.

| # | Condition | Evidence | Sprint | Owner |
|---|---|---|---|---|
| 1 | `OQ-AUD-1` answered — which voice source | The three-part experiment; LibriVox disqualified by alignment accuracy or chosen on it | E (`SE-T1`) | Owner |
| 2 | Vendor timestamp contract confirmed (char/word; survives encode) | One API call | E (`SE-T2`) | Diego |
| 3 | **G-VOICE — the pronunciation pilot signed off** (M-AUD-6, R-AUD-3) | The owner has heard 1 Chr 1 / Num 26 / Ezra 2 + the tone set | E (`SE-T3`) | Owner |
| 4 | The pronunciation lexicon exists, built from the pilot | Pinned, versioned, an input to the renderer | E (`SE-T4`) | Riley |
| 5 | ASR thresholds calibrated on real output, not proposed | Measured WER + boundary distributions | E (`SE-T5`) | Riley |
| 6 | `OQ-AUD-6` settled (24 vs 32 kbps) | A listening check on a phone speaker; it prices the corpus | E (`SE-T6`) | Owner |
| 7 | **AR-AUD-1 recorded in `docs/data/README.md`** | Written *before* the spend, alongside AR-1 | E (`SE-T7`) | Owner/Maya |
| 8 | **G-POSTURE** — the manifest delta is measured and matches | Both diffs, verbatim | A (`SA-T0`) | Jordan |
| 9 | **G-DELIVERY (a)** — Play accepts the pack layout; `requestFetch`/`assetsPath()` work | `SA-F1` on a real device | A (`SA-T6`) | Owner/Avery |
| 10 | **G-DELIVERY (b)** — the real update-patch cost is measured, and D-AUD-3 still stands at that price | `SA-F2` + `OQ-AUD-E-2` confirmed | A (`SA-T7`), E (`SE-T8`) | Owner |
| 11 | Phase 1's queue gate is green — the timing index has an **executable specification** | M-AUD-1, and Phase 1's per-verse TTS boundaries in a shipped build | B (`SB-T10`), C | Riley |
| 12 | The render machine, the vendor account and the budget have a **named owner** | `OQ-AUD-E-5` answered | §8 | **Owner** |

**If row 9 fails**, re-group to 8 section packs — one edit to `AudioPackPlan` and the generator, **no
artifact change, no re-render**. That is free today and a re-render after F. **If row 10 fails**, D-AUD-3
goes back to the owner before a dollar is spent. **If row 3 fails**, nothing downstream happens at all.

---

## 5. Quality gates per sprint

### 5.1 Standing pipeline (every sprint — unchanged discipline)

A sprint is **not done** until:
1. `./gradlew assembleDebug` succeeds.
2. `./gradlew testDebugUnitTest` passes — **including all five standing data/Room gates** (BC plan 11,
   M'Cheyne 10, Chronological 8, `BibleTextVerificationTest` 18, `BibleDatabaseRoomOpenTest` 5), and
   from Sprint F the sixth (`AudioTimingVerificationTest`).
3. `spotlessCheck` + `lintDebug` clean.
4. Kover floor met (≥ 70 % on domain/data; the project runs ~96 %) — **including `…audio.domain.*` from
   Sprint B**; new domain/data carries tests **and mutation verification**, each load-bearing mutation
   killed by exactly its intended test and restored in place.
5. The acceptance is met in **working software** — nothing closed on "should work".
6. **The production-parity bar (§1.3) is green**: the full existing suite passes UNCHANGED with no
   session live.
7. Any blocking OQ for the *next* sprint is resolved (§7).

Full local command: `./gradlew spotlessCheck lintDebug assembleDebug testDebugUnitTest koverXmlReportAppDebug koverVerifyAppDebug`.

### 5.2 Sprint-specific gates

- **A:** `SA-T0` matches ESpec §12 exactly for both postures; the PR bundle gate keeps `CEILING=12000000`
  verbatim and a planted `.opus` under `base/` fails the new job; `SA-F1`/`SA-F2` recorded.
- **B (HARD GATE):** **M-AUD-1 green with zero audio bytes in the repo** — Psalm 119 windows exact, the
  two-book portion, all 117 superscriptions spoken-not-numbered, stripped text only, the three stop rules.
- **C:** `AccessibilityGateTest` extended over every Priya-§10 tag **plus the pinned absence of a
  `liveRegion` on verse nodes**; the guilt-copy ban-scan extended over all audio strings (M-AUD-7); a
  test pins no surface distinguishes heard from read (FR-AUD-16); the four shipped surfaces' pins pass
  UNCHANGED; `listen-dev` deleted.
- **D:** the consolidated Phase-1 device pass, headed by **M-AUD-9** (hands-free correctness) and
  Priya's **two-voice TalkBack test**; one-screen fit at N=4 with a session live (M-AUD-10).
- **E:** the §4 checklist, every row.
- **F (RELEASE GATE):** `AudioTimingVerificationTest` green against the committed index; six mutations
  killed each by its intended test; every over-threshold chapter triaged and logged; `AUD-VERIFY` SHA
  step green.
- **G:** M-AUD-5 JVM-pinned; the degradation ladder green at every rung (downloaded → device → honest
  message) including an evicted pack mid-session.
- **H:** no `INTERNET` in the merged release manifest; base ≤ 12 MB; zero audio bytes under `base/`; the
  consolidated Phase-2 device pass.

### 5.3 JVM-provable vs device-pass, stated per sprint (nothing claimed that isn't)

| Sprint | **JVM-provable** | **Device-pass only** |
|---|---|---|
| A | `AudioPackPlan` totality; the CI assertions; the four settings keys | **Everything that matters**: Play accepting 66 packs, `requestFetch`, `assetsPath()`, eviction, sideload, the update-patch size. The manifest diff is build-provable, not JVM |
| B | The queue (M-AUD-1), the stop rules, the availability ladder, the sleep timer, the focus **transitions**, the controller state machine | Real TTS engines across OEMs; screen-off continuity; the lock screen; a real phone call / sat-nav; API 26/27 engine behaviour |
| C | Highlight/`activeVerseId` rendering, autoscroll yield logic, the session-scoped tap mode, marking, a11y tags + labels + the pinned `liveRegion` absence, the ban-scan, layout **arithmetic** | Highlight **contrast** in light/dark/dynamic on a real palette; autoscroll **cadence** and fling behaviour; long-press feel; **the two-voice TalkBack collision**; one-screen fit on glass; the bar's slide-in against the nav bar |
| D | — | The whole sprint |
| E | The alignment spike's numbers | **The voice itself** (M-AUD-6) and the 24-vs-32 kbps listening check |
| F | The entire gate — offline, no audio bytes (that is the design) | Nothing; the render happens off-CI on the render machine |
| G | `verseAt(ms)` binary search, `AudioPackPlan` routing, the ladder with a `HighQuality` rung, M-AUD-5 | Real download / eviction / re-download; cellular dialog on a metered network; storage figures vs the OS; cancel responsiveness |
| H | The three release assertions | Opus decode on API 26/27; highlight accuracy against the real audio at 2.0×; the full transport matrix |

---

## 6. Risks & dependencies

| # | Risk / dependency | Impact | Mitigation | Owner |
|---|---|---|---|---|
| **RM-1** | **The render is a one-way door.** ~$250–800, and a pronunciation defect means paying again (R-AUD-3). | The classic "expensive re-import" failure V3 was designed to avoid, with a real invoice attached. | The §4 checklist, twelve rows, every one ✅ before F starts. The pilot (`SE-T3`) + the lexicon (`SE-T4`) are the highest-value controls in the epic. **Sprint E exists solely to be the wall in front of F.** | Owner / Morgan |
| **RM-2** | **The Play review calendar is not ours.** `AUD-C-1` needs **two** cycles and each is days of wall-clock. | Discovering in Sprint F that Play rejects 66 packs means re-grouping *after* the render. | D-M-AUD-1: it moves to **Sprint A** and runs underneath B and C. Being wrong is a one-function edit **today** (D-AUD-E-5). | Morgan / Owner |
| **RM-3** | **`SA-T0` is unverified today.** Diego could not build the merged manifest (`platforms;android-37` unpublished). Everything about the delivery posture rests on library-manifest inference. | If the delta differs from §12, the whole delivery approach re-plans. | It is the **first ticket of the first sprint** and it is go/no-go. If no machine can satisfy `compileSdk = 37`, that is itself a finding and the pin goes back to Diego before anything else. | Jordan / Diego |
| **RM-4** | **This app is live in production at 100 %.** Phase 1 changes a **shipped gesture** (Sprint H's verse tap), adds a persistent bar, and swaps the stats cap. | A regression lands on real users of a 177-country release. | §1.3's four protections, each pinned; the parity rule ("a red shipped pin is a design defect, not a test to update"); **staged** rollouts (D-M-AUD-4), alpha first, and `OQ-AUD-4` answered by the owner before C — because changing a taught gesture is a product decision. | All / Morgan |
| **RM-5** | **The corpus ops burden is annual and therefore forgettable.** Creating a tag, uploading 66 assets, bumping `AUDIO_CORPUS_TAG` happens roughly once a year. | The one person who knows how forgets, or leaves. | `SF-T4` includes a **written runbook** in `docs/`, and §8 requires a **named owner** — not "Jordan, probably". | §8 / Jordan |
| **RM-6** | **CI release time goes ~5 → ~25–30 min** and downloads ~853 MB per release run. | Slow, flakier releases; storage spend if we keep uploading a ~900 MB AAB artifact. | Audio excluded from PR/push CI **entirely** (D-AUD-E-7a); release-only job; **no artifact upload**; timeout 45; and `ci/actions-node24-bump` lands in Sprint A **while the job is still 5 minutes**. | Jordan |
| **RM-7** | **The 12 MB gate could quietly become meaningless** once the AAB is ~900 MB. | The install-size guarantee D-V3-20 bought is lost without anyone noticing. | D-AUD-E-7: the number stays **verbatim** on an audio-less PR build, and the release job asserts **zero audio bytes under `base/`** — a structural invariant that cannot be satisfied by accident. Riley plants a fake `.opus` and proves the job fails (`SA-T3`). | Jordan / Riley |
| **RM-8** | **Timing-index drift (R-AUD-4).** A highlight on the wrong verse is worse than none, and would silently corrupt the Psalm 119 windows. | The headline correctness requirement, broken invisibly. | D-AUD-E-8's ASR boundary agreement is the **only** check on the vendor's timestamps (§16.4 — the PRD's proposed witness compares different audio and proves nothing); ≤ 300 ms hard-fail; `null` timings ⇒ **no highlight**, never a guess. | Riley |
| **RM-9** | **Scope creep into a media app (R-AUD-6/RE-AUD-8).** Session lifecycle, focus, notifications, speed, timers, downloads, queues. | The largest feature since V3 eats sprints on "just one more control". | Phase 1's cut is the forcing function; the two-seam split means Phase 2 adds **one class**; **no global mini-player** (D-AUD-E-12); PRD §4's non-goals are long on purpose and are scope protection, not commentary. | Morgan / Diego |
| **RM-10** | **`OQ-AUD-1` unanswered stalls E and F indefinitely.** | The Phase-2 path has no start. | Phase 1 (B/C/D) has **no** dependency on it and ships regardless — that is most of the argument for the two-phase cut. E's `SE-T1` is designed so the *cheap disqualifying test* (alignment accuracy) runs before the expensive one (listening). | Owner |
| **RM-11** | **One-screen-fit regression at N=4** (R-AUD-8). Four sprints were spent winning it. | The M'Cheyne case re-breaks a guarantee paid for four times. | Height-neutral card ▶ (structurally, not by luck); no new button row; Idle composes nothing; the cap swap **returns more than the bar takes** (Priya §9). Confirmed on glass in `SD-T1` (M-AUD-10). | Priya |
| **RM-12** | **Opus decode gaps on real API 26/27 devices** (RE-AUD-7). | Phase 2 silently unusable on Android 8.x. | Device pass is a **release gate** (`SH-T1`); `media3-exoplayer-opus` is the known, priced fallback (~1 MB/ABI = a third of our headroom) — **not shipped pre-emptively**. | Avery |

---

## 7. Owner decision checkpoints — which OQ blocks which sprint

**Read this as the schedule for owner attention.** Every row that says "blocks **A**" or "blocks **C**"
means work genuinely cannot start, not that it would be nice to know.

| OQ | Question | Owner | **Blocks** | Recommendation / code impact |
|---|---|---|---|---|
| **OQ-AUD-E-3** | Restate M-AUD-3 as "no `INTERNET`; exactly two new foreground-service permissions; no data-safety change" (§16.3). | Owner | **A — the first sprint.** `SA-T0` is a go/no-go and we cannot run it without a definition of "go". | **Accept the restatement.** "No new permission" is unachievable and aiming at it invites someone to redefine "new". |
| **OQ-AUD-5** | Announce the chapter reference aloud? Speak verse numbers? | Owner (tone) | **B** — it changes what the queue enqueues. | Maya's recommendation: **announce the reference once, do not speak verse numbers.** Numbers every few seconds break the reading. |
| **OQ-AUD-4** | Verse tap **seeks** during a session; the shipped Sprint-H tap-out moves to long-press. **This changes a shipped gesture.** | Owner | **C** — SC-T6/T7 are built on it. | Adopt as designed, **and the two named TalkBack custom actions are mandatory, not optional** (D-AUD-E-14) — without them the change *removes* an affordance from screen-reader users, which NFR-AUD-C forbids. |
| **D-AUD-UI-2** | Retire `ReaderAudioSlot` as a bottom bar; honour D-V3-14's intent at the root. | Owner | **C** | Confirm. Keeping it produces two bars stacked inside a third. |
| **D-AUD-UI-3** | While a session is live, the Schedule's stats cap drops 45 % → 30 %. | Owner | **C** | Confirm. It is a **cap** change (the panel already scrolls), it removes no stat, and it is what buys the Listen bar for free at N=4. |
| **OQ-AUD-8** | Whole-day playback in the first release? | Owner / **Morgan** | **C** | **Resolved by me: ship it** (D-M-AUD-5). ~a day of work, 0 dp, the commuter's natural unit, and the cleanest thing to delete if the device pass says the top bar is crowded. |
| **OQ-AUD-7** | Feature naming: "Read aloud" vs "Listen" vs "Audio"; the D-AUD-5 voice labels. | Owner (tone) | **C** (strings) / **D** (release) | "Read aloud". String **ids stay stable** whichever wins, so this can land as late as D's sign-off. |
| **OQ-AUD-3** | Confirm the two-phase cut — ship Phase 1 standalone? | Owner | **D** (only the release; not B or C) | **Strongly recommended, §1.4.** A "hold" delays users and changes nothing structural. |
| **OQ-AUD-1** | **Which voice source** — ElevenLabs pre-render vs LibriVox PD human. | Owner | **E's exit and F absolutely** | Maya recommends ElevenLabs, primarily on FR-AUD-10 (timings are essentially free; LibriVox's true cost is a forced-alignment project over ~79 h of volunteer-variable audio). Run the **cheap disqualifying test first** (`SE-T1` step 2). |
| **OQ-AUD-6** | 24 vs 32 kbps Opus. | Owner (listening) | **F** — it prices the corpus | 24 kbps (~853 MB, largest pack ~47 MB, every pack under Play's 200 MB threshold). Not an architectural constraint either way. |
| **OQ-AUD-9 / AR-AUD-1** | Confirm the accepted-risk posture extends to **recordings**, and the attribution obligations of whichever source wins. | Owner | **F** — recorded **before the spend** | Record it in `docs/data/README.md` alongside AR-1 at `SE-T7`. The spend is the commitment point, not the ship. |
| **OQ-AUD-E-2** | D-AUD-E-19 — audio content never changes in a PATCH release — as a **product** commitment. | Owner | **E** (it is part of D-AUD-3's real price) / **H** (standing rule) | Confirm once `SA-F2` gives the measured number. An audio change rides the automatic app update **with no consent prompt**. |
| **OQ-AUD-E-1** | The §8 download-unit table (§16.1): book / testament / everything / "the books your next 30 days need"? | Owner + Maya | **G** | Accept §16.1's rewrite and delete the "Today's readings ~2.4 MB" row — a pack is atomic, so that unit cannot exist. The **product rule** ("never forced to take ~850 MB") survives intact. |
| **OQ-AUD-E-5** | **Who owns the render machine and the spend?** | **Owner** | **E/F** | See §8. This is the largest genuinely unowned item in the epic. |
| **OQ-AUD-E-4** | Is `AUD-C-1` budgeted as a real ticket with a real Play cycle? | **Morgan** | — | **Resolved: yes — it is Sprint A, and it needs two cycles** (D-M-AUD-1). |

---

## 8. Work that currently has no owner (needs the owner to assign, not me)

Diego flagged these and I am not going to quietly assume them into someone's sprint.

1. **The render machine, the vendor account, and the ~$250–800.** Who runs `render_audio.py`? On what
   hardware (Whisper wants a GPU or a lot of patience)? Whose card is on the vendor account, where does
   the API key live, and — stated explicitly — **it never enters CI**, because the render happens once
   per corpus on a human's machine, not in a workflow. **Blocks Sprint F.** (`OQ-AUD-E-5`.)
2. **The audio corpus as SHA-pinned GitHub Release assets** (D-AUD-E-6). Someone creates the
   `audio-corpus-v<N>` tag, uploads 66 tarballs (~853 MB), and bumps `AUDIO_CORPUS_TAG` in the workflow —
   roughly **once a year**, which is exactly the frequency at which a procedure is forgotten. Jordan is
   the natural owner, but this needs to be an explicit assignment **plus a written runbook** (`SF-T4`),
   not an assumption. Note also that release assets are maintainer-**mutable**; the checksum pin is the
   only guard and `AUD-VERIFY` must never be made optional.
3. **The CI release job's new shape.** ~5 → ~25–30 min, a ~900 MB `.aab`, timeout raised to 45, and the
   AAB artifact upload **dropped**. Jordan owns the change; the owner should know his releases get
   slower and why. The `ci/actions-node24-bump` PR must land **first** (`SA-T1`).
4. **`AUD-A-0` needs a machine that can build the app.** `platforms;android-37` is not published, so
   `compileSdk = 37` cannot be satisfied on the machine Diego used. Somebody has to find a build
   environment where it resolves — or we discover our `compileSdk` pin is ahead of the published SDK,
   which is a finding in its own right. **Blocks the first ticket of the epic.**
5. **The Play Console cycles.** Two internal-track uploads, two "Submit for review" clicks, two device
   observations — the owner's hands, not ours. The promote-to-production pipeline is armed but is
   irrelevant here: **the spike builds are never promoted** (D-M-AUD-2).
6. **The pronunciation lexicon's editorial call.** When the pilot mispronounces Mahershalalhashbaz,
   somebody decides what right sounds like. That is the owner's ear, on the pilot, in Sprint E.

---

*End of the audio execution plan. Sprints A and B are fully ticketed above (the immediate next work);
Sprint C is ticketed at title level with its owner gates named; D–H are goal + ticket-title level and
are decomposed at the start of their own sessions, once the blocking OQs in §7 are resolved. The one
rule I will hold hardest: **Sprint F does not begin until every row of §4 is ticked.***
