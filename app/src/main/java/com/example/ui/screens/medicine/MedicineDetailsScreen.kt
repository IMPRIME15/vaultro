package com.example.ui.screens.medicine

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.model.ExpiryStatus
import com.example.model.UiState
import com.example.notifications.VaultReminderScheduler
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import com.example.ui.components.GlassVaultCard
import com.example.ui.components.ImportanceSelectorRow
import com.example.ui.components.PriorityTierBadge
import com.example.ui.components.StatusPill
import com.example.ui.components.VaultAnimatedEntrance
import com.example.ui.components.VaultAtmosphereBackground
import com.example.ui.components.VaultEmptyState
import com.example.ui.components.VaultGlassOutlinedButton
import com.example.ui.components.VaultTopBar
import com.example.ui.components.tierColor
import com.example.ui.components.vaultSharedBounds
import com.example.ui.theme.LocalVaultColors
import com.example.util.DateAndCurrencyUtils
import com.example.viewmodel.VaultViewModel
import kotlinx.coroutines.launch

@Composable
fun MedicineDetailsScreen(
    medicineId: String,
    viewModel: VaultViewModel,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    isSharedWithFamily: Boolean = false,
    onToggleFamilyShare: ((com.example.model.MedicineRecord) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val vaultColors = LocalVaultColors.current

    val medicinesState by viewModel.medicinesState.collectAsStateWithLifecycle()
    val pendingMedicineIds by viewModel.pendingMedicineIds.collectAsStateWithLifecycle()
    val priorityReport by viewModel.priorityCenterState.collectAsStateWithLifecycle()
    val medicine = (medicinesState as? UiState.Success)?.data?.firstOrNull { it.id == medicineId }
    val priorityItem = medicine?.let { priorityReport.medicinePriorityMap[it.id] }
    val isPendingSync = medicine != null && medicine.id in pendingMedicineIds

    var showDeleteDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && medicine != null) {
            VaultReminderScheduler.scheduleMedicineReminder(context, medicine)
            scope.launch { snackbarHostState.showSnackbar("Expiry reminder scheduled") }
        }
    }

    VaultAtmosphereBackground(
        accentColor = vaultColors.medicineAccent,
        secondaryAccent = vaultColors.goldAccent
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                VaultTopBar(
                    title = "Medicine Details",
                    subtitle = "Medicine Vault",
                    onBack = onBack,
                    actions = {
                        if (medicine != null) {
                            IconButton(
                                onClick = { onEdit(medicine.id) },
                                modifier = Modifier.testTag("edit_medicine_button")
                            ) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Edit medicine")
                            }
                            IconButton(
                                onClick = { showDeleteDialog = true },
                                modifier = Modifier.testTag("delete_medicine_button")
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Delete medicine",
                                    tint = vaultColors.statusExpired
                                )
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            if (medicine == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    VaultEmptyState(
                        icon = Icons.Outlined.Medication,
                        title = "Medicine not found",
                        subtitle = "This stored entry may have been removed.",
                        accent = vaultColors.medicineAccent,
                        actionLabel = "Go Back",
                        onAction = onBack
                    )
                }
            } else {
                val status = DateAndCurrencyUtils.medicineExpiryStatus(medicine)
                val days = DateAndCurrencyUtils.daysFromNow(medicine.expiryDateMillis)
                val statusColor = when {
                    medicine.completed -> vaultColors.statusValid
                    status == ExpiryStatus.VALID -> vaultColors.statusValid
                    status == ExpiryStatus.EXPIRING_SOON -> vaultColors.statusWarning
                    else -> vaultColors.statusExpired
                }
                val statusTitle = when {
                    medicine.completed -> "Resolved / Disposed"
                    status == ExpiryStatus.VALID -> "Valid Medicine"
                    status == ExpiryStatus.EXPIRING_SOON -> "Expiring Soon"
                    else -> "Expired Medicine"
                }
                val statusIcon = when {
                    medicine.completed -> Icons.Outlined.CheckCircle
                    status == ExpiryStatus.VALID -> Icons.Outlined.CheckCircle
                    status == ExpiryStatus.EXPIRING_SOON -> Icons.Outlined.Timer
                    else -> Icons.Outlined.WarningAmber
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .testTag("medicine_details_screen"),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Photograph Banner
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .vaultSharedBounds("medicine_card_${medicine.id}")
                                .height(200.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            vaultColors.medicineContainer,
                                            vaultColors.glassSurface
                                        )
                                    )
                                )
                                .border(
                                    width = 1.dp,
                                    color = vaultColors.medicineAccent.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(22.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!medicine.photoUri.isNullOrBlank()) {
                                AsyncImage(
                                    model = medicine.photoUri,
                                    contentDescription = medicine.medicineName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Outlined.Medication,
                                        contentDescription = null,
                                        tint = vaultColors.medicineAccent,
                                        modifier = Modifier.size(56.dp)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = "No photograph attached",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (isPendingSync) {
                                StatusPill(
                                    label = "PENDING SYNC",
                                    color = vaultColors.statusWarning,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(12.dp)
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                            ) {
                                if (!medicine.completed && priorityItem != null) {
                                    PriorityTierBadge(tier = priorityItem.priorityTier)
                                }
                                StatusPill(
                                    label = statusTitle.uppercase(),
                                    color = statusColor
                                )
                            }
                        }
                    }

                    // Expiry Status Banner
                    item {
                        VaultAnimatedEntrance(delayMillis = 50) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = statusColor.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.48f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(28.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(statusTitle, style = MaterialTheme.typography.titleMedium, color = statusColor)
                                        Text(
                                            text = "Expiry: ${DateAndCurrencyUtils.formatDate(medicine.expiryDateMillis)} (${DateAndCurrencyUtils.formatCountdown(days)})",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Metadata Card
                    item {
                        VaultAnimatedEntrance(delayMillis = 100) {
                        GlassVaultCard(modifier = Modifier.fillMaxWidth()) {
                            Text(medicine.medicineName, style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = vaultColors.glassBorder)
                            Spacer(Modifier.height(12.dp))

                            MedicineInfoRow("Batch Number", medicine.batchNumber.ifBlank { "Not specified" })
                            Spacer(Modifier.height(8.dp))
                            MedicineInfoRow("Expiry Date", DateAndCurrencyUtils.formatDate(medicine.expiryDateMillis))
                            Spacer(Modifier.height(8.dp))
                            MedicineInfoRow("Purchase Date", DateAndCurrencyUtils.formatDate(medicine.purchaseDateMillis))
                            Spacer(Modifier.height(8.dp))
                            MedicineInfoRow("Quantity", medicine.quantity ?: "Not specified")
                            if (!medicine.storageNote.isNullOrBlank()) {
                                Spacer(Modifier.height(8.dp))
                                MedicineInfoRow("Storage Location", medicine.storageNote)
                            }
                        }
                        }
                    }

                    // Smart Priority & Resolution Card
                    item {
                        VaultAnimatedEntrance(delayMillis = 125) {
                            GlassVaultCard(
                                modifier = Modifier.fillMaxWidth(),
                                accentBorder = priorityItem?.let { tierColor(it.priorityTier, vaultColors).copy(alpha = 0.45f) }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Outlined.PriorityHigh,
                                            contentDescription = null,
                                            tint = vaultColors.medicineAccent
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Smart Priority & Status", style = MaterialTheme.typography.titleMedium)
                                    }
                                    if (priorityItem != null) {
                                        StatusPill(
                                            label = priorityItem.priorityStatusLabel,
                                            color = if (medicine.completed) vaultColors.statusValid else tierColor(priorityItem.priorityTier, vaultColors)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                ImportanceSelectorRow(
                                    selected = medicine.resolvedImportanceOrNull(),
                                    onSelect = { level -> viewModel.updateMedicineImportance(medicine.id, level) },
                                    accentColor = vaultColors.medicineAccent,
                                    tagPrefix = "medicine_detail_importance"
                                )
                                Spacer(Modifier.height(12.dp))
                                VaultGlassOutlinedButton(
                                    onClick = { viewModel.setMedicineCompleted(medicine.id, !medicine.completed) },
                                    colors = if (medicine.completed) vaultColors.goldButtonGradient else vaultColors.medicineButtonGradient,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("medicine_toggle_completed_button")
                                ) {
                                    Icon(
                                        imageVector = if (medicine.completed) Icons.Outlined.Replay else Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (medicine.completed) "Reopen to Active Priority" else "Mark Resolved / Disposed")
                                }
                            }
                        }
                    }

                    // Reminder Settings
                    item {
                        VaultAnimatedEntrance(delayMillis = 150) {
                        GlassVaultCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = vaultColors.medicineAccent)
                                Spacer(Modifier.width(8.dp))
                                Text("Reminder Settings", style = MaterialTheme.typography.titleMedium)
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Expiry Alert", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = "Notify ${medicine.reminderDaysBefore} days before expiry",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = medicine.reminderEnabled,
                                    onCheckedChange = { enabled ->
                                        val updated = medicine.copy(reminderEnabled = enabled)
                                        scope.launch {
                                            viewModel.saveMedicine(updated)
                                            if (enabled) {
                                                if (!VaultReminderScheduler.hasNotificationPermission(context) &&
                                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                                ) {
                                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                } else {
                                                    VaultReminderScheduler.scheduleMedicineReminder(context, updated)
                                                    snackbarHostState.showSnackbar("Medicine expiry reminder enabled")
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        }
                    }

                    if (onToggleFamilyShare != null) {
                        item {
                            VaultGlassOutlinedButton(
                                onClick = { onToggleFamilyShare(medicine) },
                                colors = if (isSharedWithFamily) vaultColors.goldButtonGradient else vaultColors.medicineButtonGradient,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("medicine_family_share_button")
                            ) {
                                Icon(Icons.Outlined.Group, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(if (isSharedWithFamily) "Shared in Family Vault (Tap to Manage)" else "Share to Family Vault")
                            }
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    if (showDeleteDialog && medicine != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Medicine?") },
            text = { Text("Remove \"${medicine.medicineName}\" from your Medicine Vault?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        scope.launch {
                            viewModel.deleteMedicine(medicine.id).onSuccess {
                                onBack()
                            }
                        }
                    }
                ) {
                    Text("Delete", color = vaultColors.statusExpired)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MedicineInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.58f)
        )
    }
}
