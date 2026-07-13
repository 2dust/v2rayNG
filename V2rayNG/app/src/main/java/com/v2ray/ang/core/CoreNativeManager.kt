package com.v2ray.ang.core

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.Utils
import go.Seq
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import libv2ray.OutboundProbeController
import libv2ray.OutboundProbeHandler
import java.util.concurrent.atomic.AtomicBoolean

/**
 * V2Ray Native Library Manager
 *
 * Thread-safe singleton wrapper for Libv2ray native methods.
 * Provides initialization protection and unified API for V2Ray core operations.
 */
object CoreNativeManager {
    data class OutboundProbeStatus(
        val outboundTag: String = "",
        val alive: Boolean = false,
        val delay: Long = -1L,
        val samples: Long = 0L,
    )

    data class OutboundProbeBatchResult(
        val cancelled: Boolean = false,
        val results: List<OutboundProbeStatus> = emptyList(),
        val balancerTargets: Map<String, String> = emptyMap(),
    )

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

    fun validateOutboundProbeConfig(config: String): Boolean = try {
        Libv2ray.validateOutboundProbeConfig(config)
        true
    } catch (e: Exception) {
        LogUtil.w(AppConfig.TAG, "Rejected invalid outbound probe source: ${e.message}")
        false
    }

    fun newOutboundProbeController(): OutboundProbeController =
        Libv2ray.newOutboundProbeController()

    fun probeOutbounds(
        controller: OutboundProbeController,
        config: String,
        outboundTagsJson: String,
        balancerTagsJson: String,
        maxConcurrency: Int,
        samples: Int,
        handler: OutboundProbeHandler,
    ): OutboundProbeBatchResult {
        val payload = controller.probeOutbounds(
            config,
            outboundTagsJson,
            balancerTagsJson,
            maxConcurrency,
            samples,
            handler,
        )
        return JsonUtil.fromJsonSafe(payload, OutboundProbeBatchResult::class.java)
            ?: throw IllegalStateException("Invalid outbound probe response")
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
