package com.v2ray.ang.ui

import android.net.Uri
import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import com.v2ray.ang.dto.SubscriptionItem
import com.v2ray.ang.util.MmkvManager
import com.v2ray.ang.util.Utils

class HiddifyUtils {
    companion object {
        private val subStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SUB, MMKV.MULTI_PROCESS_MODE) }


        fun extract_package_info_from_response(response: Utils.Response?,subid:String) {
            if( !subid.isNullOrEmpty() && response?.headers!=null) {
                val json = subStorage?.decodeString(subid)
                if (!json.isNullOrBlank()) {
                    var sub = Gson().fromJson(json, SubscriptionItem::class.java)
                    var userinfo=response.headers.getOrDefault("Subscription-Userinfo",null)?.firstOrNull()
                    var homepage=response.headers.getOrDefault("Profile-Web-Page-Url",null)?.firstOrNull()
                    var content_disposition=response.headers.getOrDefault("Content-Disposition",null)?.firstOrNull()
                    var profile_update_interval=response.headers.getOrDefault("Profile-Update-Interval",null)?.firstOrNull()
                    if (!content_disposition.isNullOrEmpty()){
                        val regex = "filename=\"([^\"]+)\"".toRegex()
                        val matchResult = regex.find(content_disposition)
                        sub.remarks = matchResult?.groupValues?.getOrNull(1)?:sub.remarks
                    }else if (!response.url.isNullOrEmpty()){
                        var uri= Uri.parse(response.url)
                        sub.remarks = Utils.getQueryParameterValueCaseInsensitive(uri,"Name")?:sub.remarks
                    }
                    if (!homepage.isNullOrEmpty()){
                        sub.home_link=homepage
                    }
                    if (!userinfo.isNullOrEmpty()){
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
                        sub.total = get("total=([0-9]+)")?.toLong() ?: 0

                        sub.expire=get("expire=([0-9]+)")?.toLong() ?: 0
                    }

                    subStorage?.encode(subid, Gson().toJson(sub))
                }
            }
        }

    }

}
