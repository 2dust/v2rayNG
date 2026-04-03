package com.daggomostudios.simpsonsvpn

import android.util.Log
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object VpnConfigManager {

    // Extensão para converter string hexadecimal para ByteArray
    private fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    private val client = OkHttpClient()
    private val gson = Gson()
    private const val TAG = "VpnConfigManager"

    // URL do GitHub Raw contendo o arquivo encriptado
    const val GITHUB_RAW_URL = "https://raw.githubusercontent.com/sarlindom39/Muecaria/main/config.enc"

    // Callback para atualizar o painel de debug
    var debugUpdateCallback: ((DebugInfo) -> Unit)? = null

    /**
     * Baixa o arquivo encriptado, descriptografa em memória e faz o parsing do JSON
     * retornando a lista de servidores disponíveis.
     */
    suspend fun getVpnServers(): List<VpnServerModel> = withContext(Dispatchers.IO) {
        debugUpdateCallback?.invoke(DebugInfo(status = "Iniciando carregamento..."))

        try {
            Log.d(TAG, "Iniciando download de: $GITHUB_RAW_URL")
            debugUpdateCallback?.invoke(DebugInfo(status = "Download iniciado", httpStatus = "Aguardando..."))
            
            // 1. Fazer um requisição HTTP GET para a URL
            val request = Request.Builder()
                .url(GITHUB_RAW_URL)
                .header("User-Agent", "Mozilla/5.0 SimpsonsVPN")
                .build()
                
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "Erro HTTP: ${response.code} - ${response.message}")
                debugUpdateCallback?.invoke(DebugInfo(status = "Erro", httpStatus = "${response.code} - ${response.message}", errorMessage = "Erro HTTP"))
                throw Exception("Erro ao baixar a configuração: ${response.code}")
            }

            val downloadedBytes = response.body?.bytes()
                ?: throw Exception("Resposta vazia ao baixar a configuração.")
            
            Log.d(TAG, "Download concluído: ${downloadedBytes.size} bytes recebidos.")
            debugUpdateCallback?.invoke(DebugInfo(status = "Download concluído", httpStatus = "OK (${downloadedBytes.size} bytes)"))

            // 2. Obter a chave hexadecimal de 64 caracteres chamando NativeCrypto.getDecryptionKey()
            val hexKeyString = try {
                NativeCrypto.getDecryptionKey()
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Erro ao carregar biblioteca nativa: ${e.message}")
                debugUpdateCallback?.invoke(DebugInfo(status = "Erro", decryptionStatus = "Erro NDK", errorMessage = "Biblioteca nativa não carregada."))
                throw Exception("Biblioteca nativa não carregada.")
            }
            debugUpdateCallback?.invoke(DebugInfo(status = "Chave NDK obtida", decryptionStatus = "Chave obtida"))

            // Converter a string hexadecimal para um array de bytes
            val keyBytes = hexKeyString.decodeHex()
            if (keyBytes.size != 32) { // 32 bytes para AES-256
                Log.e(TAG, "Chave hexadecimal inválida: ${hexKeyString.length} caracteres, ${keyBytes.size} bytes.")
                debugUpdateCallback?.invoke(DebugInfo(status = "Erro", decryptionStatus = "Chave inválida", errorMessage = "Chave hexadecimal NDK inválida."))
                throw Exception("Chave hexadecimal NDK inválida.")
            }

            // 3. Derivar a chave de 32 bytes via SHA-256 (usando os bytes da chave hexadecimal)
            val digest = MessageDigest.getInstance("SHA-256")
            val secretKeyBytes = digest.digest(keyBytes)
            val secretKey = SecretKeySpec(secretKeyBytes, "AES")

            // 4. Extrair o IV (primeiros 12 bytes) e o Ciphertext (o resto)
            if (downloadedBytes.size < 28) { // 12 (IV) + pelo menos algum dado + 16 (Tag GCM)
                Log.e(TAG, "Dados insuficientes: ${downloadedBytes.size} bytes")
                debugUpdateCallback?.invoke(DebugInfo(status = "Erro", decryptionStatus = "Dados curtos", errorMessage = "Dados baixados muito curtos."))
                throw Exception("Dados baixados muito curtos.")
            }
            val iv = downloadedBytes.copyOfRange(0, 12)
            val cipherText = downloadedBytes.copyOfRange(12, downloadedBytes.size)

            // 5. Configurar o GCM (128 bits de tamanho de tag de autenticação = 16 bytes)
            val gcmSpec = GCMParameterSpec(128, iv)

            // 6. Descriptografar usando AES/GCM/NoPadding
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            
            val decryptedBytes = try {
                cipher.doFinal(cipherText)
            } catch (e: Exception) {
                Log.e(TAG, "Erro na descriptografia AES-GCM: ${e.message}. Verifique se a chave no Python é idêntica à do C++.")
                debugUpdateCallback?.invoke(DebugInfo(status = "Erro", decryptionStatus = "Falha", errorMessage = "Falha na descriptografia dos dados."))
                throw Exception("Falha na descriptografia dos dados.")
            }
            debugUpdateCallback?.invoke(DebugInfo(status = "Descriptografia OK", decryptionStatus = "OK"))

            // 7. Converter bytes descriptografados em String JSON
            val jsonString = decryptedBytes.toString(Charsets.UTF_8)
            Log.d(TAG, "JSON descriptografado com sucesso.")
            debugUpdateCallback?.invoke(DebugInfo(status = "JSON descriptografado", jsonParseStatus = "OK"))

            // 8. Fazer o parsing do JSON para a lista de servidores usando Gson
            val responseObj = try {
                gson.fromJson(jsonString, VpnConfigResponse::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao processar JSON: ${e.message}")
                debugUpdateCallback?.invoke(DebugInfo(status = "Erro", jsonParseStatus = "Inválido", errorMessage = "Formato JSON inválido."))
                throw Exception("Formato JSON inválido.")
            }
            
            if (responseObj?.listaServidores == null) {
                debugUpdateCallback?.invoke(DebugInfo(status = "Erro", jsonParseStatus = "Lista vazia", errorMessage = "Lista de servidores está vazia no JSON."))
                throw Exception("Lista de servidores está vazia no JSON.")
            }

            Log.d(TAG, "Sucesso: ${responseObj.listaServidores.size} servidores carregados (Versão ${responseObj.versao})")
            debugUpdateCallback?.invoke(DebugInfo(status = "Servidores carregados", serversLoaded = responseObj.listaServidores.size.toString()))
            
            return@withContext responseObj.listaServidores

        } catch (e: Exception) {
            Log.e(TAG, "Erro fatal no VpnConfigManager: ${e.message}", e)
            debugUpdateCallback?.invoke(DebugInfo(status = "Erro fatal", errorMessage = e.message ?: "Erro desconhecido"))
            throw e
        }
    }

    /**
     * Importa a lista de servidores para o MmkvManager do v2rayNG
     */
    suspend fun importServersToCore(servers: List<VpnServerModel>) = withContext(Dispatchers.IO) {
        try {
            val sb = StringBuilder()
            for (server in servers) {
                if (server.config.isNotBlank()) {
                    sb.append(server.config.trim())
                    sb.appendLine()
                }
            }
            
            val configText = sb.toString()
            if (configText.isBlank()) {
                Log.w(TAG, "Nenhuma configuração válida para importar.")
                debugUpdateCallback?.invoke(DebugInfo(status = "Erro", serversLoaded = "0", errorMessage = "Nenhuma configuração válida para importar."))
                return@withContext
            }

            // Usamos o AngConfigManager para importar o lote de configurações
            // O v2rayNG retorna um Pair(count, countSub)
            val (count, _) = com.v2ray.ang.handler.AngConfigManager.importBatchConfig(configText, "", true)
            
            if (count > 0) {
                Log.d(TAG, "$count servidores importados para o Core com sucesso.")
                debugUpdateCallback?.invoke(DebugInfo(status = "Servidores importados", serversLoaded = count.toString()))
            } else {
                Log.e(TAG, "O Core não conseguiu processar as configurações. Verifique o formato vmess/vless/etc.")
                debugUpdateCallback?.invoke(DebugInfo(status = "Erro", serversLoaded = "0", errorMessage = "Core não processou configs."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao importar servidores para o Core: ${e.message}")
            debugUpdateCallback?.invoke(DebugInfo(status = "Erro", errorMessage = "Erro ao importar para o Core: ${e.message}"))
        }
    }
}
