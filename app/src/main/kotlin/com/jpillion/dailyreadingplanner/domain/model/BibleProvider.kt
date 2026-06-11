package com.jpillion.dailyreadingplanner.domain.model

/**
 * The KJV destination a reading tap opens (S13, docs/features/bible-app-links.md). Bundled,
 * static definitions — no remote config. [BLB] is the zero-setup default; every shipped
 * provider's 66 books were live-link-verified before landing (spec §3 gate; results in
 * docs/data/provider-link-checks.md). The enum NAME is the persisted id — never rename a
 * constant without a settings migration.
 *
 * URL knowledge deliberately does NOT live here: ProviderUrlBuilder in data/reference is the
 * single home of every provider's URL scheme (risk R2, D-S13-2).
 *
 * [multiRefCapable]: whether one URL can carry the whole portion — the Jun 19 / Dec 19
 * two-book portion and multi-chapter ranges (spec §6). Single-chapter providers open the
 * portion's FIRST reference (the shipped BLB behavior).
 */
enum class BibleProvider(
    val multiRefCapable: Boolean,
) {
    /** Blue Letter Bible — the default since Sprint 1; per-chapter URLs. */
    BLB(multiRefCapable = false),

    /** Bible Gateway — website; native passage search carries ranges and multi-book refs. */
    BIBLE_GATEWAY(multiRefCapable = true),

    /** YouVersion / Bible.com — app-links into the YouVersion app; per-chapter URLs. */
    YOUVERSION(multiRefCapable = false),

    ;

    companion object {
        val DEFAULT: BibleProvider = BLB

        /** Stored-id lookup; unknown/corrupt stored values degrade to the default, never crash. */
        fun fromStored(stored: String?): BibleProvider = entries.firstOrNull { it.name == stored } ?: DEFAULT
    }
}
