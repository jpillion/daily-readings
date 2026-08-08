# ADR-0014 — HTTP client for online translations

**Status:** Draft, pending owner sign-off · **Date:** 2026-08-08 · **Author:** Staff / Port Architect
· **Amended:** 2026-08-08 — see [Amendment A1](#amendment-a1--providerurlbuilder-cannot-use-ktors-encoder-adr-0001-forbids-it)

## Context

Sprint 00R added online NKJV/NASB via a Cloud Run proxy. The entire network surface is **two GET
requests**:

1. `GET {baseUrl}/v1/passage?bible={code}&ref={ref}` with an optional `X-Firebase-AppCheck`
   header, returning JSON (`bible/data/remote/BibleApiClient.kt`).
2. `GET https://fums.api.bible/f3?t=…&dId=…&sId=…`, response body discarded — a **licence
   obligation**, not telemetry (`bible/data/remote/HttpFumsReporter.kt`).

Both are implemented with `HttpURLConnection` + `java.net.URL`, deliberately: the file's own KDoc
records that OkHttp/Retrofit were rejected because "the call shape is one authenticated GET
returning JSON, and this repo has held 'zero net-new runtime deps' through nearly every sprint
with a 12 MB bundle gate to protect."

Both already sit behind interfaces (`BibleApiClient`, `FumsReporter`) with fakes in tests, and the
verse cache behind `BibleTextCache` with a no-op implementation. **Nothing above these interfaces
knows how the bytes arrive.**

`java.net.HttpURLConnection` does not exist on Kotlin/Native.

## Decision

**Ktor client, in `shared/data`, with `OkHttp` engine on Android and `Darwin` on iOS.**

Marginal call, and I want the reasoning visible rather than assumed.

**For Ktor:** it puts the two request implementations in `commonMain`, so there is one copy of the
URL construction, the status-code mapping (`200` → parse, `404` → `NotFound`, everything else →
`Unavailable`), the timeout values and the swallow-everything error policy. That policy is
load-bearing — `PassageResult.Unavailable` drives the D-OT-2 offline fallback and the "Unable to
download NKJV, displaying KJV" banner. Duplicating it in two actuals means two places where a
status-code mapping can drift, and the failure mode is silent (a user sees a banner instead of
NASB, and nobody notices for months).

**Against Ktor:** it is a real dependency with a real binary cost on a project that guards a 12 MB
bundle, for two GET calls. The alternative — an `expect` `HttpGet` with `HttpURLConnection` and
`NSURLSession` actuals — is maybe 60 lines per platform and adds nothing to either binary.

**Ktor wins on the drift argument, not the convenience one.** The URL shapes and the degradation
policy are product behaviour with licence implications; they belong in one place.

**Also decided:**
- The **percent-encoding** currently done with `java.net.URLEncoder` (in `HttpFumsReporter` and,
  importantly, in `data/reference/ProviderUrlBuilder`) moves to Ktor's
  `encodeURLParameter`/`encodeURLPath`. **`ProviderUrlBuilder` is the more sensitive of the two** —
  it builds the external Bible-app URLs that were live-verified 134/134 and 132/132 against Bible
  Gateway and YouVersion, and the verse-level shapes verified again in sprint 00H. **Its output
  must be byte-identical after the swap.** `URLEncoder.encode` is form-encoding (space → `+`),
  which is *not* the same as URL-path encoding (space → `%20`). Check every existing
  `ProviderUrlBuilderTest` expectation, and treat any diff as a bug in the port, not in the test.
- `java.util.UUID` in `FumsIdentity` → `kotlin.uuid.Uuid`.
- `java.io.File` in `BibleTextCache` → **okio** (`FileSystem`, `Path`), with the cache directory
  supplied by a `shared/platform` seam.
- `android.util.Log` (3 sites) → a 2-method `Logger` interface in `shared/platform`.
- `Dispatchers.IO` **does not exist on Kotlin/Native.** Every IO dispatch routes through the
  existing `@IoDispatcher` qualifier (`di/DispatcherModule.kt`), which is provided per platform.
  This is easy to miss because it compiles fine on Android; grep for it explicitly.

## Alternatives rejected

**`expect`/`actual` `HttpGet` over `HttpURLConnection` + `NSURLSession`.** Zero new
dependencies, smallest binary, and honestly defensible for two GETs. **Rejected on the drift
argument above** — the status-code → `PassageResult` mapping is product behaviour, and it would
live in two files. If Build & Release objects to Ktor on binary-size grounds, this is the
fallback, and the mitigation is to keep the *mapping* in a shared pure function and let the
actuals return only `(status, body)`. That would make it an acceptable second choice.

**Keep `HttpURLConnection` on Android and write only an iOS actual.** Rejected — same drift
problem, worse, because the two would not even be structurally parallel.

**OkHttp with a Kotlin/Native shim.** Does not exist. Rejected.

**Retrofit / Ktorfit.** Rejected — annotation-driven API definition for two endpoints is more
machinery than the problem.

## Consequences accepted

- **A new runtime dependency** (Ktor core + two engines). Must go through Build & Release, who
  must confirm it resolves for `android`, `iosArm64` and `iosSimulatorArm64`, and must re-check
  the AAB against the 12 MB gate (currently 7.86 MB, so there is headroom).
- Ktor pulls kotlinx-serialization for JSON, **already present**. No net-new there.
- `HttpBibleApiClient` and `HttpFumsReporter` are rewritten. Both are small, both have interface
  fakes above them, and their tests do not touch sockets — so the blast radius is two files.
- The **App Check** token provider (`di/BibleRemoteModule.kt`, currently a lambda returning null)
  becomes a `shared/platform` seam with two actuals. Note for the owner: closing the open security
  item recorded in CLAUDE.md (`POLICY_ON_ATTESTATION_FAIL=allow`, endpoint publicly reachable) now
  requires configuring App Check for **two** apps, not one. Not a port blocker, but it makes that
  item slightly more expensive the longer it stays open.
- **iOS ATS** requires HTTPS. Both endpoints are HTTPS. No `NSAppTransportSecurity` exception
  needed — and none should be added.
- **The `INTERNET` permission story is Android-only.** iOS has no network permission; the app
  simply makes requests. The privacy questionnaire (RELEASING-IOS.md Part 2) is where iOS accounts
  for this, and it needs a deliberate answer about FUMS reporting.

## Revisit when

- Build & Release measures the Ktor binary cost and it is materially worse than expected — fall
  back to the `expect`/`actual` option with a shared mapping function.
- The network surface grows beyond a handful of endpoints (it would strengthen the Ktor case).
- App Check is configured — that is the point at which the token seam gets its real actuals.

---

## Amendment A1 — `ProviderUrlBuilder` cannot use Ktor's encoder; ADR-0001 forbids it

**Date:** 2026-08-08 · **Author:** Staff / Port Architect
**Corrects:** the "Also decided" bullet in this ADR that routes **both** `URLEncoder` call sites
through Ktor's `encodeURLParameter`/`encodeURLPath`.

### A1.1 The conflict

That bullet contradicts ADR-0001. `ProviderUrlBuilder` lives in `data/reference/` today and its
port destination is **`shared/domain`** (port-inventory §3.7) — it is pure URL construction over
the book catalog, with no IO. ADR-0001's forbidden list for `shared/domain` is explicit:

> `FORBIDDEN: java.*, android.*, Room, DataStore, Compose, okio, Ktor.`

Routing `ProviderUrlBuilder` through `io.ktor.http.encodeURLParameter` puts Ktor in
`shared/domain`. Either the ADR-0001 boundary erodes on its first contact with a real task, or
this bullet is wrong. **The bullet is wrong.** I wrote both and did not notice; recording it
rather than quietly fixing one of them.

The two call sites are **not** the same problem and should never have been grouped:

| Call site | Layer | Purpose | Encoder |
|---|---|---|---|
| `bible/data/remote/HttpFumsReporter.kt:65` | `shared/data` | query params on an outbound HTTP request | **Ktor's** `encodeURLParameter`. Ktor is already a dependency of `shared/data`. |
| `data/reference/ProviderUrlBuilder.kt:68,110` | `shared/domain` | builds the external Bible-app URLs the user is sent to — **product output, not a network call** | **an in-house percent-encoder in `shared/domain`.** |

### A1.2 The decision

**Write a small percent-encoder in `shared/domain`.** Roughly 20 lines: UTF-8 encode, pass
through the RFC 3986 unreserved set `A-Z a-z 0-9 - _ . ~`, percent-encode everything else as
uppercase hex.

It is pinned by an existing, unusually strong test corpus, which is why this is a safe thing to
hand-write rather than a reckless one: `ProviderUrlBuilderTest` encodes URL shapes that were
**live-verified 134/134 against Bible Gateway and 132/132 against YouVersion**, plus the
verse-level shapes verified again in sprint 00H. Those expectations are the specification.

### A1.3 The trap that this makes explicit — and it is the real point

`java.net.URLEncoder.encode` is **HTML form encoding**
(`application/x-www-form-urlencoded`), not URL encoding. The differences that bite here:

- **space → `+`**, not `%20`
- `~` is percent-encoded (`%7E`) where RFC 3986 leaves it unreserved
- `*` is passed through where RFC 3986 percent-encodes it

`ProviderUrlBuilder` builds Bible Gateway search strings that **contain spaces** — e.g.
`?search=Genesis 1-2&version=KJV`. So today's shipped, live-verified output contains `+`, and a
naive swap to any RFC 3986 encoder emits `%20` and changes every one of those URLs.

> **The in-house encoder must reproduce the current bytes exactly, including `+` for space where
> the existing tests expect it.** Any diff in `ProviderUrlBuilderTest` is a **bug in the port, not
> a stale test.** Do not update an expectation to match new output. If a genuine improvement to a
> URL shape is wanted, that is a separate, live-re-verified change on Android first.

This is exactly the risk ADR-0014's body flagged in one sentence; the amendment makes it a rule.

### A1.4 What is unchanged

Ktor is still the decision for `shared/data`'s two GET requests, for the drift reason given in
the body. `HttpFumsReporter` uses Ktor's encoder. Only `ProviderUrlBuilder` changes, and only
because of where it lives.
