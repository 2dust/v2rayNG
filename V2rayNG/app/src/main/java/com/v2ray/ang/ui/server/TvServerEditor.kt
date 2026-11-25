package com.v2ray.ang.ui.server

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.TvEditorForm
import com.v2ray.ang.ui.compose.TvIconButton
import com.v2ray.ang.ui.compose.dpadFocusOutline
import com.v2ray.ang.ui.compose.rememberEditorFocus

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
    val focus = rememberEditorFocus(fallback = back)
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
                            .dpadFocusOutline(cornerRadius = 24.dp)
                    ) {
                        Icon(painterResource(R.drawable.ic_tv_arrow_back_24dp), stringResource(R.string.acc_back))
                    }
                },
                actions = {
                    if (onDelete != null) {
                        TvIconButton(painterResource(R.drawable.ic_delete_24dp), stringResource(R.string.menu_item_del_config), onDelete,
                            focus.target(delete).focusProperties { start = back; end = if (onAdd != null) addRequester else save })
                    }
                    if (onAdd != null) {
                        IconButton(
                            onClick = onAdd,
                            modifier = focus.target(addRequester)
                                .focusProperties { start = if (onDelete != null) delete else back; end = save }
                                .dpadFocusOutline(cornerRadius = 24.dp)
                        ) {
                            Icon(painterResource(R.drawable.ic_add_24dp), stringResource(R.string.acc_add_member))
                        }
                    }
                    TvIconButton(painterResource(R.drawable.ic_fab_check), stringResource(R.string.menu_item_save_config), onSave,
                        focus.target(save).focusProperties { start = if (onAdd != null) addRequester else if (onDelete != null) delete else back })
                }
            )
        }
    ) { innerPadding ->
        TvEditorForm(innerPadding, back, focus, PaddingValues(bottom = 36.dp), content)
    }
}
