package com.mintake.mobile.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mintake.shared.DoseStatus
import com.mintake.shared.MintakeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Helper to run suspend blocks safely inside a BroadcastReceiver */
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

/** Alarm trigger: processes due doses, sends 10-min notifications, and schedules next alarm */
class MobileReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runAsync {
        val repository = MintakeRepository.get(context)
        repository.awaitLoaded()
        MobileReminderScheduler.processDoses(context, repository)
    }
}

/** Notification action buttons: Done / Snooze / Not taken */
class MobileDoseActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(MobileReminderNotifications.EXTRA_DOSE_KEY) ?: return
        val status = intent.getStringExtra(MobileReminderNotifications.EXTRA_STATUS)
            ?.let { runCatching { DoseStatus.valueOf(it) }.getOrNull() } ?: return
        runAsync {
            DoseActions.apply(context, key, status)
        }
    }
}

/** Re-arms alarms after device reboot, app update, or time change */
class MobileBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runAsync {
        val repository = MintakeRepository.get(context)
        repository.awaitLoaded()
        MobileReminderScheduler.processDoses(context, repository)
    }
}

object DoseActions {
    const val SNOOZE_MS = 30 * 60_000L

    suspend fun apply(context: Context, doseKey: String, status: DoseStatus) {
        val repository = MintakeRepository.get(context)
        val snoozedUntil = if (status == DoseStatus.SNOOZED) System.currentTimeMillis() + SNOOZE_MS else null
        repository.setDoseStatus(doseKey, status, snoozedUntil)
        MobileReminderNotifications.cancel(context, doseKey)
        MobileReminderScheduler.processDoses(context, repository)
    }
}
