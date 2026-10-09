package com.v2ray.ang.ui.xfinder

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

class XfinderLogViewModel : ViewModel() {
    val lines: StateFlow<List<String>> = XfinderLogStore.lines

    fun clear() = XfinderLogStore.clear()
}
