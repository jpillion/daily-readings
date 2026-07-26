# Design spec: Read aloud (audio) — UI/UX

> **Owner:** Priya (design/UI eng) · **Status:** Design for review — **amendment pass P2 applied**
> after Maya's PRD amendments A1–A3 and three owner decisions · needs owner sign-off on the items
> in §12 · **Last updated:** 2026-07-25 (amendment P2) ·
> **Branch:** `claude/audio-read-aloud-options-vgb793`
>
> **Input:** [docs/PRD-audio.md](../PRD-audio.md) (Maya). This doc owns **what it looks like and how
> it behaves**; it does not re-litigate anything locked in PRD-audio §7 (`D-AUD-1…28`). It defers
> mechanism (MediaSession/ExoPlayer/TTS wiring, PAD pack layout, the pack manifest + voice registry)
> to Diego and sequencing to Morgan. Design decisions here are numbered **`D-AUD-UI-n`** so they
> never collide with Maya's `D-AUD-n`.
>
> **Owner decisions already locked and honoured throughout:** delivery is Play Asset Delivery
> (~853 MB whole Bible, **the atomic unit is a book pack** — ~8 MB median, 0.3 MB for 2 John,
> ~47 MB for Psalms — Google-hosted, on demand); the voice source is **ElevenLabs** (D-AUD-22);
> **no "Narrated by AI" or synthetic-voice disclosure anywhere** — no such string exists in this
> design, and the voice is named by *origin and quality* only (D-AUD-5).

### Amendment P2 — 2026-07-25

| # | What changed | Where |
|---|---|---|
| P2.1 | **Voice source settled: ElevenLabs (D-AUD-22).** No design consequence beyond wording — the voice is still named by origin/quality only, per D-AUD-5, which is unchanged and remains absolute. | §11 wording only |
| P2.2 | **Packs are plug-and-play (D-AUD-23/24, FR-AUD-28/29).** The voice selector is designed, placed and specified — and **renders as a two-entry row in release one and as nothing in Phase 1**, with the multi-narration branch built, pinned and unexercised. The D-N-3 posture, verbatim. | §7.1, D-AUD-UI-14 |
| P2.3 | **One active voice, app-wide and exclusive (D-AUD-26/27/28, FR-AUD-30).** The six per-book download states gain a **voice dimension**; the not-downloaded prompt is promoted from an edge case to the **primary path into downloading**; whole-voice deletion is designed; and the "you have this book, but not in the voice you're listening in" copy problem is resolved. | §7.2–7.4, §8, D-AUD-UI-15…18 |
| P2.4 | **A1 sizing absorbed.** Every size in this doc was restated against the real mechanism: the pack is a **book**, so "Today's readings · 2.4 MB" is gone and the small units are honestly labelled **book-sets**. ~870 MB → ~853 MB throughout. | §7.3 |
| P2.5 | **My two placement calls were adopted by product** as D-AUD-19 (root transport, `ReaderAudioSlot` retired) and D-AUD-20 (stats cap 45 % → 30 %), both flagged to the owner as OQ-AUD-10/11. Recorded; no change to the design. | §2, §12 |
>
> **Companion reading:** [reader-portion-view.md](reader-portion-view.md) (the two reader contexts),
> [tile-hint-provider.md](tile-hint-provider.md) (the reactive-hint idiom this design reuses),
> [CLAUDE.md](../../CLAUDE.md) (S16/S18/S20 one-screen budgets, S14 `SettingsDropdownRow` idiom,
> D-S17-1/D-S20-1 no-guilt copy discipline).

---

## 0. The design in one paragraph

Read aloud adds **exactly one new persistent surface**: a 56 dp **Listen bar** that docks above the
existing `NavigationBar` in `RootScaffold` **only while a session is live**, and is **paid for out of
the Schedule's stats-panel height cap, not out of the readings**. It is the app-wide answer to
"playback outlives the screen" — it follows you onto the Schedule, into Settings, everywhere — and
tapping it opens a `ModalBottomSheet` with the full transport, speed, sleep timer, follow-along
toggle, and the download state for the current passage **in the one active voice**. Playback is started from a
**height-neutral trailing ▶ button** on each Schedule reading card (48 dp, inside the existing
48 dp checkbox row — 0 dp of new height) and from a top-bar action for the whole day. In the reader
the reserved `activeVerseId` becomes a **filled `secondaryContainer` highlight with a leading rule
and a spoken "Now playing" state** — never colour alone — with autoscroll that yields to the user's
thumb and re-arms from a visible chip. Downloads get their own Settings section and a pushed
"Downloaded audio" screen; **no byte lands unasked**, and pressing play with nothing downloaded
opens a two-choice sheet rather than dead-ending or silently substituting a different voice. **One
voice is active app-wide (D-AUD-26)** — coverage is evaluated only against it, a gap is always a
prompt to download *that* voice, and the app never plays a voice the user did not choose; the
device voice is a first-class entry in the same selector (D-AUD-27), never a consolation prize.

---

## 1. What already exists that this builds on (ground truth from the code)

| Seam | File | State today |
|---|---|---|
| Root chrome | `ui/navigation/AppNavHost.kt` → `RootScaffold` | `Scaffold` with a `NavigationBar` (Schedule \| Bible) as `bottomBar`, a `SnackbarHost`, and `AppNavHost` as content. **Every screen in the app renders inside it**, including pushed Settings. |
| Reader chrome | `bible/ui/reader/ReaderScreen.kt` | Inner `Scaffold`: `TopAppBar` (pencil + title + version), `HorizontalPager` of chapters `weight(1f)`, pinned `ReaderFooterHint`, `bottomBar = { ReaderAudioSlot() }`. |
| Audio slot | `bible/ui/reader/ReaderAudioSlot.kt` | Renders **nothing**. Reserved by D-V3-14. |
| Highlight seam | `bible/ui/reader/ReaderUiState.kt` | `Content.activeVerseId: Long? = null`, always null; `VerseItem(isActive)` currently only swaps text colour to `onPrimaryContainer`. |
| Verse tap | `ReaderScreen.VerseItem` | Whole verse row is `clickable(role = Role.Button)` ≥48 dp → external Bible app (Sprint H). Spoken label: `"Open <Book> <ch>:<verse>. <text>"`. |
| Reading card | `ui/day/DayContent.kt` `ReadingCard` | `Card(onClick)` → opens **and** marks read (Sprint 00O). Row = `[Column(streamTitle?, reference) weight(1f)] [Checkbox 48 dp]`, `padding(h=16, v=6)`. |
| Schedule chrome | `ui/day/DayReadingsScreen.kt` | `TopAppBar` (one-line title + optional "Today" + date-picker + settings), day `HorizontalPager` `weight(1f)`, `HorizontalDivider`, `StatsContent` capped at **`maxHeight * 0.45f`** and internally scrollable. |
| Settings idiom | `ui/settings/SettingsScreen.kt` | `SectionTitle` + 56 dp rows; `SettingsDropdownRow` (S14) = value + `ArrowDropDown`, `Role.DropdownList`, spoken "label, value", anchoring a `DropdownMenu` of `SelectableMenuItem`s. Switch rows are 56 dp `toggleable(role = Role.Switch)`. |
| A11y gate | `ui/AccessibilityGateTest.kt` | Measures **touch** bounds ≥48 dp on every authored control, asserts `contentDescription` substrings, asserts stock M3 Slider ≥44 dp. **Must stay green.** |

**Two facts that shape everything below.** First: `RootScaffold`'s `NavigationBar` costs ~80 dp on
*every* screen, and the reader's `ReaderAudioSlot` is a *second* bottom bar nested inside it — so a
naive transport bar produces **three** stacked bars. Second: the Schedule at N = 4 (M'Cheyne) has
roughly **53 dp** of slack after four sprints of tuning (S16, S18, S20, the N-stream fix). Any design
that spends more than that on the Schedule re-breaks a guarantee the project has paid for four times.

---

## 2. The bottom-bar problem, resolved

### D-AUD-UI-1 — One transport, at the app root, docked above the nav bar, only while live

The Listen bar lives in **`RootScaffold`**, not in the reader:

```
bottomBar = {
    Column {
        ListenBar(state = listenState, …)   // 56dp — composes NOTHING when Idle
        NavigationBar { Schedule | Bible }  // ~80dp — unchanged, always present
    }
}
```

Rationale, in order of weight:

1. **Playback outlives the screen (§3) is only solvable at the root.** A transport nested in the
   Bible graph disappears the moment the user taps Schedule — which is precisely the "how do I stop
   this?" failure. One bar, one place, every screen.
2. **The nav bar must not be replaced.** Hiding tab navigation while audio plays would trap a
   listener who wants to check tomorrow's readings, and would make the bar a mode. Co-equal tabs
   (D-V3-16) stay co-equal.
3. **It must not overlay content.** A floating mini-player costs 0 dp of layout but occludes the
   bottom of whatever is under it — and the Schedule's bottom band is the stats panel, which is
   *not* scroll-past-able content. Docked and in-layout is honest.
4. **Idle costs exactly zero.** `ListenState.Idle` emits no composable at all, so the S16/S18/S20
   resting-state budgets and the one-screen-fit guarantee are **byte-for-byte unchanged** when
   nobody is listening. The budget question is only ever "what does an *active session* cost", and
   §9 answers it with a net *gain*.

### D-AUD-UI-2 — `ReaderAudioSlot` is retired as a bottom bar; its intent is honoured at the root

`ReaderAudioSlot` is deleted from `ReaderScreen`'s `bottomBar` and the file removed. This
**supersedes the placement half of D-V3-14, not its intent** — D-V3-14 reserved a home for the
transport so follow-along would be an additive drop-in, and that is exactly what happened; the
transport simply turned out to belong one level up, because playback is an app-level session, not a
reader-screen state. Keeping the slot would produce two bars stacked inside a third, which is the
problem this section exists to prevent. The reader's other reserved seam — `activeVerseId` — is
cashed in unchanged (§4).

The reader's pinned `ReaderFooterHint` **stays** (it is 0 dp of new cost and becomes the teaching
surface for the gesture change, §5).

### D-AUD-UI-3 — On the Schedule, the Listen bar is paid for by the stats cap, not the readings

While a session is live, `DayReadingsPagerScreen`'s stats-panel cap drops from **45 % → 30 %** of the
available height:

```kotlin
val statsMaxHeight = maxHeight * if (listening) 0.30f else 0.45f
```

The panel already scrolls internally, so this is a *cap* change, not a content change — no stat, no
strip, no legend entry is removed, and nothing about the stats surface is conditional on audio
except how much of it is visible at once. It returns more height to the readings column than the
Listen bar takes (§9: readings gain **+103 dp** of slack at N = 4 while listening, versus +53 dp
idle), so **the M'Cheyne 4-stream case fits better while playing than while idle**.

Rationale: while listening, "Year at a glance" is the lowest-value thing on the screen and the
readings are the highest — the user is following a reading, not auditing a year. Ranking the panel
below the readings under pressure is the same judgement S15 made when it capped the panel in the
first place.

### The two surfaces

**Mini-player (the Listen bar, 56 dp)** — glanceable, one-thumb, always present while live:

```
┌──────────────────────────────────────────────────────────────┐
│▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│ 2dp chapter progress
│  ⏸    Genesis 1–2                                       ✕    │
│ 48dp  Law & History · Playing                          48dp  │  56dp total
├──────────────────────────────────────────────────────────────┤
│   📅 Schedule            │            📖 Bible               │  ~80dp NavigationBar
└──────────────────────────────────────────────────────────────┘
```

- Left: play/pause **toggle** (one control, not two), 48 dp.
- Centre: the passage reference via `ReadingFormatter.format` (**the one home** — "Psalm 119:1–40",
  "2 John 1; 3 John 1"; never a second formatter), and a status line. The whole centre region is the
  tap target that expands the sheet.
- Right: **✕ Stop**, 48 dp. This is the answer to "how do I stop it" (§3) and it is deliberately not
  hidden behind the sheet.
- Top edge: 2 dp determinate `LinearProgressIndicator` for position within the current chapter —
  decorative, `clearAndSetSemantics {}` (the sheet carries the spoken position).

**Expanded sheet (`ModalBottomSheet`)** — the same idiom as `BookChapterPickerSheet` (Sprint C/G),
so it is a pattern the app already teaches:

```
┌──────────────────────────────────────────────┐
│                   ────                       │  drag handle
│               Genesis 1–2                    │  titleLarge
│           Law & History · Today              │  bodyMedium, onSurfaceVariant
│                                              │
│        ⏮        ⏸ (64dp)        ⏭            │  prev/next verse-or-chapter
│                                              │
│  ▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░  4:12 / 9:40       │  position (not seekable — §6)
│                                              │
│  Follow along                        [on ]   │  56dp switch row
│  Speed                            1.0× ▾     │  SettingsDropdownRow idiom
│  Sleep timer                         Off ▾   │  SettingsDropdownRow idiom
│  ────────────────────────────────────────    │
│  High-quality voice · Genesis downloaded     │  source status (§7)
└──────────────────────────────────────────────┘
```

Speed and sleep timer reuse `SettingsDropdownRow` verbatim rather than inventing chips — same
component, same 56 dp, same `Role.DropdownList` semantics, same "label, value" speech. That is the
house idiom and it keeps the a11y gate's job trivial.

---

## 3. Playback outliving the screen — the state machine

`ListenState` is app-scoped (an `@ActivityRetainedScoped` holder, the same shape as `ReaderHandoff`
and `InAppUpdateState`, which is the pattern this codebase already uses for exactly this problem):

```kotlin
sealed interface ListenState {
    data object Idle : ListenState                                    // bar composes nothing
    data class Preparing(val ref: String, val unit: PlayUnit) : ListenState
    data class Playing(val ref: String, val activeVerseId: Long?, …) : ListenState
    data class Paused(val ref: String, val activeVerseId: Long?, …) : ListenState
    data class NeedsSource(val ref: String, val reason: SourceGap) : ListenState  // §7
    data class Failed(val ref: String, val reason: PlaybackFault) : ListenState
}
```

| From | Trigger | To | What the user sees |
|---|---|---|---|
| Idle | ▶ on a card / top bar / reader | Preparing | Bar **slides in** (`AnimatedVisibility`, `expandVertically` 180 ms); indeterminate glyph; reading marked read (FR-AUD-15) |
| Preparing | source ready | Playing | ⏸ glyph, status "Playing" |
| Preparing | source missing | NeedsSource | Bar shows the passage + "Not downloaded"; sheet auto-opens with the two-choice block (§7) |
| Playing | ⏸ / headset / focus loss | Paused | ⏵ glyph, status "Paused" |
| Playing | end of unit (FR-AUD-4 stop rules) | Idle | Bar **slides out**; no lingering "finished" chrome, no replay affordance — re-entry is tapping the reading again |
| any live | ✕ on the bar, or Stop on the notification | Idle | Bar slides out immediately |
| any live | user switches tab / opens Settings | unchanged | **Bar persists** — it is root chrome |
| any live | app backgrounded / screen off | unchanged | Media notification + lock screen are the transport; the bar is exactly as left on return |
| Playing | reader text fails to load for a page | unchanged | **Playback continues.** Audio does not depend on the text render; the reader shows its own error state and the bar keeps going. Explicitly designed, not incidental. |

**The bar is never dismissible by swipe.** ✕ is the only in-app stop. A swipe-away gesture on a
transport is a well-known accidental-stop generator, and this bar has a 48 dp explicit control.

**No auto-start, ever** (NFR-AUD-C). Nothing on launch, on tab switch, on reading tap, on
notification tap. The only transitions into a live state originate from a ▶ the user pressed.

---

## 4. Follow-along

### The highlight — `D-AUD-UI-5`

The active verse is drawn as a **filled, rounded `secondaryContainer` block with a 3 dp leading rule
in `primary`**, its label bumped to `SemiBold`, and text in `onSecondaryContainer`:

```
   ┃▓ 3  And God said, Let there be light: and there was light.  ▓   ← active
     4  And God saw the light, that it was good…                     ← normal
```

```kotlin
// pseudocode, inside VerseItem
val active = verse.canonicalId == activeVerseId
Row(
    modifier = Modifier
        .fillMaxWidth()
        .then(if (active) Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
        else Modifier)
) {
    if (active) Box(Modifier.width(3.dp).fillMaxHeight()
        .background(MaterialTheme.colorScheme.primary))
    Text(
        text = body,                                    // label SemiBold when active
        color = if (active) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .padding(horizontal = if (active) 8.dp else 0.dp, vertical = 4.dp)
            .semantics { if (active) stateDescription = playingState }   // "Now playing"
    )
}
```

Why this and not the current code: `VerseItem` today paints active text `onPrimaryContainer` **on a
plain `surface` background** — an unpaired M3 role combination that is a genuine contrast defect in
both light and dark, and it would be the only signal. The container/on-container pair is
contrast-guaranteed by the M3 tonal system in light, dark, and under dynamic color, which is the
only way to make this safe on a device whose palette we do not control.

**Never colour alone** — three redundant signals: the fill, the **3 dp leading rule** (a shape/position
cue that survives greyscale and every colourblind profile), and the **spoken
`stateDescription = "Now playing"`**.

**Font scale.** Nothing here is a fixed height: the fill and the rule are `fillMaxHeight` of a row
whose height is text-driven, and the existing `heightIn(min = 48.dp)` remains the floor. The in-app
0.85×–1.5× multiplier composes with system scaling exactly as it does today; the only visual change
at 1.5× is that a highlighted verse is a taller block. Verified in the Robolectric suite at both
ends of the range (§10).

### Autoscroll — `D-AUD-UI-6`

```kotlin
// Keep the active verse at ~1/3 from the top: enough lead to read ahead, no jump-to-top jitter.
LaunchedEffect(activeVerseId, followEnabled) {
    if (!followEnabled || activeVerseId == null) return@LaunchedEffect
    val index = indexOfVerse(activeVerseId) ?: return@LaunchedEffect
    listState.animateScrollToItem(index, scrollOffset = -(viewportHeightPx / 3))
}

// Yield to the thumb the instant it touches (FR-AUD-12).
LaunchedEffect(listState) {
    listState.interactionSource.interactions
        .filterIsInstance<DragInteraction.Start>()
        .collect { followEnabled = false }
}
```

Yielding keys off **`DragInteraction.Start`**, not `isScrollInProgress` — the latter is also true
while *we* are animating, which would make autoscroll switch itself off on its first move. Only a
real pointer drag (or a fling, which starts with one) counts.

Re-arming, per FR-AUD-12, is explicit **or** structural:

- **Explicit:** a **"Follow along" chip** appears pinned bottom-centre over the verse list whenever
  `followEnabled == false` and a session is live. `AssistChip` with a `Icons.Filled.Height`-style
  glyph, `heightIn(min = 48.dp)`, tag `listen-follow-chip`, spoken "Follow along, resumes scrolling
  to the verse being read". Tapping re-arms and immediately scrolls to the active verse. It
  **overlays** (`Box` alignment `BottomCenter` with 16 dp inset) so it costs **0 dp** of layout.
- **Structural:** playback advancing to a new chapter re-arms automatically — the user has left the
  region they were manually reading.
- Also mirrored as the sheet's "Follow along" switch, so it is reachable without hunting the chip.

The chip is the only new floating element in the design, and it exists because FR-AUD-12 demands an
explicit ≥48 dp re-engage affordance and a switch buried in a sheet is not one.

---

## 5. The verse-tap collision — resolved (OQ-AUD-4)

**The conflict.** Sprint H shipped "tap a verse → open it in your external Bible app", taught by a
reactive pinned footer hint. FR-AUD-13 wants that same tap for seek. Maya's position: while a session
is active, tap seeks and the external tap-out moves to long-press.

**`D-AUD-UI-4` — Adopt Maya's rule, but do not let long-press carry any accessibility weight.**

| Context | Tap | Long-press | TalkBack / Switch Access |
|---|---|---|---|
| **No live session** (today's world) | opens externally | *(none — unchanged)* | one click action, spoken `"Open Genesis 1:3. …"` — **byte-for-byte Sprint H** |
| **Live session in the reader** | **seeks** to that verse | opens externally | **two named custom actions**: "Play from here", "Open on Blue Letter Bible" |

Three things make this safe rather than clever:

1. **The a11y path is never the gesture.** While a session is live each verse declares
   `customActions = listOf(CustomAccessibilityAction("Play from here", …),
   CustomAccessibilityAction(openLabel, …))` with the primary `onClick` = seek. TalkBack surfaces
   both by name in its actions menu and Switch Access gets both as scannable actions — so a
   screen-reader user ends up with *more* than they have today (today: one unnamed click), and
   **nothing depends on holding a finger down**. This removes the entire real cost of long-press;
   what remains is a sighted-user shortcut.
2. **Discoverability is already funded.** `ReaderFooterHint` exists, is pinned at the bottom of the
   reader, and already reactively names the user's external app. It simply swaps string while a
   session is live: *"Tap a verse to open it on Blue Letter Bible"* → *"Tap a verse to play from
   there · hold to open on Blue Letter Bible"*. Zero new chrome, zero new dp, and it is the one
   surface in the app whose entire job is teaching this gesture. Reusing it is the reason this
   gesture change is affordable at all.
3. **The mode is short, visible and self-announcing.** The Listen bar is on screen the whole time
   the rule differs, so "why did tapping do something else" always has a visible cause 56 dp below.

**What long-press honestly costs, stated plainly** (owner should read this before signing OQ-AUD-4):
a ~500 ms hold with a haptic instead of an instant tap; no visual affordance of its own (paid by the
footer hint); one more thing to learn for users who use both features; and a small risk of an
accidental long-press while scrolling on large-font layouts where verses are tall. It costs
**nothing** for TalkBack or Switch Access users because of (1). The alternative Maya considered —
long-press to *seek* — was rejected here too: it hides the interaction the follow-along experience is
built around behind the undiscoverable gesture, and inverts which action is optimised for the mode
the user is actually in.

**Superscription taps** keep their existing clamp semantics: seek targets verse 1's audio; the
external action still clamps verse 0 → 1 (Sprint H, unchanged).

---

## 6. Starting playback — the entry points

### Schedule reading card — height-neutral (FR-AUD-17) — `D-AUD-UI-7`

The ▶ is a **trailing 48 dp `IconButton` immediately left of the existing checkbox**, inside the row
the checkbox already sizes:

```
┌────────────────────────────────────────────────────────────┐
│  Law & History                                             │
│  Genesis 1–2                                    ▶     ☑    │   ← unchanged height
└────────────────────────────────────────────────────────────┘
     weight(1f)                                48dp   48dp
```

- **Height cost: 0 dp.** The row's height is already driven by the 48 dp `Checkbox` plus 6 dp
  vertical padding; a second 48 dp control in the same row changes nothing. This is the whole reason
  it is a trailing element and not a leading one, a footer, or a second line.
- **Width cost: 48 dp**, taken from the reference column (~299 dp → ~251 dp on a P7P). `Psalms
  119:129–176` and `2 John 1; 3 John 1` fit comfortably; the *stream title* is the tight one at
  N = 4 ("Personal — Psalms & Prophets" at `labelMedium`), so it gains `maxLines = 1` +
  `TextOverflow.Ellipsis`. Legibility of the truncated title at large font is a device-pass item.
- **No tap ambiguity.** The card tap keeps its shipped meaning exactly (open + mark, Sprint 00O); the
  ▶ plays + marks and does **not** open. Different glyph, different spoken label, and the ▶ consumes
  its own click so it never reaches the `Card(onClick)`.
- **Spoken:** `"Read aloud, Genesis 1–2"` (not a bare "Play" — a screen-reader user scanning four
  cards must hear *which* reading each button plays). Tag `listen-{streamNumber}`.
- **While that reading is the live one**, its ▶ becomes ⏸ with `stateDescription = "Playing"`, so the
  card is a status indicator too — this is what stops the user hunting the Listen bar to pause the
  thing they are looking at.

### Schedule top bar — whole day (FR-AUD-18) — `D-AUD-UI-8`

An `IconButton` (`Icons.Filled.PlaylistPlay`) in the existing `actions` row, tag `listen-day`,
`contentDescription = "Read aloud, all of today's readings"`. **0 dp cost** — no new row, honouring
Sprint 00H's deliberate removal of the whole-day button row.

**Hidden (not disabled) when there is nothing to play**: on a `NoScheduledReadings` (Feb 29) or
`LoadFailed` day. A disabled control that is *never* enabled on that day is noise.

**Risk, flagged:** the top bar can carry four actions at once (jump-to-today `TextButton` + date
picker + settings + this). At default font that fits; at 1.5× in-app scale plus a large system font
the title ellipsis absorbs it, but it is tight. This is the one place the design might have to give.
If the owner drops whole-day playback (Maya's OQ-AUD-8), this action simply doesn't exist and nothing
else in the design changes — it is deliberately the most severable piece.

### Reader — `D-AUD-UI-9`

**No new bar, no new row.** A "Read aloud" prompt row rendered by the idle Listen bar was considered
and rejected: it would violate D-AUD-UI-1's "idle costs exactly zero" and put permanent chrome under
every reader for a control most sessions start from the Schedule anyway.

Instead the reader gets a **▶ in its own `TopAppBar` actions**, right of the version selector, tag
`listen-reader`, spoken `"Read aloud, Genesis 1"`. 0 dp cost (existing row), and it is where a reader
looks for chapter-level actions (the pencil/picker is already there). The play *unit* follows
`ReaderContext` per FR-AUD-5 — Browse → this chapter, continuing across book boundaries; Reading →
the whole combined portion, stopping at its end.

---

## 7. Voice, downloads and the honest inline moments

> **Rewritten in amendment P2** for the owner's three decisions. The governing sentence for this
> whole section, and the one to check any future change against:
> **you chose a voice; the app reads in that voice; a gap is a prompt to download *that* voice's
> pack; and nothing else is ever put in your ears without you asking for it** (D-AUD-26).

### 7.1 The voice selector — `D-AUD-UI-14`

**One row is the single home of voice selection, and it renders three ways.** There is no second
voice surface anywhere in the app — not in the sheet, not on the downloads screen, not in a dialog.

| Registry contents | The row renders as | When |
|---|---|---|
| Device voice only | **absent** — nothing to choose, no chrome | **Phase 1** |
| Device voice + one narration | a `SettingsDropdownRow` with **exactly two entries**: "Device voice", "High-quality voice". No pack name, no narration identity, no version chrome (**D-AUD-24**). | **Phase 2, release one — what ships** |
| Device voice + two or more narrations | the **same row, same tag**, listing every installed narration by its **pack-declared display name** plus Device voice | **only if a second narration ever exists** |

The third branch is **designed, tagged, and pinned by test, and renders as nothing today** —
deliberately the `ReaderVersionSelector` posture (D-N-3), where the multi-version dropdown was built
and tested while production shows a static "KJV". It reads its entries from the pack registry, so a
new narration appearing is a *download*, not a release (FR-AUD-28) — the branch exists precisely so
that promise is already true on the day it is first exercised, rather than retrofitted then.

**Placement: at the top of the "Read aloud" section**, above Wi-Fi-only, speed and downloads. Every
row beneath it is *scoped by* it — coverage, sizes and the whole downloads screen mean "for the
active voice" — so reading order must establish the scope before the things scoped by it. This is
the same reasoning that put "Reading plan" at the top of Settings (D-ALT-18).

**The device voice is an ordinary entry, listed first** (D-AUD-27): it costs zero storage, works the
instant the app is installed, needs no Play, and follows the voice and speed the user already tuned
system-wide. Nothing in the copy frames it as a fallback, a downgrade, or a consolation — it is
never labelled "basic", "standard" or "(free)", and it is never the thing the app *falls back to*,
only the thing a user *chooses*.

```
Read aloud                                            [SectionTitle]
Voice                        High-quality voice ▾     [SettingsDropdownRow  audio-voice-dropdown]
     ▸ Device voice                                   [audio-voice-device]
     ▸ High-quality voice                             [audio-voice-high]
Download over Wi-Fi only                      [ on]   [56dp Switch  audio-wifi-only-toggle]
Playback speed                               1.0× ▾   [SettingsDropdownRow  audio-speed-dropdown]
Downloaded audio                     1.24 GB used →   [56dp row  audio-downloads-row]
The high-quality voice downloads only when you ask.   [bodyMedium caption  audio-help]
Books you download stay on your phone until you
delete them. Audio improvements arrive with app
updates.                                              [the standing D-AUD-18 sentence]
```

- **Naming honours D-AUD-5 exactly**: origin and quality, claiming nothing in either direction. The
  ElevenLabs decision (D-AUD-22) changes **no user-visible string** — the vendor is never named, and
  **no disclosure string of any kind exists in this design**. None may be added without re-opening
  D-AUD-5.
- **A narration with nothing downloaded is still selectable, not disabled.** *(This reverses the
  pre-P2 draft, which greyed it out using the S14/S15 teaser idiom.)* Under D-AUD-26 an empty voice
  is a legitimate, coherent state — you have chosen it, and every passage is a download prompt. The
  teaser idiom is for things you *cannot* have (MySword not installed); this is something you can
  have and simply have not downloaded yet. Disabling it would make the primary path into downloading
  unreachable from the place the user goes to choose a voice.
- **No Play Store on the device**: the row pins to Device voice, the downloads row is hidden, and
  the caption becomes the single honest sentence (FR-AUD-22, `audio_no_play_store`). Said once, in
  Settings, never as a popup.
- **Switching voice never touches downloads.** Selecting a different voice downloads nothing,
  deletes nothing, and starts no transfer. It changes what the app reads in; the bytes are the
  user's to manage on the downloads screen.

#### Switching voice while something is playing — `D-AUD-UI-18`

Diego owns the mechanism; this is what the user sees:

1. **Playback pauses.** It does **not** continue in the old voice — that would be the D-AUD-26
   substitution, merely inverted — and it does **not** auto-restart (NFR-AUD-C is absolute).
2. **Position is preserved at the current verse.** The queue is verse-addressed and the timing index
   is per verse, so verse-granular resume is free; tapping ⏵ continues from where you were, in the
   new voice.
3. The Listen bar shows the new voice's state for the current passage: **"Ready to play"** if it
   covers the passage, or the `NeedsSource` state (§7.4) if it does not.

Recommended over the alternative "applies next play", which makes the setting feel broken: the thing
coming out of the speaker would contradict the thing the user just selected. Flagged in §12.

### 7.2 The voice dimension on download state — `D-AUD-UI-15`

The six states from the pre-P2 draft are unchanged as *states*; what changed is what they are a
state **of**. Under D-AUD-26 they describe **(book × active voice)**, not (book):

> **Downloaded** now means *downloaded in the voice you are listening in*. A book held only in
> another voice is, for every purpose in the app, **not downloaded** — the ▶ prompts for the active
> voice's pack, exactly as it would for a book you have never touched (D-AUD-26 b/c).

That rule is correct and simple, and it creates the hardest copy problem in the feature: the user
who downloaded Psalms once, switched voice, and now sees "Psalms — not downloaded" **plus ~47 MB of
storage they cannot account for**. Silence there would violate NFR-AUD-E (storage honesty).

**Resolution — scope the screen, footnote the inventory, never offer the other voice.** Three rules:

1. **The screen is scoped to the active voice and says so once**, in a line under the title:
   *"Showing what's downloaded for **High-quality voice**"* (`audio-downloads-scope`). One sentence
   teaches the whole model; it is not repeated per row.
2. **Book rows show the active voice's state only.** A book held solely in another voice renders the
   plain **Not downloaded ⤓** state — because that is the truth — with a quiet `labelSmall` /
   `onSurfaceVariant` footnote under the book name: **"Also downloaded in Anna"**
   (`audio_book_other_voice`). It is an *inventory fact*, phrased descriptively. It is never
   "wrong voice", never "unused", never "duplicate", never "wasted" — those all editorialise, and
   the D-S17-1 copy discipline bans commentary on the surface as firmly as it bans it in the stats
   panel.
3. **The footnote is not a control and offers nothing.** Tapping the row still downloads *this*
   voice's pack. There is deliberately **no** "play it in Anna" shortcut and **no** "switch to Anna"
   affordance on a book row — either would smuggle cross-voice substitution back in through the UI,
   and changing the app-wide voice is far too consequential a lever to sit in a list of 66 rows.
   The one place voice changes is §7.1's row.

**Other voices' bytes get their own block**, so they are visible, attributable and deletable without
ever entering the book rows:

```
Other voices                                          [audio-other-voices]
  Anna                          612 MB      Delete    [audio-voice-holdings-anna]
```

- The header total (**"1.24 GB used"**) **aggregates across every voice**, as PRD §8 requires — a
  user holding two voices for Psalms holds ~94 MB and the screen says so plainly rather than showing
  one number twice.
- **Deleting a whole voice is a first-class action** (FR-AUD-30 e), confirm-gated by name and
  amount. This is what makes two-voice holdings recoverable in one gesture instead of 66.
- **In release one this block is unreachable** — there is exactly one narration, and the device
  voice has no packs — so, like the multi-narration selector, it is built, tagged, pinned and
  renders as nothing today.

**One case that *is* reachable in release one:** the user is on **Device voice** and holds
high-quality packs. The active voice needs no downloads, so the book list would be meaningless.
The screen then replaces the book list with one line — *"The device voice doesn't need
downloads."* (`audio_device_voice_no_downloads`) — and the "Other voices" block carries the real
bytes and the real Delete. Without this, a device-voice user could never find or free 612 MB.

### 7.3 Pushed screen: "Downloaded audio" (`Routes.AUDIO_DOWNLOADS`) — `D-AUD-UI-11`

66 books plus the bulk units is far past what a Settings column can carry, so it is a pushed route
inside the Schedule graph (same as Settings itself). Reachable in **one tap from Settings**, so
"delete all" is the **second** tap — satisfying NFR-AUD-E literally.

> **Sizes rewritten in P2 (absorbing PRD A1.1).** The pack is a **book**; there is no "today's
> readings" download. The small units are honestly labelled **book-sets** and priced at what Play
> will actually charge. The old table's "Today's readings · 2.4 MB" priced *characters read* and
> would have been exposed by the first progress bar.

```
← Downloaded audio                                       [TopAppBar]
  Showing what's downloaded for High-quality voice       [audio-downloads-scope]
─────────────────────────────────────────────────────────
  1.24 GB used                         Delete all        [audio-delete-all, error colour]
─────────────────────────────────────────────────────────
  The books today's readings need         62 MB    ⤓     [audio-unit-today]
     Genesis, Psalms, Matthew                            [labelSmall subtitle]
  The books your next 30 days need       190 MB    ⤓     [audio-unit-next30]
     8 books                                             [labelSmall subtitle]
  Old Testament                          658 MB    ⤓     [audio-unit-ot]
  New Testament                          195 MB ✓ 195 MB [audio-unit-nt]
  Everything                             853 MB    ⤓     [audio-unit-all]
─────────────────────────────────────────────────────────
  OLD TESTAMENT                                          [sticky header]
  Genesis                                 21 MB    ✓     [audio-book-1]
  Exodus                                  18 MB ◔ 43%    [audio-book-2]   ← tap cancels
  Leviticus                               13 MB    ⚠     [audio-book-3]   ← tap retries
  Psalms                                  47 MB    ⤓     [audio-book-19]
     Also downloaded in Anna                             [footnote, §7.2 — unreachable today]
  …
  NEW TESTAMENT                                          [sticky header]
  3 John                                 0.3 MB    ✓     [audio-book-64]
  …
─────────────────────────────────────────────────────────
  Other voices                                           [audio-other-voices — unreachable today]
  Anna                                   612 MB  Delete  [audio-voice-holdings-anna]
```

- One `LazyColumn`; OT/NT grouped by `order <= 39`, the same rule the picker uses — **`BookCatalog`
  stays the one home of book structure** (no second book table, per D-S9-1 / D-S13-1 / Sprint G).
- **The book-set rows name their books.** "The books today's readings need — Genesis, Psalms,
  Matthew · about 62 MB" tells the truth and teaches the pack model in one line. A day's listening
  genuinely costs three books; hiding that would make the progress bar the teacher.
- Every row's trailing control is a **single ≥48 dp state-button** whose glyph, label and action are
  all a function of one state — never two competing controls. **All six are now scoped to the active
  voice** (§7.2):

  | State (in the active voice) | Glyph | Spoken | Tap |
  |---|---|---|---|
  | Not downloaded | ⤓ | "Download Genesis, 21 megabytes" | start (→ cellular check) |
  | Not downloaded, held in another voice | ⤓ | "Download Genesis, 21 megabytes. Also downloaded in Anna" | start — **downloads *this* voice**, never plays the other |
  | Queued | ⋯ | "Genesis, waiting to download" | cancel |
  | Downloading | determinate ring + % | "Genesis, downloading, 43 percent" | cancel |
  | Downloaded | ✓ | "Genesis, downloaded, 21 megabytes. Delete" | delete (confirm dialog) |
  | Failed | ⚠ | "Genesis, download didn't finish. Try again" | retry |
  | Evicted by Play | ⤓ + "Removed to free space" | "Genesis, removed by Google Play to free space. Download again" | re-download |

- **Sizes are always stated before commitment** (FR-AUD-20) — in the row, in the confirm dialog, and
  in the cellular dialog — and are **Play's own figures**, resolved before the user commits, not
  numbers we computed.
- **Cellular consent** (`audio-cellular-dialog`): requesting a download on a metered network while
  "Wi-Fi only" is on raises *"You're on mobile data. Old Testament is about 658 MB."* →
  **[Wait for Wi-Fi] [Download now]**. "Download now" is a **per-download** opt-in and **never**
  flips the global setting — the setting is the user's standing instruction, not a thing we edit on
  their behalf.
- **Delete all** is confirm-gated and names both the amount and the consequence **in terms of the
  active voice** (§7.5).

### 7.4 The primary path: pressing ▶ when this voice doesn't have it — `D-AUD-UI-16`

> **Promoted from an edge case in P2.** Under D-AUD-26 this is the **main way audio gets
> downloaded** — the user is mid-intent, wanting to listen *now*, and this moment is where a
> 21 MB transfer is either understood and consented to or resented.

Pressing ▶ with the passage missing from the active voice goes to `NeedsSource` and **the Listen
sheet opens immediately**, already at the decision:

```
┌──────────────────────────────────────────────┐
│                   ────                       │
│               Genesis 1–2                    │
│                                              │
│  Genesis isn't downloaded yet — about 21 MB. │
│                                              │
│  ┌────────────────────────────────────────┐  │
│  │              Download                  │  │  [listen-source-download]
│  │           21 MB · Wi-Fi                │  │  FILLED — the primary action
│  └────────────────────────────────────────┘  │
│                                              │
│     Or listen now with the device voice      │  [listen-source-device] TEXT button
└──────────────────────────────────────────────┘
```

**Taps to bytes moving: one.** ▶ was the tap that got them here; **Download** is the next tap and
bytes move. On cellular with Wi-Fi-only on, the standard consent dialog inserts one more tap
("Download now") — worth it, and it is the same dialog as the downloads screen, not a special one.

**Why the download is the filled button and the device voice is a text button.** The filled action
must match the voice the user chose; making "listen now with the device voice" primary would nudge
users off their own selection every time a book is missing, which is D-AUD-26's substitution
arriving through visual hierarchy instead of code. The offer is present, one tap away, and
unmistakably *an offer*.

**What plays while a ~21 MB book downloads.**

- **Nothing, by default.** The sheet stays open and *becomes* the progress surface: the filled
  button turns into a determinate progress row ("Downloading Genesis · 43 %", tap = cancel), and the
  device-voice line stays below it, now reading **"Listen with the device voice while it
  downloads"**. So the wait is never dead time — but it is also never filled with a voice the user
  didn't ask for.
- **On completion:** playback starts **only if the sheet is still open and the app is foregrounded**
  — i.e. the user is demonstrably present, watching the thing they asked for. Otherwise the bar sits
  in **Paused / "Ready to play"** and waits. This keeps NFR-AUD-C's promise where it actually
  matters (the app never speaks when you are not there) without charging a dead tap to someone
  holding the phone through a ten-second transfer. **Flagged for sign-off (§12)** because it is a
  narrow, deliberate reading of "never auto-starts".

**The device-voice offer must never read as the app overriding the choice.** Three copy rules:

1. It is phrased as **the user's action**, never the app's: *"Or listen now with the device voice"*
   — never *"Using the device voice instead"*, which is substitution language describing a thing
   D-AUD-26 forbids.
2. Taking it is **a one-listen choice that does not rewrite the app-wide selection.** The Settings
   row still says "High-quality voice" afterwards; the Listen bar's source line reads **"Device
   voice · this reading"** so the scope is visible while it is in effect. This is the same
   discipline as the S15 MySword rule, where uninstalling the app never rewrites the persisted
   provider.
3. It is never pre-selected, never remembered, and never offered as a checkbox ("always do this") —
   that checkbox is just the app-wide setting, and it lives in Settings.

**The reading is still marked read on the ▶ press** (FR-AUD-15 is about the press, not about audio
arriving) — do not move it into the download-completed path.

Other inline moments:

- **Download failed:** bar status "Couldn't download"; sheet offers Try again + the device-voice
  line. Never a toast, never a snackbar that vanishes.
- **No usable TTS engine and the active voice not downloaded:** the ▶ controls **stay enabled** (a
  permanently dead-looking button teaches nothing) and open `audio-no-engine-dialog`: *"This device
  doesn't have a voice installed for reading aloud. You can add one in your device's settings, or
  download the high-quality voice."* → **[Open device settings] [Download] [Not now]**. Same
  intent-launch idiom Settings already uses for notification settings.

### 7.5 Deleting audio, and what stays selected — `D-AUD-UI-17`

**Deleting bytes never rewrites the user's choice** (D-AUD-28). The two cases differ and must not be
conflated:

| What happens | Active voice afterwards | What the user sees |
|---|---|---|
| The active voice's packs are deleted (some or all) | **unchanged** — still that voice | Every ▶ leads to the §7.4 sheet. Settings still reads "High-quality voice". No silent switch. |
| The active *voice itself* leaves the registry (a future release drops a narration) | **Device voice** — the `fromStored` fallback (D-AUD-28) | The Settings row reads "Device voice". This is the only path by which the app changes the selection for you. |

**Delete-all confirm copy therefore names the consequence in terms of the active voice**, so the
"why is everything asking me to download again?" question is answered before it is asked:

> **Delete downloaded audio?**
> This deletes 612 MB. You'll still be listening in High-quality voice, so readings will ask you to
> download again. You can switch to the device voice in Settings.

Calm, factual, no scolding, and it points at the one lever that fixes it — the D-S17-1 copy
discipline applies here as everywhere.

### 7.6 What ships when

| Surface | Phase 1 | Phase 2 release one | If a second narration appears |
|---|---|---|---|
| Voice row (§7.1) | absent | two entries | lists every narration by pack-declared name |
| Downloads row + screen (§7.3) | absent | present | rows gain the §7.2 footnote |
| "Other voices" block (§7.2) | — | **renders as nothing** | becomes reachable |
| Wi-Fi-only, speed | present | present | unchanged |
| `NeedsSource` sheet (§7.4) | unreachable (device voice always available) | **the primary download path** | unchanged |

The section never appears empty, and nothing built for the third column requires a release to
switch on — that is FR-AUD-28's whole point.

---

## 8. Every state — the exhaustive table

| # | State | Listen bar | Reader | Schedule | Notes |
|---|---|---|---|---|---|
| 1 | Idle (no session) | **absent (0 dp)** | as today | as today, 45 % stats cap | The resting-state guarantee |
| 2 | Preparing | indeterminate glyph, ref shown | highlight absent | ▶ → busy on that card | ≤ a moment; no spinner theatre |
| 3 | Playing | ⏸, "Playing", 2 dp progress | highlight + autoscroll | live card shows ⏸ | |
| 4 | Paused | ⏵, "Paused" | highlight **stays**, autoscroll idle | live card shows ⏵ | Highlight persisting on pause is intentional — it is "where you are" |
| 5 | Buffering / extracting a pack | ⏸ dimmed + indeterminate, "Preparing audio" | highlight held at last verse | unchanged | Never blanks the highlight — a stale-but-correct verse beats a flicker |
| 6 | Not downloaded **in the active voice** | ref + "Not downloaded" | — | — | Sheet auto-opens — **the primary download path** (§7.4) |
| 6b | Not downloaded in the active voice, **held in another voice** | identical to 6 | — | — | **No difference in the player at all** (D-AUD-26 b/c). The other voice is footnoted on the downloads screen only (§7.2), never here, and is never offered as playback |
| 7 | Downloading | ring + "Downloading 43 %" | — | — | Sheet becomes the progress surface; device voice stays one tap away as a *text* action |
| 7b | Download completed while waiting | ⏸ playing **iff** sheet open + foregrounded, else ⏵ "Ready to play" | highlight begins if playing | — | D-AUD-UI-16; flagged §12 |
| 8 | Download failed | "Couldn't download" | — | — | Retry + device-voice line in sheet |
| 9 | Device voice in use **by selection** | ref + "Device voice" *(app-wide)* or "Device voice · this reading" *(one-listen, §7.4)* | full follow-along (TTS gives verse events) | unchanged | A choice, never a fallback (D-AUD-27) — stated, never apologised for |
| 9b | Active voice switched mid-playback | **Paused**, "Ready to play" or `NeedsSource` in the new voice | highlight held at the current verse | unchanged | Never continues in the old voice, never auto-restarts (§7.1, D-AUD-UI-18) |
| 9c | Active voice's packs all deleted | Idle | — | ▶ leads to the §7.4 sheet | **Selection survives** — Settings still names that voice (D-AUD-28, §7.5) |
| 9d | Active narration removed from the registry | Idle | — | — | Selection falls back to **Device voice**; the only path by which the app changes the choice |
| 10 | No TTS engine, active voice not downloaded | not entered | — | ▶ still enabled | `audio-no-engine-dialog` |
| 11 | Feb 29 (`NoScheduledReadings`) | Idle | Bible tab plays anything, unaffected | **no cards ⇒ no ▶; `listen-day` hidden** | No new empty state, no new string (PRD §8) |
| 12 | Reader `Error` (text load failed) | **unchanged if live** | reader error state as today | unchanged | Audio ≠ text: playback survives a text failure |
| 13 | Schedule `LoadFailed` | unchanged if live | — | retry state as today, `listen-day` hidden | |
| 14 | End of portion / end of day | **slides out → Idle** | highlight cleared | card checkbox already checked | No completion badge, no "well done" — D-S17-1 |
| 15 | End of chapter, Browse | **stays** — advances to next chapter | pager follows to the next page, autoscroll re-arms | — | Crosses book boundaries (FR-AUD-4) |
| 16 | Revelation 22 ends, Browse | slides out → Idle | — | — | The canon is the stop |
| 17 | Sleep timer fires | slides out → Idle | highlight cleared | — | Silent; no dialog |
| 18 | Audio focus lost (call/nav) | ⏵ "Paused" | highlight held | — | Resume per focus rules |
| 19 | Playback fault | "Playback stopped" + Try again in sheet | unaffected | unaffected | Honest, non-modal |
| 20 | No Play Store | Idle/normal (device voice only) | normal | normal | One sentence in Settings, never a popup |

---

## 9. Vertical-space budget

Method and baseline follow the S18/S20 tables: Pixel 7 Pro class, ~411 × 915 dp, **usable content
height ≈ 828 dp** after system insets, default font (1.0× in-app, 1.0× system). Layout arithmetic,
not device-measured — same caveat S15–S20 carried.

### Schedule, M'Cheyne N = 4, streaks OFF (the tight case)

| Item | Idle (today) | Session live | Δ |
|---|---:|---:|---:|
| `TopAppBar` | 64 | 64 | 0 |
| **Listen bar** | **0** | **56** | **+56** |
| `NavigationBar` | 80 | 80 | 0 |
| = Scaffold content available | **684** | **628** | −56 |
| Readings column needed (4 cards @60 + 3 gaps @12 + padding 32 + list hint 28) | 336 | 336 | 0 |
| `HorizontalDivider` | 1 | 1 | 0 |
| Stats cap (`maxHeight × 0.45 / 0.30`) | 308 | **188** | −120 |
| Stats intrinsic (N = 4, streaks off ≈ S20's 278 + one strip row/gap) | 294 | 294 | 0 |
| Stats **rendered** (min of cap, intrinsic) | 294 | 188 (scrolls internally) | −106 |
| **Used** | **631** | **525** | −106 |
| **Slack for the readings column** | **+53** | **+103** | **+50** |

**Verdict for M'Cheyne (N = 4): one-screen fit holds, and holds *better* while listening.** The
readings column ends up with roughly twice the slack it has idle, because the stats-cap swap
(D-AUD-UI-3) returns 106 dp against the bar's 56 dp cost. At N = 3 (Bible Companion) and N = 1
(Chronological) the margins are strictly larger. Nothing on the Schedule scrolls that does not
scroll today, and the reading card's ▶ contributes **0 dp**.

### Schedule reading card

| Item | Today | With ▶ | Δ |
|---|---:|---:|---:|
| Card row height (48 dp checkbox + 2 × 6 dp padding) | 60 | 60 | **0** |
| Reference column width (411 − 32 card − 32 row − controls) | ~299 | ~251 | −48 |

### Reader, verse viewport

| Item | Idle | Session live | Δ |
|---|---:|---:|---:|
| `TopAppBar` | 64 | 64 | 0 |
| `ReaderFooterHint` (incl. padding) | 28 | 28 | 0 |
| **Listen bar** | 0 | 56 | +56 |
| `NavigationBar` | 80 | 80 | 0 |
| `ReaderAudioSlot` (retired, D-AUD-UI-2) | 0 | 0 | 0 |
| **Verse viewport** | **656** | **600** | **−56** |

≈ 1.5 verses at `bodyLarge`. Accepted: while listening the page is scrolling itself, so viewport
height matters less than it does while reading. The **"Follow along" chip overlays** and costs 0 dp.

---

## 10. Accessibility

The subtle problem: **a TalkBack user pressing play produces two speech streams**. TalkBack speaks on
`USAGE_ASSISTANCE_ACCESSIBILITY`; a media player speaks on `USAGE_MEDIA`. On most devices these do
**not** duck each other, so both are audible at once. Position:

### `D-AUD-UI-13` — We duck ourselves; we never silence the screen reader

1. **Never silence or suppress TalkBack.** It is the user's navigation channel and taking it away —
   even "helpfully" — would leave a blind user unable to find the pause button they just triggered.
   Non-negotiable.
2. **The player ducks itself around our own announcements and controls.** Player
   `AudioAttributes` = `USAGE_MEDIA` + `CONTENT_TYPE_SPEECH`, focus `AUDIOFOCUS_GAIN`. When
   `AccessibilityManager.isTouchExplorationEnabled()` is true, playback drops to ~25 % volume for
   ~4 s whenever a Listen-bar or sheet control takes accessibility focus or is activated. That
   covers the dominant collision — reaching for pause while scripture plays — without any
   heuristic guessing about what TalkBack is doing.
3. **The follow-along highlight is deliberately NOT a live region.** Announcing each verse as it
   becomes active would make TalkBack read the entire chapter *over the top of the audio* — the
   single worst outcome available. The active verse carries `stateDescription = "Now playing"`, so
   it is discoverable on demand and silent otherwise.
4. **Playback state changes are announced once, politely.** The Listen bar's status text is the only
   `liveRegion = LiveRegionMode.Polite` node in the feature: "Playing" / "Paused" / "Downloading
   43 percent" / "Couldn't download". One node, one announcement per transition.
5. **Never auto-start** (NFR-AUD-C), so the player can never speak over a screen reader unbidden.

### Semantics contract

| Control | Role | `contentDescription` | `stateDescription` |
|---|---|---|---|
| Listen bar (centre region) | Button | "Playback controls, Genesis 1–2" | "Playing" / "Paused" |
| `listen-bar-play` | Button (toggle) | "Pause" / "Play" | "Playing" / "Paused" |
| `listen-bar-close` | Button | "Stop reading aloud" | — |
| `listen-{n}` (card ▶) | Button | "Read aloud, Genesis 1–2" | "Playing" when live |
| `listen-day` | Button | "Read aloud, all of today's readings" | — |
| `listen-reader` | Button | "Read aloud, Genesis 1" | — |
| `listen-follow-chip` | Button | "Follow along, resumes scrolling to the verse being read" | — |
| Active verse | Button (+ 2 custom actions, §5) | unchanged Sprint-H text | "Now playing" |
| `audio-book-{n}` trailing | Button | per the §7.3 state table — **including the "Also downloaded in Anna" clause**, so the inventory fact reaches TalkBack, never only the eye | — |
| `audio-voice-dropdown` | DropdownList | "Voice, High-quality voice" | — |
| `audio-voice-holdings-{id}` | Button | "Delete all audio for Anna, 612 megabytes" | — |
| `audio-downloads-scope` | — | plain text, read in order before the rows it scopes | — |
| `listen-source-download` | Button | "Download Genesis, 21 megabytes, over Wi-Fi" | "Downloading, 43 percent" while active |
| `listen-source-device` | Button | "Listen now with the device voice" | — |
| Sheet speed / timer rows | DropdownList | "Playback speed, 1.0×" / "Sleep timer, Off" | — |
| 2 dp bar progress | — | `clearAndSetSemantics {}` | — |

### Gate impact (`ui/AccessibilityGateTest.kt`)

Extended, not rewritten — every authored control above is pinned at **≥48 dp touch bounds** plus its
spoken label:

- `listen-bar`, `listen-bar-play`, `listen-bar-close`, `listen-follow-chip`
- `listen-1…N` on `DayContent`, `listen-day`, `listen-reader`
- Sheet: `listen-sheet-play`, `listen-sheet-prev`, `listen-sheet-next`, `listen-sheet-follow`,
  `listen-sheet-speed`, `listen-sheet-timer`, `listen-sheet-source`
- Sheet source block: `listen-source-download`, `listen-source-device`
- Settings: `audio-voice-dropdown` + **every** menu item (`audio-voice-device`, `audio-voice-high`) —
  all **enabled** (§7.1: an empty narration is selectable, not greyed), `audio-wifi-only-toggle`,
  `audio-speed-dropdown`, `audio-downloads-row`
- Downloads screen: `audio-delete-all`, `audio-unit-*`, `audio-book-1` (representative),
  `audio-voice-holdings-*` (the whole-voice Delete)
- **Pinned absences** (S16 idiom — assertions that stop someone "improving" this later): no
  `liveRegion` on any verse node; **no control on a book row that plays or selects another voice**
  (§7.2 rule 3) — the row exposes exactly one action, and it downloads the *active* voice
- Active-verse `stateDescription` present, and **no `liveRegion` on any verse node** (a pinned
  *absence*, in the S16 idiom — this is the assertion that stops someone "improving" it later)

The Listen bar is 56 dp precisely so its 48 dp buttons fit with 4 dp of breathing room; a 48 dp bar
would force under-sized controls and fail the gate.

### Copy discipline

Every string in §11 is inside the **D-S17-1 / D-S20-1 no-guilt ban**, on screen *and in speech*. The
D-S20-1 exemption is exactly the two literal legend labels in the stats legend and **does not extend
here**: no audio string congratulates, scolds, nudges, or distinguishes a heard reading from a read
one. The ban-scan (M-AUD-7) extends over the audio strings unchanged.

---

## 11. New user-visible strings — OWNER TONE SIGN-OFF NEEDED

Feature noun used throughout is **"Read aloud"** (Maya's OQ-AUD-7 recommendation). If the owner
picks "Listen" or "Audio", the ids stay and only the values change.

### Transport & follow-along

| id | string | note |
|---|---|---|
| `listen_play` | Play | bar + sheet toggle, play state |
| `listen_pause` | Pause | bar + sheet toggle, playing state |
| `listen_stop` | Stop reading aloud | the ✕ |
| `listen_bar_description` | Playback controls, %1$s | %1$s = reference |
| `listen_state_playing` | Playing | live region |
| `listen_state_paused` | Paused | live region |
| `listen_state_preparing` | Preparing audio | |
| `listen_prev` | Previous verse | |
| `listen_next` | Next verse | |
| `listen_follow_title` | Follow along | sheet switch + chip |
| `listen_follow_description` | Follow along, resumes scrolling to the verse being read | chip a11y |
| `listen_now_playing_state` | Now playing | active-verse `stateDescription` |
| `listen_speed_title` | Playback speed | |
| `listen_speed_value` | %1$s× | e.g. "1.0×" |
| `listen_timer_title` | Sleep timer | |
| `listen_timer_off` | Off | |
| `listen_timer_minutes` | %1$d minutes | |
| `listen_timer_end_of_chapter` | End of chapter | |
| `listen_timer_end_of_portion` | End of reading | |

### Entry points

| id | string | note |
|---|---|---|
| `listen_reading_description` | Read aloud, %1$s | card ▶; %1$s = reference |
| `listen_day_description` | Read aloud, all of today's readings | top-bar action |
| `listen_reader_description` | Read aloud, %1$s | reader top-bar action |
| `reader_verse_tap_hint_listening_*` | Tap a verse to play from there · hold to open it on %1$s | **five** variants mirroring the existing `reader_verse_tap_hint_*` set (blb/gateway/youversion/mysword + in-app fallback) |
| `listen_verse_action_seek` | Play from here | TalkBack custom action |
| `listen_verse_action_open` | Open on %1$s | TalkBack custom action |

### Source & degradation

| id | string | note |
|---|---|---|
| `audio_section_title` | Read aloud | Settings section |
| `audio_voice_title` | Voice | dropdown row label |
| `audio_voice_device` | Device voice | **D-AUD-5: origin/quality only** |
| `audio_voice_high` | High-quality voice | **D-AUD-5** |
| `audio_voice_high_undownloaded` | High-quality voice (not downloaded) | disabled menu item |
| `audio_voice_dropdown_description` | Voice, %1$s | |
| `audio_wifi_only_title` | Download over Wi-Fi only | on by default |
| `audio_help` | The high-quality voice downloads only when you ask. Books you download stay on your phone until you delete them. | |
| `audio_no_play_store` | The high-quality voice needs the Google Play Store, which isn't available on this device. Read aloud still works with your device voice. | said once, in Settings |
| `audio_source_missing_title` | This reading isn't downloaded yet. | sheet |
| `audio_source_download` | Download %1$s | %1$s = book |
| `audio_source_size_wifi` | %1$s · Wi-Fi | e.g. "14 MB · Wi-Fi" |
| `audio_source_device_now` | Listen now with the device voice | |
| `audio_using_device_voice` | Device voice | bar status |
| `audio_no_engine_title` | No voice installed | dialog |
| `audio_no_engine_body` | This device doesn't have a voice installed for reading aloud. You can add one in your device's settings, or download the high-quality voice. | |
| `audio_no_engine_settings` | Open device settings | |
| `audio_playback_failed` | Playback stopped | bar status |
| `audio_try_again` | Try again | |

### Downloads screen

| id | string | note |
|---|---|---|
| `audio_downloads_title` | Downloaded audio | row + screen title |
| `audio_downloads_used` | %1$s used | e.g. "1.24 GB used" |
| `audio_delete_all` | Delete all | error colour |
| `audio_delete_all_confirm_title` | Delete downloaded audio? | |
| `audio_delete_all_confirm_body` | This deletes %1$s. Read aloud will use your device voice until you download again. | factual, no scolding |
| `audio_unit_today` | Today's readings | |
| `audio_unit_next30` | Next 30 days of readings | |
| `audio_unit_ot` | Old Testament | |
| `audio_unit_nt` | New Testament | |
| `audio_unit_all` | Everything | |
| `audio_state_download` | Download %1$s, %2$s | book, size |
| `audio_state_waiting` | %1$s, waiting to download | |
| `audio_state_downloading` | %1$s, downloading, %2$d percent | |
| `audio_state_downloaded` | %1$s, downloaded, %2$s. Delete | |
| `audio_state_failed` | %1$s, download didn't finish. Try again | |
| `audio_state_evicted` | %1$s, removed by Google Play to free space. Download again | |
| `audio_evicted_label` | Removed to free space | inline row label |
| `audio_delete_confirm_body` | Delete the audio for %1$s? That frees %2$s. | |
| `audio_cellular_title` | You're on mobile data | |
| `audio_cellular_body` | %1$s is about %2$s. | |
| `audio_cellular_wait` | Wait for Wi-Fi | |
| `audio_cellular_now` | Download now | per-download only; never edits the setting |

**Deliberately absent, and must stay absent:** any synthetic-voice/AI disclosure (D-AUD-5); any
human-narrator claim (D-AUD-5); any congratulation, streak, "minutes listened", or copy that
distinguishes a heard reading from a read one (D-AUD-8, FR-AUD-16, M-AUD-7).

---

## 12. What needs the owner (recommendation given for each)

1. **OQ-AUD-4 — the verse-tap gesture change.** §5. *Recommend adopting it* as designed, with the
   two named TalkBack custom actions and the reactive footer hint. The gesture change is real; the
   a11y cost is designed to zero.
2. **OQ-AUD-8 — whole-day playback in the first release.** §6. *Recommend shipping it*; it is the
   commuter's natural unit and costs 0 dp. If the top bar proves crowded at large font on device,
   dropping `listen-day` removes it cleanly with no other change.
3. **The stats-cap swap (D-AUD-UI-3).** Confirm you're happy that starting playback shrinks the
   "Year at a glance" panel's visible height (it scrolls; nothing is removed). It is what buys the
   Listen bar for free at N = 4.
4. **`ReaderAudioSlot` retirement (D-AUD-UI-2).** Confirms that the V3-reserved slot is honoured at
   the root rather than in the reader — the intent is cashed in, the file goes away.
5. **Tone sign-off on §11** — the whole table, and especially "Device voice" / "High-quality voice"
   under D-AUD-5, and `audio_delete_all_confirm_body`.
6. Standing string sign-offs from S12–S20 and the alt-schedules work are still open; these join that
   queue rather than starting a new one.

---

## 13. Not JVM-provable — the owner's device-pass checklist

Everything below is invisible to Robolectric and belongs on the device pass, in priority order:

**One-screen fit and layout**
1. **N = 4 (M'Cheyne) Schedule with a session live** — the §9 verdict on glass, at default font and
   again at 1.5× in-app scale with a large system font.
2. The reading card's ▶ + checkbox pair at N = 4: does the stream title ("Personal — Psalms &
   Prophets") ellipsize acceptably at ~251 dp?
3. Schedule top bar with **four** actions (jump-to-today + picker + settings + `listen-day`) at large
   font — the OQ-AUD-8 fallback trigger.
4. Reader verse viewport with the bar present — does losing ~1.5 verses feel cramped?
5. The Listen bar slide-in/out against the nav bar: no jank, no content jump, no double bottom inset.

**Follow-along feel**
6. Highlight contrast in **light, dark, and under dynamic color** on a real device palette, at 0.85×
   and 1.5×.
7. Autoscroll cadence — is one-third-from-top the right lead? Does it fight a fling?
8. The "Follow along" chip: appears at the right moment, doesn't cover the last verse, easy to hit.
9. Highlight accuracy against the audio (FR-AUD-11's "correct or absent" bar) — especially at 2.0×
   speed and on the Psalm 119 windowed days.

**Gesture**
10. Long-press to open externally while listening: 500 ms feels right? Any accidental triggers while
    scrolling at large font?
11. TalkBack: both custom actions present, named, and in a sensible order.

**Accessibility / audio**
12. **The two-voice test** — TalkBack on, press play, navigate the transport. Does the self-ducking
    (D-AUD-UI-13) make it usable? This is the single most important device-pass item in the feature.
13. Live-region announcements: one per transition, not a stream.
14. Lock screen, notification, headset button, Bluetooth/car controls; a phone call interrupting and
    resuming (M-AUD-9).

**Downloads**
15. Cellular-consent dialog on a real metered connection.
16. A Play-evicted pack: does the row show "Removed to free space" and re-download cleanly?
17. Download progress smoothness and cancel responsiveness on a 700 MB unit.
18. Storage figures matching the OS's own "app storage" number (NFR-AUD-E honesty).
19. Sideloaded install: the single Settings sentence, device voice working, no dead controls.

---

## 14. Summary of design decisions

| id | decision |
|---|---|
| **D-AUD-UI-1** | One transport, in `RootScaffold`, docked **above** the `NavigationBar`, composing nothing when idle. Never replaces the nav bar, never overlays content. |
| **D-AUD-UI-2** | `ReaderAudioSlot` retired as a bottom bar (supersedes D-V3-14's *placement*, honours its intent); `activeVerseId` cashed in unchanged. |
| **D-AUD-UI-3** | While a session is live the Schedule's stats cap drops 45 % → 30 %; the Listen bar is paid for by the stats panel, and the readings column gains slack. |
| **D-AUD-UI-4** | Live session in the reader: verse tap **seeks**, long-press opens externally, **two named TalkBack custom actions** carry both so nothing depends on the hold gesture; the existing reactive footer hint teaches it. Outside a session: Sprint H, unchanged. |
| **D-AUD-UI-5** | Active verse = `secondaryContainer` fill + 3 dp `primary` leading rule + `stateDescription "Now playing"`. Never colour alone; replaces the current unpaired `onPrimaryContainer`-on-`surface` treatment. |
| **D-AUD-UI-6** | Autoscroll targets one-third from top, yields on `DragInteraction.Start`, re-arms via an overlaid ≥48 dp "Follow along" chip (0 dp), the sheet switch, or a chapter advance. |
| **D-AUD-UI-7** | Schedule card ▶ = trailing 48 dp `IconButton` left of the checkbox: **0 dp height**, distinct from the card tap, spoken with the reference. |
| **D-AUD-UI-8** | Whole-day play = a Schedule top-bar action (0 dp, no button row); hidden on Feb 29 / load-failed days; the most severable piece if the owner drops OQ-AUD-8. |
| **D-AUD-UI-9** | Reader play = a top-bar action; the play *unit* follows `ReaderContext` (Browse = chapter, continuing; Reading = the whole portion, stopping). |
| **D-AUD-UI-10** | Settings → "Read aloud" section in the house `SectionTitle` + `SettingsDropdownRow` idiom; voice named by origin/quality only (D-AUD-5); undownloaded voice = visible-but-disabled (S14/S15 idiom). |
| **D-AUD-UI-11** | Downloads are a pushed screen (`Routes.AUDIO_DOWNLOADS`) over `BookCatalog` (no second book table); one ≥48 dp state-button per row; sizes always stated; cellular consent is per-download and never edits the setting. |
| **D-AUD-UI-12** | Play never dead-ends and never silently substitutes a worse voice: an unavailable source opens a two-choice sheet; mark-on-press still fires. |
| **D-AUD-UI-13** | Never silence TalkBack; the **player ducks itself** around our controls when touch exploration is on; the highlight is **not** a live region; exactly one polite live region (the bar status). |
