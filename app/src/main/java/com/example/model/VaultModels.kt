package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ThemePreference {
    DARK, LIGHT, SYSTEM;

    companion object {
        fun fromRaw(raw: String?): ThemePreference =
            entries.firstOrNull { it.name == raw } ?: DARK
    }
}

enum class AttachmentType {
    NONE, IMAGE, PDF;

    companion object {
        fun fromRaw(raw: String?): AttachmentType =
            entries.firstOrNull { it.name == raw } ?: NONE
    }
}

enum class ExpiryStatus {
    VALID, EXPIRING_SOON, EXPIRED
}

enum class ReturnStatus {
    ELIGIBLE, CLOSING_SOON, PAST, NOT_TRACKED
}

enum class WarrantyStatus {
    ACTIVE, EXPIRING_SOON, EXPIRED, NOT_TRACKED
}

enum class VaultModule {
    PURCHASE, MEDICINE
}

enum class AlertKind {
    RETURN_APPROACHING,
    WARRANTY_APPROACHING,
    MEDICINE_EXPIRING_SOON,
    MEDICINE_EXPIRED
}

enum class ImportanceLevel(val label: String, val baseWeight: Double) {
    CRITICAL("Critical", 1000.0),
    HIGH("High", 650.0),
    MEDIUM("Medium", 350.0),
    LOW("Low", 100.0);

    companion object {
        fun fromRawOrNull(raw: String?): ImportanceLevel? {
            if (raw.isNullOrBlank() || raw.equals("AUTO", ignoreCase = true)) return null
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
        }

        fun normalizeRaw(raw: String?): String {
            return fromRawOrNull(raw)?.name ?: ""
        }
    }
}

enum class PriorityTier(val label: String, val subtitle: String, val rank: Int) {
    CRITICAL("CRITICAL", "Immediate attention required", 0),
    HIGH("HIGH", "Important upcoming deadlines", 1),
    MEDIUM("MEDIUM", "Deadlines approaching", 2),
    LOW("LOW", "Records requiring minimal attention", 3)
}

enum class WarrantyClaimStatus(val label: String) {
    NOT_SUBMITTED("Not Submitted"),
    SUBMITTED("Submitted"),
    UNDER_REVIEW("Under Review"),
    RESOLVED("Resolved"),
    REJECTED("Rejected");

    companion object {
        fun fromRaw(raw: String?): WarrantyClaimStatus =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: NOT_SUBMITTED
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val themePreference: String = ThemePreference.DARK.name,
    val purchaseRemindersEnabled: Boolean = true,
    val medicineRemindersEnabled: Boolean = true,
    val returnReminderDaysBefore: Long = 3L,
    val warrantyReminderDaysBefore: Long = 14L,
    val medicineReminderDaysBefore: Long = 14L,
    val appLockEnabled: Boolean = false,
    val seededSampleData: Boolean = false,
    val priorityCriticalAlertsEnabled: Boolean = true,
    val priorityHighAlertsEnabled: Boolean = true,
    val priorityMediumDigestEnabled: Boolean = true,
    val priorityLowMuted: Boolean = true,
    val familyId: String? = null,
    val familyRole: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedTheme(): ThemePreference = ThemePreference.fromRaw(themePreference)

    fun toCreateMap(): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "userId" to userId,
            "fullName" to fullName.take(100),
            "email" to email.take(200),
            "photoUrl" to photoUrl?.takeIf { it.isNotBlank() }?.take(1000),
            "themePreference" to ThemePreference.fromRaw(themePreference).name,
            "purchaseRemindersEnabled" to purchaseRemindersEnabled,
            "medicineRemindersEnabled" to medicineRemindersEnabled,
            "returnReminderDaysBefore" to returnReminderDaysBefore.coerceIn(1L, 30L),
            "warrantyReminderDaysBefore" to warrantyReminderDaysBefore.coerceIn(1L, 90L),
            "medicineReminderDaysBefore" to medicineReminderDaysBefore.coerceIn(1L, 90L),
            "appLockEnabled" to appLockEnabled,
            "seededSampleData" to seededSampleData,
            "priorityCriticalAlertsEnabled" to priorityCriticalAlertsEnabled,
            "priorityHighAlertsEnabled" to priorityHighAlertsEnabled,
            "priorityMediumDigestEnabled" to priorityMediumDigestEnabled,
            "priorityLowMuted" to priorityLowMuted,
            "familyId" to familyId?.takeIf { it.isNotBlank() }?.take(128),
            "familyRole" to familyRole?.takeIf { it == "ADMIN" || it == "MEMBER" },
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class PurchaseRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val productName: String = "",
    val merchantName: String = "",
    val purchasePriceInr: Double = 0.0,
    val purchaseDateMillis: Long = 0L,
    val invoiceNumber: String = "",
    val category: String = "Electronics",
    val attachmentUri: String? = null,
    val attachmentType: String = AttachmentType.NONE.name,
    val returnDeadlineMillis: Long? = null,
    val warrantyDurationMonths: Long? = null,
    val warrantyExpiryMillis: Long? = null,
    val notes: String? = null,
    val returnReminderSet: Boolean = true,
    val warrantyReminderSet: Boolean = true,
    val importanceLevel: String = "",
    val completed: Boolean = false,
    val completedAtMillis: Long? = null,
    val warrantyClaimActive: Boolean = false,
    val warrantyClaimFollowUpMillis: Long? = null,
    val brand: String = "",
    val claimStatus: String = WarrantyClaimStatus.NOT_SUBMITTED.name,
    val claimNotes: String = "",
    val claimIssueSummary: String = "",
    val claimUpdatedAtMillis: Long? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedAttachmentType(): AttachmentType = AttachmentType.fromRaw(attachmentType)
    fun resolvedImportanceOrNull(): ImportanceLevel? = ImportanceLevel.fromRawOrNull(importanceLevel)
    fun resolvedClaimStatus(): WarrantyClaimStatus = WarrantyClaimStatus.fromRaw(claimStatus)

    fun toCreateMap(uid: String): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "userId" to uid,
            "productName" to productName.trim().take(150),
            "brand" to brand.trim().take(80),
            "merchantName" to merchantName.trim().take(120),
            "purchasePriceInr" to purchasePriceInr.coerceIn(0.0, 100_000_000.0),
            "purchaseDateMillis" to purchaseDateMillis.coerceAtLeast(1L),
            "invoiceNumber" to invoiceNumber.trim().take(80),
            "category" to category.ifBlank { "General" }.take(60),
            "attachmentUri" to attachmentUri?.takeIf { it.isNotBlank() }?.take(1000),
            "attachmentType" to AttachmentType.fromRaw(attachmentType).name,
            "returnDeadlineMillis" to returnDeadlineMillis?.takeIf { it > 0L },
            "warrantyDurationMonths" to warrantyDurationMonths?.coerceIn(0L, 240L),
            "warrantyExpiryMillis" to warrantyExpiryMillis?.takeIf { it > 0L },
            "notes" to notes?.takeIf { it.isNotBlank() }?.take(500),
            "returnReminderSet" to returnReminderSet,
            "warrantyReminderSet" to warrantyReminderSet,
            "importanceLevel" to ImportanceLevel.normalizeRaw(importanceLevel),
            "completed" to completed,
            "completedAtMillis" to completedAtMillis?.takeIf { it > 0L },
            "warrantyClaimActive" to warrantyClaimActive,
            "warrantyClaimFollowUpMillis" to warrantyClaimFollowUpMillis?.takeIf { it > 0L },
            "claimStatus" to resolvedClaimStatus().name,
            "claimNotes" to claimNotes.trim().take(1000),
            "claimIssueSummary" to claimIssueSummary.trim().take(600),
            "claimUpdatedAtMillis" to claimUpdatedAtMillis?.takeIf { it > 0L },
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }

    fun toUpdateMap(): Map<String, Any> {
        return mapOf(
            "productName" to productName.trim().take(150),
            "brand" to brand.trim().take(80),
            "merchantName" to merchantName.trim().take(120),
            "purchasePriceInr" to purchasePriceInr.coerceIn(0.0, 100_000_000.0),
            "purchaseDateMillis" to purchaseDateMillis.coerceAtLeast(1L),
            "invoiceNumber" to invoiceNumber.trim().take(80),
            "category" to category.ifBlank { "General" }.take(60),
            "attachmentUri" to (attachmentUri?.takeIf { it.isNotBlank() }?.take(1000) ?: FieldValue.delete()),
            "attachmentType" to AttachmentType.fromRaw(attachmentType).name,
            "returnDeadlineMillis" to (returnDeadlineMillis?.takeIf { it > 0L } ?: FieldValue.delete()),
            "warrantyDurationMonths" to (warrantyDurationMonths?.coerceIn(0L, 240L) ?: FieldValue.delete()),
            "warrantyExpiryMillis" to (warrantyExpiryMillis?.takeIf { it > 0L } ?: FieldValue.delete()),
            "notes" to (notes?.takeIf { it.isNotBlank() }?.take(500) ?: FieldValue.delete()),
            "returnReminderSet" to returnReminderSet,
            "warrantyReminderSet" to warrantyReminderSet,
            "importanceLevel" to ImportanceLevel.normalizeRaw(importanceLevel),
            "completed" to completed,
            "completedAtMillis" to (completedAtMillis?.takeIf { it > 0L } ?: FieldValue.delete()),
            "warrantyClaimActive" to warrantyClaimActive,
            "warrantyClaimFollowUpMillis" to (warrantyClaimFollowUpMillis?.takeIf { it > 0L } ?: FieldValue.delete()),
            "claimStatus" to resolvedClaimStatus().name,
            "claimNotes" to claimNotes.trim().take(1000),
            "claimIssueSummary" to claimIssueSummary.trim().take(600),
            "claimUpdatedAtMillis" to (claimUpdatedAtMillis?.takeIf { it > 0L } ?: FieldValue.delete()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}

@Keep
@IgnoreExtraProperties
data class MedicineRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val medicineName: String = "",
    val batchNumber: String = "",
    val expiryDateMillis: Long = 0L,
    val purchaseDateMillis: Long? = null,
    val quantity: String? = null,
    val photoUri: String? = null,
    val storageNote: String? = null,
    val reminderEnabled: Boolean = true,
    val reminderDaysBefore: Long = 14L,
    val importanceLevel: String = "",
    val completed: Boolean = false,
    val completedAtMillis: Long? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedImportanceOrNull(): ImportanceLevel? = ImportanceLevel.fromRawOrNull(importanceLevel)

    fun toCreateMap(uid: String): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "userId" to uid,
            "medicineName" to medicineName.trim().take(150),
            "batchNumber" to batchNumber.trim().take(80),
            "expiryDateMillis" to expiryDateMillis.coerceAtLeast(1L),
            "purchaseDateMillis" to purchaseDateMillis?.takeIf { it > 0L },
            "quantity" to quantity?.takeIf { it.isNotBlank() }?.take(80),
            "photoUri" to photoUri?.takeIf { it.isNotBlank() }?.take(1000),
            "storageNote" to storageNote?.takeIf { it.isNotBlank() }?.take(250),
            "reminderEnabled" to reminderEnabled,
            "reminderDaysBefore" to reminderDaysBefore.coerceIn(1L, 90L),
            "importanceLevel" to ImportanceLevel.normalizeRaw(importanceLevel),
            "completed" to completed,
            "completedAtMillis" to completedAtMillis?.takeIf { it > 0L },
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }

    fun toUpdateMap(): Map<String, Any> {
        return mapOf(
            "medicineName" to medicineName.trim().take(150),
            "batchNumber" to batchNumber.trim().take(80),
            "expiryDateMillis" to expiryDateMillis.coerceAtLeast(1L),
            "purchaseDateMillis" to (purchaseDateMillis?.takeIf { it > 0L } ?: FieldValue.delete()),
            "quantity" to (quantity?.takeIf { it.isNotBlank() }?.take(80) ?: FieldValue.delete()),
            "photoUri" to (photoUri?.takeIf { it.isNotBlank() }?.take(1000) ?: FieldValue.delete()),
            "storageNote" to (storageNote?.takeIf { it.isNotBlank() }?.take(250) ?: FieldValue.delete()),
            "reminderEnabled" to reminderEnabled,
            "reminderDaysBefore" to reminderDaysBefore.coerceIn(1L, 90L),
            "importanceLevel" to ImportanceLevel.normalizeRaw(importanceLevel),
            "completed" to completed,
            "completedAtMillis" to (completedAtMillis?.takeIf { it > 0L } ?: FieldValue.delete()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}

@Keep
@IgnoreExtraProperties
data class NotificationState(
    @DocumentId val id: String = "",
    val userId: String = "",
    val alertKey: String = "",
    val readFlag: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class VaultNotification(
    val alertKey: String,
    val module: VaultModule,
    val kind: AlertKind,
    val title: String,
    val subtitle: String,
    val targetId: String,
    val dueMillis: Long,
    val daysDelta: Long,
    val isRead: Boolean,
    val isUrgent7Days: Boolean = false,
    val priorityTier: PriorityTier = PriorityTier.MEDIUM
)

data class ExtractedReceiptData(
    val productName: String = "",
    val brand: String = "",
    val merchantName: String = "",
    val purchasePriceInr: Double? = null,
    val purchaseDateMillis: Long? = null,
    val invoiceNumber: String = "",
    val returnDeadlineMillis: Long? = null,
    val warrantyDurationMonths: Long? = null,
    val warrantyExpiryMillis: Long? = null,
    val category: String = "Electronics",
    val notes: String = "",
    val isUnclearReceipt: Boolean = false,
    val clarityWarning: String? = null
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
