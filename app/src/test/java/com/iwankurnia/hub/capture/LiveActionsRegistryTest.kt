package com.iwankurnia.hub.capture

import android.app.RemoteInput
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

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
}
