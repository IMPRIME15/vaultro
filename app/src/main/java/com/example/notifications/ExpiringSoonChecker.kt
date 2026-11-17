package com.example.notifications

import com.example.model.MedicineRecord
import com.example.model.PurchaseRecord
import com.example.model.VaultModule
import com.example.util.DateAndCurrencyUtils

data class ExpiringSoonItem(
    val id: String,
    val module: VaultModule,
    val title: String,
    val detail: String,
    val expiryMillis: Long,
    val daysRemaining: Long,
    val navTarget: String,
    val alertKey: String
)

data class ExpiringSoonReport(
    val items: List<ExpiringSoonItem> = emptyList(),
    val evaluatedAtMillis: Long = 0L,
    val windowDays: Long = ExpiringSoonChecker.EXPIRING_SOON_WINDOW_DAYS
) {
    val medicineItems: List<ExpiringSoonItem>
        get() = items.filter { it.module == VaultModule.MEDICINE }

    val warrantyItems: List<ExpiringSoonItem>
        get() = items.filter { it.module == VaultModule.PURCHASE }

    val hasAlerts: Boolean
        get() = items.isNotEmpty()
}

object ExpiringSoonChecker {
    const val EXPIRING_SOON_WINDOW_DAYS = 7L

    /**
     * Checks stored medicine expiry dates and purchase warranty expiry dates against [nowMillis]
     * and returns all items expiring within [windowDays] (inclusive of today: 0..7 days).
     */
    fun evaluate(
        purchases: List<PurchaseRecord>,
        medicines: List<MedicineRecord>,
        nowMillis: Long = System.currentTimeMillis(),
        windowDays: Long = EXPIRING_SOON_WINDOW_DAYS,
        allowPurchaseWarranties: Boolean = true,
        allowMedicines: Boolean = true,
        respectPerItemToggles: Boolean = true
    ): ExpiringSoonReport {
        val alerts = mutableListOf<ExpiringSoonItem>()

        if (allowMedicines) {
            medicines.forEach { medicine ->
                val enabled = !respectPerItemToggles || medicine.reminderEnabled
                if (!medicine.completed && enabled && medicine.expiryDateMillis > 0L) {
                    val daysRemaining = DateAndCurrencyUtils.daysFromNow(
                        targetMillis = medicine.expiryDateMillis,
                        nowMillis = nowMillis
                    )
                    if (daysRemaining in 0L..windowDays) {
                        val batchText = medicine.batchNumber.takeIf { it.isNotBlank() }?.let { "Batch $it" }
                            ?: "Medicine Vault"
                        alerts.add(
                            ExpiringSoonItem(
                                id = medicine.id,
                                module = VaultModule.MEDICINE,
                                title = medicine.medicineName.ifBlank { "Stored Medicine" },
                                detail = batchText,
                                expiryMillis = medicine.expiryDateMillis,
                                daysRemaining = daysRemaining,
                                navTarget = "medicine:${medicine.id}",
                                alertKey = "medicine_expiring_${medicine.id}"
                            )
                        )
                    }
                }
            }
        }

        if (allowPurchaseWarranties) {
            purchases.forEach { purchase ->
                val enabled = !respectPerItemToggles || purchase.warrantyReminderSet
                val warrantyExpiry = purchase.warrantyExpiryMillis
                if (!purchase.completed && enabled && warrantyExpiry != null && warrantyExpiry > 0L) {
                    val daysRemaining = DateAndCurrencyUtils.daysFromNow(
                        targetMillis = warrantyExpiry,
                        nowMillis = nowMillis
                    )
                    if (daysRemaining in 0L..windowDays) {
                        val merchantText = purchase.merchantName.takeIf { it.isNotBlank() }
                            ?: "Purchase Warranty"
                        alerts.add(
                            ExpiringSoonItem(
                                id = purchase.id,
                                module = VaultModule.PURCHASE,
                                title = purchase.productName.ifBlank { "Warranty Coverage" },
                                detail = merchantText,
                                expiryMillis = warrantyExpiry,
                                daysRemaining = daysRemaining,
                                navTarget = "purchase:${purchase.id}",
                                alertKey = "purchase_warranty_${purchase.id}"
                            )
                        )
                    }
                }
            }
        }

        val sorted = alerts.sortedWith(
            compareBy<ExpiringSoonItem> { it.daysRemaining }
                .thenBy { it.expiryMillis }
                .thenBy { it.title.lowercase() }
        )
        return ExpiringSoonReport(
            items = sorted,
            evaluatedAtMillis = nowMillis,
            windowDays = windowDays
        )
    }

    fun buildDailySignature(
        report: ExpiringSoonReport,
        nowMillis: Long = report.evaluatedAtMillis
    ): String {
        val dayKey = DateAndCurrencyUtils.formatIsoDate(nowMillis)
        val itemSig = report.items.joinToString("|") { item ->
            "${item.module.name}:${item.id}:${item.daysRemaining}"
        }
        return "${dayKey}__$itemSig"
    }
}
