package top.trumeet.mipushframework.main

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.imageResource
import androidx.core.graphics.drawable.toBitmap
import androidx.collection.LruCache

class ApplicationIconCache(context: Context) {
    val context: Context = context.applicationContext

    val defaultAppIcon by lazy {
        BitmapPainter(
            ImageBitmap.imageResource(
                context.resources, android.R.mipmap.sym_def_app_icon
            )
        )
    }
    // The settings UI can enumerate hundreds of packages. Keep only a bounded
    // working set so visiting the app list cannot retain every decoded icon.
    private val iconCache = LruCache<String, Painter>(48)

    fun get(packageName: String): Painter? {
        return iconCache.get(packageName)
    }

    fun cache(packageName: String): Painter {
        val icon = getAppIcon(packageName)
        iconCache.put(packageName, icon)
        return icon
    }

    fun trimMemory(level: Int) {
        when {
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ->
                iconCache.evictAll()
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ->
                iconCache.trimToSize(24)
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE ->
                iconCache.trimToSize(12)
        }
    }

    fun clearMemory() {
        iconCache.evictAll()
    }

    private fun getAppIcon(packageName: String): BitmapPainter {
        try {
            val applicationIcon = context.packageManager.getApplicationIcon(packageName)
            return BitmapPainter(applicationIcon.toBitmap().asImageBitmap())
        } catch (_: PackageManager.NameNotFoundException) {
            return defaultAppIcon
        }
    }

}
