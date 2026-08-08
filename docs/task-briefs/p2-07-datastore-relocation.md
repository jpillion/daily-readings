# p2-07 — DataStore into `shared/data`, with the file path pinned. The silent one.

> **Assignee:** Senior Shared-Core Engineer (second core engineer; parallel with `p2-06`)
> **Release:** 1.11.0 · **Merge order:** Tranche A, after `p2-04`/`p2-05`. **Parallel with `p2-06`.**
> **Inherits:** [`p2-00-overview.md`](p2-00-overview.md) rules R1–R9.
> **Preconditions:** **`p1-08`'s `settings.preferences_pb` fixture is committed.**
> **Executes:** ADR-0008's DataStore half.

---

## Objective

Move DataStore Preferences into `shared/data` on `datastore-preferences-core`, **with the file
path, the file name and every key string byte-identical**, and prove it with a
`settings.preferences_pb` captured from a **real 1.8.1 device**.

---

## Context

### Why this task is more dangerous than it looks

`p2-06` is the scarier-*sounding* task and it fails **loudly**: a wrong identity hash throws, the
app crashes on open, and you know within an hour of rollout.

**This one fails silently.** A wrong path or a renamed key does not throw. DataStore simply finds
no file, returns defaults, and the app carries on looking perfectly healthy while:

- the theme reverts to System;
- the font-size slider returns to 1.0;
- **the tracking-start date is lost**, so days that were correctly neutral become red MISSED in the
  picker and the year strips, and the streak calculation changes;
- `selected_plan` reverts to `bible_companion`, so **a M'Cheyne reader's progress appears to
  vanish** — it is still in the database under `plan_id = 'mcheyne'`, but nothing is showing it;
- `show_streaks` returns to its absent-key default of **false** (D-S18-1), so a user who turned
  streaks on loses them;
- `persistent_notification_enabled` returns to its absent-key default of **true** (the amended
  D-S22-5), so a user who deliberately turned the tray notification **off** gets it back;
- the reminder turns off and its time reverts to 08:00;
- the external Bible app and reading-destination mode revert;
- **and every first-run marker clears, so a shipped user is greeted by the tracking-start prompt
  and the reading-destination question as though they had just installed the app.**

That last one is the visible symptom a user would report, and by then their settings are already
gone. **There is no crash report for this.** Play vitals would be clean.

### The path — this is the entire risk, in one line

Android's current store is created by
`PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }`
(`di/DataModule.kt:48-54`), which resolves to:

```
<filesDir>/datastore/settings.preferences_pb
```

The KMP `datastore-preferences-core` factory takes an **okio path**, and it will happily create a
brand-new empty store at whatever path you hand it. **The format is identical; the path is the
whole risk.**

> **Pin it: `AppFilePaths.files / "datastore" / "settings.preferences_pb"`.**
> Not "somewhere sensible." Not `filesDir` directly. That exact path, and a test that asserts the
> literal string.

### The keys

Enumerate them from `data/prefs/SettingsRepositoryImpl.kt`,
`data/prefs/PartialReadingRepositoryImpl.kt` and `bible/data/DataStoreBibleAssetVersionStore.kt`.
Known set includes: theme mode, `fontScale`, `tracking_start_epoch_day`,
`tracking_start_initialized`, `reminder_enabled`, `reminder_minute_of_day`,
`persistent_notification_enabled`, `selected_plan`, `bible_provider` / external-app id,
reading-destination mode, `show_streaks`, `reading_destination_prompt_completed`,
`upgrade_note_shown`, `partial_reading_segments`, `bible_asset_content_version`.

**Enumerate from the code, not from this list.** If your enumeration and this list differ, that is
worth reporting either way.

### The absent-key defaults are behaviour, not formatting

Several keys carry a **deliberate absent-key default that was flipped after shipping**, and each
was flipped with a mutation-pinned test:

- `show_streaks` — default **false** (D-S18-1 flipped it from true; explicitly stored `true`
  survives).
- `persistent_notification_enabled` — default **true** (D-S22-5 amended; explicitly stored `false`
  survives).
- `bible_provider` — an unknown id degrades to Blue Letter Bible, never to an error.

**"Absent key" and "explicitly stored value equal to the default" are different states**, and the
tests that pin that distinction must keep passing. A relocation that reads the file correctly but
loses the distinction is still a regression.

---

## Contract

### 1. What moves

| Item | Destination |
|---|---|
| `data/prefs/SettingsRepository{,Impl}.kt` | `shared/data/src/commonMain` |
| `data/prefs/PartialReadingRepository{,Impl}.kt` | `shared/data/src/commonMain` |
| `bible/data/DataStoreBibleAssetVersionStore.kt` | `shared/data/src/commonMain` |
| The DataStore factory + path | `shared/data`, path from `AppFilePaths` |

Library: `androidx.datastore:datastore-preferences-core` (the multiplatform artifact) with an okio
path. **Not** `datastore-preferences`, which is the Android-specific wrapper.

### 2. The path pin — write this test first

```
AppFilePaths.files / "datastore" / "settings.preferences_pb"
```

A test asserts the **resolved path string** ends with exactly `/datastore/settings.preferences_pb`,
and a Robolectric test asserts it equals what
`context.preferencesDataStoreFile("settings").absolutePath` returns **today**. That second
assertion is the one that actually protects users, because it compares against the real Android
API rather than against your own reading of it.

### 3. The key pin

A test asserting **every key's literal string**, the same discipline this project already applies to
plan-data literals and the M'Cheyne stream titles. A renamed key must fail **here**, in a unit
test, and not months later in a support email.

### 4. PG-2 — the fixture test

`SettingsFixtureReadTest` (from `p1-08`): point a DataStore at the **real 1.8.1 device fixture** and
read every key through `SettingsRepository`. Assert each value equals what the owner set — the
fixture was deliberately captured with **every setting away from its default**, so a total reset
cannot masquerade as a pass. Assert the **first-run markers read as already-completed.**

---

## Acceptance criteria

1. The resolved DataStore path is proven equal to today's Android path, by a Robolectric test that
   compares against `context.preferencesDataStoreFile("settings")`.
2. **A key-literal test asserts every key string.** Count them and state the number.
3. **PG-2 passes** against the real 1.8.1 fixture — every key, every value, and the first-run
   markers read as completed.
4. The absent-key-default tests still pass and still distinguish absent from
   explicitly-stored-equal-to-default, specifically for `show_streaks` (default **false**) and
   `persistent_notification_enabled` (default **true**).
5. `grep -rn "preferencesDataStoreFile\|datastore-preferences\b" shared/` returns **nothing** —
   the Android-specific helper does not cross into shared code.
6. **≥3 killed mutations, each restored byte-identically:** (a) the path loses its `datastore/`
   segment → the path pin and PG-2 red; (b) one key string renamed → the key pin and PG-2 red;
   (c) `show_streaks`'s absent-key default flipped back to `true` → its pin red.
7. **Test count unchanged. Zero deletions.** State before/after.
8. Full pipeline green; Kover ≥ the current floor.
9. **The six data gates untouched: 11 / 10 / 8 / 6 / 18 / 5.**
10. **R8 device smoke — upgrade in place (R8). This is the acceptance criterion that matters:**
    1. Install **1.10.0**. Change **every** setting away from default: theme → Dark; font size →
       not 1.0; tracking start → a specific non-Jan-1 date; plan → M'Cheyne; reading destination →
       external; external app → Bible Gateway; reminder → on at a non-08:00 time; persistent
       notification → **off**; show streaks → **on**. Mark a reading partially (populates
       `partial_reading_segments`).
    2. **Upgrade in place** to the 1.11.0 release build. Do **not** uninstall.
    3. Confirm **every one** of those values survived — check each individually, not "it looks
       fine."
    4. Confirm **no first-run dialog appears**: not the tracking-start prompt, not the
       reading-destination question, not the upgrade note.
    5. Confirm the partial check is still partial.
    6. Force-stop, relaunch, confirm again.

---

## Boundaries / write set

**Yours:**
- `shared/data/src/commonMain/.../prefs/**` (created by `git mv`)
- `shared/data/src/commonMain/.../bible/DataStoreBibleAssetVersionStore.kt`
- `app/src/main/kotlin/.../data/prefs/**` (emptied)
- The Koin declarations for the DataStore instance
- The corresponding tests, plus `SettingsFixtureReadTest`

**Not yours:**
- **`data/progress/**`** — **`p2-06`**, running in parallel. **The two stores must not move in one
  commit**; if both break, you cannot tell which.
- Any use case or ViewModel that reads a setting — import-only edits at most.
- `app/build.gradle.kts`, `gradle/libs.versions.toml` — **Build & Release.**
- The `p1-08` fixture files — read-only.

---

## Escalation triggers

- **The resolved path does not match today's Android path** → **Staff**, blocking. Do not "pick a
  reasonable path"; the path is not a design choice, it is where a shipped user's data already is.
- **PG-2 fails** → **Staff + Owner**, blocking. Every shipped user's settings are at stake and the
  failure is silent in production.
- **A key string must change** → **Staff**, blocking. There is no acceptable reason. If a key is
  ugly, it stays ugly.
- **`datastore-preferences-core` does not resolve, or its file format differs** → **Build &
  Release + Staff**, blocking. A format difference would be a genuine surprise and would need a
  migration, which is a much larger conversation.
- **You are tempted to "just re-prompt" first-run dialogs because it is easier than proving the
  markers survived** → **Staff**. That is a user-visible behaviour change to a shipped app.
