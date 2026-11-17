package com.example.util

import com.example.model.ExpiryStatus
import com.example.model.MedicineRecord
import com.example.model.PurchaseRecord
import com.example.model.ReturnStatus
import com.example.model.WarrantyStatus
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object DateAndCurrencyUtils {
    private val inrLocale = Locale("en", "IN")

    fun formatInr(amount: Double): String {
        val formatter = NumberFormat.getCurrencyInstance(inrLocale).apply {
            maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
            minimumFractionDigits = 0
        }
        return formatter.format(amount)
    }

    fun formatDate(millis: Long?): String {
        if (millis == null || millis <= 0L) return "Not set"
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    fun formatIsoDate(millis: Long?): String {
        if (millis == null || millis <= 0L) return ""
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date(millis))
    }

    fun parseIsoDateToMillis(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        val formats = listOf("yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy", "dd MMM yyyy", "MMM dd, yyyy")
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
                val parsed = sdf.parse(dateStr.trim())
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun addMonthsMillis(startMillis: Long, months: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = startMillis
            add(Calendar.MONTH, months)
        }
        return cal.timeInMillis
    }

    fun daysFromNow(targetMillis: Long, nowMillis: Long = System.currentTimeMillis()): Long {
        val startCal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val targetCal = Calendar.getInstance().apply {
            timeInMillis = targetMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return TimeUnit.MILLISECONDS.toDays(targetCal.timeInMillis - startCal.timeInMillis)
    }

    fun formatCountdown(daysDelta: Long): String = when {
        daysDelta < -1L -> "${-daysDelta}d ago"
        daysDelta == -1L -> "Yesterday"
        daysDelta == 0L -> "Today"
        daysDelta == 1L -> "Tomorrow"
        daysDelta < 30L -> "In ${daysDelta}d"
        daysDelta < 365L -> "In ${daysDelta / 30}mo ${daysDelta % 30}d"
        else -> "In ${daysDelta / 365}y ${(daysDelta % 365) / 30}mo"
    }

    fun medicineExpiryStatus(
        medicine: MedicineRecord,
        soonWindowDays: Long = 30L,
        nowMillis: Long = System.currentTimeMillis()
    ): ExpiryStatus {
        val days = daysFromNow(medicine.expiryDateMillis, nowMillis)
        return when {
            days < 0L -> ExpiryStatus.EXPIRED
            days <= soonWindowDays -> ExpiryStatus.EXPIRING_SOON
            else -> ExpiryStatus.VALID
        }
    }

    fun returnStatus(
        purchase: PurchaseRecord,
        soonWindowDays: Long = 5L,
        nowMillis: Long = System.currentTimeMillis()
    ): ReturnStatus {
        val deadline = purchase.returnDeadlineMillis ?: return ReturnStatus.NOT_TRACKED
        val days = daysFromNow(deadline, nowMillis)
        return when {
            days < 0L -> ReturnStatus.PAST
            days <= soonWindowDays -> ReturnStatus.CLOSING_SOON
            else -> ReturnStatus.ELIGIBLE
        }
    }

    fun warrantyStatus(
        purchase: PurchaseRecord,
        soonWindowDays: Long = 30L,
        nowMillis: Long = System.currentTimeMillis()
    ): WarrantyStatus {
        val expiry = purchase.warrantyExpiryMillis ?: return WarrantyStatus.NOT_TRACKED
        val days = daysFromNow(expiry, nowMillis)
        return when {
            days < 0L -> WarrantyStatus.EXPIRED
            days <= soonWindowDays -> WarrantyStatus.EXPIRING_SOON
            else -> WarrantyStatus.ACTIVE
        }
    }
}
