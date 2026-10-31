package com.iwankurnia.hub

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScaffoldTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun launcherIntent_resolvesMainActivity() {
        val intent = context.packageManager.getLaunchIntentForPackage("com.iwankurnia.hub")

        assertNotNull("no launcher intent for com.iwankurnia.hub", intent)
        assertEquals(MainActivity::class.java.name, intent!!.component?.className)
    }

    @Test
    fun hubIntents_open_setsExtras() {
        val intent = HubIntents.open(context, tab = "UNREAD", packageName = "x", itemId = 7L)

        assertEquals("UNREAD", intent.getStringExtra(HubIntents.EXTRA_TAB))
        assertEquals("x", intent.getStringExtra(HubIntents.EXTRA_PACKAGE))
        assertEquals(7L, intent.getLongExtra(HubIntents.EXTRA_ITEM_ID, -1L))
        assertEquals(MainActivity::class.java.name, intent.component?.className)
    }
}
