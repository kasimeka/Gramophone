package org.akanework.gramophone.logic

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Persists the user's Favourites as a set of media IDs. This replaces the old MediaStore playlist
 * implementation; it is a plain preference-backed store, not a playlist entity.
 */
class FavoritesStore(context: Context) : SharedPreferences.OnSharedPreferenceChangeListener {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    private val _ids = MutableStateFlow(read())
    val ids: StateFlow<Set<String>> = _ids

    init {
        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    fun isFavorite(mediaId: String): Boolean = mediaId in _ids.value

    fun setFavorite(mediaIds: Collection<String>, favorite: Boolean) {
        val updated = _ids.value.toMutableSet().apply {
            if (favorite) addAll(mediaIds) else removeAll(mediaIds.toSet())
        }
        write(updated)
    }

    private fun read(): Set<String> = prefs.getStringSet(KEY, emptySet())?.toSet() ?: emptySet()

    private fun write(ids: Set<String>) {
        _ids.value = ids
        prefs.edit { putStringSet(KEY, ids.toSet()) }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == KEY) _ids.value = read()
    }

    companion object {
        private const val KEY = "gramophone_favourite_media_ids"
    }
}
