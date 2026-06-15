package com.jpillion.dailyreadingplanner.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpillion.dailyreadingplanner.R
import com.jpillion.dailyreadingplanner.data.prefs.SettingsRepository
import com.jpillion.dailyreadingplanner.domain.model.ExternalBibleApp
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestinationMode
import com.jpillion.dailyreadingplanner.domain.model.ThemeMode
import java.time.LocalDate
import java.time.LocalTime
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
    val destinationMode by viewModel.destinationMode.collectAsStateWithLifecycle()
    val externalBibleApp by viewModel.externalBibleApp.collectAsStateWithLifecycle()
    val showStreaks by viewModel.showStreaks.collectAsStateWithLifecycle()
    val fontScale by viewModel.fontScale.collectAsStateWithLifecycle()
    val trackingStartDate by viewModel.trackingStartDate.collectAsStateWithLifecycle()
    val reminderEnabled by viewModel.reminderEnabled.collectAsStateWithLifecycle()
    val reminderTime by viewModel.reminderTime.collectAsStateWithLifecycle()
    val persistentNotificationEnabled by viewModel.persistentNotificationEnabled.collectAsStateWithLifecycle()
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
    val requestAppSubject = stringResource(R.string.request_app_subject)
    val requestAppBody = stringResource(R.string.request_app_body)
    SettingsScreen(
        selectedMode = themeMode,
        destinationMode = destinationMode,
        externalBibleApp = externalBibleApp,
        mySwordInstalled = viewModel.mySwordInstalled,
        showStreaks = showStreaks,
        fontScale = fontScale,
        currentYear = viewModel.currentYear,
        trackingStartDate = trackingStartDate,
        reminderEnabled = reminderEnabled,
        reminderTime = reminderTime,
        persistentNotificationEnabled = persistentNotificationEnabled,
        showReminderPermissionRationale = showPermissionRationale,
        onThemeModeSelected = viewModel::onThemeModeSelected,
        onDestinationModeSelected = viewModel::onDestinationModeSelected,
        onExternalBibleAppSelected = viewModel::onExternalBibleAppSelected,
        onShowStreaksToggled = viewModel::onShowStreaksToggled,
        onRequestApp = {
            // Spec §7: an outbound mailto intent — the same intent class as a reading link;
            // no networking, nothing collected. No email app -> a quiet no-op (G-OFFLINE spirit).
            try {
                context.startActivity(
                    Intent(Intent.ACTION_SENDTO, "mailto:".toUri())
                        .putExtra(Intent.EXTRA_EMAIL, arrayOf("jjpillion@gmail.com"))
                        .putExtra(Intent.EXTRA_SUBJECT, requestAppSubject)
                        .putExtra(Intent.EXTRA_TEXT, requestAppBody),
                )
            } catch (_: ActivityNotFoundException) {
                Log.w("SettingsRoute", "No email app available for the request-an-app intent")
            }
        },
        onFontScaleChanged = viewModel::onFontScaleChanged,
        onTrackingStartChanged = viewModel::onTrackingStartChanged,
        onReminderToggled = viewModel::onReminderToggled,
        onReminderTimeChanged = viewModel::onReminderTimeChanged,
        onPersistentNotificationToggled = viewModel::onPersistentNotificationToggled,
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
    destinationMode: ReadingDestinationMode,
    externalBibleApp: ExternalBibleApp,
    mySwordInstalled: Boolean,
    showStreaks: Boolean,
    fontScale: Float,
    currentYear: Int,
    trackingStartDate: LocalDate?,
    reminderEnabled: Boolean,
    reminderTime: LocalTime,
    persistentNotificationEnabled: Boolean,
    showReminderPermissionRationale: Boolean,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onDestinationModeSelected: (ReadingDestinationMode) -> Unit,
    onExternalBibleAppSelected: (ExternalBibleApp) -> Unit,
    onShowStreaksToggled: (Boolean) -> Unit,
    onRequestApp: () -> Unit,
    onFontScaleChanged: (Float) -> Unit,
    onTrackingStartChanged: (LocalDate?) -> Unit,
    onReminderToggled: (Boolean) -> Unit,
    onReminderTimeChanged: (LocalTime) -> Unit,
    onPersistentNotificationToggled: (Boolean) -> Unit,
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
            ThemeDropdown(
                selectedMode = selectedMode,
                onThemeModeSelected = onThemeModeSelected,
            )

            SectionTitle(stringResource(R.string.text_size_section_title))
            TextSizeSlider(fontScale = fontScale, onFontScaleChanged = onFontScaleChanged)

            SectionTitle(stringResource(R.string.provider_section_title))
            // Sprint K (D-23-1): the destination MODE is a segmented toggle ("In this app" |
            // "My Bible app"); WHICH external app is a separate dropdown shown when the mode is
            // external. The external choice is remembered while in-app is selected.
            DestinationModeToggle(
                destinationMode = destinationMode,
                onDestinationModeSelected = onDestinationModeSelected,
            )
            if (destinationMode == ReadingDestinationMode.EXTERNAL) {
                ExternalAppDropdown(
                    selectedApp = externalBibleApp,
                    mySwordInstalled = mySwordInstalled,
                    onExternalBibleAppSelected = onExternalBibleAppSelected,
                )
            }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .selectable(
                            selected = false,
                            role = Role.Button,
                            onClick = onRequestApp,
                        ).testTag("request-app-row")
                        .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.request_app_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            SectionTitle(stringResource(R.string.stats_settings_section_title))
            ShowStreaksToggleRow(
                showStreaks = showStreaks,
                onShowStreaksToggled = onShowStreaksToggled,
            )
            Text(
                text = stringResource(R.string.show_streaks_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
            )

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
            PersistentNotificationToggleRow(
                enabled = persistentNotificationEnabled,
                onToggled = onPersistentNotificationToggled,
            )
            Text(
                text = stringResource(R.string.persistent_notification_help),
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

/** S21 (D-S21-5): the persistent ongoing-notification opt-in — a labeled switch row, off by default. */
@Composable
private fun PersistentNotificationToggleRow(
    enabled: Boolean,
    onToggled: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .toggleable(
                    value = enabled,
                    role = Role.Switch,
                    onValueChange = onToggled,
                ).testTag("persistent-notification-toggle")
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.persistent_notification_toggle_title),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = enabled, onCheckedChange = null)
    }
}

/** S15 (D-S15-5): streak visibility — a labeled switch row, on by default. Display only. */
@Composable
private fun ShowStreaksToggleRow(
    showStreaks: Boolean,
    onShowStreaksToggled: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .toggleable(
                    value = showStreaks,
                    role = Role.Switch,
                    onValueChange = onShowStreaksToggled,
                ).testTag("show-streaks-toggle")
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.show_streaks_title),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = showStreaks, onCheckedChange = null)
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

/**
 * S14 (owner request): the theme selector as a compact dropdown — one row showing the
 * current value; tapping opens an M3 [DropdownMenu] with the three modes. The option test
 * tags (`theme-option-*`) carry over from the S6 radio rows onto the menu items.
 */
@Composable
private fun ThemeDropdown(
    selectedMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val options =
        listOf(
            Triple(ThemeMode.LIGHT, stringResource(R.string.theme_light), "theme-option-light"),
            Triple(ThemeMode.DARK, stringResource(R.string.theme_dark), "theme-option-dark"),
            Triple(ThemeMode.SYSTEM, stringResource(R.string.theme_system), "theme-option-system"),
        )
    val valueText = options.first { it.first == selectedMode }.second
    SettingsDropdownRow(
        valueText = valueText,
        rowDescription = stringResource(R.string.theme_dropdown_description, valueText),
        testTag = "theme-dropdown",
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        options.forEach { (mode, label, tag) ->
            SelectableMenuItem(
                label = label,
                selected = mode == selectedMode,
                testTag = tag,
                onClick = {
                    expanded = false
                    onThemeModeSelected(mode)
                },
            )
        }
    }
}

/**
 * Sprint K (D-23-1): the destination-MODE selector — a single-choice segmented toggle
 * ("In this app" | "My Bible app") under the "Open readings in" heading. The whole row carries
 * the spoken label and each segment exposes [Role.RadioButton] selection semantics so TalkBack
 * announces "In this app, selected" / "My Bible app". Tags: `destination-mode-toggle` (row),
 * `destination-mode-inapp` / `destination-mode-external` (segments).
 */
@Composable
private fun DestinationModeToggle(
    destinationMode: ReadingDestinationMode,
    onDestinationModeSelected: (ReadingDestinationMode) -> Unit,
) {
    val inAppLabel = stringResource(R.string.destination_mode_inapp)
    val externalLabel = stringResource(R.string.destination_mode_external)
    val rowDescription =
        stringResource(
            R.string.destination_mode_row_description,
            if (destinationMode == ReadingDestinationMode.IN_APP) inAppLabel else externalLabel,
        )
    SingleChoiceSegmentedButtonRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp)
                .testTag("destination-mode-toggle")
                .semantics { contentDescription = rowDescription },
    ) {
        SegmentedButton(
            selected = destinationMode == ReadingDestinationMode.IN_APP,
            onClick = { onDestinationModeSelected(ReadingDestinationMode.IN_APP) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            modifier =
                Modifier
                    .testTag("destination-mode-inapp")
                    .semantics {
                        role = Role.RadioButton
                        selected = destinationMode == ReadingDestinationMode.IN_APP
                    },
        ) { Text(text = inAppLabel) }
        SegmentedButton(
            selected = destinationMode == ReadingDestinationMode.EXTERNAL,
            onClick = { onDestinationModeSelected(ReadingDestinationMode.EXTERNAL) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            modifier =
                Modifier
                    .testTag("destination-mode-external")
                    .semantics {
                        role = Role.RadioButton
                        selected = destinationMode == ReadingDestinationMode.EXTERNAL
                    },
        ) { Text(text = externalLabel) }
    }
}

/**
 * Sprint K (D-23-1): the "My Bible app" dropdown — WHICH external app/site reading taps open
 * when the mode is EXTERNAL. The four external apps (BLB default, Bible Gateway, YouVersion,
 * MySword). MySword mirrors the S15 install-gating idiom: a visible-but-disabled
 * "app not installed" item when absent (discoverable, never a dead tap). The `provider-option-*`
 * and `provider-dropdown` test tags carry over from the retired single dropdown.
 */
@Composable
private fun ExternalAppDropdown(
    selectedApp: ExternalBibleApp,
    mySwordInstalled: Boolean,
    onExternalBibleAppSelected: (ExternalBibleApp) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val options =
        listOf(
            Triple(ExternalBibleApp.BLB, stringResource(R.string.provider_blb), "provider-option-blb"),
            Triple(
                ExternalBibleApp.BIBLE_GATEWAY,
                stringResource(R.string.provider_biblegateway),
                "provider-option-biblegateway",
            ),
            Triple(
                ExternalBibleApp.YOUVERSION,
                stringResource(R.string.provider_youversion),
                "provider-option-youversion",
            ),
        )
    val mySwordLabel =
        if (mySwordInstalled) {
            stringResource(R.string.provider_mysword)
        } else {
            stringResource(R.string.provider_mysword_not_installed)
        }
    val valueText =
        if (selectedApp == ExternalBibleApp.MYSWORD) {
            mySwordLabel
        } else {
            options.first { it.first == selectedApp }.second
        }
    SettingsDropdownRow(
        valueText = valueText,
        rowDescription = stringResource(R.string.external_app_dropdown_description, valueText),
        testTag = "provider-dropdown",
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        options.forEach { (app, label, tag) ->
            SelectableMenuItem(
                label = label,
                selected = app == selectedApp,
                testTag = tag,
                onClick = {
                    expanded = false
                    onExternalBibleAppSelected(app)
                },
            )
        }
        if (mySwordInstalled) {
            SelectableMenuItem(
                label = mySwordLabel,
                selected = selectedApp == ExternalBibleApp.MYSWORD,
                testTag = "provider-option-mysword",
                onClick = {
                    expanded = false
                    onExternalBibleAppSelected(ExternalBibleApp.MYSWORD)
                },
            )
        } else {
            DropdownMenuItem(
                text = { Text(text = mySwordLabel) },
                onClick = {},
                enabled = false,
                modifier = Modifier.testTag("provider-option-mysword"),
            )
        }
    }
}

/**
 * S14: the shared dropdown idiom — a 56dp settings row (current value + drop-down arrow,
 * [Role.DropdownList], spoken as "label, value") anchoring an M3 [DropdownMenu].
 */
@Composable
private fun SettingsDropdownRow(
    valueText: String,
    rowDescription: String,
    testTag: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    menuContent: @Composable ColumnScope.() -> Unit,
) {
    Box {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .selectable(
                        selected = false,
                        role = Role.DropdownList,
                        onClick = { onExpandedChange(true) },
                    ).testTag(testTag)
                    .semantics { contentDescription = rowDescription }
                    .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            content = menuContent,
        )
    }
}

/** One selectable menu option: a leading check on the current value + selection semantics. */
@Composable
private fun SelectableMenuItem(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text = label) },
        onClick = onClick,
        leadingIcon =
            if (selected) {
                { Icon(imageVector = Icons.Filled.Check, contentDescription = null) }
            } else {
                null
            },
        modifier =
            Modifier
                .testTag(testTag)
                .semantics { this.selected = selected },
    )
}
