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

package org.akanework.gramophone.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import org.akanework.gramophone.logic.expandedAppBarHeight
import org.akanework.gramophone.ui.components.compose.SearchAppBar
import org.akanework.gramophone.ui.GramophoneTheme
import org.akanework.gramophone.ui.pureDark
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.closeKeyboard
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener
import org.akanework.gramophone.logic.showKeyboard
import org.akanework.gramophone.logic.utils.matchesSearch
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.ui.adapters.SongAdapter
import org.akanework.gramophone.ui.adapters.Sorter

/**
 * SearchFragment:
 *   A fragment that contains a search bar which browses
 * the library finding items matching user input.
 *
 * @author AkaneTan
 */
class SearchFragment : BaseFragment() {
    // TODO this class leaks InsetSourceControl
    private lateinit var editText: EditText
    private lateinit var qTitle: Flow<String>

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        // Inflate the layout for this fragment
        val rootView = inflater.inflate(R.layout.fragment_search, container, false)
        val recyclerView = rootView.findViewById<MyRecyclerView>(R.id.recyclerview)
        val searchTextFlow = MutableStateFlow(arguments?.getString("query", "") ?: "")
        qTitle = searchTextFlow.map { requireContext().getString(R.string.search_query, it) }
        val searchSource = searchTextFlow.map { query ->
            org.akanework.gramophone.logic.queue.QueueSource(
                kind = org.akanework.gramophone.logic.queue.QueueSourceKind.SEARCH_RESULTS,
                identifier = query.trim(),
                displayName = query.trim(),
            )
        }
        val songAdapter =
            SongAdapter(
                this, qTitle, mainActivity.reader.songListFlow.combine(searchTextFlow.map {
                    it.trim()
                }) { list, text ->
                    list.filter {
                        // TODO sort results by match quality? (using raw=natural order)
                        it.matchesSearch(text)
                    }
                },
                isSubFragment = R.id.search, allowDiffUtils = true,
                rawOrderExposed = Sorter.Type.ByTitleAscending,
                queueSource = searchSource,
            )

        recyclerView.enableEdgeToEdgePaddingListener(ime = true)
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = songAdapter.concatAdapter

        // Build FastScroller.
        recyclerView.fastScroll(songAdapter, songAdapter.itemHeightHelper)

        rootView.findViewById<ComposeView>(R.id.search_app_bar).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                GramophoneTheme(pureDark = pureDark(requireContext())) {
                    val query by searchTextFlow.collectAsState()
                    SearchAppBar(
                        query = query,
                        onQueryChange = { searchTextFlow.value = it },
                        onClose = {
                            if (::editText.isInitialized) requireActivity().closeKeyboard(editText)
                            requireActivity().supportFragmentManager.popBackStack()
                        },
                        expandedHeight = expandedAppBarHeight(),
                        onFieldReady = { editText = it },
                    )
                }
            }
        }

        return rootView
    }

    override fun onPause() {
        if (!isHidden && ::editText.isInitialized) {
            requireActivity().closeKeyboard(editText)
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (!isRemoving && !isHidden && !mainActivity.playerBottomSheet.visibleAndExpanded) {
            if (::editText.isInitialized) requireActivity().showKeyboard(editText)
        } else if (::editText.isInitialized) {
            editText.clearFocus()
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        if (hidden) {
            if (::editText.isInitialized) requireActivity().closeKeyboard(editText)
            super.onHiddenChanged(true)
        } else {
            super.onHiddenChanged(false)
            if (!isRemoving && !mainActivity.playerBottomSheet.visibleAndExpanded && ::editText.isInitialized) {
                requireActivity().showKeyboard(editText)
            }
        }
    }

    override fun onDestroyView() {
        if (::editText.isInitialized) requireActivity().closeKeyboard(editText)
        super.onDestroyView()
        viewLifecycleOwner.lifecycleScope.cancel() // TODO: why?
    }

}
