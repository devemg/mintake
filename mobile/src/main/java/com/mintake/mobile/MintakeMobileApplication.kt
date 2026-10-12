package com.mintake.mobile

import android.app.Application
import com.mintake.shared.MintakeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MintakeMobileApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Pick up any dose updates the watch made while the phone app was closed.
        appScope.launch { MintakeRepository.get(this@MintakeMobileApplication).syncNow() }
    }
}
