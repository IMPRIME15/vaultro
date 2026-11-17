package com.example.notifications

import com.example.model.PriorityTier
import com.example.model.UserProfile
import com.example.model.VaultModule
import com.example.priority.PriorityCenterReport
import com.example.priority.PriorityItem
import com.example.util.DateAndCurrencyUtils

data class PriorityNotificationPreferences(
    val criticalAlertsEnabled: Boolean = true,
    val highAlertsEnabled: Boolean = true,
    val mediumDigestEnabled: Boolean = true,
    val lowMuted: Boolean = true,
    val purchaseAlertsEnabled: Boolean = true,
    val medicineAlertsEnabled: Boolean = true
) {
    companion object {
        fun fromProfile(profile: UserProfile?): PriorityNotificationPreferences {
            return PriorityNotificationPreferences(
                criticalAlertsEnabled = profile?.priorityCriticalAlertsEnabled ?: true,
                highAlertsEnabled = profile?.priorityHighAlertsEnabled ?: true,
                mediumDigestEnabled = profile?.priorityMediumDigestEnabled ?: true,
                lowMuted = profile?.priorityLowMuted ?: true,
                purchaseAlertsEnabled = profile?.purchaseRemindersEnabled ?: true,
                medicineAlertsEnabled = profile?.medicineRemindersEnabled ?: true
            )
        }
    }
}

data class PriorityNotificationMessage(
    val eventKey: String,
    val signature: String,
    val notificationId: Int,
    val tier: PriorityTier,
    val title: String,
    val body: String,
    val navTarget: String,
    val inboxLines: List<String> = emptyList()
)

data class PriorityNotificationPlan(
    val messages: List<PriorityNotificationMessage> = emptyList(),
    val suppressedLowCount: Int = 0
)

/**
 * Pure Kotlin planner for Smart Notification Management:
 * - Critical items -> timely individual alerts
 * - High items -> appropriate individual or consolidated alerts
 * - Medium items -> single grouped summary notification (rate-limited)
 * - Low items -> suppressed by default to prevent notification overload
 * - Deduplication via deterministic per-event signatures
 */
object PriorityNotificationManager {

    private const val CRITICAL_LEAD_DAYS = 7L
    private const val HIGH_LEAD_DAYS = 14L
    private const val MEDIUM_LEAD_DAYS = 14L

    fun buildPlan(
        report: PriorityCenterReport,
        prefs: PriorityNotificationPreferences,
        nowMillis: Long = report.evaluatedAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
    ): PriorityNotificationPlan {
        val dayKey = DateAndCurrencyUtils.formatIsoDate(nowMillis)
        val messages = mutableListOf<PriorityNotificationMessage>()

        fun isModuleEnabled(item: PriorityItem): Boolean = when (item.module) {
            VaultModule.PURCHASE -> prefs.purchaseAlertsEnabled
            VaultModule.MEDICINE -> prefs.medicineAlertsEnabled
        }

        // 1. CRITICAL: Timely individual notifications
        if (prefs.criticalAlertsEnabled) {
            val criticalDue = report.criticalItems.filter { item ->
                isModuleEnabled(item) && (
                    item.isOverdue ||
                        item.remainingDays == null || // e.g., active claim follow-up
                        item.remainingDays <= CRITICAL_LEAD_DAYS
                    )
            }
            criticalDue.take(3).forEach { item ->
                val nav = navTargetFor(item)
                val countdown = formatPriorityTiming(item)
                messages.add(
                    PriorityNotificationMessage(
                        eventKey = "crit_${item.recordKey}",
                        signature = "${dayKey}_${item.recordKey}_${item.remainingDays ?: "active"}",
                        notificationId = ("priority_crit_${item.recordKey}").hashCode(),
                        tier = PriorityTier.CRITICAL,
                        title = "Critical Priority: ${item.title}",
                        body = "${item.sourceKind.displayLabel} • $countdown",
                        navTarget = nav
                    )
                )
            }
        }

        // 2. HIGH: Appropriate reminders (individual if <= 2, grouped summary if >= 3)
        if (prefs.highAlertsEnabled) {
            val highDue = report.highItems.filter { item ->
                isModuleEnabled(item) && (
                    item.isOverdue ||
                        (item.remainingDays != null && item.remainingDays <= HIGH_LEAD_DAYS)
                    )
            }
            if (highDue.size in 1..2) {
                highDue.forEach { item ->
                    val countdown = formatPriorityTiming(item)
                    messages.add(
                        PriorityNotificationMessage(
                            eventKey = "high_${item.recordKey}",
                            signature = "${dayKey}_${item.recordKey}_${item.remainingDays ?: "none"}",
                            notificationId = ("priority_high_${item.recordKey}").hashCode(),
                            tier = PriorityTier.HIGH,
                            title = "High Priority: ${item.title}",
                            body = "${item.sourceKind.displayLabel} • $countdown",
                            navTarget = navTargetFor(item)
                        )
                    )
                }
            } else if (highDue.size >= 3) {
                val sig = "${dayKey}_high_" + highDue.joinToString("|") { "${it.recordKey}:${it.remainingDays}" }
                messages.add(
                    PriorityNotificationMessage(
                        eventKey = "high_group_summary",
                        signature = sig,
                        notificationId = 8802,
                        tier = PriorityTier.HIGH,
                        title = "High Priority (${highDue.size} items need attention)",
                        body = "${highDue.first().title} and ${highDue.size - 1} more upcoming deadlines.",
                        navTarget = "priority_center",
                        inboxLines = highDue.map { "${it.title} • ${formatPriorityTiming(it)}" }
                    )
                )
            }
        }

        // 3. MEDIUM: Grouped single summary reminder for approaching non-critical deadlines
        if (prefs.mediumDigestEnabled) {
            val mediumDue = report.mediumItems.filter { item ->
                isModuleEnabled(item) &&
                    item.remainingDays != null &&
                    item.remainingDays in 0L..MEDIUM_LEAD_DAYS
            }
            if (mediumDue.isNotEmpty()) {
                val sig = "${dayKey}_med_" + mediumDue.joinToString("|") { "${it.recordKey}:${it.remainingDays}" }
                messages.add(
                    PriorityNotificationMessage(
                        eventKey = "medium_group_summary",
                        signature = sig,
                        notificationId = 8803,
                        tier = PriorityTier.MEDIUM,
                        title = "Upcoming Vault Summary (${mediumDue.size} medium-priority)",
                        body = if (mediumDue.size == 1) {
                            "${mediumDue.first().title} • ${formatPriorityTiming(mediumDue.first())}"
                        } else {
                            "${mediumDue.first().title} & ${mediumDue.size - 1} more deadlines approaching."
                        },
                        navTarget = "priority_center",
                        inboxLines = if (mediumDue.size > 1) {
                            mediumDue.map { "${it.title} • ${formatPriorityTiming(it)}" }
                        } else {
                            emptyList()
                        }
                    )
                )
            }
        }

        // 4. LOW: Do not generate unnecessary notifications when lowMuted is true
        val suppressedLow = if (prefs.lowMuted) report.lowItems.size else 0

        return PriorityNotificationPlan(
            messages = messages,
            suppressedLowCount = suppressedLow
        )
    }

    private fun navTargetFor(item: PriorityItem): String = when (item.module) {
        VaultModule.PURCHASE -> "purchase:${item.id}"
        VaultModule.MEDICINE -> "medicine:${item.id}"
    }

    private fun formatPriorityTiming(item: PriorityItem): String {
        val days = item.remainingDays
        return when {
            item.isOverdue && days != null -> "Overdue by ${-days}d (${DateAndCurrencyUtils.formatDate(item.deadlineMillis)})"
            days != null -> "${DateAndCurrencyUtils.formatCountdown(days)} (${DateAndCurrencyUtils.formatDate(item.deadlineMillis)})"
            else -> "Action pending"
        }
    }
}
