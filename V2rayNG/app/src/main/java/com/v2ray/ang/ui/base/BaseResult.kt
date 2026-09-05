package com.v2ray.ang.ui.base

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

/**
 * The single screen-result contract of the app; replaces ProfileEditorResult, bare
 * `setResult(RESULT_OK)` and `SettingsChangeManager.consumeXxx()`.
 */
sealed interface BaseResult {

    /** Affected entity (guid / subscription id / asset id), empty when not applicable. */
    val id: String get() = ""

    /** The caller must restart the core service. */
    val restartService: Boolean get() = false

    /** The caller must reload its list. */
    val refreshList: Boolean get() = this !is Cancelled

    val isOk: Boolean get() = this !is Cancelled

    /** Whether the caller shows the unified "ok" feedback; aggregated results stay silent. */
    val notify: Boolean get() = this is Saved || this is Deleted

    /** Dismissed without changes. */
    data object Cancelled : BaseResult

    /** Create or update succeeded. */
    data class Saved(
        override val id: String = "",
        override val restartService: Boolean = false,
        override val refreshList: Boolean = true
    ) : BaseResult

    /** Delete succeeded. */
    data class Deleted(
        override val id: String = "",
        override val restartService: Boolean = false,
        override val refreshList: Boolean = true
    ) : BaseResult

    /** Page-level, non-CRUD change (settings, subscription refresh…). */
    data class Changed(
        override val restartService: Boolean = false,
        override val refreshList: Boolean = true
    ) : BaseResult

    /** A picker confirmed a set of values; the caller decides what they mean. */
    data class Selected(
        val values: List<String> = emptyList(),
        override val id: String = ""
    ) : BaseResult {
        override val refreshList: Boolean get() = false
        override val notify: Boolean get() = false
    }
}

private const val EXTRA_TYPE = "com.v2ray.ang.extra.RESULT_TYPE"
private const val EXTRA_ID = "com.v2ray.ang.extra.RESULT_ID"
private const val EXTRA_RESTART = "com.v2ray.ang.extra.RESULT_RESTART"
private const val EXTRA_REFRESH = "com.v2ray.ang.extra.RESULT_REFRESH"
private const val EXTRA_VALUES = "com.v2ray.ang.extra.RESULT_VALUES"

private const val TYPE_SAVED = "saved"
private const val TYPE_DELETED = "deleted"
private const val TYPE_CHANGED = "changed"
private const val TYPE_SELECTED = "selected"

/** Serialises the result; [BaseResult.Cancelled] produces an empty Intent. */
fun BaseResult.toIntent(): Intent = Intent().apply {
    val type = when (this@toIntent) {
        BaseResult.Cancelled -> return@apply
        is BaseResult.Saved -> TYPE_SAVED
        is BaseResult.Deleted -> TYPE_DELETED
        is BaseResult.Changed -> TYPE_CHANGED
        is BaseResult.Selected -> {
            putStringArrayListExtra(EXTRA_VALUES, ArrayList(values))
            TYPE_SELECTED
        }
    }
    putExtra(EXTRA_TYPE, type)
    putExtra(EXTRA_ID, id)
    putExtra(EXTRA_RESTART, restartService)
    putExtra(EXTRA_REFRESH, refreshList)
}

/** Parses a result Intent; anything unrecognised is [BaseResult.Cancelled]. */
fun Intent?.toBaseResult(): BaseResult {
    val intent = this ?: return BaseResult.Cancelled
    val id = intent.getStringExtra(EXTRA_ID).orEmpty()
    val restart = intent.getBooleanExtra(EXTRA_RESTART, false)
    val refresh = intent.getBooleanExtra(EXTRA_REFRESH, true)
    return when (intent.getStringExtra(EXTRA_TYPE)) {
        TYPE_SAVED -> BaseResult.Saved(id, restart, refresh)
        TYPE_DELETED -> BaseResult.Deleted(id, restart, refresh)
        TYPE_CHANGED -> BaseResult.Changed(restart, refresh)
        TYPE_SELECTED -> BaseResult.Selected(
            values = intent.getStringArrayListExtra(EXTRA_VALUES).orEmpty(),
            id = id
        )
        else -> BaseResult.Cancelled
    }
}

/** The single ActivityResultContract used by [BaseScreen] for every navigation. */
class BaseResultContract : ActivityResultContract<Intent, BaseResult>() {
    override fun createIntent(context: Context, input: Intent): Intent = input
    override fun parseResult(resultCode: Int, intent: Intent?): BaseResult =
        if (resultCode != Activity.RESULT_OK) BaseResult.Cancelled else intent.toBaseResult()
}

/** Finishes the Activity, mapping [result] onto RESULT_OK / RESULT_CANCELED. */
fun Activity.finishWithResult(result: BaseResult) {
    if (result.isOk) setResult(Activity.RESULT_OK, result.toIntent())
    else setResult(Activity.RESULT_CANCELED)
    finish()
}
