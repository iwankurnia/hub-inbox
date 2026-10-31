package com.iwankurnia.hub.capture

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iwankurnia.hub.capture.TestNotifications.builder
import com.iwankurnia.hub.capture.TestNotifications.sbn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationMapperTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val me = Person.Builder().setName("Me").setKey("me").build()
    private val anna = Person.Builder().setName("Anna").setKey("anna").build()
    private val ben = Person.Builder().setName("Ben").setKey("ben").build()

    @Test
    fun map_plainNotification_titleTextNoMessages() {
        val notification = builder(context)
            .setContentTitle("Matthew")
            .setContentText("Lunch?")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Lunch at one?"))
            .setSubText("work@example.com")
            .build()
        val status = sbn(notification, id = 7, tag = "t", postTime = 4_242L)

        val c = NotificationMapper.map(status, context)

        assertEquals(TestNotifications.PKG, c.packageName)
        assertEquals(status.key, c.notifKey)
        assertEquals("Matthew", c.title)
        assertEquals("Lunch?", c.text)
        assertEquals("Lunch at one?", c.bigText)
        assertEquals("work@example.com", c.subText)
        assertEquals(4_242L, c.postedAt)
        assertTrue(c.messages.isEmpty())
        assertFalse(c.hasReply)
    }

    @Test
    fun map_messagingStyle_extractsMessagesAndSender() {
        val style = NotificationCompat.MessagingStyle(me)
            .setConversationTitle("Engineering")
            .setGroupConversation(true)
            .addMessage("Morning all", 100L, anna)
            .addMessage("   ", 150L, anna)
            .addMessage("Stand-up in five", 200L, ben)
            .addMessage("On my way", 300L, me)
            .addMessage("Thanks", 400L, null as Person?)
        val notification = builder(context).setContentTitle("Anna").setStyle(style).build()

        val c = NotificationMapper.map(sbn(notification), context)

        assertEquals("Engineering", c.title)
        assertEquals(
            listOf(
                CapturedMessage("Anna", "Morning all", 100L),
                CapturedMessage("Ben", "Stand-up in five", 200L),
                CapturedMessage(null, "On my way", 300L),
                CapturedMessage(null, "Thanks", 400L),
            ),
            c.messages,
        )
    }

    @Test
    fun map_oneToOneChat_senderIsNull() {
        val style = NotificationCompat.MessagingStyle(me)
            .setGroupConversation(false)
            .addMessage("Are you free?", 100L, anna)
            .addMessage("Call me", 200L, anna)
        val notification = builder(context).setContentTitle("Anna").setStyle(style).build()

        val c = NotificationMapper.map(sbn(notification), context)

        assertEquals("Anna", c.title)
        assertEquals(listOf<String?>(null, null), c.messages.map { it.sender })
        assertEquals(listOf("Are you free?", "Call me"), c.messages.map { it.text })
    }

    @Test
    fun map_remoteInput_setsHasReply() {
        val withReply = builder(context).setContentTitle("Anna")
            .addAction(TestNotifications.plainAction(context, "Mark as read"))
            .addAction(TestNotifications.replyAction(context))
            .build()
        val withoutReply = builder(context).setContentTitle("Anna")
            .addAction(TestNotifications.plainAction(context, "Mark as read"))
            .build()

        assertTrue(NotificationMapper.map(sbn(withReply), context).hasReply)
        assertFalse(NotificationMapper.map(sbn(withoutReply), context).hasReply)
    }

    @Test
    fun map_conversationId_prefersShortcutId() {
        val withShortcut = sbn(builder(context).setContentTitle("A").setShortcutId("chat-42").build())
        val withoutShortcut = sbn(builder(context).setContentTitle("A").build(), id = 2)

        assertEquals("chat-42", NotificationMapper.map(withShortcut, context).conversationId)
        assertEquals(withoutShortcut.key, NotificationMapper.map(withoutShortcut, context).conversationId)
        assertNull(NotificationMapper.map(withoutShortcut, context).bigText)
    }
}
