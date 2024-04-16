package com.v2ray.ang.service

import android.annotation.SuppressLint
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.MessageUtil

object AutoTestConnectWorker {

    class UpdateTask(context: Context, params: WorkerParameters) :
        CoroutineWorker(context, params) {


        @SuppressLint("MissingPermission")
        override suspend fun doWork(): Result {
            MessageUtil.sendMsg2Service(applicationContext, AppConfig.MSG_MEASURE_DELAY, true)
            return Result.success()
        }
    }
}