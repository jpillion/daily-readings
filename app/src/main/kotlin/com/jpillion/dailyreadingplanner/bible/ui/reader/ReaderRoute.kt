package com.jpillion.dailyreadingplanner.bible.ui.reader

import android.os.Build
import android.widget.Toast
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
import com.jpillion.dailyreadingplanner.R
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
 *
 * **Q2 side effects.** Two one-shot event streams are performed here, by the same idiom: resolved
 * verse-open destinations go to the OS launcher, and clipboard payloads built by the VM (D-Q-4) go
 * to the system clipboard, with the sub-API-33 "Copied" toast (D-Q-5). The verse *selection* state
 * is read from the VM and passed down; the verse *menu* is the screen's own state (D-Q-2).
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
    val versionState by viewModel.versionState.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()

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

    // One-shot verse-open destinations → the OS launcher (Custom Tab / MySword intent + BLB
    // fallback). Q2: these now arrive from the verse menu's "Open in <app>" item, not from a bare tap.
    LaunchedEffect(Unit) {
        viewModel.openDestinationEvents.collectLatest { destination ->
            launchReadingDestination(context, destination)
        }
    }

    // D-Q-4 — one-shot clipboard payloads: the VM builds the string, the route performs the side
    // effect. Plain collect (not collectLatest): every copy must land, none may be cancelled by the
    // next. D-Q-5: below API 33 the app confirms with a short toast; on 33+ the system posts its
    // own clipboard confirmation and a second one would double up.
    LaunchedEffect(Unit) {
        viewModel.copyEvents.collect { text ->
            copyVerseTextToClipboard(context, text)
            if (shouldShowCopyConfirmation(Build.VERSION.SDK_INT)) {
                Toast.makeText(context, R.string.verse_copied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    ReaderScreen(
        pagerState = pagerState,
        stateForPage = { page -> viewModel.uiStateForPage(page).collectAsStateValue() },
        externalApp = externalApp,
        versionState = versionState,
        selection = selection,
        onOpenPicker = { showPicker = true },
        onSelectVersion = viewModel::selectVersion,
        onVerseLongPressed = viewModel::onVerseLongPressed,
        onVerseSelectionToggled = viewModel::onVerseSelectionToggled,
        onOpenVerseExternally = viewModel::openVerseExternally,
        onCopyVerse = viewModel::copyVerse,
        onCopySelection = viewModel::copySelection,
        onClearSelection = viewModel::onSelectionCleared,
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
