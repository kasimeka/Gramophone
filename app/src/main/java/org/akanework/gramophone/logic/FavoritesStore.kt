package org.akanework.gramophone.logic

import androidx.media3.common.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.akanework.gramophone.logic.queue.M3uImport
import org.akanework.gramophone.logic.queue.QueueExportSink
import org.akanework.gramophone.logic.queue.SystemQueue
import org.akanework.gramophone.logic.queue.SystemQueueDao
import org.akanework.gramophone.logic.queue.SystemQueueKind
import org.akanework.gramophone.logic.utils.MediaRowCodec
import java.util.UUID

/**
 * Favourites, stored as the Favourites system queue. Rows are kept in memory and written through
 * [dao] under [lock]; every change is handed to [exports] so the m3u8 mirror follows it.
 */
class FavoritesStore(
    private val scope: CoroutineScope,
    private val dao: SystemQueueDao,
    private val exports: QueueExportSink,
    private val songs: suspend () -> List<MediaItem>,
    private val legacyPlaylist: suspend () -> String?,
) {
    private val lock = Mutex()
    private val rowsState = MutableStateFlow<List<String>>(emptyList())

    val ids: StateFlow<Set<String>> = rowsState
        .map { rows -> rows.mapNotNullTo(HashSet()) { mediaIdOf(it) } }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    init {
        scope.launch {
            lock.withLock {
                rowsState.value = withContext(Dispatchers.IO) {
                    dao.load(SystemQueueKind.FAVOURITES)?.rows.orEmpty()
                }
            }
        }
    }

    fun isFavorite(mediaId: String): Boolean = mediaId in ids.value

    /**
     * [known] are the songs the caller already holds. Only ids missing from them are looked up in
     * the library, and that lookup runs outside [lock] so a slow library read cannot stall writes.
     */
    fun setFavorite(mediaIds: Collection<String>, favorite: Boolean, known: Collection<MediaItem> = emptyList()) {
        scope.launch {
            val wanted = mediaIds.toSet()
            val items = if (favorite) {
                val missing = wanted - known.mapTo(HashSet()) { it.mediaId }
                val fromLibrary = if (missing.isEmpty()) emptyMap() else
                    songs().filter { it.mediaId in missing }.associateBy { it.mediaId }
                known.filter { it.mediaId in wanted } + fromLibrary.values
            } else emptyList()
            lock.withLock { apply(wanted, favorite, items) }
        }
    }

    /** The stored Favourites list, for the startup mirror check. */
    suspend fun snapshot(): SystemQueue? = lock.withLock {
        withContext(Dispatchers.IO) { dao.load(SystemQueueKind.FAVOURITES) }
    }

    /** Record the mirror name the exporter wrote for [id]. Ignores ids this store does not own. */
    fun recordExport(id: UUID, fileName: String) {
        scope.launch(Dispatchers.IO) { dao.setExportFileName(id, fileName) }
    }

    /** Import the playlist-era Favourites file once, when the Favourites list has never been saved. */
    suspend fun importLegacyPlaylistIfNeeded() {
        val saved = lock.withLock {
            withContext(Dispatchers.IO) { dao.load(SystemQueueKind.FAVOURITES) != null }
        }
        if (saved) return
        val text = legacyPlaylist() ?: return
        val byPath = songs().mapNotNull { song ->
            song.getFile()?.absolutePath?.let { it to song }
        }.toMap()
        val matches = M3uImport.resolve(M3uImport.entries(text)) { byPath[it] }.matches
        if (matches.isEmpty()) return
        lock.withLock {
            if (withContext(Dispatchers.IO) { dao.load(SystemQueueKind.FAVOURITES) } != null) return
            save(matches.map(MediaRowCodec::encode))
        }
    }

    private suspend fun apply(wanted: Set<String>, favorite: Boolean, items: List<MediaItem>) {
        val current = rowsState.value
        if (favorite) {
            val present = current.mapNotNullTo(HashSet()) { mediaIdOf(it) }
            val added = items
                .filter { it.mediaId in wanted && it.mediaId !in present }
                .distinctBy { it.mediaId }
                .map(MediaRowCodec::encode)
            if (added.isEmpty()) return
            save(current + added)
        } else {
            // Rows that cannot be decoded are not favourites of any song, so they stay.
            val kept = current.filter { row -> mediaIdOf(row)?.let { it !in wanted } ?: true }
            if (kept.size == current.size) return
            save(kept)
        }
    }

    private suspend fun save(rows: List<String>) {
        val now = System.currentTimeMillis()
        withContext(Dispatchers.IO) { dao.save(SystemQueueKind.FAVOURITES, rows, now) }
        rowsState.value = rows
        exports.enqueue(
            SystemQueue(
                id = SystemQueueKind.FAVOURITES.id,
                title = SystemQueueKind.FAVOURITES.title,
                kind = SystemQueueKind.FAVOURITES,
                rows = rows,
                updatedAt = now,
                exportFileName = null,
            )
        )
    }

    private fun mediaIdOf(row: String): String? = MediaRowCodec.decode(row)?.mediaId
}
