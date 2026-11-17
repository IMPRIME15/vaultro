package com.example.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class VaultReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_CHECK_EXPIRING_SOON) {
            VaultReminderScheduler.checkCachedExpiriesAndNotify(context)
            return
        }

        if (!VaultReminderScheduler.hasNotificationPermission(context)) return

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "VAULTORA Reminder"
        val body = intent.getStringExtra(EXTRA_BODY) ?: "You have an upcoming vault deadline."
        val navTarget = intent.getStringExtra(EXTRA_NAV_TARGET) ?: ""
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, title.hashCode())
        val summaryLines = intent.getStringArrayListExtra(EXTRA_INBOX_LINES).orEmpty()

        showNotification(
            context = context,
            notificationId = notificationId,
            title = title,
            body = body,
            navTarget = navTarget,
            inboxLines = summaryLines
        )
    }

    companion object {
        const val CHANNEL_ID = "vaultora_deadlines"
        const val ACTION_CHECK_EXPIRING_SOON = "com.example.notifications.CHECK_EXPIRING_SOON"
        const val EXTRA_TITLE = "vaultora_title"
        const val EXTRA_BODY = "vaultora_body"
        const val EXTRA_NAV_TARGET = "vaultora_nav_target"
        const val EXTRA_NOTIFICATION_ID = "vaultora_notification_id"
        const val EXTRA_INBOX_LINES = "vaultora_inbox_lines"

        @SuppressLint("MissingPermission")
        fun showNotification(
            context: Context,
            notificationId: Int,
            title: String,
            body: String,
            navTarget: String,
            inboxLines: List<String> = emptyList()
        ) {
            if (!VaultReminderScheduler.hasNotificationPermission(context)) return
            ensureNotificationChannel(context)

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_NAV_TARGET, navTarget)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val style: NotificationCompat.Style = if (inboxLines.isNotEmpty()) {
                val inbox = NotificationCompat.InboxStyle()
                inbox.setBigContentTitle(title)
                inboxLines.take(6).forEach { inbox.addLine(it) }
                if (inboxLines.size > 6) {
                    inbox.setSummaryText("+${inboxLines.size - 6} more expiring within 7 days")
                }
                inbox
            } else {
                NotificationCompat.BigTextStyle().bigText(body)
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(style)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(notificationId, notification)
        }

        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "VAULTORA Deadlines & Expiry Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Reminders for purchase return deadlines, warranty expiries, and medicine expiries."
                }
                manager.createNotificationChannel(channel)
            }
        }
    }
}
