package com.jpillion.dailyreadingplanner.ui.navigation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * I4 (D-I-2, OQ-A) — root-level glue so the Bible **tab** click can raise the reader's
 * "reset to Browse" signal on the shared [ReaderHandoff] (which is `@ActivityRetainedScoped`, hence
 * not directly accessible from the [RootScaffold] composable). A reading-tap handoff goes the other
 * way (`ReaderHandoff.request`) and is unaffected — the two are mutually exclusive in the seam.
 */
@HiltViewModel
class RootViewModel
    @Inject
    constructor(
        private val readerHandoff: ReaderHandoff,
    ) : ViewModel() {
        /** The user tapped the Bible tab in the nav bar: reset the reader to single-chapter Browse. */
        fun onBibleTabSelected() {
            readerHandoff.requestBrowse()
        }
    }
