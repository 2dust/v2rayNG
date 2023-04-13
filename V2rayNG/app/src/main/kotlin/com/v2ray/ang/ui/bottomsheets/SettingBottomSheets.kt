package com.v2ray.ang.ui.bottomsheets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.v2ray.ang.R
import com.v2ray.ang.databinding.BottomsheetSettingBinding
import com.v2ray.ang.util.CallbackUtil

class SettingBottomSheets : BaseExpandedBottomSheet() {
    private lateinit var binding: BottomsheetSettingBinding

    interface Callback {
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NORMAL, R.style.DialogCurveStyle)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        inflater.inflate(R.layout.bottomsheet_setting, container, false)
        binding = BottomsheetSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    private fun callback(): Callback? {
        return CallbackUtil.getCallback(this, Callback::class.java)
    }

    companion object {
        fun newInstance(): SettingBottomSheets {
            return SettingBottomSheets()
        }
    }
}
