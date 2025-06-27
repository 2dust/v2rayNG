package com.v2ray.ang.dto

/**
 * VPN interface address configuration enum class
 * Defines predefined IPv4 and IPv6 address pairs for VPN TUN interface configuration.
 * Each option provides client and router addresses to establish point-to-point VPN tunnels.
 */
enum class VpnInterfaceAddressConfig(
    val displayName: String,
    val ipv4Client: String,
    val ipv4Router: String,
    val ipv6Client: String,
    val ipv6Router: String
) {
    OPTION_1("10.10.14.x", "10.10.14.1", "10.10.14.2", "fc00::10:10:14:1", "fc00::10:10:14:2"),
    OPTION_2("10.1.0.x", "10.1.0.1", "10.1.0.2", "fc00::10:1:0:1", "fc00::10:1:0:2"),
    OPTION_3("10.0.0.x", "10.0.0.1", "10.0.0.2", "fc00::10:0:0:1", "fc00::10:0:0:2"),
    OPTION_4("172.31.0.x", "172.31.0.1", "172.31.0.2", "fc00::172:31:0:1", "fc00::172:31:0:2"),
    OPTION_5("172.20.0.x", "172.20.0.1", "172.20.0.2", "fc00::172:20:0:1", "fc00::172:20:0:2"),
    OPTION_6("172.16.0.x", "172.16.0.1", "172.16.0.2", "fc00::172:16:0:1", "fc00::172:16:0:2"),
    OPTION_7("192.168.100.x", "192.168.100.1", "192.168.100.2", "fc00::192:168:100:1", "fc00::192:168:100:2");

    companion object {
        /**
         * Retrieves the VPN interface address configuration based on the specified index.
         *
         * @param index The configuration index (0-based) corresponding to user selection
         * @return The VpnInterfaceAddressConfig instance at the specified index,
         *         or OPTION_1 (default) if the index is out of bounds
         */
        fun getConfigByIndex(index: Int): VpnInterfaceAddressConfig {
            return if (index in values().indices) {
                values()[index]
            } else {
                OPTION_1 // Default to the first configuration
            }
        }
    }
}
