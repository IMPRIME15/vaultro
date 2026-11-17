package com.example

import com.example.model.MedicineRecord
import com.example.model.PurchaseRecord
import com.example.model.VaultModule
import com.example.notifications.ExpiringSoonChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ExpiringSoonCheckerTest {

    private fun middayMillis(year: Int, monthZeroBased: Int, day: Int): Long {
        return Calendar.getInstance().apply {
            set(year, monthZeroBased, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun evaluate_includesMedicinesAndWarrantiesWithin7DaysInclusiveAndExcludesOutside() {
        val now = middayMillis(2026, Calendar.NOVEMBER, 17)
        val dayMs = TimeUnit.DAYS.toMillis(1L)

        val medicines = listOf(
            MedicineRecord(
                id = "med_expired",
                medicineName = "Expired Drops",
                batchNumber = "B-00",
                expiryDateMillis = now - dayMs, // -1 day
                reminderEnabled = true
            ),
            MedicineRecord(
                id = "med_today",
                medicineName = "Vitamin D3",
                batchNumber = "B-01",
                expiryDateMillis = now, // 0 days
                reminderEnabled = true
            ),
            MedicineRecord(
                id = "med_7d",
                medicineName = "Paracetamol 500mg",
                batchNumber = "B-07",
                expiryDateMillis = now + 7 * dayMs, // +7 days
                reminderEnabled = true
            ),
            MedicineRecord(
                id = "med_8d",
                medicineName = "Antacid Syrup",
                batchNumber = "B-08",
                expiryDateMillis = now + 8 * dayMs, // +8 days
                reminderEnabled = true
            )
        )

        val purchases = listOf(
            PurchaseRecord(
                id = "pur_expired",
                productName = "Old Earbuds",
                merchantName = "Flipkart",
                purchaseDateMillis = now - 370 * dayMs,
                warrantyExpiryMillis = now - 2 * dayMs, // -2 days
                warrantyReminderSet = true
            ),
            PurchaseRecord(
                id = "pur_3d",
                productName = "Sony Headphones",
                merchantName = "Croma",
                purchaseDateMillis = now - 362 * dayMs,
                warrantyExpiryMillis = now + 3 * dayMs, // +3 days
                warrantyReminderSet = true
            ),
            PurchaseRecord(
                id = "pur_7d",
                productName = "Air Purifier",
                merchantName = "Amazon",
                purchaseDateMillis = now - 358 * dayMs,
                warrantyExpiryMillis = now + 7 * dayMs, // +7 days
                warrantyReminderSet = true
            ),
            PurchaseRecord(
                id = "pur_10d",
                productName = "Laptop",
                merchantName = "Apple Store",
                purchaseDateMillis = now - 355 * dayMs,
                warrantyExpiryMillis = now + 10 * dayMs, // +10 days
                warrantyReminderSet = true
            )
        )

        val report = ExpiringSoonChecker.evaluate(
            purchases = purchases,
            medicines = medicines,
            nowMillis = now
        )

        assertTrue(report.hasAlerts)
        assertEquals(4, report.items.size)
        assertEquals(2, report.medicineItems.size)
        assertEquals(2, report.warrantyItems.size)

        // Sorted ascending by daysRemaining
        assertEquals(listOf("med_today", "pur_3d", "pur_7d", "med_7d"), report.items.map { it.id })
        assertEquals(listOf(0L, 3L, 7L, 7L), report.items.map { it.daysRemaining })
        assertEquals("medicine:med_today", report.items[0].navTarget)
        assertEquals(VaultModule.PURCHASE, report.items[1].module)
    }

    @Test
    fun evaluate_respectsModuleAndItemReminderToggles() {
        val now = middayMillis(2026, Calendar.NOVEMBER, 17)
        val dayMs = TimeUnit.DAYS.toMillis(1L)

        val medEnabled = MedicineRecord(
            id = "med_on",
            medicineName = "Enabled Med",
            expiryDateMillis = now + 2 * dayMs,
            reminderEnabled = true
        )
        val medDisabled = MedicineRecord(
            id = "med_off",
            medicineName = "Muted Med",
            expiryDateMillis = now + 2 * dayMs,
            reminderEnabled = false
        )
        val warrantyDisabled = PurchaseRecord(
            id = "pur_off",
            productName = "Muted Warranty",
            merchantName = "Store",
            warrantyExpiryMillis = now + 2 * dayMs,
            warrantyReminderSet = false
        )

        val reportDefault = ExpiringSoonChecker.evaluate(
            purchases = listOf(warrantyDisabled),
            medicines = listOf(medEnabled, medDisabled),
            nowMillis = now
        )
        assertEquals(listOf("med_on"), reportDefault.items.map { it.id })

        val reportMedicinesMuted = ExpiringSoonChecker.evaluate(
            purchases = listOf(warrantyDisabled),
            medicines = listOf(medEnabled, medDisabled),
            nowMillis = now,
            allowMedicines = false,
            allowPurchaseWarranties = false
        )
        assertFalse(reportMedicinesMuted.hasAlerts)
    }

    @Test
    fun buildDailySignature_changesAcrossDaysOrNewExpiringItems() {
        val day1 = middayMillis(2026, Calendar.NOVEMBER, 17)
        val day2 = middayMillis(2026, Calendar.NOVEMBER, 18)
        val dayMs = TimeUnit.DAYS.toMillis(1L)

        val med = MedicineRecord(
            id = "med_1",
            medicineName = "Vitamin C",
            expiryDateMillis = day1 + 4 * dayMs,
            reminderEnabled = true
        )

        val reportDay1 = ExpiringSoonChecker.evaluate(emptyList(), listOf(med), nowMillis = day1)
        val reportDay2 = ExpiringSoonChecker.evaluate(emptyList(), listOf(med), nowMillis = day2)

        val sig1 = ExpiringSoonChecker.buildDailySignature(reportDay1)
        val sig1Repeat = ExpiringSoonChecker.buildDailySignature(reportDay1)
        val sig2 = ExpiringSoonChecker.buildDailySignature(reportDay2)

        assertEquals(sig1, sig1Repeat)
        assertNotEquals(sig1, sig2)
    }
}
