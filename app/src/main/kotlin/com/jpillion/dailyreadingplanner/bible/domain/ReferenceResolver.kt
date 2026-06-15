package com.jpillion.dailyreadingplanner.bible.domain

import com.jpillion.dailyreadingplanner.bible.domain.model.VerseId
import com.jpillion.dailyreadingplanner.bible.domain.model.VerseRange
import com.jpillion.dailyreadingplanner.data.reference.Book
import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import javax.inject.Inject

/**
 * VB-T4 / ESpec-v3 §5.3, D-V3-10 — INTERNAL navigation: parse a human/OSIS reference string to a
 * canonical verse_id range. Strictly distinct from `ProviderUrlBuilder` (external egress).
 *
 * CLEAN-FAIL is a release-gated invariant (FR-V3-3): ANY malformed input returns `null` — never a
 * plausible-but-wrong range (the worst failure for a scripture app). Unknown book, out-of-range
 * chapter/verse, reversed range, garbage, and (V3.0) cross-chapter ranges all return `null`.
 *
 * Supported forms (all single-chapter or whole-unit):
 *  - "John"            → whole book (Gospel of John; bare "John" is NEVER a numbered John)
 *  - "John 3"          → whole chapter (incl. verse-0 superscription)
 *  - "John 3:16"       → single verse
 *  - "John 3:16-18"    → same-chapter verse range (start <= end)
 *  - "John.3.16"       → OSIS-style dotted form (also "John.3", "John")
 *  - "1 John 2", "i john 2", "1john 2" → numbered books via the alias table
 *  - "Psalm 23" / "Psalms 23" / "Ps 23" → Psalms
 *
 * Cross-chapter ranges ("John 3:16-4:2") are deliberately NOT supported in V3.0 (no consumer; a
 * verse-count-aware end-of-chapter resolution is out of scope) — they clean-fail to `null`.
 */
class ReferenceResolver
    @Inject
    constructor() {
        /** Parse to a verse_id range, or `null` on ANY malformed input. */
        fun resolve(input: String): VerseRange? {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return null

            // Normalize OSIS dotted form to spaced form: "John.3.16" -> "John 3:16", "John.3" -> "John 3".
            val normalized = normalizeOsis(trimmed)

            // Split the trailing "<chapter>" or "<chapter>:<verses>" numeric tail from the book phrase.
            val match = TAIL.find(normalized)
            val bookPhrase: String
            val chapterPart: String?
            val versePart: String?
            if (match == null) {
                bookPhrase = normalized
                chapterPart = null
                versePart = null
            } else {
                bookPhrase = normalized.substring(0, match.range.first).trim()
                chapterPart = match.groupValues[1]
                versePart = match.groupValues[2].ifEmpty { null }
            }

            val book = lookupBook(bookPhrase) ?: return null

            // Whole book.
            if (chapterPart == null) return VerseId.bookRange(book.order)

            val chapter = chapterPart.toIntOrNull() ?: return null
            if (chapter !in 1..book.chapterCount) return null

            // Whole chapter (incl. verse 0).
            if (versePart == null) return VerseId.chapterRange(book.order, chapter)

            return resolveVersePart(book, chapter, versePart)
        }

        /** Whole chapter incl. verse 0; chapter is validated against the catalog by the caller. */
        fun resolveChapter(
            book: Book,
            chapter: Int,
        ): VerseRange {
            require(chapter in 1..book.chapterCount) {
                "${book.canonicalName} ch $chapter out of range"
            }
            return VerseId.chapterRange(book.order, chapter)
        }

        /** Canonical display string for a single-chapter / whole-unit range. Reserved breadth; V3.0 minimal. */
        fun format(range: VerseRange): String {
            val book = BookCatalog.books.firstOrNull { it.order == VerseId.book(range.startVerseId) }
            val name = book?.canonicalName ?: return ""
            val ch = VerseId.chapter(range.startVerseId)
            val vStart = VerseId.verse(range.startVerseId)
            val vEnd = VerseId.verse(range.endVerseId)
            return when {
                ch == 0 -> name // whole book range
                vStart == 0 && vEnd == 999 -> "$name $ch" // whole chapter
                vStart == vEnd -> "$name $ch:$vStart"
                else -> "$name $ch:$vStart-$vEnd"
            }
        }

        /** "John.3.16" — reserved for V3.x App Links. Minimal V3.0 stub over single verses/chapters. */
        fun formatOsis(range: VerseRange): String {
            val book = BookCatalog.books.firstOrNull { it.order == VerseId.book(range.startVerseId) } ?: return ""
            val ch = VerseId.chapter(range.startVerseId)
            val v = VerseId.verse(range.startVerseId)
            return if (v == 0) "${book.usfmCode}.$ch" else "${book.usfmCode}.$ch.$v"
        }

        private fun resolveVersePart(
            book: Book,
            chapter: Int,
            versePart: String,
        ): VerseRange? {
            val dash = versePart.indexOf('-')
            if (dash < 0) {
                val v = versePart.toIntOrNull() ?: return null
                if (v !in 0..999) return null
                val id = VerseId.encode(book.order, chapter, v)
                return VerseRange(id, id)
            }
            // Same-chapter range only. A second ":" (cross-chapter "16-4:2") clean-fails.
            val startStr = versePart.substring(0, dash)
            val endStr = versePart.substring(dash + 1)
            if (endStr.contains(':')) return null
            val start = startStr.toIntOrNull() ?: return null
            val end = endStr.toIntOrNull() ?: return null
            if (start !in 0..999 || end !in 0..999) return null
            if (start > end) return null
            return VerseRange(
                VerseId.encode(book.order, chapter, start),
                VerseId.encode(book.order, chapter, end),
            )
        }

        private fun normalizeOsis(s: String): String {
            // Only treat as OSIS if there is no space-separated tail already and dots are present.
            if (!s.contains('.')) return s
            val parts = s.split('.')
            return when (parts.size) {
                2 -> "${parts[0]} ${parts[1]}"
                3 -> "${parts[0]} ${parts[1]}:${parts[2]}"
                else -> s
            }
        }

        private fun lookupBook(phrase: String): Book? {
            if (phrase.isEmpty()) return null
            val key = phrase.lowercase().replace(Regex("\\s+"), " ").trim()
            return ALIASES[key]
        }

        companion object {
            // Trailing "<chapter>" optionally ":<verses>" at the very end of the string.
            private val TAIL = Regex("\\s+(\\d+)(?::([0-9-]+))?$")

            /** Lowercased lookup map: canonical names + common aliases/abbreviations. */
            private val ALIASES: Map<String, Book> = buildAliases()

            private fun buildAliases(): Map<String, Book> {
                val m = HashMap<String, Book>()

                fun put(
                    key: String,
                    book: Book,
                ) {
                    m[key.lowercase()] = book
                }
                val byName = BookCatalog.books.associateBy { it.canonicalName }
                // Canonical names (always).
                for (b in BookCatalog.books) {
                    put(b.canonicalName, b)
                    // numbered forms: "1 John" also "1john", "i john", "first john"
                    val numbered = Regex("^(\\d) (.+)$").find(b.canonicalName)
                    if (numbered != null) {
                        val n = numbered.groupValues[1]
                        val rest = numbered.groupValues[2]
                        put("$n$rest", b) // "1john"
                        val roman = mapOf("1" to "i", "2" to "ii", "3" to "iii")[n]
                        if (roman != null) {
                            put("$roman $rest", b) // "i john"
                            put("$roman$rest", b) // "ijohn"
                        }
                    }
                }

                // Hand aliases.
                fun a(
                    name: String,
                    vararg keys: String,
                ) {
                    val b = byName.getValue(name)
                    keys.forEach { put(it, b) }
                }
                a("Genesis", "gen", "gn")
                a("Exodus", "exo", "exod", "ex")
                a("Leviticus", "lev", "lv")
                a("Numbers", "num", "nm", "nb")
                a("Deuteronomy", "deut", "deu", "dt")
                a("Joshua", "josh", "jos")
                a("Judges", "judg", "jdg")
                a("Ruth", "rut", "rth")
                a("1 Samuel", "1 sam", "1sam", "i sam", "i samuel")
                a("2 Samuel", "2 sam", "2sam", "ii sam", "ii samuel")
                a("1 Kings", "1 kgs", "1kgs", "i kgs", "i kings")
                a("2 Kings", "2 kgs", "2kgs", "ii kgs", "ii kings")
                a("1 Chronicles", "1 chron", "1 chr", "1chr", "i chr", "i chronicles")
                a("2 Chronicles", "2 chron", "2 chr", "2chr", "ii chr", "ii chronicles")
                a("Nehemiah", "neh")
                a("Esther", "est", "esth")
                a("Psalms", "psalm", "psa", "ps", "pss")
                a("Proverbs", "prov", "pro", "prv")
                a("Ecclesiastes", "eccl", "ecc", "qoh")
                a("Song of Solomon", "song", "song of songs", "sos", "canticles")
                a("Isaiah", "isa", "is")
                a("Jeremiah", "jer")
                a("Lamentations", "lam")
                a("Ezekiel", "ezek", "eze", "ezk")
                a("Daniel", "dan", "dn")
                a("Hosea", "hos")
                a("Obadiah", "obad", "oba")
                a("Jonah", "jon")
                a("Micah", "mic")
                a("Nahum", "nah", "nam")
                a("Habakkuk", "hab")
                a("Zephaniah", "zeph", "zep")
                a("Haggai", "hag")
                a("Zechariah", "zech", "zec")
                a("Malachi", "mal")
                a("Matthew", "matt", "mat", "mt")
                a("Mark", "mrk", "mk")
                a("Luke", "luk", "lk")
                a("John", "jhn", "jn") // bare John = the Gospel, never a numbered John
                a("Acts", "act")
                a("Romans", "rom")
                a("1 Corinthians", "1 cor", "1cor", "i cor", "i corinthians")
                a("2 Corinthians", "2 cor", "2cor", "ii cor", "ii corinthians")
                a("Galatians", "gal")
                a("Ephesians", "eph")
                a("Philippians", "phil", "php", "phl")
                a("Colossians", "col")
                a("1 Thessalonians", "1 thess", "1 thes", "1thess", "i thess", "i thessalonians")
                a("2 Thessalonians", "2 thess", "2 thes", "2thess", "ii thess", "ii thessalonians")
                a("1 Timothy", "1 tim", "1tim", "i tim", "i timothy")
                a("2 Timothy", "2 tim", "2tim", "ii tim", "ii timothy")
                a("Titus", "tit")
                a("Philemon", "philem", "phm", "phlm")
                a("Hebrews", "heb")
                a("James", "jas", "jms")
                a("1 Peter", "1 pet", "1pet", "i pet", "i peter")
                a("2 Peter", "2 pet", "2pet", "ii pet", "ii peter")
                a("1 John", "1 jn", "1jn", "i jn")
                a("2 John", "2 jn", "2jn", "ii jn")
                a("3 John", "3 jn", "3jn", "iii jn", "iii john", "3john")
                a("Jude", "jud", "jde")
                a("Revelation", "rev", "rv", "apocalypse")
                return m
            }
        }
    }
