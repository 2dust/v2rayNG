package com.v2ray.ang.ui

import android.content.Intent
import android.view.Menu
import android.view.MenuItem
import com.v2ray.ang.R
import kotlinx.android.synthetic.main.activity_sub_setting.*
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import com.v2ray.ang.dto.SubscriptionItem
import com.v2ray.ang.util.MmkvManager

class SubSettingActivity : BaseActivity() {

    var subscriptions:List<Pair<String, SubscriptionItem>> = listOf()
    private val adapter by lazy { SubSettingRecyclerAdapter(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sub_setting)

        title = getString(R.string.title_sub_setting)

        recycler_view.setHasFixedSize(true)
        recycler_view.layoutManager = LinearLayoutManager(this)
        recycler_view.adapter = adapter

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onResume() {
        super.onResume()
        subscriptions = MmkvManager.decodeSubscriptions()
        adapter.notifyDataSetChanged()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.action_sub_setting, menu)
        menu?.findItem(R.id.del_config)?.isVisible = false
        menu?.findItem(R.id.save_config)?.isVisible = false

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.add_config -> {
            startActivity(Intent(this, SubEditActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
