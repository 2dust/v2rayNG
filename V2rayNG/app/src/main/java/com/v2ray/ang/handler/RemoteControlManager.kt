package com.v2ray.ang.handler

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.google.gson.Gson
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import java.security.MessageDigest
import java.security.SecureRandom

/** Owns external automation grants, whose lifetime is independent of VPN configuration/settings backups. */
object RemoteControlManager {
    private const val KEY_GRANTS = "grants"
    private val gson = Gson()
    private val random = SecureRandom()

    fun selectedPackages(context: Context): Set<String> = readGrants()
        .filter { it.identity == installedIdentity(context, it.packageName) }
        .mapTo(mutableSetOf()) { it.packageName }

    fun setSelectedPackages(context: Context, selected: Collection<String>) {
        val identities = selected.distinct().mapNotNull { packageName ->
            require(packageName != AppConfig.UNIDENTIFIED_PACKAGE && packageName != context.packageName)
            installedIdentity(context, packageName)?.let { packageName to it }
        }.toMap()
        MmkvManager.withRemoteControlStorage { storage ->
            val previous = decodeGrants(storage.decodeString(KEY_GRANTS))
            val updated = RemoteControlPolicy.select(previous, identities) {
                hex(ByteArray(32).also(random::nextBytes))
            }
            // Selection, identities and tokens change as one record; removed grants cannot be revived.
            check(storage.encode(KEY_GRANTS, gson.toJson(updated))) { "Failed to save remote control access" }
        }
    }

    /** Only pass Activity.getCallingPackage(), never referrer or package fields supplied in an intent. */
    fun configurationToken(context: Context, callingPackage: String?): String? {
        if (callingPackage.isNullOrEmpty()) return null
        val identity = installedIdentity(context, callingPackage) ?: return null
        return readGrants().firstOrNull {
            it.packageName == callingPackage && it.identity == identity
        }?.token
    }

    fun isAuthorized(context: Context, senderUid: Int, claimedPackage: String?, token: String?): Boolean {
        val grants = readGrants()
        val senderPackages = if (senderUid >= 0) {
            context.packageManager.getPackagesForUid(senderUid).orEmpty().toSet()
        } else null
        val candidates = senderPackages ?: setOfNotNull(claimedPackage)
        val installed = grants.filter { it.packageName in candidates }.mapNotNull { grant ->
            installedIdentity(context, grant.packageName)?.let { grant.packageName to it }
        }.toMap()
        return RemoteControlPolicy.isAuthorized(grants, installed, senderPackages, claimedPackage, token)
    }

    private fun readGrants(): List<RemoteControlGrant> =
        MmkvManager.withRemoteControlStorage { decodeGrants(it.decodeString(KEY_GRANTS)) }

    private fun decodeGrants(json: String?): List<RemoteControlGrant> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            gson.fromJson(json, Array<RemoteControlGrant>::class.java).toList().also { grants ->
                require(grants.map { it.packageName }.distinct().size == grants.size)
                require(grants.all {
                    it.packageName.isNotBlank() && it.identity.certificates.isNotEmpty() &&
                        it.identity.certificates.all { certificate -> certificate.matches(Regex("[0-9a-f]{64}")) } &&
                        it.identity.firstInstallTime > 0 && it.token.matches(Regex("[0-9a-f]{64}"))
                })
            }
        } catch (error: Exception) {
            // Do not log parsing exceptions: Gson error text can contain the credential payload.
            LogUtil.e(AppConfig.TAG, "RemoteControlManager: invalid grants; denying access (${error.javaClass.simpleName})")
            emptyList()
        }
    }

    private fun installedIdentity(context: Context, packageName: String): RemoteControlIdentity? {
        return try {
            val manager = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                manager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                legacyPackageInfo(manager, packageName)
            }
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners
            } else {
                legacySignatures(info)
            }
            val certificates = signatures.orEmpty().mapTo(mutableSetOf()) {
                hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray()))
            }
            if (certificates.isEmpty() || info.firstInstallTime <= 0) null
            else RemoteControlIdentity(certificates, info.firstInstallTime)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    // Android 24–27 lack SigningInfo. Remove these fallbacks when minSdk reaches 28.
    @Suppress("DEPRECATION")
    private fun legacyPackageInfo(manager: PackageManager, packageName: String) =
        manager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)

    @Suppress("DEPRECATION")
    private fun legacySignatures(info: android.content.pm.PackageInfo) = info.signatures

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
