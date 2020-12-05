package com.v2ray.ang.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.support.annotation.RequiresApi
import android.support.v4.app.NotificationCompat
import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.TAG_DIRECT
import com.v2ray.ang.R
import com.v2ray.ang.extension.defaultDPreference
import com.v2ray.ang.extension.toSpeedString
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.v2RayApplication
import com.v2ray.ang.ui.MainActivity
import com.v2ray.ang.ui.SettingsActivity
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.Utils
import go.Seq
import libv2ray.Libv2ray
import libv2ray.V2RayPoint
import libv2ray.V2RayVPNServiceSupportsSet
import rx.Observable
import rx.Subscription
import java.lang.ref.SoftReference
import kotlin.math.min

object V2RayServiceManager {
    private const val NOTIFICATION_ID = 1
    private const val NOTIFICATION_PENDING_INTENT_CONTENT = 0
    private const val NOTIFICATION_PENDING_INTENT_STOP_V2RAY = 1
    private const val NOTIFICATION_ICON_THRESHOLD = 3000

    val v2rayPoint: V2RayPoint = Libv2ray.newV2RayPoint(V2RayCallback())
    private val mMsgReceive = ReceiveMessageHandler()

    var serviceControl: SoftReference<ServiceControl>? = null
        set(value) {
            field = value
            val context = value?.get()?.getService()?.applicationContext
            context?.let {
                v2rayPoint.packageName = Utils.packagePath(context)
                v2rayPoint.packageCodePath = context.applicationInfo.nativeLibraryDir + "/"
                Seq.setContext(context)
            }
        }
    var currentConfigName = "NG"

    private var lastQueryTime = 0L
    private var mBuilder: NotificationCompat.Builder? = null
    private var mSubscription: Subscription? = null
    private var mNotificationManager: NotificationManager? = null

    fun startV2Ray(context: Context) {
        if (context.v2RayApplication.defaultDPreference.getPrefBoolean(SettingsActivity.PREF_PROXY_SHARING, false)) {
            context.toast(R.string.toast_warning_pref_proxysharing_short)
        }else{
            context.toast(R.string.toast_services_start)
        }
        val intent = if (context.v2RayApplication.defaultDPreference.getPrefString(AppConfig.PREF_MODE, "VPN") == "VPN") {
            Intent(context.applicationContext, V2RayVpnService::class.java)
        } else {
            Intent(context.applicationContext, V2RayProxyOnlyService::class.java)
        }
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private class V2RayCallback : V2RayVPNServiceSupportsSet {
        override fun shutdown(): Long {
            val serviceControl = serviceControl?.get() ?: return -1
            // called by go
            // shutdown the whole vpn service
            return try {
                serviceControl.stopService()
                0
            } catch (e: Exception) {
                Log.d(serviceControl.getService().packageName, e.toString())
                -1
            }
        }

        override fun prepare(): Long {
            return 0
        }

        override fun protect(l: Long): Long {
            val serviceControl = serviceControl?.get() ?: return 0
            return if (serviceControl.vpnProtect(l.toInt())) 0 else 1
        }

        override fun onEmitStatus(l: Long, s: String?): Long {
            //Logger.d(s)
            return 0
        }

        override fun setup(s: String): Long {
            val serviceControl = serviceControl?.get() ?: return -1
            //Logger.d(s)
            return try {
                serviceControl.startService(s)
                lastQueryTime = System.currentTimeMillis()
                startSpeedNotification()
                0
            } catch (e: Exception) {
                Log.d(serviceControl.getService().packageName, e.toString())
                -1
            }
        }

    }

    fun startV2rayPoint() {
        val service = serviceControl?.get()?.getService() ?: return
        if (!v2rayPoint.isRunning) {

            try {
                val mFilter = IntentFilter(AppConfig.BROADCAST_ACTION_SERVICE)
                mFilter.addAction(Intent.ACTION_SCREEN_ON)
                mFilter.addAction(Intent.ACTION_SCREEN_OFF)
                mFilter.addAction(Intent.ACTION_USER_PRESENT)
                service.registerReceiver(mMsgReceive, mFilter)
            } catch (e: Exception) {
                Log.d(service.packageName, e.toString())
            }

            v2rayPoint.configureFileContent = service.defaultDPreference.getPrefString(AppConfig.PREF_CURR_CONFIG, "")
            v2rayPoint.enableLocalDNS = service.defaultDPreference.getPrefBoolean(SettingsActivity.PREF_LOCAL_DNS_ENABLED, false)
            v2rayPoint.forwardIpv6 = service.defaultDPreference.getPrefBoolean(SettingsActivity.PREF_FORWARD_IPV6, false)
            v2rayPoint.domainName = service.defaultDPreference.getPrefString(AppConfig.PREF_CURR_CONFIG_DOMAIN, "")
            v2rayPoint.proxyOnly = service.defaultDPreference.getPrefString(AppConfig.PREF_MODE, "VPN") != "VPN"
            currentConfigName = service.defaultDPreference.getPrefString(AppConfig.PREF_CURR_CONFIG_NAME, "NG")

            try {
                v2rayPoint.runLoop()
            } catch (e: Exception) {
                Log.d(service.packageName, e.toString())
            }

            if (v2rayPoint.isRunning) {
                MessageUtil.sendMsg2UI(service, AppConfig.MSG_STATE_START_SUCCESS, "")
                showNotification()
            } else {
                MessageUtil.sendMsg2UI(service, AppConfig.MSG_STATE_START_FAILURE, "")
                cancelNotification()
            }
        }
    }

    fun stopV2rayPoint() {
        val service = serviceControl?.get()?.getService() ?: return

        if (v2rayPoint.isRunning) {
            try {
                v2rayPoint.stopLoop()
            } catch (e: Exception) {
                Log.d(service.packageName, e.toString())
            }
        }

        MessageUtil.sendMsg2UI(service, AppConfig.MSG_STATE_STOP_SUCCESS, "")
        cancelNotification()

        try {
            service.unregisterReceiver(mMsgReceive)
        } catch (e: Exception) {
            Log.d(service.packageName, e.toString())
        }
    }

    private class ReceiveMessageHandler : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val serviceControl = serviceControl?.get() ?: return
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_REGISTER_CLIENT -> {
                    //Logger.e("ReceiveMessageHandler", intent?.getIntExtra("key", 0).toString())
                    if (v2rayPoint.isRunning) {
                        MessageUtil.sendMsg2UI(serviceControl.getService(), AppConfig.MSG_STATE_RUNNING, "")
                    } else {
                        MessageUtil.sendMsg2UI(serviceControl.getService(), AppConfig.MSG_STATE_NOT_RUNNING, "")
                    }
                }
                AppConfig.MSG_UNREGISTER_CLIENT -> {
                    // nothing to do
                }
                AppConfig.MSG_STATE_START -> {
                    // nothing to do
                }
                AppConfig.MSG_STATE_STOP -> {
                    serviceControl.stopService()
                }
                AppConfig.MSG_STATE_RESTART -> {
                    startV2rayPoint()
                }
            }

            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(AppConfig.ANG_PACKAGE, "SCREEN_OFF, stop querying stats")
                    stopSpeedNotification()
                }
                Intent.ACTION_SCREEN_ON -> {
                    Log.d(AppConfig.ANG_PACKAGE, "SCREEN_ON, start querying stats")
                    startSpeedNotification()
                }
            }
        }
    }

    private fun showNotification() {
        val service = serviceControl?.get()?.getService() ?: return
        val startMainIntent = Intent(service, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(service,
                NOTIFICATION_PENDING_INTENT_CONTENT, startMainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT)

        val stopV2RayIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE)
        stopV2RayIntent.`package` = AppConfig.ANG_PACKAGE
        stopV2RayIntent.putExtra("key", AppConfig.MSG_STATE_STOP)

        val stopV2RayPendingIntent = PendingIntent.getBroadcast(service,
                NOTIFICATION_PENDING_INTENT_STOP_V2RAY, stopV2RayIntent,
                PendingIntent.FLAG_UPDATE_CURRENT)

        val channelId =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    createNotificationChannel()
                } else {
                    // If earlier version channel ID is not used
                    // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
                    ""
                }

        mBuilder = NotificationCompat.Builder(service, channelId)
                .setSmallIcon(R.drawable.ic_v)
                .setContentTitle(currentConfigName)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentPendingIntent)
                .addAction(R.drawable.ic_close_grey_800_24dp,
                        service.getString(R.string.notification_action_stop_v2ray),
                        stopV2RayPendingIntent)
        //.build()

        //mBuilder?.setDefaults(NotificationCompat.FLAG_ONLY_ALERT_ONCE)  //取消震动,铃声其他都不好使

        service.startForeground(NOTIFICATION_ID, mBuilder?.build())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channelId = "RAY_NG_M_CH_ID"
        val channelName = "V2rayNG Background Service"
        val chan = NotificationChannel(channelId,
                channelName, NotificationManager.IMPORTANCE_HIGH)
        chan.lightColor = Color.DKGRAY
        chan.importance = NotificationManager.IMPORTANCE_NONE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        getNotificationManager()?.createNotificationChannel(chan)
        return channelId
    }

    fun cancelNotification() {
        val service = serviceControl?.get()?.getService() ?: return
        service.stopForeground(true)
        mBuilder = null
        mSubscription?.unsubscribe()
        mSubscription = null
    }

    private fun updateNotification(contentText: String, proxyTraffic: Long, directTraffic: Long) {
        if (mBuilder != null) {
            if (proxyTraffic < NOTIFICATION_ICON_THRESHOLD && directTraffic < NOTIFICATION_ICON_THRESHOLD) {
                mBuilder?.setSmallIcon(R.drawable.ic_v)
            } else if (proxyTraffic > directTraffic) {
                mBuilder?.setSmallIcon(R.drawable.ic_stat_proxy)
            } else {
                mBuilder?.setSmallIcon(R.drawable.ic_stat_direct)
            }
            mBuilder?.setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            mBuilder?.setContentText(contentText) // Emui4.1 need content text even if style is set as BigTextStyle
            getNotificationManager()?.notify(NOTIFICATION_ID, mBuilder?.build())
        }
    }

    private fun getNotificationManager(): NotificationManager? {
        if (mNotificationManager == null) {
            val service = serviceControl?.get()?.getService() ?: return null
            mNotificationManager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
        return mNotificationManager
    }

    fun startSpeedNotification() {
        val service = serviceControl?.get()?.getService() ?: return
        if (mSubscription == null &&
                v2rayPoint.isRunning &&
                service.defaultDPreference.getPrefBoolean(SettingsActivity.PREF_SPEED_ENABLED, false)) {
            var lastZeroSpeed = false
            val outboundTags = service.defaultDPreference.getPrefStringOrderedSet(AppConfig.PREF_CURR_CONFIG_OUTBOUND_TAGS, LinkedHashSet())
            outboundTags.remove(TAG_DIRECT)

            mSubscription = Observable.interval(3, java.util.concurrent.TimeUnit.SECONDS)
                    .subscribe {
                        val queryTime = System.currentTimeMillis()
                        val sinceLastQueryInSeconds = (queryTime - lastQueryTime) / 1000.0
                        var proxyTotal = 0L
                        val text = StringBuilder()
                        outboundTags.forEach {
                            val up = v2rayPoint.queryStats(it, "uplink")
                            val down = v2rayPoint.queryStats(it, "downlink")
                            if (up + down > 0) {
                                appendSpeedString(text, it, up / sinceLastQueryInSeconds, down / sinceLastQueryInSeconds)
                                proxyTotal += up + down
                            }
                        }
                        val directUplink = v2rayPoint.queryStats(TAG_DIRECT, "uplink")
                        val directDownlink = v2rayPoint.queryStats(TAG_DIRECT, "downlink")
                        val zeroSpeed = (proxyTotal == 0L && directUplink == 0L && directDownlink == 0L)
                        if (!zeroSpeed || !lastZeroSpeed) {
                            if (proxyTotal == 0L) {
                                appendSpeedString(text, outboundTags.firstOrNull(), 0.0, 0.0)
                            }
                            appendSpeedString(text, TAG_DIRECT, directUplink / sinceLastQueryInSeconds,
                                    directDownlink / sinceLastQueryInSeconds)
                            updateNotification(text.toString(), proxyTotal, directDownlink + directUplink)
                        }
                        lastZeroSpeed = zeroSpeed
                        lastQueryTime = queryTime
                    }
        }
    }

    private fun appendSpeedString(text: StringBuilder, name: String?, up: Double, down: Double) {
        var n = name ?: "no tag"
        n = n.substring(0, min(n.length, 6))
        text.append(n)
        for (i in n.length..6 step 2) {
            text.append("\t")
        }
        text.append("•  ${up.toLong().toSpeedString()}↑  ${down.toLong().toSpeedString()}↓\n")
    }

    fun stopSpeedNotification() {
        if (mSubscription != null) {
            mSubscription?.unsubscribe() //stop queryStats
            mSubscription = null
            updateNotification(currentConfigName, 0, 0)
        }
    }
}
