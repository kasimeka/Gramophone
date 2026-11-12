package org.akanework.gramophone.ui.fragments.compose

import org.akanework.gramophone.logic.MultiQueueObject

// Legacy multiqueue/playlist sheet UI removed with playlists. Kept as stub so constants and
// incidental references remain compilable during Phase 3 cleanup.
open class MqState {
    companion object {
        const val CLIENT_QB_REFRESH_ALL = "qb_refresh_all"
        const val CLIENT_QB_REFRESH_ITEM = "qb_refresh_queue"
        const val CLIENT_QB_REFRESH_QUEUES = "qb_refresh_queues"
        const val CLIENT_QB_REFRESH_LIST = "qb_refresh_songs"
        const val CLIENT_QB_REFRESH_CLEAR = "qb_refresh_clear"
    }

    var isEditAllowed: Boolean = false
    val activeQueue: Pair<MutableList<Int>, MultiQueueObject>? = null

    fun renameQueue(queueId: Long, title: String, dryRun: Boolean): Boolean = false
    fun addToQueue(id: Long) {}
    fun playNext(id: Long) {}
}
