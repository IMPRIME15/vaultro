package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FamilyVaultRepository
import com.example.data.SyncedList
import com.example.model.FamilyGroup
import com.example.model.FamilyMember
import com.example.model.FamilyRole
import com.example.model.MedicineRecord
import com.example.model.PurchaseRecord
import com.example.model.SharedVaultRecord
import com.example.model.UiState
import com.example.util.NetworkMonitor
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class FamilyVaultViewModel(
    private val repository: FamilyVaultRepository,
    val currentUserId: String,
    private val networkMonitor: NetworkMonitor? = null
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val familyGroupState: StateFlow<UiState<FamilyGroup?>> =
        repository.observeUserFamilyGroup(currentUserId)
            .map<FamilyGroup?, UiState<FamilyGroup?>> { UiState.Success(it) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                Log.w(TAG, "Error loading family group", error)
                emit(UiState.Error((fsErr ?: error).localizedMessage ?: "Failed to load Family Vault"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val membersState: StateFlow<List<FamilyMember>> =
        familyGroupState
            .flatMapLatest { state ->
                val group = (state as? UiState.Success)?.data
                if (group == null || group.id.isBlank()) {
                    flowOf(emptyList())
                } else {
                    repository.observeFamilyMembers(group.id)
                        .catch { emit(emptyList()) }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = emptyList()
            )

    private val sharedSyncState: StateFlow<SyncedList<SharedVaultRecord>> =
        familyGroupState
            .flatMapLatest { state ->
                val group = (state as? UiState.Success)?.data
                if (group == null || group.id.isBlank()) {
                    flowOf(SyncedList())
                } else {
                    repository.observeSharedRecords(group.id)
                        .catch { emit(SyncedList()) }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = SyncedList()
            )

    val sharedRecordsState: StateFlow<List<SharedVaultRecord>> = sharedSyncState
        .map { it.items }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val sharedRecordIds: StateFlow<Set<String>> = sharedRecordsState
        .map { records -> records.map { it.id }.toSet() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptySet()
        )

    fun currentRole(group: FamilyGroup?): FamilyRole {
        if (group != null && group.adminUid == currentUserId) return FamilyRole.ADMIN
        val member = membersState.value.firstOrNull { it.userId == currentUserId }
        return member?.resolvedRole() ?: FamilyRole.MEMBER
    }

    fun isPurchaseShared(purchaseId: String): Boolean {
        val shareId = repository.shareIdForPurchase(currentUserId, purchaseId)
        return shareId in sharedRecordIds.value
    }

    fun isMedicineShared(medicineId: String): Boolean {
        val shareId = repository.shareIdForMedicine(currentUserId, medicineId)
        return shareId in sharedRecordIds.value
    }

    fun createFamily(
        familyName: String,
        userName: String,
        userEmail: String,
        userPhotoUrl: String?
    ) {
        viewModelScope.launch {
            _busy.value = true
            val online = networkMonitor?.refresh() ?: true
            repository.createFamily(
                familyName = familyName,
                creatorName = userName,
                creatorEmail = userEmail,
                creatorPhotoUrl = userPhotoUrl
            ).onSuccess { group ->
                _statusMessage.value = if (online) {
                    "Family Vault \"${group.name}\" created! Invite code: ${group.inviteCode}"
                } else {
                    "Family Vault created offline — will sync when online."
                }
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Failed to create family."
            }
            _busy.value = false
        }
    }

    fun joinFamily(
        inviteCode: String,
        userName: String,
        userEmail: String,
        userPhotoUrl: String?
    ) {
        viewModelScope.launch {
            _busy.value = true
            repository.joinFamilyByCode(
                rawCode = inviteCode,
                memberName = userName,
                memberEmail = userEmail,
                memberPhotoUrl = userPhotoUrl
            ).onSuccess { familyName ->
                _statusMessage.value = "Joined Family Vault: $familyName"
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Could not join family."
            }
            _busy.value = false
        }
    }

    fun regenerateInviteCode(group: FamilyGroup) {
        viewModelScope.launch {
            _busy.value = true
            repository.regenerateInviteCode(group)
                .onSuccess { newCode ->
                    _statusMessage.value = "New invitation code active: $newCode"
                }
                .onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Could not regenerate invite code."
                }
            _busy.value = false
        }
    }

    fun removeMember(group: FamilyGroup, targetUid: String, targetName: String) {
        viewModelScope.launch {
            _busy.value = true
            repository.removeMember(group, targetUid)
                .onSuccess {
                    _statusMessage.value = "Removed $targetName from Family Vault."
                }
                .onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Failed to remove member."
                }
            _busy.value = false
        }
    }

    fun leaveOrDisbandFamily(group: FamilyGroup) {
        viewModelScope.launch {
            _busy.value = true
            val ownSharedIds = sharedRecordsState.value
                .filter { it.ownerUid == currentUserId }
                .map { it.id }
            repository.leaveOrDisbandFamily(group, ownSharedIds)
                .onSuccess {
                    _statusMessage.value = if (group.adminUid == currentUserId) {
                        "Family Vault disbanded. Your private records remain safe."
                    } else {
                        "Left Family Vault. Your private records remain private."
                    }
                }
                .onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Failed to leave Family Vault."
                }
            _busy.value = false
        }
    }

    fun sharePurchase(ownerName: String, purchase: PurchaseRecord) {
        val group = (familyGroupState.value as? UiState.Success)?.data ?: run {
            _statusMessage.value = "Create or join a Family Vault first to share records."
            return
        }
        viewModelScope.launch {
            val online = networkMonitor?.refresh() ?: true
            repository.sharePurchase(group.id, ownerName, purchase)
                .onSuccess {
                    _statusMessage.value = if (online) {
                        "\"${purchase.productName}\" shared with ${group.name}."
                    } else {
                        "Shared offline — will sync with family when online."
                    }
                }
                .onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Failed to share receipt."
                }
        }
    }

    fun shareMedicine(ownerName: String, medicine: MedicineRecord) {
        val group = (familyGroupState.value as? UiState.Success)?.data ?: run {
            _statusMessage.value = "Create or join a Family Vault first to share records."
            return
        }
        viewModelScope.launch {
            val online = networkMonitor?.refresh() ?: true
            repository.shareMedicine(group.id, ownerName, medicine)
                .onSuccess {
                    _statusMessage.value = if (online) {
                        "\"${medicine.medicineName}\" shared with ${group.name}."
                    } else {
                        "Shared offline — will sync with family when online."
                    }
                }
                .onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Failed to share medicine."
                }
        }
    }

    fun unshareRecord(shareId: String, title: String) {
        val group = (familyGroupState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.unshareRecord(group.id, shareId)
                .onSuccess {
                    _statusMessage.value = "\"$title\" removed from Family Vault (private copy preserved)."
                }
                .onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Failed to unshare record."
                }
        }
    }

    fun toggleSharePurchase(ownerName: String, purchase: PurchaseRecord) {
        val shareId = repository.shareIdForPurchase(currentUserId, purchase.id)
        if (shareId in sharedRecordIds.value) {
            unshareRecord(shareId, purchase.productName)
        } else {
            sharePurchase(ownerName, purchase)
        }
    }

    fun toggleShareMedicine(ownerName: String, medicine: MedicineRecord) {
        val shareId = repository.shareIdForMedicine(currentUserId, medicine.id)
        if (shareId in sharedRecordIds.value) {
            unshareRecord(shareId, medicine.medicineName)
        } else {
            shareMedicine(ownerName, medicine)
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    private companion object {
        const val TAG = "FamilyVaultViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
