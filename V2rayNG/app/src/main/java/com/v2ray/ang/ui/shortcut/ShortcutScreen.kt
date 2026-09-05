package com.v2ray.ang.ui.shortcut

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.v2ray.ang.ui.base.BaseEvent
import com.v2ray.ang.ui.base.BaseEventEffect
import com.v2ray.ang.ui.base.BaseMessage
import com.v2ray.ang.ui.base.asString

/**
 * Host surface of the four shortcut entries.
 */
@Composable
fun ShortcutScreen(
    viewModel: ShortcutViewModel,
    onEvent: (BaseEvent) -> Boolean
) {
    val context = LocalContext.current

    BaseEventEffect(events = viewModel.events) { event ->
        when {
            onEvent(event) -> true

            event is BaseEvent.Message -> {
                context.showAsToast(event.message)
                true
            }

            event is BaseEvent.Navigate -> {
                event.route.intent(context)?.let(context::startActivity)
                true
            }

            else -> false
        }
    }
}

/**
 * Resolves a [BaseMessage] against resources and shows it as a Toast that outlives the screen.
 */
private fun Context.showAsToast(message: BaseMessage) {
    val duration = if (message.long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
    Toast.makeText(applicationContext, message.text.asString(this), duration).show()
}
