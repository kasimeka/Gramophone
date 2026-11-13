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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.LayoutInflater
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.FragmentManager
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil3.SingletonImageLoader
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.clone
import org.akanework.gramophone.logic.ui.UiExperiments
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener
import org.akanework.gramophone.logic.setExpandedAppBarHeight
import org.akanework.gramophone.logic.getFile
import org.akanework.gramophone.logic.gramophoneApplication
import org.akanework.gramophone.logic.needsManualSnackBarInset
import org.akanework.gramophone.logic.queue.M3uImport
import org.akanework.gramophone.logic.updateMargin
import org.akanework.gramophone.logic.utils.MediaRowCodec
import org.akanework.gramophone.logic.utils.SdScanner
import org.akanework.gramophone.logic.setMediaItemsWithTitle
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.adapters.ViewPager2Adapter
import org.akanework.gramophone.ui.components.PlayerBottomSheet
import org.akanework.gramophone.ui.fragments.settings.MainSettingsActivity
import org.nift4.mediastorecompat.MediaStoreCompat

/**
 * ViewPagerFragment:
 *   A fragment that's in charge of displaying tabs
 * and is connected to the drawer.
 *
 * @author AkaneTan
 */
class ViewPagerFragment : BaseFragment() {
    lateinit var appBarLayout: AppBarLayout
        private set
    val recycledViewPool = RecyclerView.RecycledViewPool()
    private lateinit var viewPager2: ViewPager2
    private lateinit var adapter: ViewPager2Adapter
    private val tabLayout: TabLayout by lazy { requireActivity().findViewById(R.id.tab_layout) }
    private var tabLayoutMediator: TabLayoutMediator? = null
    private val tabSelectedListener = object : TabLayout.OnTabSelectedListener {
        override fun onTabSelected(p0: TabLayout.Tab?) {
            // A tab tapped on a sub-page returns to this fragment and selects that section.
            if (isHidden) {
                requireActivity().supportFragmentManager
                    .popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            }
        }

        override fun onTabUnselected(p0: TabLayout.Tab?) {
            // do nothing
        }

        override fun onTabReselected(p0: TabLayout.Tab?) {
            if (p0 == null) return
            onTabReselected(p0.position)
        }
    }
    private val importPlaylistLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importPlaylist(uri)
        }

    private fun importPlaylist(uri: Uri) {
        val context = requireContext()
        val activity = requireActivity() as MainActivity
        viewLifecycleOwner.lifecycleScope.launch {
            val title = queryDisplayName(context, uri)
                ?.substringBeforeLast('.')
                ?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.import_playlist_default_title)
            val text = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }
            if (text == null) {
                Toast.makeText(context, R.string.import_playlist_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            val songsByPath = withContext(Dispatchers.Default) {
                activity.reader.songListFlow.first().mapNotNull { song ->
                    song.getFile()?.absolutePath?.let { it to song }
                }.toMap()
            }
            val result = M3uImport.resolve(M3uImport.entries(text)) { songsByPath[it] }
            if (result.matches.isEmpty()) {
                Toast.makeText(context, R.string.import_playlist_none, Toast.LENGTH_LONG).show()
                return@launch
            }
            activity.gramophoneApplication.queueRepository.create(
                source = null,
                rows = result.matches.map { MediaRowCodec.encode(it) },
                title = title,
            )
            val message = if (result.unmatched > 0) {
                context.resources.getQuantityString(
                    R.plurals.import_playlist_imported_missing, result.unmatched, result.unmatched
                )
            } else {
                context.getString(R.string.import_playlist_imported)
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_viewpager, container, false)
        val topAppBar = rootView.findViewById<MaterialToolbar>(R.id.topAppBar)
        viewPager2 = rootView.findViewById(R.id.fragment_viewpager)

        rootView.findViewById<CollapsingToolbarLayout>(R.id.collapsingtoolbar).setExpandedAppBarHeight()
        appBarLayout = rootView.findViewById(R.id.appbarlayout)
        appBarLayout.enableEdgeToEdgePaddingListener()
        if (!UiExperiments.tabsAtBottom(requireContext())) {
            (tabLayout.parent as? ViewGroup)?.removeView(tabLayout)
            tabLayout.setBackgroundColor(Color.TRANSPARENT)
            appBarLayout.addView(
                tabLayout,
                AppBarLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }
        topAppBar.overflowIcon =
            AppCompatResources.getDrawable(requireContext(), R.drawable.ic_more_vert_alt_topappbar)

        topAppBar.setOnMenuItemClickListener { it ->
            val activity = requireActivity() as MainActivity
            when (it.itemId) {
                R.id.search -> {
                    activity.startFragment(SearchFragment())
                }

                R.id.equalizer -> {
                    val intent =
                        Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                            // EXTRA_PACKAGE_NAME is probably not needed but might as well add for good measure
                            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, requireContext().packageName)
                            putExtra(
                                AudioEffect.EXTRA_AUDIO_SESSION,
                                activity.getPlayer()?.audioSessionId
                            )
                            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                        }
                    try {
                        if (Settings.System.getString(
                                requireContext().contentResolver,
                                "firebase.test.lab"
                            ) != "true"
                        ) {
                            activity.startingActivity.launch(intent)
                        }
                    } catch (_: ActivityNotFoundException) {
                        // Let's show a toast here if no system inbuilt EQ was found.
                        Toast.makeText(
                            requireContext(),
                            R.string.equalizer_not_found,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                R.id.quick_refresh -> {
                    val imageLoader = SingletonImageLoader.get(requireContext())
                    imageLoader.memoryCache?.clear()
                    val playerLayout = activity.playerBottomSheet
                    activity.updateLibrary {
                        viewLifecycleOwner.lifecycleScope.launch {
                            showRefreshDoneSnackBar(
                                playerLayout,
                                activity.reader.songListFlow.first().size
                            )
                        }
                    }
                }

                R.id.refresh -> {
                    val context = requireContext()
                    val imageLoader = SingletonImageLoader.get(context)
                    imageLoader.memoryCache?.clear()
                    val playerLayout = activity.playerBottomSheet
                    MaterialAlertDialogBuilder(context)
                        .setIcon(R.drawable.ic_refresh)
                        .setTitle(R.string.did_you_know)
                        .setMessage(R.string.refresh_did_you_know)
                        .setPositiveButton(android.R.string.ok) { _, _ -> }
                        .show()
                    Toast.makeText(context, R.string.refreshing_wait, Toast.LENGTH_LONG).show()
                    CoroutineScope(Dispatchers.Default).launch {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            SdScanner.scanEverything(context, 5000) { progress ->
                                if (progress.step != SdScanner.SimpleProgress.Step.DONE) {
                                    val str = if (progress.percentage == null)
                                        context.getString(R.string.refreshing_wait)
                                    else context.getString(
                                        R.string.still_refreshing,
                                        progress.step.ordinal,
                                        SdScanner.SimpleProgress.Step.DONE.ordinal - 1,
                                        "${progress.percentage}%"
                                    )
                                    CoroutineScope(Dispatchers.Main).launch {
                                        Toast.makeText(context, str, Toast.LENGTH_SHORT).show()
                                    }
                                    return@scanEverything
                                }
                                activity.updateLibrary(false) {
                                    CoroutineScope(Dispatchers.Main).launch {
                                        showRefreshDoneSnackBar(
                                            playerLayout,
                                            activity.reader.songListFlow.first().size
                                        )
                                    }
                                }
                            }
                        } else {
                            val job = launch(Dispatchers.IO) {
                                MediaStoreCompat.scanEverything(context)
                            }
                            while (!job.isCompleted) {
                                delay(5000)
                                CoroutineScope(Dispatchers.Main).launch {
                                    Toast.makeText(
                                        context, context.getString(R.string.refreshing_wait),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }
                }

                R.id.import_playlist -> {
                    // m3u8 files often have no registered MIME type, so the picker accepts any file.
                    importPlaylistLauncher.launch(arrayOf("*/*"))
                }

                R.id.settings -> {
                    activity.startActivity(Intent(activity, MainSettingsActivity::class.java))
                }

                R.id.shuffle -> {
                    ShortcutManagerCompat.reportShortcutUsed(requireContext(), "shuffle_all")
                    val controller = activity.getPlayer()
                    viewLifecycleOwner.lifecycleScope.launch {
                        val songs = activity.reader.songListFlow.first()
                        if (songs.isNotEmpty()) {
                            val allSongsSource = org.akanework.gramophone.logic.queue.QueueSource(
                                kind = org.akanework.gramophone.logic.queue.QueueSourceKind.ALL_SONGS,
                                identifier = "all_songs",
                                displayName = requireContext().getString(R.string.category_songs),
                            )
                            controller?.setMediaItemsWithTitle(
                                songs.shuffled(),
                                title = requireContext().getString(R.string.category_songs),
                                original = false,
                                source = allSongsSource,
                            )
                            controller?.prepare()
                            controller?.play()
                        } else {
                            controller?.setMediaItems(listOf())
                        }
                    }
                }

                else -> throw IllegalStateException()
            }
            true
        }

        // Connect ViewPager2.

        viewPager2.offscreenPageLimit = 99999 // TODO is 99999 a good value?
        adapter =
            ViewPager2Adapter(
                childFragmentManager,
                viewLifecycleOwner.lifecycle,
                requireContext(),
                viewPager2
            )
        viewPager2.adapter = adapter

        val enoughSections = adapter.itemCount >= 2
        viewPager2.isUserInputEnabled = enoughSections
        adapter.onTabListChanged = {
            if (view != null) {
                viewPager2.isUserInputEnabled = adapter.itemCount >= 2
                if (tabLayoutMediator != null) updateBottomTabBar()
            }
        }

        return rootView
    }

    override fun onStart() {
        super.onStart()
        // The tab bar lives in the activity layout, which is only inflated after this fragment's view.
        if (tabLayoutMediator == null) attachBottomTabBar()
    }

    private fun attachBottomTabBar() {
        tabLayoutMediator = TabLayoutMediator(tabLayout, viewPager2) { tab, position ->
            tab.text = getString(adapter.getLabelResId(position))
            tab.view.post {
                try {
                    /*
                     * Add margin to last and first tab.
                     * There's no attribute to let you set margin
                     * to the last tab.
                     */
                    val lp = tab.view.layoutParams as ViewGroup.MarginLayoutParams
                    lp.marginStart = if (position == 0)
                        resources.getDimension(R.dimen.tab_layout_content_padding).toInt() else 0
                    lp.marginEnd = if (position == tabLayout.tabCount - 1)
                        resources.getDimension(R.dimen.tab_layout_content_padding).toInt() else 0
                    tab.view.layoutParams = lp
                } catch (_: IllegalStateException) {
                }
            }
        }.also { it.attach() }
        tabLayout.addOnTabSelectedListener(tabSelectedListener)
        updateBottomTabBar()
    }

    private fun detachBottomTabBar() {
        tabLayoutMediator?.detach()
        tabLayoutMediator = null
        tabLayout.removeOnTabSelectedListener(tabSelectedListener)
        (requireActivity() as MainActivity).playerBottomSheet.setBottomTabBarAvailable(false, false)
    }

    /** The bar is shown whenever there are enough sections, including on top of sub-pages. */
    private fun updateBottomTabBar() {
        val enough = adapter.itemCount >= 2
        if (!UiExperiments.tabsAtBottom(requireContext())) {
            tabLayout.visibility = if (enough) View.VISIBLE else View.GONE
            return
        }
        (requireActivity() as MainActivity).playerBottomSheet
            .setBottomTabBarAvailable(enough, enough)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (tabLayoutMediator != null) updateBottomTabBar()
    }

    fun onTabReselected(position: Int) {
        (childFragmentManager.findFragmentByTag("f${adapter.getItemId(position)}")
                as? AdapterFragment)?.onTabReselected()
    }

    /** Switch to the Queues tab. */
    fun openQueuesTab() {
        val queuesId = R.id.playlists.toLong()
        val position = (0 until adapter.itemCount).firstOrNull { adapter.getItemId(it) == queuesId }
            ?: return
        viewPager2.setCurrentItem(position, false)
    }

    override fun onDestroyView() {
        if (tabLayoutMediator != null) detachBottomTabBar()
        super.onDestroyView()
    }

    fun maybeReportFullyDrawn(itemId: Int) {
        if (view != null && adapter.getItemId(viewPager2.currentItem).toInt() == itemId)
            mainActivity.maybeReportFullyDrawn()
    }

    private fun showRefreshDoneSnackBar(playerLayout: PlayerBottomSheet, count: Int) {
        val view = view
        if (view == null) return
        val snackBar =
            Snackbar.make(
                view,
                getString(
                    R.string.refreshed_songs,
                    count,
                ),
                Snackbar.LENGTH_LONG,
            )
        snackBar.setAction(R.string.dismiss) {
            snackBar.dismiss()
        }

        /*
		 * Let's override snack bar's color here so it would
		 * adapt dark mode.
		 */
        snackBar.setBackgroundTint(
            MaterialColors.getColor(
                snackBar.view,
                com.google.android.material.R.attr.colorSurface,
            ),
        )
        snackBar.setActionTextColor(
            MaterialColors.getColor(
                snackBar.view,
                androidx.appcompat.R.attr.colorPrimary,
            ),
        )
        snackBar.setTextColor(
            MaterialColors.getColor(
                snackBar.view,
                com.google.android.material.R.attr.colorOnSurface,
            ),
        )

        // Set an anchor for snack bar.
        if (playerLayout.actuallyVisible)
            snackBar.anchorView = playerLayout
        else if (tabLayout.isVisible)
            snackBar.anchorView = tabLayout
        else if (needsManualSnackBarInset()) {
            // snack bar only implements proper insets handling for Q+
            snackBar.view.updateMargin {
                val i = ViewCompat.getRootWindowInsets(requireActivity().window.decorView)
                if (i != null) {
                    bottom += i.clone()
                        .getInsets(WindowInsetsCompat.Type.systemBars()).bottom
                }
            }
        }
        snackBar.show()
    }
}
