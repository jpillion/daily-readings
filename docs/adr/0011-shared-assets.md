# ADR-0011 — Shared assets: exactly one copy in git

**Status:** Draft, pending owner sign-off · **Date:** 2026-08-08 · **Author:** Staff / Port Architect

## Context

Five files under `app/src/main/assets/` are the project's core IP:

| Asset | Size | Consumed by |
|---|---|---|
| `plans/registry.json` | 307 B | `PlanRegistry` |
| `plans/bible_companion/plan.json` | 172 KB | `ReadingPlanAssetLoader` |
| `plans/mcheyne/plan.json` | 203 KB | ” |
| `plans/chronological/plan.json` | 117 KB | ” |
| `bible/bible.db` | **5.6 MB** | Room / SQLite, via a file path |

Each is byte-reproducible from a SHA-pinned source by a script in `tools/`, guarded by a CI job
that asserts a byte-diff of zero, and verified by a gate test that reads it **from the source
tree** (via the `planAssetsDir` system property set in `app/build.gradle.kts:86-95`).

Android reaches them through `context.assets.open(path)`, already abstracted behind a one-method
`PlanAssetSource` (`data/plan/PlanAssetSource.kt`) whose only implementation is a lambda in
`di/DataModule.kt:58-66`. iOS has no `assets/`; bundled files live in `NSBundle`.

The delivery brief already makes one-copy a hard acceptance criterion
(`find . -name bible.db -not -path './*/build/*'` returns one path).

## Decision

**One physical location, two packaging mechanisms, split by how the file is consumed.**

### Location

Move the assets to a shared, platform-neutral directory:

```
shared/assets/
  plans/registry.json
  plans/{bible_companion,mcheyne,chronological}/plan.json
  bible/bible.db
```

- **Android** picks it up via `android.sourceSets.main.assets.srcDir("../shared/assets")` — the
  same mechanism `app/build.gradle.kts` already uses for the debug-only Room schemas. The
  resulting APK/AAB layout is unchanged, so nothing about the Android runtime path changes.
- **iOS** picks it up via a Copy Bundle Resources build phase in `iosApp.xcodeproj` referencing
  the same directory (folder reference, so new plans are picked up automatically).

`tools/*.py` output paths and the CI byte-diff jobs update to the new location. The gate tests'
`planAssetsDir` becomes the new path (ADR-0010 turns it into a generated constant anyway).

**⟦VERIFY⟧ V7** (Build & Release): confirm the Android `assets.srcDir` redirect works and the
`planAssetsDir` system property + the `inputs.dir` up-to-date declaration still function. That
`inputs.dir` declaration exists because of a Sprint-1 lesson — without it, edits to a bundled
asset are silently skipped as UP-TO-DATE and the gate never re-runs. **Do not lose it.**

### Access — two mechanisms, deliberately

**Plan JSON → the existing `PlanAssetSource` seam, promoted to `shared/platform`.**

```kotlin
// shared/platform
/**
 * Reads a bundled text asset by its path relative to the app's asset root
 * (e.g. "plans/mcheyne/plan.json"). The asset is guaranteed to exist — a missing one is a
 * packaging defect, not a runtime condition — so implementations throw rather than returning null.
 * Never called on the main thread.
 */
fun interface TextAssetSource {
    suspend fun read(assetPath: String): String
}
```

Android's actual: `context.assets.open(path).bufferedReader().use { it.readText() }` — unchanged
from today. iOS's actual: `NSBundle.mainBundle.pathForResource` + okio read.

**`bible.db` → `BundledDatabaseProvider` (ADR-0007), which returns a real file path.**

**`bible.db` must NOT be read through Compose Resources or any byte-array loader.** SQLite needs a
filesystem path, and round-tripping 5.6 MB through a `ByteArray` at startup is unacceptable on
both platforms. This split — text assets through a reader, the database through a path — is
deliberate and is the reason there are two mechanisms rather than one.

## Alternatives rejected

**Compose Multiplatform Resources (`composeResources/files/`) for everything.** The obvious "KMP
way" and it does handle files. Rejected for two reasons: it gives you bytes, not a path, which is
wrong for a 5.6 MB SQLite file; and it would place data assets under the **UI** module, inverting
the dependency direction — `shared/data` would depend on `shared/ui` to load a plan. That is
backwards and would not survive review.

**Keep the assets in `app/src/main/assets/` and have iOS reference that path.** Rejected as
sloppy: it makes the Android module the owner of shared data, so the iOS build breaks the moment
anyone reorganises `app/`. It also reads as an accident rather than a decision.

**Duplicate the assets into `iosApp/Resources/`.** Rejected — the one-copy rule exists precisely
because this project's core IP is data, and a second copy is a silent drift vector. It would also
add 5.6 MB of duplicated binary to git.

**Generate the iOS copy at build time from the Android location.** Rejected as needless
indirection; a folder reference in the Xcode project achieves the same with nothing to go wrong.

**Git LFS for `bible.db`.** Rejected — 5.6 MB is well within normal git limits, the file changes
essentially never, and LFS would complicate every clone and every CI checkout for no gain.

## Consequences accepted

- **A repo-wide file move** touching `app/build.gradle.kts`, four `tools/*.py` output paths,
  three CI byte-diff jobs, and six gate tests. Sequence it as one atomic commit, early in Phase B,
  and verify the byte-diff jobs still reproduce zero before moving on.
- The iOS `.ipa` gains ~2 MB (compressed `bible.db`) plus ~30 KB (compressed plan JSON). Within
  the 12 MB budget the Android CI already enforces; the iOS pipeline should get the same gate.
- `TextAssetSource` is a `fun interface` in `shared/platform` while `PlanAssetSource` today lives
  in `data/plan/`. That is a move plus a rename; downstream call sites are few
  (`ReadingPlanRepositoryImpl`, `PlanRegistry`).
- iOS bundle resource paths are **flat by default** unless the folder is added as a *folder
  reference* (blue folder) rather than a *group* (yellow). Get this wrong and
  `plans/mcheyne/plan.json` becomes `plan.json` — colliding with two other files of the same
  name. **Explicitly call this out in the iOS task brief**; it is a 20-minute mistake that looks
  like a mysterious "wrong plan loaded" bug.

## Revisit when

- A fourth plan is added — confirm the folder reference picks it up without an Xcode change.
- A shipped `bible.db` correction lands — that exercises `BundledDatabaseProvider`'s re-copy path
  on both platforms for the first time.
- The bundle-size gate approaches its ceiling.
