# iOS port — the execution plan

> **Status:** authoritative execution plan · **Date:** 2026-08-08 · **Author:** Morgan (EM)
>
> Derived from the owner-signed [ios-port-approach.md](ios-port-approach.md) and the 25 task briefs,
> 14 ADRs, [port-inventory.md](port-inventory.md), [parity-matrix.md](parity-matrix.md) and
> [test-port-strategy.md](test-port-strategy.md) produced from it. **This is the document a fresh
> session opens to start work; it assumes no context beyond this repo.**
>
> **Evidence discipline is inherited verbatim.** `VERIFIED` = someone read the code, ran the command,
> or got an HTTP 200 against the real artifact. `INFERRED` = reasoned from documentation or API
> contracts; nobody here executed it. `UNVERIFIED` = nobody knows. **Labels are never upgraded
> without doing the work.**

**Two things in this plan the owner has not seen yet:** it adds one decision — a **fourth**
Android-only Play release (**D-PORT-9**, §6) — and it defers one question to the owner
(**A11Y-03**, §8). Everything else transcribes decisions already made.

---

## 1. START HERE — the first three actions, in order

Assume zero context. Do these three before anything else.

### Action 1 — Owner, tonight: `docs/RELEASING-IOS.md` Steps 0, 0a, 0b, then submit Step 1

**Why it is first: Gate 0 cannot close without Xcode.** Two of Gate 0's four briefs need it.
[gate0-reader-gesture-rig.md](task-briefs/gate0-reader-gesture-rig.md) states it outright, and
[gate0-room-identity-hash.md](task-briefs/gate0-room-identity-hash.md) mandates configuring the
`iosSimulatorArm64` target, which (INFERRED, `ios-port-approach.md` §7 item 1) Kotlin/Native cannot
compile with Command Line Tools alone. The identity-hash spike can return a JVM-only MATCH/MISMATCH
without Xcode, but its most consequential branch — *the hash differs **between** targets, which
forces ADR-0007 Stage 1b* — is unreachable until an Apple target compiles. So the **D-PORT-1 stop
rule cannot be fully evaluated before Xcode exists.**

Measured on this machine today (**VERIFIED**, 2026-08-08):

| Check | Reading |
|---|---|
| `xcodebuild` active developer directory | `/Library/Developer/CommandLineTools` — **Xcode is not installed** |
| `~/.konan` | **does not exist** |
| `ruby -v` | **2.6.10** (system Ruby; Step 0b needs a managed Ruby ≥3.1) |
| `df -h /` | **126 GiB available** |

**~~A discrepancy for the owner to resolve.~~ RESOLVED 2026-08-08 — Step 0 is already satisfied;
it is a verify step, not a blocker.** `RELEASING-IOS.md` Step 0 read as self-contradictory ("94 %
used" alongside a 50 GB threshold that 126 GiB already clears). It has been amended with the
reconciled figure and now states the status explicitly. The authoritative budget lives there:
the install **peaks near 84 GB** (the Xcode `.xip` and its expansion coexist until the `.xip` is
deleted — this plan's earlier ~60 GB figure omitted that overlap), settling near **67 GB**. So
~40–60 GB remains: above the 50 GB threshold, below the 100 GB comfort line. **No action is
required to start.** `~/.gradle` is 3 GB and is the easiest reclaim if a later build fails oddly.

**Submit the Step 1 enrolment the same evening.** Apple publishes no timeline; it has multi-day
latency; it costs $99 and an evening; and nothing about it is wasted if the port is later cancelled.

### Action 2 — Dispatch [p1-08-persistence-fixture-capture.md](task-briefs/p1-08-persistence-fixture-capture.md)

Verification Engineer, plus an owner device session. It needs no Xcode, no Apple account and no new
dependency. **It is the only item in the program whose latency is calendar time an engineer cannot
absorb** — the owner must install a 1.8.1-equivalent build, use it across two plans and two years,
and pull two files with `adb`. It ships zero production bytes and **blocks release 1.11.0**. See
**D-PORT-11** (§4) for why it starts during Gate 0 rather than after it.

### Action 3 — Dispatch [p0-build-foundation.md](task-briefs/p0-build-foundation.md), §3.B only

Build & Release Engineer — **this role is singular; never parallelize it.** §3.B is the per-artifact,
per-target dependency contract. Every Gate 0 spike that adds a coordinate (Room KMP for
`gate0-room-identity-hash.md`, kotlinx-datetime 0.7.0 for `gate0-minor-spikes.md` M1) and every
Phase 1 brief that replaces a library is downstream of it:

> *"Nothing that adds, moves or replaces a dependency may start until §3.B of this document is on
> disk and green."*

§3.B needs no Xcode. The rest of p0 — framework shape, the Gradle→Xcode handoff, the staleness and
negative proofs — does, and is Phase 2/4 work. See §12.C.

**Then Gate 0's four spikes, per §3.**

---

## 2. Phase vocabulary — state one, use it

Three vocabularies exist in the repo. Only one is authoritative.

| Vocabulary | Where | Status |
|---|---|---|
| **Gate 0 + Phases 1–5** | `ios-port-approach.md` §5 | **AUTHORITATIVE.** Both `p0-build-foundation.md` and `ios-delivery-pipeline.md` already name it as such |
| Phases 0–5 | the team roster, `teams/ios-port/README.md` | **DO NOT USE.** Approach doc Appendix A9: nothing maps it, and its Phase 4 exit criterion names TTS and background audio, which **this app does not have** (D-V3-14 — `ReaderAudioSlot` deliberately renders nothing) |
| Phases 0 and A–E | older briefs and ADRs | **DO NOT USE** |
| Sprint letters A–R, sprint numbers 0001–0023 | `CLAUDE.md` | **Not a phase vocabulary at all** — Android delivery history. `sprint-00A` is **not** `Phase A` |

> **D-PORT-12 — one vocabulary.** Gate 0, then Phase 1, 2, 3, 4, 5, exactly as `ios-port-approach.md`
> §5 defines them. Inside **Phase 2 only**, `p2-00-overview.md`'s **tranche A** (Xcode-free, ships as
> 1.11.0) and **tranche B** (needs Xcode, ships nothing) are retained as a sub-division, because they
> are unambiguous and already in the briefs. **No brief may introduce a fifth vocabulary.**

One brief straddles: `p0-build-foundation.md` labels itself "Gate 0 → Phase 1" and **that is
correct** — its §3.B gates Phase 1, its framework criteria are Phase 2/4.

---

## 3. Gate 0 first, and why (D-PORT-1)

> **D-PORT-1.** Run Gate 0 — the V1 identity-hash spike and an on-device reader-gesture rig —
> **before any production Android file changes**. If the hash differs *and* ADR-0007 Stage 1b is
> judged too large, **or** the gesture rig fails on physical hardware with no fix inside a week,
> **stop and reassess.**

The reason, stated plainly: **the three (now four) Android-only releases are not free option value.**
Stop after them and you have a *slightly worse* Android app — Koin loses Hilt's compile-time graph
verification, kotlinx-datetime is neutral at best on an Android-only product, and only the module
split is genuinely positive. **Do not spend that risk before you know the port can finish.**

| Brief | Owner | Timebox | Xcode? | Decides | Stop rule |
|---|---|---|---|---|---|
| `gate0-room-identity-hash.md` | Sr Shared-Core | **1 day** | Yes, for the cross-target half | ADR-0007 Stage 1a vs 1b | **Stage 1b chosen AND judged too large = the D-PORT-1 stop condition** |
| `gate0-reader-gesture-rig.md` | iOS Platform (drives) + Sr Shared-UI (writes the Compose) | **3 days**, ~1 of it toolchain setup | **YES.** $99 **NO** — free provisioning, 7-day install (INFERRED, standard Xcode behaviour, unverified here) | ADR-0004 | **G2 or G3 or G1 or G4 fails with no fix inside one week = STOP THE PROGRAM** |
| `gate0-schema-tripwire.md` | Sr Shared-Core | **half a day** on top of V1's module | same `iosSimulatorArm64` caveat | ADR-0008 | **`identityHash` differs = STOP.** Blocks the persistence phase and 1.11.0 entirely |
| `gate0-minor-spikes.md` | Sr Shared-Core (M1–M3) + Build & Release (M4) | **1 day total** | M1/M3/M4 no; **M2's iOS half yes** | ADR-0009 §1 and §3, the shape of p1-07, ADR-0011 | None can stop the port; each left open makes a Phase 1 or Phase 2 brief **unclosable** |

The four Gate 0 items **run in parallel with each other.**

**M4 is the one worth spending most of the minor-spikes day on.** Its `inputs.dir` half **fails
silently** — the gates pass because Gradle never runs them, and you ship a green build over corrupted
core IP. **The corrupt-a-byte probe *is* the spike**; the temptation to skip it because steps 1–4
passed is named in the brief.

**Gate 0 exit condition.** ADR-0007 and ADR-0008 accepted or their fallbacks chosen; the gesture rig
passes on hardware or the program stops; the owner has signed §3 of the approach doc (**done**) and
answered its §10 (**partly — see §9**).

---

## 4. The dependency graph across all 25 briefs

```
OWNER: Xcode + Ruby ≥3.1 ──┐              OWNER: $99 enrolment ─────────────────┐
                           │                (multi-day, unpublished latency)    │
p0 §3.B (Build & Release) ─┼─► GATE 0 ─► PHASE 1 ─────────────────────────────  │
   (no Xcode needed)       │    ├ room-identity-hash                            │
                           │    ├ schema-tripwire      1.9.0 ─soak─► 1.10.0     │
                           └───►├ reader-gesture-rig     (p1-01..05)  (p1-06/07)│
                                └ minor-spikes M1..M4         │           │     │
                                                              ▼           ▼     │
   p1-08 (owner device session) ══════════════════════► 1.11.0 = PHASE 2A       │
   ── runs alongside Gate 0 and all of Phase 1 ──►       (p2-01..08)            │
                                                              │                 │
                                                              ▼                 │
                                                         1.12.0 (D-PORT-9)      │
                                                              │                 │
                                                              ▼                 │
                                            PHASE 2B: p2-09 ⌘ (ships nothing)   │
                                                              │                 │
                                                              ▼                 │
                                    PHASE 3 ⌘ ─► PHASE 4 ⌘$ ◄──────────────────┘
                                                              │
                                                              ▼
                                                         PHASE 5 ⌘$
```

**☐ = needs neither · ⌘ = needs Xcode · $ = needs the $99 enrolment**

| Brief | Phase | Owner | Blocked by | Unblocks | Xcode | $99 | Owner action | Ships in |
|---|---|---|---|---|---|---|---|---|
| `p0-build-foundation.md` (§3.B) | Gate 0→1 | Build & Release | — | every dependency-touching brief | ☐ | ☐ | — | — |
| `p0-build-foundation.md` (framework/handoff criteria) | 2/4 | Build & Release | Xcode; modules exist | `ios-delivery-pipeline` | ⌘ | ☐ | Xcode | — |
| `gate0-room-identity-hash.md` | Gate 0 | Sr Shared-Core | p0 §3.B (Room KMP coords) | ADR-0007; whole bible/reader track; p2-06, p2-09 | ⌘ (cross-target half) | ☐ | Xcode | — |
| `gate0-schema-tripwire.md` | Gate 0 | Sr Shared-Core | V1's throwaway module | ADR-0008; p2-06, p2-07, 1.11.0 | ⌘ (same caveat) | ☐ | — | — |
| `gate0-reader-gesture-rig.md` | Gate 0 | iOS Platform + Sr Shared-UI | Xcode installed | ADR-0004; the reader track; parity SEL-01 | ⌘ | ☐ | Xcode, a physical iPhone | — |
| `gate0-minor-spikes.md` (M1, M3) | Gate 0 | Sr Shared-Core | p0 §3.B | p1-02 | ☐ | ☐ | — | — |
| `gate0-minor-spikes.md` (M2) | Gate 0 | Sr Shared-Core | p0 §3.B | p1-07's `koinViewModel()` criterion | ⌘ (iOS half) | ☐ | — | — |
| `gate0-minor-spikes.md` (M4) | Gate 0 | Build & Release | — | p2-01 | ☐ | ☐ | — | — |
| `p1-00-overview.md` | 1 | Staff | Gate 0 closed | rules R1–R7 for every p1 brief | ☐ | ☐ | — | — |
| `p1-08-persistence-fixture-capture.md` | 1 | Verification + owner | none beyond Gate 0 (see D-PORT-11) | p2-06 (PG-1), p2-07 (PG-2), 1.11.0 | ☐ | ☐ | **device session, multi-day latency** | none — preparation |
| `p1-01-date-text-formatter-seam.md` | 1 | Sr Shared-UI | Gate 0 | p1-02 (they share six files) | ☐ | ☐ | R8 smoke | 1.9.0 |
| `p1-03-ktor-http-and-url-encoding.md` | 1 | Sr Shared-Core | Gate 0; Ktor coords added | p2-04, p2-05 | ☐ | ☐ | R8 smoke | 1.9.0 |
| `p1-04-okio-file-io-and-dispatchers.md` | 1 | Sr Shared-Core (2nd) | Gate 0; okio added | p2-05, p2-07 (`AppFilePaths`) | ☐ | ☐ | R8 smoke | 1.9.0 |
| `p1-02-kotlinx-datetime-sweep.md` | 1 | Sr Shared-Core | p1-01 merged; M1 + M3 answered | p2-03, p2-04 | ☐ | ☐ | R8 smoke incl. a real reminder fire | 1.9.0 |
| `p1-05-assertk-residual-sweep.md` | 1 | Verification | p1-01…p1-04 merged; assertk added | p1-06 | ☐ | ☐ | none (say so) | 1.9.0 |
| `p1-06-viewmodel-init-rule.md` | 1 | Verification | p1-05 merged | **p1-07 — the ordering is the point** | ☐ | ☐ | none (say so) | 1.10.0 |
| `p1-07-koin-migration.md` | 1 | Sr Shared-Core + Verification | **1.9.0 live + 24–72h clean vitals**; p1-06; M2 answered | p2-05, p2-06, p2-07 | ☐ | ☐ | **8-step R8 smoke incl. a reboot and two alarm waits** | 1.10.0 |
| `p2-00-overview.md` | 2A | Staff | 1.10.0 live + clean vitals | rules R1–R9 for every p2 brief | ☐ | ☐ | — | — |
| `p2-01-asset-move.md` | 2A | Build & Release | 1.10.0 live; M4 answered incl. M4b | p2-02, p2-08 | ☐ | ☐ | R8 smoke | 1.11.0 |
| `p2-02-module-scaffolding.md` | 2A | Build & Release | p2-01 | p2-03 and everything after | ☐ | ☐ | light R8 smoke | 1.11.0 |
| `p2-03-platform-seam-interfaces.md` | 2A | **Staff — not delegable** | p2-02; p1-02 landed | p2-04…p2-09, all of Phase 3 and 4 | ☐ | ☐ | none (say so) | 1.11.0 |
| `p2-04-shared-domain-move.md` | 2A | Sr Shared-Core | p2-03 | p2-08 | ☐ | ☐ | R8 smoke | 1.11.0 |
| `p2-05-shared-data-move.md` | 2A | Sr Shared-Core (2nd) | p2-03 | p2-08 | ☐ | ☐ | R8 smoke | 1.11.0 |
| `p2-06-room-kmp-progress-persistence.md` | 2A | Sr Shared-Core + Verification | p2-04/05; **V2 = IDENTICAL**; **p1-08 fixtures committed** | the 1.11.0 tag | ☐ | ☐ | **upgrade-in-place R8 smoke** | 1.11.0 |
| `p2-07-datastore-relocation.md` | 2A | Sr Shared-Core (2nd) | p2-04/05; **p1-08 settings fixture committed** | the 1.11.0 tag | ☐ | ☐ | **upgrade-in-place R8 smoke** | 1.11.0 |
| `p2-08-plan-gates-to-commontest.md` | 2A | Verification | p2-01, p2-05 | p2-09 (specifies `BundleAssetIntegrityTest`) | ☐ | ☐ | none (say so) | 1.11.0 |
| `p2-09-ios-targets-and-bundled-database.md` | 2B | Sr Shared-Core + iOS Platform | **1.11.0 live + 24–72h clean vitals**; Xcode; Gate 0 V1 answered; p2-08 | Phase 3 | ⌘ | ☐ | App Groups ticked at bundle-ID registration | **nothing** |
| *(no brief exists)* UI dependency realignment | 2A→3 | **unassigned — see §12.B** | 1.11.0 live | Phase 3 | ☐ | ☐ | R8 smoke | **1.12.0 (D-PORT-9)** |
| `ios-delivery-pipeline.md` | 4–5 | Build & Release | p0; `shared/*` compiles for `iosArm64`; **owner's Apple prerequisites** | TestFlight, App Review | ⌘ | **$** | 7 GitHub secrets, `match` run locally once, CI cost model, the manual first submission | iOS 1.0.0 |

### The human gates

- Four Play tags, **each followed by a 24–72h vitals soak the owner must watch**.
- Gate 0's stop rule (§3).
- The owner's Apple critical path (§10).
- The reader gesture pass on **physical hardware** — Phase 3 exit.
- The **6–10h first device pass** and **3–4h first VoiceOver pass** — Phase 5.
- The **manual first App Store submission** (D-PORT-8).

### The two load-bearing orderings, restated so they cannot be lost

- **`p1-06` before `p1-07`** — build the detector before the change it is meant to detect. Moving
  `MainDispatcherRule` off `UnconfinedTestDispatcher` in a commit that changes nothing else means
  every test that reddens is **attributable to the dispatcher alone**.
- **`p2-01` before `p2-02`** — the six data gates must be proven still reading the right files before
  anything else moves. ADR-0009 and ADR-0011 **both claim "first"**; this is the tie-break.

### D-PORT-11 — `p1-08` starts during Gate 0, not after it

`p1-08` says "preconditions: none beyond Gate 0" and also "start this first, on day one of Phase 1."

**Resolved: the owner's device session starts in parallel with Gate 0.** D-PORT-1 forbids spending
*production Android risk* before Gate 0 closes; `p1-08` writes only `app/src/test/resources/` fixtures
and tests, ships zero production bytes and is in no release. Its cost if the port stops is one
afternoon of owner time plus two fixture files that would catch a future Android persistence
regression regardless of the port. **That is genuinely free option value — unlike the releases.**
Starting it late is the one scheduling mistake that cannot be recovered by adding engineers.

---

## 5. Phase by phase

Engineer-week ranges are the approach doc's, and are **order-of-magnitude, not commitments**.
Phase 1's range **predates the fourth release added by D-PORT-9**.

| Phase | Approach-doc range |
|---|---|
| Gate 0 | 1 engineer-week |
| Phase 1 | 5–8 |
| Phase 2 | 3–5 |
| Phase 3 | 4–7 |
| Phase 4 | 2–4 |
| Phase 5 | 2–4 |
| **Total** | **16–28 engineer-weeks ≈ 4–7 months as one stream** |

### Gate 0

| | |
|---|---|
| **Entry** | `p0` §3.B green |
| **Briefs** | the four `gate0-*` |
| **Assignees** | Sr Shared-Core, iOS Platform, Sr Shared-UI, Build & Release |
| **Ships** | nothing |
| **Exit** | as §3 |
| **Xcode / $99** | ⌘ partly / ☐ |

### Phase 1 — Android-only, shipped to Play. ☐ entirely

**Entry:** Gate 0 closed. **Briefs:** `p1-00` (rules) + `p1-01`…`p1-08`, plus the unwritten 1.12.0
brief. **Four releases now, each vitals-watched before the next.**

| Release | Contents | Order | Gate before the tag |
|---|---|---|---|
| **1.9.0 / 10900** | datetime + HTTP + IO + the assertion sweep | `p1-01` ∥ `p1-03` ∥ `p1-04` (group A) → `p1-02` (serial — shares six files with p1-01) → `p1-05` | the ADR-0009 epoch-day equivalence pin over **pre-1970 and leap days**, and `ProviderUrlBuilder` output **byte-identical** |
| **1.10.0 / 11000** | Hilt → Koin, **alone** | `p1-06` then `p1-07` | Koin `checkModules()` **as a CI unit test**, a ViewModel-construction smoke, and an **`assembleRelease`** on-device smoke |
| **1.11.0 / 11100** | Phase 2 tranche A | see Phase 2 | see §7 |
| **1.12.0 / 11200** | UI dependency realignment | D-PORT-9 | see §6 |

- **1.9.0 rationale:** nothing here can brick an install; failure modes are loud or already have a
  fallback. On the URL pin: `URLEncoder.encode` is **form-encoding** (space → `+`); Ktor's is
  **path-encoding** (space → `%20`). **Any diff is a bug in the port, not a stale test.**
- **1.10.0 rationale:** isolated because its failure mode is **the 1.7.0 class**. ADR-0012 says the
  decision is materially worse than Hilt **without** `checkModules()` in CI. The smoke is
  `assembleRelease`, **not `assembleDebug`**.

**Exit:** 1.12.0 live from the new module structure with **zero related crash signal after 24–72h**.
No iOS target exists yet.

### Phase 2 — the shared core compiles for iOS

**Tranche A (☐, ships as 1.11.0).** Order: `p2-01` → `p2-02` → `p2-03` → {`p2-04` ∥ `p2-05`} →
{`p2-06` ∥ `p2-07`} → `p2-08` → tag. **1.11.0 is a release event, not a work package.**

| Decision | Content |
|---|---|
| Room KMP driver | `AndroidSQLiteDriver` on Android, `BundledSQLiteDriver` on iOS. Swapping the SQLite engine under live production user data buys nothing and costs **~1.5–3 MB per ABI**; the Android choice is byte-parity for shipped users at zero size cost |
| DataStore path | pinned to `<filesDir>/datastore/settings.preferences_pb`. The format is identical — **the path is the whole risk, and getting it wrong is silent** |

**Tranche B (⌘, ships nothing): `p2-09`.**
**Entry:** 1.11.0 live + 24–72h clean vitals, Xcode installed, Gate 0 V1 answered.
**Exit:** `shared/domain` and `shared/data` compile for `iosArm64` and `iosSimulatorArm64`; tier-1
gates green in `commonTest` on iOS targets; the new `BibleDatabaseOpenTest` and
`BundleAssetIntegrityTest` green on a simulator, **and `BundleAssetIntegrityTest` demonstrated
failing** — a packaging gate nobody has seen fail is not known to work. **Highest-priority criterion
in the whole brief: `BibleDatabaseRoomOpenTest` (5) still green on Android** through the new
`BundledDatabaseProvider` path.

### Phase 3 — the shared UI and the iOS shell (⌘)

**No brief exists yet — Phase 3 briefs are written after Phase 2 closes.** Content per the approach
doc: 161 strings and 181 call sites to Compose Resources (~11 deliberately duplicated, each named and
commented); ~4,000 LOC of Robolectric-bound Compose tests re-hosted on `runComposeUiTest`; the
SwiftUI host wrapping one `ComposeUIViewController`.

**Good news carried forward:** string *values* do not change, so **~338 literal assertions survive
verbatim and only ~19 genuinely break** — the cost is the harness, not the strings.

Three exit criteria, **all criteria and not discoveries**:

1. Every screen renders in the simulator.
2. **The reader gesture pass on physical hardware.** `performTouchInput { longClick() }` is synthetic
   and **is not evidence**.
3. **One combined worst-case layout prototype** — 4-stream M'Cheyne day cards **plus** the stats
   panel, smallest iPhone, Dynamic Type at **AX5**, app slider at **1.5×**.

### Phase 4 — platform seam, parity, delivery (⌘ + $)

The first phase that genuinely blocks on Apple enrolment. **Brief:** `ios-delivery-pipeline.md`.

| Item | Note |
|---|---|
| `UNUserNotificationCenter` | + the §4.1 reminder decision |
| **Provisional authorization** | quiet delivery, no permission prompt — **strictly better** than the Android launch-time prompt D-S22-5 needed |
| `SFSafariViewController` · `UIPasteboard` · `NSDateFormatter` | direct seam implementations |
| `WidgetCenter` | a **no-op** |
| `ITSAppUsesNonExemptEncryption = false` | without it **every TestFlight upload stalls** on export compliance |
| `UIBackgroundModes` | **none declared at all** |
| three iOS workflows · `fastlane match` | delivery |

**Exit:** a tagged commit puts a signed build on TestFlight internal **with no local step**.

**The `ios-release-smoke` gate is mandatory from Phase 4 onward:** a release-configuration build on
physical hardware running a ~90-second scripted XCUITest of the **1.7.0 reading-tap crash**, the
**1.8.1 picker jump** and the **sprint-00F reader-load failure**; a **DCE canary** — Kotlin/Native
release links strip anything reachable only through the Obj-C bridge, and it surfaces as **a missing
symbol or a nil, not a stack trace**; and the ViewModel-init rule under `StandardTestDispatcher`.

### Phase 5 — hardening and App Review (⌘ + $)

Dominated by **owner hours and review latency.** Full device pass **6–10 focused hours over ≥2
sessions** (reminder rows require overnight waits); VoiceOver pass **3–4 hours the first time**, and
the warning stands verbatim: *if nobody on the team can drive VoiceOver competently, that is a hiring
or consulting line item, not an afternoon.*

> **D-PORT-8 — assume the first App Store submission is manual.** `RELEASING.md` records this
> project's most expensive pipeline lesson: the Play Developer API could not create the first release
> on a track that had never had one, forcing a manual Console promotion for 1.5.1. App Store Connect
> has the same shape. **Budget a manual first submission and a rejection round trip.**

**Exit:** parity matrix clean — **no `DEFECT` row, no shipping-feature row `UNVERIFIED` at a required
tier of T4.**

---

## 6. Resolution: E1 — the three unassigned dependency changes

**The finding first.** Build & Release **verified by grepping the briefs** (not assumed) that three
Android-user-facing dependency changes are assigned to **no release**.

| Change | Nature | Failure mode | Guard |
|---|---|---|---|
| `androidx.lifecycle:*:2.10.0` → `org.jetbrains.androidx.lifecycle:*:2.11.0` | **Forced** — `lifecycle-viewmodel-compose-iosarm64:2.10.0` is a **verified 404**; 2.11.0 returns 200. 2.11.0 is the floor | ViewModel / `SavedStateHandle` behaviour. A `SavedStateHandle` that silently stops restoring is **not loud** — the reader reopens at Genesis 1 (D-V3-13) with no crash and no compile error | M2 spike, plus p1-07's `SavedStateHandle` criterion |
| `androidx.navigation:navigation-compose:2.9.8` → `org.jetbrains.androidx.navigation:navigation-compose:2.9.2` | Different artifact lineage at a numerically **lower** version | A destination becomes unreachable, or a tab back-stack stops preserving (D-V3-16, D-D-3, U18) | **`NavRegressionTest` is the only guard** |
| Drop `material-icons-core:1.7.8`, vendor 9 glyphs | The JetBrains fork is frozen at 1.7.3 and publishes no `-android` variant | A missing or wrong glyph; **3 of the 9 are `AutoMirrored`** | The Compose UI suite; visual |

They appear in `p0-build-foundation.md` §3.B.3 and §3.B.7 and in **no** `p1-*` or `p2-*` brief. p0
explicitly disclaims the vendoring itself: *"the vendoring is Shared-UI's task, not mine; my contract
is that the dependency is gone and the list is exactly these nine."*

> **D-PORT-9 — a fourth Android-only Play release, `1.12.0 / 11200` "UI dependency realignment",
> after 1.11.0 and before Phase 3 begins.** Contains all three changes and nothing else.

**Reasoning, in order:**

1. **Not 1.11.0.** Agreed with Build & Release, for their reason: 1.11.0's failure mode is **silent
   data loss**, and adding a navigation lineage swap destroys the bisection value that justified
   isolating it. Generalise the rule: **nothing merges into 1.11.0.**
2. **Not 1.9.0 either**, which is where Build & Release's alternative put them. 1.9.0's whole
   characterisation is *"nothing here can brick an install; all failure modes are loud or already
   have a fallback."* A navigation fork swap can plausibly make a screen unreachable, and a lifecycle
   swap can silently break `SavedStateHandle` restoration. **Neither is loud-with-a-fallback**, and
   both belong to a different family than datetime/HTTP/IO. 1.9.0 is already the largest mechanical
   change in the port (`p1-02` alone touches **36 main and 38 test files**); adding a third failure
   family to it trades away the homogeneity that is the entire reason for four releases.
3. **Not inserted before 1.10.0**, which is where the coupling argument would put it. There is a real
   coupling nobody stated: **`koinViewModel()` resolves against the lifecycle artifact**, and M2
   answers "does `SavedStateHandle` survive Koin construction" on the **old** artifact. Ideally
   lifecycle swaps first so Koin lands on its final substrate. **Rejected on cost:** inserting a
   release renumbers 1.10.0 and 1.11.0 across ~20 briefs, and a missed edit means a brief targets a
   release that does not exist — precisely the defect class the approach doc's A9 warns about. The
   coupling is instead discharged as **an explicit acceptance criterion on 1.12.0** (gate 3 below).
   Re-running one smoke is cheaper than renumbering twenty briefs.
4. **Appending, not inserting, is the point of the number.** `1.12.0` changes **no existing brief's
   release label**. Every `p1-*` and `p2-*` header stays correct as written.
5. **Placement before Phase 3 is where it is actually forced.** None of the three is needed until
   `shared/ui` gets Compose content, which is Phase 3 — `p2-02` creates `shared/ui` **empty**. But all
   three are Android-user-facing the moment they land, so letting them drift into Phase 3 means they
   reach real users bundled with the largest UI churn in the program and with **no Play release
   boundary at all**. 1.12.0 is the natural handoff between Phase 2 and Phase 3, its soak overlaps
   Phase 3's opening test-harness work (which is not UI-dependency-sensitive), and it lands the swap
   **in the module world it will live in**, after the module split has already shipped and soaked —
   so a nav defect has **exactly one candidate cause**.

**Mandatory gates before the 1.12.0 tag:**

1. **`NavRegressionTest` green** — it is the only guard on the nav swap. If it does not currently
   cover a destination or a back-stack, **extend it before the swap, not after**.
2. The full Compose UI suite green, **test count unchanged, zero deletions**.
3. **Re-run `p1-07`'s ViewModel-construction smoke and its `ReaderViewModel`-with-a-real-
   `SavedStateHandle` criterion on the new lifecycle artifact.** M2 and p1-07 answered that question
   against `androidx.lifecycle 2.10.0`; this release changes the substrate underneath it. **This
   criterion exists solely to discharge the coupling in point 3 above and must not be dropped as
   redundant.**
4. All 10 vendored glyphs visually compared against the originals, and the 3 `AutoMirrored` ones
   vendored **as auto-mirroring** rather than silently losing the property — the app is not localized
   (ADR-0013) so it is inert today, **which is exactly why nobody would notice**.
5. `grep -r "material-icons" --include=*.kts --include=*.toml .` is **empty**.
6. An **`assembleRelease`** on-device smoke that walks **every** navigation destination and **both**
   tab back-stacks, including a tab switch and back. **Not `assembleDebug`** — the 1.7.0 lesson.
7. The six data gates report unchanged counts: **11 / 10 / 8 / 6 / 18 / 5**.

**The cost to the owner, stated plainly:** this takes the release count **from three to four**. The
escape valve in approach §10 #8 still works — **merging 1.9.0 and 1.10.0 returns to three cycles**, at
the price of losing the DI isolation that the 1.7.0 P0 is the argument for. **Nothing may merge into
1.11.0, and nothing may merge into 1.12.0 from 1.11.0.**

**Not ready:** **no brief exists for 1.12.0.** See §12.B.

---

## 7. Resolution: E2 — the four variables in 1.11.0

1.11.0 carries the **asset move** (`p2-01`), the **module split** (`p2-02`…`p2-05`), the **Room
relocation** (`p2-06`) and the **DataStore relocation** (`p2-07`) — four variables, **two of which
touch irreplaceable shipped-user state.** Staff raised it because the owner signed a release *count*,
and would rather they know what is inside 1.11.0 than discover it.

> **D-PORT-10 — ship 1.11.0 as signed off. Do not split it.** Three conditions are binding, and the
> third **corrects the owner's monitoring instruction**.

**Reasoning:**

1. **The two stores fail differently and distinguishably, so a release boundary buys almost nothing
   here.**

   | Store | Failure mode | Signal |
   |---|---|---|
   | Room | **loud** — crash-on-open, visible in Play vitals within hours; `fallbackToDestructiveMigration` is off so it **cannot** silently delete | a crash cluster on DB open |
   | DataStore | **silent** — settings reset, tracking-start lost, plan reverts to Bible Companion, first-run dialogs re-fire | a wave of "why did my settings reset" |

   A symptom identifies its cause **with no bisection**; and neither is the module split or the asset
   move, which are structurally verified by the six gates and the three byte-diff CI jobs. Bisection
   value is what a release boundary buys, and **these failure modes self-identify.**
2. **Staff is right that the real control is the upgrade-in-place smoke, not the boundary.** `p2-06`
   and `p2-07` each carry a mandatory R8 **upgrade-in-place** device smoke against PG-1/PG-2 fixtures
   captured from a real shipped 1.8.1 device. A release boundary catches a defect *after* it reaches
   users; **the upgrade smoke catches it before.** A fresh install proves nothing about a relocation.
3. **A point neither reviewer made, and it is decisive: splitting 1.11.0 would be actively worse.**
   `p2-06` and `p2-07` move `data/progress/**` and `data/prefs/**` **into** the modules that `p2-02`
   creates. Shipping the module split without them puts a **deliberately half-migrated persistence
   layer into production** — `shared/data` populated by `p2-05` while progress and prefs remain in
   `:app` — a topology **no rule in `p2-00` describes**, that nobody designed, and that would sit on
   real users' phones for the duration of a soak. **Four designed variables in one release is a
   smaller risk than one undesigned topology in production.**

**Binding conditions:**

1. **`p2-06` and `p2-07` do not merge until PG-1 and PG-2 are green against the real 1.8.1 fixtures.**
   This makes `p1-08` a **hard blocker on 1.11.0**, not a nice-to-have — which is why it is Action 2
   in §1. The existing `ProgressMigrationTest` **synthesizes** a v1 database and therefore does **not**
   prove that a file written by the shipped Room runtime on a real device opens against the ported
   one. **That distinction is the entire reason `p1-08` exists.**
2. **Both upgrade-in-place smokes are run separately**, each from a device that was on 1.10.0 with
   real state: two plans, two years, one partial and one complete day, and **every** setting away from
   its default. **Check each value individually, not "it looks fine."**
3. **The 24–72h vitals soak is not a sufficient gate for 1.11.0, and the owner must be told why.** A
   DataStore path error produces **no crash signal at all** — Play vitals will be clean while every
   user's settings are quietly resetting. "Watch vitals" is the correct instruction for the Room half
   and **the wrong instruction for the DataStore half.** For 1.11.0 the owner's soak must additionally
   include: an **upgrade-in-place check on the owner's own device** (confirm theme, font scale,
   tracking start, plan, destination, reminder and the partial check all survived, and that **no
   first-run dialog appears**), and monitoring **reviews and support mail**, not just the vitals
   dashboard.

**The owner is not being asked for a fifth release.** The count stays at four (§6), and 1.11.0's
contents are now **on the record rather than discovered**.

---

## 8. Owner question: E3 — A11Y-03 (do not decide this)

**Engineering has deliberately not decided this.**

`parity-matrix.md` row **A11Y-03**, tier **T4**, currently
`UNVERIFIED (int: DIVERGENT or DEFECT — undecided)`, referencing approach §3 **G7**.

**The process defect that caused the escalation:** G7 says it *"needs an owner/accessibility decision,
not a silent ship"* — but it **never made the §10 decision list**, so **nobody would have asked**.

**The question, in owner-facing words:**

> When someone uses your app with VoiceOver — the iPhone's screen reader — every verse of scripture is
> announced with the word **"button"** after it. Reading Psalm 119 aloud that way means hearing
> "button" 176 times. This happens because each verse is tappable (tap opens the verse menu,
> long-press starts a selection), and the app currently tells the system that a tappable thing is a
> button. **Android does exactly the same today** — TalkBack says "button" after every verse — so this
> is not something the port breaks. The port just makes it newly visible, and it needs a decision
> before someone spends 3–4 hours doing the first VoiceOver pass and finds something they have no
> authority to act on.

| Option | What it does | Cost | Consequence |
|---|---|---|---|
| **(a) Keep it** | Announce "button" on both platforms, and record it as a **signed `DIVERGENT`** in the parity matrix | **none** | Defensible — it matches shipped Android behaviour and the tap really is a button. **Verification's recorded fallback** |
| **(b) Drop the role on iOS only** | Expose the verse action as a VoiceOver **custom action**, keep the tap affordance in `onClickLabel` | a small Phase 4 task plus an ADR | Better listening experience. **Constraint:** it is a platform-conditional UI decision and `p2-00` rule **R2 makes `if (isIOS)` a build failure**, so it must live at a seam. `p2-03`'s inventory currently has exactly **two** `expect`/`actual` declarations in `shared/ui` (`dynamicColorScheme`, `SystemBackHandler`); **adding a third is Staff's call under R3**. **Verification's recommendation** |
| **(c) Drop it on both platforms** | Changes shipped Android behaviour | — | The port is explicitly **not** supposed to do that (approach §9 item 9: not refactoring during the port). **Listed for completeness, not recommended** |

**Two deadlines, and the earlier one is the one that matters:**

- **Hard deadline:** before the first VoiceOver pass is scheduled (Phase 5). After that, the pass
  produces a finding it cannot act on.
- **Cheap deadline:** before the `shared/ui` reader screen is written in **Phase 3**. If the answer is
  (b), the seam is designed **once, with the screen**; retrofitting a semantics seam into a written
  reader costs materially more, and `p2-03`'s platform contract is Staff-owned and explicitly *"does
  not get edited"* after it lands.

Until the owner rules, **A11Y-03 cannot be assigned a terminal status** — `DIVERGENT` requires a
recorded decision, `DEFECT` requires that the difference be unintended, and **nobody may quietly pick
one.** That is parity rule 6: ambiguity between `DEFECT` and unrecorded intentional divergence is an
**escalation**, not a judgement made in the file.

---

## 9. The parallelism lever (make it impossible to miss)

**Build & Release presented "Phase 1 alone is 3.5–4.5 weeks and produces no iOS binary" as a warning.
It is the plan's single largest schedule lever.** Apple enrolment has unpublished multi-day latency
and Xcode is a 17 GB install that has not started; **producing no iOS binary for the first two months
is a feature.**

| ☐ Needs neither Xcode nor Apple | ⌘ Needs Xcode | $ Needs the $99 enrolment |
|---|---|---|
| `p0` §3.B | `gate0-reader-gesture-rig` | **only** the delivery pipeline's signing / TestFlight / App Store Connect half (`ios-delivery-pipeline.md`) |
| `gate0-minor-spikes` M1 / M3 / M4 | the cross-target half of `gate0-room-identity-hash` and `gate0-schema-tripwire` | Phase 5 |
| **all of Phase 1** — `p1-01`…`p1-08`, releases 1.9.0, 1.10.0, 1.12.0 | `gate0-minor-spikes` M2's iOS half | |
| **all of Phase 2 tranche A** — `p2-01`…`p2-08`, release 1.11.0 | `p2-09` (tranche B) | |
| `p1-08`'s owner device session (**Android** device, not Apple) | all of Phase 3; the rest of `p0`; Phase 4 build work | |

Stated twice in the briefs and worth repeating: **the gesture rig does not need the $99 program.**
Xcode **free provisioning** signs a build onto your own device for 7 days (INFERRED, standard Xcode
behaviour, unverified here; **if it fails it becomes an Owner + Build & Release escalation**, because
it moves the program's critical path).

**The sentence that should be impossible to miss: four Play releases and eight of the ten `p2-*`
briefs — the majority of the program's engineer-weeks — need no Xcode and no Apple money.** The
owner's Apple critical path and Phase 1/2A run **fully concurrently**. The only thing Xcode gates
*early* is **Gate 0's closure** (§1, Action 1) — and that is why Xcode is Action 1 rather than a
Phase 2 prerequisite.

---

## 10. The owner's critical path (live checklist, ordered by latency)

`docs/RELEASING-IOS.md` was amended today (2026-08-08) and **Part 6 carries the canonical checklist**.
This is that list with status, and with the §4.2 App Groups correction folded in.

**Already DONE:** scope sign-off of approach §3 (no widget, no persistent tray notification, no
in-app updates, no MySword — **therefore no glanceable surface on iOS**); the generic reminder body
for v1.0 (§10 #3 option (a)); App Group reserved from the first build (§10 #4 = D-PORT-4); reader
stays in v1.0 (§10 #11 answered "no, keep it"); Android feature freeze accepted (§10 #7); three
Android-only Play releases accepted (§10 #8) — **now four, pending the owner seeing D-PORT-9**.

**Outstanding, ordered by latency:**

| # | Item | Latency | Status | Blocks |
|---|---|---|---|---|
| 1 | Free disk / install **Xcode** / install a **managed Ruby ≥3.1** | 1 evening | VERIFIED today: not installed; Ruby 2.6.10; 126 GiB free | **Gate 0 closure** |
| 2 | Decide **entity type** — Individual vs Organization (**Individual recommended**; your legal name becomes publicly the seller) | a decision | open | enrolment |
| 3 | **Enrol via the web path**, not the Apple Developer app (the app path mandates a photo-ID scan and bills as an auto-renewing subscription) | **Apple publishes no timeline**; contact Developer Support if no confirmation in 24 hours | open | everything Apple |
| 4 | **Register the bundle ID `com.jpillion.dailyreadingplanner` with App Groups ticked, and register `group.com.jpillion.dailyreadingplanner`** | 5 minutes | **the most time-sensitive item on the list** — not because it is slow but because it is **ordered** | every `fastlane match` profile |
| 5 | Create the **App Store Connect record** to reserve the name | the hour enrolment clears | "Daily Bible Reading Planner" is 27/30 chars, globally unique, first-come. Fallbacks in order: `Daily Bible Reading Plan`, `Bible Companion Readings`, `Daily Readings — Bible` | the name |
| 6 | **`p1-08` device session** | **multi-day calendar latency** | **start it now** (§1 Action 2) | **the only owner item that blocks Phase 1's fourth release path (1.11.0)** |
| 7 | App Store Connect **API key** (App Manager, **not** Admin; the `.p8` downloads **exactly once**) → certs repo + `MATCH_PASSWORD` + `fastlane match appstore` run locally once → the **seven** GitHub secrets | hours | open | Phase 4 CI |
| 8 | **Choose the macOS CI cost model.** GitHub Free on a private repo gives ~200 macOS minutes at the 10× multiplier — **less than one release run**. **Engineering must not choose this for you** | a decision | until you do, the workflows read `runs-on: ${{ vars.IOS_RUNNER \|\| 'macos-latest' }}` so the switch is a repository-variable change | Phase 4 CI cost |
| 9 | **Author and host a privacy policy URL** | owner time | **mandatory for every App Store app, no exceptions**, and nothing in this repo records one | App Review |
| 10 | Decide the **App Privacy questionnaire** answer for the translations proxy + API.Bible FUMS reporting | a decision | **the answer most likely to draw an App Review question** | App Review |
| 11 | Decide the **content-rights** answer (KJV public domain; **NKJV and NASB are licensed** via API.Bible) | a decision | open | App Review |
| 12 | **1024×1024 icon from vector source** | owner time | the current asset is a **2× upscale**, and its three dots signified the Bible Companion's three streams — mildly inaccurate since 1.5.0 when plans gained 1, 3 and 4 streams | submission |
| 13 | **Screenshots** — iPhone-only for v1 recommended | owner time | blocked on the app existing | submission |
| 14 | Decide whether the standing **string tone sign-off backlog** is cleared or ships as draft copy on a second platform too | a decision | open | — |
| 15 | **New, from D-PORT-9:** accept a **fourth** Android-only release, or elect to merge 1.9.0 and 1.10.0 to stay at three | a decision | open | Phase 1 shape |

---

## 11. Standing rules (every task, every phase)

0. **THE SHIPPED ANDROID CODE IS THE SPEC. Documentation supplies *why*, never *what*.**
   *(Owner-directed, 2026-08-08: "use the Android code as documentation so it matches the actual
   live Android app.")* This rule outranks every other document in the repo, including this one.

   - **What the port must reproduce is the behaviour of the code at `main`** — not what `CLAUDE.md`,
     a `docs/features/*.md` spec, an ADR, or a task brief says the code does. Where a document and
     the code disagree, **the code wins, and you report the discrepancy** rather than silently
     following either.
   - **`CLAUDE.md` and the `D-*` decision codes are for rationale only** — why Feb 29 has no
     readings, why `exportSchema` was `false`, why the persistent notification defaults on. That
     context is load-bearing (it stops an implementer "fixing" a deliberate decision) and it is the
     *only* thing code cannot tell you. It is **not** a source of truth for behaviour.
   - **This is not hypothetical — Phase 0 found six live drifts** and every one was caught by
     reading the code: the log said 936 tests (**940**); five data gates (**six** — 11/10/8/6/18/5,
     re-run and confirmed 2026-08-08); `D-K-HINT-2` places the reader footer inside the verse
     `LazyColumn` (**the code moved it out**, which changes the reader's vertical budget); the a11y
     gate described as "13 assertions" (**13 tests, ~60 assertions** — sizing off the doc understates
     it ~4×); `ProgressMigrationTest`'s KDoc describes schema wiring **that does not exist**; and
     `docs/data/README.md` still records a superseded `bible.db` SHA.
   - **Beware the team roster specifically.** `teams/ios-port/README.md` is a *generic template*
     written for a different product. It repeatedly names TTS, `AVSpeechSynthesizer`, audio-session
     configuration, background audio and lock-screen controls. **This app has none of that** —
     verified 2026-08-08: zero `TextToSpeech`, zero `MediaPlayer`, zero `AudioManager` anywhere in
     `app/src/main`, and `bible/ui/reader/ReaderAudioSlot.kt` is a **literally empty composable**
     (a four-line function with an empty body, D-V3-14). **Schedule no audio work.** If a brief,
     roster or phase table implies audio, it is template contamination — reject it and say so.
     *(Note for clarity: **VoiceOver is not audio.** It is iOS's screen reader, the direct
     equivalent of Android's TalkBack, which this app already supports and gates. Accessibility
     work is in scope; audio playback is not.)*
   - **Practical test before you write a line:** can you point at the file and line in
     `app/src/main` that defines the behaviour you are porting? If not, you are porting a document.

1. **An `assembleRelease` on-device smoke before every Play tag, never `assembleDebug`.** The 1.7.0
   P0 — a crash on every reading tap — reproduced **only under R8**; the owner's device pass at the
   time was on a debug build and passed. Briefs that require *no* smoke must **say so explicitly**
   rather than omitting it.
2. **A ported test is not ported until its original mutation has been re-killed.** Not "it passes",
   not "it compiles": the mutation is re-applied to the *ported* production code, the *ported* test is
   observed going red, and production is restored **byte-identically, SHA-256-verified**. CLAUDE.md
   records **160 named mutations across 34 sprint entries** — a ready-made, zero-authoring-cost
   regression suite. The **two that SURVIVED** (sprint 00Q: deleting the `BackHandler`; Copy firing
   without dismissing the menu) are worth re-running first.
3. **No parity row may be marked green on a tier lower than the behaviour requires**, and **you may
   not resolve an `UNVERIFIED` row by narrowing it.** Rewording a row to fit the evidence you have is
   the most common way a parity matrix becomes decorative. A mis-specified row is an **escalation and
   a commit**, never a quiet edit.
4. **Not evidence, ever:** "the shared code is the same, so it must work"; "it compiled"; "the Android
   test passes"; "an agent reported it done"; a green `AccessibilityGateTest` for **any** accessibility
   row.
5. **The gate ledger is reported as `common: 35 · jvm: 18 · android: 5 · ios: 10 (both new)`. Never
   "all gates run everywhere"** (D-PORT-6). And there are **six** data gates today, not five —
   `PlanSegmentGateTest` (6 assertions, 0 violations across 2,920 portions) postdates several briefs.
   Counts: **11 / 10 / 8 / 6 / 18 / 5**. **Any change to any of them is stop-and-escalate.**
6. **`data-rebuild` never moves to a macOS runner.** Its `LD_PRELOAD` of a self-compiled SQLite 3.43.2
   has **no macOS equivalent** — `DYLD_INSERT_LIBRARIES` is SIP-blocked. Moving it reopens the defect
   that sat red on `main` for six weeks. Anyone proposing it is not raising an escalation; they are
   **proposing something already rejected.**
7. **`fallbackToDestructiveMigration` stays off. Ever. Not "temporarily during development."** It
   converts a loud, diagnosable crash into **the silent deletion of every user's reading history**.
8. **Exactly one copy of the plan JSONs and `bible.db` in git.**
   `find . -name bible.db -not -path './*/build/*'` returns **one** path.
9. **No refactoring during the port.** The one exception is **ADR-0007 Stage 1b**, which is a forced
   choice, not opportunism.
10. **`if (isIOS)` in shared code is a build failure** (`p2-00` R2). Platform difference lives at a
    seam, and **`shared/platform` is Staff-owned**: implementers may not add, rename or widen an
    interface.
11. **A check nobody has seen fail is not known to work.** Every guard added by this program — the CI
    boundary check, the corrupt-a-byte probe, `BundleAssetIntegrityTest`, PG-3, the archive-staleness
    proof — must be **demonstrated failing and then restored, with the output quoted.**
12. **`shared/ui` may never depend on `shared/data`**, and the absence is **load-bearing, not
    incidental**.
13. **A dropped test is a failed task.** Test counts strictly increase or stay equal; **zero deletions
    without an explicit, justified line in the PR.**
14. **Report VERIFIED / INFERRED / UNVERIFIED with the evidence, never "done."**

---

## 12. What is not ready (honest)

### A. Blocked on a Gate 0 answer — `p2-09` needs a Staff rewrite if the identity hash returns MISMATCH

`p2-09-ios-targets-and-bundled-database.md` is written **Stage-1a-shaped** and **inherits the branch
rather than choosing it**. Its **only** Stage-1b provision is one sentence scoped to a build script.
If Gate 0 V1 forces Stage 1b, these become invalid and **Staff must rewrite before dispatch**:

| Invalidated | Why |
|---|---|
| the `Room.databaseBuilder<BibleDatabase>(path)` mechanism | there is **no Room `BibleDatabase`** to build under 1b |
| acceptance criterion 5 — *"`BibleDatabaseRoomOpenTest` (5) still green on Android"*, described in the brief as **its highest-priority criterion** | **its subject ceases to exist** |
| the `/5` in the six-gate count `11 / 10 / 8 / 6 / 18 / 5` | asserted in `p2-00` R6 and in **nearly every other `p2-*` brief** |
| two of `p2-09`'s own boundaries | they **contradict each other** under 1b — `shared/assets/**` is declared read-only, while removing `ROOM_IDENTITY_HASH` and the `room_master_table` row **requires regenerating the asset** |

**The intermediate outcome nobody scoped:** if the hash MISMATCHes but is **stable and identical
across targets**, Stage 1a survives and the fix is one constant in `tools/build_bible_db.py` plus a
`data-rebuild` re-run — but **`p2-09` puts both files outside its write set**, so **no brief currently
owns that fix.**

### B. Left open by E1 and E2

- **No brief exists for release 1.12.0.** `p0` §3.B decides the three dependency changes but
  **explicitly disclaims the glyph vendoring**; nothing assigns the lifecycle swap, the navigation
  swap or the vendoring to an executable task with acceptance criteria. **This is the single largest
  gap in the plan and it has no owner.** It needs a brief from **Build & Release** (the two coordinate
  swaps) and one from **Sr Shared-UI** (the 9 glyphs, against `p0` §3.B.7's enumerated list), both
  against the gates in §6.
- **A11Y-03 is undecided** and cannot be given a terminal status until the owner rules (§8).
- **Two brief-level contradictions need a ruling from their owners, not from an implementer at 5pm:**
  - `p1-05` acceptance criterion 2 requires **Truth removed** from `gradle/libs.versions.toml`;
    `p0-build-foundation.md` §3.B says *"Truth **stays** for tests that remain in
    `jvmTest`/`androidUnitTest`. Do not delete it."* **Direct conflict, and `p1-05` cannot close until
    it is resolved.**
  - `gate0-minor-spikes.md` appendix **V5** still poses the `material-icons-core` question as **open**
    and estimates **"~6 glyphs"**, while `p0` §3.B.7 **already decided it** (vendor them) and
    enumerates **10**. **V5 should be struck** and replaced by the Shared-UI vendoring ticket from
    item 1 above.
- **`gate0-room-identity-hash.md` does not acknowledge its own Xcode dependency** — it mandates an
  `iosSimulatorArm64` target while presenting itself as immediately runnable.
- **One open Staff call inside `p0` §3.C:** whether a `jvm()` target exists in `shared/data` to keep
  `BibleTextVerificationTest` on sqlite-jdbc, or whether that test stays in `:app`'s
  `androidUnitTest`.
- **Phases 3, 4 and 5 have no task briefs** beyond `ios-delivery-pipeline.md`. That is **correct
  sequencing, not an omission** — but it means **this plan is executable in detail only through Phase
  2**, and **Phase 3 briefs must be written before Phase 2 closes.**

### C. INFERRED, not verified — every one of these a build would settle

| Claim | Label | What would settle it |
|---|---|---|
| Kotlin/Native cannot compile Apple targets with Command Line Tools alone | INFERRED | installing Xcode and trying |
| Xcode free provisioning signs a 7-day build onto your own device without the $99 program | INFERRED | the gesture rig's first install |
| `macos-latest` is Apple Silicon | INFERRED | one CI run. **Load-bearing**, because `iosX64` is **unavailable** in CMP 1.11.1 (**VERIFIED**: `ui-iosx64:1.11.1` is a 404; last published `1.11.0-alpha01`). That is not a cost optimisation — **an Intel Mac cannot build this app**, and an Intel-Mac contributor is unsupported |
| The `.ipa` size | **UNVERIFIED** | the first real archive. **Android's 12 MB gate must NOT be inherited** — set the number from the first archive |
| Symbolication of Kotlin frames from a static framework's dSYM | INFERRED mechanism, **empirical criterion by design** | a deliberate crash from a **release** build on hardware |
| Gradle configuration-cache incompatibility of the iOS link tasks | INFERRED, **not reproduced**, because there is no Xcode here | a real configuration-cache run |
| **A3:** the hybrid reminder design is impossible — a repeating `UNCalendarNotificationTrigger` has no start date and cannot be date-excluded, so inside the horizon it fires alongside every dated one-shot | INFERRED from the UNNotification API contract | the Phase 4 spike |
| XcodeGen can express the `embedAndSignAppleFrameworkForXcode` run script in first position | **UNVERIFIED** until it runs | running it |
| The **940-test baseline** and the six gate counts | **asserted from CLAUDE.md, not re-measured for this plan** | the kickoff (§13). **Do not carry the number forward on trust** |

**Standing warning.** `testing/MainDispatcherRule.kt` moving from `UnconfinedTestDispatcher` to
`StandardTestDispatcher` **will turn currently-green tests red, and those reds are findings, not
conversion damage.** `Unconfined` is the most forgiving possible scheduling and is a large part of why
the 1.7.1 ordering bug hid in a green suite. Fix the fallout by adding explicit `advanceUntilIdle()` /
`runCurrent()` where the test actually needs them — **never** by reverting a file to `Unconfined` to
make it pass. **If more than ~10 test files redden, that is a larger finding about the suite's
dependence on eager dispatch and it changes the estimate for `p1-07`.**

---

## 13. Kickoff checklist (run and verify before the first brief executes)

1. **Repo state.** `git status` is clean or its untracked docs are understood; HEAD is `1bcc98e` or
   later; branch is `main`. **Note (VERIFIED today): the entire port documentation set is currently
   UNTRACKED** — `docs/adr/`, `docs/task-briefs/`, `docs/ios-port-approach.md`,
   `docs/parity-matrix.md`, `docs/port-inventory.md`, `docs/test-port-strategy.md`,
   `docs/RELEASING-IOS.md` and this file are not committed, and `CLAUDE.md` is modified. **Commit them
   before dispatching anything** — the entire plan depends on agents reading files that are currently
   one `git clean` from gone.
2. **The baseline, measured not assumed.**
   `./gradlew spotlessCheck lintDebug assembleDebug testDebugUnitTest koverXmlReportAppDebug koverVerifyAppDebug`
   green **from clean**, and the test count recorded. CLAUDE.md asserts **940** at 1.8.1 / 10801.
   **Record what you actually measure; if it is not 940, that discrepancy is the first finding.**
3. **The six data gates present and green, counts recorded** — all VERIFIED present on disk today:

   | Gate | Path | Count |
   |---|---|---|
   | Bible Companion plan | `app/src/test/kotlin/com/jpillion/dailyreadingplanner/data/plan/ReadingPlanVerificationTest.kt` | 11 |
   | M'Cheyne | `.../data/plan/McheynePlanVerificationTest.kt` | 10 |
   | Chronological | `.../data/plan/ChronologicalPlanVerificationTest.kt` | 8 |
   | Segments | `.../data/plan/PlanSegmentGateTest.kt` | 6 |
   | Bible text | `app/src/test/kotlin/com/jpillion/dailyreadingplanner/bible/data/BibleTextVerificationTest.kt` | 18 |
   | Room open | `.../bible/data/BibleDatabaseRoomOpenTest.kt` | 5 |
   | *(plus)* a11y gate | `app/src/test/kotlin/com/jpillion/dailyreadingplanner/ui/AccessibilityGateTest.kt` | 13 |

4. **`./gradlew bundleRelease` clean**, AAB size recorded against the **12 MB CI gate** (7.86 MB at
   1.6.0; 8.12 MB measured post-alt-schedules).
5. **CI green on `main` across all five jobs**, including the three asset byte-diff jobs.
6. **Delete `.github/workflows/zz-sqlite-probe.yml`** — it is titled "TEMPORARY - delete before
   merge", it is on `main`, and it is **registered as an active workflow**. Do not add three iOS
   workflows next to a stale one.
7. **Disk.** `df -h /` — **126 GiB available today (VERIFIED)**. Xcode budget ≈ **60 GB** including
   `~/.konan`.
8. **Toolchain, all VERIFIED today:** Java 17.0.18 present · Xcode **absent**
   (`/Library/Developer/CommandLineTools`) · `~/.konan` **absent** · Ruby **2.6.10**, needs a managed
   ≥3.1 · `xcodegen` **2.46.0 already installed** · fastlane **not installed**.
9. **Confirm the owner has the approach doc §3 scope sign-off recorded**, and knows §10 #8 now means
   **four** releases (§6) pending their acceptance.
10. **Read, in this order, before dispatching:** this file, `ios-port-approach.md` §§2–5 and §10,
    `p0-build-foundation.md` §3.B, then the brief you are about to dispatch. **Do not read all 25
    briefs into one context** — dispatch each brief to a **fresh agent as its standalone prompt**.
    That is what they were written for.
11. **Known stale record to correct while you are here** (pre-existing, non-blocking):
    `docs/data/README.md` still records the bible asset SHA as `ce174e9…29da4909`; the committed asset
    is `ad46a777…9099`, regenerated in sprint-00F.

---

## Decision index

| Code | Meaning | Defined in |
|---|---|---|
| **D-PORT-1** | The stop rule — Gate 0 before any production Android file changes | `ios-port-approach.md` §2 |
| **D-PORT-2…8** | Scope, App Group, versioning, gate ledger, manual first App Store submission and the rest of the signed port decisions | `ios-port-approach.md` §4 |
| **D-PORT-9** | A **fourth** Android-only Play release, `1.12.0 / 11200` "UI dependency realignment" | **§6, here** |
| **D-PORT-10** | 1.11.0 ships whole — do not split it; three binding conditions | **§7, here** |
| **D-PORT-11** | `p1-08` starts during Gate 0, not after it | **§4, here** |
| **D-PORT-12** | One phase vocabulary: Gate 0 + Phases 1–5 | **§2, here** |
| **D-IOS-1** | iOS versioning | `ios-delivery-pipeline.md` §3.1 |

---

**This plan is executable in detail through the end of Phase 2.** It has **one gap with no owner**
(the 1.12.0 brief), **one owner question that gates a Phase 3 design choice** (A11Y-03), and **one
brief that needs a Staff rewrite on a Gate 0 outcome that has not been measured** (`p2-09`).
Everything else is dispatchable as written.
