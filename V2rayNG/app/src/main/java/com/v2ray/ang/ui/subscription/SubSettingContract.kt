package com.v2ray.ang.ui.subscription

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.v2ray.ang.dto.SubUpdateOptions
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.ui.base.BaseAction
import com.v2ray.ang.ui.base.BaseEvent
import com.v2ray.ang.ui.base.BaseResult
import com.v2ray.ang.ui.base.BaseUiState
import com.v2ray.ang.util.Utils

enum class UpdateOptionField {
    UPDATE_SUBSCRIPTION,
    AUTO_TEST_AFTER_UPDATE,
    AUTO_REMOVE_INVALID,
    AUTO_SORT_AFTER_TEST
}

enum class ShareMethod {
    QR_CODE,
    CLIPBOARD
}

@Immutable
data class SubRow(
    val guid: String,
    val remarks: String,
    val url: String,
    val lastUpdatedText: String,
    val enabled: Boolean
) {
    val hasUrl: Boolean get() = url.isNotEmpty()
}

@Immutable
data class SubUpdateProgress(val done: Int, val total: Int) {
    val fraction: Float get() = if (total <= 0) 0f else done.toFloat() / total
}

@Immutable
data class SubUiState(
    val subscriptions: List<SubRow> = emptyList(),
    val updateOptions: SubUpdateOptions = SubUpdateOptions(),
    val confirmRemove: Boolean = false
) : BaseUiState

sealed interface SubAction : BaseAction {
    data object Back : SubAction
    data object Add : SubAction
    data class Edit(val subId: String) : SubAction
    data class RemoveConfirmed(val subId: String) : SubAction
    data class ToggleEnabled(val subId: String, val enabled: Boolean) : SubAction
    data class Move(val fromId: String, val toId: String) : SubAction
    data object OpenUpdateOptions : SubAction
    data class UpdateOptionChanged(val field: UpdateOptionField, val value: Boolean) : SubAction
    data object ConfirmUpdateOptions : SubAction
    data object DismissUpdateOptions : SubAction
    data class ShareClicked(val url: String) : SubAction
    data class ShareMethodSelected(val method: ShareMethod, val url: String) : SubAction
    data class ResultReceived(val result: BaseResult) : SubAction
}

sealed interface SubEvent : BaseEvent.Platform {
    data object ShowUpdateOptions : SubEvent
    data class ShowShare(val url: String) : SubEvent
    data class ShowQrCode(val bitmap: Bitmap) : SubEvent
    data object CloseUpdateOptions : SubEvent
}

@Stable
class SubRowCallbacks(
    val onShare: (String) -> Unit,
    val onEdit: (String) -> Unit,
    val onRemove: (String) -> Unit,
    val onToggle: (String, Boolean) -> Unit
)

fun List<SubscriptionCache>.toSubRows(): List<SubRow> = map { cache ->
    SubRow(
        guid = cache.guid,
        remarks = cache.subscription.remarks,
        url = cache.subscription.url,
        lastUpdatedText = Utils.formatTimestamp(cache.subscription.lastUpdated),
        enabled = cache.subscription.enabled
    )
}

fun UpdateOptionField.set(options: SubUpdateOptions, value: Boolean): SubUpdateOptions = when (this) {
    UpdateOptionField.UPDATE_SUBSCRIPTION -> options.copy(updateSubscription = value)
    UpdateOptionField.AUTO_TEST_AFTER_UPDATE -> options.copy(autoTestAfterUpdate = value)
    UpdateOptionField.AUTO_REMOVE_INVALID -> options.copy(autoRemoveInvalid = value)
    UpdateOptionField.AUTO_SORT_AFTER_TEST -> options.copy(autoSortAfterTest = value)
}
