package io.github.mangi.eta.agent.runtime

import androidx.annotation.StringRes
import io.github.mangi.eta.R

/** Semantic identity is independent of translated preview copy. */
internal enum class PreviewHintType(@param:StringRes val resourceId: Int, val refreshesWithStatus: Boolean = false) {
    Connecting(R.string.preview_hint_connecting),
    WaitingFrame(R.string.preview_hint_waiting_frame),
    CommandPending(R.string.preview_hint_command_pending),
    FrameUnavailable(R.string.preview_hint_frame_unavailable),
    ManualReady(R.string.preview_hint_manual_ready),
    ConnectionLost(R.string.preview_hint_connection_lost),
    Acquiring(R.string.preview_hint_acquiring),
    Acquired(R.string.preview_hint_acquired),
    TakeoverPending(R.string.preview_hint_takeover_pending),
    ReleasedActive(R.string.preview_hint_released_active, true),
    ReleasedInactive(R.string.preview_hint_released_inactive),
    TaskDraining(R.string.preview_hint_task_draining),
    CommandBusy(R.string.preview_hint_command_busy),
    Refreshing(R.string.preview_hint_refreshing),
    OtherOwner(R.string.preview_hint_other_owner),
    MovedManual(R.string.preview_hint_moved_manual),
    PageReleased(R.string.preview_hint_page_released),
    InputNotReady(R.string.preview_hint_input_not_ready),
    LeaseExpired(R.string.preview_hint_lease_expired),
    Failure(R.string.preview_hint_failure),
    ReadOnlyCompleted(R.string.preview_hint_read_only_completed, true),
    ReadOnlyPaused(R.string.preview_hint_read_only_paused, true),
    ReadOnlyRunning(R.string.preview_hint_read_only_running, true),
    ReadOnlyPlain(R.string.preview_hint_read_only_plain, true),
}

internal data class PreviewHintMessage(val type: PreviewHintType, val text: String)
