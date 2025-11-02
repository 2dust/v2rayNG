package com.v2ray.ang


object AppConfig {

    /** The application's package name. */
    const val ANG_PACKAGE = BuildConfig.APPLICATION_ID
    const val TAG = BuildConfig.APPLICATION_ID

    /** Directory names used in the app's file system. */
    const val DIR_ASSETS = "assets"
    const val DIR_BACKUPS = "backups"

    /** Legacy configuration keys. */
    const val ANG_CONFIG = "ang_config"

    /** Preferences mapped to MMKV storage. */
    const val PREF_SNIFFING_ENABLED = "pref_sniffing_enabled"
    const val PREF_ROUTE_ONLY_ENABLED = "pref_route_only_enabled"
    const val PREF_PER_APP_PROXY = "pref_per_app_proxy"
    const val PREF_PER_APP_PROXY_SET = "pref_per_app_proxy_set"
    const val PREF_BYPASS_APPS = "pref_bypass_apps"
    const val PREF_LOCAL_DNS_ENABLED = "pref_local_dns_enabled"
    const val PREF_FAKE_DNS_ENABLED = "pref_fake_dns_enabled"
    const val PREF_APPEND_HTTP_PROXY = "pref_append_http_proxy"
    const val PREF_LOCAL_DNS_PORT = "pref_local_dns_port"
    const val PREF_VPN_DNS = "pref_vpn_dns"
    const val PREF_VPN_BYPASS_LAN = "pref_vpn_bypass_lan"
    const val PREF_VPN_INTERFACE_ADDRESS_CONFIG_INDEX = "pref_vpn_interface_address_config_index"
    const val PREF_VPN_MTU = "pref_vpn_mtu"
    const val PREF_ROUTING_DOMAIN_STRATEGY = "pref_routing_domain_strategy"
    const val PREF_ROUTING_RULESET = "pref_routing_ruleset"
    const val PREF_MUX_ENABLED = "pref_mux_enabled"
    const val PREF_MUX_CONCURRENCY = "pref_mux_concurrency"
    const val PREF_MUX_XUDP_CONCURRENCY = "pref_mux_xudp_concurrency"
    const val PREF_MUX_XUDP_QUIC = "pref_mux_xudp_quic"
    const val PREF_FRAGMENT_ENABLED = "pref_fragment_enabled"
    const val PREF_FRAGMENT_PACKETS = "pref_fragment_packets"
    const val PREF_FRAGMENT_LENGTH = "pref_fragment_length"
    const val PREF_FRAGMENT_INTERVAL = "pref_fragment_interval"
    const val SUBSCRIPTION_AUTO_UPDATE = "pref_auto_update_subscription"
    const val SUBSCRIPTION_AUTO_UPDATE_INTERVAL = "pref_auto_update_interval"
    const val SUBSCRIPTION_DEFAULT_UPDATE_INTERVAL = "1440" // Default is 24 hours
    const val SUBSCRIPTION_UPDATE_TASK_NAME = "subscription_updater"
    const val PREF_SPEED_ENABLED = "pref_speed_enabled"
    const val PREF_CONFIRM_REMOVE = "pref_confirm_remove"
    const val PREF_START_SCAN_IMMEDIATE = "pref_start_scan_immediate"
    const val PREF_DOUBLE_COLUMN_DISPLAY = "pref_double_column_display"
    const val PREF_LANGUAGE = "pref_language"
    const val PREF_UI_MODE_NIGHT = "pref_ui_mode_night"
    const val PREF_PREFER_IPV6 = "pref_prefer_ipv6"
    const val PREF_PROXY_SHARING = "pref_proxy_sharing_enabled"
    const val PREF_ALLOW_INSECURE = "pref_allow_insecure"
    const val PREF_SOCKS_PORT = "pref_socks_port"
    const val PREF_REMOTE_DNS = "pref_remote_dns"
    const val PREF_DOMESTIC_DNS = "pref_domestic_dns"
    const val PREF_DNS_HOSTS = "pref_dns_hosts"
    const val PREF_DELAY_TEST_URL = "pref_delay_test_url"
    const val PREF_LOGLEVEL = "pref_core_loglevel"
    const val PREF_OUTBOUND_DOMAIN_RESOLVE_METHOD = "pref_outbound_domain_resolve_method"
    const val PREF_INTELLIGENT_SELECTION_METHOD = "pref_intelligent_selection_method"
    const val PREF_MODE = "pref_mode"
    const val PREF_IS_BOOTED = "pref_is_booted"
    const val PREF_CHECK_UPDATE_PRE_RELEASE = "pref_check_update_pre_release"
    const val PREF_GEO_FILES_SOURCES = "pref_geo_files_sources"
    const val PREF_USE_HEV_TUNNEL = "pref_use_hev_tunnel"
    const val PREF_HEV_TUNNEL_LOGLEVEL = "pref_hev_tunnel_loglevel"
    const val PREF_HEV_TUNNEL_RW_TIMEOUT = "pref_hev_tunnel_rw_timeout"

    /** Cache keys. */
    const val CACHE_SUBSCRIPTION_ID = "cache_subscription_id"
    const val CACHE_KEYWORD_FILTER = "cache_keyword_filter"

    /** Protocol identifiers. */
    const val PROTOCOL_FREEDOM = "freedom"

    /** Broadcast actions. */
    const val BROADCAST_ACTION_SERVICE = "$ANG_PACKAGE.action.service"
    const val BROADCAST_ACTION_ACTIVITY = "$ANG_PACKAGE.action.activity"
    const val BROADCAST_ACTION_WIDGET_CLICK = "$ANG_PACKAGE.action.widget.click"

    /** Tasker extras. */
    const val TASKER_EXTRA_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE"
    const val TASKER_EXTRA_STRING_BLURB = "com.twofortyfouram.locale.intent.extra.BLURB"
    const val TASKER_EXTRA_BUNDLE_SWITCH = "tasker_extra_bundle_switch"
    const val TASKER_EXTRA_BUNDLE_GUID = "tasker_extra_bundle_guid"
    const val TASKER_DEFAULT_GUID = "Default"

    /** Tags for different proxy modes. */
    const val TAG_PROXY = "proxy"
    const val TAG_DIRECT = "direct"
    const val TAG_BLOCKED = "block"
    const val TAG_FRAGMENT = "fragment"
    const val TAG_DNS = "dns-module"
    const val TAG_DOMESTIC_DNS = "domestic-dns"

    /** Network-related constants. */
    const val UPLINK = "uplink"
    const val DOWNLINK = "downlink"

    /** URLs for various resources. */
    const val GITHUB_URL = "https://github.com"
    const val GITHUB_RAW_URL = "https://raw.githubusercontent.com"
    const val GITHUB_DOWNLOAD_URL = "$GITHUB_URL/%s/releases/latest/download"
    const val ANDROID_PACKAGE_NAME_LIST_URL = "$GITHUB_RAW_URL/2dust/androidpackagenamelist/master/proxy.txt"
    const val APP_URL = "$GITHUB_URL/2dust/v2rayNG"
    const val APP_API_URL = "https://api.github.com/repos/2dust/v2rayNG/releases"
    const val APP_ISSUES_URL = "$APP_URL/issues"
    const val APP_WIKI_MODE = "$APP_URL/wiki/Mode"
    const val APP_PRIVACY_POLICY = "$GITHUB_RAW_URL/2dust/v2rayNG/master/CR.md"
    const val APP_PROMOTION_URL = "aHR0cHM6Ly85LjIzNDQ1Ni54eXovYWJjLmh0bWw="
    const val TG_CHANNEL_URL = "https://t.me/github_2dust"
    const val DELAY_TEST_URL = "https://www.gstatic.com/generate_204"
    const val DELAY_TEST_URL2 = "https://www.google.com/generate_204"
    const val IP_API_URL = "https://speed.cloudflare.com/meta"

    /** DNS server addresses. */
    const val DNS_PROXY = "1.1.1.1"
    const val DNS_DIRECT = "223.5.5.5"
    const val DNS_VPN = "1.1.1.1"
    const val GEOSITE_PRIVATE = "geosite:private"
    const val GEOSITE_CN = "geosite:cn"
    const val GEOIP_PRIVATE = "geoip:private"
    const val GEOIP_CN = "geoip:cn"

    /** Ports and addresses for various services. */
    const val PORT_LOCAL_DNS = "10853"
    const val PORT_SOCKS = "10808"
    const val WIREGUARD_LOCAL_ADDRESS_V4 = "172.16.0.2/32"
    const val WIREGUARD_LOCAL_ADDRESS_V6 = "2606:4700:110:8f81:d551:a0:532e:a2b3/128"
    const val WIREGUARD_LOCAL_MTU = "1420"
    const val LOOPBACK = "127.0.0.1"

    /** Message constants for communication. */
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
    const val MSG_MEASURE_DELAY = 6
    const val MSG_MEASURE_DELAY_SUCCESS = 61
    const val MSG_MEASURE_CONFIG = 7
    const val MSG_MEASURE_CONFIG_SUCCESS = 71
    const val MSG_MEASURE_CONFIG_CANCEL = 72

    /** Notification channel IDs and names. */
    const val RAY_NG_CHANNEL_ID = "RAY_NG_M_CH_ID"
    const val RAY_NG_CHANNEL_NAME = "v2rayNG Background Service"
    const val SUBSCRIPTION_UPDATE_CHANNEL = "subscription_update_channel"
    const val SUBSCRIPTION_UPDATE_CHANNEL_NAME = "Subscription Update Service"

    /** Protocols Scheme **/
    const val VMESS = "vmess://"
    const val CUSTOM = ""
    const val SHADOWSOCKS = "ss://"
    const val SOCKS = "socks://"
    const val HTTP = "http://"
    const val VLESS = "vless://"
    const val TROJAN = "trojan://"
    const val WIREGUARD = "wireguard://"
    const val TUIC = "tuic://"
    const val HYSTERIA2 = "hysteria2://"
    const val HY2 = "hy2://"

    /** Give a good name to this, IDK*/
    const val VPN = "VPN"
    const val VPN_MTU = 1500

    /** hev-sock5-tunnel read-write-timeout value */
    const val HEVTUN_RW_TIMEOUT = "300000"

    // Google API rule constants
    const val GOOGLEAPIS_CN_DOMAIN = "domain:googleapis.cn"
    const val GOOGLEAPIS_COM_DOMAIN = "googleapis.com"

    // Android Private DNS constants
    const val DNS_DNSPOD_DOMAIN = "dot.pub"
    const val DNS_ALIDNS_DOMAIN = "dns.alidns.com"
    const val DNS_CLOUDFLARE_ONE_DOMAIN = "one.one.one.one"
    const val DNS_CLOUDFLARE_DNS_COM_DOMAIN = "dns.cloudflare.com"
    const val DNS_CLOUDFLARE_DNS_DOMAIN = "cloudflare-dns.com"
    const val DNS_GOOGLE_DOMAIN = "dns.google"
    const val DNS_QUAD9_DOMAIN = "dns.quad9.net"
    const val DNS_YANDEX_DOMAIN = "common.dot.dns.yandex.net"

    const val DEFAULT_PORT = 443
    const val DEFAULT_SECURITY = "auto"
    const val DEFAULT_LEVEL = 8
    const val DEFAULT_NETWORK = "tcp"
    const val TLS = "tls"
    const val REALITY = "reality"
    const val HEADER_TYPE_HTTP = "http"

    val DNS_ALIDNS_ADDRESSES = arrayListOf("223.5.5.5", "223.6.6.6", "2400:3200::1", "2400:3200:baba::1")
    val DNS_CLOUDFLARE_ONE_ADDRESSES = arrayListOf("1.1.1.1", "1.0.0.1", "2606:4700:4700::1111", "2606:4700:4700::1001")
    val DNS_CLOUDFLARE_DNS_COM_ADDRESSES = arrayListOf("104.16.132.229", "104.16.133.229", "2606:4700::6810:84e5", "2606:4700::6810:85e5")
    val DNS_CLOUDFLARE_DNS_ADDRESSES = arrayListOf("104.16.248.249", "104.16.249.249", "2606:4700::6810:f8f9", "2606:4700::6810:f9f9")
    val DNS_DNSPOD_ADDRESSES = arrayListOf("1.12.12.12", "120.53.53.53")
    val DNS_GOOGLE_ADDRESSES = arrayListOf("8.8.8.8", "8.8.4.4", "2001:4860:4860::8888", "2001:4860:4860::8844")
    val DNS_QUAD9_ADDRESSES = arrayListOf("9.9.9.9", "149.112.112.112", "2620:fe::fe", "2620:fe::9")
    val DNS_YANDEX_ADDRESSES = arrayListOf("77.88.8.8", "77.88.8.1", "2a02:6b8::feed:0ff", "2a02:6b8:0:1::feed:0ff")

    //minimum list https://serverfault.com/a/304791
    val ROUTED_IP_LIST = arrayListOf(
        "0.0.0.0/5",
        "8.0.0.0/7",
        "11.0.0.0/8",
        "12.0.0.0/6",
        "16.0.0.0/4",
        "32.0.0.0/3",
        "64.0.0.0/2",
        "128.0.0.0/3",
        "160.0.0.0/5",
        "168.0.0.0/6",
        "172.0.0.0/12",
        "172.32.0.0/11",
        "172.64.0.0/10",
        "172.128.0.0/9",
        "173.0.0.0/8",
        "174.0.0.0/7",
        "176.0.0.0/4",
        "192.0.0.0/9",
        "192.128.0.0/11",
        "192.160.0.0/13",
        "192.169.0.0/16",
        "192.170.0.0/15",
        "192.172.0.0/14",
        "192.176.0.0/12",
        "192.192.0.0/10",
        "193.0.0.0/8",
        "194.0.0.0/7",
        "196.0.0.0/6",
        "200.0.0.0/5",
        "208.0.0.0/4",
        "240.0.0.0/4"
    )

    val PRIVATE_IP_LIST = arrayListOf(
        "0.0.0.0/8",
        "10.0.0.0/8",
        "127.0.0.0/8",
        "172.16.0.0/12",
        "192.168.0.0/16",
        "169.254.0.0/16",
        "224.0.0.0/4"
    )

    val GEO_FILES_SOURCES = arrayListOf(
        "Loyalsoldier/v2ray-rules-dat",
        "runetfreedom/russia-v2ray-rules-dat",
        "Chocolate4U/Iran-v2ray-rules"
    )

}
