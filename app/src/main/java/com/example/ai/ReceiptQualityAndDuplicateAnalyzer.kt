package com.example.ai

import com.example.model.ExtractedReceiptData
import com.example.model.PurchaseRecord
import com.example.util.DateAndCurrencyUtils
import java.util.Locale
import kotlin.math.abs

data class DuplicateReceiptMatch(
    val existingPurchaseId: String,
    val productName: String,
    val merchantName: String,
    val purchaseDateLabel: String,
    val reason: String
)

data class ScanDiagnosticReport(
    val isUnclearReceipt: Boolean = false,
    val clarityReason: String? = null,
    val missingCriticalFields: List<String> = emptyList(),
    val missingOptionalFields: List<String> = emptyList(),
    val duplicateMatches: List<DuplicateReceiptMatch> = emptyList(),
    val autoDestinations: List<String> = listOf("Purchase Vault")
) {
    val requiresManualReview: Boolean
        get() = isUnclearReceipt || missingCriticalFields.isNotEmpty() || duplicateMatches.isNotEmpty()
}

object ReceiptQualityAndDuplicateAnalyzer {

    fun analyze(
        extracted: ExtractedReceiptData,
        rawOcrText: String,
        existingPurchases: List<PurchaseRecord>,
        excludePurchaseId: String? = null
    ): ScanDiagnosticReport {
        val clarityIssues = mutableListOf<String>()
        if (extracted.isUnclearReceipt) {
            clarityIssues += extracted.clarityWarning?.takeIf { it.isNotBlank() }
                ?: "The uploaded image appears blurry, cropped, or not a clear receipt."
        }

        val cleanedText = rawOcrText.trim()
        val alnumCount = cleanedText.count { it.isLetterOrDigit() }
        val nonSpaceCount = cleanedText.count { !it.isWhitespace() }
        if (cleanedText.isNotBlank()) {
            val alnumRatio = if (nonSpaceCount > 0) alnumCount.toDouble() / nonSpaceCount else 0.0
            if (alnumCount < 18 || alnumRatio < 0.45) {
                clarityIssues += "Very little readable invoice text was detected ($alnumCount characters)."
            }
        }

        val missingCritical = buildList {
            if (extracted.productName.isBlank()) add("Product Name")
            if (extracted.merchantName.isBlank()) add("Shop / Merchant Name")
            if (extracted.purchasePriceInr == null || extracted.purchasePriceInr <= 0.0) add("Purchase Price")
            if (extracted.purchaseDateMillis == null || extracted.purchaseDateMillis <= 0L) add("Purchase Date")
        }

        val missingOptional = buildList {
            if (extracted.brand.isBlank()) add("Brand")
            if (extracted.invoiceNumber.isBlank()) add("Invoice Number")
            if (extracted.warrantyDurationMonths == null && extracted.warrantyExpiryMillis == null) {
                add("Warranty Duration / Expiry")
            }
            if (extracted.returnDeadlineMillis == null) add("Return Deadline")
        }

        val isUnclear = clarityIssues.isNotEmpty() || missingCritical.size >= 3
        val claritySummary = when {
            clarityIssues.isNotEmpty() -> clarityIssues.joinToString(" ")
            missingCritical.size >= 3 -> "Most key invoice fields could not be read clearly. Please verify or retake photo."
            else -> null
        }

        val duplicates = findDuplicates(
            extracted = extracted,
            existingPurchases = existingPurchases,
            excludePurchaseId = excludePurchaseId
        )

        val destinations = buildList {
            add("Purchase Vault")
            if (extracted.warrantyDurationMonths != null || extracted.warrantyExpiryMillis != null) {
                add("Warranty Tracking")
            }
            if (extracted.returnDeadlineMillis != null) {
                add("Return Tracking")
            }
        }

        return ScanDiagnosticReport(
            isUnclearReceipt = isUnclear,
            clarityReason = claritySummary,
            missingCriticalFields = missingCritical,
            missingOptionalFields = missingOptional,
            duplicateMatches = duplicates,
            autoDestinations = destinations
        )
    }

    fun findDuplicates(
        extracted: ExtractedReceiptData,
        existingPurchases: List<PurchaseRecord>,
        excludePurchaseId: String? = null
    ): List<DuplicateReceiptMatch> {
        val normInvoice = normalizeToken(extracted.invoiceNumber)
        val normMerchant = normalizeText(extracted.merchantName)
        val normProduct = normalizeText(extracted.productName)
        val price = extracted.purchasePriceInr
        val dateMillis = extracted.purchaseDateMillis
        val isoDay = DateAndCurrencyUtils.formatIsoDate(dateMillis)

        return existingPurchases
            .asSequence()
            .filter { excludePurchaseId == null || it.id != excludePurchaseId }
            .mapNotNull { existing ->
                val existingInvoice = normalizeToken(existing.invoiceNumber)
                val existingMerchant = normalizeText(existing.merchantName)
                val existingProduct = normalizeText(existing.productName)
                val existingIsoDay = DateAndCurrencyUtils.formatIsoDate(existing.purchaseDateMillis)

                val sameInvoice = normInvoice.length >= 3 && normInvoice == existingInvoice
                val sameMerchant = normMerchant.isNotBlank() && normMerchant == existingMerchant
                val samePrice = price != null && price > 0.0 && abs(price - existing.purchasePriceInr) <= 1.0
                val sameDay = isoDay.isNotBlank() && isoDay == existingIsoDay
                val sameProduct = normProduct.length >= 4 &&
                    (normProduct == existingProduct ||
                        existingProduct.contains(normProduct) ||
                        normProduct.contains(existingProduct))

                val reason = when {
                    sameInvoice -> "Matching Invoice # (${existing.invoiceNumber})"
                    sameMerchant && samePrice && sameDay ->
                        "Same shop, amount (${DateAndCurrencyUtils.formatInr(existing.purchasePriceInr)}) & purchase date"
                    sameMerchant && samePrice && sameProduct ->
                        "Same product, shop & price (${DateAndCurrencyUtils.formatInr(existing.purchasePriceInr)})"
                    else -> null
                }

                reason?.let {
                    DuplicateReceiptMatch(
                        existingPurchaseId = existing.id,
                        productName = existing.productName,
                        merchantName = existing.merchantName,
                        purchaseDateLabel = DateAndCurrencyUtils.formatDate(existing.purchaseDateMillis),
                        reason = it
                    )
                }
            }
            .take(3)
            .toList()
    }

    private fun normalizeToken(value: String): String =
        value.trim().lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")

    private fun normalizeText(value: String): String =
        value.trim().lowercase(Locale.US).replace(Regex("\\s+"), " ")
}
