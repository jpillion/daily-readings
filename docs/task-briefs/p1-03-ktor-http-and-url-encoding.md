# p1-03 — Ktor client, the in-house percent-encoder, and `kotlin.uuid.Uuid`

> **Assignee:** Senior Shared-Core Engineer (or a second core engineer — this is fully parallel
> with `p1-01`/`p1-02`)
> **Release:** 1.9.0 · **Merge order:** Group A, parallel with `p1-01`, `p1-04`, `p1-08`.
> **Inherits:** [`p1-00-overview.md`](p1-00-overview.md) rules R1–R7.
> **Preconditions:** Gate 0 closed. **Build & Release has approved and added the Ktor coordinates.**
> **Executes:** ADR-0014 **including its Amendment A1**, which you must read before starting.

---

## Objective

Replace `HttpURLConnection` with a Ktor client for the app's **two** GET requests, and replace
`java.net.URLEncoder` at **two structurally different call sites** — one with Ktor's encoder, one
with a small in-house percent-encoder — for a reason that is about module boundaries, not
convenience.

`java.util.UUID` → `kotlin.uuid.Uuid` rides along.

---

## Context

Sprint 00R added online NKJV/NASB via a Cloud Run proxy. The **entire** network surface is:

1. `GET {baseUrl}/v1/passage?bible={code}&ref={ref}` with an optional `X-Firebase-AppCheck`
   header, returning JSON — `bible/data/remote/BibleApiClient.kt`.
2. `GET https://fums.api.bible/f3?t=…&dId=…&sId=…`, response body discarded — a **licence
   obligation** under the API.Bible terms, not telemetry — `bible/data/remote/HttpFumsReporter.kt`.

Both already sit behind interfaces (`BibleApiClient`, `FumsReporter`) with fakes in tests, and the
verse cache behind `BibleTextCache`. **Nothing above these interfaces knows how the bytes arrive**,
so the blast radius is genuinely two files.

`java.net.HttpURLConnection` does not exist on Kotlin/Native.

Note the deliberate history you are reversing: the files' own KDoc records that OkHttp and Retrofit
were **rejected** because "the call shape is one authenticated GET returning JSON, and this repo has
held 'zero net-new runtime deps' through nearly every sprint with a 12 MB bundle gate to protect."
That judgement was right for an Android-only app. **Ktor wins here on the drift argument, not the
convenience one**: the status-code mapping (`200` → parse, `404` → `NotFound`, everything else →
`Unavailable`) is **product behaviour** — it drives the D-OT-2 offline fallback and the "Unable to
download NKJV, displaying KJV" banner — and duplicating it in two platform actuals gives you two
places a mapping can drift, with a **silent** failure mode.

### The `URLEncoder` split — read ADR-0014 Amendment A1, this is the crux

The two call sites are **not** the same problem:

| Call site | Port destination | Encoder |
|---|---|---|
| `bible/data/remote/HttpFumsReporter.kt:65` | `shared/data` | **Ktor's** `encodeURLParameter`. Ktor is already a dependency there. |
| `data/reference/ProviderUrlBuilder.kt:68,110` | **`shared/domain`** | **an in-house percent-encoder.** |

ADR-0001 forbids Ktor in `shared/domain`. `ProviderUrlBuilder` is pure URL construction over the
book catalog with no IO, so it belongs in `shared/domain` — therefore it cannot use Ktor's encoder.
ADR-0014's original text said to use Ktor for both; **Amendment A1 corrects it.**

### The trap, stated so nobody has to rediscover it

**`java.net.URLEncoder.encode` is HTML form encoding (`application/x-www-form-urlencoded`), not URL
encoding.**

- **space → `+`**, not `%20`
- `~` → `%7E`, where RFC 3986 leaves it unreserved
- `*` passes through, where RFC 3986 encodes it

`ProviderUrlBuilder` builds Bible Gateway search strings that **contain spaces** — e.g.
`?search=Genesis 1-2&version=KJV`. So the shipped, live-verified output contains `+`, and any naive
RFC 3986 encoder emits `%20` and changes every one of those URLs.

Those URLs are not incidental. They were **live-verified 134/134 against Bible Gateway and 132/132
against YouVersion** (sprint 13), with the verse-level shapes verified again in sprint 00H
(2026-06-15). `docs/data/provider-link-checks.md` is the record. The committed test suite is
offline and its expectations **are the specification**.

> **`ProviderUrlBuilderTest` must pass byte-for-byte unchanged.** Any diff is a **bug in the port,
> not a stale test.** Do not update an expectation to match new output — that is how a
> live-verified URL corpus silently rots.

---

## Contract

### 1. Ktor for the two GETs

`HttpBibleApiClient` and `HttpFumsReporter` are rewritten against Ktor's `HttpClient`. Preserve
**exactly**:

- The URL shapes, including query-parameter **order**.
- The optional `X-Firebase-AppCheck` header — still supplied by the `appCheckTokenProvider` lambda
  in `di/BibleRemoteModule.kt`, which still returns `null` today. **Do not implement App Check
  here**; it is a live, owner-accepted open security item and closing it is a separate decision.
- The **status-code → `PassageResult` mapping**, verbatim: `200` → parse, `404` → `NotFound`,
  everything else → `Unavailable`.
- **The swallow-everything error policy.** Every failure — timeout, DNS, TLS, malformed JSON,
  cancellation — becomes `Unavailable`, never a thrown exception reaching the UI. The user sees
  the fallback banner and KJV text. **This is load-bearing** and it is the behaviour a Ktor rewrite
  is most likely to change by accident, because Ktor throws where `HttpURLConnection` returned a
  code. Wrap accordingly.
- The timeout values, unchanged.
- FUMS: the response body is discarded and failures are ignored **completely** — a FUMS failure
  must never affect passage display.

Engine: **OkHttp on Android**. Structure the client so the engine is supplied at the DI boundary,
not constructed inside the client class — Phase 2 adds a Darwin engine there and should not have
to reopen this file.

### 2. The in-house percent-encoder

`data/reference/PercentEncoder.kt` (destined for `shared/domain`), ~20 lines:

- UTF-8 encode the input.
- Pass through the RFC 3986 unreserved set: `A–Z a–z 0–9 - _ . ~`
- **Reproduce today's shipped bytes.** Where `ProviderUrlBuilderTest` expects `+` for a space,
  **emit `+`.** Where it expects `%20`, emit `%20`. **The tests define the contract**; derive the
  rule from them, do not derive them from a spec.
- Uppercase hex digits.

Its own test pins: space, `+`, `&`, `?`, `:`, `/`, `,`, `;`, a non-ASCII character (multi-byte
UTF-8), and the empty string.

### 3. `kotlin.uuid.Uuid`

`bible/data/remote/FumsIdentity.kt:40,44`. **The generated string format must not change** — FUMS
device and session ids are an external licence-reporting contract. Pin the shape (canonical
8-4-4-4-12 lowercase hex) in a test.

### 4. `Dispatchers.IO` in `BibleApiClient.kt:61`

Route through the existing `@IoDispatcher` qualifier. **`Dispatchers.IO` does not exist on
Kotlin/Native** and this compiles fine on Android, which is exactly why it is easy to miss.
`p1-04` owns the other four sites; you own this one only.

---

## Acceptance criteria

1. `grep -rn "HttpURLConnection\|java.net.URL\b\|java.net.URLEncoder\|java.util.UUID\|BufferedReader" app/src/main/kotlin`
   returns **nothing**.
2. `grep -rn "Dispatchers.IO" app/src/main/kotlin/com/jpillion/dailyreadingplanner/bible/data/remote/BibleApiClient.kt`
   returns nothing.
3. **`ProviderUrlBuilderTest` passes with ZERO changes to any expected string.** State this
   explicitly in the PR: *"N expectations, 0 modified."*
4. `OpenVerseUseCaseTest` and `OpenReferenceUseCaseTest` pass unchanged — they pin the four
   provider URL shapes and the MySword→BLB fallback.
5. `HttpFumsReporterTest` and the `BibleApiClient` tests pass, converted to assertk. The
   status-code mapping and the swallow-everything policy each keep a dedicated test.
6. **≥4 killed mutations, each by its intended test, each restored byte-identically:**
   (a) `404` mapped to `Unavailable` instead of `NotFound`; (b) an exception allowed to propagate
   instead of becoming `Unavailable`; (c) the percent-encoder emitting `%20` where the corpus
   expects `+`; (d) the FUMS header dropped from the passage request.
7. `grep -rn "io.ktor" app/src/main/kotlin/com/jpillion/dailyreadingplanner/data/reference/`
   returns **nothing** — the `shared/domain`-destined boundary holds from day one.
8. Every test file touched is converted Truth → assertk (R4).
9. Full pipeline green. **`bundleRelease` builds and the AAB is reported** against the **12 MB CI
   gate** (currently 7.86 MB). If Ktor pushes it materially — escalate, do not silently accept.
10. **The six data gates untouched, counts unchanged: 11 / 10 / 8 / 6 / 18 / 5.**
11. **R5 R8 release-build device smoke:** with the network **on**, switch the reader to NKJV and
    then NASB and confirm real text renders with the publisher copyright; with the network **off**,
    confirm the banner "Unable to download NKJV, displaying KJV" appears **above KJV text**; then
    tap a verse and confirm it opens at the right verse in **each** external provider.

---

## Boundaries / write set

**Yours:**
- `app/src/main/kotlin/.../bible/data/remote/{BibleApiClient,HttpFumsReporter,FumsIdentity}.kt`
- `app/src/main/kotlin/.../data/reference/ProviderUrlBuilder.kt`
- `app/src/main/kotlin/.../data/reference/PercentEncoder.kt` **(new)**
- `app/src/main/kotlin/.../di/BibleRemoteModule.kt`
- The test files covering the above

**Not yours:**
- `bible/data/remote/BibleTextCache.kt` — **`p1-04`** (file IO, not HTTP).
- `bible/data/remote/{BibleTextResolver,UsxTransformer}.kt` — nobody; they do not change.
- `di/{DispatcherModule,BibleModule}.kt` — **`p1-04`**.
- Anything with a `java.time` import — **`p1-02`**.
- `gradle/libs.versions.toml`, `app/build.gradle.kts` — **Build & Release** (invariant 6). Ktor
  must be added by them, resolving for `android` **and** (for Phase 2) `iosArm64` /
  `iosSimulatorArm64`.
- `docs/data/provider-link-checks.md` — historical record. **Read it; do not edit it.**

---

## Escalation triggers

- **Any `ProviderUrlBuilderTest` expectation appears to need changing** → **Staff**, blocking.
  That corpus is live-verified against real services; a change means the port altered a shipped
  URL. Bring the exact before/after string.
- **You cannot reproduce `+`-for-space without special-casing** → that special case *is* the
  requirement. If it feels wrong, escalate to **Staff** rather than "correcting" to RFC 3986. A
  URL-shape improvement is a separate, live-re-verified change on Android first.
- **Ktor coordinates do not resolve, or the AAB grows materially** → **Build & Release**. ADR-0014
  names the fallback: `expect`/`actual` `HttpGet` with the **mapping kept in one shared pure
  function** and the actuals returning only `(status, body)`. That fallback is acceptable; a
  drifting mapping is not.
- **You are tempted to implement App Check** → **Owner**, non-blocking. The proxy runs
  `POLICY_ON_ATTESTATION_FAIL=allow` today and it is an owner-accepted open item. The port makes it
  cost twice (iOS App Check is a different SDK) but does not make it this task.
- **The UUID string format changes** → **Staff**, blocking. It is an external contract.
