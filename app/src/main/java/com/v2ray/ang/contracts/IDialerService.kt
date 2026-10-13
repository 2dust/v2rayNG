package com.v2ray.ang.contracts

import android.content.Context

interface IDialerService {
    fun start(context: Context, dialerAddr: String)
    fun stop()
}