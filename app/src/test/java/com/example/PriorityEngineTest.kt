package com.example

import com.example.model.ImportanceLevel
import com.example.model.MedicineRecord
import com.example.model.PriorityTier
import com.example.model.PurchaseRecord
import com.example.notifications.PriorityNotificationManager
import com.example.notifications.PriorityNotificationPreferences
import com.example.priority.PriorityEngine
import com.example.priority.PrioritySourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.TimeUnit

class PriorityEngineTest {

    private val engine = PriorityEngine()

    private fun middayMillis(year: Int, monthZeroBased: Int, day: Int): Long {
        return Calendar.getInstance().apply {
            set(year, monthZeroBased, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun weightedScoring_criticalAndHighAlwaysOutrankLowEvenWhenLowDueToday() {
        val now = middayMillis(2026, Calendar.NOVEMBER, 17)
        val dayMs = TimeUnit.DAYS.toMillis(1L)

        val lowDueToday = PurchaseRecord(
            id = "p_low_today",
            productName = "Phone Case",
            merchantName = "Amazon",
            purchaseDateMillis = now - 5 * dayMs,
            returnDeadlineMillis = now, // 0 days left
            importanceLevel = ImportanceLevel.LOW.name
        )
        val highLater = PurchaseRecord(
            id = "p_high_20d",
            productName = "MacBook Pro",
            merchantName = "Apple",
            purchaseDateMillis = now - 10 * dayMs,
            warrantyExpiryMillis = now + 20 * dayMs,
            importanceLevel = ImportanceLevel.HIGH.name
        )
        val criticalLater = PurchaseRecord(
            id = "p_critical_25d",
            productName = "Inverter AC",
            merchantName = "Croma",
            purchaseDateMillis = now - 10 * dayMs,
            warrantyExpiryMillis = now + 25 * dayMs,
            importanceLevel = ImportanceLevel.CRITICAL.name
        )

        val report = engine.evaluate(
            purchases = listOf(lowDueToday, highLater, criticalLater),
            medicines = emptyList(),
            nowMillis = now
        )

        assertEquals(
            listOf("p_critical_25d", "p_high_20d", "p_low_today"),
            report.activeItems.map { it.id }
        )
        assertEquals(PriorityTier.CRITICAL, report.purchasePriorityMap["p_critical_25d"]?.priorityTier)
        assertEquals(PriorityTier.HIGH, report.purchasePriorityMap["p_high_20d"]?.priorityTier)
        assertEquals(PriorityTier.MEDIUM, report.purchasePriorityMap["p_low_today"]?.priorityTier)
    }

    @Test
    fun priorityTierResolution_coversCriticalHighMediumAndLowNoDeadline() {
        val now = middayMillis(2026, Calendar.NOVEMBER, 17)
        val dayMs = TimeUnit.DAYS.toMillis(1L)

        val critical = PurchaseRecord(
            id = "crit",
            productName = "Critical Item",
            returnDeadlineMillis = now + 5 * dayMs,
            importanceLevel = ImportanceLevel.CRITICAL.name
        )
        val high = PurchaseRecord(
            id = "high",
            productName = "High Item",
            warrantyExpiryMillis = now + 20 * dayMs,
            importanceLevel = ImportanceLevel.HIGH.name
        )
        val medium = PurchaseRecord(
            id = "med",
            productName = "Medium Item",
            warrantyExpiryMillis = now + 45 * dayMs,
            importanceLevel = ImportanceLevel.MEDIUM.name
        )
        val lowNoDeadline = PurchaseRecord(
            id = "low",
            productName = "Cable Pack",
            importanceLevel = ImportanceLevel.LOW.name
        )

        val report = engine.evaluate(
            purchases = listOf(lowNoDeadline, medium, high, critical),
            medicines = emptyList(),
            nowMillis = now
        )

        assertEquals(listOf("crit"), report.criticalItems.map { it.id })
        assertEquals(listOf("high"), report.highItems.map { it.id })
        assertEquals(listOf("med"), report.mediumItems.map { it.id })
        assertEquals(listOf("low"), report.lowItems.map { it.id })
    }

    @Test
    fun dynamicAutoSorting_reactsToDeadlineChangeTimeElapseImportanceChangeAndCompletion() {
        val day0 = middayMillis(2026, Calendar.NOVEMBER, 17)
        val dayMs = TimeUnit.DAYS.toMillis(1L)

        val itemA = PurchaseRecord(
            id = "a",
            productName = "Item A",
            warrantyExpiryMillis = day0 + 20 * dayMs,
            importanceLevel = ImportanceLevel.MEDIUM.name
        )
        val itemB = PurchaseRecord(
            id = "b",
            productName = "Item B",
            warrantyExpiryMillis = day0 + 10 * dayMs,
            importanceLevel = ImportanceLevel.MEDIUM.name
        )

        // Initially B is closer (10d vs 20d) -> B ranks first
        val initial = engine.evaluate(listOf(itemA, itemB), emptyList(), nowMillis = day0)
        assertEquals(listOf("b", "a"), initial.activeItems.map { it.id })

        // Deadline change: move A closer (3d vs 10d) -> A ranks first
        val closerA = itemA.copy(warrantyExpiryMillis = day0 + 3 * dayMs)
        val afterDeadlineChange = engine.evaluate(listOf(closerA, itemB), emptyList(), nowMillis = day0)
        assertEquals(listOf("a", "b"), afterDeadlineChange.activeItems.map { it.id })

        // Time elapse: advance 8 days so itemB is now 2d away while itemC (same medium) is 22d away
        val itemC = PurchaseRecord(
            id = "c",
            productName = "Item C",
            warrantyExpiryMillis = day0 + 30 * dayMs,
            importanceLevel = ImportanceLevel.HIGH.name
        )
        val later = day0 + 27 * dayMs // itemC is now 3 days left (escalates to CRITICAL tier)
        val afterTimeElapse = engine.evaluate(listOf(itemC), emptyList(), nowMillis = later)
        assertEquals(PriorityTier.CRITICAL, afterTimeElapse.activeItems.first().priorityTier)

        // Importance elevation: raise itemB to CRITICAL -> outranks itemA even with longer deadline
        val elevatedB = itemB.copy(importanceLevel = ImportanceLevel.CRITICAL.name)
        val afterImportanceChange = engine.evaluate(listOf(closerA, elevatedB), emptyList(), nowMillis = day0)
        assertEquals(listOf("b", "a"), afterImportanceChange.activeItems.map { it.id })

        // Completed item disappears from activeItems and moves to completedItems
        val completedB = elevatedB.copy(completed = true, completedAtMillis = day0)
        val afterComplete = engine.evaluate(listOf(closerA, completedB), emptyList(), nowMillis = day0)
        assertEquals(listOf("a"), afterComplete.activeItems.map { it.id })
        assertEquals(listOf("b"), afterComplete.completedItems.map { it.id })
    }

    @Test
    fun purchaseSlots_prioritizeClaimFollowUpAndMarkOverdueReturnsAndWarranties() {
        val now = middayMillis(2026, Calendar.NOVEMBER, 17)
        val dayMs = TimeUnit.DAYS.toMillis(1L)

        val activeClaim = PurchaseRecord(
            id = "claim_1",
            productName = "OLED Monitor",
            warrantyExpiryMillis = now + 90 * dayMs,
            warrantyClaimActive = true,
            warrantyClaimFollowUpMillis = now + 2 * dayMs
        )
        val overdueReturn = PurchaseRecord(
            id = "ret_overdue",
            productName = "Blender",
            returnDeadlineMillis = now - 3 * dayMs,
            warrantyExpiryMillis = now + 180 * dayMs
        )
        val overdueWarranty = PurchaseRecord(
            id = "war_overdue",
            productName = "Router",
            warrantyExpiryMillis = now - 5 * dayMs
        )

        val report = engine.evaluate(
            purchases = listOf(overdueWarranty, overdueReturn, activeClaim),
            medicines = emptyList(),
            nowMillis = now
        )

        val claimItem = report.purchasePriorityMap.getValue("claim_1")
        assertEquals(PrioritySourceKind.WARRANTY_CLAIM_FOLLOW_UP, claimItem.sourceKind)
        assertEquals(PriorityTier.CRITICAL, claimItem.priorityTier)

        val retItem = report.purchasePriorityMap.getValue("ret_overdue")
        assertTrue(retItem.isOverdue)
        assertTrue(retItem.priorityStatusLabel.startsWith("OVERDUE"))

        val warItem = report.purchasePriorityMap.getValue("war_overdue")
        assertTrue(warItem.isOverdue)
        assertTrue(warItem.priorityStatusLabel.startsWith("OVERDUE"))
        assertTrue(report.overdueItems.map { it.id }.containsAll(listOf("ret_overdue", "war_overdue")))
    }

    @Test
    fun medicines_separateExpiredAndCompletedFromActiveAndRankUrgentExpiriesFirst() {
        val now = middayMillis(2026, Calendar.NOVEMBER, 17)
        val dayMs = TimeUnit.DAYS.toMillis(1L)

        val expired = MedicineRecord(
            id = "m_expired",
            medicineName = "Old Cough Syrup",
            expiryDateMillis = now - 4 * dayMs
        )
        val completed = MedicineRecord(
            id = "m_done",
            medicineName = "Finished Course",
            expiryDateMillis = now + 2 * dayMs,
            completed = true,
            completedAtMillis = now
        )
        val urgent = MedicineRecord(
            id = "m_urgent",
            medicineName = "Insulin Pen",
            expiryDateMillis = now + 3 * dayMs
        )
        val distant = MedicineRecord(
            id = "m_distant",
            medicineName = "Antacid",
            expiryDateMillis = now + 120 * dayMs
        )

        val report = engine.evaluate(
            purchases = emptyList(),
            medicines = listOf(distant, expired, completed, urgent),
            nowMillis = now
        )

        assertEquals(listOf("m_urgent", "m_distant"), report.activeItems.map { it.id })
        assertEquals(listOf("m_expired"), report.expiredMedicines.map { it.id })
        assertEquals(listOf("m_done"), report.completedItems.map { it.id })
        assertEquals(PriorityTier.CRITICAL, report.medicinePriorityMap["m_urgent"]?.priorityTier)
        assertEquals(PriorityTier.LOW, report.medicinePriorityMap["m_distant"]?.priorityTier)
    }

    @Test
    fun notificationPlanner_tiersAndSuppressesLowWhenMutedAndGroupsMedium() {
        val now = middayMillis(2026, Calendar.NOVEMBER, 17)
        val dayMs = TimeUnit.DAYS.toMillis(1L)

        val purchases = listOf(
            PurchaseRecord(
                id = "c1",
                productName = "TV Claim",
                warrantyClaimActive = true,
                warrantyClaimFollowUpMillis = now + 1 * dayMs,
                importanceLevel = ImportanceLevel.CRITICAL.name
            ),
            PurchaseRecord(
                id = "h1",
                productName = "Laptop Warranty",
                warrantyExpiryMillis = now + 10 * dayMs,
                importanceLevel = ImportanceLevel.HIGH.name
            ),
            PurchaseRecord(
                id = "m1",
                productName = "Toaster Warranty",
                warrantyExpiryMillis = now + 10 * dayMs,
                importanceLevel = ImportanceLevel.MEDIUM.name
            ),
            PurchaseRecord(
                id = "m2",
                productName = "Lamp Warranty",
                warrantyExpiryMillis = now + 12 * dayMs,
                importanceLevel = ImportanceLevel.MEDIUM.name
            ),
            PurchaseRecord(
                id = "l1",
                productName = "Mousepad",
                importanceLevel = ImportanceLevel.LOW.name
            )
        )

        val report = engine.evaluate(purchases, emptyList(), nowMillis = now)
        val plan = PriorityNotificationManager.buildPlan(
            report = report,
            prefs = PriorityNotificationPreferences(lowMuted = true),
            nowMillis = now
        )

        val tiers = plan.messages.map { it.tier }
        assertTrue(PriorityTier.CRITICAL in tiers)
        assertTrue(PriorityTier.HIGH in tiers)
        assertTrue(PriorityTier.MEDIUM in tiers)
        assertFalse(PriorityTier.LOW in tiers)
        assertEquals(1, plan.suppressedLowCount)

        val mediumMsg = plan.messages.first { it.tier == PriorityTier.MEDIUM }
        assertEquals("medium_group_summary", mediumMsg.eventKey)
        assertEquals(2, mediumMsg.inboxLines.size)
        assertEquals("priority_center", mediumMsg.navTarget)
    }
}
