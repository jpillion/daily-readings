package com.jpillion.dailyreadingplanner.data.reference

/** One of the 66 books: canonical name, canon order, chapter count, and BLB URL abbreviation. */
data class Book(
    val order: Int,
    val canonicalName: String,
    val chapterCount: Int,
    val blbAbbrev: String,
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
            Book(order = 1, canonicalName = "Genesis", chapterCount = 50, blbAbbrev = "gen"),
            Book(order = 2, canonicalName = "Exodus", chapterCount = 40, blbAbbrev = "exo"),
            Book(order = 3, canonicalName = "Leviticus", chapterCount = 27, blbAbbrev = "lev"),
            Book(order = 4, canonicalName = "Numbers", chapterCount = 36, blbAbbrev = "num"),
            Book(order = 5, canonicalName = "Deuteronomy", chapterCount = 34, blbAbbrev = "deu"),
            Book(order = 6, canonicalName = "Joshua", chapterCount = 24, blbAbbrev = "jos"),
            Book(order = 7, canonicalName = "Judges", chapterCount = 21, blbAbbrev = "jdg"),
            Book(order = 8, canonicalName = "Ruth", chapterCount = 4, blbAbbrev = "rth"),
            Book(order = 9, canonicalName = "1 Samuel", chapterCount = 31, blbAbbrev = "1sa"),
            Book(order = 10, canonicalName = "2 Samuel", chapterCount = 24, blbAbbrev = "2sa"),
            Book(order = 11, canonicalName = "1 Kings", chapterCount = 22, blbAbbrev = "1ki"),
            Book(order = 12, canonicalName = "2 Kings", chapterCount = 25, blbAbbrev = "2ki"),
            Book(order = 13, canonicalName = "1 Chronicles", chapterCount = 29, blbAbbrev = "1ch"),
            Book(order = 14, canonicalName = "2 Chronicles", chapterCount = 36, blbAbbrev = "2ch"),
            Book(order = 15, canonicalName = "Ezra", chapterCount = 10, blbAbbrev = "ezr"),
            Book(order = 16, canonicalName = "Nehemiah", chapterCount = 13, blbAbbrev = "neh"),
            Book(order = 17, canonicalName = "Esther", chapterCount = 10, blbAbbrev = "est"),
            Book(order = 18, canonicalName = "Job", chapterCount = 42, blbAbbrev = "job"),
            Book(order = 19, canonicalName = "Psalms", chapterCount = 150, blbAbbrev = "psa"),
            Book(order = 20, canonicalName = "Proverbs", chapterCount = 31, blbAbbrev = "pro"),
            Book(order = 21, canonicalName = "Ecclesiastes", chapterCount = 12, blbAbbrev = "ecc"),
            Book(order = 22, canonicalName = "Song of Solomon", chapterCount = 8, blbAbbrev = "sng"),
            Book(order = 23, canonicalName = "Isaiah", chapterCount = 66, blbAbbrev = "isa"),
            Book(order = 24, canonicalName = "Jeremiah", chapterCount = 52, blbAbbrev = "jer"),
            Book(order = 25, canonicalName = "Lamentations", chapterCount = 5, blbAbbrev = "lam"),
            Book(order = 26, canonicalName = "Ezekiel", chapterCount = 48, blbAbbrev = "eze"),
            Book(order = 27, canonicalName = "Daniel", chapterCount = 12, blbAbbrev = "dan"),
            Book(order = 28, canonicalName = "Hosea", chapterCount = 14, blbAbbrev = "hos"),
            Book(order = 29, canonicalName = "Joel", chapterCount = 3, blbAbbrev = "joe"),
            Book(order = 30, canonicalName = "Amos", chapterCount = 9, blbAbbrev = "amo"),
            Book(order = 31, canonicalName = "Obadiah", chapterCount = 1, blbAbbrev = "oba"),
            Book(order = 32, canonicalName = "Jonah", chapterCount = 4, blbAbbrev = "jon"),
            Book(order = 33, canonicalName = "Micah", chapterCount = 7, blbAbbrev = "mic"),
            Book(order = 34, canonicalName = "Nahum", chapterCount = 3, blbAbbrev = "nah"),
            Book(order = 35, canonicalName = "Habakkuk", chapterCount = 3, blbAbbrev = "hab"),
            Book(order = 36, canonicalName = "Zephaniah", chapterCount = 3, blbAbbrev = "zep"),
            Book(order = 37, canonicalName = "Haggai", chapterCount = 2, blbAbbrev = "hag"),
            Book(order = 38, canonicalName = "Zechariah", chapterCount = 14, blbAbbrev = "zec"),
            Book(order = 39, canonicalName = "Malachi", chapterCount = 4, blbAbbrev = "mal"),
            Book(order = 40, canonicalName = "Matthew", chapterCount = 28, blbAbbrev = "mat"),
            Book(order = 41, canonicalName = "Mark", chapterCount = 16, blbAbbrev = "mar"),
            Book(order = 42, canonicalName = "Luke", chapterCount = 24, blbAbbrev = "luk"),
            Book(order = 43, canonicalName = "John", chapterCount = 21, blbAbbrev = "jhn"),
            Book(order = 44, canonicalName = "Acts", chapterCount = 28, blbAbbrev = "act"),
            Book(order = 45, canonicalName = "Romans", chapterCount = 16, blbAbbrev = "rom"),
            Book(order = 46, canonicalName = "1 Corinthians", chapterCount = 16, blbAbbrev = "1co"),
            Book(order = 47, canonicalName = "2 Corinthians", chapterCount = 13, blbAbbrev = "2co"),
            Book(order = 48, canonicalName = "Galatians", chapterCount = 6, blbAbbrev = "gal"),
            Book(order = 49, canonicalName = "Ephesians", chapterCount = 6, blbAbbrev = "eph"),
            Book(order = 50, canonicalName = "Philippians", chapterCount = 4, blbAbbrev = "phl"),
            Book(order = 51, canonicalName = "Colossians", chapterCount = 4, blbAbbrev = "col"),
            Book(order = 52, canonicalName = "1 Thessalonians", chapterCount = 5, blbAbbrev = "1th"),
            Book(order = 53, canonicalName = "2 Thessalonians", chapterCount = 3, blbAbbrev = "2th"),
            Book(order = 54, canonicalName = "1 Timothy", chapterCount = 6, blbAbbrev = "1ti"),
            Book(order = 55, canonicalName = "2 Timothy", chapterCount = 4, blbAbbrev = "2ti"),
            Book(order = 56, canonicalName = "Titus", chapterCount = 3, blbAbbrev = "tit"),
            Book(order = 57, canonicalName = "Philemon", chapterCount = 1, blbAbbrev = "phm"),
            Book(order = 58, canonicalName = "Hebrews", chapterCount = 13, blbAbbrev = "heb"),
            Book(order = 59, canonicalName = "James", chapterCount = 5, blbAbbrev = "jas"),
            Book(order = 60, canonicalName = "1 Peter", chapterCount = 5, blbAbbrev = "1pe"),
            Book(order = 61, canonicalName = "2 Peter", chapterCount = 3, blbAbbrev = "2pe"),
            Book(order = 62, canonicalName = "1 John", chapterCount = 5, blbAbbrev = "1jo"),
            Book(order = 63, canonicalName = "2 John", chapterCount = 1, blbAbbrev = "2jo"),
            Book(order = 64, canonicalName = "3 John", chapterCount = 1, blbAbbrev = "3jo"),
            Book(order = 65, canonicalName = "Jude", chapterCount = 1, blbAbbrev = "jde"),
            Book(order = 66, canonicalName = "Revelation", chapterCount = 22, blbAbbrev = "rev"),
        )

    private val byName: Map<String, Book> = books.associateBy { it.canonicalName }

    fun findByName(canonicalName: String): Book? = byName[canonicalName]

    fun requireByName(canonicalName: String): Book =
        findByName(canonicalName)
            ?: throw IllegalArgumentException("unknown book '$canonicalName' — not in the 66-book catalog")
}
