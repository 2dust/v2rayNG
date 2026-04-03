package com.daggomostudios.simpsonsvpn

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
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

            val downloadedBytes = response.body?.bytes()
                ?: throw Exception("Resposta vazia ao baixar a configuração.")

            // 2. Obter a chave de 200 caracteres chamando NativeCrypto.getDecryptionKey()
            val longKey = NativeCrypto.getDecryptionKey()

            // 3. Derivar a chave de 32 bytes via SHA-256
            val digest = MessageDigest.getInstance("SHA-256")
            val secretKeyBytes = digest.digest(longKey.toByteArray(Charsets.UTF_8))
            val secretKey = SecretKeySpec(secretKeyBytes, "AES")

            // 4. Extrair o IV (primeiros 12 bytes) e o Ciphertext (o resto)
            if (downloadedBytes.size < 12) {
                throw Exception("Dados baixados muito curtos para conter o IV.")
            }
            val iv = downloadedBytes.copyOfRange(0, 12)
            val cipherText = downloadedBytes.copyOfRange(12, downloadedBytes.size)

            // 5. Configurar o GCM (128 bits de tamanho de tag de autenticação = 16 bytes)
            val gcmSpec = GCMParameterSpec(128, iv)

            // 6. Descriptografar usando AES/GCM/NoPadding
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            val decryptedBytes = cipher.doFinal(cipherText)

            // 7. Retornar o resultado final como uma String pura
            decryptedBytes.toString(Charsets.UTF_8)

        } catch (e: Exception) {
            Log.e("VpnConfigManager", "Erro ao baixar ou descriptografar a configuração: ${e.message}", e)
            throw e
        }
    }
}
