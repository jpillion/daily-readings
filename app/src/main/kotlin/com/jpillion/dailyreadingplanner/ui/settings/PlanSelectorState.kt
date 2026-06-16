package com.jpillion.dailyreadingplanner.ui.settings

/**
 * One selectable reading plan in the Settings selector (Alt Sprint D, D-ALT-18): its stable
 * registry [id] and the display [name] read from the plan's own descriptor head (so the registry
 * can never disagree with the plan about its name).
 */
data class PlanOption(
    val id: String,
    val name: String,
)

/**
 * The state of the Settings "Reading plan" selector (D-ALT-18): the [options] to choose among
 * (registry order, Bible Companion first/default) and the currently [activeId]. Empty/blank
 * [activeId] before the first emission — the row simply shows nothing until the active plan
 * resolves, never a wrong name.
 */
data class PlanSelectorUiState(
    val options: List<PlanOption> = emptyList(),
    val activeId: String = "",
) {
    /** The active plan's display name, or empty before the options/active id resolve. */
    val activeName: String get() = options.firstOrNull { it.id == activeId }?.name.orEmpty()
}

/**
 * A pending, user-initiated plan switch awaiting the one-time explanation dialog (D-ALT-19).
 * Carries the [from] (current) and [to] (target) plan display names purely for the dialog copy;
 * the switch is non-destructive by construction (per-plan progress) so NO data operation is
 * implied — confirming only writes `selected_plan`.
 */
data class PendingPlanSwitch(
    val toId: String,
    val fromName: String,
    val toName: String,
)
