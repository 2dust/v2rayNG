package com.v2ray.ang.ui.shortcut

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.SettingsSwitchItem
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class TaskerActivity : BaseComponentActivity() {

    private val viewModel: TaskerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.action != AppConfig.TASKER_ACTION_EDIT_SETTING || callingPackage == null) {
            denyAccess()
            return
        }
        lifecycleScope.launch {
            try {
                val bundle = intent.getBundleExtra(AppConfig.TASKER_EXTRA_BUNDLE)
                if (!viewModel.load(
                        callingPackage, bundle?.getBoolean(AppConfig.TASKER_EXTRA_BUNDLE_SWITCH),
                        bundle?.getString(AppConfig.TASKER_EXTRA_BUNDLE_GUID)
                    )) denyAccess()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "TaskerActivity: configuration failed (${error.javaClass.simpleName})")
                denyAccess()
            }
        }
    }

    @Composable
    override fun ScreenContent() {
        val items by viewModel.items.collectAsStateWithLifecycle()
        val start by viewModel.start.collectAsStateWithLifecycle()
        val guid by viewModel.selectedGuid.collectAsStateWithLifecycle()
        TaskerScreen(
            items = items,
            start = start,
            selectedGuid = guid,
            onStartChanged = viewModel::setStart,
            onSelect = viewModel::select,
            onBackClick = { finish() },
            onSave = { confirmFinish() }
        )
    }

    private fun denyAccess() {
        setResult(RESULT_CANCELED)
        toastError(R.string.remote_control_access_required)
        finish()
    }

    private fun confirmFinish() {
        if (viewModel.selectedGuid.value == null) return
        lifecycleScope.launch {
            try {
                val configuration = viewModel.configuration(callingPackage)
                if (configuration == null) {
                    denyAccess()
                    return@launch
                }
                val extraBundle = Bundle().apply {
                    putBoolean(AppConfig.TASKER_EXTRA_BUNDLE_SWITCH, configuration.start)
                    putString(AppConfig.TASKER_EXTRA_BUNDLE_GUID, configuration.item.guid)
                    putString(AppConfig.TASKER_EXTRA_BUNDLE_PACKAGE, callingPackage)
                    putString(AppConfig.TASKER_EXTRA_BUNDLE_TOKEN, configuration.token)
                }
                val result = Intent().apply {
                    putExtra(AppConfig.TASKER_EXTRA_BUNDLE, extraBundle)
                    putExtra(AppConfig.TASKER_EXTRA_STRING_BLURB, getString(
                        if (configuration.start) R.string.tasker_blurb_start else R.string.tasker_blurb_stop,
                        configuration.item.label
                    ))
                }
                setResult(RESULT_OK, result)
                finish()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "TaskerActivity: authorization failed (${error.javaClass.simpleName})")
                denyAccess()
            }
        }
    }
}

@Composable
fun TaskerScreen(
    items: List<TaskerItem>,
    start: Boolean,
    selectedGuid: String?,
    onStartChanged: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    onBackClick: () -> Unit,
    onSave: () -> Unit
) {
    val listState = rememberLazyListState()
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = "",
                onBackClick = onBackClick,
                actions = {
                    IconButton(onClick = onSave) {
                        Icon(painterResource(R.drawable.ic_fab_check), contentDescription = stringResource(R.string.acc_save))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            SettingsSwitchItem(
                title = stringResource(R.string.tasker_start_service),
                checked = start,
                onCheckedChange = onStartChanged
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScrollbar(listState),
                contentPadding = NavigationBarsBottomPadding()
            ) {
                items(items, key = { it.guid }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = selectedGuid == item.guid, role = Role.RadioButton) { onSelect(item.guid) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedGuid == item.guid,
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = item.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
