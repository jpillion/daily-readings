# Audio render runbook — producing the read-aloud corpus

> **Who runs this:** the owner (or whoever holds the ElevenLabs account), from a **standalone
> session**. This document assumes **no prior context** — you do not need to have read the audio
> PRD, the engineering spec, or any sprint handoff. Everything needed is here or is named here.
> **Companion:** [RELEASING.md](RELEASING.md) is the repo's other owner-runnable procedure; this one
> follows the same shape. Background (not required reading): [PRD-audio.md](PRD-audio.md),
> [ENGINEERING_SPEC-audio.md](ENGINEERING_SPEC-audio.md) §10.
>
> **Status:** procedure defined; **not yet run**. Ticket `SE-T10`, brought forward at the owner's
> request. **Last updated:** 2026-07-26.

---

## 0. What this produces, and why it is a one-way door

You are producing the app's **third core content asset**, after the reading-plan data and the KJV
text. The output is:

- **~1,189 chapter audio files** (the whole KJV, ~79 hours, ~853 MB at 24 kbps Opus),
- **~1,401 short heading clips** ("Genesis Chapter 1.", "Chapter 2.", "Psalm 119, verses 1 to 40."),
- a **per-verse timing index** (~31,219 entries) without which the app cannot highlight, cannot
  seek, and cannot play the plan's verse-windowed days,
- a **manifest** pinning every file by SHA-256.

**Why it is a one-way door.** The render costs real money and the audio is not byte-reproducible —
running it again produces different bytes. More importantly, a mispronunciation discovered *after
the audio ships* costs a paid re-render **and** waits for a minor app release **and** silently
pushes a patch download to every user who has that book. So the expensive, irreversible step is
**last**, and everything before it exists to make sure it only happens once.

**The order below is not a suggestion.** Steps 1–6 are cheap and reversible. Step 7 is the door.

---

## 1. Hard rules

These are invariants, not preferences. Violating any of them invalidates the output.

| # | Rule | Why |
|---|---|---|
| **R1** | **Do not modify `app/src/main/assets/bible/bible.db`.** Read from it only. | It is a pre-packaged Room database carrying a pinned identity hash (`8144e1bc57f05006d1a15856ac762552`). Editing it — even adding an index — makes the app fail to open it at runtime with "Pre-packaged database has an invalid schema". This has happened once already (sprint-00F) and cost a P0 fix. **Read-only. No `ALTER`, no `VACUUM`, no writes of any kind.** |
| **R2** | **Spoken text comes from `strip(text_markup)` and nothing else.** | That is the exact same function TalkBack already speaks. A second normalizer anywhere — in Python, in a spreadsheet, in a "quick clean-up" — creates a second version of scripture. There is one text. |
| **R3** | **Timings come from the API and are never estimated, interpolated, or guessed.** | A timing index that is subtly wrong is worse than none: it silently mis-highlights and silently mis-plays the plan's verse windows. If a chapter returns no usable alignment, that chapter **fails and is re-rendered** (§9.4). |
| **R4** | **The pronunciation lexicon is finished and signed off BEFORE the corpus render.** | See §0. This is the only cheap moment to fix pronunciation. |
| **R5** | **Never exceed the spend ceiling without stopping and asking.** | §2.3. The script enforces it; do not raise it to "just finish". |
| **R6** | **Every chapter is rendered from whole verses.** Segment boundaries fall *between* verses, never inside one. | Keeps each segment's timings exact and self-contained, and puts the seams where an audio Bible naturally pauses. |
| **R7** | **Concatenate PCM, then encode once.** Never concatenate encoded `.opus` files. | Joining encoded files produces boundary artifacts and a duration that does not match the timing index. |

---

## 2. Before you start

### 2.1 Accounts and tools

| Need | Detail |
|---|---|
| ElevenLabs account | A **paid** plan. The commercial licence on a paid plan is what permits redistributing the generated audio in the app. Note the plan tier — it sets the per-character rate (§2.3). |
| API key | Held in the environment as `ELEVENLABS_API_KEY`. **Never** commit it, never paste it into a file in this repo, never put it in the app. |
| Python | 3.9+ |
| `ffmpeg` / `ffprobe` | For PCM→Opus encoding and duration measurement |
| Whisper | `openai-whisper` or `faster-whisper`, run **locally/offline**, for the verification pass (§9). Pin the model name and revision in the log. |
| Disk | **~15 GB free.** Raw PCM is ~10× the encoded size; the pipeline holds PCM per chapter transiently plus ~853 MB of final Opus. |
| Time | Expect the corpus render to run for **many hours**. It is resumable (§10) — this is expected, not a failure. |

### 2.2 Repo state

Work from a clean checkout of `main`. You will produce files under `audio/` and a hand-back
package; you will **not** modify any app code, any asset under `app/src/main/assets/`, or `bible.db`
(R1).

### 2.3 The spend ceiling

| Phase | Billable characters | Notes |
|---|---|---|
| Pilot (§5) | ~40,000 | 7 chapters. **Ceiling: $10.** |
| Corpus | 4,112,530 | The whole KJV, stripped |
| Headings | ~24,900 | ~1,401 short clips |
| **Total** | **~4,168,411** | |

Billing depends on model and plan: `eleven_v3` bills **1 credit/char**, `eleven_flash_v2` bills
**0.5**. At Scale-tier rates that is roughly **$688 (v3)** vs **$344 (flash_v2)**; at Business
overage roughly **$500** vs **$250**.

> **Ceiling: $750 for the corpus + headings.** The render script tracks characters submitted and
> **hard-stops** if the projection exceeds it. If it stops, do not raise the number — stop and
> report. A projection overrun means something is wrong (a retry loop, a duplicated chapter), and
> the money is real.

---

## 3. Inputs and outputs

**Inputs (all already in the repo, all read-only):**

| Path | What |
|---|---|
| `app/src/main/assets/bible/bible.db` | The KJV text. **R1: read-only.** |
| `app/src/main/assets/plans/*/plan.json` | The reading plans — the source of which verse-windowed headings are needed |
| `audio/headings/inventory.json` | The generated heading clip list (produced in §4.2 — do not hand-author it) |
| `audio/lexicon/<voiceId>.json` | The pronunciation lexicon (produced in §6) |

**Outputs (what you hand back):**

| Path | What | Committed to git? |
|---|---|---|
| `out/audio/<USFM>/<NNN>.opus` | 1,189 chapter files | **No** — too large (§11) |
| `out/audio/_headings/<clipId>.opus` | ~1,401 heading clips | **No** |
| `audio/timings/<voiceId>/<USFM>.json` | Per-verse ms offsets | **Yes** — small, reviewable |
| `audio/audio_manifest.json` | Per-file SHA-256, bytes, duration, WER, render params | **Yes** |
| `out/SHA256SUMS` | Checksums of every produced blob | **Yes** (inside the manifest) |
| `out/render_log.jsonl` | Per-chapter record of what was done | **Yes** |

---

## 4. Step 1 — Derive the text and the heading inventory (free, reversible)

### 4.1 Chapter text

For each of the 1,189 chapters, build the spoken text from `bible.db`:

- Select every verse row for the chapter **in `verse_id` order**. This includes **verse 0** where it
  exists — those are the 117 Psalm/Habakkuk superscriptions, and they are **spoken as part of the
  chapter**, before verse 1.
- Apply `strip()` to each verse's `text_markup` (**R2**). The rule, which must match the app exactly:
  drop all tags keeping inner text, turn `<l/>` into a single space, then collapse whitespace.
  Tags in the corpus are `<a>` (translator-added words), and possibly `<w>`, `<l/>`.
- Join verses with a single space.
- **Never insert verse numbers.** Verse numbers are never spoken (this is a product rule, not a
  style choice).

Record, per chapter, the **character offset range of every verse** within the joined text. This is
what turns the API's character-level timestamps into verse boundaries later — you cannot recover it
afterwards, so capture it now.

Also compute and store a **SHA-256 of each chapter's text**. The resume logic (§10) uses it to
guarantee you never stitch new audio onto text that has changed.

**Check before continuing:** 1,189 chapters; 31,219 verse entries total (31,102 numbered + 117
superscriptions); no chapter empty; the longest chapter is **Psalm 119 at 12,999 characters**; total
**4,112,530 characters**. If any of these differ, stop — the extraction is wrong.

### 4.2 Heading inventory

Headings are pre-rendered as a closed set of short clips. Generate `audio/headings/inventory.json`
with the repo's export task — **do not hand-write the heading strings**, because the wording rules
(especially the Psalms singular rule) live in app code and must not be duplicated by hand.

The inventory contains **1,401 clips**:

| Form | Count | Example text |
|---|---|---|
| Full, per canon chapter | 1,189 | `"Genesis Chapter 1."` |
| Full, per distinct verse window | 31 | `"Psalm 119, verses 1 to 40."` |
| Short, book-agnostic | 150 | `"Chapter 2."` |
| Short, per distinct verse window | 31 | `"Chapter 119, verses 1 to 40."` |

Rules the generator applies (listed so you can sanity-check the output, not so you can re-implement
them):

- **Psalms is singular for a single chapter**: `"Psalm 23."`, never `"Psalms Chapter 23."`
- **Single-chapter books get the book name only**, no chapter number: Obadiah, Philemon, 2 John,
  3 John, Jude.
- The short form is **book-agnostic** — `"Chapter 2."` is one clip used everywhere in the canon.

**Check:** exactly 1,401 entries, every `clipId` unique, ~24,900 characters total.

---

## 5. Step 2 — The pilot render (cheap, and it decides two open questions)

Render **only** these seven chapters, and nothing else:

| Chapter | Why it is in the set |
|---|---|
| 1 Chronicles 1 | Dense proper nouns — the pronunciation stress test |
| Numbers 26 | Dense proper nouns |
| Ezra 2 | Dense proper nouns and numbers |
| Psalm 23 | Tone |
| Isaiah 53 | Tone |
| John 11 | Tone, and contains the shortest verse |
| **1 Kings 8** | **11,367 chars — long enough to be split into segments.** This is the seam test |

**Ceiling: $10.**

The pilot answers four questions. Record each answer in `out/render_log.jsonl` and hand them back:

**Q1 — Voice sign-off (owner).** Listen. Is this voice fit for scripture? This is the owner's
judgement and nobody else's. *If no, choose another voice and re-run the pilot before anything else.*

**Q2 — What is the real per-request character cap for the model, on the endpoint that returns
timings?** This is the single most important number from the pilot. Published guidance is
ambiguous — a documented per-request maximum of ~5,000 characters, and separate guidance suggesting
reliable generation nearer ~2,000. **Determine it empirically**: submit chapter texts of increasing
length and find where quality or the timing response degrades. It changes the render's shape by 2×:

| Effective cap | Requests for the corpus | Chapters that must be split |
|---|---|---|
| 30,000 | 1,189 | 0 (0%) |
| 5,000 | 1,402 | 208 (17.5%) |
| 3,000 | 1,969 | 665 (56%) |
| 2,000 | 2,698 | 944 (79%) |

**Q3 — Are the segment seams audible?** Render 1 Kings 8 split on verse boundaries at the cap from
Q2, concatenate, and listen **specifically at the joins**. You are listening for a change in energy,
pitch or pace between segments — not for a click. If chapters sound like they were assembled from
pieces, say so; that is the finding that would send us to the alternative model (§5.1).

**Q4 — Does every pilot chapter return usable character-level timings?** If any chapter returns none,
note it — the fallback path (§9.4) exists for this and needs to be exercised at least once.

### 5.1 If Q2 or Q3 comes back badly

The chosen model is the expressive one, and the owner chose it deliberately. If the pilot shows the
per-request cap is very low **and** the seams are audible, the fallback is a different model that
renders every chapter in a single request with no seams at all, at half the cost, at some loss of
expressiveness. **That is an owner decision, not a render-machine decision** — stop, report Q2 and
Q3 with audio examples, and wait.

---

## 6. Step 3 — Build the lexicon (R4 — before the corpus, never after)

1. **Extract the candidate list.** Run the repo's out-of-lexicon token extractor over `bible.db`: it
   emits every distinct token in the corpus absent from a standard English dictionary, ranked by
   occurrence count. Expect a few thousand, with a steep head.
2. **Listen to the pilot for the ones that matter.** The pilot chapters were chosen to expose the
   worst of them (Mahershalalhashbaz, Chushanrishathaim, Zaphnathpaaneah and their neighbours).
3. **Write phoneme rules** (IPA or CMU) into `audio/lexicon/<voiceId>.json`. Use phoneme rules, not
   respellings — respelling is guesswork and does not survive a model change.
4. **Get the owner's sign-off on the lexicon.**
5. **Commit it.** Record its SHA-256; the manifest will carry that SHA and the app's verification
   gate asserts the committed lexicon is the one that was actually used.
6. Re-render **two or three** pilot chapters with the lexicon attached and confirm the corrections
   took effect.

> If you find yourself thinking "we can fix that one later" — you cannot, cheaply. See §0.

---

## 7. Step 4 — Record the rights acceptance (before spending)

Before commissioning the corpus, the accepted-risk note covering **recorded audio** must be written
into [`docs/data/README.md`](data/README.md), alongside the existing note that covers the KJV text.
A recording is a derivative work and carries its own rights layer on top of the text's.

**The spend is the commitment point** — this is recorded before the money is spent, not before the
app ships.

---

## 8. Step 5 — The corpus render (the one-way door)

### 8.1 Request parameters — pin these exactly and record them in the manifest

| Parameter | Value |
|---|---|
| Endpoint | The text-to-speech endpoint **that returns character-level timestamps**. Not the plain synthesis endpoint — without timings the output is unusable (R3). |
| `model_id` | As decided by the pilot (§5) |
| `voice_id` | The voice the owner signed off in Q1 |
| `output_format` | **PCM, 24 kHz, mono** — raw PCM, *not* a compressed format. You encode once, at the end (R7). |
| Pronunciation dictionary | The lexicon from §6, attached to **every** request |
| Voice settings | Pin stability / similarity / style once, from the pilot, and never vary them mid-corpus |
| Seed | If the API accepts one, pin it and record it. It will not make output byte-reproducible; it slightly improves consistency, which is worth having. |

**Record all of the above in `audio_manifest.json`.** The audio is not reproducible byte-for-byte,
so the *configuration* is what makes a future re-render comparable.

### 8.2 Segmentation (R6)

For each chapter: if its text fits the cap, it is one segment. Otherwise, greedily pack **whole
verses** into segments up to the cap.

This always works — the longest single verse in the KJV is **Esther 8:9 at 529 characters**, far
below any plausible cap, so no verse ever needs splitting.

Record the segment plan (which verses in which segment) in the log. It is part of the chapter's
provenance.

### 8.3 Per chapter

1. For each segment: submit the request, receive base64 PCM + a character-level alignment.
2. **Derive verse boundaries from the alignment**, using the character offsets captured in §4.1:
   a verse starts at the alignment start-time of its first character and ends at the end-time of its
   last. Use the alignment over the **raw input text** (the one whose indices match the text you
   submitted), not a normalized variant.
3. **Concatenate the segments' PCM** in order (R7). Track the cumulative offset of each segment.
4. **Merge the timings**: a verse's chapter-relative time is its segment-relative time **plus** that
   segment's cumulative offset. Offsets are additive because each segment holds whole verses.
5. **Encode once**, PCM → Opus, ~24 kbps, mono, 24 kHz, strip all metadata (keeps output stable).
6. **Measure the encoded duration** with `ffprobe`.

### 8.4 Per-chapter checks — run these immediately, not at the end

A chapter is not "done" until all of these pass. A failure means re-render that chapter, not
continue.

| Check | Requirement |
|---|---|
| Verse set | The chapter's timing entries match `bible.db`'s verse set for that chapter **exactly** — same count, same ids, no extras, no omissions. Superscriptions included. |
| Monotonic | Start times strictly increase; each verse's end is after its start and not after the next verse's start. |
| **Per-segment truncation guard** | For **each segment**: the last verse's end time equals that segment's own duration, within 250 ms. |
| **Sum guard** | The segment durations sum to the encoded chapter file's duration, within 250 ms — and therefore the last verse of the chapter ends at the chapter's duration. |
| Plausibility | The duration is within ±35% of `characters ÷ 14.46` seconds. Wildly short means truncation; wildly long means something was rendered twice. |

> The two guards together are what catch a **truncated render** — the most likely silent failure.
> A per-chapter check alone would miss a segment that came back short in the middle of a chapter.

### 8.5 Headings

Render the 1,401 heading clips from `audio/headings/inventory.json` using the **same voice, model and
settings** as the corpus. They must sound like the same reader, because they play immediately before
the chapter audio.

Heading clips **do not need a timing index** — each is a single short utterance and is played as its
own item. Do capture each one's duration.

**Check:** every `clipId` in the inventory has exactly one output file; no extra files.

---

## 9. Step 6 — The verification pass (ASR round-trip)

The reason this step exists: the audio is not byte-reproducible, so the usual "rebuild it and compare
bytes" check that guards the repo's other data assets **cannot be used here**. This is its
replacement, and it is the only independent check on the timings — the vendor's own alignment cannot
verify itself.

Run **locally and offline** with Whisper, over **every** chapter:

**9.1 — Transcribe** each chapter's final `.opus`.

**9.2 — Word error rate.** Normalize both the transcript and `strip(text_markup)` (lowercase, strip
punctuation) and compute WER.

- **Expected: ≤ 2%.** **Hard fail: > 5%.**
- KJV archaisms and proper nouns inflate WER against a modern ASR model, so keep a small pinned list
  of known ASR-vs-KJV spelling pairs in the normalizer, and **log it** — it must not become a way to
  hide real errors.

**9.3 — Boundary agreement.** Derive verse boundaries independently from the ASR word timestamps and
compare against the API-derived ones.

- **Hard fail: any verse off by > 300 ms.**
- Record the whole distribution, not just pass/fail. A systematic drift is a finding even if every
  verse is technically inside tolerance.

**9.4 — When a chapter fails.** Do not edit numbers to make it pass.

| Symptom | Action |
|---|---|
| High WER | Listen. Usually a truncated render or a lexicon miss. Re-render the chapter. |
| No alignment returned | Re-request. If it persists, use the vendor's **forced-alignment** endpoint (audio + known text → timestamps) for that chapter and record that you did. |
| Boundary drift over tolerance | Re-render. If it recurs on the same chapter, escalate — do not ship an approximate index (R3). |
| Genuinely acceptable variance | Record it in the reconciliation log **with the reason**, the same way the text corpus's handful of documented variances were recorded. Triage is a human act; log it. |

Every over-threshold chapter gets a line in the reconciliation log in
[`docs/data/README.md`](data/README.md) saying what it was and what was decided.

---

## 10. Resuming after an interruption

This will be interrupted — it runs for hours. It is designed to resume, and resuming is safe.

- The script maintains a **ledger** (`out/state.json`) with one entry per chapter and per heading
  clip: `pending` → `rendered` → `verified` → (or `failed`).
- On restart it **skips anything already `verified` whose recorded text SHA still matches** the text
  derived in §4.1. A mismatch means the text changed underneath the audio — it re-renders rather than
  silently pairing new text with old audio.
- Anything `failed` is retried; anything `rendered` but not `verified` re-runs verification only
  (free — no API spend).
- **Character spend is accumulated in the ledger**, so the §2.3 ceiling survives a restart. It is a
  ceiling on the whole render, not on one run.
- **Never delete the ledger to "start clean".** That silently re-spends. If you truly need to
  re-render one chapter, mark that one entry `pending`.

---

## 11. Step 7 — The hand-back package

Produce this on the render machine, then hand it over:

1. **One tarball per book**, 66 of them: `GEN.tar` … `REV.tar`, each containing that book's chapter
   `.opus` files. Plus `HEADINGS.tar`.
2. **`SHA256SUMS`** — checksums of **every** file, generated **on the render machine**.
3. **`audio/timings/<voiceId>/<USFM>.json`** — 66 files, the per-verse index. Small and readable.
4. **`audio/audio_manifest.json`** — for every chapter and clip: SHA-256, byte size, duration, WER,
   boundary-agreement figure; plus the pinned voice id, model id, all voice settings, the lexicon's
   SHA-256, and the Whisper model + revision used for verification.
5. **`out/render_log.jsonl`** — per chapter: segment plan, requests made, characters billed, retries,
   any variance recorded.
6. **The pilot answers** Q1–Q4 from §5, in writing.
7. **Total characters billed and the actual cost.**

**On arrival, the checksums are re-verified** (`sha256sum -c SHA256SUMS`) before anything is
published. The blobs are large and do not go into git; they are pinned by the checksums in the
manifest, which does. That is what makes a tampered or truncated file impossible to sneak in.

---

## 12. Definition of done

- [ ] 1,189 chapter files; every chapter of all 66 books present, none extra
- [ ] 1,401 heading clips; every inventory `clipId` present, none extra
- [ ] Timing index covers **31,219** verses — every chapter's verse set matching `bible.db` exactly,
      superscriptions included
- [ ] Every chapter passes the per-segment guard, the sum guard, monotonicity, and plausibility (§8.4)
- [ ] Every chapter passes WER ≤ 5% and boundary agreement ≤ 300 ms — or is logged with a reason
- [ ] The lexicon is committed, signed off, and its SHA recorded in the manifest
- [ ] The rights note is recorded in `docs/data/README.md` (§7)
- [ ] Manifest, timings, log and `SHA256SUMS` produced; checksums re-verified on arrival
- [ ] Total spend within the ceiling, and reported
- [ ] **`bible.db` is untouched** — confirm with `git status` (R1)

---

## 13. If you are unsure

Stop and ask. Every step before §8 is cheap to repeat; §8 is not. There is no deadline that makes it
cheaper to guess. In particular, do not:

- raise the spend ceiling to finish a run,
- edit a timing value to make a check pass,
- accept "close enough" on pronunciation because a re-render is inconvenient,
- or touch `bible.db` for any reason at all.
