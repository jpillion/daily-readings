# Daily Reading Planner — Engineering Spec: Read aloud (audio)

> **Owner:** Diego (Tech Lead / Android Architect) · **Status:** Draft for build ·
> **Last updated:** 2026-07-25 (**amendment pass A1**)
>
> ### Amendment A1 — 2026-07-25 — two owner decisions
>
> **(1) OQ-AUD-1 is RESOLVED: ElevenLabs.** The owner auditioned the field himself and chose on
> voice realism. Not re-litigated. Specced in **§10.0**; alternatives demoted to a
> recorded-considered footnote (§10.0.4). New decisions **D-AUD-E-20 … D-AUD-E-23** (model pin,
> lexicon-before-render, timing-source chain).
>
> **(2) NEW OWNER REQUIREMENT — audio packs must be plug-and-play, like translations.** Owner's
> words: *"the audio should be plug and play, similar to the translations. If they download
> different packs, the audio assets just get plugged in. There shouldn't need to be logic that is
> dependent on which asset is used."* This is an architectural requirement and it is specced as the
> **third instance of a pattern this repo already runs twice** (`PlanRegistry`+`PlanDescriptor`
> D-ALT-1/2/3; `translation`+`ReaderVersionSelector` D-N-1/2/3) rather than as a third shape. New
> **§7A**; new decisions **D-AUD-E-24 … D-AUD-E-28**.
>
> **What A1 supersedes** (marked in place, never silently rewritten): the *pack-count half* of
> **D-AUD-E-5** (§7.3), the timing-path scoping in **D-AUD-E-3** (§6.2), the `audio_voice_source`
> key in **§13.3**, and the single-voice reading of the §9.2 total ceiling. **Nothing in §4 (the
> player) or §5 (the queue) is invalidated** — see §7A.8, which is the interesting part.
>
> ### Amendment A4 — 2026-07-26 — `eleven_v3` is mandatory; the seam problem must be solved
>
> **The owner rejected `eleven_multilingual_v2` and `eleven_flash_v2` on output quality**, so A3's
> "named fallback" is **revoked** — there is no model to fall back to. **And Request Stitching, the
> vendor feature designed to make segmented long-form generation continuous, is unavailable for
> `eleven_v3`** (verified; **D-AUD-E-41**). Both escape routes are therefore closed and RE-AUD-18 is
> re-rated **HIGH**: the seams must be *solved*. **D-AUD-E-40** is an ordered remedy ladder with no
> early exit (§10.0.2b), and the runbook gains a hard **stop-and-escalate** condition if the ladder
> is exhausted with seams still audible. The pilot is **re-ordered so the per-request cap is
> question one, ahead of voice** — it is now a seam-count question with a 7× swing (213 seams at a
> 5,000 cap vs 1,509 at 2,000). **Recommendation: spike this before Sprint AUD-D is scheduled** —
> it blocks nothing, because Phase 1 has no render dependency.
>
> ### Amendment A3 — 2026-07-26 — spoken headings, the v3 verdict, and the render runbook
>
> **(B) Spoken headings (closes OQ-AUD-5).** Verse numbers are never spoken; a reading announces
> **Book + Chapter** once, then **"Chapter N"** for subsequent chapters — with the full form
> repeated whenever the **book** changes (so 3 John in the Jun 19 / Dec 19 portion is announced in
> full). Pre-rendered as a closed **1,401-clip** set (~$3–4), living **inside the voice pack**. New
> **§5A**; decisions **D-AUD-E-36 … D-AUD-E-39**. **The verse timing index and its truncation guard
> are untouched** (D-AUD-E-37) — headings are separate media items, never inside a chapter file.
>
> **(C) `eleven_v3` is pinned; D-AUD-E-21 is SUPERSEDED by D-AUD-E-34.** My objection assumed
> arbitrary splits; splitting on **verse boundaries** is always feasible (longest verse: Esther 8:9,
> 529 chars) and defeats it. The truncation guard is reworked to **per-segment + sum** (D-AUD-E-35)
> and comes out *stronger*. Two pilot gates remain (real per-request cap; seam audibility). §10.0.2a.
>
> **(A) New sibling document: [AUDIO_RENDER_RUNBOOK.md](AUDIO_RENDER_RUNBOOK.md)** — the
> owner-runnable procedure for producing the corpus, written to be read with **no other context
> loaded** (ticket `SE-T10`, brought forward).
>
> ### Amendment A2 — owner ruling on mixed/partial coverage
>
> **Voice selection is app-wide and exclusive.** One active voice for the whole app; coverage is
> evaluated only against it; a chapter it lacks prompts a download of **that voice's** pack, never a
> cross-voice substitution. **This supersedes D-AUD-E-24** (my per-play-unit resolution rule with a
> preferred-voice ordering), which is recorded-and-struck in **§7A.7** rather than deleted: I framed
> "which voice plays?" as an availability optimisation when it is a *correctness* rule, and the
> owner was right. New decisions **D-AUD-E-29 … D-AUD-E-33** (exclusive selection · the device voice
> as a registry entry · switch behaviour · storage/deletion · `active_voice_id`). Knock-on: the
> plug-and-play seam **moves into Phase 1** (§18) and gate assertions **17–20** are new (§7A.9).
> **Companion docs:** [PRD-audio.md](PRD-audio.md) (Maya — owns *what/why*; every `FR-AUD-*` /
> `NFR-AUD-*` / `D-AUD-n` / `M-AUD-n` / `R-AUD-n` / `OQ-AUD-n` id below is hers and is referenced,
> not restated), [ENGINEERING_SPEC-v3.md](ENGINEERING_SPEC-v3.md) (the text spine this sits on —
> same voice, same `D-*` convention), [ENGINEERING_SPEC-alternate-schedules.md](ENGINEERING_SPEC-alternate-schedules.md),
> [features/bible-data-architecture-review.md](features/bible-data-architecture-review.md) (the
> 2026-06 review that lifted audio out of V3 and first named the Media3 stack),
> [data/README.md](data/README.md) (provenance + AR-1), [CLAUDE.md](../CLAUDE.md).
>
> This doc owns **how** we build read-aloud: the player and its two seams, the queue, the per-verse
> timing index, the Play Asset Delivery pack design, where ~850 MB of build artifacts live, the CI
> gate redefinition, the render pipeline and its **replacement for the byte-diff idiom**, and the
> integration with the existing reader/schedule. Engineering decisions are `D-AUD-E-n` so they never
> collide with Maya's product `D-AUD-n`. Where I disagree with the PRD I say so in §16 rather than
> silently resolving it.
>
> **Owner decisions I build within and do not re-litigate:** delivery is Google Play Asset Delivery
> on-demand packs (D-AUD-1); no synthetic-voice disclosure (D-AUD-5); audio is pre-rendered once
> from the bundled KJV and never synthesized per user, never bundled in the AAB (D-AUD-4, D-AUD-6).

---

## 0. What I verified, and how (this section is the evidence, not a claim)

This repo's culture is that architectural claims are verified. Everything in this section was
executed in this session; the reproduction command is given so Morgan or Jordan can re-run it.

| # | Claim | How verified | Result |
|---|---|---|---|
| V1 | `com.google.android.play:asset-delivery:2.3.0` declares **no `INTERNET`** | Pulled the real AAR from `dl.google.com/dl/android/maven2` and read its `AndroidManifest.xml` | **Confirmed.** No `INTERNET`, no GMS permission |
| V2 | …but it **does** merge two `FOREGROUND_SERVICE*` permissions + 3 components | same AAR manifest | **Confirmed** — see §12 for the verbatim delta |
| V3 | `android.permission.FOREGROUND_SERVICE` is **already** in our merged manifest today | Read `androidx.work:work-runtime:2.7.1`'s AAR manifest (pulled via `androidx.glance:glance-appwidget:1.1.1` → `androidx.glance:glance:1.1.1` → `work-runtime 2.7.1`) | **Confirmed** — so asset-delivery adds only **one** genuinely new permission |
| V4 | The 6 permissions in today's merged release manifest, itemised by origin | Reconstructed from first-party AAR manifests (§12.1). The 6th, previously unattributed in our docs, is `${applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` from `androidx.core:core` | **Confirmed, fully attributed** |
| V5 | asset-delivery's transitive set + the version bumps it forces | Read the POM | `work-runtime 2.7.1→2.9.1`, `play-services-basement 18.1.0→18.4.0`, `play-services-tasks 18.0.2→18.2.0`, `core-common 2.0.3→2.0.4` |
| V6 | The `work-runtime` bump is manifest-neutral | Diffed the full `android:name` sets of 2.7.1 vs 2.9.1 AAR manifests | **Zero diff** — same permissions, same components |
| V7 | `androidx.media3:media3-session` declares **no** permissions and **no** components | Pulled `media3-session:1.10.1` AAR; its manifest has neither | **Confirmed** — the `MediaSessionService` is ours to declare, therefore `FOREGROUND_SERVICE_MEDIA_PLAYBACK` is ours to declare |
| V8 | `media3-exoplayer` merges only `ACCESS_NETWORK_STATE` + `WAKE_LOCK` | same | **Confirmed** — both already present via work-runtime ⇒ media3 adds **zero** new permissions |
| V9 | `SimpleBasePlayer` exists in `media3-common:1.10.1` | Listed `classes.jar` | **Confirmed** (this is what makes D-AUD-E-2 possible) |
| V10 | Opus decoding is available at minSdk 26 | developer.android.com supported-formats table | **Opus decoder: Android 5.0+**, containers Ogg / MP4 / Matroska. No bundled decoder needed |
| V11 | Play's cellular-consent threshold | developer.android.com PAD integration guide | **200 MB**, enforced by Play itself via `WAITING_FOR_WIFI` + `showConfirmationDialog()` — *not* 150 MB |
| V12 | What happens to downloaded packs on an app update | developer.android.com PAD guide, quoted verbatim | "**All previously-downloaded asset packs are invalidated**" — then "the patch for the assets is copied and applied"; patching is "a local, offline action". **Consequence in §7.5 — this is the real price of D-AUD-3** |
| V13 | Play size limits | Play Console help + App Bundle FAQ | base module ≤ **200 MB** compressed download; cumulative per-device ≤ **4 GB**; **≤ 1.5 GB per asset pack** |
| V14 | Per-book corpus sizes (for pack sizing) | Ran `MarkupStripper`'s rules over the shipped `app/src/main/assets/bible/bible.db` | 4,112,530 stripped chars over **31,219** verse rows / 1,189 chapters / 66 books — table in §7.2 |
| V15 | Adding a **free-form** (non-`@Entity`) table to `bible.db` does **not** disturb `ROOM_IDENTITY_HASH` | `tools/build_bible_db.py:288-333` — `translation` and `book` are already free-form tables Room never maps; only the `verse` DDL is hash-relevant | **Confirmed** — this *corrects* the brief's framing of problem 1 (§6.1) |
| **A1 additions — 2026-07-25** | | | |
| V16 | ElevenLabs phoneme tags (IPA/CMU pronunciation-dictionary rules) work on **only** `eleven_v3` and `eleven_flash_v2`; every other model falls back to **alias substitution only** | ElevenLabs docs (pronunciation dictionaries) | **Confirmed** — this is the constraint that pins the model (§10.0.2) |
| V17 | Model character limits per request: `eleven_v3` **5,000** (~5 min) · `eleven_flash_v2` **30,000** (~30 min, English-only) · `eleven_flash_v2_5` 40,000 · `eleven_multilingual_v2` 10,000 · `eleven_turbo_v2_5` **deprecated** | ElevenLabs models page | **Confirmed** |
| V18 | Chapter length distribution of our own corpus, against those caps | Ran `MarkupStripper`'s rules per chapter over the shipped `bible.db` | **0 of 1,189 chapters exceed 30,000 chars**; **208 (17.5%) exceed 5,000**; 4 exceed 10,000; longest = **Psalm 119 at 12,999 chars**; median 3,282. *This is the decisive number in §10.0.2* |
| V19 | ElevenLabs `with-timestamps` returns **character-level** `alignment` + `normalized_alignment`; per-model support is **not documented** | ElevenLabs API reference | Confirmed for the endpoint; the model-support gap is why §10.0.3 specifies a fallback |
| V20 | ElevenLabs **Forced Alignment API** exists: audio + text → time-aligned transcript, **max 10 h audio**, priced at the Speech-to-Text rate | ElevenLabs capabilities page | **Confirmed** — the fallback timing source (D-AUD-E-23) |

**Not verified here, and honestly labelled:** the *merged* release manifest could **not** be built in
this session — `platforms;android-37` is not published to the SDK repository yet (`sdkmanager`
returns `Failed to find package 'platforms;android-37'`), so `compileSdk = 37` cannot be satisfied
on this machine. The permission delta in §12 is therefore derived from the first-party **library
manifests**, which is what the merger consumes, and is exact for `uses-permission` and component
elements (the merger's only transform on them is placeholder substitution and `tools:` overrides,
neither of which applies). **The D-L-6 ritual still runs, on a build machine, as ticket
`AUD-A-0`:**

```bash
# baseline
./gradlew :app:processReleaseMainManifest
cp app/build/intermediates/merged_manifests/release/AndroidManifest.xml /tmp/before.xml
# add the dependency, rebuild, diff
./gradlew :app:processReleaseMainManifest
diff <(grep -oE '(uses-permission|<service|<receiver|<activity|<provider)[^>]*' /tmp/before.xml | sort) \
     <(grep -oE '(uses-permission|<service|<receiver|<activity|<provider)[^>]*' app/build/intermediates/merged_manifests/release/AndroidManifest.xml | sort)
```

It is the **first ticket of the first sprint** and it is a go/no-go: if the delta is anything other
than §12's table, we stop and re-plan.

---

## 1. Purpose & scope

Read-aloud graduates the app from "read it, or tap out to your Bible app" to "**read it, tap out, or
hear it**" (PRD §1). This spec covers, at buildable detail:

- the **player** — Media3 `MediaSessionService`, the two seams that make device-TTS (Phase 1) and
  pre-rendered files (Phase 2) interchangeable without rewriting the player (NFR-AUD-F);
- the **queue** — `Portion` → verse ranges → playable units, built from `PortionVerseBridge` so
  Psalm 119's windows work by construction (FR-AUD-6, M-AUD-1);
- the **per-verse timing index** (FR-AUD-10, D-AUD-10) — where it lives, why not in `bible.db`;
- the **PAD pack design** — granularity, lifecycle, eviction, sideload, consent (FR-AUD-19/20/21);
- **where ~850 MB of build artifacts live** and how CI touches them without paying for them daily;
- the **CI bundle-size gate redefinition** so it still guards what it was guarding (NFR-AUD-B);
- the **render pipeline** and — because TTS output is not byte-deterministic — the **replacement for
  the byte-diff idiom** (NFR-AUD-D, M-AUD-2);
- **integration**: entry points, follow-along, the shipped verse-tap gesture, one-screen fit;
- the **manifest/permission delta**, NFRs, deps, risks, decisions, and a sprint breakdown.

**Out of scope** (placed, not built): everything in PRD §4's non-goals, plus — as an engineering
addition — a **global mini-player** (§11.4, D-AUD-E-12) and **word-level** highlight.

**Hard invariants carried forward:** no `INTERNET`; no analytics; no server of ours; the base
module stays under the CI ceiling; `PortionVerseBridge` stays the *only* home of portion→verse
resolution; `DayCompletionClassifier` stays the *only* completion predicate; `MarkReadOnOpenUseCase`
stays the *only* marking rule.

---

## 2. Architecture overview

Unchanged pattern: single-activity Compose, MVVM + UDF, repository pattern, Hilt, domain free of
Android framework types. Audio adds one feature area (`audio/`), the app's **first `<service>`**,
and its first Gradle modules that are not `:app`.

```
        ┌──────────────────────────────────────────────────────────────────────────┐
        │                              UI (Compose)                                 │
        │  Schedule: per-card ▶ + top-bar ▶      Reader: ReaderAudioSlot transport   │
        │  Settings → Audio: voice, downloads, storage, Wi-Fi-only, speed, timer     │
        └───────────────┬──────────────────────────────────────────────────────────┘
                        │ state (StateFlow<PlaybackUiState>) ▲ intents
                        ▼                                    │
        ┌──────────────────────────────────────────────────────────────────────────┐
        │  SEAM 1 (app-facing, ONE):  AudioReadingController      @Singleton         │
        │  play(AudioQueue) · pause · seekToVerse · speed · sleepTimer               │
        │  state: StateFlow<PlaybackState>  (incl. activeVerseId)                    │
        └───────────────┬──────────────────────────────────────────────────────────┘
                        │ owns
                        ▼
        ┌──────────────────────────────────────────────────────────────────────────┐
        │  Media3:  PlaybackService : MediaSessionService  →  MediaSession           │
        │  ⇒ lock screen · notification · headset/Bluetooth · Android Auto-for-free  │
        │  AudioFocusCoordinator (ONE home for focus + becoming-noisy)               │
        └───────────────┬──────────────────────────────────────────────────────────┘
                        │ media3 Player (the phase-swappable unit)
        ┌───────────────┴───────────────────────────┐
        ▼                                           ▼
┌───────────────────────────┐          ┌───────────────────────────────────────────┐
│ SEAM 2a — PHASE 1          │          │ SEAM 2b — PHASE 2                          │
│ TtsVersePlayer             │          │ FileVersePlayer (ExoPlayer)                │
│ : SimpleBasePlayer         │          │ ClippingMediaSource per chapter window     │
│ android.speech.tts         │          │ verse position ← VerseTimingIndex          │
│ boundaries ← utterance cbs │          │ files ← AssetPackManager (on-demand)       │
└───────────────────────────┘          └───────────────────────────────────────────┘
                        ▲                                           ▲
                        └──────────────── AudioQueue ───────────────┘
                    built ONCE from PortionVerseBridge + BibleTextSource
```

**The two seams are the whole durability argument (NFR-AUD-F).** Seam 1 is what the UI sees; it
never changes. Seam 2 is a media3 `Player` — Phase 1 and Phase 2 are *two implementations of one
interface*, and everything above them (session, notification, focus, transport, queue, follow-along,
marking, stop rules, speed, sleep timer) is written once. Swapping ElevenLabs for LibriVox is a
change to the *artifact*, not to seam 2; swapping device TTS for files is a change to seam 2, not to
seam 1.

---

## 3. Module / package layout

The app stays one Gradle module (`:app`) for **code**. Asset packs, however, *must* be separate
Gradle modules — that is how AGP models them — so the project gains its first sibling modules. They
contain **no code**, only `src/main/assets/`, and are **generated** (D-AUD-E-5).

```
daily-readings/
├─ app/                                        # unchanged single code module
│  └─ src/main/kotlin/com/jpillion/dailyreadingplanner/
│     ├─ audio/                                # NEW feature area (sibling of bible/, like bible/ is of ui/)
│     │  ├─ domain/
│     │  │  ├─ AudioReadingController.kt       # SEAM 1 — the ONLY type the UI injects
│     │  │  ├─ model/
│     │  │  │  ├─ AudioQueue.kt                # ordered List<QueueChapter>; the unit of play
│     │  │  │  ├─ QueueChapter.kt              # (bookNo, chapter, window: VerseRange, verses: List<SpokenVerse>)
│     │  │  │  ├─ SpokenVerse.kt               # (canonicalId, plainText, isTitle)
│     │  │  │  ├─ PlaybackState.kt             # sealed: Idle | Preparing | Playing | Paused | Ended (+ activeVerseId)
│     │  │  │  ├─ PlayUnit.kt                  # sealed: PortionUnit | DayUnit | BrowseChapterUnit  (FR-AUD-4 stop rules)
│     │  │  │  └─ AudioAvailability.kt         # sealed: HighQuality | DeviceVoice | Unavailable(reason)
│     │  │  ├─ BuildAudioQueueUseCase.kt       # Portion/day/chapter -> AudioQueue  (via PortionVerseBridge)
│     │  │  ├─ ResolveAudioAvailabilityUseCase.kt   # THE degradation ladder, one home (FR-AUD-22)
│     │  │  ├─ VerseTimingIndex.kt             # (chapter) -> List<VerseTiming(verseId,startMs,endMs)>; pure
│     │  │  ├─ VerseTimingSource.kt            # seam: suspend fun timingsFor(bookNo, chapter): VerseTimingIndex?
│     │  │  ├─ AudioPackPlan.kt                # THE ONE mapper: verse range / book -> pack name(s). Pure.
│     │  │  ├─ AudioDownloadRepository.kt      # interface: states, request, cancel, remove, sizes
│     │  │  └─ StartReadAloudUseCase.kt        # marks read (MarkReadOnOpenUseCase) + builds + starts
│     │  ├─ data/
│     │  │  ├─ playback/
│     │  │  │  ├─ PlaybackService.kt           # @AndroidEntryPoint MediaSessionService (the app's first service)
│     │  │  │  ├─ Media3AudioReadingController.kt   # SEAM 1 impl; owns the MediaController/session link
│     │  │  │  ├─ TtsVersePlayer.kt            # SEAM 2a: SimpleBasePlayer over android.speech.tts
│     │  │  │  ├─ FileVersePlayer.kt           # SEAM 2b: ExoPlayer + ClippingMediaSource  [P2]
│     │  │  │  ├─ AudioFocusCoordinator.kt     # focus + ACTION_AUDIO_BECOMING_NOISY, ONE home
│     │  │  │  ├─ PlaybackNotification.kt      # channel "read_aloud", id 3003
│     │  │  │  └─ SleepTimer.kt                # pure countdown -> stop reason
│     │  │  ├─ packs/                          # [P2]
│     │  │  │  ├─ PlayAssetPackRepository.kt   # AudioDownloadRepository impl over AssetPackManager
│     │  │  │  ├─ AssetPackAvailability.kt     # Play-less/sideload detection, eviction handling
│     │  │  │  └─ PackFileLocator.kt           # pack -> assetsPath()/<usfm>/<ch>.opus, re-resolved every launch
│     │  │  └─ timing/
│     │  │     └─ PackVerseTimingSource.kt     # VerseTimingSource over the pack's per-book sidecar
│     │  └─ ui/
│     │     ├─ ReadAloudTransport.kt           # the composable that fills ReaderAudioSlot
│     │     ├─ ReadAloudViewModel.kt           # activity-scoped-ish state for the transport
│     │     └─ downloads/AudioDownloadsScreen.kt + AudioDownloadsViewModel.kt   # Settings → Audio [P2]
│     ├─ bible/ui/reader/ReaderAudioSlot.kt    # EDITED: gains parameters, renders ReadAloudTransport
│     ├─ bible/ui/reader/ReaderViewModel.kt    # EDITED: combines activeVerseId into uiStateForPage
│     ├─ bible/ui/reader/ReaderScreen.kt       # EDITED: follow-along autoscroll + session-scoped verse tap
│     ├─ ui/day/DayContent.kt                  # EDITED: trailing ▶ in the existing card Row (FR-AUD-17)
│     ├─ ui/day/DayReadingsViewModel.kt        # EDITED: onReadAloudTapped(date, portion)
│     ├─ data/prefs/SettingsRepository.kt      # EDITED: 4 new keys (§13.3)
│     └─ di/AudioModule.kt                     # NEW
├─ audio-packs/                                # NEW — generated Gradle asset-pack modules, code-free
│  ├─ gen/build-packs.gradle.kts               # generates the 66 module dirs from book_catalog_export.json
│  ├─ audio_gen/  audio_exo/ … audio_rev/      # 66 dirs, each: build.gradle.kts + src/main/assets/
│  └─ .gitignore                               # src/main/assets/** is NEVER committed (D-AUD-E-6)
├─ audio/                                      # NEW — the SMALL, reviewable, COMMITTED half
│  ├─ timings/<usfm>.json                       # 66 files, per-verse ms offsets — COMMITTED (D-AUD-E-3)
│  └─ audio_manifest.json                       # per-chapter sha256/bytes/durationMs/wer — COMMITTED
└─ tools/
   ├─ render_audio.py                          # the render driver (build-time only, never ships)
   ├─ verify_audio_asr.py                      # the ASR round-trip gate (render-time)
   └─ build_audio_packs.py                     # blobs + timings -> pack module assets
```

**Why `audio/` is a sibling of `bible/`, not nested in it.** Identical reasoning to D-V3-1: audio is
a self-contained feature area with its own data source, domain and UI. It *depends on* `bible/`
(text, verse ids, markup stripping) and on `domain/` (portions, marking) and is depended on by
neither — a clean one-way arrow. The pure spine (`AudioQueue`, `VerseTimingIndex`, `AudioPackPlan`)
lives in `audio/domain` and is JVM-testable with no Android types.

**Hilt wiring (`di/AudioModule.kt`).**

```kotlin
@Module @InstallIn(SingletonComponent::class)
abstract class AudioModule {
    @Binds @Singleton
    abstract fun bindController(impl: Media3AudioReadingController): AudioReadingController

    @Binds @Singleton
    abstract fun bindTimingSource(impl: PackVerseTimingSource): VerseTimingSource

    @Binds @Singleton
    abstract fun bindDownloads(impl: PlayAssetPackRepository): AudioDownloadRepository
}
```

**Decision D-AUD-E-18 — `AudioReadingController` is `@Singleton`, not `@ActivityRetainedScoped`.**
`ReaderHandoff` and `InAppUpdateState` are `@ActivityRetainedScoped` because their lifetime is a
screen flow. Playback must survive the Activity entirely (that is the feature). The controller is a
process-lifetime singleton bound to the `MediaSessionService` via a `MediaController` future; the
service is what actually outlives everything. `PlaybackService` is `@AndroidEntryPoint` and injects
the queue/timing/text use cases directly — the same pattern the widget uses via `@EntryPoint`.

---

## 4. The player (problem 5)

### 4.1 The decision

**Decision D-AUD-E-2 — the player is `androidx.media3`: a `MediaSessionService` hosting a
`MediaSession` over a media3 `Player`; the *phase-swappable unit is one `Player` implementation*.**

Rejected alternatives, with the reason:

| Option | Why rejected |
|---|---|
| `android.media.MediaPlayer` | No session/notification/transport plumbing at all — we would hand-write lock-screen controls, media buttons, focus, and metadata, i.e. re-implement `MediaSession` badly. It also cannot clip a chapter file to a verse window without seek-and-poll hacks. |
| Device `TextToSpeech` **as the architecture** (not just as a source) | Then Phase 2 is a rewrite, which is exactly what NFR-AUD-F forbids. TTS is a *source*, and it belongs behind seam 2. |
| `androidx.media` (legacy `MediaBrowserServiceCompat`) | Superseded; media3 is the supported path and is what the June architecture review already named. |
| media3 **without** `media3-session` (ExoPlayer only) | Loses the lock screen, headset buttons and notification — i.e. loses G13, the point of the feature. |

The load-bearing fact, verified (V9): **`SimpleBasePlayer` is in `media3-common`.** It exists
precisely so a non-ExoPlayer backend can be a first-class media3 `Player` — you implement
`getState()` and a handful of `handleX()` commands and you get a full `Player`. That makes Phase 1
(TTS) a *real* media3 player, which means `MediaSession`, the notification, the lock screen, headset
buttons, Bluetooth/car transport and Android Auto's basic surface all work in **Phase 1**, unchanged
in Phase 2. This is what makes Maya's "~80% of the engineering is the player" claim true rather than
hopeful — I priced it and I agree with it.

### 4.2 The timeline model — media items are **chapters**, not verses

**Decision D-AUD-E-4 — one media item per chapter (clipped to the queue's verse window); the
*verse* is a derived position, never a timeline entry.**

The tempting alternative — one media item per verse — makes `seekToNextMediaItem()` mean "next
verse" and looks elegant. It is wrong for this product for two reasons:

1. **Audible seams.** 31,219 clip boundaries in a *scripture reading* is 31,219 chances for a click
   or a swallowed final consonant. Continuity is a quality attribute here, not a nicety.
2. **It forces a per-verse file layout on Phase 2** or a `ClippingMediaSource` per verse over the
   same file. Whole-chapter files with a timing index is the layout that makes the artifact small,
   cache-friendly and cheap to re-render.

So: `Player.getCurrentMediaItemIndex()` = the chapter; `activeVerseId` is published by seam 1 as a
separate value, derived from position (Phase 2) or utterance callbacks (Phase 1). MediaSession
`next`/`previous` therefore mean **next/previous chapter**, which is also the right transport
semantic on a car stereo. Verse-level movement is an *in-app* affordance (tap-to-seek, FR-AUD-13),
not a media-button one. This satisfies FR-AUD-2's "previous/next verse-**or**-chapter" as
chapter-on-transport / verse-in-app, and I flag the reading in §16.

### 4.3 Phase 1 — `TtsVersePlayer`

**Decision D-AUD-E-9 — one TTS utterance per verse; `UtteranceProgressListener.onStart(id)` is the
verse boundary; no timing index is involved.**

```kotlin
class TtsVersePlayer(...) : SimpleBasePlayer(Looper.getMainLooper()) {
    // queue.chapters -> MediaItemData per chapter; verses enqueued with QUEUE_ADD,
    // utteranceId = canonicalId.toString()
    // onStart(utteranceId) -> activeVerseId = utteranceId.toLong(); invalidateState()
    // onDone(lastVerseOfChapter) -> advance media item (or stop, per PlayUnit)
    // onError -> AudioAvailability.Unavailable, surfaced once, never a crash
}
```

Notes that will otherwise be discovered painfully:

- `TextToSpeech.speak(..., QUEUE_ADD, ...)` with per-verse utterance ids gives verse granularity
  exactly. `onRangeStart` (API 26+) gives *word* ranges and is **not needed** — word highlight is
  out of scope, and depending on it would add an API-26-engine-support risk for nothing.
- **Duration and scrubbing are unavailable.** `TtsVersePlayer` reports `C.TIME_UNSET` for duration
  and does not advertise `COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM`, so the media notification shows no
  scrubber in Phase 1. That is honest — we genuinely do not know where we are in milliseconds — and
  it degrades gracefully: Phase 2 turns the scrubber on by advertising the command. Recorded so it
  is a decision and not a bug report.
- **Engine absence is a first-class state**, not an exception: `TextToSpeech` init status
  `ERROR`, or `isLanguageAvailable(Locale.UK/US) < LANG_AVAILABLE`, resolves to
  `AudioAvailability.Unavailable(NoEngine)` and the control explains itself (FR-AUD-22, U-AUD-7).
- **Speed**: `setSpeechRate(f)`. Engines vary; clamp the persisted factor to `0.75f..2.0f` and note
  that above ~1.5× several stock engines degrade badly. Same persisted value drives
  `ExoPlayer.setPlaybackSpeed` in Phase 2, so the user's setting survives the phase change.

### 4.4 Phase 2 — `FileVersePlayer`

`ExoPlayer` with, per queue chapter, a `ClippingMediaSource` over the chapter's local file, clipped
to `[timing(window.start).startMs, timing(window.end).endMs]`. **This is the mechanism by which
Psalm 119:1–40 plays exactly its verses out of a whole-chapter file** — the reason FR-AUD-10 exists.

`activeVerseId` comes from a 200 ms position poll mapped through `VerseTimingIndex.verseAt(ms)`
(binary search, pure, JVM-tested). 200 ms is well inside the human tolerance for a highlight and
costs nothing; a `Player.Listener` alone is not enough because there is no per-verse event.

**Decision D-AUD-E-11 — one `AudioFocusCoordinator` owns audio focus and
`ACTION_AUDIO_BECOMING_NOISY` for both players; ExoPlayer's built-in handling is left OFF.**
ExoPlayer will do focus and becoming-noisy for you (`setAudioAttributes(attrs, true)`,
`setHandleAudioBecomingNoisy(true)`) but `SimpleBasePlayer` will not, so Phase 1 would need its own.
Two implementations of "duck for the sat-nav" is exactly the kind of drift this codebase kills on
sight (R-STREAK-5, D-S17-2, D-K-HINT-1). One home, used by both, `AudioFocusRequest` (API 26 — our
minSdk exactly, so no legacy branch).

### 4.5 The service and its notification

```xml
<service
    android:name=".audio.data.playback.PlaybackService"
    android:exported="true"
    android:foregroundServiceType="mediaPlayback">
    <intent-filter><action android:name="androidx.media3.session.MediaSessionService" /></intent-filter>
</service>
```

- `exported="true"` with the media3 intent filter is required for external controllers (Bluetooth,
  Assistant, Auto) to bind. This is the app's **first exported component other than `MainActivity`**
  and should be called out in the security review.
- Notification channel `read_aloud`, id **3003** (3001 = reminder, 3002 = persistent tray — both
  taken; §17 of the codebase survey). `IMPORTANCE_LOW`, `CATEGORY_TRANSPORT`. Media3 builds the
  notification; we supply metadata (`MediaMetadata.title` = the reference, e.g. "Genesis 1–2",
  through the same `ReadingFormatter` — no second formatter, D-UI-2's singular-Psalm rule inherited
  for free).
- `POST_NOTIFICATIONS`: playback is user-initiated, so the media notification appears only after an
  explicit press. We reuse the existing `NotificationPermissionChecker` + `MainActivity`'s
  `RequestPermission` launcher pattern (S12/S22). **A denial does not block playback** — audio still
  plays, the transport is in-app only; that is strictly better than refusing to play.

---

## 5. The queue — portion → verses → playable units

**Decision D-AUD-E-6a — `BuildAudioQueueUseCase` is the single constructor of an `AudioQueue`, and it
gets its ranges from `PortionVerseBridge.rangesFor(portion)` and its text from `BibleTextSource` +
`MarkupStripper.strip`. It never re-derives a range and never uses a chapter file's bounds.**

```kotlin
class BuildAudioQueueUseCase @Inject constructor(
    private val bridge: PortionVerseBridge,
    private val textSource: BibleTextSource,
) {
    suspend fun forPortion(portion: Portion): AudioQueue =
        AudioQueue(bridge.rangesFor(portion).map { range -> chapterFor(range) })

    suspend fun forChapter(book: Book, chapter: Int): AudioQueue =
        AudioQueue(listOf(chapterFor(VerseId.chapterRange(book.order, chapter))))

    private suspend fun chapterFor(range: VerseRange): QueueChapter =
        QueueChapter(
            bookNo = VerseId.book(range.startVerseId),
            chapter = VerseId.chapter(range.startVerseId),
            window = range,
            verses = textSource.getVerses(range).map {
                SpokenVerse(it.canonicalId, MarkupStripper.strip(it.markup), it.isTitle)
            },
        )
}
```

Everything the PRD asks for falls out of this rather than being special-cased:

- **FR-AUD-6 (Psalm 119 windows)** — `PortionVerseBridge` already narrows a windowed `Reference` to
  `[19_119_001, 19_119_040]` (Sprint J, D-READER-1). The queue inherits it. A whole-chapter file is
  clipped to that window in Phase 2 and simply not enqueued beyond verse 40 in Phase 1.
- **FR-AUD-7 (117 superscriptions)** — `VerseId.chapterRange` starts at verse **0**, so titles are
  already in the range. `SpokenVerse.isTitle` carries them; they are **spoken, not numbered**
  (nothing speaks `nativeLabel` anywhere — see FR-AUD-9).
- **FR-AUD-8 (the two-book Jun 19 / Dec 19 portion)** — `rangesFor` maps each `Reference`
  independently and never assumes a shared book (the Sprint-B mutation-pinned property). Two ranges,
  two queue chapters, one portion.
- **FR-AUD-9 (spoken text)** — `MarkupStripper.strip` is already the app's TalkBack text source
  (`ReaderScreen.verseTapDescription`). One home; a markup tag can never reach a voice.
- **N-stream plans** — `PlayUnit.DayUnit` iterates the *active* plan's portions in stream order
  (`GetDayReadingsUseCase`), so M'Cheyne's 4 and a 1-stream plan work with no audio-side branch.
- **Feb 29** — the day has no portions, so `DayUnit` is empty and no control is rendered. No guard
  needed (the D-O-5 pattern).

**Stop rules (FR-AUD-4, D-AUD-9)** live in `PlayUnit`, evaluated when the last media item ends:
`PortionUnit` → stop; `DayUnit` → advance to the next stream's portion, then stop; `BrowseChapterUnit`
→ append the next chapter via `GlobalChapterIndex` (which already crosses book boundaries and is
bounded at Gen 1 / Rev 22 — Sprint H, D-H-2) and keep going. One `when`, one home, fully
JVM-testable.

---

## 5A. Spoken headings (NEW OWNER REQUIREMENT, A3 — closes OQ-AUD-5)

**Owner ruling (2026-07-26).** Verse numbers are **never** spoken (FR-AUD-9, unchanged). A reading
**announces Book + Chapter once at the start**; subsequent chapters *within the same reading* get
**"Chapter N" only**. His example: `Genesis 1-2` → *"Genesis Chapter 1."* → chapter 1 →
*"Chapter 2."* → chapter 2.

### 5A.1 Why this is pre-rendered, not synthesised at runtime

The heading space is **closed and enumerable at build time**, which is the whole reason this is
cheap. A realtime API path was considered and **rejected by the owner**: it needs either an
on-device key (unshippable) or a proxy of ours (reintroduces the hosting D-AUD-1 exists to avoid),
and either way it needs `INTERNET` — which would undo the reason Play Asset Delivery was chosen at
all (NFR-AUD-A). Pre-rendering costs **~$3–4 once**.

**Inventory — computed from `BookCatalog` and the three bundled plan assets, not estimated:**

| Form | Count | Example |
|---|---|---|
| Full, per canon chapter | **1,189** | "Genesis Chapter 1." |
| Full, per distinct verse window | **31** | "Psalm 119, verses 1 to 40." |
| Short, **book-agnostic** | **150** | "Chapter 2." |
| Short, per distinct verse window | **31** | "Chapter 119, verses 1 to 40." |
| **Total distinct clips** | **1,401** | **~24,900 chars ⇒ ~$3–4** |

> **Reconciliation note.** My first count came to 1,370 because I generated only one form per
> windowed reference. The extra **31** are the *short* forms of the windowed headings, reachable
> when a windowed chapter is not the first chapter of its reading. Rather than reason about
> reachability, **we generate the closure** — 31 extra two-second clips is far cheaper than a
> missing clip at runtime. The two counts now agree at 1,401.
>
> Windowed refs per plan: Bible Companion **4**, M'Cheyne **38**, Chronological **0** — collapsing
> to **31 distinct** windows across all plans (the plans share some divisions).

**A property worth recording, because it pays off later (D-AUD-E-19):** the full form covers *every*
canon chapter and the short form is *book-agnostic*, so **any future whole-chapter reading plan
needs zero new heading audio.** Only a plan introducing a *novel verse window* would — and under
D-AUD-E-19 (audio never changes in a patch release) that is exactly the kind of coupling worth
knowing about in advance. A new plan is still a pure data drop unless it invents a new window.

### 5A.2 The selection rule

**Decision D-AUD-E-38 — heading selection is a pure function of the queue,
`HeadingPlan.headingsFor(queue): List<HeadingRef?>`, one entry per `QueueChapter`.** The rule:

1. **Full form when the *book* changes** — including the first chapter of the reading. This is
   deliberately "book changes", not "first chapter": the Jun 19 / Dec 19 portion is **2 John +
   3 John**, so 3 John takes the **full** form. A "first chapter only" rule would announce
   *"3 John Chapter 1"* as *"Chapter 1"*, which is wrong.
2. **Short form otherwise** — subsequent chapters of the same book within the reading.
3. **Single-chapter books: book name only, no chapter number** — Obadiah, Philemon, 2 John, 3 John,
   Jude (verified: exactly 5).
4. **Psalms is singular for one chapter**: "Psalm 23", never "Psalms Chapter 23". This **reuses
   `ReadingFormatter.singularizeBookName(canonicalName, singleChapter)`** — the D-UI-2 rule already
   shared by the Schedule and the reader. **It is not reimplemented**; a fourth copy of that rule is
   exactly the drift this codebase kills on sight.
5. **Windowed refs take the verse form**: "Psalm 119, verses 1 to 40."
6. `BrowseChapterUnit` auto-advance announces each chapter as it arrives, applying the same rule —
   so browsing from Genesis 50 into Exodus 1 announces the book, because the book changed.

### 5A.3 Where the clips live, and the drift guard

**Decision D-AUD-E-36 — headings are voice-specific, so they live **inside the voice pack** and are
declared by its manifest like everything else (D-AUD-E-25/26), keyed by a stable `clipId`.**

```json
"headings": { "format": "headings-v1", "pathTemplate": "audio/_headings/{clipId}.opus",
              "inventorySha256": "…" }
```

They must be voice-specific for the obvious reason — a heading in a different voice from the chapter
it introduces would be jarring — and putting them in the pack means they are plug-and-play by
construction: a second voice ships its own headings and **no code changes**. Cost: **~5 MB per
voice**, under 1% of the corpus.

Because headings are keyed by reference and resolved through the manifest, a future *unbounded*
heading feature (arbitrary references, e.g. spoken cross-references) is an **additive generator
against an existing seam**, not a retrofit.

**Decision D-AUD-E-39 — the heading inventory is GENERATED from `BookCatalog` + the plan registry
into a committed `audio/headings/inventory.json`, and the render reads *that file*; the heading
strings are never hand-authored and never re-derived in Python.** This is the `exportBookCatalog`
discipline (D-V3-5/§6) applied to audio: the wording rules — especially the Psalms singular rule —
live in Kotlin, are exported once, and the render machine consumes the export. A Python
reimplementation of `singularizeBookName` would be a second source of truth for how scripture is
announced. The app computes a `clipId` with the *same* Kotlin code that generated the inventory, so
lookup and generation cannot drift.

### 5A.4 The timing index is untouched — stated explicitly

**Decision D-AUD-E-37 — a heading is a SEPARATE media item, played before its chapter. Verse timings
remain relative to the chapter file, and the truncation guard is unchanged.**

The alternative — prepending the heading audio to the chapter file — would shift every verse offset
by the heading's duration and make the chapter's duration include narration that is not scripture.
That would quietly break the invariant that `endMs[last] == chapterDurationMs`, which is the
single most valuable check in the whole verification gate (§10.3 assertion 5, and now D-AUD-E-35's
per-segment form). **It is not worth trading a truncation guard for a file-count saving.**

So, concretely, and this is the sentence to hold onto: **§10.3's assertion 5 and D-AUD-E-35's
per-segment guard are completely unaffected by the heading layer, because no heading audio is ever
inside a chapter file.** Headings have their own durations in the manifest and need no timing index
at all (each is one short utterance).

Player-side this is free: the media timeline (D-AUD-E-4) already holds one item per chapter, and a
heading is simply another item ahead of it. `activeVerseId` is `null` while a heading plays — which
is already a legal state the reader handles (highlight absent rather than wrong, FR-AUD-11).

### 5A.5 What the gate asserts

Added to §10.3 / §7A.9:

| # | Assertion | Catches |
|---|---|---|
| 21 | **Totality**: every heading any queue can request — over all bundled plans, every canon chapter, and every browse transition — exists in the inventory, and the inventory contains **no unreachable entries** beyond the deliberate windowed-short closure | a missing clip at runtime, i.e. a silent gap before a chapter |
| 22 | The inventory is **byte-identical to a fresh generation** from `BookCatalog` + the plan registry | a hand-edited heading string |
| 23 | Psalms single-chapter entries read "Psalm N", multi-chapter "Psalms"; the 5 single-chapter books carry **no** chapter number; every windowed entry has both forms | a reimplemented or drifted `singularizeBookName` |
| 24 | The Jun 19 / Dec 19 portion yields **full** form for both 2 John and 3 John | the "first chapter only" bug — the exact case the book-change rule exists for |
| 25 | Manifest `headings.inventorySha256` equals the committed inventory's SHA | a pack rendered against a different inventory |

Mutation targets: change the rule to "first chapter only" (→ 24); force plural Psalms (→ 23); drop
the windowed short forms (→ 21); prepend heading audio into a chapter file and confirm the *existing*
truncation guard goes red (→ §10.3 assertion 5, proving D-AUD-E-37 is enforced and not merely
asserted).

---

## 6. Where the per-verse timing index lives (problem 1)

31,219 rows need `(verseId, startMs, endMs)` or there is no highlight (FR-AUD-11), no seek
(FR-AUD-13) and — the hard one — **no Psalm 119 windows** (FR-AUD-6). D-AUD-10 makes an artifact
without timings unshippable, so this is a first-class design question, not a storage detail.

### 6.1 The three candidates, and the trap that turns out not to be the deciding factor

**A table in `bible.db`.** The brief flags the `room_master_table` identity-hash trap
(`8144e1bc57f05006d1a15856ac762552`, sprint-00F). **I checked, and the trap does not bite here**
(V15): `tools/build_bible_db.py` already creates `translation` and `book` as **free-form tables Room
never maps**, and `RoomBibleTextSource.translations()` already reads one of them via a raw
`SimpleSQLiteQuery` on `openHelper.readableDatabase` (Sprint 00N, D-N-1) precisely so the hash stays
untouched. A `verse_timing` table added the same way would be **hash-neutral**. So option A is
*safer than the brief assumes*, and I am recording that correction rather than inheriting a wrong
premise.

It is still the wrong choice, for a reason that has nothing to do with the hash:

> **Timing is a property of the recording, not of the text.** Putting it in `bible.db` couples the
> two artifacts: re-render one chapter of Ezekiel and you must re-commit a 5.7 MB binary, bump
> `BibleAssetVersion.ASSET_CONTENT_VERSION`, ship a base-module release, and make **every** user —
> including the ones with no audio at all — re-copy the whole Bible database on next launch. That is
> a 5.7 MB device-side operation and a `data-rebuild` byte-diff churn to fix a pronunciation. It also
> grows the base module by ~500 KB–1 MB of timings for audio most users will never download, which
> spends the exact budget NFR-AUD-B protects.

**A third read-only Room DB (`audio_timings.db`) in the base module.** Removes the hash question
entirely and is clean. But it keeps the base-module cost and the *same* coupling: correcting audio
still requires shipping a base-module asset. Rejected for the same reason as A, minus the hash.

**Per-book sidecar inside the asset pack.** The timing travels with the audio it describes.

### 6.2 The decision

**Decision D-AUD-E-3 — the per-verse timing index ships as a per-book JSON sidecar *inside the same
asset pack as that book's audio*, and its source of truth is a set of 66 files **committed to git**
at `audio/timings/<usfm>.json` — outside `app/src/main/assets/`, so it is reviewable and gate-able
but contributes **zero bytes to the base module**.**

> **A1 amendment (D-AUD-E-24 ff.):** the *paths* below are now **voice-scoped** —
> `audio/timings/<voiceId>/<USFM>.json` in git, and inside the pack the location is declared by the
> pack's own manifest (`timing.path`) rather than hard-coded (§7A.3). **The reasoning below is
> unchanged and in fact strengthened**: timing is a property of *a recording*, so once a second
> recording is reachable, per-voice scoping is the only correct shape — the plug-and-play
> requirement proves the decision rather than disturbing it.

```
audio_gen/src/main/assets/audio/GEN/            # pack "audio_gen"   [A1: "audio_<voiceId>_gen"]
├── 001.opus … 050.opus                          # 50 chapter files  (not in git)
└── timings.json                                 # copied from audio/timings/<voiceId>/GEN.json (in git)
```

```json
{ "usfm": "GEN", "codec": "opus", "sampleRate": 24000,
  "chapters": [
    { "chapter": 1, "durationMs": 253480,
      "verses": [[1001001, 0, 7320], [1001002, 7320, 15980], … ] } ] }
```

Verse rows are `[canonicalId, startMs, endMs]` triples — the id is the **`VerseId` spine value**, so
the index joins to the text, the reader, the plan and `PortionVerseBridge` with no second key and no
mapping table. Total on disk ≈ 31,219 × ~28 bytes ≈ **~0.9 MB across 66 files** — small enough to
read in a PR, small enough to commit, small enough to load one book at a time into memory (~14 KB
average; Psalms ~70 KB) and cache.

**What this buys, concretely:**

| Property | Sidecar-in-pack | Table in `bible.db` |
|---|---|---|
| Base-module bytes | **0** | +0.5–1 MB for everyone |
| Cost of re-rendering one chapter | one book's pack delta, to users who have that book | new `bible.db` + version bump + 5.7 MB re-copy for **every** user |
| Can a user ever have audio without timings? | **No — same pack, atomic** | Yes (pack present, stale db) — a whole failure mode deleted |
| Can a user have timings without audio? | Yes, harmlessly (git copy is build-side only) | Yes |
| Offline CI gate over the index with no audio present | **Yes** — the git copy (§10.3) | Yes |
| Hash / `data-rebuild` churn | none | none (free-form table) but asset re-commit each render |

**The converse rule, stated so it is enforced:** because a pack is wiped and re-fetched
independently of the app's own storage, **no user data may live in a pack**, and the app must
tolerate `timingsFor()` returning `null` at any moment (pack evicted mid-session) by falling back to
`AudioAvailability.DeviceVoice` and **suppressing the highlight rather than guessing** (FR-AUD-11's
"correct or absent").

---

## 7. Play Asset Delivery: pack design and lifecycle (problem 4)

### 7.1 Constraints, verified

- ≤ **1.5 GB per asset pack**; base module ≤ **200 MB** compressed; ≤ **4 GB** cumulative per device
  (V13). Our whole corpus (~850 MB) fits comfortably under all three — **one pack would technically
  be legal.** The constraint that actually shapes the design is Maya's product rule: *a user must
  never be required to take ~850 MB to hear today's chapter* (PRD §8).
- I could **not** find a first-party statement of a maximum *asset-pack count* (the widely-cited
  "100" is the recommended maximum number of **feature modules**, which is a different thing). I am
  therefore treating a high pack count as **unproven**, and designing so that being wrong is cheap.

### 7.2 Sizes, computed from our own corpus

Derived by running `MarkupStripper`'s rules over the shipped `bible.db` (V14) and pricing at Opus
24 kbps mono (3,000 B/s) with the implied 14.46 chars/s speaking rate that yields the PRD's ~79 h:

| Unit | Chars | ≈ Size @24 kbps | ≈ Duration |
|---|---|---|---|
| Whole Bible | 4,112,530 | **~853 MB** | ~79 h |
| Old Testament (1–39) | 3,171,012 | ~658 MB | ~61 h |
| New Testament (40–66) | 941,518 | ~195 MB | ~18 h |
| Largest book — Psalms | 226,520 | ~47 MB | ~4.4 h |
| Next four — Jeremiah / Ezekiel / Genesis / Isaiah | — | ~46 / 42 / 41 / 40 MB | — |
| Median book | — | ~8 MB | — |
| Smallest — 2 John | 1,540 | **~0.32 MB** | ~2 min |
| Average chapter | 3,459 | ~0.72 MB | ~4 min |

Every book is far under the 1.5 GB per-pack ceiling; the *largest* pack is ~47 MB, which is also
comfortably under Play's 200 MB cellular-consent threshold — meaning a single-book download will
**never** trigger `WAITING_FOR_WIFI` on its own. Our own Wi-Fi-only preference therefore has to be
app-enforced (§7.4); Play will not do it for us at this size. That is a real finding and it changes
FR-AUD-20's implementation from "rely on Play" to "gate the `requestFetch` ourselves."

### 7.3 The granularity decision

**Decision D-AUD-E-5 — 66 on-demand asset packs, one per book, named `audio_<usfm lowercased>`,
**generated** from `BookCatalog` (never hand-authored); every mapping from a verse range to a pack
goes through the single pure function `AudioPackPlan.packsFor(...)`.**

> **A1 — PARTLY SUPERSEDED by D-AUD-E-27.** The *pack-count and naming* half of this decision is
> demoted from an app-level decision to **a property the KJV/ElevenLabs voice's manifest declares**:
> pack names gain a voice axis (`audio_<voiceId>_<usfm>`), and **the app no longer knows the number
> 66** — it asks the manifest which pack holds a chapter (§7A.5). The *reasoning* below (why the
> book is the right default unit, and why being wrong must be cheap) is unchanged and is now the
> justification for the **default** `packing` a voice declares. See §7A.5 for what this costs.

Rationale:

- **The book is the finest unit that composes for both play contexts.** The Bible tab browses *any*
  chapter, so a plan-window grouping would leave holes; per-chapter packs (1,189 modules) are
  absurd; testament-level packs (~658 MB) violate the product rule outright.
- **It matches the user's model and the app's existing one-catalog discipline.** `BookCatalog` is
  already the single home of book structure (D-S9-1, D-S13-1, the picker-grid sprint). Generating
  the modules and the pack names from `book_catalog_export.json` means there is **no second book
  table** and no possibility of a pack name drifting from a `usfmCode`.
- **Being wrong is cheap** — and this is the part that makes 66 an acceptable bet on an unverified
  count limit. Every request in the app is `AudioPackPlan.packsFor(range)`; re-grouping to 8 packs
  (Pentateuch / History / Wisdom / Major Prophets / Minor Prophets / Gospels+Acts / Epistles /
  Revelation) is a change to *that one function and the module generator*. **No audio file changes,
  no timing file changes, no app logic changes, no re-render.** The fallback is a build-config edit.

**Ticket `AUD-C-1` (gating, before any render is commissioned):** upload a bundle with all 66 packs
containing **placeholder** payloads to the Play **internal** track and confirm Play accepts it,
`requestFetch` works, and `assetsPath()` resolves. This is the same "prove the plumbing before you
spend the money" discipline as R-AUD-3's pronunciation pilot, applied to the delivery mechanism. It
also front-loads R-AUD-10 (new Play review surface).

### 7.4 Lifecycle, and the five edges that must be designed

`AudioDownloadRepository` is the only place the app touches `AssetPackManager`:

```kotlin
interface AudioDownloadRepository {
    val states: Flow<Map<String, PackState>>            // name -> Downloaded(bytes) | Downloading(pct) | NotDownloaded | Failed
    suspend fun sizeOf(packs: Set<String>): Long        // AssetPackStates.totalBytesToDownload() — Play's truth
    suspend fun request(packs: Set<String>, allowCellular: Boolean)
    suspend fun cancel(packs: Set<String>)
    suspend fun remove(packs: Set<String>)
    suspend fun locate(pack: String): File?             // getPackLocation()!!.assetsPath(); NEVER cached across launches
}
```

1. **Sizes are Play's, not ours (FR-AUD-20 "states its size before starting").** We show
   `AssetPackStates.totalBytesToDownload()`, not a number from a committed table. It is the truthful
   number, it accounts for what is partially present, and it means the size shown can never drift
   from the size charged. Cost: one Play round-trip before the confirmation sheet; the sheet shows a
   brief "checking size…" state.
2. **Wi-Fi-only is ours to enforce (§7.2).** Our packs are all < 200 MB so Play's own
   `WAITING_FOR_WIFI` gate will not fire. `request()` therefore checks
   `ConnectivityManager.getNetworkCapabilities(...).hasTransport(TRANSPORT_WIFI)` (or
   `!isActiveNetworkMetered`) and, when the preference is on and the network is metered, refuses
   and surfaces the "use cellular for this download?" choice. **We still call
   `showConfirmationDialog()` when Play reports `WAITING_FOR_WIFI` or
   `REQUIRES_USER_CONFIRMATION`** — the latter is how a sideloaded install manifests, and it must
   not be swallowed.
3. **Locations are re-resolved every launch, never cached** (Play's explicit instruction).
   `PackFileLocator` holds a per-process memo keyed by `Application` start, nothing durable.
4. **Eviction is a normal state, not an error.** `locate()` returning `null` for a pack the user
   downloaded ⇒ `AudioAvailability.DeviceVoice` + a plain "this download is no longer on the device"
   with a re-download action. Never a crash, never silence (R-AUD-5, FR-AUD-21).
5. **Non-Play installs.** `AssetPackManager` on a sideloaded build reports
   `REQUIRES_USER_CONFIRMATION` / fails; `AssetPackAvailability` resolves once per process to
   `PlayUnavailable` and the Settings → Audio surface shows one honest sentence instead of a dead
   button. Device voice unaffected (FR-AUD-22, U-AUD-7).

### 7.5 What "audio updates ride a release" actually costs (D-AUD-3, priced)

Play's documented behaviour, quoted (V12): during an app update, "**All previously-downloaded asset
packs are invalidated**," then "the patch for the assets is copied and applied to assets stored in
the app's internal storage," and "applying the patch is a local, offline action."

So the honest reading is **better than "re-download 850 MB every release," and worse than "free":**

- If a release changes **no** audio, the asset patch for unchanged packs is ~empty. The user's
  downloads survive. Good.
- If a release changes **one book's** audio, the patch for *that* pack is downloaded — **as part of
  the automatic app update, without a consent prompt**, because it rides the app update rather than
  a `requestFetch`. A re-rendered Ezekiel is ~42 MB pushed silently to every user who had Ezekiel.
- Local patch *application* needs temporary disk headroom on top of the pack size.

**Consequences I am imposing as build rules (D-AUD-E-19):**

1. **Audio content never changes in a PATCH release.** Audio corrections batch into a MINOR release
   with a whatsnew line that says so. This keeps D-AUD-11's "nothing arrives unasked" spirit intact
   even though the mechanism is outside our control.
2. **Per-book packs are load-bearing here too** — they bound the silent delta to one book instead of
   one testament.
3. **`AUD-C-1` measures it**: on the internal track, change one placeholder pack, publish, and
   observe the actual update download size on a device that has packs installed. If the observed
   behaviour is a full re-download rather than a patch, D-AUD-3 needs re-discussion with the owner
   *before* the render is commissioned, not after.

---

## 7A. Plug-and-play packs — manifest, registry, resolution (NEW OWNER REQUIREMENT, A1)

> *"The audio should be plug and play, similar to the translations. If they download different
> packs, the audio assets just get plugged in. There shouldn't need to be logic that is dependent on
> which asset is used."* — the owner, 2026-07-25.

### 7A.1 Reading the requirement precisely

The owner is asking for the property this codebase has already demonstrated twice, and the bar is
set by what those two precedents actually achieved:

- **Plans (D-ALT-1/2/3, Alt Sprints A–F).** A plan **declares its own shape** in its head block
  (`planId`/`name`/`anchoring`/`dayCount`/`streams[]`); `ReadingPlanAssetLoader` validates *against
  the declared descriptor* rather than against `365`/`listOf(1,2,3)`; Sprint C generalized every
  surface to N streams. Consequence, and the bar: **the Chronological plan (N=1) shipped with zero
  production code change.**
- **Translations (D-N-1/2/3).** The reader's version label is read from the artifact's own
  `translation` table, never a literal; `ReaderVersionSelector` renders a **static title for one
  version and a dropdown for more than one** — the multi-artifact branch is *built and tested but
  unexercised* while only KJV exists.

So "plug and play" decomposes into four testable properties, and I am adopting them as the
acceptance bar for this section:

| P | Property | Precedent |
|---|---|---|
| **P1** | An artifact **declares its own shape**; the app validates against the declaration, never against a constant | D-ALT-2 |
| **P2** | Adding an artifact is **data + an asset pack**, never a code change | Chronological, zero code |
| **P3** | The **multi-artifact branch exists and is tested from day one**, and stays invisible while there is one | D-N-3 |
| **P4** | An artifact that does not fit the declared contract **fails loudly and cleanly**, never silently degrades or is half-played | `anchoring != "DATE"` clean-fail; `BibleProvider.fromStored` degrade-to-default |

### 7A.2 The one place a code branch may legitimately remain — stated honestly

"No logic dependent on which asset is used" is achievable for **everything the player consumes as
data**: file paths, codec, bitrate, sample rate, coverage, pack layout, display name, language,
priority. It is **not** achievable for a genuinely *new encoding of the timing index*, because a new
encoding needs a parser and a parser is code.

The honest boundary, and it is exactly the plan loader's:

> **Data may vary freely within a declared contract. A *new contract* requires code — and the pack
> must say which contract it speaks, so the app can refuse cleanly instead of guessing.**

Hence `timing.format` and `manifestVersion` are **closed sets** the app publishes
(`SUPPORTED_TIMING_FORMATS = setOf("verse-ms-v1")`, `SUPPORTED_MANIFEST_VERSIONS = 1..1`), and a
pack declaring anything outside them is *unusable and says so* — never parsed on a hopeful guess.
This is the same clean-fail the resolver, the plan loader and `BibleProvider.fromStored` all use,
and it is what makes P4 true.

### 7A.3 The manifest — `voice.json`, inside every pack

```json
{
  "manifestVersion": 1,
  "voiceId": "kjv_en_standard",
  "displayName": "Standard voice",
  "shortCode": "STD",
  "language": "en",
  "textVersion": "KJV",
  "corpusVersion": 1,
  "packing": "per-book",
  "packNamePattern": "audio_{voiceId}_{usfm_lower}",
  "coverage": { "GEN": [1, 50], "EXO": [1, 40], "…": [] },
  "audio": {
    "format": "opus-ogg-v1",
    "codec": "opus", "container": "ogg",
    "bitrateKbps": 24, "sampleRate": 24000, "channels": 1,
    "pathTemplate": "audio/{USFM}/{CHAPTER:03}.opus"
  },
  "timing": { "format": "verse-ms-v1", "pathTemplate": "audio/{USFM}/timings.json" }
}
```

Field-by-field, this is `PlanDescriptor` for audio:

| Field | Plays the role of | Validated how |
|---|---|---|
| `manifestVersion` | `schemaVersion` | ∈ `SUPPORTED_MANIFEST_VERSIONS`, else the pack is unusable (§7A.6) |
| `voiceId` | `planId` | **triple anti-drift**: `voiceId` == catalog entry id == pack-name segment (the D-ALT-2 `planId`==registry-id==directory check) |
| `displayName` / `shortCode` | `name` / the `translation` row's `name`/`code` | non-blank; `shortCode` ≤ 6 chars. `shortCode` is the compact label, `displayName` the spoken one (D-N-2's exact split) |
| `textVersion` | — (new) | must match a `code` in `bible.db`'s `translation` table, so a narration can never be silently paired with the wrong text |
| `coverage` | `streams[]`/`dayCount` — *the declared shape* | every key ∈ `BookCatalog.usfmCode`; every range within that book's `chapterCount`; **both directions** against the timing index (§7A.7) |
| `packing` + `packNamePattern` | — (new) | the pack layout, **declared not assumed** — this is what retires the hardcoded "66" |
| `audio.*` | — | `format` ∈ the supported set; the rest is handed to ExoPlayer as data |
| `timing.format` | `anchoring` | ∈ `SUPPORTED_TIMING_FORMATS`, else clean-fail |

`coverage` is deliberately **book → chapter range**, not a chapter list: it is compact, it is
diffable, and partial coverage ("NT only") is expressible without inventing a second notion.

### 7A.4 Where the manifest lives — the answer is *both*, with divided authority

**Decision D-AUD-E-25 — two artifacts, one authority each:**

| | `assets/audio/catalog.json` (**base module**, ~2 KB) | `voice.json` (**inside each pack**) |
|---|---|---|
| Answers | "what *could* I download?" | "what *is* installed, and how do I play it?" |
| Contains | per voice: `voiceId`, `displayName`, `shortCode`, `packing`, `packNamePattern`, the pack-name list, approximate coverage. *(A2: the `order`/priority field is **cut** — D-AUD-E-29 leaves nothing to prioritise; catalog order is display order in the selector only)* | the full manifest of §7A.3 |
| Used by | the **download menu only** | **all playback decisions** |
| Never used for | playback, resolution, path building | enumerating what is downloadable |
| Cost when the pack is **missing** | still works — the menu is complete | absent, and that *is* the "not installed" signal |
| Cost when the pack is **installed** | ignored for playback | the single source of truth |
| Cost when the pack is **stale/newer** | may not list the voice at all (a later app's pack) | `manifestVersion` guard decides (§7A.6) |

**Why not one or the other.** A committed index *alone* cannot describe a pack shipped by a *later*
app version, and it would drift the moment a pack is patched — precisely the "registry typo
contradicts the asset" failure D-ALT-1 designed against, which is why the plans' heavy descriptor
lives in the plan's own head. A pack-only manifest *alone* cannot power a download menu for
something not yet installed — you cannot read a manifest out of bytes the user does not have. So:
the thin enumerator is committed (exactly as `plans/registry.json` is thin), the heavy
self-describing descriptor travels with the bytes, and the **anti-drift id check** is what keeps
them honest. That check is not decorative: on mismatch the pack is treated as **not installed**,
logged, and surfaced in Settings → Audio with an honest line.

Discovery is therefore **runtime**, unlike plans — and that is the one genuine structural difference
from the precedent, forced by the fact that packs arrive after the install:

```kotlin
@Singleton
class AudioPackRegistry @Inject constructor(
    private val packs: AudioDownloadRepository,      // AssetPackManager behind the seam
    private val catalog: AudioCatalogSource,         // assets/audio/catalog.json, memoized
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** Voices offerable for download (menu only). */
    suspend fun offerable(): List<CatalogVoice>
    /** Voices actually installed AND usable — manifest parsed, version+format supported, ids agree. */
    suspend fun installed(): List<InstalledVoice>   // (manifest, packName -> File)
    /** Packs present but rejected, with the reason — surfaced honestly, never swallowed. */
    suspend fun rejected(): List<RejectedPack>
}
```

Re-resolved every launch, never cached durably — Play's explicit instruction, already §7.4 edge 3.

### 7A.5 Interaction with pack granularity and `AudioPackPlan`

**Decision D-AUD-E-27 — `AudioPackPlan` reads the pack layout from the manifest (installed) or the
catalog entry (not yet installed); the app contains no `66`, no per-book assumption, and no book
list of its own.**

```kotlin
// was: fun packsFor(range: VerseRange): Set<String>            // implied per-book
fun packsFor(voice: VoiceLayout, range: VerseRange): Set<String>  // VoiceLayout = packing + pattern + coverage
```

`VoiceLayout` comes from data on both paths, so a voice shipped as **one 850 MB pack**, as **8
section packs**, or as **66 per-book packs** is a manifest difference and nothing else. This is the
single change that most directly delivers P2, and it costs one parameter.

**What it costs, stated plainly:**

- **Pack count is now per voice.** 66 packs × 2 voices = 132. I could not find a first-party
  statement of a maximum asset-pack count (§7.1), so **a second voice doubles an already-unverified
  budget**. This materially raises **RE-AUD-2** and it changes what `AUD-C-1` must measure: the
  placeholder internal-track upload now has to establish the *ceiling*, not just that 66 works.
  It also strengthens the case for the 8-section fallback (8 × voices = 16 for two voices), which is
  now expressible **as data** — a second voice may declare `"packing": "sections"` while the first
  keeps `"per-book"`, with no code change. That is a genuinely nice property that only exists
  because of this requirement.
- **Timing sidecars are voice-scoped** (`audio/timings/<voiceId>/…`), so the committed index grows
  ~0.9 MB per voice. Fine.
- **The corpus artifact tag is voice-scoped**: `audio-corpus-<voiceId>-v<N>` (§8.2 otherwise
  unchanged).

### 7A.6 Schema versioning and forward compatibility

**Decision D-AUD-E-26 — `manifestVersion` and `timing.format`/`audio.format` are closed sets;
within a `manifestVersion`, changes are additive only.** The two directions, both required:

- **A pack built by a LATER app arriving on an OLDER app** (`manifestVersion` above the supported
  range): the pack is **rejected cleanly** — "this voice needs a newer version of the app" — and
  resolution falls through to the next installed voice, then to device TTS (§7A.7). It is **never**
  parsed optimistically. Note that Play's update mechanics make this unlikely-but-reachable: packs
  are invalidated and patched *during* the app update (§7.5), so there is a window, and mid-update
  is exactly when a half-applied newer pack could be observed. "Unlikely" is not a guard.
- **A pack built by an EARLIER app arriving on a NEWER app**: supported for the life of that
  `manifestVersion`. New fields are additive and optional; the app supports a *set* of versions, not
  a single value. This is D-ALT-3's "the v2 body is a strict subset of v3" rule, restated for audio.
- A breaking change bumps `manifestVersion` and both versions are supported for at least one release
  — which, given D-AUD-E-19 (audio never changes in a PATCH), is a comfortable window.

### 7A.7 Partial coverage and mixed packs — the resolution rule

> #### ⛔ D-AUD-E-24 — SUPERSEDED by D-AUD-E-29 (owner ruling, 2026-07-25, amendment A2)
>
> *Recorded, not deleted, because the reasoning trail matters.* I originally specified per-play-unit
> resolution: preferred voice if it covers the unit, else the highest-priority installed voice that
> covers it, else device TTS — with a catalog `order` field as the tie-break. **The owner ruled this
> out, and he is right on a point I got wrong:** I treated "which voice do we play?" as an
> *optimisation* (get the best available coverage) when it is a *correctness* question (the user
> chose a voice and must not be handed a different one without being told). A preferred-voice
> ordering is a silent substitution mechanism, however deterministic. The `order` field, the
> cross-voice fallback, and `ResolveVoiceForUnitUseCase` are all **cut**.

**Owner ruling, verbatim (2026-07-25):** *"As far as mixed voices - a single pack should be selected
for the whole app context. So even if they've downloaded different assets - the app should read in
the voice/pack they've selected. So, if they download multiple packs, there should be a
dropdown/settings in the settings that let them select which one to use, and the whole app points to
that asset pack. And if a chapter asset is missing for that pack, they're prompted to download it,
regardless of whether there is an asset/pack for that chapter from a different voice pack."*

**Decision D-AUD-E-29 — voice selection is app-wide and exclusive. There is exactly one *active
voice* for the whole app. Coverage is only ever evaluated against the active voice. A chapter the
active voice does not have installed produces a **prompt to download that voice's pack** — never a
cross-voice substitution, even when another installed voice covers it.**

This is not a preference and it is not an availability heuristic; it is the same class of rule as
"the app renders the *active plan's* actual shape" (D-ALT-17). Note how much it collapses:

- **`ResolveVoiceForUnitUseCase` is deleted before it is written.** There is no resolution
  algorithm — there is a *selection*, held in one place, read everywhere.
- **Cross-voice coverage arithmetic disappears.** Nothing ever asks "does any installed voice cover
  this?"; the only question is "does *the active voice* cover this?", which is a lookup in one
  manifest's `coverage`.
- **The mixed-pack state stops being a state.** A user with voice A for Genesis and voice B for
  Psalms has one active voice and a gap; the gap is a download prompt, not a fallback.

**The distinction that carries the whole rule: substitution is forbidden, offering is required.**
When the active voice lacks a chapter the app must not quietly sound different — but it may
absolutely *offer* the alternatives, because an offer is a choice the user makes with their eyes
open. So the missing-chapter prompt states the passage, the voice, and the size, and offers:
**"Download <size>"** · **"Read it with the device voice"** (an explicit one-tap election, not a
substitution) · **Cancel**. That satisfies U-AUD-7 and FR-AUD-22 without violating the ruling.
The rule the code enforces is precise: *no code path may bind a voice other than the active one
without a user action naming it.*

**`ActiveVoiceRepository` — mirroring `ActivePlanRepository` exactly (D-ALT-16/17):**

```kotlin
interface ActiveVoiceRepository {
    val activeVoiceId: Flow<String>        // absent ⇒ DEVICE_VOICE_ID; unknown/uninstalled ⇒ DEVICE_VOICE_ID
    val activeVoice: Flow<ActiveVoice>     // the resolved, usable descriptor (device or an installed pack voice)
    suspend fun setActiveVoiceId(id: String)
}
```

`AudioReadingController` and the entry-point use cases `combine` `activeVoice` into their flows, so
a voice switch re-emits everything live — the D-ALT-17 property, reused rather than reinvented.

### 7A.7a The device voice is a registry entry (`device_tts`)

**Decision D-AUD-E-30 — the Phase-1 device voice is a first-class entry in the same voice registry,
with id `device_tts`, and it is the default active voice. It is always "installed" and always
covers the whole canon.**

The coordinator proposed this and I agree — and there are three engineering reasons beyond the
product tidiness, one of which changes the sprint plan:

1. **It gives "nothing downloaded" a principled home.** Under D-AUD-E-29 the device voice must be
   *selected*, never silently substituted. Making it a registry entry is the only way to say that
   coherently: the default user has actively-by-default got `device_tts` selected, so nothing is
   being substituted for anything.
2. **It collapses the degradation ladder into selection.** `ResolveAudioAvailabilityUseCase`
   (D-AUD-E-16) stops being "downloaded → device → message" and becomes "the active voice, or an
   honest prompt." One concept instead of two. The only genuine unavailability left is *the device
   has no usable TTS engine and the active voice is `device_tts`* — a real state (R-AUD-7) with a
   real message.
3. **It forces the plug-and-play seam to exist in Phase 1, which is where it should be tested.**
   With `device_tts` in the registry, Phase 1 ships with a registry, a manifest contract, an active-
   voice key, and the D-N-3 one-vs-many selector — all exercised by real code from day one, with
   **zero audio bytes and zero PAD dependency**. Phase 2 is then literally "a second entry appears."
   That is a materially stronger test position than building the seam alongside the first downloaded
   pack, and **it moves the §7A machinery out of Sprint AUD-C into AUD-A/AUD-B** (§18, amended).

`device_tts` is a **synthetic manifest**, constructed in code, not a file — it has no pack, no bytes
and no `pathTemplate`. It declares `coverage` = every book at full chapter range, `timing.format` =
`"utterance-callback-v1"` (the Phase-1 boundary mechanism, §4.3), and `audio.format` = `"device-tts"`.
That keeps it inside the *same* contract rather than beside it, so the selector, the gate and the
resolution path have exactly one shape to handle. The only place it is special is that
`AudioPackPlan` is never asked about it.

### 7A.7b Switching voice mid-playback

**Decision D-AUD-E-31 — a voice switch **stops playback**, and the controller retains the queue and
`activeVerseId` so the next press resumes the same passage **from the current verse** in the new
voice.**

The three candidates and why this one:

| Option | Rejected because |
|---|---|
| Keep playing in the old voice, apply on next play | Directly contradicts "the whole app points to that asset pack" — the app would be audibly on a voice the Settings screen says is not selected |
| Seamlessly restart at the current verse | The new voice may not cover the current chapter, so this can *require a download prompt raised from the Settings screen* mid-listen — a bad place for it, and a rule violation if we substituted instead |
| **Stop, retain position, resume on next press** | ✅ No half-state, no cross-voice moment, no prompt from Settings. Coverage is evaluated where it belongs — at the next press, from the transport, where the download prompt has a natural home |

So the switch is honest and cheap, and "resume where I was" (FR-AUD-25, already in-session state)
does the work. Practically: the user is in Settings, not listening intently, and one tap on the
transport puts them back.

### 7A.7c Storage, deletion, and what happens to the selection

**Decision D-AUD-E-32 — downloads are grouped **by voice** in Settings → Audio; "Delete this voice"
removes all of that voice's packs in one action; a **partially** deleted active voice stays active
(and prompts per missing chapter, per D-AUD-E-29); a **fully** uninstalled active voice reverts the
selection to `device_tts` with one honest notice.**

- **Duplication is real and must be visible.** Two voices covering the same books cost twice —
  ~853 MB each at 24 kbps (§7.2). Under an exclusive-selection rule a second voice is *pure
  duplication*, which is exactly why the storage surface must show per-voice totals, not one lump.
  NFR-AUD-E's "always reports its true on-disk usage" now means *per voice*.
- **Partial deletion is not an error state.** It is the ordinary consequence of per-book packs, and
  the owner's ruling already says what happens: prompt for that voice's pack.
- **Full uninstall is the `unknown id ⇒ default` rule** from `ActivePlanRepository` /
  `BibleProvider.fromStored`, applied to "no longer installed". Reverting to `device_tts` is a
  *substitution*, so it is **not silent**: one notice, once, stating that the voice's downloads were
  removed and the device voice is now selected. The stored id is **not** rewritten to `device_tts`
  — the same indistinguishability discipline as D-S14-1 — so re-downloading the voice restores the
  user's original choice without them re-picking it. `activeVoiceId` degrades on *read*, not on
  *write*.

### 7A.7d Persistence

**Decision D-AUD-E-33 — `active_voice_id`, a string key in the existing `SettingsRepository`
DataStore. Absent ⇒ `device_tts`. A stored id that is not a known, installed, usable voice ⇒
`device_tts` on read, without rewriting the stored value.** This is the `selected_plan` key
(D-ALT-16) and the `bible_provider` posture, mirrored field for field. No new DataStore file, no
Room change.

**Supersedes** §13.3's `audio_voice_source` (A1's `audio_preferred_voice_id` is also cut — there is
no "preferred" voice now, only an active one).

### 7A.8 What this invalidates in the rest of this spec — and what it validates

**Changed** (all marked in place): D-AUD-E-5's pack-count half (§7.3 → D-AUD-E-27); D-AUD-E-3's
timing paths (§6.2, voice-scoped); §13.3's `audio_voice_source` key → **`active_voice_id`**
(A2, D-AUD-E-33); §9's total-bundle ceiling is now per-voice-aware (§9.2 note); the corpus tag is
voice-scoped (§8.2). **A2 additionally cuts** `ResolveVoiceForUnitUseCase` and the catalog `order`
field before either was written, and **folds `ResolveAudioAvailabilityUseCase` (D-AUD-E-16) down to
"the active voice, or an honest prompt"** (§7A.7a) — a simplification, not a rewrite.

**Not changed, and this is the interesting part:** **§4 (the player) and §5 (the queue) are
untouched.** Voice resolution happens *above* seam 2 and produces a `VoiceBinding` (resolved file
paths + a `VerseTimingSource`) that seam 2 consumes. `FileVersePlayer` never learns which voice it
is playing; `AudioQueue` never learns that voices exist; `AudioReadingController` takes a
`VoiceBinding` the way it already takes a queue. The owner's requirement therefore **validated the
two-seam design instead of breaking it** — which is the outcome you want when a new requirement
lands on an architecture, and it is worth recording as evidence that seam 2 was drawn in the right
place.

One thing the requirement genuinely *adds* rather than changes: `ResolveAudioAvailabilityUseCase`
(D-AUD-E-16) grows from a 3-rung ladder to a 3-rung ladder **whose top rung is itself resolved by
D-AUD-E-24**. Same single home, one more pure input.

### 7A.9 What the offline gate must assert about a manifest

Added to `AudioTimingVerificationTest` (§10.3), all offline, all with zero audio bytes:

| # | Assertion | Catches |
|---|---|---|
| 11 | Every catalog voice has a committed manifest fixture; **`voiceId` == catalog id == pack-name segment == timings directory** (all four) | the anti-drift failure D-ALT-2's `planId` check exists for |
| 12 | `manifestVersion` ∈ supported; `timing.format` and `audio.format` ∈ supported | a pack the app cannot honestly play being shipped anyway |
| 13 | `textVersion` matches a `code` in `bible.db`'s `translation` table | a narration paired with the wrong text |
| 14 | **Coverage ↔ timing index, both directions**: every declared-covered `(book, chapter)` has a timing entry, and every timing entry is declared covered | the "declared 66 books, rendered 65" silent hole — the superscription-CSV both-directions idiom |
| 15 | `audio.pathTemplate` renders to a **unique** path per covered chapter, and every rendered path appears in `audio_manifest.json` | a template typo, a collision, a missing file |
| 16 | Every covered chapter resolves through `AudioPackPlan.packsFor` to **exactly one** pack, and every declared pack name is non-empty and unique | a pack with no chapters, a chapter in two packs |
| 17 | **A2 — exclusivity (the correctness rule).** With a synthetic **two**-voice fixture (test-only, zero bytes) where voice A covers Genesis and voice B covers Psalms: with A active, a Psalms unit resolves to **A-with-a-missing-chapter ⇒ prompt**, and **never** to B. Asserted as a *negative*: no binding produced by any entry point ever names a voice other than `activeVoiceId`, for every (active voice × requested passage) pair in the fixture matrix | **D-AUD-E-29** — the silent-substitution failure. This is the load-bearing new assertion |
| 18 | **A2 — `device_tts` is a registry entry**: it appears in `installed()` with full canon coverage on a device with an engine, is the value of `activeVoiceId` when the key is absent, and is never asked of `AudioPackPlan` | D-AUD-E-30 regressing to a special-cased side path |
| 19 | **A2 — degradation on read, not on write**: a stored `active_voice_id` naming an uninstalled voice reads as `device_tts` **while the stored value is unchanged**; re-installing that voice restores it as active without a user re-selection | D-AUD-E-32/33 — the D-S14-1 indistinguishability discipline |
| 20 | **A2 — the D-N-3 branch**: one usable voice ⇒ the selector renders static text and reports no chooser; two ⇒ a chooser listing exactly the installed usable voices plus `device_tts`; an unknown-`manifestVersion` pack is excluded from both | P3 — multi-voice machinery proven while invisible |

Mutation targets: flip one coverage range by one chapter (→ 14); point `pathTemplate` at a
non-existent file (→ 15); make `packsFor` return two packs for one chapter (→ 16); **make the
binding fall through to another installed voice when the active one lacks a chapter (→ 17 — the
mutation that matters most, because it is the exact bug the owner ruled out)**; drop `device_tts`
from the registry (→ 18); rewrite the stored id on degradation (→ 19); set the fixture voice's
`manifestVersion` to 99 and assert it is rejected rather than used (→ 20).

### 7A.10 The voice selector UI — the D-N-3 idiom, literally

**Decision D-AUD-E-28 — `AudioVoiceSelector` follows `ReaderVersionSelector`'s shape exactly: 0 or 1
usable voice ⇒ **no chooser** (Settings → Audio shows the voice as static text: "Voice: Device
voice"); more than one ⇒ a `SettingsDropdownRow` chooser (the S14 idiom) writing the active-voice
key. The multi-voice branch is built and tested from day one and is unexercised in production while
there is one voice.**

> **A2 amendment:** the key written is **`active_voice_id`** (D-AUD-E-33), not
> `audio_preferred_voice_id` — the selector picks *the* voice, app-wide, not a preference among
> fallbacks. And because `device_tts` is a registry entry (D-AUD-E-30), the **Phase-1 app already
> has exactly one voice**, so the static-text branch is live in production from Phase 1 and the
> chooser branch is real code exercised by the two-voice test fixture. This is the D-N-3 situation
> reproduced precisely: one artifact today, the many-artifact branch built and tested, invisible
> until a second arrives.

Tags: `audio-voice-row`, `audio-voice-dropdown`, `audio-voice-option-<voiceId>` — mirroring
`theme-dropdown`/`provider-dropdown`/`reader-version-dropdown`. The static branch is **not** a
disabled control (the S14 teaser idiom is for something deliberately unavailable; one voice is not
unavailable, it is simply the only one), and TalkBack hears `displayName`, never `shortCode` — D-N-2
verbatim.

---

## 8. Where ~850 MB of build artifacts live (problem 2)

This is the decision most likely to be wrong, so it gets the most analysis. The constraint set:
asset-pack modules need their files present at `bundleRelease` time; CI builds `bundleRelease` on
**every push and PR** today; the owner's objection is to *runtime hosting operations*, and a
build-time artifact store is a different thing but is still ops and must be priced honestly.

### 8.1 The options

| Option | Storage | Everyday CI cost | Failure mode | Verdict |
|---|---|---|---|---|
| **Commit blobs to git** | 850 MB in history; a re-render **adds** another 850 MB forever (git never forgets) | every clone pulls it; `actions/checkout` on every job | repo becomes unclonable within two renders; GitHub's 5 GB soft limit breached | **No** |
| **Git LFS** | GitHub LFS free tier is 1 GB storage **and** 1 GB bandwidth/month — blown by a single fetch. Paid packs $5/50 GB/mo, and *bandwidth* is charged per CI fetch | every `release-bundle` job pulls 850 MB | recurring bill scaling with CI runs; the V3 spec already said "no LFS" for a 5.7 MB file | **No** |
| **Third-party build-time store** (GCS/S3) | fine technically | fine | a new vendor, a new account, a new secret, a new bill, a new thing the owner maintains | **No — smallest-ops principle** |
| **GitHub Release assets on this repo** | ≤ 2 GB per file, unlimited assets, free bandwidth; no new vendor, no new account, `gh` already in use | **zero** on PRs (§9) | assets are mutable by anyone with push rights — mitigated by checksum pinning | **Yes** |
| Assemble packs only on release runs | (orthogonal — combined with the above) | — | — | **Yes, combined** |

### 8.2 The decision

**Decision D-AUD-E-6 — audio blobs never enter git. They are published as **GitHub Release assets**
on a dedicated, non-app tag (`audio-corpus-v<N>`), one tarball per book, and every one of them is
**pinned by SHA-256 in the committed `audio/audio_manifest.json`**. CI fetches them **only** on
release runs; PR/push CI builds an audio-less bundle and never touches them.**

```
tag audio-corpus-v1
├── GEN.tar   (sha256 in audio_manifest.json)   ~41 MB
├── EXO.tar   …
└── REV.tar                                      66 assets, ~853 MB total
```

The split that makes this work, and that mirrors this repo exactly:

> **The small, reviewable, checkable artifact is in git. The large opaque blob is not — and is
> pinned by checksum *from* git.** `audio/timings/*.json` (~0.9 MB, human-readable, diffable) and
> `audio/audio_manifest.json` (1,189 rows of sha256/bytes/durationMs/WER) are committed and are what
> the offline gate reads. The `.opus` files are content-addressed by that manifest. A tampered or
> truncated blob fails `AUD-VERIFY` in CI; a hand-edited timing file fails the offline gate. Neither
> can sneak through, which is precisely the property the `data-rebuild` byte-diff job gives us for
> `bible.db`.

**Honest costs, stated rather than buried:**

- It **is** ops: someone (Jordan) creates the corpus tag, uploads 66 assets, and bumps
  `AUDIO_CORPUS_TAG` in the workflow. It happens once per render, roughly once a year.
- The release job downloads ~853 MB and builds a ~900 MB `.aab`. Expect the `release-bundle`/release
  workflow to go from ~5 min to **~20–30 min**, and its `timeout-minutes: 30` **must be raised**.
- We must **stop uploading the AAB as a GitHub Actions artifact** on release runs (§9.3) — a 900 MB
  artifact per run is real storage spend for no benefit, since the AAB goes straight to Play.
- Release assets are mutable by a maintainer. The checksum pin is the guard, and it is a hard one:
  the workflow verifies every file before the pack modules are populated.

---

## 9. CI, and redefining the bundle-size gate (problem 3)

### 9.1 What the gate was actually protecting

Today: `SIZE=$(stat -c%s app-release.aab); CEILING=12000000`. It protects **install size** — the
D-V3-20 statement that the text asset costs ≤ +6 MB on a ~5.7 MB app. With asset packs, the `.aab`
becomes ~900 MB and the check becomes meaningless *as written*, but the thing it protects is
unchanged and is now expressed by Play as the **base module**.

### 9.2 The decision

**Decision D-AUD-E-7 — split the gate in two, and keep the 12 MB number verbatim on the artifact it
was always about.**

**(a) PR/push CI — unchanged number, audio-less build.** Asset-pack modules are included only when
`-PwithAudio=true`; `settings.gradle.kts` conditionally `include`s `audio-packs/*`. The everyday
`release-bundle` job builds `./gradlew bundleRelease` exactly as today, produces a base-only AAB,
and applies **`CEILING=12000000` unchanged**. The gate keeps its meaning, its number, and its
history. Media3 lands inside it (§13.2 prices it at ~+1.5–2.5 MB against ~3.9 MB of headroom) —
which is the point: *the player must fit the app's existing budget, and CI still says so.*

**(b) Release-only `audio-bundle` job — three new assertions.** On tags, with `-PwithAudio=true`:

```bash
# 1. BASE-MODULE gate — the real successor to the 12 MB check.
#    An .aab is a zip; base module entries are under base/.
BASE=$(unzip -v app-release.aab | awk '$0 ~ / base\// {s+=$1} END {print s+0}')
[ "$BASE" -le 12000000 ] || { echo "::error::base module ${BASE}B exceeds 12 MB"; exit 1; }

# 2. ZERO AUDIO BYTES IN THE BASE (NFR-AUD-B / D-AUD-6, stated as an invariant not a size)
! unzip -l app-release.aab | grep -qE ' base/.*\.(opus|ogg|mp3|m4a)$' \
  || { echo "::error::audio bytes in the base module"; exit 1; }

# 3. TOTAL ceiling — catches a runaway render (e.g. someone renders at 64 kbps)
TOTAL=$(stat -c%s app-release.aab)
[ "$TOTAL" -le 1200000000 ] || { echo "::error::bundle ${TOTAL}B exceeds the 1.2 GB corpus ceiling"; exit 1; }
```

Assertion 2 is the one I care most about: it turns "no audio in the AAB" from a *size heuristic*
into a *structural invariant* that cannot be satisfied by accident.

### 9.3 The other CI changes

| Job | Change |
|---|---|
| `build` | unchanged. `testDebugUnitTest` gains `AudioTimingVerificationTest` (§10.3) — offline, no audio bytes, ~1 s |
| `release-bundle` (PR/push) | unchanged commands, unchanged 12 MB ceiling; now provably audio-less |
| `audio-bundle` (**new**, tags only) | fetch corpus assets → verify SHA-256 vs `audio_manifest.json` → populate pack modules → `bundleRelease -PwithAudio=true` → the three assertions above. `timeout-minutes: 45`. **No artifact upload** |
| `release.yml` | build with `-PwithAudio=true`; raise the timeout; keep the Play upload |
| **not added** | no `audio-rebuild` byte-diff job. **The render is not reproducible and we will not pretend otherwise** — §10 is its replacement |

Also note the standing CI debt (`ci/actions-node24-bump`) — a 30-minute job that suddenly takes 30
minutes of download is a bad time to discover a broken action. Land that PR first.

---

## 10. The render pipeline and its verification gate (problem 6)

### 10.0 The voice source — OQ-AUD-1 RESOLVED (A1)

#### 10.0.1 The decision

**Decision D-AUD-E-20 — the voice source is ElevenLabs.** The owner auditioned the field himself and
chose on **voice realism**. This is his call to make (it is the M-AUD-6 axis) and it is **not
re-litigated here**. Everything downstream in this section is written to that choice.

Convenient side-effect worth noting rather than claiming credit for: it also happens to be the
option my §16 analysis favoured, on FR-AUD-10 grounds — vendor-supplied timestamps versus a
forced-alignment project over ~79 h of volunteer-variable audio. The owner reached the same
destination by a different road.

#### 10.0.2 The model pin — `eleven_flash_v2`

This is where the choice stops being a preference and becomes an engineering constraint, because
**the pronunciation-lexicon requirement narrows the model set to two.**

**Verified (V16):** ElevenLabs phoneme tags — IPA/CMU pronunciation-dictionary *rules* — work on
**only `eleven_v3` and `eleven_flash_v2`**. Every other model degrades to **alias substitution
only** (respelling a word and hoping). For a corpus containing Mahershalalhashbaz,
Chushanrishathaim and Zaphnathpaaneah (R-AUD-3), phoneme-level control is not a nicety; alias
respelling is guesswork at a scale of thousands of proper nouns. **So the field is `eleven_v3` or
`eleven_flash_v2`, and nothing else.**

Between those two, the deciding number is ours, not the vendor's (V17, V18):

| | `eleven_v3` | `eleven_flash_v2` |
|---|---|---|
| Phoneme tags | ✅ | ✅ |
| Chars per request | **5,000** (~5 min) | **30,000** (~30 min) |
| Languages | 70+ | English only |
| **Our 1,189 chapters that exceed the cap** | **208 (17.5%)** | **0** |
| Longest chapter (Ps 119, 12,999 chars) | needs ≥3 requests | **one request** |
| Consequence | ~250+ intra-chapter splice points | **chapter = one atomic render** |

> #### ⛔ D-AUD-E-21 — SUPERSEDED by D-AUD-E-34 (amendment A3, 2026-07-26)
>
> **My objection was built on a premise I did not check: that splitting a chapter means splitting it
> arbitrarily. It does not.** We assemble chapter text *from verses*, so we can split strictly on
> **verse boundaries** — which is where an audio Bible pauses anyway. That defeats most of the
> argument below, and the dollar delta ($250–344 one-time) was never prohibitive. The owner's
> preference for `eleven_v3` stands. See **§10.0.2a** for the verdict, the reworked truncation guard,
> and the residual risks that are real. *(The reasoning is kept because the "one request per chapter"
> property is still genuinely valuable, and is still the reason flash_v2 is the named fallback.)*

**Decision D-AUD-E-21 — pin `model_id = "eleven_flash_v2"`, and record the pin (with voice id and
all synthesis parameters) in `audio_manifest.json` so a re-render is reproducible in configuration
even though it is not reproducible in bytes.**

The reasoning, in one line: **`eleven_flash_v2` is the only phoneme-capable model that renders every
chapter of our corpus in exactly one request**, and one-request-per-chapter is load-bearing three
times over —

1. **No splices.** 208 chapters would otherwise be stitched from 2–3 syntheses, each seam a possible
   click, a prosody discontinuity mid-sentence, and a place for a swallowed word. In a scripture
   reading that is a defect, not an artifact.
2. **The timing index stays one unbroken alignment per chapter**, which is exactly what makes
   §10.3's assertion 5 (`endMs[last] == durationMs`) a meaningful truncation guard. Stitched audio
   would require offsetting and re-basing alignments across chunk boundaries — a new class of
   silent, systematic error precisely where R-AUD-4 says a drifting index is worse than none.
3. **A per-chapter re-render is a single reproducible call** (R-AUD-1's "cheap in dollars even if
   slow in calendar" mitigation), rather than a chunk plan that must be reconstructed identically.

**"English only" is a non-cost here** — the corpus is the KJV — and it is worth saying out loud
because it looks like a downgrade on a spec sheet and is not one for this product.

**If the owner prefers `eleven_v3` on the pilot** (it is the expressiveness model, and expressiveness
is a legitimate thing to prefer for scripture), the cost is named rather than absorbed: sub-chapter
chunking at sentence/verse boundaries, splice-and-stitch machinery in `render_audio.py`, alignment
re-basing across chunks, ~250+ new seams to spot-check, and a longer render. That is a real chunk of
Sprint AUD-D. **It is a decision to put in front of him at the pilot with the price attached, not
one to make for him** — see OQ-AUD-E-6.

#### 10.0.2a A3 — the v3 verdict: **pin `eleven_v3`**, with two pilot gates

> #### A4 (2026-07-26) — D-AUD-E-34's fallback clause is REVOKED; see D-AUD-E-40/41
>
> The owner tested `eleven_multilingual_v2` and `eleven_flash_v2` and **rejected both on output
> quality**: *"I do NOT like the output. So, I'll need to find a way to use V3 regardless. We can
> address those transitions in the generation options."* **`eleven_v3` is mandatory, not preferred.**
> The "named fallback" below **does not exist** — there is no model to fall back *to*, because the
> only two candidates that render a chapter in one request are the two he rejected. RE-AUD-18
> therefore cannot be escaped; it has to be **solved**. And the mitigation I would have reached for
> first is gone: see D-AUD-E-41.

**Decision D-AUD-E-34 — pin `model_id = "eleven_v3"`, subject to two facts the pilot must establish
(§5 of the runbook, Q2 and Q3). ~~`eleven_flash_v2` is the named fallback if either comes back badly,
and that fallback is an *owner* decision, not a render-machine one.~~ (A4: fallback revoked.)**

**Where I was wrong, stated plainly.** D-AUD-E-21 rested on "208 chapters must be split ⇒ ~250 splice
points ⇒ audible seams and a stitched alignment." That is true of *arbitrary* splits. It is not true
of the split we actually get to take: we build chapter text by concatenating verses, so we can pack
**whole verses** into segments. I checked the one thing that could have made this infeasible — the
longest single verse in the KJV is **Esther 8:9 at 529 characters** — so even at a 2,000-char cap
**no verse ever needs an intra-verse split.** Verse-boundary segmentation is always available.

Once seams fall between verses, the three sub-arguments collapse in turn:

| My objection | Status under verse-boundary splitting |
|---|---|
| Seams mid-sentence, swallowed words | **Gone.** Seams land where an audio Bible pauses anyway |
| "The timing index stops being one unbroken alignment" | **Answered.** Each segment carries a whole number of verses, so its alignment is exact and self-contained; chapter-relative times are segment-relative times plus a cumulative offset. Offsets are *additive* precisely because no verse straddles a boundary |
| "A per-chapter re-render stops being one reproducible call" | **Weakened to a bookkeeping cost.** It becomes one reproducible *segment plan* — deterministic given the text and the cap, and recorded in the manifest |
| Dollars | **Never the obstacle.** ~4,168,411 billable chars; v3 bills 1 credit/char vs flash_v2's 0.5 ⇒ **$688 vs $344** at Scale, **$500 vs $250** at Business overage. A **$250–344 one-time** delta on a once-forever asset |

**Measured cost of the choice** (computed over the real corpus, verse-boundary packing):

| Effective cap | Requests | Chapters split | Infeasible verses |
|---|---|---|---|
| 30,000 (`flash_v2`) | 1,189 | 0 (0%) | 0 |
| 5,000 (`v3` documented max) | **1,402** | 208 (17.5%) | 0 |
| 3,000 | 1,969 | 665 (56%) | 0 |
| 2,000 (`v3` reliable-generation signal) | **2,698** | 944 (79%) | 0 |

So the engineering cost is **real but not prohibitive**: a segment planner, PCM concatenation, an
offset-additive timing merge, and a per-segment guard — roughly a day inside `render_audio.py`,
which Sprint AUD-D already owns. That is *inconvenient*, not *disqualifying*, and the bar the owner
set is the right one.

**Decision D-AUD-E-35 — the truncation guard becomes per-segment, and the chapter-level invariant
survives as its consequence.** This is the load-bearing change, so it is stated precisely:

- **Per segment:** `endMs[last verse of segment] == segmentDurationMs` (±250 ms).
- **Sum:** `Σ segmentDurationMs == chapterDurationMs` (±250 ms), measured on the *encoded* file.
- **Therefore, unchanged:** `endMs[last verse of chapter] == chapterDurationMs` — §10.3's assertion
  5 **survives verbatim**, and is now *stronger*, because a segment that came back short **in the
  middle** of a chapter is caught by the per-segment guard where the chapter-level check alone would
  have missed it (the interior verses would simply shift).

**Residual risks — real, named, and pilot-checkable:**

1. **Prosody discontinuity between segments (RE-AUD-18).** This is the one that survives, and it is
   a *quality* risk rather than an integrity one. `eleven_v3` is the expressive model, and
   expressiveness is context-dependent: each segment is generated with no knowledge of its
   neighbours, so energy, pitch and pace can step at a seam. A click is not the worry — a *level
   change* is, and it is more audible than a boundary artifact. At a 2,000-char cap this is 2,698
   independent generations, and it partially erodes the very quality being bought. **Mitigation: the
   pilot renders 1 Kings 8 (11,367 chars) segmented and the owner listens at the joins** (runbook
   Q3). This is cheap and decisive.
2. **Generation variance within a chapter (RE-AUD-19).** v3 is documented as more variable
   generation-to-generation. With one request per chapter that variance sits *between* chapters,
   where nobody notices. Segmented, it sits *inside* one chapter, where they might. Pinning a seed
   (if the endpoint accepts one) and fixed voice settings reduces but does not remove it.
3. **The cap is unverified and swings the burden 2× (RE-AUD-20).** Documented max ~5,000 vs a
   reliable-generation signal nearer ~2,000. **Runbook Q2 establishes it empirically before the
   corpus is commissioned** — this is exactly the kind of number we do not take on trust.
4. **Timestamp availability on v3 (RE-AUD-21).** v3 does have a speech-with-timing path, so the
   index survives — but per-model support is not documented, so **runbook Q4 confirms it on real
   pilot output**, and the Forced Alignment fallback (D-AUD-E-23) is already specified for any
   chapter that returns none.

**Encoding rule that this decision forces (and that is easy to get wrong):** concatenate the
**PCM**, then encode **once**. Never concatenate encoded `.opus` files — that produces boundary
artifacts *and* a container duration that no longer matches the timing index, silently breaking
D-AUD-E-35's sum guard. Recorded as **R7** in the runbook.

**Verdict.** I do not think v3 is the wrong call. My objection was to arbitrary splitting, and we do
not have to split arbitrarily. The one thing I would not wave through is risk 1 — so the pin is
real, and Q3 is a genuine gate rather than a formality.

#### 10.0.2b A4 — the seam problem must be solved, not escaped

**Decision D-AUD-E-41 — Request Stitching is unavailable for `eleven_v3`, so the obvious mitigation
is off the table. This is verified, load-bearing, and re-verified at the pilot before we design
around it.**

ElevenLabs' Request Stitching (`previous_text`/`next_text`, `previous_request_ids`/`next_request_ids`)
exists precisely to make segmented long-form generation prosodically continuous — it conditions each
request on what came before and after. **Documentation states it is not available for `eleven_v3`.**
I verified this independently rather than taking it on report, because it is the single fact that
decides how much work §10.0.2b is: if it *were* supported, it would be the answer and almost
everything below would be unnecessary. Docs change, so **runbook Q2 re-verifies it empirically on
live pilot output.** If it turns out to work, the ladder collapses to "use stitching" and the render
is straightforward.

**How bad the problem is, measured** (verse-boundary packing over the real corpus):

| Effective cap | Requests | Split chapters | **Seams** | Chapters that are **seam-free** |
|---|---|---|---|---|
| 5,000 | 1,402 | 208 | **213** | **981 (82.5%)** |
| 3,000 | 1,969 | 665 | 780 | 524 (44.1%) |
| 2,000 | 2,698 | 944 | **1,509** | 245 (20.6%) |

**This is why the cap is now the pilot's first question, ahead of voice** (runbook §5, re-ordered).
It is no longer a cost question — the dollar delta is trivial — it is a **seam-count** question, and
the swing is 7×. It also reframes the risk usefully for the owner: even at the worst cap, **20.6% of
the Bible has no seams at all**, and at the documented cap **82.5% does**. The problem is
concentrated in long chapters, not spread across scripture.

**Decision D-AUD-E-40 — the remedy ladder.** Ordered cheapest-and-safest first. The render session
works down it until the seams are inaudible; it does **not** get to stop early because there is no
fallback model to stop *into*.

| # | Remedy | Verdict | Notes |
|---|---|---|---|
| **1** | **Split at the strongest available break.** Prefer verse boundaries whose preceding verse ends in `.` `?` `!`; then `:` `;`; then any verse boundary | **Keep — first, free** | Measured: **82.9%** of KJV verses end in strong terminal punctuation and **94.5%** end sentence- or clause-terminally, so a preferring splitter has abundant choice and will almost never be forced onto a weak break. A join at a full stop is far more forgiving than one mid-clause. Pure, deterministic, unit-testable |
| **2** | **Balanced segmentation, not greedy fill** | **Keep — free** | Greedy filling to the cap leaves a short tail segment, and tails are where prosody drifts worst. Balanced splitting gives the **same request count** for far fewer pathological segments — it does not reduce the seam *count*, it reduces the badness of the worst segment. Say that honestly rather than overselling it |
| **3** | **Per-segment corrective gain + one chapter-level R128 pass** | **Keep — the highest-value deterministic fix** | A *level step* is the most audible seam artefact, and this removes it without touching the model. **Not** plain per-segment normalisation — that would flatten intra-chapter dynamics. Measure each segment's integrated loudness, compute its deviation from the chapter mean, apply a **capped corrective gain (±2 dB)**, then one EBU R128 pass over the whole chapter for corpus-wide consistency. Gain does not shift time, so the timing index is untouched |
| **4** | **Silence trim + a controlled inter-segment gap** | **Keep — with a correctness hazard flagged** | Trimming each segment's leading/trailing silence and inserting a fixed ~250–350 ms gap makes a join read as a deliberate pause rather than a splice. **But it changes segment durations, so cumulative offsets must be computed from the FINAL post-trim PCM lengths, never from the API-reported durations; verse times shift by −leadTrim within their segment; and the last verse's end must be clamped to the trimmed duration.** D-AUD-E-35's per-segment guard is evaluated **after** trimming. This is the single easiest way to silently corrupt the index, so it is spelled out in the runbook |
| **5** | **Outlier detection + bounded targeted re-render** | **Keep — with a termination rule** | Per segment, compare integrated loudness and speaking rate (chars/sec) against the chapter's own segment distribution; flag deviations beyond a MAD threshold; re-render flagged segments only (a few hundred characters — cheap). **Termination: at most 2 re-render passes per chapter, keeping the best of N by the outlier metric, not "re-render until good"** — v3's variance means an unbounded loop may never converge and would spend real money doing it. A segment still flagged after 2 passes is accepted and logged, or the chapter escalates |
| **6** | **Seed**, if the v3 timing endpoint accepts one | **Keep — open question** | Reduces cross-generation variance; will not make output byte-reproducible. Already RE-AUD-19 |
| **7** | **v3 `stability` toward the robust/consistent end** | **Keep — but LAST, and it is the owner's call** | This is the one remedy that trades away **the exact thing he chose v3 for.** It must not be quietly dialled in by the render session: any change is A/B'd by the owner on pilot audio, with the expressiveness cost stated. Naming the trade is the point |
| **8** | **ElevenLabs Studio / Projects** | **Research spike — see below** | Potentially sidesteps segmentation entirely |
| ✗ | **Cross-fading the joins** | **Rejected** | Overlapping audio makes two verses claim the same milliseconds precisely at the seam. It trades an audible artefact for an **index-correctness** one, and index correctness is R3 / D-AUD-E-35. Not a trade we make |
| ✗ | **Re-render whole chapters until seams vanish** | **Rejected** | Unbounded spend with no termination, and v3 variance means it may not converge. Superseded by remedy 5's bounded best-of-N |
| ✗ | **Hand-editing seams in an audio editor** | **Rejected** | 1,509 seams is not hand-workable, and it destroys reproducibility — nothing would be re-derivable from the runbook, which is the property that makes the corpus trustworthy |
| ✗ | **Accept audible seams and document them** | **Rejected as a remedy** | That is surrender, not mitigation. It remains available only as the escalation outcome in §13 of the runbook, and only as an owner decision |

**The Studio spike (remedy 8) — verdict: research, not a plan.** ElevenLabs Studio is the long-form
/ audiobook product, it is documented as supporting the latest models **including v3**, it has API
endpoints for creating projects and chapters, and it exports per chapter. If it handles
chapter-length text with internal continuity, it removes the seam problem at the root rather than
patching it. Three questions decide it, and **two are unanswerable from documentation**:

1. **Can it be driven headlessly from the API at 1,189-chapter scale?** (Endpoints exist; the
   ergonomics at that scale are unknown.)
2. **Does it return per-character or per-word timestamps?** Documentation points at **Forced
   Alignment** for that, not at Studio itself. Since Forced Alignment is *already* our specified
   fallback (D-AUD-E-23) and caps at 10 h of audio — irrelevant per chapter — **this is probably
   surmountable**: Studio for the audio, Forced Alignment for the index.
3. **The decisive unknown: how does Studio achieve continuity for v3, given stitching is
   unavailable for v3?** Either it has an internal mechanism we cannot get at through the TTS API —
   in which case it is the answer — or it segments internally and has *the same problem*, in which
   case it buys nothing. **No document answers this. Only a spike does.**

Also unknown and worth capturing in the spike: whether pronunciation dictionaries (D-AUD-E-22) apply
in Studio, and how it bills.

**My recommendation on scheduling.** **Do not accept v3-without-stitching as-is; spike it before
Sprint AUD-D is scheduled.** The reasoning is about asymmetry, not pessimism: the corpus render is a
**one-way, ~$500–688 door**, the fallback model has been removed by the owner's own quality
judgement, and the best mitigation has been removed by the vendor — so the two escape routes that
would normally make "try it and see" safe are both gone. Against that, the spike is a few dollars of
pilot spend and about a day, and — this is the part that makes it cheap — **it blocks nothing**:
Sprints AUD-A and AUD-B (the entire player, follow-along, entry points, marking, and the
plug-and-play seam) have no dependency on the render whatsoever and ship as Phase 1 on the device
voice. So the spike costs a day of one person's time and delays nothing that a user can see.

#### 10.0.3 The lexicon must exist before the corpus render

**Decision D-AUD-E-22 — the pronunciation lexicon is a **committed artifact**
(`audio/lexicon/<voiceId>.json`, phoneme rules in IPA/CMU), built and signed off **before** the
corpus render, and the SHA-256 of the lexicon actually used is recorded in `audio_manifest.json`
(gate-asserted equal to the committed file).**

This is not process for its own sake. Under D-AUD-3 + **D-AUD-E-19**, a mispronunciation discovered
after ship costs (a) a paid re-render of the affected chapters, **and** (b) a wait for a MINOR
release, **and** (c) a silent asset-patch download to every user who has that book (§7.5). Three
costs, one of which the user pays. The only cheap moment to fix pronunciation is *before* the corpus
exists, which makes lexicon construction a **prerequisite of the render, not a follow-up to it** —
the exact shape of R-AUD-3's "render a pilot first" control, extended one step earlier.

Practically: the pilot (1 Chr 1, Num 26, Ezra 2 for proper nouns; Ps 23, John 11, Isa 53 for tone)
is rendered *to expose* lexicon gaps; the gaps become rules; the rules are signed off; **then** the
corpus is commissioned. Sprint AUD-D sequences it that way, and the corpus render is the last thing
in it.

Sourcing the candidate list is mechanical and should not be hand-guessed: extract every distinct
token in the corpus not present in a standard English lexicon (a few thousand), rank by occurrence,
and lexicon the head of that distribution. That extraction is a deterministic script over `bible.db`
and belongs in `tools/`, so the lexicon's *coverage* is itself reviewable.

#### 10.0.4 Timing-index source chain, and the alternatives footnote

**Decision D-AUD-E-23 — timing sources, in order: (1) the `with-timestamps` character alignment
returned by the *same* synthesis request; (2) the **Forced Alignment API** for any chapter whose
timestamps are missing or rejected; (3) fail the chapter and re-render — never estimate.** The
independent witness is unchanged: local Whisper (D-AUD-E-8), because **vendor alignment cannot check
itself**, whichever vendor endpoint produced it.

Notes: (1) is free (same request, no extra call) and shares the audio's exact lineage. (2) is
verified to exist, caps at **10 h of audio** and is priced at the Speech-to-Text rate (V20) — the
10 h cap is irrelevant per chapter (longest ≈ 15 min) but rules it out as a whole-corpus operation.
Per-model support for the `with-timestamps` endpoint is **not documented** (V19), which is precisely
why (2) is specified up front rather than discovered mid-render; `render_audio.py` must handle a
chapter with no returned alignment as a normal branch.

> **Recorded-considered (footnote, closed).** *LibriVox / public-domain human KJV* was the
> alternative in OQ-AUD-1. It was $0 for audio but carried a forced-alignment project across ~79 h
> of volunteer-variable recordings, ~31,219 boundaries to verify, reader/mic/room-tone inconsistency
> across 66 books, and no way to fix a mispronunciation short of replacing a reading. The owner
> chose ElevenLabs on voice realism; the engineering analysis independently favoured it on
> FR-AUD-10. **Closed — do not re-propose without new evidence.** *(Rights note: the ElevenLabs
> commercial licence on a paid plan settles the recording layer for AR-AUD-1; OQ-AUD-9 still needs
> the owner's explicit acceptance recorded in `docs/data/README.md` **before** the render is
> commissioned, since the spend is the commitment point.)*

### 10.1 The honest statement, up front

> **TTS output is not byte-deterministic. The `cmp`-based byte-diff idiom that gates
> `bible.db`, `reading_plan.json`, the M'Cheyne asset and the Chronological asset does not transfer
> to audio, and no amount of pinning will make it.** Vendor models change, sampling is
> non-deterministic, and encoders are not bit-reproducible across versions. Any spec that claims a
> reproducible audio rebuild is lying.

What we replace it with is a pair of substitutions, each of which preserves one of the two things
the byte-diff actually gave us:

| The byte-diff gave us | Audio replacement |
|---|---|
| *"the committed artifact is exactly what the pinned sources produce"* (integrity) | **Checksum pinning** — every blob's SHA-256 in the committed manifest, verified in CI before it enters a pack (§8.2) |
| *"the artifact's content is correct"* (correctness) | **An ASR round-trip at render time + an offline structural gate over the committed index in CI** (§10.2–10.3) |

### 10.2 Render time — runs ONCE per render, on the render machine, not in CI

```
bible.db  ──►  tools/render_audio.py                (pinned voice id, model, params, lexicon)
   │            per chapter: strip(text_markup) for the chapter's verses, joined
   │            → vendor TTS with per-request character/word timestamps
   │            → encode Opus 24 kbps mono
   │            → derive [verseId, startMs, endMs] from the vendor timestamps
   ▼
{CH}.opus + provisional timings
   │
   ├──►  tools/verify_audio_asr.py                  THE INDEPENDENT WITNESS
   │      local Whisper (pinned model + revision, offline, no API)
   │      (a) transcribe each chapter → normalize → WER vs strip(text_markup)
   │      (b) ASR word timestamps → independently derived verse boundaries
   │      (c) |asr_boundary − vendor_boundary| per verse
   ▼
audio/audio_manifest.json  +  audio/timings/<USFM>.json     ← COMMITTED to git
audio-corpus-v<N> release assets (the .opus blobs)          ← NOT in git
```

**Decision D-AUD-E-8 — the ASR round-trip is the audio artifact's *independent second witness*, and
it is the same discipline as the plan and text gates: two independent derivations of the same fact,
required to agree, with every disagreement reconciled on evidence and logged in
`docs/data/README.md`.**

Concretely, what ASR catches that nothing else can:

| Failure | Caught by |
|---|---|
| Wrong text rendered (wrong chapter, stale text, a `<a>` tag spoken aloud) | (a) WER explodes |
| Truncated audio (render cut off mid-chapter) | (a) WER + (§10.3) last-verse `endMs` ≠ duration |
| A verse silently dropped | (a) WER + verse-set equality (§10.3) |
| Vendor timestamps misaligned (R-AUD-4 — "a drifting highlight is worse than none") | **(c)** — the *only* thing that catches this, because the vendor's timestamps cannot check themselves |

Thresholds (to be calibrated on the R-AUD-3 pilot, which is exactly what a pilot is for; these are
the starting proposal): per-chapter **WER ≤ 5%** hard-fail, ≤ 2% expected — KJV archaisms and proper
nouns inflate WER against a modern-English ASR model, so the normalizer must lowercase, strip
punctuation, and map a small pinned list of ASR-vs-KJV orthography pairs; **per-verse boundary
agreement ≤ 300 ms** hard-fail, with the *distribution* recorded. Any chapter over threshold is
triaged by a human and either re-rendered or recorded as an accepted variance with a reason — the
same posture as Sprint A's five `TEXT_OVERRIDES` and Sprint-1's seven reconciled conflicts.

Whisper adds `tools/requirements.txt` its first real dependency (the file currently, proudly,
declares that the importer is stdlib-only). That is fine — it is a render-machine dependency, pinned
by version + model revision, and it never ships. It must be stated in the file, not smuggled in.

### 10.3 CI time — `AudioTimingVerificationTest`, offline, every run, **no audio bytes needed**

This is the release gate (M-AUD-2), and it is a peer of `ReadingPlanVerificationTest` (11),
`McheynePlanVerificationTest` (10), `ChronologicalPlanVerificationTest` (8),
`BibleTextVerificationTest` (18) and `BibleDatabaseRoomOpenTest` (5). It reads
`audio/timings/*.json` + `audio/audio_manifest.json` (committed) and `app/src/main/assets/bible/bible.db`
(committed, via the existing `sqlite-jdbc` driver and the existing `planAssetsDir` system property
idiom). It runs in ~1 s and needs not one byte of audio.

| # | Assertion | The failure it catches |
|---|---|---|
| 1 | **Chapter coverage** — exactly 1,189 chapters present, matching `BookCatalog` chapter counts, none extra | a book or chapter missed by the render loop |
| 2 | **Verse-set equality per chapter** — the sidecar's verse-id set is **exactly** `bible.db`'s for that chapter | a dropped verse, an extra verse, a mis-encoded id. *This is the day-by-day-equality analogue* |
| 3 | **All 117 superscriptions present** at verse 0, both directions (present where the text has one, absent where it does not) | the classic "title folded into verse 1" failure, in audio form |
| 4 | **Monotonic, non-overlapping, gapless-within-tolerance**: `startMs[i] < endMs[i] <= startMs[i+1]`, `startMs[0] <= 500` | a shuffled or duplicated index |
| 5 | **`endMs[last] == chapter.durationMs` (±250 ms)** | **truncation** — the single most likely silent render failure, and the one a coverage check alone misses |
| 6 | **Duration plausibility** — `durationMs` within ±35% of `charCount / 14.46 chars-per-sec` | a wrong-chapter render, a stuck encoder, a wrong bitrate |
| 7 | **Manifest ↔ index agreement** — same chapter set; `durationMs` equal; every entry has a non-empty sha256 and `bytes > 0` | a manifest/index desync, an unpinned blob |
| 8 | **WER + boundary-agreement thresholds** are recorded **and within bounds** for every chapter | a render that skipped the ASR gate, or a human waving through a bad chapter |
| 9 | **Psalm 119 windows (M-AUD-1)** — for each of the four days, `PortionVerseBridge.rangesFor` → the index yields a well-formed `[startMs, endMs]` covering exactly verses 1–40 / 41–80 / 81–128 / 129–176 and **nothing else** | the headline correctness requirement, gate-proven offline |
| 10 | **Pack mapping totality** — `AudioPackPlan.packsFor` resolves every one of the 1,189 chapters to exactly one existing pack name, and every pack name is derivable from a `BookCatalog` `usfmCode` | a pack-name typo, a book with no pack, a second book table |

Mutation targets Riley should kill, each by exactly its intended test: drop a verse from a sidecar
(→ 2); delete a superscription entry (→ 3); shorten a `durationMs` by 20% (→ 5, and *not* 6, proving
5 is the truncation guard); shift one verse's `startMs` past the next (→ 4); widen the Psalm 119
window by one verse (→ 9); rename one pack (→ 10).

**M-AUD-5 ("zero unconsented bytes") is also JVM-provable** and belongs here: a test that the only
call path reaching `AudioDownloadRepository.request` originates in an explicit user intent, plus a
`PlayAssetPackRepository` test that construction/observation alone never calls `requestFetch`.

### 10.4 What is device-pass-only

Playback continuity with the screen off; audio focus against a real phone call and a real sat-nav;
lock-screen and Bluetooth transport; a real pack download, eviction and re-download; the actual
update-patch size (§7.5); Opus decode on a real API 26/27 device; and the voice itself (M-AUD-6).
None of it is JVM-provable and none of it should be claimed as such.

---

## 11. Integration with the existing app (problem 7)

### 11.1 Entry points

| Entry point | Where | Mechanism |
|---|---|---|
| Per-reading ▶ (FR-AUD-5 row 1) | `DayContent.ReadingCard`'s existing `Row`, **between** the text `Column(weight(1f))` and the existing `Checkbox` | `IconButton` sized 48dp; **no height increase** — the row is already ≥48dp because of the checkbox (FR-AUD-17 satisfied structurally, not by luck) |
| Whole-day ▶ (row 2) | Schedule `TopAppBar` action | no new row; FR-AUD-18 satisfied. This is the one entry point I recommend Morgan holds as droppable (OQ-AUD-8) |
| Reader transport (rows 3–4) | **`ReaderAudioSlot`** — the reserved `bottomBar` of the reader's own nested `Scaffold` | D-V3-14 cashed in exactly as designed |
| Transport only | media notification / lock screen / headset | never starts a new unit — `MediaSession` commands map to the *current* queue only |

`ReaderAudioSlot()` gains parameters. It has exactly **one** call site (`ReaderScreen`'s `bottomBar`),
so this is a contained change — but `ReaderScreen`'s signature change fans out to `ReaderRoute`,
`ReaderScreenTest`, and `AccessibilityGateTest` test #5, all of which must be updated in the same
commit.

### 11.2 Follow-along

**Decision D-AUD-E-13 — `activeVerseId` is injected by `combine`-ing the controller's
`PlaybackState` into `ReaderViewModel.uiStateForPage(page)`; the reader does not observe the player
directly and holds no player reference.**

```kotlin
fun uiStateForPage(page: Int): StateFlow<ReaderUiState> = /* existing content flow */
    .combine(audioController.state) { content, playback ->
        if (content is ReaderUiState.Content && playback.showsOnPage(content))
            content.copy(activeVerseId = playback.activeVerseId) else content
    }.stateIn(...)
```

`ReaderUiState.Content.activeVerseId` already exists and `VerseItem` already reads it (today it
changes text colour). Priya owns upgrading that to a theme-aware highlight that meets contrast in
light and dark (FR-AUD-27).

**Autoscroll that yields (FR-AUD-12).** `ReaderScreen` already has
`LaunchedEffect(chapterKey) { listState.scrollToItem(0) }` — the new autoscroll must not fight it.
The rule: a `var followEnabled by remember(chapterKey) { mutableStateOf(true) }`;
`LaunchedEffect(activeVerseId) { if (followEnabled) listState.animateScrollToItem(indexOfKey(activeVerseId)) }`;
`listState.isScrollInProgress` **caused by a drag** (`interactionSource`, not by our own animate
call) sets `followEnabled = false`; a new chapter or an explicit ≥48dp "follow along" chip re-arms
it. Distinguishing our scroll from the user's is the whole trick — use the drag interaction, not
`isScrollInProgress`.

### 11.3 The verse-tap collision (FR-AUD-14 / OQ-AUD-4 / R-AUD-9)

Today every verse is `Modifier.clickable(role = Role.Button) { onVerseTapped(canonicalId) }` with
`contentDescription = "Open <Book> <ch>:<v>. <text>"` — a shipped, taught gesture (Sprint H).

**Decision D-AUD-E-14 — the gesture change is implemented as a *session-scoped mode* in one place
(`VerseItem`), and the accessibility path is a `CustomAccessibilityAction`, not a long-press.**

```kotlin
.combinedClickable(
    role = Role.Button,
    onClick = { if (sessionActive) onSeekToVerse(id) else onOpenExternally(id) },
    onLongClick = { onOpenExternally(id) }.takeIf { sessionActive },
)
.semantics {
    contentDescription = if (sessionActive) "Play from $ref. $plain" else "Open $ref. $plain"
    if (sessionActive) customActions = listOf(CustomAccessibilityAction("Open in <app>") { …; true })
}
```

Two engineering notes for the owner's OQ-AUD-4 call:
- A **long-press is not discoverable to TalkBack** and has no default announcement. Shipping
  FR-AUD-14 without the custom action would *remove* an affordance from screen-reader users, which
  NFR-AUD-C forbids ("no accessibility affordance is removed or degraded"). The custom action is not
  optional; it is the cost of the gesture change.
- The footer hint (`ReaderFooterHint`, already reactive to the external app) becomes reactive to the
  session too, which is a one-line change to an existing `when`. It stays
  `clearAndSetSemantics {}` — the verse nodes carry the speech.

### 11.4 One-screen fit, and why there is no mini-player

**Decision D-AUD-E-12 — no global mini-player above the `NavigationBar` in this cut.** The root
`Scaffold`'s `bottomBar` is **already** the `NavigationBar` (~80dp, R-V3-1, owner-accepted). A
mini-player would be a `Column` above it, spending another ~64dp *permanently*, on a screen where
four sprints (S16, S18, S20, the N-stream fix) were spent winning one-screen fit and the 4-stream
M'Cheyne case has ~0 slack. Playback outside the reader is controlled by the **media notification**,
which is free, always available, and is what a user reaching for a control actually looks at. If the
device pass says otherwise, a mini-player is an additive follow-up — it is not free and must not be
smuggled in as polish.

### 11.5 Marking

`StartReadAloudUseCase` calls the **existing** `MarkReadOnOpenUseCase(date, portion.streamNumber)`
and then `widgetRefresher.refreshTodayWidget()` — byte-identical to `onReadingTapped`'s D-O-1/D-O-2
path (mark first, in one `viewModelScope.launch`, before resolving the destination, so it lands
uniformly). **No new marking rule enters the codebase** (FR-AUD-15). `DayUnit` marks each stream as
its portion *begins*, from the queue advance callback. Browse marks nothing. FR-AUD-16 (no second
progress axis) is satisfied by there being nothing to satisfy: there is no listening state anywhere
in the model.

---

## 12. The manifest / permission delta (VERIFIED)

### 12.1 Today's merged release manifest, fully attributed

| Permission | Origin (verified from the AAR manifest) |
|---|---|
| `android.permission.POST_NOTIFICATIONS` | our `AndroidManifest.xml` (S12) |
| `android.permission.RECEIVE_BOOT_COMPLETED` | ours (S12) — also from `work-runtime` |
| `android.permission.WAKE_LOCK` | `androidx.work:work-runtime:2.7.1` ← Glance |
| `android.permission.ACCESS_NETWORK_STATE` | `androidx.work:work-runtime:2.7.1` ← Glance |
| `android.permission.FOREGROUND_SERVICE` | `androidx.work:work-runtime:2.7.1` ← Glance |
| `com.jpillion.dailyreadingplanner.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | `androidx.core:core` (app-private signature permission) |

That is the "6 permissions" our docs record, now itemised by origin for the first time.

### 12.2 The delta

**From `com.google.android.play:asset-delivery:2.3.0` — verbatim from its AAR manifest:**

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />            <!-- ALREADY PRESENT -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />  <!-- NEW -->
<service android:name="…assetpacks.AssetPackExtractionService"
         android:enabled="false" android:exported="true">
  <meta-data android:name="…assetpacks.versionCode" android:value="20300" /></service>
<service android:name="…assetpacks.ExtractionForegroundService"
         android:enabled="false" android:exported="false"
         android:foregroundServiceType="dataSync" />
<receiver android:name="…assetpacks.SessionStateBroadcastReceiver"
          android:enabled="true" android:exported="true"
          android:permission="android.permission.INSTALL_PACKAGES">
  <intent-filter><action android:name="…ACTION_SESSION_UPDATE" /></intent-filter></receiver>
```

**From us, for the player:** `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK` (required from
targetSdk 34; we target 37) + our `PlaybackService`.

**From media3:** **nothing.** `media3-session` declares no permissions and no components (V7);
`media3-exoplayer`/`media3-common` merge only `ACCESS_NETWORK_STATE` + `WAKE_LOCK`, both already
present (V8).

### 12.3 The honest summary

| | Before | After |
|---|---|---|
| Permissions | 6 | **8** |
| New permissions | — | `FOREGROUND_SERVICE_DATA_SYNC` (asset-delivery), `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (ours) |
| `INTERNET` | absent | **absent** — NFR-AUD-A / NFR-V3-A holds |
| Exported components | 1 (`MainActivity`) | **4** (`+ PlaybackService`, `+ AssetPackExtractionService`, `+ SessionStateBroadcastReceiver`) |
| Foreground services | 0 | 2 (ours: `mediaPlayback`; Play's: `dataSync`, `enabled=false` until Play enables it) |
| Version bumps | — | `work-runtime 2.7.1→2.9.1` (manifest-neutral, V6), `basement 18.1.0→18.4.0`, `tasks 18.0.2→18.2.0`, `core-common 2.0.3→2.0.4` |

**This is not "zero permission impact," and the spec will not say that it is.** It is: *no
`INTERNET`, no data-collection change, no data-safety-form change — but a materially wider manifest
surface, two new foreground-service permissions, and three new exported components.* Two of the
exported components are Play's own and are permission-guarded or disabled-by-default; ours is the
standard `MediaSessionService` contract. `M-AUD-3` should be **restated** from "no new permission"
to "**no `INTERNET`, and exactly these two new permissions, both foreground-service types, both
required by shipped functionality**" — see §16.

---

## 13. Non-functional requirements

### 13.1 The numbers

| NFR | Target | How held |
|---|---|---|
| **Cold start** | unchanged | Nothing audio-related runs at startup. No `AssetPackManager` call, no TTS init, no service start until the user presses play. `getPackLocation` is called lazily, off-main, on first Settings→Audio open or first play |
| **Memory** | +~2 MB peak | One book's timing index (~14 KB avg, ~70 KB Psalms) + ExoPlayer buffers. `AudioQueue` for a portion is ~200 `SpokenVerse` ≈ tens of KB. Nothing holds the corpus |
| **Battery (NFR-AUD-G)** | playback-proportional | ExoPlayer's `setWakeMode(C.WAKE_MODE_LOCAL)` (CPU only, **never** `WAKE_MODE_NETWORK` — there is no network at playback time). The foreground service stops itself on `Ended`/`Idle`; `SleepTimer` is a coroutine delay, never an `AlarmManager` (D-S12-1's inexact-alarm posture is for *scheduling*, and playback is not scheduled) |
| **Storage (NFR-AUD-E)** | truthful, ≤2 taps to delete | Sizes from `AssetPackStates`; deletion via `removePack`. We never compute a size ourselves |
| **Install size (NFR-AUD-B)** | base ≤ 12 MB | §9's split gate; media3 ~+1.5–2.5 MB post-R8 against ~3.9 MB headroom |
| **Offline (NFR-AUD-A)** | no `INTERNET` | §12; asserted by `AUD-A-0` and re-asserted at release |
| **StrictMode** | clean | All pack, timing-file and text reads are `suspend` on `Dispatchers.IO`. The one hazard is `PackFileLocator` — `getPackLocation()` is a binder call and **must not** be on main. Pinned by review, not provable on JVM |
| **A11y (NFR-AUD-C, M-AUD-8)** | gate green | `AccessibilityGateTest` hardcodes tags, so **every** new control must be added there: `read-aloud-<stream>`, `read-aloud-day`, `transport-play`, `transport-pause`, `transport-next`, `transport-prev`, `follow-along-chip`, `audio-download-<usfm>`, `audio-delete-all`, `audio-wifi-only`, `audio-speed`, `audio-sleep-timer`. Playback **never auto-starts**, so the player can never speak over TalkBack unbidden |
| **Kover** | floor holds | The filter today covers `…domain.*` and `…data.*` only. **Recommendation: extend it to `…audio.domain.*`** — the queue builder, timing index, pack plan, availability resolver and sleep timer are exactly the pure logic the floor exists for. `audio.data.playback` is Android-coupled and stays out, like `bible/` and `ui/` |

### 13.2 minSdk 26 implications (all clear, no legacy branches)

`AudioFocusRequest` (26), `NotificationChannel` (26), `TextToSpeech.onRangeStart` (26, unused),
media3 (21), Opus decode (21, V10). `foregroundServiceType` as a manifest attribute is API 29+ but
is simply ignored below; `FOREGROUND_SERVICE` is required only from 28 and
`FOREGROUND_SERVICE_MEDIA_PLAYBACK` only from 34 — declaring both is correct and harmless on 26–27.
**minSdk 26 costs us nothing here**, which is a pleasant surprise worth recording.

The one genuine 26/27 risk is **OEM Ogg-Opus decoder coverage on Android 8.x**, which is documented
as supported but has historically been patchy. Mitigation, deliberately *not* taken pre-emptively:
`androidx.media3:media3-exoplayer-opus` bundles a software decoder but adds ~1 MB **per ABI** to the
base module — that spends a third of our headroom to fix a problem we have not observed. **Ship
without it; add it only if the device pass on a real API 26/27 device shows a gap.**

### 13.3 Storage/state

- **No Room schema change. No new database. No new DataStore file.** Four new keys in the existing
  `SettingsRepository` (`data/prefs`): ~~`audio_voice_source` (string, default `DEVICE`)~~ →
  **A2: `active_voice_id`** (string, absent ⇒ `device_tts`; unknown/uninstalled ⇒ `device_tts`
  **on read, without rewriting the stored value** — D-AUD-E-33, the `selected_plan` idiom),
  `audio_wifi_only` (bool, default **true**), `audio_speed` (float, default 1.0f, clamped
  0.75–2.0), `audio_sleep_timer_minutes` (int, 0 = off). Each must also be added to
  `testing/FakeSettingsRepository`.
- **Downloaded-pack state is Play's**, never mirrored into our store. Mirroring it would create a
  second truth that goes stale on eviction — the exact class of bug §7.4 edge 4 exists to prevent.
- FR-AUD-25 ("resume where I stopped") is **in-session only**, in the controller, matching D-V3-13's
  `SavedStateHandle` posture for last-read. Durable cross-session resume is a follow-up.

---

## 14. Dependencies

| Dependency | Version | Scope | Justification |
|---|---|---|---|
| `androidx.media3:media3-exoplayer` | 1.10.1 | main | Phase 2 file playback, clipping, speed, focus. Merges only already-present permissions (V8) |
| `androidx.media3:media3-session` | 1.10.1 | main | `MediaSession`/`MediaSessionService` ⇒ lock screen, notification, headset, Bluetooth. **Zero manifest contribution** (V7) — the service is ours to declare |
| `androidx.media3:media3-common` | 1.10.1 | main | transitive; contains **`SimpleBasePlayer`** (V9), the thing that makes D-AUD-E-2 work |
| `com.google.android.play:asset-delivery` | 2.3.0 | main **[P2]** | D-AUD-1. Manifest delta in §12 |
| `com.google.android.play:asset-delivery-ktx` | 2.3.0 | main **[P2]** | `suspend` wrappers (`requestFetch`, `requestPackStates`) — avoids hand-rolling `Task` bridges |
| `android.speech.tts` | platform | — | Phase 1. No dependency |
| `androidx.work:work-runtime` | 2.7.1 → **2.9.1** | transitive | forced by asset-delivery; **manifest-neutral** (V6). Bump the catalog explicitly so it is a reviewed change, not a silent resolution |
| `com.google.android.gms:play-services-basement` | 18.1.0 → **18.4.0** | transitive | forced by asset-delivery; contributes only the existing `gms.version` meta-data |
| `com.google.android.play:core-common` | 2.0.3 → **2.0.4** | transitive | forced; `PlayCoreDialogWrapperActivity` already merged via `app-update` |
| `openai-whisper` (or `faster-whisper`) + `ffmpeg` | pinned | **render machine only** | §10.2. Goes in `tools/requirements.txt`, which must stop claiming stdlib-only |
| **Net new test deps** | — | — | **zero** — the gate is JSON + `sqlite-jdbc` (present) + JUnit/Truth (present) |

**No `INTERNET`. No analytics. No CDN client. No new vendor at runtime.**

---

## 15. Risk register (engineering)

| ID | Risk | Severity | Mitigation |
|---|---|---|---|
| **RE-AUD-1** | The merged-manifest delta differs from §12 (could not be built here — SDK 37 unavailable) | High | `AUD-A-0` is the **first ticket**, and it is go/no-go before any further work |
| **RE-AUD-2** | Play rejects 66 asset packs, or `requestFetch` behaves differently at that count | High | `AUD-C-1` internal-track placeholder upload **before the render is commissioned**; the fallback (8 section packs) is one edit to `AudioPackPlan` + the generator, with no artifact change |
| **RE-AUD-3** | The update-patch behaviour (§7.5) is a full re-download in practice, not a delta | High | measured in `AUD-C-1`; if so, D-AUD-3 goes back to the owner **before** the spend |
| **RE-AUD-4** | The 850 MB build-artifact flow makes release CI slow, flaky or expensive | Medium | audio excluded from PR/push CI entirely (§9); release-only job; no artifact upload; timeout raised to 45 min |
| **RE-AUD-5** | ASR WER thresholds are miscalibrated and either wave through defects or block a good render | Medium | calibrate on the R-AUD-3 pilot **first**; record the distribution, not just pass/fail; every over-threshold chapter is human-triaged and logged |
| **RE-AUD-6** | Vendor verse timestamps drift; the highlight lands on the wrong verse | Medium | D-AUD-E-8's boundary-agreement check is the *only* thing that catches this; ≤300 ms hard-fail; `null` timings ⇒ **no highlight**, never a guess |
| **RE-AUD-7** | Ogg-Opus decode gaps on real API 26/27 devices | Medium | device pass on a real Android 8 device is a **release gate**; `media3-exoplayer-opus` is the known, priced fallback (§13.2) |
| **RE-AUD-8** | The reader becomes a media app — service lifecycle, focus, notification bugs eating sprints (Maya's R-AUD-6) | Medium | Phase 1 is the forcing function; the two-seam split means Phase 2 adds one `Player`; no mini-player (D-AUD-E-12); the §4 non-goals hold |
| **RE-AUD-9** | The gesture change (FR-AUD-14) degrades TalkBack | Medium | D-AUD-E-14's custom action is mandatory, not optional; `AccessibilityGateTest` extended before the gesture ships |
| **RE-AUD-10** | One-screen-fit regression at N=4 from the per-card ▶ | Low-Med | the control sits in the existing ≥48dp `Row`; height-neutral by construction; M-AUD-10 device pass |
| **RE-AUD-11** | `PlaybackService` is a new exported component | Low | required by the `MediaSessionService` contract; no custom actions accepted from external controllers beyond media3's standard set; security-review item |
| **RE-AUD-12** | Corpus release assets are mutable by a maintainer | Low | SHA-256 pin in `audio_manifest.json`, verified in CI before packs are populated |
| **RE-AUD-13** (A1) | Pack count is now **per voice** — 66 × voices against an unverified ceiling | High | `AUD-C-1` must establish the *ceiling*, not just that 66 works; the 8-section fallback is now expressible **as data** (`"packing": "sections"`), per voice, with no code change (§7A.5) |
| **RE-AUD-14** (A2) | **A code path binds a voice other than the active one** — the exact failure the owner ruled out | High | Gate assertion 17 states it as a *negative* over a (voice × passage) matrix; the highest-value mutation in the suite is "fall through to another installed voice" |
| **RE-AUD-15** (A1) | Owner elects `eleven_v3` at the pilot, adding chunk/splice/stitch machinery to AUD-D | Medium | Priced in §10.0.2 and put to him **with the price attached** at pilot time (OQ-AUD-E-6), not absorbed silently |
| **RE-AUD-16** (A1) | A mispronunciation ships and costs a paid re-render + a MINOR wait + a silent patch to users | Medium | D-AUD-E-22: lexicon built, signed off and **committed before** the corpus render; candidate list extracted mechanically from `bible.db`, so lexicon *coverage* is reviewable |
| **RE-AUD-17** (A2) | Two voices = paying for the same books twice (~853 MB each) | Medium | Exclusive selection makes a second voice pure duplication — so per-voice storage totals and one-action "delete this voice" are requirements, not polish (D-AUD-E-32) |
| **RE-AUD-18** (A3, **re-rated A4**) | **Prosody/level discontinuity at segment seams.** ~~Mitigated by falling back to flash_v2.~~ **A4: there is no fallback** — the owner rejected both single-request models on quality, and Request Stitching (the designed mitigation) is unavailable for v3. This risk must be *solved* | **HIGH** (was Medium-High) | The **D-AUD-E-40 remedy ladder**, worked in order, plus the Studio spike. Runbook **Q4** listens at the joins; **§13 makes "ladder exhausted, seams still audible" a hard stop-and-escalate before any corpus spend** |
| **RE-AUD-23** (A4) | Request Stitching unavailable for v3 removes the best mitigation | **High** | Verified independently; **re-verified empirically at the pilot (Q2)** because docs change and this one fact would collapse the whole ladder if it flipped |
| **RE-AUD-24** (A4) | The silence-trim/gap remedy (ladder #4) **silently corrupts the timing index** if offsets are taken from API-reported durations instead of final PCM lengths | **High** | Spelled out step-by-step in the runbook; D-AUD-E-35's per-segment guard is evaluated **after** trimming, so a mistake here fails the gate rather than shipping |
| **RE-AUD-25** (A4) | An outlier re-render loop spins and spends without converging | Medium | Bounded: **max 2 passes per chapter, best-of-N by metric**, then accept-and-log or escalate. The ledger's spend accumulator enforces the ceiling across restarts |
| **RE-AUD-19** (A3) | v3's generation variance now sits *inside* a chapter rather than between chapters | Medium | Pin a seed if the endpoint accepts one; fixed voice settings for the whole corpus; both recorded in the manifest |
| **RE-AUD-20** (A3) | The real per-request cap is unverified and swings the request count 2× (1,402 vs 2,698) | Medium | Runbook **Q2** establishes it empirically **before** the corpus is commissioned |
| **RE-AUD-21** (A3) | v3 timestamp support is undocumented per model | Medium | Runbook **Q4** confirms on real pilot output; the Forced Alignment fallback (D-AUD-E-23) already covers a chapter that returns none |
| **RE-AUD-22** (A3) | A heading clip is missing at runtime ⇒ a silent gap before a chapter | Low | Gate assertion 21 asserts **totality** over all bundled plans and all canon chapters; we generate the closure (incl. the 31 windowed short forms) rather than reason about reachability |

---

## 16. Where I disagree with the PRD, or think it is priced wrong

Recorded rather than silently resolved, per Maya's own review note.

> **A1/A2/A3 status.** **OQ-AUD-1 is resolved (ElevenLabs, D-AUD-E-20)** and **OQ-AUD-5 is resolved**
> (spoken headings, §5A) — items below that assumed either open are historical. **Items 9 and 11 are
> disagreements I lost**, both recorded because the reasoning is worth keeping.

1. **§8's download-unit table is not deliverable as written, and the sizes are optimistic.** An asset
   pack is the *atomic* download unit, so "Today's readings ~2.4 MB" cannot exist — today's readings
   span 3–4 books, so the true cost is "the books those readings need," ~40–120 MB. Likewise "next
   30 days ~70–90 MB" is really "the 6–10 books your next 30 days touch," ~150–250 MB. **The
   product rule survives intact** (never forced to take 850 MB) and FR-AUD-20's "state the size
   before starting" is satisfied *more* honestly by quoting Play's real number. But the unit table
   should be rewritten as **book / testament / everything / "the books your next 30 days need"**,
   and the "today's readings" row deleted. *Alternative if the small unit is a hard product
   requirement:* per-chapter packs (1,189 modules) — I do not recommend it and would want strong
   evidence before building it.
2. **D-AUD-3 is priced as "slow but free"; it is not free.** §7.5: a release that changes audio
   pushes the delta to existing users *as part of the automatic app update, with no consent prompt*.
   That is in tension with D-AUD-11's spirit. My proposed rule (D-AUD-E-19) — never change audio in
   a PATCH release, batch corrections into a MINOR with a whatsnew line — should be added to the PRD
   as a product commitment, not left as an engineering habit.
3. **M-AUD-3 should be restated.** "No new permission after the audio work" is not achievable and
   the spec should not aim for it. The achievable, meaningful gate is: **no `INTERNET`; exactly two
   new permissions, both foreground-service types, both required by shipped functionality; no
   data-safety change.** Aiming at the unachievable version invites someone to quietly redefine
   "new."
4. **R-AUD-4's proposed independent witness does not work.** "Spot-verification against the Phase-1
   device-TTS boundaries" compares boundaries in *different audio* — a Samsung TTS engine's verse
   boundaries say nothing about where a verse starts in an ElevenLabs recording. The witness has to
   be derived from **the same audio by an independent method**, which is what D-AUD-E-8's ASR
   alignment is. This matters because it is the *only* check on the timing index, and the timing
   index is the requirement Maya correctly identifies as pricing the whole feature.
5. **FR-AUD-10's count is inconsistent.** "31,102 verses (+117 superscriptions)" is right as
   arithmetic but the index covers **31,219 rows**; the gate should be stated against 31,219 or the
   superscriptions become an easy thing to forget. Our own `bible.db` returns 31,219 (V14).
6. **FR-AUD-2's "previous/next verse-or-chapter" needs a call, and I have made one** (D-AUD-E-4):
   transport buttons = **chapter**; verse movement = in-app only. Verse-level media buttons on a car
   stereo would be maddening, and per-verse media items cost audio continuity. Flagging it because
   it is a visible behaviour, not an internal detail.
7. **FR-AUD-23 (speed) is not symmetric across phases.** TTS `setSpeechRate` and ExoPlayer
   `setPlaybackSpeed` are different mechanisms with different quality curves; several stock engines
   are unintelligible above ~1.5×. One persisted normalized factor, clamped, with the honest note
   that Phase 1's high end is worse than Phase 2's.
9. **A2 — a disagreement I lost, and why the loss is the right outcome.** I designed cross-voice
   resolution (D-AUD-E-24) because it maximises the chance that a press of ▶ produces good audio.
   The owner ruled it out: the user *chose* a voice, and handing them a different one — however
   deterministically — is a substitution they did not ask for. He is right, and the tell is that my
   version needed a priority-ordering field in a data file to be explainable at all, which is
   usually the sign that a rule is doing something the user cannot predict. The exclusive rule is
   also strictly simpler: it deletes an algorithm, a config field, a use case, and a whole class of
   state (mixed coverage) from the design. Recorded because "the simpler rule was also the correct
   one" is worth remembering the next time availability logic starts growing a preference order.
11. **A3 — the second disagreement I lost, and the lesson in it.** I ruled out `eleven_v3`
    (D-AUD-E-21) on splice-integrity grounds without checking whether the splits had to be
    arbitrary. They did not: we assemble chapter text from verses, so we can split on verse
    boundaries, and one query against the corpus (longest verse = **Esther 8:9, 529 chars**) shows
    that is *always* feasible even at the most pessimistic cap. The lesson is narrow and worth
    keeping: **I priced a constraint before checking whether it was one.** The residual risk
    (RE-AUD-18, prosody at seams) is real and is now a pilot gate rather than a reason to overrule
    the owner — which is the right shape for a taste decision with an engineering cost attached.
12. **Agreement, recorded:** D-AUD-7 (two-phase) is right, and the reason is stronger than the PRD
   states — with `SimpleBasePlayer`, Phase 1 is not merely "the UX before the spend," it is the
   *same media3 player architecture*, so Phase 2 is one class. And D-AUD-8 (play marks read via the
   existing seam) is right: the alternative needs completion tracking, is ambiguous on partial
   listens, and would be the app's first divergence between "opened" and "done."

---

## 17. Decisions (`D-AUD-E-*`) — index

| ID | Decision |
|---|---|
| D-AUD-E-1 | App code stays in `:app` with a new feature-grouped `audio/` tree (sibling of `bible/`); asset packs are code-free **generated** Gradle modules under `audio-packs/`. |
| D-AUD-E-2 | The player is androidx.media3: `MediaSessionService` + `MediaSession` over a media3 `Player`; the phase-swappable unit is **one `Player` implementation** (`SimpleBasePlayer` for TTS, ExoPlayer for files). Seam 1 = `AudioReadingController` (app-facing, one); seam 2 = the `Player` (source-facing, two). |
| D-AUD-E-3 | The per-verse timing index is a **per-book sidecar inside the asset pack**, sourced from 66 files **committed to git** at `audio/timings/`. **Not** in `bible.db` (which would couple text to audio and cost every user a 5.7 MB re-copy per render) and **not** a third base-module DB. |
| D-AUD-E-4 | Media items are **chapters** (clipped to the queue's verse window); the verse is a derived position, never a timeline entry. Transport next/prev = chapter; verse seek = in-app. |
| D-AUD-E-5 | **66 on-demand packs, one per book**, generated from `BookCatalog`; `AudioPackPlan.packsFor` is the single mapper, so re-grouping to 8 section packs is a config edit with no artifact change. Gated by an internal-track upload before any render spend. |
| D-AUD-E-6 | Audio blobs **never enter git**; they are GitHub Release assets on an `audio-corpus-v<N>` tag, **pinned by SHA-256 in the committed manifest**, fetched by CI on release runs only. The small checkable artifacts (timings + manifest) are committed. |
| D-AUD-E-6a | `BuildAudioQueueUseCase` is the only constructor of an `AudioQueue` and sources ranges from `PortionVerseBridge` and text from `MarkupStripper.strip` — never chapter-file boundaries, never a re-derived range. |
| D-AUD-E-7 | The 12 MB CI gate is **re-pointed at the base module** and kept verbatim on an audio-less PR build; a release-only job adds a base-module measurement, a **zero-audio-bytes-in-`base/` structural assertion**, and a 1.2 GB total ceiling. |
| D-AUD-E-8 | The byte-diff idiom is replaced by **checksum pinning (integrity) + an ASR round-trip at render time (correctness) + an offline structural gate in CI**. ASR-derived verse boundaries are the *independent second witness* for the vendor's timestamps. |
| D-AUD-E-9 | Phase 1: one TTS utterance per verse; `UtteranceProgressListener.onStart` is the verse boundary; no timing index; no scrubber (duration honestly `TIME_UNSET`). |
| D-AUD-E-10 | Audio format is **Opus ~24 kbps mono in Ogg** (decode documented Android 5.0+); the software Opus decoder extension is **not** shipped pre-emptively (~1 MB/ABI). |
| D-AUD-E-11 | **One** `AudioFocusCoordinator` owns audio focus and becoming-noisy for both players; ExoPlayer's built-in handling stays off, so there is one home and no drift. |
| D-AUD-E-12 | **No global mini-player.** The root `bottomBar` is already the `NavigationBar`; out-of-reader control is the media notification. Additive follow-up only if the device pass demands it. |
| D-AUD-E-13 | `activeVerseId` is `combine`d into `ReaderViewModel.uiStateForPage`; the reader never holds a player reference. Autoscroll yields on a **drag** interaction and re-arms on a new chapter or an explicit ≥48dp chip. |
| D-AUD-E-14 | The session-scoped verse-tap change lives in one place (`VerseItem`) and **must** ship with a `CustomAccessibilityAction` for the external tap-out — a long-press alone would remove an affordance from TalkBack users. |
| D-AUD-E-15 | No Room schema change, no new database, no new DataStore file: four new keys in the existing `SettingsRepository`. Downloaded-pack state stays Play's, never mirrored. |
| D-AUD-E-16 | `ResolveAudioAvailabilityUseCase` is the single home of the degradation ladder (downloaded → device voice → honest message), consumed by every entry point. |
| D-AUD-E-17 | Marking reuses `MarkReadOnOpenUseCase` unchanged, on press, before destination resolution (the D-O-1/D-O-2 path); `DayUnit` marks each stream as its portion begins; Browse marks nothing. |
| D-AUD-E-18 | `AudioReadingController` is `@Singleton` (playback outlives the Activity), not `@ActivityRetainedScoped`; `PlaybackService` is `@AndroidEntryPoint`. |
| D-AUD-E-19 | **Audio content never changes in a PATCH release** — corrections batch into a MINOR, because an audio change rides the automatic app update and spends user bytes without a consent prompt (§7.5). |
| **A1 — voice source (OQ-AUD-1 resolved)** | |
| D-AUD-E-20 | The voice source is **ElevenLabs** (owner's call, on voice realism). Alternatives closed to a recorded-considered footnote (§10.0.4). |
| D-AUD-E-21 | Pin **`model_id = "eleven_flash_v2"`** — phoneme tags exist only on `eleven_v3`/`eleven_flash_v2`, and flash_v2 is the only one of the two that renders **every** chapter in one request (0 of 1,189 over its 30,000-char cap vs 208 over v3's 5,000). One request per chapter ⇒ no splices, one unbroken alignment, a reproducible per-chapter re-render. Voice id + model + params recorded in the manifest. |
| D-AUD-E-22 | The **pronunciation lexicon is committed and signed off BEFORE the corpus render** (`audio/lexicon/<voiceId>.json`); its SHA is recorded in the manifest and gate-asserted. A post-ship mispronunciation costs a paid re-render + a MINOR wait + a silent patch — so the only cheap moment is before the corpus exists. |
| D-AUD-E-23 | Timing sources in order: `with-timestamps` character alignment from the same request → the **Forced Alignment API** → fail and re-render. Never estimate. The independent witness stays local Whisper (D-AUD-E-8) because vendor alignment cannot check itself. |
| **A1 — plug-and-play packs** | |
| D-AUD-E-24 | ⛔ **SUPERSEDED by D-AUD-E-29** (owner ruling, A2). *Was:* per-play-unit voice resolution with a preferred-voice priority order. Recorded, not deleted — §7A.7. |
| D-AUD-E-25 | **Two artifacts, one authority each**: committed `assets/audio/catalog.json` = the download menu only; in-pack `voice.json` = the sole authority for playback. Triple anti-drift id check (`voiceId` == catalog id == pack-name segment == timings dir). |
| D-AUD-E-26 | `manifestVersion`, `timing.format` and `audio.format` are **closed sets**; a pack outside them is unusable and says so (clean-fail). Additive-only within a `manifestVersion`; a newer pack on an older app is rejected, never optimistically parsed. |
| D-AUD-E-27 | `AudioPackPlan` reads the **pack layout from the manifest/catalog**; the app contains no `66` and no per-book assumption — a voice shipped as 1, 8 or 66 packs is a data difference. (Supersedes the pack-count half of D-AUD-E-5.) |
| D-AUD-E-28 | `AudioVoiceSelector` follows **D-N-3 exactly**: ≤1 usable voice ⇒ static text, >1 ⇒ a chooser. Branch built and tested from day one, invisible in production while there is one voice. |
| **A2 — owner ruling: exclusive app-wide voice** | |
| D-AUD-E-29 | **Voice selection is app-wide and exclusive.** One active voice for the whole app; coverage is evaluated **only** against it; a chapter it lacks ⇒ **prompt to download that voice's pack**, never a cross-voice substitution even when another installed voice covers it. *Substitution is forbidden; offering is required.* `ActiveVoiceRepository` mirrors `ActivePlanRepository`. |
| D-AUD-E-30 | The **device voice is a registry entry** (`device_tts`), the default, always installed, always fully covering — a synthetic manifest inside the same contract. Collapses the degradation ladder into selection and **forces the plug-and-play seam into Phase 1**, where it is tested with zero bytes and no PAD dependency. |
| D-AUD-E-31 | A voice switch **stops playback** and retains queue + `activeVerseId`, so the next press resumes the same passage from the current verse in the new voice — no half-state, no cross-voice moment, and no download prompt raised from the Settings screen. |
| D-AUD-E-32 | Downloads are grouped **by voice**; "delete this voice" is one action; a **partially** deleted active voice stays active and prompts per missing chapter; a **fully** uninstalled active voice reverts to `device_tts` **with one notice** (never silently) and **without rewriting the stored id**, so re-downloading restores the user's choice. |
| **A3 — headings, and the v3 verdict** | |
| D-AUD-E-34 | **Pin `model_id = "eleven_v3"`** (supersedes D-AUD-E-21), subject to two pilot gates: the real per-request cap (Q2) and seam audibility on a segmented long chapter (Q3). `eleven_flash_v2` is the named fallback, and taking it is an **owner** decision. Verse-boundary splitting is always feasible (longest verse = Esther 8:9, 529 chars), which defeats the alignment-integrity objection; the $250–344 delta was never prohibitive. |
| D-AUD-E-35 | The truncation guard becomes **per-segment** (`endMs[last] == segmentDuration`) **plus a sum guard** (`Σ segments == chapter duration`). The chapter-level invariant survives as a consequence and is now *stronger* — a short segment mid-chapter is caught where the chapter-level check alone would miss it. Concatenate **PCM then encode once**; never join encoded files. |
| **A4 — v3 mandatory; the seam problem** | |
| D-AUD-E-40 | **The remedy ladder** for segment seams, worked in order and with no early exit: strongest-break splitting (82.9% of verses end sentence-terminally) → balanced segmentation → per-segment corrective gain + one chapter R128 pass → silence-trim + controlled gap (with the offset hazard) → bounded outlier re-render (max 2 passes, best-of-N) → seed → **stability, last, and the owner's call because it trades away what he chose v3 for** → the Studio spike. **Rejected:** cross-fades (they corrupt the index), unbounded whole-chapter re-renders, hand-editing, and "document the seams". |
| D-AUD-E-41 | **Request Stitching is unavailable for `eleven_v3`** — the designed mitigation for segmented long-form continuity is off the table. Verified independently and **re-verified at the pilot**, because if it flipped, the ladder would collapse to one line. This is what makes RE-AUD-18 HIGH and un-escapable, since the owner's quality rejection also removed the fallback model. |
| D-AUD-E-36 | Headings are **voice-specific**, live **inside the voice pack**, and are declared by its manifest (`headings` block), keyed by `clipId` — plug-and-play by construction, ~5 MB/voice. A future unbounded-heading feature is an additive generator against this seam. |
| D-AUD-E-37 | A heading is a **separate media item**; verse timings stay **chapter-relative** and **no heading audio is ever inside a chapter file** — so §10.3 assertion 5 and D-AUD-E-35's guards are completely unaffected. `activeVerseId` is null while a heading plays (a legal, already-handled state). |
| D-AUD-E-38 | Heading selection is the pure `HeadingPlan.headingsFor(queue)`: **full form whenever the *book* changes** (so 3 John in the Jun 19 / Dec 19 portion takes the full form), short form otherwise; single-chapter books get the book name alone; Psalms singular **via `ReadingFormatter.singularizeBookName`, never reimplemented**; windowed refs take the verse form. |
| D-AUD-E-39 | The 1,401-clip inventory is **generated** from `BookCatalog` + the plan registry into a committed `audio/headings/inventory.json`; the render consumes that export and never re-derives heading strings in Python (the `exportBookCatalog` anti-drift discipline). |
| D-AUD-E-33 | **`active_voice_id`** string key in the existing DataStore. Absent ⇒ `device_tts`; unknown/uninstalled ⇒ `device_tts` **on read, not on write** — the `selected_plan` / `BibleProvider.fromStored` posture, field for field. Supersedes A1's `audio_preferred_voice_id` and §13.3's `audio_voice_source`. |

---

## 18. Recommended sprint breakdown

Sequenced by dependency and by *what must be true before money is spent*. Morgan turns these into
tickets.

**Sprint AUD-A — Posture proof + the player spine on the device voice (no audio artifact).**
`AUD-A-0` the merged-manifest diff (go/no-go). media3 deps + the 12 MB gate re-point (D-AUD-E-7a);
`AudioReadingController`, `PlaybackService`, `MediaSession`, `AudioFocusCoordinator`,
`TtsVersePlayer`; `AudioQueue` + `BuildAudioQueueUseCase` + `PlayUnit` stop rules;
`ResolveAudioAvailabilityUseCase`. **Deliverable gate: the Psalm-119-window queue test (M-AUD-1) is
green with zero audio bytes in the repo**, and the merged manifest matches §12 exactly.

> **A2 — the plug-and-play seam moves INTO A/B, out of C.** Because `device_tts` is a registry entry
> (D-AUD-E-30), the voice registry, the manifest contract, `ActiveVoiceRepository` + `active_voice_id`,
> the D-N-3 selector, and gate assertions 17–20 with the **synthetic two-voice fixture** are all
> buildable and testable in Phase 1 with **zero audio bytes and no PAD dependency**. Build them here
> (A: registry/active-voice/manifest contract; B: selector UI + the missing-chapter prompt shape),
> not in C. Phase 2 then genuinely is "a second entry appears," which is the property the owner
> asked for and the only way to prove it before there is anything to plug in. **Morgan: this is a
> re-scope of AUD-A/B upward and AUD-C downward, not net-new work.**

**Sprint AUD-B — Follow-along, entry points, marking, Phase-1 polish.**
`ReaderAudioSlot` filled; `activeVerseId` combine; yielding autoscroll; the session-scoped verse tap
+ custom accessibility action (D-AUD-E-14); the Schedule card ▶ and top-bar ▶; `StartReadAloudUseCase`
+ marking; speed; sleep timer; `AccessibilityGateTest` + guilt-copy ban-scan extension.
**This is Phase 1 shippable**, at $0 and 0 bytes, and it is the executable specification of what the
Phase-2 timing index must deliver.

**Sprint AUD-C — Delivery plumbing on placeholders (BEFORE any render spend).**
Generated pack modules + `AudioPackPlan`; `PlayAssetPackRepository`; Settings → Audio; the
release-only `audio-bundle` CI job; the corpus-asset fetch + checksum verification.
`AUD-C-1`: **internal-track upload with placeholder payloads** — proves 66 packs, `requestFetch`,
`assetsPath()`, eviction, the sideload path, and measures the real update-patch size (RE-AUD-2/3).
**This sprint is the gate on commissioning the render.**

**Sprint AUD-D — The render + the gate (the data project).**
> **A3:** the owner runs this from a standalone session — the step-by-step procedure now lives in
> [AUDIO_RENDER_RUNBOOK.md](AUDIO_RENDER_RUNBOOK.md) (`SE-T10`, brought forward). The order below is
> its summary; the runbook is authoritative.
Ordered strictly, because the last step is the one-way door: **(1)** extract the out-of-lexicon
token list from `bible.db`; **(2)** render the R-AUD-3 pilot on **`eleven_v3`** (D-AUD-E-34 — the
model is settled and has no fallback; the pilot establishes the **cap** and the **stitching**
facts, runbook Q1/Q2, not the model); **(3)** owner voice sign-off (M-AUD-6) **and** the seam
verdict against the D-AUD-E-40 ladder — if the ladder is exhausted with seams still audible, **stop
and escalate; do not spend**; **(4)** build + commit + sign off the **lexicon** (D-AUD-E-22); **(5)** record
AR-AUD-1 in `docs/data/README.md` — *before the spend, the spend is the commitment point*;
**(6)** commission the corpus render; **(7)** `tools/verify_audio_asr.py` over all 1,189 chapters;
**(8)** commit `audio/timings/<voiceId>/*` + `audio_manifest.json` (incl. the lexicon SHA + the
pinned model/voice/params); **(9)** `AudioTimingVerificationTest` green (§10.3, assertions 1–20);
reconciliation log for every over-threshold chapter.

**Sprint AUD-E — Phase 2 playback + hardening + release.**
`FileVersePlayer` + clipping + position→verse mapping; download UI wired to real packs; voice
selection; plan-window download; the consolidated device pass (screen-off, focus, lock screen,
Bluetooth, a real download/eviction/re-download, API 26/27 Opus decode, one-screen fit at N=4);
version bump + rollout.

> **Sequencing note for Morgan:** A and B are strictly first and have **no** artifact dependency —
> that is most of the argument for D-AUD-7. C is deliberately before D: *prove the delivery plumbing
> with placeholder bytes before commissioning a four-figure render*, exactly as R-AUD-3 says prove
> the pronunciation before rendering the corpus. D is a data project of Sprint-A-of-V3 size and is
> gated on OQ-AUD-1. E is last. A–B can ship to users as Phase 1 without C–E existing.

---

## 19. Open questions

### Needs the owner

- **OQ-AUD-E-1 — the §8 download-unit table** (§16.1). Book / testament / everything / "the books
  your next 30 days need" — or is a finer unit a hard requirement? This changes the pack design.
- **OQ-AUD-E-2 — D-AUD-E-19** (no audio changes in a PATCH release): confirm as a product
  commitment, given §7.5's silent-delta finding.
- **OQ-AUD-E-3 — M-AUD-3's restatement** (§16.3): accept "no `INTERNET` + exactly two new
  foreground-service permissions" as the posture gate.
- ~~**OQ-AUD-E-6 (A1) — `eleven_flash_v2` (pinned) vs `eleven_v3`, decided at the pilot with the
  price attached.** Recommend flash_v2 unless the pilot shows a difference the owner can hear.~~
  ✅ **RESOLVED (owner, 2026-07-26) — `eleven_v3`, and there is no fallback.** The owner tested
  `eleven_multilingual_v2` and `eleven_flash_v2` and rejected both on output quality: *"I do NOT
  like the output. So, I'll need to find a way to use V3 regardless."* The pilot no longer decides
  the model — see **D-AUD-E-34** (the pin) and the **A4** note revoking its fallback clause. What
  the pilot must still establish is narrower and is now runbook **Q1/Q2**: the real per-request cap
  (a 7× swing in seam count) and whether Request Stitching genuinely does not work on v3. The
  seam cost this question was originally weighing is not avoidable any more — it is worked through
  the **D-AUD-E-40** remedy ladder, and **RE-AUD-15 is retired** (it priced electing v3 as a risk;
  v3 is now the requirement).
- **OQ-AUD-E-7 (A2) — the missing-chapter prompt's third option.** D-AUD-E-29 forbids substitution
  but permits *offering*, so the prompt reads: "Download <size>" · "Read it with the device voice" ·
  Cancel. Confirm the middle option is wanted — it is the one place the user can end a session in a
  voice other than the one they selected, **by choosing it explicitly**. Removing it is a one-line
  change and makes the rule absolute; keeping it satisfies U-AUD-7 more generously.
- **OQ-AUD-9 (Maya's) — AR-AUD-1**, now with a concrete gate: it must be recorded in
  `docs/data/README.md` at **step (5) of Sprint AUD-D**, before the render is commissioned.
- **OQ-AUD-4 (Maya's)** — the verse-tap gesture change. Engineering position: implementable in one
  place, **but only with the custom accessibility action** (D-AUD-E-14).
- **OQ-AUD-6 (Maya's)** — 24 vs 32 kbps. Engineering input: 24 kbps ⇒ ~853 MB total, largest pack
  ~47 MB, every pack under Play's 200 MB cellular threshold. 32 kbps ⇒ ~1.14 GB, largest pack
  ~63 MB — still legal, still under thresholds, ~34% more bytes. Not an architectural constraint
  either way; a pure listening call.

### Needs Morgan

- **OQ-AUD-E-4** — is `AUD-C-1` (the placeholder internal-track upload) budgeted as a real ticket
  with a real Play release cycle? It is the cheapest de-risking in the whole plan and it has a
  calendar cost (review turnaround) that must be sequenced, not assumed.
- ~~**OQ-AUD-E-5** — who owns the render machine, and where does the ~$250–800 spend sit in the
  process? D-AUD-E-6 assumes a human uploads corpus assets once per render.~~
  **RESOLVED (owner, 2026-07-26): the owner owns the render machine, the vendor account and the
  spend, and will drive the render himself — possibly from a separate working session.** That
  last clause carries a requirement: the render must be runnable by someone who does not have
  this document's context loaded. The pieces already exist but are scattered across
  D-AUD-E-21 (the `eleven_flash_v2` pin and why), D-AUD-E-22 (lexicon committed and signed off
  *before* the corpus render), D-AUD-E-23 (`with-timestamps`, Forced Alignment fallback, never
  estimated) and Sprint AUD-D's 9-step order. **They must be assembled into one self-contained,
  owner-runnable render runbook** — inputs, exact request parameters, the step order, what to
  check between steps, and what "done" looks like — rather than left as decisions to be
  reassembled. Tracked as a deliverable, not a question.
- **OQ-AUD-8 (Maya's)** — whole-day playback in the first release. Engineering view: it is ~a day of
  work (a `PlayUnit` arm + a top-bar action) and is the cleanly droppable one.

### Needs research before Sprint D

- ASR threshold calibration on the pilot (RE-AUD-5) — the WER floor achievable against KJV
  orthography with a modern ASR model is genuinely unknown until measured.
- Whether the chosen vendor's timestamps are **character**-level or **word**-level, and whether they
  survive the encode step (some pipelines report timings against the pre-encode PCM). This decides
  how `render_audio.py` derives verse boundaries and is worth one API call to settle.
