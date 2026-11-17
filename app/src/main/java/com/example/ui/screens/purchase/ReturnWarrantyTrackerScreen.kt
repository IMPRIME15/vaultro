package com.example.ui.screens.purchase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.PurchaseRecord
import com.example.model.ReturnStatus
import com.example.model.UiState
import com.example.model.WarrantyStatus
import com.example.ui.components.GlassVaultCard
import com.example.ui.components.StatusPill
import com.example.ui.components.VaultAtmosphereBackground
import com.example.ui.components.VaultEmptyState
import com.example.ui.components.VaultTopBar
import com.example.ui.components.vaultSharedBounds
import com.example.ui.theme.LocalVaultColors
import com.example.util.DateAndCurrencyUtils
import com.example.viewmodel.VaultViewModel

@Composable
fun ReturnWarrantyTrackerScreen(
    initialTab: Int = 0,
    viewModel: VaultViewModel,
    onBack: () -> Unit,
    onOpenPurchaseDetail: (String) -> Unit,
    onOpenWarrantyClaim: (String) -> Unit
) {
    val purchasesState by viewModel.purchasesState.collectAsStateWithLifecycle()
    val vaultColors = LocalVaultColors.current
    val purchases = (purchasesState as? UiState.Success)?.data.orEmpty()

    var selectedTab by rememberSaveable(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 1)) }

    val activePurchases = purchases.filter { !it.completed }
    val completedTrackerItems = purchases.filter {
        it.completed && (it.returnDeadlineMillis != null || it.warrantyExpiryMillis != null)
    }

    val returnItems = activePurchases.filter { it.returnDeadlineMillis != null }
        .sortedBy { it.returnDeadlineMillis }
    val eligibleReturns = returnItems.filter {
        val st = DateAndCurrencyUtils.returnStatus(it)
        st == ReturnStatus.ELIGIBLE || st == ReturnStatus.CLOSING_SOON
    }
    val pastReturns = returnItems.filter {
        DateAndCurrencyUtils.returnStatus(it) == ReturnStatus.PAST
    }

    val warrantyItems = activePurchases.filter { it.warrantyExpiryMillis != null }
        .sortedBy { it.warrantyExpiryMillis }
    val activeWarranties = warrantyItems.filter {
        val st = DateAndCurrencyUtils.warrantyStatus(it)
        st == WarrantyStatus.ACTIVE || st == WarrantyStatus.EXPIRING_SOON
    }
    val expiredWarranties = warrantyItems.filter {
        DateAndCurrencyUtils.warrantyStatus(it) == WarrantyStatus.EXPIRED
    }

    VaultAtmosphereBackground(
        accentColor = vaultColors.purchaseAccent,
        secondaryAccent = vaultColors.goldAccent
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                VaultTopBar(
                    title = "Return & Warranty Tracker",
                    subtitle = "Purchase Vault Deadlines",
                    onBack = onBack
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("return_warranty_tracker_screen")
            ) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = vaultColors.purchaseAccent,
                    indicator = { positions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(positions[selectedTab]),
                            color = vaultColors.purchaseAccent
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Return Tracker (${returnItems.size})") },
                        modifier = Modifier.testTag("return_tracker_tab")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Warranty Tracker (${warrantyItems.size})") },
                        modifier = Modifier.testTag("warranty_tracker_tab")
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (selectedTab == 0) {
                        if (returnItems.isEmpty()) {
                            item {
                                VaultEmptyState(
                                    icon = Icons.Outlined.EventAvailable,
                                    title = "No return deadlines tracked",
                                    subtitle = "Add a return deadline when saving a receipt to see live countdowns here.",
                                    accent = vaultColors.purchaseAccent
                                )
                            }
                        } else {
                            if (eligibleReturns.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Eligible & Upcoming Returns (${eligibleReturns.size})",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                items(eligibleReturns, key = { "ret_${it.id}" }) { p ->
                                    val days = DateAndCurrencyUtils.daysFromNow(p.returnDeadlineMillis!!)
                                    val isSoon = days <= 5L
                                    TrackerRowCard(
                                        purchase = p,
                                        dateLabel = "Return by ${DateAndCurrencyUtils.formatDate(p.returnDeadlineMillis)}",
                                        countdownLabel = DateAndCurrencyUtils.formatCountdown(days),
                                        statusColor = if (isSoon) vaultColors.statusWarning else vaultColors.statusValid,
                                        actionLabel = null,
                                        onClick = { onOpenPurchaseDetail(p.id) },
                                        onActionClick = null
                                    )
                                }
                            }
                            if (pastReturns.isNotEmpty()) {
                                item {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "Overdue Return Deadlines (${pastReturns.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = vaultColors.statusExpired
                                    )
                                }
                                items(pastReturns, key = { "ret_past_${it.id}" }) { p ->
                                    val days = DateAndCurrencyUtils.daysFromNow(p.returnDeadlineMillis!!)
                                    val overdueDays = if (days < 0L) -days else 0L
                                    TrackerRowCard(
                                        purchase = p,
                                        dateLabel = "Deadline passed ${DateAndCurrencyUtils.formatDate(p.returnDeadlineMillis)}",
                                        countdownLabel = "OVERDUE ${overdueDays}d",
                                        statusColor = vaultColors.statusExpired,
                                        actionLabel = null,
                                        onClick = { onOpenPurchaseDetail(p.id) },
                                        onActionClick = null
                                    )
                                }
                            }
                        }
                    } else {
                        if (warrantyItems.isEmpty()) {
                            item {
                                VaultEmptyState(
                                    icon = Icons.Outlined.Shield,
                                    title = "No warranties tracked",
                                    subtitle = "Set a warranty duration or expiry date on a receipt to monitor coverage.",
                                    accent = vaultColors.purchaseAccent
                                )
                            }
                        } else {
                            if (activeWarranties.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Active Warranties (${activeWarranties.size})",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                items(activeWarranties, key = { "war_${it.id}" }) { p ->
                                    val days = DateAndCurrencyUtils.daysFromNow(p.warrantyExpiryMillis!!)
                                    val status = DateAndCurrencyUtils.warrantyStatus(p)
                                    val color = if (status == WarrantyStatus.EXPIRING_SOON) {
                                        vaultColors.statusWarning
                                    } else {
                                        vaultColors.statusValid
                                    }
                                    TrackerRowCard(
                                        purchase = p,
                                        dateLabel = "Expires ${DateAndCurrencyUtils.formatDate(p.warrantyExpiryMillis)}",
                                        countdownLabel = DateAndCurrencyUtils.formatCountdown(days),
                                        statusColor = color,
                                        actionLabel = "Prepare Claim",
                                        onClick = { onOpenPurchaseDetail(p.id) },
                                        onActionClick = { onOpenWarrantyClaim(p.id) }
                                    )
                                }
                            }
                            if (expiredWarranties.isNotEmpty()) {
                                item {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "Overdue / Expired Warranties (${expiredWarranties.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = vaultColors.statusExpired
                                    )
                                }
                                items(expiredWarranties, key = { "war_exp_${it.id}" }) { p ->
                                    val days = DateAndCurrencyUtils.daysFromNow(p.warrantyExpiryMillis!!)
                                    val overdueDays = if (days < 0L) -days else 0L
                                    TrackerRowCard(
                                        purchase = p,
                                        dateLabel = "Expired ${DateAndCurrencyUtils.formatDate(p.warrantyExpiryMillis)}",
                                        countdownLabel = "OVERDUE ${overdueDays}d",
                                        statusColor = vaultColors.statusExpired,
                                        actionLabel = null,
                                        onClick = { onOpenPurchaseDetail(p.id) },
                                        onActionClick = null
                                    )
                                }
                            }
                        }
                    }

                    if (completedTrackerItems.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Completed History (${completedTrackerItems.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(completedTrackerItems, key = { "completed_trk_${it.id}" }) { p ->
                            TrackerRowCard(
                                purchase = p,
                                dateLabel = "Marked completed • ${DateAndCurrencyUtils.formatDate(p.purchaseDateMillis)}",
                                countdownLabel = "COMPLETED",
                                statusColor = vaultColors.statusValid,
                                actionLabel = null,
                                onClick = { onOpenPurchaseDetail(p.id) },
                                onActionClick = null
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackerRowCard(
    purchase: PurchaseRecord,
    dateLabel: String,
    countdownLabel: String,
    statusColor: Color,
    actionLabel: String?,
    onClick: () -> Unit,
    onActionClick: (() -> Unit)?
) {
    GlassVaultCard(
        modifier = Modifier
            .fillMaxWidth()
            .vaultSharedBounds("purchase_card_${purchase.id}"),
        accentBorder = statusColor.copy(alpha = 0.4f),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(purchase.productName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${purchase.merchantName} • $dateLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusPill(label = countdownLabel, color = statusColor)
        }
        val claimStatus = purchase.resolvedClaimStatus()
        if (claimStatus != com.example.model.WarrantyClaimStatus.NOT_SUBMITTED) {
            Spacer(Modifier.height(6.dp))
            StatusPill(
                label = "CLAIM: ${claimStatus.label.uppercase()}",
                color = LocalVaultColors.current.goldAccent
            )
        }
        if (actionLabel != null && onActionClick != null) {
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = onActionClick,
                    modifier = Modifier.testTag("tracker_prepare_claim_${purchase.id}")
                ) {
                    Text(actionLabel, color = LocalVaultColors.current.purchaseAccent)
                }
            }
        }
    }
}
