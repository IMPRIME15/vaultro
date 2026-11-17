package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.ReceiptOcrExtractor
import com.example.ai.ReceiptQualityAndDuplicateAnalyzer
import com.example.ai.ScanDiagnosticReport
import com.example.ai.VaultAiRepository
import com.example.model.AttachmentType
import com.example.model.ExtractedReceiptData
import com.example.model.PurchaseRecord
import com.example.util.FileAttachmentUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ReceiptScanState {
    data object Idle : ReceiptScanState
    data class Scanning(val statusText: String) : ReceiptScanState
    data class Success(
        val extracted: ExtractedReceiptData,
        val rawOcrText: String = "",
        val usedAiRefinement: Boolean = false,
        val diagnostics: ScanDiagnosticReport = ScanDiagnosticReport()
    ) : ReceiptScanState
    data class Error(val message: String) : ReceiptScanState
}

class ReceiptScannerViewModel(
    private val aiRepository: VaultAiRepository = VaultAiRepository(),
    private val ocrExtractor: ReceiptOcrExtractor = ReceiptOcrExtractor()
) : ViewModel() {

    private val _scanState = MutableStateFlow<ReceiptScanState>(ReceiptScanState.Idle)
    val scanState: StateFlow<ReceiptScanState> = _scanState.asStateFlow()

    fun scanAttachedDocument(
        context: Context,
        uriString: String,
        attachmentType: AttachmentType,
        existingPurchases: List<PurchaseRecord> = emptyList(),
        editingPurchaseId: String? = null
    ) {
        viewModelScope.launch {
            _scanState.value = ReceiptScanState.Scanning("Preparing receipt image for OCR...")
            val bitmaps: List<Bitmap> = when (attachmentType) {
                AttachmentType.IMAGE -> {
                    listOfNotNull(FileAttachmentUtils.loadScaledBitmapFromUri(context, uriString))
                }
                AttachmentType.PDF -> {
                    _scanState.value = ReceiptScanState.Scanning("Rendering PDF invoice pages for OCR...")
                    FileAttachmentUtils.renderPdfPagesToBitmaps(context, uriString, maxPages = 2)
                }
                AttachmentType.NONE -> emptyList()
            }

            if (bitmaps.isEmpty()) {
                _scanState.value = ReceiptScanState.Error("Could not read image or PDF document for scanning.")
                return@launch
            }

            // Step 1: On-device ML Kit OCR extraction (works online & offline)
            _scanState.value = ReceiptScanState.Scanning("Running on-device OCR to detect product, shop, date & total...")
            val ocrResult = ocrExtractor.extractFromBitmaps(bitmaps)
            val ocrData = ocrResult.getOrNull()?.extracted
            val rawOcrText = ocrResult.getOrNull()?.rawText.orEmpty()

            // Step 2: Optional cloud AI vision refinement only when online
            val isOnline = com.example.util.NetworkMonitor.isCurrentlyOnline(context)
            val aiResult = if (isOnline) {
                _scanState.value = ReceiptScanState.Scanning("AI Auto-Detection analyzing receipt fields & clarity...")
                aiRepository.extractReceiptDetails(bitmaps)
            } else {
                null
            }
            val aiData = aiResult?.getOrNull()

            if (ocrData == null && aiData == null) {
                val errMsg = ocrResult.exceptionOrNull()?.localizedMessage
                    ?: aiResult?.exceptionOrNull()?.localizedMessage
                    ?: "Could not detect text on this receipt. Please retake a clearer photo or enter fields manually."
                _scanState.value = ReceiptScanState.Error(errMsg)
                return@launch
            }

            val merged = mergeExtracted(ocr = ocrData, ai = aiData)
            val diagnostics = ReceiptQualityAndDuplicateAnalyzer.analyze(
                extracted = merged,
                rawOcrText = rawOcrText,
                existingPurchases = existingPurchases,
                excludePurchaseId = editingPurchaseId
            )
            _scanState.value = ReceiptScanState.Success(
                extracted = merged,
                rawOcrText = rawOcrText,
                usedAiRefinement = aiData != null,
                diagnostics = diagnostics
            )
        }
    }

    private fun mergeExtracted(
        ocr: ExtractedReceiptData?,
        ai: ExtractedReceiptData?
    ): ExtractedReceiptData {
        if (ai == null) return ocr!!
        if (ocr == null) return ai
        return ExtractedReceiptData(
            productName = ai.productName.ifBlank { ocr.productName },
            brand = ai.brand.ifBlank { ocr.brand },
            merchantName = ai.merchantName.ifBlank { ocr.merchantName },
            purchasePriceInr = ai.purchasePriceInr ?: ocr.purchasePriceInr,
            purchaseDateMillis = ai.purchaseDateMillis ?: ocr.purchaseDateMillis,
            invoiceNumber = ai.invoiceNumber.ifBlank { ocr.invoiceNumber },
            returnDeadlineMillis = ai.returnDeadlineMillis ?: ocr.returnDeadlineMillis,
            warrantyDurationMonths = ai.warrantyDurationMonths ?: ocr.warrantyDurationMonths,
            warrantyExpiryMillis = ai.warrantyExpiryMillis ?: ocr.warrantyExpiryMillis,
            category = ai.category.ifBlank { ocr.category },
            notes = ai.notes.ifBlank { ocr.notes },
            isUnclearReceipt = ai.isUnclearReceipt,
            clarityWarning = ai.clarityWarning ?: ocr.clarityWarning
        )
    }

    fun resetState() {
        _scanState.value = ReceiptScanState.Idle
    }
}
