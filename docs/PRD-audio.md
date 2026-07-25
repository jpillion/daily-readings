# Daily Reading Planner — PRD: Read aloud (audio)

> **Owner:** Maya (Product) · **Status:** Scope defined; **amendment pass A1 applied after Diego's
> engineering spec §16 and Priya's design** — **awaiting owner sign-off on the consolidated
> questions in §11** · **Last updated:** 2026-07-25 (amendment A1)
> **Companion docs:** [docs/PRD.md](PRD.md) (V1/V2), [docs/PRD-v3.md](PRD-v3.md) (the in-app
> reader this feature sits inside), **[docs/ENGINEERING_SPEC-audio.md](ENGINEERING_SPEC-audio.md)
> (Diego — architecture, verified findings, `D-AUD-E-*`)**, **[docs/features/audio-read-aloud-design.md](features/audio-read-aloud-design.md)
> (Priya — UX, `D-AUD-UI-*`)**, [docs/BACKLOG.md](BACKLOG.md) (#2 "Bible audio"),
> [docs/data/README.md](data/README.md) (provenance + AR-1), [CLAUDE.md](../CLAUDE.md).
>
> This document owns **what** read-aloud is and **why**. It defers **how to build** to Diego and
> **how to sequence** to Morgan. Owner decisions already locked are recorded in §7 as `D-AUD-n`
> and are **not** re-litigated here. Everything I had to assume is stated as an assumption and
> carried into the open-questions table (§11) — nothing is silently decided.

## Amendment log

> Kept visible rather than folded in silently, in the repo's usual style. **No requirement was
> renumbered**; superseded text is marked in place with the decision that superseded it.

**A1 — 2026-07-25, after [ENGINEERING_SPEC-audio.md](ENGINEERING_SPEC-audio.md) §16 (eight
pushbacks) and [features/audio-read-aloud-design.md](features/audio-read-aloud-design.md).** Eight
engineering pushbacks resolved as product decisions, two of Priya's design calls adopted, three new
questions raised for the owner:

| # | What changed | Where |
|---|---|---|
| A1.1 | **The download-unit table is rewritten pack-shaped.** An asset pack is atomic, so "today's readings, ~2.4 MB" cannot exist — a day's readings span 3–4 *books* (~40–120 MB). New **D-AUD-17**: the **book** is the atomic download unit. | §6 FR-AUD-20/21/26, §8 |
| A1.2 | **D-AUD-3 priced honestly and constrained.** New **D-AUD-18**: audio content never changes in a PATCH release (adopts D-AUD-E-19), plus what the user is told when a release does move audio bytes. | §7, §11 R-AUD-1 |
| A1.3 | **M-AUD-3 and NFR-AUD-A restated.** "No new permission" was unachievable; the gate now measures the thing we actually care about — **no `INTERNET`** — plus a named, bounded permission delta (6→8). | §6 NFR-AUD-A, §10 M-AUD-3 |
| A1.4 | **R-AUD-4's witness replaced.** Phase-1 TTS boundaries describe *different audio* and cannot validate Phase-2 file timings; ASR-derived boundaries over the same audio are the witness (D-AUD-E-8). | §11 R-AUD-4 |
| A1.5 | **FR-AUD-10 count corrected** 31,102 → **31,219** index rows (verses **plus** the 117 superscriptions, which need timings too). | §6 FR-AUD-10, §10 M-AUD-2 |
| A1.6 | **Transport buttons move chapters, not verses** (adopts D-AUD-E-4) — a verse-stepping button on a car stereo is a defect, not a feature. | §6 FR-AUD-2 |
| A1.7 | **Speed is not symmetric across phases** (adopts §16.7) — one persisted normalised factor; Phase 1's high end is honestly worse. | §6 FR-AUD-23 |
| A1.8 | **Priya's two placement calls adopted, both flagged for the owner:** the transport moves to the app root and `ReaderAudioSlot` retires as a bottom bar (**D-AUD-19**, supersedes the *placement* half of D-V3-14); the Schedule stats cap drops 45 % → 30 % while a session is live (**D-AUD-20**). | §7, §11 OQ-AUD-10/11 |
| A1.9 | **`AUD-C-1` confirmed as wanted** — a placeholder internal-track upload before the render is commissioned (**D-AUD-21**), with its calendar cost stated. | §7, §9, §11 OQ-AUD-12 |
| A1.10 | **Render ownership and spend flagged as unowned** — new **OQ-AUD-13**. | §11 |
| A1.11 | Corpus size **~870 MB → ~853 MB** (Diego computed it from our own `bible.db` rather than estimating); per-book figures adopted. | throughout |
| A1.12 | **FR-AUD-14 hardened:** the verse-tap change may ship **only** with named custom accessibility actions — long-press must carry no accessibility weight (adopts D-AUD-E-14 / D-AUD-UI-4). | §6 FR-AUD-14 |
| A1.13 | **Adjudicated a direct spec conflict:** Diego's `D-AUD-E-12` ("no global mini-player") vs Priya's `D-AUD-UI-1` (docked Listen bar at the root). Product sides with Priya; `D-AUD-E-12` is superseded on that point. | §11 |

**Narrowed or rejected, with reasons, in §11 ("Pushback I did not accept").**

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
Store app; the app itself never holds a network grant. **Google Play Asset Delivery brokers
downloads the same way.** Google hosts and serves the audio; the app requests an on-demand asset
pack through Play; the bytes arrive on disk; `INTERNET` stays out of the manifest and the Play
data-safety disclosure ("no data collected") is unchanged.

> **Amended A1.3 — the permission claim, corrected.** The sentence above originally read "works the
> same way," which invited the reading that asset delivery is *also* permission-free. It is not.
> Diego's verified manifest delta (ESpec §12) is **6 → 8 permissions**: `asset-delivery` merges
> `FOREGROUND_SERVICE_DATA_SYNC`, and our own media player needs
> `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (required from targetSdk 34; we target 37). Exported
> components go 1 → 4 (our `PlaybackService` plus two of Play's own, permission-guarded or
> disabled-by-default). **What is preserved is the thing that actually carries the identity: no
> `INTERNET`, no data collection, no data-safety-form change.** The brokering claim is true; the
> zero-delta claim was not, and NFR-AUD-A / M-AUD-3 are restated accordingly.

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
windows — to exact verse ranges. The only thing missing was a way to deliver ~853 MB without
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
  (passage reference), and exposes play/pause/stop plus previous/next transport. `[P1]`
  > **Amended A1.6 (adopts D-AUD-E-4; supersedes "verse-or-chapter" above).** **Transport
  > previous/next moves by *chapter*.** Verse-level movement is in-app only (tap-to-seek,
  > FR-AUD-13). Diego is right and this is a product call, not an internal detail: a next-track
  > button on a car stereo or a headset that advances one *verse* is a defect — the user presses it
  > expecting to move a meaningful distance and moves eight seconds. Chapter is the unit a
  > steering-wheel button should mean.
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
  timing index** — **31,219 rows** (31,102 verses **plus** the 117 verse-0 superscriptions), each
  the offset at which that row begins in its chapter's audio. This is what makes FR-AUD-6 (verse
  windows), FR-AUD-11 (highlight) and FR-AUD-12 (tap-to-seek) possible at all. **An artifact
  without verse timings is not shippable** (D-AUD-10). *This is the single requirement that most
  shapes engineering and most shapes the voice-source decision (§11, OQ-AUD-1).*
  > **Amended A1.5 (adopts ESpec §16.5).** The original text said "31,102 verses (+117
  > superscriptions)" — arithmetically correct but operationally sloppy: the *index* has 31,219
  > rows, and stating the smaller number is exactly how the superscriptions get forgotten by
  > whoever implements the gate. FR-AUD-7 already requires the superscriptions to be *spoken*;
  > they must therefore also be *timed*. The gate (M-AUD-2) is stated against **31,219**.

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
  > **Amended A1.12 — hardened (adopts D-AUD-E-14 / D-AUD-UI-4).** The rule ships **only** with
  > **named custom accessibility actions on each verse** while a session is live ("Play from here"
  > and "Open on <app>"), with the primary click = seek. **Long-press must carry no accessibility
  > weight.** Both Diego and Priya arrived at this independently and they are right: a long-press
  > that is the *only* route to the external tap-out would silently delete an affordance from
  > TalkBack and Switch Access users — a regression against a shipped feature, in the app's most
  > carefully gated area. With the named actions, screen-reader users end up with **more** than
  > they have today (two named actions vs one unnamed click) and nothing depends on holding a
  > finger down. **This is a condition of FR-AUD-14, not a nice-to-have:** if the custom actions
  > are not shipped, the gesture change is not shipped.

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
  download is user-initiated, **states its true size before starting**, and is **Wi-Fi-only by
  default** with per-download cellular opt-in. No download on install, first launch, idle, or
  charging. `[P2]`
  > **Amended A1.1 — two clarifications forced by how packs actually work.** (a) The size shown
  > must be **Play's own reported figure** for the request, not a number from a table we maintain,
  > so the size shown can never drift from the size charged. (b) **Wi-Fi-only is ours to enforce**:
  > Diego verified that every one of our packs is under Play's ~200 MB cellular-consent threshold
  > (largest, Psalms, ≈47 MB), so Play will never gate a single-book download for us. The product
  > requirement is unchanged; the mechanism is app-side, and "we rely on Play for this" would have
  > been a silent hole.
- **FR-AUD-21 `[P2]` (U-AUD-6, G16)** **Download management is a first-class surface** (§8): what's
  downloaded, how much space it uses, download by unit, delete by unit, delete all, and honest
  handling of Play-evicted packs (re-download offered, never a silent failure). *(Priya's design
  places it as a route pushed one tap from Settings, which keeps "delete all" the second tap and
  satisfies NFR-AUD-E literally.)*
- **FR-AUD-22 (U-AUD-7, G17)** **Graceful degradation, in this order:** downloaded high-quality
  audio → on-device TTS → an honest explanatory message. The reader itself is **never** blocked,
  degraded, or error-stated by audio being missing or failing. A sideloaded install gets the
  device voice and one clear statement of why the download option is unavailable. `[P1+P2]`

### P1 — strongly desired; first follow-up if cut

- **FR-AUD-23** **Playback speed** presets (0.75×–2.0×), persisted as **one normalised factor
  shared by both phases**, clamped to what the active source can render intelligibly. *(In scope:
  a standard spoken-word expectation, natively supported by both a TTS engine and a media
  player.)*
  > **Amended A1.7 (adopts ESpec §16.7).** The two phases are **not** symmetric: TTS
  > `setSpeechRate` and player `setPlaybackSpeed` are different mechanisms with different quality
  > curves, and several stock TTS engines are unintelligible above ~1.5×. Product position: one
  > persisted setting (a user should not have to re-learn their speed when they download a voice),
  > clamped per source, and **we say so honestly** rather than offering a 2× that garbles. This is
  > part of Phase 1 being framed as *usable*, not *good* (§9).
- **FR-AUD-24** **Sleep timer** (e.g. 10/20/30/60 min, end-of-chapter, end-of-portion) — the
  bedtime-listening case, and the natural stop for continuous Browse playback.
- **FR-AUD-25** **Resume where I stopped** — the app remembers the last playback position per
  passage within a session and offers to resume.
- **FR-AUD-26 `[P2]`** **Plan-window download** — "download **the books your next 30 days need**"
  (≈150–250 MB, typically 6–10 books), computed from the active plan, as an alternative to picking
  books by hand or taking everything.
  > **Amended A1.1.** Originally "the next 30 days of readings (~70–90 MB)". That size was
  > computed from *characters read* and is not purchasable: packs are whole books, so a 30-day
  > window costs the books it touches, not the verses it reads. The feature survives — it is still
  > the single most useful bulk unit for a commuter — but it is honestly a **book-set shortcut**,
  > and its label and size must say so.
- **FR-AUD-27** Read-aloud honours the app's existing **theme** and **text-size** settings; the
  follow-along highlight is theme-aware and meets contrast requirements in light and dark.

### P2 — explicitly out (tracked, not committed)

Widget/tray play controls · Android Auto / Wear / Cast certification · voice picker · audio for a
second translation · verse-audio sharing/export · bookmarks-with-audio · offline "daily episode"
podcast feed · background pre-fetch of upcoming readings.

### Non-functional requirements

- **NFR-AUD-A — The offline/no-network identity holds.** **No `INTERNET`** in the merged release
  manifest, no analytics, no telemetry, no account, no server of ours; Play data-safety disclosure
  unchanged. Verified by manifest diff, in the D-L-6 discipline. *Load-bearing identity, not a
  preference.*
  > **Amended A1.3 — what "holds" means, stated precisely.** This NFR originally implied a
  > zero-permission delta. It is not zero: **6 → 8 permissions**, both additions foreground-service
  > types (`FOREGROUND_SERVICE_DATA_SYNC` from `asset-delivery`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`
  > for our player), and exported components **1 → 4**. Product's position on that delta: **both new
  > permissions are required by functionality the user can see and asked for** (downloading, and
  > playback that survives screen-off), neither grants network or data access, and no
  > data-safety-form change results. The line that must never move is `INTERNET`. **A wider
  > manifest is an acceptable, named cost; a network grant is not.**
- **NFR-AUD-B — The install stays small.** Zero audio bytes in what the user installs; the CI
  bundle-size gate (12 MB) remains green and remains the guard.
  > **Clarified A1.3.** With asset packs in the bundle, "the AAB" is no longer one number — Diego
  > re-points the 12 MB gate at the **base module** (what every user downloads on install) and adds
  > a structural assertion that **no audio byte appears in `base/`**, plus a separate total ceiling.
  > The product requirement is unchanged and is now measured against the right thing: *installing
  > the app must still cost ~8 MB, whatever optional content exists beside it.*
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
  in-app-update dependency does (verified finding D-L-6). The offline identity (NFR-V3-A) is
  preserved, not traded. *(A1.3: D-L-6's **zero-permission** result was specific to Play Core
  in-app updates. `asset-delivery` does add one foreground-service permission — see NFR-AUD-A. The
  part that carries over, and the part this decision is about, is that **Play brokers the transfer
  and the app never holds a network grant**.)*
- **D-AUD-3 — Audio updates ride an app release.** A corrected or added recording ships by
  re-releasing the app (asset packs version with the app). The owner explicitly prefers
  re-releasing over running hosting infrastructure. *(Consequence and risk: R-AUD-1.)*
  > **Amended A1.2 — priced honestly. D-AUD-3 is slow *and not free*.** The PRD treated
  > release-coupled updates as costing only calendar time. Diego found the real cost (ESpec §7.5,
  > quoting Play): on an app update **all previously-downloaded asset packs are invalidated** and
  > the asset patch is downloaded **as part of the automatic app update, with no consent prompt** —
  > because it rides the update, not a `requestFetch`. A re-rendered Ezekiel is ~42 MB pushed
  > silently to every user who had Ezekiel. **That is in direct tension with D-AUD-11 ("nothing
  > arrives unasked"), which is the promise this feature's storage story rests on.** The mechanism
  > is outside our control; what we control is how often we trigger it — hence **D-AUD-18**.
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
  (currently 8.12 MB) and D-V3-20 set a +6 MB asset budget. Whole-Bible audio is **~853 MB** at Opus
  24 kbps mono (~1.14 GB at 32 kbps); ~8 MB median per book, ~47 MB largest (Psalms). Bundling is
  arithmetically
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

**Added in amendment A1 — new product decisions.**

- **D-AUD-17 — The book is the atomic download unit** *(A1.1; adopts D-AUD-E-5)*. An asset pack is
  the smallest thing Play can deliver, and the pack is a book (66 of them, generated from
  `BookCatalog` so there is no second book table). Everything the UI calls a "unit" — a day, a
  30-day window, a testament, everything — is a **selection of book packs**, sized by Play's own
  reported figure for that selection. Sub-book units (per-chapter packs, 1,189 modules) are
  **rejected**: I asked whether a finer unit was a hard product requirement and it is not — the
  product rule was always *"never forced to take ~853 MB to hear today's chapter,"* and a ~14 MB
  book satisfies it. §8 is rewritten accordingly. *(Consequence: the smallest possible download is
  one book, ~0.3 MB for 2 John, ~47 MB for Psalms, ~8 MB median.)*
- **D-AUD-18 — Audio content never changes in a PATCH release** *(A1.2; adopts D-AUD-E-19)*.
  Corrections and additions batch into a **MINOR** release. **What the user is told:** the release
  carries a plain whatsnew line — *"Updated read-aloud audio. If you've downloaded books, this
  update refreshes them."* — and Settings → Read aloud states the standing rule once
  (*"Downloaded audio is refreshed when the app updates"*). We do not pretend it is consented, and
  we do not hide it; we bound it (per-book packs mean the silent delta is one book, not one
  testament) and we name it. **This is a product commitment, not an engineering habit** — it
  constrains release planning, so it belongs here rather than in a build script. *(If `AUD-C-1`
  measures a full re-download rather than a patch, D-AUD-3 itself must be re-discussed with the
  owner **before** the render is commissioned — see D-AUD-21.)*
- **D-AUD-19 — The transport lives at the app root; `ReaderAudioSlot` retires as a bottom bar**
  *(A1.8; adopts D-AUD-UI-1/2)*. **This supersedes the *placement* half of D-V3-14, not its
  intent.** V3 reserved a slot so that follow-along would be an additive drop-in — that intent is
  exactly what is being cashed in; the transport simply belongs one level up, because **playback is
  an app-level session, not a reader-screen state**. The product argument is the one that decides
  it: a listener who taps Schedule to check tomorrow's readings must not lose the pause button.
  A transport nested in the Bible graph disappears on a tab switch, which is the "how do I stop
  this?" failure. The bar renders **nothing** when idle, so every resting-state layout budget
  (S16/S18/S20) is byte-for-byte unchanged. `activeVerseId` — the other reserved seam — is used
  unchanged. **Needs owner sign-off (OQ-AUD-10)** because it retires a seam V3 deliberately placed.
- **D-AUD-20 — While a session is live, the Schedule stats cap drops 45 % → 30 %** *(A1.8; adopts
  D-AUD-UI-3)*. The Listen bar is paid for by "Year at a glance", not by the readings. The panel
  already scrolls internally, so **nothing is removed** — only how much is visible at once, and
  only while listening. Product rationale: while a reading is playing, the year's statistics are
  the lowest-value thing on the screen and the readings are the highest; ranking them that way
  under pressure is the same judgement S15 made when it capped the panel at all. Net effect is a
  *gain* — the 4-stream M'Cheyne case fits **better** while playing than while idle. **Needs owner
  sign-off (OQ-AUD-11)** because it makes a shipped layout conditional on playback.
- **D-AUD-21 — The delivery plumbing is proven on placeholders before the render is commissioned**
  *(A1.9; adopts `AUD-C-1`)*. **Confirmed as wanted.** A bundle with all 66 packs carrying
  placeholder payloads goes to the Play **internal** track and must demonstrate: Play accepts 66
  packs, download and location resolution work, eviction behaves, the sideload path degrades, and —
  critically — **the real update-patch size when one pack changes** (the D-AUD-18 measurement).
  This is the same discipline as R-AUD-3's pronunciation pilot, applied to delivery: *prove the
  pipeline before spending four figures on a render.* **It has a real calendar cost** (a Play review
  cycle, which we do not control) and must be sequenced as a ticket, not assumed — Morgan owns that
  (OQ-AUD-12). Product's position: the cost is worth it; a render commissioned against a delivery
  mechanism Play turns out to reject is the single most expensive failure available to this feature.

---

## 8. Download management as a product surface

> **Rewritten in amendment A1.1.** The original §8 offered a unit table headed by "Today's
> readings — ~2.4 MB". **That download cannot exist.** An asset pack is atomic and a pack is a
> book, so a day whose three or four readings span three or four different books costs
> **~40–120 MB**, not 2.4 MB. The old table priced *characters read*; users buy *books*. The
> product rule it existed to serve survives untouched — *never forced to take the whole Bible to
> hear today's chapter* — and is in fact better served by quoting Play's real number than by a
> figure we computed and could never charge. See **D-AUD-17**.

~853 MB of optional content on an 8 MB app is not a settings toggle; it is a surface, and it will
be judged as one (uninstalls and one-star reviews about storage are the failure mode).

**Settings → Audio** shows, at minimum:

- **Voice:** Device voice / High-quality voice (download). *(Phase 1 ships only the first.)*
- **Downloaded audio:** total on-disk size, and what's downloaded, listed by unit.
- **Download:** by unit, each with its size stated before the user commits.
- **Delete:** any unit, and "delete all downloaded audio" — reachable in ≤2 taps (NFR-AUD-E).
- **Download over Wi-Fi only** — on by default.

**Granularity (product constraint; the pack layout is Diego's).** The rule is unchanged: **a user
must never be required to take ~853 MB in order to hear today's chapter.** The **book** is the
atomic unit (D-AUD-17); everything below is a *selection of books*, and every size shown is Play's
own figure for that selection, resolved before the user commits:

| What the user picks | What it actually is | ≈ Size | Why it exists |
|---|---|---|---|
| **A book** | one pack | ~8 MB median · ~0.3 MB (2 John) · ~47 MB (Psalms) | The atom. Matches the reader's mental model and `BookCatalog`. |
| **The books today's readings need** | the 3–4 packs today touches | **~40–120 MB** | The "just let me listen now" case — honestly priced |
| **The books your next 30 days need** (FR-AUD-26) | typically 6–10 packs | **~150–250 MB** | The commuter covering a month |
| **Old Testament** / **New Testament** | 39 / 27 packs | ~658 MB / ~195 MB | Bulk without total commitment |
| **Everything** | 66 packs | ~853 MB | The "I have space and Wi-Fi" user |

Two consequences the UI must carry, both of which are honesty improvements rather than
regressions:

1. **The small units are labelled as book-sets, not as readings.** "The books today's readings
   need — Genesis, Psalms, Matthew · about 62 MB" tells the truth and teaches the model in one
   line. Labelling it "Today's readings · 2.4 MB" would have been a lie the download progress bar
   immediately exposed.
2. **A day's listening may cost three books.** That is the genuine cost of the mechanism the owner
   chose, and it is *why* per-book granularity matters so much: at testament granularity the same
   user pays ~658 MB.

> **Assumption A-AUD-1 (updated; OQ-AUD-2 partially resolved).** The original assumption — that
> per-book granularity is achievable — **holds**, with one open edge: Diego could find **no
> first-party statement of a maximum asset-pack count** (the widely-cited "100" is about feature
> modules, a different thing), so 66 packs is an *unproven* bet. It is a cheap bet: re-grouping to
> ~8 section packs is a change to one mapping function with no re-render and no artifact change,
> and **`AUD-C-1` (D-AUD-21) proves or disproves it before any money is spent.** The product
> constraint stands either way; if 66 packs are rejected by Play, the fallback grouping must still
> satisfy "never forced to take everything," and section packs (~40–200 MB) do.

**A helpful arithmetic fact, unchanged:** audio is stored per chapter inside per-book packs, and
the plan re-reads the NT twice a year from the same chapters. A full plan-year of listening
(≈98 hours) needs the corpus downloaded **once** (≈79 hours of audio, ~853 MB). Download size is
bounded by the Bible, not by the plan.

**Edges that must be designed, not discovered:**

- **Play evicts asset packs** under storage pressure. The app must detect a missing pack, say so
  plainly, offer re-download, and fall back to the device voice meanwhile — never crash, never
  silently play nothing.
- **Sideloaded / non-Play installs** get nothing from PAD. One clear statement, once; device
  voice still works (FR-AUD-22). Distribution is Play-only, so this is an edge case — but a
  silent dead button is not acceptable in it.
- **Download in progress** — visible progress, cancellable, resumable, and pressing play meanwhile
  offers the device voice rather than waiting.
- **An app update refreshes downloaded audio** *(added A1.2)*. When a release changes audio, Play
  re-patches the affected packs inside the automatic app update, with no prompt. Bounded by
  **D-AUD-18** (never in a PATCH), disclosed by a whatsnew line and one standing sentence in
  Settings. The user is never *surprised twice*: they are told the rule once, and told each time a
  release exercises it.
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

**Gated on**, in order: the voice-source decision (OQ-AUD-1) → **`AUD-C-1`, the placeholder
internal-track upload that proves the delivery plumbing and measures the D-AUD-18 patch size
(D-AUD-21)** → a pronunciation pilot with owner sign-off (R-AUD-3, M-AUD-6) → the render budget and
its named owner (OQ-AUD-13). *Two of these four gates exist specifically so that the four-figure
spend happens last, after both the mechanism and the voice have been proven.*

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
  audio, and the timing index covers **all 31,219 rows** (31,102 verses + 117 superscriptions),
  with no gaps, no duplicates, and monotonic offsets within each chapter. Reproducible build +
  committed manifest, in the discipline of the plan and text gates (NFR-AUD-D). *(Count corrected
  in A1.5.)*
- **M-AUD-3 — Posture gate (release gate). ~~Restated in A1.3.~~**
  > **Superseded text:** *"The merged release manifest contains no `INTERNET` and no new permission
  > after the audio work — verified by manifest diff, exactly as D-L-6 was verified for Play
  > Core."* **This was unachievable and is withdrawn** (ESpec §12/§16.3). Aiming a gate at a number
  > that fails on day one is worse than having no gate: it invites someone to quietly redefine
  > "new" to make it pass.

  **The gate, restated to measure what the product actually cares about:**
  1. **`INTERNET` is absent** from the merged release manifest. *(The identity line. Non-negotiable,
     and the reason the whole feature is possible.)*
  2. **Exactly two new permissions**, both foreground-service types —
     `FOREGROUND_SERVICE_DATA_SYNC` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — both required by
     shipped, user-visible functionality. A third new permission fails the gate and needs a product
     decision, not a merge.
  3. **No data-safety-form change** and no data collection.
  4. The exported-component delta (1 → 4) is **recorded and attributed** at release time, not
     asserted to be zero.

  Verified by merged-manifest diff, in the D-L-6 discipline — the *discipline* transfers even
  though the *result* does not.
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
| **OQ-AUD-2** ✅ **mostly resolved (A1.1)** | Pack granularity. **Answered:** per-book is the design (D-AUD-17 / D-AUD-E-5); no sub-book unit is a product requirement. **Residual:** Play's max pack *count* is unverified, so 66 packs is proven by `AUD-C-1`, with ~8 section packs as a config-only fallback. | Diego / `AUD-C-1` | The "never force ~853 MB" constraint is fixed; the pack layout is negotiable. |
| **OQ-AUD-3** | Confirm the **two-phase cut** (§9, D-AUD-7) — ship the player on device TTS first, then the rendered voice? | Owner | Strongly recommended. The alternative (wait and ship Phase 2 whole) spends the render budget before the UX is judged. |
| **OQ-AUD-4** *(still owner's call; a11y cost now designed to zero)* | Verse tap during playback **seeks**, and the shipped Sprint-H external tap-out moves to **long-press** (FR-AUD-14). This changes a shipped gesture. | Owner | Recommended as stated. Both Diego and Priya independently made it conditional on **named custom accessibility actions** ("Play from here" / "Open on <app>"), which is now a hard condition of FR-AUD-14 (A1.12). With those, TalkBack users gain an affordance rather than lose one; the residual cost is a ~500 ms hold for sighted users, taught by the existing footer hint at 0 dp. |
| **OQ-AUD-5** | Should the reader **announce the reference** aloud at the start of a chapter ("Genesis, chapter 1") and/or **speak verse numbers**? | Owner (tone) | Recommend announcing the chapter reference once, and **not** speaking verse numbers — numbers every few seconds break the reading. Both are copy/tone calls. |
| **OQ-AUD-6** | Is **Opus ~24 kbps mono** acceptable for spoken word on phone speakers and cheap earbuds (assumption A-AUD-2), or should we pay ~1.14 GB for 32 kbps? | Owner (listening check) | Recommend 24 kbps after a listening check on a phone speaker; the ~290 MB delta is real. Diego: not an architectural constraint either way — every pack stays under Play's thresholds at both rates. |
| **OQ-AUD-7** | User-facing naming: **"Read aloud"** (recommended) vs "Listen" vs "Audio". And the voice labels under D-AUD-5. | Owner (tone) | "Read aloud" describes the act without implying a produced audiobook or a narrator. |
| **OQ-AUD-8** | Does **whole-day playback** (FR-AUD-5 row 2, FR-AUD-18) belong in the first release, or is per-reading playback enough to start? | Owner / Morgan | It is the natural commuter unit; but it is the one entry point that needs new chrome. Droppable. |
| **OQ-AUD-9** | AR-AUD-1 (below): confirm the accepted-risk posture extends to **recordings**, and confirm attribution obligations under whichever voice source wins. | Owner | Recommend recording the acceptance explicitly before any render is commissioned. |
| **OQ-AUD-10** *(new, A1.8)* | **Retire `ReaderAudioSlot` and put the transport at the app root** (D-AUD-19) — superseding the *placement* half of V3's D-V3-14, which reserved the slot inside the reader. | Owner | **Recommend adopting.** Playback is an app-level session: a listener who taps Schedule must not lose the pause button. D-V3-14's *intent* (an additive drop-in) is honoured exactly; only its location moves. Idle costs 0 dp, so no resting layout changes. |
| **OQ-AUD-11** *(new, A1.8)* | **While a session is live, the Schedule's stats panel cap drops 45 % → 30 %** (D-AUD-20) — the Listen bar is paid for by "Year at a glance", not by the readings. | Owner | **Recommend adopting.** Nothing is removed (the panel already scrolls); the readings column ends up with *more* slack while playing than while idle, so the N=4 M'Cheyne case improves. Flagged because it makes a shipped layout conditional on playback. |
| **OQ-AUD-12** *(new, A1.9)* | **Is `AUD-C-1` budgeted as a real ticket with a real Play review cycle?** A placeholder 66-pack internal-track upload *before* the render is commissioned (D-AUD-21). | Morgan (+ owner for the calendar) | **Product confirms it is wanted.** It is the cheapest de-risking in the plan and it is the only way to measure the D-AUD-18 patch size before we are committed. It costs calendar time we do not control, so it must be **sequenced, not assumed**. |
| **OQ-AUD-13** *(new, A1.10)* | **Who owns the render machine and the ~$250–800 spend?** Diego's pipeline assumes a named human runs the render and uploads corpus assets once per render; nobody is named. | Owner | Product position: **the owner owns the spend** (it is a business decision, not an engineering one), and a **single named human owns the render run** — including the pronunciation lexicon and the pilot sign-off. An unowned four-figure step with a vendor account attached is how this stalls between Sprints C and D. Needs naming **before** Sprint AUD-D is scheduled. |

### Pushback I did not accept, and one conflict I had to adjudicate *(added A1)*

All eight of Diego's §16 items are substantively accepted — five outright, three with a narrowing
recorded here.

1. **Rejected: per-chapter packs (§16.1's alternative, 1,189 modules).** Diego offered it "if the
   small unit is a hard product requirement" and recommended against it. **It is not a hard
   requirement, and I am closing that door explicitly** so nobody reopens it in a sprint. The
   product rule was never "the smallest possible download"; it was *"never forced to take the whole
   Bible to hear today's chapter."* A ~8 MB median book satisfies it with room to spare.
2. **Partially declined: deleting the "today's readings" row.** Diego proposed removing it from the
   unit table. I **kept the affordance and rewrote its label and price** ("the books today's
   readings need — Genesis, Psalms, Matthew · about 62 MB"). Reason: "just let me listen now" is
   the single most common download moment, and making the user work out which three books today
   touches — then find and tap three rows — is worse UX than composing the selection for them. What
   was wrong was the *number and the name*, not the shortcut. This is a real divergence and it puts
   one composed row back into Priya's downloads screen.
3. **Adjudicated: Diego's D-AUD-E-12 ("no global mini-player") vs Priya's D-AUD-UI-1 (a docked
   Listen bar at the root).** These two specs directly conflict, and implementation would otherwise
   resolve it by whoever wrote the code first. **Product sides with Priya (D-AUD-19), and D-AUD-E-12
   is superseded on this point.** Diego's objection was that the root `bottomBar` is already spoken
   for by the `NavigationBar` and that the media notification is sufficient out-of-reader control.
   The first half is **answered rather than overridden** by Priya's design — the bar composes
   *nothing* when idle, so it costs the navigation bar nothing in the resting state. The second half
   I disagree with on product grounds: when the app is in the *foreground*, telling a user to pull
   down the notification shade to pause what they are looking at is a worse interaction than a
   control that is already on screen — and it is worst for exactly the personas this feature exists
   for (the aging/low-vision reader, the driver-adjacent listener). The notification remains the
   right answer when the app is *not* in the foreground; it is not the right answer when it is.
   *(Owner sign-off: OQ-AUD-10.)*

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

- **R-AUD-1 — Audio corrections are coupled to app releases, and the coupling spends user bytes
  (owner-accepted D-AUD-3, bounded by D-AUD-18).** *Restated in A1.2.* Two distinct costs, not one:
  (a) a mispronunciation cannot be hot-fixed — it waits for a re-render *and* a release; and (b)
  when that release ships, **Play pushes the changed packs inside the automatic app update with no
  consent prompt** (ESpec §7.5). *Mitigations:* D-AUD-18 (never in a PATCH — corrections batch into
  a MINOR with a whatsnew line); per-book packs bound the silent delta to one book (~8 MB median,
  ~47 MB worst) rather than a testament; a reproducible per-chapter render pipeline keeps a fix
  cheap in dollars even when slow in calendar; and `AUD-C-1` (D-AUD-21) **measures the real patch
  size before we are committed**. *Residual risk, accepted:* a user on a metered connection with
  the whole Bible downloaded can be charged for a book-sized patch by an app update they did not
  initiate. If `AUD-C-1` shows it is worse than a patch — i.e. a full re-download — **D-AUD-3
  itself goes back to the owner before the render is commissioned.**
- **R-AUD-2 — Storage-pressure uninstalls and size complaints.** ~853 MB on an 8 MB app is the
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
  (FR-AUD-11) and would also silently corrupt Psalm 119 windows. *Mitigation, **corrected in
  A1.4**:* the M-AUD-2 gate (coverage of all 31,219 rows, monotonic offsets) **plus an
  ASR-derived second witness over the same audio** — transcribe the rendered files independently
  and derive verse boundaries from the transcript, then reconcile against the vendor's timestamps
  (D-AUD-E-8).
  > **Superseded:** the original mitigation proposed "spot-verification against the Phase-1
  > device-TTS boundaries as an independent witness." **That does not work and Diego is right to
  > kill it.** Phase-1 boundaries describe *a different recording* — where a Samsung TTS engine
  > starts a verse says nothing about where it starts in an ElevenLabs file. It is the same
  > category error as the Sprint-1 re-mirror trap: a witness must be an independent *method*
  > applied to the *same* subject, not the same claim from a different subject. The second-source
  > discipline of the plan and text gates transfers; that particular witness did not.
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
- **Play Asset Delivery (`asset-delivery`) + androidx.media3 (`media3-session`, `media3-exoplayer`)**
  — the two new runtime dependency families, and the source of the permission delta recorded in
  NFR-AUD-A. *(Updated A1.3: the Play Core / D-L-6 precedent establishes the **brokering** model,
  not a zero-permission outcome.)*
- **The CI bundle-size gate (re-pointed at the base module, D-AUD-E-7) and the merged-manifest
  permission check** — the two automated guards that keep NFR-AUD-A/B honest.
- **A Play internal track and a review cycle** — `AUD-C-1` / D-AUD-21 needs a real publish, so the
  Play release process is a *dependency*, not just a destination (OQ-AUD-12).
- **A named render owner and a vendor account** — OQ-AUD-13; currently unowned and blocking the
  scheduling of the render sprint.
- **`SettingsRepository` / DataStore** — audio preferences (voice, Wi-Fi-only, speed, sleep timer).
  No Room/schema change is anticipated; downloaded-pack state is Play's, not ours.
- **`AccessibilityGateTest`** — extended over the transport controls and follow-along (M-AUD-8).
- **The guilt-copy ban-scan (D-S17-1 / D-S20-1)** — extended over all audio strings (M-AUD-7).

---

> **Review note (Morgan + Diego), updated after amendment A1.** Every locked decision in §7 traces
> to the owner's brief; everything I recommended rather than received is marked as such and carried
> into §11 with the evidence that would settle it. D-AUD-8 (play marks read on press, via the
> existing seam) was challenged and **held** — Diego reached the same conclusion independently
> (ESpec §16.8). FR-AUD-14 (the verse-tap change) was challenged and **hardened**: it now ships only
> with named custom accessibility actions. The requirement I most want engineering to price early is
> still **FR-AUD-10** (the per-verse timing index, now correctly stated at 31,219 rows), because it,
> not the audio, is what decides OQ-AUD-1.
>
> **What changed most in A1 is the honesty of three numbers** — the download unit (a book, not a
> day), the permission delta (6→8, not 0), and the cost of a release that moves audio (bytes, not
> just calendar). None of them changes what we are building; all three change what we tell the
> owner and the user, which is the part a PRD is actually responsible for.
