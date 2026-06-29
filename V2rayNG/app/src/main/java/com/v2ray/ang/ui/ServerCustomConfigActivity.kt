package com.v2ray.ang.ui

import android.os.Bundle
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.compose.AppTheme
import com.v2ray.ang.compose.AppTopBar
import com.v2ray.ang.compose.ConfirmDialog
import com.v2ray.ang.compose.FormTextField
import com.v2ray.ang.compose.LocalDarkTheme
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.fmt.CustomFmt
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils

class ServerCustomConfigActivity : ComponentActivity() {

    private val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    private val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false)
                && editGuid.isNotEmpty()
                && editGuid == MmkvManager.getSelectServer()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val config = MmkvManager.decodeServerConfig(editGuid)
        val initialRemarks = config?.remarks ?: ""
        val initialContent = MmkvManager.decodeServerRaw(editGuid).orEmpty()

        setContent {
            AppTheme {
                ServerCustomConfigScreen(
                    editGuid = editGuid,
                    isRunning = isRunning,
                    initialRemarks = initialRemarks,
                    initialContent = initialContent,
                    onBackClick = { finish() },
                    onSave = { remarks, content -> saveServer(remarks, content) },
                    onDelete = { deleteServer() }
                )
            }
        }
    }

    private fun saveServer(remarks: String, content: String): Boolean {
        if (TextUtils.isEmpty(remarks)) {
            toast(R.string.server_lab_remarks)
            return false
        }

        val profileItem = try {
            CustomFmt.parse(content)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to parse custom configuration", e)
            toast("${getString(R.string.toast_malformed_josn)} ${e.cause?.message}")
            return false
        }

        val config = MmkvManager.decodeServerConfig(editGuid) ?: ProfileItem.create(EConfigType.CUSTOM)
        config.remarks = if (remarks.isEmpty()) profileItem?.remarks.orEmpty() else remarks
        config.server = profileItem?.server
        config.serverPort = profileItem?.serverPort
        config.description = AngConfigManager.generateDescription(config)

        MmkvManager.encodeServerConfig(editGuid, config)
        MmkvManager.encodeServerRaw(editGuid, content)
        if (isRunning) {
            SettingsChangeManager.makeRestartService()
        }
        toastSuccess(R.string.toast_success)
        finish()
        return true
    }

    private fun deleteServer(): Boolean {
        if (editGuid.isNotEmpty()) {
            MmkvManager.removeServer(editGuid)
            finish()
        }
        return true
    }
}

/**
 * Visual transformation that highlights JSON syntax symbols ( { } [ ] : , )
 * with a color that adapts to the current theme.
 */
class JsonSyntaxVisualTransformation(private val isDarkTheme: Boolean) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text.text)
        val symbolColor = if (isDarkTheme) Color(0xFFFFD700) else Color(0xFF000000)
        text.text.forEachIndexed { index, c ->
            if (c in setOf('{', '}', '[', ']', ':', ',')) {
                builder.addStyle(
                    SpanStyle(color = symbolColor),
                    index,
                    index + 1
                )
            }
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

@Composable
fun ServerCustomConfigScreen(
    editGuid: String,
    isRunning: Boolean,
    initialRemarks: String,
    initialContent: String,
    onBackClick: () -> Unit,
    onSave: (String, String) -> Boolean,
    onDelete: () -> Unit
) {
    var remarks by remember { mutableStateOf(initialRemarks) }
    var textFieldValue by remember { mutableStateOf(TextFieldValue(initialContent)) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val showDelete = editGuid.isNotEmpty() && !isRunning
    val scrollState = rememberScrollState()
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val density = LocalDensity.current
    val isDarkTheme = LocalDarkTheme.current

    LaunchedEffect(textFieldValue.selection, textLayoutResult) {
        val layout = textLayoutResult ?: return@LaunchedEffect
        val cursorPos = textFieldValue.selection.start
        if (cursorPos < 0 || cursorPos > layout.layoutInput.text.length) return@LaunchedEffect

        val line = layout.getLineForOffset(cursorPos)
        val lineTop = layout.getLineTop(line)
        val lineBottom = layout.getLineBottom(line)
        val viewportSize = scrollState.viewportSize
        val currentScroll = scrollState.value

        val padding = with(density) { 60.dp.toPx() }

        // Scroll down if the cursor line is near the bottom of the viewport.
        if (lineBottom > currentScroll + viewportSize - padding) {
            val target = (lineBottom - viewportSize + padding).toInt().coerceAtLeast(0)
            scrollState.animateScrollTo(target)
        }
        // Scroll up if the cursor line is near the top of the viewport.
        else if (lineTop < currentScroll + padding) {
            val target = (lineTop - padding).toInt().coerceAtLeast(0)
            scrollState.animateScrollTo(target)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = EConfigType.CUSTOM.toString(),
                onBackClick = onBackClick,
                actions = {
                    if (showDelete) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(painterResource(R.drawable.ic_delete_24dp), contentDescription = stringResource(R.string.menu_item_del_config))
                        }
                    }
                    IconButton(onClick = { onSave(remarks, textFieldValue.text) }) {
                        Icon(painterResource(R.drawable.ic_fab_check), contentDescription = stringResource(R.string.menu_item_save_config))
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            FormTextField(stringResource(R.string.server_lab_remarks), remarks, { remarks = it })

            BasicTextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .padding(bottom = 36.dp),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = Color(0xFF009966)
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                visualTransformation = remember(isDarkTheme) { JsonSyntaxVisualTransformation(isDarkTheme) },
                onTextLayout = { textLayoutResult = it }
            )
        }
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            message = stringResource(R.string.del_config_comfirm),
            confirmText = stringResource(android.R.string.ok),
            dismissText = stringResource(android.R.string.cancel),
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}
