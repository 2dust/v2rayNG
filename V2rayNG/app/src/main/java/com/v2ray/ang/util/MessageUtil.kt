package com.v2ray.ang.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.v2ray.ang.AppConfig
import com.v2ray.ang.service.V2RayTestService
import java.io.Serializable


object MessageUtil {

    fun sendMsg2Service(ctx: Context, what: Int, content: Serializable) {
        sendMsg(ctx, AppConfig.BROADCAST_ACTION_SERVICE, what, content)
    }

    fun sendMsg2UI(ctx: Context, what: Int, content: Serializable) {
        sendMsg(ctx, AppConfig.BROADCAST_ACTION_ACTIVITY, what, content)
    }

    fun sendMsg2TestService(ctx: Context, what: Int, content: Serializable) {
        try {
            val intent = Intent()
            intent.component = ComponentName(ctx, V2RayTestService::class.java)
            intent.putExtra("key", what)
            intent.putExtra("content", content)
            ctx.startService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendMsg(ctx: Context, action: String, what: Int, content: Serializable) {
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
