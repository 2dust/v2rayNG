package com.v2ray.ang.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.*
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.StrictMode
import android.support.v4.app.NotificationCompat
import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.extension.defaultDPreference
import com.v2ray.ang.extension.toSpeedString
import com.v2ray.ang.ui.SettingsActivity
import com.v2ray.ang.util.AngConfigManager
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.Utils
import go.Seq
import libv2ray.Libv2ray
import libv2ray.V2RayVPNServiceSupportsSet
import org.jetbrains.anko.doAsync
import rx.Observable
import rx.Subscription
import java.io.File
import java.lang.ref.SoftReference

class V2RayTestService : VpnService() {
    companion object {
        fun startV2Ray(context: Context, index: Int) {
            Log.e("startV2Ray-a",index.toString())
            val intent = Intent(context.applicationContext, V2RayTestService::class.java)
            intent.putExtra("index", index.toString());
            context.startService(intent)
        }
    }

    private lateinit var index:String
    private val v2rayPoint = Libv2ray.newV2RayPoint(V2RayCallback())
    private lateinit var configContent: String
    private lateinit var mInterface: ParcelFileDescriptor

    private val connectivity by lazy { getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }

    private val defaultNetworkCallback by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    setUnderlyingNetworks(arrayOf(network))
                }
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities?) {
                    // it's a good idea to refresh capabilities
                    setUnderlyingNetworks(arrayOf(network))
                }
                override fun onLost(network: Network) {
                    setUnderlyingNetworks(null)
                }
            }
        } else {
            null
        }
    }
    private var listeningForDefaultNetwork = false

    override fun onCreate() {
        super.onCreate()
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        v2rayPoint.packageName = Utils.packagePath(applicationContext)
        Seq.setContext(applicationContext)
    }

    override fun onRevoke() {
        stopV2Ray()
    }

    override fun onLowMemory() {
        stopV2Ray()
        super.onLowMemory()
    }

    fun shutdown() {
        stopV2Ray(true)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        index=intent.getStringExtra("index")
        Log.e("onStartCommand",index)
        startV2ray2(index)
        return START_STICKY
    }

    private fun startV2ray2(index:String) {
        if (!v2rayPoint.isRunning) {
            try {
                val mFilter = IntentFilter(AppConfig.BROADCAST_ACTION_SERVICE)
                mFilter.addAction(Intent.ACTION_SCREEN_ON)
                mFilter.addAction(Intent.ACTION_SCREEN_OFF)
                mFilter.addAction(Intent.ACTION_USER_PRESENT)
                //registerReceiver(mMsgReceive, mFilter)
            } catch (e: Exception) {
            }

            configContent = defaultDPreference.getPrefString(AppConfig.PREF_CURR_CONFIG, "")
            v2rayPoint.configureFileContent = configContent
            v2rayPoint.enableLocalDNS = defaultDPreference.getPrefBoolean(SettingsActivity.PREF_LOCAL_DNS_ENABLED, false)
            v2rayPoint.forwardIpv6 = defaultDPreference.getPrefBoolean(SettingsActivity.PREF_FORWARD_IPV6, false)
            v2rayPoint.domainName = defaultDPreference.getPrefString(AppConfig.PREF_CURR_CONFIG_DOMAIN, "")

            try {
                v2rayPoint.runLoop()
            } catch (e: Exception) {
                Log.d(packageName, e.toString())
            }

            if (v2rayPoint.isRunning) {
                val result = Utils.testConnection2(this, 10808)
                Log.e("testconnect2", "$index, speed is $result")
                AngConfigManager.configs.vmess[index.toInt()].testResult = result.toString()
                MessageUtil.sendMsg2UI(this, AppConfig.MSG_TEST_SUCCESS, "$index,$result"+"ms")
           } else {
                AngConfigManager.configs.vmess[index.toInt()].testResult = "-1"
                MessageUtil.sendMsg2UI(this, AppConfig.MSG_TEST_SUCCESS, "$index,-1ms")
            }
            stopV2Ray(true)
        }
    }

    private fun stopV2Ray(isForced: Boolean = true) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (listeningForDefaultNetwork) {
                connectivity.unregisterNetworkCallback(defaultNetworkCallback)
                listeningForDefaultNetwork = false
            }
        }
        if (v2rayPoint.isRunning) {
            try {
                v2rayPoint.stopLoop()
            } catch (e: Exception) {
                Log.d(packageName, e.toString())
            }
        }

        if (isForced) {
            //stopSelf has to be called ahead of mInterface.close(). otherwise v2ray core cannot be stooped
            //It's strage but true.
            //This can be verified by putting stopself() behind and call stopLoop and startLoop
            //in a row for several times. You will find that later created v2ray core report port in use
            //which means the first v2ray core somehow failed to stop and release the port.
            stopSelf()
            try {
                mInterface.close()
            } catch (ignored: Exception) {
            }

        }
    }

    private inner class V2RayCallback : V2RayVPNServiceSupportsSet {
        override fun shutdown(): Long {
            try {
                this@V2RayTestService.shutdown()
                return 0
            } catch (e: Exception) {
                Log.d(packageName, e.toString())
                return -1
            }
        }

        override fun prepare(): Long {
            return 0
        }

        override fun protect(l: Long) = (if (this@V2RayTestService.protect(l.toInt())) 0 else 1).toLong()

        override fun onEmitStatus(l: Long, s: String?): Long {
            return 0
        }

        override fun setup(s: String): Long {
                return 0
        }
        override fun sendFd(): Long {
            return 0
        }
    }
}

