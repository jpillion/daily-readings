package com.jpillion.dailyreadingplanner.data.progress

import androidx.room.ColumnInfo
import androidx.room.Entity
import com.jpillion.dailyreadingplanner.data.plan.PlanRegistry

/**
 * One completed reading (ESpec §5.3). Presence of a row means "read"; unmarking deletes it.
 * Keyed by epoch day — the FULL date including year — so 1 Jan 2026 ≠ 1 Jan 2027 (FR-6/U7).
 *
 * D-ALT-12 (Alt Sprint B): the row carries a [planId] so each plan keeps its own marks; the PK is
 * `(plan_id, dateEpochDay, stream)`. Existing Bible-Companion marks were stamped [DEFAULT_PLAN_ID]
 * by `MIGRATION_1_2` (zero loss). The `plan_id` column name is explicit (snake_case) so the
 * hand-written migration DDL and the Room-generated DDL agree byte-for-byte.
 */
@Entity(
    tableName = "reading_progress",
    primaryKeys = ["plan_id", "dateEpochDay", "stream"],
)
data class ReadingProgressEntity(
    @ColumnInfo(name = "plan_id")
    val planId: String,
    val dateEpochDay: Long,
    val stream: Int,
    val readAtEpochMillis: Long,
) {
    companion object {
        /**
         * The plan id every existing (pre-Sprint-B) mark belongs to — the flagship default
         * (D-ALT-13). It IS [PlanRegistry.DEFAULT_PLAN_ID]; the migration stamp and the active-plan
         * default both reference this single constant so the literal can never drift.
         */
        const val DEFAULT_PLAN_ID: String = PlanRegistry.DEFAULT_PLAN_ID
    }
}
