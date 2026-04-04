package com.v2ray.ang.ui

import android.os.Bundle
import android.view.MenuItem
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityLocationsBinding

class LocationsActivity : BaseActivity() {
    private val binding by lazy {
        ActivityLocationsBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "LOCATIONS"

        // Reutiliza o fragmento que já tem a lógica de listagem e seleção
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, GroupServerFragment.newInstance(""))
                .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
