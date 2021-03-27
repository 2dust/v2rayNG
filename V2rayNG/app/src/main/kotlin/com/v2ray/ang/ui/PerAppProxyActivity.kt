package com.v2ray.ang.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.os.Bundle
import android.support.v7.preference.PreferenceManager
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import com.v2ray.ang.R
import com.v2ray.ang.util.AppManagerUtil
import kotlinx.android.synthetic.main.activity_bypass_list.*
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.text.Collator
import java.util.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.v2RayApplication
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.net.URL

class PerAppProxyActivity : BaseActivity() {

    private var adapter: PerAppProxyAdapter? = null
    private var appsAll: List<AppInfo>? = null
    private val defaultSharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bypass_list)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val dividerItemDecoration = DividerItemDecoration(this, LinearLayoutManager.VERTICAL)
        recycler_view.addItemDecoration(dividerItemDecoration)

        val blacklist = defaultSharedPreferences.getStringSet(AppConfig.PREF_PER_APP_PROXY_SET, null)

        AppManagerUtil.rxLoadNetworkAppList(this)
                .subscribeOn(Schedulers.io())
                .map {
                    if (blacklist != null) {
                        it.forEach { one ->
                            if ((blacklist.contains(one.packageName))) {
                                one.isSelected = 1
                            } else {
                                one.isSelected = 0
                            }
                        }
                        val comparator = object : Comparator<AppInfo> {
                            override fun compare(p1: AppInfo, p2: AppInfo): Int = when {
                                p1.isSelected > p2.isSelected -> -1
                                p1.isSelected == p2.isSelected -> 0
                                else -> 1
                            }
                        }
                        it.sortedWith(comparator)
                    } else {
                        val comparator = object : Comparator<AppInfo> {
                            val collator = Collator.getInstance()
                            override fun compare(o1: AppInfo, o2: AppInfo) = collator.compare(o1.appName, o2.appName)
                        }
                        it.sortedWith(comparator)
                    }
                }
//                .map {
//                    val comparator = object : Comparator<AppInfo> {
//                        val collator = Collator.getInstance()
//                        override fun compare(o1: AppInfo, o2: AppInfo) = collator.compare(o1.appName, o2.appName)
//                    }
//                    it.sortedWith(comparator)
//                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    appsAll = it
                    adapter = PerAppProxyAdapter(this, it, blacklist)
                    recycler_view.adapter = adapter
                    pb_waiting.visibility = View.GONE
                }

        recycler_view.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            var dst = 0
            val threshold = resources.getDimensionPixelSize(R.dimen.bypass_list_header_height) * 3
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                dst += dy
                if (dst > threshold) {
                    header_view.hide()
                    dst = 0
                } else if (dst < -20) {
                    header_view.show()
                    dst = 0
                }
            }

            var hiding = false
            fun View.hide() {
                val target = -height.toFloat()
                if (hiding || translationY == target) return
                animate()
                        .translationY(target)
                        .setInterpolator(AccelerateInterpolator(2F))
                        .setListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator?) {
                                hiding = false
                            }
                        })
                hiding = true
            }

            var showing = false
            fun View.show() {
                val target = 0f
                if (showing || translationY == target) return
                animate()
                        .translationY(target)
                        .setInterpolator(DecelerateInterpolator(2F))
                        .setListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator?) {
                                showing = false
                            }
                        })
                showing = true
            }
        })

        switch_per_app_proxy.setOnCheckedChangeListener { _, isChecked ->
            defaultSharedPreferences.edit().putBoolean(AppConfig.PREF_PER_APP_PROXY, isChecked).apply()
        }
        switch_per_app_proxy.isChecked = defaultSharedPreferences.getBoolean(AppConfig.PREF_PER_APP_PROXY, false)

        switch_bypass_apps.setOnCheckedChangeListener { _, isChecked ->
            defaultSharedPreferences.edit().putBoolean(AppConfig.PREF_BYPASS_APPS, isChecked).apply()
        }
        switch_bypass_apps.isChecked = defaultSharedPreferences.getBoolean(AppConfig.PREF_BYPASS_APPS, false)

        et_search.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                //hide
                var imm: InputMethodManager = v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.toggleSoftInput(0, InputMethodManager.HIDE_NOT_ALWAYS)

                val key = v.text.toString().toUpperCase()
                val apps = ArrayList<AppInfo>()
                if (TextUtils.isEmpty(key)) {
                    appsAll?.forEach {
                        apps.add(it)
                    }
                } else {
                    appsAll?.forEach {
                        if (it.appName.toUpperCase().indexOf(key) >= 0) {
                            apps.add(it)
                        }
                    }
                }
                adapter = PerAppProxyAdapter(this, apps, adapter?.blacklist)
                recycler_view.adapter = adapter
                adapter?.notifyDataSetChanged()
                true
            } else {
                false
            }
        }
    }

    override fun onPause() {
        super.onPause()
        adapter?.let {
            defaultSharedPreferences.edit().putStringSet(AppConfig.PREF_PER_APP_PROXY_SET, it.blacklist).apply()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_bypass_list, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.select_all -> adapter?.let {
            val pkgNames = it.apps.map { it.packageName }
            if (it.blacklist.containsAll(pkgNames)) {
                it.apps.forEach {
                    val packageName = it.packageName
                    adapter?.blacklist!!.remove(packageName)
                }
            } else {
                it.apps.forEach {
                    val packageName = it.packageName
                    adapter?.blacklist!!.add(packageName)
                }

            }
            it.notifyDataSetChanged()
            true
        } ?: false
        R.id.select_proxy_app -> {
            selectProxyApp()

            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun selectProxyApp() {
        toast(R.string.msg_downloading_content)
        val url = AppConfig.androidpackagenamelistUrl
        GlobalScope.launch(Dispatchers.IO) {
            val content = try {
                URL(url).readText()
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
            launch(Dispatchers.Main) {
                Log.d("selectProxyApp", content)
                selectProxyApp(content)
                toast(R.string.toast_success)
            }
        }
    }

    private fun selectProxyApp(content: String): Boolean {
        try {
            var proxyApps = content
            if (TextUtils.isEmpty(content)) {
                val assets = Utils.readTextFromAssets(v2RayApplication, "proxy_packagename.txt")
                proxyApps = assets.lines().toString()
            }
            if (TextUtils.isEmpty(proxyApps)) {
                return false
            }

            adapter?.blacklist!!.clear()

            if (switch_bypass_apps.isChecked) {
                adapter?.let {
                    it.apps.forEach block@{
                        val packageName = it.packageName
                        Log.d("selectProxyApp2", packageName)
                        if (proxyApps.indexOf(packageName) < 0) {
                            adapter?.blacklist!!.add(packageName)
                            println(packageName)
                            return@block
                        }
                    }
                    it.notifyDataSetChanged()
                }
            } else {
                adapter?.let {
                    it.apps.forEach block@{
                        val packageName = it.packageName
                        Log.d("selectProxyApp3", packageName)
                        if (proxyApps.indexOf(packageName) >= 0) {
                            adapter?.blacklist!!.add(packageName)
                            println(packageName)
                            return@block
                        }
                    }
                    it.notifyDataSetChanged()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }
}
