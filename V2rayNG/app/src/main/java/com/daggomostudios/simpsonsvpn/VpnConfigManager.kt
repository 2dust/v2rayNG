package com.daggomostudios.simpsonsvpn

import android.util.Log
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.net.Proxy
import java.net.InetSocketAddress
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

    // Porta do proxy local quando a VPN está conectada
    private const val LOCAL_PROXY_PORT = 10808
    
    // Cliente HTTP normal (sem proxy)
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    
    // Cliente HTTP via proxy local (para usar quando VPN está conectada)
    private fun createProxyClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", LOCAL_PROXY_PORT)))
            .build()
    }
    private val gson = Gson()
    private const val TAG = "VpnConfigManager"

    // URL do GitHub Raw contendo o arquivo encriptado (obtido via NDK)
    private val GITHUB_RAW_URL: String by lazy { NativeCrypto.getConfigUrl() }

    // Prefixo de chave no settingsStorage para mapear ID do servidor → GUID interno do v2rayNG
    private const val KEY_VPN_ID_PREFIX = "SIMPSONS_VPN_ID_"

    // Callback para atualizar o painel de debug
    var debugUpdateCallback: ((DebugInfo) -> Unit)? = null

    /**
     * Guarda o mapeamento entre o ID do servidor VPN (do JSON) e o GUID interno do v2rayNG.
     */
    private fun saveVpnIdMapping(vpnId: String, guid: String) {
        com.v2ray.ang.handler.MmkvManager.encodeSettings("$KEY_VPN_ID_PREFIX$vpnId", guid)
    }

    /**
     * Obtém o GUID interno do v2rayNG associado a um ID do servidor VPN, ou null se não existir.
     */
    private fun getGuidForVpnId(vpnId: String): String? {
        return com.v2ray.ang.handler.MmkvManager.decodeSettingsString("$KEY_VPN_ID_PREFIX$vpnId")
    }

    /**
     * Remove o mapeamento de um ID do servidor VPN.
     */
    private fun removeVpnIdMapping(vpnId: String) {
        com.v2ray.ang.handler.MmkvManager.encodeSettings("$KEY_VPN_ID_PREFIX$vpnId", null)
    }

    /**
     * Baixa o arquivo encriptado, descriptografa em memória e faz o parsing do JSON
     * retornando a lista de servidores disponíveis.
     * @param useProxy Se true, usa o proxy local (para quando VPN está conectada)
     */
    suspend fun getVpnServers(useProxy: Boolean = false): List<VpnServerModel> = withContext(Dispatchers.IO) {
        debugUpdateCallback?.invoke(DebugInfo(status = "Iniciando carregamento..."))

        try {
            Log.d(TAG, "Iniciando download de: $GITHUB_RAW_URL (useProxy=$useProxy)")
            debugUpdateCallback?.invoke(DebugInfo(status = "Download iniciado", httpStatus = "Aguardando..."))

            // Selecionar cliente HTTP (com ou sem proxy)
            val httpClient = if (useProxy) {
                debugUpdateCallback?.invoke(DebugInfo(status = "Usando proxy local...", httpStatus = "SOCKS 127.0.0.1:$LOCAL_PROXY_PORT"))
                createProxyClient()
            } else {
                client
            }
            
            // 1. Fazer um requisição HTTP GET para a URL
            val request = Request.Builder()
                .url(GITHUB_RAW_URL)
                .header("User-Agent", "Mozilla/5.0 SimpsonsVPN")
                .build()
                
            val response = httpClient.newCall(request).execute()

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
            // Retornar lista vazia em vez de lançar exceção para permitir o servidor "Clique para atualizar"
            return@withContext emptyList<VpnServerModel>()
        }
    }

    /**
     * Importa/actualiza/remove servidores no MmkvManager do v2rayNG com base na lógica de IDs:
     *
     *  - ID termina em "rem" (ex: "001rem") → REMOVER a configuração correspondente do app.
     *  - ID já existe localmente (mapeamento ID→GUID guardado) → ACTUALIZAR (subscrever) a configuração existente.
     *  - ID novo (sem mapeamento local) → ADICIONAR como nova configuração.
     *
     * Protege a privacidade usando apenas o campo 'nome' do JSON e ocultando detalhes técnicos.
     */
    suspend fun importServersToCore(servers: List<VpnServerModel>) = withContext(Dispatchers.IO) {
        try {
            var added = 0
            var updated = 0
            var removed = 0

            // Simpsons VPN: Sempre adicionar "Localização Inteligente" no topo
            val smartLocation = VpnServerModel(
                id = "000_smart_location",
                nome = "Localização Inteligente",
                protocolo = "vmess",
                config = "vmess://eyJhZGQiOiIxMjcuMC4wLjEiLCJhaWQiOiIwIiwiYWxwaCI6IiIsImZwIjoiIiwiaG9zdCI6IiIsImlkIjoiMDAwMDAwMDAtMDAwMC0wMDAwLTAwMDAtMDAwMDAwMDAwMDAwIiwiaW5zZWN1cmUiOiIwIiwibmV0Ijoid3MiLCJwYXRoIjoiLyIsInBvcnQiOiI4MCIsInBzIjoiTG9jYWxpemHDp8OjbyBJbnRlbGlnZW50ZSIsInNjeSI6ImF1dG8iLCJzbmkiOiIiLCJ0bHMiOiIiLCJ0eXBlIjoibm9uZSIsInYiOiIyIn0="
            )

            // Se a lista estiver vazia, adicionar também o servidor especial "Clique para atualizar"
            val finalServers = if (servers.isEmpty()) {
                listOf(
                    smartLocation,
                    VpnServerModel(
                        id = "000_update",
                        nome = "Clique para atualizar os servidores",
                        protocolo = "vmess",
                        config = "vmess://eyJhZGQiOiIxMjcuMC4wLjEiLCJhaWQiOiIwIiwiYWxwaCI6IiIsImZwIjoiIiwiaG9zdCI6IiIsImlkIjoiMDAwMDAwMDAtMDAwMC0wMDAwLTAwMDAtMDAwMDAwMDAwMDAwIiwiaW5zZWN1cmUiOiIwIiwibmV0Ijoid3MiLCJwYXRoIjoiLyIsInBvcnQiOiI4MCIsInBzIjoiQ2xpcXVlIHBhcmEgYXR1YWxpemFyIiwic2N5IjoiYXV0byIsInNuaSI6IiIsInRscyI6IiIsInR5cGUiOiJub25lIiwidiI6IjIifQ=="
                    )
                )
            } else {
                listOf(smartLocation) + servers
            }

            for (server in finalServers) {
                val vpnId = server.id.trim()

                // --- Lógica de REMOÇÃO: ID termina em "rem" ---
                if (vpnId.endsWith("rem", ignoreCase = true)) {
                    val baseId = vpnId.dropLast(3) // ex: "001rem" → "001"
                    val existingGuid = getGuidForVpnId(baseId)
                    if (existingGuid != null) {
                        com.v2ray.ang.handler.MmkvManager.removeServer(existingGuid)
                        removeVpnIdMapping(baseId)
                        // Também limpar o mapeamento com o ID completo "rem" caso exista
                        removeVpnIdMapping(vpnId)
                        removed++
                        Log.d(TAG, "Servidor '$baseId' (GUID: $existingGuid) removido.")
                    } else {
                        Log.w(TAG, "Servidor com ID base '$baseId' não encontrado para remoção.")
                    }
                    continue
                }

                if (server.config.isBlank()) continue

                val existingGuid = getGuidForVpnId(vpnId)

                if (existingGuid != null) {
                    // --- Lógica de ACTUALIZAÇÃO (subscrever): mesmo ID já existe ---
                    val existingProfile = com.v2ray.ang.handler.MmkvManager.decodeServerConfig(existingGuid)
                    if (existingProfile != null) {
                        // Reimportar a configuração actualizada substituindo a entrada existente
                        val (count, _) = com.v2ray.ang.handler.AngConfigManager.importBatchConfig(server.config.trim(), "", true)
                        if (count > 0) {
                            val allServers = com.v2ray.ang.handler.MmkvManager.decodeAllServerList()
                            if (allServers.isNotEmpty()) {
                                val newGuid = allServers[0]
                                // Remover a entrada antiga e actualizar o mapeamento
                                com.v2ray.ang.handler.MmkvManager.removeServer(existingGuid)
                                val profile = com.v2ray.ang.handler.MmkvManager.decodeServerConfig(newGuid)
                                if (profile != null) {
                                    profile.remarks = server.nome
                                    profile.description = "Simpsons VPN Secure Connection"
                                    com.v2ray.ang.handler.MmkvManager.encodeServerConfig(newGuid, profile)
                                }
                                saveVpnIdMapping(vpnId, newGuid)
                                updated++
                                Log.d(TAG, "Servidor '$vpnId' actualizado (novo GUID: $newGuid).")
                            }
                        }
                    } else {
                        // O GUID guardado já não existe — tratar como novo
                        removeVpnIdMapping(vpnId)
                        importNewServer(server, vpnId)
                        added++
                    }
                } else {
                    // --- Lógica de ADIÇÃO: ID novo ---
                    importNewServer(server, vpnId)
                    added++
                }
            }

            Log.d(TAG, "Sincronização concluída: $added adicionados, $updated actualizados, $removed removidos.")
            
            // --- Lógica de ORDENAÇÃO FIXA por ID ---
            // Reordenar a lista global do v2rayNG com base na ordem dos IDs (001, 002, ...)
            val allVpnIds = com.v2ray.ang.handler.MmkvManager.decodeSettingsAllKeys()
                ?.filter { it.startsWith(KEY_VPN_ID_PREFIX) }
                ?.map { it.removePrefix(KEY_VPN_ID_PREFIX) }
                ?.filter { !it.endsWith("rem", ignoreCase = true) }
                ?.sorted() ?: emptyList() // Ordenar por ID: 001, 002, 003...

            val sortedGuids = allVpnIds.mapNotNull { getGuidForVpnId(it) }
            if (sortedGuids.isNotEmpty()) {
                com.v2ray.ang.handler.MmkvManager.encodeServerList(sortedGuids.toMutableList(), "")
                Log.d(TAG, "Servidores reordenados por ID: $allVpnIds")

                // Simpsons VPN: Definir Localização Inteligente como padrão se nada estiver selecionado
                if (com.v2ray.ang.handler.MmkvManager.getSelectServer().isNullOrEmpty()) {
                    val smartGuid = getGuidForVpnId("000_smart_location")
                    if (smartGuid != null) {
                        com.v2ray.ang.handler.MmkvManager.setSelectServer(smartGuid)
                        Log.d(TAG, "Localização Inteligente definida como servidor padrão.")
                    }
                }
            }

            debugUpdateCallback?.invoke(
                DebugInfo(
                    status = "Sincronização concluída",
                    serversLoaded = "${added + updated}"
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao importar servidores para o Core: ${e.message}")
            debugUpdateCallback?.invoke(DebugInfo(status = "Erro", errorMessage = "Erro ao importar para o Core: ${e.message}"))
        }
    }

    /**
     * Importa um servidor novo para o Core e guarda o mapeamento ID→GUID.
     */
    private fun importNewServer(server: VpnServerModel, vpnId: String) {
        val (count, _) = com.v2ray.ang.handler.AngConfigManager.importBatchConfig(server.config.trim(), "", true)
        if (count > 0) {
            val allServers = com.v2ray.ang.handler.MmkvManager.decodeAllServerList()
            if (allServers.isNotEmpty()) {
                val newGuid = allServers[0]
                val profile = com.v2ray.ang.handler.MmkvManager.decodeServerConfig(newGuid)
                if (profile != null) {
                    profile.remarks = server.nome
                    profile.description = "Simpsons VPN Secure Connection"
                    com.v2ray.ang.handler.MmkvManager.encodeServerConfig(newGuid, profile)
                }
                saveVpnIdMapping(vpnId, newGuid)
                Log.d(TAG, "Servidor '$vpnId' adicionado (GUID: $newGuid).")
            }
        }
    }
}
