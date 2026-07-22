package com.dalulong.app.ui

import android.os.Bundle
import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dalulong.app.AppConfig
import com.dalulong.app.BuildConfig
import com.dalulong.app.R
import com.dalulong.app.compose.AppTopBar
import com.dalulong.app.compose.SettingsMenuItem
import com.dalulong.app.compose.VersionInfoBlock
import com.dalulong.app.core.CoreNativeManager
import com.dalulong.app.ui.base.BaseComponentActivity
import com.dalulong.app.util.Utils

class AboutActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent() {
        AboutScreen(onBackClick = { finish() })
    }
}

@Composable
fun AboutScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    var showOssDialog by remember { mutableStateOf(false) }

    val versionText = "v${BuildConfig.VERSION_NAME} (${CoreNativeManager.getLibVersion()})"
    val appIdText = BuildConfig.APPLICATION_ID

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_about),
                onBackClick = onBackClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_source_code_24dp),
                title = stringResource(R.string.title_source_code),
                onClick = { Utils.openUri(context, AppConfig.APP_URL) }
            )
            SettingsMenuItem(
                icon = painterResource(R.drawable.license_24px),
                title = stringResource(R.string.title_oss_license),
                onClick = { showOssDialog = true }
            )
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_feedback_24dp),
                title = stringResource(R.string.title_pref_feedback),
                onClick = { Utils.openUri(context, AppConfig.APP_ISSUES_URL) }
            )
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_telegram_24dp),
                title = stringResource(R.string.title_tg_channel),
                onClick = { Utils.openUri(context, AppConfig.TG_CHANNEL_URL) }
            )
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_privacy_24dp),
                title = stringResource(R.string.title_privacy_policy),
                onClick = { Utils.openUri(context, AppConfig.APP_PRIVACY_POLICY) }
            )
            VersionInfoBlock(
                versionText = versionText,
                appIdText = appIdText
            )
        }
    }

    if (showOssDialog) {
        AlertDialog(
            onDismissRequest = { showOssDialog = false },
            title = { Text(stringResource(R.string.title_oss_license)) },
            text = {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            loadUrl("file:///android_asset/open_source_licenses.html")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 300.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = { showOssDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(bottom = 60.dp)
        )
    }
}
