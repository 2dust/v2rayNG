package com.v2ray.ang.viewmodel

import android.app.Application
import androidx.lifecycle.MutableLiveData

class HiddifyMainViewModel(application: Application) : MainViewModel(application) {
    val subscriptionsAdded by lazy { MutableLiveData<Boolean>() }

    fun subscriptionsAddedCheck() {
        subscriptionsAdded.value = subscriptions.isNotEmpty()
    }

    fun reloadSubscriptionsState() {
        subscriptionsAddedCheck()
    }
}
