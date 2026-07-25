# Daily Reading Planner — PRD: Read aloud (audio)

> **Owner:** Maya (Product) · **Status:** Scope defined — **awaiting owner sign-off on the
> two-phase cut (§9) and the voice-source question (OQ-AUD-1)**; then EM (Morgan) + staff-eng
> (Diego) review · **Last updated:** 2026-07-25
> **Companion docs:** [docs/PRD.md](PRD.md) (V1/V2), [docs/PRD-v3.md](PRD-v3.md) (the in-app
> reader this feature sits inside), [docs/BACKLOG.md](BACKLOG.md) (#2 "Bible audio"),
> [docs/data/README.md](data/README.md) (provenance + AR-1), [CLAUDE.md](../CLAUDE.md).
>
> This document owns **what** read-aloud is and **why**. It defers **how to build** to Diego and
> **how to sequence** to Morgan. Owner decisions already locked are recorded in §7 as `D-AUD-n`
> and are **not** re-litigated here. Everything I had to assume is stated as an assumption and
> carried into the open-questions table (§11) — nothing is silently decided.

---

## 0. The framing shift — read this first

Audio has been explicitly out of scope three times, always for the same stated reason. PRD-v3 §3:

> **No audio.** Full KJV narration is ~1.6–2 GB and therefore *cannot be bundled* — it would
> require streaming/download, i.e. the **first network dependency this app has ever had**. That
> is an identity-level decision the owner has deliberately deferred. **Audio is V4, gated on a
> separate, explicit "are we willing to become a networked app?" decision.**

The backlog's parking lot says the same thing more bluntly: items #1, #2 and #4 all "want to
reach the network," and that is *one* owner decision, not three.

**That gate is now resolved, and resolved *without* becoming a networked app.** Sprint 23 proved
the precedent empirically: the Play In-App Updates dependency was added, the merged release
manifest was diffed with and without it, and it introduced **zero new permissions — no
`INTERNET`, no GMS permissions** (decision **D-L-6**). Play brokers the transfer through the Play
Store app; the app itself never holds a network grant. **Google Play Asset Delivery works the
same way.** Google hosts and serves the audio; the app requests an on-demand asset pack through
Play; the bytes arrive on disk; `INTERNET` stays out of the manifest and the Play data-safety
disclosure ("no data collected") is unchanged.

So the identity question the V3 PRD deferred has an answer that keeps the identity intact:

- **NFR-V3-A (offline / no-network) holds.** No `INTERNET`, no analytics, no telemetry, no
  server of ours, no account. Nothing about the user leaves the device.
- **NFR-V3-D (bundle budget) holds.** No audio byte ever enters the AAB. The CI bundle-size gate
  (12 MB; currently 8.12 MB) is untouched by design, not by luck.
- What *does* change: the app can now hold **optional, user-requested, Play-delivered content**
  that is not in the install. That is a real change in the product's shape and it is stated
  plainly here rather than smuggled in as a feature detail.

**Where the previous framing is superseded:** PRD-v3 §3 "No audio … audio is V4, gated on a
separate, explicit 'are we willing to become a networked app?' decision" and PRD.md §6's "audio
follow-along (V4 — gated on an explicit decision to become a networked app)". Those statements
stand as a record of V3's scope; **they are retired as a description of the product going
forward**, because the premise they rest on (audio ⇒ networking) turned out to be false. The
V3 PRD's *other* audio-adjacent statements — that the verse-id spine, `ReaderAudioSlot` and
`ReaderUiState.Content.activeVerseId` were reserved "so V4 follow-along is an additive drop-in"
(D-V3-14) — are not superseded; they are being cashed in.

---

## 1. Overview / vision

Open today's readings and tap **Read aloud**. The day's portion is read to you — Genesis 1–2,
start to finish — while you drive, cook, walk, or lie in the dark. If you have the app open, the
verse being read is quietly highlighted and the page follows along; if you don't, playback keeps
going with the screen off, and the lock screen and your headset buttons control it. In the Bible
tab, the same control reads whatever chapter you're on, and keeps going into the next one.

It is the same product it has always been — the schedule is still the front door, the reader is
still calm, marking is still marking. Read aloud is the third way into the same text: **read it,
tap out to your Bible app, or hear it.**

---

## 2. Problem & motivation

**The reading itself excludes people and moments.** V3 closed the gap between "tells you what to
read" and "lets you read it" — but "read it" still means *eyes on a screen, hands on a phone,
sitting still*. That excludes:

1. **The commute and the kitchen.** The single biggest block of unclaimed devotional time most
   people have is time when their eyes and hands are busy. A reader who wants to keep the plan
   and drives 40 minutes a day currently cannot use those 40 minutes at all.
2. **Readers whose eyes don't cooperate.** Aging eyes, low vision, fatigue, migraine, dyslexia.
   The app has a text-size slider (Sprint 8) and passes an accessibility gate, and neither of
   those helps someone who simply cannot read comfortably today.
3. **Readers who comprehend better hearing it.** Scripture was written to be heard aloud in
   assembly long before it was read privately. Plenty of readers follow along better with the
   words in their ears and their eyes on the page — which is why *follow-along*, not just
   playback, is the requirement.
4. **Falling behind.** Ruth's problem (PRD §3) is friction. "I don't have 30 minutes to sit and
   read" is a real, common reason the plan lapses. "I have 30 minutes of driving" is a real,
   common answer to it.

**Why this is not already solved by TalkBack.** *(Stated explicitly because it is the most
common way this feature gets waved away.)* TalkBack is a **screen reader**: it speaks whatever UI
element has accessibility focus, in the user's screen-reader voice, driven by exploration
gestures, and it stops when the user stops navigating. It is a *navigation* aid for blind and
low-vision users, and the app supports it well (NFR-V3-C, the `AccessibilityGateTest`).

A **read-aloud player** is a different job with a different quality bar:

| | TalkBack | Read aloud |
|---|---|---|
| Purpose | Navigate and operate the UI | Hear scripture continuously |
| Unit | The focused element | A whole portion / chapter, auto-advancing |
| Hands | Gesture per element | Press play once, then nothing |
| Screen off / backgrounded | No | **Yes** — the point |
| Lock screen / headset / car controls | No | **Yes** |
| Voice | The user's screen-reader voice, tuned for speed | A voice chosen for listening to scripture |
| Follow-along on screen | N/A | Verse highlight + autoscroll |

**We must not count TalkBack as "we already have audio," and we must not break TalkBack by
shipping this.** Both are stated as requirements (FR-AUD-22, NFR-AUD-C).

**Why now.** Every prerequisite already exists and was deliberately built for this: the KJV text
is on-device and gate-verified (V3 Sprint A); the verse-id spine addresses every verse; the
reader is verse-keyed with `activeVerseId` reserved; `ReaderAudioSlot` is an empty bottom bar
waiting; `PortionVerseBridge` already resolves a plan portion — including the Psalm 119 verse
windows — to exact verse ranges. The only thing missing was a way to deliver ~870 MB without
becoming a networked app, and Play Asset Delivery is it.

---

## 3. Target users & personas

Same general Christadelphian audience (PRD §3, PRD-v3 §4) — **not** a new audience, and
explicitly **not** a repositioning as an accessibility product. The existing personas extend, and
two facets that have always been present in the audience become servable for the first time.

- **Hannah — the daily reader.** Reads most mornings; values speed. *Read aloud gives her the
  mornings she currently loses:* press play, put the phone down, make breakfast, and the reading
  is marked done. She is the primary beneficiary, exactly as she was in V3.
- **David — syncing with the ecclesia.** Cares that the text is exactly right. He extends to
  caring that the **audio is exactly the right verses** — which is why the Psalm 119 window
  requirement (FR-AUD-6) is a hard gate for him, not an edge case, and why a mispronounced or
  skipped verse is a correctness bug, not a polish item.
- **Ruth — the returning reader.** Low friction, no guilt. Listening is the lowest-friction way
  back into a lapsed habit. The reader must not develop a "you only *listened*, you didn't
  *read*" tone — listening **is** reading (D-AUD-8).
- **The commuter / driver (new facet, not a new persona).** Eyes and hands unavailable. Requires:
  one press to start, zero interaction thereafter, background playback, lock-screen and
  headset/Bluetooth transport, audio focus that yields to navigation and calls. Safety framing:
  **the app must never require a glance at the screen once playback has started.**
- **The low-vision / print-disabled reader (new facet).** Wants scripture in their ears with the
  same dignity a sighted reader gets from the page — a good voice, correct verses, and controls
  they can find. Served *by* the player, *alongside* (never instead of) TalkBack support.
- **The read-along listener (new facet).** Hears and sees at once for focus and comprehension.
  Requires the follow-along half of the feature — verse highlight, autoscroll, tap-to-seek — and
  is the reason follow-along is P0 rather than a Phase-3 nicety.

---

## 4. Goals & non-goals

### Goals (continuing the G-numbering; V3 ended at G11)

- **G12 — Hear the day's readings.** One press on a reading plays that whole portion, correctly
  and completely, including verse-windowed days.
- **G13 — Hands-free and eyes-free.** Playback survives screen-off and backgrounding, and is
  controllable from the lock screen, notification, and headset/car controls.
- **G14 — Follow along.** With the app open, the verse being spoken is highlighted, the page
  scrolls to keep it in view, and tapping a verse seeks to it.
- **G15 — Keep the app's identity.** No `INTERNET` permission, no analytics, no account, no
  server of ours; the install stays small and the AAB stays under the existing 12 MB CI gate.
- **G16 — Honest, consenting downloads.** No byte of audio arrives without the user asking for
  it, knowing its size, and (by default) being on Wi-Fi; and every downloaded byte is visible
  and deletable.
- **G17 — Degrade, never dead-end.** If high-quality audio is not downloaded, not available, or
  the install did not come from Play, read aloud still works — at lower quality, with an honest
  explanation, never a broken button.

### Non-goals (explicit — do not build)

- **No runtime/cloud TTS per user.** Rejected on arithmetic, not taste (D-AUD-4, §7). Recorded so
  no one re-proposes it.
- **No self-hosted CDN, no our-server anything.** Google Play hosts the audio (D-AUD-1). We do not
  operate infrastructure, ever.
- **No voice picker / multi-voice / narrator marketplace.** Phase 2 ships **one** voice
  (D-AUD-13). Device-voice selection belongs to the OS; we may deep-link to system TTS settings,
  we do not build a chooser.
- **No dramatized, multi-speaker, or music-bedded audio.** Plain reading. This is scripture, not
  a production.
- **No audio for a second translation.** KJV-anchored, per PRD-v3 §3.
- **No background/automatic download of the whole Bible.** Not on install, not on first launch,
  not "while charging on Wi-Fi" without an explicit opt-in (D-AUD-11).
- **No listening streaks, listening stats, "minutes listened", or any second progress axis.**
  Listening is reading; it feeds the exact same marks (D-AUD-8). The no-guilt/no-gamification
  discipline (§13.0 of PRD.md, D-S17-1, D-S20-1) applies unchanged.
- **No per-verse audio clip sharing / export.** No sharing surface exists in the app and this
  does not introduce one; audio clips also carry a rights layer the text does not (AR-AUD-1).
- **No Android Auto, Wear OS, or Cast integration in scope.** Standard MediaSession means the
  lock screen and Bluetooth controls work for free; a *certified* Auto/Wear/Cast experience is a
  separate product with its own review requirements. Candidate, not scope.
- **No audio-driven auto-marking beyond the existing rule.** Pressing play marks read exactly as
  tapping to open marks read (D-AUD-8). We do **not** add "marked when playback completes."
- **No play control on the home-screen widget or in the persistent tray notification.** The
  widget is read-only by decision (D-S7-4) and the tray notification is content-only (Sprint 22);
  reopening either is out of scope. The **media notification** produced by the player is the
  playback transport and is expected — that is not the same surface (D-AUD-16).
- **No sleep-timer-as-alarm, no scheduled auto-play, no "wake me with scripture."** A sleep timer
  that stops playback is in scope (P1, D-AUD-14); a scheduler is not.

---

## 5. User stories & acceptance criteria

> **Numbering note.** PRD.md ran U1–U12 and PRD-v3 continued U13–U18, but the alternate-schedules
> feature spec restarted its own U1… namespace. To avoid a third collision, audio stories are
> **U-AUD-n**, matching the `FR-AUD-*` / `D-AUD-n` namespacing used throughout this doc.

### U-AUD-1 — Hear today's reading

*As Hannah, I want to press play on a reading and hear the whole portion, so I can keep the plan
on a morning when I can't sit and read.*

- Tapping the read-aloud control on a reading card starts playback of **that portion in full** —
  every chapter, in plan order, including the two-book Jun 19 / Dec 19 portion. **AC.**
- Playback stops at the end of the portion; it does not run on into the next stream or the next
  chapter of the Bible. **AC.**
- The reading is marked read at the moment playback is started, by the same one-way rule as
  tapping to open it (Sprint 00O). **AC.**
- Playback continues with the screen off and the app backgrounded. **AC (device-pass).**

### U-AUD-2 — Hear exactly the verses the plan assigns

*As David, I want a verse-windowed day to play only its verses, so what I hear is what the plan
says I read.*

- On Mar 9–12, the Psalms stream plays **only** Psalm 119:1–40 / 41–80 / 81–128 / 129–176
  respectively — starting at the first verse of the window and stopping at the last. **AC (gate,
  M-AUD-1).**
- The queue is built from the same `PortionVerseBridge` verse ranges the reader renders — not
  from chapter-file boundaries. **AC (gate).**

### U-AUD-3 — Listen hands-free

*As a commuter, I want to start playback and then not touch the phone.*

- Once started, no further interaction is required for the whole portion. **AC.**
- Lock screen, notification, headset button, and Bluetooth/car controls play, pause, skip, and
  show what is playing. **AC (device-pass).**
- Playback ducks or pauses for navigation prompts and calls, and resumes appropriately. **AC
  (device-pass).**

### U-AUD-4 — Follow along on screen

*As a read-along listener, I want to see where we are.*

- The verse currently being spoken is visually indicated (via the reserved `activeVerseId`).
  **AC.**
- The page scrolls to keep the active verse in view. **AC.**
- **If I scroll, the app stops fighting me:** autoscroll suspends until I resume it explicitly or
  playback moves to a new chapter. **AC.**
- Tapping a verse seeks playback to that verse. **AC.**
- The highlight is either correct or absent — a highlight that drifts onto the wrong verse is a
  defect, not a tolerance. **AC (tolerance bar in FR-AUD-11).**

### U-AUD-5 — Hear any chapter I'm browsing

*As David, I want the Bible tab to read the chapter I'm on.*

- In the Bible tab, the read-aloud control plays the currently displayed chapter. **AC.**
- Playback **continues into the next chapter**, across book boundaries, until I stop it, the
  sleep timer fires, or the canon ends. **AC.**
- Nothing is marked read — browsing is not a reading. **AC.**

### U-AUD-6 — Choose to download the good voice, knowingly

*As any user, I want to decide what audio lands on my phone.*

- No audio downloads without an explicit action of mine. **AC (gate, M-AUD-5).**
- Before a download starts I see **what** it covers and **how large** it is. **AC.**
- Downloads are Wi-Fi-only unless I explicitly allow cellular for that download. **AC.**
- Settings shows what I have downloaded and how much space it uses, and lets me delete any part
  or all of it. **AC.**

### U-AUD-7 — Press play on something I haven't downloaded

*As any user, I want play to do something sensible when the audio isn't here yet.*

- Pressing play with no downloaded audio for that passage offers the download (size + network
  stated) **and** offers to read it now with the device voice. **AC.**
- If the device has no usable TTS engine and nothing is downloaded, the control explains that
  plainly instead of failing silently. **AC.**
- On a non-Play (sideloaded) install, high-quality downloads are unavailable and say so once,
  clearly; the device voice still works. **AC.**

### U-AUD-8 — Listen without the app deciding things about me

*As Ruth, I want listening to count as reading and nothing else to happen.*

- Listening produces exactly the same mark as reading — no separate counter, badge, or copy that
  distinguishes them anywhere in the app. **AC (gate, M-AUD-7).**
- No copy anywhere in the audio feature scolds, congratulates, or nudges. **AC (the existing
  guilt-copy ban-scan, extended).**

---

## 6. Functional requirements

`FR-AUD-*` namespace. **Phase tags** (`[P1]`/`[P2]`) refer to the delivery phases in §9, **not**
to priority; priority is the P0/P1 grouping below, as in the other PRDs.

### P0 — must ship (the smallest lovable read-aloud)

**The player** *(source-agnostic — identical work whichever voice source wins)*

- **FR-AUD-1 (U-AUD-1, G12)** A **playback engine** plays an ordered queue of verses and reports,
  per verse, when it starts speaking it. The queue is the unit of play; a "chapter" and a
  "portion" are just queues. `[P1]`
- **FR-AUD-2 (U-AUD-3, G13)** Playback runs in a **foreground/background media session** that
  survives screen-off, app backgrounding and task-switching, publishes now-playing metadata
  (passage reference), and exposes play/pause/stop plus previous/next **verse-or-chapter**
  transport. `[P1]`
- **FR-AUD-3 (U-AUD-3, G13)** Lock-screen and notification controls, headset/Bluetooth media
  buttons, and **audio focus** handling (duck/pause/resume for calls, navigation, other media).
  `[P1]`
- **FR-AUD-4 (U-AUD-1)** **Auto-advance within the play unit**, with unit-specific stop rules
  (D-AUD-9): a **portion** plays its chapters in order and **stops at the end of the portion**; a
  **whole-day** queue plays each stream's portion in stream order and stops at the end of the
  day; a **browsed chapter** auto-advances into the next chapter continuously across book
  boundaries until stopped, the sleep timer fires, or Revelation 22 ends. `[P1]`
- **FR-AUD-5 (U-AUD-1, U-AUD-5)** **Entry points and their play units** (D-AUD-16):

  | Entry point | Plays | Auto-advance | Marks read |
  |---|---|---|---|
  | Read-aloud control on a Schedule reading card | that reading's whole portion | stops at end of portion | **yes** — that reading, on press |
  | Read-aloud action in the Schedule top bar `[P1 priority]` | all N streams for the displayed day, in stream order | stops at end of day | **yes** — each stream as its portion begins |
  | Read-aloud control in the reader, **Reading** context | the combined portion page | stops at end of portion | already marked at the originating tap |
  | Read-aloud control in the reader, **Browse** context | the displayed chapter | continues into next chapter | **no** |
  | Lock screen / media notification / headset | transport only — never starts a new unit | — | no |

  No read-aloud control is added to the home-screen widget or the persistent tray notification.

**Correctness (the part that must be gate-proven)**

- **FR-AUD-6 (U-AUD-2, G12)** **Verse-windowed portions are authoritative for audio.** A portion's
  audio queue is built from `PortionVerseBridge`'s verse-id ranges — the same seam the reader
  renders from — so a windowed reference plays **exactly** its verses (Psalm 119:1–40 and nothing
  else) and an unwindowed reference plays the whole chapter. Playing a chapter file end-to-end for
  a windowed day is a **defect**, and the release gate asserts it (M-AUD-1). `[P1+P2]`
- **FR-AUD-7 (U-AUD-2)** The queue includes **verse-0 superscriptions** where the text has them
  (117 of them), read as the title before verse 1 — the audio must not skip them and must not
  number them. `[P1+P2]`
- **FR-AUD-8 (G12)** The **two-book portion** (Jun 19 / Dec 19 = 2 John + 3 John) plays both
  books in order as one portion. `[P1+P2]`
- **FR-AUD-9 (U-AUD-2)** Spoken text is the **stripped plain text** (`MarkupStripper.strip`) —
  never markup tags, never verse numbers spoken aloud as content. *(Whether the reference is
  announced at the start of a chapter is a copy question, OQ-AUD-5.)* `[P1+P2]`
- **FR-AUD-10 `[P2]` (U-AUD-4, G14)** **Any downloadable audio artifact must carry a per-verse
  timing index** — for every one of the 31,102 verses (+117 superscriptions), the offset at which
  it begins in its chapter's audio. This is what makes FR-AUD-6 (verse windows), FR-AUD-11
  (highlight) and FR-AUD-12 (tap-to-seek) possible at all. **An artifact without verse timings is
  not shippable** (D-AUD-10). *This is the single requirement that most shapes engineering and
  most shapes the voice-source decision (§11, OQ-AUD-1).*

**Follow-along**

- **FR-AUD-11 (U-AUD-4, G14)** The verse being spoken is **visually indicated in the reader** via
  the reserved `ReaderUiState.Content.activeVerseId`. Tolerance: the indicated verse must be the
  verse actually being spoken; a highlight that is on the wrong verse is a defect. If timing
  confidence is unavailable for a passage, the app shows **no** highlight rather than a wrong one.
  `[P1+P2]`
- **FR-AUD-12 (U-AUD-4)** **Autoscroll** keeps the active verse in view, and **yields to the
  user**: any manual scroll suspends autoscroll until the user re-engages it (an explicit,
  ≥48dp "follow along" affordance) or playback advances to a new chapter. The app never
  scroll-fights the reader's thumb. `[P1+P2]`
- **FR-AUD-13 (U-AUD-4)** **Tap a verse to seek** to it while a session is active. `[P1+P2]`
- **FR-AUD-14 (U-AUD-4)** **The existing verse tap-out gesture is preserved.** Sprint H shipped
  "tap a verse → open it in your external Bible app"; FR-AUD-13 wants the same gesture. Product
  position: **while an audio session is active in the reader, a verse tap seeks; the external
  tap-out moves to long-press**, and the reader's footer hint text changes to say so. Outside an
  active session, the shipped Sprint-H behaviour is byte-for-byte unchanged. *(This changes a
  shipped gesture — flagged for owner confirmation, OQ-AUD-4.)* `[P1+P2]`

**Marking, and staying out of the way**

- **FR-AUD-15 (U-AUD-1, U-AUD-8)** **Starting playback of a reading marks it read**, one-way,
  via the existing `MarkReadOnOpenUseCase` seam (D-O-1) — same semantics as the shipped tap-to-open
  rule: never unmarks, idempotent, active-plan-scoped, and the on-card checkbox remains the only
  undo. Whole-day playback marks each stream **as its portion begins**, not all up front.
  Browse-context playback marks nothing. `[P1]`
- **FR-AUD-16 (G15, U-AUD-8)** Read aloud introduces **no second progress axis** — no listened
  state, no listening stats, no listening streak, no distinction anywhere in the UI, the widget,
  the stats panel, the strips, or the notifications between a reading that was read and one that
  was heard. `[P1]`
- **FR-AUD-17** The read-aloud affordance on a Schedule reading card **must not increase the
  card's height.** Four sprints (S16, S18, S20, and the N-stream fix) were spent winning
  one-screen fit, and the 4-stream M'Cheyne case has ~0 slack. The control is a trailing element
  in the existing card row. `[P1]`
- **FR-AUD-18** Whole-day playback is reached **without reintroducing a whole-day button row**
  (Sprint 00H deliberately removed one) — recommended home is a top-bar action on the Schedule.
  `[P1]`

**Delivery, storage & degradation**

- **FR-AUD-19 `[P2]` (U-AUD-6, G16)** High-quality audio is delivered as **Play Asset Delivery
  on-demand asset packs**. The app adds **no `INTERNET` permission**, ships **no audio in the
  AAB**, and the release bundle stays under the existing 12 MB CI gate. `[P2]`
- **FR-AUD-20 `[P2]` (U-AUD-6, G16)** **Nothing downloads without explicit consent.** Every
  download is user-initiated, states its coverage and size before starting, and is **Wi-Fi-only
  by default** with per-download cellular opt-in. No download on install, first launch, idle, or
  charging. `[P2]`
- **FR-AUD-21 `[P2]` (U-AUD-6, G16)** **Download management is a first-class Settings surface**
  (§8): what's downloaded, how much space it uses, download by unit, delete by unit, delete all,
  and honest handling of Play-evicted packs (re-download offered, never a silent failure).
- **FR-AUD-22 (U-AUD-7, G17)** **Graceful degradation, in this order:** downloaded high-quality
  audio → on-device TTS → an honest explanatory message. The reader itself is **never** blocked,
  degraded, or error-stated by audio being missing or failing. A sideloaded install gets the
  device voice and one clear statement of why the download option is unavailable. `[P1+P2]`

### P1 — strongly desired; first follow-up if cut

- **FR-AUD-23** **Playback speed** presets (0.75×–2.0×), persisted. *(In scope: it is a standard
  spoken-word expectation, and both a TTS engine and a media player support rate natively.)*
- **FR-AUD-24** **Sleep timer** (e.g. 10/20/30/60 min, end-of-chapter, end-of-portion) — the
  bedtime-listening case, and the natural stop for continuous Browse playback.
- **FR-AUD-25** **Resume where I stopped** — the app remembers the last playback position per
  passage within a session and offers to resume.
- **FR-AUD-26 `[P2]`** **Plan-window download** — "download the next 30 days of readings"
  (~70–90 MB), computed from the active plan, as an alternative to per-book or whole-Bible.
- **FR-AUD-27** Read-aloud honours the app's existing **theme** and **text-size** settings; the
  follow-along highlight is theme-aware and meets contrast requirements in light and dark.

### P2 — explicitly out (tracked, not committed)

Widget/tray play controls · Android Auto / Wear / Cast certification · voice picker · audio for a
second translation · verse-audio sharing/export · bookmarks-with-audio · offline "daily episode"
podcast feed · background pre-fetch of upcoming readings.

### Non-functional requirements

- **NFR-AUD-A — The offline/no-network identity holds.** No `INTERNET` in the merged release
  manifest, no analytics, no telemetry, no account, no server of ours; Play data-safety
  disclosure unchanged. Verified by manifest diff exactly as D-L-6 was. *Load-bearing identity,
  not a preference.*
- **NFR-AUD-B — The install stays small.** Zero audio bytes in the AAB; the CI bundle-size gate
  (12 MB) remains green and remains the guard.
- **NFR-AUD-C — Accessibility parity, and TalkBack coexistence.** All transport controls meet the
  existing gate (≥48dp, meaningful labels); playback state changes are announced; the feature is
  fully operable with TalkBack on. **Playback never auto-starts**, so the player can never speak
  over a screen reader unbidden. Read aloud is additive to TalkBack, never a substitute for it,
  and no accessibility affordance is removed or degraded.
- **NFR-AUD-D — Audio correctness is held to the text's bar.** The audio artifact is the
  project's **third core content asset** (plan → text → audio) and gets the same discipline: a
  reproducible build, a committed manifest of what was produced, and an offline CI-gating
  verification test (§10, M-AUD-2). Release is blocked on it.
- **NFR-AUD-E — Storage honesty.** The app never holds audio the user did not ask for, always
  reports its true on-disk usage, and always makes deletion reachable in ≤2 taps from Settings.
- **NFR-AUD-F — Voice-source independence.** The player, queue, follow-along, marking, and
  download UI are defined against a **source-agnostic seam** (a queue of verses + a source that
  can speak/stream a verse range and report verse boundaries). Swapping device TTS for a
  downloaded artifact — or ElevenLabs for LibriVox — must not rewrite the player. *This is a
  product-durability requirement (mirroring NFR-V3-E), because it is what makes Phase 1 real work
  rather than throwaway work.*
- **NFR-AUD-G — Battery and data decency.** Continuous playback is a long-running foreground
  session; it must not wake-lock the CPU beyond what playback needs, and no network is used at
  playback time at all (audio is local by then).

---

## 7. Product decisions (`D-AUD-n`)

**Locked by the owner — recorded, not re-litigated.**

- **D-AUD-1 — Delivery is Google Play Asset Delivery, on-demand asset packs.** Google hosts and
  serves the audio. No self-hosted CDN, no our-server anything, ever.
- **D-AUD-2 — No `INTERNET` permission.** Play brokers the download, exactly as the Play Core
  in-app-update dependency does (verified finding D-L-6: zero new permissions). The offline
  identity (NFR-V3-A) is preserved, not traded.
- **D-AUD-3 — Audio updates ride an app release.** A corrected or added recording ships by
  re-releasing the app (asset packs version with the app). The owner explicitly prefers
  re-releasing over running hosting infrastructure. *(Consequence and risk: R-AUD-1.)*
- **D-AUD-4 — Audio is pre-rendered once from the bundled KJV text; never synthesized at runtime
  per user.** Recorded with the arithmetic that settles it, so it is never re-proposed:
  - Whole KJV = **4,112,530 characters** (790,638 words, 1,189 chapters, 31,102 verses + 117
    superscriptions). One full-corpus render ≈ **$250–$800** (ElevenLabs, tier/model dependent) —
    **once, for every user, forever.**
  - One user-year of the Bible Companion plan (OT once + NT twice) = **5,054,048 characters**.
    **One user reading for one year consumes more characters than rendering the entire corpus
    once for everyone.**
  - Realtime cloud TTS ≈ **$35–70 per user per month** at ElevenLabs rates; ≈ **$6.50 per user
    per month** even on the cheapest cloud neural TTS. Roughly **100× worse**, unbounded, and it
    would require the network dependency D-AUD-2 exists to avoid. **Rejected.**
- **D-AUD-5 — No "Narrated by AI" or synthetic-voice disclosure** anywhere in the app or the Play
  listing. *Product consequence, and the honest way to honour it:* copy names the voice by
  **origin and quality, claiming nothing in either direction** — "High-quality voice (download)"
  vs "Device voice". We do not call it AI, and equally we do not claim a human narrator or
  "professionally narrated." (If OQ-AUD-1 resolves to LibriVox, human narration is a fact we may
  state, and attribution may be *required* — see AR-AUD-1.)
- **D-AUD-6 — Audio is never bundled in the AAB.** Non-negotiable: the CI gate fails above 12 MB
  (currently 8.12 MB) and D-V3-20 set a +6 MB asset budget. Whole-Bible audio is ~870 MB at Opus
  24 kbps mono (~1.15 GB at 32 kbps), ~13 MB per book on average. Bundling is arithmetically
  impossible, not merely unwise.

**Recommended by product — need owner sign-off (see §9, §11).**

- **D-AUD-7 — Two-phase delivery.** Phase 1 = on-device TTS (zero cost, zero bytes, no network,
  no licensing). Phase 2 = the pre-rendered high-quality voice as optional on-demand downloads.
  Rationale in §9. **Recommended; not assumed settled.**
- **D-AUD-8 — Listening is reading.** Pressing play on a reading marks it read by the *existing*
  one-way mark-on-open rule (D-O-1, Sprint 00O) — not a new rule, not a new state, not a
  completion-based mark. No listening streak, counter or badge anywhere. *(Rationale: a
  completion-based mark needs playback-completion tracking, is ambiguous for partial listens, and
  would be the app's first divergence between "opened" and "done"; and a separate listening axis
  is exactly the gamification the owner ruled out.)*
- **D-AUD-9 — Stop rules are unit-specific** (FR-AUD-4): a portion and a day **end**; browsing
  **continues**. Rationale: the day's reading has a defined end and running past it blurs "I've
  read today's portion"; browsing has no such boundary and stopping at a chapter break would be
  an annoyance.
- **D-AUD-10 — A per-verse timing index is a required property of any audio artifact**
  (FR-AUD-10). An artifact without verse timings cannot honour Psalm 119 windows, cannot
  highlight, and cannot seek — it is not shippable. *This is the requirement that prices the
  voice-source decision.*
- **D-AUD-11 — Nothing downloads without explicit consent; Wi-Fi by default** (FR-AUD-20).
- **D-AUD-12 — Missing audio degrades to the device voice, never to a dead button** (FR-AUD-22).
  This is also *why* Phase 1 is worth shipping standalone: it is Phase 2's permanent fallback.
- **D-AUD-13 — One voice.** No voice picker, no multi-voice.
- **D-AUD-14 — Playback speed and a sleep timer are in scope (P1);** equalizer, background music,
  and any scheduled/auto-play behaviour are not.
- **D-AUD-15 — Reading is never blocked by audio.** Audio absent, undownloaded, evicted, or
  failed leaves the reader exactly as it is today.
- **D-AUD-16 — Entry points are the Schedule card, the Schedule top bar, and the reader**
  (FR-AUD-5). **Not** the home-screen widget (read-only, D-S7-4) and **not** the persistent tray
  notification (content-only, Sprint 22). The player's own media notification is the transport and
  is a different surface.

---

## 8. Download management as a product surface

~870 MB of optional content on an 8 MB app is not a settings toggle; it is a surface, and it will
be judged as one (uninstalls and one-star reviews about storage are the failure mode).

**Settings → Audio** shows, at minimum:

- **Voice:** Device voice / High-quality voice (download). *(Phase 1 ships only the first.)*
- **Downloaded audio:** total on-disk size, and what's downloaded, listed by unit.
- **Download:** by unit, each with its size stated before the user commits.
- **Delete:** any unit, and "delete all downloaded audio" — reachable in ≤2 taps (NFR-AUD-E).
- **Download over Wi-Fi only** — on by default.

**Granularity (product constraint, mechanism is Diego's).** The rule is: **a user must never be
required to take ~870 MB in order to hear today's chapter.** The recommended unit set:

| Unit | Approx size | Why it exists |
|---|---|---|
| **Today's readings** | ~2.4 MB (3–4 chapters ≈ 12,000 chars) | The "just let me listen now" case |
| **Next 30 days of readings** (FR-AUD-26, P1) | ~70–90 MB | The commuter who wants a month covered |
| **A book** | ~13 MB average | Natural, matches the reader's mental model |
| **Old Testament / New Testament** | ~700 MB / ~170 MB | Bulk without total commitment |
| **Everything** | ~870 MB | The "I have space and Wi-Fi" user |

**Assumption A-AUD-1 (flagged, OQ-AUD-2):** that Play Asset Delivery's on-demand packs can be
organised at roughly per-book granularity within Play's pack-count and per-pack size limits, with
the smaller units above composed from them. If Play's limits force coarser packs, the *product
constraint* (never force the whole Bible) stands and the unit list is renegotiated — the pack
layout is an engineering decision, the "never force 870 MB" rule is not.

**A helpful arithmetic fact:** audio is stored **per chapter**, and the plan re-reads the NT twice
a year from the same chapters. So a full plan-year of listening (≈98 hours) needs the corpus
downloaded **once** (≈79 hours of audio, ~870 MB). Download size is bounded by the Bible, not by
the plan.

**Edges that must be designed, not discovered:**

- **Play evicts asset packs** under storage pressure. The app must detect a missing pack, say so
  plainly, offer re-download, and fall back to the device voice meanwhile — never crash, never
  silently play nothing.
- **Sideloaded / non-Play installs** get nothing from PAD. One clear statement, once; device
  voice still works (FR-AUD-22). Distribution is Play-only, so this is an edge case — but a
  silent dead button is not acceptable in it.
- **Download in progress** — visible progress, cancellable, resumable, and pressing play meanwhile
  offers the device voice rather than waiting.
- **Feb 29** — no scheduled readings means no reading cards, so no read-aloud control on the
  Schedule and nothing to download or play; the message is unchanged. The Bible tab still plays
  any chapter. *(No new empty state is introduced.)*
- **Existing reader error states** (asset load failure) are unchanged and take precedence: if the
  text can't load, audio is not offered for that passage.

---

## 9. Release scoping — the two-phase cut (needs sign-off)

### Phase 1 — the player, on the device voice

**Ship:** the whole player and the whole follow-along experience, speaking through
`android.speech.tts` — queue, background/foreground media session, lock-screen/headset/Bluetooth
controls, audio focus, auto-advance with the FR-AUD-4 stop rules, verse highlight, yielding
autoscroll, tap-to-seek, mark-on-play, speed, sleep timer, and every entry point in FR-AUD-5.
**Cost: $0. Bytes: 0. Network: none. Licensing: none.**

**Why this is the right first cut, in one sentence:** roughly **80% of the engineering is the
player**, and the player is identical whichever voice source wins — so Phase 1 buys the entire UX,
and the owner's judgement of it, **before** the render bill is spent and before the voice-source
question has to be answered.

Two further arguments that are easy to miss and are load-bearing:

1. **Device TTS gives verse-level events for free.** Enqueuing one utterance per verse and
   listening for utterance-start yields exact verse boundaries with **no timing index at all** —
   so highlight, seek and Psalm 119 windows are all provable in Phase 1. That makes Phase 1 the
   **executable specification** of what Phase 2's timing index (FR-AUD-10) must deliver.
2. **Phase 1 is permanent, not throwaway.** It is Phase 2's fallback for undownloaded passages,
   evicted packs and sideloaded installs (D-AUD-12), so none of it is discarded.

**Honest limits of Phase 1**, to be stated to the owner rather than discovered: device TTS voice
quality varies by manufacturer and installed engine; some devices ship a poor or no engine;
proper-noun pronunciation in the KJV will be visibly imperfect; and no downloadable-quality
promise can be made from it. Phase 1 is a *usable* feature, not the *good* one.

### Phase 2 — the high-quality voice

**Ship:** the pre-rendered corpus (one voice, D-AUD-13) with its per-verse timing index
(FR-AUD-10), delivered as PAD on-demand packs (FR-AUD-19), plus the download-management surface
(§8), voice selection between device and downloaded (D-AUD-5 wording), and the plan-window
download (FR-AUD-26).

**Gated on:** the voice-source decision (OQ-AUD-1), a pronunciation pilot (R-AUD-3), and the
render budget.

### Not committed

Widget/tray controls · Android Auto/Wear/Cast · a second voice or translation · verse-audio
sharing · background pre-fetch. See §4.

---

## 10. Success metrics

The app has **no analytics or telemetry** (settled, V1; unchanged here). So, as with M1, M8 and
M-V3-*, metrics are **release gates plus owner-observable signals** — not dashboards.

- **M-AUD-1 — Verse-window fidelity (release gate).** For the four Psalm 119 days, the constructed
  audio queue covers exactly verses 1–40 / 41–80 / 81–128 / 129–176 — no more, no less — and for
  every other day covers exactly the portion's chapters. Test-gated, offline, in
  `testDebugUnitTest`. *Hard gate, in the lineage of the plan gate (M1) and the text gate
  (M-V3-1).*
- **M-AUD-2 — Artifact coverage (release gate, Phase 2).** Every one of the **1,189 chapters** has
  audio, and the timing index covers every one of the **31,102 verses + 117 superscriptions**, with
  no gaps, no duplicates, and monotonic offsets within each chapter. Reproducible build + committed
  manifest, in the discipline of the plan and text gates (NFR-AUD-D).
- **M-AUD-3 — Posture unchanged (release gate).** The merged **release** manifest contains **no
  `INTERNET`** and no new permission after the audio work — verified by manifest diff, exactly as
  D-L-6 was verified for Play Core.
- **M-AUD-4 — Install size unchanged (release gate).** The AAB contains zero audio bytes and stays
  under the 12 MB CI ceiling.
- **M-AUD-5 — Zero unconsented bytes (gate + device pass).** No audio download is initiated by any
  path other than an explicit user action. Pinned by test where provable, confirmed on device.
- **M-AUD-6 — Voice sign-off (qualitative gate, owner).** The owner listens to a fixed sample set
  (§11, OQ-AUD-1) and signs off that the voice is fit for scripture — mirroring M8 and M-V3-2,
  because "reverent enough" is the owner's bar to judge, not ours.
- **M-AUD-7 — Tone & no-second-axis (gate).** The existing guilt-copy ban-scan extends over all
  audio strings, and a test pins that no surface distinguishes a heard reading from a read one.
- **M-AUD-8 — Accessibility parity (gate).** `AccessibilityGateTest` extends over the transport
  controls and the follow-along affordance; playback is fully operable with TalkBack on and never
  auto-starts.
- **M-AUD-9 — Hands-free correctness (device pass).** Start playback, lock the phone, put it in a
  pocket: the portion plays to its end, the lock-screen controls work, a phone call interrupts and
  resumes, and a Bluetooth headset button pauses.
- **M-AUD-10 — One-screen fit holds (device pass).** With the read-aloud control present, the
  Schedule still fits one screen at default font for the 4-stream M'Cheyne plan (FR-AUD-17).
- **M-AUD-11 — Adoption signal (owner-observable, no telemetry).** From the owner's testers: do
  people use it? On the Schedule or in the Bible tab? Do they download the good voice, and does the
  size bother them? Asked directly — we are not adding instrumentation.

---

## 11. Open questions, risks & accepted risks

### The open question that must be answered before Phase 2

**OQ-AUD-1 — Owner: which voice source?** *(Recommendation given; explicitly not silently
decided.)*

| | **ElevenLabs pre-render** | **LibriVox PD human KJV** |
|---|---|---|
| Cost | ~$250–800 one-time, **plus the same again on any re-render** | **$0** |
| Voice | Consistent, controllable, high quality; owner's stated preference | Human — many find human reading preferable for scripture |
| Consistency | One voice across all 66 books, guaranteed | Volunteer-variable: reader changes, mic quality, room tone, pacing |
| **Verse timings (FR-AUD-10)** | **Provided by the API** (character/word timestamps) — essentially free | **Absent** — requires forced alignment across ~79 hours, then verification |
| Rights | Commercial rights granted on paid plans | Public-domain dedication; redistributable, including commercially |
| Corrections | Re-render the affected chapters (cheap per-chapter, and repeatable) | Cannot fix a recording — only replace it with another reading |

**Recommendation: ElevenLabs**, primarily on **FR-AUD-10**. The headline comparison ($800 vs $0)
is misleading, because it prices only the audio and not the timing index — and without verse
timings there is no verse highlight, no tap-to-seek, and **no Psalm 119 windows**, which is a hard
requirement (M-AUD-1). LibriVox's true cost is $0 + a forced-alignment pipeline over ~79 hours of
volunteer-variable audio + a verification pass on 31,219 boundaries, which is a data project of
roughly Sprint-A size with a real failure mode (a misaligned highlight is worse than none,
FR-AUD-11). Consistency across 66 books and the ability to fix a mispronunciation without
re-recruiting a narrator are secondary but real.

**What evidence would settle it** — a short, cheap, decisive experiment before any spend:

1. **A blind listening test on a fixed sample set:** Genesis 1, Psalm 23, Psalm 119:1–40, Isaiah
   53, Matthew 5, John 11, and one proper-noun-heavy chapter (1 Chronicles 1 or Numbers 26).
   Same passages, both sources, owner + a handful of testers, preference recorded (M-AUD-6).
2. **Does a *complete, single-reader* LibriVox KJV exist?** A patchwork across readers likely
   fails the consistency bar on its own.
3. **A forced-alignment spike on one hour of LibriVox audio** — measured boundary accuracy. If
   alignment is not reliably verse-accurate, LibriVox is disqualified by FR-AUD-10 regardless of
   preference.

If (1) favours LibriVox **and** (2) and (3) both come back clean, the recommendation flips —
it saves the render bill and the re-render risk entirely. Otherwise: ElevenLabs.

### Other open questions

| # | Question | Owner | Product position |
|---|---|---|---|
| **OQ-AUD-2** | Can PAD on-demand packs be organised at ~per-book granularity within Play's pack-count/size limits (assumption A-AUD-1, §8)? If not, what's the finest workable unit? | Diego | The "never force ~870 MB" constraint is fixed; the pack layout is negotiable. |
| **OQ-AUD-3** | Confirm the **two-phase cut** (§9, D-AUD-7) — ship the player on device TTS first, then the rendered voice? | Owner | Strongly recommended. The alternative (wait and ship Phase 2 whole) spends the render budget before the UX is judged. |
| **OQ-AUD-4** | Verse tap during playback **seeks**, and the shipped Sprint-H external tap-out moves to **long-press** (FR-AUD-14). This changes a shipped gesture. | Owner | Recommended as stated; the alternative (long-press to seek) makes the core follow-along interaction the hidden one. |
| **OQ-AUD-5** | Should the reader **announce the reference** aloud at the start of a chapter ("Genesis, chapter 1") and/or **speak verse numbers**? | Owner (tone) | Recommend announcing the chapter reference once, and **not** speaking verse numbers — numbers every few seconds break the reading. Both are copy/tone calls. |
| **OQ-AUD-6** | Is **Opus ~24 kbps mono** acceptable for spoken word on phone speakers and cheap earbuds (assumption A-AUD-2), or should we pay ~1.15 GB for 32 kbps? | Owner (listening check) | Recommend 24 kbps after a listening check on a phone speaker; the 280 MB delta is real. |
| **OQ-AUD-7** | User-facing naming: **"Read aloud"** (recommended) vs "Listen" vs "Audio". And the voice labels under D-AUD-5. | Owner (tone) | "Read aloud" describes the act without implying a produced audiobook or a narrator. |
| **OQ-AUD-8** | Does **whole-day playback** (FR-AUD-5 row 2, FR-AUD-18) belong in the first release, or is per-reading playback enough to start? | Owner / Morgan | It is the natural commuter unit; but it is the one entry point that needs new chrome. Droppable. |
| **OQ-AUD-9** | AR-AUD-1 (below): confirm the accepted-risk posture extends to **recordings**, and confirm attribution obligations under whichever voice source wins. | Owner | Recommend recording the acceptance explicitly before any render is commissioned. |

### Accepted risks

- **AR-AUD-1 — UK Crown copyright extends to the audio (proposed ACCEPTED, extends AR-1).** AR-1
  (docs/data/README.md) records the owner's decision to accept the UK Crown-copyright position on
  the KJV **text** — public domain worldwide except the UK, where Cambridge/the King's Printer
  administer it — and to neither geo-restrict nor alter the text. **A recording of that text is a
  derivative work and inherits the same position**, plus a *second, separate* rights layer in the
  recording itself: ElevenLabs output is licensed to us by the paid plan; LibriVox recordings are
  public-domain dedicated but may carry attribution conventions. Product position: **the same
  accepted-risk posture applies** — do not geo-restrict, do not alter the text — and the recording
  layer is settled by the voice-source decision (OQ-AUD-1/OQ-AUD-9). **This must be recorded in
  docs/data/README.md alongside AR-1 before any render is commissioned**, not before ship — the
  spend is the commitment point.

### Risks

- **R-AUD-1 — Audio corrections are coupled to app releases (owner-accepted, D-AUD-3).** A
  mispronunciation reported by a user cannot be hot-fixed; it waits for a re-render *and* a release.
  *Mitigation:* batch corrections; treat audio fixes as normal release content; keep the render
  pipeline reproducible and per-chapter so a fix is cheap in dollars even if slow in calendar.
- **R-AUD-2 — Storage-pressure uninstalls and size complaints.** ~870 MB on an 8 MB app is the
  most likely source of a bad review. *Mitigation:* D-AUD-11 (never download unasked), fine
  granularity (§8), always-visible usage and one-tap deletion, and a device voice that means the
  feature is *usable* at 0 MB.
- **R-AUD-3 — A pronunciation defect costs a re-render.** The KJV is dense with proper nouns
  (Mahershalalhashbaz, Chushanrishathaim, Zaphnathpaaneah) and words whose modern pronunciation
  differs. Discovering a systematic error *after* the full render is exactly the "expensive
  re-import" failure V3 was designed to avoid. **This is the highest-value risk control in the
  whole feature:** *render a pilot first* — a proper-noun-heavy sample (1 Chr 1, Num 26, Ezra 2,
  plus Ps 23 / John 11 / Isa 53 for tone) — get owner sign-off (M-AUD-6), build a pronunciation
  lexicon from what it exposes, and only then commission the corpus.
- **R-AUD-4 — Timing-index quality (Phase 2).** A highlight that drifts is worse than no highlight
  (FR-AUD-11) and would also silently corrupt Psalm 119 windows. *Mitigation:* the M-AUD-2 gate,
  monotonicity and coverage assertions, and spot-verification against the Phase-1 device-TTS
  boundaries as an independent witness — the same second-source discipline as the plan and text
  gates.
- **R-AUD-5 — PAD constraints and eviction.** Pack-count/size limits (OQ-AUD-2), Play evicting
  packs under storage pressure, and non-Play installs getting nothing. *Mitigation:* §8's edge
  design; degradation is a P0 requirement (FR-AUD-22), not error handling bolted on later.
- **R-AUD-6 — Scope creep into a media app.** MediaSession, notifications, focus, speed, timers,
  offline downloads, queue management — this is genuinely the largest single feature since V3, and
  the player half is where "just one more control" lives. *Mitigation:* the §4 non-goals list is
  long on purpose; Phase 1's cut is the forcing function.
- **R-AUD-7 — Device TTS variance (Phase 1).** Quality, availability, and locale vary; some
  devices ship no usable engine. *Mitigation:* honest degradation (FR-AUD-22), and framing Phase 1
  to the owner as *usable*, not *good*.
- **R-AUD-8 — One-screen-fit regression.** Four sprints were spent on it and M'Cheyne (N=4) has
  no slack. *Mitigation:* FR-AUD-17 (height-neutral control), FR-AUD-18 (no new button row),
  M-AUD-10 (device-pass gate).
- **R-AUD-9 — Gesture conflict with the shipped verse tap-out.** Sprint H taught users that
  tapping a verse leaves for their Bible app; FR-AUD-13 wants that tap. *Mitigation:* the
  session-scoped rule in FR-AUD-14 plus reactive footer copy — and owner confirmation
  (OQ-AUD-4), because changing a shipped gesture is a product decision, not a UI detail.
- **R-AUD-10 — Play policy/review surface changes.** A large on-demand asset app, a foreground
  media session, and a new Play Console asset-pack configuration are all new review surface.
  *Mitigation:* no new permissions (M-AUD-3), no data collection, no change to the data-safety
  form; confirm asset-pack configuration on an internal track before the render is commissioned.

---

## 12. Dependencies

> **The critical path (must be sequenced, not parallelised).** For Phase 2 there is one ordered
> chain everything hangs off: **voice-source decision (OQ-AUD-1) → pronunciation pilot + owner
> sign-off (R-AUD-3, M-AUD-6) → reproducible full render + per-verse timing index (FR-AUD-10) →
> committed artifact manifest → the coverage gate (M-AUD-2) → PAD pack layout (OQ-AUD-2)**. No
> Phase-2 download UI can be *verified* until the artifact and its index exist. **Phase 1 has none
> of these dependencies** — which is most of why it should go first.

- **The V3 text spine** — `bible.db`, `VerseId`/`VerseRange`, `BibleTextSource`, `MarkupStripper`.
  The audio queue is built from the same verses the reader renders; there is no second text path.
- **`PortionVerseBridge` + `GetPortionTextUseCase`** — the *only* source of a portion's verse
  ranges, including the Psalm 119 windows (Sprint J, D-READER-1). FR-AUD-6 depends on it entirely
  and must never re-derive ranges (the same one-home discipline as R-STREAK-5 and D-S17-2).
- **`ReaderAudioSlot` + `ReaderUiState.Content.activeVerseId`** — the seams reserved for exactly
  this (D-V3-14). The transport goes in the slot; the highlight reads the field.
- **`ReaderContext` (Browse vs Reading, Sprint I)** — determines the play unit and stop rule
  (FR-AUD-4/5) and whether anything is marked.
- **`MarkReadOnOpenUseCase` (D-O-1, Sprint 00O)** — FR-AUD-15 reuses it unchanged; no new marking
  rule enters the codebase.
- **`PlanDescriptor` / `StreamDescriptor` (Alt Sprint A/C)** — whole-day playback iterates the
  active plan's N streams (3 / 4 / 1), never a hard-coded 3.
- **`BookCatalog`** — the one home of book structure (66 books, chapter counts). Asset-pack naming
  and per-book download units derive from it; **no second book table** (the discipline held through
  D-S9-1, D-S13-1, and the picker-grid sprint).
- **Play Asset Delivery + the Play Core precedent (D-L-6)** — the delivery mechanism and the
  verified no-permission finding it rests on.
- **The CI bundle-size gate (12 MB) and the merged-manifest permission check** — the two automated
  guards that keep NFR-AUD-A/B honest.
- **`SettingsRepository` / DataStore** — audio preferences (voice, Wi-Fi-only, speed, sleep timer).
  No Room/schema change is anticipated; downloaded-pack state is Play's, not ours.
- **`AccessibilityGateTest`** — extended over the transport controls and follow-along (M-AUD-8).
- **The guilt-copy ban-scan (D-S17-1 / D-S20-1)** — extended over all audio strings (M-AUD-7).

---

> **Review note (Morgan + Diego):** every locked decision in §7 traces to the owner's brief;
> everything I recommended rather than received is marked as such and carried into §11 with the
> evidence that would settle it. The two decisions I most want challenged are **D-AUD-8**
> (play marks read, on press, via the existing seam) and **FR-AUD-14** (the verse-tap gesture
> change) — both touch shipped behaviour. The requirement I most want engineering to price early
> is **FR-AUD-10** (the per-verse timing index), because it, not the audio, is what decides
> OQ-AUD-1.
