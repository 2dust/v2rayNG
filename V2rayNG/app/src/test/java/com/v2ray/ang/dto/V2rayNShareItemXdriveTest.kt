package com.v2ray.ang.dto

import com.google.gson.JsonParser
import com.v2ray.ang.enums.NetworkType
import com.v2ray.ang.util.JsonUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class V2rayNShareItemXdriveTest {

    private fun item(network: String, transportExtra: String) = requireNotNull(
        JsonUtil.fromJson(
            """{"ConfigType":5,"Remarks":"r","Address":"a.example","Port":443,"Password":"id","Network":"$network",
               |"ProtoExtraObj":{"VlessEncryption":"enc"},"TransportExtraObj":$transportExtra}""".trimMargin(),
            V2rayNShareItem::class.java
        )
    )

    @Test
    fun importsXdriveFieldsAndMergesS3IntoExtra() {
        val profile = item(
            "xdrive",
            """{"Path":"/old","XdriveService":"s3","XdriveRemoteFolder":"/remote","XdriveSecrets":"AK,SK",
               |"XdriveS3Endpoint":"https://s3.example","XdriveS3Region":"auto","XdriveS3Bucket":"b",
               |"XdriveS3PathStyle":true,"XdriveExtra":"{\"concurrency\":4}"}""".trimMargin()
        ).toProfileItem()

        assertEquals(NetworkType.XDRIVE.type, profile.network)
        assertEquals("s3", profile.xdriveService)
        assertEquals("/remote", profile.path)
        assertEquals("AK,SK", profile.xdriveSecrets)
        assertEquals(
            JsonParser.parseString(
                """{"concurrency":4,"s3":{"endpoint":"https://s3.example","region":"auto","bucket":"b","pathStyle":true}}"""
            ),
            JsonParser.parseString(profile.xdriveExtra)
        )
    }

    @Test
    fun keepsExplicitS3ObjectAndOmitsEmptyExtra() {
        val explicit = item(
            "xdrive",
            """{"XdriveS3Bucket":"ignored","XdriveExtra":"{\"s3\":{\"bucket\":\"kept\"}}"}"""
        ).toProfileItem()
        assertEquals("""{"s3":{"bucket":"kept"}}""", explicit.xdriveExtra)

        val empty = item("xdrive", """{"Path":"/p","XdriveService":"google-drive"}""").toProfileItem()
        assertNull(empty.xdriveExtra)
        assertEquals("/p", empty.path)
    }

    @Test
    fun ignoresXdriveFieldsForOtherTransports() {
        val profile = item("ws", """{"Path":"/ws","XdriveService":"s3","XdriveRemoteFolder":"/remote","XdriveSecrets":"a"}""")
            .toProfileItem()

        assertEquals("/ws", profile.path)
        assertNull(profile.xdriveService)
        assertNull(profile.xdriveSecrets)
        assertNull(profile.xdriveExtra)
    }
}
