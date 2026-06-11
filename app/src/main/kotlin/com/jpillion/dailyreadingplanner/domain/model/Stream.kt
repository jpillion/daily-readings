package com.jpillion.dailyreadingplanner.domain.model

/** The Bible Companion's three parallel reading streams (one portion from each per day). */
enum class Stream(
    val number: Int,
) {
    LAW_AND_HISTORY(1),
    PSALMS_AND_PROPHECY(2),
    NEW_TESTAMENT(3),
    ;

    companion object {
        fun fromNumber(number: Int): Stream =
            entries.firstOrNull { it.number == number }
                ?: throw IllegalArgumentException("invalid stream number $number")
    }
}
