package com.jpillion.dailyreadingplanner.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpillion.dailyreadingplanner.R
import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.domain.model.ThemeMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/** Stateful entry point for the pushed `settings` route (ESpec §7). */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val fontScale by viewModel.fontScale.collectAsStateWithLifecycle()
    val trackingStartDate by viewModel.trackingStartDate.collectAsStateWithLifecycle()
    SettingsScreen(
        selectedMode = themeMode,
        fontScale = fontScale,
        currentYear = viewModel.currentYear,
        trackingStartDate = trackingStartDate,
        onThemeModeSelected = viewModel::onThemeModeSelected,
        onFontScaleChanged = viewModel::onFontScaleChanged,
        onTrackingStartChanged = viewModel::onTrackingStartChanged,
        onResetProgressConfirmed = viewModel::onResetProgressConfirmed,
        onBack = onBack,
    )
}

/**
 * The Settings screen (FR-9 + S8): Theme radio group, the text-size slider (live preview —
 * the slider writes the same persisted scale the whole app themes from, D-S8-5), and the
 * year-scoped "Reset progress" action behind a confirmation dialog.
 * Stateless apart from dialog visibility — testable without Hilt or a ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    selectedMode: ThemeMode,
    fontScale: Float,
    currentYear: Int,
    trackingStartDate: LocalDate?,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onFontScaleChanged: (Float) -> Unit,
    onTrackingStartChanged: (LocalDate?) -> Unit,
    onResetProgressConfirmed: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var showTrackingStartDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings-back"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle(stringResource(R.string.theme_section_title))
            Column(modifier = Modifier.selectableGroup()) {
                ThemeModeRow(
                    mode = ThemeMode.LIGHT,
                    label = stringResource(R.string.theme_light),
                    testTag = "theme-option-light",
                    selectedMode = selectedMode,
                    onThemeModeSelected = onThemeModeSelected,
                )
                ThemeModeRow(
                    mode = ThemeMode.DARK,
                    label = stringResource(R.string.theme_dark),
                    testTag = "theme-option-dark",
                    selectedMode = selectedMode,
                    onThemeModeSelected = onThemeModeSelected,
                )
                ThemeModeRow(
                    mode = ThemeMode.SYSTEM,
                    label = stringResource(R.string.theme_system),
                    testTag = "theme-option-system",
                    selectedMode = selectedMode,
                    onThemeModeSelected = onThemeModeSelected,
                )
            }

            SectionTitle(stringResource(R.string.text_size_section_title))
            TextSizeSlider(fontScale = fontScale, onFontScaleChanged = onFontScaleChanged)

            SectionTitle(stringResource(R.string.tracking_section_title))
            TrackingStartRow(
                trackingStartDate = trackingStartDate,
                onOpenPicker = { showTrackingStartDialog = true },
                onClear = { onTrackingStartChanged(null) },
            )
            Text(
                text = stringResource(R.string.tracking_start_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
            )

            SectionTitle(stringResource(R.string.progress_section_title))
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .selectable(
                            selected = false,
                            role = Role.Button,
                            onClick = { showResetDialog = true },
                        ).testTag("reset-progress")
                        .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.reset_progress),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(text = stringResource(R.string.reset_dialog_title)) },
            text = { Text(text = stringResource(R.string.reset_dialog_body, currentYear)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        onResetProgressConfirmed()
                    },
                    modifier = Modifier.testTag("reset-confirm"),
                ) { Text(text = stringResource(R.string.reset_dialog_confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetDialog = false },
                    modifier = Modifier.testTag("reset-cancel"),
                ) { Text(text = stringResource(R.string.reset_dialog_cancel)) }
            },
        )
    }

    if (showTrackingStartDialog) {
        TrackingStartDatePickerDialog(
            initialDate = trackingStartDate,
            onConfirm = { picked ->
                showTrackingStartDialog = false
                onTrackingStartChanged(picked)
            },
            onDismiss = { showTrackingStartDialog = false },
        )
    }
}

/**
 * The tracking-start row (S10): label + current value ("Not set" when null) + a Clear
 * affordance when set. Tapping the row opens the full-date picker dialog.
 */
@Composable
private fun TrackingStartRow(
    trackingStartDate: LocalDate?,
    onOpenPicker: () -> Unit,
    onClear: () -> Unit,
) {
    val valueText =
        trackingStartDate?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
            ?: stringResource(R.string.tracking_start_unset)
    val rowDescription = stringResource(R.string.tracking_start_row_description, valueText)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .selectable(
                    selected = false,
                    role = Role.Button,
                    onClick = onOpenPicker,
                ).testTag("tracking-start-row")
                .semantics { contentDescription = rowDescription }
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.tracking_start_title),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("tracking-start-value"),
        )
        if (trackingStartDate != null) {
            val clearDescription = stringResource(R.string.tracking_start_clear_description)
            TextButton(
                onClick = onClear,
                modifier =
                    Modifier
                        .padding(start = 8.dp)
                        .testTag("tracking-start-clear")
                        .semantics { contentDescription = clearDescription },
            ) { Text(text = stringResource(R.string.tracking_start_clear)) }
        }
    }
}

/**
 * Stock M3 full-calendar-date picker (S10): unlike the schedule's pinned-year
 * [DayDatePickerDialog][com.jpillion.dailyreadingplanner.ui.datepicker.DayDatePickerDialog],
 * the tracking start is a real year-qualified date, so year navigation matters here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackingStartDatePickerDialog(
    initialDate: LocalDate?,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state =
        rememberDatePickerState(
            initialSelectedDateMillis =
                (initialDate ?: LocalDate.now())
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli(),
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis ?: return@TextButton
                    onConfirm(
                        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate(),
                    )
                },
                enabled = state.selectedDateMillis != null,
                modifier = Modifier.testTag("tracking-start-confirm"),
            ) { Text(text = stringResource(R.string.tracking_start_dialog_confirm)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("tracking-start-cancel"),
            ) { Text(text = stringResource(R.string.tracking_start_dialog_cancel)) }
        },
        modifier = Modifier.testTag("tracking-start-dialog"),
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun TextSizeSlider(
    fontScale: Float,
    onFontScaleChanged: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = fontScale,
                onValueChange = onFontScaleChanged,
                valueRange = SettingsRepository.MIN_FONT_SCALE..SettingsRepository.MAX_FONT_SCALE,
                // 12 interior stops = 0.05 increments across 0.85..1.50.
                steps = 12,
                modifier = Modifier.weight(1f).testTag("text-size-slider"),
            )
            Text(
                text = stringResource(R.string.text_size_percent, (fontScale * 100).roundToInt()),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 12.dp).testTag("text-size-value"),
            )
        }
        // The whole app rescales live as the slider moves; this line makes the effect
        // obvious right under the user's thumb (S8 owner request).
        Text(
            text = stringResource(R.string.text_size_preview),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp).testTag("text-size-preview"),
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun ThemeModeRow(
    mode: ThemeMode,
    label: String,
    testTag: String,
    selectedMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .selectable(
                    selected = mode == selectedMode,
                    role = Role.RadioButton,
                    onClick = { onThemeModeSelected(mode) },
                ).testTag(testTag)
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = mode == selectedMode,
            onClick = null,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
