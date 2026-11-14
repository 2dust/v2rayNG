package com.v2ray.ang.ui.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.LocalOutboundEditorFocus
import com.v2ray.ang.ui.compose.LocalTvEditor
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.TvEditorNavigation
import com.v2ray.ang.ui.compose.rememberOutboundEditorFocus
import com.v2ray.ang.ui.compose.rememberTvEditorIme
import com.v2ray.ang.ui.compose.tvEditorFocus
import com.v2ray.ang.ui.compose.verticalScrollbar

@Composable
internal fun TvServerEditor(
    title: String, onBack: () -> Unit, onSave: () -> Unit, onDelete: (() -> Unit)?,
    onAdd: (() -> Unit)? = null,
    addRequester: FocusRequester = remember { FocusRequester() },
    content: @Composable ColumnScope.() -> Unit
) {
    val back = remember { FocusRequester() }
    val save = remember { FocusRequester() }
    val delete = remember { FocusRequester() }
    val focus = rememberOutboundEditorFocus(fallback = back)
    val scroll = rememberScrollState()
    val ime = rememberTvEditorIme()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val firstAction = if (onDelete != null) delete else if (onAdd != null) addRequester else save
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = title,
                onBackClick = onBack,
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = focus.target(back)
                            .focusProperties { end = firstAction }
                            .tvEditorFocus(radius = 24.dp)
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back_24dp),
                            stringResource(R.string.acc_back),
                            modifier = Modifier.scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f)
                        )
                    }
                },
                actions = {
                    if (onDelete != null) {
                        EditorAction(R.drawable.ic_delete_24dp, stringResource(R.string.menu_item_del_config), onDelete,
                            focus.target(delete).focusProperties { start = back; end = if (onAdd != null) addRequester else save })
                    }
                    if (onAdd != null) {
                        IconButton(
                            onClick = onAdd,
                            modifier = focus.target(addRequester)
                                .focusProperties { start = if (onDelete != null) delete else back; end = save }
                                .tvEditorFocus(radius = 24.dp)
                        ) {
                            Icon(painterResource(R.drawable.ic_add_24dp), stringResource(R.string.acc_add_member))
                        }
                    }
                    EditorAction(R.drawable.ic_fab_check, stringResource(R.string.menu_item_save_config), onSave,
                        focus.target(save).focusProperties { start = if (onAdd != null) addRequester else if (onDelete != null) delete else back })
                }
            )
        }
    ) { innerPadding ->
        CompositionLocalProvider(
            LocalOutboundEditorFocus provides focus,
            LocalTvEditor provides remember(back, ime.visible) { TvEditorNavigation(back, ime.visible) }
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)
                    .then(ime.padding).padding(horizontal = 48.dp)
                    .verticalScroll(scroll).verticalScrollbar(scroll).padding(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                content()
                NavigationBarsSpacer()
            }
        }
    }
}

@Composable
private fun EditorAction(icon: Int, label: String, onClick: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    TextButton(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused }.tvEditorFocus(radius = 24.dp)
    ) {
        Icon(painterResource(icon), contentDescription = if (focused) null else label, modifier = Modifier.size(24.dp))
        if (focused) Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
