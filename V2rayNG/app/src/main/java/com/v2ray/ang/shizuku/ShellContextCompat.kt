package com.v2ray.ang.shizuku

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper

/** Creates a context whose Binder attribution matches the Shizuku shell UID. */
internal object ShellContextCompat {

    // Android has no public API for changing a Context's Binder attribution package. The
    // UserService runs as UID 2000, so framework calls must identify com.android.shell.
    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    fun create(context: Context): Context {
        val baseContext = (context as? ContextWrapper)?.baseContext ?: context
        val contextImplClass = Class.forName("android.app.ContextImpl")
        val activityThreadClass = Class.forName("android.app.ActivityThread")
        val loadedApkClass = Class.forName("android.app.LoadedApk")
        val mainThread = contextImplClass.getDeclaredField("mMainThread").run {
            isAccessible = true
            get(baseContext)
        }
        val systemContext = activityThreadClass.getDeclaredMethod("getSystemContext")
            .invoke(mainThread) as Context
        val packageContext = systemContext.createPackageContext(
            SHELL_PACKAGE_NAME,
            Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
        )
        val loadedApk = contextImplClass.getDeclaredField("mPackageInfo").run {
            isAccessible = true
            get(packageContext)
        }
        val createAppContext = contextImplClass.declaredMethods.firstOrNull {
            it.name == "createAppContext" && it.parameterTypes.contentEquals(
                arrayOf(activityThreadClass, loadedApkClass, String::class.java),
            )
        } ?: contextImplClass.getDeclaredMethod(
            "createAppContext",
            activityThreadClass,
            loadedApkClass,
        )
        createAppContext.isAccessible = true

        return if (createAppContext.parameterCount == 3) {
            createAppContext.invoke(null, mainThread, loadedApk, SHELL_PACKAGE_NAME) as Context
        } else {
            createAppContext.invoke(null, mainThread, loadedApk) as Context
        }
    }

    private const val SHELL_PACKAGE_NAME = "com.android.shell"
}
