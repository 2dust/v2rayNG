package com.v2ray.ang.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.Utils
import java.lang.ref.SoftReference

@RequiresApi(Build.VERSION_CODES.N)
class QSTileService : TileService() {

    /**
     * Sets the state of the tile.
     * @param state The state to set.
     */
    fun setState(state: Int) {
        qsTile?.icon = Icon.createWithResource(applicationContext, R.drawable.ic_stat_name)
        if (state == Tile.STATE_INACTIVE) {
            qsTile?.state = Tile.STATE_INACTIVE
            qsTile?.label = getString(R.string.app_name)
        } else if (state == Tile.STATE_ACTIVE) {
            qsTile?.state = Tile.STATE_ACTIVE
            qsTile?.label = V2RayServiceManager.getRunningServerName()
        }

        qsTile?.updateTile()
    }

    /**
     * Refer to the official documentation for [registerReceiver](https://developer.android.com/reference/androidx/core/content/ContextCompat#registerReceiver(android.content.Context,android.content.BroadcastReceiver,android.content.IntentFilter,int):
     * `registerReceiver(Context, BroadcastReceiver, IntentFilter, int)`.
     */
    override fun onStartListening() {
        super.onStartListening()

        if (V2RayServiceManager.isRunning()) {
            setState(Tile.STATE_ACTIVE)
        } else {
            setState(Tile.STATE_INACTIVE)
        }
        mMsgReceive = ReceiveMessageHandler(this)
        val mFilter = IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY)
        ContextCompat.registerReceiver(applicationContext, mMsgReceive, mFilter, Utils.receiverFlags())
        MessageUtil.sendMsg2Service(this, AppConfig.MSG_REGISTER_CLIENT, "")
    }

    /**
     * Called when the tile stops listening.
     */
    override fun onStopListening() {
        super.onStopListening()

        try {
            applicationContext.unregisterReceiver(mMsgReceive)
            mMsgReceive = null
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to unregister receiver", e)
        }

    }

    /**
     * Called when the tile is clicked.
     */
    override fun onClick() {
        super.onClick()
        when (qsTile.state) {
            Tile.STATE_INACTIVE -> {
                V2RayServiceManager.startVServiceFromToggle(this)
            }

            Tile.STATE_ACTIVE -> {
                V2RayServiceManager.stopVService(this)
            }
        }
    }

    private var mMsgReceive: BroadcastReceiver? = null

    private class ReceiveMessageHandler(context: QSTileService) : BroadcastReceiver() {
        var mReference: SoftReference<QSTileService> = SoftReference(context)
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val context = mReference.get()
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING -> {
                    context?.setState(Tile.STATE_ACTIVE)
                }

                AppConfig.MSG_STATE_NOT_RUNNING -> {
                    context?.setState(Tile.STATE_INACTIVE)
                }

                AppConfig.MSG_STATE_START_SUCCESS -> {
                    context?.setState(Tile.STATE_ACTIVE)
                }

                AppConfig.MSG_STATE_START_FAILURE -> {
                    context?.setState(Tile.STATE_INACTIVE)
                }

                AppConfig.MSG_STATE_STOP_SUCCESS -> {
                    context?.setState(Tile.STATE_INACTIVE)
                }
            }
        }
    }
}
