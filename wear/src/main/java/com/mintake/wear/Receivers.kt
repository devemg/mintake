package com.mintake.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mintake.shared.DoseStatus
import com.mintake.shared.MintakeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Runs [block] off the main thread while keeping the receiver alive until it finishes. */
private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** Fired by the alarm: shows notifications for doses that are due, then arms the next alarm. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runAsync {
        val repository = MintakeRepository.get(context)
        repository.awaitLoaded()
        val state = repository.state.value
        ReminderScheduler.dueNow(state, System.currentTimeMillis())
            .forEach { ReminderNotifications.show(context, it) }
        ReminderScheduler.reschedule(context, state)
    }
}

/** Done / Not taken / Snooze buttons on the notification. */
class DoseActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(ReminderNotifications.EXTRA_DOSE_KEY) ?: return
        val status = intent.getStringExtra(ReminderNotifications.EXTRA_STATUS)
            ?.let { runCatching { DoseStatus.valueOf(it) }.getOrNull() } ?: return
        runAsync { DoseActions.apply(context, key, status) }
    }
}

/** Re-arms the alarm after a reboot, an app update or a clock change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runAsync {
        val repository = MintakeRepository.get(context)
        repository.awaitLoaded()
        ReminderScheduler.reschedule(context, repository.state.value)
    }
}

object DoseActions {
    const val SNOOZE_MS = 30 * 60_000L

    suspend fun apply(context: Context, doseKey: String, status: DoseStatus) {
        val repository = MintakeRepository.get(context)
        val snoozedUntil = if (status == DoseStatus.SNOOZED) System.currentTimeMillis() + SNOOZE_MS else null
        repository.setDoseStatus(doseKey, status, snoozedUntil)
        ReminderNotifications.cancel(context, doseKey)
        ReminderScheduler.reschedule(context, repository.state.value)
    }
}
