package com.example.ui.screens.medicine

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.model.ExpiryStatus
import com.example.model.MedicineRecord
import com.example.model.UiState
import com.example.priority.PriorityEngine
import com.example.priority.PriorityItem
import com.example.ui.components.GlassVaultCard
import com.example.ui.components.MetricStatTile
import com.example.ui.components.PriorityTierBadge
import com.example.ui.components.StatusPill
import com.example.ui.components.VaultAnimatedEntrance
import com.example.ui.components.VaultAtmosphereBackground
import com.example.ui.components.VaultEmptyState
import com.example.ui.components.VaultGlassSearchBar
import com.example.ui.components.VaultGradientButton
import com.example.ui.components.vaultSharedBounds
import com.example.ui.theme.LocalVaultColors
import com.example.util.DateAndCurrencyUtils
import com.example.viewmodel.VaultViewModel

@Composable
fun MedicineVaultDashboardScreen(
    viewModel: VaultViewModel,
    bottomContentPadding: Dp = 0.dp,
    onAddMedicine: () -> Unit,
    onOpenPriorityCenter: () -> Unit,
    onOpenMedicineDetail: (String) -> Unit
) {
    val medicinesState by viewModel.medicinesState.collectAsStateWithLifecycle()
    val sortedActiveMedicines by viewModel.sortedActiveMedicinesState.collectAsStateWithLifecycle()
    val priorityReport by viewModel.priorityCenterState.collectAsStateWithLifecycle()
    val vaultColors = LocalVaultColors.current
    val medicines = (medicinesState as? UiState.Success)?.data.orEmpty()

    var searchQuery by rememberSaveable { mutableStateOf("") }

    val matchesQuery: (MedicineRecord) -> Boolean = { m ->
        val q = searchQuery.trim()
        q.isEmpty() ||
            m.medicineName.contains(q, ignoreCase = true) ||
            m.batchNumber.contains(q, ignoreCase = true) ||
            (m.storageNote?.contains(q, ignoreCase = true) == true)
    }

    val activeFiltered = sortedActiveMedicines.filter(matchesQuery)
    val expiringSoon = activeFiltered.filter {
        DateAndCurrencyUtils.medicineExpiryStatus(it) == ExpiryStatus.EXPIRING_SOON
    }
    val valid = activeFiltered.filter {
        DateAndCurrencyUtils.medicineExpiryStatus(it) == ExpiryStatus.VALID
    }
    val expired = medicines.filter {
        !it.completed && DateAndCurrencyUtils.medicineExpiryStatus(it) == ExpiryStatus.EXPIRED && matchesQuery(it)
    }
    val completedMeds = medicines.filter { it.completed && matchesQuery(it) }

    val activeUnexpired = medicines.filter { !it.completed }
    val totalExpiringSoon = activeUnexpired.count {
        DateAndCurrencyUtils.medicineExpiryStatus(it) == ExpiryStatus.EXPIRING_SOON
    }
    val totalExpired = activeUnexpired.count {
        DateAndCurrencyUtils.medicineExpiryStatus(it) == ExpiryStatus.EXPIRED
    }
    val totalValid = activeUnexpired.count {
        DateAndCurrencyUtils.medicineExpiryStatus(it) == ExpiryStatus.VALID
    }

    VaultAtmosphereBackground(
        accentColor = vaultColors.medicineAccent,
        secondaryAccent = vaultColors.goldAccent
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("medicine_vault_dashboard"),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 16.dp,
                bottom = 16.dp + bottomContentPadding
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Banner
            item {
                GlassVaultCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .vaultSharedBounds("module_card_medicine"),
                    accentBorder = vaultColors.medicineAccent.copy(alpha = 0.72f),
                    containerBrush = Brush.linearGradient(
                        colors = listOf(
                            vaultColors.medicineContainer.copy(alpha = if (vaultColors.isDark) 0.78f else 0.85f),
                            vaultColors.glassSurface,
                            vaultColors.medicineAccent.copy(alpha = 0.12f)
                        )
                    )
                ) {
                    StatusPill(label = "MEDICINE VAULT", color = vaultColors.medicineAccent)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "${medicines.size} Stored Medicines",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Organized batch numbers, photos & expiry tracking",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    VaultGradientButton(
                        onClick = onAddMedicine,
                        colors = vaultColors.medicineButtonGradient,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("medicine_add_button")
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add Medicine")
                    }
                }
            }

            // Storage-only disclaimer
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = vaultColors.medicineAccent.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, vaultColors.medicineAccent.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = vaultColors.medicineAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(id = R.string.medicine_disclaimer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Stats
            item {
                VaultAnimatedEntrance(delayMillis = 70) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricStatTile(
                            label = "Valid",
                            value = totalValid.toString(),
                            icon = Icons.Outlined.CheckCircle,
                            accent = vaultColors.statusValid,
                            modifier = Modifier.weight(1f)
                        )
                        MetricStatTile(
                            label = "Expiring Soon",
                            value = totalExpiringSoon.toString(),
                            icon = Icons.Outlined.Timer,
                            accent = vaultColors.statusWarning,
                            modifier = Modifier.weight(1f)
                        )
                        MetricStatTile(
                            label = "Expired",
                            value = totalExpired.toString(),
                            icon = Icons.Outlined.WarningAmber,
                            accent = vaultColors.statusExpired,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Search Bar
            item {
                VaultGlassSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Search medicine name, batch, or note...",
                    accentColors = vaultColors.medicineButtonGradient,
                    testTag = "medicine_search_input"
                )
            }

            // Priority shortcut
            item {
                GlassVaultCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("medicine_priority_shortcut_card"),
                    accentBorder = vaultColors.goldAccent.copy(alpha = 0.45f),
                    onClick = onOpenPriorityCenter
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Bolt, contentDescription = null, tint = vaultColors.goldAccent)
                            Column {
                                Text("Priority Expiry Queue", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Medicines auto-sorted by expiry urgency & importance",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text("Open", color = vaultColors.goldAccent, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (activeFiltered.isEmpty() && expired.isEmpty() && completedMeds.isEmpty()) {
                item {
                    VaultEmptyState(
                        icon = Icons.Outlined.Medication,
                        title = if (medicines.isEmpty()) "No medicines stored" else "No matching medicines",
                        subtitle = "Add medicine photographs, batch numbers, and expiry dates to stay organized.",
                        accent = vaultColors.medicineAccent,
                        actionLabel = "Add Medicine",
                        onAction = onAddMedicine
                    )
                }
            } else {
                if (expiringSoon.isNotEmpty()) {
                    item {
                        Text(
                            text = "Priority Upcoming Expiry (${expiringSoon.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = vaultColors.statusWarning
                        )
                    }
                    items(expiringSoon, key = { "soon_${it.id}" }) { med ->
                        MedicineListCard(
                            medicine = med,
                            priorityItem = priorityReport.medicinePriorityMap[med.id],
                            onClick = { onOpenMedicineDetail(med.id) }
                        )
                    }
                }

                if (valid.isNotEmpty()) {
                    item {
                        Text(
                            text = "Valid Inventory (${valid.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    items(valid, key = { "val_${it.id}" }) { med ->
                        MedicineListCard(
                            medicine = med,
                            priorityItem = priorityReport.medicinePriorityMap[med.id],
                            onClick = { onOpenMedicineDetail(med.id) }
                        )
                    }
                }

                if (expired.isNotEmpty()) {
                    item {
                        Text(
                            text = "Expired Medicines (${expired.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = vaultColors.statusExpired
                        )
                    }
                    items(expired, key = { "exp_${it.id}" }) { med ->
                        MedicineListCard(
                            medicine = med,
                            priorityItem = priorityReport.medicinePriorityMap[med.id],
                            onClick = { onOpenMedicineDetail(med.id) }
                        )
                    }
                }

                if (completedMeds.isNotEmpty()) {
                    item {
                        Text(
                            text = "Resolved / Disposed History (${completedMeds.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(completedMeds, key = { "completed_med_${it.id}" }) { med ->
                        MedicineListCard(
                            medicine = med,
                            priorityItem = priorityReport.medicinePriorityMap[med.id],
                            onClick = { onOpenMedicineDetail(med.id) }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
fun MedicineListCard(
    medicine: MedicineRecord,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    priorityItem: PriorityItem? = null
) {
    val vaultColors = LocalVaultColors.current
    val resolvedPriority = priorityItem ?: PriorityEngine.Default.evaluateMedicine(medicine)
    val status = DateAndCurrencyUtils.medicineExpiryStatus(medicine)
    val days = DateAndCurrencyUtils.daysFromNow(medicine.expiryDateMillis)
    val statusColor = when {
        medicine.completed -> vaultColors.statusValid
        status == ExpiryStatus.VALID -> vaultColors.statusValid
        status == ExpiryStatus.EXPIRING_SOON -> vaultColors.statusWarning
        else -> vaultColors.statusExpired
    }
    val statusLabel = when {
        medicine.completed -> "RESOLVED"
        status == ExpiryStatus.VALID -> "VALID • ${DateAndCurrencyUtils.formatCountdown(days)}"
        status == ExpiryStatus.EXPIRING_SOON -> "SOON • ${DateAndCurrencyUtils.formatCountdown(days)}"
        else -> "EXPIRED"
    }

    GlassVaultCard(
        modifier = modifier
            .fillMaxWidth()
            .vaultSharedBounds("medicine_card_${medicine.id}"),
        accentBorder = statusColor.copy(alpha = 0.48f),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(vaultColors.medicineAccent.copy(alpha = 0.16f)),
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
                        Icon(
                            imageVector = Icons.Outlined.Medication,
                            contentDescription = null,
                            tint = vaultColors.medicineAccent
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = medicine.medicineName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Batch ${medicine.batchNumber.ifBlank { "N/A" }} • Exp ${DateAndCurrencyUtils.formatDate(medicine.expiryDateMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!medicine.quantity.isNullOrBlank()) {
                        Text(
                            text = medicine.quantity,
                            style = MaterialTheme.typography.bodySmall,
                            color = vaultColors.medicineAccent
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            StatusPill(label = statusLabel, color = statusColor)
        }
        if (!medicine.completed && status != ExpiryStatus.EXPIRED) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PriorityTierBadge(tier = resolvedPriority.priorityTier)
                StatusPill(
                    label = "Importance: ${resolvedPriority.importanceLevel.label}",
                    color = vaultColors.medicineAccent
                )
            }
        }
    }
}
