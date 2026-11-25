package com.v2ray.ang.fmt

import android.util.Base64
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.mockStatic
import java.util.Base64 as JavaBase64

class V2rayNFmtTest {
    private lateinit var mockBase64: MockedStatic<Base64>

    @BeforeEach
    fun setUp() {
        // Base64 alphabet handling is not under test: decode both alphabets directly so every
        // payload reaches V2rayNShareItem parsing.
        mockBase64 = mockStatic(Base64::class.java)
        mockBase64.`when`<ByteArray> {
            Base64.decode(Mockito.anyString(), Mockito.anyInt())
        }.thenAnswer { invocation ->
            val input = (invocation.arguments[0] as String).replace('+', '-').replace('/', '_')
            JavaBase64.getUrlDecoder().decode(input)
        }
    }

    @AfterEach
    fun tearDown() {
        mockBase64.close()
    }

    /** Builds a line in the format v2rayN exports: base64url JSON without padding. */
    private fun shareLine(json: String): String =
        "v2rayn://item/" + JavaBase64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())

    private fun node(type: Int, remarks: String, indexId: String? = null, extra: String = ""): String {
        val id = indexId?.let { "\"IndexId\":\"$it\"," }.orEmpty()
        return shareLine(
            """{$id"ConfigType":$type,"ConfigVersion":4,"Remarks":"$remarks",""" +
                """"Address":"example.com","Port":443,"Password":"secret"$extra}"""
        )
    }

    @Test
    fun unsupportedConfigTypeSkipsOnlyThatEntry() {
        val profiles = V2rayNFmt.parse(
            listOf(
                node(7, "hy2"),
                node(8, "tuic"),
                node(11, "anytls"),
                node(13, "custom"),
                node(6, "trojan"),
            ),
            "sub",
        )

        assertEquals(listOf("hy2", "trojan"), profiles.map { it.remarks })
        assertEquals(listOf(EConfigType.HYSTERIA2, EConfigType.TROJAN), profiles.map { it.configType })
    }

    @Test
    fun entriesWithoutIndexIdAreAllKept() {
        val profiles = V2rayNFmt.parse(
            listOf(node(5, "vless"), node(6, "trojan"), node(7, "hy2")),
            "sub",
        )

        assertEquals(listOf("vless", "trojan", "hy2"), profiles.map { it.remarks })
    }

    @Test
    fun duplicateIndexIdKeepsFirstEntry() {
        val profiles = V2rayNFmt.parse(
            listOf(node(5, "first", "same"), node(6, "second", "same")),
            "sub",
        )

        assertEquals(listOf("first"), profiles.map { it.remarks })
    }

    @Test
    fun proxyChainWhoseMembersWereAllSkippedIsDropped() {
        val profiles = V2rayNFmt.parse(
            listOf(
                node(13, "tls", "a"),
                node(13, "ss", "b"),
                node(102, "chain", "c", ""","ProtoExtraObj":{"ChildItems":"a,b"}"""),
                node(7, "hy2"),
            ),
            "sub",
        )

        assertEquals(listOf("hy2"), profiles.map { it.remarks })
    }

    @Test
    fun proxyChainKeepsSupportedMembers() {
        val profiles = V2rayNFmt.parse(
            listOf(
                node(6, "A", "a"),
                node(7, "B", "b"),
                node(102, "chain", "c", ""","ProtoExtraObj":{"ChildItems":"a,b"}"""),
            ),
            "sub",
        )

        val chain = profiles.single { it.configType == EConfigType.PROXYCHAIN }
        assertEquals(listOf("A", "B"), chain.proxyChainProfileList)
    }

    @Test
    fun selfSubscriptionGroupIsKeptWithoutListedMembers() {
        val profiles = V2rayNFmt.parse(
            listOf(node(101, "group", "g", ""","ProtoExtraObj":{"SubChildItems":"self"}""")),
            "sub-id",
        )

        val group = profiles.single()
        assertEquals(EConfigType.POLICYGROUP, group.configType)
        assertEquals("sub-id", group.policyGroupSubscriptionId)
        assertTrue(group.policyGroupFilter.isNullOrEmpty())
    }
}
