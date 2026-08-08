# p2-05 — Move the non-persistence data layer into `shared/data`

> **Assignee:** Senior Shared-Core Engineer (second core engineer; parallel with `p2-04`)
> **Release:** 1.11.0 · **Merge order:** Tranche A, after `p2-03`. **Parallel with `p2-04`.**
> **Inherits:** [`p2-00-overview.md`](p2-00-overview.md) rules R1–R9.

---

## Objective

Move the parts of the data layer that are **not** a live user-data store into `shared/data`: the
plan loader and registry, the reference/URL layer's data side, the remote translations client and
its cache, and the bible text source.

**`ProgressDatabase` (`p2-06`) and DataStore (`p2-07`) are deliberately excluded.** They are the
two stores that hold irreplaceable shipped-user state, and they move under their own gates.

---

## Context

### What moves

| Area | Files | Note |
|---|---|---|
| `data/plan/**` | DTOs, `ReadingPlanAssetLoader`, `ReadingPlanRepositoryImpl`, `PlanRegistry`, `PlanAssetSource` | Already kotlinx-serialization. `PlanAssetSource` → the `TextAssetSource` seam (`p2-03` B1). |
| `data/reference/**` | `ProviderUrlBuilder`'s data-side collaborators | **`ProviderUrlBuilder` itself goes to `shared/domain` via `p2-04`.** Coordinate; do not both move it. |
| `data/apps/AppInstallChecker.kt` | 1 | **The only `data/` file touching `android.*`.** Becomes the `ExternalAppLauncher` seam (`p2-03` B3); the Android implementation stays in `:app`. |
| `bible/data/**` minus Room-specific | `BibleTextSource`, `RoomBibleTextSource`, `VerseEntity`, `VerseDao`, `BibleDatabase`, `BibleAssetGate`, `BibleAssetVersion`, `DataStoreBibleAssetVersionStore` | See the warnings below. |
| `bible/data/remote/**` | `BibleApiClient`, `HttpFumsReporter`, `FumsIdentity`, `BibleTextCache`, `BibleTextResolver`, `UsxTransformer` | Already Ktor + okio after `p1-03`/`p1-04`. |
| `di/**` | The Koin module declarations | Split to the module each declaration belongs to; the Android-only ones stay in `:app`. |

### Three things in this write set that must NOT change

**1. `VerseEntity`, `VerseDao`, `BibleDatabase` — move the files, change the package, and change
nothing else.**

`VerseEntity` is the ADR-0007 identity-hash surface. The shipped `bible.db` carries a hand-forged
`room_master_table` row matching the hash Room's generator computed from this entity. Room
validates it on **first query** and a mismatch throws `IllegalStateException: Pre-packaged database
has an invalid schema`.

**This exact failure shipped to production once** (sprint-00F): every chapter showed "couldn't load
this chapter," and nothing caught it because `BibleTextVerificationTest` bypasses Room via JDBC and
every reader test fakes `BibleTextSource`.

A package move alone does **not** change the hash — it is computed from table name, column
names/types/nullability/defaults, primary key, indices and foreign keys, none of which are
package-sensitive. **An added `@Index`, a reordered `@ColumnInfo`, or a changed nullability does.**

> **`BibleDatabaseRoomOpenTest` (5) is your tripwire. If it goes red, stop.**

**2. `createFromAsset` stays exactly where it is, in `androidMain`.**

ADR-0007 **Amendment A2**: the Android bible-DB open path does **not** change in 1.11.0.
`BundledDatabaseProvider` is declared by `p2-03` but is not wired until tranche B. `shared/data`
is a KMP module with one target, so an Android-only API in `androidMain` compiles and is honest
about what it is.

**3. `BibleAssetGate` keeps its shape**, including the `runBlocking`-inside-a-DI-provider
construction. It is ugly; ADR-0007 says it folds into `BundledDatabaseProvider`. **Later.** Its
three Robolectric wiring tests stay in `androidUnitTest` and stay green.

---

## Contract

### 1. Move, do not edit (rule R5)

`git mv`, then packages and imports. Split any genuinely necessary change into its own commit.

### 2. Seams replace Android implementations

- `PlanAssetSource` → `TextAssetSource` (`p2-03` B1). The Android implementation
  (`context.assets.open(path).bufferedReader().use { it.readText() }`) moves to `:app` **unchanged**.
- `AppInstallChecker` → `ExternalAppLauncher` (`p2-03` B3). Android implementation stays in `:app`
  with the manifest `<queries>` declaration.
- `android.util.Log` → `Logger` (`p2-03` C5).

**Do not widen a seam to fit a call site.** If one does not fit, escalate to Staff.

### 3. Tests

Move to `commonTest` where they can, converting JUnit 4 → `kotlin.test`. **`BibleDatabaseRoomOpenTest`
and the `BibleAssetGate` Robolectric tests stay in `androidUnitTest`** — that is correct and
permanent, not a compromise.

`BibleTextVerificationTest` (18, sqlite-jdbc) moves to `shared/data/src/jvmTest`, **unchanged**. It
asks *"is the committed `bible.db` the right data?"* — a platform-independent question that needs
answering once per commit, not once per target. Running it on a simulator would tell us nothing new
(ADR-0010 tier 2).

### 4. `PlanRegistry`'s single-flight cache

`ReadingPlanRepositoryImpl` holds a **per-plan cache under a mutex**, so the active plan's asset is
parsed once and a Bible Companion user **never parses M'Cheyne**. That is a cold-start property
worth keeping. Its `@Singleton`-ness is `p1-07`'s Koin `single { }`; **verify it survived** — an
accidental `factory { }` reintroduces per-subscription parsing, and nothing would fail, it would
just get slower.

---

## Acceptance criteria

1. The listed files live under `shared/data/src/commonMain` (or `androidMain` where Android-only).
   State the count moved and the count left behind, with reasons for each of the latter.
2. `data/apps/AppInstallChecker.kt` is gone; `ExternalAppLauncher` has an Android implementation in
   `:app` **and the manifest `<queries>` block is unchanged.**
3. `grep -rn "android\.\|androidx\." shared/data/src/commonMain/` returns **nothing**.
   `androidMain` may, and that is the point of the split.
4. **`VerseEntity`, `VerseDao` and `BibleDatabase` differ from their previous versions ONLY in
   package declaration and imports.** Prove it: quote a `git diff -M` showing exactly that.
5. **`BibleDatabaseRoomOpenTest` (5) passes**, reading Gen 1:1, John 3:16, John 11:35 and the Ps 3
   verse-0 superscription through the real asset via the real `createFromAsset` builder.
6. `BibleTextVerificationTest` (18) passes from `shared/data/src/jvmTest`.
7. `createFromAsset` still exists, in `androidMain`. **Confirm it explicitly** — its absence would
   mean someone did tranche B's work early.
8. `BibleAssetGate`'s three Robolectric wiring tests pass, and its two killed mutations (comparison
   flip, skipped delete) are **re-verified and restored byte-identically.**
9. `ReadingPlanRepository` is proven single-instance: resolve twice, assert the asset parses
   **once**, and assert **M'Cheyne is not parsed** when Bible Companion is active.
10. **Test count unchanged. Zero deletions.** State before/after.
11. Full pipeline green; **Kover ≥ the current floor (~95% on data)**, with `shared/data` confirmed
    inside the report.
12. **The six data gates untouched: 11 / 10 / 8 / 6 / 18 / 5.**
13. **R8 device smoke:** cold launch; open the reader and read a KJV chapter (`bible.db` through the
    moved Room code — **the specific risk of this task**); fetch NKJV online, then go offline and
    confirm the fallback banner; switch plan to M'Cheyne and back; tap a verse out to an external
    provider.

---

## Boundaries / write set

**Yours:**
- `shared/data/src/{commonMain,androidMain,commonTest,jvmTest}/**` (created by `git mv`)
- `app/src/main/kotlin/.../data/{plan,reference,apps}/**` (emptied)
- `app/src/main/kotlin/.../bible/data/**` (emptied, except what stays Android-only)
- `app/src/main/kotlin/.../di/**` — Koin module declarations, split by destination
- The corresponding test trees

**Not yours:**
- **`data/progress/**`** — **`p2-06`.** Live user data.
- **`data/prefs/**`** — **`p2-07`.** Live user data.
- `domain/`, `core/`, `bible/domain/` — **`p2-04`**, running in parallel. **`ProviderUrlBuilder` is
  theirs; coordinate before touching `data/reference/`.**
- `ui/`, `bible/ui/`, `widget/` — Phase 3.
- `shared/platform/**` — **Staff.**
- Any `build.gradle.kts` — **Build & Release.**
- **`tools/build_bible_db.py`** and `shared/assets/**` — the asset and its builder are untouchable
  here.

---

## Escalation triggers

- **`BibleDatabaseRoomOpenTest` goes red** → **Staff**, blocking, immediately. That is the
  sprint-00F signal: the identity hash or the asset path changed. Do not investigate for an hour
  first; it is the one failure in this task that has already reached users once.
- **`VerseEntity` needs any change to compile** → **Staff**, blocking. Any change to it is a change
  to the shipped asset's validity.
- **A seam does not fit a call site** → **Staff**. Do not widen it.
- **`p2-04` and this task both want `data/reference/ProviderUrlBuilder.kt`** → agree it is theirs
  (it goes to `shared/domain`) before either of you touches it. A merge conflict in a
  live-verified-URL file is worth ten minutes of coordination.
- **`ReadingPlanRepository` resolves as more than one instance** → **Staff**. `p1-07`'s scope
  mapping is wrong and it is a cold-start regression that no test currently catches.
