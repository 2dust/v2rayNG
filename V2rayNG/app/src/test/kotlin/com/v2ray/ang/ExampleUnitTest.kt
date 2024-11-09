import com.v2ray.ang.util.Utils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilTest {

    @Test
    fun test_parseInt() {
        assertEquals(Utils.parseInt("1234"), 1234)
    }

    @Test
    fun test_isIpAddress() {
        assertFalse(Utils.isIpAddress("114.113.112.266"))
        assertFalse(Utils.isIpAddress("666.666.666.666"))
        assertFalse(Utils.isIpAddress("256.0.0.0"))
        assertFalse(Utils.isIpAddress("::ffff:127.0.0.0.1"))
        assertFalse(Utils.isIpAddress("baidu.com"))
        assertFalse(Utils.isIpAddress(""))

        assertTrue(Utils.isIpAddress("127.0.0.1"))
        assertTrue(Utils.isIpAddress("127.0.0.1:80"))
        assertTrue(Utils.isIpAddress("0.0.0.0/0"))
        assertTrue(Utils.isIpAddress("::1"))
        assertTrue(Utils.isIpAddress("[::1]:80"))
        assertTrue(Utils.isIpAddress("2605:2700:0:3::4713:93e3"))
        assertTrue(Utils.isIpAddress("[2605:2700:0:3::4713:93e3]:80"))
        assertTrue(Utils.isIpAddress("::ffff:192.168.173.22"))
        assertTrue(Utils.isIpAddress("[::ffff:192.168.173.22]:80"))
        assertTrue(Utils.isIpAddress("1::"))
        assertTrue(Utils.isIpAddress("::"))
        assertTrue(Utils.isIpAddress("::/0"))
        assertTrue(Utils.isIpAddress("10.24.56.0/24"))
        assertTrue(Utils.isIpAddress("2001:4321::1"))
        assertTrue(Utils.isIpAddress("240e:1234:abcd:12::6666"))
        assertTrue(Utils.isIpAddress("240e:1234:abcd:12::/64"))
    }

//    @Test
//    fun test_fmtHysteria2Parse() {
//        val url2 = "hysteria2://password2@127.0.0.1:443?obfs=salamander&obfs-password=obfs2&insecure=0#Hy22"
//        var result2 = Hysteria2Fmt.parse(url2)
//        assertTrue(result2 != null)
//        assertTrue(result2?.server == "127.0.0.1")
//        assertTrue(result2?.obfsPassword == "obfs2")
//        assertTrue(result2?.security == "tls")
//
//        var url22 = Hysteria2Fmt.toUri(result2!!)
//        assertTrue(url22.contains("obfs2"))
//    }
//
//    @Test
//    fun test_fmtSsParse() {
//        val url2 = "ss://aa:bb@127.0.0.1:10000#sss"
//        var result2 = ShadowsocksFmt.parse(url2)
//        assertTrue(result2 != null)
//        assertTrue(result2?.server == "127.0.0.1")
//
//        var result = ShadowsocksFmt.parse("ss://YWVzLTI1Ni1nY206cGFzc3dvcmQy@127.0.0.1:10000#sss")
//        assertTrue(result != null)
//        assertTrue(result?.server == "127.0.0.1")
//    }
//
//    @Test
//    fun test_fmtSocksParse() {
//        val url2 = "socks://Og%3D%3D@127.0.0.1:1000#socks2"
//        var result2 = SocksFmt.parse(url2)
//        assertTrue(result2 != null)
//        assertTrue(result2?.server == "127.0.0.1")
//        var url22 = SocksFmt.toUri(result2!!)
//        assertTrue(url2.contains(url22))
//
//        var result = SocksFmt.parse("socks://dXNlcjpwYXNz@127.0.0.1:1000#socks2")
//        assertTrue(result != null)
//        assertTrue(result?.server == "127.0.0.1")
//    }
//
//    @Test
//    fun test_fmtTrojanParse() {
//        val url2 = "trojan://password2@127.0.0.1:443?flow=xtls-rprx-vision&security=tls&type=tcp&headerType=none#Trojan"
//        var result2 = TrojanFmt.parse(url2)
//        assertTrue(result2 != null)
//        assertTrue(result2?.server == "127.0.0.1")
//        assertTrue(result2?.flow == "xtls-rprx-vision")
//
//        val url = "trojan://password2@127.0.0.1:443#Trojan"
//        var result = TrojanFmt.parse(url)
//        assertTrue(result != null)
//        assertTrue(result?.server == "127.0.0.1")
//        assertTrue(result?.security == "tls")
//
//
//    }
//
//    @Test
//    fun test_fmtVlessParse() {
//        val url2 =
//            "vless://cae1dc39-0547-4b1d-9e7a-01132c7ae3a7@127.0.0.1:443?encryption=none&flow=xtls-rprx-vision&security=reality&sni=sni2&fp=chrome&pbk=publickkey&sid=123456&spx=%2F&type=ws&host=host2&path=path2#VLESS"
//        var result2 = VlessFmt.parse(url2)
//        assertTrue(result2 != null)
//        assertTrue(result2?.server == "127.0.0.1")
//        assertTrue(result2?.flow == "xtls-rprx-vision")
//
//
//        var url22 = VlessFmt.toUri(result2!!)
//        assertTrue(url22.contains("xtls-rprx-vision"))
//
//    }
//
//    @Test
//    fun test_fmtVmessParse() {
//        val url2 =
//            "vmess://ew0KICAidiI6ICIyIiwNCiAgInBzIjogIlZtZXNzIiwNCiAgImFkZCI6ICIxMjcuMC4wLjEiLA0KICAicG9ydCI6ICIxMDAwMCIsDQogICJpZCI6ICJlYmI5MWM5OS1lZjA3LTRmZjUtOThhYS01OTAyYWI0ZDAyODYiLA0KICAiYWlkIjogIjEyMyIsDQogICJzY3kiOiAiYWVzLTEyOC1nY20iLA0KICAibmV0IjogInRjcCIsDQogICJ0eXBlIjogIm5vbmUiLA0KICAiaG9zdCI6ICJob3N0MiIsDQogICJwYXRoIjogInBhdGgyIiwNCiAgInRscyI6ICIiLA0KICAic25pIjogIiIsDQogICJhbHBuIjogIiINCn0="
//        var result2 = VmessFmt.parse(url2)
//        assertTrue(result2 != null)
//        assertTrue(result2?.server == "127.0.0.1")
//        assertTrue(result2?.method == "aes-128-gcm")
//
//    }
//
//
//    @Test
//    fun test_fmtWireguardParse() {
//        val url2 = "wireguard://privatekey2@127.0.0.1:2000?publickey=publickey2&reserved=2%2C2%2C3&address=127.0.0.127&mtu=1250#WGG"
//        var result2 = WireguardFmt.parse(url2)
//        assertTrue(result2 != null)
//        assertTrue(result2?.server == "127.0.0.1")
//        assertTrue(result2?.publicKey == "publickey2")
//        assertTrue(result2?.localAddress == "127.0.0.127")
//
//
//        var url22 = WireguardFmt.toUri(result2!!)
//        assertTrue(url22.contains("publickey2"))
//    }
}

