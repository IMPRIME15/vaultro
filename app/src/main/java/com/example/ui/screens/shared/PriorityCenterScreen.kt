package com.example.ui.screens.shared

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.PriorityTier
import com.example.model.VaultModule
import com.example.priority.PriorityItem
import com.example.ui.components.GlassVaultCard
import com.example.ui.components.PriorityRecordCard
import com.example.ui.components.StatusPill
import com.example.ui.components.VaultAtmosphereBackground
import com.example.ui.components.VaultEmptyState
import com.example.ui.components.VaultTopBar
import com.example.ui.components.tierColor
import com.example.ui.theme.LocalVaultColors
import com.example.viewmodel.VaultViewModel

private enum class PriorityCenterFilter(val label: String) {
    ALL_ACTIVE("All Active"),
    CRITICAL("Critical"),
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low"),
    OVERDUE("Overdue"),
    EXPIRED("Expired"),
    COMPLETED("Completed")
}

@Composable
fun PriorityCenterScreen(
    viewModel: VaultViewModel,
    onBack: () -> Unit,
    onOpenPurchaseDetail: (String) -> Unit,
    onOpenMedicineDetail: (String) -> Unit,
    onOpenNotificationPreferences: () -> Unit
) {
    val report by viewModel.priorityCenterState.collectAsStateWithLifecycle()
    val vaultColors = LocalVaultColors.current
    var selectedFilter by rememberSaveable { mutableStateOf(PriorityCenterFilter.ALL_ACTIVE) }

    val openRecord: (PriorityItem) -> Unit = { item ->
        if (item.module == VaultModule.PURCHASE) {
            onOpenPurchaseDetail(item.id)
        } else {
            onOpenMedicineDetail(item.id)
        }
    }

    VaultAtmosphereBackground(
        accentColor = vaultColors.purchaseAccent,
        secondaryAccent = vaultColors.medicineAccent
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                VaultTopBar(
                    title = "Priority Center",
                    subtitle = "${report.activeCount} active • ${report.overdueItems.size} overdue • Offline SPE",
                    onBack = onBack,
                    actions = {
                        IconButton(
                            onClick = onOpenNotificationPreferences,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("priority_center_preferences_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = "Priority notification preferences"
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("priority_center_screen"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Summary bar
                item {
                    GlassVaultCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentBorder = vaultColors.goldAccent.copy(alpha = 0.48f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Bolt,
                                contentDescription = null,
                                tint = vaultColors.goldAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Smart Priority Engine",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Weighted auto-ranking across Purchase & Medicine Vaults combining Importance Level and remaining time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PriorityTier.entries.forEach { tier ->
                                StatusPill(
                                    label = "${tier.label}: ${report.countForTier(tier)}",
                                    color = tierColor(tier, vaultColors)
                                )
                            }
                        }
                    }
                }

                // Filter chips
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PriorityCenterFilter.entries.forEach { filter ->
                            val count = when (filter) {
                                PriorityCenterFilter.ALL_ACTIVE -> report.activeCount
                                PriorityCenterFilter.CRITICAL -> report.criticalItems.size
                                PriorityCenterFilter.HIGH -> report.highItems.size
                                PriorityCenterFilter.MEDIUM -> report.mediumItems.size
                                PriorityCenterFilter.LOW -> report.lowItems.size
                                PriorityCenterFilter.OVERDUE -> report.overdueItems.size
                                PriorityCenterFilter.EXPIRED -> report.expiredMedicines.size
                                PriorityCenterFilter.COMPLETED -> report.completedItems.size
                            }
                            val chipColor = when (filter) {
                                PriorityCenterFilter.ALL_ACTIVE -> vaultColors.goldAccent
                                PriorityCenterFilter.CRITICAL -> vaultColors.statusExpired
                                PriorityCenterFilter.HIGH -> vaultColors.statusWarning
                                PriorityCenterFilter.MEDIUM -> vaultColors.goldAccent
                                PriorityCenterFilter.LOW -> vaultColors.statusValid
                                PriorityCenterFilter.OVERDUE -> vaultColors.statusExpired
                                PriorityCenterFilter.EXPIRED -> vaultColors.statusExpired
                                PriorityCenterFilter.COMPLETED -> vaultColors.statusValid
                            }
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = { Text("${filter.label} ($count)", maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = chipColor.copy(alpha = 0.22f),
                                    selectedLabelColor = chipColor
                                ),
                                modifier = Modifier.testTag("priority_filter_${filter.name.lowercase()}")
                            )
                        }
                    }
                }

                when (selectedFilter) {
                    PriorityCenterFilter.ALL_ACTIVE -> {
                        if (report.activeItems.isEmpty()) {
                            item {
                                VaultEmptyState(
                                    icon = Icons.Outlined.CheckCircle,
                                    title = "No active priority records",
                                    subtitle = "Add receipts, warranties, or medicines to see them automatically ranked here.",
                                    accent = vaultColors.goldAccent
                                )
                            }
                        } else {
                            PriorityTier.entries.forEach { tier ->
                                val tierItems = report.itemsForTier(tier)
                                if (tierItems.isNotEmpty()) {
                                    item(key = "header_${tier.name}") {
                                        PriorityGroupHeader(
                                            title = "${tier.label} – ${tier.subtitle}",
                                            count = tierItems.size,
                                            accent = tierColor(tier, vaultColors)
                                        )
                                    }
                                    items(tierItems, key = { "active_${it.recordKey}" }) { item ->
                                        PriorityRecordCard(
                                            item = item,
                                            onOpen = { openRecord(item) },
                                            onToggleComplete = {
                                                if (item.module == VaultModule.PURCHASE) {
                                                    viewModel.setPurchaseCompleted(item.id, true)
                                                } else {
                                                    viewModel.setMedicineCompleted(item.id, true)
                                                }
                                            },
                                            onImportanceSelected = { level ->
                                                if (item.module == VaultModule.PURCHASE) {
                                                    viewModel.updatePurchaseImportance(item.id, level)
                                                } else {
                                                    viewModel.updateMedicineImportance(item.id, level)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    PriorityCenterFilter.CRITICAL,
                    PriorityCenterFilter.HIGH,
                    PriorityCenterFilter.MEDIUM,
                    PriorityCenterFilter.LOW -> {
                        val tier = when (selectedFilter) {
                            PriorityCenterFilter.CRITICAL -> PriorityTier.CRITICAL
                            PriorityCenterFilter.HIGH -> PriorityTier.HIGH
                            PriorityCenterFilter.MEDIUM -> PriorityTier.MEDIUM
                            else -> PriorityTier.LOW
                        }
                        val itemsList = report.itemsForTier(tier)
                        if (itemsList.isEmpty()) {
                            item {
                                VaultEmptyState(
                                    icon = Icons.Outlined.CheckCircle,
                                    title = "No ${tier.label} items",
                                    subtitle = tier.subtitle,
                                    accent = tierColor(tier, vaultColors)
                                )
                            }
                        } else {
                            item {
                                PriorityGroupHeader(
                                    title = "${tier.label} – ${tier.subtitle}",
                                    count = itemsList.size,
                                    accent = tierColor(tier, vaultColors)
                                )
                            }
                            items(itemsList, key = { "tier_${it.recordKey}" }) { item ->
                                PriorityRecordCard(
                                    item = item,
                                    onOpen = { openRecord(item) },
                                    onToggleComplete = {
                                        if (item.module == VaultModule.PURCHASE) {
                                            viewModel.setPurchaseCompleted(item.id, true)
                                        } else {
                                            viewModel.setMedicineCompleted(item.id, true)
                                        }
                                    },
                                    onImportanceSelected = { level ->
                                        if (item.module == VaultModule.PURCHASE) {
                                            viewModel.updatePurchaseImportance(item.id, level)
                                        } else {
                                            viewModel.updateMedicineImportance(item.id, level)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    PriorityCenterFilter.OVERDUE -> {
                        if (report.overdueItems.isEmpty()) {
                            item {
                                VaultEmptyState(
                                    icon = Icons.Outlined.WarningAmber,
                                    title = "No overdue returns or warranties",
                                    subtitle = "Overdue purchase return deadlines, warranty expiries, and claim follow-ups appear here.",
                                    accent = vaultColors.statusValid
                                )
                            }
                        } else {
                            item {
                                PriorityGroupHeader(
                                    title = "OVERDUE RETURNS & WARRANTIES",
                                    count = report.overdueItems.size,
                                    accent = vaultColors.statusExpired
                                )
                            }
                            items(report.overdueItems, key = { "overdue_${it.recordKey}" }) { item ->
                                PriorityRecordCard(
                                    item = item,
                                    onOpen = { openRecord(item) },
                                    onToggleComplete = {
                                        viewModel.setPurchaseCompleted(item.id, true)
                                    },
                                    onImportanceSelected = { level ->
                                        viewModel.updatePurchaseImportance(item.id, level)
                                    }
                                )
                            }
                        }
                    }

                    PriorityCenterFilter.EXPIRED -> {
                        if (report.expiredMedicines.isEmpty()) {
                            item {
                                VaultEmptyState(
                                    icon = Icons.Outlined.EventBusy,
                                    title = "No expired medicines",
                                    subtitle = "Medicines past their expiry date are preserved here separately from active priority.",
                                    accent = vaultColors.medicineAccent
                                )
                            }
                        } else {
                            item {
                                PriorityGroupHeader(
                                    title = "EXPIRED MEDICINES (PRESERVED)",
                                    count = report.expiredMedicines.size,
                                    accent = vaultColors.statusExpired
                                )
                            }
                            items(report.expiredMedicines, key = { "expired_${it.recordKey}" }) { item ->
                                PriorityRecordCard(
                                    item = item,
                                    onOpen = { openRecord(item) },
                                    onToggleComplete = {
                                        viewModel.setMedicineCompleted(item.id, true)
                                    }
                                )
                            }
                        }
                    }

                    PriorityCenterFilter.COMPLETED -> {
                        if (report.completedItems.isEmpty()) {
                            item {
                                VaultEmptyState(
                                    icon = Icons.Outlined.CheckCircle,
                                    title = "No completed records yet",
                                    subtitle = "Completed purchases and resolved medicines are preserved here and excluded from active alerts.",
                                    accent = vaultColors.statusValid
                                )
                            }
                        } else {
                            item {
                                PriorityGroupHeader(
                                    title = "COMPLETED HISTORY (PRESERVED)",
                                    count = report.completedItems.size,
                                    accent = vaultColors.statusValid
                                )
                            }
                            items(report.completedItems, key = { "completed_${it.recordKey}" }) { item ->
                                PriorityRecordCard(
                                    item = item,
                                    onOpen = { openRecord(item) },
                                    onToggleComplete = {
                                        if (item.module == VaultModule.PURCHASE) {
                                            viewModel.setPurchaseCompleted(item.id, false)
                                        } else {
                                            viewModel.setMedicineCompleted(item.id, false)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun PriorityGroupHeader(
    title: String,
    count: Int,
    accent: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
        StatusPill(label = count.toString(), color = accent)
    }
}
