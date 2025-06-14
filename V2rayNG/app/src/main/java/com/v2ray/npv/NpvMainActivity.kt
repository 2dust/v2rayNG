package com.v2ray.npv

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import com.npv.crsgw.databinding.NpvActivityMainBinding
import com.npv.crsgw.rest.model.GetHomeDataItemResponse
import com.npv.crsgw.store.UserStore
import com.npv.crsgw.ui.UserViewModel
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.ui.AboutActivity
import com.v2ray.ang.ui.CheckUpdateActivity
import com.v2ray.ang.ui.LogcatActivity
import com.v2ray.ang.ui.PerAppProxyActivity
import com.v2ray.ang.ui.UserAssetActivity
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class NpvMainActivity : NpvBaseActivity(), NavigationView.OnNavigationItemSelectedListener {
    private val TAG = NpvMainActivity::class.simpleName

    private val binding by lazy { NpvActivityMainBinding.inflate(layoutInflater) }

    private val viewModel: UserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        val navView = binding.navView
        val headerView = navView.getHeaderView(0)
        val userNameView = headerView.findViewById<TextView>(com.npv.crsgw.R.id.user_name)
        val expireTime = headerView.findViewById<TextView>(com.npv.crsgw.R.id.subscription_expire_time)

        lifecycleScope.launch {
            val email = UserStore.getUser()?.username
            userNameView.text = email

            // showLoading()

            viewModel.getHomeData(email.toString())
        }

        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        binding.navView.setNavigationItemSelectedListener(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        observeState(
            viewModel.userState,
            onLoading = {
                showLoading()
                        },
            onSuccess = {
                hideLoading()
                // progressBar.visibility = View.GONE
                // textView.text = it.name
                userNameView.text = it.email
                val t = formatExpireDate(it)
                expireTime.text = t
                Log.i(TAG, "get home data successfully: ${it.expireAt}")
            },
            onFailure = { _, msg ->
                hideLoading()

                Log.e(TAG, "Failed to get home data: $msg")
            }
        )
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        when (item.itemId) {
            R.id.per_app_proxy_settings -> startActivity(Intent(this, PerAppProxyActivity::class.java))
            R.id.user_asset_setting -> startActivity(Intent(this, UserAssetActivity::class.java))
            R.id.promotion -> Utils.openUri(this, "${Utils.decode(AppConfig.APP_PROMOTION_URL)}?t=${System.currentTimeMillis()}")
            R.id.logcat -> startActivity(Intent(this, LogcatActivity::class.java))
            R.id.check_for_update -> startActivity(Intent(this, CheckUpdateActivity::class.java))
            R.id.about -> startActivity(Intent(this, AboutActivity::class.java))
        }

        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    fun formatExpireDate(homeData: GetHomeDataItemResponse): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        var result: String? = null
        try {
            LocalDateTime.parse(homeData.expireAt, formatter)
            result = homeData.expireAt
        } catch (e: Exception) {
            Log.e(TAG, "Format expire date failed: $e")
        }

        if (result.isNullOrEmpty()) {
            result = getString(com.npv.crsgw.R.string.npv_purchase_membership)
        } else {
            result = getString(com.npv.crsgw.R.string.npv_membership_status_normal, result)
        }
        return result
    }
}