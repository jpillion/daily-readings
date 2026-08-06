# Releasing — tag-to-Play pipeline

Pushing a version tag publishes that commit to **BOTH the internal testing track and the
closed-testing (Alpha) track** automatically via `.github/workflows/release.yml`.

Full flow: `tag → internal + alpha (auto)`, then `alpha → production (manual promote)`.

## Release procedure

1. Bump `versionName` and `versionCode` in `app/build.gradle.kts` (D-S9-3:
   `versionCode = MAJOR*10000 + MINOR*100 + PATCH`), commit, push.
2. Update `distribution/whatsnew/whatsnew-en-US` (the Play "What's new" text).
3. Tag and push:
   ```bash
   git tag v1.2.0 && git push origin v1.2.0
   ```
The workflow verifies the tag matches `versionName`, runs the unit-test gate (incl. the
plan-data verification gate), builds the signed bundle, and uploads it to the **internal AND
alpha** tracks with the what's-new text.

**Why both (owner, 2026-08-06):** internal testers must never be stranded on an older build.
Before this, release.yml uploaded to alpha only, so internal sat on 1.7.1 while production
moved to 1.8.0 — and the Play Store served enrolled internal testers the *stale* build. It is
ONE upload to two tracks, not two uploads: a versionCode can only ever be uploaded once, so a
second upload step would fail. Promotion to production touches only its source and target, so
the internal assignment survives it.

## Promoting a release to production

Production is a **deliberate, manual second stage**: `tag → Alpha (auto)`, then
`Alpha → Production (manual promote)`. The promotion moves the *same reviewed AAB* (by
`versionCode`) onto the production track — no rebuild, no re-sign, no duplicate versionCode,
no second review.

Run **Actions → "Promote to Production" → Run workflow** (`.github/workflows/promote-production.yml`),
with:
- **version_code** — the build already live on the source track (e.g. `10501` for 1.5.1).
- **source_track** — usually `alpha`.
- **rollout** — `1` for a full 100% rollout; a fraction (e.g. `0.2`) for a staged rollout
  (the workflow sets the release status to `inProgress` automatically when rollout < 1).

Equivalent local command (Fastlane `supply`):
```bash
fastlane supply --package_name com.jpillion.dailyreadingplanner \
  --json_key key.json --track alpha --track_promote_to production \
  --version_code 10501 --release_status completed --rollout 1 \
  --skip_upload_apk true --skip_upload_aab true --skip_upload_changelogs true
```

### Two one-time prerequisites — BOTH now satisfied (2026-07-23), so CI promote is ready
The `promote-production.yml` workflow (and any Play API promotion) needed BOTH of these once.
They are done, so from the next release the CI promote is one click:

1. ✅ **Production permission on the service account** — granted 2026-07-23. The
   `play-publisher@daily-reading-planner-app.iam.gserviceaccount.com` account (used by
   `PLAY_SERVICE_ACCOUNT_JSON`) now has **"Release to production, exclude devices, and use Play
   App Signing"** for `com.jpillion.dailyreadingplanner`, alongside its existing "Release apps to
   testing tracks" (Play Console → Users and permissions → that user → app permissions). It was
   originally scoped to testing tracks only, which is why early CI promote attempts 403'd.
2. ✅ **Production track initialized** — the first production release (1.5.1, below) seeded the
   track. The Play Developer API cannot create the *first* release on a track that's never had
   one (it returns `Invalid request - The caller does not have permission` even with the right
   permission), so the first one had to be a manual Console promotion; subsequent ones can be CI.

### First production release — done via Console UI (2026-07-23)
1.5.1 / 10501 was promoted to **production at 100% (full rollout)** through the Play Console UI
(Test and release → Closed testing → Alpha → the 1.5.1 release → **Promote release → Production**
→ Next → Save → Publishing overview → **Submit change for review**), because of prerequisite #2
above. It went into Google review (typically ≤7 days) and rolls out to all users on approval.
**From the next release onward, use `promote-production.yml`** (Actions → "Promote to Production").

## One-time setup (owner)

### 1. Play service account
1. In [Google Cloud Console](https://console.cloud.google.com/) create a project (or reuse
   one) → IAM & Admin → Service Accounts → **Create service account**
   (e.g. `play-publisher`). No GCP roles needed.
2. Create a **JSON key** for it (Keys → Add key → JSON) and download it.
3. In [Play Console](https://play.google.com/console) → **Users and permissions** →
   Invite new users → enter the service account's email → grant the app
   `com.jpillion.dailyreadingplanner` with **Release to testing tracks** permissions
   (Releases: "Release apps to testing tracks", plus view app information).

### 2. GitHub repository secrets
Settings → Secrets and variables → Actions → New repository secret:

| Secret | Value |
|---|---|
| `PLAY_SERVICE_ACCOUNT_JSON` | full contents of the service-account JSON key |
| `DRP_UPLOAD_KEYSTORE_B64` | `base64 -i upload-keystore.jks \| pbcopy` |
| `DRP_UPLOAD_STORE_PASSWORD` | the keystore password |
| `DRP_UPLOAD_KEY_ALIAS` | `upload` |
| `DRP_UPLOAD_KEY_PASSWORD` | the key password |

Notes:
- The API can only publish to an app/track that already has at least one manually
  uploaded release (done — 1.1.x is on Alpha).
- The workflow uploads with `status: completed` — testers get it as soon as Google's
  review clears. Change `track:` in the workflow to `internal` for a no-review smoke lane.
- Tag-protection tip: in GitHub → Settings → Tags, add a protection rule for `v*` so
  only you can push release tags.
