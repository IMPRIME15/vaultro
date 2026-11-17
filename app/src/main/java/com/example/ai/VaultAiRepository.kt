package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.model.ExtractedReceiptData
import com.example.model.PurchaseRecord
import com.example.util.DateAndCurrencyUtils
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

class VaultAiRepository {
    private val receiptSchema = Schema.obj(
        properties = mapOf(
            "productName" to Schema.string(
                description = "Primary purchased product or item name if clearly visible, otherwise empty string",
                nullable = true
            ),
            "brand" to Schema.string(
                description = "Product manufacturer or brand name (e.g. Apple, Sony, Samsung, Philips) if explicitly visible, otherwise empty string",
                nullable = true
            ),
            "merchantName" to Schema.string(
                description = "Store, retailer, or shop name if clearly visible, otherwise empty string",
                nullable = true
            ),
            "purchasePriceInr" to Schema.double(
                description = "Total purchase amount in INR/numeric value",
                nullable = true
            ),
            "purchaseDateIso" to Schema.string(
                description = "Purchase date strictly in YYYY-MM-DD format if clearly visible on the document, otherwise null",
                nullable = true
            ),
            "invoiceNumber" to Schema.string(
                description = "Invoice, order, or receipt reference number if visible, otherwise empty string",
                nullable = true
            ),
            "returnDeadlineIso" to Schema.string(
                description = "Return deadline strictly in YYYY-MM-DD format ONLY if explicitly stated or calculable from an explicit return policy on the receipt, otherwise null. Do NOT invent.",
                nullable = true
            ),
            "warrantyDurationMonths" to Schema.integer(
                description = "Warranty duration in months ONLY if explicitly stated on the invoice, otherwise null. Do NOT invent.",
                nullable = true
            ),
            "warrantyExpiryIso" to Schema.string(
                description = "Warranty expiry date in YYYY-MM-DD format ONLY if explicitly printed or directly stated on the receipt, otherwise null. Do NOT invent.",
                nullable = true
            ),
            "category" to Schema.enumeration(
                values = listOf("Electronics", "Home Appliances", "Kitchen", "Fashion", "Health", "General"),
                description = "Best matching purchase category"
            ),
            "isUnclearImage" to Schema.boolean(
                description = "True if the image is blurry, cropped, too dark, unreadable, or not a purchase receipt"
            ),
            "clarityReason" to Schema.string(
                description = "Short explanation if isUnclearImage is true or key invoice text is cut off, otherwise null",
                nullable = true
            )
        ),
        optionalProperties = listOf(
            "productName",
            "brand",
            "merchantName",
            "purchasePriceInr",
            "purchaseDateIso",
            "invoiceNumber",
            "returnDeadlineIso",
            "warrantyDurationMonths",
            "warrantyExpiryIso",
            "clarityReason"
        )
    )

    private val receiptExtractionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = receiptSchema
            }
        )
    }

    private val emailAssistantModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash"
        )
    }

    suspend fun extractReceiptDetails(images: List<Bitmap>): Result<ExtractedReceiptData> {
        if (images.isEmpty()) {
            return Result.failure(IllegalArgumentException("No receipt image or PDF pages provided."))
        }
        return try {
            val response = withTimeout(60_000L) {
                receiptExtractionModel.generateContent(
                    content {
                        text(
                            "Extract purchase invoice details from the attached receipt image(s). " +
                                "CRITICAL RULE: Never invent or guess any field. If product name, brand, shop name, price, " +
                                "invoice number, return deadline, warranty duration, or warranty expiry is not clearly visible on the receipt, return null or empty string. " +
                                "Set isUnclearImage=true if the photo is blurry, cropped, unreadable, or missing essential bill context. " +
                                "Convert currency totals to numeric value."
                        )
                        images.take(2).forEach { bitmap ->
                            image(bitmap)
                        }
                    }
                )
            }
            val jsonStr = response.text
                ?: throw IllegalStateException("AI scanner returned an empty response.")
            val json = JSONObject(jsonStr)
            val purchaseDateMillis = DateAndCurrencyUtils.parseIsoDateToMillis(
                json.optString("purchaseDateIso", "").takeIf { it.isNotBlank() && it != "null" }
            )
            val returnDeadlineMillis = DateAndCurrencyUtils.parseIsoDateToMillis(
                json.optString("returnDeadlineIso", "").takeIf { it.isNotBlank() && it != "null" }
            )
            val warrantyMonths = if (json.has("warrantyDurationMonths") && !json.isNull("warrantyDurationMonths")) {
                json.optLong("warrantyDurationMonths", 0L).takeIf { it > 0L }
            } else null
            val explicitWarrantyExpiry = DateAndCurrencyUtils.parseIsoDateToMillis(
                json.optString("warrantyExpiryIso", "").takeIf { it.isNotBlank() && it != "null" }
            )
            val computedWarrantyExpiry = explicitWarrantyExpiry ?: if (purchaseDateMillis != null && warrantyMonths != null) {
                DateAndCurrencyUtils.addMonthsMillis(purchaseDateMillis, warrantyMonths.toInt())
            } else null

            val price = if (json.has("purchasePriceInr") && !json.isNull("purchasePriceInr")) {
                json.optDouble("purchasePriceInr", -1.0).takeIf { it >= 0.0 }
            } else null

            val isUnclear = json.optBoolean("isUnclearImage", false)
            val clarityReason = json.optString("clarityReason", "").takeIf { it.isNotBlank() && it != "null" }

            Result.success(
                ExtractedReceiptData(
                    productName = json.optString("productName", "").takeIf { it != "null" }.orEmpty(),
                    brand = json.optString("brand", "").takeIf { it != "null" }.orEmpty(),
                    merchantName = json.optString("merchantName", "").takeIf { it != "null" }.orEmpty(),
                    purchasePriceInr = price,
                    purchaseDateMillis = purchaseDateMillis,
                    invoiceNumber = json.optString("invoiceNumber", "").takeIf { it != "null" }.orEmpty(),
                    returnDeadlineMillis = returnDeadlineMillis,
                    warrantyDurationMonths = warrantyMonths,
                    warrantyExpiryMillis = computedWarrantyExpiry,
                    category = json.optString("category", "Electronics").ifBlank { "Electronics" },
                    isUnclearReceipt = isUnclear,
                    clarityWarning = clarityReason
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Receipt extraction timed out", e)
            Result.failure(Exception("Receipt scanning timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Receipt extraction failed", e)
            Result.failure(e)
        }
    }

    fun streamWarrantyClaimEmail(
        purchase: PurchaseRecord,
        issueDescription: String,
        customerName: String
    ): Flow<String> = flow {
        val prompt = buildString {
            appendLine("Draft a concise, polite, and formal warranty claim email for the user to review and send.")
            appendLine("Format clearly with 'Subject: ...' on the first line, followed by a blank line and the email body.")
            appendLine("Use ONLY the provided facts:")
            appendLine("- Customer Name: ${customerName.ifBlank { "Customer" }}")
            appendLine("- Product Name: ${purchase.productName}")
            if (purchase.brand.isNotBlank()) {
                appendLine("- Brand: ${purchase.brand}")
            }
            appendLine("- Merchant / Store: ${purchase.merchantName}")
            appendLine("- Purchase Date: ${DateAndCurrencyUtils.formatDate(purchase.purchaseDateMillis)}")
            appendLine("- Invoice Number: ${purchase.invoiceNumber.ifBlank { "Attached / Not listed" }}")
            appendLine("- Purchase Price: ${DateAndCurrencyUtils.formatInr(purchase.purchasePriceInr)}")
            if (purchase.warrantyDurationMonths != null) {
                appendLine("- Warranty Duration: ${purchase.warrantyDurationMonths} months")
            }
            if (purchase.warrantyExpiryMillis != null) {
                appendLine("- Warranty Expiry Date: ${DateAndCurrencyUtils.formatDate(purchase.warrantyExpiryMillis)}")
            }
            appendLine("- Reported Issue: $issueDescription")
            appendLine("Request clear warranty repair, replacement, or inspection support and reference attached invoice.")
        }

        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            emailAssistantModel.generateContentStream(prompt).collect { chunk ->
                val part = chunk.text.orEmpty()
                if (part.isNotEmpty()) {
                    accumulated.append(part)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("AI returned an empty draft.")
        }
    }

    private companion object {
        const val TAG = "VaultAiRepository"
    }
}
