package com.v2ray.ang.ui.server

import android.os.Bundle
import android.view.View
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.text.BidiFormatter
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.moveItem
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppIconButton
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.AppTopBarAction
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.DpadReorderItem
import com.v2ray.ang.ui.compose.FormTextField
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.ReorderableListItem
import com.v2ray.ang.ui.compose.ServerSelectButton
import com.v2ray.ang.ui.compose.ServerSelectListDialog
import com.v2ray.ang.ui.compose.TvTextFieldNavigation
import com.v2ray.ang.ui.compose.dpadLongPressToMove
import com.v2ray.ang.ui.compose.dpadMovePreviousNavigation
import com.v2ray.ang.ui.compose.dpadOrderedFocusNavigation
import com.v2ray.ang.ui.compose.dpadVerticalFocusNavigation
import com.v2ray.ang.ui.compose.isTelevisionDevice
import com.v2ray.ang.ui.compose.keepDpadReorderItemVisible
import com.v2ray.ang.ui.compose.rememberLazyDpadFocus
import com.v2ray.ang.ui.compose.rememberDpadFocusTargets
import com.v2ray.ang.ui.compose.rememberDpadFocusRequester
import com.v2ray.ang.ui.compose.rememberSyncedDpadReorderState
import com.v2ray.ang.ui.compose.reorderIndicesForKeys
import com.v2ray.ang.ui.compose.reorderableDragHandle
import com.v2ray.ang.ui.compose.requestFocusWhenReady
import com.v2ray.ang.ui.compose.tvAwareImePadding
import com.v2ray.ang.ui.compose.tvSafeAreaPadding
import com.v2ray.ang.ui.compose.verticalDpadReorderTarget
import com.v2ray.ang.ui.compose.verticalScrollbar
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.UUID

private data class ProxyChainMemberFocusTargets(
    val field: FocusRequester = FocusRequester(),
    val remove: FocusRequester = FocusRequester()
)

class ServerProxyChainActivity : BaseComponentActivity() {

    private val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    private val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false)
                && editGuid.isNotEmpty()
                && editGuid == MmkvManager.getSelectServer()
    }
    private val subscriptionId by lazy { intent.getStringExtra("subscriptionId") }

    private lateinit var allRemarks: List<String>
    private lateinit var initialRemarks: String
    private lateinit var initialMembers: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        allRemarks = SettingsManager.getProfileRemarks(
            excludeConfigTypes = setOf(
                EConfigType.CUSTOM,
                EConfigType.CUSTOM_OUTBOUND,
                EConfigType.POLICYGROUP,
                EConfigType.PROXYCHAIN,
            )
        )
        val config = MmkvManager.decodeServerConfig(editGuid)
        initialRemarks = config?.remarks ?: ""
        initialMembers = config?.proxyChainProfiles?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: listOf("", "")
    }

    @Composable
    override fun ScreenContent() {
        ProxyChainScreen(
            editGuid = editGuid,
            isRunning = isRunning,
            initialRemarks = initialRemarks,
            initialMembers = initialMembers,
            allRemarks = allRemarks,
            onBackClick = { finish() },
            onSave = { remarks, members -> saveServer(remarks, members) },
            onDelete = { deleteServer() }
        )
    }

    private fun saveServer(
        remarks: String,
        members: List<String>
    ): Boolean {
        if (remarks.isBlank()) {
            return false
        }

        val chainMembers = members
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (chainMembers.size != members.size) {
            toast(R.string.server_proxy_chain_members_unselected)
            return false
        }

        if (chainMembers.size < 2) {
            toast(R.string.server_proxy_chain_members_insufficient)
            return false
        }

        val invalidMembers = chainMembers.filter { member ->
            val entry = SettingsManager.getProfileEntryViaRemarks(member)
            entry == null || entry.profile.configType.isComplexType()
        }

        if (invalidMembers.isNotEmpty()) {
            toast(
                getString(
                    R.string.server_proxy_chain_members_invalid,
                    invalidMembers.joinToString(", ")
                )
            )
            return false
        }

        val config =
            MmkvManager.decodeServerConfig(editGuid)
                ?: ProfileItem.create(EConfigType.PROXYCHAIN)

        config.remarks = remarks.trim()
        config.proxyChainProfiles = chainMembers.joinToString(",")
        val isRtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val bidiFormatter = BidiFormatter.getInstance(isRtl)
        val chainSeparator = if (isRtl) " ← " else " → "
        config.description = chainMembers.joinToString(chainSeparator) { bidiFormatter.unicodeWrap(it) }

        if (
            config.subscriptionId.isEmpty() &&
            !subscriptionId.isNullOrEmpty()
        ) {
            config.subscriptionId = subscriptionId.orEmpty()
        }

        val savedGuid = MmkvManager.encodeServerConfig(
            editGuid,
            config
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

@Composable
fun ProxyChainScreen(
    editGuid: String,
    isRunning: Boolean,
    initialRemarks: String,
    initialMembers: List<String>,
    allRemarks: List<String>,
    onBackClick: () -> Unit,
    onSave: (String, List<String>) -> Boolean,
    onDelete: () -> Unit
) {
    val isTelevision = isTelevisionDevice()
    var remarks by rememberSaveable { mutableStateOf(initialRemarks) }
    var isRemarksError by rememberSaveable { mutableStateOf(false) }
    var savedMembers by rememberSaveable { mutableStateOf(initialMembers) }
    var savedMemberKeys by rememberSaveable { mutableStateOf(List(initialMembers.size) { UUID.randomUUID().toString() }) }
    val members = savedMembers
    val memberKeys = savedMemberKeys
    var showProfileDeleteConfirm by remember { mutableStateOf(false) }
    var memberToDeleteKey by rememberSaveable { mutableStateOf<String?>(null) }
    var editingMemberKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingMemberFocusKey by remember { mutableStateOf<String?>(null) }
    var pendingAddFocus by remember { mutableStateOf(false) }
    val showDelete = editGuid.isNotEmpty() && !isRunning

    val backFocusRequester = rememberDpadFocusRequester()
    val remarksFocusRequester = remember { FocusRequester() }
    val saveFocusRequester = remember { FocusRequester() }
    val addFocusRequester = remember { FocusRequester() }
    val memberFocusTargets = rememberDpadFocusTargets(memberKeys) { ProxyChainMemberFocusTargets() }
    val lazyListState = rememberLazyListState()
    val itemKeys = listOf("remarks_field", "members_header") + memberKeys
    val requestRowFocus = rememberLazyDpadFocus(
        listOf(listOf(remarksFocusRequester), emptyList()) + memberKeys.map {
            with(memberFocusTargets.getValue(it)) { listOf(field, remove) }
        }
    ) { lazyListState.scrollToItem(it) }
    val dpadReorderState = rememberSyncedDpadReorderState(memberKeys, isTelevision) { key, index ->
        val memberKey = key as? String ?: return@rememberSyncedDpadReorderState
        val focusTargets = memberFocusTargets[memberKey]
        if (index >= 0 && focusTargets != null) {
            lazyListState.keepDpadReorderItemVisible(memberKey, itemKeys.indexOf(memberKey))
            requestFocusWhenReady(focusTargets.field)
        }
    }

    fun moveMember(fromIdx: Int, toIdx: Int) {
        val reordered = savedMembers.toMutableList()
        if (reordered.moveItem(fromIdx, toIdx)) {
            savedMembers = reordered
            savedMemberKeys = savedMemberKeys.toMutableList().also { it.moveItem(fromIdx, toIdx) }
        }
    }

    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        reorderIndicesForKeys(memberKeys, from.key, to.key)?.let { (fromIndex, toIndex) ->
            moveMember(fromIndex, toIndex)
        }
    }

    LaunchedEffect(pendingMemberFocusKey, pendingAddFocus, memberKeys) {
        val target = pendingMemberFocusKey?.let { memberFocusTargets[it]?.field }
            ?: addFocusRequester.takeIf { pendingAddFocus }
        if (target != null) requestRowFocus(target)
        pendingMemberFocusKey = null
        pendingAddFocus = false
    }

    fun addMember() {
        val memberKey = UUID.randomUUID().toString()
        savedMembers = savedMembers + ""
        savedMemberKeys = savedMemberKeys + memberKey
        if (isTelevision) pendingMemberFocusKey = memberKey
    }

    fun removeMember(memberKey: String) {
        val index = savedMemberKeys.indexOf(memberKey)
        if (index < 0) return
        val nextFocusKey = savedMemberKeys.getOrNull(index + 1) ?: savedMemberKeys.getOrNull(index - 1)
        val (remainingMembers, remainingKeys) = withoutProxyChainMember(savedMembers, savedMemberKeys, memberKey)
        savedMembers = remainingMembers
        savedMemberKeys = remainingKeys
        if (isTelevision) {
            pendingMemberFocusKey = nextFocusKey
            pendingAddFocus = nextFocusKey == null
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = EConfigType.PROXYCHAIN.toString(),
                onBackClick = onBackClick,
                navigationFocusRequester = backFocusRequester,
                onMoveDown = { requestRowFocus(remarksFocusRequester) },
                actionItems = buildList {
                    if (showDelete) add(AppTopBarAction(
                        painterResource(R.drawable.ic_delete_24dp), stringResource(R.string.menu_item_del_config),
                        onClick = { showProfileDeleteConfirm = true }
                    ))
                    add(AppTopBarAction(
                        painterResource(R.drawable.ic_fab_check), stringResource(R.string.menu_item_save_config),
                        onClick = {
                            isRemarksError = remarks.isBlank()
                            if (!isRemarksError) onSave(remarks, savedMembers)
                        },
                        focusRequester = saveFocusRequester
                    ))
                }
            )
        },
        floatingActionButton = {
            if (isTelevision) {
                AppIconButton(
                    icon = painterResource(R.drawable.ic_add_24dp),
                    label = stringResource(R.string.action_add_member),
                    focusRequester = addFocusRequester,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .offset(y = -20.dp)
                        .navigationBarsPadding()
                        .dpadOrderedFocusNavigation(addFocusRequester, listOf(addFocusRequester))
                        .dpadVerticalFocusNavigation(
                            onMoveUp = {
                                memberKeys.lastOrNull()
                                    ?.let { memberFocusTargets[it]?.field?.let(requestRowFocus) }
                                    ?: requestRowFocus(remarksFocusRequester)
                            },
                            onMoveDown = { true }
                        ),
                    onClick = ::addMember
                )
            } else {
                FloatingActionButton(onClick = ::addMember, modifier = Modifier.offset(y = -20.dp).navigationBarsPadding()) {
                    Icon(painterResource(R.drawable.ic_add_24dp), stringResource(R.string.action_add_member))
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .tvAwareImePadding()
                .verticalScrollbar(lazyListState)
                .tvSafeAreaPadding(),
            contentPadding = if (isTelevision) PaddingValues(
                top = 8.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = 36.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            ) else NavigationBarsBottomPadding()
        ) {
            item(key = "remarks_field") {
                FormTextField(
                    label = stringResource(R.string.server_lab_remarks),
                    value = remarks,
                    onValueChange = { remarks = it },
                    isError = isRemarksError,
                    tvNavigation = TvTextFieldNavigation(
                        focusRequester = remarksFocusRequester,
                        onMoveUp = { backFocusRequester.requestFocus() },
                        onMoveDown = {
                            memberKeys.firstOrNull()
                                ?.let { memberFocusTargets[it]?.field?.let(requestRowFocus) }
                                ?: addFocusRequester.requestFocus()
                        }
                    )
                )
            }

            item(key = "members_header") {
                Text(
                    text = stringResource(R.string.server_proxy_chain_members),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                )
            }

            itemsIndexed(items = members, key = { index, _ -> memberKeys[index] }) { index, member ->
                val memberKey = memberKeys[index]
                val focusTargets = memberFocusTargets.getValue(memberKey)
                val previousTargets = memberKeys.getOrNull(index - 1)
                    ?.let(memberFocusTargets::get)
                val nextTargets = memberKeys.getOrNull(index + 1)
                    ?.let(memberFocusTargets::get)
                val dpadReorderItem = DpadReorderItem(
                    state = dpadReorderState,
                    key = memberKey,
                    index = index,
                    itemCount = memberKeys.size,
                    targetIndex = ::verticalDpadReorderTarget,
                    onMove = ::moveMember
                )
                val isMoving = dpadReorderState.isMoving(memberKey)
                val openMemberPicker: () -> Unit = { editingMemberKey = memberKey }
                val actionFocusOrder = remember(focusTargets) {
                    listOf(focusTargets.field, focusTargets.remove)
                }

                ReorderableItem(reorderableState, key = memberKey, modifier = Modifier.zIndex(if (isMoving) 1f else 0f)) { isDragging ->
                    val itemScope = this
                    val rowContent: @Composable RowScope.() -> Unit = {
                        if (isTelevision) {
                            Text("${index + 1}", modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_menu_24dp),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.reorderableDragHandle(itemScope).padding(4.dp)
                            )
                        }
                    ServerSelectButton(
                        label = stringResource(R.string.server_proxy_chain_member),
                        value = member,
                        placeholder = stringResource(R.string.server_proxy_chain_member_unselected),
                        focusRequester = focusTargets.field,
                        showFocus = !isMoving,
                        onClick = openMemberPicker,
                        modifier = Modifier
                            .weight(1f)
                            .dpadLongPressToMove(
                                enabled = isTelevision,
                                item = dpadReorderItem,
                                onClick = openMemberPicker,
                                addFocusTarget = false
                            )
                            .dpadOrderedFocusNavigation(
                                current = focusTargets.field,
                                order = actionFocusOrder,
                                onBeforeFirst = { backFocusRequester.requestFocus() }
                            )
                            .dpadVerticalFocusNavigation(
                                onMoveUp = {
                                    previousTargets?.field?.let(requestRowFocus)
                                        ?: requestRowFocus(remarksFocusRequester)
                                },
                                onMoveDown = {
                                    nextTargets?.field?.let(requestRowFocus)
                                        ?: addFocusRequester.requestFocus()
                                }
                            )
                    )
                    AppIconButton(
                        icon = painterResource(R.drawable.ic_delete_24dp),
                        label = stringResource(R.string.action_remove),
                        focusRequester = focusTargets.remove,
                        modifier = Modifier
                            .dpadOrderedFocusNavigation(focusTargets.remove, actionFocusOrder)
                            .dpadVerticalFocusNavigation(
                                onMoveUp = {
                                    previousTargets?.remove?.let(requestRowFocus)
                                        ?: saveFocusRequester.requestFocus()
                                },
                                onMoveDown = {
                                    nextTargets?.remove?.let(requestRowFocus)
                                        ?: addFocusRequester.requestFocus()
                                }
                            ),
                        onClick = {
                            if (member.isBlank()) removeMember(memberKey)
                            else memberToDeleteKey = memberKey
                        }
                    )
                    }
                    if (isTelevision) {
                        ReorderableListItem(itemScope, isDragging, isMoving) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .dpadMovePreviousNavigation(enabled = !dpadReorderState.isMoving) {
                                        backFocusRequester.requestFocus()
                                    }
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                content = rowContent
                            )
                        }
                    } else {
                        val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shadowElevation = elevation
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                content = rowContent
                            )
                        }
                    }
                }
            }
        }
    }

    if (showProfileDeleteConfirm) {
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_profile),
            itemName = initialRemarks,
            onConfirm = { showProfileDeleteConfirm = false; onDelete() },
            onDismiss = { showProfileDeleteConfirm = false }
        )
    }
    memberToDeleteKey?.let { memberKey ->
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_proxy_chain_member),
            itemName = members.getOrNull(memberKeys.indexOf(memberKey)).orEmpty(),
            onConfirm = {
                removeMember(memberKey)
                memberToDeleteKey = null
            },
            onDismiss = { memberToDeleteKey = null }
        )
    }
    editingMemberKey?.takeIf { it in memberKeys }?.let { memberKey ->
        fun closePicker() {
            editingMemberKey = null
            if (isTelevision) pendingMemberFocusKey = memberKey
        }

        ServerSelectListDialog(
            title = stringResource(
                if (isTelevision) R.string.server_proxy_chain_member_unselected else R.string.server_proxy_chain_pick_members
            ),
            options = allRemarks,
            selectedOption = members.getOrNull(memberKeys.indexOf(memberKey)),
            onSelected = { selectedRemark ->
                val index = savedMemberKeys.indexOf(memberKey)
                if (index >= 0) savedMembers = savedMembers.toMutableList().also { it[index] = selectedRemark }
                closePicker()
            },
            onDismiss = ::closePicker
        )
    }
}
