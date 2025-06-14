package com.npv.crsgw.rest.network

import android.util.Log
import com.npv.crsgw.rest.model.NpvUser
import com.npv.crsgw.rest.model.SignInWithEmailRequest
import com.npv.crsgw.rest.repository.NpvRepository
import com.npv.crsgw.store.UserStore
import kotlinx.coroutines.flow.first

object TokenManager {
    private val repo = NpvRepository()

    // private var token: String? = null
    suspend fun getToken(): String? {
        val user = UserStore.getUser()
        if (user != null) {
            return user.accessToken
        }
        return null
    }

    suspend fun refreshToken(): Boolean {
        val user = UserStore.getUser() ?: return false
        val username = user.username
        val password = user.password

        return try {
            val result = repo.login(SignInWithEmailRequest(username, password.toString())).first()
            when (result) {
                is ApiResult.Success -> {
                    val r = result.data
                    val newUser = user.copy(
                        avatar = r.avatar,
                        status = r.status,
                        accessToken = r.accessToken,
                    )
                    UserStore.storeUser(newUser)
                    true
                }

                else -> {
                    Log.e("TokenManager", "Refresh failed: ${(result as? ApiResult.Failure)?.message}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e("TokenManager", "Exception during refresh: $e")
            false
        }
    }

    /*
    suspend fun refreshToken(): Boolean {
        val user = UserStore.getUser()
        if (user == null) {
            return false
        }

        return try {
            repo.login(SignInWithEmailRequest(user.username.toString(), user.password.toString()).first()
                    when (result) {
                        is ApiResult.Success -> {
                            val r = result.data
                            val user = NpvUser(
                                r.avatar,
                                r.username,
                                r.nickname,
                                r.accessToken,
                                r.tokenType,
                                r.status,
                                ""
                            )
                            UserStore.storeUser(user)
                            true
                        }

                        else -> {
                            // ❌ 登录失败，显示错误
                            Log.e("Login", "Login failed: ${result.message}")
                            false
                        }

                        ApiResult.Loading -> {}
                }
        } catch (e: Exception) {
            false
        }
    }
    */
}