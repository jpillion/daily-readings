package com.jpillion.dailyreadingplanner.ui.navigation

import com.jpillion.dailyreadingplanner.domain.model.Portion
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * VD-T5 (D-D-1) — the cross-graph reading-tap handoff seam. A tap on a Schedule reading whose
 * provider is the in-app reader (`BibleProvider.IN_APP`) must open that whole [Portion] in the
 * Bible graph's reader. The two graphs have separate ViewModel stores, so the portion can't
 * ride a route argument cleanly across a tab switch; instead the Schedule side publishes the
 * pending portion here, the root switches to the Bible tab, and the reader consumes it.
 *
 * `@ActivityRetainedScoped` so a single instance is shared by `DayReadingsViewModel` (producer)
 * and `ReaderViewModel`/`ReaderRoute` (consumer) for the activity's lifetime, surviving config
 * change. [consume] is single-shot: it returns the pending portion and clears it, so a config
 * change or a manual Bible-tab visit does not re-trigger the open.
 */
@ActivityRetainedScoped
class ReaderHandoff
    @Inject
    constructor() {
        private val _pending = MutableStateFlow<Portion?>(null)

        /** The pending portion to open in the reader, or null. Observed by the reader on the Bible tab. */
        val pending: StateFlow<Portion?> = _pending.asStateFlow()

        /** Publish a portion to open in the in-app reader (called by the Schedule tap handoff). */
        fun request(portion: Portion) {
            _pending.value = portion
        }

        /** Single-shot read: returns and clears the pending portion (null if none). */
        fun consume(): Portion? {
            val p = _pending.value
            _pending.value = null
            return p
        }
    }
