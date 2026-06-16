package com.jpillion.dailyreadingplanner.domain

import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.domain.model.ExternalBibleApp
import com.jpillion.dailyreadingplanner.domain.model.ReadingDestinationMode
import com.jpillion.dailyreadingplanner.testing.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

/**
 * VD-T7/T10 — the first-run reading-destination question and the one-time upgrade note. The
 * load-bearing rules pinned here:
 *  - in-app is NEVER a silent default: resolving the prompt persists nothing;
 *  - the prompt shows ONLY for a fresh install (no marks), the note ONLY for an upgrader (marks);
 *  - the two gates are mutually exclusive;
 *  - an answer writes the destination MODE + the completed marker; the note preserves the existing
 *    choice (mode + external app) unless the user opts in.
 */
class ReadingDestinationPromptUseCasesTest {
    private val settings = FakeSettingsRepository()
    private val progress = FakeProgressRepository()

    private val resolvePrompt = ResolveReadingDestinationPromptUseCase(settings, progress)
    private val completePrompt = CompleteReadingDestinationPromptUseCase(settings)
    private val resolveNote = ResolveUpgradeNoteUseCase(settings, progress)
    private val completeNote = CompleteUpgradeNoteUseCase(settings)

    private suspend fun seedAMark() = progress.setRead(LocalDate.of(2026, 6, 14), 1, isRead = true)

    // --- the first-run question (fresh install) ---

    @Test
    fun `a fresh install is shown the reading-destination question`() =
        runTest {
            assertThat(resolvePrompt()).isTrue()
        }

    @Test
    fun `resolving the question persists nothing - in-app is never a silent default`() =
        runTest {
            resolvePrompt()
            assertThat(settings.readingDestinationPromptCompletedCalls).isEqualTo(0)
            assertThat(settings.destinationModeCalls).isEmpty()
            assertThat(settings.externalBibleAppCalls).isEmpty()
            assertThat(settings.storedDestinationMode.value).isEqualTo(ReadingDestinationMode.EXTERNAL)
        }

    @Test
    fun `an upgrader with existing marks is NOT shown the question`() =
        runTest {
            seedAMark()
            assertThat(resolvePrompt()).isFalse()
        }

    @Test
    fun `once answered the question never shows again`() =
        runTest {
            completePrompt(ReadingDestinationMode.IN_APP)
            assertThat(resolvePrompt()).isFalse()
        }

    @Test
    fun `answering writes the chosen provider and the completed marker`() =
        runTest {
            completePrompt(ReadingDestinationMode.IN_APP)
            assertThat(settings.readingDestinationMode.first()).isEqualTo(ReadingDestinationMode.IN_APP)
            assertThat(settings.readingDestinationPromptCompletedCalls).isEqualTo(1)
        }

    @Test
    fun `answering external keeps the external mode`() =
        runTest {
            completePrompt(ReadingDestinationMode.EXTERNAL)
            assertThat(settings.readingDestinationMode.first()).isEqualTo(ReadingDestinationMode.EXTERNAL)
        }

    // --- the one-time upgrade note (existing user) ---

    @Test
    fun `an upgrader with existing marks is shown the upgrade note`() =
        runTest {
            seedAMark()
            assertThat(resolveNote()).isTrue()
        }

    @Test
    fun `a fresh install is NOT shown the upgrade note`() =
        runTest {
            assertThat(resolveNote()).isFalse()
        }

    @Test
    fun `the note never shows to someone who answered the fresh-install question`() =
        runTest {
            seedAMark()
            settings.markReadingDestinationPromptCompleted()
            assertThat(resolveNote()).isFalse()
        }

    @Test
    fun `once shown the note never shows again`() =
        runTest {
            seedAMark()
            settings.markUpgradeNoteShown()
            assertThat(resolveNote()).isFalse()
        }

    @Test
    fun `MUTATION dismissing the note preserves the existing external choice - mode and app`() =
        runTest {
            // upgrade-note-preserves-B: an existing user on YouVersion in external mode keeps BOTH
            // axes when they dismiss. A mutation that writes a mode on dismiss reddens here.
            settings.storedDestinationMode.value = ReadingDestinationMode.EXTERNAL
            settings.storedExternalBibleApp.value = ExternalBibleApp.YOUVERSION
            settings.destinationModeCalls.clear()
            completeNote(switchToInApp = false)
            assertThat(settings.readingDestinationMode.first()).isEqualTo(ReadingDestinationMode.EXTERNAL)
            assertThat(settings.externalBibleApp.first()).isEqualTo(ExternalBibleApp.YOUVERSION)
            assertThat(settings.destinationModeCalls).isEmpty()
            assertThat(settings.upgradeNoteShownCalls).isEqualTo(1)
        }

    @Test
    fun `using the in-app reader from the note switches the mode and keeps the remembered app`() =
        runTest {
            settings.storedDestinationMode.value = ReadingDestinationMode.EXTERNAL
            settings.storedExternalBibleApp.value = ExternalBibleApp.YOUVERSION
            completeNote(switchToInApp = true)
            assertThat(settings.readingDestinationMode.first()).isEqualTo(ReadingDestinationMode.IN_APP)
            // The external app B is remembered through the switch (round-trip back to external).
            assertThat(settings.externalBibleApp.first()).isEqualTo(ExternalBibleApp.YOUVERSION)
            assertThat(settings.upgradeNoteShownCalls).isEqualTo(1)
        }

    // --- mutual exclusivity (the upgrade-vs-fresh-install distinction) ---

    @Test
    fun `prompt and note are never both shown - fresh install`() =
        runTest {
            assertThat(resolvePrompt()).isTrue()
            assertThat(resolveNote()).isFalse()
        }

    @Test
    fun `prompt and note are never both shown - upgrader`() =
        runTest {
            seedAMark()
            assertThat(resolvePrompt()).isFalse()
            assertThat(resolveNote()).isTrue()
        }
}
