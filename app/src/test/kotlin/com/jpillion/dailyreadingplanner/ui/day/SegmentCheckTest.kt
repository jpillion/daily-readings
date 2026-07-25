package com.jpillion.dailyreadingplanner.ui.day

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.domain.model.ReadingCheckState
import com.jpillion.dailyreadingplanner.ui.theme.DailyReadingPlannerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for the reusable tri-state reading check (sprint-00P), run under Robolectric so
 * they execute in the standard testDebugUnitTest pipeline (D-S4-1).
 *
 * What these pin — deliberately, the JVM-provable half:
 * - each state speaks the right `stateDescription` (LITERAL strings, never computed from the enum,
 *   so a reworded or mismapped state reddens exactly one pin);
 * - all three states keep the ≥48dp touch target;
 * - every state is clickable, INCLUDING COMPLETE (a completed reading must stay un-checkable);
 * - PARTIAL is distinguishable from COMPLETE *to a test*, not just to the eye — a different spoken
 *   state, and PARTIAL still reports a checked `ToggleableState.On` (a tick: not an unchecked box,
 *   and not an M3 indeterminate dash).
 *
 * What they CANNOT pin: colour identity. Semantics carry no pixels, so "the partial tick is a
 * different colour from the completed one" is NOT JVM-provable — pointing the partial colour at the
 * completed colour leaves every test green (verified by mutation). That is exactly why the spoken
 * state above is load-bearing, and why the hue itself is a device-pass item.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SegmentCheckTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val tag = "segment-check"

    private val state = mutableStateOf(ReadingCheckState.UNCHECKED)
    private var clicks = 0
    private var contentSet = false

    /**
     * Shows the control in [value]. The state is hoisted into a snapshot state so a single
     * `setContent` (the rule allows only one) can serve the loop-over-all-states tests.
     */
    private fun show(value: ReadingCheckState) {
        if (!contentSet) {
            contentSet = true
            composeRule.setContent {
                DailyReadingPlannerTheme(dynamicColor = false) {
                    SegmentCheckbox(
                        state = state.value,
                        onClick = { clicks++ },
                        // The caller owns the tag (segment-indexed at the real call site); the
                        // control hardcodes none, so it can only arrive through the modifier.
                        modifier = Modifier.testTag(tag),
                    )
                }
            }
        }
        composeRule.runOnUiThread { state.value = value }
        composeRule.waitForIdle()
    }

    /** Asserts the node's *touch* bounds (incl. minimum-target expansion) are at least [min] square. */
    private fun SemanticsNodeInteraction.assertTouchTargetAtLeast(min: Dp): SemanticsNodeInteraction {
        val node = fetchSemanticsNode("failed to check touch target")
        val bounds = node.touchBoundsInRoot
        with(node.layoutInfo.density) {
            assertThat(bounds.width.toDp().value).isAtLeast(min.value - 0.5f)
            assertThat(bounds.height.toDp().value).isAtLeast(min.value - 0.5f)
        }
        return this
    }

    private fun hasStateDescription(expected: String): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expected)

    private fun hasToggleableState(expected: ToggleableState): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, expected)

    private fun spokenState(): String? =
        composeRule
            .onNodeWithTag(tag)
            .fetchSemanticsNode("no check node")
            .config
            .getOrNull(SemanticsProperties.StateDescription)

    // --- spoken state: never colour alone (literal strings) ---

    @Test
    fun unchecked_speaksNotRead() {
        show(ReadingCheckState.UNCHECKED)
        composeRule.onNodeWithTag(tag).assertIsDisplayed().assert(hasStateDescription("not read"))
    }

    @Test
    fun partial_speaksPartiallyRead() {
        // MUTATION-PIN: the PARTIAL -> "partially read" mapping. Pointing PARTIAL at any other
        // string (e.g. "read") reddens exactly this test.
        show(ReadingCheckState.PARTIAL)
        composeRule.onNodeWithTag(tag).assertIsDisplayed().assert(hasStateDescription("partially read"))
    }

    @Test
    fun complete_speaksRead() {
        show(ReadingCheckState.COMPLETE)
        composeRule.onNodeWithTag(tag).assertIsDisplayed().assert(hasStateDescription("read"))
    }

    // --- 48dp touch targets, every state ---

    @Test
    fun everyState_meets48dpTouchTarget() {
        for (value in ReadingCheckState.entries) {
            show(value)
            composeRule.onNodeWithTag(tag).assertTouchTargetAtLeast(48.dp)
        }
    }

    // --- clickable in every state, including COMPLETE ---

    @Test
    fun uncheckedClick_invokesOnClick() {
        show(ReadingCheckState.UNCHECKED)
        composeRule.onNodeWithTag(tag).performClick()
        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun partialClick_invokesOnClick() {
        show(ReadingCheckState.PARTIAL)
        composeRule.onNodeWithTag(tag).performClick()
        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun completeClick_invokesOnClick_soACompletedReadingStaysUncheckable() {
        // A COMPLETE check is NOT disabled: tapping it is how the user un-marks the reading (the
        // policy upstream decides what that means). A regression that disables the control when
        // complete, or drops the callback, reddens here.
        show(ReadingCheckState.COMPLETE)
        composeRule.onNodeWithTag(tag).performClick()
        assertThat(clicks).isEqualTo(1)
    }

    // --- PARTIAL vs COMPLETE: distinguishable to a test, not only to the eye ---

    @Test
    fun partial_isACheckedTick_notUncheckedAndNotIndeterminate() {
        // The spec's shape requirement: PARTIAL is a *checked* checkbox in a distinct colour — a
        // tick, deliberately NOT an M3 indeterminate dash. Semantics is where that is provable.
        show(ReadingCheckState.PARTIAL)
        composeRule.onNodeWithTag(tag).assert(hasToggleableState(ToggleableState.On))
    }

    @Test
    fun unchecked_reportsOffAndCompleteReportsOn() {
        show(ReadingCheckState.UNCHECKED)
        composeRule.onNodeWithTag(tag).assert(hasToggleableState(ToggleableState.Off))
        show(ReadingCheckState.COMPLETE)
        composeRule.onNodeWithTag(tag).assert(hasToggleableState(ToggleableState.On))
    }

    @Test
    fun partialAndComplete_shareTheCheckedShapeButNotTheSpokenState() {
        // The pair that matters for TalkBack: both are checked ticks, so ToggleableState alone
        // cannot tell them apart — the spoken state MUST. Collapsing the two descriptions onto one
        // string (the a11y regression this control exists to prevent) reddens here.
        show(ReadingCheckState.PARTIAL)
        composeRule.onNodeWithTag(tag).assert(hasToggleableState(ToggleableState.On))
        val partialSpoken = spokenState()
        show(ReadingCheckState.COMPLETE)
        composeRule.onNodeWithTag(tag).assert(hasToggleableState(ToggleableState.On))
        val completeSpoken = spokenState()
        assertThat(partialSpoken).isEqualTo("partially read")
        assertThat(completeSpoken).isEqualTo("read")
        assertThat(partialSpoken).isNotEqualTo(completeSpoken)
    }
}
