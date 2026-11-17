package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SyncedItem
import com.example.data.SyncedList
import com.example.data.VaultRepository
import com.example.model.AlertKind
import com.example.model.ImportanceLevel
import com.example.model.MedicineRecord
import com.example.model.PriorityTier
import com.example.model.PurchaseRecord
import com.example.model.ThemePreference
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.model.VaultModule
import com.example.model.VaultNotification
import com.example.notifications.ExpiringSoonChecker
import com.example.notifications.ExpiringSoonReport
import com.example.priority.PriorityCenterReport
import com.example.priority.PriorityEngine
import com.example.util.DateAndCurrencyUtils
import com.example.util.NetworkMonitor
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VaultViewModel(
    private val repository: VaultRepository,
    val currentUserId: String,
    private val networkMonitor: NetworkMonitor? = null
) : ViewModel() {

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage

    private val evaluationClockMillis = MutableStateFlow(System.currentTimeMillis())

    val isOnline: StateFlow<Boolean> = networkMonitor?.isOnline
        ?: MutableStateFlow(true).asStateFlow()

    private var wasOffline = false
    private var hadPendingWrites = false
    private var syncTriggeredForPendingBatch = false

    private val profileSyncState: StateFlow<UiState<SyncedItem<UserProfile?>>> =
        repository.observeUserProfileWithSync(currentUserId)
            .map<SyncedItem<UserProfile?>, UiState<SyncedItem<UserProfile?>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing profile", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load profile"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private val purchasesSyncState: StateFlow<UiState<SyncedList<PurchaseRecord>>> =
        repository.observePurchasesWithSync(currentUserId)
            .map<SyncedList<PurchaseRecord>, UiState<SyncedList<PurchaseRecord>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing purchases", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load purchases"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private val medicinesSyncState: StateFlow<UiState<SyncedList<MedicineRecord>>> =
        repository.observeMedicinesWithSync(currentUserId)
            .map<SyncedList<MedicineRecord>, UiState<SyncedList<MedicineRecord>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing medicines", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load medicines"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val userProfileState: StateFlow<UiState<UserProfile?>> = profileSyncState
        .map { state ->
            when (state) {
                is UiState.Loading -> UiState.Loading
                is UiState.Error -> UiState.Error(state.message)
                is UiState.Success -> UiState.Success(state.data.item)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val purchasesState: StateFlow<UiState<List<PurchaseRecord>>> = purchasesSyncState
        .map { state ->
            when (state) {
                is UiState.Loading -> UiState.Loading
                is UiState.Error -> UiState.Error(state.message)
                is UiState.Success -> UiState.Success(state.data.items)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val medicinesState: StateFlow<UiState<List<MedicineRecord>>> = medicinesSyncState
        .map { state ->
            when (state) {
                is UiState.Loading -> UiState.Loading
                is UiState.Error -> UiState.Error(state.message)
                is UiState.Success -> UiState.Success(state.data.items)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val pendingPurchaseIds: StateFlow<Set<String>> = purchasesSyncState
        .map { (it as? UiState.Success)?.data?.pendingIds ?: emptySet() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptySet()
        )

    val pendingMedicineIds: StateFlow<Set<String>> = medicinesSyncState
        .map { (it as? UiState.Success)?.data?.pendingIds ?: emptySet() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptySet()
        )

    val pendingSyncCount: StateFlow<Int> = combine(
        profileSyncState,
        pendingPurchaseIds,
        pendingMedicineIds
    ) { profileSync, pendingPurchases, pendingMedicines ->
        val profilePending = if ((profileSync as? UiState.Success)?.data?.hasPendingWrites == true) 1 else 0
        profilePending + pendingPurchases.size + pendingMedicines.size
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = 0
    )

    private val notificationReadMap: StateFlow<Map<String, Boolean>> = repository.observeNotificationStates(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            emit(emptyMap())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyMap()
        )

    val priorityCenterState: StateFlow<PriorityCenterReport> = combine(
        purchasesState,
        medicinesState,
        evaluationClockMillis
    ) { purchaseState, medicineState, nowMillis ->
        val purchases = (purchaseState as? UiState.Success)?.data.orEmpty()
        val medicines = (medicineState as? UiState.Success)?.data.orEmpty()
        PriorityEngine.Default.evaluate(purchases, medicines, nowMillis)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = PriorityCenterReport()
    )

    val sortedActivePurchasesState: StateFlow<List<PurchaseRecord>> = combine(
        purchasesState,
        evaluationClockMillis
    ) { purchaseState, nowMillis ->
        val purchases = (purchaseState as? UiState.Success)?.data.orEmpty()
        PriorityEngine.Default.sortActivePurchases(purchases, nowMillis)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    val sortedActiveMedicinesState: StateFlow<List<MedicineRecord>> = combine(
        medicinesState,
        evaluationClockMillis
    ) { medicineState, nowMillis ->
        val medicines = (medicineState as? UiState.Success)?.data.orEmpty()
        PriorityEngine.Default.sortActiveMedicines(medicines, nowMillis)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    val notificationsState: StateFlow<List<VaultNotification>> = combine(
        userProfileState,
        purchasesState,
        medicinesState,
        notificationReadMap,
        evaluationClockMillis
    ) { profileState, purchaseState, medicineState, readMap, nowMillis ->
        val profile = (profileState as? UiState.Success)?.data
        val purchases = (purchaseState as? UiState.Success)?.data.orEmpty()
        val medicines = (medicineState as? UiState.Success)?.data.orEmpty()
        buildNotifications(profile, purchases, medicines, readMap, nowMillis)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    val unreadNotificationsCount: StateFlow<Int> = notificationsState
        .map { list -> list.count { !it.isRead } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = 0
        )

    val expiringSoon7DaysState: StateFlow<ExpiringSoonReport> = combine(
        userProfileState,
        purchasesState,
        medicinesState,
        evaluationClockMillis
    ) { profileState, purchaseState, medicineState, nowMillis ->
        val profile = (profileState as? UiState.Success)?.data
        val purchases = (purchaseState as? UiState.Success)?.data.orEmpty()
        val medicines = (medicineState as? UiState.Success)?.data.orEmpty()
        ExpiringSoonChecker.evaluate(
            purchases = purchases,
            medicines = medicines,
            nowMillis = nowMillis,
            allowPurchaseWarranties = profile?.purchaseRemindersEnabled ?: true,
            allowMedicines = profile?.medicineRemindersEnabled ?: true,
            respectPerItemToggles = true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ExpiringSoonReport()
    )

    init {
        wasOffline = !isOnline.value
        viewModelScope.launch {
            repository.ensureUserProfileAndSeedIfNeeded()
        }
        viewModelScope.launch {
            combine(isOnline, pendingSyncCount) { online, pending -> online to pending }
                .collect { (online, pending) ->
                    if (!online) {
                        wasOffline = true
                        syncTriggeredForPendingBatch = false
                        if (pending > 0) {
                            hadPendingWrites = true
                        }
                    } else {
                        if (pending > 0) {
                            hadPendingWrites = true
                            if (wasOffline || !syncTriggeredForPendingBatch) {
                                syncTriggeredForPendingBatch = true
                                val restoredFromOffline = wasOffline
                                wasOffline = false
                                _actionMessage.value = if (restoredFromOffline) {
                                    "Internet restored — syncing $pending local change(s) to server..."
                                } else {
                                    "Syncing $pending pending change(s) to server..."
                                }
                                repository.triggerAutoSync()
                            }
                        } else {
                            wasOffline = false
                            syncTriggeredForPendingBatch = false
                            if (hadPendingWrites) {
                                hadPendingWrites = false
                                _actionMessage.value = "All offline changes synced to server."
                            }
                        }
                    }
                }
        }
    }

    private fun buildNotifications(
        profile: UserProfile?,
        purchases: List<PurchaseRecord>,
        medicines: List<MedicineRecord>,
        readMap: Map<String, Boolean>,
        nowMillis: Long
    ): List<VaultNotification> {
        val alerts = mutableListOf<VaultNotification>()
        val purchaseAlertsEnabled = profile?.purchaseRemindersEnabled ?: true
        val medicineAlertsEnabled = profile?.medicineRemindersEnabled ?: true
        val returnLead = profile?.returnReminderDaysBefore ?: 3L
        val warrantyLead = profile?.warrantyReminderDaysBefore ?: 14L
        val medicineDefaultLead = profile?.medicineReminderDaysBefore ?: 14L
        val report = PriorityEngine.Default.evaluate(purchases, medicines, nowMillis)

        if (purchaseAlertsEnabled) {
            purchases.filter { !it.completed }.forEach { p ->
                val tier = report.purchasePriorityMap[p.id]?.priorityTier ?: PriorityTier.MEDIUM
                val returnDeadline = p.returnDeadlineMillis
                if (returnDeadline != null && p.returnReminderSet) {
                    val delta = DateAndCurrencyUtils.daysFromNow(returnDeadline, nowMillis)
                    if (delta in -30L..maxOf(returnLead, 7L)) {
                        val key = "purchase_return_${p.id}"
                        alerts.add(
                            VaultNotification(
                                alertKey = key,
                                module = VaultModule.PURCHASE,
                                kind = AlertKind.RETURN_APPROACHING,
                                title = if (delta < 0L) "Overdue return: ${p.productName}" else "Return window closing: ${p.productName}",
                                subtitle = "${p.merchantName} • ${DateAndCurrencyUtils.formatCountdown(delta)} (${DateAndCurrencyUtils.formatDate(returnDeadline)})",
                                targetId = p.id,
                                dueMillis = returnDeadline,
                                daysDelta = delta,
                                isRead = readMap[key] == true,
                                isUrgent7Days = delta <= ExpiringSoonChecker.EXPIRING_SOON_WINDOW_DAYS,
                                priorityTier = tier
                            )
                        )
                    }
                }
                val warrantyExpiry = p.warrantyExpiryMillis
                if (warrantyExpiry != null && p.warrantyReminderSet) {
                    val delta = DateAndCurrencyUtils.daysFromNow(warrantyExpiry, nowMillis)
                    if (delta in 0L..maxOf(warrantyLead, 30L)) {
                        val key = "purchase_warranty_${p.id}"
                        alerts.add(
                            VaultNotification(
                                alertKey = key,
                                module = VaultModule.PURCHASE,
                                kind = AlertKind.WARRANTY_APPROACHING,
                                title = "Warranty expiring soon: ${p.productName}",
                                subtitle = "${p.merchantName} • ${DateAndCurrencyUtils.formatCountdown(delta)} (${DateAndCurrencyUtils.formatDate(warrantyExpiry)})",
                                targetId = p.id,
                                dueMillis = warrantyExpiry,
                                daysDelta = delta,
                                isRead = readMap[key] == true,
                                isUrgent7Days = delta in 0L..ExpiringSoonChecker.EXPIRING_SOON_WINDOW_DAYS,
                                priorityTier = tier
                            )
                        )
                    }
                }
            }
        }

        if (medicineAlertsEnabled) {
            medicines.filter { !it.completed }.forEach { m ->
                val tier = report.medicinePriorityMap[m.id]?.priorityTier ?: PriorityTier.MEDIUM
                val delta = DateAndCurrencyUtils.daysFromNow(m.expiryDateMillis, nowMillis)
                val lead = maxOf(
                    m.reminderDaysBefore,
                    medicineDefaultLead,
                    ExpiringSoonChecker.EXPIRING_SOON_WINDOW_DAYS
                )
                if (delta < 0L) {
                    val key = "medicine_expired_${m.id}"
                    alerts.add(
                        VaultNotification(
                            alertKey = key,
                            module = VaultModule.MEDICINE,
                            kind = AlertKind.MEDICINE_EXPIRED,
                            title = "Expired medicine: ${m.medicineName}",
                            subtitle = "Batch ${m.batchNumber.ifBlank { "N/A" }} expired ${DateAndCurrencyUtils.formatCountdown(delta)}",
                            targetId = m.id,
                            dueMillis = m.expiryDateMillis,
                            daysDelta = delta,
                            isRead = readMap[key] == true,
                            priorityTier = PriorityTier.HIGH
                        )
                    )
                } else if (m.reminderEnabled && delta <= lead) {
                    val key = "medicine_expiring_${m.id}"
                    alerts.add(
                        VaultNotification(
                            alertKey = key,
                            module = VaultModule.MEDICINE,
                            kind = AlertKind.MEDICINE_EXPIRING_SOON,
                            title = "Medicine expiring soon: ${m.medicineName}",
                            subtitle = "Batch ${m.batchNumber.ifBlank { "N/A" }} • ${DateAndCurrencyUtils.formatCountdown(delta)} (${DateAndCurrencyUtils.formatDate(m.expiryDateMillis)})",
                            targetId = m.id,
                            dueMillis = m.expiryDateMillis,
                            daysDelta = delta,
                            isRead = readMap[key] == true,
                            isUrgent7Days = delta in 0L..ExpiringSoonChecker.EXPIRING_SOON_WINDOW_DAYS,
                            priorityTier = tier
                        )
                    )
                }
            }
        }

        return alerts.sortedWith(
            compareBy<VaultNotification> { it.isRead }
                .thenBy { it.priorityTier.rank }
                .thenByDescending { it.isUrgent7Days }
                .thenBy { it.daysDelta }
        )
    }

    fun refreshPriorityEvaluations() {
        evaluationClockMillis.value = System.currentTimeMillis()
    }

    fun setPurchaseCompleted(purchaseId: String, completed: Boolean) {
        viewModelScope.launch {
            val online = networkMonitor?.refresh() ?: isOnline.value
            repository.updatePurchasePriority(purchaseId = purchaseId, completed = completed)
                .onSuccess {
                    _actionMessage.value = when {
                        completed && online -> "Marked completed and archived from active priority."
                        completed -> "Marked completed offline — archived locally & will sync."
                        online -> "Reopened to active Priority Center."
                        else -> "Reopened offline — will sync when online."
                    }
                }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Failed to update status." }
        }
    }

    fun setMedicineCompleted(medicineId: String, completed: Boolean) {
        viewModelScope.launch {
            val online = networkMonitor?.refresh() ?: isOnline.value
            repository.updateMedicinePriority(medicineId = medicineId, completed = completed)
                .onSuccess {
                    _actionMessage.value = when {
                        completed && online -> "Marked resolved and archived from active priority."
                        completed -> "Marked resolved offline — archived locally & will sync."
                        online -> "Reopened to active Priority Center."
                        else -> "Reopened offline — will sync when online."
                    }
                }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Failed to update status." }
        }
    }

    fun updatePurchaseImportance(purchaseId: String, importance: ImportanceLevel?) {
        viewModelScope.launch {
            repository.updatePurchasePriority(
                purchaseId = purchaseId,
                importanceLevel = importance?.name ?: ""
            ).onFailure {
                _actionMessage.value = it.localizedMessage ?: "Failed to update importance."
            }
        }
    }

    fun updateMedicineImportance(medicineId: String, importance: ImportanceLevel?) {
        viewModelScope.launch {
            repository.updateMedicinePriority(
                medicineId = medicineId,
                importanceLevel = importance?.name ?: ""
            ).onFailure {
                _actionMessage.value = it.localizedMessage ?: "Failed to update importance."
            }
        }
    }

    fun updateWarrantyClaimFollowUp(
        purchaseId: String,
        active: Boolean,
        followUpMillis: Long?,
        clearFollowUp: Boolean = false
    ) {
        viewModelScope.launch {
            repository.updatePurchasePriority(
                purchaseId = purchaseId,
                warrantyClaimActive = active,
                warrantyClaimFollowUpMillis = followUpMillis,
                clearFollowUp = clearFollowUp || !active
            ).onSuccess {
                _actionMessage.value = if (active) {
                    "Warranty claim follow-up prioritized."
                } else {
                    "Warranty claim follow-up cleared."
                }
            }.onFailure {
                _actionMessage.value = it.localizedMessage ?: "Failed to update claim follow-up."
            }
        }
    }

    fun updateWarrantyClaimDetails(
        purchaseId: String,
        claimStatus: com.example.model.WarrantyClaimStatus,
        claimNotes: String,
        claimIssueSummary: String
    ) {
        viewModelScope.launch {
            val online = networkMonitor?.refresh() ?: isOnline.value
            repository.updatePurchaseClaimDetails(
                purchaseId = purchaseId,
                claimStatus = claimStatus.name,
                claimNotes = claimNotes,
                claimIssueSummary = claimIssueSummary
            ).onSuccess {
                _actionMessage.value = if (online) {
                    "Claim status saved as ${claimStatus.label}."
                } else {
                    "Claim status saved offline — will sync when online."
                }
            }.onFailure {
                _actionMessage.value = it.localizedMessage ?: "Failed to update claim status."
            }
        }
    }

    fun showActionMessage(message: String) {
        _actionMessage.value = message
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun syncNow() {
        viewModelScope.launch {
            val online = networkMonitor?.refresh() ?: isOnline.value
            if (!online) {
                _actionMessage.value = "You are offline. Changes are safely saved on device and will sync automatically when connected."
            } else {
                repository.triggerAutoSync()
                    .onSuccess {
                        _actionMessage.value = if (pendingSyncCount.value == 0) {
                            "All vault records are synced with server."
                        } else {
                            "Sync completed."
                        }
                    }
                    .onFailure {
                        _actionMessage.value = "Sync queued — will retry automatically."
                    }
            }
        }
    }

    suspend fun savePurchase(record: PurchaseRecord): Result<String> {
        val online = networkMonitor?.refresh() ?: isOnline.value
        return repository.savePurchase(record).also { res ->
            res.onSuccess {
                if (!online) {
                    _actionMessage.value = "Saved in device memory (offline) — will auto-sync when online."
                }
            }
            res.onFailure { _actionMessage.value = it.localizedMessage ?: "Failed to save purchase." }
        }
    }

    suspend fun deletePurchase(purchaseId: String): Result<Unit> {
        val online = networkMonitor?.refresh() ?: isOnline.value
        return repository.deletePurchase(purchaseId).also { res ->
            res.onSuccess {
                _actionMessage.value = if (online) {
                    "Receipt removed from Purchase Vault."
                } else {
                    "Removed locally (offline) — will sync deletion when online."
                }
            }
            res.onFailure { _actionMessage.value = it.localizedMessage ?: "Failed to delete receipt." }
        }
    }

    suspend fun saveMedicine(record: MedicineRecord): Result<String> {
        val online = networkMonitor?.refresh() ?: isOnline.value
        return repository.saveMedicine(record).also { res ->
            res.onSuccess {
                if (!online) {
                    _actionMessage.value = "Saved in device memory (offline) — will auto-sync when online."
                }
            }
            res.onFailure { _actionMessage.value = it.localizedMessage ?: "Failed to save medicine." }
        }
    }

    suspend fun deleteMedicine(medicineId: String): Result<Unit> {
        val online = networkMonitor?.refresh() ?: isOnline.value
        return repository.deleteMedicine(medicineId).also { res ->
            res.onSuccess {
                _actionMessage.value = if (online) {
                    "Medicine removed from Medicine Vault."
                } else {
                    "Removed locally (offline) — will sync deletion when online."
                }
            }
            res.onFailure { _actionMessage.value = it.localizedMessage ?: "Failed to delete medicine." }
        }
    }

    fun updateProfileName(fullName: String) {
        viewModelScope.launch {
            val online = networkMonitor?.refresh() ?: isOnline.value
            repository.updateProfileName(fullName)
                .onSuccess {
                    _actionMessage.value = if (online) {
                        "Profile updated."
                    } else {
                        "Profile saved locally (offline) — will sync when online."
                    }
                }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Failed to update profile." }
        }
    }

    fun updateThemePreference(pref: ThemePreference) {
        viewModelScope.launch {
            repository.updateUserSettings(mapOf("themePreference" to pref.name))
        }
    }

    fun updateSettings(updates: Map<String, Any>) {
        viewModelScope.launch {
            repository.updateUserSettings(updates)
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Failed to update setting." }
        }
    }

    fun markNotificationRead(alertKey: String) {
        viewModelScope.launch {
            repository.markNotificationRead(alertKey, true)
        }
    }

    fun markAllNotificationsRead() {
        val keys = notificationsState.value.filter { !it.isRead }.map { it.alertKey }
        if (keys.isEmpty()) return
        viewModelScope.launch {
            repository.markAllNotificationsRead(keys)
                .onSuccess { _actionMessage.value = "All alerts marked as read." }
        }
    }

    fun loadSampleVaultData() {
        viewModelScope.launch {
            val online = networkMonitor?.refresh() ?: isOnline.value
            repository.loadSampleVaultData()
                .onSuccess {
                    _actionMessage.value = if (online) {
                        "Sample receipts and medicines added to your vault."
                    } else {
                        "Sample data saved on device (offline) — will sync when online."
                    }
                }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Could not load sample data." }
        }
    }

    override fun onCleared() {
        networkMonitor?.unregister()
        super.onCleared()
    }

    private companion object {
        const val TAG = "VaultViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
