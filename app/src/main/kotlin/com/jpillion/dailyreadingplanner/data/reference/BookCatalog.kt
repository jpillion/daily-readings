package com.jpillion.dailyreadingplanner.data.reference

/**
 * One of the 66 books: canonical name, canon order, chapter count, BLB URL abbreviation, and
 * the USFM/OSIS book code ([usfmCode], e.g. "GEN", "PHP", "EZK") used by YouVersion/Bible.com
 * URLs (S13). One catalog, N token columns — never N separate tables (D-S9-1/D-S13-1); every
 * token column is live-verified once (docs/data/) and pinned forever in tests.
 */
data class Book(
    val order: Int,
    val canonicalName: String,
    val chapterCount: Int,
    val blbAbbrev: String,
    val usfmCode: String,
) {
    /**
     * Compact display form for width-constrained surfaces (the 1x1/1x2 widget, D-S9-1):
     * the live-verified BLB URL token with its first letter uppercased — "gen" -> "Gen",
     * "2jo" -> "2Jo". Derived, never hand-authored, so it can't drift from the catalog.
     */
    val displayAbbrev: String
        get() {
            val i = blbAbbrev.indexOfFirst { it.isLetter() }
            return blbAbbrev.substring(0, i) +
                blbAbbrev[i].uppercaseChar() +
                blbAbbrev.substring(i + 1)
        }
}

/**
 * Production source of truth for the 66-book canon (ESpec §5.2). Supersedes the Sprint 1
 * book_catalog.csv, which remains as a test fixture; BookCatalogTest reconciles the two
 * field-by-field so they can never drift. All abbreviations were live-verified against
 * blueletterbible.org in Sprint 1 (docs/data/README.md).
 */
object BookCatalog {
    val books: List<Book> =
        listOf(
            Book(order = 1, canonicalName = "Genesis", chapterCount = 50, blbAbbrev = "gen", usfmCode = "GEN"),
            Book(order = 2, canonicalName = "Exodus", chapterCount = 40, blbAbbrev = "exo", usfmCode = "EXO"),
            Book(order = 3, canonicalName = "Leviticus", chapterCount = 27, blbAbbrev = "lev", usfmCode = "LEV"),
            Book(order = 4, canonicalName = "Numbers", chapterCount = 36, blbAbbrev = "num", usfmCode = "NUM"),
            Book(order = 5, canonicalName = "Deuteronomy", chapterCount = 34, blbAbbrev = "deu", usfmCode = "DEU"),
            Book(order = 6, canonicalName = "Joshua", chapterCount = 24, blbAbbrev = "jos", usfmCode = "JOS"),
            Book(order = 7, canonicalName = "Judges", chapterCount = 21, blbAbbrev = "jdg", usfmCode = "JDG"),
            Book(order = 8, canonicalName = "Ruth", chapterCount = 4, blbAbbrev = "rth", usfmCode = "RUT"),
            Book(order = 9, canonicalName = "1 Samuel", chapterCount = 31, blbAbbrev = "1sa", usfmCode = "1SA"),
            Book(order = 10, canonicalName = "2 Samuel", chapterCount = 24, blbAbbrev = "2sa", usfmCode = "2SA"),
            Book(order = 11, canonicalName = "1 Kings", chapterCount = 22, blbAbbrev = "1ki", usfmCode = "1KI"),
            Book(order = 12, canonicalName = "2 Kings", chapterCount = 25, blbAbbrev = "2ki", usfmCode = "2KI"),
            Book(order = 13, canonicalName = "1 Chronicles", chapterCount = 29, blbAbbrev = "1ch", usfmCode = "1CH"),
            Book(order = 14, canonicalName = "2 Chronicles", chapterCount = 36, blbAbbrev = "2ch", usfmCode = "2CH"),
            Book(order = 15, canonicalName = "Ezra", chapterCount = 10, blbAbbrev = "ezr", usfmCode = "EZR"),
            Book(order = 16, canonicalName = "Nehemiah", chapterCount = 13, blbAbbrev = "neh", usfmCode = "NEH"),
            Book(order = 17, canonicalName = "Esther", chapterCount = 10, blbAbbrev = "est", usfmCode = "EST"),
            Book(order = 18, canonicalName = "Job", chapterCount = 42, blbAbbrev = "job", usfmCode = "JOB"),
            Book(order = 19, canonicalName = "Psalms", chapterCount = 150, blbAbbrev = "psa", usfmCode = "PSA"),
            Book(order = 20, canonicalName = "Proverbs", chapterCount = 31, blbAbbrev = "pro", usfmCode = "PRO"),
            Book(order = 21, canonicalName = "Ecclesiastes", chapterCount = 12, blbAbbrev = "ecc", usfmCode = "ECC"),
            Book(order = 22, canonicalName = "Song of Solomon", chapterCount = 8, blbAbbrev = "sng", usfmCode = "SNG"),
            Book(order = 23, canonicalName = "Isaiah", chapterCount = 66, blbAbbrev = "isa", usfmCode = "ISA"),
            Book(order = 24, canonicalName = "Jeremiah", chapterCount = 52, blbAbbrev = "jer", usfmCode = "JER"),
            Book(order = 25, canonicalName = "Lamentations", chapterCount = 5, blbAbbrev = "lam", usfmCode = "LAM"),
            Book(order = 26, canonicalName = "Ezekiel", chapterCount = 48, blbAbbrev = "eze", usfmCode = "EZK"),
            Book(order = 27, canonicalName = "Daniel", chapterCount = 12, blbAbbrev = "dan", usfmCode = "DAN"),
            Book(order = 28, canonicalName = "Hosea", chapterCount = 14, blbAbbrev = "hos", usfmCode = "HOS"),
            Book(order = 29, canonicalName = "Joel", chapterCount = 3, blbAbbrev = "joe", usfmCode = "JOL"),
            Book(order = 30, canonicalName = "Amos", chapterCount = 9, blbAbbrev = "amo", usfmCode = "AMO"),
            Book(order = 31, canonicalName = "Obadiah", chapterCount = 1, blbAbbrev = "oba", usfmCode = "OBA"),
            Book(order = 32, canonicalName = "Jonah", chapterCount = 4, blbAbbrev = "jon", usfmCode = "JON"),
            Book(order = 33, canonicalName = "Micah", chapterCount = 7, blbAbbrev = "mic", usfmCode = "MIC"),
            Book(order = 34, canonicalName = "Nahum", chapterCount = 3, blbAbbrev = "nah", usfmCode = "NAM"),
            Book(order = 35, canonicalName = "Habakkuk", chapterCount = 3, blbAbbrev = "hab", usfmCode = "HAB"),
            Book(order = 36, canonicalName = "Zephaniah", chapterCount = 3, blbAbbrev = "zep", usfmCode = "ZEP"),
            Book(order = 37, canonicalName = "Haggai", chapterCount = 2, blbAbbrev = "hag", usfmCode = "HAG"),
            Book(order = 38, canonicalName = "Zechariah", chapterCount = 14, blbAbbrev = "zec", usfmCode = "ZEC"),
            Book(order = 39, canonicalName = "Malachi", chapterCount = 4, blbAbbrev = "mal", usfmCode = "MAL"),
            Book(order = 40, canonicalName = "Matthew", chapterCount = 28, blbAbbrev = "mat", usfmCode = "MAT"),
            Book(order = 41, canonicalName = "Mark", chapterCount = 16, blbAbbrev = "mar", usfmCode = "MRK"),
            Book(order = 42, canonicalName = "Luke", chapterCount = 24, blbAbbrev = "luk", usfmCode = "LUK"),
            Book(order = 43, canonicalName = "John", chapterCount = 21, blbAbbrev = "jhn", usfmCode = "JHN"),
            Book(order = 44, canonicalName = "Acts", chapterCount = 28, blbAbbrev = "act", usfmCode = "ACT"),
            Book(order = 45, canonicalName = "Romans", chapterCount = 16, blbAbbrev = "rom", usfmCode = "ROM"),
            Book(order = 46, canonicalName = "1 Corinthians", chapterCount = 16, blbAbbrev = "1co", usfmCode = "1CO"),
            Book(order = 47, canonicalName = "2 Corinthians", chapterCount = 13, blbAbbrev = "2co", usfmCode = "2CO"),
            Book(order = 48, canonicalName = "Galatians", chapterCount = 6, blbAbbrev = "gal", usfmCode = "GAL"),
            Book(order = 49, canonicalName = "Ephesians", chapterCount = 6, blbAbbrev = "eph", usfmCode = "EPH"),
            Book(order = 50, canonicalName = "Philippians", chapterCount = 4, blbAbbrev = "phl", usfmCode = "PHP"),
            Book(order = 51, canonicalName = "Colossians", chapterCount = 4, blbAbbrev = "col", usfmCode = "COL"),
            Book(order = 52, canonicalName = "1 Thessalonians", chapterCount = 5, blbAbbrev = "1th", usfmCode = "1TH"),
            Book(order = 53, canonicalName = "2 Thessalonians", chapterCount = 3, blbAbbrev = "2th", usfmCode = "2TH"),
            Book(order = 54, canonicalName = "1 Timothy", chapterCount = 6, blbAbbrev = "1ti", usfmCode = "1TI"),
            Book(order = 55, canonicalName = "2 Timothy", chapterCount = 4, blbAbbrev = "2ti", usfmCode = "2TI"),
            Book(order = 56, canonicalName = "Titus", chapterCount = 3, blbAbbrev = "tit", usfmCode = "TIT"),
            Book(order = 57, canonicalName = "Philemon", chapterCount = 1, blbAbbrev = "phm", usfmCode = "PHM"),
            Book(order = 58, canonicalName = "Hebrews", chapterCount = 13, blbAbbrev = "heb", usfmCode = "HEB"),
            Book(order = 59, canonicalName = "James", chapterCount = 5, blbAbbrev = "jas", usfmCode = "JAS"),
            Book(order = 60, canonicalName = "1 Peter", chapterCount = 5, blbAbbrev = "1pe", usfmCode = "1PE"),
            Book(order = 61, canonicalName = "2 Peter", chapterCount = 3, blbAbbrev = "2pe", usfmCode = "2PE"),
            Book(order = 62, canonicalName = "1 John", chapterCount = 5, blbAbbrev = "1jo", usfmCode = "1JN"),
            Book(order = 63, canonicalName = "2 John", chapterCount = 1, blbAbbrev = "2jo", usfmCode = "2JN"),
            Book(order = 64, canonicalName = "3 John", chapterCount = 1, blbAbbrev = "3jo", usfmCode = "3JN"),
            Book(order = 65, canonicalName = "Jude", chapterCount = 1, blbAbbrev = "jde", usfmCode = "JUD"),
            Book(order = 66, canonicalName = "Revelation", chapterCount = 22, blbAbbrev = "rev", usfmCode = "REV"),
        )

    private val byName: Map<String, Book> = books.associateBy { it.canonicalName }

    private val byUsfm: Map<String, Book> = books.associateBy { it.usfmCode }

    fun findByName(canonicalName: String): Book? = byName[canonicalName]

    /**
     * USFM/OSIS code ("PSA", "3JN") → book. Case-insensitive. Used by the remote text source to
     * resolve API.Bible verse ids onto catalog book order (D-OT-6) — the SAME [Book.usfmCode]
     * column the external-link builders use (D-S13-1), never a second table.
     */
    fun findByUsfm(usfmCode: String): Book? = byUsfm[usfmCode.uppercase()]

    fun requireByName(canonicalName: String): Book =
        findByName(canonicalName)
            ?: throw IllegalArgumentException("unknown book '$canonicalName' — not in the 66-book catalog")
}
