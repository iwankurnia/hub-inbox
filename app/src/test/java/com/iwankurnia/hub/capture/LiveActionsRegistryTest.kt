package com.iwankurnia.hub.capture

import android.app.ActivityOptions
import android.app.Application
import android.app.PendingIntent
import android.os.Bundle
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iwankurnia.hub.capture.TestNotifications.builder
import com.iwankurnia.hub.capture.TestNotifications.sbn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class LiveActionsRegistryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val registry = LiveActionsRegistry(context)
    private val received = mutableListOf<Intent>()

    /** Stands in for the posting app, so sent intents have somewhere to land. */
    private fun listenFor(vararg actions: String) {
        val filter = IntentFilter().apply { actions.forEach(::addAction) }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                received += intent
            }
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    @Test
    fun openContent_firesContentIntent_untilUnregistered() {
        val chat = sbn(builder(context).setContentTitle("Anna").setContentIntent(TestNotifications.pendingIntent(context, "open")).build())
        registry.register(chat)
        listenFor("open")

        assertTrue(registry.openContent(chat.key))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("open"), received.map { it.action })

        registry.unregister(chat.key)
        assertFalse(registry.openContent(chat.key))
    }

    @Test
    fun openContent_withoutContentIntent_returnsFalse() {
        val plain = sbn(builder(context).setContentTitle("Anna").build())
        registry.register(plain)

        assertFalse(registry.openContent(plain.key))
        assertFalse(registry.openContent("unknown|key"))
    }

    @Test
    fun register_repostWithoutContentIntent_dropsTheOldOne() {
        val withIntent = sbn(builder(context).setContentTitle("Anna").setContentIntent(TestNotifications.pendingIntent(context, "open")).build())
        val without = sbn(builder(context).setContentTitle("Anna").build())
        registry.register(withIntent)

        registry.register(without)

        assertFalse(registry.openContent(withIntent.key))
    }

    @Test
    fun register_beyondTheCap_evictsTheLeastRecentlyUsed() {
        val intent = TestNotifications.pendingIntent(context, "open")
        val keys = (1..LiveActionsRegistry.MAX_ENTRIES + 1).map { id ->
            sbn(builder(context).setContentTitle("n$id").setContentIntent(intent).build(), id = id)
                .also(registry::register).key
        }

        assertFalse(registry.openContent(keys.first()))
        assertTrue(registry.openContent(keys[1]))
        assertTrue(registry.openContent(keys.last()))
    }

    @Test
    fun cancel_usesTheListenersCanceller() {
        val cancelled = mutableListOf<String>()
        registry.cancel("before|connect")
        registry.canceller = { cancelled += it }

        registry.cancel("k1")

        assertEquals(listOf("k1"), cancelled)
    }

    @Test
    @Config(sdk = [35])
    fun openContent_onApi34_passesBackgroundActivityStartOptions() {
        val open = activityIntent("open")
        val chat = sbn(builder(context).setContentTitle("Anna").setContentIntent(open).build())
        registry.register(chat)
        val started = shadowOf(context as Application)

        assertTrue(registry.openContent(chat.key))
        assertAllowsBackgroundStart(started.nextStartedActivityForResult.options)
    }

    private fun activityIntent(action: String): PendingIntent = PendingIntent.getActivity(
        context,
        action.hashCode(),
        Intent(action).setClassName(context, "com.iwankurnia.hub.MainActivity"),
        PendingIntent.FLAG_IMMUTABLE,
    )

    /** The send options carry every key that the background-start mode adds to a basic options bundle. */
    private fun assertAllowsBackgroundStart(options: Bundle?) {
        assertNotNull("send options", options)
        val basic = ActivityOptions.makeBasic().toBundle()
        val allowed = ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            .toBundle()
        val modeKeys = allowed.keySet() - basic.keySet()
        assertTrue("the mode adds a key", modeKeys.isNotEmpty())
        modeKeys.forEach { key ->
            @Suppress("DEPRECATION")
            assertEquals(key, allowed.get(key), options!!.get(key))
        }
    }
}
