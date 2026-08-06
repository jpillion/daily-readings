"""drp-bible-proxy — a thin, stateless API.Bible pass-through.

Holds the API.Bible key server-side (so it is never extractable from the APK) and only
answers requests Google attests came from the real app, via Firebase App Check backed by
Play Integrity.

Deliberately stores NO scripture. See backend/README.md "Why not cache here" — a
pass-through redistributes nothing; a server-side corpus would be prohibited. Do not add
a response cache to this service.
"""
from __future__ import annotations

import logging
import os
import re
import threading
from datetime import datetime, timezone

import httpx
import jwt
from fastapi import FastAPI, Header, HTTPException, Query
from fastapi.responses import JSONResponse

log = logging.getLogger("proxy")
logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")

# ---------------------------------------------------------------- configuration

API_BIBLE_KEY = os.environ.get("API_BIBLE_KEY", "")
FIREBASE_PROJECT_NUMBER = os.environ.get("FIREBASE_PROJECT_NUMBER", "")
FIREBASE_PROJECT_ID = os.environ.get("FIREBASE_PROJECT_ID", "")
MONTHLY_CALL_BUDGET = int(os.environ.get("MONTHLY_CALL_BUDGET", "140000"))
BUDGET_BACKEND = os.environ.get("BUDGET_BACKEND", "memory").lower()
POLICY_ON_ATTESTATION_FAIL = os.environ.get("POLICY_ON_ATTESTATION_FAIL", "deny").lower()

# Translation code -> API.Bible bible id. An explicit allowlist, so a stolen token cannot
# turn this service into a general-purpose relay for the whole account catalogue.
BIBLE_IDS = {
    "NKJV": "63097d2a0a2f7db3-01",
    "NASB": "a761ca71e0b3ddcf-01",  # New American Standard Bible 2020
}
ALLOWED_BIBLES = {
    c.strip().upper()
    for c in os.environ.get("ALLOWED_BIBLES", "NKJV").split(",")
    if c.strip()
}

API_BIBLE_BASE = "https://rest.api.bible/v1"
APP_CHECK_JWKS = "https://firebaseappcheck.googleapis.com/v1/jwks"
UPSTREAM_TIMEOUT = httpx.Timeout(10.0, connect=5.0)

# USFM passage ids: GEN.1 | GEN.1.1 | GEN.1-GEN.2 | GEN.1.1-GEN.2.25
REF_RE = re.compile(r"^[A-Z0-9]{3}\.\d{1,3}(\.\d{1,3})?(-[A-Z0-9]{3}\.\d{1,3}(\.\d{1,3})?)?$")

app = FastAPI(title="drp-bible-proxy", docs_url=None, redoc_url=None, openapi_url=None)

_jwks_client = jwt.PyJWKClient(APP_CHECK_JWKS, cache_keys=True)
_http: httpx.AsyncClient | None = None


# ---------------------------------------------------------------- budget guard

class _MemoryBudget:
    """Per-instance counter. Resets on cold start — a smoke alarm, not a budget."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._period = ""
        self._count = 0

    def check_and_increment(self, period: str) -> int:
        with self._lock:
            if period != self._period:
                self._period, self._count = period, 0
            self._count += 1
            return self._count


class _FirestoreBudget:
    """Cross-instance monthly counter. The only setting that actually enforces a ceiling."""

    def __init__(self) -> None:
        from google.cloud import firestore  # imported lazily so `memory` needs no dep

        self._db = firestore.Client()
        self._inc = firestore.Increment(1)

    def check_and_increment(self, period: str) -> int:
        doc = self._db.collection("proxy_budget").document(period)
        doc.set({"calls": self._inc}, merge=True)
        snap = doc.get()
        return int(snap.get("calls") or 0)


_budget = _FirestoreBudget() if BUDGET_BACKEND == "firestore" else _MemoryBudget()


def _current_period() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m")


# ---------------------------------------------------------------- app check

def _verify_app_check(token: str) -> str:
    """Return the attested app id, or raise. Verifies signature, issuer, audience, expiry."""
    signing_key = _jwks_client.get_signing_key_from_jwt(token).key
    claims = jwt.decode(
        token,
        signing_key,
        algorithms=["RS256"],
        audience=f"projects/{FIREBASE_PROJECT_NUMBER}",
        issuer=f"https://firebaseappcheck.googleapis.com/{FIREBASE_PROJECT_NUMBER}",
        options={"require": ["exp", "iat", "sub", "aud", "iss"]},
    )
    sub = claims.get("sub")
    if not sub:
        raise jwt.InvalidTokenError("no subject")
    return str(sub)


def _attest(token: str | None) -> str | None:
    """Enforce the attestation policy. Returns app id, or None when running log-only."""
    if not token:
        if POLICY_ON_ATTESTATION_FAIL == "allow":
            log.warning("attestation_failed reason=missing_token policy=allow")
            return None
        raise HTTPException(status_code=401, detail="app attestation required")
    try:
        return _verify_app_check(token)
    except Exception as exc:  # noqa: BLE001 — any verification failure is a denial
        log.warning("attestation_failed reason=%s policy=%s", type(exc).__name__,
                    POLICY_ON_ATTESTATION_FAIL)
        if POLICY_ON_ATTESTATION_FAIL == "allow":
            return None
        raise HTTPException(status_code=401, detail="app attestation failed") from exc


# ---------------------------------------------------------------- lifecycle

@app.on_event("startup")
async def _startup() -> None:
    global _http
    missing = [
        n for n, v in (
            ("API_BIBLE_KEY", API_BIBLE_KEY),
            ("FIREBASE_PROJECT_NUMBER", FIREBASE_PROJECT_NUMBER),
            ("FIREBASE_PROJECT_ID", FIREBASE_PROJECT_ID),
        ) if not v
    ]
    if missing:
        raise RuntimeError(f"missing required configuration: {', '.join(missing)}")
    _http = httpx.AsyncClient(timeout=UPSTREAM_TIMEOUT, headers={"api-key": API_BIBLE_KEY})
    log.info("started budget_backend=%s policy=%s bibles=%s",
             BUDGET_BACKEND, POLICY_ON_ATTESTATION_FAIL, sorted(ALLOWED_BIBLES))


@app.on_event("shutdown")
async def _shutdown() -> None:
    if _http:
        await _http.aclose()


# ---------------------------------------------------------------- routes

@app.get("/healthz")
async def healthz() -> dict[str, str]:
    """Unauthenticated liveness probe. Reveals nothing."""
    return {"status": "ok"}


@app.get("/v1/passage")
async def passage(
    bible: str = Query(..., max_length=16),
    ref: str = Query(..., max_length=32),
    x_firebase_appcheck: str | None = Header(default=None, alias="X-Firebase-AppCheck"),
) -> JSONResponse:
    _attest(x_firebase_appcheck)

    code = bible.strip().upper()
    if code not in ALLOWED_BIBLES or code not in BIBLE_IDS:
        raise HTTPException(status_code=400, detail="unsupported translation")
    if not REF_RE.match(ref):
        raise HTTPException(status_code=400, detail="malformed reference")

    used = _budget.check_and_increment(_current_period())
    if used > MONTHLY_CALL_BUDGET:
        log.error("budget_exceeded used=%s ceiling=%s", used, MONTHLY_CALL_BUDGET)
        # 503 + Retry-After: the client treats this exactly like being offline and
        # degrades to the bundled KJV (D-T-6). It is not an error the user should see.
        return JSONResponse(
            status_code=503,
            content={"detail": "translation temporarily unavailable"},
            headers={"Retry-After": "3600"},
        )

    assert _http is not None
    url = f"{API_BIBLE_BASE}/bibles/{BIBLE_IDS[code]}/passages/{ref}"
    try:
        # D-OT-6: structured USX, NOT html. The client needs per-verse addressing
        # (verse tags carry `sid`/`number`) to build its verse-id-keyed reader; an html
        # blob cannot be split back into verses reliably.
        upstream = await _http.get(url, params={"content-type": "json"})
    except httpx.RequestError as exc:
        log.error("upstream_unreachable error=%s", type(exc).__name__)
        raise HTTPException(status_code=502, detail="upstream unreachable") from exc

    if upstream.status_code == 404:
        raise HTTPException(status_code=404, detail="passage not found")
    if upstream.status_code != 200:
        log.error("upstream_error status=%s", upstream.status_code)
        raise HTTPException(status_code=502, detail="upstream error")

    body = upstream.json()
    data = body.get("data", {})
    return JSONResponse({
        "reference": data.get("reference"),
        "content": data.get("content"),
        "copyright": data.get("copyright"),
        # Passed through untouched — the client reports FUMS, since device and session
        # ids are the client's to supply.
        "fumsToken": (body.get("meta") or {}).get("fumsToken"),
    })
