package com.v2ray.ang.dto

data class AetherEndpoint(val host: String, val port: Int) {

    override fun toString(): String = if (':' in host) "[$host]:$port" else "$host:$port"

    companion object {

        fun of(host: String?, port: String?): AetherEndpoint? {
            val address = host?.trim()?.removeSurrounding("[", "]")?.let(::canonicalAddress) ?: return null
            val number = port?.trim()?.let(::decimal)?.takeIf { it in 1..65535 } ?: return null
            return AetherEndpoint(address, number)
        }

        fun parse(value: String?): AetherEndpoint? {
            val text = value?.trim().orEmpty()
            val separator = text.lastIndexOf(':')
            if (separator <= 0) return null
            val host = text.substring(0, separator)
            if (':' in host && !(host.startsWith('[') && host.endsWith(']'))) return null
            return of(host, text.substring(separator + 1))
        }

        private fun canonicalAddress(address: String): String? =
            if (':' in address) {
                ipv6Groups(address)?.let(::formatIpv6)
            } else {
                ipv4Octets(address)?.joinToString(".")
            }

        private fun decimal(text: String): Int? {
            if (text.isEmpty() || text.length > 5) return null
            return text.fold(0) { value, ch -> value * 10 + (ch.digitToIntOrNull() ?: return null) }
        }

        private fun ipv4Octets(address: String): List<Int>? {
            val parts = address.split('.')
            if (parts.size != 4) return null
            return parts.map { part ->
                if (part.length !in 1..3 || part.length > 1 && part.first().digitToIntOrNull() == 0) return null
                decimal(part)?.takeIf { it <= 255 } ?: return null
            }
        }

        private fun ipv6Groups(address: String): List<Int>? {
            val halves = address.split("::")
            if (halves.size > 2) return null
            val compressed = halves.size == 2
            val head = hexGroups(halves[0], allowIpv4 = !compressed) ?: return null
            val tail = if (compressed) hexGroups(halves[1], allowIpv4 = true) ?: return null else emptyList()
            val missing = 8 - head.size - tail.size
            if (if (compressed) missing < 1 else missing != 0) return null
            return head + List(missing) { 0 } + tail
        }

        private fun hexGroups(section: String, allowIpv4: Boolean): List<Int>? {
            if (section.isEmpty()) return emptyList()
            val pieces = section.split(':')
            return buildList {
                pieces.forEachIndexed { index, piece ->
                    if (allowIpv4 && index == pieces.lastIndex && '.' in piece) {
                        val octets = ipv4Octets(piece) ?: return null
                        add(octets[0] shl 8 or octets[1])
                        add(octets[2] shl 8 or octets[3])
                    } else {
                        if (piece.length !in 1..4) return null
                        add(piece.fold(0) { value, ch -> value * 16 + (ch.digitToIntOrNull(16) ?: return null) })
                    }
                }
            }
        }

        private fun formatIpv6(groups: List<Int>): String {
            var runStart = -1
            var runLength = 1
            var index = 0
            while (index < groups.size) {
                val start = index
                while (index < groups.size && groups[index] == 0) index++
                if (index - start > runLength) {
                    runStart = start
                    runLength = index - start
                }
                index++
            }
            val text = groups.map { it.toString(16) }
            if (runStart < 0) return text.joinToString(":")
            val head = text.subList(0, runStart).joinToString(":")
            val tail = text.subList(runStart + runLength, text.size).joinToString(":")
            return "$head::$tail"
        }
    }
}
