package com.v2ray.ang

/**
 *
 * App Config Const
 */
object AppConfig {
    const val ANG_PACKAGE = "com.v2ray.ang"
    const val ANG_CONFIG = "ang_config"
    const val PREF_CURR_CONFIG = "pref_v2ray_config"
    const val PREF_CURR_CONFIG_GUID = "pref_v2ray_config_guid"
    const val PREF_CURR_CONFIG_NAME = "pref_v2ray_config_name"
    const val PREF_CURR_CONFIG_DOMAIN = "pref_v2ray_config_domain"
    const val PREF_CURR_CONFIG_OUTBOUND_TAGS = "pref_v2ray_config_outbound_tags"
    const val PREF_INAPP_BUY_IS_PREMIUM = "pref_inapp_buy_is_premium"
    const val PREF_MODE = "pref_mode"

    const val VMESS_PROTOCOL: String = "vmess://"
    const val SS_PROTOCOL: String = "ss://"
    const val SOCKS_PROTOCOL: String = "socks://"
    const val BROADCAST_ACTION_SERVICE = "com.v2ray.ang.action.service"
    const val BROADCAST_ACTION_ACTIVITY = "com.v2ray.ang.action.activity"
    const val BROADCAST_ACTION_WIDGET_CLICK = "com.v2ray.ang.action.widget.click"

    const val TASKER_EXTRA_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE"
    const val TASKER_EXTRA_STRING_BLURB = "com.twofortyfouram.locale.intent.extra.BLURB"
    const val TASKER_EXTRA_BUNDLE_SWITCH = "tasker_extra_bundle_switch"
    const val TASKER_EXTRA_BUNDLE_GUID = "tasker_extra_bundle_guid"
    const val TASKER_DEFAULT_GUID = "Default"

    const val PREF_V2RAY_ROUTING_AGENT = "pref_v2ray_routing_agent"
    const val PREF_V2RAY_ROUTING_DIRECT = "pref_v2ray_routing_direct"
    const val PREF_V2RAY_ROUTING_BLOCKED = "pref_v2ray_routing_blocked"
    const val TAG_AGENT = "proxy"
    const val TAG_DIRECT = "direct"
    const val TAG_BLOCKED = "block"

    const val androidpackagenamelistUrl = "https://raw.githubusercontent.com/2dust/androidpackagenamelist/master/proxy.txt"
    const val v2rayCustomRoutingListUrl = "https://raw.githubusercontent.com/2dust/v2rayCustomRoutingList/master/"
    const val v2rayNGIssues = "https://github.com/2dust/v2rayNG/issues"
    const val v2rayNGWikiMode = "https://github.com/2dust/v2rayNG/wiki/Mode"
    const val promotionUrl = "https://1.2345345.xyz/ads.html"

    const val DNS_AGENT = "1.1.1.1"
    const val DNS_DIRECT = "223.5.5.5"

    const val MSG_REGISTER_CLIENT = 1
    const val MSG_STATE_RUNNING = 11
    const val MSG_STATE_NOT_RUNNING = 12
    const val MSG_UNREGISTER_CLIENT = 2
    const val MSG_STATE_START = 3
    const val MSG_STATE_START_SUCCESS = 31
    const val MSG_STATE_START_FAILURE = 32
    const val MSG_STATE_STOP = 4
    const val MSG_STATE_STOP_SUCCESS = 41
    const val MSG_STATE_RESTART = 5
}
