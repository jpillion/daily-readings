package com.jpillion.dailyreadingplanner.bible.ui.reader

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpillion.dailyreadingplanner.bible.ui.picker.BookChapterPickerSheet
import com.jpillion.dailyreadingplanner.ui.browser.launchReadingDestination
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * I3 (D-I-1 / D-I-2) — stateful reader entry, two-context aware. Owns [ReaderViewModel], the
 * chapter [rememberPagerState], the picker sheet, and the verse-tap launch side-effect.
 *
 * The pager is built for the VM's CURRENT [ReaderViewModel.context] — Browse over the whole canon
 * ([GlobalChapterIndex.TOTAL_CHAPTERS] pages), or Reading over the portion-anchored
 * [ReadingPagerIndex] ([ReaderContext.pageCount] pages with the portion collapsed to one page). A
 * context switch (a Schedule reading tap → Reading; a Bible-tab tap / picker jump → Browse)
 * rebuilds the [androidx.compose.foundation.pager.PagerState] from scratch via [keyForContext], so
 * the page-index space and initial page are always those of the active context.
 *
 * The picker forces Browse and jumps the pager to the chosen chapter's global page (D-I-2).
 */
@Composable
fun ReaderRoute(
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScopeForRoute()
    val readerContext by viewModel.context.collectAsStateWithLifecycle()
    val initialPage by viewModel.initialPage.collectAsStateWithLifecycle()
    val externalApp by viewModel.externalApp.collectAsStateWithLifecycle()

    // A fresh PagerState per context (and per portion / initial page): switching Browse <-> Reading
    // changes the page-index space, so the pager must be rebuilt — wrapped in key(...) on the
    // context identity so a switch discards the old state (rememberPagerState has no key param).
    val pagerState =
        key(keyForContext(readerContext, initialPage)) {
            rememberPagerState(initialPage = initialPage) { readerContext.pageCount }
        }

    var showPicker by remember { mutableStateOf(false) }

    // Record the settled page as the in-session last-read.
    LaunchedEffect(pagerState, readerContext) {
        snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect(viewModel::onPageSettled)
    }

    // One-shot verse-tap destinations → the OS launcher (Custom Tab / MySword intent + BLB fallback).
    LaunchedEffect(Unit) {
        viewModel.openDestinationEvents.collectLatest { destination ->
            launchReadingDestination(context, destination)
        }
    }

    ReaderScreen(
        pagerState = pagerState,
        stateForPage = { page -> viewModel.uiStateForPage(page).collectAsStateValue() },
        externalApp = externalApp,
        onOpenPicker = { showPicker = true },
        onVerseTapped = { _, verseId -> viewModel.onVerseTapped(verseId) },
        onRetry = { page -> viewModel.retry(page) },
        modifier = modifier,
    )

    if (showPicker) {
        BookChapterPickerSheet(
            onChapterSelected = { book, chapter ->
                showPicker = false
                // D-I-2: a picker jump is a Browse action — leave any Reading context first, then
                // jump the (rebuilt) Browse pager to the chosen chapter.
                viewModel.resetToBrowse()
                scope.launch { pagerState.animateScrollToPage(GlobalChapterIndex.indexOf(book, chapter)) }
            },
            onDismiss = { showPicker = false },
        )
    }
}

/**
 * A stable identity for a context: Browse is one key; each Reading session is keyed by its portion
 * page so re-entering a different reading rebuilds the pager. The initial page is folded in so an
 * in-session restore to a different page rebuilds too.
 */
private fun keyForContext(
    context: ReaderContext,
    initialPage: Int,
): Any =
    when (context) {
        ReaderContext.Browse -> "browse:$initialPage"
        is ReaderContext.Reading -> "reading:${context.index.portionFirstGlobal}-${context.index.portionLastGlobal}"
    }

@Composable
private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateValue(): T {
    val v by collectAsStateWithLifecycle()
    return v
}

@Composable
private fun rememberCoroutineScopeForRoute() = androidx.compose.runtime.rememberCoroutineScope()
