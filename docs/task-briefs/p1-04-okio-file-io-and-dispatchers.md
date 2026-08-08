# p1-04 — `java.io.File` → okio, the `AppFilePaths` seam, and the `Dispatchers.IO` sweep

> **Assignee:** Senior Shared-Core Engineer (second core engineer; parallel with `p1-03`)
> **Release:** 1.9.0 · **Merge order:** Group A, parallel with `p1-01`, `p1-03`, `p1-08`.
> **Inherits:** [`p1-00-overview.md`](p1-00-overview.md) rules R1–R7.
> **Preconditions:** Gate 0 closed. **Build & Release has added okio.**

---

## Objective

Three small, related changes with one theme — **remove `java.io` and platform-implicit file
locations from the data layer**:

1. `java.io.File` → **okio** `FileSystem` / `Path` at both call sites.
2. Introduce **`AppFilePaths`**, the seam that answers *"where does this app keep its private
   data?"* — because on iOS that answer is **not** the default sandbox (ADR-0006 A1).
3. Route the remaining **four** `Dispatchers.IO` references through the existing `@IoDispatcher`
   qualifier.

---

## Context

### `java.io.File` — two call sites, both real

| File | Usage |
|---|---|
| `bible/data/remote/BibleTextCache.kt` | The online-translation verse cache: a size-capped LRU keyed by file, with **14-day freshness** enforced via `System.currentTimeMillis() - f.lastModified() > MAX_AGE_MS` (line 94), and `deleteRecursively()` for clear (line 125). The freshness rule is an **API.Bible licence obligation** from sprint 00R, not a performance tweak. |
| `bible/data/BibleAssetVersion.kt` | Deletes the copied `bible.db` / `-wal` / `-shm` when `ASSET_CONTENT_VERSION` is bumped, so a shipped text correction reaches existing installs (D-V3-8). |

The okio subtlety worth naming up front: there is **no `deleteRecursively()` and no `lastModified`
property**. Use `FileSystem.deleteRecursively(path)` and
`FileSystem.metadata(path).lastModifiedAtMillis` — the latter is **nullable**, and the null branch
must be decided deliberately.

> **Decision, so it is not invented at 5pm: a null `lastModifiedAtMillis` is treated as STALE.**
> The cache is a convenience over a network fetch; the licence obligation is that stale text is not
> served. Failing toward a refetch is correct. Add a test.

### `AppFilePaths` — why this exists now, in an Android-only release

`BibleAssetGate` reaches `context.getDatabasePath(name)` directly (`bible/data/BibleAssetGate.kt:47`)
and `BibleTextCache` derives its root from the app context. On Android those are the right answers.

On iOS they will **not** be, and the reason is decided and non-obvious: **D-PORT-4 reserves the App
Group container from the first iOS build**, so `progress.db`, the DataStore settings file and the
copied `bible.db` live in `group.com.jpillion.dailyreadingplanner`, **not** in the app sandbox's
default Application Support directory. Adding that later would be a user-data migration on real
devices.

**Introducing the seam now, on Android, costs an afternoon and makes that a one-line iOS decision
later.** A brief that says "use Application Support" has already made the wrong decision — which is
exactly why the interface is written to semantics and Staff holds the pen on it.

### `Dispatchers.IO` — the quiet Kotlin/Native trap

**`Dispatchers.IO` does not exist on Kotlin/Native.** It compiles fine on Android, so it is easy to
miss and it fails at a phase boundary rather than at the keyboard. Remaining sites after `p1-03`
takes `BibleApiClient`:

- `bible/data/RoomBibleTextSource.kt:42`
- `bible/data/remote/BibleTextCache.kt:81`, `:113`, `:125`
- `di/DispatcherModule.kt:25` — **the provider. This one stays**, and becomes the single place the
  platform answer lives.

---

## Contract

### The seam — Staff-owned. Copy exactly. Do not add members.

`app/src/main/kotlin/.../platform/AppFilePaths.kt`:

```kotlin
package com.jpillion.dailyreadingplanner.platform

import okio.Path

/**
 * Where this app keeps its private data on this device.
 *
 * These are the app's own directories: not user-visible, not shared with other apps, backed up or
 * excluded from backup according to each platform's own convention for that category. Callers must
 * never construct a path by string-appending to a platform directory of their own; every private
 * file the app owns is resolved from here, so that the *location* decision has exactly one home.
 *
 * Directories are guaranteed to exist when returned. Implementations create them if needed.
 */
interface AppFilePaths {

    /**
     * The directory holding SQLite databases. A database's file is [databases] / "<name>",
     * with its `-wal` and `-shm` sidecars alongside.
     */
    val databases: Path

    /**
     * The directory for regenerable caches. The platform may reclaim this space at any time, so
     * nothing whose loss the user would notice belongs here.
     */
    val cache: Path

    /** The directory for small persistent app files that are not databases and not caches. */
    val files: Path
}
```

Android implementation `platform/AndroidAppFilePaths.kt`: `context.getDatabasePath("x").parentFile`,
`context.cacheDir`, `context.filesDir`, each `.toOkioPath()`. `@Singleton` in `di/DataModule.kt` or
`di/AppModule.kt` — your choice, state it.

> **The iOS implementation is not written here and is not yours.** But it is the reason the
> interface says *"where this app keeps its private data"* rather than *"filesDir"*. Do not narrow
> the KDoc.

### The okio conversions

`BibleTextCache` and `BibleAssetVersion` take `FileSystem` (`FileSystem.SYSTEM` in production, and
now trivially fakeable in tests) plus `AppFilePaths`. **Behaviour is identical**: same directory
layout, same file names, same LRU rule, same 14-day `MAX_AGE_MS`, same
"no LRU touch on read — `lastModified` IS the fetch time" comment, which must survive because it
documents why the freshness rule works.

### `Dispatchers.IO`

Inject `@IoDispatcher CoroutineDispatcher` and `withContext(ioDispatcher)`. `DispatcherModule` keeps
providing `Dispatchers.IO` on Android.

---

## Acceptance criteria

1. `grep -rn "java.io.File\|java.io.BufferedReader" app/src/main/kotlin` returns **nothing**.
2. `grep -rn "Dispatchers.IO" app/src/main/kotlin` returns **exactly one** line —
   `di/DispatcherModule.kt:25`.
3. `grep -rn "getDatabasePath\|cacheDir\|filesDir" app/src/main/kotlin` returns hits **only** in
   `platform/AndroidAppFilePaths.kt`.
4. `AppFilePaths` is byte-identical to the source above.
5. **The 14-day freshness rule is unchanged and directly tested**, including the boundary
   (exactly `MAX_AGE_MS` old is *fresh*; one millisecond older is *stale* — match today's `>`
   semantics exactly) **and the null-`lastModifiedAtMillis` → stale rule.**
6. `BibleAssetVersion`'s delete-on-version-bump behaviour is unchanged and its **three existing
   Robolectric wiring tests pass**, along with the **two previously-killed mutations** (comparison
   flip, skipped delete) still being killable. Re-run both and say so.
7. **≥3 killed mutations, each by its intended test, restored byte-identically:** (a) the freshness
   comparison flipped to `<`; (b) null `lastModifiedAtMillis` treated as fresh; (c) the `-wal`/`-shm`
   sidecars not deleted on a version bump.
8. Every test file touched is converted Truth → assertk (R4).
9. Full pipeline green; Kover ≥ the current floor.
10. **The six data gates untouched, counts unchanged: 11 / 10 / 8 / 6 / 18 / 5.** In particular,
    `BibleDatabaseRoomOpenTest` (5) must still pass — you are changing code adjacent to the
    database file's location, and that test is the one that catches the sprint-00F class.
11. **R5 R8 release-build device smoke:** fetch an NKJV chapter (writes cache), force-stop, reopen
    and confirm it renders from cache **offline**; then open a KJV chapter and confirm the reader
    still works (`bible.db` still resolves after the `AppFilePaths` change — **this is the specific
    risk of this task**).

---

## Boundaries / write set

**Yours:**
- `app/src/main/kotlin/.../platform/AppFilePaths.kt` **(new)**
- `app/src/main/kotlin/.../platform/AndroidAppFilePaths.kt` **(new)**
- `app/src/main/kotlin/.../bible/data/remote/BibleTextCache.kt`
- `app/src/main/kotlin/.../bible/data/BibleAssetVersion.kt`
- `app/src/main/kotlin/.../bible/data/BibleAssetGate.kt` — **paths and dispatcher only.**
- `app/src/main/kotlin/.../bible/data/RoomBibleTextSource.kt` — **the dispatcher only.**
- `app/src/main/kotlin/.../di/{DispatcherModule,BibleModule}.kt`
- The test files covering the above

**Not yours:**
- `bible/data/remote/{BibleApiClient,HttpFumsReporter,FumsIdentity}.kt`,
  `data/reference/**`, `di/BibleRemoteModule.kt` — **`p1-03`**.
- Anything with a `java.time` import — **`p1-02`**.
- **`bible/data/BibleDatabase.kt`, `VerseEntity.kt`, `VerseDao.kt` — nobody touches these in Phase
  1.** They are the ADR-0007 identity-hash surface. A change to `VerseEntity` invalidates the
  shipped asset.
- **`BibleAssetGate`'s `runBlocking`-inside-a-DI-provider structure.** It is ugly and ADR-0007 says
  it should fold into `BundledDatabaseProvider` — **later.** Not this task, not this release.
  Faithful port first.
- `gradle/libs.versions.toml`, `app/build.gradle.kts` — **Build & Release.** okio is a new
  dependency: request it.

---

## Escalation triggers

- **okio has no equivalent for something you need** → **Staff**. Bring the exact operation. Do not
  keep a stray `java.io.File` "just for this one thing" — that is the boundary eroding on contact.
- **The freshness or LRU behaviour would change** → **Staff**, blocking. It is a licence
  obligation, and "the okio API made it awkward" is not a reason to alter it.
- **You want a fourth member on `AppFilePaths`** (a "downloads" or "temp" directory) → **Staff**.
  Three covers every current caller.
- **`BibleDatabaseRoomOpenTest` goes red** → **Staff**, blocking, immediately. That is the
  sprint-00F signal and it means the database path moved.
- **You are tempted to restructure `BibleAssetGate`** → don't; escalate if you think it is
  unavoidable.
