package com.v2ray.ang.util

import android.content.Context
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import com.v2ray.ang.AppConfig

class AppIconFetcher(
    private val packageName: String,
    private val context: Context
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val drawable = try {
            if (packageName == AppConfig.UNIDENTIFIED_PACKAGE) {
                context.getDrawable(android.R.drawable.ic_menu_help)
                    ?: context.getDrawable(android.R.drawable.sym_def_app_icon)
            } else {
                context.packageManager.getApplicationIcon(packageName)
            }
        } catch (_: Exception) {
            null
        } ?: return null

        return ImageFetchResult(
            image = drawable.asImage(),
            isSampled = false,
            dataSource = DataSource.DISK
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<Any> {
        override fun create(data: Any, options: Options, imageLoader: ImageLoader): Fetcher? {
            val dataString = data.toString()

            if (dataString.startsWith("appicon:")) {
                val pkg = dataString.substringAfter("appicon:")
                return AppIconFetcher(pkg, context)
            }
            return null
        }
    }
}
