package com.mintake.wear

import android.app.Application
import com.mintake.shared.MintakeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MintakeWearApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        ReminderNotifications.ensureChannel(this)

        val repository = MintakeRepository.get(this)
        // Whenever reminders or dose statuses change (here or from the phone), re-arm the next alarm.
        appScope.launch {
            repository.awaitLoaded()
            repository.state.collect { ReminderScheduler.reschedule(this@MintakeWearApplication, it) }
        }
        appScope.launch { repository.syncNow() }
    }
}
