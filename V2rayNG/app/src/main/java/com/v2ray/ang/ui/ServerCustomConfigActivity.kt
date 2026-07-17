package com.v2ray.ang.ui

import android.app.Activity
import android.os.Bundle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.compose.AppTopBar
import com.v2ray.ang.compose.ConfirmDialog
import com.v2ray.ang.compose.FormTextField
import com.v2ray.ang.compose.horizontalScrollbar
import com.v2ray.ang.compose.verticalScrollbar
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.fmt.CustomFmt
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.flow.collectLatest

class ServerCustomConfigActivity : BaseComponentActivity() {

    private val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    private val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false)
                && editGuid.isNotEmpty()
                && editGuid == MmkvManager.getSelectServer()
    }

    private var initialRemarks: String = ""
    private var initialContent: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        val config = MmkvManager.decodeServerConfig(editGuid)
        initialRemarks = config?.remarks ?: ""
        initialContent = MmkvManager.decodeServerRaw(editGuid).orEmpty()
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent() {
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

    private fun saveServer(
        remarks: String,
        content: String
    ): Boolean {
        if (remarks.isBlank()) {
            toast(R.string.server_lab_remarks)
            return false
        }

        val parsedProfile = try {
            CustomFmt.parse(content)
        } catch (e: Exception) {
            LogUtil.e(
                AppConfig.TAG,
                "Failed to parse custom configuration",
                e
            )
            toast(
                "${getString(R.string.toast_malformed_josn)} " +
                        "${e.cause?.message.orEmpty()}"
            )
            return false
        }

        val config =
            MmkvManager.decodeServerConfig(editGuid)
                ?: ProfileItem.create(EConfigType.CUSTOM)

        config.remarks =
            remarks.ifEmpty { parsedProfile?.remarks.orEmpty() }

        config.server = parsedProfile?.server
        config.serverPort = parsedProfile?.serverPort
        config.description =
            AngConfigManager.generateDescription(config)

        val savedGuid = MmkvManager.encodeServerConfig(
            editGuid,
            config
        )

        MmkvManager.encodeServerRaw(
            savedGuid,
            content
        )

        toastSuccess(R.string.toast_success)

        ProfileEditorResult.run {
            finishSaved(
                guid = savedGuid,
                restartService = isRunning
            )
        }

        return true
    }

    private fun deleteServer(): Boolean {
        if (editGuid.isEmpty()) {
            return false
        }

        if (editGuid == MmkvManager.getSelectServer()) {
            toast(R.string.toast_action_not_allowed)
            return false
        }

        MmkvManager.removeServer(editGuid)

        ProfileEditorResult.run {
            finishDeleted(editGuid)
        }

        return true
    }
}

private object EditorConstants {
    val FONT_SIZE = 14.sp
    val LINE_HEIGHT = 20.sp
    val LINE_NUMBER_HORIZONTAL_PADDING = 8.dp
    val SCROLLBAR_THICKNESS = 4.dp
    val SCROLLBAR_PADDING = 2.dp
    val SCROLL_PADDING = 60.dp
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
    var remarks by rememberSaveable { mutableStateOf(initialRemarks) }
    val textFieldState = rememberTextFieldState(initialText = initialContent)
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val showDelete = editGuid.isNotEmpty() && !isRunning

    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val density = LocalDensity.current

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var hasInitializedCursor by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!hasInitializedCursor) {
            textFieldState.edit {
                selection = TextRange(0, 0)
            }
            hasInitializedCursor = true
        }
    }

    val textMeasurer = rememberTextMeasurer()
    val lineCount = remember(textFieldState.text.toString()) {
        textFieldState.text.toString().count { it == '\n' } + 1
    }
    val lineNumberStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = EditorConstants.FONT_SIZE,
        lineHeight = EditorConstants.LINE_HEIGHT,
    )
    val lineNumberWidth: Dp = remember(lineCount, density) {
        val sampleText = lineCount.toString()
        val measured = textMeasurer.measure(
            text = sampleText,
            style = lineNumberStyle,
        )
        with(density) {
            measured.size.width.toDp() +
                    EditorConstants.LINE_NUMBER_HORIZONTAL_PADDING * 2
        }
    }

    LaunchedEffect(textFieldState, verticalScroll, horizontalScroll) {
        snapshotFlow {
            Triple(
                textFieldState.selection,
                verticalScroll.viewportSize,
                horizontalScroll.viewportSize
            )
        }.collectLatest { (selection, _, _) ->
            val layout = textLayoutResult ?: return@collectLatest
            val cursor = selection.start
            val textLen = layout.layoutInput.text.length

            if (textLen == 0 || cursor < 0 || cursor > textLen) return@collectLatest

            val line = layout.getLineForOffset(cursor)
            val lineTop = layout.getLineTop(line)
            val lineBottom = layout.getLineBottom(line)

            val vh = verticalScroll.viewportSize.toFloat()
            if (vh > 0f) {
                val scrollY = verticalScroll.value.toFloat()
                val pad = with(density) { EditorConstants.SCROLL_PADDING.toPx() }

                val targetY = when {
                    lineBottom > scrollY + vh - pad ->
                        (lineBottom - vh + pad).toInt()
                    lineTop < scrollY + pad ->
                        (lineTop - pad).toInt()
                    else -> null
                }
                targetY?.let {
                    verticalScroll.animateScrollTo(
                        it.coerceIn(0, verticalScroll.maxValue)
                    )
                }
            }

            val cursorX = layout.getHorizontalPosition(cursor, true)
            val vw = horizontalScroll.viewportSize.toFloat()
            if (vw > 0f) {
                val scrollX = horizontalScroll.value.toFloat()
                val pad = with(density) { EditorConstants.SCROLL_PADDING.toPx() }

                val targetX = when {
                    cursorX < scrollX + pad ->
                        (cursorX - pad).toInt().coerceAtLeast(0)
                    cursorX > scrollX + vw - pad ->
                        (cursorX - vw + pad).toInt()
                            .coerceAtMost(horizontalScroll.maxValue)
                    else -> null
                }
                targetX?.let {
                    horizontalScroll.animateScrollTo(it)
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        topBar = {
            AppTopBar(
                title = EConfigType.CUSTOM.toString(),
                onBackClick = onBackClick,
                actions = {
                    if (showDelete) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                painterResource(R.drawable.ic_delete_24dp),
                                contentDescription = stringResource(R.string.menu_item_del_config)
                            )
                        }
                    }
                    IconButton(onClick = { onSave(remarks, textFieldState.text.toString()) }) {
                        Icon(
                            painterResource(R.drawable.ic_fab_check),
                            contentDescription = stringResource(R.string.menu_item_save_config)
                        )
                    }
                }
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            FormTextField(
                label = stringResource(R.string.server_lab_remarks),
                value = remarks,
                onValueChange = { remarks = it }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(verticalScroll)
                ) {
                    val lineNumberColor =
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    val layoutForLineNumbers = textLayoutResult

                    if (layoutForLineNumbers != null && layoutForLineNumbers.lineCount > 0) {
                        val totalTextHeight = layoutForLineNumbers.size.height
                        Canvas(
                            modifier = Modifier
                                .width(lineNumberWidth)
                                .height(with(density) { totalTextHeight.toDp() })
                        ) {
                            val lc = layoutForLineNumbers.lineCount
                            for (i in 0 until lc) {
                                val lineLabel = (i + 1).toString()
                                val measured = textMeasurer.measure(
                                    text = lineLabel,
                                    style = lineNumberStyle.copy(
                                        color = lineNumberColor,
                                        textAlign = TextAlign.End,
                                    ),
                                )
                                val lineTop = layoutForLineNumbers.getLineTop(i)
                                val lineBaseline = layoutForLineNumbers.getLineBaseline(i)
                                val measuredBaseline = measured.firstBaseline
                                val yOffset = lineBaseline - measuredBaseline
                                val xOffset = size.width -
                                        EditorConstants.LINE_NUMBER_HORIZONTAL_PADDING.toPx() -
                                        measured.size.width
                                drawText(
                                    textLayoutResult = measured,
                                    topLeft = Offset(
                                        x = xOffset.coerceAtLeast(0f),
                                        y = yOffset
                                    )
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .width(lineNumberWidth)
                                .padding(end = EditorConstants.LINE_NUMBER_HORIZONTAL_PADDING),
                            contentAlignment = Alignment.TopEnd
                        ) {
                            Text(
                                text = "1",
                                style = lineNumberStyle.copy(color = lineNumberColor),
                            )
                        }
                    }

                    CompositionLocalProvider(
                        LocalTextSelectionColors provides TextSelectionColors(
                            handleColor = MaterialTheme.colorScheme.secondary,
                            backgroundColor = MaterialTheme.colorScheme.secondary.copy(
                                alpha = 0.4f
                            )
                        )
                    ) {
                        BasicTextField(
                            state = textFieldState,
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(horizontalScroll)
                                .padding(end = 24.dp)
                                .padding(bottom = 36.dp),
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = EditorConstants.FONT_SIZE,
                                lineHeight = EditorConstants.LINE_HEIGHT,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            lineLimits = TextFieldLineLimits.MultiLine(),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.secondary),
                            onTextLayout = { resultProvider ->
                                textLayoutResult = resultProvider()
                            },
                            decorator = { innerTextField ->
                                Box {
                                    if (textFieldState.text.isEmpty()) {
                                        Text(
                                            text = "{ }",
                                            style = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = EditorConstants.FONT_SIZE,
                                                lineHeight = EditorConstants.LINE_HEIGHT,
                                                color = MaterialTheme.colorScheme.onSurface.copy(
                                                    alpha = 0.38f
                                                )
                                            )
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(
                            EditorConstants.SCROLLBAR_THICKNESS +
                                    EditorConstants.SCROLLBAR_PADDING * 2
                        )
                        .verticalScrollbar(scrollState = verticalScroll)
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(
                            EditorConstants.SCROLLBAR_THICKNESS +
                                    EditorConstants.SCROLLBAR_PADDING * 2
                        )
                        .horizontalScrollbar(scrollState = horizontalScroll)
                )
            }
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
