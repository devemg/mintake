package com.mintake.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mintake.mobile.ui.MintakeMobileTheme
import com.mintake.mobile.ui.MobileApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MintakeMobileTheme {
                MobileApp()
            }
        }
    }
}
