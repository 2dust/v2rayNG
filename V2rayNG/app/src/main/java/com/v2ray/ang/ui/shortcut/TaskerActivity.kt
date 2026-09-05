package com.v2ray.ang.ui.shortcut

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import com.v2ray.ang.AppConfig
import com.v2ray.ang.repository.ShortcutRepository
import com.v2ray.ang.ui.base.BaseActivity
import com.v2ray.ang.ui.base.BaseEvent
import com.v2ray.ang.ui.base.asString
import com.v2ray.ang.ui.base.baseViewModels

class TaskerActivity : BaseActivity() {

    private val viewModel: TaskerViewModel by baseViewModels { _, handle ->
        TaskerViewModel(handle, ShortcutRepository())
    }

    @Composable
    override fun ScreenContent() = TaskerScreen(viewModel, ::handleEvent)

    /**
     * Translates the save event into the Tasker/Locale protocol.
     *
     * @return true when the event has been consumed.
     */
    private fun handleEvent(event: BaseEvent): Boolean = when (event) {
        is TaskerEvent.SaveSetting -> {
            val extras = bundleOf(
                AppConfig.TASKER_EXTRA_BUNDLE_SWITCH to event.startService,
                AppConfig.TASKER_EXTRA_BUNDLE_GUID to event.guid
            )
            setResult(
                RESULT_OK,
                Intent()
                    .putExtra(AppConfig.TASKER_EXTRA_BUNDLE, extras)
                    .putExtra(AppConfig.TASKER_EXTRA_STRING_BLURB, event.blurb.asString(this))
            )
            finish()
            true
        }

        else -> false
    }
}
