package com.jpillion.dailyreadingplanner.bible.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jpillion.dailyreadingplanner.bible.domain.GetChapterUseCase
import com.jpillion.dailyreadingplanner.bible.domain.GetPortionTextUseCase
import com.jpillion.dailyreadingplanner.data.reference.Book
import com.jpillion.dailyreadingplanner.data.reference.BookCatalog
import com.jpillion.dailyreadingplanner.domain.model.Portion
import com.jpillion.dailyreadingplanner.ui.navigation.ReaderHandoff
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * VC-T1 — drives the in-app reader over the Sprint B spine use cases. Stateless [ReaderScreen]
 * renders [uiState]; this owns the load + transitions.
 *
 * In-session last-read (D-V3-13, FR-V3-15): the last opened (bookNo, chapter) is held in
 * [SavedStateHandle], so it survives configuration change and process death *within a session*.
 * Durable cross-session last-read keys the verse_id into the read-write ProgressDatabase and is
 * explicitly V3.x — it is NEVER written here.
 */
@HiltViewModel
class ReaderViewModel
    @Inject
    constructor(
        private val getChapter: GetChapterUseCase,
        private val getPortionText: GetPortionTextUseCase,
        private val savedStateHandle: SavedStateHandle,
        private val readerHandoff: ReaderHandoff,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
        val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

        init {
            val restored = lastReadChapter()
            if (restored != null) {
                val (bookNo, chapter) = restored
                BookCatalog.books.firstOrNull { it.order == bookNo }?.let { openChapter(it, chapter) }
                    ?: openDefault()
            } else {
                openDefault()
            }
            // VD-T5 (D-D-1): when a Schedule reading tap hands off a portion to open in-app, render
            // it. consume() is single-shot, so a later config change won't re-open it. Collected for
            // the VM's lifetime so a handoff that arrives while the reader is already alive is honored.
            viewModelScope.launch {
                readerHandoff.pending.filterNotNull().collect { portion ->
                    readerHandoff.consume()
                    openPortion(portion)
                }
            }
        }

        private fun openDefault() = openChapter(BookCatalog.requireByName("Genesis"), 1)

        /** Browse a single (book, chapter). Records it as the in-session last-read on success. */
        fun openChapter(
            book: Book,
            chapter: Int,
        ) {
            _uiState.value = ReaderUiState.Loading
            viewModelScope.launch {
                try {
                    val content = getChapter(book, chapter)
                    savedStateHandle[KEY_BOOK_NO] = book.order
                    savedStateHandle[KEY_CHAPTER] = chapter
                    _uiState.value =
                        ReaderUiState.Content(
                            blocks = listOf(content),
                            title = "${book.canonicalName} $chapter",
                        )
                } catch (e: Exception) {
                    _uiState.value = ReaderUiState.Error()
                }
            }
        }

        /** Open a whole reading-plan portion (the Sprint D tap-in flow; M-V3-4 two-book portion). */
        fun openPortion(portion: Portion) {
            _uiState.value = ReaderUiState.Loading
            viewModelScope.launch {
                try {
                    val content = getPortionText(portion)
                    _uiState.value =
                        ReaderUiState.Content(
                            blocks = content.blocks,
                            title =
                                content.blocks.firstOrNull()?.let { "${it.bookName} ${it.chapter}" }
                                    ?: "",
                        )
                } catch (e: Exception) {
                    _uiState.value = ReaderUiState.Error()
                }
            }
        }

        /** The in-session last-read (bookNo, chapter), or null if none recorded this session. */
        fun lastReadChapter(): Pair<Int, Int>? {
            val bookNo = savedStateHandle.get<Int>(KEY_BOOK_NO) ?: return null
            val chapter = savedStateHandle.get<Int>(KEY_CHAPTER) ?: return null
            return bookNo to chapter
        }

        private companion object {
            const val KEY_BOOK_NO = "reader_book_no"
            const val KEY_CHAPTER = "reader_chapter"
        }
    }
