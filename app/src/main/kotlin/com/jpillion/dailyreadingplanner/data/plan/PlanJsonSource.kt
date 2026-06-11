package com.jpillion.dailyreadingplanner.data.plan

/**
 * Supplies the raw reading_plan.json text. In production this reads the bundled asset
 * (provided in DataModule); tests substitute the same file from the source tree.
 */
fun interface PlanJsonSource {
    fun readText(): String
}
