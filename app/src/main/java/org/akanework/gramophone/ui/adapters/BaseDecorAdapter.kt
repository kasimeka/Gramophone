/*
 *     Copyright (C) 2024 Akane Foundation
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
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.ui.ItemHeightHelper
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.logic.ui.QuickLinearSmoothScroller
import org.akanework.gramophone.ui.fragments.AdapterFragment

open class BaseDecorAdapter<T : AdapterFragment.BaseInterface<*>>(
    protected val adapter: T,
    private val pluralStr: Int
) : MyRecyclerView.Adapter<BaseDecorAdapter.ViewHolder>(), ItemHeightHelper {

    protected val context: Context = adapter.context
    private val dpHeight = context.resources.getDimensionPixelSize(R.dimen.decor_height)
    private var recyclerView: MyRecyclerView? = null
    var jumpUpPos: (() -> Int)? = null
    var jumpDownPos: (() -> Int)? = null
    var offsetPos: (() -> Int)? = null

    private val binder = DecorPanelBinder(
        context = context,
        adapter = adapter,
        pluralStr = pluralStr,
        onSortPressed = { onSortButtonPressed(it) },
        onExtraMenuItem = { onExtraMenuButtonPressed(it) },
        onGoToPlayingSong = if (adapter is SongAdapter) { { goToPlayingSong() } } else null,
        jumpUpSupplier = { jumpUpPos },
        jumpDownSupplier = { jumpDownPos },
        onScrollToPosition = { scrollToViewPosition(it) },
    )

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val view = adapter.layoutInflater.inflate(R.layout.general_decor, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        binder.bind(holder)
    }

    fun goToPlayingSong() {
        if (adapter is SongAdapter) {
            adapter.getPlayingSong()?.let { scrollToViewPosition(
                (offsetPos?.invoke() ?: 0) + itemCount + it) }
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        binder.unbind(holder)
        super.onViewRecycled(holder)
    }

    override fun onAttachedToRecyclerView(recyclerView: MyRecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: MyRecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this.recyclerView = null
    }

    private fun scrollToViewPosition(pos: Int) {
        val smoothScroller = object : QuickLinearSmoothScroller(context) {
            override fun calculateDtToFit(
                viewStart: Int,
                viewEnd: Int,
                boxStart: Int,
                boxEnd: Int,
                snapPreference: Int
            ): Int {
                return (super.calculateDtToFit(
                    viewStart,
                    viewEnd,
                    boxStart,
                    boxEnd,
                    snapPreference
                )) + (viewEnd - viewStart) / 2
            }

            override fun getVerticalSnapPreference(): Int {
                return SNAP_TO_START
            }
        }
        smoothScroller.targetPosition = pos
        recyclerView?.startSmoothScrollCompat(smoothScroller)
    }

    protected open fun onSortButtonPressed(popupMenu: PopupMenu) {}
    protected open fun onExtraMenuButtonPressed(menuItem: MenuItem): Boolean = false

    override fun getItemCount(): Int = 1
    override fun getItemViewType(position: Int): Int = R.layout.general_decor

    class ViewHolder(
        view: View,
    ) : RecyclerView.ViewHolder(view) {
        val addToQueue: MaterialButton = view.findViewById(R.id.add_to_queue)
        val sortButton: MaterialButton = view.findViewById(R.id.sort)
        val playAll: MaterialButton = view.findViewById(R.id.play_all)
        val shuffleAll: MaterialButton = view.findViewById(R.id.shuffle_all)
        val jumpUp: MaterialButton = view.findViewById(R.id.jumpUp)
        val jumpDown: MaterialButton = view.findViewById(R.id.jumpDown)
        val counter: TextView = view.findViewById(R.id.song_counter)
    }

    fun updateSongCounter() {
        notifyItemChanged(0)
    }

    override fun getItemHeightFromZeroTo(to: Int): Int {
        return if (to > 0) dpHeight else 0
    }
}
