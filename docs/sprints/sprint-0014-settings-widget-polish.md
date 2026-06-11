# Sprint 0014 — Settings UI tweaks + widget visual redesign (owner feedback)

**Status: GOAL MET.** Closed 2026-06-11. (Owner-redirected from the planned
`v2-release-prep`, which rolls to Sprint 15. CLI sub-agent dispatch still down on
credentials — EM executed tickets directly under per-ticket verification discipline.
Working tree handed over **uncommitted** by request. A prior interrupted session had left
tickets 1–3's production code in the tree; it was verified against the acceptance criteria
and accepted; all tests, the widget redesign, and docs were authored this session.)

## Goal outcome

**Met.** Settings now presents Theme and "Open readings in" as compact dropdown rows (the
screen lost two 3-row radio groups of vertical space — the owner's ask), the provider menu
teases the V3 in-app reading mode without any way to select it, fresh installs track from
Jan 1 instead of install day, and the widget's layout fills and balances the card at every
size instead of top-stacking small rows into a corner.

## Current capability

- **Settings → Theme / Open readings in:** one 56dp row each showing the current value;
  tapping opens an M3 `DropdownMenu` with a leading check on the current choice. TalkBack
  speaks "Theme, Dark" / "Open readings in, Blue Letter Bible (default)" on the rows and
  selection state on the items. Row tags `theme-dropdown` / `provider-dropdown`; the S6/S13
  option tags carry over onto the menu items.
- **Coming-soon teaser:** the provider menu's last item is "Read in this app (coming
  soon)" — visible, greyed out, `enabled = false` (TalkBack announces disabled), tag
  `provider-option-inapp`. It is **render-layer only**: not a `BibleProvider` constant, so
  no code path can ever persist it (pinned: a tap reports nothing).
- **Tracking start (D-S14-1):** a fresh install defaults to **Jan 1 of the current year**;
  the user can still change or clear it. Existing devices keep whatever they have.
- **Widget (D-S14-2):** each size is a deliberate design — rows distributed evenly down
  the full card, type and padding scaled per tier, wide-short widgets get full references,
  Feb-29/error states centered. All S9 invariants hold: three readings listed at every
  size, read/unread marks, single tap target, spoken canonical names, system theme.
- Verified: **285/285 tests** (net +6; the 7-test Sprint 1 plan gate untouched at 7/7),
  full pipeline green (`spotlessCheck lintDebug assembleDebug testDebugUnitTest
  koverXmlReportAppDebug koverVerifyAppDebug`), **Kover 96.5%** on domain/data (floor 70%).
  **4 mutations killed**, each by exactly its intended test, restored in place:
  (1) LARGE width-only again → tier-chooser + wide-short + size-point-mapping tests fail;
  (2) header no longer height-gated → header-height-decision + wide-short tests fail;
  (3) initializer reverted to install date → Jan-1 fresh-install test fails;
  (4) teaser `enabled = true` → disabled-and-silent test fails.

## Decisions & rationale (do not relitigate)

- **D-S14-1 — Tracking-start default = Jan 1 of the current year** (supersedes the
  default-value part of D-S10-1; the gating logic — marker + zero-marks check, upgraders
  left null, deliberate clears never re-defaulted — is unchanged). **No migration for
  already-initialized devices:** `tracking_start_initialized` records only THAT the
  initializer ran, not whether the user later changed the value, so an auto-set install
  date is indistinguishable from a deliberate user choice of that same date. Rewriting
  would risk clobbering real user intent to fix a cosmetic default; existing devices keep
  their value (the owner changes his manually in Settings). Pinned by
  `already-initialized device keeps its stored install-date value`.
- **D-S14-2 — Widget per-size design system:**
  - **Distribution, not stacking:** the three reading rows each take `defaultWeight()` of
    the height remaining after the (optional) header, content vertically centered per row.
    The card is always filled — the LARGE dead zone cannot exist by construction.
  - **LARGE requires both axes** (≥203dp wide AND ≥102dp tall). Width alone no longer
    qualifies; a wide-short widget is MEDIUM.
  - **Wide-short size points** (`MEDIUM_SHORT_SIZE` 130x48, `WIDE_SHORT_SIZE` 203x48) were
    added to `SizeMode.Responsive`. Root cause of the owner's "3x2-wide shows squeezed
    abbreviated rows": Responsive only picks a declared size that fits BOTH dimensions, so
    a wide-but-short widget failed every 102dp-tall size and fell to TINY. Now it resolves
    to MEDIUM: full references, no date header.
  - **The date header is a height decision** (`showsHeader`): LARGE always; MEDIUM/SMALL
    only at ≥102dp height; TINY never. Short cards spend every dp on the readings; a
    headerless complete day keeps a compact spoken "✓" badge.
  - **Type/inset scale per tier** (`WidgetScale`/`scaleFor`): LARGE 16sp date / 17sp refs /
    12sp stream titles / 16dp padding; MEDIUM 14/15/12dp; SMALL 12/13/10dp; TINY 12sp/8dp.
    LARGE reads from a distance; TINY keeps breathing room.
  - Stream titles remain LARGE-only; abbreviated references (D-S9-1) remain the
    SMALL/TINY form; a11y always speaks canonical names.
- **Dropdown idiom:** shared `SettingsDropdownRow` (row + `DropdownMenu`), not
  `ExposedDropdownMenuBox` — a settings row that opens a menu matches the rest of the
  screen's row language (tracking-start, reminder-time) and avoids a text-field look for
  a non-text control. The teaser lives in the UI layer by design (see above).

## User-visible strings — OWNER TONE SIGN-OFF NEEDED (PRD M8)

New in `app/src/main/res/values/strings.xml` this sprint:

| id | string | where |
|---|---|---|
| `provider_inapp_coming_soon` | "Read in this app (coming soon)" | disabled provider menu item |
| `theme_dropdown_description` | "Theme, %1$s" | spoken only (TalkBack row description) |
| `provider_dropdown_description` | "Open readings in, %1$s" | spoken only (TalkBack row description) |

(S12 and S13 string tables are still awaiting sign-off — see those handoffs.)

## State of the codebase

- **UI:** `ui/settings/SettingsScreen.kt` — `ThemeDropdown`, `ProviderDropdown`,
  `SettingsDropdownRow`, `SelectableMenuItem` replace the radio groups; everything else on
  the screen unchanged.
- **Domain:** `domain/InitializeTrackingStartUseCase.kt` — one-line default change + KDoc.
- **Widget:** `widget/WidgetContent.kt` rewritten around `layoutFor` (both-axes LARGE),
  `showsHeader`, `WidgetScale`/`scaleFor`, one parameterized `Readings` composable with
  weighted rows; `widget/TodayWidget.kt` uses `RESPONSIVE_SIZES` (6 size points);
  `res/drawable/widget_preview.xml` refreshed to the new LARGE look. No manifest, Room,
  DataStore-key, or `today_widget_info.xml` changes.
- **Tests:** `InitializeTrackingStartUseCaseTest` (Jan-1 + no-migration),
  `SettingsScreenTest` (dropdown open/select/close, teaser disabled-and-silent),
  `AccessibilityGateTest` (48dp on dropdown rows AND open menu items incl. the disabled
  teaser), `WidgetContentSizesTest` (tier bounds, header gate, declared-size mapping,
  wide-short render, headerless badge). 285 total, net +6.
- **Version: still 1.1.1 (10101) — MUST be bumped before these changes ship** (next:
  1.2.0/10200 per D-S9-3).
- Nothing committed this sprint (by request); `docs/explorations/` (untracked,
  `social-shared-progress.md`) predates the sprint and was left alone.

## Needs the owner's device pass (genuinely not JVM-provable)

1. **The whole point of D-S14-2:** at each real size on the Pixel 7 Pro launcher — 4x2,
   3x2, 3x1, 2x2, 1x2, 1x1 — the widget should look filled and balanced (no dead zone, no
   corner-crammed rows) with comfortably larger type on bigger cards. Weight distribution
   is invisible to the JVM test rig.
2. Wide-short resize (drag a widget to ~3 wide x 1 tall): full references appear, no date
   header, nothing falls back to the abbreviated TINY look.
3. The two dropdowns open/select correctly under TalkBack; the coming-soon item is
   announced as unavailable.
4. Fresh-install check (new tester): tracking starts Jan 1; picker dot colors sane for
   pre-install completed days (earned green preserved).

## Carryover & next goal

- **Next goal (Sprint 15): V2 release prep** — version bump past 1.1.1/10101, the
  consolidated device pass (S9 checklists + tracking-start + stats + S12 reminders + S13
  provider items + S14 list above), S12/S13/S14 string tone sign-offs, upload key + Play
  listing if still pending, closed-track rollout.
- **Queued/deferred (unchanged from S13):** second-wave web providers; installed-app
  tier-2 providers behind install detection (D-S13-5); toggle-from-widget; Psalm 119
  verse-ranges; API 26–28 scrim check; TIME_SET/TIMEZONE_CHANGED receiver; deprecation
  housekeeping; public requests channel (spec §10.2). New candidate from `docs/
  explorations/social-shared-progress.md` if the owner promotes it.
- **Scope protected out this sprint:** any further Settings reorganization (grouping,
  separate screens), widget configuration options, marking from the widget, retro-fitting
  Jan-1 onto already-initialized devices (deliberate D-S14-1 call, not an omission).

## Next sprint

`next: sprint-0015-v2-release-prep`

## Open questions & risks

- **Owner tone sign-off pending** on the S14 strings above (plus the standing S12 + S13
  tables). In particular: is "Read in this app (coming soon)" the wording he wants?
- **Per-size look is unverified on-device** — the redesign is geometry-by-construction
  plus pinned tier logic, but only the launcher proves the aesthetics. If a size still
  looks off, the per-tier `WidgetScale` values are the single tuning point.
- Responsive size-point math vs real launcher grids remains heuristic (cell ≈57dp+); if
  the P7P still picks an unexpected tier, log `LocalSize` from a debug build to recalibrate
  the declared set.
- Standing debt unchanged: Robolectric pinned `@Config(sdk = [34])`; JVM-untested
  MainActivity hooks; widget ignores in-app font scale (by design, D-S8-5/D-S7-3); CLI
  agent credentials still expired (owner: `claude /login`); CI unexercised on these changes
  until they're committed.
