package com.jpillion.dailyreadingplanner.bible.ui.reader

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
 * H2 (D-H-2) — stateful reader entry. Owns [ReaderViewModel], the chapter [rememberPagerState] over
 * the whole canon ([GlobalChapterIndex.TOTAL_CHAPTERS] pages), the picker sheet, and the verse-tap
 * launch side-effect. The pager opens on the VM's [ReaderViewModel.initialPage] (in-session last-read,
 * or a Schedule-tap portion handoff — D-H-7); the picker jumps the pager to the chosen chapter's page.
 */
@Composable
fun ReaderRoute(
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScopeForRoute()
    val initialPage by viewModel.initialPage.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(initialPage = initialPage) { GlobalChapterIndex.TOTAL_CHAPTERS }
    var showPicker by remember { mutableStateOf(false) }

    // A portion handoff arriving while the reader is alive bumps initialPage: animate there.
    LaunchedEffect(initialPage) {
        if (pagerState.currentPage != initialPage) pagerState.animateScrollToPage(initialPage)
    }

    // Record the settled page as the in-session last-read.
    LaunchedEffect(pagerState) {
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
        onOpenPicker = { showPicker = true },
        onVerseTapped = { _, verseId -> viewModel.onVerseTapped(verseId) },
        onRetry = { page -> viewModel.retry(page) },
        modifier = modifier,
    )

    if (showPicker) {
        BookChapterPickerSheet(
            onChapterSelected = { book, chapter ->
                showPicker = false
                scope.launch { pagerState.animateScrollToPage(GlobalChapterIndex.indexOf(book, chapter)) }
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateValue(): T {
    val v by collectAsStateWithLifecycle()
    return v
}

@Composable
private fun rememberCoroutineScopeForRoute() = androidx.compose.runtime.rememberCoroutineScope()
