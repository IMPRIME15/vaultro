package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.VaultAiRepository
import com.example.model.PurchaseRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ClaimDraftState {
    data object Idle : ClaimDraftState
    data object Loading : ClaimDraftState
    data class Streaming(val rawText: String) : ClaimDraftState
    data object Ready : ClaimDraftState
    data class Error(val message: String) : ClaimDraftState
}

class WarrantyClaimViewModel(
    private val aiRepository: VaultAiRepository = VaultAiRepository()
) : ViewModel() {

    private val _draftState = MutableStateFlow<ClaimDraftState>(ClaimDraftState.Idle)
    val draftState: StateFlow<ClaimDraftState> = _draftState.asStateFlow()

    private val _subject = MutableStateFlow("")
    val subject: StateFlow<String> = _subject.asStateFlow()

    private val _body = MutableStateFlow("")
    val body: StateFlow<String> = _body.asStateFlow()

    fun updateSubject(value: String) {
        _subject.value = value
    }

    fun updateBody(value: String) {
        _body.value = value
    }

    fun generateClaimEmail(
        purchase: PurchaseRecord,
        issueDescription: String,
        customerName: String
    ) {
        if (issueDescription.isBlank()) {
            _draftState.value = ClaimDraftState.Error("Please describe the product issue first.")
            return
        }
        viewModelScope.launch {
            _draftState.value = ClaimDraftState.Loading
            var latestText = ""
            try {
                aiRepository.streamWarrantyClaimEmail(purchase, issueDescription, customerName)
                    .collect { partial ->
                        latestText = partial
                        _draftState.value = ClaimDraftState.Streaming(partial)
                        applyParsedDraft(partial, purchase)
                    }
                applyParsedDraft(latestText, purchase)
                _draftState.value = ClaimDraftState.Ready
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Claim generation timed out", e)
                _draftState.value = ClaimDraftState.Error("Email generation timed out. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Claim generation failed", e)
                _draftState.value = ClaimDraftState.Error(e.localizedMessage ?: "Failed to generate email draft.")
            }
        }
    }

    private fun applyParsedDraft(rawText: String, purchase: PurchaseRecord) {
        val trimmed = rawText.trim()
        val lines = trimmed.lines()
        val firstLine = lines.firstOrNull().orEmpty()
        if (firstLine.startsWith("Subject:", ignoreCase = true)) {
            _subject.value = firstLine.substringAfter(":").trim()
            _body.value = lines.drop(1).joinToString("\n").trimStart()
        } else {
            if (_subject.value.isBlank()) {
                val invPart = if (purchase.invoiceNumber.isNotBlank()) " (Invoice ${purchase.invoiceNumber})" else ""
                _subject.value = "Warranty Claim Request – ${purchase.productName}$invPart"
            }
            _body.value = trimmed
        }
    }

    private companion object {
        const val TAG = "WarrantyClaimVM"
    }
}
