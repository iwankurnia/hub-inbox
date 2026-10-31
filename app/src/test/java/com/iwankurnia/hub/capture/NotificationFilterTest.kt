package com.iwankurnia.hub.capture

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iwankurnia.hub.capture.TestNotifications.builder
import com.iwankurnia.hub.capture.TestNotifications.sbn
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationFilterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val filter = NotificationFilter(ownPackage = "com.iwankurnia.hub")

    private fun plain() = builder(context).setContentTitle("Matthew").setContentText("Lunch?")

    private fun drops(n: Notification, pkg: String = TestNotifications.PKG, importance: Int? = null, hideSilent: Boolean = true) =
        filter.shouldDrop(sbn(n, pkg = pkg), importance, hideSilent)

    @Test
    fun filter_dropsOwnPackage() {
        assertTrue(drops(plain().build(), pkg = "com.iwankurnia.hub"))
        assertFalse(drops(plain().build()))
    }

    @Test
    fun filter_dropsOngoingAndSummary() {
        assertTrue(drops(plain().setOngoing(true).build()))
        assertTrue(drops(plain().setGroup("g").setGroupSummary(true).build()))
        val foreground = plain().build().apply { flags = flags or Notification.FLAG_FOREGROUND_SERVICE }
        assertTrue(drops(foreground))
        assertFalse(drops(plain().setGroup("g").build()))
    }

    @Test
    fun filter_dropsMediaAndProgress() {
        assertTrue(drops(plain().setCategory(NotificationCompat.CATEGORY_TRANSPORT).build()))
        assertTrue(drops(plain().setCategory(NotificationCompat.CATEGORY_PROGRESS).build()))
        assertTrue(drops(plain().setProgress(100, 40, false).build()))
        assertFalse(drops(plain().setCategory(NotificationCompat.CATEGORY_MESSAGE).build()))
    }

    @Test
    fun filter_dropsLowImportanceOnlyWhenHideSilent() {
        val n = plain().build()
        assertTrue(drops(n, importance = NotificationManager.IMPORTANCE_LOW, hideSilent = true))
        assertTrue(drops(n, importance = NotificationManager.IMPORTANCE_MIN, hideSilent = true))
        assertFalse(drops(n, importance = NotificationManager.IMPORTANCE_LOW, hideSilent = false))
        assertFalse(drops(n, importance = NotificationManager.IMPORTANCE_DEFAULT, hideSilent = true))
        assertFalse(drops(n, importance = null, hideSilent = true))
    }
}
