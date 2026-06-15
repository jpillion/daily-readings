package com.jpillion.dailyreadingplanner.domain.model

/**
 * The KJV destination a reading tap opens (S13, docs/features/bible-app-links.md). Bundled,
 * static definitions — no remote config. [BLB] is the zero-setup default; every shipped
 * provider's 66 books were verified before landing (spec §3 gate: web providers live-checked,
 * results in docs/data/provider-link-checks.md; MYSWORD resolves only inside the app, so its
 * 66-book gate is the owner's on-device pass — S15 handoff). The enum NAME is the persisted
 * id — never rename a constant without a settings migration.
 *
 * URL knowledge deliberately does NOT live here: ProviderUrlBuilder in data/reference is the
 * single home of every provider's URL scheme (risk R2, D-S13-2).
 *
 * [multiRefCapable]: whether one URL can carry the whole portion — the Jun 19 / Dec 19
 * two-book portion and multi-chapter ranges (spec §6). Single-chapter providers open the
 * portion's FIRST reference (the shipped BLB behavior).
 *
 * [requiresApp]: an installed-app provider (spec §4 product rule): selectable only when its
 * app is detectably installed, and a tap falls back to BLB if the app has gone (D-S15-3) —
 * its URL never degrades gracefully to a browser the way an https template does.
 */
enum class BibleProvider(
    val multiRefCapable: Boolean,
    val requiresApp: Boolean = false,
) {
    /** The in-app KJV reader (V3, D-V3-18): renders the whole portion natively, no app/URL needed. */
    IN_APP(multiRefCapable = true, requiresApp = false),

    /** Blue Letter Bible — the default since Sprint 1; per-chapter URLs. */
    BLB(multiRefCapable = false),

    /** Bible Gateway — website; native passage search carries ranges and multi-book refs. */
    BIBLE_GATEWAY(multiRefCapable = true),

    /** YouVersion / Bible.com — app-links into the YouVersion app; per-chapter URLs. */
    YOUVERSION(multiRefCapable = false),

    /** MySword — explicit-intent app provider (S15, D-S15-1/3); install-detected. */
    MYSWORD(multiRefCapable = false, requiresApp = true),

    ;

    companion object {
        val DEFAULT: BibleProvider = BLB

        /** Stored-id lookup; unknown/corrupt stored values degrade to the default, never crash. */
        fun fromStored(stored: String?): BibleProvider = entries.firstOrNull { it.name == stored } ?: DEFAULT
    }
}
