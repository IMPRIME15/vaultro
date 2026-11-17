package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.security.SecureRandom

enum class FamilyRole(val label: String) {
    ADMIN("Admin"),
    MEMBER("Member");

    companion object {
        fun fromRaw(raw: String?): FamilyRole =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: MEMBER
    }
}

enum class SharedRecordType(val label: String) {
    PURCHASE("Receipt & Warranty"),
    MEDICINE("Medicine");

    companion object {
        fun fromRaw(raw: String?): SharedRecordType =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: PURCHASE
    }
}

object FamilyInviteCodeGenerator {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val random = SecureRandom()

    fun generate(): String {
        val part1 = buildString(4) {
            repeat(4) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }
        val part2 = buildString(4) {
            repeat(4) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }
        return "VLT-$part1-$part2"
    }

    fun normalize(raw: String): String =
        raw.trim().uppercase().replace(Regex("[^A-Z0-9\\-]"), "")
}

@Keep
@IgnoreExtraProperties
data class FamilyGroup(
    @DocumentId val id: String = "",
    val name: String = "",
    val adminUid: String = "",
    val inviteCode: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String, cleanName: String, code: String): Map<String, Any> = mapOf(
        "name" to cleanName.trim().take(80),
        "adminUid" to uid,
        "inviteCode" to FamilyInviteCodeGenerator.normalize(code).take(32),
        "memberUids" to listOf(uid),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class FamilyMember(
    @DocumentId val id: String = "",
    val familyId: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val role: String = FamilyRole.MEMBER.name,
    val inviteCodeUsed: String = "",
    val joinedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedRole(): FamilyRole = FamilyRole.fromRaw(role)

    fun toCreateMap(): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "familyId" to familyId,
            "userId" to userId,
            "displayName" to displayName.trim().ifBlank { "Family Member" }.take(100),
            "email" to email.trim().ifBlank { "member@vaultora.app" }.take(200),
            "photoUrl" to photoUrl?.takeIf { it.isNotBlank() }?.take(1000),
            "role" to FamilyRole.fromRaw(role).name,
            "inviteCodeUsed" to inviteCodeUsed.trim().take(32),
            "joinedAtMillis" to joinedAtMillis.coerceAtLeast(1L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class FamilyInvite(
    @DocumentId val id: String = "",
    val inviteCode: String = "",
    val familyId: String = "",
    val familyName: String = "",
    val createdByUid: String = "",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "inviteCode" to FamilyInviteCodeGenerator.normalize(inviteCode).take(32),
        "familyId" to familyId,
        "familyName" to familyName.trim().take(80),
        "createdByUid" to createdByUid,
        "active" to active,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class SharedVaultRecord(
    @DocumentId val id: String = "",
    val familyId: String = "",
    val ownerUid: String = "",
    val ownerName: String = "",
    val recordType: String = SharedRecordType.PURCHASE.name,
    val sourceRecordId: String = "",
    val title: String = "",
    val brand: String = "",
    val merchantOrBatch: String = "",
    val category: String = "",
    val priceInr: Double? = null,
    val purchaseDateMillis: Long? = null,
    val returnDeadlineMillis: Long? = null,
    val warrantyDurationMonths: Long? = null,
    val warrantyExpiryMillis: Long? = null,
    val medicineExpiryMillis: Long? = null,
    val quantity: String? = null,
    val storageNote: String? = null,
    val notes: String? = null,
    val invoiceNumber: String = "",
    val importanceLevel: String = "",
    val claimStatus: String = WarrantyClaimStatus.NOT_SUBMITTED.name,
    val sharedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedType(): SharedRecordType = SharedRecordType.fromRaw(recordType)
    fun resolvedClaimStatus(): WarrantyClaimStatus = WarrantyClaimStatus.fromRaw(claimStatus)

    fun toCreateMap(): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "familyId" to familyId,
            "ownerUid" to ownerUid,
            "ownerName" to ownerName.trim().ifBlank { "Family Member" }.take(100),
            "recordType" to SharedRecordType.fromRaw(recordType).name,
            "sourceRecordId" to sourceRecordId.trim().take(128),
            "title" to title.trim().take(150),
            "brand" to brand.trim().take(80),
            "merchantOrBatch" to merchantOrBatch.trim().take(120),
            "category" to category.trim().take(60),
            "priceInr" to priceInr?.coerceIn(0.0, 100_000_000.0),
            "purchaseDateMillis" to purchaseDateMillis?.takeIf { it > 0L },
            "returnDeadlineMillis" to returnDeadlineMillis?.takeIf { it > 0L },
            "warrantyDurationMonths" to warrantyDurationMonths?.coerceIn(0L, 240L),
            "warrantyExpiryMillis" to warrantyExpiryMillis?.takeIf { it > 0L },
            "medicineExpiryMillis" to medicineExpiryMillis?.takeIf { it > 0L },
            "quantity" to quantity?.takeIf { it.isNotBlank() }?.take(80),
            "storageNote" to storageNote?.takeIf { it.isNotBlank() }?.take(250),
            "notes" to notes?.takeIf { it.isNotBlank() }?.take(500),
            "invoiceNumber" to invoiceNumber.trim().take(80),
            "importanceLevel" to ImportanceLevel.normalizeRaw(importanceLevel),
            "claimStatus" to WarrantyClaimStatus.fromRaw(claimStatus).name,
            "sharedAtMillis" to sharedAtMillis.coerceAtLeast(1L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }

    companion object {
        fun deterministicShareId(
            ownerUid: String,
            recordType: SharedRecordType,
            sourceRecordId: String
        ): String {
            val safeOwner = ownerUid.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(40)
            val safeSource = sourceRecordId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(64)
            return "${safeOwner}_${recordType.name.lowercase()}_$safeSource"
        }

        fun fromPurchase(
            familyId: String,
            ownerUid: String,
            ownerName: String,
            purchase: PurchaseRecord,
            nowMillis: Long = System.currentTimeMillis()
        ): SharedVaultRecord = SharedVaultRecord(
            id = deterministicShareId(ownerUid, SharedRecordType.PURCHASE, purchase.id),
            familyId = familyId,
            ownerUid = ownerUid,
            ownerName = ownerName,
            recordType = SharedRecordType.PURCHASE.name,
            sourceRecordId = purchase.id,
            title = purchase.productName,
            brand = purchase.brand,
            merchantOrBatch = purchase.merchantName,
            category = purchase.category,
            priceInr = purchase.purchasePriceInr,
            purchaseDateMillis = purchase.purchaseDateMillis,
            returnDeadlineMillis = purchase.returnDeadlineMillis,
            warrantyDurationMonths = purchase.warrantyDurationMonths,
            warrantyExpiryMillis = purchase.warrantyExpiryMillis,
            notes = purchase.notes,
            invoiceNumber = purchase.invoiceNumber,
            importanceLevel = purchase.importanceLevel,
            claimStatus = purchase.resolvedClaimStatus().name,
            sharedAtMillis = nowMillis
        )

        fun fromMedicine(
            familyId: String,
            ownerUid: String,
            ownerName: String,
            medicine: MedicineRecord,
            nowMillis: Long = System.currentTimeMillis()
        ): SharedVaultRecord = SharedVaultRecord(
            id = deterministicShareId(ownerUid, SharedRecordType.MEDICINE, medicine.id),
            familyId = familyId,
            ownerUid = ownerUid,
            ownerName = ownerName,
            recordType = SharedRecordType.MEDICINE.name,
            sourceRecordId = medicine.id,
            title = medicine.medicineName,
            merchantOrBatch = medicine.batchNumber,
            category = "Medicine Vault",
            purchaseDateMillis = medicine.purchaseDateMillis,
            medicineExpiryMillis = medicine.expiryDateMillis,
            quantity = medicine.quantity,
            storageNote = medicine.storageNote,
            importanceLevel = medicine.importanceLevel,
            sharedAtMillis = nowMillis
        )
    }
}
