package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityLogcatBinding
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException


class LogcatActivity : BaseActivity(), SwipeRefreshLayout.OnRefreshListener {
    private val binding by lazy { ActivityLogcatBinding.inflate(layoutInflater) }

    private var logsetsAll: MutableList<String> = mutableListOf()
    var logsets: MutableList<String> = mutableListOf()
    private val adapter by lazy { LogcatRecyclerAdapter(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        title = getString(R.string.title_logcat)

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        addCustomDividerToRecyclerView(binding.recyclerView, this, R.drawable.custom_divider)
        binding.recyclerView.adapter = adapter

        binding.refreshLayout.setOnRefreshListener(this)

        logsets.add(getString(R.string.pull_down_to_refresh))
    }

    private fun getLogcat() {

        try {
            binding.refreshLayout.isRefreshing = true

            lifecycleScope.launch(Dispatchers.Default) {
                val lst = LinkedHashSet<String>()
                lst.add("logcat")
                lst.add("-d")
                lst.add("-v")
                lst.add("time")
                lst.add("-s")
                lst.add("GoLog,tun2socks,${ANG_PACKAGE},AndroidRuntime,System.err")
                val process = withContext(Dispatchers.IO) {
                    Runtime.getRuntime().exec(lst.toTypedArray())
                }

                val allText = process.inputStream.bufferedReader().use { it.readLines() }.reversed()
                launch(Dispatchers.Main) {
                    logsetsAll = allText.toMutableList()
                    logsets = allText.toMutableList()
                    refreshData()
                    binding.refreshLayout.isRefreshing = false
                }
            }
        } catch (e: IOException) {
            Log.e(AppConfig.TAG, "Failed to get logcat", e)
        }
    }

    private fun clearLogcat() {
        try {
            lifecycleScope.launch(Dispatchers.Default) {
                val lst = LinkedHashSet<String>()
                lst.add("logcat")
                lst.add("-c")
                withContext(Dispatchers.IO) {
                    val process = Runtime.getRuntime().exec(lst.toTypedArray())
                    process.waitFor()
                }
                launch(Dispatchers.Main) {
                    logsetsAll.clear()
                    logsets.clear()
                    refreshData()
                }
            }
        } catch (e: IOException) {
            Log.e(AppConfig.TAG, "Failed to clear logcat", e)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_logcat, menu)

        val searchItem = menu.findItem(R.id.search_view)
        if (searchItem != null) {
            val searchView = searchItem.actionView as SearchView
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean = false

                override fun onQueryTextChange(newText: String?): Boolean {
                    filterLogs(newText)
                    return false
                }
            })
            searchView.setOnCloseListener {
                filterLogs("")
                false
            }
        }

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.copy_all -> {
            Utils.setClipboard(this, logsets.joinToString("\n"))
            toastSuccess(R.string.toast_success)
            true
        }

        R.id.clear_all -> {
            clearLogcat()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun filterLogs(content: String?): Boolean {
        val key = content?.trim()
        logsets = if (key.isNullOrEmpty()) {
            logsetsAll.toMutableList()
        } else {
            logsetsAll.filter { it.contains(key) }.toMutableList()
        }

        refreshData()
        return true
    }

    override fun onRefresh() {
        getLogcat()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun refreshData() {
        adapter.notifyDataSetChanged()
    }
}