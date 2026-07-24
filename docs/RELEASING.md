# Releasing — tag-to-Play pipeline

Pushing a version tag publishes that commit to the **Play closed-testing (Alpha) track**
automatically via `.github/workflows/release.yml`.

## Release procedure

1. Bump `versionName` and `versionCode` in `app/build.gradle.kts` (D-S9-3:
   `versionCode = MAJOR*10000 + MINOR*100 + PATCH`), commit, push.
2. Update `distribution/whatsnew/whatsnew-en-US` (the Play "What's new" text).
3. Tag and push:
   ```bash
   git tag v1.2.0 && git push origin v1.2.0
   ```
The workflow verifies the tag matches `versionName`, runs the unit-test gate (incl. the
plan-data verification gate), builds the signed bundle, and uploads it to the Alpha track
with the what's-new text.

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

### One-time prerequisite — production permission (owner)
The `PLAY_SERVICE_ACCOUNT_JSON` account was originally scoped to **testing tracks only**, so
it **cannot** promote to production until you grant it once:
Play Console → **Users and permissions** → the service-account user → app permissions for
`com.jpillion.dailyreadingplanner` → enable **"Release to production, exclude devices, and use
Play App Signing"**. Until that's granted, the promote workflow fails with a **403**.

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
