package com.jpillion.dailyreadingplanner.bible.ui.reader

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpillion.dailyreadingplanner.bible.ui.picker.BookChapterPickerSheet
import com.jpillion.dailyreadingplanner.data.reference.Book
import com.jpillion.dailyreadingplanner.data.reference.BookCatalog

/**
 * VC-T6 — stateful reader entry. Owns [ReaderViewModel], the picker-sheet visibility, the scroll
 * state, and the current (book, chapter) cursor that drives Prev/Next enablement (computed purely
 * via [ChapterNavigator] over canon order). Mirrors the app's Route/Screen split (DayReadingsRoute).
 *
 * V3.0 browses single chapters; the Sprint D tap-handoff will call into [ReaderViewModel.openPortion]
 * for whole-portion opens (the screen already renders multi-block content).
 */
@Composable
fun ReaderRoute(
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var showPicker by remember { mutableStateOf(false) }

    // The browse cursor: the (book, chapter) of the first rendered block, restored from the VM's
    // in-session last-read so Prev/Next and the picker stay in sync across config change.
    val restored = viewModel.lastReadChapter()
    var current by remember {
        mutableStateOf(
            restored?.let { (bookNo, ch) ->
                BookCatalog.books.firstOrNull { it.order == bookNo }?.let { it to ch }
            } ?: (BookCatalog.requireByName("Genesis") to 1),
        )
    }

    fun go(target: Pair<Book, Int>?) {
        if (target == null) return
        current = target
        viewModel.openChapter(target.first, target.second)
    }

    ReaderScreen(
        state = state,
        onOpenPicker = { showPicker = true },
        onPrevChapter = { go(ChapterNavigator.previous(current.first, current.second)) },
        onNextChapter = { go(ChapterNavigator.next(current.first, current.second)) },
        onRetry = { viewModel.openChapter(current.first, current.second) },
        modifier = modifier,
        listState = listState,
        prevEnabled = ChapterNavigator.previous(current.first, current.second) != null,
        nextEnabled = ChapterNavigator.next(current.first, current.second) != null,
    )

    if (showPicker) {
        BookChapterPickerSheet(
            onChapterSelected = { book, chapter ->
                showPicker = false
                go(book to chapter)
            },
            onDismiss = { showPicker = false },
        )
    }
}
