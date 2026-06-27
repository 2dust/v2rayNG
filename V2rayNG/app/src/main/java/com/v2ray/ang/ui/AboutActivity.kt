package com.v2ray.ang.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.util.Utils

class AboutActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AboutScreen(
                    version = "v${BuildConfig.VERSION_NAME} (${CoreNativeManager.getLibVersion()})",
                    appId = BuildConfig.APPLICATION_ID,
                    onBack = { finish() },
                    onUrlClick = { Utils.openUri(this, it) },
                    onLicensesClick = { showLicensesDialog() }
                )
            }
        }
    }

    private fun showLicensesDialog() {
        val webView = android.webkit.WebView(this)
        webView.loadUrl("file:///android_asset/open_source_licenses.html")
        android.app.AlertDialog.Builder(this)
            .setTitle("Open source licenses")
            .setView(webView)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    version: String,
    appId: String,
    onBack: () -> Unit,
    onUrlClick: (String) -> Unit,
    onLicensesClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_about)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AboutItem("Version", version)
            AboutItem("Application ID", appId)
            
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            
            ClickableAboutItem(stringResource(R.string.title_source_code)) { onUrlClick(AppConfig.APP_URL) }
            ClickableAboutItem(stringResource(R.string.title_pref_feedback)) { onUrlClick(AppConfig.APP_ISSUES_URL) }
            ClickableAboutItem(stringResource(R.string.title_oss_license)) { onLicensesClick() }
            ClickableAboutItem(stringResource(R.string.title_tg_channel)) { onUrlClick(AppConfig.TG_CHANNEL_URL) }
            ClickableAboutItem(stringResource(R.string.title_privacy_policy)) { onUrlClick(AppConfig.APP_PRIVACY_POLICY) }
        }
    }
}

@Composable
fun AboutItem(title: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ClickableAboutItem(title: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
    }
}
