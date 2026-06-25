package com.v2ray.ang.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import com.v2ray.ang.R
import com.v2ray.ang.databinding.DialogConfigImportGalaxyTunnelBinding

class ConfigImportDialogGalaxyTunnel(
    context: Context,
    private val onImport: (String) -> Unit
) : Dialog(context) {

    private lateinit var binding: DialogConfigImportGalaxyTunnelBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogConfigImportGalaxyTunnelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        binding.btnCancel.setOnClickListener { dismiss() }

        binding.btnImport.setOnClickListener {
            val link = binding.etConfigLink.text.toString().trim()
            if (link.isNotEmpty() && 
                (link.startsWith("vless://") || 
                 link.startsWith("trojan://") || 
                 link.startsWith("vmess://"))) {
                onImport(link)
                dismiss()
            } else {
                Toast.makeText(context, R.string.gt_config_import_error, Toast.LENGTH_SHORT).show()
            }
        }
    }
}