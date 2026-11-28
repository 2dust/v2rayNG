package com.v2ray.ang.ui.mobile

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.mobile.base.BaseComponentActivity
import com.v2ray.ang.ui.mobile.compose.AppTopBar
import com.v2ray.ang.ui.mobile.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.mobile.compose.verticalScrollbar
import com.v2ray.ang.ui.settings.SettingsForm
import com.v2ray.ang.ui.settings.SettingsRows
import com.v2ray.ang.vm.SettingsViewModel
import kotlinx.coroutines.launch
import com.v2ray.ang.ui.mobile.compose.CollapsiblePreferenceGroupHeader as GroupHeader
import com.v2ray.ang.ui.mobile.compose.SettingsSwitchItem as SwitchItem
import com.v2ray.ang.ui.mobile.compose.SettingsEditItem as EditItem
import com.v2ray.ang.ui.mobile.compose.SettingsListItem as ListItem
import com.v2ray.ang.ui.mobile.compose.SettingsMenuItem as MenuItem

class SettingsActivity : BaseComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.refreshSystemVpnSettingsAvailability()
            }
        }
    }

    @Composable
    override fun ScreenContent() {
        SettingsScreen(
            viewModel = viewModel,
            onBackClick = { finish() },
            onModeHelpClicked = { viewModel.openModeHelp(this) },
            onSystemVpnSettingsClicked = { viewModel.openSystemVpnSettings(this) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    onModeHelpClicked: () -> Unit,
    onSystemVpnSettingsClicked: () -> Unit
) {
    val scrollState = rememberScrollState()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val systemVpnSettingsAvailable by viewModel.systemVpnSettingsAvailable.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_settings),
                onBackClick = onBackClick,
                isLoading = isLoading
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScrollbar(scrollState)
                .verticalScroll(scrollState)
        ) {
            MobileSettingsRows.SettingsForm(viewModel, systemVpnSettingsAvailable, onModeHelpClicked, onSystemVpnSettingsClicked)
            Spacer(modifier = Modifier.height(24.dp))
            NavigationBarsSpacer()
        }
    }
}

private object MobileSettingsRows : SettingsRows {
    @Composable
    override fun CollapsiblePreferenceGroupHeader(title: String, expanded: Boolean, onExpandedChange: (Boolean) -> Unit) {
        GroupHeader(title, expanded, onExpandedChange)
    }

    @Composable
    override fun SettingsSwitchItem(title: String, summary: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean) {
        SwitchItem(title = title, summary = summary, checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }

    @Composable
    override fun SettingsEditItem(title: String, value: String, onValueChanged: (String) -> Unit, enabled: Boolean, isPassword: Boolean, keyboardNumber: Boolean) {
        EditItem(title = title, value = value, onValueChanged = onValueChanged, enabled = enabled,
            isPassword = isPassword, keyboardNumber = keyboardNumber)
    }

    @Composable
    override fun SettingsListItem(
        title: String, entries: List<String>, values: List<String>, selectedValue: String,
        onSelected: (String) -> Unit, enabled: Boolean, focusKey: String?
    ) {
        ListItem(title = title, entries = entries, values = values, selectedValue = selectedValue,
            onSelected = onSelected, enabled = enabled)
    }

    @Composable
    override fun SettingsMenuItem(title: String, onClick: () -> Unit, subtitle: String?) {
        MenuItem(title = title, onClick = onClick, subtitle = subtitle)
    }
}
