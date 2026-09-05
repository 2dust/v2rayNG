package com.v2ray.ang.ui.subscription

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseScreen
import com.v2ray.ang.ui.compose.AppTheme
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.FormDropdownField
import com.v2ray.ang.ui.compose.FormTextField
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.SettingsSwitchItem
import com.v2ray.ang.ui.compose.StringOptions
import com.v2ray.ang.ui.compose.verticalScrollbar

private val FormVerticalPad = 8.dp
private val FormBottomGap = 36.dp

@Stable
private class SubFieldCallbacks(onAction: (SubEditAction) -> Unit) {
    private val text: Map<SubField, (String) -> Unit> =
        SubField.entries.associateWith { field ->
            { value: String -> onAction(SubEditAction.TextChanged(field, value)) }
        }
    private val flags: Map<SubFlag, (Boolean) -> Unit> =
        SubFlag.entries.associateWith { flag ->
            { value: Boolean -> onAction(SubEditAction.FlagChanged(flag, value)) }
        }

    operator fun get(field: SubField): (String) -> Unit = text.getValue(field)
    operator fun get(flag: SubFlag): (Boolean) -> Unit = flags.getValue(flag)
}

@Stable
private class SubEditHost(private val onAction: (SubEditAction) -> Unit) {
    var isEdit: Boolean = false
    var confirmRemove: Boolean = false
    var showDelete by mutableStateOf(false)
        private set

    val requestDelete: () -> Unit = {
        if (confirmRemove) showDelete = true
        else onAction(SubEditAction.DeleteConfirmed)
    }
    val dismissDelete: () -> Unit = { showDelete = false }
    val confirmDelete: () -> Unit = {
        showDelete = false
        onAction(SubEditAction.DeleteConfirmed)
    }
}

@Composable
fun SubEditScreen(viewModel: SubEditViewModel) {
    val onAction = remember(viewModel) { viewModel::onAction }
    val callbacks = remember(onAction) { SubFieldCallbacks(onAction) }
    val host = remember(onAction) { SubEditHost(onAction) }

    BackHandler { onAction(SubEditAction.Back) }

    BaseScreen(
        viewModel = viewModel,
        topBar = { SubEditTopBar(host = host, onAction = onAction) }
    ) { state, _ ->
        SideEffect {
            host.isEdit = state.isEdit
            host.confirmRemove = state.confirmRemove
        }

        SubEditFields(
            form = state.form,
            profileOptions = state.profileOptions,
            callbacks = callbacks,
            modifier = Modifier.fillMaxSize().imePadding()
        )

        if (host.showDelete) {
            DeleteConfirmDialog(
                message = stringResource(R.string.confirm_delete_subscription_group),
                onConfirm = host.confirmDelete,
                onDismiss = host.dismissDelete
            )
        }
    }
}

@Composable
private fun SubEditTopBar(
    host: SubEditHost,
    onAction: (SubEditAction) -> Unit
) {
    AppTopBar(
        title = stringResource(R.string.title_sub_setting),
        onBackClick = { onAction(SubEditAction.Back) },
        actions = {
            if (host.isEdit) {
                IconButton(onClick = host.requestDelete) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete_24dp),
                        contentDescription = stringResource(R.string.menu_item_del_config)
                    )
                }
            }
            IconButton(onClick = { onAction(SubEditAction.Save) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_fab_check),
                    contentDescription = stringResource(R.string.menu_item_save_config)
                )
            }
        }
    )
}

@Composable
private fun SubEditFields(
    form: SubEditForm,
    profileOptions: List<String>,
    callbacks: SubFieldCallbacks,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val options = remember(profileOptions) { StringOptions(profileOptions) }

    Column(
        modifier = modifier
            .verticalScrollbar(scrollState)
            .verticalScroll(scrollState)
            .padding(vertical = FormVerticalPad)
    ) {
        FormTextField(
            label = stringResource(R.string.sub_setting_remarks),
            value = form.remarks,
            onValueChange = callbacks[SubField.REMARKS]
        )
        FormTextField(
            label = stringResource(R.string.sub_setting_url),
            value = form.url,
            onValueChange = callbacks[SubField.URL]
        )
        FormTextField(
            label = stringResource(R.string.sub_setting_user_agent),
            value = form.userAgent,
            onValueChange = callbacks[SubField.USER_AGENT]
        )
        FormTextField(
            label = stringResource(R.string.sub_setting_request_headers),
            value = form.requestHeaders,
            onValueChange = callbacks[SubField.REQUEST_HEADERS]
        )
        FormTextField(
            label = stringResource(R.string.sub_setting_filter),
            value = form.filter,
            onValueChange = callbacks[SubField.FILTER]
        )
        SettingsSwitchItem(
            title = stringResource(R.string.sub_setting_enable),
            checked = form.enabled,
            onCheckedChange = callbacks[SubFlag.ENABLED]
        )
        SettingsSwitchItem(
            title = stringResource(R.string.sub_auto_update),
            checked = form.autoUpdate,
            onCheckedChange = callbacks[SubFlag.AUTO_UPDATE]
        )
        FormTextField(
            label = stringResource(R.string.title_pref_auto_update_interval),
            value = form.updateInterval,
            onValueChange = callbacks[SubField.UPDATE_INTERVAL],
            keyboardType = KeyboardType.Number
        )
        SettingsSwitchItem(
            title = stringResource(R.string.sub_allow_insecure_url),
            checked = form.allowInsecureUrl,
            onCheckedChange = callbacks[SubFlag.ALLOW_INSECURE_URL]
        )
        FormDropdownField(
            label = stringResource(R.string.sub_setting_pre_profile),
            placeholder = stringResource(R.string.sub_setting_pre_profile_tip),
            value = form.prevProfile,
            options = options,
            onValueChange = callbacks[SubField.PREV_PROFILE],
            editable = true,
            supportingText = stringResource(R.string.sub_setting_entry_proxy_tip)
        )
        FormDropdownField(
            label = stringResource(R.string.sub_setting_next_profile),
            placeholder = stringResource(R.string.sub_setting_pre_profile_tip),
            value = form.nextProfile,
            options = options,
            onValueChange = callbacks[SubField.NEXT_PROFILE],
            editable = true,
            supportingText = stringResource(R.string.sub_setting_exit_proxy_tip)
        )
        Spacer(modifier = Modifier.height(FormBottomGap))
        NavigationBarsSpacer()
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SubEditFieldsPreview() = AppTheme {
    SubEditFields(
        form = SubEditForm(
            remarks = "Primary",
            url = "https://example.com/sub",
            autoUpdate = true,
            updateInterval = "1440"
        ),
        profileOptions = listOf("direct", "proxy"),
        callbacks = SubFieldCallbacks({})
    )
}
