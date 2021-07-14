package com.v2ray.ang.ui

import android.graphics.Color
import android.os.Bundle
import com.v2ray.ang.R
import androidx.fragment.app.Fragment
import com.v2ray.ang.AppConfig
import kotlinx.android.synthetic.main.activity_routing_settings.*


class RoutingSettingsActivity : BaseActivity() {
    private val titles: Array<out String> by lazy {
        resources.getStringArray(R.array.routing_tag)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_routing_settings)

        title = getString(R.string.routing_settings_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val fragments = ArrayList<Fragment>()
        fragments.add(RoutingSettingsFragment().newInstance(AppConfig.PREF_V2RAY_ROUTING_AGENT))
        fragments.add(RoutingSettingsFragment().newInstance(AppConfig.PREF_V2RAY_ROUTING_DIRECT))
        fragments.add(RoutingSettingsFragment().newInstance(AppConfig.PREF_V2RAY_ROUTING_BLOCKED))

        val adapter = FragmentAdapter(supportFragmentManager, fragments, titles.toList())
        viewpager?.adapter = adapter
        tablayout.setTabTextColors(Color.BLACK, Color.RED)
        tablayout.setupWithViewPager(viewpager)
    }
}
