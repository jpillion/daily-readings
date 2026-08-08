# p2-09 — Tranche B: add the iOS targets, the `BundledDatabaseProvider`, and the two new iOS gates

> **Assignee:** Senior Shared-Core Engineer (drives) + iOS Platform Engineer (the bundle side)
> **Release:** **none.** No Play release, no App Store build. The shared core simply compiles and
> its tests pass on a simulator.
> **Merge order:** Tranche B — **after 1.11.0 is live on Play with 24–72 h of clean vitals.**
> **Inherits:** [`p2-00-overview.md`](p2-00-overview.md) rules R1–R9, **especially R9.**
> **Preconditions:** Xcode installed (owner §7 item 1). **Gate 0 V1 answered** — this brief takes
> whichever ADR-0007 branch it chose. **`p2-08` has specified `BundleAssetIntegrityTest`.**

---

## Objective

Add `iosArm64`, `iosSimulatorArm64` and `iosX64` to the shared modules; write the iOS actuals for
the file/asset/database seams; replace `createFromAsset` with `BundledDatabaseProvider` on **both**
platforms; and land the **two new, release-blocking iOS gates.**

This is the phase exit: `shared/domain` and `shared/data` compile for iOS, tier-1 gates green in
`commonTest` on iOS targets, and both new gates green on a simulator.

**No Apple Developer Program needed.** Compiling and running simulator tests need Xcode only.

---

## Context

### The three axes of divergence — say this out loud before reporting anything

**You cannot run Kotlin/Native unit tests on a physical iPhone.** Every automated iOS result in this
task is **simulator + debug + Apple Silicon host arch**. The shipped artifact is **device + release
+ arm64**.

**There is no configuration in which the suite runs against what ships.** Android had **one** axis
of divergence (debug vs R8) and it still shipped the 1.7.0 P0. iOS has three. Nothing in this brief
removes that; the `ios-release-smoke` gate from Phase 4 onward is the only mitigation, and it does
not exist yet.

**So: no result from this task may be reported as "verified on iOS."** It is "compiles and passes
on a simulator," which is a real and useful thing and is not the same thing.

### Why this is not in the 1.11.0 release

ADR-0007 **Amendment A2**: the Android bible-DB open path does **not** change in 1.11.0. Replacing
`createFromAsset` is the change that failed in production once (sprint-00F — every chapter showed
"couldn't load this chapter"), and there is no reason to put it in front of shipped Android users in
a release whose purpose is a database relocation. Tranche B ships nothing, so the change costs
nothing and buys a clean bisect.

**But note carefully: this task still changes Android's open path**, because
`BundledDatabaseProvider` replaces `createFromAsset` on both platforms. It simply does so in a
commit that no user receives until a later release. **`BibleDatabaseRoomOpenTest` (5) is therefore
still a hard gate here, and it is the most important test in this brief.**

---

## Contract

### 1. The targets

Add `iosArm64`, `iosSimulatorArm64`, `iosX64` to `shared/domain`, `shared/platform`, `shared/data`.
`shared/ui` follows in Phase 3.

> **Expect the first Kotlin/Native link to take 20–45 minutes** and to consume significant disk in
> `~/.konan` and DerivedData. The machine was at **94% used / 126 GB free** before Xcode. If disk
> becomes the blocker, that is an **owner** escalation, not something to work around.

### 2. The iOS actuals for the seams `p2-03` defined

| Seam | iOS implementation |
|---|---|
| `AppFilePaths` | **Paths inside the App Group container** — `NSFileManager.containerURLForSecurityApplicationGroupIdentifier("group.com.jpillion.dailyreadingplanner")`, **not** the app sandbox's Application Support. See below. |
| `TextAssetSource` | `NSBundle.mainBundle.pathForResource` at the **nested** path, + okio read |
| `BundledDatabaseProvider` | copy from `NSBundle` into `AppFilePaths.databases`, honouring `contentVersion` |
| `DateProvider` | `Clock.System` + `TimeZone.currentSystemDefault()` — identical to Android |
| `Logger` | `NSLog` |
| `WidgetRefresher` | **no-op** (v1.0; becomes `WidgetCenter` when WidgetKit ships) |
| `PlatformCapabilities` | `false, false, false, false` |
| `ExternalAppLauncher` | `UIApplication.canOpenURL` with declared schemes; **`MYSWORD` → `false` permanently** |

**`AppFilePaths` on iOS uses the App Group container from the first build.** This is **D-PORT-4**,
owner-signed. `progress.db`, the DataStore settings file and the copied `bible.db` all live there.

> Reserving it costs one entitlement and one path constant. **Adding it later is a user-data
> migration on real devices** — moving irreplaceable reading history out of a sandbox on a shipped
> app, with a half-migrated failure mode. That is why the seam's KDoc says *"where this app keeps
> its private data"* rather than naming a directory.
>
> **This depends on the owner having enabled App Groups at bundle-ID registration** (the amendment
> to `RELEASING-IOS.md` Step 2). If they have not, **escalate to the owner immediately** — adding
> the capability later invalidates every provisioning profile `fastlane match` minted.

### 3. `BundledDatabaseProvider` replaces `createFromAsset` — on both platforms

- **Android's actual:** copies from `context.assets` into `AppFilePaths.databases`.
- **iOS's actual:** copies from `NSBundle` into the App Group container, using okio.
- Both then feed `Room.databaseBuilder<BibleDatabase>(path)` with the driver ADR-0007's branch
  selected. `createFromAsset` is **deleted**.

**`BibleAssetGate`'s delete-on-version-bump logic folds into `materialise`**, which is where it
belonged all along, and its `runBlocking`-inside-a-DI-provider construction disappears with it.
**That is a genuine improvement the port makes possible — and its three Robolectric wiring tests and
two killed mutations (comparison flip, skipped delete) must be preserved in spirit against the new
seam. Write the replacement tests in this task**, not later.

**The copy runs off the main thread on both platforms.** 5.6 MB is a few hundred milliseconds;
Android already pays this via `createFromAsset`. Same StrictMode discipline.

### 4. The iOS bundle — the flat-resource trap

`shared/assets/` is added to the Xcode project as a **folder reference (blue)**, **not a group
(yellow)**. As a group, `plans/mcheyne/plan.json` becomes `plan.json` in the bundle root and
**collides with two identically-named files.** It presents as "the wrong plan loaded."

A folder reference also picks up a fourth plan automatically, with no Xcode change.

### 5. The two new gates — both release-blocking, neither optional

**`BibleDatabaseOpenTest` (iosTest, 5 assertions).** Opens the bundled asset through whatever
ADR-0007 selected and reads the same four probes as the Android test: **Genesis 1:1, John 3:16,
John 11:35 ("Jesus wept."), and the Psalm 3 verse-0 superscription.**

> **This is the test that would have caught sprint-00F.** Its absence on Android is precisely why
> that P0 shipped: `BibleTextVerificationTest` bypasses Room via JDBC and every reader test fakes
> `BibleTextSource`, so **Room never opened the real asset in any test.** Not optional. Not a
> footnote. It is new code with no Android counterpart to port from — budget it as real work.

**`BundleAssetIntegrityTest` (iosTest, 5 assertions).** As specified by `p2-08`: resolve each of the
five assets from `NSBundle` **at its expected nested path** and assert SHA-256 against a generated
constant. **By nested path, never by filename** — a test that finds `plan.json` anywhere in the
bundle passes under exactly the defect it exists to catch.

---

## Acceptance criteria

1. `shared/domain`, `shared/platform` and `shared/data` compile for `iosArm64`,
   `iosSimulatorArm64` and `iosX64`.
2. **Tier-1 gates green on an iOS simulator target: 11 / 10 / 8 / 6 = 35 assertions.** Report using
   the D-PORT-6 ledger, verbatim.
3. **`BibleDatabaseOpenTest` green on a simulator**, all four probes.
4. **`BundleAssetIntegrityTest` green on a simulator**, all five assets at their **nested** paths.
   **Demonstrate it failing**: add `shared/assets` as a group instead of a folder reference,
   confirm red, restore to a folder reference. **A packaging gate nobody has seen fail is not known
   to work**, and this is the one whose defect is a 20-minute mistake.
5. **`BibleDatabaseRoomOpenTest` (5) still green on Android** through the new
   `BundledDatabaseProvider` path. **This is the highest-priority criterion in the brief.**
6. `createFromAsset` is **gone**: `grep -rn "createFromAsset" .` returns nothing outside docs.
7. `BibleAssetGate`'s behaviour is preserved by replacement tests against the new seam, and **both**
   its mutations (comparison flip, skipped delete) are re-killed and restored byte-identically.
8. iOS `AppFilePaths` returns **App Group container** paths. A test asserts the container prefix.
9. `PlatformCapabilities` on iOS is `false, false, false, false`, pinned.
10. `ExternalAppLauncher.isAvailable(MYSWORD)` is `false` on iOS, pinned. The enum value still
    exists — **it is a persisted id.**
11. **The Android side of every seam is unchanged in behaviour**, and the full Android pipeline is
    green with **unchanged counts: 11 / 10 / 8 / 6 / 18 / 5.**
12. **A `.ipa`/framework size figure is reported**, so an iOS-specific budget can be set from real
    numbers. **Do NOT inherit Android's 12 MB gate** — an `.ipa` with the same assets plus a
    Kotlin/Native framework, unsplit by ABI, will be larger, and nobody has estimated it. Set the
    number once a real archive exists.
13. **The R9 honesty statement appears in the report**: *"simulator + debug + host arch. The
    shipped artifact is device + release + arm64. No configuration runs the suite against what
    ships."*
14. **No R8 device smoke** — nothing ships from this task. But the **Android** pipeline must be
    fully green, because this task changed Android's bible-DB open path.

---

## Boundaries / write set

**Yours:**
- The `shared/*/build.gradle.kts` **iOS target blocks** — coordinate with **Build & Release**, who
  own those files; agree who commits.
- `shared/platform/src/iosMain/**` — the actuals
- `shared/data/src/iosMain/**` and `src/commonMain` — the `BundledDatabaseProvider` wiring
- `shared/data/src/iosTest/**` — the two new gates
- `shared/data/src/androidMain/**` — the Android actual of `BundledDatabaseProvider`;
  `createFromAsset` deleted
- The Xcode project's Copy Bundle Resources phase (folder reference)

**Not yours:**
- **`iosApp/Configuration/**`** — **Build & Release**, and it is a hard invariant.
- **`shared/assets/**`** — read-only. One copy in git; `find . -name bible.db -not -path './*/build/*'`
  returns one path.
- **`tools/build_bible_db.py`** — unless ADR-0007 chose Stage 1b, in which case removing
  `ROOM_IDENTITY_HASH` and the `room_master_table` row is a **separate, Staff-approved commit** with
  the `data-rebuild` byte-diff gate re-verified.
- `shared/ui/**` — Phase 3.
- `data/progress/**`, `data/prefs/**` — `p2-06`/`p2-07`, already merged and shipped. **Do not
  revisit them here.**

---

## Escalation triggers

- **`BibleDatabaseRoomOpenTest` goes red** → **Staff**, blocking, immediately. The sprint-00F
  signal, on the exact change that caused it.
- **The App Group container is unavailable** because the capability was not enabled at bundle-ID
  registration → **Owner**, blocking. Adding it later invalidates provisioning profiles.
- **`BundleAssetIntegrityTest` cannot be made to fail** in its folder-reference-vs-group
  demonstration → **Staff**, blocking. It is not testing what it claims to.
- **Xcode is not installed, or disk is exhausted** → **Owner**, blocking. Xcode is ~17 GB to
  download and ~40 GB installed, before `~/.konan` caches.
- **The first Kotlin/Native link takes materially longer than ~45 min, or CI cannot afford it** →
  **Build & Release + EM.** macOS runners cost **10×** and GitHub Free gives ~200 macOS
  minutes/month — **less than one release run.** The owner has a cost-model decision pending (§7
  item 9); **engineering must not make it for them.**
- **Anyone reports a simulator result as "verified on iOS"** → correct it. R9 exists because that
  sentence is how a three-axis divergence gets forgotten.
