package com.v2ray.ang.ui.shortcut

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.compose.AppTopBar
import com.v2ray.ang.compose.SettingsSwitchItem
import com.v2ray.ang.compose.verticalScrollbar
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.util.LogUtil

class TaskerActivity : BaseComponentActivity() {

    private var lstData: ArrayList<String> = ArrayList()
    private var lstGuid: ArrayList<String> = ArrayList()

    private val switchState = mutableStateOf(false)
    private val selectedPosition = mutableStateOf(-1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lstData.add("Default")
        lstGuid.add(AppConfig.TASKER_DEFAULT_GUID)

        MmkvManager.decodeAllServerList().forEach { key ->
            MmkvManager.decodeServerConfig(key)?.let { config ->
                lstData.add(config.remarks)
                lstGuid.add(key)
            }
        }

        init()
    }

    @Composable
    override fun ScreenContent() {
        TaskerScreen(
            items = lstData,
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
                val pos = lstGuid.indexOf(guid.toString())
                if (pos >= 0) {
                    selectedPosition.value = pos
                }
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
        extraBundle.putString(AppConfig.TASKER_EXTRA_BUNDLE_GUID, lstGuid[position])
        val intent = Intent()

        val remarks = lstData[position]
        val blurb = if (switchState.value) {
            "Start $remarks"
        } else {
            "Stop $remarks"
        }

        intent.putExtra(AppConfig.TASKER_EXTRA_BUNDLE, extraBundle)
        intent.putExtra(AppConfig.TASKER_EXTRA_STRING_BLURB, blurb)
        setResult(RESULT_OK, intent)
        finish()
    }
}

@Composable
fun TaskerScreen(
    items: List<String>,
    switchState: MutableState<Boolean>,
    selectedPosition: MutableState<Int>,
    onBackClick: () -> Unit,
    onSave: () -> Unit
) {
    val listState = rememberLazyListState()
    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        topBar = {
            AppTopBar(
                title = "",
                onBackClick = onBackClick,
                actions = {
                    IconButton(onClick = onSave) {
                        Icon(painterResource(R.drawable.ic_fab_check), contentDescription = stringResource(R.string.menu_item_save_config))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            SettingsSwitchItem(
                title = stringResource(R.string.tasker_start_service),
                checked = switchState.value,
                onCheckedChange = { switchState.value = it }
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScrollbar(listState)
            ) {
                itemsIndexed(items) { index, remarks ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPosition.value = index }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedPosition.value == index,
                            onClick = { selectedPosition.value = index }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = remarks, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
