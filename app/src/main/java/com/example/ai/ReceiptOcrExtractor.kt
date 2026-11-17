package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.model.ExtractedReceiptData
import com.example.util.DateAndCurrencyUtils
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrReceiptResult(
    val rawText: String,
    val extracted: ExtractedReceiptData
)

class ReceiptOcrExtractor {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun extractFromBitmaps(bitmaps: List<Bitmap>): Result<OcrReceiptResult> {
        if (bitmaps.isEmpty()) {
            return Result.failure(IllegalArgumentException("No image provided for OCR."))
        }
        return try {
            val combinedText = StringBuilder()
            for (bitmap in bitmaps.take(2)) {
                val visionText = recognizeBitmap(bitmap)
                val formatted = buildOrderedText(visionText)
                if (formatted.isNotBlank()) {
                    if (combinedText.isNotEmpty()) combinedText.append('\n')
                    combinedText.append(formatted)
                }
            }
            val raw = combinedText.toString().trim()
            if (raw.isBlank()) {
                return Result.failure(IllegalStateException("No readable text detected on the receipt image."))
            }
            Result.success(parseReceiptText(raw))
        } catch (e: Exception) {
            Log.e(TAG, "ML Kit OCR extraction failed", e)
            Result.failure(e)
        }
    }

    private suspend fun recognizeBitmap(bitmap: Bitmap): Text =
        suspendCancellableCoroutine { continuation ->
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(inputImage)
                .addOnSuccessListener { result ->
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(error)
                    }
                }
        }

    private fun buildOrderedText(visionText: Text): String {
        val sortedLines = visionText.textBlocks
            .flatMap { it.lines }
            .sortedWith(
                compareBy<Text.Line> { it.boundingBox?.top ?: 0 }
                    .thenBy { it.boundingBox?.left ?: 0 }
            )
        if (sortedLines.isEmpty()) return visionText.text
        return sortedLines.joinToString("\n") { it.text.trim() }
    }

    fun parseReceiptText(rawText: String): OcrReceiptResult {
        val lines = rawText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val vendor = extractVendor(lines)
        val purchaseDateMillis = extractDateMillis(lines)
        val totalAmount = extractTotalAmount(lines)
        val invoiceNumber = extractInvoiceNumber(lines)
        val category = inferCategory(rawText, vendor)
        val productName = extractPrimaryItem(lines, vendor, category)
        val brand = extractBrand(lines, productName)
        val warrantyDurationMonths = extractExplicitWarrantyMonths(lines)
        val warrantyExpiryMillis = if (purchaseDateMillis != null && warrantyDurationMonths != null) {
            DateAndCurrencyUtils.addMonthsMillis(purchaseDateMillis, warrantyDurationMonths.toInt())
        } else null
        val returnDeadlineMillis = extractExplicitReturnDeadline(lines, purchaseDateMillis)

        return OcrReceiptResult(
            rawText = rawText,
            extracted = ExtractedReceiptData(
                productName = productName,
                brand = brand,
                merchantName = vendor,
                purchasePriceInr = totalAmount,
                purchaseDateMillis = purchaseDateMillis,
                invoiceNumber = invoiceNumber,
                returnDeadlineMillis = returnDeadlineMillis,
                warrantyDurationMonths = warrantyDurationMonths,
                warrantyExpiryMillis = warrantyExpiryMillis,
                category = category
            )
        )
    }

    private fun extractVendor(lines: List<String>): String {
        val ignoreHeaderWords = listOf(
            "tax invoice", "retail invoice", "cash memo", "sales receipt", "receipt",
            "invoice", "bill of supply", "duplicate", "original", "customer copy",
            "merchant copy", "welcome", "thank you", "gstin", "gst no", "cin", "tin",
            "fssai", "tel", "phone", "mob", "mobile", "fax", "email", "www.", "http",
            "date", "time", "bill no", "inv no", "order", "cashier", "table"
        )

        for (line in lines.take(7)) {
            val cleaned = line.replace(Regex("[^A-Za-z0-9&'.,\\-\\s]"), " ").trim()
            val lower = cleaned.lowercase(Locale.US)
            if (cleaned.length < 3) continue
            if (ignoreHeaderWords.any { lower == it || lower.startsWith("$it:") || lower.startsWith("$it ") || lower.contains("gstin") }) {
                continue
            }
            // Skip lines that are mostly digits (phone numbers, dates, pin codes)
            val letterCount = cleaned.count { it.isLetter() }
            val digitCount = cleaned.count { it.isDigit() }
            if (letterCount < 3 || digitCount > letterCount) continue
            // Skip pure address markers
            if (Regex(".*\\b(road|street|st\\.|ave|floor|sector|block|phase|nagar|marg|pin\\s*-?\\s*\\d{6})\\b.*", RegexOption.IGNORE_CASE).matches(lower)) {
                continue
            }
            return cleaned.take(80)
        }

        // Fallback: first line with at least 3 letters
        return lines.firstOrNull { it.count { ch -> ch.isLetter() } >= 3 }
            ?.take(80)
            .orEmpty()
    }

    private fun extractDateMillis(lines: List<String>): Long? {
        val dateKeywordRegex = Regex("(date|dated|bill\\s*dt|inv\\s*dt|issued|purchase)", RegexOption.IGNORE_CASE)
        val prioritizedLines = lines.sortedByDescending { if (dateKeywordRegex.containsMatchIn(it)) 1 else 0 }

        val dateCandidatePatterns = listOf(
            // YYYY-MM-DD or YYYY/MM/DD
            Regex("\\b(20\\d{2}[-/]\\d{1,2}[-/]\\d{1,2})\\b") to listOf("yyyy-MM-dd", "yyyy/MM/dd"),
            // DD/MM/YYYY or DD-MM-YYYY or DD.MM.YYYY
            Regex("\\b(\\d{1,2}[-/.]\\d{1,2}[-/.]20\\d{2})\\b") to listOf("dd/MM/yyyy", "dd-MM-yyyy", "dd.MM.yyyy", "MM/dd/yyyy", "MM-dd-yyyy"),
            // DD MMM YYYY or DD-MMM-YYYY
            Regex("\\b(\\d{1,2}[\\s-][A-Za-z]{3,9}[\\s-,]+20\\d{2})\\b") to listOf("dd MMM yyyy", "dd-MMM-yyyy", "dd MMMM yyyy"),
            // MMM DD, YYYY
            Regex("\\b([A-Za-z]{3,9}\\s+\\d{1,2},?\\s+20\\d{2})\\b") to listOf("MMM dd, yyyy", "MMM dd yyyy", "MMMM dd, yyyy"),
            // DD/MM/YY or DD-MM-YY
            Regex("\\b(\\d{1,2}[-/]\\d{1,2}[-/]\\d{2})\\b") to listOf("dd/MM/yy", "dd-MM-yy", "MM/dd/yy"),
            // DD MMM YY
            Regex("\\b(\\d{1,2}[\\s-][A-Za-z]{3}[\\s-]\\d{2})\\b") to listOf("dd MMM yy", "dd-MMM-yy")
        )

        for (line in prioritizedLines) {
            for ((regex, formats) in dateCandidatePatterns) {
                val match = regex.find(line)?.groupValues?.getOrNull(1) ?: continue
                val normalized = match.replace(',', ' ').replace(Regex("\\s+"), " ").trim()
                for (pattern in formats) {
                    val cleanPattern = pattern.replace(',', ' ').replace(Regex("\\s+"), " ").trim()
                    try {
                        val sdf = SimpleDateFormat(cleanPattern, Locale.US).apply { isLenient = false }
                        val parsed = sdf.parse(normalized)
                        if (parsed != null) return parsed.time
                    } catch (_: Exception) {
                    }
                }
            }
        }
        return null
    }

    private fun extractTotalAmount(lines: List<String>): Double? {
        val highPriorityTotal = Regex(
            "(grand\\s*total|net\\s*payable|total\\s*payable|amount\\s*payable|net\\s*amount|amount\\s*paid|total\\s*amount|balance\\s*due|final\\s*total|invoice\\s*total)",
            RegexOption.IGNORE_CASE
        )
        val mediumTotal = Regex("\\btotal\\b", RegexOption.IGNORE_CASE)
        val excludedTotalWords = Regex(
            "(sub\\s*-?\\s*total|total\\s*qty|total\\s*items|total\\s*tax|total\\s*gst|total\\s*discount|total\\s*savings|qty|items)",
            RegexOption.IGNORE_CASE
        )
        val paymentKeywords = Regex("(amount|paid|upi|card|cash|net\\s*amt|payable|subtotal)", RegexOption.IGNORE_CASE)

        val amountRegex = Regex("(?:₹|Rs\\.?|INR|\\$|USD)?\\s*(\\d{1,3}(?:,\\d{2,3})*(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?)", RegexOption.IGNORE_CASE)

        fun parseAmountsFromLine(line: String): List<Double> {
            // Avoid date strings or phone numbers masquerading as amounts
            val stripped = line
                .replace(Regex("\\b\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4}\\b"), " ")
                .replace(Regex("\\b\\d{10,}\\b"), " ")
            return amountRegex.findAll(stripped).mapNotNull { match ->
                val rawNum = match.groupValues[1].replace(",", "")
                val full = match.value.trim()
                val hasCurrencyOrDecimal = full.contains('.') ||
                    full.contains('₹') ||
                    full.contains('$') ||
                    full.contains("rs", ignoreCase = true) ||
                    full.contains("inr", ignoreCase = true)
                val value = rawNum.toDoubleOrNull()
                if (value != null && value > 0.0 && value <= 10_000_000.0 && (hasCurrencyOrDecimal || value >= 10.0)) {
                    value
                } else null
            }.toList()
        }

        // Priority 1: Grand total / Net payable lines (take last match on line)
        for (i in lines.indices.reversed()) {
            val line = lines[i]
            if (highPriorityTotal.containsMatchIn(line)) {
                val nums = parseAmountsFromLine(line)
                if (nums.isNotEmpty()) return nums.last()
                // Sometimes total label is on one line and amount is on the very next OCR line
                if (i + 1 < lines.size) {
                    val nextNums = parseAmountsFromLine(lines[i + 1])
                    if (nextNums.isNotEmpty()) return nextNums.last()
                }
            }
        }

        // Priority 2: Generic "Total" lines (excluding subtotal, total qty, total tax, etc.)
        for (i in lines.indices.reversed()) {
            val line = lines[i]
            if (mediumTotal.containsMatchIn(line) && !excludedTotalWords.containsMatchIn(line)) {
                val nums = parseAmountsFromLine(line)
                if (nums.isNotEmpty()) return nums.last()
                if (i + 1 < lines.size) {
                    val nextNums = parseAmountsFromLine(lines[i + 1])
                    if (nextNums.isNotEmpty()) return nextNums.last()
                }
            }
        }

        // Priority 3: Payment / Amount / Subtotal lines
        for (i in lines.indices.reversed()) {
            val line = lines[i]
            if (paymentKeywords.containsMatchIn(line)) {
                val nums = parseAmountsFromLine(line)
                if (nums.isNotEmpty()) return nums.last()
            }
        }

        // Fallback: Largest decimal/currency amount on the receipt
        val allCurrencyCandidates = lines.flatMap { line ->
            Regex("(?:₹|Rs\\.?|INR|\\$)\\s*(\\d{1,3}(?:,\\d{2,3})*(?:\\.\\d{1,2})?|\\d+\\.\\d{2})|\\b(\\d{1,6}\\.\\d{2})\\b", RegexOption.IGNORE_CASE)
                .findAll(line)
                .mapNotNull { m ->
                    val token = m.groupValues[1].ifBlank { m.groupValues[2] }.replace(",", "")
                    token.toDoubleOrNull()?.takeIf { it in 1.0..10_000_000.0 }
                }
                .toList()
        }
        return allCurrencyCandidates.maxOrNull()
    }

    private fun extractInvoiceNumber(lines: List<String>): String {
        val invRegex = Regex(
            "(?:inv(?:oice)?|bill|receipt|order|txn|ref)\\s*(?:no\\.?|number|#|:)?\\s*[:#-]?\\s*([A-Z0-9][A-Z0-9\\-/]{2,24})",
            RegexOption.IGNORE_CASE
        )
        for (line in lines) {
            val match = invRegex.find(line)?.groupValues?.getOrNull(1)?.trim()
            if (!match.isNullOrBlank() && match.any { it.isDigit() }) {
                return match
            }
        }
        return ""
    }

    private fun inferCategory(rawText: String, vendor: String): String {
        val haystack = "$vendor $rawText".lowercase(Locale.US)
        return when {
            listOf("pharmacy", "medical", "chemist", "apollo", "medplus", "1mg", "pharmeasy", "tablet", "capsule", "clinic").any { haystack.contains(it) } -> "Health"
            listOf("apple", "croma", "reliance digital", "vijay sales", "laptop", "macbook", "iphone", "samsung", "sony", "tv", "earbuds", "ssd", "keyboard", "electronics", "charger").any { haystack.contains(it) } -> "Electronics"
            listOf("refrigerator", "washing machine", "microwave", "ac ", "air conditioner", "vacuum", "dishwasher", "geyser", "appliance").any { haystack.contains(it) } -> "Home Appliances"
            listOf("kitchen", "blender", "mixer", "cooker", "kettle", "pan", "mug", "coffee", "restaurant", "cafe", "bakery", "grocery", "mart").any { haystack.contains(it) } -> "Kitchen"
            listOf("apparel", "fashion", "zara", "h&m", "uniqlo", "nike", "adidas", "myntra", "ajio", "shirt", "shoes", "jacket").any { haystack.contains(it) } -> "Fashion"
            else -> "General"
        }
    }

    private fun extractPrimaryItem(lines: List<String>, vendor: String, category: String): String {
        val skipPattern = Regex(
            "(total|subtotal|tax|gst|cgst|sgst|igst|discount|cash|upi|card|change|balance|thank you|visit again|invoice|bill|receipt|date|time|gstin|phone|tel|www\\.|http|qty|price|rate|amount)",
            RegexOption.IGNORE_CASE
        )
        val itemCandidate = lines.drop(1).firstOrNull { line ->
            val lower = line.lowercase(Locale.US)
            line != vendor &&
                line.length in 4..64 &&
                line.count { it.isLetter() } >= 4 &&
                !skipPattern.containsMatchIn(lower) &&
                !Regex("^\\d[\\d\\s\\-/.:]+$").matches(line)
        }
        if (!itemCandidate.isNullOrBlank()) {
            // Strip trailing price tokens like "1,299.00"
            val cleaned = itemCandidate
                .replace(Regex("(?:₹|Rs\\.?|INR|\\$)?\\s*\\d{1,3}(?:,\\d{2,3})*(?:\\.\\d{1,2})?$"), "")
                .trim()
            if (cleaned.length >= 3) return cleaned.take(90)
        }
        return if (vendor.isNotBlank()) "$vendor ($category)" else ""
    }

    private fun extractBrand(lines: List<String>, productName: String): String {
        val labelMatch = lines.firstNotNullOfOrNull { line ->
            Regex("(?:brand|make|manufacturer)\\s*[:\\-]\\s*([A-Za-z0-9&\\-\\s]{2,32})", RegexOption.IGNORE_CASE)
                .find(line)?.groupValues?.getOrNull(1)?.trim()
        }
        if (!labelMatch.isNullOrBlank()) return labelMatch.take(50)

        val knownBrands = listOf(
            "Apple", "Samsung", "Sony", "LG", "Dell", "HP", "Lenovo", "Asus", "Acer",
            "Philips", "Bosch", "Whirlpool", "Dyson", "Bose", "JBL", "Logitech", "Anker",
            "Xiaomi", "OnePlus", "Realme", "Nothing", "Nike", "Adidas", "Puma", "Boat",
            "Havells", "Prestige", "Bajaj", "IFB", "Voltas", "Panasonic", "Canon", "Nikon"
        )
        val combined = "$productName ${lines.joinToString(" ")}"
        return knownBrands.firstOrNull { brand ->
            Regex("\\b${Regex.escape(brand)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(combined)
        }.orEmpty()
    }

    private fun extractExplicitWarrantyMonths(lines: List<String>): Long? {
        val yearRegex = Regex("(?:warranty|guarantee)\\s*(?:period|term|:|\\-|of|for)?\\s*(\\d{1,2})\\s*(?:yr|yrs|year|years)\\b|\\b(\\d{1,2})\\s*(?:yr|yrs|year|years)\\s*(?:manufacturer\\s*)?(?:warranty|guarantee)\\b", RegexOption.IGNORE_CASE)
        val monthRegex = Regex("(?:warranty|guarantee)\\s*(?:period|term|:|\\-|of|for)?\\s*(\\d{1,3})\\s*(?:mo|mos|month|months)\\b|\\b(\\d{1,3})\\s*(?:mo|mos|month|months)\\s*(?:manufacturer\\s*)?(?:warranty|guarantee)\\b", RegexOption.IGNORE_CASE)
        for (line in lines) {
            yearRegex.find(line)?.let { match ->
                val raw = match.groupValues[1].ifBlank { match.groupValues[2] }.toLongOrNull()
                if (raw != null && raw in 1L..20L) return raw * 12L
            }
            monthRegex.find(line)?.let { match ->
                val raw = match.groupValues[1].ifBlank { match.groupValues[2] }.toLongOrNull()
                if (raw != null && raw in 1L..240L) return raw
            }
        }
        return null
    }

    private fun extractExplicitReturnDeadline(lines: List<String>, purchaseDateMillis: Long?): Long? {
        if (purchaseDateMillis == null || purchaseDateMillis <= 0L) return null
        val returnDaysRegex = Regex("(?:return(?:able)?|exchange)\\s*(?:within|window|period|:|\\-|of|for)?\\s*(\\d{1,2})\\s*days?\\b|\\b(\\d{1,2})[-\\s]*days?\\s*(?:easy\\s*)?(?:return|replacement|exchange)\\b", RegexOption.IGNORE_CASE)
        for (line in lines) {
            returnDaysRegex.find(line)?.let { match ->
                val days = match.groupValues[1].ifBlank { match.groupValues[2] }.toLongOrNull()
                if (days != null && days in 1L..90L) {
                    return purchaseDateMillis + TimeUnit.DAYS.toMillis(days)
                }
            }
        }
        return null
    }

    private companion object {
        const val TAG = "ReceiptOcrExtractor"
    }
}
