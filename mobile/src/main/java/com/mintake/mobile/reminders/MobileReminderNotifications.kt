package com.mintake.mobile.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mintake.mobile.MainActivity
import com.mintake.mobile.R
import com.mintake.shared.Dose
import com.mintake.shared.DoseStatus
import com.mintake.shared.Format

object MobileReminderNotifications {
    const val CHANNEL_ID = "dose_reminders"
    const val EXTRA_DOSE_KEY = "com.mintake.extra.DOSE_KEY"
    const val EXTRA_STATUS = "com.mintake.extra.STATUS"
    private const val ACTION_DOSE = "com.mintake.action.DOSE"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_reminders),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_reminders_description)
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    fun show(context: Context, dose: Dose, attempt: Int = 1) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val id = dose.key.hashCode()
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openApp = PendingIntent.getActivity(
            context,
            id,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val reminderText = "${dose.medicine.doseLabel} · ${Format.time(dose.time)}"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mintake)
            .setContentTitle(dose.medicine.name)
            .setContentText(reminderText)
            .setSubText("Reminder $attempt of 5")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setContentIntent(openApp)
            .addAction(
                R.drawable.ic_action_done,
                context.getString(R.string.action_done),
                actionIntent(context, dose.key, DoseStatus.TAKEN),
            )
            .addAction(
                R.drawable.ic_action_snooze,
                context.getString(R.string.action_snooze),
                actionIntent(context, dose.key, DoseStatus.SNOOZED),
            )
            .addAction(
                R.drawable.ic_action_not_taken,
                context.getString(R.string.action_not_taken),
                actionIntent(context, dose.key, DoseStatus.NOT_TAKEN),
            )
            .build()

        NotificationManagerCompat.from(context).notify(id, notification)
    }

    fun cancel(context: Context, doseKey: String) {
        NotificationManagerCompat.from(context).cancel(doseKey.hashCode())
    }

    private fun actionIntent(context: Context, doseKey: String, status: DoseStatus): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (doseKey + status.name).hashCode(),
            Intent(context, MobileDoseActionReceiver::class.java)
                .setAction(ACTION_DOSE)
                .putExtra(EXTRA_DOSE_KEY, doseKey)
                .putExtra(EXTRA_STATUS, status.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
