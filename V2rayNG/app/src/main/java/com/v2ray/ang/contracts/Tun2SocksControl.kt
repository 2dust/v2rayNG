package com.v2ray.ang.contracts

/**
 * Interface that defines the control operations for tun2socks implementations.
 * 
 * This interface is implemented by different tunnel solutions like:
 */
interface Tun2SocksControl {
    /**
     * Starts the tun2socks process with the appropriate parameters.
     * This initializes the VPN tunnel and connects it to the SOCKS proxy.
     *
     * @return False when it could not be started; the tunnel would then be up with nothing reading it.
     */
    fun startTun2Socks(): Boolean

    /**
     * Stops the tun2socks process and cleans up resources.
     */
    fun stopTun2Socks()

    /**
     * Whether tun2socks runs. It sets itself up after [startTun2Socks] has returned and ends without
     * a word when that fails, so the service looks once when the rest of its start is done.
     */
    fun isTun2SocksRunning(): Boolean

    companion object {

        /**
         * Whether the start of a service may go on, given what [check] finds of its [tunnel]. The mode
         * where Xray reads the interface itself has no tun2socks, and then nothing stands in the way.
         */
        fun startMayGoOn(tunnel: Tun2SocksControl?, check: (Tun2SocksControl) -> Boolean): Boolean =
            tunnel?.let(check) ?: true
    }
}
