package com.iwankurnia.hub.icon

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class AppIconCacheTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun install(packageName: String, label: String) {
        val info = PackageInfo().apply {
            this.packageName = packageName
            applicationInfo = ApplicationInfo().apply {
                this.packageName = packageName
                nonLocalizedLabel = label
            }
        }
        shadowOf(context.packageManager).installPackage(info)
    }

    @Test
    fun label_isCachedAfterFirstLookup() {
        install("com.example.chat", "Chat")
        val cache = AppIconCache(context)
        assertEquals("Chat", cache.label("com.example.chat"))

        // A second lookup must not go back to the package manager.
        shadowOf(context.packageManager).deletePackage("com.example.chat")

        assertEquals("Chat", cache.label("com.example.chat"))
        assertEquals("com.example.gone", cache.label("com.example.gone"))
    }
}
