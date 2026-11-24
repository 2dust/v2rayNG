package com.v2ray.ang.ui.settings

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.verticalScrollbar

class TvSettingsActivity : BaseSettingsActivity() {
    @Composable
    override fun SettingsLayout(isLoading: Boolean, form: @Composable (Modifier, SettingsRows) -> Unit) {
        TvSettingsScreen(onBackClick = { finish() }, isLoading = isLoading, form = form)
    }
}

@Composable
private fun TvSettingsScreen(onBackClick: () -> Unit, isLoading: Boolean, form: @Composable (Modifier, SettingsRows) -> Unit) {
    val back = remember { FocusRequester() }
    val focusedSetting = rememberSaveable { mutableStateOf<String?>(null) }
    val restoreKey = remember { focusedSetting.value }
    val rows = remember(back) { TvSettingsRows(back, onRowFocused = { focusedSetting.value = it }) }
    val scroll = rememberScrollState()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_settings),
                onBackClick = onBackClick,
                isLoading = isLoading,
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.focusRequester(back)
                            .onFocusChanged { if (it.isFocused) focusedSetting.value = null }
                            .tvSettingsFocusOutline(24.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back_24dp),
                            contentDescription = stringResource(R.string.acc_back),
                            modifier = Modifier.scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        form(
            Modifier.fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 48.dp)
                .verticalScrollbar(scroll)
                .verticalScroll(scroll),
            rows
        )
    }
    LaunchedEffect(rows) {
        if (!rows.restoreFocus(restoreKey)) back.requestFocus()
    }
}
