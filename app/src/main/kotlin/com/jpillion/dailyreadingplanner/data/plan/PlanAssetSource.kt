package com.jpillion.dailyreadingplanner.data.plan

/**
 * Supplies the raw text of a plan asset by its bundled asset path (ESpec-alt §3.5, D-ALT-1).
 * Replaces the single-asset `PlanJsonSource`: now path-parameterized so the one provider reads
 * `assets/plans/<id>/plan.json` (and `assets/plans/registry.json`). In production this reads the
 * bundled asset (provided in DataModule); tests substitute file text from the source tree.
 */
fun interface PlanAssetSource {
    fun readText(assetPath: String): String
}
