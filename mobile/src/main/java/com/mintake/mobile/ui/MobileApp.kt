package com.mintake.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

@Composable
fun MobileApp() {
    var adding by rememberSaveable { mutableStateOf(false) }
    var toast by rememberSaveable { mutableStateOf<String?>(null) }

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
