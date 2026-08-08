# Task brief — iOS delivery pipeline (tag → TestFlight → App Store)

> **Assignee:** `kmp-build-release-eng` (singular — never parallelize this role)
> **Runs in:** **Phase 4** (platform seam, parity, delivery) and Phase 5, per
> [../ios-port-approach.md](../ios-port-approach.md) §5 — which is the **authoritative phase
> vocabulary** for this program. The roster's phases 0–5 and the older briefs' phases 0/A–E do not
> map to each other (approach doc, Appendix A9); this brief uses 0–5.
> **Blocks on:** [p0-build-foundation.md](p0-build-foundation.md) (the dependency contract and the
> Gradle→Xcode handoff), a `shared/*` that compiles for `iosArm64`, and the owner's Apple
> prerequisites in [../RELEASING-IOS.md](../RELEASING-IOS.md) Part 1.
> **Merge order:** land alone. It touches global build state and three new workflows.
> **Rewritten:** 2026-08-08, by the Build & Release Engineer, applying his own review of the
> 2026-08-08 original. See §0.

---

## 0. What changed in this rewrite, and why

The original was written before the port had an architecture. Twelve specific defects; each fix is
pointed at here so a reader can check the work rather than trust it.

| # | Defect in the original | Fixed in |
|---|---|---|
| 1 | **Under-scoped the role and mis-sequenced it.** Dependency decisions gate Phase 1 (three Play releases), not Phase B. As written, the person who owns the dependency graph arrived after the modules existed. | Split out: [p0-build-foundation.md](p0-build-foundation.md). This brief is now genuinely delivery-only. |
| 2 | Hard-coded `app/src/main/assets/**` as a CI trigger path — **a path the port deletes** (ADR-0011 moves assets to `shared/assets/`). | §3.3 |
| 3 | Acceptance criterion 2 demanded gate **assertion-count parity** with the Android run. Not achievable, and an invitation to a false claim. | §4.2, the D-PORT-6 ledger |
| 4 | No Gradle→Xcode handoff contract at all — the highest-consequence, lowest-visibility failure in an iOS KMP pipeline. | §3.2 + §4.3 |
| 5 | No `~/.konan` cache strategy. It is the difference between a 4-minute and a 25-minute run, on the most expensive runner GitHub sells. | §3.6 |
| 6 | Did not carry across `RELEASING.md`'s most expensive lesson: the Play API could not create the **first** release on an empty track, forcing a manual Console promotion for 1.5.1. App Store Connect has the same shape. | §3.10 (D-PORT-8) |
| 7 | No release-configuration smoke requirement, on a project whose worst production defect (1.7.0) was invisible in debug builds. | §3.7 |
| 8 | No dSYM / symbolication criterion. Kotlin/Native crash reports are unreadable without a UUID-matched dSYM. | §3.8 + §4.8 |
| 9 | No Ruby/fastlane environment contract. This Mac has **system Ruby 2.6.10** and no fastlane (VERIFIED 2026-08-08). | §3.5 |
| 10 | Inherited Android's 12 MB bundle gate. An `.ipa` carrying `bible.db` plus a Kotlin/Native framework is a different animal and **nobody has estimated it**. | §3.11 |
| 11 | `iosApp/iosApp.xcodeproj/project.pbxproj` had **no owner**. Build settings are mine, file references are iOS Platform's, and Xcode rewrites the file constantly. | §3.9 — XcodeGen adopted |
| 12 | Said "the five data-verification gates." **There are six** — `PlanSegmentGateTest` (6 assertions, 2,920 portions) postdates the original. | §2, §4.2 |

---

## 1. Objective

A tagged commit produces a signed iOS build on TestFlight without a human touching Xcode, and a
manual dispatch submits that exact build to App Review — mirroring the proven Play pipeline in
[../RELEASING.md](../RELEASING.md) so the project has **one** release mental model, not two.

Done means: `git tag ios-v1.0.1 && git push origin ios-v1.0.1` puts a build in front of TestFlight
internal testers with no local step.

**Explicitly out of scope, and this is the point of §3.10: the *first* App Store submission is
manual and is not automated by this task.**

---

## 2. Context

- The iOS binary comes from the KMP + Compose Multiplatform shared core. There is no separate Swift
  app. The framework/handoff mechanics are settled in
  [p0-build-foundation.md](p0-build-foundation.md) §3.D–§3.E — **read that first; do not re-decide
  it here.**
- **The Android pipeline is the oracle for release *discipline*.** Read
  [../RELEASING.md](../RELEASING.md) and
  [../../.github/workflows/release.yml](../../.github/workflows/release.yml) before writing
  anything. Inherit specifically:
  - the **tag-matches-version guard** that hard-fails on mismatch;
  - **commit-SHA pinning** for any action that sees a publishing secret (`release.yml` pins
    `r0adkll/upload-google-play@e738b9dd…` for exactly this reason);
  - the **artifact upload** of the shipped binary as a backup;
  - release notes **committed in the repo**, not typed into a console.
- **There are six data-verification gates**, not five:

  | Gate | Assertions |
  |---|---|
  | `ReadingPlanVerificationTest` (Bible Companion) | 11 |
  | `McheynePlanVerificationTest` | 10 |
  | `ChronologicalPlanVerificationTest` | 8 |
  | `PlanSegmentGateTest` | 6 |
  | `BibleTextVerificationTest` | 18 |
  | `BibleDatabaseRoomOpenTest` | 5 |

  They are the project's core-IP protection. §4.2 states exactly which run where, and the honest
  count to report.
- The shared assets live at **`shared/assets/`** after ADR-0011 (they are at
  `app/src/main/assets/` today). **There must remain exactly one copy in git.** A second copy is a
  drift vector and is rejected in review, not negotiated.
- Owner-supplied credentials are specified in [../RELEASING-IOS.md](../RELEASING-IOS.md) Step 6.
  **Use these exact secret names** — the owner is configuring them from that document:
  `APP_STORE_CONNECT_ISSUER_ID`, `APP_STORE_CONNECT_KEY_ID`, `APP_STORE_CONNECT_KEY_P8_B64`,
  `MATCH_PASSWORD`, `MATCH_GIT_URL`, `MATCH_GIT_BASIC_AUTHORIZATION`, `DRP_DEVELOPMENT_TEAM`.
- **`.github/workflows/zz-sqlite-probe.yml`** ("TEMPORARY - delete before merge") is still an active
  workflow on `main`. **Delete it as the first commit of this task.** Do not add three workflows
  next to a stale one.

---

## 3. Contract

### 3.1 Versioning — D-IOS-1

- `MARKETING_VERSION` in `iosApp/Configuration/Config.xcconfig` is the single source of truth.
- The build number is **derived**, never hand-maintained:
  `base = MAJOR*10000 + MINOR*100 + PATCH` (identical to Android's D-S9-3 `versionCode`), then
  `CURRENT_PROJECT_VERSION = "<base>.<github.run_number>"` in CI and `"<base>.0"` locally.
  `CFBundleVersion` permits three dot-separated integers compared component-wise, so this stays
  monotonic **and** allows a re-upload after a rejected build without touching the marketing
  version. Android's flat integer has no room for that; iOS needs it because Apple rejects a
  duplicate build number permanently.
- **iOS versioning starts at 1.0.0 / 10000.** Do not mirror the Android 1.8.x history — a new store
  listing has no version history, and pretending otherwise makes the tag guard lie.

### 3.2 The Gradle→Xcode handoff — the contract this pipeline rides on

Mechanism, export list and framework shape are fixed in
[p0-build-foundation.md](p0-build-foundation.md) §3.D–§3.E:
`embedAndSignAppleFrameworkForXcode` from a first-position Run Script phase, **not** CocoaPods, one
static umbrella `Shared.framework`.

**What this brief adds is the CI-side obligation, because the failure is silent.** If the run script
does not execute — wrong phase order, wrong `CONFIGURATION`, a `pod`-style workaround, an
"optimisation" that declares output files — Xcode **does not go red**. It links whatever framework
is already in `DerivedData`. The archive builds, signs, uploads, and ships stale Kotlin.

Contract:

1. `ios-release.yml` runs on a **fresh** runner or, on self-hosted, deletes `DerivedData` for this
   project before archiving. A CI archive must never be able to reuse a previous run's framework.
2. The workflow **prints** the Gradle task's outcome (`grep` the archive log for
   `embedAndSignAppleFrameworkForXcode`) and fails if the task did not execute. An absent task in a
   green build is the exact defect.
3. The workflow records, in the run summary, the SHA of the commit **and** the SHA-256 of the
   archived app binary. Two archives of the same commit must produce the same Kotlin behaviour; a
   surprise here is the first symptom of staleness.
4. Acceptance criterion §4.3 proves it empirically, including a **negative** proof.

### 3.3 Workflows

Three files. All three are mine; none of them touches the four existing Android workflows.

| File | Trigger | Does |
|---|---|---|
| `ios-ci.yml` | PR + push to `main`, `paths:` → `iosApp/**`, `shared/**`, **`shared/assets/**`**, `gradle/**`, `**/build.gradle.kts`, `settings.gradle.kts`, `fastlane/**`, `.github/workflows/ios-ci.yml` | build + the iOS test matrix incl. the gates that run on iOS. **No signing secrets.** |
| `ios-release.yml` | `push: tags: ['ios-v*']` | tag guard → tests → `match` → archive → TestFlight (internal + external) → upload `.ipa` **and dSYMs** as artifacts |
| `ios-promote-appstore.yml` | `workflow_dispatch` (inputs: `version`, `build_number`, `phased`) | submit that build to App Review; phased release default **on** |

**`app/src/main/assets/**` must NOT appear in any trigger path.** That directory is deleted by
ADR-0011. The correct path is `shared/assets/**`, and it **must** be present: the plan and bible
assets are shared, so a plan-data change has to re-run the iOS gates. Omitting it is the drift bug
this project has repeatedly designed against.

`paths:` filters on `ios-ci.yml` are **cost control, not tidiness** — see §3.4. An Android-only PR
must not start a macOS runner. Conversely, a `shared/**` PR must start both.

**`ios-v*` cannot collide with the Android `v*` trigger** — the glob is anchored at the start of the
ref name, so `ios-v1.0.0` does not match `v*`. Verified against `release.yml`.

### 3.4 Runner selection — blocked on an owner decision

`ios-ci.yml`'s `runs-on:` is **not mine to choose.** GitHub Free on a private repo is 2,000
minutes/month, and macOS bills at a 10× multiplier = **~200 macOS minutes, less than one release
run**. The options and their caveats are in [../RELEASING-IOS.md](../RELEASING-IOS.md) Part 3, and
the owner decides (approach doc §7 item 9).

Until they decide: write the workflows with `runs-on: ${{ vars.IOS_RUNNER || 'macos-latest' }}` so
the switch is a repository-variable change and not a code change, and **report criterion §4.10
(cost) as UNVERIFIED with the reason.**

### 3.5 Ruby and fastlane — an environment contract, not an install command

VERIFIED on this machine, 2026-08-08: `ruby -v` → **2.6.10** (macOS system Ruby, `/usr/bin/ruby`);
`fastlane` → **not installed**; `bundle` → present at `/usr/bin/bundle` (RubyGems 3.0.3.1).

macOS system Ruby is deprecated by Apple, is not writable without `sudo`, and is below the floor
modern fastlane requires. `sudo gem install fastlane` "works" and then produces an environment no CI
runner reproduces — which is the same class of unpinned-moving-part defect that cost this project
six weeks of red CI (CLAUDE.md, 2026-07-25).

Contract:

1. **A managed Ruby ≥ 3.1**, via `rbenv` or `mise`, pinned by a committed **`.ruby-version`** at the
   repo root. Neither is installed today; the owner installs one (see
   [../RELEASING-IOS.md](../RELEASING-IOS.md) Step 0b).
2. A committed **`Gemfile`** and — non-negotiable — a committed **`Gemfile.lock`**. The lock file is
   the pin. Without it "fastlane" means a different program every month.
3. **Every** fastlane invocation, locally and in CI, is `bundle exec fastlane …`. A bare `fastlane`
   in a workflow or in `RELEASING-IOS.md` is a defect.
4. CI uses `ruby/setup-ruby` with `bundler-cache: true`, reading `.ruby-version`. Pin the action to
   a commit SHA in `ios-release.yml` (it runs in the job that holds the signing secrets).
5. `Gemfile` contents are minimal: `fastlane`, and `cocoapods` **absent** (we do not use CocoaPods —
   p0 brief §3.E).
6. Print `bundle exec fastlane --version` and `ruby -v` in every run, so a green build always
   records which fastlane produced it — the same rule §3.13 applies to Xcode.

### 3.6 `~/.konan` on CI

Full strategy in [p0-build-foundation.md](p0-build-foundation.md) §3.F. The delivery-specific
obligations:

- **Hosted runner:** `actions/cache` on `~/.konan`, keyed
  `konan-${{ runner.os }}-${{ hashFiles('gradle/libs.versions.toml') }}`. Restore before the first
  Gradle invocation.
- **Self-hosted runner:** no cache action (the directory persists); add a scheduled prune, because
  `~/.konan` grows silently across Kotlin upgrades and this Mac is at 94 % disk.
- **Report the numbers** in criterion §4.10: cold vs warm wall clock, cache hit rate, cache size. If
  the save/restore costs more than the download, delete the cache step and say so. A caching ritual
  that is slower than no caching is worse than no caching, because it looks like diligence.
- Kotlin/Native **release** links use no compiler cache. `linkReleaseFrameworkIosArm64` is
  intrinsically slow (6–15 min locally, materially longer hosted). Budget it; do not hunt it.

### 3.7 `ios-release-smoke` — mandatory from Phase 4 onward

The Verification engineer's structural finding, restated because it must survive into the pipeline:
**you cannot run Kotlin/Native unit tests on a physical iPhone.** Every automated iOS result is
simulator + debug + host arch. The shipped artifact is device + release + arm64. **There is no
configuration in which the suite runs against what ships.** Android had one axis of divergence
(debug vs R8) and still shipped the 1.7.0 P0 — a crash on every reading tap, invisible in debug,
caused by Kotlin field-initialisation order that only R8's scheduling exposed. iOS has three axes.

A tag must not be pushed until this has been run, and `ios-release.yml` states in its run summary
whether it was:

1. **A release-configuration build on physical hardware**, running a scripted XCUITest of exactly
   the paths that broke before — the 1.7.0 reading-tap crash, the 1.8.1 picker jump, the sprint-00F
   reader-load failure — in one ~90-second run.
2. **A DCE canary.** Kotlin/Native release links do aggressive dead-code elimination. Anything
   reachable only across the Obj-C bridge can be present in debug and **stripped from release**, and
   it surfaces as a missing symbol or a nil — **not a stack trace.** The canary asserts that every
   `shared/platform` actual and `MainViewController` are reachable in the release binary
   (`nm -gU` over the archived binary is the cheap version; a runtime probe is the honest one).
3. **The ViewModel-init rule**, generalized from `ReaderViewModelHandoffInitTest`, run under
   `StandardTestDispatcher` — not `Unconfined`, which is "the most forgiving possible scheduling"
   and is precisely what let the 1.7.1 ordering bug hide.

Parts 1 and 3 are Verification's to author. **Part 2 and the "was it run?" gate are mine.**

### 3.8 dSYMs and symbolication

A Kotlin/Native crash report is unreadable without a UUID-matched dSYM. With a **static** framework
the Kotlin symbols are linked into the app binary, so the app's own dSYM should carry them — but
only if `DEBUG_INFORMATION_FORMAT = dwarf-with-dsym` for Release and the linked product is not
stripped of the DWARF that Kotlin/Native emits. **The mechanism is INFERRED; the criterion is
empirical** (§4.8) precisely because I will not claim symbolication works without seeing a
symbolicated Kotlin frame.

Contract:

1. `Config.xcconfig` sets `DEBUG_INFORMATION_FORMAT = dwarf-with-dsym` for the Release
   configuration.
2. `ios-release.yml` uploads **every** `.dSYM` produced by the archive as a workflow artifact,
   retained at least as long as the `.ipa`. This mirrors `release.yml`'s AAB artifact upload, and it
   exists for the same reason: when a crash arrives three weeks later, the artifact is the only copy
   you have.
3. The workflow records `dwarfdump --uuid` for the app binary **and** each dSYM in the run summary,
   and **fails if they do not match**. A mismatched dSYM is worse than a missing one — it
   symbolicates to plausible wrong frames.
4. This is the direct analogue of Android's `mapping.txt` discipline, which is what made the 1.7.0
   P0 diagnosable: the retrace only worked because the `pg_map_id` in the stack matched the mapping
   file. Carry the lesson, do not relearn it.

### 3.9 `iosApp/iosApp.xcodeproj` — ownership, resolved

**Decision: adopt XcodeGen.** `xcodegen` 2.46.0 is already installed on this machine (VERIFIED).

The problem being solved: `project.pbxproj` mixes **build settings** (mine), **file references**
(iOS Platform's) and **Xcode's own churn** (nobody's) in one machine-written file with a merge
behaviour best described as hostile. Assigning the whole file to one role makes the other role
escalate to add a Swift file. Assigning it to neither is what the original brief did.

The resolution:

| Artifact | Owner | In git? |
|---|---|---|
| `iosApp/project.yml` (XcodeGen spec) | **Build & Release** | yes |
| `iosApp/Configuration/*.xcconfig` | **Build & Release** | yes |
| `iosApp/**` Swift sources, `Info.plist`, asset catalogs | **iOS Platform** | yes |
| `iosApp/iosApp.xcodeproj/**` | **generated** | **no — gitignored** |

Why this actually resolves it rather than relabelling it:

- XcodeGen derives file references **from the filesystem**. Adding a Swift file requires no project
  edit by anyone, so the collision does not merely move — it ceases to exist.
- **Hard invariant under this decision:** every build setting lives in an `.xcconfig`, never inline
  in `project.yml` targets and never in Xcode's UI. `MARKETING_VERSION`,
  `CURRENT_PROJECT_VERSION`, `DEVELOPMENT_TEAM`, `PRODUCT_BUNDLE_IDENTIFIER`,
  `DEBUG_INFORMATION_FORMAT`, `ENABLE_USER_SCRIPT_SANDBOXING` — all in `Configuration/`. A setting
  changed in the Xcode UI is silently discarded on the next `xcodegen generate`, which is a feature:
  it makes the xcconfig authoritative by construction.
- `iosApp` **does not exist yet**, so we author it as a `project.yml` from day one. There is no
  migration cost — this is the cheapest moment this decision will ever be available.

Costs, stated:

- `xcodegen generate` becomes a prerequisite for opening the project. Documented in
  `RELEASING-IOS.md` Step 0 and run as an explicit step in all three workflows.
- XcodeGen's own version is pinned only by Homebrew. **Print `xcodegen --version` in every run** so a
  green build records which generator produced the project, and pin the CI install to a specific
  formula version if it ever bites. This is a deliberate, recorded softness of the same shape as the
  Xcode-version decision in §3.13.
- The Run Script phase for `embedAndSignAppleFrameworkForXcode` is expressed in `project.yml`
  (`preBuildScripts`, first position). **UNVERIFIED until it runs** — verifying it is criterion
  §4.3.

**Fallback, if XcodeGen proves troublesome in practice:** commit the `.xcodeproj` and assign
`project.pbxproj` to **iOS Platform**, keeping the xcconfig invariant above. That invariant is what
does the real work; XcodeGen just makes it enforceable. Do not adopt the fallback silently —
record it.

### 3.10 The first App Store submission is manual — D-PORT-8

`RELEASING.md` records this project's most expensive pipeline lesson: **the Play Developer API
cannot create the first release on a track that has never had one.** Every CI dispatch 403'd with
"caller does not have permission" even with correct permissions, and 1.5.1 had to be promoted by
hand in the Play Console. The workflow that failed was correct; the *precondition* did not exist.

**App Store Connect has the same shape.** A first submission requires completed metadata,
screenshots at the required display sizes, the App Privacy questionnaire, an age rating, content
rights and export compliance — none of which an API key can invent, and several of which are owner
decisions (approach doc §7 items 10–12).

Contract:

1. **Budget a manual first submission and a rejection round trip** (a day or more). Do not schedule
   as if `ios-promote-appstore.yml` will work the first time.
2. `ios-promote-appstore.yml` is written and committed in this task, but its acceptance criterion
   (§4.6) is **explicitly deferred to the second release**. Reporting it green off a first
   submission would be the same false claim the Play pipeline taught us to avoid.
3. `RELEASING-IOS.md` says this in the owner's own words, so the owner is not surprised by it at the
   worst possible moment. (Amended — see that document.)
4. Note the intermediate trap too: **the first external TestFlight build of each version needs Beta
   App Review** (~24 h). Internal testers bypass it. `ios-release.yml` uploads to both; only the
   external group waits.

### 3.11 Bundle size — an iOS budget, set from measurement, not inherited

**Do not inherit Android's 12 MB gate.** It guards an AAB (7.86 MB at 1.6.0, 8.12 MB measured
post-alt-schedules), which Play splits per-ABI and per-density before delivery. An `.ipa` is a
different artifact: one architecture, no ABI splitting, carrying `bible.db` (5.6 MB raw, ~2 MB
compressed), ~490 KB of plan JSON, the Compose Multiplatform runtime, and a statically-linked
Kotlin/Native framework.

**Nobody has estimated the result. This is UNVERIFIED and must be reported as UNVERIFIED until a
real archive exists.** Guessing a number and gating on it produces one of two failures: a gate so
loose it never fires, or a red build on day one that everyone learns to ignore.

Procedure:

1. The **first successful archive** records three numbers in the run summary:
   - the `.ipa` file size,
   - the uncompressed `.app` size,
   - App Store Connect's reported **download size** and **install size** for the primary device
     class — this is the number that affects users and the only one Apple publishes.
2. Set the CI gate at **measured download size + 25 % headroom**, with the measurement date and the
   build it came from in a comment. Revisit when a new asset lands.
3. The only externally-imposed ceiling is Apple's **cellular-download warning threshold** (200 MB at
   last publication — INFERRED, verify against Apple's current documentation before quoting it to
   the owner). If we approach it, that is an escalation, not a gate tweak.
4. Android's 12 MB gate in `ci.yml` is **unchanged** and remains Android's. Do not unify them; they
   measure different things.

### 3.12 Signing

- `fastlane match` in **`readonly: true`** for all CI lanes. CI never creates certificates.
- Provide a `certificates` lane for the owner's one-time local bootstrap, and put the exact command
  in `RELEASING-IOS.md` Step 5, replacing its forward reference to this brief.
- Use `setup_ci` so the runner gets a temporary keychain. **Never install into the login keychain** —
  on a self-hosted runner that is a persistent credential on the owner's daily-driver machine.
- The App Group entitlement (`group.com.jpillion.dailyreadingplanner`, D-PORT-4) must be present in
  the provisioning profile from the **first** `match` run. If the owner registered the bundle ID
  without it, the profiles must be regenerated — flag it immediately rather than working around it.

### 3.13 Simulator destination and Xcode version

**Do not hard-code `name=iPhone 16`.** Device names churn with every Xcode image and this is the
single most common iOS-CI breakage. Resolve a concrete available iPhone UDID at runtime via
`xcrun simctl list devices available -j` and pass `id=<udid>`. Fail loudly, printing the available
device list, if none is found.

Expose the Xcode selection as one env var at the top of each workflow (`XCODE_VERSION`, defaulting
to the runner's `latest-stable`) so pinning later is a one-line change, and print the resolved
`xcodebuild -version` in every run.

> This is a **deliberate, documented deviation** from the repo's pin-everything discipline. A hard
> pin to `/Applications/Xcode_X.Y.app` breaks permanently when GitHub rotates the image; an
> unpinned-but-recorded version degrades gracefully. Revisit once the project has a stable Xcode
> baseline. Note the contrast with §3.5 (Ruby) and the pinned publishing action — there, the pin is
> mandatory, because those are the surfaces where a silent change alters what ships.

---

## 4. Acceptance criteria

Report each as **VERIFIED / INFERRED / UNVERIFIED with evidence**, never as "done."

1. `ios-ci.yml` is green on a PR touching `shared/**`, and **does not trigger** on a PR touching
   only `app/src/main/kotlin/**`. **Demonstrate both.** Additionally: it **does** trigger on a PR
   touching `shared/assets/**`.

2. **The gate ledger — report these exact numbers, in this shape, and never the phrase "all gates
   run everywhere":**

   | Tier | Gate | Assertions | Source set | Executes on |
   |---|---|---|---|---|
   | 1 | 4 plan gates (`ReadingPlan` 11, `Mcheyne` 10, `Chronological` 8, `PlanSegment` 6) | **35** | `commonTest` | JVM/Android **every PR**; iOS targets **on the release pipeline only** |
   | 2 | `BibleTextVerificationTest` | **18** | `jvmTest` (sqlite-jdbc, unchanged) | JVM only. The iOS release pipeline **depends on the task passing** and reports it explicitly |
   | 3 | `BibleDatabaseRoomOpenTest` | **5** | `androidUnitTest` (unchanged, forever) | Android only |
   | 4 | **NEW** `BibleDatabaseOpenTest` (iOS) | **5** | `iosTest` | iOS. **Release-blocking, not optional.** Gen 1:1, John 3:16, John 11:35, Ps 3 verse-0 superscription — the probes that would have caught sprint-00F |
   | 5 | **NEW** `BundleAssetIntegrityTest` (iOS) | **5** | `iosTest` | iOS. Resolves each of the five assets from `NSBundle` at its expected **nested** path and asserts SHA-256 against a generated constant |

   **The honest headline is `common: 35 · jvm: 18 · android: 5 · ios: 10 (both new)`.**

   Two things this criterion deliberately does **not** claim, and the report must not either:
   - Running the plan gates on an iOS target **does not prove bundle packaging.** They read the
     source tree by absolute path (ADR-0010), which a simulator can see. Packaging is proven by
     tier 5 and nothing else.
   - The counts are not "parity with Android." They are five different numbers on purpose.

3. **The Gradle→Xcode handoff, proven both ways** (§3.2, p0 brief §3.E):
   a. Change a string in `shared/ui` Kotlin only, **archive in Release**, extract the string from
      the archived binary, show it is the new value.
   b. **Negative proof:** disable the Run Script phase, archive again, show the build fails or
      produces the **old** string. Re-enable and re-verify.
   c. Show a CI archive log containing the `embedAndSignAppleFrameworkForXcode` task execution, and
      show that the guard in §3.2.2 fails a run where the task is absent.
   Paste literal output for all three. A summary is not acceptable here.

4. Pushing `ios-v0.0.1-test` (or an equivalent throwaway) puts a build on TestFlight internal, end
   to end, with no local step. State the **wall-clock time and the macOS minutes consumed**.

5. A tag that does **not** match `MARKETING_VERSION` fails the workflow **before** any build step
   runs. **Demonstrate the failure.**

6. `ios-promote-appstore.yml` exists, is SHA-pinned and is syntactically valid. **Its end-to-end
   behaviour is UNVERIFIED by design** until the *second* App Store submission (§3.10). Say so;
   do not report it green off the first, manual one.

7. Every action in `ios-release.yml` and `ios-promote-appstore.yml` is pinned to a **commit SHA**
   with a comment naming the version it corresponds to — including `ruby/setup-ruby`. `ios-ci.yml`
   holds no secrets and may use tags.

8. **Symbolication proven, not assumed** (§3.8): take a deliberate crash from a **release**
   configuration build on physical hardware, symbolicate it with the archived dSYM, and show a
   Kotlin frame resolving to a Kotlin function name. Show `dwarfdump --uuid` matching between the
   binary and the dSYM, and show the workflow **failing** on a deliberately mismatched pair.

9. **Repo hygiene:**
   - exactly **one** copy of the plan JSONs and `bible.db`:
     `find . -name bible.db -not -path './*/build/*'` returns one path;
   - `.gitignore` covers iOS build output, `iosApp/iosApp.xcodeproj/`, `**/*.xcuserdata*`,
     `fastlane/report.xml`, `fastlane/README.md`, `*.ipa`, `*.dSYM.zip`, `DerivedData/`;
   - `Gemfile.lock` **is** committed (it is a pin, not build output);
   - `.github/workflows/zz-sqlite-probe.yml` is **deleted**.

10. **Cost and cache, as numbers:** macOS minutes per `ios-ci` run and per `ios-release` run;
    `~/.konan` cold vs warm wall clock and cache hit rate; the archive step's share of total. If
    the owner has not chosen a runner model, report this as UNVERIFIED **with that as the reason**
    (§3.4) — not as an oversight.

11. **Bundle size** per §3.11: the three measured numbers from the first real archive, the gate set
    at measured + 25 %, and the measurement date recorded in the workflow comment. Until an archive
    exists, report **UNVERIFIED** and set no gate.

12. **A clean clone builds and tests from documented commands alone.** Actually clone to a temp
    directory — including `xcodegen generate` and `bundle install` — and follow
    `RELEASING-IOS.md` verbatim. Any step you had to improvise is a documentation defect; fix the
    document, do not note it in the report.

13. `docs/RELEASING-IOS.md` Part 4's "not built yet" section is replaced with the real procedure,
    and the Step 5 forward reference resolves to the actual `match` command.

---

## 5. Boundaries / write set

**Mine, exclusively:**

```
.github/workflows/ios-*.yml
.github/workflows/zz-sqlite-probe.yml   (to delete it)
iosApp/Configuration/**
iosApp/project.yml
fastlane/**
Gemfile, Gemfile.lock, .ruby-version
**/build.gradle.kts, settings.gradle.kts, gradle/libs.versions.toml, gradle.properties
.gitignore
docs/RELEASING-IOS.md
```

**Not mine — escalate instead of editing:**

- `iosApp/**` Swift sources, `Info.plist`, asset catalogs → `ios-platform-eng` (I may **read** them).
  The `Info.plist` **keys** I depend on (`ITSAppUsesNonExemptEncryption`, and the absence of any
  `UIBackgroundModes`) are a contract with that role, not a file I edit.
- `iosApp/iosApp.xcodeproj/**` → **generated and gitignored** (§3.9). Nobody edits it. If it is in a
  diff, something is wrong.
- `shared/**` → Core/UI roles.
- `docs/adr/**`, `docs/parity-matrix.md`, `docs/test-port-strategy.md`,
  `docs/ios-execution-plan.md` → Staff / Verification / EM.
- `.github/workflows/{ci,release,promote-production,assign-track}.yml` — **the live Android
  pipeline. It currently ships production releases.** Do not refactor it to "share" steps with iOS.
  Duplication between two release pipelines is correct here; a shared abstraction that breaks an
  Android release is not. The only permitted change is the ADR-0011 asset path update, and all three
  byte-diff jobs must be re-verified at zero afterwards.

**Dependencies:** I am the only role that may add one, and only per the contract in
[p0-build-foundation.md](p0-build-foundation.md) §3.B. Reject with alternatives otherwise.

---

## 6. Escalation triggers

Return the `ESCALATION:` block, do not improvise, if:

- **The owner's Apple prerequisites are incomplete.** You cannot verify criteria 4, 6 or 8 without
  them. Build everything else and report those as **UNVERIFIED with the reason.** Never report a
  pipeline as working because the YAML looks right.
- **The macOS cost model has not been chosen** (§3.4). It changes `runs-on:`. Do not pick for the
  owner.
- **The bundle ID was registered without the App Group capability.** Provisioning profiles must be
  regenerated; this is owner-facing Apple work, not a build workaround. → Owner, via EM. Blocking
  for signing.
- **Getting the shared assets into the iOS bundle appears to require a second copy in the repo.**
  Architecture question. → **Staff.** Blocking.
- **Any data-verification gate cannot run where §4.2 says it runs.** Release-blocking, and Staff's
  call. Never ship a pipeline with a silently-skipped gate.
- **The archive staleness proof (§4.3) cannot be made to fail** when the Run Script is disabled.
  That means something else is supplying the framework, and it means the pipeline can ship stale
  Kotlin without warning. → **Staff.** Blocking, immediately.
- **XcodeGen cannot express the Run Script phase or the framework search paths** (§3.9). Take the
  recorded fallback and **say so** — do not quietly commit a `.xcodeproj`.
- **The App Privacy / content-rights answers block submission** (`RELEASING-IOS.md` Part 2). Owner
  decision, not mine.
- **Kotlin/Native link times regress** past the p0 baseline by more than 50 %. That throttles every
  other agent. → **EM**, as a ticket. Non-blocking; do not absorb it.

---

## 7. Report format

State plainly, in this order:

1. **What a tag now does that it didn't before.**
2. **The gate ledger** in the §4.2 shape, with the actual observed counts — and never the phrase
   "all gates run everywhere."
3. **The archive staleness proof**, literal output, both directions.
4. **What was verified against real App Store Connect versus only locally.** This is the single most
   important line in the report.
5. **macOS minutes per run**, and the `~/.konan` cold/warm numbers.
6. **The bundle-size measurements**, or UNVERIFIED with the reason.
7. **Every UNVERIFIED item with its reason** — particularly anything blocked on owner Apple actions.
8. **Any Android-pipeline file touched** (expected: `ci.yml` asset paths only, with the three
   byte-diff jobs re-verified at zero — or none).
