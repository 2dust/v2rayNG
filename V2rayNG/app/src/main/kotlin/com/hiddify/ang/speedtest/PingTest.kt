package com.hiddify.ang.speedtest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toast
import com.v2ray.ang.service.V2RayServiceManager
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.MmkvManager
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import javax.net.ssl.HttpsURLConnection

/**
 * @author erdigurbuz
 */
class PingTest(serverIpAddress: String, pingTryCount: Int, useProxy:Boolean) : Thread() {
    private var useProxy: Boolean
    var result = HashMap<String, Any>()
    var server = ""
    var count: Int
    var instantRtt = 0.0
    var avgRtt = 0.0
    var isFinished = false
    var started = false
    var done=0

    init {
        server = serverIpAddress
        if(!server.contains(":"))
            server= "$server:80"
        count = pingTryCount
        this.useProxy= useProxy
    }

    override fun run() {
        started=true
//        if(useProxy)
//            proxyConnectionTest()
//        else {
            connectionTest()
//        }
    }
    fun proxyConnectionTest(){
        AngApplication.appContext.registerReceiver(mMsgReceiver, IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY))
        MessageUtil.sendMsg2Service(AngApplication.appContext, AppConfig.MSG_MEASURE_DELAY, "")


    }
    private val mMsgReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_MEASURE_DELAY_SUCCESS -> {
                    done++

                    val resultPair = intent.getSerializableExtra("content") as Pair<String, Long>
                    instantRtt = resultPair.second.toDouble()
                    if(resultPair.second>0) {
                        avgRtt = (avgRtt/count + instantRtt/count)
                        Thread.sleep(100)
                    }else{
                        Thread.sleep(500)
                    }
                    if(done>=count) {
                        isFinished = true
                    }else{

                        MessageUtil.sendMsg2Service(AngApplication.appContext, AppConfig.MSG_MEASURE_DELAY, "")
                    }
                }
                else -> {
                }
            }
        }

    }
    fun connectionTest() {

        for (i in 0 until count) {
            try {
                val startTime = System.nanoTime()
                val serverSplt = server.split(":")
                val socketAddr = InetSocketAddress(serverSplt[0], serverSplt[1].toInt())
                var socket=Socket()
                socket.connect(socketAddr, 10000)
                var timeTaken=(System.nanoTime() - startTime) / 1e6f

                instantRtt= timeTaken.toDouble()
                avgRtt=(avgRtt*i+instantRtt)/(i+1)
                Thread.sleep(100)
            } catch (e: IOException) {
                instantRtt=0.0
                Thread.sleep(500)
            }
        }
        isFinished = true
    }

    fun ping() {
        try {
            val ps = ProcessBuilder("ping", "-c $count", server)
            ps.redirectErrorStream(true)
            val pr = ps.start()
            val `in` = BufferedReader(InputStreamReader(pr.inputStream))
            var line: String
            while (`in`.readLine().also { line = it } != null) {
                if (line.contains("icmp_seq")) {
                    instantRtt = line.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()[line.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray().size - 2].replace("time=", "").toDouble()
                }
                if (line.startsWith("rtt ")) {
                    avgRtt = line.split("/".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()[4].toDouble()
                    break
                }
                if (line.contains("Unreachable") || line.contains("Unknown") || line.contains("%100 packet loss")) {
                    return
                }
            }
            pr.waitFor()
            `in`.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}