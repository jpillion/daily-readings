package com.jpillion.dailyreadingplanner.ui.settings

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.data.plan.ActivePlanRepository
import com.jpillion.dailyreadingplanner.data.plan.PlanAssetSource
import com.jpillion.dailyreadingplanner.data.plan.PlanRegistry
import com.jpillion.dailyreadingplanner.data.plan.ReadingPlanAssetLoader
import com.jpillion.dailyreadingplanner.data.plan.ReadingPlanRepositoryImpl
import com.jpillion.dailyreadingplanner.domain.FakeProgressRepository
import com.jpillion.dailyreadingplanner.domain.ResetYearProgressUseCase
import com.jpillion.dailyreadingplanner.domain.bibleCompanionDescriptor
import com.jpillion.dailyreadingplanner.domain.model.ExternalBibleApp
import com.jpillion.dailyreadingplanner.domain.model.PlanDescriptor
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestinationMode
import com.jpillion.dailyreadingplanner.domain.model.ThemeMode
import com.jpillion.dailyreadingplanner.testing.FakeNotificationPermissionChecker
import com.jpillion.dailyreadingplanner.testing.FakeReminderScheduler
import com.jpillion.dailyreadingplanner.testing.FakeSettingsRepository
import com.jpillion.dailyreadingplanner.testing.FakeWidgetRefresher
import com.jpillion.dailyreadingplanner.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSettingsRepository()
    private val progress = FakeProgressRepository()
    private val widgetRefresher = FakeWidgetRefresher()
    private val reminderScheduler = FakeReminderScheduler()
    private val persistentNotifier =
        com.jpillion.dailyreadingplanner.testing
            .FakePersistentNotifier()
    private val permissionChecker = FakeNotificationPermissionChecker(granted = true)
    private var mySwordInstalled = false
    private val appInstallChecker =
        object : com.jpillion.dailyreadingplanner.data.apps.AppInstallChecker {
            val queries = mutableListOf<String>()

            override fun isInstalled(packageName: String): Boolean {
                queries += packageName
                return mySwordInstalled
            }
        }

    // Alt Sprint D: the selector reads real plan names from the real bundled descriptors.
    private val assetsRoot = File(System.getProperty("planAssetsDir") ?: error("planAssetsDir not set"))
    private val planSource = PlanAssetSource { path -> assetsRoot.resolve(path).readText() }
    private val unconfined = UnconfinedTestDispatcher()
    private val planRegistry = PlanRegistry(planSource, unconfined)
    private val readingPlanRepository =
        ReadingPlanRepositoryImpl(planRegistry, ReadingPlanAssetLoader(planSource, unconfined))

    // A controllable active-plan fake (the active id is a MutableStateFlow the switch tests drive).
    private val activePlanId = MutableStateFlow("bible_companion")
    private val activePlanRepository =
        object : ActivePlanRepository {
            override val activePlanId: Flow<String> = this@SettingsViewModelTest.activePlanId
            override val activeDescriptor: Flow<PlanDescriptor> = flowOf(bibleCompanionDescriptor)
        }

    private val clock = Clock.fixed(Instant.parse("2026-06-10T12:00:00Z"), ZoneOffset.UTC)
    private val refreshPersistent =
        com.jpillion.dailyreadingplanner.domain.RefreshPersistentNotificationUseCase(
            settingsRepository = repository,
            getDayReadings =
                com.jpillion.dailyreadingplanner.domain.GetDayReadingsUseCase(
                    resolver =
                        com.jpillion.dailyreadingplanner.core.date
                            .ScheduleDateResolver(),
                    planRepository =
                        com.jpillion.dailyreadingplanner.domain
                            .FakeReadingPlanRepository(),
                    progressRepository = progress,
                    activePlanRepository =
                        com.jpillion.dailyreadingplanner.domain
                            .FakeActivePlanRepository(),
                ),
            notifier = persistentNotifier,
            scheduler = reminderScheduler,
            clock = clock,
        )
    private val viewModel by lazy {
        SettingsViewModel(
            settingsRepository = repository,
            resetYearProgress = ResetYearProgressUseCase(progress, clock),
            widgetRefresher = widgetRefresher,
            reminderScheduler = reminderScheduler,
            refreshPersistentNotification = refreshPersistent,
            notificationPermissionChecker = permissionChecker,
            appInstallChecker = appInstallChecker,
            activePlanRepository = activePlanRepository,
            planRegistry = planRegistry,
            readingPlanRepository = readingPlanRepository,
            clock = clock,
        )
    }

    @Test
    fun `initial state is SYSTEM before the repository emits`() {
        assertThat(viewModel.themeMode.value).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test
    fun `persisted mode flows into state`() =
        runTest {
            repository.stored.value = ThemeMode.DARK
            viewModel.themeMode.test {
                var state = awaitItem()
                while (state != ThemeMode.DARK) state = awaitItem()
                assertThat(state).isEqualTo(ThemeMode.DARK)
            }
        }

    @Test
    fun `selecting a mode persists it and updates state`() =
        runTest {
            viewModel.themeMode.test {
                awaitItem() // SYSTEM
                viewModel.onThemeModeSelected(ThemeMode.DARK)
                assertThat(awaitItem()).isEqualTo(ThemeMode.DARK)
                viewModel.onThemeModeSelected(ThemeMode.LIGHT)
                assertThat(awaitItem()).isEqualTo(ThemeMode.LIGHT)
            }
            assertThat(repository.setCalls).containsExactly(ThemeMode.DARK, ThemeMode.LIGHT).inOrder()
        }

    @Test
    fun `font scale changes persist and update state`() =
        runTest {
            viewModel.fontScale.test {
                awaitItem() // 1.0 default
                viewModel.onFontScaleChanged(1.25f)
                assertThat(awaitItem()).isEqualTo(1.25f)
            }
            assertThat(repository.fontScaleCalls).containsExactly(1.25f)
        }

    @Test
    fun `current year comes from the clock`() {
        assertThat(viewModel.currentYear).isEqualTo(2026)
    }

    @Test
    fun `confirmed reset clears the current year and refreshes the widget`() =
        runTest {
            progress.setWholeDay(LocalDate.of(2026, 6, 1), listOf(1, 2, 3), isRead = true)
            progress.setRead(LocalDate.of(2025, 6, 1), 3, isRead = true)
            viewModel.onResetProgressConfirmed()
            assertThat(progress.clearYearCalls).containsExactly(2026)
            assertThat(progress.marksFor(LocalDate.of(2026, 6, 1))).isEmpty()
            assertThat(progress.marksFor(LocalDate.of(2025, 6, 1))).containsExactly(3)
            assertThat(widgetRefresher.refreshCount).isEqualTo(1)
        }

    // --- S10: tracking start date. ---

    @Test
    fun `tracking start defaults to null and reflects the persisted value`() =
        runTest {
            viewModel.trackingStartDate.test {
                assertThat(awaitItem()).isNull()
                repository.storedTrackingStartDate.value = LocalDate.of(2026, 6, 3)
                assertThat(awaitItem()).isEqualTo(LocalDate.of(2026, 6, 3))
            }
        }

    @Test
    fun `changing the tracking start writes through - and clearing writes null`() =
        runTest {
            viewModel.trackingStartDate.test {
                awaitItem() // null default
                viewModel.onTrackingStartChanged(LocalDate.of(2026, 6, 3))
                assertThat(awaitItem()).isEqualTo(LocalDate.of(2026, 6, 3))
                viewModel.onTrackingStartChanged(null)
                assertThat(awaitItem()).isNull()
            }
            assertThat(repository.trackingStartCalls)
                .containsExactly(LocalDate.of(2026, 6, 3), null)
                .inOrder()
        }

    @Test
    fun `reset and tracking start are independent - neither touches the other`() =
        runTest {
            // Reset clears marks but never the tracking start date (spec §7).
            repository.storedTrackingStartDate.value = LocalDate.of(2026, 6, 3)
            viewModel.onResetProgressConfirmed()
            assertThat(repository.trackingStartCalls).isEmpty()
            assertThat(repository.storedTrackingStartDate.value).isEqualTo(LocalDate.of(2026, 6, 3))
            // Changing the start date never clears marks.
            progress.setWholeDay(LocalDate.of(2026, 5, 1), listOf(1, 2, 3), isRead = true)
            val clearsBefore = progress.clearYearCalls.size
            viewModel.onTrackingStartChanged(LocalDate.of(2026, 1, 1))
            assertThat(progress.clearYearCalls).hasSize(clearsBefore)
            assertThat(progress.marksFor(LocalDate.of(2026, 5, 1))).isNotEmpty()
        }

    // --- S12: daily reminder (R-REM-1/2/7). ---

    @Test
    fun `reminder defaults off with the 8am time`() {
        assertThat(viewModel.reminderEnabled.value).isFalse()
        assertThat(viewModel.reminderTime.value).isEqualTo(LocalTime.of(8, 0))
    }

    @Test
    fun `enabling with permission persists and arms the alarm at the stored time`() =
        runTest {
            repository.storedReminderTime.value = LocalTime.of(7, 15)
            viewModel.onReminderToggled(true)
            assertThat(repository.reminderEnabledCalls).containsExactly(true)
            assertThat(reminderScheduler.scheduledTimes).containsExactly(LocalTime.of(7, 15))
        }

    @Test
    fun `disabling persists off and cancels the alarm`() =
        runTest {
            repository.storedReminderEnabled.value = true
            viewModel.onReminderToggled(false)
            assertThat(repository.reminderEnabledCalls).containsExactly(false)
            assertThat(reminderScheduler.cancelCount).isEqualTo(1)
            assertThat(reminderScheduler.scheduledTimes).isEmpty()
        }

    @Test
    fun `enabling without permission requests it instead of persisting - toggle reflects reality`() =
        runTest {
            permissionChecker.granted = false
            viewModel.permissionRequests.test {
                viewModel.onReminderToggled(true)
                awaitItem()
                assertThat(repository.reminderEnabledCalls).isEmpty()
                assertThat(reminderScheduler.scheduledTimes).isEmpty()
            }
        }

    @Test
    fun `permission granted from the prompt enables and arms`() =
        runTest {
            viewModel.onNotificationPermissionResult(granted = true)
            assertThat(repository.reminderEnabledCalls).containsExactly(true)
            assertThat(reminderScheduler.scheduledTimes).hasSize(1)
            assertThat(viewModel.showPermissionRationale.value).isFalse()
        }

    @Test
    fun `permission denied keeps the setting off and shows the explanation until dismissed`() =
        runTest {
            viewModel.onNotificationPermissionResult(granted = false)
            assertThat(repository.reminderEnabledCalls).isEmpty()
            assertThat(reminderScheduler.scheduledTimes).isEmpty()
            assertThat(viewModel.showPermissionRationale.value).isTrue()
            viewModel.onPermissionRationaleDismissed()
            assertThat(viewModel.showPermissionRationale.value).isFalse()
        }

    @Test
    fun `changing the time while enabled persists and reschedules to the new time`() =
        runTest {
            repository.storedReminderEnabled.value = true
            viewModel.onReminderTimeChanged(LocalTime.of(21, 0))
            assertThat(repository.reminderTimeCalls).containsExactly(LocalTime.of(21, 0))
            assertThat(reminderScheduler.scheduledTimes).containsExactly(LocalTime.of(21, 0))
        }

    @Test
    fun `changing the time while disabled persists but never arms an alarm`() =
        runTest {
            viewModel.onReminderTimeChanged(LocalTime.of(21, 0))
            assertThat(repository.reminderTimeCalls).containsExactly(LocalTime.of(21, 0))
            assertThat(reminderScheduler.scheduledTimes).isEmpty()
        }

    // --- Sprint K (D-23-1): destination mode (segmented toggle) + external app (dropdown). ---

    @Test
    fun `selecting the destination mode persists it`() =
        runTest {
            viewModel.onDestinationModeSelected(ReadingDestinationMode.IN_APP)
            assertThat(repository.destinationModeCalls).containsExactly(ReadingDestinationMode.IN_APP)
            assertThat(repository.storedDestinationMode.value).isEqualTo(ReadingDestinationMode.IN_APP)
        }

    @Test
    fun `selecting an external app persists it without touching the mode`() =
        runTest {
            viewModel.onExternalBibleAppSelected(ExternalBibleApp.YOUVERSION)
            assertThat(repository.externalBibleAppCalls).containsExactly(ExternalBibleApp.YOUVERSION)
            assertThat(repository.storedExternalBibleApp.value).isEqualTo(ExternalBibleApp.YOUVERSION)
            // The two axes are independent: the mode was not written.
            assertThat(repository.destinationModeCalls).isEmpty()
        }

    // --- S15: MySword install detection (D-S15-2) + show-streaks (D-S15-5). ---

    @Test
    fun `mySwordInstalled reflects the install checker - queried for the mysword package`() {
        mySwordInstalled = true
        assertThat(viewModel.mySwordInstalled).isTrue()
        assertThat(appInstallChecker.queries).containsExactly("com.riversoft.android.mysword")
    }

    @Test
    fun `mySwordInstalled is false when the app is absent`() {
        mySwordInstalled = false
        assertThat(viewModel.mySwordInstalled).isFalse()
    }

    @Test
    fun `show streaks defaults off and toggling persists the new value`() =
        runTest {
            // S18 (owner): streaks are opt-in — the default is off.
            viewModel.showStreaks.test {
                assertThat(awaitItem()).isFalse()
                viewModel.onShowStreaksToggled(true)
                assertThat(awaitItem()).isTrue()
                viewModel.onShowStreaksToggled(false)
                assertThat(awaitItem()).isFalse()
            }
            assertThat(repository.showStreaksCalls).containsExactly(true, false).inOrder()
        }

    // --- S21: persistent (ongoing) notification toggle. ---

    @Test
    fun `enabling the persistent notification with permission schedules the alarm and posts`() =
        runTest {
            permissionChecker.granted = true
            viewModel.onPersistentNotificationToggled(true)
            assertThat(repository.persistentEnabledCalls).containsExactly(true)
            assertThat(reminderScheduler.persistentRefreshCount).isEqualTo(1)
            assertThat(persistentNotifier.shown).hasSize(1)
        }

    @Test
    fun `disabling the persistent notification cancels the alarm and dismisses it`() =
        runTest {
            repository.storedPersistentEnabled.value = true
            viewModel.onPersistentNotificationToggled(false)
            assertThat(repository.persistentEnabledCalls).contains(false)
            assertThat(reminderScheduler.cancelPersistentCount).isEqualTo(1)
            assertThat(persistentNotifier.cancelCount).isEqualTo(1)
        }

    @Test
    fun `enabling without permission requests it and does not persist yet`() =
        runTest {
            permissionChecker.granted = false
            viewModel.permissionRequests.test {
                viewModel.onPersistentNotificationToggled(true)
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
            assertThat(repository.persistentEnabledCalls).isEmpty()
            assertThat(persistentNotifier.shown).isEmpty()
        }

    @Test
    fun `a granted permission result for the persistent toggle enables persistent - not the reminder`() =
        runTest {
            // The shared prompt must route to whichever toggle requested it (S21).
            permissionChecker.granted = false
            viewModel.permissionRequests.test {
                viewModel.onPersistentNotificationToggled(true)
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.onNotificationPermissionResult(granted = true)
            assertThat(repository.persistentEnabledCalls).containsExactly(true)
            assertThat(repository.reminderEnabledCalls).isEmpty()
        }

    // --- Alt Sprint D (D-ALT-18/19): the reading-plan selector + non-destructive switch. ---

    @Test
    fun `the selector lists the registry plans with their descriptor names and the active id`() =
        runTest {
            viewModel.planSelector.test {
                // Skip the initial empty state; the registry/descriptor load resolves to three plans
                // (CHR-5: chronological joined the registry in alt Sprint F).
                var state = awaitItem()
                while (state.options.size < 3) state = awaitItem()
                assertThat(state.options.map { it.id })
                    .containsExactly("bible_companion", "mcheyne", "chronological")
                    .inOrder()
                assertThat(state.options.first { it.id == "bible_companion" }.name).isEqualTo("Bible Companion")
                assertThat(state.options.first { it.id == "mcheyne" }.name).isEqualTo("M'Cheyne")
                assertThat(state.options.first { it.id == "chronological" }.name).isEqualTo("Chronological")
                assertThat(state.activeId).isEqualTo("bible_companion")
                assertThat(state.activeName).isEqualTo("Bible Companion")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the selector re-selects the row live when the active plan changes`() =
        runTest {
            viewModel.planSelector.test {
                var state = awaitItem()
                while (state.options.size < 2) state = awaitItem()
                assertThat(state.activeId).isEqualTo("bible_companion")
                activePlanId.value = "mcheyne"
                state = awaitItem()
                assertThat(state.activeId).isEqualTo("mcheyne")
                assertThat(state.activeName).isEqualTo("M'Cheyne")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `selecting the already-active plan is a no-op - no dialog - no write`() =
        runTest {
            // Prime the selector so options are loaded (activeId == bible_companion).
            viewModel.planSelector.test {
                var state = awaitItem()
                while (state.options.size < 2) state = awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.onPlanSelected("bible_companion")
            assertThat(viewModel.pendingPlanSwitch.value).isNull()
            assertThat(repository.selectedPlanIdCalls).isEmpty()
        }

    @Test
    fun `selecting a different plan raises the switch dialog with from and to names but writes nothing`() =
        runTest {
            viewModel.planSelector.test {
                var state = awaitItem()
                while (state.options.size < 2) state = awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.onPlanSelected("mcheyne")
            val pending = viewModel.pendingPlanSwitch.value
            assertThat(pending).isNotNull()
            assertThat(pending!!.toId).isEqualTo("mcheyne")
            assertThat(pending.fromName).isEqualTo("Bible Companion")
            assertThat(pending.toName).isEqualTo("M'Cheyne")
            // D-ALT-19: NO write happens until the user confirms.
            assertThat(repository.selectedPlanIdCalls).isEmpty()
            assertThat(widgetRefresher.refreshCount).isEqualTo(0)
        }

    @Test
    fun `confirming the switch writes selected_plan and refreshes the widget`() =
        runTest {
            viewModel.planSelector.test {
                var state = awaitItem()
                while (state.options.size < 2) state = awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.onPlanSelected("mcheyne")
            viewModel.onPlanSwitchConfirmed()
            assertThat(repository.selectedPlanIdCalls).containsExactly("mcheyne")
            assertThat(widgetRefresher.refreshCount).isEqualTo(1)
            assertThat(viewModel.pendingPlanSwitch.value).isNull()
        }

    @Test
    fun `dismissing the switch clears the dialog and writes nothing`() =
        runTest {
            viewModel.planSelector.test {
                var state = awaitItem()
                while (state.options.size < 2) state = awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.onPlanSelected("mcheyne")
            viewModel.onPlanSwitchDismissed()
            assertThat(viewModel.pendingPlanSwitch.value).isNull()
            assertThat(repository.selectedPlanIdCalls).isEmpty()
            assertThat(widgetRefresher.refreshCount).isEqualTo(0)
        }
}
