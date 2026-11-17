package com.example.ui.screens.family

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.FamilyGroup
import com.example.model.FamilyMember
import com.example.model.FamilyRole
import com.example.model.MedicineRecord
import com.example.model.PurchaseRecord
import com.example.model.SharedRecordType
import com.example.model.SharedVaultRecord
import com.example.model.UiState
import com.example.ui.components.GlassVaultCard
import com.example.ui.components.StatusPill
import com.example.ui.components.VaultAtmosphereBackground
import com.example.ui.components.VaultEmptyState
import com.example.ui.components.VaultGlassOutlinedButton
import com.example.ui.components.VaultGradientButton
import com.example.ui.components.VaultTopBar
import com.example.ui.theme.LocalVaultColors
import com.example.util.DateAndCurrencyUtils
import com.example.viewmodel.FamilyVaultViewModel
import com.example.viewmodel.VaultViewModel
import kotlinx.coroutines.launch

private enum class FamilyShareFilter(val label: String) {
    ALL("All Shared"),
    PURCHASE("Receipts & Warranties"),
    MEDICINE("Medicines")
}

@Composable
fun FamilyVaultScreen(
    vaultViewModel: VaultViewModel,
    familyViewModel: FamilyVaultViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val vaultColors = LocalVaultColors.current

    val profileState by vaultViewModel.userProfileState.collectAsStateWithLifecycle()
    val purchasesState by vaultViewModel.purchasesState.collectAsStateWithLifecycle()
    val medicinesState by vaultViewModel.medicinesState.collectAsStateWithLifecycle()
    val isOnline by vaultViewModel.isOnline.collectAsStateWithLifecycle()

    val familyState by familyViewModel.familyGroupState.collectAsStateWithLifecycle()
    val members by familyViewModel.membersState.collectAsStateWithLifecycle()
    val sharedRecords by familyViewModel.sharedRecordsState.collectAsStateWithLifecycle()
    val sharedRecordIds by familyViewModel.sharedRecordIds.collectAsStateWithLifecycle()
    val isBusy by familyViewModel.isBusy.collectAsStateWithLifecycle()
    val statusMessage by familyViewModel.statusMessage.collectAsStateWithLifecycle()

    val profile = (profileState as? UiState.Success)?.data
    val purchases = (purchasesState as? UiState.Success)?.data.orEmpty()
    val medicines = (medicinesState as? UiState.Success)?.data.orEmpty()
    val userName = profile?.fullName?.takeIf { it.isNotBlank() } ?: "Vault Owner"
    val userEmail = profile?.email?.takeIf { it.isNotBlank() } ?: "user@vaultora.app"
    val userPhoto = profile?.photoUrl

    var familyNameInput by rememberSaveable { mutableStateOf("") }
    var inviteCodeInput by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(FamilyShareFilter.ALL) }
    var showSharePickerDialog by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }

    LaunchedEffect(statusMessage) {
        val msg = statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            familyViewModel.clearStatusMessage()
        }
    }

    VaultAtmosphereBackground(
        accentColor = vaultColors.purchaseAccent,
        secondaryAccent = vaultColors.medicineAccent
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                VaultTopBar(
                    title = "Family Vault",
                    subtitle = "Opt-In Shared Records & Roles",
                    onBack = onBack
                )
            }
        ) { innerPadding ->
            when (val state = familyState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = vaultColors.goldAccent)
                    }
                }
                is UiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        VaultEmptyState(
                            icon = Icons.Outlined.Group,
                            title = "Could not load Family Vault",
                            subtitle = state.message,
                            accent = vaultColors.statusExpired,
                            actionLabel = "Go Back",
                            onAction = onBack
                        )
                    }
                }
                is UiState.Success -> {
                    val group = state.data
                    if (group == null) {
                        NoFamilyContent(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            familyNameInput = familyNameInput,
                            onFamilyNameChange = { familyNameInput = it },
                            inviteCodeInput = inviteCodeInput,
                            onInviteCodeChange = { inviteCodeInput = it.uppercase() },
                            isBusy = isBusy,
                            isOnline = isOnline,
                            onCreateFamily = {
                                familyViewModel.createFamily(
                                    familyName = familyNameInput,
                                    userName = userName,
                                    userEmail = userEmail,
                                    userPhotoUrl = userPhoto
                                )
                            },
                            onJoinFamily = {
                                familyViewModel.joinFamily(
                                    inviteCode = inviteCodeInput,
                                    userName = userName,
                                    userEmail = userEmail,
                                    userPhotoUrl = userPhoto
                                )
                            }
                        )
                    } else {
                        val isAdmin = familyViewModel.currentRole(group) == FamilyRole.ADMIN
                        val filteredShared = remember(sharedRecords, selectedFilter) {
                            when (selectedFilter) {
                                FamilyShareFilter.ALL -> sharedRecords
                                FamilyShareFilter.PURCHASE -> sharedRecords.filter {
                                    it.resolvedType() == SharedRecordType.PURCHASE
                                }
                                FamilyShareFilter.MEDICINE -> sharedRecords.filter {
                                    it.resolvedType() == SharedRecordType.MEDICINE
                                }
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .testTag("family_vault_dashboard"),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Family Overview & Invite Code Card
                            item {
                                FamilyOverviewCard(
                                    group = group,
                                    isAdmin = isAdmin,
                                    memberCount = members.size.coerceAtLeast(group.memberUids.size),
                                    sharedCount = sharedRecords.size,
                                    isBusy = isBusy,
                                    onCopyInviteCode = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Family Invite Code", group.inviteCode))
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Invite code ${group.inviteCode} copied")
                                        }
                                    },
                                    onRotateInviteCode = { familyViewModel.regenerateInviteCode(group) },
                                    onLeaveOrDisband = { showLeaveDialog = true }
                                )
                            }

                            // Family Members & Roles
                            item {
                                FamilyMembersCard(
                                    members = members,
                                    currentUserId = familyViewModel.currentUserId,
                                    isAdmin = isAdmin,
                                    onRemoveMember = { member ->
                                        familyViewModel.removeMember(group, member.userId, member.displayName)
                                    }
                                )
                            }

                            // Shared Records Header & Share Button
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Shared Family Records", style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                text = "Only records you explicitly share appear here",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        VaultGradientButton(
                                            onClick = { showSharePickerDialog = true },
                                            colors = vaultColors.purchaseButtonGradient,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .height(44.dp)
                                                .testTag("family_share_from_vault_button")
                                        ) {
                                            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Share Items")
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FamilyShareFilter.entries.forEach { filter ->
                                            val accent = when (filter) {
                                                FamilyShareFilter.ALL -> vaultColors.goldAccent
                                                FamilyShareFilter.PURCHASE -> vaultColors.purchaseAccent
                                                FamilyShareFilter.MEDICINE -> vaultColors.medicineAccent
                                            }
                                            FilterChip(
                                                selected = selectedFilter == filter,
                                                onClick = { selectedFilter = filter },
                                                label = { Text(filter.label) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = accent.copy(alpha = 0.22f),
                                                    selectedLabelColor = accent
                                                ),
                                                modifier = Modifier.testTag("family_filter_${filter.name.lowercase()}")
                                            )
                                        }
                                    }
                                }
                            }

                            if (filteredShared.isEmpty()) {
                                item {
                                    VaultEmptyState(
                                        icon = Icons.Outlined.Share,
                                        title = "No shared records yet",
                                        subtitle = "Tap \"Share Items\" above or use \"Share to Family Vault\" inside any receipt or medicine detail page.",
                                        accent = vaultColors.purchaseAccent,
                                        actionLabel = "Select Records to Share",
                                        onAction = { showSharePickerDialog = true }
                                    )
                                }
                            } else {
                                items(filteredShared, key = { it.id }) { record ->
                                    SharedVaultRecordCard(
                                        record = record,
                                        canRemove = isAdmin || record.ownerUid == familyViewModel.currentUserId,
                                        onUnshare = {
                                            familyViewModel.unshareRecord(record.id, record.title)
                                        }
                                    )
                                }
                            }

                            item { Spacer(Modifier.height(16.dp)) }
                        }

                        if (showLeaveDialog) {
                            AlertDialog(
                                onDismissRequest = { showLeaveDialog = false },
                                title = {
                                    Text(if (isAdmin) "Disband Family Vault?" else "Leave Family Vault?")
                                },
                                text = {
                                    Text(
                                        if (isAdmin) {
                                            "Disbanding \"${group.name}\" removes the family group and invite code. Your private purchases and medicines remain intact."
                                        } else {
                                            "You will leave \"${group.name}\" and remove items you shared. Your private purchases and medicines remain completely intact."
                                        }
                                    )
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            showLeaveDialog = false
                                            familyViewModel.leaveOrDisbandFamily(group)
                                        }
                                    ) {
                                        Text(
                                            text = if (isAdmin) "Disband" else "Leave",
                                            color = vaultColors.statusExpired
                                        )
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showLeaveDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        if (showSharePickerDialog) {
                            ShareRecordsPickerDialog(
                                purchases = purchases,
                                medicines = medicines,
                                currentUserId = familyViewModel.currentUserId,
                                sharedRecordIds = sharedRecordIds,
                                onTogglePurchase = { purchase ->
                                    familyViewModel.toggleSharePurchase(userName, purchase)
                                },
                                onToggleMedicine = { medicine ->
                                    familyViewModel.toggleShareMedicine(userName, medicine)
                                },
                                onDismiss = { showSharePickerDialog = false }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoFamilyContent(
    modifier: Modifier = Modifier,
    familyNameInput: String,
    onFamilyNameChange: (String) -> Unit,
    inviteCodeInput: String,
    onInviteCodeChange: (String) -> Unit,
    isBusy: Boolean,
    isOnline: Boolean,
    onCreateFamily: () -> Unit,
    onJoinFamily: () -> Unit
) {
    val vaultColors = LocalVaultColors.current
    LazyColumn(
        modifier = modifier.testTag("family_vault_onboarding"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            GlassVaultCard(
                modifier = Modifier.fillMaxWidth(),
                accentBorder = vaultColors.goldAccent.copy(alpha = 0.4f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = vaultColors.goldAccent)
                    Spacer(Modifier.width(8.dp))
                    Text("Privacy-First Family Sharing", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Your Purchase Vault and Medicine Vault remain strictly private by default. " +
                        "Create or join an optional Family Vault to share only the receipts, warranties, or medicines you choose.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            GlassVaultCard(
                modifier = Modifier.fillMaxWidth(),
                accentBorder = vaultColors.purchaseAccent.copy(alpha = 0.45f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.GroupAdd, contentDescription = null, tint = vaultColors.purchaseAccent)
                    Spacer(Modifier.width(8.dp))
                    Text("Create a New Family (Admin)", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = familyNameInput,
                    onValueChange = onFamilyNameChange,
                    label = { Text("Family Name (e.g., Sharma Household)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("family_name_input")
                )
                Spacer(Modifier.height(12.dp))
                VaultGradientButton(
                    onClick = onCreateFamily,
                    enabled = !isBusy && familyNameInput.isNotBlank(),
                    colors = vaultColors.purchaseButtonGradient,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("create_family_button")
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Create Family Vault", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        item {
            GlassVaultCard(
                modifier = Modifier.fillMaxWidth(),
                accentBorder = vaultColors.medicineAccent.copy(alpha = 0.45f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Group, contentDescription = null, tint = vaultColors.medicineAccent)
                    Spacer(Modifier.width(8.dp))
                    Text("Join an Existing Family (Member)", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (isOnline) {
                        "Enter the secure invitation code shared by your Family Admin."
                    } else {
                        "Connect to the internet to verify and join a family invitation code."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = inviteCodeInput,
                    onValueChange = onInviteCodeChange,
                    label = { Text("Invitation Code (e.g., VLT-ABCD-2345)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("family_invite_code_input")
                )
                Spacer(Modifier.height(12.dp))
                VaultGlassOutlinedButton(
                    onClick = onJoinFamily,
                    enabled = !isBusy && inviteCodeInput.trim().length >= 4 && isOnline,
                    colors = vaultColors.medicineButtonGradient,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("join_family_button")
                ) {
                    Text("Join with Invite Code", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun FamilyOverviewCard(
    group: FamilyGroup,
    isAdmin: Boolean,
    memberCount: Int,
    sharedCount: Int,
    isBusy: Boolean,
    onCopyInviteCode: () -> Unit,
    onRotateInviteCode: () -> Unit,
    onLeaveOrDisband: () -> Unit
) {
    val vaultColors = LocalVaultColors.current
    GlassVaultCard(
        modifier = Modifier.fillMaxWidth(),
        accentBorder = vaultColors.goldAccent.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                StatusPill(
                    label = if (isAdmin) "ROLE: ADMIN" else "ROLE: MEMBER",
                    color = if (isAdmin) vaultColors.goldAccent else vaultColors.purchaseAccent
                )
                Spacer(Modifier.height(8.dp))
                Text(group.name, style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "$memberCount member(s) • $sharedCount shared record(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onLeaveOrDisband,
                modifier = Modifier.testTag("leave_family_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Logout,
                    contentDescription = if (isAdmin) "Disband Family" else "Leave Family",
                    tint = vaultColors.statusExpired
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = vaultColors.glassBorder)
        Spacer(Modifier.height(12.dp))

        Text("Family Invitation Code", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = vaultColors.goldContainer,
            border = BorderStroke(1.dp, vaultColors.goldAccent.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = group.inviteCode.ifBlank { "Generating..." },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = vaultColors.goldAccent,
                    modifier = Modifier.testTag("family_invite_code_display")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onCopyInviteCode,
                        modifier = Modifier.testTag("copy_family_invite_button")
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy invite code", tint = vaultColors.goldAccent)
                    }
                    if (isAdmin) {
                        IconButton(
                            onClick = onRotateInviteCode,
                            enabled = !isBusy,
                            modifier = Modifier.testTag("regenerate_family_invite_button")
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Generate new invite code", tint = vaultColors.purchaseAccent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FamilyMembersCard(
    members: List<FamilyMember>,
    currentUserId: String,
    isAdmin: Boolean,
    onRemoveMember: (FamilyMember) -> Unit
) {
    val vaultColors = LocalVaultColors.current
    GlassVaultCard(modifier = Modifier.fillMaxWidth()) {
        Text("Family Members (${members.size})", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        if (members.isEmpty()) {
            Text(
                text = "Loading member directory...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                members.forEach { member ->
                    val role = member.resolvedRole()
                    val isSelf = member.userId == currentUserId
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (role == FamilyRole.ADMIN) vaultColors.goldContainer else vaultColors.purchaseContainer
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = member.displayName.take(1).uppercase().ifBlank { "F" },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (role == FamilyRole.ADMIN) vaultColors.goldAccent else vaultColors.purchaseAccent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isSelf) "${member.displayName} (You)" else member.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = member.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            StatusPill(
                                label = role.label.uppercase(),
                                color = if (role == FamilyRole.ADMIN) vaultColors.goldAccent else vaultColors.purchaseAccent
                            )
                            if (isAdmin && !isSelf && role != FamilyRole.ADMIN) {
                                IconButton(
                                    onClick = { onRemoveMember(member) },
                                    modifier = Modifier.testTag("remove_member_${member.userId}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.PersonRemove,
                                        contentDescription = "Remove ${member.displayName}",
                                        tint = vaultColors.statusExpired
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedVaultRecordCard(
    record: SharedVaultRecord,
    canRemove: Boolean,
    onUnshare: () -> Unit
) {
    val vaultColors = LocalVaultColors.current
    val isMedicine = record.resolvedType() == SharedRecordType.MEDICINE
    val accent = if (isMedicine) vaultColors.medicineAccent else vaultColors.purchaseAccent

    GlassVaultCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("shared_record_card_${record.id}"),
        accentBorder = accent.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isMedicine) Icons.Outlined.Medication else Icons.Outlined.ReceiptLong,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    StatusPill(
                        label = if (isMedicine) "SHARED MEDICINE" else "SHARED RECEIPT / WARRANTY",
                        color = accent
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(record.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = buildString {
                            if (isMedicine) {
                                append("Batch ${record.merchantOrBatch.ifBlank { "N/A" }}")
                                record.medicineExpiryMillis?.let {
                                    append(" • Exp ${DateAndCurrencyUtils.formatDate(it)}")
                                }
                            } else {
                                if (record.brand.isNotBlank()) append("${record.brand} • ")
                                append(record.merchantOrBatch)
                                record.priceInr?.let { append(" • ${DateAndCurrencyUtils.formatInr(it)}") }
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!isMedicine && record.warrantyExpiryMillis != null) {
                        Text(
                            text = "Warranty until ${DateAndCurrencyUtils.formatDate(record.warrantyExpiryMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = vaultColors.statusValid
                        )
                    }
                    if (isMedicine && !record.storageNote.isNullOrBlank()) {
                        Text(
                            text = "Storage: ${record.storageNote}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Shared by ${record.ownerName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = vaultColors.goldAccent
                    )
                }
            }
            if (canRemove) {
                TextButton(
                    onClick = onUnshare,
                    modifier = Modifier.testTag("unshare_record_${record.id}")
                ) {
                    Text("Unshare", color = vaultColors.statusExpired)
                }
            }
        }
    }
}

@Composable
private fun ShareRecordsPickerDialog(
    purchases: List<PurchaseRecord>,
    medicines: List<MedicineRecord>,
    currentUserId: String,
    sharedRecordIds: Set<String>,
    onTogglePurchase: (PurchaseRecord) -> Unit,
    onToggleMedicine: (MedicineRecord) -> Unit,
    onDismiss: () -> Unit
) {
    val vaultColors = LocalVaultColors.current
    Dialog(onDismissRequest = onDismiss) {
        GlassVaultCard(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 540.dp),
            accentBorder = vaultColors.purchaseAccent.copy(alpha = 0.6f)
        ) {
            Text("Share Selected Records", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Toggle individual items to share with your family. Unchecked records stay strictly private.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (purchases.isNotEmpty()) {
                    item {
                        Text(
                            text = "Purchases & Warranties (${purchases.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = vaultColors.purchaseAccent
                        )
                    }
                    items(purchases, key = { "pick_p_${it.id}" }) { p ->
                        val shareId = SharedVaultRecord.deterministicShareId(
                            currentUserId,
                            SharedRecordType.PURCHASE,
                            p.id
                        )
                        val isShared = shareId in sharedRecordIds
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(p.productName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    text = "${p.merchantName} • ${DateAndCurrencyUtils.formatInr(p.purchasePriceInr)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isShared,
                                onCheckedChange = { onTogglePurchase(p) },
                                modifier = Modifier.testTag("pick_share_purchase_${p.id}")
                            )
                        }
                    }
                }

                if (medicines.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Medicines (${medicines.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = vaultColors.medicineAccent
                        )
                    }
                    items(medicines, key = { "pick_m_${it.id}" }) { m ->
                        val shareId = SharedVaultRecord.deterministicShareId(
                            currentUserId,
                            SharedRecordType.MEDICINE,
                            m.id
                        )
                        val isShared = shareId in sharedRecordIds
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(m.medicineName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    text = "Exp ${DateAndCurrencyUtils.formatDate(m.expiryDateMillis)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isShared,
                                onCheckedChange = { onToggleMedicine(m) },
                                modifier = Modifier.testTag("pick_share_medicine_${m.id}")
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            VaultGradientButton(
                onClick = onDismiss,
                colors = vaultColors.purchaseButtonGradient,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Done")
            }
        }
    }
}
