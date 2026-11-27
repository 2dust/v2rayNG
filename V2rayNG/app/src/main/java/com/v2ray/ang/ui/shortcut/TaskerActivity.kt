package com.v2ray.ang.ui.shortcut

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.mobile.base.BaseComponentActivity
import com.v2ray.ang.ui.mobile.compose.AppTopBar
import com.v2ray.ang.ui.mobile.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.mobile.compose.SettingsSwitchItem
import com.v2ray.ang.ui.mobile.compose.verticalScrollbar
import com.v2ray.ang.vm.TaskerViewModel

data class TaskerItem(
    val label: String,
    val guid: String,
)

class TaskerActivity : BaseComponentActivity() {

    private val viewModel: TaskerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.loadTaskerConfig(intent)
    }

    @Composable
    override fun ScreenContent() {
        TaskerScreen(
            viewModel = viewModel,
            onBackClick = { finish() },
            onSave = { confirmFinish() }
        )
    }

    private fun confirmFinish() {
        val resultIntent = viewModel.buildResultIntent() ?: return
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}

@Composable
fun TaskerScreen(
    viewModel: TaskerViewModel,
    onBackClick: () -> Unit,
    onSave: () -> Unit
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val switchState by viewModel.switchState.collectAsStateWithLifecycle()
    val selectedPosition by viewModel.selectedPosition.collectAsStateWithLifecycle()

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
                checked = switchState,
                onCheckedChange = { viewModel.setSwitchState(it) }
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScrollbar(listState),
                contentPadding = NavigationBarsBottomPadding()
            ) {
                itemsIndexed(items, key = { _, item -> item.guid }) { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setSelectedPosition(index) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedPosition == index,
                            onClick = { viewModel.setSelectedPosition(index) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = item.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
