# Releasing — iOS / App Store pipeline

> **Status:** setup runbook. The pipeline is **not built yet** — the iOS app itself does not exist.
> **Architecture decision (owner, 2026-08-08):** iOS ships from a **Kotlin Multiplatform +
> Compose Multiplatform** shared core, not a separate native Swift app. The port is run by the
> `run-ios-port` team ([SKILL.md](../.claude/skills/run-ios-port/SKILL.md)); this document covers
> only **delivery** — the Apple accounts, signing, and the tag-to-TestFlight-to-App-Store
> pipeline that the port's binary will ride.
>
> Companion: [RELEASING.md](RELEASING.md) (the proven Play pipeline this mirrors).

> **Amended 2026-08-08** (Build & Release), after the six specialist reviews and the owner's
> sign-off of [ios-port-approach.md](ios-port-approach.md). What changed, most urgent first:
>
> 1. **Step 2 now tells you to enable App Groups when you register the bundle ID.** The previous
>    instruction ("leave everything off") is wrong given D-PORT-4, and this is the next Apple action
>    on your list. Adding the capability later invalidates provisioning profiles *and* turns a
>    storage decision into a user-data migration.
> 2. **New Step 0 (free ≥ 50 GB of disk) and Step 0b (a managed Ruby ≥ 3.1).** Both are hard
>    prerequisites that were missing; this machine is at 94 % disk with system Ruby 2.6.10.
> 3. **`ITSAppUsesNonExemptEncryption = false` is now a listed required `Info.plist` key.** The app
>    has made real network requests since 1.8.0, so without it every TestFlight upload stalls on
>    export compliance.
> 4. **The CI cost model is marked as an OPEN owner decision** with a hybrid recommendation and two
>    caveats you should weigh (disk pressure; a self-hosted runner is a supply-chain surface).
> 5. **The first App Store submission is manual** — the same lesson Play taught at 1.5.1, carried
>    across before it costs a second afternoon.
> 6. XcodeGen **is** used by the KMP path (a correction), and Part 4's write-set table and the
>    Part 6 checklist are updated to match.

---

## Why this document exists now, before the app

Two things on the iOS critical path have **multi-day latency that no amount of engineering
speed can compress**:

1. **Apple Developer Program enrollment** — identity verification, often 24–48h, sometimes
   longer.
2. **App name reservation** — the App Store name is globally unique and first-come. "Daily
   Bible Reading Planner" is not reserved until an app record exists.

Both are owner-only actions. Start them today; the port team can work for weeks without them,
but you cannot ship a single TestFlight build until they are done.

---

## Part 1 — Owner critical path

Ordered by dependency. Times are Apple's, not ours.

### Step 0 — Free disk space  ⛔ do this *before* Step 0a, or the Xcode install fails partway

Measured on this machine, 2026-08-08: **94 % used, 126 GB free.** That sounds like plenty and it is
not, because the iOS toolchain arrives in layers and each one is invisible until it lands:

| Item | Size | Note |
|---|---|---|
| Xcode download | ~17 GB | the `.xip`, which is then expanded |
| Xcode installed | ~40 GB | the expansion is **in addition to** the download until you delete the `.xip` |
| One iOS simulator runtime | ~8 GB | Xcode no longer bundles them; each iOS version is a separate download |
| `~/.konan` (Kotlin/Native toolchain) | ~4 GB | per Kotlin version. Does not exist yet on this machine. |
| Gradle caches + Xcode DerivedData | ~15 GB | grows with every build; DerivedData is safe to delete, the Gradle cache is expensive to rebuild |

**Free at least 50 GB before starting**, and treat 100 GB as the comfortable number. The failure
mode if you do not is not a clean error — it is a half-expanded Xcode, or a Kotlin/Native link that
dies with an out-of-space message three layers down in a Gradle stack trace.

> **Status as measured 2026-08-08: this step is already satisfied — verify, don't panic.** 126 GB
> free clears the 50 GB threshold, so **no action is required to start.** The "94 % used" figure is
> a ratio on a 1.8 TB disk and is not the number that matters here.
>
> It is still worth reading, because the margin is thinner than 126 GB suggests. The table above
> peaks at roughly **84 GB** during the install (the `.xip` and its expansion coexist until you
> delete the `.xip`), settling near **67 GB** afterwards. That leaves ~40–60 GB — workable, below
> the 100 GB comfort line, and worth a `du` pass if a later build dies oddly. `~/.gradle` is 3 GB
> and is the easiest reclaim; DerivedData is safe to delete at any time.

```bash
df -h /System/Volumes/Data          # check
du -sh ~/Library/Developer/Xcode/DerivedData ~/.gradle 2>/dev/null   # the usual suspects, later
```

### Step 0a — Install Xcode  ⛔ blocks everything, including local builds

This machine currently has **only Command Line Tools**, not Xcode:

```bash
xcode-select -p
```

If that prints `/Library/Developer/CommandLineTools`, Xcode is missing. Install it from the Mac
App Store (search "Xcode"; ~17 GB, expect 30–60 min). Then, in a terminal:

```bash
sudo xcode-select -s /Applications/Xcode.app/Contents/Developer && sudo xcodebuild -license accept && xcodebuild -version
```

That needs your admin password, which is why it is yours to run and not mine. Verify afterwards:

```bash
xcodebuild -version && xcrun simctl list devices available | head
```

Also install the generator/formatter tooling the pipeline expects:

```bash
brew install xcbeautify swiftlint xcodegen
```

> **Correction to an earlier draft:** that draft said `xcodegen` (already installed, 2.46.0) is
> "not used by the KMP path." **It is now used, deliberately.** The Xcode project is *generated*
> from `iosApp/project.yml` and is **not** in git. The reason is ownership: `project.pbxproj` mixes
> build settings, source-file references and Xcode's own churn into one machine-written file, and
> two different roles need to change it for different reasons. Generating it means adding a Swift
> file requires no project edit at all. Consequence for you: **run `xcodegen generate` in `iosApp/`
> before opening the project in Xcode**, and remember that a build setting changed in Xcode's UI is
> discarded on the next generate — settings live in `iosApp/Configuration/*.xcconfig`.
> Full reasoning: [task-briefs/ios-delivery-pipeline.md](task-briefs/ios-delivery-pipeline.md) §3.9.

### Step 0b — Install a managed Ruby (fastlane's runtime)  ⏱ 15 min

Measured on this machine, 2026-08-08: `ruby -v` → **2.6.10**, the macOS **system** Ruby, and
fastlane is not installed.

Do **not** `sudo gem install fastlane` against system Ruby. Apple deprecated it, it needs `sudo` to
write to, it is below the version modern fastlane wants, and — the real problem — it produces a
toolchain that no CI runner reproduces. That is the same class of unpinned-moving-part defect that
left this project's CI red for six weeks (CLAUDE.md, 2026-07-25).

```bash
brew install mise                     # or rbenv, either is fine
mise use --global ruby@3.3            # writes ~/.config/mise/config.toml
ruby -v                               # expect 3.3.x, NOT 2.6.10
```

The repo will carry a `.ruby-version`, a `Gemfile` and a **committed `Gemfile.lock`** (the lock file
is the pin). After those land, the only fastlane command you ever type is:

```bash
bundle install          # once, and after any Gemfile change
bundle exec fastlane …  # always. A bare `fastlane` is a bug.
```

### Step 1 — Enrol in the Apple Developer Program  ⏱ $99/yr, timeline not published

<https://developer.apple.com/programs/enroll/> · verified against Apple's docs 2026-08-08

**Choose the entity type deliberately — it is painful to change later:**

| | Individual / Sole Proprietor | Organization |
|---|---|---|
| Cost | $99/yr | $99/yr |
| Requires | Apple Account with 2FA, legal name, non-P.O.-box address | all of that **plus a D-U-N-S number**, a work email on your org domain, a public functional website, and binding authority |
| Payment timing | **at enrolment**, before verification completes | **after** Apple verifies the org |
| Extra friction | — | may be asked for **notarised** business documents |
| Seller name shown on the App Store | **your personal legal name** | your company name |

**Recommendation: Individual**, unless you want a company name on the listing badly enough to
obtain a D-U-N-S number and clear a heavier verification. Note the consequence honestly: your
legal name becomes publicly visible as the seller on every App Store listing. Decide now, not
after launch.

**Apple does not publish an approval timeline.** What it does commit to: you should get a
confirmation email once the purchase is processed, and if none arrives **within 24 hours** you
should contact Apple Developer Support. For individuals this is often same-day; treat anything
longer as normal-but-worth-chasing, not as a rejection.

#### Two enrolment paths — they are not equivalent

| | Apple Developer **app** (iPhone/iPad/Mac) | **Web** |
|---|---|---|
| Photo ID | **Required** — you photograph a driver's licence or passport in-app | **Not required** in the normal flow |
| Device | must be the *same* device for the whole process; needs Face ID/Touch ID/passcode | any browser |
| Billing | **auto-renewable subscription** via your Apple Account | one-time purchase; you choose the payment method and renew manually |
| Availability | select regions only (the *only* option in India) | broad |

> **Correction to an earlier draft of this document:** it claimed the app path had "smoother"
> identity verification and recommended it. That was wrong. The app path *mandates* a photo-ID
> scan; the web path does not ask for ID at all unless something trips verification. The web path
> is the lower-friction route for an Individual enrolment, not the higher one.

**One rule that catches people either way:** if you enrol as an individual and pay by credit card,
it must be **your own** card. Apple is explicit that otherwise "your enrollment will be delayed
and you'll be asked for a copy of your government-issued photo identification."

### Step 2 — Register the bundle ID  ⏱ 5 min, after Step 1

<https://developer.apple.com/account/resources/identifiers/list>

→ Identifiers → **+** → App IDs → App → **Explicit**

| Field | Value |
|---|---|
| Description | Daily Readings |
| Bundle ID | `com.jpillion.dailyreadingplanner` |

#### ⚠️ Capabilities: tick **App Groups**, and only App Groups

> **This instruction changed on 2026-08-08 and it changed for a reason. An earlier draft of this
> document said "leave everything off." That is now wrong, and this is the one step on your Apple
> critical path where getting it wrong costs real rework.**

1. Under **Capabilities**, tick **App Groups**.
2. Then go to Identifiers → **App Groups** → **+** and register the group itself:

   | Field | Value |
   |---|---|
   | Description | Daily Readings shared container |
   | Identifier | `group.com.jpillion.dailyreadingplanner` |

3. Back on the App ID, click **Configure** next to App Groups and assign that group.

Leave **everything else off.** Push Notifications is *not* needed — the app's reminders are local
notifications, which require no entitlement. Background Modes is deliberately not declared: the app
needs none, and declaring an unused one is a gratuitous App Review question.

**Why App Groups, when the widget that needs it isn't shipping in v1.0?**

Adding a capability later **invalidates every provisioning profile** built against the old App ID.
Those profiles are created once by `fastlane match`, stored encrypted in a private repo, and used by
CI. Adding App Groups after the fact means regenerating them, re-running `match`, and re-testing the
signing path — during a release, which is when you will discover it.

And it is not only a signing problem. The App Group is a **file container**. The app's databases
(`progress.db`), its settings store and the copied `bible.db` are written into it from the very
first build. Deciding that later means *moving live user data on real devices* — a migration with a
silent failure mode (settings reset to defaults, reading history apparently gone). This project has
already shipped that class of bug once, in sprint-00F.

So the storage location is decided in v1.0 and the container is reserved from the first build, even
though the home-screen widget that will eventually read it is planned for iOS 1.1. Cost today: one
tick box and five minutes. Cost later: a data migration.

(Recorded as **D-PORT-4** in [ios-port-approach.md](ios-port-approach.md) §4.2.)

> Reusing the Android application ID verbatim is correct and intentional. Google Play and the
> App Store are separate registries; a matching reverse-DNS identifier across both is the norm
> and keeps one mental model.

### Step 3 — Create the App Store Connect app record  ⏱ 10 min — **do this early to reserve the name**

<https://appstoreconnect.apple.com/apps> → **+** → New App

| Field | Value | Note |
|---|---|---|
| Platform | iOS | |
| Name | `Daily Bible Reading Planner` | **30 char max** — this is 27. Must be globally unique. |
| Primary language | English (U.S.) | |
| Bundle ID | `com.jpillion.dailyreadingplanner` | from Step 2 |
| SKU | `dailyreadingplanner-ios` | internal only, never shown, never changes |
| User access | Full Access | |

**If the name is taken**, App Store Connect tells you immediately. Fallbacks, in order of
preference: `Daily Bible Reading Plan`, `Bible Companion Readings`, `Daily Readings — Bible`.
Decide fast; the record reserves the name for you.

This matches the deliberate Android split recorded in CLAUDE.md: **store title** is the
discoverable long name, **on-device label** stays "Daily Readings".

### Step 4 — Create an App Store Connect API key  ⏱ 5 min — the CI credential

App Store Connect → **Users and Access** → **Integrations** → **App Store Connect API** →
**Team Keys** → **+**

| Field | Value |
|---|---|
| Name | `github-actions-ci` |
| Access | **App Manager** |

App Manager is sufficient to upload builds, manage TestFlight, and submit for review. Do not
grant Admin — the same minimal-scope discipline as the Play service account (see
[RELEASING.md](RELEASING.md)).

On creation, record three things:

1. **Issuer ID** — shown once at the top of the Keys page; it is per-team, copy it now.
2. **Key ID** — the 10-character identifier in the row.
3. **The `.p8` private key file** — **downloadable exactly once.** If you lose it, the only
   remedy is to revoke the key and make a new one. Save it to your password manager, not just
   to `~/Downloads`.

### Step 5 — Create the code-signing certificate repo  ⏱ 10 min

CI needs a distribution certificate and provisioning profile. The reliable mechanism is
**fastlane match**, which stores them encrypted in a private git repo and hands them to any
machine that has the passphrase.

```bash
gh repo create jpillion/daily-readings-certs --private --description "fastlane match — encrypted iOS signing assets. Do not make public."
```

Then generate a strong passphrase and keep it in your password manager — this is
`MATCH_PASSWORD`, and losing it means re-creating all signing assets:

```bash
openssl rand -base64 32
```

The one-time `fastlane match appstore` run that populates this repo **requires Xcode and a
completed Step 1**, and it is best run by you locally so the certificate's private key
originates on your machine. Exact command is in the task brief
([docs/task-briefs/ios-delivery-pipeline.md](task-briefs/ios-delivery-pipeline.md)).

> **Why not automatic cloud signing?** `xcodebuild -allowProvisioningUpdates` with an API key
> also works, but on ephemeral CI runners it tends to mint a *new* distribution certificate per
> run, and Apple caps you at a small number per team. You hit the cap, then releases fail. match
> exists precisely to avoid that.

### Step 6 — Add the GitHub secrets  ⏱ 5 min

Repo → Settings → Secrets and variables → Actions. These mirror the Play secret set.

| Secret | From | Notes |
|---|---|---|
| `APP_STORE_CONNECT_ISSUER_ID` | Step 4 | UUID |
| `APP_STORE_CONNECT_KEY_ID` | Step 4 | 10 chars |
| `APP_STORE_CONNECT_KEY_P8_B64` | Step 4 | see below |
| `MATCH_PASSWORD` | Step 5 | the generated passphrase |
| `MATCH_GIT_URL` | Step 5 | `https://github.com/jpillion/daily-readings-certs.git` |
| `MATCH_GIT_BASIC_AUTHORIZATION` | see below | lets CI clone the private certs repo |
| `DRP_DEVELOPMENT_TEAM` | Apple Developer → Membership | the 10-char Team ID |

Base64 the `.p8` (single line, no wrapping):

```bash
base64 -i ~/Downloads/AuthKey_XXXXXXXXXX.p8 | tr -d '\n' | pbcopy
```

For the certs-repo clone credential, create a fine-grained PAT scoped to **only**
`daily-readings-certs` with Contents: Read, then:

```bash
printf 'jpillion:github_pat_YOURTOKEN' | base64 | tr -d '\n' | pbcopy
```

> The Play pipeline pins the one action that sees a publishing secret to a commit SHA
> ([release.yml](../.github/workflows/release.yml)). The iOS pipeline must apply the same rule
> to every action in the release workflow — it is in the delivery contract below.

---

## Part 2 — Store listing prerequisites

Apple blocks submission on these regardless of build quality. None require the app to exist, so
they can be prepared in parallel with the port.

| Item | Status | Note |
|---|---|---|
| **Privacy policy URL** | ⚠️ **needed** | **Mandatory** for every App Store app, no exceptions. Nothing in this repo records one; if Play has one, reuse it. If not, this is a real task — the app needs a hosted policy page. |
| **App Privacy questionnaire** | ⚠️ needs a decision | Not a formality. The V1 planner collects nothing, but **online translations** (sprint 00R) send reference requests to `drp-bible-proxy…run.app`, and API.Bible's licence obliges **FUMS usage reporting**. Whether that is "Data Not Collected" needs a deliberate answer, not a guess. Decide before submission. |
| **Age rating** | easy | Questionnaire; expect 4+. No objectionable content. |
| **Category** | easy | Primary **Reference** or **Books**. Match whatever Play uses. |
| **Screenshots** | blocked on the app | At least one iPhone set at the largest supported display size. **Recommendation: ship iPhone-only for v1** — supporting iPad adds a required iPad screenshot set *and* real iPad layout QA. |
| **Export compliance** | handled in build — **see the required-keys section below** | `ITSAppUsesNonExemptEncryption = false` in `Info.plist`. **This is now load-bearing, not tidiness:** since 1.8.0 the app makes real HTTPS requests (online NKJV/NASB via the Cloud Run proxy), so Apple's export-compliance question fires on **every** upload. Without the key, every TestFlight build stalls awaiting a manual answer. |
| **Content rights** | ⚠️ answer carefully | Apple asks whether the app contains third-party content. KJV is public domain, but **NKJV and NASB are licensed** via API.Bible. Answer yes and be ready to describe the licence. |
| App icon 1024×1024 | ✅ **two candidates generated — pick one** | See below. Both are 1024×1024, opaque, square, no pre-rounded corners (Apple rejects alpha and applies its own mask). |
| Support URL | easy | Required field. |

Not applicable, for the record: Sign in with Apple (no accounts), account deletion (no
accounts), IDFA/tracking (no analytics — the no-telemetry stance in PRD §12 Q6 holds).

### Required `Info.plist` keys — the engineering side of the list above

Not owner actions; recorded here because each one is a *store* requirement enforced at upload or
review time, not a build requirement, so they are invisible until they bite.

| Key | Value | Why |
|---|---|---|
| `ITSAppUsesNonExemptEncryption` | `false` (Boolean) | **The one that stalls uploads.** The app uses only HTTPS/TLS, which is exempt under the standard-cryptography exemption — but Apple asks anyway, per upload, until this key answers it. The app has had outbound network access since 1.8.0, so this is not hypothetical. |
| `CFBundleShortVersionString` | from `MARKETING_VERSION` | the tag guard's source of truth (D-IOS-1) |
| `CFBundleVersion` | from `CURRENT_PROJECT_VERSION` | derived: `MAJOR*10000 + MINOR*100 + PATCH`, then `.<run_number>` in CI |
| `NSUserNotificationsUsageDescription` — **not** required | — | local notifications need no usage-description string; authorization is requested at runtime. Do not add one "to be safe." |
| `UIBackgroundModes` | **absent** | The app needs none. Declaring an unused background mode is a gratuitous App Review question, and this app has no audio, no TTS and no background refresh. |
| `NSAppTransportSecurity` | **absent** | Both network endpoints are HTTPS. **No ATS exception is needed and none should be added** — an ATS exception is a question you have to answer at review. |

`Info.plist` is owned by the iOS Platform engineer; the keys above are a contract with Build &
Release, who depends on them in the release workflow.

### The app icon — an owner decision

Android uses **two different** icon artworks, which is fine there and a problem here: on iOS a
single 1024×1024 asset is both the App Store icon *and* the home-screen icon, so you must choose
one.

| Candidate | Source | Looks like |
|---|---|---|
| [`appicon-1024.png`](app-store-listing/appicon-1024.png) | regenerated from the **launcher** vector, `tools/make_ios_appicon.sh` | flat white book on solid `#1F4E3D`; crisp at any size because it is rendered from vector |
| [`appicon-1024-from-play-listing.png`](app-store-listing/appicon-1024-from-play-listing.png) | 2× upscale of [`play-icon-512.png`](play-listing/play-icon-512.png) | gradient background, three dots under the book; **softer** — it is an upscaled raster, not a re-render |

**Recommendation:** the Play-listing artwork is the better-designed icon, so **re-export it at
1024 from whatever design file produced it** (you have Illustrator; a vector source almost
certainly exists) and use that. The upscale above is a working stand-in, not a shipping asset.

Two caveats worth deciding on deliberately:

- The three dots signified the Bible Companion's **three streams**. Since alternate schedules
  shipped (1.5.0), plans have 1, 3, or 4 streams — so the dots are now mildly inaccurate.
- Fine detail like small dots is the first thing to disappear at the home-screen size. If you keep
  them, check the icon at 60×60 before shipping.

Regenerate the vector variant at any time with:

```bash
tools/make_ios_appicon.sh
```

---

## Part 3 — How the pipeline will work

Deliberately shaped to match the Play pipeline you already trust, so there is one release
mental model rather than two.

### Track mapping

| Play (today) | App Store (planned) | Review? |
|---|---|---|
| Internal testing | TestFlight **internal** testers (≤100, your own team) | none |
| Closed testing / Alpha | TestFlight **external** group (≤10,000) | **Beta App Review**, ~24h, first build of a version only |
| Production | App Store release | **full App Review**, ~24–48h |
| Staged rollout % | **Phased release** (7-day automatic ramp) | — |

### Trigger scheme

```
push tag ios-v1.0.0  →  test  →  archive  →  TestFlight (internal + external)
                                                    ↓
                              workflow_dispatch  →  submit to App Review  →  phased release
```

`ios-v*` **cannot collide** with the Android `v*` trigger — that glob is anchored at the start of
the ref name, so `ios-v1.0.0` does not match `v*`. Verified against
[release.yml](../.github/workflows/release.yml).

### ⚠️ The **first** App Store submission is manual. Plan for it.

You have met this exact problem before, on Play, and it cost real time — so it is written down here
before it happens again rather than after.

[RELEASING.md](RELEASING.md) records that the **Play Developer API cannot create the first release
on a track that has never had one.** Every CI promote attempt returned "caller does not have
permission" even with the permission correctly granted, and 1.5.1 had to be promoted by hand through
the Play Console. The workflow was right; the *precondition* did not exist yet. Only from 1.6.0
onward did the fully automated `tag → alpha → promote` path work — and it then took **39 seconds**.

**App Store Connect has the same shape.** A first submission requires completed metadata,
screenshots at the required display sizes, the App Privacy questionnaire, an age rating, content
rights and export compliance — none of which an API key can invent, and several of which are
decisions only you can make (Part 2). So:

- **The first submission is done by you, in App Store Connect, by hand.** Budget an afternoon, plus a
  rejection round trip of a day or more. Rejections for an app like this are usually *metadata*
  (privacy policy, content rights), not code.
- `ios-promote-appstore.yml` is written and committed anyway, and it is expected to work **from the
  second release onward** — exactly like `promote-production.yml` did on Play.
- Nobody will report that workflow as "verified" off the first release. That would be the same false
  claim the Play pipeline taught us to avoid.

One intermediate trap on the way there: **the first external TestFlight build of each version needs
Beta App Review (~24 h).** Internal testers bypass it entirely. Plan the beta cadence around that,
not around Play's instant internal track.

### Versioning — D-IOS-1

- `MARKETING_VERSION` in `iosApp/Configuration/Config.xcconfig` is the single source of truth,
  and the release workflow **hard-fails if the tag doesn't match it** — exactly the guard
  [release.yml](../.github/workflows/release.yml) applies to `versionName`.
- The build number (`CURRENT_PROJECT_VERSION`) is **derived** by the same rule as Android's
  `versionCode` (D-S9-3): `MAJOR*10000 + MINOR*100 + PATCH`. So 1.0.0 → `10000`.
- CI appends `.<github.run_number>` → `10000.42`. `CFBundleVersion` allows up to three
  dot-separated integers and compares component-wise, so this stays monotonic *and* lets you
  re-upload after a rejected build without touching the marketing version. Android's flat
  integer has no room for that; iOS needs it because Apple rejects duplicate build numbers.
- **iOS starts at 1.0.0, not 1.8.x.** The Android version history does not transfer to a new
  store listing, and pretending otherwise would make the tag guard lie.

### ⚠️ CI cost model — **OPEN OWNER DECISION.** Engineering must not choose this for you.

**Status: unresolved as of 2026-08-08.** It is item 9 on your critical path
([ios-port-approach.md](ios-port-approach.md) §7) and it decides one line —
`runs-on:` — in the iOS workflows. The workflows will be written as
`runs-on: ${{ vars.IOS_RUNNER || 'macos-latest' }}` so your answer is a repository-variable change
rather than a code change, but the **cost consequence is yours and the numbers are stark.**

This repo is **private**, and GitHub bills macOS runners at a large multiplier (historically 10×
Linux). GitHub Free gives 2,000 included minutes/month, which at 10× is **~200 macOS minutes — less
than one release run.** A Compose Multiplatform release build is Kotlin/Native linking (release
links use no compiler cache, so 12–25 min hosted) plus `xcodebuild archive`: realistically 25–45 min
cold. PR CI on hosted runners would exhaust the allowance in days.

Three options, recommendation first:

1. **Hybrid — self-hosted runner on your Mac for PR CI, GitHub-hosted for releases.** ⭐
   Free for the common case, and much faster: a warm Gradle + `~/.konan` cache is worth more here
   than anywhere else in the build (2–4 min warm vs ~25 min cold hosted). Releases stay on a clean
   hosted runner so they remain reproducible and do not depend on your machine being awake — that
   matters, because a release artifact built on a developer's laptop is a release artifact nobody
   can reproduce. Hosted releases cost roughly **$15–25 each**.
2. **Pay for hosted minutes throughout.** Simplest, fully hosted, monthly cost, nothing on your Mac.
3. **Hosted-only, release builds only — no PR test gate.** Cheapest. It also throws away the
   discipline that makes the Android side trustworthy, on the platform where the automated suite
   already covers *less*. Not recommended.

**Two caveats on option 1 that you should weigh before choosing it — neither is a dealbreaker, both
are real:**

- **Disk.** A self-hosted runner keeps Gradle caches, `~/.konan`, DerivedData and every checkout on
  a machine already at **94 % full**. Do Step 0 first, and expect to prune. The failure mode is a
  release that fails at 02:00 because a build directory filled the disk.
- **Supply chain.** A self-hosted runner executes whatever a workflow tells it to, on your personal
  machine, with your keychain and your SSH keys present. On a **private** repo that risk is bounded
  — only people you have granted access can open a PR — but it is not zero, and the standard
  mitigations are not optional:
  - restrict the runner to **same-repository** PRs only; **never** run it on forks;
  - never use `pull_request_target` on a workflow that touches the self-hosted runner;
  - keep **all** signing and publishing secrets out of `ios-ci.yml` (they belong only in
    `ios-release.yml`, which runs hosted);
  - if you ever make this repo public, **remove the self-hosted runner the same day.**

Verify current multipliers and included minutes against GitHub's billing docs before committing —
the numbers above are the shape of the problem, not a quote.

---

## Part 4 — What is deliberately NOT built yet, and why

Under the port team's roster
(`/Users/jpillion/code/Agentic Teams/teams/ios-port/README.md`), these paths are
**`kmp-build-release-eng`'s exclusive write set**:

| Path | Owner |
|---|---|
| `.github/workflows/**` | Build & Release **only** |
| `iosApp/Configuration/**` (the `.xcconfig` files) | Build & Release **only** |
| `iosApp/project.yml` (the XcodeGen spec) | Build & Release **only** |
| `**/build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties` | Build & Release **only** |
| `Gemfile`, `Gemfile.lock`, `.ruby-version` | Build & Release **only** |
| `iosApp/**` Swift sources, `Info.plist`, asset catalogs | iOS Platform |
| `iosApp/iosApp.xcodeproj/**` | **generated, gitignored — nobody edits it** |

So the workflow files are not written here — not out of ceremony, but because they would have to
hard-code an `iosApp/` and `shared/` layout that **does not exist yet** and whose shape is an
output of the port's Phase 0 inventory. A workflow referencing a nonexistent scheme is worse
than no workflow: it is red on day one and teaches you to ignore it.

The work is specified instead as two executable contracts with acceptance criteria:

| Brief | Covers | Dispatched at |
|---|---|---|
| **[p0-build-foundation.md](task-briefs/p0-build-foundation.md)** | the **dependency contract** (what resolves for which target, and the named replacement where it does not), the target set, source-set hierarchy, framework shape, the Gradle→Xcode handoff, `~/.konan` caching | **Gate 0 → Phase 1.** Early, and deliberately so: four dependency decisions ship to *Android* users across releases 1.9.0 / 1.10.0 / 1.11.0 before any iOS target exists. Settling them afterwards would mean three Play releases cut against unverified assumptions. |
| **[ios-delivery-pipeline.md](task-briefs/ios-delivery-pipeline.md)** | the three iOS workflows, signing, versioning, TestFlight, App Review, dSYMs, bundle size | **Phase 4**, once a `shared/*` compiles for `iosArm64` and your Apple prerequisites (Part 1) are done. |

> **A note on phase names, because three documents disagree.** The team roster uses phases 0–5, the
> older briefs use 0 and A–E, and nothing maps them.
> **[ios-port-approach.md](ios-port-approach.md) §5 is authoritative: phases 0–5.** Anything still
> saying "Phase B" predates that decision.

---

## Part 5 — Differences from Play that will surprise you

Recorded up front, because each one has bitten someone.

1. **Every build number is permanent.** Upload 10000.42 and that number is burned for that
   version, forever, even if you delete the build. The `.run_number` suffix exists for this.
2. **First external TestFlight build of each version needs Beta App Review** (~24h). Internal
   testers bypass it. Plan the beta cadence around that, not around Play's instant internal
   track.
3. **App Review is a human.** Rejections cite guideline numbers and cost a round trip of a day
   or more. The most common cause for an app like this is metadata (privacy policy, content
   rights), not code.
4. **Screenshots are per-device-size and per-locale**, not one graphic. More work than Play's
   feature graphic.
5. **A debug-build device pass does not cover the release build.** This bit you already, in
   1.7.0, where R8 exposed a Kotlin initialization-order crash that debug builds hid (see
   CLAUDE.md). The iOS equivalent is real: Kotlin/Native release builds optimize and strip
   differently from debug. **Smoke-test from a release archive, not a debug build**, before
   every tag. Carry the lesson across, don't relearn it.
6. **Certificates and profiles expire** (distribution certs annually). A pipeline that worked
   for a year can fail on a Tuesday for no code reason. match makes the fix a one-command
   renewal instead of an afternoon.
7. **The first release on a track cannot be created by the API.** Play taught this the expensive
   way (1.5.1, manual Console promotion). App Store Connect is the same. See Part 3.
8. **Crash reports need a UUID-matched dSYM**, and a Kotlin/Native crash without one is
   unreadable — the iOS analogue of Android's `mapping.txt`, which is the only reason the 1.7.0 P0
   was diagnosable. The release workflow archives every dSYM as a build artifact and fails if the
   UUIDs do not match the shipped binary. Do not delete those artifacts to save space.

---

## Part 6 — Owner checklist

Copy into an issue and tick off. **Steps 0–6 are strictly sequential.**

**Local machine — none of this needs Apple**
- [ ] **0.** Free ≥ 50 GB of disk (currently 94 % used / 126 GB free)
- [ ] **0a.** Install Xcode; `xcodebuild -version` succeeds; `brew install xcbeautify swiftlint xcodegen`
- [ ] **0b.** Install a managed Ruby ≥ 3.1 (`mise` or `rbenv`); `ruby -v` is **not** 2.6.10

**Apple — start today, these have multi-day latency**
- [ ] **1.** Apple Developer Program enrolment submitted via the **web** path (entity type decided: Individual / Org)
- [ ] **1a.** Enrolment approved
- [ ] **2.** Bundle ID `com.jpillion.dailyreadingplanner` registered — ⚠️ **with App Groups ticked**
- [ ] **2a.** App Group `group.com.jpillion.dailyreadingplanner` registered and assigned to the App ID
- [ ] **3.** App Store Connect record created — **name reserved**
- [ ] **4.** API key created (**App Manager**, not Admin); Issuer ID, Key ID, and `.p8` saved to the password manager
- [ ] **5.** `daily-readings-certs` private repo created; `MATCH_PASSWORD` generated and saved
- [ ] **6.** All 7 GitHub secrets added

**Decisions and assets — no latency, but they block submission**
- [ ] **7.** Privacy policy URL exists and is hosted
- [ ] **8.** App Privacy questionnaire answer decided for the translations proxy + FUMS
- [ ] **9.** 1024×1024 icon produced (no alpha, square)
- [ ] **10.** CI cost model chosen (hybrid / paid hosted / release-only) — see Part 3
- [ ] **11.** iOS v1.0 scope signed off ([ios-port-approach.md](ios-port-approach.md) §3) — **no widget, no persistent notification, no in-app updates, no MySword**
- [ ] **12.** Content-rights answer prepared (KJV public domain; **NKJV and NASB are licensed** via API.Bible)

> **Not on this list, deliberately: "automate the first App Store submission."** It cannot be done.
> See Part 3.
