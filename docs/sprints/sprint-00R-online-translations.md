# Sprint 00R — Online translations (NKJV + NASB) — **INCOMPLETE, hand-off**

**Branch:** `claude/available-translations-7zc3pq` (pushed)
**Spec:** [docs/features/online-translations.md](../features/online-translations.md) — read this first; all D-OT-* decisions live there.
**Status:** backend **done and deployed**; app-side engine + UI **done and tested** (steps 1–5); **FUMS (6) and App Check (7) NOT done** — neither is optional before shipping.
**Version:** untouched (1.7.1 / 10701). Nothing here ships yet.

> **The feature is user-visible as of steps 4–5.** Pick NKJV or NASB in the reader top bar and the
> text follows; if it cannot be fetched the reader shows bundled KJV with the D-OT-2 banner. A user
> who never opens the selector reads exactly the offline KJV they read before.
>
> **Not yet shippable:** FUMS reporting (step 6) is a *licence obligation* and is still a no-op, and
> App Check (step 7) is not configured, so the proxy is still publicly reachable (§4).

**Update (steps 1–5 landed).** 926 tests, 0 failures; full pipeline green from clean,
`assembleRelease` clean, Kover 96.4%, a11y gate 13. The five data/Room gates are untouched
(11/10/8/18/5).

⚠️ **`spotlessCheck` was already RED on the committed branch state** before this work — the earlier
"907 tests green" did not include it, so CI would have failed on this branch regardless. Fixed in
`1ca15c7`: `spotlessApply` over the four remote-stack files, plus `BibleVersions.kt` →
`BibleVersion.kt` (ktlint's `standard:filename` is not auto-fixable). **Run the full pipeline, not
just `testDebugUnitTest`.**

---

## 1. What is DONE

### Backend — live in production

| | |
|---|---|
| URL | `https://drp-bible-proxy-954215684233.us-central1.run.app` |
| Project | `daily-readings-proxy` (`954215684233`), billing = FlipReady |
| Runtime SA | `drp-proxy-sa@daily-readings-proxy.iam.gserviceaccount.com` |
| Revision | `drp-bible-proxy-00005-z4d` |

- `content-type=json` (was `html`) — the client needs per-verse addressing; an html blob cannot be
  split back into verses reliably.
- `ALLOWED_BIBLES=NKJV,NASB`; `BIBLE_IDS` gained NASB 2020 (`a761ca71e0b3ddcf-01`).
- **`POLICY_ON_ATTESTATION_FAIL=allow`** — see §4, this is a live security caveat.

Verified live: both translations return structured content + FUMS token + copyright; `NIV` → 400.

### App — engine (committed with the earlier session, 907 tests green at that point)

| File | What |
|---|---|
| `bible/data/remote/UsxTransformer.kt` | USX tree → `VerseText` rows. 17 tests. |
| `bible/domain/model/BibleVersion.kt` | `BibleVersion` enum: KJV bundled, NKJV/NASB remote. (Moved out of `data/remote` in step 2.) |
| `bible/data/remote/BibleApiClient.kt` | `HttpURLConnection` client (no new dependency). |
| `bible/data/remote/BibleTextCache.kt` | Interface + file-backed + no-op impls. |
| `bible/data/remote/BibleTextResolver.kt` | The D-OT-2 fallback chain. 8 tests. |
| `bible/domain/model/VerseText.kt` | Gained `heading: String?` (defaults null). |
| `data/reference/BookCatalog.kt` | Gained `findByUsfm()`. |
| `AndroidManifest.xml` | `INTERNET` added, with a comment saying it retires NFR-V3-A. |

---

## 2. What is NOT done — the remaining work

In dependency order. **Steps 1–5 are now DONE** (`c9f3c34`, `157c571`, `dce0489`); **6–7 remain.**

1. ~~**Hilt module**~~ ✅ **DONE** — `di/BibleRemoteModule` provides `BibleApiClient` →
   `HttpBibleApiClient(PROXY_BASE_URL) { null }`, `BibleTextCache` →
   `FileBibleTextCache(context.cacheDir)`, `FumsReporter` → `NoOpFumsReporter()` (step 6 replaces
   it) and `BibleTextResolver`. All `@Provides`, since `HttpBibleApiClient`/`FileBibleTextCache`
   take non-injectable params and carry no `@Inject`. Kept separate from `BibleModule` so the
   offline bundled asset and the online stack never blur. The App Check token supplier is a
   per-call lambda returning null, so **step 7 is a change to that one provider**.
   Pinned by `BibleRemoteModuleTest` (4).
2. ~~**Use cases → resolver**~~ ✅ **DONE** — `GetChapterUseCase`/`GetPortionTextUseCase` inject
   `BibleTextResolver` + `SettingsRepository`, read the selection at load time (D-S13-4 idiom) and
   pass it down. `ChapterContent` gained `requestedVersion`/`servedVersion`/`copyright` with
   `degraded` derived **per block** (never a shared flag — a pager would banner the wrong page).
   All three default to KJV, so every pre-existing construction site is untouched.
   `GetPortionTextUseCase` reads the selection ONCE per portion, so one reading can never render
   two blocks in different versions. **`BibleVersion` moved to `bible/domain/model`** — a domain
   model now has to name a version.
3. ~~**Persist the selection**~~ ✅ **DONE** — `selectedBibleVersion` over a new
   `selected_bible_version` key; absent ⇒ KJV; unknown codes degrade to KJV, never throw.
4. ~~**Wire `ReaderViewModel.selectVersion`**~~ ✅ **DONE** — `versionState` lists all three
   `BibleVersion` entries, so D-N-3's dropdown branch renders for the first time. `selectVersion`
   persists and does NOT write `_versionState`; the settings collector is the single writer, so the
   label can only show a version that is actually stored. A switch reloads already-open pages.
   **Supersedes D-N-1 for this control** — NKJV/NASB are not in the bundled artifact, so the version
   catalog is necessarily code. `BibleTextSource.translations()` stays as the asset-integrity seam;
   the orphaned `GetTranslationsUseCase` was deleted.
5. ~~**The banner**~~ ✅ **DONE** — `ReaderUiState.Content.degraded`, computed per page from the
   blocks (never a shared flag); on a portion page ANY degraded block banners the page. Sits ABOVE
   the verse list so it cannot occlude scripture, and carries its message in text, not colour.
   **⚠️ Wording discrepancy for sign-off:** this doc says *"Unable to download content, display
   KJV"* (labelled the owner's exact words, and what shipped); `docs/features/online-translations.md`
   D-OT-2 says *"…, displaying KJV"*. Flagged in a comment beside the string.

   **Also fixed here:** the clipboard citation now names the version actually **served**, not the
   one selected. Those can differ now, and citing "(NKJV)" over degraded KJV text would put a false
   attribution in someone's notes — the silent-swap failure the banner prevents, pasted somewhere
   the banner cannot follow. This was the one mutation that survived first time; it is now pinned.
6. **FUMS + copyright** (D-OT-9, licence obligations). `FumsReporter` currently has only
   `NoOpFumsReporter`; a real implementation is needed, plus copyright displayed wherever non-KJV
   text is shown (`ResolvedVerses.copyright` already carries it).
7. **App Check**, then flip the proxy back to `deny`. See §4.

Then: version bump (MINOR per D-S9-3 → **1.8.0 / 10800**), whatsnew, `tag → alpha → promote`.

---

## 3. Decisions a future session must not undo

- **D-OT-1 — no versification mapping table.** API.Bible's *display* `verseId` matches KJV
  numbering; `verseOrgIds` is Hebrew numbering and must **not** be used. Divergences resolve
  themselves: NASB Matthew 17 yields 26 verses (v21 genuinely absent), NASB 3 John yields 15.
  Measured live, not assumed.
- **D-OT-10 — a section heading is NOT its own row.** `ReaderScreen` keys by `canonicalId`
  (D-V3-12) and verse ids are dense, so a mid-chapter heading has no free id between verse 8 and
  verse 9 — a row of its own is a duplicate key and a Compose crash. It rides on the verse it
  introduces, which also excludes headings from selection/copy by construction.
- **D-OT-3 — KJV stays bundled and offline.** It is the fallback target; the fallback needs
  something that cannot itself fail.
- **A genuinely absent passage ≠ a failed fetch.** `NotFound` returns empty in the *requested*
  version (nothing failed); only `Unavailable` falls back to KJV. Silently showing a KJV verse the
  user's translation does not contain would be a real defect.
- **The cache is an interface on purpose** — caching permission is unconfirmed (§5). A "no" means
  binding `NoOpBibleTextCache`, never a rewrite.

---

## 4. ⚠️ Live security caveat

The proxy is deployed with **`POLICY_ON_ATTESTATION_FAIL=allow`**, so **the endpoint is publicly
reachable right now**. It was shipped `deny` originally; `allow` was necessary because App Check is
not configured and the client has no token to send, so `deny` blocks every fetch.

Mitigations in place: unguessable URL, `ALLOWED_BIBLES` allowlist, and the Firestore budget guard
(140K calls/month, verified recording). **Setting up App Check and returning the policy to `deny` is
the highest-priority follow-up.** One command:

```
gcloud run services update drp-bible-proxy --region us-central1 --project daily-readings-proxy \
  --update-env-vars POLICY_ON_ATTESTATION_FAIL=deny
```

---

## 5. Owner-side, outstanding

- **API.Bible confirmation** (owner is sending): (1) server-side proxy holding the key acceptable?
  (2) on-device caching permitted, retention limit? (3) prefetching adjacent chapters acceptable?
  Owner directed implementation to proceed ahead of the reply.
- **API key rotation** — the key was exposed in session transcripts. One
  `gcloud secrets versions add api-bible-key --data-file=-`; no redeploy needed (service reads
  `:latest`).
- **String tone sign-off** — the banner wording, and version display names.

---

## 6. Deferred by the owner to the next iteration

**Background prefetch of adjoining chapters** (±2/±3). The owner's reasoning is measured and
correct: a multi-chapter span costs **one** API call (`GEN.1-GEN.5` verified), so a prefetch window
is *cheaper* than paging chapter-by-chapter. Gate on question (3) in §5.

---

## 7. Environment gotchas — these will waste an hour otherwise

- **`LANG=C.UTF-8` is required.** Without it Kotlin fails with an opaque *"Internal compiler error"*
  — a pre-existing Sprint B test name contains an em dash that cannot be encoded into a `.class`
  filename under the default locale. Export `LANG`/`LC_ALL`/`LC_CTYPE`.
- **Android SDK** was installed at `/opt/android-sdk`; `local.properties` points at it and is
  gitignored. A fresh container needs it reinstalled.
- **`pkill -f KotlinCompileDaemon` kills its own shell** (its command line contains the pattern) and
  exits 144. Use `pkill -f '[K]otlinCompileDaemon'`.
- **gcloud tokens expire in ~1h.** Any `gcloud` work needs a fresh one from the owner.
- **`--update-env-vars` with a comma in the value needs the delimiter prefix**:
  `--update-env-vars "^|^ALLOWED_BIBLES=NKJV,NASB"`. Getting this wrong stores the literal
  `NKJV^|^NASB` and every translation 400s.
- **Verify deploys against the raw response, not a parsed field.** A check script that prints
  `None` for missing keys reported success while the service was returning 400 to everything.

---

## 8. Useful commands

```bash
export ANDROID_HOME=/opt/android-sdk LANG=C.UTF-8 LC_ALL=C.UTF-8 LC_CTYPE=C.UTF-8
./gradlew testDebugUnitTest                       # 907 tests, 0 failures

# proxy smoke test (should return structured content + fumsToken)
curl -s "https://drp-bible-proxy-954215684233.us-central1.run.app/v1/passage?bible=NASB&ref=GEN.1.1-GEN.1.2"
```

The five data/Room gates must stay untouched: plan **11**, M'Cheyne **10**, Chronological **8**,
`BibleTextVerificationTest` **18**, `BibleDatabaseRoomOpenTest` **5**.

---

## 9. Commits on this branch

| Commit | What |
|---|---|
| `1fefedc` | Proxy deployed to Cloud Run; deployed state recorded |
| `c7da547` | Spec: online translations (NKJV + NASB) |
| `cafb945` | USX → `VerseText` transformer + heading support |
| `1c896b2` | Remote stack, fallback resolver, `INTERNET`; proxy serves NKJV+NASB |
| `1ca15c7` | Make the remote stack pass spotless/ktlint (branch was red) |
| `c9f3c34` | Step 1 — `di/BibleRemoteModule` |
| `157c571` | Steps 2+3 — use cases resolve the selected version; persist it |
| `dce0489` | Steps 4+5 — version selector wired; D-OT-2 banner; served-version citation |

---

## 10. Observation for whoever does step 4 (pre-existing, not introduced here)

`BibleAssetGate`'s KDoc claims its `runBlocking` DataStore read is StrictMode-clean because the
`BibleDatabase` provider "is only ever resolved off the main thread". That does not hold as written:
`ReaderViewModel` injects `GetChapterUseCase` directly (no `Lazy`/`Provider`), so constructing the
ViewModel on the main thread resolves `BibleTextSource` → `RoomBibleTextSource` → `BibleDatabase` →
`assetGate.ensureUpToDate()` → `runBlocking`.

This predates sprint 00R (Sprint E) and the resolver does **not** deepen it — the same graph edge
already existed. Flagged because step 4 touches exactly this constructor, so it is the natural
moment to either wrap the dependency in `Provider`/`Lazy` or correct the comment.

## 11. Known inefficiency (deliberate, not a defect)

`GetPortionTextUseCase` resolves **one range per ref**, so a two-chapter portion costs two API calls
where the spec's §"Why this is smaller than it looks" measured that a whole portion can be fetched
in **one** (`GEN.1-GEN.2`). Correct, just chattier. Collapsing it needs the bridge to yield a single
span for contiguous refs, which is entangled with the deferred prefetch work — worth doing together
with that, not before.
