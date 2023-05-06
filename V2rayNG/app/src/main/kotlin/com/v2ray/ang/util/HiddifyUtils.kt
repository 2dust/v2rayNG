package com.v2ray.ang.util

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.Settings.Global
import android.text.SpannableString
import android.util.Log
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.ServerConfig
import com.v2ray.ang.dto.SubscriptionItem
import com.v2ray.ang.extension.*
import com.v2ray.ang.util.Utils.getLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Base64
import java.util.Locale
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.withContext

class HiddifyUtils {
    companion object {
        private val subStorage by lazy {MMKV.mmkvWithID(                MmkvManager.ID_SUB,                MMKV.MULTI_PROCESS_MODE            )        }
        private val mainStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_MAIN, MMKV.MULTI_PROCESS_MODE) }
        private val settingsStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SETTING, MMKV.MULTI_PROCESS_MODE) }
        private val defaultSharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(            AngApplication.appContext) }
        fun getSelectedServer() : ServerConfig? {
            return MmkvManager.decodeServerConfig(getSelectedServerId()?:"")
        }
        fun getSelectedServerId() : String? {
            return mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER)
        }
        fun setSelectedServer(guid:String){
            mainStorage?.encode(MmkvManager.KEY_SELECTED_SERVER, guid)
        }
        fun getSelectedSub() : Pair<String, SubscriptionItem>? {
            val selected=mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SUB)?:""
            var subscriptions = MmkvManager.decodeSubscriptions()
            return subscriptions.find { it.first==selected }
        }
        fun getSelectedSubId() : String {
            return mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SUB)?:"default"
        }
        fun setSelectedSub(subid:String)  {
            mainStorage?.encode(MmkvManager.KEY_SELECTED_SUB,subid)
        }
        fun getMode(): Int {
            var currentSub=HiddifyUtils.getSelectedSubId()
            var currentServer=HiddifyUtils.getSelectedServerId()
            return when(currentServer){
                currentSub+"1"->1
                currentSub+"2"->2
                else->3
            }
        }
        fun setMode(mode:Int){
            var currentSub=HiddifyUtils.getSelectedSubId()
            if (mode!=3)
                setSelectedServer(currentSub+mode)
            else{
                val servers=MmkvManager.getServerConfigs(currentSub)
                if (!servers.isEmpty())
                    setSelectedServer(servers[0].first)
            }

        }
        fun toTotalUsedGig(totalInBytes: Long, usedInBytes: Long, context: Context): String {
            val total = totalInBytes.toDouble() / 1073741824
            val used = usedInBytes.toDouble() / 1073741824
            return if (getLocale(context) == Locale("fa"))
                String.format("%.0f/%.0f G", used, total).toPersianDigit()
            else
                String.format("%.0f/%.0f G", used, total)
        }

        fun timeToRelativeDate(
            time: Long,
            totalInBytes: Long,
            usedInBytes: Long,
            context: Context
        ): SpannableString {
            if (time < 0)
                return "".bold("")

            val now = System.currentTimeMillis()
            val diffInMillis = (time - now)/86400/1000
            if (diffInMillis <= 0)
                return context.getString(R.string.expired) .bold("")

            if (totalInBytes>0 && totalInBytes <= usedInBytes)
                return context.getString(R.string.full_usage) .bold("")



            return if (getLocale(context).toString().startsWith("fa")) {
                if (diffInMillis > 10)
                    "$diffInMillis روز \n باقیمانده".toPersianDigit()
                        .colorlessTextPart("باقیمانده", context.getColorEx(R.color.colorBorder))
                else
                    "$diffInMillis روز \n باقیمانده".toPersianDigit()
                        .colorlessTextPart("باقیمانده", context.getColorEx(R.color.colorBorder))
                        .colorlessTextPart(
                            "${diffInMillis.toString().toPersianDigit()} روز ",
                            context.getColorEx(R.color.colorRed)
                        )
            } else {
                if (diffInMillis > 10)
                    "$diffInMillis days \n Remain".colorlessTextPart(
                        "Remain",
                        context.getColorEx(R.color.colorBorder)
                    )
                else
                    "$diffInMillis days \n Remain".colorlessTextPart(
                        "Remain",
                        context.getColorEx(R.color.colorBorder)
                    ).colorlessTextPart("$diffInMillis days ", context.getColorEx(R.color.colorRed))
            }
        }

        fun checkState(time: Long, totalInBytes: Long, usedInBytes: Long): String {
            if (totalInBytes>0 && usedInBytes>=totalInBytes)
                return "disable"


            val now = System.currentTimeMillis() / 1000
            val diffInMillis = (time - now) / 86400
            if (time>0 && diffInMillis <= 0)
                return "disable"


            return "enable"
        }

        fun extract_package_info_from_response(response: Utils.Response?, subid: String) {
            if (!subid.isNullOrEmpty() && response?.headers != null) {
                val json = subStorage?.decodeString(subid)
                if (!json.isNullOrBlank()) {
                    var sub = Gson().fromJson(json, SubscriptionItem::class.java)
                    sub.lastUpdateTime=System.currentTimeMillis()
                    var userinfo =
                        response.headers.getOrDefault("Subscription-Userinfo", null)?.firstOrNull()
                    var homepage =
                        response.headers.getOrDefault("Profile-Web-Page-Url", null)?.firstOrNull()
                    var content_disposition =
                        response.headers.getOrDefault("Content-Disposition", null)?.firstOrNull()
                    var profile_title =
                        response.headers.getOrDefault("Profile-Title", null)?.firstOrNull()
                    var profile_update_interval =
                        response.headers.getOrDefault("Profile-Update-Interval", null)
                            ?.firstOrNull()
                    if (!profile_update_interval.isNullOrEmpty()) {
                        sub.update_interval = profile_update_interval.toInt();
                    }
                    if (!profile_title.isNullOrEmpty()){

                        sub.remarks=if (!profile_title.startsWith("base64:"))profile_title else
                            Utils.decode(profile_title.substring("base64:".length))
                    }else if (!content_disposition.isNullOrEmpty()) {
                        val regex = "filename=\"([^\"]+)\"".toRegex()
                        val matchResult = regex.find(content_disposition)
                        sub.remarks = matchResult?.groupValues?.getOrNull(1) ?: sub.remarks
                    } else if (!response.url.isNullOrEmpty()) {
                        var uri = Uri.parse(response.url)
                        sub.remarks =
                            Utils.getQueryParameterValueCaseInsensitive(uri, "Name") ?: sub.remarks
                    }
                    if (!homepage.isNullOrEmpty()) {
                        sub.home_link = homepage
                    }
                    if (!userinfo.isNullOrEmpty()) {
                        fun get(regex: String): String? {
                            return regex.toRegex().findAll(userinfo).mapNotNull {
                                if (it.groupValues.size > 1) it.groupValues[1] else null
                            }.firstOrNull();
                        }

                        sub.used = 0
                        get("upload=([0-9]+)")?.apply {
                            sub.used += toLong()
                        }
                        get("download=([0-9]+)")?.apply {
                            sub.used += toLong()
                        }
                        sub.total = get("total=([0-9]+)")?.toLong() ?: -1
                        //in ms
                        sub.expire = get("expire=([0-9]+)")?.toLong()?.times(1000) ?: -1
                    }

                    subStorage?.encode(subid, Gson().toJson(sub))
                }
            }
        }

        fun setCountry(country: String?) {
            settingsStorage?.encode(AppConfig.PREF_COUNTRY, country)
        }

        fun getCountry():String {
            return settingsStorage?.decodeString(AppConfig.PREF_COUNTRY)!!
        }


        fun getPerAppProxyMode(): PerAppProxyMode {
            if(!defaultSharedPreferences.getBoolean(AppConfig.PREF_PER_APP_PROXY, false))
                return PerAppProxyMode.Global
            if (!defaultSharedPreferences.getBoolean(AppConfig.PREF_BYPASS_APPS, false))
                return PerAppProxyMode.Blocked
            return PerAppProxyMode.Foreign
        }

        fun setPerAppProxyMode(mode: PerAppProxyMode){ //1 disable 2: filtered , 3: foreign
            setDefaultProxyModes()
            defaultSharedPreferences.edit().putBoolean(AppConfig.PREF_PER_APP_PROXY, mode!=PerAppProxyMode.Global).apply()
            if(mode==PerAppProxyMode.Blocked) {
                var blacklist=    defaultSharedPreferences.getStringSet(
                    AppConfig.PREF_PER_APP_PROXY_SET_BLACK,
                    null
                )
                if (blacklist==null){
                    blacklist= HashSet(Utils.readTextFromAssets(AngApplication.appContext, "applications_proxy").split("\n"))
                    defaultSharedPreferences.edit().putStringSet(
                        AppConfig.PREF_PER_APP_PROXY_SET_BLACK,
                        blacklist
                    ).apply()
                }
                defaultSharedPreferences.edit().putBoolean(AppConfig.PREF_BYPASS_APPS, false).apply()
                defaultSharedPreferences.edit().putStringSet(
                    AppConfig.PREF_PER_APP_PROXY_SET,
                    defaultSharedPreferences.getStringSet(
                        AppConfig.PREF_PER_APP_PROXY_SET_BLACK,
                        null
                    )
                ).apply()
            }else if (mode==PerAppProxyMode.Foreign){
                defaultSharedPreferences.edit().putBoolean(AppConfig.PREF_BYPASS_APPS, true).apply()
                var blacklist=    defaultSharedPreferences.getStringSet(
                    AppConfig.PREF_PER_APP_PROXY_SET_WHITE,
                    null
                )
                if (blacklist==null){
                    blacklist= HashSet(Utils.readTextFromAssets(AngApplication.appContext, "applications_direct").split("\n"))
                    defaultSharedPreferences.edit().putStringSet(
                        AppConfig.PREF_PER_APP_PROXY_SET_WHITE,
                        blacklist
                    ).apply()
                }

                defaultSharedPreferences.edit().putStringSet(
                    AppConfig.PREF_PER_APP_PROXY_SET,
                    defaultSharedPreferences.getStringSet(
                        AppConfig.PREF_PER_APP_PROXY_SET_WHITE,
                        null
                    )
                ).apply()

            }
        }

        fun getProxyDataUrl(mode:PerAppProxyMode, sites: Boolean=false): String? {
            var url=if (sites)AppConfig.v2rayCustomRoutingListUrl else AppConfig.androidpackagenamelistUrl
            if(mode==PerAppProxyMode.Foreign)
                return url+"direct_"+ getCountry()
            if(mode==PerAppProxyMode.Blocked)
                return url+"proxy_"+ getCountry()
            return null
        }
        fun getProxyDataAssets(mode:PerAppProxyMode, sites: Boolean=false): String? {
            var url=if (sites)"custom_routing_" else "applications_"
            if(mode==PerAppProxyMode.Foreign)
                return url+"direct_"+ getCountry()
            if(mode==PerAppProxyMode.Blocked)
                return url+"proxy_"+ getCountry()
            return null
        }

        fun setDefaultProxyModes(){
            var country= getCountry()
            mutableListOf("direct","proxy").forEach {
                val app_key=when(it){
                    "direct"->AppConfig.PREF_PER_APP_PROXY_SET_WHITE
                    "proxy"->AppConfig.PREF_PER_APP_PROXY_SET_BLACK
                    else->""
                }
                val routing_key=when(it){
                    "direct"->AppConfig.PREF_V2RAY_ROUTING_DIRECT
                    "proxy"->AppConfig.PREF_V2RAY_ROUTING_AGENT
                    else->""
                }
                var list=defaultSharedPreferences.getStringSet(app_key,null)
                if(list==null||list.isEmpty()){
                    list= HashSet(Utils.readTextFromAssets(AngApplication.appContext, "applications_${it}_${country}").split("\n"))
                    defaultSharedPreferences.edit().putStringSet(app_key,list).apply()
                }
                var routings= settingsStorage?.decodeString(routing_key)
                if(routings.isNullOrEmpty()){
                    routings= Utils.readTextFromAssets(AngApplication.appContext, "applications_${it}_${country}").replace('\n',',')
                    settingsStorage?.encode(routing_key,routings)
                }
            }

        }
    }

    enum class PerAppProxyMode{
        Global,
        Blocked,
        Foreign
    }
}