package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.example.model.ImportanceLevel
import com.example.model.MedicineRecord
import com.example.model.NotificationState
import com.example.model.PurchaseRecord
import com.example.model.ThemePreference
import com.example.model.UserProfile
import com.example.util.NetworkMonitor
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

data class SyncedList<T>(
    val items: List<T> = emptyList(),
    val pendingIds: Set<String> = emptySet(),
    val isFromCache: Boolean = false
)

data class SyncedItem<T>(
    val item: T,
    val hasPendingWrites: Boolean = false,
    val isFromCache: Boolean = false
)

class VaultRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val networkMonitor: NetworkMonitor? = null
) {
    constructor(
        context: Context,
        networkMonitor: NetworkMonitor? = NetworkMonitor(context.applicationContext)
    ) : this(
        db = createConfiguredFirestore(context),
        auth = Firebase.auth,
        networkMonitor = networkMonitor
    )

    private fun isOnlineNow(): Boolean = networkMonitor?.refresh() ?: true

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeUserProfileWithSync(userId: String): Flow<SyncedItem<UserProfile?>> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    val profile = snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                    SyncedItem(
                        item = profile,
                        hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                        isFromCache = snapshot.metadata.isFromCache
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> =
        observeUserProfileWithSync(userId).map { it.item }

    fun observePurchasesWithSync(userId: String): Flow<SyncedList<PurchaseRecord>> = flow {
        val path = "users/$userId/purchases"
        emitAll(
            db.collection("users").document(userId).collection("purchases")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    val items = snapshot.toObjects(
                        PurchaseRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.purchaseDateMillis }
                    val pendingIds = snapshot.documents
                        .filter { it.metadata.hasPendingWrites() }
                        .map { it.id }
                        .toSet()
                    SyncedList(
                        items = items,
                        pendingIds = pendingIds,
                        isFromCache = snapshot.metadata.isFromCache
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observePurchases(userId: String): Flow<List<PurchaseRecord>> =
        observePurchasesWithSync(userId).map { it.items }

    fun observeMedicinesWithSync(userId: String): Flow<SyncedList<MedicineRecord>> = flow {
        val path = "users/$userId/medicines"
        emitAll(
            db.collection("users").document(userId).collection("medicines")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    val items = snapshot.toObjects(
                        MedicineRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.expiryDateMillis }
                    val pendingIds = snapshot.documents
                        .filter { it.metadata.hasPendingWrites() }
                        .map { it.id }
                        .toSet()
                    SyncedList(
                        items = items,
                        pendingIds = pendingIds,
                        isFromCache = snapshot.metadata.isFromCache
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMedicines(userId: String): Flow<List<MedicineRecord>> =
        observeMedicinesWithSync(userId).map { it.items }

    fun observeNotificationStates(userId: String): Flow<Map<String, Boolean>> = flow {
        val path = "users/$userId/notificationStates"
        emitAll(
            db.collection("users").document(userId).collection("notificationStates")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    snapshot.toObjects(
                        NotificationState::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).associate { it.alertKey to it.readFlag }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun triggerAutoSync(): Result<Unit> {
        return try {
            withTimeoutOrNull(30_000L) {
                db.enableNetwork().await()
                db.waitForPendingWrites().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Auto-sync wait interrupted: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Awaits a Firestore mutation when online (up to [WRITE_ACK_TIMEOUT_MS]), or returns
     * immediately when offline after Firestore persists the write into local device memory.
     * Pending local mutations are uploaded automatically by Firestore when internet returns.
     */
    private suspend fun awaitWriteOrQueueOffline(
        task: Task<Void>,
        operation: OperationType,
        path: String
    ) {
        task.addOnFailureListener { error ->
            if (error !is CancellationException) {
                handleFirestoreError(error, operation, path)
            }
        }
        if (isOnlineNow()) {
            withTimeoutOrNull(WRITE_ACK_TIMEOUT_MS) {
                task.await()
            }
        }
    }

    private suspend fun getDocumentCacheAware(
        docRef: com.google.firebase.firestore.DocumentReference
    ): DocumentSnapshot? {
        return if (isOnlineNow()) {
            try {
                withTimeoutOrNull(WRITE_ACK_TIMEOUT_MS) {
                    docRef.get(Source.DEFAULT).await()
                } ?: docRef.get(Source.CACHE).await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                try {
                    docRef.get(Source.CACHE).await()
                } catch (_: Exception) {
                    null
                }
            }
        } else {
            try {
                docRef.get(Source.CACHE).await()
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun ensureUserProfileAndSeedIfNeeded(
        preferredName: String? = null
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val userDoc = db.collection("users").document(uid)
            val snap = getDocumentCacheAware(userDoc)
            val firebaseUser = auth.currentUser
            if (snap == null || !snap.exists()) {
                val resolvedName = preferredName?.takeIf { it.isNotBlank() }
                    ?: firebaseUser?.displayName?.takeIf { it.isNotBlank() }
                    ?: firebaseUser?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                    ?: "Vault Owner"
                val resolvedEmail = firebaseUser?.email?.takeIf { it.isNotBlank() } ?: "user@vaultora.app"
                val profile = UserProfile(
                    userId = uid,
                    fullName = resolvedName,
                    email = resolvedEmail,
                    photoUrl = firebaseUser?.photoUrl?.toString(),
                    themePreference = ThemePreference.DARK.name,
                    purchaseRemindersEnabled = true,
                    medicineRemindersEnabled = true,
                    returnReminderDaysBefore = 3L,
                    warrantyReminderDaysBefore = 14L,
                    medicineReminderDaysBefore = 14L,
                    appLockEnabled = false,
                    seededSampleData = true
                )
                awaitWriteOrQueueOffline(
                    task = userDoc.set(profile.toCreateMap()),
                    operation = OperationType.CREATE,
                    path = path
                )
                seedSampleVaultInternal(uid)
            } else {
                val existingProfile = snap.toObject(UserProfile::class.java)
                if (!preferredName.isNullOrBlank() && existingProfile?.fullName != preferredName) {
                    awaitWriteOrQueueOffline(
                        task = userDoc.update(
                            mapOf(
                                "fullName" to preferredName.trim().take(100),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ),
                        operation = OperationType.UPDATE,
                        path = path
                    )
                }
                if (existingProfile != null && !existingProfile.seededSampleData && isOnlineNow()) {
                    val existingPurchases = db.collection("users").document(uid).collection("purchases").limit(1).get().await()
                    val existingMeds = db.collection("users").document(uid).collection("medicines").limit(1).get().await()
                    if (existingPurchases.isEmpty && existingMeds.isEmpty) {
                        seedSampleVaultInternal(uid)
                    }
                    awaitWriteOrQueueOffline(
                        task = userDoc.update(
                            mapOf(
                                "seededSampleData" to true,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ),
                        operation = OperationType.UPDATE,
                        path = path
                    )
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun loadSampleVaultData(): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            seedSampleVaultInternal(uid)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid")
            Result.failure(e)
        }
    }

    private suspend fun seedSampleVaultInternal(uid: String) {
        val batch = db.batch()
        val purchasesRef = db.collection("users").document(uid).collection("purchases")
        SampleVaultData.samplePurchases(uid).forEach { purchase ->
            batch.set(purchasesRef.document(), purchase.toCreateMap(uid))
        }
        val medicinesRef = db.collection("users").document(uid).collection("medicines")
        SampleVaultData.sampleMedicines(uid).forEach { med ->
            batch.set(medicinesRef.document(), med.toCreateMap(uid))
        }
        awaitWriteOrQueueOffline(
            task = batch.commit(),
            operation = OperationType.WRITE,
            path = "users/$uid"
        )
    }

    suspend fun updateProfileName(fullName: String): Result<Unit> {
        val uid = requireUserId()
        val path = "users/$uid"
        return try {
            val clean = fullName.trim().take(100)
            require(clean.isNotEmpty()) { "Name cannot be empty" }
            awaitWriteOrQueueOffline(
                task = db.document(path).update(
                    mapOf(
                        "fullName" to clean,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ),
                operation = OperationType.UPDATE,
                path = path
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateUserSettings(updates: Map<String, Any>): Result<Unit> {
        val uid = requireUserId()
        val path = "users/$uid"
        return try {
            val payload = updates.toMutableMap().apply {
                put("updatedAt", FieldValue.serverTimestamp())
            }
            awaitWriteOrQueueOffline(
                task = db.document(path).update(payload),
                operation = OperationType.UPDATE,
                path = path
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun savePurchase(record: PurchaseRecord): Result<String> {
        val uid = requireUserId()
        val collection = db.collection("users").document(uid).collection("purchases")
        val isCreate = record.id.isBlank()
        val docRef = if (isCreate) collection.document() else collection.document(record.id)
        return try {
            require(record.productName.isNotBlank()) { "Product name is required" }
            require(record.merchantName.isNotBlank()) { "Merchant name is required" }
            val task = if (isCreate) {
                docRef.set(record.toCreateMap(uid))
            } else {
                docRef.update(record.toUpdateMap())
            }
            awaitWriteOrQueueOffline(
                task = task,
                operation = if (isCreate) OperationType.CREATE else OperationType.UPDATE,
                path = docRef.path
            )
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (isCreate) OperationType.CREATE else OperationType.UPDATE,
                docRef.path
            )
            Result.failure(e)
        }
    }

    suspend fun deletePurchase(purchaseId: String): Result<Unit> {
        val uid = requireUserId()
        val path = "users/$uid/purchases/$purchaseId"
        return try {
            require(purchaseId.isNotBlank()) { "Purchase ID is required" }
            awaitWriteOrQueueOffline(
                task = db.document(path).delete(),
                operation = OperationType.DELETE,
                path = path
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun saveMedicine(record: MedicineRecord): Result<String> {
        val uid = requireUserId()
        val collection = db.collection("users").document(uid).collection("medicines")
        val isCreate = record.id.isBlank()
        val docRef = if (isCreate) collection.document() else collection.document(record.id)
        return try {
            require(record.medicineName.isNotBlank()) { "Medicine name is required" }
            require(record.expiryDateMillis > 0L) { "Expiry date is required" }
            val task = if (isCreate) {
                docRef.set(record.toCreateMap(uid))
            } else {
                docRef.update(record.toUpdateMap())
            }
            awaitWriteOrQueueOffline(
                task = task,
                operation = if (isCreate) OperationType.CREATE else OperationType.UPDATE,
                path = docRef.path
            )
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (isCreate) OperationType.CREATE else OperationType.UPDATE,
                docRef.path
            )
            Result.failure(e)
        }
    }

    suspend fun deleteMedicine(medicineId: String): Result<Unit> {
        val uid = requireUserId()
        val path = "users/$uid/medicines/$medicineId"
        return try {
            require(medicineId.isNotBlank()) { "Medicine ID is required" }
            awaitWriteOrQueueOffline(
                task = db.document(path).delete(),
                operation = OperationType.DELETE,
                path = path
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePurchasePriority(
        purchaseId: String,
        importanceLevel: String? = null,
        completed: Boolean? = null,
        warrantyClaimActive: Boolean? = null,
        warrantyClaimFollowUpMillis: Long? = null,
        clearFollowUp: Boolean = false
    ): Result<Unit> {
        val uid = requireUserId()
        val path = "users/$uid/purchases/$purchaseId"
        return try {
            require(purchaseId.isNotBlank()) { "Purchase ID is required" }
            val payload = mutableMapOf<String, Any>(
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (importanceLevel != null) {
                payload["importanceLevel"] = ImportanceLevel.normalizeRaw(importanceLevel)
            }
            if (completed != null) {
                payload["completed"] = completed
                payload["completedAtMillis"] = if (completed) {
                    System.currentTimeMillis()
                } else {
                    FieldValue.delete()
                }
            }
            if (warrantyClaimActive != null) {
                payload["warrantyClaimActive"] = warrantyClaimActive
            }
            if (clearFollowUp) {
                payload["warrantyClaimFollowUpMillis"] = FieldValue.delete()
            } else if (warrantyClaimFollowUpMillis != null && warrantyClaimFollowUpMillis > 0L) {
                payload["warrantyClaimFollowUpMillis"] = warrantyClaimFollowUpMillis
            }
            awaitWriteOrQueueOffline(
                task = db.document(path).update(payload),
                operation = OperationType.UPDATE,
                path = path
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePurchaseClaimDetails(
        purchaseId: String,
        claimStatus: String,
        claimNotes: String,
        claimIssueSummary: String
    ): Result<Unit> {
        val uid = requireUserId()
        val path = "users/$uid/purchases/$purchaseId"
        return try {
            require(purchaseId.isNotBlank()) { "Purchase ID is required" }
            val normalizedStatus = com.example.model.WarrantyClaimStatus.fromRaw(claimStatus).name
            val payload = mapOf(
                "claimStatus" to normalizedStatus,
                "claimNotes" to claimNotes.trim().take(1000),
                "claimIssueSummary" to claimIssueSummary.trim().take(600),
                "claimUpdatedAtMillis" to System.currentTimeMillis(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            awaitWriteOrQueueOffline(
                task = db.document(path).update(payload),
                operation = OperationType.UPDATE,
                path = path
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateMedicinePriority(
        medicineId: String,
        importanceLevel: String? = null,
        completed: Boolean? = null
    ): Result<Unit> {
        val uid = requireUserId()
        val path = "users/$uid/medicines/$medicineId"
        return try {
            require(medicineId.isNotBlank()) { "Medicine ID is required" }
            val payload = mutableMapOf<String, Any>(
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (importanceLevel != null) {
                payload["importanceLevel"] = ImportanceLevel.normalizeRaw(importanceLevel)
            }
            if (completed != null) {
                payload["completed"] = completed
                payload["completedAtMillis"] = if (completed) {
                    System.currentTimeMillis()
                } else {
                    FieldValue.delete()
                }
            }
            awaitWriteOrQueueOffline(
                task = db.document(path).update(payload),
                operation = OperationType.UPDATE,
                path = path
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun markNotificationRead(alertKey: String, read: Boolean = true): Result<Unit> {
        val uid = requireUserId()
        val safeKey = alertKey.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128)
        val path = "users/$uid/notificationStates/$safeKey"
        return try {
            val docRef = db.document(path)
            val existing = getDocumentCacheAware(docRef)
            val task = if (existing?.exists() == true) {
                docRef.update(
                    mapOf(
                        "readFlag" to read,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "alertKey" to safeKey,
                        "readFlag" to read,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            awaitWriteOrQueueOffline(task = task, operation = OperationType.WRITE, path = path)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun markAllNotificationsRead(alertKeys: List<String>): Result<Unit> {
        val uid = requireUserId()
        val basePath = "users/$uid/notificationStates"
        return try {
            for (rawKey in alertKeys) {
                val safeKey = rawKey.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128)
                val docRef = db.collection("users").document(uid)
                    .collection("notificationStates").document(safeKey)
                val existing = getDocumentCacheAware(docRef)
                val task = if (existing?.exists() == true) {
                    docRef.update(
                        mapOf(
                            "readFlag" to true,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    docRef.set(
                        mapOf(
                            "userId" to uid,
                            "alertKey" to safeKey,
                            "readFlag" to true,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                awaitWriteOrQueueOffline(task = task, operation = OperationType.WRITE, path = docRef.path)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, basePath)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "VaultRepository"
        private const val WRITE_ACK_TIMEOUT_MS = 3_500L
        private const val CACHE_SIZE_BYTES = 100L * 1024L * 1024L

        @Volatile
        private var firestoreConfigured = false

        fun buildOfflinePersistentSettings(): FirebaseFirestoreSettings {
            return FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        .setSizeBytes(CACHE_SIZE_BYTES)
                        .build()
                )
                .build()
        }

        fun createConfiguredFirestore(context: Context): FirebaseFirestore {
            val db = FirebaseFirestore.getInstance(
                context.applicationContext.getString(R.string.firestore_database_id)
            )
            if (!firestoreConfigured) {
                synchronized(this) {
                    if (!firestoreConfigured) {
                        try {
                            db.firestoreSettings = buildOfflinePersistentSettings()
                        } catch (e: IllegalStateException) {
                            Log.d(TAG, "Firestore settings already active: ${e.message}")
                        } finally {
                            firestoreConfigured = true
                        }
                    }
                }
            }
            return db
        }
    }
}
