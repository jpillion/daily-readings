package com.jpillion.dailyreadingplanner.bible.ui.picker

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jpillion.dailyreadingplanner.data.reference.Book

/**
 * VC-T4 — M3 bottom-sheet wrapper over the stateless [BookChapterPicker]. Dismissing the sheet
 * (scrim/back) calls [onDismiss]; choosing a chapter calls [onChapterSelected] (the caller
 * dismisses + navigates the reader). The sheet uses near-full height so the long OT/NT book list
 * and the chapter grid have room.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookChapterPickerSheet(
    onChapterSelected: (Book, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("book-chapter-picker-sheet"),
    ) {
        BookChapterPicker(
            onChapterSelected = onChapterSelected,
            modifier = Modifier.fillMaxHeight(0.9f),
        )
    }
}
