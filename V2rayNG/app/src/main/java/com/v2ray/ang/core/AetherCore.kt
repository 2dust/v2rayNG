package com.v2ray.ang.core

import com.google.gson.JsonParser
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.AetherProtocol
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.util.JsonUtil
import java.security.MessageDigest

/**
 * The Aether core a configuration runs on, as the arguments its process is started with, the
 * listeners among them. The core of a profile is built from the profile's settings, or is the
 * command line the profile carries in their place; the core of a custom configuration is the
 * command line it carries as aetherCommand. A command line is read as written and run as written,
 * so that what the profile or the configuration says is what runs. Two cores with the same
 * arguments are one core, which is how one process comes to serve several outbounds, as long as
 * they dial out through the same [exit] as well: a process dials out through one.
 */
data class AetherCore(val arguments: List<String>, val exit: AetherExit = AetherExit.PLAIN) {

    /**
     * The loopback port the app dials: Psiphon's or Tor's listener when one of them runs inside the
     * tunnel and is what the app reaches, the core's own listener otherwise.
     */
    val port: Int get() = AetherCoreManager.listenerPortOf(arguments) ?: AetherCoreManager.socksPort

    /** Every loopback port the core is told to listen on; an inbound of the configuration cannot share one. */
    val ports: List<Int>
        get() = LISTENERS
            .mapNotNull { AetherCoreManager.portAfter(arguments, it) }
            .filter { it != 0 }
            .distinct()

    /** The protocol, which tells whose identity files the core uses. */
    val protocol: AetherProtocol get() = AetherCoreManager.protocolOf(arguments)

    /** The tunnel as the names of its parts from the outside in, carriers included; see [AetherCoreManager.pathOf]. */
    val path: List<String> get() = AetherCoreManager.pathOf(arguments)

    /** The command line a profile or a custom configuration carries for this core; [ofCommand] reads it back. */
    val command: String get() = (listOf(COMMAND_NAME) + arguments).joinToString(" ", transform = ::quoted)

    /** True when a process started with [processArguments] runs this core, on whatever ports and at whatever log level. */
    fun runsAs(processArguments: List<String>): Boolean =
        AetherCoreManager.tunnelArguments(processArguments) == AetherCoreManager.tunnelArguments(arguments)

    /** True when the core is told which proxy to dial out through, as a command written by hand may be. */
    val hasUpstream: Boolean get() = AetherCoreManager.UPSTREAM in arguments

    /**
     * This core dialling out through the SOCKS inbound on [port] of the loopback address, which Xray
     * serves so that what the core sends leaves through Xray. A core told an upstream of its own keeps it.
     */
    fun through(port: Int): AetherCore =
        if (hasUpstream) this else copy(arguments = arguments + listOf(AetherCoreManager.UPSTREAM, "socks5://${AppConfig.LOOPBACK}:$port"))

    companion object {

        /** The name a command line starts with; the app runs its own copy of the core whatever the name says. */
        const val COMMAND_NAME = "aether"

        /** The listeners a core may be told to bind: the core's own, Tor's, Psiphon's. */
        private val LISTENERS = listOf("--bind", AetherCoreManager.TOR_BIND, AetherCoreManager.PSIPHON_BIND)

        /**
         * The core of [profile]: the command line it carries, or its settings as arguments on
         * [listenPort], the Aether listen port of the app, dialling out through the exit-node of its
         * settings. The log level is the session's to add. A command the app cannot read is left aside
         * for the settings; the profile editor refuses to store one. A screen passes the port it holds,
         * since the setting is read from storage.
         */
        fun of(profile: ProfileItem, listenPort: Int = AetherCoreManager.socksPort): AetherCore {
            val core = profile.aetherCommand?.takeIf { it.isNotBlank() }?.let(::ofCommand)
                ?: AetherCore(
                    AetherCoreManager.withoutOption(
                        AetherCoreManager.buildArguments(profile, listenPort),
                        "--log-level",
                    )
                )
            return core.copy(exit = AetherExit.of(profile))
        }

        /**
         * The core [command] describes, or null when it names nothing the app can run: no argument
         * at all, or a listener whose port cannot be read. Words are split on whitespace, quotes keep a
         * word together, and a program name in front is dropped. A command that names no listener for
         * the app to dial gets one on [AetherCoreManager.socksPort], the port the Aether outbounds of
         * the app dial unless told otherwise; the core's own defaults are other ports, which nothing
         * in the app dials.
         */
        fun ofCommand(command: String): AetherCore? {
            val arguments = argumentsOf(command)
            if (arguments.isEmpty()) return null
            val listener = AetherCoreManager.listenerFlagOf(arguments)
            if (listener !in arguments) return AetherCore(AetherCoreManager.withListener(arguments, listener, AetherCoreManager.socksPort))
            return AetherCore(arguments).takeIf { AetherCoreManager.portAfter(arguments, listener) != null }
        }

        /** The arguments of [command]: its [words], without a program name in front. */
        internal fun argumentsOf(command: String): List<String> {
            val words = words(command)
            return if (words.firstOrNull()?.startsWith("-") == false) words.drop(1) else words
        }

        /** The words of a command line: split on whitespace, with single or double quotes keeping a word together. */
        internal fun words(command: String): List<String> {
            val words = mutableListOf<String>()
            val word = StringBuilder()
            var quote: Char? = null
            var open = false
            for (c in command) {
                when {
                    quote != null -> if (c == quote) quote = null else word.append(c)
                    c == '"' || c == '\'' -> {
                        quote = c
                        open = true
                    }

                    c.isWhitespace() -> if (open) {
                        words.add(word.toString())
                        word.setLength(0)
                        open = false
                    }

                    else -> {
                        word.append(c)
                        open = true
                    }
                }
            }
            if (open) words.add(word.toString())
            return words
        }

        /** [word] as a command line carries it: quoted when whitespace would split it. */
        private fun quoted(word: String): String = if (word.isEmpty() || word.any(Char::isWhitespace)) "\"$word\"" else word
    }
}

/**
 * Matixray: the exit-node of an Aether core, the outbound that what the core dials out through leaves
 * Xray by. As a rule it is a plain freedom outbound with the finalMask and the dialMode of the core's
 * Aether profile, as an ordinary profile sets them on its own outbound: an Aether profile's outbound
 * only reaches the core on the loopback address, where they would do nothing. See
 * [CoreOutboundBuilder.toOutboundAetherExit].
 *
 * In a proxy chain where the Aether profile is not the entry hop, the one that dials the internet
 * itself, the core dials out through the hop on its entry side instead: that hop's outbound, as the
 * chain builds it, is the exit-node, and [hops] tells those hops apart. The profile's finalMask and
 * dialMode do not count then, nor does anything else of a plain exit-node.
 */
data class AetherExit(val finalMask: String? = null, val dialMode: String? = null, val hops: String? = null) {

    /**
     * What tells this exit-node from another in another process, without what it is made of: a digest
     * of it, which the session's core carries in its environment, see [AetherCoreManager.EXIT_ENV].
     */
    val key: String get() = digest(listOf(finalMask, dialMode, hops).joinToString("\u0000") { it.orEmpty() })

    companion object {
        /** An exit-node with nothing set, as the core of a custom configuration dials out through. */
        val PLAIN = AetherExit()

        /** The exit-node of the core of [profile]. */
        fun of(profile: ProfileItem): AetherExit = AetherExit(profile.finalMask.nullIfBlank(), profile.dialMode.nullIfBlank())

        /**
         * Whether an exit-node takes [finalMask]: blank, or a JSON object, read by the parser JsonUtil uses but
         * without the error JsonUtil logs for text that is not one, since a typing mistake is no failure. The Aether
         * editor and the WARP keys page check theirs alike.
         */
        fun takesFinalMask(finalMask: String?): Boolean = finalMask.isNullOrBlank() || try {
            JsonParser.parseString(finalMask).isJsonObject
        } catch (_: RuntimeException) {
            false
        }

        /**
         * The exit-node of an Aether hop of a proxy chain that dials out through [hops]: the hops on its
         * entry side, in the order a chain lists its profiles, from the one it dials out through, which
         * is the exit-node, to the entry hop. They are told apart by a digest of their profiles, which
         * hold secrets.
         */
        fun through(hops: List<ProfileItem>): AetherExit = AetherExit(hops = digest(JsonUtil.toJson(hops)))

        private fun digest(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
