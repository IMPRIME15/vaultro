package com.example

import com.example.ai.ReceiptOcrExtractor
import com.example.ai.ReceiptQualityAndDuplicateAnalyzer
import com.example.model.ExtractedReceiptData
import com.example.model.PurchaseRecord
import com.example.util.DateAndCurrencyUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAutoDetectionTest {

    private val extractor = ReceiptOcrExtractor()

    @Test
    fun parseReceiptText_extractsBrandWarrantyAndReturnWithoutInventingMissingFields() {
        val rawWithWarrantyAndReturn = """
            RELIANCE DIGITAL
            Bill No: RD-55410
            Date: 10/11/2026
            Samsung Galaxy Watch 7     29,999.00
            Warranty: 12 months manufacturer warranty
            Returnable within 7 days of delivery
            Grand Total: Rs. 29,999.00
        """.trimIndent()

        val result = extractor.parseReceiptText(rawWithWarrantyAndReturn)
        assertEquals("RELIANCE DIGITAL", result.extracted.merchantName)
        assertEquals("Samsung", result.extracted.brand)
        assertEquals(12L, result.extracted.warrantyDurationMonths)
        assertNotNull(result.extracted.warrantyExpiryMillis)
        assertNotNull(result.extracted.returnDeadlineMillis)

        // When no warranty or return policy is mentioned, both must remain null (never invented)
        val rawMinimal = """
            LOCAL BOOKSTORE
            Date: 10/11/2026
            Clean Architecture Book   550.00
            Total: 550.00
        """.trimIndent()
        val minimalResult = extractor.parseReceiptText(rawMinimal)
        assertNull(minimalResult.extracted.warrantyDurationMonths)
        assertNull(minimalResult.extracted.warrantyExpiryMillis)
        assertNull(minimalResult.extracted.returnDeadlineMillis)
        assertEquals("", minimalResult.extracted.brand)
    }

    @Test
    fun analyzer_detectsUnclearReceiptAndMissingFields() {
        val extracted = ExtractedReceiptData(
            productName = "",
            brand = "",
            merchantName = "",
            purchasePriceInr = null,
            purchaseDateMillis = null
        )
        val report = ReceiptQualityAndDuplicateAnalyzer.analyze(
            extracted = extracted,
            rawOcrText = "### ... 12",
            existingPurchases = emptyList()
        )
        assertTrue(report.isUnclearReceipt)
        assertTrue(report.requiresManualReview)
        assertTrue(report.missingCriticalFields.contains("Product Name"))
        assertTrue(report.missingCriticalFields.contains("Shop / Merchant Name"))
        assertTrue(report.missingCriticalFields.contains("Purchase Price"))
        assertTrue(report.missingCriticalFields.contains("Purchase Date"))
    }

    @Test
    fun analyzer_detectsDuplicateByInvoiceOrShopAmountDate_andExcludesEditedId() {
        val purchaseDate = DateAndCurrencyUtils.parseIsoDateToMillis("2026-11-10")!!
        val existing = listOf(
            PurchaseRecord(
                id = "pur_existing_1",
                productName = "Sony WH-1000XM5 Headphones",
                brand = "Sony",
                merchantName = "Croma Electronics",
                purchasePriceInr = 24990.0,
                purchaseDateMillis = purchaseDate,
                invoiceNumber = "CRM-90812"
            )
        )

        val duplicateByInvoice = ExtractedReceiptData(
            productName = "Sony Headphones",
            brand = "Sony",
            merchantName = "Croma",
            purchasePriceInr = 24990.0,
            purchaseDateMillis = purchaseDate,
            invoiceNumber = "crm 90812",
            warrantyDurationMonths = 12L
        )
        val report = ReceiptQualityAndDuplicateAnalyzer.analyze(
            extracted = duplicateByInvoice,
            rawOcrText = "Croma Electronics Sony Headphones CRM-90812 24990.00",
            existingPurchases = existing
        )
        assertEquals(1, report.duplicateMatches.size)
        assertEquals("pur_existing_1", report.duplicateMatches.first().existingPurchaseId)
        assertTrue(report.requiresManualReview)
        assertTrue(report.autoDestinations.contains("Purchase Vault"))
        assertTrue(report.autoDestinations.contains("Warranty Tracking"))
        assertFalse(report.autoDestinations.contains("Return Tracking"))

        // Excluding the same record while editing must not flag itself as duplicate
        val editReport = ReceiptQualityAndDuplicateAnalyzer.analyze(
            extracted = duplicateByInvoice,
            rawOcrText = "Croma Electronics Sony Headphones CRM-90812 24990.00",
            existingPurchases = existing,
            excludePurchaseId = "pur_existing_1"
        )
        assertTrue(editReport.duplicateMatches.isEmpty())
    }
}
