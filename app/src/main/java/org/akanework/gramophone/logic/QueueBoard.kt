package org.akanework.gramophone.logic

import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import androidx.media3.common.BundleListRetriever
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player.REPEAT_MODE_OFF
import org.akanework.gramophone.logic.utils.MediaItemList

/**
 * Compatibility snapshot of a queue sent over session commands to the current UI. The durable
 * repository is the source of truth; this object carries no lifecycle, pinning, expiry, or
 * original-queue semantics.
 */
data class MultiQueueObject(
    val id: Long,
    val title: String,
    val queue: MutableList<MediaItem>,
    var startIndex: Int = C.INDEX_UNSET,
    var startPositionMs: Long = C.TIME_UNSET,
    var repeatMode: Int = REPEAT_MODE_OFF,
    var ended: Boolean = false,
) {
    fun getSize(): Int = queue.size

    fun getDuration(): Long = queue.sumOf { it.mediaMetadata.durationMs ?: 0L }

    fun toBundle(): Bundle = Bundle().apply {
        putLong("id", id)
        putString("title", title)
        putBinder("queue", MediaItemList(queue))
        putInt("startIndex", startIndex)
        putLong("startPositionMs", startPositionMs)
        putInt("repeatMode", repeatMode)
        putBoolean("ended", ended)
    }

    companion object {
        fun fromBundle(bundle: Bundle): MultiQueueObject {
            val binder = bundle.getBinder("queue")!!
            val queue = MediaItemList.getList(binder).toMutableList()
            return MultiQueueObject(
                id = bundle.getLong("id"),
                title = bundle.getString("title") ?: "",
                queue = queue,
                startIndex = bundle.getInt("startIndex", C.INDEX_UNSET),
                startPositionMs = bundle.getLong("startPositionMs", C.TIME_UNSET),
                repeatMode = bundle.getInt("repeatMode", REPEAT_MODE_OFF),
                ended = bundle.getBoolean("ended"),
            )
        }
    }
}

class MultiQueueList(val list: List<MultiQueueObject>) : Binder() {
    private val blr by lazy { BundleListRetriever(list.map { it.toBundle() }) }
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == FIRST_CALL_TRANSACTION) {
            return blr.transact(code, data, reply, flags)
        }
        return super.onTransact(code, data, reply, flags)
    }

    companion object {
        fun getList(binder: IBinder?): List<MultiQueueObject> {
            if (binder == null) return emptyList()
            if (binder is MultiQueueList) return binder.list
            return BundleListRetriever.getList(binder).map { MultiQueueObject.fromBundle(it) }
        }
    }
}
