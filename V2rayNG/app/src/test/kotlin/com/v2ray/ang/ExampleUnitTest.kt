import org.junit.Assert.*
import org.junit.Test
import com.v2ray.ang.util.Utils

class UtilTest {

  @Test
  fun test_parseInt() {
      assertEquals(Utils.parseInt("1234"), 1234)
  }
    
  @Test
  fun test_isIpAddress() {
      assertFalse(Utils.isIpAddress("114.113.112.266"))
      assertFalse(Utils.isIpAddress("666.666.666.666"))
      assertFalse(Utils.isIpAddress("256.0.0.0" ))
      assertFalse(Utils.isIpAddress("::ffff:127.0.0.0.1" ))
      assertFalse(Utils.isIpAddress("baidu.com"))
      assertFalse(Utils.isIpAddress(""))

      assertTrue(Utils.isIpAddress("127.0.0.1" ))
      assertTrue(Utils.isIpAddress("127.0.0.1:80" ))
      assertTrue(Utils.isIpAddress("0.0.0.0/0" ))
      assertTrue(Utils.isIpAddress("::1" ))
      assertTrue(Utils.isIpAddress("[::1]:80" ))
      assertTrue(Utils.isIpAddress("2605:2700:0:3::4713:93e3" ))
      assertTrue(Utils.isIpAddress("[2605:2700:0:3::4713:93e3]:80" ))
      assertTrue(Utils.isIpAddress("::ffff:192.168.173.22" ))
      assertTrue(Utils.isIpAddress("[::ffff:192.168.173.22]:80" ))
      assertTrue(Utils.isIpAddress("1::" ))
      assertTrue(Utils.isIpAddress("::" ))
      assertTrue(Utils.isIpAddress("::/0" ))
      assertTrue(Utils.isIpAddress("10.24.56.0/24" ))
      assertTrue(Utils.isIpAddress("2001:4321::1" ))
      assertTrue(Utils.isIpAddress("240e:1234:abcd:12::6666" ))
      assertTrue(Utils.isIpAddress("240e:1234:abcd:12::/64" ))
  }
}

