package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ai.configureAppCheck
import com.example.data.VaultRepository
import com.example.navigation.VaultoraApp
import com.example.notifications.VaultReminderReceiver

class MainActivity : ComponentActivity() {
    private var pendingNavTarget by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configure Firebase App Check and Firestore offline persistence before any ViewModel/query runs
        configureAppCheck(this, intent)
        VaultRepository.createConfiguredFirestore(this)
        VaultReminderReceiver.ensureNotificationChannel(this)
        pendingNavTarget = intent?.getStringExtra(VaultReminderReceiver.EXTRA_NAV_TARGET)
        enableEdgeToEdge()

        setContent {
            VaultoraApp(
                pendingNavTarget = pendingNavTarget,
                onConsumeNavTarget = { pendingNavTarget = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val target = intent.getStringExtra(VaultReminderReceiver.EXTRA_NAV_TARGET)
        if (!target.isNullOrBlank()) {
            pendingNavTarget = target
        }
    }
}
