package com.mintake.wear

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.mintake.wear.ui.MintakeWearTheme
import com.mintake.wear.ui.WearApp

class MainActivity : ComponentActivity() {

    /** Dose to show on the "Time for your medicine" screen (set when opened from a notification). */
    private val pendingDoseKey = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingDoseKey.value = intent?.getStringExtra(ReminderNotifications.EXTRA_DOSE_KEY)
        setContent {
            MintakeWearTheme {
                WearApp(
                    pendingDoseKey = pendingDoseKey.value,
                    onDoseKeyConsumed = { pendingDoseKey.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(ReminderNotifications.EXTRA_DOSE_KEY)?.let { pendingDoseKey.value = it }
    }

    companion object {
        fun intentFor(context: Context, doseKey: String): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(ReminderNotifications.EXTRA_DOSE_KEY, doseKey)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
