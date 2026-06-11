package com.jpillion.dailyreadingplanner.data.progress

import androidx.room.Entity

/**
 * One completed reading (ESpec §5.3). Presence of a row means "read"; unmarking deletes it.
 * Keyed by epoch day — the FULL date including year — so 1 Jan 2026 ≠ 1 Jan 2027 (FR-6/U7).
 */
@Entity(
    tableName = "reading_progress",
    primaryKeys = ["dateEpochDay", "stream"],
)
data class ReadingProgressEntity(
    val dateEpochDay: Long,
    val stream: Int,
    val readAtEpochMillis: Long,
)
