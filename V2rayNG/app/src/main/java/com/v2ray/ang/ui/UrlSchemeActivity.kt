package com.v2ray.ang.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toast
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.util.LogUtil

class UrlSchemeActivity : BaseComponentActivity() {

    private val viewModel: UrlSchemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val bundle = intent.getBundleExtra(AppConfig.TASKER_EXTRA_BUNDLE)
            viewModel.initialize(
                action = intent.action,
                type = intent.type,
                data = intent.dataString,
                sharedText = intent.getStringExtra(Intent.EXTRA_TEXT),
                // API 34+ exposes the launcher UID only when Android shares its identity.
                senderUid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) launchedFromUid else -1,
                claimedPackage = bundle?.getString(AppConfig.TASKER_EXTRA_BUNDLE_PACKAGE),
                token = bundle?.getString(AppConfig.TASKER_EXTRA_BUNDLE_TOKEN),
            )
        } catch (error: Exception) {
            // Malformed parcel exceptions can contain credentials from the incoming configuration.
            LogUtil.e(AppConfig.TAG, "UrlSchemeActivity: invalid request (${error.javaClass.simpleName})")
            finish()
        }
    }

    @Composable
    override fun ScreenContent() {
        val state by viewModel.state.collectAsStateWithLifecycle()
        BackHandler { viewModel.onAction(UrlSchemeAction.Cancel) }
        LaunchedEffect(state) {
            when (state) {
                UrlSchemeState.Imported -> {
                    toast(R.string.import_subscription_success)
                    startActivity(Intent(this@UrlSchemeActivity, MainActivity::class.java))
                    finish()
                }
                UrlSchemeState.Failed -> {
                    toast(R.string.import_subscription_failure)
                    finish()
                }
                UrlSchemeState.Cancelled -> finish()
                else -> Unit
            }
        }
        Surface(modifier = Modifier.fillMaxSize()) {
            when (val current = state) {
                is UrlSchemeState.Pending -> {
                    val cancelFocus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { cancelFocus.requestFocus() }
                    AlertDialog(
                        onDismissRequest = { viewModel.onAction(UrlSchemeAction.Cancel) },
                        title = { Text(stringResource(R.string.external_import_title)) },
                        text = {
                            Column(
                                modifier = Modifier.heightIn(max = 320.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Text(
                                    stringResource(
                                        R.string.external_import_message,
                                        stringResource(R.string.title_remote_control),
                                    )
                                )
                                Text(current.content, style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { viewModel.onAction(UrlSchemeAction.Confirm) }) {
                                Text(stringResource(R.string.external_import_confirm))
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { viewModel.onAction(UrlSchemeAction.Cancel) },
                                modifier = Modifier.focusRequester(cancelFocus),
                            ) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        },
                    )
                }
                UrlSchemeState.Loading, UrlSchemeState.Importing -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                else -> Unit
            }
        }
    }
}
