package com.v2ray.ang.ui.bottomsheets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.v2ray.ang.R
import com.v2ray.ang.databinding.BottomsheetAddConfigBinding
import com.v2ray.ang.extension.click
import com.v2ray.ang.util.CallbackUtil


class AddConfigBottomSheets : BaseExpandedBottomSheet() {
    private lateinit var binding: BottomsheetAddConfigBinding

    interface Callback {
        fun onClipBoard()
        fun onQrCode()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NORMAL, R.style.DialogCurveStyle)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        inflater.inflate(R.layout.bottomsheet_add_config, container, false)
        binding = BottomsheetAddConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.importFromClipBoard.click {
            callback()?.onClipBoard()
            dismiss()
        }

        binding.scanQrCode.click {
            callback()?.onQrCode()
            dismiss()
        }
    }

    private fun callback(): Callback? {
        return CallbackUtil.getCallback(this, Callback::class.java)
    }

    companion object {
        fun newInstance(): AddConfigBottomSheets {
            return AddConfigBottomSheets()
        }
    }
}
