package SpeedUpVPN

import android.os.Build
import android.support.annotation.RequiresApi
import android.util.Log
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec


object VpnEncrypt{
    private const val theKey="your key"
    const val vpnRemark="v2ray.vpn"
    val vpnGroupName="SpeedUp.VPN"
    @JvmField var builtinServersUpdated=false
    @JvmField var builtinSubID="999"
    @JvmStatic fun aesEncrypt(v:String, secretKey:String=theKey) = AES256.encrypt(v, secretKey)
    @JvmStatic fun aesDecrypt(v:String, secretKey:String=theKey) = AES256.decrypt(v, secretKey)
    @JvmStatic fun readFileAsTextUsingInputStream(fileName: String)  = File(fileName).inputStream().readBytes().toString(Charsets.UTF_8)
}


private object AES256{
    private fun cipher(opmode:Int, secretKey:String):Cipher{
        if(secretKey.length != 32) throw RuntimeException("SecretKey length is not 32 chars")
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val sk = SecretKeySpec(secretKey.toByteArray(Charsets.UTF_8), "AES")
        val iv = IvParameterSpec(secretKey.substring(0, 16).toByteArray(Charsets.UTF_8))
        c.init(opmode, sk, iv)
        return c
    }
    fun encrypt(str:String, secretKey:String):String{
        val encrypted = cipher(Cipher.ENCRYPT_MODE, secretKey).doFinal(str.toByteArray(Charsets.UTF_8))
        var encstr: String
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O || isWindows())
            encstr = java.util.Base64.getEncoder().encodeToString(encrypted)
        else
            encstr = android.util.Base64.encodeToString(encrypted, android.util.Base64.DEFAULT)

        return encstr
    }
    fun decrypt(str:String, secretKey:String):String{
        try {
            val byteStr: ByteArray;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O || isWindows())
                byteStr = java.util.Base64.getDecoder().decode(str.toByteArray(Charsets.UTF_8))
            else
                byteStr = android.util.Base64.decode(str.toByteArray(Charsets.UTF_8), android.util.Base64.DEFAULT)

            return String(cipher(Cipher.DECRYPT_MODE, secretKey).doFinal(byteStr))
        } catch (e: Exception) {
            Log.e("VpnEncrypt","decrypt failed",e)
            return ""
        }
    }
    fun isWindows(): Boolean {
        var os= System.getProperty("os.name")
        if(os.isNullOrEmpty())
            return false
        else
            return os.contains("Windows")
    }
}

