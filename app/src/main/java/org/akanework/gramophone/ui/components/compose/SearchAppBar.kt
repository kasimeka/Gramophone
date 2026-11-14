package org.akanework.gramophone.ui.components.compose

import android.view.LayoutInflater
import android.widget.EditText
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.lerp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.addTextChangedListener
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.closeKeyboard
import org.akanework.gramophone.logic.showKeyboard
import org.akanework.gramophone.ui.MainActivity

/**
 * Scroll behaviour for the search bars. The app bar drags itself when given a scroll behaviour,
 * and without a snap it stays wherever the drag leaves it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberSearchBarScrollBehavior(): TopAppBarScrollBehavior =
    TopAppBarDefaults.exitUntilCollapsedScrollBehavior(snapAnimationSpec = null)

/**
 * The search bar shared by global search and queue search: back on the navigation side and a
 * single search field overlaid at the bottom of the bar so LargeTopAppBar never composes a
 * duplicate title into its collapsed top row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAppBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    expandedHeight: Dp = 152.dp,
    scrollBehavior: TopAppBarScrollBehavior = rememberSearchBarScrollBehavior(),
    onFieldReady: (EditText) -> Unit = {},
) {
    Box {
        LargeTopAppBar(
            expandedHeight = expandedHeight,
            title = {},
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
            scrollBehavior = scrollBehavior,
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
            },
        )
        val fraction = scrollBehavior.state.collapsedFraction
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(
                    start = lerp(16.dp, 56.dp, fraction),
                    end = 16.dp,
                    bottom = lerp(12.dp, 8.dp, fraction),
                )
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        val state = scrollBehavior.state
                        state.heightOffset = (state.heightOffset + delta)
                            .coerceIn(state.heightOffsetLimit, 0f)
                    },
                ),
        ) {
            SearchField(query = query, onQueryChange = onQueryChange, onFieldReady = onFieldReady)
        }
    }
}

/** The search text field (R.layout.search_field), with the keyboard shown while it is on screen. */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onFieldReady: (EditText) -> Unit = {},
) {
    val activity = LocalActivity.current as MainActivity
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)
    val currentOnFieldReady by rememberUpdatedState(onFieldReady)
    val field = remember { LayoutInflater.from(activity).inflate(R.layout.search_field, null) as EditText }
    DisposableEffect(Unit) {
        field.addTextChangedListener { currentOnQueryChange(it?.toString() ?: "") }
        currentOnFieldReady(field)
        field.post {
            if (field.isAttachedToWindow && !activity.playerBottomSheet.visibleAndExpanded) {
                activity.showKeyboard(field)
            }
        }
        onDispose { activity.closeKeyboard(field) }
    }
    AndroidView(
        factory = { field },
        update = { if (field.text.toString() != query) field.setText(query) },
        modifier = Modifier.fillMaxWidth(),
    )
}
