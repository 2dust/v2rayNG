package com.v2ray.npv

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.npv.crsgw.databinding.NpvActivityRenewBinding
import com.npv.crsgw.store.UserStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NpvRenewMembershipActivity : NpvBaseActivity() {
    private val TAG = NpvRenewMembershipActivity::class.simpleName

    private val binding by lazy { NpvActivityRenewBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

    }
}