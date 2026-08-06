package com.jpillion.dailyreadingplanner.bible.ui.reader

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.jpillion.dailyreadingplanner.bible.domain.FakeBibleTextSource
import com.jpillion.dailyreadingplanner.bible.domain.GetChapterUseCase
import com.jpillion.dailyreadingplanner.bible.domain.GetPortionTextUseCase
import com.jpillion.dailyreadingplanner.bible.domain.PortionVerseBridge
import com.jpillion.dailyreadingplanner.bible.domain.bundledResolver
import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import com.jpillion.dailyreadingplanner.data.reference.ProviderUrlBuilder
import com.jpillion.dailyreadingplanner.domain.OpenVerseUseCase
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.domain.model.Reference
import com.jpillion.dailyreadingplanner.testing.FakeSettingsRepository
import com.jpillion.dailyreadingplanner.testing.MainDispatcherRule
import com.jpillion.dailyreadingplanner.ui.navigation.ReaderHandoff
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * Regression pins for the **1.7.0 production P0**: tapping a reading card on the Schedule crashed the
 * app on every day of every plan, with
 * `NullPointerException: Attempt to invoke virtual method 'void StateFlowImpl.setValue(Object)' on a
 * null object reference`.
 *
 * **Root cause — Kotlin initialization order.** `ReaderViewModel.init` collects
 * `ReaderHandoff.pending`, and a Schedule reading tap populates that handoff BEFORE the reader
 * ViewModel is constructed. So the collect fires *during construction* → `enterReading` →
 * `switchContext`, which writes `_selection.value`. Sprint 00Q declared `_selection` BELOW the init
 * block; Kotlin initializes in declaration order, so the field was still null at that moment.
 *
 * **Why every existing test and the device pass missed it:** the crash only reproduced in the
 * R8-minified RELEASE build (debug dispatched the collect late enough to miss the window), and every
 * prior test constructed the ViewModel FIRST and only then set the handoff — the safe order, which
 * never touches the uninitialized field.
 *
 * These tests therefore pin the ORDER, not just the outcome: the handoff is populated **before**
 * `ReaderViewModel(...)` is constructed. Moving `_selection` back below `init` makes them fail.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelHandoffInitTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val source = FakeBibleTextSource()
    private val settings = FakeSettingsRepository()
    private val resolver = bundledResolver(source)
    private val getChapter = GetChapterUseCase(resolver, settings)
    private val getPortionText = GetPortionTextUseCase(PortionVerseBridge(), resolver, settings)
    private val openVerse = OpenVerseUseCase(settings, ProviderUrlBuilder())

    private val genesis = BookCatalog.requireByName("Genesis")
    private val psalms = BookCatalog.requireByName("Psalms")

    private fun vm(handoff: ReaderHandoff) =
        ReaderViewModel(
            getChapter,
            getPortionText,
            openVerse,
            SavedStateHandle(),
            handoff,
            settings,
        )

    @Test
    fun `a portion pending BEFORE construction enters Reading without crashing`() =
        runTest {
            // THE P0 ORDER: the Schedule tap publishes the portion, THEN the reader VM is built.
            val handoff = ReaderHandoff()
            handoff.request(Portion(1, listOf(Reference(genesis, 1))))

            val model = vm(handoff)
            advanceUntilIdle()

            // Reached at all == no NPE during construction.
            assertThat(model.context.value).isInstanceOf(ReaderContext.Reading::class.java)
            // And the field that was null in 1.7.0 is readable and at its initial value.
            assertThat(model.selection.value).isEqualTo(VerseSelection.NONE)
        }

    @Test
    fun `a browse request pending BEFORE construction resets to Browse without crashing`() =
        runTest {
            // The same hazard on the other init-block collector (the Bible-tab reset path):
            // resetToBrowse also routes through switchContext.
            val handoff = ReaderHandoff()
            handoff.requestBrowse()

            val model = vm(handoff)
            advanceUntilIdle()

            assertThat(model.context.value).isEqualTo(ReaderContext.Browse)
            assertThat(model.selection.value).isEqualTo(VerseSelection.NONE)
        }

    @Test
    fun `a multi-chapter portion pending BEFORE construction still opens the combined page`() =
        runTest {
            // Guards the same ordering for the portion-pager path, and that the crash was not
            // specific to single-chapter readings — the owner saw it on "all days, every plan".
            val handoff = ReaderHandoff()
            val portion = Portion(2, listOf(Reference(psalms, 1), Reference(psalms, 2)))
            handoff.request(portion)

            val model = vm(handoff)
            advanceUntilIdle()

            val context = model.context.value
            assertThat(context).isInstanceOf(ReaderContext.Reading::class.java)
            assertThat((context as ReaderContext.Reading).portion).isEqualTo(portion)
            assertThat(model.selection.value).isEqualTo(VerseSelection.NONE)
        }
}
