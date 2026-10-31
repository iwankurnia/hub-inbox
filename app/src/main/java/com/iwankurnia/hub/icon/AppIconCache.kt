package com.iwankurnia.hub.icon

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** App labels and launcher icons, cached so list rows and widgets do not hit the package manager each time. */
@Singleton
class AppIconCache @Inject constructor(@ApplicationContext ctx: Context) {

    private val packageManager = ctx.packageManager
    private val icons = LruCache<String, Drawable>(MAX_ICONS)
    private val labels = LruCache<String, String>(MAX_LABELS)

    /** The app's label, or [packageName] when the app is not installed; cached, as rows ask for it while composing. */
    fun label(packageName: String): String {
        labels.get(packageName)?.let { return it }
        val label = try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
        labels.put(packageName, label)
        return label
    }

    /** The app's icon, or null when the app is not installed. */
    fun icon(packageName: String): Drawable? {
        icons.get(packageName)?.let { return it }
        return try {
            packageManager.getApplicationIcon(packageName).also { icons.put(packageName, it) }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    /** The icon drawn into a square bitmap of [sizePx], for widgets. */
    fun iconBitmap(packageName: String, sizePx: Int): Bitmap? = icon(packageName)?.toBitmap(sizePx, sizePx)

    private companion object {
        const val MAX_ICONS = 64
        const val MAX_LABELS = 64
    }
}
