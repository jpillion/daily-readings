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
| Contains | per voice: `voiceId`, `displayName`, `shortCode`, `order` (priority), `packing`, `packNamePattern`, the pack-name list, approximate coverage | the full manifest of §7A.3 |
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

The requirement makes a new state reachable: **voice A has Genesis, voice B has Psalms.** This must
be decided, not discovered.

**Decision D-AUD-E-24 — resolution is per *play unit*, deterministic, and data-driven:**

1. the user's **preferred voice** (`audio_preferred_voice_id`), **if it covers the whole play unit**;
2. else the highest-priority **installed, usable** voice that covers the **whole** play unit —
   ordered by the catalog's `order` field, then `voiceId` lexically for voices the catalog does not
   list (a later app's pack), so the order is total and reproducible;
3. else **device TTS** for the whole unit.

**Never mix voices inside a play unit.** A portion is one continuous listening experience; changing
narrator between Genesis 1 and Genesis 2 — let alone mid-chapter — would read as a defect, not a
feature. The unit of resolution is the `PlayUnit`, not the chapter. `BrowseChapterUnit` resolves
per chapter *as it advances*, which is correct: continuous browsing genuinely is a sequence of
units, and a voice change at a book boundary the user chose to cross is legible.

Consequences worth naming:

- **Partial coverage is first-class, not an error.** An NT-only voice is a legal, shippable pack
  set; it simply never wins for an OT unit. `coverage` is what makes this expressible, which is why
  it is declared rather than inferred from which files happen to be present.
- **Falling back to device TTS for a portion the preferred voice half-covers is the *correct*
  outcome**, and the UI must say why in one honest line rather than silently sounding different.
- The rule is a **pure function** (`ResolveVoiceForUnitUseCase(units, installedVoices, preferred)`),
  so every branch — mixed, partial, none, rejected-pack, preferred-not-installed — is JVM-testable
  with **synthetic manifests and zero audio bytes**. That is how P3 is satisfied before a second
  voice exists.

### 7A.8 What this invalidates in the rest of this spec — and what it validates

**Changed** (all marked in place): D-AUD-E-5's pack-count half (§7.3 → D-AUD-E-27); D-AUD-E-3's
timing paths (§6.2, voice-scoped); §13.3's `audio_voice_source` key → `audio_preferred_voice_id`;
§9's total-bundle ceiling is now per-voice-aware (§9.2 note); the corpus tag is voice-scoped (§8.2).

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
| 17 | **Synthetic-second-voice fixture** (test-only, no bytes): the resolution rule (D-AUD-E-24) returns the documented answer for preferred-covers / preferred-partial / mixed / none; the chooser branch renders; an unknown `manifestVersion` is rejected and does **not** enter `installed()` | P3 — the multi-voice machinery is proven while invisible |

Mutation targets: flip one coverage range by one chapter (→ 14); point `pathTemplate` at a
non-existent file (→ 15); set the fixture voice's `manifestVersion` to 99 and assert it is rejected
rather than used (→ 17); make `packsFor` return two packs for one chapter (→ 16); change the
synthetic voice's coverage so it half-covers a portion and assert the resolver falls to device TTS
rather than mixing (→ 17).

### 7A.10 The voice selector UI — the D-N-3 idiom, literally

**Decision D-AUD-E-28 — `AudioVoiceSelector` follows `ReaderVersionSelector`'s shape exactly: 0 or 1
usable installed voice ⇒ **no chooser** (Settings → Audio shows the voice as static text: "Voice:
Standard voice" or "Voice: Device voice"); more than one ⇒ a `SettingsDropdownRow` chooser (the S14
idiom) writing `audio_preferred_voice_id`. The multi-voice branch is built and tested from day one
and is unexercised in production while there is one voice.**

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
  `SettingsRepository` (`data/prefs`): `audio_voice_source` (string, default `DEVICE`),
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

---

## 16. Where I disagree with the PRD, or think it is priced wrong

Recorded rather than silently resolved, per Maya's own review note.

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
8. **Agreement, recorded:** D-AUD-7 (two-phase) is right, and the reason is stronger than the PRD
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
`tools/render_audio.py` (pilot first, per R-AUD-3, with owner sign-off M-AUD-6 and a pronunciation
lexicon built from what the pilot exposes); `tools/verify_audio_asr.py`; the full corpus;
`audio/timings/*` + `audio_manifest.json` committed; **`AudioTimingVerificationTest` green (§10.3)**;
reconciliation log in `docs/data/README.md`; AR-AUD-1 recorded **before the spend**, not before ship.

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
- **OQ-AUD-E-5** — who owns the render machine, and where does the ~$250–800 spend sit in the
  process? D-AUD-E-6 assumes a human uploads corpus assets once per render.
- **OQ-AUD-8 (Maya's)** — whole-day playback in the first release. Engineering view: it is ~a day of
  work (a `PlayUnit` arm + a top-bar action) and is the cleanly droppable one.

### Needs research before Sprint D

- ASR threshold calibration on the pilot (RE-AUD-5) — the WER floor achievable against KJV
  orthography with a modern ASR model is genuinely unknown until measured.
- Whether the chosen vendor's timestamps are **character**-level or **word**-level, and whether they
  survive the encode step (some pipelines report timings against the pre-encode PCM). This decides
  how `render_audio.py` derives verse boundaries and is worth one API call to settle.
