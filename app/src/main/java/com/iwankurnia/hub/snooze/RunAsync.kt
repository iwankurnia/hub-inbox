package com.iwankurnia.hub.snooze

import android.content.BroadcastReceiver
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal const val RECEIVER_TAG = "HubReceiver"

/** Logs instead of crashing the app, as the listener service does; the broadcast is still finished. */
private val crashGuard = CoroutineExceptionHandler { _, e -> Log.e(RECEIVER_TAG, "Receiver work failed", e) }

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + crashGuard)

/** Runs [block] off the main thread while keeping the broadcast alive until it finishes. */
internal fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    launchReceiverWork(finish = pending::finish, block = block)
}

/** Runs [block] in the receivers' scope and calls [finish] however it ends. */
internal fun launchReceiverWork(finish: () -> Unit, block: suspend () -> Unit): Job =
    receiverScope.launch {
        try {
            block()
        } finally {
            finish()
        }
    }
