package com.npv.crsgw.rest.network

import com.npv.crsgw.store.UserStore

object TokenManager {
    // private var token: String? = null
    suspend fun getToken(): String? {
        val user = UserStore.getUser()
        if (user != null) {
            return user.accessToken
        }
        return null
    }
}