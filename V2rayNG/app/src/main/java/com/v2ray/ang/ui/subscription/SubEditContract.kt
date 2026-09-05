package com.v2ray.ang.ui.subscription

import androidx.compose.runtime.Immutable
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.extension.toLongEx
import com.v2ray.ang.ui.base.BaseAction
import com.v2ray.ang.ui.base.BaseUiState

@Immutable
data class SubEditForm(
    val remarks: String = "",
    val url: String = "",
    val userAgent: String = "",
    val requestHeaders: String = "",
    val filter: String = "",
    val enabled: Boolean = true,
    val autoUpdate: Boolean = false,
    val updateInterval: String = SubscriptionItem().updateInterval.toString(),
    val allowInsecureUrl: Boolean = false,
    val prevProfile: String = "",
    val nextProfile: String = ""
)

enum class SubField {
    REMARKS,
    URL,
    USER_AGENT,
    REQUEST_HEADERS,
    FILTER,
    UPDATE_INTERVAL,
    PREV_PROFILE,
    NEXT_PROFILE
}

enum class SubFlag {
    ENABLED,
    AUTO_UPDATE,
    ALLOW_INSECURE_URL
}

@Immutable
data class SubEditUiState(
    val subId: String = "",
    val form: SubEditForm = SubEditForm(),
    val profileOptions: List<String> = emptyList(),
    val confirmRemove: Boolean = false
) : BaseUiState {
    val isEdit: Boolean get() = subId.isNotEmpty()
}

sealed interface SubEditAction : BaseAction {
    data class TextChanged(val field: SubField, val value: String) : SubEditAction
    data class FlagChanged(val flag: SubFlag, val value: Boolean) : SubEditAction
    data object Save : SubEditAction
    data object Back : SubEditAction
    data object DeleteConfirmed : SubEditAction
}

fun SubField.set(form: SubEditForm, value: String): SubEditForm = when (this) {
    SubField.REMARKS -> form.copy(remarks = value)
    SubField.URL -> form.copy(url = value)
    SubField.USER_AGENT -> form.copy(userAgent = value)
    SubField.REQUEST_HEADERS -> form.copy(requestHeaders = value)
    SubField.FILTER -> form.copy(filter = value)
    SubField.UPDATE_INTERVAL -> form.copy(updateInterval = value)
    SubField.PREV_PROFILE -> form.copy(prevProfile = value)
    SubField.NEXT_PROFILE -> form.copy(nextProfile = value)
}

fun SubFlag.set(form: SubEditForm, value: Boolean): SubEditForm = when (this) {
    SubFlag.ENABLED -> form.copy(enabled = value)
    SubFlag.AUTO_UPDATE -> form.copy(autoUpdate = value)
    SubFlag.ALLOW_INSECURE_URL -> form.copy(allowInsecureUrl = value)
}

fun SubscriptionItem?.toSubEditForm(): SubEditForm {
    val item = this ?: SubscriptionItem()
    return SubEditForm(
        remarks = item.remarks,
        url = item.url,
        userAgent = item.userAgent.orEmpty(),
        requestHeaders = item.requestHeaders.orEmpty(),
        filter = item.filter.orEmpty(),
        enabled = item.enabled,
        autoUpdate = item.autoUpdate,
        updateInterval = item.updateInterval.toString(),
        allowInsecureUrl = item.allowInsecureUrl,
        prevProfile = item.prevProfile.orEmpty(),
        nextProfile = item.nextProfile.orEmpty()
    )
}

fun SubscriptionItem.applySubEditForm(form: SubEditForm): SubscriptionItem = apply {
    remarks = form.remarks
    url = form.url
    userAgent = form.userAgent
    requestHeaders = form.requestHeaders
    filter = form.filter
    enabled = form.enabled
    autoUpdate = form.autoUpdate
    updateInterval = form.updateInterval.toLongEx()
    allowInsecureUrl = form.allowInsecureUrl
    prevProfile = form.prevProfile
    nextProfile = form.nextProfile
}
