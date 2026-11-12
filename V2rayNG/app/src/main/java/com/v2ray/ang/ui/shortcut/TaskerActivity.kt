package com.v2ray.ang.ui.shortcut

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppTopBarAction
import com.v2ray.ang.ui.compose.dpadClickable
import com.v2ray.ang.ui.compose.dpadFocusOutline
import com.v2ray.ang.ui.compose.isTelevisionDevice
import com.v2ray.ang.ui.compose.tvSafeAreaPadding
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.rememberDpadFocusRequester
import com.v2ray.ang.ui.compose.dpadMovePreviousNavigation
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.SettingsSwitchItem
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.util.LogUtil

data class TaskerItem(
    val label: String,
    val guid: String,
)

class TaskerActivity : BaseComponentActivity() {

    private val items = mutableListOf<TaskerItem>()

    private val switchState = mutableStateOf(false)
    private val selectedPosition = mutableStateOf(-1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        items.add(TaskerItem(label = "Default", guid = AppConfig.TASKER_DEFAULT_GUID))

        MmkvManager.decodeAllServerList().forEach { key ->
            MmkvManager.decodeServerConfig(key)?.let { config ->
                items.add(TaskerItem(label = config.remarks, guid = key))
            }
        }

        init()
    }

    @Composable
    override fun ScreenContent() {
        TaskerScreen(
            items = items,
            switchState = switchState,
            selectedPosition = selectedPosition,
            onBackClick = { finish() },
            onSave = { confirmFinish() }
        )
    }

    private fun init() {
        try {
            val bundle = intent?.getBundleExtra(AppConfig.TASKER_EXTRA_BUNDLE)
            val switch = bundle?.getBoolean(AppConfig.TASKER_EXTRA_BUNDLE_SWITCH, false)
            val guid = bundle?.getString(AppConfig.TASKER_EXTRA_BUNDLE_GUID, "")

            if (switch == null || TextUtils.isEmpty(guid)) {
                return
            } else {
                switchState.value = switch
                selectedPosition.value = items.indexOfFirst { it.guid == guid.toString() }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to initialize Tasker settings", e)
        }
    }

    private fun confirmFinish() {
        val position = selectedPosition.value
        if (position < 0) {
            return
        }

        val extraBundle = Bundle()
        extraBundle.putBoolean(AppConfig.TASKER_EXTRA_BUNDLE_SWITCH, switchState.value)
        extraBundle.putString(AppConfig.TASKER_EXTRA_BUNDLE_GUID, items[position].guid)
        val intent = Intent()

        val blurb = getString(
            if (switchState.value) R.string.tasker_blurb_start else R.string.tasker_blurb_stop,
            items[position].label
        )

        intent.putExtra(AppConfig.TASKER_EXTRA_BUNDLE, extraBundle)
        intent.putExtra(AppConfig.TASKER_EXTRA_STRING_BLURB, blurb)
        setResult(RESULT_OK, intent)
        finish()
    }
}

@Composable
fun TaskerScreen(
    items: List<TaskerItem>,
    switchState: MutableState<Boolean>,
    selectedPosition: MutableState<Int>,
    onBackClick: () -> Unit,
    onSave: () -> Unit
) {
    val isTelevision = isTelevisionDevice()
    val listState = rememberLazyListState()
    val switchFocusRequester = remember { FocusRequester() }
    val backFocusRequester = rememberDpadFocusRequester()
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = "",
                onBackClick = onBackClick,
                navigationFocusRequester = backFocusRequester,
                onMoveDown = switchFocusRequester::requestFocus,
                actionItems = listOf(
                    AppTopBarAction(
                        icon = painterResource(R.drawable.ic_fab_check),
                        label = stringResource(R.string.action_save),
                        contentDescription = stringResource(R.string.acc_save),
                        onClick = onSave
                    )
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .tvSafeAreaPadding()
        ) {
            SettingsSwitchItem(
                title = stringResource(R.string.tasker_start_service),
                checked = switchState.value,
                onCheckedChange = { switchState.value = it },
                modifier = Modifier.focusRequester(switchFocusRequester)
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .dpadMovePreviousNavigation { backFocusRequester.requestFocus() }
                    .verticalScrollbar(listState),
                contentPadding = NavigationBarsBottomPadding()
            ) {
                itemsIndexed(items, key = { _, item -> item.guid }) { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .dpadFocusOutline()
                            .dpadClickable { selectedPosition.value = index }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedPosition.value == index,
                            onClick = if (isTelevision) null else ({ selectedPosition.value = index })
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = item.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
