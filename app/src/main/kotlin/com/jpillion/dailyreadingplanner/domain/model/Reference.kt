package com.jpillion.dailyreadingplanner.domain.model

import com.jpillion.dailyreadingplanner.data.reference.Book

/**
 * A single chapter reference, with the book already resolved against the catalog — unknown
 * book names fail at plan-load time, never at display or URL-build time.
 */
data class Reference(
    val book: Book,
    val chapter: Int,
) {
    init {
        require(chapter in 1..book.chapterCount) {
            "${book.canonicalName} has ${book.chapterCount} chapters; chapter $chapter out of range"
        }
    }
}
