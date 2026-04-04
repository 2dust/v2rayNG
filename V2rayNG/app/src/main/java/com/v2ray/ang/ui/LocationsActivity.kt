package com.v2ray.ang.ui

import android.os.Bundle
import android.view.MenuItem
import android.view.animation.AnimationUtils
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityLocationsBinding

/**
 * LocationsActivity para Simpsons VPN
 * Redesenhada com estilo Neobrutalista e animações de nuvens.
 */
class LocationsActivity : HelperBaseActivity() {
    private val binding by lazy {
        ActivityLocationsBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        // Ocultar Toolbar original
        setSupportActionBar(null)

        // Iniciar Animações de Nuvens
        startCloudAnimations()

        // Botão de fechar (X)
        binding.btnClose.setOnClickListener {
            finish()
        }

        // Reutiliza o fragmento que já tem a lógica de listagem e seleção
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, GroupServerFragment.newInstance(""))
                .commit()
        }
    }

    private fun startCloudAnimations() {
        val cloudAnim = AnimationUtils.loadAnimation(this, R.anim.cloud_float)
        binding.cloud1.startAnimation(cloudAnim)
        
        val cloudAnim2 = AnimationUtils.loadAnimation(this, R.anim.cloud_float)
        cloudAnim2.startOffset = 5000
        binding.cloud2.startAnimation(cloudAnim2)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // Simpsons VPN: O restartV2Ray e importConfigViaSub agora são desacoplados
    // para evitar crashes de casting e garantir estabilidade total.
}
