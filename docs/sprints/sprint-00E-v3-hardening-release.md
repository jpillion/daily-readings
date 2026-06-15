# V3 Sprint E — V3.0 hardening + release readiness

> **EM:** Morgan · **Status:** DONE (uncommitted; main session/owner commits + cuts the release) ·
> **Date:** 2026-06-15 · **Next:** release cut (owner device pass + sign-offs → 1.4.0/10400 tag-to-Play)

## Goal outcome — MET (engineering); release gated on owner sign-offs

**V3.0 is release-ready.** This sprint added no reader features; it hardened the build, wired the
last deferred engineering piece (the asset-version startup hook), and assembled the artifacts the
owner needs to sign off and ship. Everything JVM/CI-provable is green; the remaining gates are
the owner's device pass and three tone/presentation sign-offs (below) — those are owner-run by
design and were NOT faked.

## Current capability (working software)

- A **future corrected `bible.db` now actually reaches existing users.** Bumping
  `BibleAssetVersion.ASSET_CONTENT_VERSION` deletes the stale copied DB before Room opens it, so
  `createFromAsset` re-copies the corrected asset on next launch — wired off-main, StrictMode-clean
  (the deferred Sprint-A hook is live).
- The full release pipeline is green from a clean build and **`bundleRelease` produces a clean
  7.67 MB signed `.aab`** (within the 12 MB / +6 MB bundle budget). The app ships with **no
  `INTERNET` permission** — the offline identity holds with the text now bundled.

## Tickets (administrative record)

| Ticket | Status | What it delivered |
|---|---|---|
| VE-T0 wire the deferred `BibleAssetVersion` startup hook (D-V3-8) | ✅ | `BibleAssetGate` + DataStore-backed version store, wired into `provideBibleDatabase`; re-copy on bump |
| VE-T1 consolidated owner device-pass checklist | ✅ | Assembled below (owner-run) |
| VE-T2 JVM-provable hardening confirmation | ✅ | StrictMode/INTERNET/a11y/R8/bundle-size all confirmed green |
| VE-T3 full V3 strings table for tone sign-off | ✅ | Assembled below (owner-run) |
| VE-T4 AR-1 recorded + version recommendation + bundleRelease + whatsnew | ✅ | AR-1 in `docs/data/README.md`; 1.4.0/10400 recommended (NOT applied); whatsnew drafted |
| VE-T5 CLAUDE.md + this handoff | ✅ | — |

**490 tests** (net +3: the 3 `BibleAssetGateTest` wiring tests; **both data gates untouched —
plan gate = 7, `BibleTextVerificationTest` = 18**), full standing pipeline green from clean,
`bundleRelease` clean, **Kover 95.1%** on domain/data (≥70% floor), **2 load-bearing mutations
killed** (asset-version comparison flip, skipped delete), each restored in place. No version bump,
no tag, no commit (per the sprint constraints).

## Decisions & rationale (this sprint)

- **D-E-1 — the asset-version gate runs inside the `BibleDatabase` Hilt provider, before
  `.build()`.** It is the one place guaranteed to execute after the constant/stored-version are
  known and before Room opens (and possibly copies) the asset. The provider is `@Singleton` and
  the bible DB is only ever first touched from a suspend `RoomBibleTextSource.getVerses` on Room's
  background executor — never the main thread — so the gate's `runBlocking` DataStore read/write is
  StrictMode-clean. The pure compare/delete orchestration stays in `BibleAssetVersion`
  (JVM-pinned in Sprint A); `BibleAssetGate` only supplies the Android seams (database-file path +
  the persistence read/write).
- **D-E-2 — the persisted version lives under its own DataStore key, not in `SettingsRepository`.**
  A narrow `BibleAssetVersionStore` (read/write Int?) backed by `DataStoreBibleAssetVersionStore`
  (key `bible_asset_content_version` in the existing shared store) keeps the gate's dependency to
  one read/write pair and avoids bloating the `SettingsRepository` interface. It is NOT inside the
  read-only `bible.db` — the D-V3-8 converse rule (the asset DB is wiped on every content bump, so
  no persisted state may live in it).
- **D-E-3 — version bump RECOMMENDED, not applied.** V3.0 is a significant feature (the in-app
  reader). Recommended: **versionName 1.4.0 / versionCode 10400** (D-S9-3: MAJOR*10000 +
  MINOR*100 + PATCH). NOT applied this sprint — the main session/owner cuts the release via the
  tag-to-Play pipeline.
- **R8 `VerseEntity` keep is defense-in-depth.** Room ships consumer keep rules for the
  entities/DAOs it generates against; the added `-keepclassmembers VerseEntity { <fields>; <init>(...); }`
  mirrors the existing serialization keep (a stripped column field would fail the asset read only
  at runtime). The InputMerger keep (Glance widget) is intact and unchanged.

## State of the codebase

New (under `app/src/main/kotlin/.../bible/data/`):
- `BibleAssetGate.kt` — `@Singleton`; `ensureUpToDate(databaseName)` does the off-main version
  compare → delete copied DB files → persist, delegating the pure logic to `BibleAssetVersion`.
  Also declares the narrow `BibleAssetVersionStore` seam (`read(): Int?` / `write(Int)`).
- `DataStoreBibleAssetVersionStore.kt` — DataStore-backed `BibleAssetVersionStore` (key
  `bible_asset_content_version`).

Edited:
- `di/BibleModule.kt` — `provideBibleDatabase` now takes `BibleAssetGate` and calls
  `assetGate.ensureUpToDate(BIBLE_DB)` before `.build()`; `BibleBindsModule` binds
  `DataStoreBibleAssetVersionStore → BibleAssetVersionStore`.
- `app/proguard-rules.pro` — bible `VerseEntity` keep (rule #4).
- `docs/data/README.md` — AR-1 (accepted UK risk) recorded.
- `distribution/whatsnew/whatsnew-en-US` — V3.0 reader draft (381 chars, < 500 limit).

New test: `app/src/test/.../bible/data/BibleAssetGateTest.kt` (3, Robolectric @Config sdk=34):
fresh-install re-copy + persist, current-version no-op (no delete/no write), older-version
re-copy + record newer constant.

## VE-T1 — Consolidated owner device-pass checklist (OWNER-RUN)

Run on a Pixel 7 Pro (P7P) at default font unless a step says otherwise. None of these is
JVM-provable; each is a real on-glass check.

**A — bible asset (from Sprint A / VE-T0):**
- [ ] First install: tapping a reading (in-app destination) opens the text instantly — the real
      `createFromAsset` copy of the ~5.7 MB asset succeeds, no crash, no ANR (M-V3-3, U13).
- [ ] Asset re-copy: with a debug build, bump `ASSET_CONTENT_VERSION` to 2, relaunch — confirm the
      reader still opens correct text (the copied DB was deleted + re-copied; D-V3-8, VE-T0). Then
      restore to 1.

**C — reader reading-feel & faithful presentation (U15, M-V3-2):**
- [ ] Genesis 1 reads as calm prose, verse numbers de-emphasized, not a table (U15, device-pass).
- [ ] Italic translator-added words render italic (e.g. Gen 1:2 "without form, **and** void"-class).
- [ ] Titled Psalm 3 and Psalm 51 show the superscription as an unnumbered italic heading BEFORE
      verse 1 (M-V3-2); untitled Psalm 1 has NO heading.
- [ ] The Jun 19 / Dec 19 two-book portion (2 John + 3 John) renders both books in sequence (M-V3-4).
- [ ] Light and dark mode both read faithfully.
- [ ] Large `fontScale` (Settings text-size at max): reader stays readable and usable (NFR-V3-C).
- [ ] Load feel: opening a chapter is instant — no loading spinner (U13, device-pass).

**D — nav & integration (R-V3-1, U18):**
- [ ] One-screen fit: the Schedule tab still fits one screen at default font WITH the ~80dp bottom
      bar present (R-V3-1, VD-T9 — the owner-accepted cost).
- [ ] Tab state preservation: drill into Settings, switch to Bible and back — land on Settings, not
      the pager; the reader remembers its chapter (U18).
- [ ] Reading-tap → reader hop: with "Read in this app" chosen, tapping a reading switches to the
      Bible tab and shows that portion (the cross-graph handoff feel).
- [ ] First-run dialog on glass: a fresh install is asked the reading-destination question once;
      in-app is never silently pre-chosen (M-V3-6).
- [ ] Upgrade note on glass: an existing user (with prior marks) sees the one-time in-app note;
      their external provider is unchanged unless they opt in.

## VE-T3 — Full V3 user-visible strings for owner tone sign-off (OWNER-RUN)

Current strings shown; nothing here is final copy — present for the owner's tone pass. Team
recommendations flagged.

| Key | Current value | Note / recommendation |
|---|---|---|
| `nav_schedule` | "Schedule" | **OQ-3** — owner to confirm vs "Today"/"Plan" |
| `nav_bible` | "Bible" | **OQ-3** — owner to confirm vs "Read"/glyph; also note the Bible-tab icon is `AutoMirrored.List` placeholder (MenuBook absent from frozen icons-core) — owner may want a custom book drawable (ic_stats.xml pattern) |
| `provider_inapp` | "Read in this app" | Settings "Open readings in" option (S13/D) |
| `reader_open` | "Open the Bible reader" | content description |
| `reader_pick_chapter` | "Choose a book and chapter" | |
| `reader_prev_chapter` | "Previous chapter" | |
| `reader_next_chapter` | "Next chapter" | |
| `reader_load_failed` | "Couldn't load this chapter" | error state |
| `reader_retry` | "Retry" | |
| `picker_testament_ot` | "Old Testament" | |
| `picker_testament_nt` | "New Testament" | |
| `picker_back_to_books` | "All books" | |
| `reading_destination_prompt_title` | "Where would you like to read?" | first-run question |
| `reading_destination_prompt_body` | "You can read each day's chapters right here in the app, or open them in a Bible website or app. You can change this anytime in Settings." | |
| `reading_destination_prompt_inapp` | "Read in this app" | |
| `reading_destination_prompt_external` | "Open in a Bible website or app" | |
| `upgrade_note_title` | "Read the Bible in the app" | one-time upgrade note |
| `upgrade_note_body` | "You can now read each day's chapters right inside the app, fully offline. Your current reading destination is unchanged — switch anytime in Settings." | |
| `upgrade_note_use_it_now` | "Use the in-app reader" | |
| `upgrade_note_keep_current` | "Keep my current choice" | |

(Carryover note: the OQ-1 first-run emphasis and OQ-2 upgrade-nag calls shipped as the
recommended defaults in Sprint D — neutral question, leave-them-be upgrade note with an opt-in —
and stand unless the owner overrides at sign-off.)

## Release prep (VE-T4)

- **Version (RECOMMENDED, NOT applied):** `versionName = "1.4.0"`, `versionCode = 10400`
  (D-S9-3). Apply in `app/build.gradle.kts` at release cut.
- **`bundleRelease`:** builds clean — signed `app-release.aab`, **7.67 MB** (< 12 MB ceiling,
  bundle-size CI gate green).
- **AR-1:** recorded in `docs/data/README.md` (accepted UK Crown-copyright risk; OQ-5 courtesy
  Cambridge filing left optional/owner-deferred, not a gate).
- **whatsnew:** `distribution/whatsnew/whatsnew-en-US` rewritten for the V3.0 reader (381 chars).

## Carryover & next goal

Next: **release cut** (owner + main session). Carryover:
- The **owner device pass** (VE-T1 above) and the three sign-offs (M-V3-2 faithful presentation,
  OQ-3 nav label/icon, S-A..S-D string tone) — all owner-run, the only remaining V3.0 gates.
- Apply the 1.4.0/10400 bump + the closed-track tag-to-Play rollout.
- **Deferred / V3.x (scope-protected OUT of E):** the `openPortion` browse-cursor polish (D's
  known debt — Prev/Next resumes from the last chapter cursor, not the portion); highlights /
  bookmarks / durable last-read / full-text search / App Links / red-letter / poetry lines (all
  PRD §9 V3.x); the `exportBookCatalog` Gradle-task wrapping (A's queued Jordan follow-up — the
  fixture is drift-gated already, low-risk).

## Open questions & risks / tech debt

- **Real on-device asset re-copy is device-pass.** The version-compare + delete-before-build is
  JVM-pinned and mutation-verified; the actual `createFromAsset` re-copy of the corrected asset on
  a device is in the VE-T1 checklist (cannot be JVM-proven).
- **Library-merged permissions are NOT INTERNET.** The merged release manifest carries
  ACCESS_NETWORK_STATE / WAKE_LOCK / FOREGROUND_SERVICE from pre-V3 Glance/WorkManager — none is
  INTERNET and none grants network access; the offline guarantee (no INTERNET) holds. Recorded so
  a future reviewer doesn't mistake them for a network regression.
- **`hiltViewModel` deprecation warnings** persist (pre-existing, project-wide; not new debt).

## Next sprint

`next: release-cut` (owner-run device pass + sign-offs, then main session applies 1.4.0/10400 +
the tag-to-Play rollout). No further feature sprint is queued for V3.0; V3.x is demand-ordered
(PRD §9), V2.x release prep remains independently queued.
