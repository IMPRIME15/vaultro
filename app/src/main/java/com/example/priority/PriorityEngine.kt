package com.example.priority

import com.example.model.ImportanceLevel
import com.example.model.MedicineRecord
import com.example.model.PriorityTier
import com.example.model.PurchaseRecord
import com.example.model.VaultModule
import com.example.util.DateAndCurrencyUtils
import kotlin.math.min

enum class PrioritySourceKind(val displayLabel: String) {
    WARRANTY_CLAIM_FOLLOW_UP("Claim Follow-Up"),
    RETURN_DEADLINE("Return Deadline"),
    WARRANTY_EXPIRY("Warranty Expiry"),
    PURCHASE_RECORD("Purchase Record"),
    MEDICINE_EXPIRY("Medicine Expiry")
}

data class PriorityItem(
    val id: String,
    val recordKey: String,
    val module: VaultModule,
    val sourceKind: PrioritySourceKind,
    val title: String,
    val subtitle: String,
    val deadlineMillis: Long?,
    val remainingDays: Long?,
    val isOverdue: Boolean,
    val isExpired: Boolean,
    val isCompleted: Boolean,
    val isExplicitImportance: Boolean,
    val importanceLevel: ImportanceLevel,
    val priorityTier: PriorityTier,
    val priorityScore: Double,
    val priorityStatusLabel: String,
    val completedAtMillis: Long? = null
)

data class PriorityCenterReport(
    val evaluatedAtMillis: Long = 0L,
    val activeItems: List<PriorityItem> = emptyList(),
    val criticalItems: List<PriorityItem> = emptyList(),
    val highItems: List<PriorityItem> = emptyList(),
    val mediumItems: List<PriorityItem> = emptyList(),
    val lowItems: List<PriorityItem> = emptyList(),
    val overdueItems: List<PriorityItem> = emptyList(),
    val completedItems: List<PriorityItem> = emptyList(),
    val expiredMedicines: List<PriorityItem> = emptyList(),
    val purchasePriorityMap: Map<String, PriorityItem> = emptyMap(),
    val medicinePriorityMap: Map<String, PriorityItem> = emptyMap()
) {
    val activeCount: Int get() = activeItems.size

    fun itemsForTier(tier: PriorityTier): List<PriorityItem> = when (tier) {
        PriorityTier.CRITICAL -> criticalItems
        PriorityTier.HIGH -> highItems
        PriorityTier.MEDIUM -> mediumItems
        PriorityTier.LOW -> lowItems
    }

    fun countForTier(tier: PriorityTier): Int = itemsForTier(tier).size
}

/**
 * Vaultora Smart Priority Engine (SPE).
 *
 * Implements an offline-first, deterministic weighted priority algorithm inspired by CPU
 * priority-queue scheduling and cache aging. Ensures genuinely critical and high-importance
 * records remain ahead of low-importance items even when deadlines tighten, while boosting
 * approaching or overdue deadlines within each importance band.
 */
class PriorityEngine {

    fun evaluate(
        purchases: List<PurchaseRecord>,
        medicines: List<MedicineRecord>,
        nowMillis: Long = System.currentTimeMillis()
    ): PriorityCenterReport {
        val active = mutableListOf<PriorityItem>()
        val completed = mutableListOf<PriorityItem>()
        val expiredMeds = mutableListOf<PriorityItem>()
        val purchaseMap = mutableMapOf<String, PriorityItem>()
        val medicineMap = mutableMapOf<String, PriorityItem>()

        purchases.forEach { purchase ->
            val item = evaluatePurchase(purchase, nowMillis)
            purchaseMap[purchase.id] = item
            if (item.isCompleted) {
                completed.add(item)
            } else {
                active.add(item)
            }
        }

        medicines.forEach { medicine ->
            val item = evaluateMedicine(medicine, nowMillis)
            medicineMap[medicine.id] = item
            when {
                item.isCompleted -> completed.add(item)
                item.isExpired -> expiredMeds.add(item)
                else -> active.add(item)
            }
        }

        val sortedActive = active.sortedWith(priorityComparator())
        val sortedCompleted = completed.sortedByDescending { it.completedAtMillis ?: it.deadlineMillis ?: 0L }
        val sortedExpiredMeds = expiredMeds.sortedByDescending { it.deadlineMillis ?: 0L }

        return PriorityCenterReport(
            evaluatedAtMillis = nowMillis,
            activeItems = sortedActive,
            criticalItems = sortedActive.filter { it.priorityTier == PriorityTier.CRITICAL },
            highItems = sortedActive.filter { it.priorityTier == PriorityTier.HIGH },
            mediumItems = sortedActive.filter { it.priorityTier == PriorityTier.MEDIUM },
            lowItems = sortedActive.filter { it.priorityTier == PriorityTier.LOW },
            overdueItems = sortedActive.filter { it.isOverdue },
            completedItems = sortedCompleted,
            expiredMedicines = sortedExpiredMeds,
            purchasePriorityMap = purchaseMap,
            medicinePriorityMap = medicineMap
        )
    }

    fun evaluatePurchase(
        purchase: PurchaseRecord,
        nowMillis: Long = System.currentTimeMillis()
    ): PriorityItem {
        val selectedSlot = selectPrimaryPurchaseSlot(purchase, nowMillis)
        val explicitImportance = purchase.resolvedImportanceOrNull()
        val effectiveImportance = explicitImportance ?: inferPurchaseImportance(
            purchase = purchase,
            slot = selectedSlot
        )

        val remainingDays = selectedSlot.remainingDays
        val isOverdue = !purchase.completed && remainingDays != null && remainingDays < 0L
        val score = calculateWeightedScore(
            importance = effectiveImportance,
            remainingDays = remainingDays,
            isOverdue = isOverdue,
            sourceKind = selectedSlot.sourceKind
        )
        val tier = resolvePriorityTier(
            importance = effectiveImportance,
            remainingDays = remainingDays,
            isOverdue = isOverdue,
            sourceKind = selectedSlot.sourceKind
        )
        val statusLabel = buildStatusLabel(
            isCompleted = purchase.completed,
            isExpiredMedicine = false,
            isOverdue = isOverdue,
            remainingDays = remainingDays,
            tier = tier
        )

        val subtitleParts = buildList {
            if (purchase.merchantName.isNotBlank()) add(purchase.merchantName.trim())
            add(selectedSlot.sourceKind.displayLabel)
        }

        return PriorityItem(
            id = purchase.id,
            recordKey = "purchase_${purchase.id}",
            module = VaultModule.PURCHASE,
            sourceKind = selectedSlot.sourceKind,
            title = purchase.productName.ifBlank { "Saved Receipt" },
            subtitle = subtitleParts.joinToString(" • "),
            deadlineMillis = selectedSlot.deadlineMillis,
            remainingDays = remainingDays,
            isOverdue = isOverdue,
            isExpired = false,
            isCompleted = purchase.completed,
            isExplicitImportance = explicitImportance != null,
            importanceLevel = effectiveImportance,
            priorityTier = tier,
            priorityScore = score,
            priorityStatusLabel = statusLabel,
            completedAtMillis = purchase.completedAtMillis
        )
    }

    fun evaluateMedicine(
        medicine: MedicineRecord,
        nowMillis: Long = System.currentTimeMillis()
    ): PriorityItem {
        val validExpiry = medicine.expiryDateMillis.takeIf { it > 0L }
        val remainingDays = validExpiry?.let { DateAndCurrencyUtils.daysFromNow(it, nowMillis) }
        val isExpired = !medicine.completed && remainingDays != null && remainingDays < 0L

        val explicitImportance = medicine.resolvedImportanceOrNull()
        val effectiveImportance = explicitImportance ?: inferMedicineImportance(remainingDays)

        val score = calculateWeightedScore(
            importance = effectiveImportance,
            remainingDays = remainingDays,
            isOverdue = false,
            sourceKind = PrioritySourceKind.MEDICINE_EXPIRY
        )
        val tier = resolvePriorityTier(
            importance = effectiveImportance,
            remainingDays = remainingDays,
            isOverdue = false,
            sourceKind = PrioritySourceKind.MEDICINE_EXPIRY
        )
        val statusLabel = buildStatusLabel(
            isCompleted = medicine.completed,
            isExpiredMedicine = isExpired,
            isOverdue = false,
            remainingDays = remainingDays,
            tier = tier
        )

        val batchLabel = medicine.batchNumber.takeIf { it.isNotBlank() }?.let { "Batch $it" } ?: "Medicine Storage"

        return PriorityItem(
            id = medicine.id,
            recordKey = "medicine_${medicine.id}",
            module = VaultModule.MEDICINE,
            sourceKind = PrioritySourceKind.MEDICINE_EXPIRY,
            title = medicine.medicineName.ifBlank { "Stored Medicine" },
            subtitle = "$batchLabel • Expiry Check",
            deadlineMillis = validExpiry,
            remainingDays = remainingDays,
            isOverdue = false,
            isExpired = isExpired,
            isCompleted = medicine.completed,
            isExplicitImportance = explicitImportance != null,
            importanceLevel = effectiveImportance,
            priorityTier = tier,
            priorityScore = score,
            priorityStatusLabel = statusLabel,
            completedAtMillis = medicine.completedAtMillis
        )
    }

    fun sortActivePurchases(
        purchases: List<PurchaseRecord>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<PurchaseRecord> {
        return purchases
            .filter { !it.completed }
            .sortedWith(
                compareByDescending<PurchaseRecord> { evaluatePurchase(it, nowMillis).priorityScore }
                    .thenBy { evaluatePurchase(it, nowMillis).remainingDays ?: Long.MAX_VALUE }
                    .thenBy { it.productName.lowercase() }
            )
    }

    fun sortActiveMedicines(
        medicines: List<MedicineRecord>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<MedicineRecord> {
        return medicines
            .filter { !it.completed && DateAndCurrencyUtils.daysFromNow(it.expiryDateMillis, nowMillis) >= 0L }
            .sortedWith(
                compareByDescending<MedicineRecord> { evaluateMedicine(it, nowMillis).priorityScore }
                    .thenBy { it.expiryDateMillis }
                    .thenBy { it.medicineName.lowercase() }
            )
    }

    internal fun calculateWeightedScore(
        importance: ImportanceLevel,
        remainingDays: Long?,
        isOverdue: Boolean,
        sourceKind: PrioritySourceKind
    ): Double {
        val baseWeight = importance.baseWeight
        val urgencyBonus = calculateTimeUrgencyBonus(remainingDays, isOverdue)
        val claimFollowUpBoost = if (sourceKind == PrioritySourceKind.WARRANTY_CLAIM_FOLLOW_UP) 35.0 else 0.0
        return baseWeight + urgencyBonus + claimFollowUpBoost
    }

    /**
     * Bounded strictly below the 250..350pt gap between adjacent [ImportanceLevel] bands so a
     * low-importance record with an immediate deadline never blindly outranks a genuinely critical
     * or high-importance record.
     */
    internal fun calculateTimeUrgencyBonus(
        remainingDays: Long?,
        isOverdue: Boolean
    ): Double {
        if (remainingDays == null) return 0.0
        return when {
            remainingDays < 0L -> {
                val overdueDays = -remainingDays
                if (isOverdue && overdueDays <= 30L) {
                    215.0 + min(overdueDays.toDouble() * 1.0, 25.0)
                } else {
                    // Older unresolved overdue deadlines remain highlighted as overdue without
                    // eclipsing fresh immediate deadlines in the same importance band.
                    maxOf(45.0, 175.0 - (overdueDays - 30L) * 0.4)
                }
            }
            remainingDays == 0L -> 210.0
            remainingDays in 1L..3L -> 195.0 - (remainingDays - 1L) * 8.0
            remainingDays in 4L..7L -> 170.0 - (remainingDays - 4L) * 9.0
            remainingDays in 8L..14L -> 130.0 - (remainingDays - 8L) * 5.0
            remainingDays in 15L..30L -> 92.0 - (remainingDays - 15L) * 2.5
            remainingDays in 31L..90L -> 52.0 - (remainingDays - 31L) * 0.6
            else -> maxOf(4.0, 16.0 - (remainingDays - 90L) * 0.03)
        }
    }

    internal fun resolvePriorityTier(
        importance: ImportanceLevel,
        remainingDays: Long?,
        isOverdue: Boolean,
        sourceKind: PrioritySourceKind
    ): PriorityTier {
        val isClaimFollowUp = sourceKind == PrioritySourceKind.WARRANTY_CLAIM_FOLLOW_UP
        val approachingSoon = remainingDays != null && remainingDays <= 30L
        val immediate = isOverdue || (remainingDays != null && remainingDays <= 3L)
        val withinWeek = isOverdue || (remainingDays != null && remainingDays <= 7L)

        return when (importance) {
            ImportanceLevel.CRITICAL -> {
                if (approachingSoon || isOverdue || isClaimFollowUp || remainingDays == null) {
                    PriorityTier.CRITICAL
                } else {
                    PriorityTier.HIGH
                }
            }
            ImportanceLevel.HIGH -> {
                when {
                    immediate || (isClaimFollowUp && withinWeek) -> PriorityTier.CRITICAL
                    else -> PriorityTier.HIGH
                }
            }
            ImportanceLevel.MEDIUM -> {
                when {
                    withinWeek -> PriorityTier.HIGH
                    remainingDays == null || remainingDays <= 90L -> PriorityTier.MEDIUM
                    else -> PriorityTier.MEDIUM
                }
            }
            ImportanceLevel.LOW -> {
                when {
                    withinWeek -> PriorityTier.MEDIUM
                    else -> PriorityTier.LOW
                }
            }
        }
    }

    private data class PurchaseDeadlineSlot(
        val sourceKind: PrioritySourceKind,
        val deadlineMillis: Long?,
        val remainingDays: Long?
    )

    private fun selectPrimaryPurchaseSlot(
        purchase: PurchaseRecord,
        nowMillis: Long
    ): PurchaseDeadlineSlot {
        val candidates = mutableListOf<PurchaseDeadlineSlot>()

        val claimMillis = purchase.warrantyClaimFollowUpMillis?.takeIf { it > 0L }
        if (purchase.warrantyClaimActive || claimMillis != null) {
            candidates.add(
                PurchaseDeadlineSlot(
                    sourceKind = PrioritySourceKind.WARRANTY_CLAIM_FOLLOW_UP,
                    deadlineMillis = claimMillis,
                    remainingDays = claimMillis?.let { DateAndCurrencyUtils.daysFromNow(it, nowMillis) }
                )
            )
        }

        val returnMillis = purchase.returnDeadlineMillis?.takeIf { it > 0L }
        if (returnMillis != null) {
            val returnDays = DateAndCurrencyUtils.daysFromNow(returnMillis, nowMillis)
            val hasActiveFutureWarranty = purchase.warrantyExpiryMillis?.let {
                it > 0L && DateAndCurrencyUtils.daysFromNow(it, nowMillis) >= 0L
            } == true
            // If a return window closed >30 days ago and an active warranty still exists, focus on the
            // active warranty rather than keeping an old return deadline ahead of active coverage.
            if (!(returnDays < -30L && hasActiveFutureWarranty)) {
                candidates.add(
                    PurchaseDeadlineSlot(
                        sourceKind = PrioritySourceKind.RETURN_DEADLINE,
                        deadlineMillis = returnMillis,
                        remainingDays = returnDays
                    )
                )
            }
        }

        val warrantyMillis = purchase.warrantyExpiryMillis?.takeIf { it > 0L }
        if (warrantyMillis != null) {
            candidates.add(
                PurchaseDeadlineSlot(
                    sourceKind = PrioritySourceKind.WARRANTY_EXPIRY,
                    deadlineMillis = warrantyMillis,
                    remainingDays = DateAndCurrencyUtils.daysFromNow(warrantyMillis, nowMillis)
                )
            )
        }

        if (candidates.isEmpty()) {
            return PurchaseDeadlineSlot(
                sourceKind = PrioritySourceKind.PURCHASE_RECORD,
                deadlineMillis = null,
                remainingDays = null
            )
        }

        return candidates.maxWithOrNull(
            compareBy<PurchaseDeadlineSlot> { slot ->
                val isOverdue = !purchase.completed && slot.remainingDays != null && slot.remainingDays < 0L
                val bonus = calculateTimeUrgencyBonus(slot.remainingDays, isOverdue)
                val kindBoost = when (slot.sourceKind) {
                    PrioritySourceKind.WARRANTY_CLAIM_FOLLOW_UP -> 45.0
                    PrioritySourceKind.RETURN_DEADLINE -> 20.0
                    PrioritySourceKind.WARRANTY_EXPIRY -> 10.0
                    PrioritySourceKind.PURCHASE_RECORD, PrioritySourceKind.MEDICINE_EXPIRY -> 0.0
                }
                bonus + kindBoost
            }.thenByDescending { it.deadlineMillis ?: 0L }
        ) ?: candidates.first()
    }

    private fun inferPurchaseImportance(
        purchase: PurchaseRecord,
        slot: PurchaseDeadlineSlot
    ): ImportanceLevel {
        val days = slot.remainingDays
        return when {
            slot.sourceKind == PrioritySourceKind.WARRANTY_CLAIM_FOLLOW_UP -> ImportanceLevel.CRITICAL
            slot.sourceKind == PrioritySourceKind.RETURN_DEADLINE && days != null && days <= 3L -> ImportanceLevel.HIGH
            days != null && days < 0L && days >= -30L -> ImportanceLevel.HIGH
            purchase.purchasePriceInr >= 25_000.0 && days != null && days <= 30L -> ImportanceLevel.HIGH
            days != null -> ImportanceLevel.MEDIUM
            else -> ImportanceLevel.LOW
        }
    }

    private fun inferMedicineImportance(remainingDays: Long?): ImportanceLevel {
        return when {
            remainingDays == null -> ImportanceLevel.LOW
            remainingDays < 0L -> ImportanceLevel.HIGH
            remainingDays <= 7L -> ImportanceLevel.CRITICAL
            remainingDays <= 30L -> ImportanceLevel.HIGH
            remainingDays <= 90L -> ImportanceLevel.MEDIUM
            else -> ImportanceLevel.LOW
        }
    }

    private fun buildStatusLabel(
        isCompleted: Boolean,
        isExpiredMedicine: Boolean,
        isOverdue: Boolean,
        remainingDays: Long?,
        tier: PriorityTier
    ): String {
        return when {
            isCompleted -> "COMPLETED"
            isExpiredMedicine -> "EXPIRED"
            isOverdue && remainingDays != null -> "OVERDUE ${-remainingDays}d"
            remainingDays == 0L -> "DUE TODAY"
            remainingDays == 1L -> "DUE TOMORROW"
            remainingDays != null && remainingDays > 0L -> "${tier.label} • IN ${remainingDays}d"
            else -> "${tier.label} • NO DEADLINE"
        }
    }

    private fun priorityComparator(): Comparator<PriorityItem> {
        return compareByDescending<PriorityItem> { it.priorityScore }
            .thenBy { it.priorityTier.rank }
            .thenBy { it.remainingDays ?: Long.MAX_VALUE }
            .thenBy { it.deadlineMillis ?: Long.MAX_VALUE }
            .thenBy { it.title.lowercase() }
    }

    companion object {
        val Default = PriorityEngine()
    }
}
