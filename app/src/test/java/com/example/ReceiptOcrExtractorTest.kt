package com.example

import com.example.ai.ReceiptOcrExtractor
import com.example.util.DateAndCurrencyUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ReceiptOcrExtractorTest {

    private val extractor = ReceiptOcrExtractor()

    @Test
    fun parseIndianRetailReceipt_extractsVendorDateAndTotal() {
        val rawOcr = """
            TAX INVOICE
            CROMA ELECTRONICS
            GSTIN: 27AABCI1234F1Z5
            Bill No: CRM-90812
            Date: 17/11/2026
            Sony WH-1000XM5 Headphones   24,990.00
            Sub Total                    21,177.97
            CGST 9%                       1,906.02
            SGST 9%                       1,906.01
            GRAND TOTAL                 ₹24,990.00
            Thank you for shopping!
        """.trimIndent()

        val result = extractor.parseReceiptText(rawOcr)
        assertEquals("CROMA ELECTRONICS", result.extracted.merchantName)
        assertEquals("2026-11-17", DateAndCurrencyUtils.formatIsoDate(result.extracted.purchaseDateMillis))
        assertNotNull(result.extracted.purchasePriceInr)
        assertEquals(24990.0, result.extracted.purchasePriceInr!!, 0.01)
        assertEquals("CRM-90812", result.extracted.invoiceNumber)
        assertEquals("Electronics", result.extracted.category)
    }

    @Test
    fun parseCoffeeShopReceipt_extractsVendorIsoDateAndNetPayable() {
        val rawOcr = """
            Blue Tokai Coffee Roasters
            Indiranagar Road, Bangalore
            Order #BT-4410
            2026-10-05
            Pour Over Coffee     Rs. 240.00
            Total Qty: 2
            Net Payable: Rs. 480.50
        """.trimIndent()

        val result = extractor.parseReceiptText(rawOcr)
        assertEquals("Blue Tokai Coffee Roasters", result.extracted.merchantName)
        assertEquals("2026-10-05", DateAndCurrencyUtils.formatIsoDate(result.extracted.purchaseDateMillis))
        assertNotNull(result.extracted.purchasePriceInr)
        assertEquals(480.50, result.extracted.purchasePriceInr!!, 0.01)
    }
}
