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
 * I2 (D-I-2, OQ-A) — this seam ALSO carries the opposite signal: tapping the Bible **tab** in the
 * nav bar (not a reading tap) must reset the reader to plain single-chapter Browse. The nav bar
 * raises [requestBrowse]; the reader consumes it via [consumeBrowseRequest]. The two signals are
 * mutually exclusive — a reading tap sets [pending] (→ Reading), a bare tab tap sets the browse
 * request (→ Browse) — so the reader can always tell which entry it is.
 *
 * `@ActivityRetainedScoped` so a single instance is shared by `DayReadingsViewModel` (producer),
 * the nav bar (browse-reset producer), and `ReaderViewModel`/`ReaderRoute` (consumer) for the
 * activity's lifetime, surviving config change. [consume] / [consumeBrowseRequest] are single-shot,
 * so a config change does not re-trigger the open or the reset.
 */
@ActivityRetainedScoped
class ReaderHandoff
    @Inject
    constructor() {
        private val _pending = MutableStateFlow<Portion?>(null)

        /** The pending portion to open in the reader, or null. Observed by the reader on the Bible tab. */
        val pending: StateFlow<Portion?> = _pending.asStateFlow()

        private val _browseRequested = MutableStateFlow(false)

        /**
         * Raised when the user taps the Bible **tab** (not a reading): the reader must reset to
         * single-chapter Browse (D-I-2). Observed by the reader; cleared by [consumeBrowseRequest].
         */
        val browseRequested: StateFlow<Boolean> = _browseRequested.asStateFlow()

        /** Publish a portion to open in the in-app reader (called by the Schedule tap handoff). */
        fun request(portion: Portion) {
            // A reading tap supersedes any stale browse-reset request.
            _browseRequested.value = false
            _pending.value = portion
        }

        /** Single-shot read: returns and clears the pending portion (null if none). */
        fun consume(): Portion? {
            val p = _pending.value
            _pending.value = null
            return p
        }

        /** The nav bar tapped the Bible tab: ask the reader to reset to Browse (D-I-2). */
        fun requestBrowse() {
            // A pending reading open supersedes a bare tab tap, so don't reset over a fresh handoff.
            if (_pending.value != null) return
            _browseRequested.value = true
        }

        /** Single-shot read: true if a browse reset was requested; clears the flag. */
        fun consumeBrowseRequest(): Boolean {
            val requested = _browseRequested.value
            _browseRequested.value = false
            return requested
        }
    }
