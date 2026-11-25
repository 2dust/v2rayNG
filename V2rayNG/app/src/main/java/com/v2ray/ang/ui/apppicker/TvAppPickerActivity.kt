package com.v2ray.ang.ui.apppicker

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.ui.compose.TvAppMenuAction
import com.v2ray.ang.ui.compose.TvAppSelectionList
import com.v2ray.ang.ui.compose.RestoreFocus
import com.v2ray.ang.ui.compose.TvAppSelectionToolbar
import com.v2ray.ang.ui.compose.rememberTvAppListFocus
import com.v2ray.ang.ui.compose.rememberTvAppSearch

class TvAppPickerActivity : BaseAppPickerActivity() {
    @Composable
    override fun PickerContent(title: String, apps: List<AppInfo>, isLoading: Boolean, selectedPackages: Set<String>, actions: AppPickerActions) {
        TvAppPickerScreen(title, apps, isLoading, selectedPackages, actions)
    }
}

@Composable
private fun TvAppPickerScreen(title: String, apps: List<AppInfo>, isLoading: Boolean, selectedPackages: Set<String>, actions: AppPickerActions) {
    val search = rememberTvAppSearch(actions.onSearch)
    val appFocus = rememberTvAppListFocus(apps)
    val menu = remember(actions) {
        AppPickerMenuAction.entries.map { TvAppMenuAction(it.labelRes) { actions.onMenuAction(it) } }
    }
    var focusedPackage by rememberSaveable { mutableStateOf<String?>(null) }
    appFocus.RestoreFocus(focusedPackage, search.toolbarRestoreTarget, isLoading)

    Scaffold(
        topBar = {
            TvAppSelectionToolbar(
                title, isLoading, search, menu, actions.onBack,
                onMoveDown = { appFocus.focusAt(0) },
                modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedPackage = null }
            )
        }
    ) { innerPadding ->
        TvAppSelectionList(
            apps, selectedPackages, appFocus, search.back, actions.onToggleApp,
            onAppFocused = { focusedPackage = it },
            onMoveAboveList = { search.toolbarEntry.requestFocus() },
            modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)
                .imePadding().padding(horizontal = 48.dp)
        )
    }
}
