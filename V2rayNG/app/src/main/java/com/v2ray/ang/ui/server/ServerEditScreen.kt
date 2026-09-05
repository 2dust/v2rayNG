package com.v2ray.ang.ui.server

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.ui.base.BaseScreen
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.StringOptions
import com.v2ray.ang.ui.compose.verticalScrollbar
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest

private const val RawContentSyncDebounceMs = 250L
private val ChainMemberFabOffsetY = (-28).dp

@Composable
fun ServerEditScreen(viewModel: ServerEditViewModel) {
    val header by viewModel.header.collectAsStateWithLifecycle()
    var pendingDialog by rememberSaveable { mutableStateOf<ServerDialog?>(null) }

    val editorState = remember { mutableStateOf<TextFieldState?>(null) }
    val onAction: (ServerAction) -> Unit = remember(viewModel) {
        { action ->
            if (action is ServerAction.Save) {
                editorState.value?.let {
                    viewModel.onAction(ServerAction.RawContentChanged(it.text.toString()))
                }
            }
            viewModel.onAction(action)
        }
    }

    BaseScreen(
        viewModel = viewModel,
        topBar = { ServerEditTopBar(header = header, onAction = onAction) },
        floatingActionButton = { ChainMemberFab(configType = header.configType, onAction = onAction) },
        onEvent = { event ->
            when (event) {
                ServerEvent.ConfirmDeleteProfile -> {
                    pendingDialog = ServerDialog.DeleteProfile
                    true
                }
                is ServerEvent.ConfirmRemoveChainMember -> {
                    pendingDialog = ServerDialog.RemoveChainMember(event.id)
                    true
                }
                else -> false
            }
        },
    ) { state, _ ->
        val rawContent = rememberSyncedRawContent(
            guid = state.guid,
            externalValue = state.rawContent,
            onAction = onAction,
            editorSlot = editorState,
        )

        ServerEditContent(
            configType = state.configType,
            form = state.form,
            options = state.options,
            isFetchingCert = state.isFetchingCert,
            rawContent = rawContent,
            onAction = onAction,
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        )

        ServerEditDialogs(
            dialog = pendingDialog,
            configType = state.configType,
            onAction = onAction,
            onDismiss = { pendingDialog = null },
        )
    }
}

@Composable
private fun rememberSyncedRawContent(
    guid: String,
    externalValue: String,
    onAction: (ServerAction) -> Unit,
    editorSlot: MutableState<TextFieldState?>,
): TextFieldState {
    val textFieldState = rememberTextFieldState()

    var synced by remember(guid) { mutableStateOf<String?>(null) }

    DisposableEffect(textFieldState, editorSlot) {
        editorSlot.value = textFieldState
        onDispose { editorSlot.value = null }
    }

    LaunchedEffect(guid, externalValue) {
        if (externalValue == synced) return@LaunchedEffect
        val current = textFieldState.text.toString()
        if (externalValue == current) {
            synced = externalValue
            return@LaunchedEffect
        }
        synced = externalValue
        textFieldState.edit {
            replace(0, length, externalValue)
            selection = TextRange(0, 0)
        }
    }

    // editor -> ViewModel, debounced so a large config is not re-serialised on every keystroke.
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .debounce(RawContentSyncDebounceMs)
            .collectLatest { text ->
                if (text == synced) return@collectLatest
                synced = text
                onAction(ServerAction.RawContentChanged(text))
            }
    }

    return textFieldState
}

@Composable
private fun ServerEditTopBar(header: ServerHeader, onAction: (ServerAction) -> Unit) {
    AppTopBar(
        title = header.configType.toString(),
        onBackClick = { onAction(ServerAction.Back) },
        actions = {
            if (header.canDelete) {
                IconButton(onClick = { onAction(ServerAction.DeleteClicked) }) {
                    Icon(
                        painterResource(R.drawable.ic_delete_24dp),
                        contentDescription = stringResource(R.string.menu_item_del_config),
                    )
                }
            }
            IconButton(onClick = { onAction(ServerAction.Save) }) {
                Icon(
                    painterResource(R.drawable.ic_fab_check),
                    contentDescription = stringResource(R.string.menu_item_save_config),
                )
            }
        },
    )
}

@Composable
private fun ChainMemberFab(configType: EConfigType, onAction: (ServerAction) -> Unit) {
    if (configType != EConfigType.PROXYCHAIN) return
    FloatingActionButton(
        onClick = { onAction(ServerAction.AddChainMember) },
        modifier = Modifier
            .offset(y = ChainMemberFabOffsetY)
            .navigationBarsPadding(),
    ) {
        Icon(
            painterResource(R.drawable.ic_add_24dp),
            contentDescription = stringResource(R.string.server_proxy_chain_members),
        )
    }
}

@Composable
private fun ServerEditDialogs(
    dialog: ServerDialog?,
    configType: EConfigType,
    onAction: (ServerAction) -> Unit,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        ServerDialog.DeleteProfile -> DeleteConfirmDialog(
            message = stringResource(
                if (configType == EConfigType.POLICYGROUP) R.string.confirm_delete_policy_group
                else R.string.confirm_delete_profile
            ),
            onConfirm = {
                onAction(ServerAction.ConfirmDeleteProfile)
                onDismiss()
            },
            onDismiss = onDismiss,
        )

        is ServerDialog.RemoveChainMember -> DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_proxy_chain_member),
            onConfirm = {
                onAction(ServerAction.ConfirmRemoveChainMember(dialog.id))
                onDismiss()
            },
            onDismiss = onDismiss,
        )

        null -> Unit
    }
}

@Composable
private fun ServerEditContent(
    configType: EConfigType,
    form: ServerForm,
    options: ServerOptions,
    isFetchingCert: Boolean,
    rawContent: TextFieldState,
    onAction: (ServerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        when (configType) {
            EConfigType.CUSTOM -> {
                ServerJsonEditor(
                    remarks = form.remarks,
                    rawContent = rawContent,
                    onAction = onAction,
                    modifier = Modifier.weight(1f),
                )
            }

            EConfigType.PROXYCHAIN -> {
                ProxyChainForm(
                    remarks = form.remarks,
                    members = form.chainMembers,
                    candidates = StringOptions(options.profileRemarks),
                    onAction = onAction,
                    modifier = Modifier.weight(1f),
                    contentPadding = NavigationBarsBottomPadding(extra = 64.dp)
                )
            }

            EConfigType.POLICYGROUP -> {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScrollbar(scrollState)
                        .verticalScroll(scrollState)
                        .padding(top = 8.dp),
                ) {
                    PolicyGroupForm(form = form, options = options, onAction = onAction)
                    NavigationBarsSpacer()
                }
            }

            else -> {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScrollbar(scrollState)
                        .verticalScroll(scrollState)
                        .padding(top = 8.dp),
                ) {
                    ProtocolForm(
                        configType = configType,
                        form = form,
                        isFetchingCert = isFetchingCert,
                        onAction = onAction,
                    )
                    Spacer(modifier = Modifier.height(36.dp))
                    NavigationBarsSpacer()
                }
            }
        }
    }
}
