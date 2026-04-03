package com.daggomostudios.simpsonsvpn

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object VpnConfigManager {

    private val client = OkHttpClient()

    // Variável para a URL do GitHub Raw, a ser preenchida posteriormente
    var githubRawUrl: String = "COLOQUE_A_URL_AQUI"

    suspend fun downloadAndDecryptConfig(): String = withContext(Dispatchers.IO) {
        if (githubRawUrl == "COLOQUE_A_URL_AQUI") {
            throw IllegalStateException("githubRawUrl não foi configurada.")
        }

        try {
            // 1. Fazer um requisição HTTP GET para a URL
            val request = Request.Builder().url(githubRawUrl).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                throw Exception("Erro ao baixar a configuração: ${response.code}")
            }

            val encryptedBytes = response.body?.bytes()
                ?: throw Exception("Resposta vazia ao baixar a configuração.")

            // 2. Obter a chave de 200 caracteres chamando NativeCrypto.getDecryptionKey()
            val longKey = NativeCrypto.getDecryptionKey()

            // 3. Derivar da chave longa uma chave AES válida de 256 bits (32 bytes) usando SHA-256
            val sha256Digest = MessageDigest.getInstance("SHA-256")
            val aesKeyBytes = sha256Digest.digest(longKey.toByteArray(Charsets.UTF_8))
            val secretKeySpec = SecretKeySpec(aesKeyBytes, "AES")

            // 4. Descriptografar os bytes em memória usando AES/GCM/NoPadding
            val ivSize = 12 // Tamanho comum para GCM
            if (encryptedBytes.size < ivSize) {
                throw Exception("Dados encriptados muito curtos para conter o IV.")
            }
            val iv = encryptedBytes.copyOfRange(0, ivSize)
            val cipherText = encryptedBytes.copyOfRange(ivSize, encryptedBytes.size)

            val ivParameterSpec = IvParameterSpec(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec)

            val decryptedBytes = cipher.doFinal(cipherText)

            // 5. Retornar o resultado final como uma String pura (o valor final do bloco withContext)
            decryptedBytes.toString(Charsets.UTF_8)

        } catch (e: Exception) {
            Log.e("VpnConfigManager", "Erro ao baixar ou descriptografar a configuração: ${e.message}", e)
            throw e
        }
    }
}
