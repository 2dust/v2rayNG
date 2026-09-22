package com.v2ray.ang.handler

import org.junit.Assert.*
import org.junit.Test

class RemoteControlPolicyTest {
    private val identity = RemoteControlIdentity(setOf("certificate"), 1)
    private val grant = RemoteControlGrant("trusted.app", identity, "secret")
    private val installed = mapOf(grant.packageName to identity)

    @Test fun emptySelectionAndAnonymousBroadcastsAreDenied() {
        assertFalse(RemoteControlPolicy.isAuthorized(emptyList(), installed, null, grant.packageName, grant.token))
        assertFalse(RemoteControlPolicy.isAuthorized(listOf(grant), installed, null, grant.packageName, null))
        assertFalse(RemoteControlPolicy.isAuthorized(listOf(grant), installed, null, null, grant.token))
        assertFalse(RemoteControlPolicy.isAuthorized(listOf(grant), installed, null, grant.packageName, ""))
        assertFalse(RemoteControlPolicy.isAuthorized(listOf(grant), installed, null, grant.packageName, "wrong"))
    }

    @Test fun capabilityWorksOnlyForItsActiveGrant() {
        assertTrue(RemoteControlPolicy.isAuthorized(listOf(grant), installed, null, grant.packageName, grant.token))
        assertFalse(RemoteControlPolicy.isAuthorized(listOf(grant), installed, null, "other.app", grant.token))
        assertFalse(RemoteControlPolicy.isAuthorized(listOf(grant), emptyMap(), null, grant.packageName, grant.token))
        for (replacement in listOf(identity.copy(firstInstallTime = 2), identity.copy(certificates = setOf("other")))) {
            assertFalse(RemoteControlPolicy.isAuthorized(
                listOf(grant), mapOf(grant.packageName to replacement), null, grant.packageName, grant.token
            ))
        }
    }

    @Test fun platformIdentityCannotBeOverriddenByAnExtraOrToken() {
        assertTrue(RemoteControlPolicy.isAuthorized(listOf(grant), installed, setOf(grant.packageName), null, null))
        assertFalse(RemoteControlPolicy.isAuthorized(listOf(grant), installed, setOf("attacker"), grant.packageName, grant.token))
        assertFalse(RemoteControlPolicy.isAuthorized(listOf(grant), installed, emptySet(), grant.packageName, grant.token))
    }

    @Test fun revocationAndReauthorizationDoNotReviveOldTokens() {
        assertEquals(listOf(grant), RemoteControlPolicy.select(listOf(grant), installed) { error("must retain active token") })
        val revoked = RemoteControlPolicy.select(listOf(grant), emptyMap()) { error("must not create token") }
        assertTrue(revoked.isEmpty())
        val reauthorized = RemoteControlPolicy.select(revoked, installed) { "new-secret" }
        assertFalse(RemoteControlPolicy.isAuthorized(reauthorized, installed, null, grant.packageName, grant.token))
        assertTrue(RemoteControlPolicy.isAuthorized(reauthorized, installed, null, grant.packageName, "new-secret"))
    }

    @Test fun reinstallAndSignerChangesRequireNewGrants() {
        for (identity in listOf(identity.copy(firstInstallTime = 2), identity.copy(certificates = setOf("replacement")))) {
            val result = RemoteControlPolicy.select(listOf(grant), mapOf(grant.packageName to identity)) { "new" }
            assertEquals("new", result.single().token)
            assertEquals(identity, result.single().identity)
        }
    }
}
