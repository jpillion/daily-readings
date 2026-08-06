package com.jpillion.dailyreadingplanner.bible.domain.model

/**
 * The text versions the app can show. KJV is the bundled asset; the rest come from the proxy.
 *
 * Lives in `domain/model`, not `data/remote`: "which version is the user reading" is a domain
 * concept, and [ChapterContent] has to carry the version actually served (D-OT-5) so the D-OT-2
 * banner can be derived. Only the *transport* for the online versions is a data-layer concern.
 *
 * KJV is deliberately first and is the fallback target (D-OT-3): it is the only version guaranteed
 * available with no network, so the planner and reader keep working offline and D-OT-2's banner
 * always has something to fall back TO.
 */
enum class BibleVersion(
    val code: String,
    val displayName: String,
    /** False for the bundled asset; true for anything that needs the network. */
    val requiresNetwork: Boolean,
) {
    KJV("KJV", "King James Version", requiresNetwork = false),
    NKJV("NKJV", "New King James Version", requiresNetwork = true),
    NASB("NASB", "New American Standard Bible 2020", requiresNetwork = true),
    ;

    fun toTranslation(): BibleTranslation = BibleTranslation(code = code, name = displayName)

    companion object {
        val DEFAULT = KJV

        /** Unknown/renamed ids degrade to the bundled default rather than failing (cf. D-S13-4). */
        fun fromCode(code: String?): BibleVersion =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: DEFAULT
    }
}
