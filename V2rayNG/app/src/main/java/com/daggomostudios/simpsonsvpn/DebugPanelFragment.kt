package com.daggomostudios.simpsonsvpn

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.v2ray.ang.databinding.FragmentDebugPanelBinding
import com.v2ray.ang.ui.MainActivity

class DebugPanelFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentDebugPanelBinding? = null
    private val binding get() = _binding!!
    private val debugViewModel: DebugViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDebugPanelBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        debugViewModel.debugInfo.observe(viewLifecycleOwner) {
            binding.tvDebugStatus.text = "Status: ${it.status}"
            binding.tvDebugHttp.text = "HTTP: ${it.httpStatus}"
            binding.tvDebugDecryption.text = "Decryption: ${it.decryptionStatus}"
            binding.tvDebugJson.text = "JSON Parse: ${it.jsonParseStatus}"
            binding.tvDebugServersLoaded.text = "Servers Loaded: ${it.serversLoaded}"
            binding.tvDebugError.text = "Error: ${it.errorMessage}"
        }

        binding.btnRefreshDebug.setOnClickListener {
            // Disparar o refresh na MainActivity
            (activity as? MainActivity)?.loadVpnServers()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "DebugPanelFragment"
    }
}
