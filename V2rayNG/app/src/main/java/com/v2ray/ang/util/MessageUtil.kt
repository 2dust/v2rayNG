package com.v2ray.ang.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.service.V2RayTestService
import java.io.Serializable

object MessageUtil {


    /**
     * Sends a message to the service.
     *
     * @param ctx The context.
     * @param what The message identifier.
     * @param content The message content.
     */
    fun sendMsg2Service(ctx: Context, what: Int, content: Serializable) {
        sendMsg(ctx, AppConfig.BROADCAST_ACTION_SERVICE, what, content)
    }

    /**
     * Sends a message to the UI.
     *
     * @param ctx The context.
     * @param what The message identifier.
     * @param content The message content.
     */
    fun sendMsg2UI(ctx: Context, what: Int, content: Serializable) {
        sendMsg(ctx, AppConfig.BROADCAST_ACTION_ACTIVITY, what, content)
    }

    /**
     * Sends a message to the test service.
     *
     * @param ctx The context.
     * @param what The message identifier.
     * @param content The message content.
     */
    fun sendMsg2TestService(ctx: Context, what: Int, content: Serializable) {
        try {
            val intent = Intent()
            intent.component = ComponentName(ctx, V2RayTestService::class.java)
            intent.putExtra("key", what)
            intent.putExtra("content", content)
            ctx.startService(intent)
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to send message to test service", e)
        }
    }

    /**
     * Sends a message with the specified action.
     *
     * @param ctx The context.
     * @param action The action string.
     * @param what The message identifier.
     * @param content The message content.
     */
    private fun sendMsg(ctx: Context, action: String, what: Int, content: Serializable) {
        try {
            val intent = Intent()
            intent.action = action
            intent.`package` = AppConfig.ANG_PACKAGE
            intent.putExtra("key", what)
            intent.putExtra("content", content)
            ctx.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to send message with action: $action", e)
        }
    }
}
