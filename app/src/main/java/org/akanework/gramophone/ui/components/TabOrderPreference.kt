/*
 *     Copyright (C) 2025 nift4
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

package org.akanework.gramophone.ui.components

import android.content.Context
import android.content.res.TypedArray
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.preference.DialogPreference
import androidx.preference.PreferenceDialogFragmentCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.DOWN
import androidx.recyclerview.widget.ItemTouchHelper.UP
import androidx.recyclerview.widget.RecyclerView
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.ui.adapters.ViewPager2Adapter.Companion.Tab
import org.akanework.gramophone.ui.adapters.ViewPager2Adapter.Companion.mapSettingToTabList
import org.akanework.gramophone.ui.adapters.ViewPager2Adapter.Companion.mapTabListToSetting
import org.akanework.gramophone.ui.components.TabOrderPreference.TabOrderAdapter.TabOrderViewHolder

class TabOrderPreference(context: Context, attrs: AttributeSet) : DialogPreference(context, attrs) {
    private var _value = ""
    var value
        get() = _value
        set(new) {
            _value = new
            persistString(new)
        }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return a.getString(index) ?: ""
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        _value = getPersistedString((defaultValue as String?) ?: "")
    }

    override fun getDialogLayoutResource(): Int {
        return R.layout.tab_order_dialog
    }

    class TabOrderDialog : PreferenceDialogFragmentCompat() {
        private val adapter by lazy {
            TabOrderAdapter((preference as TabOrderPreference).value) { viewHolder ->
                itemTouchHelper?.startDrag(viewHolder)
            }
        }
        private var itemTouchHelper: ItemTouchHelper? = null

        override fun onBindDialogView(view: View) {
            super.onBindDialogView(view)
            val recyclerView = view.findViewById<MyRecyclerView>(R.id.recyclerview)
            recyclerView.adapter = adapter
            itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(UP or DOWN, 0) {
                override fun isLongPressDragEnabled(): Boolean = false

                override fun getDragDirs(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder
                ): Int {
                    return if (adapter.canDrag(viewHolder.bindingAdapterPosition)) UP or DOWN else 0
                }

                override fun isItemViewSwipeEnabled(): Boolean = false

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    val from = viewHolder.bindingAdapterPosition
                    val to = target.bindingAdapterPosition
                    if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                    if (!adapter.canMove(from, to)) return false
                    adapter.value.add(to, adapter.value.removeAt(from))
                    adapter.notifyItemMoved(from, to)
                    return true
                }

                override fun onSwiped(
                    viewHolder: RecyclerView.ViewHolder,
                    direction: Int
                ) {
                    throw IllegalStateException()
                }
            }).also { it.attachToRecyclerView(recyclerView) }
        }

        override fun onDestroyView() {
            itemTouchHelper = null
            super.onDestroyView()
        }

        override fun onDialogClosed(positiveResult: Boolean) {
            if (positiveResult) {
                val newValue = mapTabListToSetting(adapter.value)
                (preference as TabOrderPreference).let {
                    if (it.callChangeListener(newValue)) {
                        it.value = newValue
                    }
                }
            }
        }

        companion object {
            fun newInstance(key: String): TabOrderDialog {
                return TabOrderDialog().apply {
                    arguments = Bundle().apply {
                        putString(ARG_KEY, key)
                    }
                }
            }
        }
    }

    class TabOrderAdapter(
        initialValue: String,
        private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
    ) : MyRecyclerView.Adapter<TabOrderViewHolder>() {
        val value = mapSettingToTabList(initialValue).toMutableList()

        private val separatorIndex: Int
            get() = value.indexOf(null)

        /** The Queues tab (persisted as Playlist) is permanent and cannot be hidden. */
        private fun isPermanent(tab: Tab?): Boolean = tab == Tab.Playlist

        fun canDrag(position: Int): Boolean {
            if (position !in value.indices) return false
            val item = value[position]
            return item == null || !isPermanent(item)
        }

        fun canMove(from: Int, to: Int): Boolean {
            if (from == to) return false
            if (from !in value.indices || to !in value.indices) return false
            val moving = value[from]
            val sep = separatorIndex
            val permanentIndex = value.indexOfFirst { isPermanent(it) }
            if (moving == null) {
                // The divider cannot move above the first visible tab or below the permanent Queues tab.
                if (to == 0) return false
                if (permanentIndex != -1 && to <= permanentIndex) return false
                return true
            }
            if (isPermanent(moving)) return false
            // Keep the permanent Queues tab first and never move other tabs above it.
            if (permanentIndex != -1 && to <= permanentIndex) return false
            // No tab may cross below the divider (tabs after it are hidden).
            if (sep != -1 && to >= sep) return false
            return true
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ) =
            if (viewType == 2)
                TabOrderSeparatorViewHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.tab_order_seperator, parent, false)
                )
            else
                TabOrderItemViewHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.tab_order_item, parent, false)
                )

        override fun onBindViewHolder(
            holder: TabOrderViewHolder,
            position: Int
        ) {
            if (holder is TabOrderItemViewHolder) {
                val item = value[position]!!
                holder.name.text = holder.itemView.context.getString(item.label)
                val permanent = isPermanent(item)
                holder.hint.visibility = if (permanent) View.VISIBLE else View.GONE
                if (permanent) {
                    holder.hint.setText(R.string.tab_order_queues_permanent)
                }
                holder.itemView.isClickable = !permanent
                holder.itemView.isFocusable = !permanent
                holder.itemView.alpha = if (permanent) 0.38f else 1f
                holder.dragHandle.alpha = if (permanent) 0.38f else 1f
                holder.dragHandle.setOnTouchListener { _, event ->
                    if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN && !permanent) {
                        holder.itemView.performClick()
                        onStartDrag(holder)
                    }
                    false
                }
            } else if (holder is TabOrderSeparatorViewHolder) {
                holder.itemView.setOnTouchListener { view, event ->
                    if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                        view.performClick()
                        onStartDrag(holder)
                    }
                    false
                }
            }
        }

        override fun getItemCount() = value.count()

        override fun getItemViewType(position: Int): Int {
            return if (value[position] == null) 2 else 1
        }

        open class TabOrderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
        class TabOrderItemViewHolder(itemView: View) : TabOrderViewHolder(itemView) {
            val name: TextView = itemView.findViewById(R.id.tabName)
            val hint: TextView = itemView.findViewById(R.id.tabHint)
            val dragHandle: View = itemView.findViewById(R.id.dragHandle)
        }

        class TabOrderSeparatorViewHolder(itemView: View) : TabOrderViewHolder(itemView)
    }
}