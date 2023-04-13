package com.v2ray.ang.ui.bottomsheets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.v2ray.ang.R
import com.v2ray.ang.databinding.BottomsheetProfilesBinding
import com.v2ray.ang.extension.click
import com.v2ray.ang.ui.HiddifyMainSubAdapter
import com.v2ray.ang.util.CallbackUtil

class ProfilesBottomSheets : BaseExpandedBottomSheet() {
    private lateinit var binding: BottomsheetProfilesBinding
    private lateinit var subAdapter : HiddifyMainSubAdapter

    interface Callback {
        fun onAddProfile()
        fun onImportQrCode()
        fun onSelectSub(subPosition: Int)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NORMAL, R.style.DialogCurveStyle)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        inflater.inflate(R.layout.bottomsheet_profiles, container, false)
        binding = BottomsheetProfilesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        subAdapter = HiddifyMainSubAdapter(requireContext()) { subPosition ->
            callback()?.onSelectSub(subPosition)
            dismiss()
        }

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = subAdapter

        binding.addProfile.click {
            callback()?.onAddProfile()
            dismiss()
        }

        binding.scanQrCode.click {
            callback()?.onImportQrCode()
            dismiss()
        }
    }

    private fun callback(): Callback? {
        return CallbackUtil.getCallback(this, Callback::class.java)
    }

    companion object {
        fun newInstance(): ProfilesBottomSheets {
            return ProfilesBottomSheets()
        }
    }
}
