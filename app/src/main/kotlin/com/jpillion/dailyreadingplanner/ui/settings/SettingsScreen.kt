package com.jpillion.dailyreadingplanner.ui.settings

import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import java.time.LocalTime
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
    val reminderEnabled by viewModel.reminderEnabled.collectAsStateWithLifecycle()
    val reminderTime by viewModel.reminderTime.collectAsStateWithLifecycle()
    val showPermissionRationale by viewModel.showPermissionRationale.collectAsStateWithLifecycle()

    // R-REM-7: the system POST_NOTIFICATIONS prompt, launched only when the user flips the
    // toggle on without permission (the ViewModel emits the one-shot request event).
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onNotificationPermissionResult(granted)
        }
    LaunchedEffect(viewModel) {
        viewModel.permissionRequests.collect {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val context = LocalContext.current
    SettingsScreen(
        selectedMode = themeMode,
        fontScale = fontScale,
        currentYear = viewModel.currentYear,
        trackingStartDate = trackingStartDate,
        reminderEnabled = reminderEnabled,
        reminderTime = reminderTime,
        showReminderPermissionRationale = showPermissionRationale,
        onThemeModeSelected = viewModel::onThemeModeSelected,
        onFontScaleChanged = viewModel::onFontScaleChanged,
        onTrackingStartChanged = viewModel::onTrackingStartChanged,
        onReminderToggled = viewModel::onReminderToggled,
        onReminderTimeChanged = viewModel::onReminderTimeChanged,
        onPermissionRationaleDismissed = viewModel::onPermissionRationaleDismissed,
        onOpenNotificationSettings = {
            viewModel.onPermissionRationaleDismissed()
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        },
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
    reminderEnabled: Boolean,
    reminderTime: LocalTime,
    showReminderPermissionRationale: Boolean,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onFontScaleChanged: (Float) -> Unit,
    onTrackingStartChanged: (LocalDate?) -> Unit,
    onReminderToggled: (Boolean) -> Unit,
    onReminderTimeChanged: (LocalTime) -> Unit,
    onPermissionRationaleDismissed: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onResetProgressConfirmed: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var showTrackingStartDialog by rememberSaveable { mutableStateOf(false) }
    var showReminderTimeDialog by rememberSaveable { mutableStateOf(false) }

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

            SectionTitle(stringResource(R.string.reminders_section_title))
            ReminderToggleRow(
                reminderEnabled = reminderEnabled,
                onReminderToggled = onReminderToggled,
            )
            if (reminderEnabled) {
                ReminderTimeRow(
                    reminderTime = reminderTime,
                    onOpenPicker = { showReminderTimeDialog = true },
                )
            }
            Text(
                text = stringResource(R.string.reminder_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
            )

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

    if (showReminderTimeDialog) {
        ReminderTimePickerDialog(
            initialTime = reminderTime,
            onConfirm = { picked ->
                showReminderTimeDialog = false
                onReminderTimeChanged(picked)
            },
            onDismiss = { showReminderTimeDialog = false },
        )
    }

    if (showReminderPermissionRationale) {
        // R-REM-7: a denial leaves the toggle off and explains why, with the system-settings
        // path — never a silent pretend-on, never an unprompted re-ask.
        AlertDialog(
            onDismissRequest = onPermissionRationaleDismissed,
            title = { Text(text = stringResource(R.string.reminder_permission_title)) },
            text = { Text(text = stringResource(R.string.reminder_permission_body)) },
            confirmButton = {
                TextButton(
                    onClick = onOpenNotificationSettings,
                    modifier = Modifier.testTag("reminder-permission-settings"),
                ) { Text(text = stringResource(R.string.reminder_permission_settings)) }
            },
            dismissButton = {
                TextButton(
                    onClick = onPermissionRationaleDismissed,
                    modifier = Modifier.testTag("reminder-permission-dismiss"),
                ) { Text(text = stringResource(R.string.reminder_permission_dismiss)) }
            },
            modifier = Modifier.testTag("reminder-permission-dialog"),
        )
    }
}

/** The daily-reminder opt-in (R-REM-1/2): a labeled switch row, off by default. */
@Composable
private fun ReminderToggleRow(
    reminderEnabled: Boolean,
    onReminderToggled: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .toggleable(
                    value = reminderEnabled,
                    role = Role.Switch,
                    onValueChange = onReminderToggled,
                ).testTag("reminder-toggle")
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.reminder_toggle_title),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = reminderEnabled, onCheckedChange = null)
    }
}

/** Label + the chosen time; tapping opens the time-picker dialog (R-REM-2). */
@Composable
private fun ReminderTimeRow(
    reminderTime: LocalTime,
    onOpenPicker: () -> Unit,
) {
    val valueText = reminderTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    val rowDescription = stringResource(R.string.reminder_time_row_description, valueText)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .selectable(
                    selected = false,
                    role = Role.Button,
                    onClick = onOpenPicker,
                ).testTag("reminder-time-row")
                .semantics { contentDescription = rowDescription }
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.reminder_time_title),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("reminder-time-value"),
        )
    }
}

/** Stock M3 time picker in a plain dialog; 12/24-hour input follows the device setting. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimePickerDialog(
    initialTime: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state =
        rememberTimePickerState(
            initialHour = initialTime.hour,
            initialMinute = initialTime.minute,
            is24Hour = DateFormat.is24HourFormat(LocalContext.current),
        )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.reminder_time_dialog_title)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) },
                modifier = Modifier.testTag("reminder-time-confirm"),
            ) { Text(text = stringResource(R.string.reminder_time_dialog_confirm)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("reminder-time-cancel"),
            ) { Text(text = stringResource(R.string.reminder_time_dialog_cancel)) }
        },
        modifier = Modifier.testTag("reminder-time-dialog"),
    )
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
