package com.example.data

import android.content.Context
import android.util.Log
import com.example.model.FamilyGroup
import com.example.model.FamilyInvite
import com.example.model.FamilyInviteCodeGenerator
import com.example.model.FamilyMember
import com.example.model.FamilyRole
import com.example.model.MedicineRecord
import com.example.model.PurchaseRecord
import com.example.model.SharedRecordType
import com.example.model.SharedVaultRecord
import com.example.util.NetworkMonitor
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
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

class FamilyVaultRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val networkMonitor: NetworkMonitor? = null
) {
    constructor(
        context: Context,
        networkMonitor: NetworkMonitor? = NetworkMonitor(context.applicationContext)
    ) : this(
        db = VaultRepository.createConfiguredFirestore(context),
        auth = Firebase.auth,
        networkMonitor = networkMonitor
    )

    private fun isOnlineNow(): Boolean = networkMonitor?.refresh() ?: true

    private fun requireUid(): String = auth.currentUser?.uid
        ?: throw IllegalStateException("Please sign in with Google to use Family Vault.")

    fun observeUserFamilyGroup(userId: String): Flow<FamilyGroup?> = flow {
        emitAll(
            db.collection("families")
                .whereArrayContains("memberUids", userId)
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    snapshot.toObjects(
                        FamilyGroup::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).firstOrNull()
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    Log.w(TAG, "Error observing user family group", error)
                    throw error
                }
        )
    }

    fun observeFamilyMembers(familyId: String): Flow<List<FamilyMember>> = flow {
        emitAll(
            db.collection("families").document(familyId).collection("members")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    snapshot.toObjects(
                        FamilyMember::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(compareBy<FamilyMember> { if (it.resolvedRole() == FamilyRole.ADMIN) 0 else 1 }.thenBy { it.joinedAtMillis })
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    Log.w(TAG, "Error observing family members", error)
                    throw error
                }
        )
    }

    fun observeSharedRecords(familyId: String): Flow<SyncedList<SharedVaultRecord>> = flow {
        emitAll(
            db.collection("families").document(familyId).collection("sharedRecords")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    val items = snapshot.toObjects(
                        SharedVaultRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.sharedAtMillis }
                    val pending = snapshot.documents
                        .filter { it.metadata.hasPendingWrites() }
                        .map { it.id }
                        .toSet()
                    SyncedList(
                        items = items,
                        pendingIds = pending,
                        isFromCache = snapshot.metadata.isFromCache
                    )
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    Log.w(TAG, "Error observing shared records", error)
                    throw error
                }
        )
    }

    suspend fun createFamily(
        familyName: String,
        creatorName: String,
        creatorEmail: String,
        creatorPhotoUrl: String?
    ): Result<FamilyGroup> {
        val uid = requireUid()
        val cleanName = familyName.trim().take(80)
        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter a family name."))
        }
        val familyRef = db.collection("families").document()
        val inviteCode = FamilyInviteCodeGenerator.generate()
        val group = FamilyGroup(
            id = familyRef.id,
            name = cleanName,
            adminUid = uid,
            inviteCode = inviteCode,
            memberUids = listOf(uid)
        )
        return try {
            // 1. Create family document
            awaitWriteOrQueueOffline(
                task = familyRef.set(group.toCreateMap(uid, cleanName, inviteCode))
            )
            // 2. Create admin member document
            val member = FamilyMember(
                id = uid,
                familyId = familyRef.id,
                userId = uid,
                displayName = creatorName.ifBlank { "Family Admin" },
                email = creatorEmail.ifBlank { "admin@vaultora.app" },
                photoUrl = creatorPhotoUrl,
                role = FamilyRole.ADMIN.name,
                inviteCodeUsed = inviteCode,
                joinedAtMillis = System.currentTimeMillis()
            )
            awaitWriteOrQueueOffline(
                task = familyRef.collection("members").document(uid).set(member.toCreateMap())
            )
            // 3. Create active invitation lookup document
            val invite = FamilyInvite(
                id = inviteCode,
                inviteCode = inviteCode,
                familyId = familyRef.id,
                familyName = cleanName,
                createdByUid = uid,
                active = true
            )
            awaitWriteOrQueueOffline(
                task = db.collection("familyInvites").document(inviteCode).set(invite.toCreateMap())
            )
            // 4. Record family metadata on user profile
            awaitWriteOrQueueOffline(
                task = db.collection("users").document(uid).update(
                    mapOf(
                        "familyId" to familyRef.id,
                        "familyRole" to FamilyRole.ADMIN.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            )
            Result.success(group)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "createFamily failed", e)
            Result.failure(e)
        }
    }

    suspend fun joinFamilyByCode(
        rawCode: String,
        memberName: String,
        memberEmail: String,
        memberPhotoUrl: String?
    ): Result<String> {
        val uid = requireUid()
        val normalizedCode = FamilyInviteCodeGenerator.normalize(rawCode)
        if (normalizedCode.length < 4) {
            return Result.failure(IllegalArgumentException("Please enter a valid invitation code."))
        }
        if (!isOnlineNow()) {
            return Result.failure(
                IllegalStateException("Internet connection is required to verify and join a family invitation code.")
            )
        }
        return try {
            val inviteSnap = db.collection("familyInvites").document(normalizedCode).get(Source.SERVER).await()
            if (!inviteSnap.exists()) {
                return Result.failure(IllegalArgumentException("Invitation code not found or expired."))
            }
            val invite = inviteSnap.toObject(FamilyInvite::class.java)
                ?: return Result.failure(IllegalStateException("Invalid invitation data."))
            if (!invite.active || invite.familyId.isBlank()) {
                return Result.failure(IllegalStateException("This invitation code is no longer active."))
            }

            // 1. Add self to family memberUids
            val familyRef = db.collection("families").document(invite.familyId)
            familyRef.update(
                mapOf(
                    "memberUids" to FieldValue.arrayUnion(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // 2. Register member record with inviteCodeUsed proof
            val member = FamilyMember(
                id = uid,
                familyId = invite.familyId,
                userId = uid,
                displayName = memberName.ifBlank { "Family Member" },
                email = memberEmail.ifBlank { "member@vaultora.app" },
                photoUrl = memberPhotoUrl,
                role = FamilyRole.MEMBER.name,
                inviteCodeUsed = normalizedCode,
                joinedAtMillis = System.currentTimeMillis()
            )
            familyRef.collection("members").document(uid).set(member.toCreateMap()).await()

            // 3. Persist membership on own profile
            db.collection("users").document(uid).update(
                mapOf(
                    "familyId" to invite.familyId,
                    "familyRole" to FamilyRole.MEMBER.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(invite.familyName.ifBlank { "Family Vault" })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "joinFamilyByCode failed", e)
            Result.failure(e)
        }
    }

    suspend fun regenerateInviteCode(family: FamilyGroup): Result<String> {
        val uid = requireUid()
        if (family.adminUid != uid) {
            return Result.failure(IllegalStateException("Only the Family Admin can regenerate the invitation code."))
        }
        val oldCode = FamilyInviteCodeGenerator.normalize(family.inviteCode)
        val newCode = FamilyInviteCodeGenerator.generate()
        return try {
            val newInvite = FamilyInvite(
                id = newCode,
                inviteCode = newCode,
                familyId = family.id,
                familyName = family.name,
                createdByUid = uid,
                active = true
            )
            awaitWriteOrQueueOffline(
                task = db.collection("familyInvites").document(newCode).set(newInvite.toCreateMap())
            )
            awaitWriteOrQueueOffline(
                task = db.collection("families").document(family.id).update(
                    mapOf(
                        "inviteCode" to newCode,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            )
            if (oldCode.isNotBlank() && oldCode != newCode) {
                awaitWriteOrQueueOffline(
                    task = db.collection("familyInvites").document(oldCode).delete()
                )
            }
            Result.success(newCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "regenerateInviteCode failed", e)
            Result.failure(e)
        }
    }

    suspend fun removeMember(family: FamilyGroup, targetUid: String): Result<Unit> {
        val uid = requireUid()
        if (family.adminUid != uid) {
            return Result.failure(IllegalStateException("Only the Family Admin can remove members."))
        }
        if (targetUid == family.adminUid) {
            return Result.failure(IllegalArgumentException("Admin cannot be removed directly. Disband the family instead."))
        }
        return try {
            val familyRef = db.collection("families").document(family.id)
            awaitWriteOrQueueOffline(
                task = familyRef.collection("members").document(targetUid).delete()
            )
            awaitWriteOrQueueOffline(
                task = familyRef.update(
                    mapOf(
                        "memberUids" to FieldValue.arrayRemove(targetUid),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "removeMember failed", e)
            Result.failure(e)
        }
    }

    suspend fun leaveOrDisbandFamily(family: FamilyGroup, ownSharedIds: List<String>): Result<Unit> {
        val uid = requireUid()
        val familyRef = db.collection("families").document(family.id)
        return try {
            if (family.adminUid == uid) {
                // Admin disbands the family: clean up own invites & family group
                val code = FamilyInviteCodeGenerator.normalize(family.inviteCode)
                if (code.isNotBlank()) {
                    awaitWriteOrQueueOffline(task = db.collection("familyInvites").document(code).delete())
                }
                ownSharedIds.forEach { shareId ->
                    awaitWriteOrQueueOffline(
                        task = familyRef.collection("sharedRecords").document(shareId).delete()
                    )
                }
                awaitWriteOrQueueOffline(task = familyRef.collection("members").document(uid).delete())
                awaitWriteOrQueueOffline(task = familyRef.delete())
            } else {
                // Member removes own shared items & membership
                ownSharedIds.forEach { shareId ->
                    awaitWriteOrQueueOffline(
                        task = familyRef.collection("sharedRecords").document(shareId).delete()
                    )
                }
                awaitWriteOrQueueOffline(task = familyRef.collection("members").document(uid).delete())
                awaitWriteOrQueueOffline(
                    task = familyRef.update(
                        mapOf(
                            "memberUids" to FieldValue.arrayRemove(uid),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                )
            }
            awaitWriteOrQueueOffline(
                task = db.collection("users").document(uid).update(
                    mapOf(
                        "familyId" to FieldValue.delete(),
                        "familyRole" to FieldValue.delete(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "leaveOrDisbandFamily failed", e)
            Result.failure(e)
        }
    }

    suspend fun sharePurchase(
        familyId: String,
        ownerName: String,
        purchase: PurchaseRecord
    ): Result<String> {
        val uid = requireUid()
        if (familyId.isBlank() || purchase.id.isBlank()) {
            return Result.failure(IllegalArgumentException("Family and purchase ID are required."))
        }
        val shared = SharedVaultRecord.fromPurchase(
            familyId = familyId,
            ownerUid = uid,
            ownerName = ownerName,
            purchase = purchase
        )
        return try {
            awaitWriteOrQueueOffline(
                task = db.collection("families").document(familyId)
                    .collection("sharedRecords").document(shared.id)
                    .set(shared.toCreateMap())
            )
            Result.success(shared.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "sharePurchase failed", e)
            Result.failure(e)
        }
    }

    suspend fun shareMedicine(
        familyId: String,
        ownerName: String,
        medicine: MedicineRecord
    ): Result<String> {
        val uid = requireUid()
        if (familyId.isBlank() || medicine.id.isBlank()) {
            return Result.failure(IllegalArgumentException("Family and medicine ID are required."))
        }
        val shared = SharedVaultRecord.fromMedicine(
            familyId = familyId,
            ownerUid = uid,
            ownerName = ownerName,
            medicine = medicine
        )
        return try {
            awaitWriteOrQueueOffline(
                task = db.collection("families").document(familyId)
                    .collection("sharedRecords").document(shared.id)
                    .set(shared.toCreateMap())
            )
            Result.success(shared.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "shareMedicine failed", e)
            Result.failure(e)
        }
    }

    suspend fun unshareRecord(familyId: String, shareId: String): Result<Unit> {
        requireUid()
        if (familyId.isBlank() || shareId.isBlank()) {
            return Result.failure(IllegalArgumentException("Share ID is required."))
        }
        return try {
            awaitWriteOrQueueOffline(
                task = db.collection("families").document(familyId)
                    .collection("sharedRecords").document(shareId)
                    .delete()
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "unshareRecord failed", e)
            Result.failure(e)
        }
    }

    fun shareIdForPurchase(ownerUid: String, purchaseId: String): String =
        SharedVaultRecord.deterministicShareId(ownerUid, SharedRecordType.PURCHASE, purchaseId)

    fun shareIdForMedicine(ownerUid: String, medicineId: String): String =
        SharedVaultRecord.deterministicShareId(ownerUid, SharedRecordType.MEDICINE, medicineId)

    private suspend fun awaitWriteOrQueueOffline(task: Task<Void>) {
        if (isOnlineNow()) {
            withTimeoutOrNull(WRITE_ACK_TIMEOUT_MS) {
                task.await()
            }
        }
    }

    private companion object {
        const val TAG = "FamilyVaultRepository"
        const val WRITE_ACK_TIMEOUT_MS = 4_000L
    }
}
