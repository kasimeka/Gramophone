package org.akanework.gramophone.ui.components.compose

import android.view.LayoutInflater
import android.widget.EditText
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
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
 * The search bar shared by global search and queue search: back on the navigation side, the
 * search field in the title slot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAppBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    expandedHeight: Dp = 152.dp,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    onFieldReady: (EditText) -> Unit = {},
) {
    LargeTopAppBar(
        expandedHeight = expandedHeight,
        title = {
            SearchField(query = query, onQueryChange = onQueryChange, onFieldReady = onFieldReady)
        },
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
            if (!activity.playerBottomSheet.visibleAndExpanded) activity.showKeyboard(field)
        }
        onDispose { activity.closeKeyboard(field) }
    }
    AndroidView(
        factory = { field },
        update = { if (field.text.toString() != query) field.setText(query) },
        modifier = Modifier.fillMaxWidth(),
    )
}
