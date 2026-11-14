package com.v2ray.ang.ui.server

import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager.LayoutParams
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.FormTextField
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.ServerSelectListDialog
import com.v2ray.ang.ui.compose.reorderableDragHandle
import com.v2ray.ang.ui.compose.verticalScrollbar
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.UUID

class ServerProxyChainActivity : BaseComponentActivity() {

    private val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    private val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false)
                && editGuid.isNotEmpty()
                && editGuid == MmkvManager.getSelectServer()
    }
    private val subscriptionId by lazy { intent.getStringExtra("subscriptionId") }
    private var isTelevision = false

    private lateinit var allRemarks: List<String>
    private lateinit var initialRemarks: String
    private lateinit var initialMembers: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isTelevision = resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
        if (isTelevision && savedInstanceState != null) {
            val adjustment = window.attributes.softInputMode and LayoutParams.SOFT_INPUT_MASK_ADJUST
            window.setSoftInputMode(adjustment or LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        }

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
        initialMembers = config?.proxyChainProfileList?.map { it.trim() }?.filter { it.isNotEmpty() } ?: listOf("", "")
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
            onDelete = { deleteServer() },
            isTelevision = isTelevision
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
        config.proxyChainProfileList = chainMembers

        config.description =
            chainMembers.joinToString(" -> ")

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
private fun ProxyChainScreen(
    editGuid: String,
    isRunning: Boolean,
    initialRemarks: String,
    initialMembers: List<String>,
    allRemarks: List<String>,
    onBackClick: () -> Unit,
    onSave: (String, List<String>) -> Boolean,
    onDelete: () -> Unit,
    isTelevision: Boolean
) {
    var remarks by rememberSaveable { mutableStateOf(initialRemarks) }
    var isRemarksError by rememberSaveable { mutableStateOf(false) }
    var members by rememberSaveable { mutableStateOf(initialMembers) }
    var memberKeys by rememberSaveable { mutableStateOf(List(initialMembers.size) { UUID.randomUUID().toString() }) }
    var showProfileDeleteConfirm by remember { mutableStateOf(false) }
    var memberToDeleteKey by rememberSaveable { mutableStateOf<String?>(null) }
    var editingMemberKey by rememberSaveable { mutableStateOf<String?>(null) }
    val showDelete = editGuid.isNotEmpty() && !isRunning
    var focusMemberKey by remember { mutableStateOf<String?>(null) }
    val add: () -> Unit = {
        val key = UUID.randomUUID().toString()
        members = members + ""
        memberKeys = memberKeys + key
        if (isTelevision) focusMemberKey = key
    }
    fun move(from: Int, to: Int) {
        val reordered = members.toMutableList()
        val reorderedKeys = memberKeys.toMutableList()
        if (reordered.moveItem(from, to)) {
            reorderedKeys.moveItem(from, to)
            members = reordered
            memberKeys = reorderedKeys
        }
    }
    fun remove(key: String) {
        val index = memberKeys.indexOf(key)
        if (isTelevision && index >= 0) {
            focusMemberKey = memberKeys.getOrNull(index + 1) ?: memberKeys.getOrNull(index - 1) ?: TV_CHAIN_ADD
        }
        val (remainingMembers, remainingKeys) = withoutProxyChainMember(members, memberKeys, key)
        members = remainingMembers
        memberKeys = remainingKeys
    }
    val save: () -> Unit = {
        isRemarksError = remarks.isBlank()
        if (!isRemarksError) onSave(remarks, members)
    }
    val delete: (() -> Unit)? = if (showDelete) ({ showProfileDeleteConfirm = true }) else null
    val removeMember: (String, String) -> Unit = { key, member ->
        if (member.isBlank()) remove(key) else memberToDeleteKey = key
    }

    if (isTelevision) {
        TvProxyChainEditor(
            remarks, { remarks = it }, isRemarksError, members, memberKeys, focusMemberKey, { focusMemberKey = null },
            onBackClick, save, delete, add, { editingMemberKey = it }, removeMember,
            { key, step ->
                val index = memberKeys.indexOf(key)
                if (index >= 0) move(index, index + step)
            }
        )
    } else {
        PhoneProxyChainEditor(
            remarks, { remarks = it }, isRemarksError, members, memberKeys, onBackClick, save, delete, add,
            { editingMemberKey = it }, removeMember, ::move
        )
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
            onConfirm = { remove(memberKey); memberToDeleteKey = null },
            onDismiss = { memberToDeleteKey = null }
        )
    }
    editingMemberKey?.let { memberKey ->
        fun closePicker() {
            editingMemberKey = null
            if (isTelevision) focusMemberKey = memberKey
        }
        if (memberKey in memberKeys) {
            val selected = members.getOrNull(memberKeys.indexOf(memberKey))
            val select: (String) -> Unit = { remark ->
                val index = memberKeys.indexOf(memberKey)
                if (index >= 0) members = members.toMutableList().also { it[index] = remark }
                closePicker()
            }
            if (isTelevision) {
                TvProfilePicker(stringResource(R.string.server_proxy_chain_member_unselected), allRemarks, selected, select, ::closePicker)
            } else {
                ServerSelectListDialog(
                    title = stringResource(R.string.server_proxy_chain_pick_members),
                    options = allRemarks,
                    selectedOption = selected,
                    onSelected = select,
                    onDismiss = ::closePicker
                )
            }
        }
    }
}

@Composable
private fun PhoneProxyChainEditor(
    remarks: String, onRemarksChange: (String) -> Unit, isRemarksError: Boolean,
    members: List<String>, memberKeys: List<String>,
    onBackClick: () -> Unit, onSave: () -> Unit, onDelete: (() -> Unit)?, onAdd: () -> Unit,
    onPick: (String) -> Unit, onRemove: (String, String) -> Unit, onMove: (Int, Int) -> Unit
) {
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIndex = memberKeys.indexOf(from.key)
        val toIndex = memberKeys.indexOf(to.key)
        if (fromIndex != -1 && toIndex != -1) onMove(fromIndex, toIndex)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = EConfigType.PROXYCHAIN.toString(),
                onBackClick = onBackClick,
                actions = {
                    if (onDelete != null) {
                        IconButton(onClick = onDelete) {
                            Icon(painterResource(R.drawable.ic_delete_24dp), contentDescription = stringResource(R.string.acc_delete))
                        }
                    }
                    IconButton(onClick = onSave) {
                        Icon(painterResource(R.drawable.ic_fab_check), contentDescription = stringResource(R.string.acc_save))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                modifier = Modifier
                    .offset(y = -20.dp)
                    .navigationBarsPadding()
            ) {
                Icon(painterResource(R.drawable.ic_add_24dp), contentDescription = stringResource(R.string.acc_add_member))
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .verticalScrollbar(lazyListState),
            contentPadding = NavigationBarsBottomPadding()
        ) {
            item(key = "remarks_field") {
                FormTextField(
                    label = stringResource(R.string.server_lab_remarks),
                    value = remarks,
                    onValueChange = onRemarksChange,
                    isError = isRemarksError
                )
            }

            item {
                Text(
                    text = stringResource(R.string.server_proxy_chain_members),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                )
            }

            itemsIndexed(items = members, key = { index, _ -> memberKeys[index] }) { index, member ->
                val memberKey = memberKeys[index]
                ReorderableItem(reorderableState, key = memberKey) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shadowElevation = elevation
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .then(with(this@ReorderableItem) { reorderableDragHandle() })
                                    .padding(4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_menu_24dp),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onPick(memberKey) },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = member.ifBlank { stringResource(R.string.server_proxy_chain_member_unselected) },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (member.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (member.isBlank()) FontWeight.Normal else FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                            IconButton(
                                onClick = { onRemove(memberKey, member) },
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(36.dp)
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_delete_24dp),
                                    contentDescription = stringResource(R.string.acc_remove),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

}
