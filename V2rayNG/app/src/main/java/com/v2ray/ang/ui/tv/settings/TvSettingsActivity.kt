package com.v2ray.ang.ui.tv.settings

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.mobile.base.BaseComponentActivity
import com.v2ray.ang.ui.settings.SettingsForm
import com.v2ray.ang.ui.tv.compose.TvIconButton
import com.v2ray.ang.ui.tv.compose.requestFocusWhenReady
import com.v2ray.ang.vm.SettingsViewModel
import kotlinx.coroutines.launch

class TvSettingsActivity : BaseComponentActivity() {
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
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
        val systemVpnSettingsAvailable by viewModel.systemVpnSettingsAvailable.collectAsStateWithLifecycle()
        val back = remember { FocusRequester() }
        val focusedSetting = rememberSaveable { mutableStateOf<String?>(null) }
        val restoreKey = remember { focusedSetting.value }
        val rows = remember(back) { TvSettingsRows(back) { focusedSetting.value = it } }
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            topBar = {
                Column {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        TvIconButton(
                            painterResource(R.drawable.ic_tv_arrow_back_24dp), stringResource(R.string.action_back), ::finish,
                            modifier = Modifier.onFocusChanged { if (it.isFocused) focusedSetting.value = null },
                            focusRequester = back, contentDescription = stringResource(R.string.acc_back)
                        )
                        Text(stringResource(R.string.title_settings), style = MaterialTheme.typography.titleLarge)
                    }
                    if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        ) { innerPadding ->
            Column(Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)
                .padding(horizontal = 48.dp).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                rows.SettingsForm(
                    viewModel, systemVpnSettingsAvailable,
                    onModeHelpClicked = { viewModel.openModeHelp(this@TvSettingsActivity) },
                    onSystemVpnSettingsClicked = { viewModel.openSystemVpnSettings(this@TvSettingsActivity) }
                )
            }
        }
        LaunchedEffect(restoreKey) { requestFocusWhenReady(rows.focusTarget(restoreKey) ?: back, back) }
    }
}
