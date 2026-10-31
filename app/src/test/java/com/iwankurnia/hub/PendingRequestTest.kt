package com.iwankurnia.hub

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iwankurnia.hub.data.HubTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PendingRequestTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun from_readsEveryExtra() {
        val intent = HubIntents.open(context, tab = "UNREAD", packageName = "com.whatsapp", itemId = 7L, replyItemId = 9L)
            .putExtra(HubIntents.EXTRA_OPEN_SEARCH, true)

        assertEquals(
            PendingRequest(tab = HubTab.UNREAD, packageName = "com.whatsapp", itemId = 7L, replyItemId = 9L, openSearch = true),
            PendingRequest.from(intent),
        )
    }

    @Test
    fun from_unknownTab_isIgnored() {
        val intent = HubIntents.open(context, tab = "NOPE", itemId = 3L)

        assertEquals(PendingRequest(null, null, 3L, null, false), PendingRequest.from(intent))
    }

    @Test
    fun from_nullOrEmptyIntent_isNull() {
        assertNull(PendingRequest.from(null))
        assertNull(PendingRequest.from(Intent(context, MainActivity::class.java)))
    }
}
