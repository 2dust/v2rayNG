package com.v2ray.ang.ui

import android.content.*
import android.net.VpnService
import com.v2ray.ang.R
import com.v2ray.ang.util.Utils
import android.os.Bundle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.MessageUtil
import java.lang.ref.SoftReference
import android.content.IntentFilter
import kotlinx.android.synthetic.main.activity_main.*
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit

class ScSwitchActivity : BaseActivity() {
    companion object {
        private const val REQUEST_CODE_VPN_PREPARE = 0
    }

    var isRunning = false
        set(value) {
            field = value
            if (value) {
                Utils.stopVService(this)
            } else {
                val intent = VpnService.prepare(this)
                if (intent == null) {
                    Utils.startVService(this)
                } else {
                    startActivityForResult(intent, REQUEST_CODE_VPN_PREPARE)
                }
            }
            finishActivity()
        }

    fun finishActivity() {
        try {
            Observable.timer(5000, TimeUnit.MILLISECONDS)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe {
                        finish()
                    }
        } catch (e: Exception) {
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        moveTaskToBack(true)

        setContentView(R.layout.activity_none)

        val isRunning = Utils.isServiceRun(this, "com.v2ray.ang.service.V2RayVpnService")
        if (isRunning) {
            //Utils.stopVService(this)
            mMsgReceive = ReceiveMessageHandler(this@ScSwitchActivity)
            registerReceiver(mMsgReceive, IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY))
            MessageUtil.sendMsg2Service(this, AppConfig.MSG_REGISTER_CLIENT, "")

        } else {
            Utils.startVService(this)
            finishActivity()
        }
    }

    override fun onStop() {
        super.onStop()
        if (mMsgReceive != null) {
            unregisterReceiver(mMsgReceive)
            mMsgReceive = null
        }
    }

    private var mMsgReceive: BroadcastReceiver? = null

    private class ReceiveMessageHandler(activity: ScSwitchActivity) : BroadcastReceiver() {
        internal var mReference: SoftReference<ScSwitchActivity> = SoftReference(activity)
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val activity = mReference.get()
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING -> {
                    activity?.isRunning = true
                }
                AppConfig.MSG_STATE_NOT_RUNNING -> {
                    activity?.isRunning = false
                }
//                AppConfig.MSG_STATE_START_SUCCESS -> {
//                    activity?.toast(R.string.toast_services_success)
//                    activity?.isRunning = true
//                }
//                AppConfig.MSG_STATE_START_FAILURE -> {
//                    activity?.toast(R.string.toast_services_failure)
//                    activity?.isRunning = false
//                }
//                AppConfig.MSG_STATE_STOP_SUCCESS -> {
//                    activity?.isRunning = false
//                }
            }
        }
    }

}