package com.example.data

import com.example.model.AttachmentType
import com.example.model.MedicineRecord
import com.example.model.PurchaseRecord
import java.util.concurrent.TimeUnit

object SampleVaultData {
    fun samplePurchases(uid: String, nowMillis: Long = System.currentTimeMillis()): List<PurchaseRecord> {
        val day = TimeUnit.DAYS.toMillis(1)
        return listOf(
            PurchaseRecord(
                userId = uid,
                productName = "Sony WH-1000XM5 Headphones",
                merchantName = "Amazon India",
                purchasePriceInr = 26990.0,
                purchaseDateMillis = nowMillis - 4 * day,
                invoiceNumber = "AMZ-IN-408-99214",
                category = "Electronics",
                attachmentType = AttachmentType.NONE.name,
                returnDeadlineMillis = nowMillis + 3 * day,
                warrantyDurationMonths = 12L,
                warrantyExpiryMillis = nowMillis + 361 * day,
                notes = "Midnight Blue Edition, 7-day replacement window",
                returnReminderSet = true,
                warrantyReminderSet = true
            ),
            PurchaseRecord(
                userId = uid,
                productName = "Apple MacBook Air 13\" M3",
                merchantName = "Croma",
                purchasePriceInr = 114900.0,
                purchaseDateMillis = nowMillis - 350 * day,
                invoiceNumber = "CRM-BLR-2025-8831",
                category = "Electronics",
                attachmentType = AttachmentType.NONE.name,
                returnDeadlineMillis = nowMillis - 343 * day,
                warrantyDurationMonths = 12L,
                warrantyExpiryMillis = nowMillis + 15 * day,
                notes = "16GB Unified Memory, 512GB SSD",
                returnReminderSet = false,
                warrantyReminderSet = true
            ),
            PurchaseRecord(
                userId = uid,
                productName = "Dyson V12 Detect Slim",
                merchantName = "Reliance Digital",
                purchasePriceInr = 55900.0,
                purchaseDateMillis = nowMillis - 9 * day,
                invoiceNumber = "RLD-MUM-77412",
                category = "Home Appliances",
                attachmentType = AttachmentType.NONE.name,
                returnDeadlineMillis = nowMillis + 5 * day,
                warrantyDurationMonths = 24L,
                warrantyExpiryMillis = nowMillis + 721 * day,
                notes = "2-year manufacturer warranty registered",
                returnReminderSet = true,
                warrantyReminderSet = true
            ),
            PurchaseRecord(
                userId = uid,
                productName = "Philips Air Fryer HD9252",
                merchantName = "Flipkart",
                purchasePriceInr = 8499.0,
                purchaseDateMillis = nowMillis - 420 * day,
                invoiceNumber = "FLK-OD-551902",
                category = "Kitchen",
                attachmentType = AttachmentType.NONE.name,
                returnDeadlineMillis = nowMillis - 410 * day,
                warrantyDurationMonths = 12L,
                warrantyExpiryMillis = nowMillis - 55 * day,
                notes = "Standard 1-year domestic warranty",
                returnReminderSet = false,
                warrantyReminderSet = false
            )
        )
    }

    fun sampleMedicines(uid: String, nowMillis: Long = System.currentTimeMillis()): List<MedicineRecord> {
        val day = TimeUnit.DAYS.toMillis(1)
        return listOf(
            MedicineRecord(
                userId = uid,
                medicineName = "Dolo 650mg Tablets",
                batchNumber = "DL26-884A",
                expiryDateMillis = nowMillis + 12 * day,
                purchaseDateMillis = nowMillis - 120 * day,
                quantity = "2 Strips (30 tabs)",
                storageNote = "First Aid Cabinet - Upper Shelf",
                reminderEnabled = true,
                reminderDaysBefore = 14L
            ),
            MedicineRecord(
                userId = uid,
                medicineName = "Allegra 120mg Strip",
                batchNumber = "ALG-912C",
                expiryDateMillis = nowMillis + 240 * day,
                purchaseDateMillis = nowMillis - 45 * day,
                quantity = "1 Strip (10 tabs)",
                storageNote = "Cool dry drawer",
                reminderEnabled = true,
                reminderDaysBefore = 14L
            ),
            MedicineRecord(
                userId = uid,
                medicineName = "Vitamin D3 60K Capsules",
                batchNumber = "VD3-4410K",
                expiryDateMillis = nowMillis + 6 * day,
                purchaseDateMillis = nowMillis - 180 * day,
                quantity = "4 Softgels",
                storageNote = "Bedroom Organizer",
                reminderEnabled = true,
                reminderDaysBefore = 14L
            ),
            MedicineRecord(
                userId = uid,
                medicineName = "Betadine Antiseptic Ointment",
                batchNumber = "BTD-201X",
                expiryDateMillis = nowMillis - 18 * day,
                purchaseDateMillis = nowMillis - 400 * day,
                quantity = "1 Tube (20g)",
                storageNote = "Needs safe disposal",
                reminderEnabled = false,
                reminderDaysBefore = 14L
            )
        )
    }
}
