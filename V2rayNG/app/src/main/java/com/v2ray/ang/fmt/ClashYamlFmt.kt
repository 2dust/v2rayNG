package com.v2ray.ang.fmt

import com.v2ray.ang.AppConfig
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

object ClashYamlFmt {
    private val clashProxyRegex = Regex("""(?m)^\s*proxies\s*:""")

    fun isClashYaml(str: String?): Boolean {
        val text = str?.trim().orEmpty()
        if (text.isEmpty()) return false
        if (text.startsWith("{") || text.startsWith("[")) return false
        return clashProxyRegex.containsMatchIn(text)
    }

    fun parse(str: String): List<RawProfileImport> {
        if (!isClashYaml(str)) return emptyList()

        val yaml = Yaml(SafeConstructor(LoaderOptions()))
        val root = yaml.load<Any?>(str) as? Map<*, *> ?: return emptyList()
        val proxies = root["proxies"] as? List<*> ?: return emptyList()

        return proxies.mapNotNull { node ->
            parseProxy(asStringKeyMap(node as? Map<*, *> ?: return@mapNotNull null))
        }
    }

    private fun parseProxy(source: Map<String, Any?>): RawProfileImport? {
        val type = getString(source, "type")?.lowercase() ?: return null
        val server = getString(source, "server") ?: return null
        val serverPort = getInt(source, "port") ?: return null
        val tls = buildTls(source, forceEnabled = type in setOf("anytls", "trojan", "hysteria2"))

        val outbound = when (type) {
            "anytls" -> mutableMapOf<String, Any?>(
                "type" to "anytls",
                "tag" to AppConfig.TAG_PROXY,
                "server" to server,
                "server_port" to serverPort,
                "password" to (getString(source, "password") ?: return null),
                "tls" to (tls ?: return null),
            )

            "trojan" -> mutableMapOf<String, Any?>(
                "type" to "trojan",
                "tag" to AppConfig.TAG_PROXY,
                "server" to server,
                "server_port" to serverPort,
                "password" to (getString(source, "password") ?: return null),
            ).apply {
                tls?.let { this["tls"] = it }
                buildTransport(source)?.let { this["transport"] = it }
            }

            "vless" -> mutableMapOf<String, Any?>(
                "type" to "vless",
                "tag" to AppConfig.TAG_PROXY,
                "server" to server,
                "server_port" to serverPort,
                "uuid" to (getString(source, "uuid") ?: return null),
            ).apply {
                getString(source, "flow")?.let { this["flow"] = it }
                tls?.let { this["tls"] = it }
                buildTransport(source)?.let { this["transport"] = it }
            }

            "vmess" -> mutableMapOf<String, Any?>(
                "type" to "vmess",
                "tag" to AppConfig.TAG_PROXY,
                "server" to server,
                "server_port" to serverPort,
                "uuid" to (getString(source, "uuid") ?: return null),
            ).apply {
                getString(source, "cipher")?.let { this["security"] = it }
                getInt(source, "alterId")?.let { this["alter_id"] = it }
                tls?.let { this["tls"] = it }
                buildTransport(source)?.let { this["transport"] = it }
            }

            "ss", "shadowsocks" -> mutableMapOf<String, Any?>(
                "type" to "shadowsocks",
                "tag" to AppConfig.TAG_PROXY,
                "server" to server,
                "server_port" to serverPort,
                "method" to (getString(source, "cipher") ?: getString(source, "method") ?: return null),
                "password" to (getString(source, "password") ?: return null),
            )

            "socks5", "socks" -> mutableMapOf<String, Any?>(
                "type" to "socks",
                "tag" to AppConfig.TAG_PROXY,
                "server" to server,
                "server_port" to serverPort,
                "version" to "5",
            ).apply {
                getString(source, "username")?.let { this["username"] = it }
                getString(source, "password")?.let { this["password"] = it }
            }

            "http" -> mutableMapOf<String, Any?>(
                "type" to "http",
                "tag" to AppConfig.TAG_PROXY,
                "server" to server,
                "server_port" to serverPort,
            ).apply {
                getString(source, "username")?.let { this["username"] = it }
                getString(source, "password")?.let { this["password"] = it }
                tls?.let { this["tls"] = it }
            }

            "hysteria2", "hy2" -> mutableMapOf<String, Any?>(
                "type" to "hysteria2",
                "tag" to AppConfig.TAG_PROXY,
                "server" to server,
                "server_port" to serverPort,
                "password" to (getString(source, "password") ?: return null),
                "tls" to (tls ?: return null),
            )

            else -> return null
        }

        val name = getString(source, "name")
            ?.takeIf { it.isNotBlank() }
            ?: "$type $server:$serverPort"
        val rawConfig = RawProfileFmt.createRawConfig(outbound) ?: return null
        val profile = RawProfileFmt.createProfile(
            name = name,
            server = server,
            serverPort = serverPort,
            password = getString(source, "password"),
            method = getString(source, "cipher") ?: getString(source, "method"),
            username = getString(source, "username"),
            sni = getString(source, "servername") ?: getString(source, "sni"),
            alpn = getStringList(source, "alpn"),
            fingerPrint = getString(source, "client-fingerprint"),
            insecure = getBoolean(source, "skip-cert-verify"),
        ).apply {
            security = getString(source, "cipher")
        }

        return RawProfileImport(profile = profile, rawConfig = rawConfig)
    }

    private fun buildTls(source: Map<String, Any?>, forceEnabled: Boolean = false): Map<String, Any?>? {
        val serverName = getString(source, "servername") ?: getString(source, "sni")
        val insecure = getBoolean(source, "skip-cert-verify")
        val alpn = getStringList(source, "alpn")
        val fingerprint = getString(source, "client-fingerprint")
        val reality = asStringKeyMap(getMap(source, "reality-opts"))
        val enabled = forceEnabled ||
                getBoolean(source, "tls") == true ||
                !serverName.isNullOrBlank() ||
                insecure == true ||
                alpn.isNotEmpty() ||
                !fingerprint.isNullOrBlank() ||
                reality.isNotEmpty()
        if (!enabled) return null

        return linkedMapOf<String, Any?>(
            "enabled" to true,
        ).apply {
            serverName?.let { this["server_name"] = it }
            if (insecure != null) {
                this["insecure"] = insecure
            }
            if (alpn.isNotEmpty()) {
                this["alpn"] = alpn
            }
            fingerprint?.let {
                this["utls"] = linkedMapOf(
                    "enabled" to true,
                    "fingerprint" to it,
                )
            }
            if (reality.isNotEmpty()) {
                this["reality"] = linkedMapOf<String, Any?>(
                    "enabled" to true,
                ).apply {
                    getString(reality, "public-key")?.let { this["public_key"] = it }
                    getString(reality, "short-id")?.let { this["short_id"] = it }
                }
            }
        }
    }

    private fun buildTransport(source: Map<String, Any?>): Map<String, Any?>? {
        return when (getString(source, "network")?.lowercase()) {
            null, "", "tcp" -> null
            "ws" -> buildWebSocketTransport(source)
            "grpc" -> buildGrpcTransport(source)
            "http", "h2" -> buildHttpTransport(source)
            "httpupgrade", "http-upgrade" -> buildHttpUpgradeTransport(source)
            else -> null
        }
    }

    private fun buildWebSocketTransport(source: Map<String, Any?>): Map<String, Any?> {
        val wsOpts = asStringKeyMap(getMap(source, "ws-opts"))
        val headers = mutableMapOf<String, String>()
        val headerHost = getString(asStringKeyMap(getMap(wsOpts, "headers")), "Host")
        val host = headerHost ?: getStringList(source, "host").firstOrNull()
        host?.let { headers["Host"] = it }

        return linkedMapOf<String, Any?>(
            "type" to "ws",
        ).apply {
            getString(wsOpts, "path")?.let { this["path"] = it }
            if (headers.isNotEmpty()) {
                this["headers"] = headers
            }
        }
    }

    private fun buildGrpcTransport(source: Map<String, Any?>): Map<String, Any?> {
        val grpcOpts = asStringKeyMap(getMap(source, "grpc-opts"))
        return linkedMapOf<String, Any?>(
            "type" to "grpc",
        ).apply {
            (getString(grpcOpts, "grpc-service-name") ?: getString(source, "grpc-service-name"))
                ?.let { this["service_name"] = it }
        }
    }

    private fun buildHttpTransport(source: Map<String, Any?>): Map<String, Any?> {
        val httpOpts = asStringKeyMap(getMap(source, "http-opts"))
            .ifEmpty { asStringKeyMap(getMap(source, "h2-opts")) }
        val hosts = getStringList(httpOpts, "host").ifEmpty { getStringList(source, "host") }

        return linkedMapOf<String, Any?>(
            "type" to "http",
        ).apply {
            if (hosts.isNotEmpty()) {
                this["host"] = hosts
            }
            getString(httpOpts, "path")?.let { this["path"] = it }
        }
    }

    private fun buildHttpUpgradeTransport(source: Map<String, Any?>): Map<String, Any?> {
        val host = getStringList(source, "host").firstOrNull()
        return linkedMapOf<String, Any?>(
            "type" to "httpupgrade",
        ).apply {
            host?.let { this["host"] = it }
            getString(source, "path")?.let { this["path"] = it }
        }
    }

    private fun getMap(source: Map<String, Any?>, key: String): Map<*, *>? {
        return source[key] as? Map<*, *>
    }

    private fun getString(source: Map<String, Any?>, key: String): String? {
        return source[key]?.toString()?.takeIf { it.isNotBlank() }
    }

    private fun getInt(source: Map<String, Any?>, key: String): Int? {
        val value = source[key] ?: return null
        return when (value) {
            is Int -> value
            is Long -> value.toInt()
            is Double -> value.toInt()
            is Float -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }

    private fun getBoolean(source: Map<String, Any?>, key: String): Boolean? {
        val value = source[key] ?: return null
        return when (value) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true)
            else -> null
        }
    }

    private fun getStringList(source: Map<String, Any?>, key: String): List<String> {
        val value = source[key] ?: return emptyList()
        return when (value) {
            is List<*> -> value.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
            is String -> listOf(value).filter(String::isNotBlank)
            else -> emptyList()
        }
    }

    private fun asStringKeyMap(source: Map<*, *>?): Map<String, Any?> {
        if (source == null) return emptyMap()
        return source.entries.associate { (key, value) -> key.toString() to value }
    }
}
