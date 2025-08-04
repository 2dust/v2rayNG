package com.v2ray.ang.service

/**
 * Interface that defines the control operations for tun2socks implementations.
 * 
 * This interface is implemented by different tunnel solutions like:
 */
interface Tun2SocksControl {
    /**
     * Starts the tun2socks process with the appropriate parameters.
     * This initializes the VPN tunnel and connects it to the SOCKS proxy.
     */
    fun startTun2Socks()
    
    /**
     * Stops the tun2socks process and cleans up resources.
     */
    fun stopTun2Socks()
}
