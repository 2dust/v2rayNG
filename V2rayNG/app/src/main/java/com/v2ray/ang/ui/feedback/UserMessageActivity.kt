package com.v2ray.ang.ui.feedback

import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar

/** A notification can truncate an error; this private destination always exposes its full text. */
class UserMessageActivity : BaseComponentActivity() {
    private val viewModel: UserMessageViewModel by viewModels()

    @Composable
    override fun ScreenContent() {
        val message by viewModel.message.collectAsStateWithLifecycle()
        Scaffold(
            topBar = { AppTopBar(stringResource(R.string.app_name), onBackClick = { finish() }) },
        ) { innerPadding ->
            Column(Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp)) {
                Text(
                    text = message,
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                )
                TextButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_close))
                }
            }
        }
    }

    companion object {
        const val EXTRA_MESSAGE = "userMessage"
    }
}

class UserMessageViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    val message = savedStateHandle.getStateFlow(UserMessageActivity.EXTRA_MESSAGE, "")
}
