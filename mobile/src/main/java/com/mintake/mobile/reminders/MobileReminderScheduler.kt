package com.mintake.mobile.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.mintake.shared.AppState
import com.mintake.shared.Dose
import com.mintake.shared.DoseStatus
import com.mintake.shared.MintakeRepository
import com.mintake.shared.Schedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

object MobileReminderScheduler {
    private const val TAG = "MobileReminderScheduler"

    /** 10 minutes between reminder notifications */
    const val RETRY_INTERVAL_MS = 10 * 60_000L

    /** Maximum 5 notifications per dose before marking as NOT_TAKEN */
    const val MAX_ATTEMPTS = 5

    private fun candidateDoses(state: AppState): List<Dose> {
        val today = LocalDate.now()
        return (-1L..1L).flatMap { Schedule.dosesOn(today.plusDays(it), state.medicines) }
    }

    /**
     * Inspects active doses, posts notifications for due doses (retrying every 10 mins up to 5 times),
     * auto-marks doses as NOT_TAKEN if 5 attempts passed without response, and sets the next exact alarm.
     */
    suspend fun processDoses(
        context: Context,
        repository: MintakeRepository,
        now: Long = System.currentTimeMillis(),
    ) {
        val state = repository.state.value
        val doses = candidateDoses(state)
        var nextAlarm: Long? = null

        for (dose in doses) {
            val record = state.records[dose.key]

            // If already resolved (TAKEN or NOT_TAKEN), skip
            if (record != null && (record.status == DoseStatus.TAKEN || record.status == DoseStatus.NOT_TAKEN)) {
                continue
            }

            val dueTime = if (record?.status == DoseStatus.SNOOZED) {
                record.snoozedUntil ?: dose.epochMillis()
            } else {
                dose.epochMillis()
            }

            if (now < dueTime) {
                // Scheduled in the future
                nextAlarm = minOfNotNull(nextAlarm, dueTime)
            } else {
                // Due or past due
                val elapsed = now - dueTime
                val attempt = (elapsed / RETRY_INTERVAL_MS).toInt() + 1

                if (attempt > MAX_ATTEMPTS) {
                    // Exceeded max 5 attempts -> Auto-mark as NOT_TAKEN by itself
                    repository.setDoseStatus(dose.key, DoseStatus.NOT_TAKEN)
                    MobileReminderNotifications.cancel(context, dose.key)
                } else {
                    // Show push notification for this attempt
                    MobileReminderNotifications.show(context, dose, attempt = attempt)

                    // Schedule next 10-minute retry alarm
                    val nextRetry = dueTime + attempt * RETRY_INTERVAL_MS
                    nextAlarm = minOfNotNull(nextAlarm, nextRetry)
                }
            }
        }

        setAlarm(context, nextAlarm)
    }

    fun reschedule(context: Context, state: AppState) {
        val repository = MintakeRepository.get(context)
        CoroutineScope(Dispatchers.Default).launch {
            repository.awaitLoaded()
            processDoses(context, repository)
        }
    }

    private fun setAlarm(context: Context, timeMs: Long?) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, MobileReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        if (timeMs == null) {
            alarmManager.cancel(pendingIntent)
            return
        }

        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        try {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMs, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMs, pendingIntent)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm not allowed, using inexact alarm", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMs, pendingIntent)
        }
    }

    private fun minOfNotNull(a: Long?, b: Long): Long = if (a == null) b else minOf(a, b)
}
