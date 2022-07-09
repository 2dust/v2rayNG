package com.v2ray.ang.dto

data class AngConfig(
        var index: Int,
        var vmess: ArrayList<VmessBean>,
        var subItem: ArrayList<SubItemBean>
) {
    data class VmessBean(var guid: String = "123456",
                         var address: String = "v2ray.cool",
                         var port: Int = 10086,
                         var id: String = "a3482e88-686a-4a58-8126-99c9df64b7bf",
                         var alterId: Int = 64,
                         var security: String = "aes-128-cfb",
                         var network: String = "tcp",
                         var remarks: String = "def",
                         var headerType: String = "",
                         var requestHost: String = "",
                         var path: String = "",
                         var streamSecurity: String = "",
                         var allowInsecure: String = "",
                         var configType: Int = 1,
                         var configVersion: Int = 1,
                         var testResult: String = "",
                         var subid: String = "",
                         var flow: String = "",
                         var sni: String = "")

    data class SubItemBean(var id: String = "",
                           var remarks: String = "",
                           var url: String = "",
                           var enabled: Boolean = true)
}
