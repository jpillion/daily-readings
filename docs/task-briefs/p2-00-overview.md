# Phase 2 — overview, the tranche split, and the rules every `p2-*` brief inherits

> **Staff-owned. Read this before any `p2-*` brief.**
>
> **Phase 2 is module scaffolding and the shared core.** It produces **no shippable iOS binary**.
> **Preconditions: Gate 0 closed; 1.10.0 live on Play with zero related crash signal in vitals.**

---

## The tranche split — read this first, it resolves an apparent contradiction

The signed-off plan describes Phase 1 as "three Android-only Play releases (1.9.0 → 1.10.0 →
1.11.0)" **and** describes Phase 2 as "module scaffolding, the asset move, the shared-core move and
the persistence relocation."

Those are the same work described from two angles. Reconciled:

> **1.11.0 is a release event, not a work package. Its payload is Phase 2, tranche A.**

| | Tranche A | Tranche B |
|---|---|---|
| **Targets** | `androidTarget()` + `jvm()` only | adds `iosArm64`, `iosSimulatorArm64`, `iosX64` |
| **Needs Xcode?** | **No** | **Yes** — blocks on the owner's §7 item 1 |
| **Needs the $99 program?** | No | **No.** Compiling and simulator tests need Xcode only. |
| **Ships?** | **Yes — Play release 1.11.0** | No release |
| **Briefs** | `p2-01` … `p2-08` | `p2-09` (+ Phase 3 onward) |

So `shared/*` are Kotlin Multiplatform modules from day one, with **one platform target** in
tranche A. The KMP plugin, Room KMP's generator and the `commonTest` source set are all exercised
by shipped Android users before an iOS target exists — which is the maximum de-risking available.

**Do not add iOS targets in tranche A "since we're here."** The 1.11.0 release must change exactly
one thing: where the code lives and where two stores live.

---

## Merge order

**Tranche A — the 1.11.0 payload.**

```
p2-01  asset move                     ← FIRST. Atomic. Nothing else moves until the gates
                                          are proven still reading the right files.
p2-02  module scaffolding             ← creates shared/{domain,platform,data,ui}, android+jvm
p2-03  platform seam interfaces       ← STAFF. The contract. Merges with or immediately after p2-02.
        ├─ p2-04  shared/domain move  ┐
        └─ p2-05  shared/data move    ┘ parallel, disjoint write sets
p2-06  Room KMP + ProgressDatabase    ┐ parallel, disjoint — but BOTH are the
p2-07  DataStore relocation           ┘ high-risk payload. Neither merges without its PG gate.
p2-08  plan gates → commonTest
                                          ─── TAG 1.11.0 ───
                                          ─── WATCH VITALS 24–72 h ───
```

**Tranche B — after 1.11.0 is proven live.**

```
p2-09  iOS targets + BundledDatabaseProvider + the two new iOS gates
```

### The `p2-01`-before-`p2-02` tie-break

ADR-0009 and ADR-0011 both claim "first / early" and nothing arbitrates. **This does:** the asset
move goes **before** the module split, because the six data-verification gates must be proven still
reading the right files before anything else moves. If the assets and the modules move together and
a gate goes quiet, you cannot tell which change did it.

---

## Rules every `p2-*` brief inherits

### R1 — Invariant 1 is enforced by CI from `p2-02` onward, not by good intentions

> `shared/domain` and `shared/ui/commonMain` contain **ZERO** `java.*` and **ZERO** `android.*`
> imports. No exceptions.

A grep-based CI check lands in `p2-02` and fails the build. Extended: **`shared/domain` must not
import `okio`, `io.ktor`, `androidx.room`, `androidx.datastore` or `androidx.compose` either**
(ADR-0001's forbidden list). This is why ADR-0014 A1 exists — `ProviderUrlBuilder` gets an in-house
percent-encoder rather than Ktor's.

### R2 — Invariant 2: `if (isIOS)` in shared code is a **build failure**, not a review comment

Platform-conditional behaviour lives at the `expect`/`actual` boundary or behind a
`shared/platform` interface. If you need a platform difference and no seam expresses it, **that is
an escalation to Staff**, not a branch.

### R3 — Staff holds the pen on `shared/platform`

**Implementers may not add, rename or widen an interface there.** If an implementation cannot be
written against the contract, escalate. Contracts that implementers can edit stop being contracts —
they get edited to match whatever was already written.

### R4 — Dependencies point one way, and `shared/ui` may never see `shared/data`

```
shared/domain   ← shared/platform ← shared/data
      ↑                 ↑
      └───── shared/ui ─┘            (ui NEVER depends on data)
```

`shared/ui` being **unable** to import a Room entity is worth more here than the convenience of one
module. Every ViewModel already injects use cases rather than repositories, so no rework is
expected — if one does not, say so rather than adding the dependency.

### R5 — Moves are moves. `git mv`, then fix imports.

A file that changes content in the same commit that changes its path is unreviewable. If a file
genuinely needs a change, **split the commit**.

### R6 — The six gates, unchanged counts, throughout

**11 / 10 / 8 / 6 / 18 / 5.** There are **six**, not five (ADR-0010 A1). Restated in every brief.

### R7 — `fallbackToDestructiveMigration` stays off. `exportSchema` stays on. `2.json` is a tripwire.

If `app/schemas/…/ProgressDatabase/2.json` changes at all — **stop and escalate. Do not regenerate
the baseline.**

### R8 — The 1.11.0 release gets an R8 device smoke, and it gets an extra step

`p1-00` rule R5 applies, **plus**: install 1.10.0 first, use it, then **upgrade in place** to the
1.11.0 build and confirm all history and settings survive. A fresh install proves nothing about a
relocation.

### R9 — Report honestly, including "not proven"

The tranche B briefs will produce results from a **simulator**, in **debug**, on **host arch**,
while the shipped artifact is **device / release / arm64**. **There is no configuration in which
the suite runs against what ships.** Say so; do not let a green simulator run be reported as
verification.

---

## Definition of done for Phase 2

- **Tranche A:** 1.11.0 live on Play from the new module structure, **zero migration-related crash
  signal in vitals after 24–72 h.** No iOS target exists.
- **Tranche B:** `shared/domain` and `shared/data` compile for `iosArm64` and
  `iosSimulatorArm64`; the tier-1 gates are green in `commonTest` on iOS targets; the new
  `BibleDatabaseOpenTest` and `BundleAssetIntegrityTest` are green on a simulator.
