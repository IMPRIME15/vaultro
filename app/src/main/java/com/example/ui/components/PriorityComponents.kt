package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ImportanceLevel
import com.example.model.PriorityTier
import com.example.model.VaultModule
import com.example.priority.PriorityItem
import com.example.ui.theme.LocalVaultColors
import com.example.ui.theme.VaultExtendedColors
import com.example.util.DateAndCurrencyUtils

fun tierColor(tier: PriorityTier, vaultColors: VaultExtendedColors): Color {
    return when (tier) {
        PriorityTier.CRITICAL -> vaultColors.statusExpired
        PriorityTier.HIGH -> vaultColors.statusWarning
        PriorityTier.MEDIUM -> vaultColors.goldAccent
        PriorityTier.LOW -> vaultColors.statusValid
    }
}

fun importanceColor(level: ImportanceLevel, vaultColors: VaultExtendedColors): Color {
    return when (level) {
        ImportanceLevel.CRITICAL -> vaultColors.statusExpired
        ImportanceLevel.HIGH -> vaultColors.statusWarning
        ImportanceLevel.MEDIUM -> vaultColors.goldAccent
        ImportanceLevel.LOW -> vaultColors.statusValid
    }
}

@Composable
fun PriorityTierBadge(
    tier: PriorityTier,
    modifier: Modifier = Modifier
) {
    val vaultColors = LocalVaultColors.current
    StatusPill(
        label = tier.label,
        color = tierColor(tier, vaultColors),
        modifier = modifier
    )
}

@Composable
fun ImportanceSelectorRow(
    selected: ImportanceLevel?,
    onSelect: (ImportanceLevel?) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
    tagPrefix: String = "importance"
) {
    val vaultColors = LocalVaultColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Importance Level (Smart Priority)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("Auto", maxLines = 1) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accentColor.copy(alpha = 0.22f),
                    selectedLabelColor = accentColor
                ),
                modifier = Modifier.testTag("${tagPrefix}_auto")
            )
            ImportanceLevel.entries.forEach { level ->
                val chipColor = importanceColor(level, vaultColors)
                FilterChip(
                    selected = selected == level,
                    onClick = { onSelect(level) },
                    label = { Text(level.label, maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipColor.copy(alpha = 0.22f),
                        selectedLabelColor = chipColor
                    ),
                    modifier = Modifier.testTag("${tagPrefix}_${level.name.lowercase()}")
                )
            }
        }
    }
}

@Composable
fun PriorityRecordCard(
    item: PriorityItem,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleComplete: (() -> Unit)? = null,
    onImportanceSelected: ((ImportanceLevel?) -> Unit)? = null
) {
    val vaultColors = LocalVaultColors.current
    val moduleColor = if (item.module == VaultModule.PURCHASE) {
        vaultColors.purchaseAccent
    } else {
        vaultColors.medicineAccent
    }
    val priorityColor = when {
        item.isCompleted -> vaultColors.statusValid
        item.isExpired -> vaultColors.statusExpired
        item.isOverdue -> vaultColors.statusExpired
        else -> tierColor(item.priorityTier, vaultColors)
    }
    val importanceAccent = importanceColor(item.importanceLevel, vaultColors)
    var showImportanceMenu by remember { mutableStateOf(false) }

    val deadlineText = if (item.deadlineMillis != null && item.deadlineMillis > 0L) {
        DateAndCurrencyUtils.formatDate(item.deadlineMillis)
    } else {
        "No fixed deadline"
    }
    val remainingText = when {
        item.isCompleted -> "Completed"
        item.isExpired && item.remainingDays != null -> "Expired ${-item.remainingDays}d ago"
        item.isOverdue && item.remainingDays != null -> "Overdue by ${-item.remainingDays}d"
        item.remainingDays == 0L -> "Due Today"
        item.remainingDays == 1L -> "1 day left"
        item.remainingDays != null -> "${item.remainingDays} days left"
        else -> "No deadline"
    }

    GlassVaultCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("priority_item_card_${item.recordKey}"),
        accentBorder = priorityColor.copy(alpha = if (item.isCompleted) 0.30f else 0.55f),
        onClick = onOpen
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
                        .background(moduleColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.module == VaultModule.PURCHASE) {
                            Icons.Outlined.ReceiptLong
                        } else {
                            Icons.Outlined.Medication
                        },
                        contentDescription = if (item.module == VaultModule.PURCHASE) "Purchase record" else "Medicine record",
                        tint = moduleColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            StatusPill(
                label = when {
                    item.isCompleted -> "COMPLETED"
                    item.isExpired -> "EXPIRED"
                    item.isOverdue -> "OVERDUE"
                    else -> item.priorityTier.label
                },
                color = priorityColor
            )
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = vaultColors.glassBorder)
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = priorityColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "$deadlineText • $remainingText",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.isOverdue || item.isExpired) priorityColor else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (item.isOverdue || item.isExpired || (item.remainingDays != null && item.remainingDays <= 3L)) {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Normal
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = item.priorityStatusLabel,
                style = MaterialTheme.typography.labelSmall,
                color = priorityColor
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                val importanceText = if (item.isExplicitImportance) {
                    "Importance: ${item.importanceLevel.label}"
                } else {
                    "Importance: ${item.importanceLevel.label} (Auto)"
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = importanceAccent.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, importanceAccent.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(50))
                        .then(
                            if (onImportanceSelected != null) {
                                Modifier.clickable { showImportanceMenu = true }
                            } else {
                                Modifier
                            }
                        )
                        .testTag("priority_importance_pill_${item.recordKey}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PriorityHigh,
                            contentDescription = "Change importance level",
                            tint = importanceAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = importanceText,
                            style = MaterialTheme.typography.labelSmall,
                            color = importanceAccent,
                            maxLines = 1
                        )
                    }
                }
                if (onImportanceSelected != null) {
                    DropdownMenu(
                        expanded = showImportanceMenu,
                        onDismissRequest = { showImportanceMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Auto (SPE Calculated)") },
                            onClick = {
                                showImportanceMenu = false
                                onImportanceSelected(null)
                            }
                        )
                        ImportanceLevel.entries.forEach { level ->
                            DropdownMenuItem(
                                text = { Text(level.label) },
                                onClick = {
                                    showImportanceMenu = false
                                    onImportanceSelected(level)
                                }
                            )
                        }
                    }
                }
            }

            if (onToggleComplete != null) {
                TextButton(
                    onClick = onToggleComplete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("priority_toggle_complete_${item.recordKey}")
                ) {
                    Icon(
                        imageVector = if (item.isCompleted) Icons.Outlined.Replay else Icons.Outlined.CheckCircle,
                        contentDescription = if (item.isCompleted) "Reopen record" else "Mark completed",
                        tint = if (item.isCompleted) vaultColors.goldAccent else moduleColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (item.isCompleted) "Reopen" else "Complete",
                        color = if (item.isCompleted) vaultColors.goldAccent else moduleColor
                    )
                }
            }
        }
    }
}
