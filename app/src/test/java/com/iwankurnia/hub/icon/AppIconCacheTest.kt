package com.iwankurnia.hub.icon

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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

    @Test
    fun iconBitmap_isCachedPerSize() = runTest {
        install("com.example.chat", "Chat")
        shadowOf(context.packageManager).setApplicationIcon("com.example.chat", ColorDrawable(Color.BLUE))
        val cache = AppIconCache(context)

        assertNull(cache.cachedBitmap("com.example.chat", 48))
        val small = cache.iconBitmapOnMain("com.example.chat", 48)!!
        val large = cache.iconBitmapOnMain("com.example.chat", 96)!!
        assertEquals(48, small.width)
        assertEquals(96, large.width)
        assertNotSame(small, large)

        // Drawn once per size: later lookups, peeked or suspending, return the same bitmap without the package manager.
        shadowOf(context.packageManager).deletePackage("com.example.chat")
        assertSame(small, cache.cachedBitmap("com.example.chat", 48))
        assertSame(large, cache.iconBitmapOnMain("com.example.chat", 96))
        assertNull(cache.iconBitmapOnMain("com.example.gone", 48))
    }
}
