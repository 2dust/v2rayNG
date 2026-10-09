package com.v2ray.ang.core

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import go.Seq
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.util.concurrent.atomic.AtomicBoolean

/**
 * V2Ray Native Library Manager
 *
 * Thread-safe singleton wrapper for Libv2ray native methods.
 * Provides initialization protection and unified API for V2Ray core operations.
 */
object CoreNativeManager {
    private val initialized = AtomicBoolean(false)

    /**
     * Initialize V2Ray core environment.
     * This method is thread-safe and ensures initialization happens only once.
     * Subsequent calls will be ignored silently.
     *
     */
    fun initCoreEnv(context: Context?) {
        if (initialized.compareAndSet(false, true)) {
            try {
                Seq.setContext(context?.applicationContext)
                val assetPath = Utils.userAssetPath(context)
                val deviceId = Utils.getDeviceIdForXUDPBaseKey()
                Libv2ray.initCoreEnv(assetPath, deviceId)
                LogUtil.i(AppConfig.TAG, "V2Ray core environment initialized successfully")
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to initialize V2Ray core environment", e)
                initialized.set(false)
                throw e
            }
        } else {
            LogUtil.d(AppConfig.TAG, "V2Ray core environment already initialized, skipping")
        }
    }

    fun reconcileBrowserDialer(dialerAddr: String) {
        try {
            Libv2ray.reconcileBrowserDialer(dialerAddr)
            LogUtil.i(AppConfig.TAG, "Browser dialer reconciled successfully with address: $dialerAddr")
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to reconcile browser dialer with address: $dialerAddr", e)
        }
    }


    /**
     * Get V2Ray core version.
     *
     * @return Version string of the V2Ray core
     */
    fun getLibVersion(): String {
        return try {
            Libv2ray.checkVersionX()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to check V2Ray version", e)
            "Unknown"
        }
    }

    /**
     * Measure outbound connection delay.
     *
     * @param config The configuration JSON string
     * @param testUrl The URL to test against
     * @param batch The batch of the measurement, which [cancelOutboundDelays] ends at once
     * @return Delay in milliseconds, or -1 if test failed or its batch was cancelled
     */
    fun measureOutboundDelay(config: String, testUrl: String, batch: String): Long {
        return try {
            Libv2ray.measureOutboundDelayInBatch(batch, config, testUrl)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to measure outbound delay", e)
            -1L
        }
    }

    /**
     * End the measurements of a batch at once: the running ones return -1, and so do those that start later.
     *
     * @param batch The batch of the measurements
     */
    fun cancelOutboundDelays(batch: String) {
        try {
            Libv2ray.cancelOutboundDelays(batch)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to cancel outbound delays", e)
        }
    }

    /**
     * Matixray: opens the exit of this process's Xray and returns the port of its inbound: a mixed inbound
     * on the loopback address, which stays, with its routing rule, while the instance runs. Its exit-node
     * is the outbound tagged exit-node of [configuration], the JSON of an Xray configuration, and comes
     * with the outbounds it dials through; of the rest only the log counts, should the exit start the
     * instance. The Aether core of a scan, a key renewal or a latency test dials out through it, one at
     * a time. The exit is part of the instance that the process measures delays in, since a second
     * instance would take the dialer of the process from the measurements. Each successful call must be
     * followed by [closeExit].
     *
     * @throws Exception when the exit does not open, or another core holds it
     */
    fun openExit(context: Context, configuration: String): Int {
        initCoreEnv(context)
        return Libv2ray.openExit(configuration).toInt()
    }

    /**
     * Close the exit [openExit] opened: its exit-node goes, with the outbounds it dials through.
     */
    fun closeExit() {
        try {
            Libv2ray.closeExit()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to close the exit", e)
        }
    }

    /**
     * Create a new core controller instance.
     *
     * @param handler The callback handler for core events
     * @return A new CoreController instance
     */
    fun newCoreController(handler: CoreCallbackHandler): CoreController {
        return try {
            Libv2ray.newCoreController(handler)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to create core controller", e)
            throw e
        }
    }
}