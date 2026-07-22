package com.dalulong.app.ui.server

import android.app.Activity
import android.content.Intent

object ProfileEditorResult {

    const val EXTRA_ACTION =
        "com.dalulong.app.extra.PROFILE_EDITOR_ACTION"

    const val EXTRA_GUID =
        "com.dalulong.app.extra.PROFILE_EDITOR_GUID"

    const val EXTRA_RESTART_SERVICE =
        "com.dalulong.app.extra.PROFILE_EDITOR_RESTART_SERVICE"

    const val ACTION_SAVED = "saved"
    const val ACTION_DELETED = "deleted"

    fun Activity.finishSaved(
        guid: String,
        restartService: Boolean
    ) {
        setResult(
            Activity.RESULT_OK,
            Intent().apply {
                putExtra(EXTRA_ACTION, ACTION_SAVED)
                putExtra(EXTRA_GUID, guid)
                putExtra(EXTRA_RESTART_SERVICE, restartService)
            }
        )
        finish()
    }

    fun Activity.finishDeleted(guid: String) {
        setResult(
            Activity.RESULT_OK,
            Intent().apply {
                putExtra(EXTRA_ACTION, ACTION_DELETED)
                putExtra(EXTRA_GUID, guid)
                putExtra(EXTRA_RESTART_SERVICE, false)
            }
        )
        finish()
    }
}