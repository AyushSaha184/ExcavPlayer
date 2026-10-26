package com.excavplayer.domain.lifecycle

import com.excavplayer.core.result.ExcavResult
import com.excavplayer.domain.model.Folder
import com.excavplayer.domain.model.Video

/**
 * One owner for the video lifecycle: refresh, delete, and post-approval
 * cleanup. Every "a video was added, changed, or removed" flow routes
 * through here instead of being hand-threaded across modules.
 *
 * Plain Kotlin — no Android types except the wrapped sender handle inside
 * [DeleteOutcome.ApprovalRequired], which the data impl fills with the
 * platform IntentSender.
 */
interface VideoLifecycle {

    /**
     * The full refresh ritual: purge transient/corrupted entries, then sync
     * with MediaStore (and the filesystem scan) under one call.
     */
    suspend fun refresh(force: Boolean): ExcavResult<Int>

    /**
     * Deletes videos from disk + database. MediaStore-backed videos on
     * Android R+ cannot be deleted directly — the system approval dialog
     * must run first — so the outcome carries the approval handle.
     */
    suspend fun deleteVideos(videos: List<Video>): DeleteOutcome

    /** Post-approval cleanup: evict thumbnails + remove confirmed rows. */
    suspend fun finalizeApprovedDeletion(videoIds: List<String>): ExcavResult<Unit>

    /** Purge transient/SAF/private entries without syncing. */
    suspend fun purgeCorruptedEntries()
}

/** Result of a delete attempt, as plain data the UI can branch on. */
sealed interface DeleteOutcome {

    /**
     * The system approval dialog must run before deletion is final.
     * [senderHandle] is the platform [android.content.IntentSender] at
     * runtime; tests may store any marker.
     */
    data class ApprovalRequired(
        val senderHandle: Any,
        val videoIds: List<String>
    ) : DeleteOutcome

    /** Every video deleted immediately (SAF / file paths / pre-R content). */
    data class Deleted(val videoIds: List<String>) : DeleteOutcome

    /** Mixed success from immediate deletes. */
    data class Partial(
        val deletedIds: List<String>,
        val failedIds: List<String>
    ) : DeleteOutcome
}
