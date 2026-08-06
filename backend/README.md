# `drp-bible-proxy` — thin API.Bible proxy

A stateless pass-through proxy that holds the API.Bible key server-side so it is never shipped
inside the APK, and only answers requests that Google attests came from the real app.

**It stores no scripture.** Every request is forwarded, the response is returned, nothing is
retained. This is deliberate and load-bearing — see [Why not cache here](#why-not-cache-here).

---

## Deployed (2026-08-06)

| | |
|---|---|
| **URL** | `https://drp-bible-proxy-954215684233.us-central1.run.app` |
| Project | `daily-readings-proxy` (number `954215684233`) |
| Billing | `billingAccounts/0130C6-BEDA74-720DBD` ("FlipReady") — same account as `daily-reading-planner-app` |
| Region | `us-central1` |
| Runtime SA | `drp-proxy-sa@daily-readings-proxy.iam.gserviceaccount.com` (dedicated, least-privilege) |
| Budget backend | `firestore` (verified recording) |
| **Attestation policy** | **`deny`** |

Deployed into its **own** project rather than `daily-reading-planner-app`, which has
`androidpublisher` enabled and holds the Play publishing service account — a public
internet-facing service does not belong in that blast radius.

**Shipped in `deny`, not the `allow` default in `deploy.sh`.** App Check is not configured yet and
no client exists, so denying everything means there is no open endpoint sitting on the internet in
the meantime. The staged rollout in [Configuration](#configuration) still applies, but its correct
order here is: flip to `allow` **when the client first reaches internal testing**, measure the real
lockout from the logs, then return to `deny`.

### Verified in production

| Check | Result |
|---|---|
| Secret injection + upstream fetch (`GEN.1-GEN.2`) | 200 — 12,398 chars, copyright + FUMS token |
| Firestore budget counter | doc `2026-08` incremented to 1 |
| No attestation token | 401 |
| Malformed token | 401 (`attestation_failed reason=DecodeError policy=deny`) |
| `/docs`, `/openapi.json` | 404 — not exposed |

> **Note on `/healthz`.** It is served correctly by the container, but was unreachable from the
> environment this was deployed from: bare `/healthz` never appeared in the Cloud Run request log
> while every other path did, and `/healthz/` reached the container and returned FastAPI's own 307.
> That is a health-check path collision in that egress proxy, not a defect here. Verify it from a
> normal network.

### Not done yet

Firebase, App Check and Play Integrity are **not** set up — they need the Play Console (app signing
SHA-256, Play Integrity enablement, Play↔Firebase linkage). Until then the service correctly refuses
every request.

---

## Why this exists

`docs/features/additional-translations.md` **OQ-3**: an API key shipped in an APK is extractable,
and the API.Bible quota is a single shared exhaustible pool (150,000 calls/month). An extracted key
lets anyone burn the whole month's quota and kill NKJV for every user, with rotation requiring an
app update.

This proxy moves the key somewhere it cannot be extracted, and puts an attestation gate in front.

## Architecture

```
Android app
  └─ Firebase App Check (Play Integrity provider) ──► short-lived App Check JWT
       │
       ▼
  GET /v1/passage?bible=NKJV&ref=GEN.1-GEN.2
  X-Firebase-AppCheck: <jwt>
       │
       ▼
  Cloud Run (scale-to-zero, 1 container, no DB)
    1. verify App Check JWT   (signature via Firebase JWKS, iss/aud/exp/sub)
    2. validate bible + ref   (strict allowlist + regex — not an open relay)
    3. budget guard           (refuse near the monthly quota ceiling)
    4. forward to rest.api.bible with the key from Secret Manager
       │
       ▼
  { reference, content, copyright, fumsToken }
       │
       ▼
  app renders, reports FUMS, caches on-device for ≤30 days
```

## What "locked to the app" actually means

This is the part worth reading carefully, because it cannot be made absolute.

**What Play Integrity via App Check does give you.** Google attests that the caller is your app
package, signed with your upload/play signing certificate, installed from Play, unmodified, running
on a genuine Android device that passes basic integrity. That is a real, meaningful gate — it
defeats casual key extraction, curl, scripts, scrapers, and repackaged APKs.

**What it does not give you.** It is not proof of identity, it is evidence of it. A determined
attacker with a rooted device, a hooking framework, or an instrumented build can sometimes extract
a valid token and replay it from elsewhere. Tokens are short-lived, which bounds the damage, but
the honest framing is that this **raises the cost of abuse a great deal; it does not make abuse
impossible.** Anyone who tells you otherwise is selling something.

**Who it locks out that you may not want to lock out.** Attestation fails for: rooted devices,
custom ROMs (GrapheneOS, LineageOS), sideloaded or APK-mirror installs, emulators, devices without
Play Services, and older devices with a broken Play stack. Those users lose NKJV specifically —
the bundled KJV and everything else keeps working, because the app degrades to KJV on any failure
(D-T-6). **This is a product decision, not a technical one**, and it is `POLICY_ON_ATTESTATION_FAIL`
below.

**Defence in depth beyond attestation:** the endpoint is not a generic relay. It accepts only a
fixed allowlist of translation codes and a strict reference regex, and it exposes exactly one route.
Even with a stolen token, an attacker can fetch scripture — which is what the app does anyway — and
cannot pivot the proxy into a general-purpose API gateway or use it to enumerate your account.

## Why not cache here

An earlier design considered a backend that fetched the corpus and served it. That is prohibited:
Lockman and the other publishers explicitly forbid "bulk downloading … or creation of standalone
datasets for redistribution", and a server-side corpus is exactly that regardless of who fetched it.

A **pass-through proxy is categorically different** — it redistributes nothing, retains nothing, and
builds no dataset. That distinction is the whole reason this design is acceptable, so it must not
erode. Do not add a response cache to this service. On-device caching (≤30 days, D-T-5) stays on the
device, where the terms contemplate it.

> **Confirm before launch:** API.Bible's terms are written assuming your app calls them directly.
> A server-side proxy holding the key is normal practice, but **email support@api.bible and get it
> in writing** before this goes live. It is one email and it removes the only unresolved legal
> question in this design.

## Cost

Cloud Run scales to zero. At this app's traffic the expected bill is **≈ $0/month** — comfortably
inside the free tier (2M requests, 360k GB-s). Secret Manager is ~$0.06/month per secret version.
The real cost risk is not compute, it is the API.Bible quota, which is what the budget guard exists
to protect.

---

## Deploy

### Prerequisites (one-time)

1. **A GCP project** with billing enabled.
2. **Firebase added to that project**, and the Android app registered in it
   (package `com.jpillion.dailyreadingplanner`).
3. **App Check enabled** with the **Play Integrity** provider, using your Play app signing
   certificate SHA-256.
4. `gcloud` authenticated as a principal with Cloud Run Admin, Secret Manager Admin and
   Service Account User.

### Launch

```bash
cd backend
export PROJECT_ID=your-project-id
export REGION=us-central1
export API_BIBLE_KEY=<your api.bible key>

./deploy.sh
```

`deploy.sh` is idempotent — it enables the required APIs, creates or updates the secret, builds the
container, deploys to Cloud Run, and prints the service URL.

### Configuration

| Env var | Default | Meaning |
|---|---|---|
| `FIREBASE_PROJECT_NUMBER` | *(required)* | Numeric project number, for App Check `iss`/`aud` |
| `FIREBASE_PROJECT_ID` | *(required)* | Project id, for the second `aud` form |
| `API_BIBLE_KEY` | *(required, from Secret Manager)* | Never baked into the image |
| `ALLOWED_BIBLES` | `NKJV` | Comma-separated allowlist of translation codes |
| `MONTHLY_CALL_BUDGET` | `140000` | Refuse past this; headroom under the 150k plan |
| `BUDGET_BACKEND` | `memory` | `memory` or `firestore` — see below |
| `POLICY_ON_ATTESTATION_FAIL` | `deny` | `deny` or `allow` (log-only, for staged rollout) |

**`BUDGET_BACKEND`.** `memory` is per-instance and resets on cold start, so it is a smoke alarm,
not a budget. **Use `firestore` in production** — it is the only setting that actually enforces a
cross-instance monthly ceiling, costs effectively nothing at this volume, and is what stands
between a runaway client and a dead quota. `memory` exists so the service runs locally with no
dependencies.

**Staged rollout.** Ship with `POLICY_ON_ATTESTATION_FAIL=allow` first and watch the logs for
`attestation_failed` counts. That tells you how many real users would be locked out before you
lock anyone out. Flip to `deny` once the number looks sane.

### Verify

```bash
URL=$(gcloud run services describe drp-bible-proxy --region "$REGION" --format='value(status.url)')

curl -i "$URL/healthz"                                    # 200, no auth
curl -i "$URL/v1/passage?bible=NKJV&ref=GEN.1"            # 401 — no App Check token
curl -i -H "X-Firebase-AppCheck: garbage" \
     "$URL/v1/passage?bible=NKJV&ref=GEN.1"               # 401 — bad token
```

A valid call can only be made from the app, which is the entire point. Test it from a debug build
with an App Check **debug token** registered in the Firebase console.

## Client changes (not in this directory)

Phase 2 of `docs/features/additional-translations.md`:

- `firebase-appcheck` + Play Integrity provider
- `ApiBibleTextSource` pointed at this service, sending `X-Firebase-AppCheck`
- on-device cache, ≤30-day expiry
- FUMS reporting from the client (device/session ids are the client's to report — the proxy
  forwards `fumsToken` untouched)
- degradation to bundled KJV on any failure (D-T-6)
- `INTERNET` permission; CI must assert **only** `INTERNET` is added
