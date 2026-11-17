package com.example.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.model.MedicineRecord
import com.example.model.PriorityTier
import com.example.model.PurchaseRecord
import com.example.model.UserProfile
import com.example.model.VaultModule
import com.example.priority.PriorityEngine
import com.example.util.DateAndCurrencyUtils
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.TimeUnit

object VaultReminderScheduler {
    private const val PREFS_NAME = "vaultora_expiring_soon_prefs"
    private const val KEY_CACHED_PURCHASES = "cached_warranties_json"
    private const val KEY_CACHED_MEDICINES = "cached_medicines_json"
    private const val KEY_ALLOW_PURCHASES = "allow_purchases"
    private const val KEY_ALLOW_MEDICINES = "allow_medicines"
    private const val KEY_CRIT_ALERTS = "priority_crit_alerts"
    private const val KEY_HIGH_ALERTS = "priority_high_alerts"
    private const val KEY_MED_DIGEST = "priority_med_digest"
    private const val KEY_LOW_MUTED = "priority_low_muted"
    private const val KEY_LAST_SIGNATURE = "last_expiring_7d_signature"
    private const val KEY_LAST_CHECK_MILLIS = "last_expiring_7d_check_millis"
    private const val KEY_LAST_MEDIUM_DIGEST_MILLIS = "last_medium_digest_millis"
    private const val KEY_DISPATCHED_EVENT_PREFIX = "priority_dispatched_"

    private const val DAILY_CHECK_REQUEST_CODE = 7701
    private const val EXPIRING_SOON_NOTIFICATION_ID = 7700
    private val MEDIUM_THROTTLE_MILLIS = TimeUnit.DAYS.toMillis(3)

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun getLastCheckedMillis(context: Context): Long {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_CHECK_MILLIS, 0L)
    }

    fun schedulePurchaseReminders(
        context: Context,
        purchase: PurchaseRecord,
        returnLeadDays: Long,
        warrantyLeadDays: Long
    ) {
        if (!hasNotificationPermission(context) || purchase.completed) return
        VaultReminderReceiver.ensureNotificationChannel(context)
        val now = System.currentTimeMillis()

        val returnDeadline = purchase.returnDeadlineMillis
        if (purchase.returnReminderSet && returnDeadline != null && returnDeadline > now) {
            val triggerAt = maxOf(
                now + 5_000L,
                returnDeadline - TimeUnit.DAYS.toMillis(returnLeadDays.coerceAtLeast(1L))
            )
            scheduleAlarm(
                context = context,
                requestCode = ("return_${purchase.id}").hashCode(),
                triggerAtMillis = triggerAt,
                title = "Return Window Closing: ${purchase.productName}",
                body = "Return deadline for ${purchase.merchantName} purchase is approaching.",
                navTarget = "purchase:${purchase.id}"
            )
        }

        val warrantyExpiry = purchase.warrantyExpiryMillis
        if (purchase.warrantyReminderSet && warrantyExpiry != null && warrantyExpiry > now) {
            val leadTriggerAt = maxOf(
                now + 5_000L,
                warrantyExpiry - TimeUnit.DAYS.toMillis(warrantyLeadDays.coerceAtLeast(1L))
            )
            scheduleAlarm(
                context = context,
                requestCode = ("warranty_${purchase.id}").hashCode(),
                triggerAtMillis = leadTriggerAt,
                title = "Warranty Expiring Soon: ${purchase.productName}",
                body = "Warranty coverage from ${purchase.merchantName} ends soon. Review or prepare a claim.",
                navTarget = "purchase:${purchase.id}"
            )

            // Also schedule dedicated 7-day "Expiring Soon" check alarm
            val sevenDayTriggerAt = warrantyExpiry - TimeUnit.DAYS.toMillis(ExpiringSoonChecker.EXPIRING_SOON_WINDOW_DAYS)
            if (sevenDayTriggerAt > now) {
                scheduleAlarm(
                    context = context,
                    requestCode = ("exp7_warranty_${purchase.id}").hashCode(),
                    triggerAtMillis = sevenDayTriggerAt,
                    title = "Expiring Soon: ${purchase.productName} Warranty",
                    body = "Warranty coverage from ${purchase.merchantName} expires within 7 days (${DateAndCurrencyUtils.formatDate(warrantyExpiry)}).",
                    navTarget = "purchase:${purchase.id}"
                )
            }
        }
    }

    fun scheduleMedicineReminder(
        context: Context,
        medicine: MedicineRecord
    ) {
        if (!hasNotificationPermission(context) || medicine.completed) return
        VaultReminderReceiver.ensureNotificationChannel(context)
        val now = System.currentTimeMillis()

        if (medicine.reminderEnabled && medicine.expiryDateMillis > now) {
            val leadTriggerAt = maxOf(
                now + 5_000L,
                medicine.expiryDateMillis - TimeUnit.DAYS.toMillis(medicine.reminderDaysBefore.coerceAtLeast(1L))
            )
            scheduleAlarm(
                context = context,
                requestCode = ("med_${medicine.id}").hashCode(),
                triggerAtMillis = leadTriggerAt,
                title = "Medicine Expiring Soon: ${medicine.medicineName}",
                body = "Batch ${medicine.batchNumber.ifBlank { "N/A" }} is nearing its expiry date.",
                navTarget = "medicine:${medicine.id}"
            )

            // Also schedule dedicated 7-day "Expiring Soon" check alarm
            val sevenDayTriggerAt = medicine.expiryDateMillis - TimeUnit.DAYS.toMillis(ExpiringSoonChecker.EXPIRING_SOON_WINDOW_DAYS)
            if (sevenDayTriggerAt > now) {
                scheduleAlarm(
                    context = context,
                    requestCode = ("exp7_med_${medicine.id}").hashCode(),
                    triggerAtMillis = sevenDayTriggerAt,
                    title = "Expiring Soon: ${medicine.medicineName}",
                    body = "Batch ${medicine.batchNumber.ifBlank { "N/A" }} expires within 7 days (${DateAndCurrencyUtils.formatDate(medicine.expiryDateMillis)}).",
                    navTarget = "medicine:${medicine.id}"
                )
            }
        }
    }

    /**
     * Persists a lightweight snapshot of medicine & purchase priority metadata and schedules a daily
     * offline-ready background check so expirations & priority deadlines trigger notifications automatically.
     */
    fun syncTrackedExpiriesAndScheduleDailyCheck(
        context: Context,
        purchases: List<PurchaseRecord>,
        medicines: List<MedicineRecord>,
        profile: UserProfile?
    ) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val purchasesJson = JSONArray().apply {
            purchases.forEach { p ->
                put(
                    JSONObject().apply {
                        put("id", p.id)
                        put("productName", p.productName)
                        put("merchantName", p.merchantName)
                        put("purchasePriceInr", p.purchasePriceInr)
                        put("returnDeadlineMillis", p.returnDeadlineMillis ?: 0L)
                        put("warrantyExpiryMillis", p.warrantyExpiryMillis ?: 0L)
                        put("warrantyReminderSet", p.warrantyReminderSet)
                        put("returnReminderSet", p.returnReminderSet)
                        put("importanceLevel", p.importanceLevel)
                        put("completed", p.completed)
                        put("warrantyClaimActive", p.warrantyClaimActive)
                        put("warrantyClaimFollowUpMillis", p.warrantyClaimFollowUpMillis ?: 0L)
                    }
                )
            }
        }

        val medicinesJson = JSONArray().apply {
            medicines.forEach { m ->
                if (m.expiryDateMillis > 0L) {
                    put(
                        JSONObject().apply {
                            put("id", m.id)
                            put("medicineName", m.medicineName)
                            put("batchNumber", m.batchNumber)
                            put("expiryDateMillis", m.expiryDateMillis)
                            put("reminderEnabled", m.reminderEnabled)
                            put("importanceLevel", m.importanceLevel)
                            put("completed", m.completed)
                        }
                    )
                }
            }
        }

        prefs.edit()
            .putString(KEY_CACHED_PURCHASES, purchasesJson.toString())
            .putString(KEY_CACHED_MEDICINES, medicinesJson.toString())
            .putBoolean(KEY_ALLOW_PURCHASES, profile?.purchaseRemindersEnabled ?: true)
            .putBoolean(KEY_ALLOW_MEDICINES, profile?.medicineRemindersEnabled ?: true)
            .putBoolean(KEY_CRIT_ALERTS, profile?.priorityCriticalAlertsEnabled ?: true)
            .putBoolean(KEY_HIGH_ALERTS, profile?.priorityHighAlertsEnabled ?: true)
            .putBoolean(KEY_MED_DIGEST, profile?.priorityMediumDigestEnabled ?: true)
            .putBoolean(KEY_LOW_MUTED, profile?.priorityLowMuted ?: true)
            .apply()

        scheduleDailyExpiringSoonCheck(appContext)
    }

    fun scheduleDailyExpiringSoonCheck(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, VaultReminderReceiver::class.java).apply {
            action = VaultReminderReceiver.ACTION_CHECK_EXPIRING_SOON
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DAILY_CHECK_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextMorning = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            nextMorning,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    /**
     * Checks stored medicine and warranty expiry dates against [nowMillis] AND runs the
     * Smart Priority Engine notification planner (critical individual alerts, high reminders,
     * limited medium summary, low muted) with strict per-event deduplication.
     */
    fun checkAndNotifyExpiringSoon(
        context: Context,
        purchases: List<PurchaseRecord>,
        medicines: List<MedicineRecord>,
        profile: UserProfile?,
        forceAlert: Boolean = false,
        nowMillis: Long = System.currentTimeMillis()
    ): ExpiringSoonReport {
        val appContext = context.applicationContext
        val report = ExpiringSoonChecker.evaluate(
            purchases = purchases,
            medicines = medicines,
            nowMillis = nowMillis,
            windowDays = ExpiringSoonChecker.EXPIRING_SOON_WINDOW_DAYS,
            allowPurchaseWarranties = profile?.purchaseRemindersEnabled ?: true,
            allowMedicines = profile?.medicineRemindersEnabled ?: true,
            respectPerItemToggles = true
        )

        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_CHECK_MILLIS, nowMillis).apply()

        if (!hasNotificationPermission(appContext)) {
            return report
        }

        if (report.hasAlerts) {
            val signature = ExpiringSoonChecker.buildDailySignature(report, nowMillis)
            val lastSignature = prefs.getString(KEY_LAST_SIGNATURE, null)
            if (forceAlert || lastSignature != signature) {
                dispatchExpiringSoonNotification(appContext, report)
                prefs.edit().putString(KEY_LAST_SIGNATURE, signature).apply()
            }
        }

        val priorityPrefs = PriorityNotificationPreferences.fromProfile(profile)
        dispatchSmartPriorityPlan(
            context = appContext,
            purchases = purchases,
            medicines = medicines,
            priorityPrefs = priorityPrefs,
            forceAlert = forceAlert,
            nowMillis = nowMillis
        )
        return report
    }

    fun checkCachedExpiriesAndNotify(
        context: Context,
        nowMillis: Long = System.currentTimeMillis()
    ): ExpiringSoonReport {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val purchases = parseCachedPurchases(prefs.getString(KEY_CACHED_PURCHASES, null))
        val medicines = parseCachedMedicines(prefs.getString(KEY_CACHED_MEDICINES, null))
        val allowPurchases = prefs.getBoolean(KEY_ALLOW_PURCHASES, true)
        val allowMedicines = prefs.getBoolean(KEY_ALLOW_MEDICINES, true)

        val report = ExpiringSoonChecker.evaluate(
            purchases = purchases,
            medicines = medicines,
            nowMillis = nowMillis,
            windowDays = ExpiringSoonChecker.EXPIRING_SOON_WINDOW_DAYS,
            allowPurchaseWarranties = allowPurchases,
            allowMedicines = allowMedicines,
            respectPerItemToggles = true
        )

        prefs.edit().putLong(KEY_LAST_CHECK_MILLIS, nowMillis).apply()
        if (!hasNotificationPermission(appContext)) {
            return report
        }

        if (report.hasAlerts) {
            val signature = ExpiringSoonChecker.buildDailySignature(report, nowMillis)
            val lastSignature = prefs.getString(KEY_LAST_SIGNATURE, null)
            if (lastSignature != signature) {
                dispatchExpiringSoonNotification(appContext, report)
                prefs.edit().putString(KEY_LAST_SIGNATURE, signature).apply()
            }
        }

        val priorityPrefs = PriorityNotificationPreferences(
            criticalAlertsEnabled = prefs.getBoolean(KEY_CRIT_ALERTS, true),
            highAlertsEnabled = prefs.getBoolean(KEY_HIGH_ALERTS, true),
            mediumDigestEnabled = prefs.getBoolean(KEY_MED_DIGEST, true),
            lowMuted = prefs.getBoolean(KEY_LOW_MUTED, true),
            purchaseAlertsEnabled = allowPurchases,
            medicineAlertsEnabled = allowMedicines
        )
        dispatchSmartPriorityPlan(
            context = appContext,
            purchases = purchases,
            medicines = medicines,
            priorityPrefs = priorityPrefs,
            forceAlert = false,
            nowMillis = nowMillis
        )
        return report
    }

    private fun dispatchSmartPriorityPlan(
        context: Context,
        purchases: List<PurchaseRecord>,
        medicines: List<MedicineRecord>,
        priorityPrefs: PriorityNotificationPreferences,
        forceAlert: Boolean,
        nowMillis: Long
    ) {
        val priorityReport = PriorityEngine.Default.evaluate(purchases, medicines, nowMillis)
        val plan = PriorityNotificationManager.buildPlan(priorityReport, priorityPrefs, nowMillis)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        plan.messages.forEach { msg ->
            if (msg.tier == PriorityTier.MEDIUM && !forceAlert) {
                val lastMediumAt = prefs.getLong(KEY_LAST_MEDIUM_DIGEST_MILLIS, 0L)
                if (lastMediumAt > 0L && (nowMillis - lastMediumAt) < MEDIUM_THROTTLE_MILLIS) {
                    return@forEach
                }
            }
            val eventPrefKey = "$KEY_DISPATCHED_EVENT_PREFIX${msg.eventKey}"
            val lastSig = prefs.getString(eventPrefKey, null)
            if (!forceAlert && lastSig == msg.signature) {
                return@forEach
            }

            VaultReminderReceiver.showNotification(
                context = context,
                notificationId = msg.notificationId,
                title = msg.title,
                body = msg.body,
                navTarget = msg.navTarget,
                inboxLines = msg.inboxLines
            )
            editor.putString(eventPrefKey, msg.signature)
            if (msg.tier == PriorityTier.MEDIUM) {
                editor.putLong(KEY_LAST_MEDIUM_DIGEST_MILLIS, nowMillis)
            }
        }
        editor.apply()
    }

    private fun dispatchExpiringSoonNotification(
        context: Context,
        report: ExpiringSoonReport
    ) {
        val items = report.items
        if (items.isEmpty()) return

        if (items.size == 1) {
            val single = items.first()
            val moduleLabel = if (single.module == VaultModule.MEDICINE) "Medicine" else "Warranty"
            val countdown = DateAndCurrencyUtils.formatCountdown(single.daysRemaining)
            val dateLabel = DateAndCurrencyUtils.formatDate(single.expiryMillis)
            VaultReminderReceiver.showNotification(
                context = context,
                notificationId = EXPIRING_SOON_NOTIFICATION_ID,
                title = "Expiring Soon: ${single.title}",
                body = "$moduleLabel (${single.detail}) expires $countdown ($dateLabel).",
                navTarget = single.navTarget
            )
        } else {
            val medCount = report.medicineItems.size
            val warrantyCount = report.warrantyItems.size
            val summaryParts = buildList {
                if (medCount > 0) add("$medCount ${if (medCount == 1) "medicine" else "medicines"}")
                if (warrantyCount > 0) add("$warrantyCount ${if (warrantyCount == 1) "warranty" else "warranties"}")
            }
            val title = "Expiring Soon (${items.size} items within 7 days)"
            val body = "${summaryParts.joinToString(" & ")} nearing expiration. Tap to review."
            val lines = items.map { item ->
                val tag = if (item.module == VaultModule.MEDICINE) "Med" else "Warranty"
                val countdown = DateAndCurrencyUtils.formatCountdown(item.daysRemaining)
                "$tag • ${item.title} ($countdown)"
            }
            VaultReminderReceiver.showNotification(
                context = context,
                notificationId = EXPIRING_SOON_NOTIFICATION_ID,
                title = title,
                body = body,
                navTarget = "notifications",
                inboxLines = lines
            )
        }
    }

    fun sendImmediateTestReminder(context: Context, navTarget: String = "") {
        if (!hasNotificationPermission(context)) return
        val intent = Intent(context, VaultReminderReceiver::class.java).apply {
            putExtra(VaultReminderReceiver.EXTRA_TITLE, "VAULTORA Alerts Active")
            putExtra(VaultReminderReceiver.EXTRA_BODY, "Smart Priority Engine, return, warranty, and expiry alerts are active.")
            putExtra(VaultReminderReceiver.EXTRA_NAV_TARGET, navTarget)
            putExtra(VaultReminderReceiver.EXTRA_NOTIFICATION_ID, 9001)
        }
        context.sendBroadcast(intent)
    }

    private fun scheduleAlarm(
        context: Context,
        requestCode: Int,
        triggerAtMillis: Long,
        title: String,
        body: String,
        navTarget: String
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, VaultReminderReceiver::class.java).apply {
            putExtra(VaultReminderReceiver.EXTRA_TITLE, title)
            putExtra(VaultReminderReceiver.EXTRA_BODY, body)
            putExtra(VaultReminderReceiver.EXTRA_NAV_TARGET, navTarget)
            putExtra(VaultReminderReceiver.EXTRA_NOTIFICATION_ID, requestCode)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }

    private fun parseCachedPurchases(rawJson: String?): List<PurchaseRecord> {
        if (rawJson.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(rawJson)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    add(
                        PurchaseRecord(
                            id = obj.optString("id"),
                            productName = obj.optString("productName"),
                            merchantName = obj.optString("merchantName"),
                            purchasePriceInr = obj.optDouble("purchasePriceInr", 0.0),
                            returnDeadlineMillis = obj.optLong("returnDeadlineMillis").takeIf { it > 0L },
                            warrantyExpiryMillis = obj.optLong("warrantyExpiryMillis").takeIf { it > 0L },
                            warrantyReminderSet = obj.optBoolean("warrantyReminderSet", true),
                            returnReminderSet = obj.optBoolean("returnReminderSet", true),
                            importanceLevel = obj.optString("importanceLevel"),
                            completed = obj.optBoolean("completed", false),
                            warrantyClaimActive = obj.optBoolean("warrantyClaimActive", false),
                            warrantyClaimFollowUpMillis = obj.optLong("warrantyClaimFollowUpMillis").takeIf { it > 0L }
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseCachedMedicines(rawJson: String?): List<MedicineRecord> {
        if (rawJson.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(rawJson)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    add(
                        MedicineRecord(
                            id = obj.optString("id"),
                            medicineName = obj.optString("medicineName"),
                            batchNumber = obj.optString("batchNumber"),
                            expiryDateMillis = obj.optLong("expiryDateMillis"),
                            reminderEnabled = obj.optBoolean("reminderEnabled", true),
                            importanceLevel = obj.optString("importanceLevel"),
                            completed = obj.optBoolean("completed", false)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
