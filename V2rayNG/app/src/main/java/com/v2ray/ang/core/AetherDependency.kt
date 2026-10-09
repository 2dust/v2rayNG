package com.v2ray.ang.core

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.CoreConfigContext
import com.v2ray.ang.enums.CoreResolvedType
import com.v2ray.ang.enums.EConfigType

/**
 * The Aether core a configuration runs on. Every Aether outbound is a SOCKS connection to the one
 * core process the daemon starts, so a configuration can use one core, whether it is the selected
 * profile itself, a hop of a chain, a routing target or a policy-group member. Two profiles count as
 * the same core when it would be started with the same arguments, the port it listens on included,
 * and would dial out through the same exit-node.
 *
 * In a chain the Aether hop can stand anywhere. The hops on its exit side dial through its outbound,
 * as they dial through any hop. Its outbound only reaches the core on the loopback address, so it
 * dials through nothing itself; the core dials out through the hop on its entry side, if there is
 * one, see [AetherExit.through]. A chain of two Aether hops would need two cores, and one runs.
 *
 * A custom configuration asks for its core itself, with the command line of the core as
 * aetherCommand at its top level, and the SOCKS outbounds that dial the port that command listens
 * on are its Aether outbounds.
 */
sealed interface AetherDependency {

    /** No Aether outbound anywhere in the configuration. */
    data object None : AetherDependency

    /** Exactly one Aether core; the daemon starts it, and the Aether outbounds dial its [AetherCore.port]. */
    data class Single(val core: AetherCore) : AetherDependency

    /** Aether profiles with different settings, which one core cannot serve. */
    data object Conflicting : AetherDependency

    /**
     * One tunnel that would dial out two ways: on its own and through a hop of a proxy chain, through
     * two different hops, or by two different exit-node settings. One core dials out one way.
     */
    data object TwoExits : AetherDependency

    /** A proxy chain with more than one Aether hop. */
    data class TwoAetherHops(val chainTag: String) : AetherDependency

    /**
     * A custom configuration whose aetherCommand is no command line the app can run; [written] quotes
     * it for the screen. It stays out of the log, where what was written could carry a secret.
     */
    data class UnusableCommand(val written: String) : AetherDependency {
        override fun toString(): String = "UnusableCommand"
    }

    /** A custom configuration whose aetherCommand listens on [port] of [AppConfig.LOOPBACK], which none of its SOCKS outbounds dials. */
    data class NoOutbound(val port: Int) : AetherDependency

    /** How [AetherDependency.routeThroughXray] left a custom configuration. */
    sealed interface Routing {
        /** [core] as it is to be started: dialling out through Xray, or by an upstream of its own. */
        data class Routed(val core: AetherCore) : Routing

        /** An inbound of the configuration listens on the port of the secondary-socks inbound already. */
        data object PortTaken : Routing

        /** A balancer or an observatory of the configuration picks outbounds by [selector], which would pick the exit-node too. */
        data class ExitNodeSelected(val selector: String) : Routing
    }

    companion object {

        /** The key of a custom configuration that carries the command line of its core. */
        const val COMMAND_KEY = "aetherCommand"

        /** How much of a value that is no command at all is quoted back in the error. */
        private const val QUOTED_LENGTH = 40

        /**
         * [outbounds] are the resolved outbounds of a configuration. A chain's profiles are in
         * reverse dial order: the first is the exit, the last is the entry hop.
         */
        fun of(outbounds: List<CoreConfigContext.ResolvedOutbound>): AetherDependency {
            var found: AetherCore? = null
            for (outbound in outbounds) {
                val profiles = outbound.resolvedProfiles
                val chained = outbound.resolvedType == CoreResolvedType.PROXYCHAIN
                if (chained && profiles.count { it.configType == EConfigType.AETHER } > 1) return TwoAetherHops(outbound.tag)
                for ((index, profile) in profiles.withIndex()) {
                    if (profile.configType != EConfigType.AETHER) continue
                    val core = AetherCore.of(profile).let { core ->
                        // The hops after it in the list are those on its entry side, which the core dials out through.
                        if (chained && index < profiles.lastIndex) core.copy(exit = AetherExit.through(profiles.subList(index + 1, profiles.size))) else core
                    }
                    if (found == null) {
                        found = core
                    } else if (core != found) {
                        return if (core.arguments == found.arguments) TwoExits else Conflicting
                    }
                }
            }
            return found?.let(::Single) ?: None
        }

        /**
         * [config] is a custom configuration. Its core is the command line at its aetherCommand key,
         * and the SOCKS outbounds dialing the port that command listens on are the ones the core
         * serves; there has to be at least one, or the core would run for nothing. Without that key,
         * the configuration asks for no core.
         */
        fun ofCustom(config: JsonObject): AetherDependency {
            val written = config.get(COMMAND_KEY)?.takeUnless { it.isJsonNull } ?: return None
            val text = textOf(written) ?: return UnusableCommand(written.toString().take(QUOTED_LENGTH))
            val core = AetherCore.ofCommand(text) ?: return UnusableCommand(text.take(QUOTED_LENGTH))
            if (socksOutboundSettings(config).none { dials(it, core.port) }) return NoOutbound(core.port)
            return Single(core)
        }

        /**
         * Has what the Aether [core] of the custom configuration [config] sends out leave through
         * Xray, as the configuration of a profile does: the secondary-socks inbound on [port], three
         * above the Aether listen port for every Aether core, after the other inbounds, a freedom outbound
         * after the other outbounds, and a rule ahead of every other that joins the two. Returns the
         * core told to dial out through that inbound, which is also written back as aetherCommand.
         * Nothing is added when an inbound of the configuration listens on [port] already, nor when a
         * balancer or an observatory would pick the freedom outbound among its own, since those pick
         * outbounds by the start of their tags: what it balances could leave directly. A core that
         * names an upstream of its own, as one exported from the app does, is left as it is with the
         * configuration; so is a configuration that already has an inbound or an outbound under those
         * tags, or something else than a list where they would go.
         */
        fun routeThroughXray(config: JsonObject, core: AetherCore, port: Int): Routing {
            if (core.hasUpstream) return Routing.Routed(core)
            val inbounds = listOrNew(config, "inbounds") ?: return Routing.Routed(core)
            val outbounds = listOrNew(config, "outbounds") ?: return Routing.Routed(core)
            val routing = objectOrNew(config, "routing") ?: return Routing.Routed(core)
            val rules = listOrNew(routing, "rules") ?: return Routing.Routed(core)
            if (tagged(inbounds, AppConfig.TAG_SECONDARY_SOCKS) || tagged(outbounds, AppConfig.TAG_EXIT_NODE)) return Routing.Routed(core)

            val taken = inbounds.flatMap { inbound ->
                inbound.takeIf { it.isJsonObject }?.let { inboundPorts(it.asJsonObject.get("port")) }.orEmpty()
            }
            if (taken.any { port in it }) return Routing.PortTaken
            exitNodeSelector(config, routing)?.let { return Routing.ExitNodeSelected(it) }
            inbounds.add(JsonObject().apply {
                addProperty("tag", AppConfig.TAG_SECONDARY_SOCKS)
                addProperty("port", port)
                addProperty("listen", AppConfig.LOOPBACK)
                addProperty("protocol", "mixed")
                add("settings", JsonObject().apply { addProperty("udp", true) })
            })
            outbounds.add(JsonObject().apply {
                addProperty("tag", AppConfig.TAG_EXIT_NODE)
                addProperty("protocol", "freedom")
            })
            val exitRule = JsonObject().apply {
                add("inboundTag", JsonArray().apply { add(AppConfig.TAG_SECONDARY_SOCKS) })
                addProperty("outboundTag", AppConfig.TAG_EXIT_NODE)
            }
            routing.add("rules", JsonArray().apply {
                add(exitRule)
                addAll(rules)
            })
            config.add("inbounds", inbounds)
            config.add("outbounds", outbounds)
            config.add("routing", routing)

            val routed = core.through(port)
            config.addProperty(COMMAND_KEY, routed.command)
            return Routing.Routed(routed)
        }

        /**
         * The first selector of the balancers in [routing] or of the observatories of [config] that the
         * tag of the exit-node starts with; those pick the outbounds whose tags start with a selector.
         */
        private fun exitNodeSelector(config: JsonObject, routing: JsonObject): String? {
            val balancers: Iterable<JsonElement> = routing.get("balancers")?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
            val selectors = balancers.flatMap { textsAt(it, "selector") } +
                listOf("observatory", "burstObservatory").flatMap { key -> config.get(key)?.let { textsAt(it, "subjectSelector") }.orEmpty() }
            return selectors.firstOrNull { AppConfig.TAG_EXIT_NODE.startsWith(it) }
        }

        /** The texts in the list at [key] of [element], an object; none when there is no such list. */
        private fun textsAt(element: JsonElement, key: String): List<String> =
            element.takeIf { it.isJsonObject }?.asJsonObject?.get(key)?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { it.takeIf { text -> text.isJsonPrimitive && text.asJsonPrimitive.isString }?.asString }
                .orEmpty()

        /** The list at [key] of [config], a new one when there is none; null when [key] holds something else. */
        private fun listOrNew(config: JsonObject, key: String): JsonArray? {
            val element = config.get(key)?.takeUnless { it.isJsonNull } ?: return JsonArray()
            return element.takeIf { it.isJsonArray }?.asJsonArray
        }

        /** The object at [key] of [config], a new one when there is none; null when [key] holds something else. */
        private fun objectOrNew(config: JsonObject, key: String): JsonObject? {
            val element = config.get(key)?.takeUnless { it.isJsonNull } ?: return JsonObject()
            return element.takeIf { it.isJsonObject }?.asJsonObject
        }

        private fun tagged(list: JsonArray, tag: String): Boolean =
            list.any { it.isJsonObject && it.asJsonObject.get("tag")?.let(::textOf) == tag }

        /**
         * True when an inbound of the configuration [content], as it is handed to Xray, listens on
         * [port]. The Aether core has to listen there on the loopback address, and Xray comes first:
         * the core only binds once its tunnel is up. The Aether outbound would then dial the inbound of
         * its own configuration until the core gives up. This covers what the profile editor cannot
         * see: a local proxy port changed later or picked at random, an imported profile, and the
         * inbounds of a custom configuration.
         */
        fun inboundListensOn(content: String, port: Int): Boolean {
            val config = try {
                JsonParser.parseString(content).takeIf { it.isJsonObject }?.asJsonObject
            } catch (_: JsonParseException) {
                null
            }
            val inbounds = config?.get("inbounds")?.takeIf { it.isJsonArray }?.asJsonArray ?: return false
            return inbounds.any { inbound ->
                inbound.isJsonObject && inboundPorts(inbound.asJsonObject.get("port")).any { port in it }
            }
        }

        /** The ports of an inbound: a number, or the text forms Xray reads, such as "1080", "1000-2000" and "53,443,1000-2000". */
        private fun inboundPorts(element: JsonElement?): List<IntRange> {
            val primitive = element?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return emptyList()
            return primitive.asString.split(',').mapNotNull { part ->
                val bounds = part.split('-').map { it.trim().toIntOrNull() ?: return@mapNotNull null }
                if (bounds.size in 1..2) bounds.min()..bounds.max() else null
            }
        }

        /** True when the SOCKS outbound with [settings] dials the core's listener on [port]. */
        private fun dials(settings: JsonObject, port: Int): Boolean =
            settings.get("address")?.let(::textOf) == AppConfig.LOOPBACK && settings.get("port")?.let(::portOf) == port

        private fun socksOutboundSettings(config: JsonObject): List<JsonObject> {
            val outbounds = config.get("outbounds")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
            return outbounds.mapNotNull { element ->
                val outbound = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val protocol = outbound.get("protocol")?.let(::textOf)
                if (!protocol.equals(EConfigType.SOCKS.name, ignoreCase = true)) return@mapNotNull null
                outbound.get("settings")?.takeIf { it.isJsonObject }?.asJsonObject
            }
        }

        private fun textOf(element: JsonElement): String? =
            element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.trim()

        /** The port of a SOCKS outbound: a JSON number, which is all Xray reads there. */
        private fun portOf(element: JsonElement): Int? =
            element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asString?.toIntOrNull()?.takeIf { it in 1..65535 }
    }
}
