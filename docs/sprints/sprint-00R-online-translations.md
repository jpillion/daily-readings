# Sprint 00R — Online translations (NKJV + NASB) — **INCOMPLETE, hand-off**

**Branch:** `claude/available-translations-7zc3pq` (pushed)
**Spec:** [docs/features/online-translations.md](../features/online-translations.md) — read this first; all D-OT-* decisions live there.
**Status:** backend **done and deployed**; app-side engine **done and tested**; **UI wiring NOT done**.
**Version:** untouched (1.7.1 / 10701). Nothing here ships yet.

> **Install this branch today and the app behaves exactly as 1.7.1.** The remote stack exists and is
> tested, but nothing calls it. That is the honest state — no partial UI was left applied.

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

### App — engine only (committed, 907 tests green)

| File | What |
|---|---|
| `bible/data/remote/UsxTransformer.kt` | USX tree → `VerseText` rows. 17 tests. |
| `bible/data/remote/BibleVersions.kt` | `BibleVersion` enum: KJV bundled, NKJV/NASB remote. |
| `bible/data/remote/BibleApiClient.kt` | `HttpURLConnection` client (no new dependency). |
| `bible/data/remote/BibleTextCache.kt` | Interface + file-backed + no-op impls. |
| `bible/data/remote/BibleTextResolver.kt` | The D-OT-2 fallback chain. 8 tests. |
| `bible/domain/model/VerseText.kt` | Gained `heading: String?` (defaults null). |
| `data/reference/BookCatalog.kt` | Gained `findByUsfm()`. |
| `AndroidManifest.xml` | `INTERNET` added, with a comment saying it retires NFR-V3-A. |

---

## 2. What is NOT done — the remaining work

In dependency order. Nothing here is started; no stubs were left behind.

1. **Hilt module** binding `BibleApiClient` → `HttpBibleApiClient` (base URL above),
   `BibleTextCache` → `FileBibleTextCache(context.cacheDir)`, and providing `BibleTextResolver`.
   `HttpBibleApiClient` and `FileBibleTextCache` deliberately have **no `@Inject`** (non-injectable
   `String`/`File`/lambda params) — they must be `@Provides`-constructed.
2. **Use cases → resolver.** `GetChapterUseCase` and `GetPortionTextUseCase` currently inject
   `BibleTextSource`; they should inject `BibleTextResolver` and pass the selected version.
   `ChapterContent` needs to carry the **served** version so the banner can be derived.
3. **Persist the selection.** A `selected_bible_version` key in the DataStore `SettingsRepository`,
   defaulting to `BibleVersion.KJV`.
4. **Wire `ReaderViewModel.selectVersion`** — currently a documented no-op. `versionState` should
   list all three versions (`BibleVersion.entries`), not just the bundled asset's `translation`
   table. **The dropdown UI already exists** (`ReaderVersionSelector`, D-N-3, built Sprint 00N and
   never exercised) — it switches from static title to dropdown automatically at >1 version.
5. **The banner** (owner's exact wording): *"Unable to download content, display KJV"*. Drive it
   off `ResolvedVerses.degraded`. Do **not** reintroduce a shared "last load failed" flag — the
   served-version field exists precisely so a pager rendering several pages cannot banner the
   wrong one.
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
