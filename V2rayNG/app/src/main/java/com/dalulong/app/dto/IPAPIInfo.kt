package com.dalulong.app.dto

data class IPAPIInfo(
    var ip: String? = null,
    var clientIp: String? = null,
    var ip_addr: String? = null,
    var query: String? = null,
    var country: String? = null,
    var country_name: String? = null,
    var country_code: String? = null,
    var countryCode: String? = null,
    var location: LocationBean? = null
) {
    data class LocationBean(
        var country_code: String? = null
    )
}