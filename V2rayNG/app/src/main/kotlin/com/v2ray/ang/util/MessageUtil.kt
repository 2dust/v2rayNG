package com.v2ray.ang.util

import android.content.Context
import android.content.Intent
import com.v2ray.ang.AppConfig


object MessageUtil {

    fun sendMsg2Service(ctx: Context, what: Int, content: String) {
        sendMsg(ctx, AppConfig.BROADCAST_ACTION_SERVICE, what, content)
    }

    fun sendMsg2UI(ctx: Context, what: Int, content: String) {
        sendMsg(ctx, AppConfig.BROADCAST_ACTION_ACTIVITY, what, content)
    }

    private fun sendMsg(ctx: Context, action: String, what: Int, content: String) {
        try {
            val intent = Intent()
            intent.action = action
            intent.`package` = AppConfig.ANG_PACKAGE
            intent.putExtra("key", what)
            intent.putExtra("content", content)
            ctx.sendBroadcast(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}