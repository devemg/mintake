package com.mintake.mobile.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

@Composable
fun MobileApp() {
    var adding by rememberSaveable { mutableStateOf(false) }
    var toast by rememberSaveable { mutableStateOf<String?>(null) }

    RequestNotificationPermission()

    BackHandler(enabled = adding) { adding = false }

    if (adding) {
        AddReminderScreen(
            onCancel = { adding = false },
            onSaved = { name ->
                adding = false
                toast = "$name added · synced to watch"
            },
        )
    } else {
        HomeScreen(
            toast = toast,
            onAddReminder = {
                toast = null
                adding = true
            },
        )
    }
}

@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}
