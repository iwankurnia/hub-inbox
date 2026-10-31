package com.iwankurnia.hub.snooze

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog

@RunWith(AndroidJUnit4::class)
class RunAsyncTest {

    private val uncaught = mutableListOf<Throwable>()
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    @Before
    fun setUp() {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> synchronized(uncaught) { uncaught += e } }
        ShadowLog.clear()
    }

    @After
    fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
    }

    @Test
    fun runAsync_exceptionIsLoggedNotThrown() {
        var finished = false

        val job = launchReceiverWork(finish = { finished = true }) { error("boom") }
        runBlocking { job.join() }

        assertTrue("the broadcast is released", finished)
        val logged = ShadowLog.getLogsForTag(RECEIVER_TAG).single()
        assertEquals(Log.ERROR, logged.type)
        assertEquals("boom", logged.throwable?.message)
        assertTrue("nothing reaches the thread's crash handler", synchronized(uncaught) { uncaught.isEmpty() })
    }
}
