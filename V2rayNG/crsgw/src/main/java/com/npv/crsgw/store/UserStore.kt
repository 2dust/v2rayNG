package com.npv.crsgw.store

import android.content.Context
import com.google.gson.Gson
import com.npv.crsgw.rest.model.NpvUser
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/*
// Application 中初始化
UserStore.init(applicationContext)

// 登录成功后保存用户信息
UserStore.storeUser(user)

// 获取用户信息
val user = UserStore.getUser()

// 退出登录时清除
UserStore.clear()
*/

object UserStore {
    private const val USER_PREFERENCES_NAME = "user_prefs"
    private val Context.dataStore by preferencesDataStore(name = USER_PREFERENCES_NAME)
    private val USER_INFO_KEY = stringPreferencesKey("user_info")

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    suspend fun isLoggedIn(): Boolean {
        val user = getUser()
        return user != null
    }

    suspend fun storeUser(user: NpvUser) {
        appContext.dataStore.edit { prefs ->
            prefs[USER_INFO_KEY] = Gson().toJson(user)
        }
    }

    suspend fun getUser(): NpvUser? {
        val json = appContext.dataStore.data.first()[USER_INFO_KEY]
        return json?.let { Gson().fromJson(it, NpvUser::class.java) }
    }

    suspend fun clear() {
        appContext.dataStore.edit { it.clear() }
    }
}