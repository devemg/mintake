package com.mintake.mobile

import android.app.Application
import com.mintake.mobile.reminders.MobileReminderNotifications
import com.mintake.mobile.reminders.MobileReminderScheduler
import com.mintake.shared.MintakeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MintakeMobileApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        MobileReminderNotifications.ensureChannel(this)

        val repository = MintakeRepository.get(this)
        appScope.launch {
            repository.awaitLoaded()
            repository.state.collect { state ->
                MobileReminderScheduler.reschedule(this@MintakeMobileApplication, state)
            }
        }

        // Pick up any dose updates the watch made while the phone app was closed.
        appScope.launch { repository.syncNow() }
    }
}
