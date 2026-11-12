/*
 *     Copyright (C) 2026 The Gramophone authors
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.akanework.gramophone.ui.adapters

import android.content.Context
import android.content.ContextWrapper
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.edit
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.gramophoneApplication
import org.akanework.gramophone.logic.queue.QueueDeduplicationRules
import org.akanework.gramophone.logic.setMediaItemsWithTitle
import org.akanework.gramophone.logic.shuffleQueue
import org.akanework.gramophone.logic.ui.DecorPanelRules
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.components.AddToQueueSheet
import org.akanework.gramophone.ui.fragments.AdapterFragment
import org.akanework.gramophone.ui.getAdapterType

/**
 * Consolidates button wiring, media actions (add to queue, play all, shuffle all),
 * sorting menu, and navigation behavior for decor panels across all library views.
 */
class DecorPanelBinder(
    private val context: Context,
    private val adapter: AdapterFragment.BaseInterface<*>,
    private val pluralStr: Int,
    private val onSortPressed: (PopupMenu) -> Unit = {},
    private val onExtraMenuItem: (MenuItem) -> Boolean = { false },
    private val onGoToPlayingSong: (() -> Unit)? = null,
    private val jumpUpSupplier: () -> (() -> Int)? = { null },
    private val jumpDownSupplier: () -> (() -> Int)? = { null },
    private val onScrollToPosition: ((Int) -> Unit)? = null,
) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    private fun resolveActivity(): MainActivity? {
        var ctx: Context? = context
        while (ctx is ContextWrapper) {
            if (ctx is MainActivity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    fun bind(holder: BaseDecorAdapter.ViewHolder) {
        val count = adapter.itemCountForDecor
        holder.counter.text = context.resources.getQuantityString(pluralStr, count, count)
        if (onGoToPlayingSong != null) {
            holder.counter.setOnClickListener {
                onGoToPlayingSong.invoke()
            }
        } else {
            holder.counter.setOnClickListener(null)
        }

        bindMediaActions(holder)
        bindSortButton(holder)
        bindJumpButtons(holder)
    }

    private fun bindMediaActions(holder: BaseDecorAdapter.ViewHolder) {
        val mediaProvider = adapter as? DecorMediaProvider
        val isSupported = mediaProvider?.supportsMediaActions == true
        val mediaVisibility = if (isSupported) View.VISIBLE else View.GONE

        holder.addToQueue.visibility = mediaVisibility
        holder.playAll.visibility = mediaVisibility
        holder.shuffleAll.visibility = mediaVisibility

        if (mediaProvider != null && isSupported) {
            holder.addToQueue.setOnClickListener {
                val activity = resolveActivity() ?: return@setOnClickListener
                val songs = mediaProvider.getRepresentedSongs()
                if (DecorPanelRules.canAddToQueue(songs.size)) {
                    AddToQueueSheet(activity, songs).show()
                }
            }
            holder.playAll.setOnClickListener {
                playMedia(mediaProvider, shuffle = false)
            }
            holder.shuffleAll.setOnClickListener {
                playMedia(mediaProvider, shuffle = true)
            }
        } else {
            holder.addToQueue.setOnClickListener(null)
            holder.playAll.setOnClickListener(null)
            holder.shuffleAll.setOnClickListener(null)
        }
    }

    private fun playMedia(provider: DecorMediaProvider, shuffle: Boolean) {
        val activity = resolveActivity() ?: return
        val controller = activity.getPlayer() ?: return
        val songs = provider.getRepresentedSongs()
        if (shuffle) {
            ShortcutManagerCompat.reportShortcutUsed(context, "shuffle_all")
        }
        val lifecycleScope = (activity as? LifecycleOwner)?.lifecycleScope ?: return
        lifecycleScope.launch {
            val source = provider.queueSourceFlow?.first()
            val rawTitle = provider.queueTitleFlow?.first() ?: ""
            val title = DecorPanelRules.formatQueueTitle(
                title = rawTitle,
                shuffle = shuffle,
                applyShuffledTemplate = provider.useShuffledTitleTemplate,
                shuffledTemplate = { context.getString(R.string.shuffled, it) },
            )
            controller.setMediaItemsWithTitle(
                songs,
                title = title,
                repeatMode = REPEAT_MODE_OFF,
                original = !shuffle,
                source = source,
            )
            if (songs.isNotEmpty()) {
                controller.prepare()
                controller.play()
            }
        }
    }

    private fun bindSortButton(holder: BaseDecorAdapter.ViewHolder) {
        val canSort = adapter.sortType.value != Sorter.Type.None || adapter.canChangeLayout
        holder.sortButton.visibility = if (canSort) View.VISIBLE else View.GONE
        if (canSort) {
            holder.sortButton.setOnClickListener { view ->
                showSortMenu(view)
            }
        } else {
            holder.sortButton.setOnClickListener(null)
        }
    }

    private fun showSortMenu(anchor: View) {
        val popupMenu = PopupMenu(context, anchor)
        popupMenu.inflate(R.menu.sort_menu)
        val buttonMap = mapOf(
            Pair(R.id.natural, Sorter.Type.NaturalOrder),
            Pair(R.id.name, Sorter.Type.ByTitleAscending),
            Pair(R.id.artist, Sorter.Type.ByArtistAscending),
            Pair(R.id.artist_year, Sorter.Type.ByArtistYearAscending),
            Pair(R.id.album, Sorter.Type.ByAlbumTitleAscending),
            Pair(R.id.album_artist, Sorter.Type.ByAlbumArtistAscending),
            Pair(R.id.album_artist_year, Sorter.Type.ByAlbumArtistYearAscending),
            Pair(R.id.album_year, Sorter.Type.ByAlbumYearDescending),
            Pair(R.id.size, Sorter.Type.BySizeDescending),
            Pair(R.id.add_date, Sorter.Type.ByAddDateDescending),
            Pair(R.id.release_date, Sorter.Type.ByReleaseDateDescending),
            Pair(R.id.mod_date, Sorter.Type.ByModifiedDateDescending),
            Pair(R.id.file_path, Sorter.Type.ByFilePathAscending)
        )
        val layoutMap = mapOf(
            Pair(R.id.list, BaseAdapter.LayoutType.LIST),
            Pair(R.id.compact_list, BaseAdapter.LayoutType.COMPACT_LIST),
            Pair(R.id.grid, BaseAdapter.LayoutType.GRID),
            Pair(R.id.compact_grid, BaseAdapter.LayoutType.COMPACT_GRID)
        )
        buttonMap.forEach {
            popupMenu.menu.findItem(it.key).isVisible = adapter.sortTypes.contains(it.value)
        }
        layoutMap.forEach {
            popupMenu.menu.findItem(it.key).isVisible = adapter.canChangeLayout
        }
        popupMenu.menu.findItem(R.id.display).isVisible = adapter.canChangeLayout

        val currentSort = adapter.sortType.value
        val activeEntry = buttonMap.entries.find { it.value == currentSort || Sorter.Type.inverse(it.value) == currentSort }

        if (activeEntry == null) {
            throw IllegalStateException("Invalid sortType ${adapter.sortType.value.name}")
        }

        popupMenu.menu.findItem(activeEntry.key).isChecked = true

        if (adapter.canChangeLayout) {
            when (adapter.layoutType) {
                in layoutMap.values -> {
                    popupMenu.menu.findItem(
                        layoutMap.entries
                            .first { it.value == adapter.layoutType }.key
                    ).isChecked = true
                }

                else -> throw IllegalStateException("Invalid layoutType ${adapter.layoutType?.name}")
            }
        }

        val reverseItem = popupMenu.menu.findItem(R.id.reverse_order)
        val inverse = Sorter.Type.inverse(adapter.sortType.value)
        if (inverse == null) {
            reverseItem.isVisible = false
        } else {
            reverseItem.isChecked = currentSort != activeEntry.value && currentSort != Sorter.Type.None
        }

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                in buttonMap.keys -> {
                    if (!menuItem.isChecked) {
                        val baseType = buttonMap[menuItem.itemId]!!
                        val reverse = prefs.getBoolean("S" + getAdapterType(adapter) +
                                "_reverse_" + baseType, false)
                        val targetType = if (!reverse) baseType else
                            Sorter.Type.inverse(baseType) ?: baseType
                        reverseItem.isChecked = reverse
                        adapter.sort(targetType)
                        menuItem.isChecked = true
                        prefs.edit {
                            putString(
                                "S" + getAdapterType(adapter).toString(),
                                targetType.toString()
                            )
                        }
                    }
                    true
                }

                in layoutMap.keys -> {
                    if (!menuItem.isChecked) {
                        adapter.layoutType = layoutMap[menuItem.itemId]!!
                        menuItem.isChecked = true
                        prefs.edit {
                            putString(
                                "L" + getAdapterType(adapter).toString(),
                                layoutMap[menuItem.itemId].toString()
                            )
                        }
                    }
                    true
                }

                R.id.reverse_order -> {
                    menuItem.isChecked = !menuItem.isChecked
                    val activeId = buttonMap.entries.first {
                        it.value == adapter.sortType.value || Sorter.Type.inverse(it.value) == adapter.sortType.value
                    }.key
                    val baseType = buttonMap[activeId]!!
                    val targetType = if (menuItem.isChecked) Sorter.Type.inverse(baseType) ?: baseType else baseType
                    adapter.sort(targetType)
                    prefs.edit {
                        putBoolean("S" + getAdapterType(adapter) + "_reverse_" + baseType,
                            menuItem.isChecked)
                        putString(
                            "S" + getAdapterType(adapter).toString(),
                            targetType.toString()
                        )
                    }
                    true
                }

                else -> onExtraMenuItem(menuItem)
            }
        }
        onSortPressed(popupMenu)
        popupMenu.show()
    }

    private fun bindJumpButtons(holder: BaseDecorAdapter.ViewHolder) {
        val jumpUp = jumpUpSupplier()
        holder.jumpUp.visibility = if (jumpUp != null) View.VISIBLE else View.GONE
        holder.jumpUp.setOnClickListener(if (jumpUp != null) {
            View.OnClickListener {
                jumpUp.invoke().let { onScrollToPosition?.invoke(it) }
            }
        } else null)

        val jumpDown = jumpDownSupplier()
        holder.jumpDown.visibility = if (jumpDown != null) View.VISIBLE else View.GONE
        holder.jumpDown.setOnClickListener(if (jumpDown != null) {
            View.OnClickListener {
                jumpDown.invoke().let { onScrollToPosition?.invoke(it) }
            }
        } else null)
    }

    fun unbind(holder: BaseDecorAdapter.ViewHolder) {
        holder.addToQueue.setOnClickListener(null)
        holder.playAll.setOnClickListener(null)
        holder.shuffleAll.setOnClickListener(null)
        holder.sortButton.setOnClickListener(null)
        holder.jumpUp.setOnClickListener(null)
        holder.jumpDown.setOnClickListener(null)
        holder.counter.setOnClickListener(null)
    }
}
