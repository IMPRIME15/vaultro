package com.example

import com.example.model.FamilyInviteCodeGenerator
import com.example.model.FamilyRole
import com.example.model.MedicineRecord
import com.example.model.PurchaseRecord
import com.example.model.SharedRecordType
import com.example.model.SharedVaultRecord
import com.example.model.WarrantyClaimStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyVaultModelsTest {

    @Test
    fun inviteCodeGenerator_producesFormattedUppercaseCodeAndNormalizesInput() {
        val code = FamilyInviteCodeGenerator.generate()
        assertTrue(code.matches(Regex("^VLT-[A-Z0-9]{4}-[A-Z0-9]{4}$")))
        assertEquals("VLT-8K2M-9P4Q", FamilyInviteCodeGenerator.normalize("  vlt-8k2m-9p4q !! "))
    }

    @Test
    fun sharedVaultRecord_preservesPurchaseAndMedicineFieldsDeterministically() {
        val purchase = PurchaseRecord(
            id = "pur_123",
            userId = "uid_alice",
            productName = "Dyson Airwrap",
            brand = "Dyson",
            merchantName = "Dyson India",
            purchasePriceInr = 45900.0,
            purchaseDateMillis = 1700000000000L,
            warrantyDurationMonths = 24L,
            warrantyExpiryMillis = 1763072000000L,
            invoiceNumber = "DYS-7781",
            importanceLevel = "HIGH",
            claimStatus = WarrantyClaimStatus.UNDER_REVIEW.name
        )
        val sharedPurchase = SharedVaultRecord.fromPurchase(
            familyId = "fam_1",
            ownerUid = "uid_alice",
            ownerName = "Alice",
            purchase = purchase
        )
        assertEquals("uid_alice_purchase_pur_123", sharedPurchase.id)
        assertEquals(SharedRecordType.PURCHASE, sharedPurchase.resolvedType())
        assertEquals("Dyson", sharedPurchase.brand)
        assertEquals(WarrantyClaimStatus.UNDER_REVIEW, sharedPurchase.resolvedClaimStatus())

        val medicine = MedicineRecord(
            id = "med_99",
            userId = "uid_alice",
            medicineName = "Paracetamol 500",
            batchNumber = "PCM-91",
            expiryDateMillis = 1780000000000L,
            quantity = "2 Strips",
            storageNote = "Cool dry shelf"
        )
        val sharedMed = SharedVaultRecord.fromMedicine(
            familyId = "fam_1",
            ownerUid = "uid_alice",
            ownerName = "Alice",
            medicine = medicine
        )
        assertEquals("uid_alice_medicine_med_99", sharedMed.id)
        assertEquals(SharedRecordType.MEDICINE, sharedMed.resolvedType())
        assertNull(sharedMed.priceInr)
        assertEquals("2 Strips", sharedMed.quantity)
    }

    @Test
    fun roleAndClaimStatus_fallbackSafelyOnUnknownValues() {
        assertEquals(FamilyRole.MEMBER, FamilyRole.fromRaw("UNKNOWN_ROLE"))
        assertEquals(FamilyRole.ADMIN, FamilyRole.fromRaw("admin"))
        assertEquals(WarrantyClaimStatus.NOT_SUBMITTED, WarrantyClaimStatus.fromRaw("bad"))
        assertEquals(WarrantyClaimStatus.RESOLVED, WarrantyClaimStatus.fromRaw("resolved"))
    }
}
