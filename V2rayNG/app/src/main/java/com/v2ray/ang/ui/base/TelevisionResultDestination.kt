package com.v2ray.ang.ui.base

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration

/** Keep the original caller as the recipient of the TV editor or picker's result. */
internal fun Activity.televisionResultDestination(target: Class<out Activity>): Intent? {
    val uiMode = resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    return if (uiMode == Configuration.UI_MODE_TYPE_TELEVISION) {
        Intent(intent).setClass(this, target).addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
    } else null
}
