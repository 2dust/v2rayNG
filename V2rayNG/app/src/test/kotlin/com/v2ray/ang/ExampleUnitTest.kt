import com.v2ray.ang.util.Utils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilTest {

    @Test
    fun testParseInt() {
        assertEquals(1234, Utils.parseInt("1234"))
    }

    @Test
    fun testIsIpAddress() {
        // Invalid IP addresses
        assertFalse(Utils.isIpAddress("114.113.112.266"))
        assertFalse(Utils.isIpAddress("666.666.666.666"))
        assertFalse(Utils.isIpAddress("256.0.0.0"))
        assertFalse(Utils.isIpAddress("::ffff:127.0.0.0.1"))
        assertFalse(Utils.isIpAddress("baidu.com"))
        assertFalse(Utils.isIpAddress(""))

        // Valid IP addresses
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

    // Uncomment and implement the following tests as needed

    /*
    @Test
    fun testFmtHysteria2Parse() {
        val url = "hysteria2://password2@127.0.0.1:443?obfs=salamander&obfs-password=obfs2&insecure=0#Hy22"
        val result = Hysteria2Fmt.parse(url)
        
        assertTrue(result != null)
        assertEquals("127.0.0.1", result?.server)
        assertEquals("obfs2", result?.obfsPassword)
        assertEquals("tls", result?.security)

        val urlConverted = Hysteria2Fmt.toUri(result!!)
        assertTrue(urlConverted.contains("obfs2"))
    }

    @Test
    fun testFmtSsParse() {
        val url = "ss://aa:bb@127.0.0.1:10000#sss"
        val result = ShadowsocksFmt.parse(url)
        
        assertTrue(result != null)
        assertEquals("127.0.0.1", result?.server)

        val resultWithEncoded = ShadowsocksFmt.parse("ss://YWVzLTI1Ni1nY206cGFzc3dvcmQy@127.0.0.1:10000#sss")
        
        assertTrue(resultWithEncoded != null)
        assertEquals("127.0.0.1", resultWithEncoded?.server)
    }

    @Test
    fun testFmtSocksParse() {
        val url = "socks://Og%3D%3D@127.0.0.1:1000#socks2"
        val result = SocksFmt.parse(url)
        
        assertTrue(result != null)
        assertEquals("127.0.0.1", result?.server)

        val urlConverted = SocksFmt.toUri(result!!)
        assertTrue(url.contains(urlConverted))

        val resultWithEncoded = SocksFmt.parse("socks://dXNlcjpwYXNz@127.0.0.1:1000#socks2")
        
        assertTrue(resultWithEncoded != null)
        assertEquals("127.0.0.1", resultWithEncoded?.server)
    }

    @Test
    fun testFmtTrojanParse() {
        val url = "trojan://password2@127.0.0.1:443?flow=xtls-rprx-vision&security=tls&type=tcp&headerType=none#Trojan"
        val result = TrojanFmt.parse(url)
        
        assertTrue(result != null)
        assertEquals("127.0.0.1", result?.server)
        assertEquals("xtls-rprx-vision", result?.flow)

        val simpleUrl = "trojan://password2@127.0.0.1:443#Trojan"
        val simpleResult = TrojanFmt.parse(simpleUrl)
        
        assertTrue(simpleResult != null)
        assertEquals("127.0.0.1", simpleResult?.server)
        assertEquals("tls", simpleResult?.security)
    }

    @Test
    fun testFmtVlessParse() {
        val url = "vless://cae1dc39-0547-4b1d-9e7a-01132c7ae3a7@127.0.0.1:443?encryption=none&flow=xtls-rprx-vision&security=reality&sni=sni2&fp=chrome&pbk=publickkey&sid=123456&spx=%2F&type=ws&host=host2&path=path2#VLESS"
        
        val result = VlessFmt.parse(url)
        
        assertTrue(result != null)
        assertEquals("127.0.0.1", result?.server)
        assertEquals("xtls-rprx-vision", result?.flow)

        val urlConverted = VlessFmt.toUri(result!!)
        
        assertTrue(urlConverted.contains("xtls-rprx-vision"))
    }

    @Test
    fun testFmtVmessParse() {
         val url = "vmess://ew0KICAidiI6ICIyIiwNCiAgInBzIjogIlZtZXNzIiwNCiAgImFkZCI6ICIxMjcuMC4wLjEiLA..."
         
         val result = VmessFmt.parse(url)

         // Validate the parsed results
         assertTrue(result != null)
         // Add more assertions based on expected values here.
         // Example:
         //assertEquals(expectedValue, result?.someField) 
     }

     @Test
     fun testFmtWireguardParse() {
         val url = "wireguard://privatekey2@127.0.0.1:2000?publickey=publickey2&reserved=2%2C2%2C3&address=127..."
         
         val result = WireguardFmt.parse(url)

         // Validate the parsed results
         assertTrue(result != null)
         // Add more assertions based on expected values here.
         // Example:
         //assertEquals(expectedValue, result?.someField) 
     }
     */
}
