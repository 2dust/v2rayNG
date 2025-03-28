package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivitySubSettingBinding
import com.v2ray.ang.dto.SubscriptionItem
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.helper.SimpleItemTouchHelperCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SubSettingActivity : BaseActivity() {
    private val binding by lazy { ActivitySubSettingBinding.inflate(layoutInflater) }

    var subscriptions: List<Pair<String, SubscriptionItem>> = listOf()
    private val adapter by lazy { SubSettingRecyclerAdapter(this) }
    private var mItemTouchHelper: ItemTouchHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        title = getString(R.string.title_sub_setting)

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        addCustomDividerToRecyclerView(binding.recyclerView, this, R.drawable.custom_divider)
        binding.recyclerView.adapter = adapter

        mItemTouchHelper = ItemTouchHelper(SimpleItemTouchHelperCallback(adapter))
        mItemTouchHelper?.attachToRecyclerView(binding.recyclerView)
    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.action_sub_setting, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.add_config -> {
            startActivity(Intent(this, SubEditActivity::class.java))
            true
        }

        R.id.sub_update -> {
            binding.pbWaiting.show()

            lifecycleScope.launch(Dispatchers.IO) {
                val count = AngConfigManager.updateConfigViaSubAll()
                delay(500L)
                launch(Dispatchers.Main) {
                    if (count > 0) {
                        toastSuccess(R.string.toast_success)
                    } else {
                        toastError(R.string.toast_failure)
                    }
                    binding.pbWaiting.hide()
                }
            }

            true
        }

        else -> super.onOptionsItemSelected(item)

    }

    @SuppressLint("NotifyDataSetChanged")
    fun refreshData() {
        subscriptions = MmkvManager.decodeSubscriptions()
        adapter.notifyDataSetChanged()
    }
}
