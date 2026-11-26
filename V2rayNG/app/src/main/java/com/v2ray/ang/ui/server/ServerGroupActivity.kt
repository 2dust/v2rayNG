package com.v2ray.ang.ui.server

import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.AppConfig.BUILTIN_OUTBOUND_TAGS
import com.v2ray.ang.AppConfig.TAG_PROXY
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.BalancerStrategyType
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.base.televisionResultDestination
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.FormDropdownField
import com.v2ray.ang.ui.compose.FormTextField
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.ProfilePickerChoices
import com.v2ray.ang.ui.compose.ServerSelectListDialog
import com.v2ray.ang.ui.compose.SettingsSwitchItem

class ServerGroupActivity : BaseServerGroupActivity() {
    override fun televisionDestination() = televisionResultDestination(TvServerGroupActivity::class.java)

    @Composable
    override fun EditorLayout(onBack: () -> Unit, onSave: () -> Unit, onDelete: (() -> Unit)?, fields: @Composable ColumnScope.() -> Unit) =
        PhonePolicyGroupEditor(onBack, onSave, onDelete, fields)

    @Composable
    override fun FallbackField(value: String, onSelect: (String) -> Unit) = PhonePolicyFallbackField(value, onSelect)
}

abstract class BaseServerGroupActivity : BaseComponentActivity() {
    private var forwardingToTv = false

    protected open fun televisionDestination(): Intent? = null

    @Composable
    internal abstract fun EditorLayout(onBack: () -> Unit, onSave: () -> Unit, onDelete: (() -> Unit)?, fields: @Composable ColumnScope.() -> Unit)

    @Composable
    internal abstract fun FallbackField(value: String, onSelect: (String) -> Unit)

    private val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    private val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false)
                && editGuid.isNotEmpty()
                && editGuid == MmkvManager.getSelectServer()
    }
    private val subscriptionId by lazy { intent.getStringExtra("subscriptionId") }
    private lateinit var sourceLabels: Map<String, String>

    private lateinit var initialRemarks: String
    private lateinit var initialFilter: String
    private var initialType: Int = 0
    private var initialSourceId = ""
    private var initialTestOutbounds: Boolean = false
    private lateinit var initialFallbackTag: String

    override fun onCreate(savedInstanceState: Bundle?) {
        val destination = televisionDestination()
        forwardingToTv = destination != null
        super.onCreate(savedInstanceState)
        if (destination != null) {
            startActivity(destination)
            finish()
            return
        }

        val config = MmkvManager.decodeServerConfig(editGuid)
        sourceLabels = buildMap {
            put("", getString(R.string.filter_config_all))
            MmkvManager.decodeSubscriptions().forEach { sub -> put(sub.guid, sub.subscription.remarks.ifBlank { sub.guid }) }
        }

        initialRemarks = config?.remarks ?: ""
        initialFilter = config?.policyGroupFilter ?: ""
        initialType = config?.policyGroupType?.toIntOrNull() ?: 0
        initialTestOutbounds = config == null || config.policyGroupTestOutbounds != false ||
                !BalancerStrategyType.from(config.policyGroupType).supportsObservatory
        initialFallbackTag = config?.policyGroupFallbackTag.orEmpty()
        initialSourceId = (if (config != null) config.policyGroupSubscriptionId else subscriptionId)
            .takeIf { it in sourceLabels }.orEmpty()
    }

    @Composable
    final override fun ScreenContent() {
        if (forwardingToTv) return
        ServerGroupScreen(
            editGuid = editGuid,
            isRunning = isRunning,
            sourceLabels = sourceLabels,
            initialRemarks = initialRemarks,
            initialFilter = initialFilter,
            initialType = initialType,
            initialSourceId = initialSourceId,
            initialTestOutbounds = initialTestOutbounds,
            initialFallbackTag = initialFallbackTag,
            onBackClick = { finish() },
            onSave = { remarks, filter, typeIdx, sourceId, testOutbounds, fallbackTag ->
                saveServer(remarks, filter, typeIdx, sourceId, testOutbounds, fallbackTag)
            },
            onDelete = { deleteServer() }
        )
    }

    private fun saveServer(
        remarks: String,
        filter: String,
        typeIdx: Int,
        sourceId: String,
        testOutbounds: Boolean,
        fallbackTag: String,
    ): Boolean {
        if (remarks.isBlank()) {
            return false
        }

        val config =
            MmkvManager.decodeServerConfig(editGuid)
                ?: ProfileItem.create(EConfigType.POLICYGROUP)

        config.remarks = remarks.trim()
        config.policyGroupFilter = filter.trim()
        config.policyGroupType = typeIdx.toString()
        config.policyGroupSubscriptionId = sourceId
        config.policyGroupTestOutbounds = testOutbounds
        config.policyGroupFallbackTag = fallbackTag.trim().takeIf { it.isNotEmpty() }

        if (
            config.subscriptionId.isEmpty() &&
            !subscriptionId.isNullOrEmpty()
        ) {
            config.subscriptionId = subscriptionId.orEmpty()
        }

        val typeDisplay =
            stringArrayPolicyGroupType()
                .getOrNull(typeIdx)
                .orEmpty()

        config.description = buildString {
            append(typeDisplay)
            append(" - ")
            append(sourceLabels[sourceId].orEmpty())
            append(" - ")
            append(config.policyGroupFilter)
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

    private fun stringArrayPolicyGroupType(): Array<String> =
        resources.getStringArray(R.array.policy_group_type)
}

@Composable
private fun BaseServerGroupActivity.ServerGroupScreen(
    editGuid: String,
    isRunning: Boolean,
    sourceLabels: Map<String, String>,
    initialRemarks: String,
    initialFilter: String,
    initialType: Int,
    initialSourceId: String,
    initialTestOutbounds: Boolean,
    initialFallbackTag: String,
    onBackClick: () -> Unit,
    onSave: (String, String, Int, String, Boolean, String) -> Boolean,
    onDelete: () -> Unit
) {
    val typeEntries = stringArrayResource(R.array.policy_group_type).toList()

    var remarks by rememberSaveable { mutableStateOf(initialRemarks) }
    var isRemarksError by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(initialFilter) }
    var typeIndex by rememberSaveable { mutableStateOf(initialType.coerceIn(typeEntries.indices)) }
    var sourceId by rememberSaveable { mutableStateOf(initialSourceId) }
    var testOutbounds by rememberSaveable { mutableStateOf(initialTestOutbounds) }
    var fallbackTag by rememberSaveable { mutableStateOf(initialFallbackTag) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    val showDelete = editGuid.isNotEmpty() && !isRunning
    val supportsObservatory = BalancerStrategyType.from(typeIndex.toString()).supportsObservatory
    val save: () -> Unit = {
        isRemarksError = remarks.isBlank()
        if (!isRemarksError) onSave(remarks, filter, typeIndex, sourceId.takeIf { it in sourceLabels }.orEmpty(), testOutbounds, fallbackTag)
    }
    val fields: @Composable ColumnScope.() -> Unit = {
        FormTextField(
            label = stringResource(R.string.server_lab_remarks),
            value = remarks,
            onValueChange = { remarks = it },
            isError = isRemarksError, announceValue = true
        )
        FormDropdownField(
            label = stringResource(R.string.title_policy_group_type),
            value = typeEntries[typeIndex],
            options = typeEntries,
            onValueChange = { typeIndex = typeEntries.indexOf(it) }
        )
        val sourceIds = remember(sourceLabels) { sourceLabels.keys.toList() }
        FormDropdownField(stringResource(R.string.title_policy_group_subscription_id),
            sourceId.takeIf { it in sourceLabels }.orEmpty(), sourceIds, { sourceId = it },
            optionLabel = { sourceLabels[it].orEmpty() })
        FormTextField(stringResource(R.string.title_policy_group_subscription_filter), filter, { filter = it }, announceValue = true)
        if (supportsObservatory) {
            SettingsSwitchItem(
                title = stringResource(R.string.title_policy_group_test_outbounds),
                checked = testOutbounds,
                onCheckedChange = { testOutbounds = it }
            )
            if (testOutbounds) {
                FallbackField(fallbackTag) { fallbackTag = it }
            }
        }
    }
    val delete: (() -> Unit)? = if (showDelete) ({ showDeleteConfirm = true }) else null
    EditorLayout(onBackClick, save, delete, fields)

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_policy_group),
            itemName = initialRemarks,
            onConfirm = onDelete,
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

@Composable
internal fun PolicyFallbackChoices(onDismiss: () -> Unit, content: @Composable (List<String>) -> Unit) {
    ProfilePickerChoices({ (BUILTIN_OUTBOUND_TAGS + SettingsManager.getSimpleProfileRemarks()).filter { it != TAG_PROXY } }, onDismiss, content)
}

@Composable
private fun PhonePolicyFallbackField(value: String, onSelect: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    PolicyFallbackRow(value, onPick = { open = true }, onClear = { onSelect("") })
    if (open) PolicyFallbackChoices({ open = false }) { options ->
        ServerSelectListDialog(
            title = stringResource(R.string.title_policy_group_fallback), options = options, selectedOption = value,
            onSelected = { onSelect(it); open = false }, onDismiss = { open = false }
        )
    }
}

@Composable
internal fun PolicyFallbackRow(
    value: String, onPick: () -> Unit, onClear: () -> Unit,
    pickModifier: Modifier = Modifier, clearModifier: Modifier = Modifier
) {
    val label = stringResource(R.string.title_policy_group_fallback)
    val display = value.ifBlank { stringResource(R.string.server_proxy_chain_member_unselected) }
    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp))
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.weight(1f).then(pickModifier).semantics {
                contentDescription = label
                stateDescription = display
            }.clickable(role = Role.Button, onClick = onPick),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Text(
                text = display,
                style = MaterialTheme.typography.bodyMedium,
                color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (value.isBlank()) FontWeight.Normal else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clearAndSetSemantics {}.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
        IconButton(onClick = onClear, modifier = clearModifier) {
            Icon(painterResource(R.drawable.ic_delete_24dp), "${stringResource(R.string.logcat_clear)}, $label")
        }
    }
}

@Composable
private fun PhonePolicyGroupEditor(
    onBack: () -> Unit, onSave: () -> Unit, onDelete: (() -> Unit)?,
    fields: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(EConfigType.POLICYGROUP.toString(), onBackClick = onBack, actions = {
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(painterResource(R.drawable.ic_delete_24dp), stringResource(R.string.acc_delete))
                    }
                }
                IconButton(onClick = onSave) {
                    Icon(painterResource(R.drawable.ic_fab_check), stringResource(R.string.acc_save))
                }
            })
        }
    ) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding).imePadding()
                .padding(vertical = 8.dp).verticalScroll(rememberScrollState())
        ) {
            fields()
            NavigationBarsSpacer()
        }
    }
}
