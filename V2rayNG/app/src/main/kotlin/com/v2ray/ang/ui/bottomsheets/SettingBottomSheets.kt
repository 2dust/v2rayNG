package com.v2ray.ang.ui.bottomsheets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.v2ray.ang.R
import com.v2ray.ang.databinding.BottomsheetSettingBinding
import com.v2ray.ang.util.CallbackUtil

class SettingBottomSheets(var mode: Int) : BaseExpandedBottomSheet() {
    public lateinit var binding: BottomsheetSettingBinding

    interface Callback {
        fun onModeChange(mode: Int)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NORMAL, R.style.DialogCurveStyle)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        inflater.inflate(R.layout.bottomsheet_setting, container, false)
        binding = BottomsheetSettingBinding.inflate(inflater, container, false)
        val checkId = when (mode) {
            1 -> binding.smart.id
            2 -> binding.loadBalance.id
            else -> binding.manual.id
        }
        binding.connectToggleButton.check(checkId)
        binding.connectToggleButton.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                mode = when (checkedId) {
                    binding.smart.id ->1
                    binding.loadBalance.id ->2
                    else ->3
                }
                callback()?.onModeChange(mode)
            }else if (group.checkedButtonIds.isEmpty()) {
                // No buttons are selected, select the first one by default
                val checkId = when (mode) {
                    1 -> binding.smart.id
                    2 -> binding.loadBalance.id
                    else -> binding.manual.id
                }
                group.check(checkId)
            }
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    private fun callback(): Callback? {
        return CallbackUtil.getCallback(this, Callback::class.java)
    }

    companion object {
        fun newInstance(mode: Int): SettingBottomSheets {
            return SettingBottomSheets(mode)
        }
    }
}
