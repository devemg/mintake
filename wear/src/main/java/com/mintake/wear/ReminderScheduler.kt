package com.mintake.wear

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.mintake.shared.AppState
import com.mintake.shared.Dose
import com.mintake.shared.DoseStatus
import com.mintake.shared.Schedule
import java.time.LocalDate

/**
 * Keeps exactly one alarm armed: the next moment a dose is due (or a snooze ends).
 * When it fires, [ReminderReceiver] posts the notifications and calls [reschedule] again.
 */
object ReminderScheduler {
    private const val TAG = "ReminderScheduler"

    /** Doses that became due within this window still get a notification (covers late alarms). */
    private const val CATCH_UP_MS = 15 * 60_000L

    private fun candidateDoses(state: AppState): List<Dose> {
        val today = LocalDate.now()
        return (-1L..1L).flatMap { Schedule.dosesOn(today.plusDays(it), state.medicines) }
    }

    /** When this dose should alert, or null if it has already been handled. */
    private fun alertTime(state: AppState, dose: Dose): Long? {
        val record = state.records[dose.key] ?: return dose.epochMillis()
        return if (record.status == DoseStatus.SNOOZED) record.snoozedUntil else null
    }

    fun nextAlarm(state: AppState, now: Long): Long? =
        candidateDoses(state).mapNotNull { dose -> alertTime(state, dose)?.takeIf { it > now } }.minOrNull()

    fun dueNow(state: AppState, now: Long): List<Dose> =
        candidateDoses(state).filter { dose ->
            val t = alertTime(state, dose) ?: return@filter false
            t in (now - CATCH_UP_MS)..(now + 30_000L)
        }

    fun reschedule(context: Context, state: AppState) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val next = nextAlarm(state, System.currentTimeMillis())
        if (next == null) {
            alarmManager.cancel(pendingIntent)
            return
        }

        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        try {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pendingIntent)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm not allowed, using an inexact one", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pendingIntent)
        }
    }
}
