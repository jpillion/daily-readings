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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestinationMode
import com.jpillion.dailyreadingplanner.domain.model.ReadingStatus

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
 */
@Composable
fun DayContent(
    state: DayUiState,
    onToggleReading: (ReadingStatus) -> Unit,
    onReadingTapped: (Portion) -> Unit,
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
                    onToggleReading = onToggleReading,
                    onReadingTapped = onReadingTapped,
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
    onToggleReading: (ReadingStatus) -> Unit,
    onReadingTapped: (Portion) -> Unit,
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
        state.readings.forEach { reading ->
            ReadingCard(
                reading = reading,
                onToggleReading = onToggleReading,
                onReadingTapped = onReadingTapped,
                destinationMode = destinationMode,
                externalApp = externalApp,
            )
        }
    }
}

@Composable
private fun ReadingCard(
    reading: ReadingStatus,
    onToggleReading: (ReadingStatus) -> Unit,
    onReadingTapped: (Portion) -> Unit,
    destinationMode: ReadingDestinationMode,
    externalApp: ExternalBibleApp,
) {
    val portion = reading.portion
    val referenceText = ReadingFormatter.format(portion)
    Card(
        onClick = { onReadingTapped(portion) },
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("reading-${portion.stream.number}"),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (reading.isRead) {
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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ReadingFormatter.streamTitle(portion.stream),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = referenceText,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(readingOpenHintRes(destinationMode, externalApp), referenceText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Checkbox(
                checked = reading.isRead,
                onCheckedChange = { onToggleReading(reading) },
                modifier =
                    Modifier
                        // G-A11Y: keep the toggle a ≥48dp target.
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("toggle-${portion.stream.number}"),
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
 * The reading-tile hint string for the user's effective reading destination (Sprint K, D-23-1):
 * the small supplementary line under each reading reflects where a tap will go, with a natural
 * preposition per destination ("…in this app", "…on Blue Letter Bible", "…in MySword"). `%1$s`
 * is the reference text, e.g. "Genesis 1–2".
 *
 * This is the SINGLE home of the destination → hint mapping (no second enum). The hint follows the
 * stored setting only: in-app mode reads "…in this app"; in external mode, if MYSWORD is selected
 * but not installed, the tap falls back to BLB at tap time, yet the hint still reads "…in MySword"
 * — the hint mirrors the *setting*, not the install-aware tap-time resolution.
 */
@StringRes
internal fun readingOpenHintRes(
    mode: ReadingDestinationMode,
    externalApp: ExternalBibleApp,
): Int =
    when (mode) {
        // In-app mode ignores the remembered external app: the tap reads in the app.
        ReadingDestinationMode.IN_APP -> R.string.reading_open_hint_inapp
        ReadingDestinationMode.EXTERNAL ->
            when (externalApp) {
                ExternalBibleApp.BLB -> R.string.reading_open_hint_blb
                ExternalBibleApp.BIBLE_GATEWAY -> R.string.reading_open_hint_gateway
                ExternalBibleApp.YOUVERSION -> R.string.reading_open_hint_youversion
                ExternalBibleApp.MYSWORD -> R.string.reading_open_hint_mysword
            }
    }

/**
 * Sprint K (reader footer hint) — the in-app reader's footer hint string for the user's chosen
 * external Bible app: "Tap a verse to open it on Blue Letter Bible" / "…on Bible Gateway" /
 * "…on YouVersion" / "…in MySword". `%1$s` is the external app display name
 * ([externalBibleAppNameRes]).
 *
 * This lives next to [readingOpenHintRes] ON PURPOSE: the two hint surfaces (the Schedule day-tile
 * and the reader footer) share ONE home so their per-provider prepositions can never drift. The
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
