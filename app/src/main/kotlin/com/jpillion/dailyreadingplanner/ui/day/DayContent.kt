package com.jpillion.dailyreadingplanner.ui.day

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jpillion.dailyreadingplanner.R
import com.jpillion.dailyreadingplanner.domain.model.ExternalBibleApp
import com.jpillion.dailyreadingplanner.domain.model.ReadingCheckState
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestinationMode

/**
 * Stateless content for one day's readings — directly testable without Hilt or a ViewModel.
 * Sprint 5 (D-S5-1): this is Sprint 4's TodayScreen body, generalized so each pager page
 * renders one [DayUiState]; the caller binds the page's date into the callbacks. The Scaffold
 * and top bar live above the pager in [DayReadingsPagerScreen].
 *
 * H3/H4 (owner): the "Mark whole day as done" button and the "All readings done" badge are
 * removed — the three per-reading checkboxes are the only mark affordance and the only
 * completion cue (owner: the visible checkboxes are enough). [MarkWholeDayUseCase] remains for
 * the widget; only the on-screen button UI is gone.
 *
 * sprint-00P: one card per **segment** (a contiguous passage), not per portion — the day's
 * readings arrive already split as [DayUiState.Scheduled.segments] (the split itself is the
 * ViewModel's job, D-SEG-8).
 */
@Composable
fun DayContent(
    state: DayUiState,
    onToggleSegment: (ReadingSegmentUiState) -> Unit,
    onSegmentTapped: (ReadingSegmentUiState) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    // Sprint K (D-23-1): the per-tile hint reflects the effective destination — the in-app
    // reader when the mode is IN_APP, otherwise the chosen external app. Defaults keep
    // previews/tests that don't care about the hint on the historical BLB-in-browser wording.
    destinationMode: ReadingDestinationMode = ReadingDestinationMode.EXTERNAL,
    externalApp: ExternalBibleApp = ExternalBibleApp.BLB,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (state) {
            is DayUiState.Loading -> LoadingContent()
            is DayUiState.Scheduled ->
                ScheduledContent(
                    state = state,
                    onToggleSegment = onToggleSegment,
                    onSegmentTapped = onSegmentTapped,
                    destinationMode = destinationMode,
                    externalApp = externalApp,
                )
            is DayUiState.NoScheduledReadings -> NoReadingsContent()
            is DayUiState.LoadFailed -> LoadFailedContent(onRetry = onRetry)
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.testTag("loading"))
    }
}

@Composable
private fun ScheduledContent(
    state: DayUiState.Scheduled,
    onToggleSegment: (ReadingSegmentUiState) -> Unit,
    onSegmentTapped: (ReadingSegmentUiState) -> Unit,
    destinationMode: ReadingDestinationMode,
    externalApp: ExternalBibleApp,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // sprint-00P: one card per contiguous passage. A single-segment reading (the overwhelming
        // majority) contributes exactly one card, so the familiar three-card day is unchanged.
        state.segments.forEach { segment ->
            ReadingCard(
                segment = segment,
                onToggleSegment = onToggleSegment,
                onSegmentTapped = onSegmentTapped,
            )
        }
        // One list-level caption replaces the per-card hint (owner: one-screen-fit). Because it
        // is list-level it cannot name a specific reading, so it uses NEW wording (no reference
        // substitution): "Tap a reading to open it …". It still reflects the user's effective
        // destination reactively (in-app vs the chosen external app), via the same single-home
        // preposition mapping the day-tile used (readingListHintRes). Placed BELOW the list so the
        // readings lead and the caption reads as a quiet footnote. One supplementary bodySmall node.
        Text(
            text = stringResource(readingListHintRes(destinationMode, externalApp)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("reading-list-hint"),
        )
    }
}

/**
 * One reading card = one D-SEG-1 segment. The reference text is formatted from the SEGMENT's
 * portion, so a card shows only its own passage ("Isaiah 37–39", "Psalm 76"), and a tap hands that
 * same segment out (D-SEG-6) — what the card says is exactly what opens.
 */
@Composable
private fun ReadingCard(
    segment: ReadingSegmentUiState,
    onToggleSegment: (ReadingSegmentUiState) -> Unit,
    onSegmentTapped: (ReadingSegmentUiState) -> Unit,
) {
    val referenceText = ReadingFormatter.format(segment.portion)
    Card(
        onClick = { onSegmentTapped(segment) },
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("reading-${segment.streamNumber}-${segment.segmentIndex}"),
        colors =
            CardDefaults.cardColors(
                // The completed container colour is reserved for COMPLETE. PARTIAL keeps the
                // UNCHECKED container on purpose: a partially-read reading is NOT done, and the
                // partial-hue tick already carries the distinction (with a spoken state behind it).
                containerColor =
                    if (segment.checkState == ReadingCheckState.COMPLETE) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    // Vertical padding 8→6dp (owner: one-screen-fit). The per-card hint moved to a
                    // single list-level caption, so the card is stream-title + reference + checkbox.
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // D-ALT-22/23: the stream title is plan data, resolved upstream onto the reading.
                // A single-stream plan supplies null — a lone reading needs no "which stream" label.
                // sprint-00P: it repeats on every card of a multi-segment stream (per spec).
                segment.streamTitle?.let { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = referenceText,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            // The tri-state check (sprint-00P). It already owns the ≥48dp target and the spoken
            // stateDescription ("not read" / "partially read" / "read"); the caller supplies only
            // the segment-indexed testTag.
            SegmentCheckbox(
                state = segment.checkState,
                onClick = { onToggleSegment(segment) },
                modifier = Modifier.testTag("toggle-${segment.streamNumber}-${segment.segmentIndex}"),
            )
        }
    }
}

@Composable
private fun NoReadingsContent() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.no_scheduled_readings_feb29),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.padding(top = 8.dp))
        Text(
            text = stringResource(R.string.enjoy_the_rest_day),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadFailedContent(onRetry: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.load_failed_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.padding(top = 8.dp))
        Text(
            text = stringResource(R.string.load_failed_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.padding(top = 16.dp))
        Button(onClick = onRetry, modifier = Modifier.testTag("retry-button")) {
            Text(text = stringResource(R.string.retry))
        }
    }
}

/**
 * The list-level reading hint for the user's effective reading destination: ONE caption below the
 * day's readings reflecting where a tap will go, with a natural preposition per destination
 * ("…in this app", "…on Blue Letter Bible", "…in MySword"). Unlike the retired per-card hint this
 * cannot name a specific reading (it is list-level), so it carries NO `%1$s` reference and uses
 * "Tap a reading to open it …" wording.
 *
 * This is the SINGLE home of the destination → hint mapping (no second enum). The hint follows the
 * stored setting only: in-app mode reads "…in this app"; in external mode, if MYSWORD is selected
 * but not installed, the tap falls back to BLB at tap time, yet the hint still reads "…in MySword"
 * — the hint mirrors the *setting*, not the install-aware tap-time resolution.
 */
@StringRes
internal fun readingListHintRes(
    mode: ReadingDestinationMode,
    externalApp: ExternalBibleApp,
): Int =
    when (mode) {
        // In-app mode ignores the remembered external app: the tap reads in the app.
        ReadingDestinationMode.IN_APP -> R.string.reading_list_hint_inapp
        ReadingDestinationMode.EXTERNAL ->
            when (externalApp) {
                ExternalBibleApp.BLB -> R.string.reading_list_hint_blb
                ExternalBibleApp.BIBLE_GATEWAY -> R.string.reading_list_hint_gateway
                ExternalBibleApp.YOUVERSION -> R.string.reading_list_hint_youversion
                ExternalBibleApp.MYSWORD -> R.string.reading_list_hint_mysword
            }
    }

/**
 * Sprint K (reader footer hint) — the in-app reader's footer hint string for the user's chosen
 * external Bible app: "Tap a verse to copy it or open it on Blue Letter Bible" / "…on Bible
 * Gateway" / "…on YouVersion" / "…in MySword". `%1$s` is the external app display name
 * ([externalBibleAppNameRes]).
 *
 * **sprint-00Q reworded the string VALUES, not this mapping.** A verse tap now opens the verse
 * action menu (Open in `<app>` / Copy this verse / Select verses) rather than launching the app
 * straight away, so the old "Tap a verse to open it on `<app>`" wording had become false. This
 * function stays the single home of the destination → hint mapping (D-K-HINT-1); the per-app
 * preposition split ("on" vs MySword's "in") is unchanged.
 *
 * This lives next to [readingListHintRes] ON PURPOSE: the two hint surfaces (the Schedule list
 * caption and the reader footer) share ONE home so their per-provider prepositions can never drift. The
 * reader hint is the external-app axis ALONE — it reflects the chosen external app *regardless of*
 * the [ReadingDestinationMode], because it is most useful precisely when the user reads IN_APP
 * (the read-here / study-there bridge). There is therefore no in-app branch here.
 */
@StringRes
internal fun readerVerseTapHintRes(externalApp: ExternalBibleApp): Int =
    when (externalApp) {
        ExternalBibleApp.BLB -> R.string.reader_verse_tap_hint_blb
        ExternalBibleApp.BIBLE_GATEWAY -> R.string.reader_verse_tap_hint_gateway
        ExternalBibleApp.YOUVERSION -> R.string.reader_verse_tap_hint_youversion
        ExternalBibleApp.MYSWORD -> R.string.reader_verse_tap_hint_mysword
    }

/**
 * Sprint K — the display name of an [ExternalBibleApp], substituted into [readerVerseTapHintRes]'s
 * `%1$s`. Kept adjacent to the hint mapping so the two `when` branches are reviewed together.
 */
@StringRes
internal fun externalBibleAppNameRes(externalApp: ExternalBibleApp): Int =
    when (externalApp) {
        ExternalBibleApp.BLB -> R.string.external_app_name_blb
        ExternalBibleApp.BIBLE_GATEWAY -> R.string.external_app_name_gateway
        ExternalBibleApp.YOUVERSION -> R.string.external_app_name_youversion
        ExternalBibleApp.MYSWORD -> R.string.external_app_name_mysword
    }
