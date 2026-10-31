package com.iwankurnia.hub.capture

import android.app.ActivityOptions
import android.app.Application
import android.app.PendingIntent
import android.app.RemoteInput
import android.os.Bundle
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import androidx.core.app.NotificationCompat
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
    fun reply_withoutRemoteInput_returnsFalse() {
        val plain = sbn(
            builder(context).setContentTitle("Anna")
                .addAction(TestNotifications.plainAction(context, "Mark as read"))
                .build(),
        )
        registry.register(plain)

        assertFalse(registry.hasReply(plain.key))
        assertFalse(registry.reply(plain.key, "On my way"))
        assertFalse(registry.reply("unknown|key", "On my way"))
    }

    @Test
    fun actions_listsNonReplyActions() {
        val chat = sbn(
            builder(context).setContentTitle("Anna")
                .setContentIntent(TestNotifications.pendingIntent(context, "open"))
                .addAction(TestNotifications.replyAction(context))
                .addAction(TestNotifications.plainAction(context, "Mark as read"))
                .addAction(TestNotifications.plainAction(context, "Mute"))
                .build(),
        )
        registry.register(chat)
        listenFor("reply", "open", "Mark as read")

        val actions = registry.actions(chat.key)

        assertEquals(listOf("Mark as read", "Mute"), actions.map { it.title })
        assertTrue(actions.first().send())
        assertTrue(registry.hasReply(chat.key))
        assertTrue(registry.reply(chat.key, "On my way"))
        assertTrue(registry.openContent(chat.key))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("Mark as read", "reply", "open"), received.map { it.action })
        val replied = RemoteInput.getResultsFromIntent(received[1])
        assertEquals("On my way", replied?.getCharSequence("reply_text")?.toString())

        registry.unregister(chat.key)
        assertTrue(registry.actions(chat.key).isEmpty())
        assertFalse(registry.openContent(chat.key))
    }

    @Test
    fun actions_withoutIntent_areSkipped() {
        val noIntent = NotificationCompat.Action.Builder(0, "Broken", null).build()
        val noIntentReply = NotificationCompat.Action.Builder(0, "Reply", null)
            .addRemoteInput(androidx.core.app.RemoteInput.Builder("reply_text").setLabel("Reply").build())
            .build()
        val chat = sbn(
            builder(context).setContentTitle("Anna")
                .addAction(noIntent)
                .addAction(noIntentReply)
                .addAction(TestNotifications.plainAction(context, "Mute"))
                .build(),
        )
        registry.register(chat)

        assertEquals(listOf("Mute"), registry.actions(chat.key).map { it.title })
        assertFalse(registry.hasReply(chat.key))
        assertFalse(registry.reply(chat.key, "On my way"))
    }

    @Test
    @Config(sdk = [35])
    fun openContent_onApi34_passesBackgroundActivityStartOptions() {
        val open = activityIntent("open")
        val chat = sbn(
            builder(context).setContentTitle("Anna")
                .setContentIntent(open)
                .addAction(androidx.core.app.NotificationCompat.Action.Builder(0, "View", activityIntent("view")).build())
                .build(),
        )
        registry.register(chat)
        val started = shadowOf(context as Application)

        assertTrue(registry.openContent(chat.key))
        assertAllowsBackgroundStart(started.nextStartedActivityForResult.options)

        assertTrue(registry.actions(chat.key).single().send())
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
