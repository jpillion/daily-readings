# Architecture Decision Records — iOS port

Staff-locked. One decision per file. Each ADR states **the decision**, **the alternatives
rejected and why**, **the consequences accepted**, and **when it should be revisited**.

All ADRs below are **DRAFT — pending owner sign-off** unless marked otherwise. Draft means the
recommendation is made and defended; it does not mean work may proceed against it. Anything
marked `⟦VERIFY⟧` in [../port-inventory.md](../port-inventory.md) must be closed before the
dependent ADR is accepted.

| ADR | Title | Status | Blocks |
|---|---|---|---|
| [0001](0001-module-topology.md) | Module topology and the `shared/platform` boundary | Draft | everything |
| [0002](0002-skie-vs-swift-export.md) | SKIE vs Swift Export | Draft | iOS Platform work |
| [0003](0003-navigation.md) | Navigation: shared Compose vs native iOS shell | Draft | Phase 3 UI |
| [0004](0004-reader-screen.md) | The reader screen: Compose vs native | Draft — **gated on the Gate 0 gesture rig** | Phase 3 UI |
| [0005](0005-notifications.md) | Notification and scheduling strategy on iOS | **ACCEPTED** + Amendment A1 | Phase 4 |
| [0006](0006-widgets.md) | Android widget, iOS widget, and the persistent notification | **ACCEPTED** + Amendment A1 | scope; **owner's bundle-ID step** |
| [0007](0007-bible-db-and-room-identity-hash.md) | Room, the pre-packaged `bible.db`, and the identity hash | Draft — **gated on Gate 0 V1** · Amendments A1, A2 | all bible work |
| [0008](0008-progress-migration-integrity.md) | `ProgressDatabase` migration integrity for shipped users | Draft — **gated on Gate 0 V2** | Phase 2 persistence |
| [0009](0009-datetime.md) | `java.time` → `kotlinx-datetime`, and `YearMonth` | Draft — minor spikes open | Phase 1 |
| [0010](0010-data-verification-gates.md) | The **six** data-verification gates in a multiplatform suite | Draft + Amendment A1 | CI, Phase 2 |
| [0011](0011-shared-assets.md) | Shared assets — exactly one copy in git | Draft — minor spike open | Phase 2 |
| [0012](0012-dependency-injection.md) | Dependency injection after Hilt | Draft | Phase 1 (1.10.0) |
| [0013](0013-strings-and-resources.md) | Strings and resources | Draft | Phase 3 UI |
| [0014](0014-http-client.md) | HTTP client for online translations | Draft + Amendment A1 | Phase 1 (1.9.0) |

## Amendments — 2026-08-08, after the six-specialist synthesis

The EM's `../ios-port-approach.md` was signed off by the owner. It resolved four contradictions
between reviewers and added a program-level stop rule (D-PORT-1). Five ADRs were amended as a
result. **Amendments are appended, not merged into the body**, so a reversal stays visible:

| ADR | Amendment | Substance |
|---|---|---|
| 0005 | A1 | The iOS reminder body is **generic** (owner decision). Option (c), the hybrid, is **impossible** — a repeating `UNCalendarNotificationTrigger` has no start date, so it fires *alongside* every dated one-shot. Marked **INFERRED**; a Phase 4 spike must confirm it. |
| 0006 | A1 | No widget in v1.0 stands, **but the App Group is reserved from the first build** (D-PORT-4). This changes the owner's very next Apple action — `RELEASING-IOS.md` Step 2 must enable App Groups at bundle-ID registration. |
| 0007 | A1 | `exportSchema = true` on `BibleDatabase` — recorded as **superseding the sprint-00F decision**, not as a new idea. |
| 0007 | A2 | The Android bible-DB open path does **not** change in the 1.11.0 release; `BundledDatabaseProvider` lands with the iOS targets. |
| 0010 | A1 | **Six gates, not five.** The Tier 1 justification is corrected: the plan gates read the source tree, so running them on iOS proves **nothing** about bundle packaging. Adds the required `BundleAssetIntegrityTest` and the D-PORT-6 reporting ledger. |
| 0014 | A1 | `ProviderUrlBuilder` **cannot** use Ktor's encoder — ADR-0001 forbids Ktor in `shared/domain`. It gets an in-house percent-encoder that must reproduce today's bytes, `+`-for-space included. |

## Reading order for someone new

`../ios-port-approach.md` (the signed-off program) → 0001 → 0009 → 0007 → 0008 → 0012 → 0003 →
0004 → 0005 → 0006. The rest are supporting. **Read every ADR's amendments before its body** —
where they disagree, the amendment wins.

## Related

- [../port-inventory.md](../port-inventory.md) — the Phase 0 inventory these decisions rest on.
- [../ios-port-approach.md](../ios-port-approach.md) — **the signed-off program.** Where it and an
  un-amended ADR disagree, it wins.
- [../RELEASING-IOS.md](../RELEASING-IOS.md) — owner critical path and delivery model.
- [../task-briefs/](../task-briefs/) — the executable briefs. `gate0-*` run first and may contain
  open questions; `p1-*` and `p2-*` may not.
- `docs/parity-matrix.md` — Verification owns it. ADR-0005 A1, ADR-0006 A1, ADR-0004, ADR-0013 and
  several inventory items all require entries in it.
