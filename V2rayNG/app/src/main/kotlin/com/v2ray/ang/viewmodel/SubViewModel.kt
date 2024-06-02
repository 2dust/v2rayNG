package com.v2ray.ang.viewmodel

import android.app.Application
import android.text.TextUtils
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.SubscriptionItem
import com.v2ray.ang.util.AngConfigManager
import com.v2ray.ang.util.MmkvManager
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class SubViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsStorage by lazy {
        MMKV.mmkvWithID(
            MmkvManager.ID_SETTING,
            MMKV.MULTI_PROCESS_MODE
        )
    }

    private val tcpingTestScope by lazy { CoroutineScope(Dispatchers.IO) }

    fun updateConfigViaSubAll(): Int {
        var count = 0
        try {
            MmkvManager.decodeSubscriptions().forEach {
                count += updateConfigViaSub(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return 0
        }
        return count
    }

    fun updateConfigViaSub(it: Pair<String, SubscriptionItem>): Int {
        try {
            if (TextUtils.isEmpty(it.first)
                || TextUtils.isEmpty(it.second.remarks)
                || TextUtils.isEmpty(it.second.url)
            ) {
                return 0
            }
            if (!it.second.enabled) {
                return 0
            }
            val url = Utils.idnToASCII(it.second.url)
            if (!Utils.isValidUrl(url)) {
                return 0
            }
            Log.d(AppConfig.ANG_PACKAGE, url)
            var configText = try {
                Utils.getUrlContentWithCustomUserAgent(url)
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
            if (configText.isEmpty()) {
                configText = try {
                    val httpPort = Utils.parseInt(
                        settingsStorage?.decodeString(AppConfig.PREF_HTTP_PORT),
                        AppConfig.PORT_HTTP.toInt()
                    )
                    Utils.getUrlContentWithCustomUserAgent(url, httpPort)
                } catch (e: Exception) {
                    e.printStackTrace()
                    ""
                }
            }
            if (configText.isEmpty()) {
                return 0
            }
            return importBatchConfig(configText, it.first, false)
        } catch (e: Exception) {
            e.printStackTrace()
            return 0
        }
    }

    fun importBatchConfig(server: String?, subid: String = "", append: Boolean): Int {
        var count = AngConfigManager.importBatchConfig(server, subid, append)
        if (count <= 0) {
            count = AngConfigManager.importBatchConfig(Utils.decode(server), subid, append)
        }
        if (count <= 0) {
            count = AngConfigManager.appendCustomConfigServer(server, subid)
        }
        return count
    }
}
