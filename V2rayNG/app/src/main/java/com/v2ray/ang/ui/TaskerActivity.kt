package com.v2ray.ang.ui

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityTaskerBinding
import com.v2ray.ang.handler.MmkvManager

class TaskerActivity : BaseActivity() {
    private val binding by lazy { ActivityTaskerBinding.inflate(layoutInflater) }

    private var listview: ListView? = null
    private var lstData: ArrayList<String> = ArrayList()
    private var lstGuid: ArrayList<String> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        //add def value
        lstData.add("Default")
        lstGuid.add(AppConfig.TASKER_DEFAULT_GUID)

        MmkvManager.decodeServerList()?.forEach { key ->
            MmkvManager.decodeServerConfig(key)?.let { config ->
                lstData.add(config.remarks)
                lstGuid.add(key)
            }
        }
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_single_choice, lstData
        )
        listview = findViewById<View>(R.id.listview) as ListView
        listview?.adapter = adapter

        init()
    }

    private fun init() {
        try {
            val bundle = intent?.getBundleExtra(AppConfig.TASKER_EXTRA_BUNDLE)
            val switch = bundle?.getBoolean(AppConfig.TASKER_EXTRA_BUNDLE_SWITCH, false)
            val guid = bundle?.getString(AppConfig.TASKER_EXTRA_BUNDLE_GUID, "")

            if (switch == null || TextUtils.isEmpty(guid)) {
                return
            } else {
                binding.switchStartService.isChecked = switch
                val pos = lstGuid.indexOf(guid.toString())
                if (pos >= 0) {
                    listview?.setItemChecked(pos, true)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()

        }
    }

    private fun confirmFinish() {
        val position = listview?.checkedItemPosition
        if (position == null || position < 0) {
            return
        }

        val extraBundle = Bundle()
        extraBundle.putBoolean(AppConfig.TASKER_EXTRA_BUNDLE_SWITCH, binding.switchStartService.isChecked)
        extraBundle.putString(AppConfig.TASKER_EXTRA_BUNDLE_GUID, lstGuid[position])
        val intent = Intent()

        val remarks = lstData[position]
        val blurb = if (binding.switchStartService.isChecked) {
            "Start $remarks"
        } else {
            "Stop $remarks"
        }

        intent.putExtra(AppConfig.TASKER_EXTRA_BUNDLE, extraBundle)
        intent.putExtra(AppConfig.TASKER_EXTRA_STRING_BLURB, blurb)
        setResult(RESULT_OK, intent)
        finish()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.action_server, menu)
        val del_config = menu.findItem(R.id.del_config)
        del_config?.isVisible = false
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.del_config -> {
            true
        }

        R.id.save_config -> {
            confirmFinish()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

}

