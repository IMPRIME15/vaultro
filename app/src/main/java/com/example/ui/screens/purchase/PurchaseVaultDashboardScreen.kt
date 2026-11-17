package com.example.ui.screens.purchase

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.PurchaseRecord
import com.example.model.ReturnStatus
import com.example.model.UiState
import com.example.model.WarrantyStatus
import com.example.priority.PriorityEngine
import com.example.priority.PriorityItem
import com.example.ui.components.GlassVaultCard
import com.example.ui.components.MetricStatTile
import com.example.ui.components.PriorityTierBadge
import com.example.ui.components.StatusPill
import com.example.ui.components.VaultAnimatedEntrance
import com.example.ui.components.VaultAtmosphereBackground
import com.example.ui.components.VaultEmptyState
import com.example.ui.components.VaultGlassOutlinedButton
import com.example.ui.components.VaultGlassSearchBar
import com.example.ui.components.VaultGradientButton
import com.example.ui.components.vaultSharedBounds
import com.example.ui.theme.LocalVaultColors
import com.example.util.DateAndCurrencyUtils
import com.example.viewmodel.VaultViewModel

@Composable
fun PurchaseVaultDashboardScreen(
    viewModel: VaultViewModel,
    bottomContentPadding: Dp = 0.dp,
    onScanReceipt: () -> Unit,
    onAddReceipt: () -> Unit,
    onViewAllReceipts: () -> Unit,
    onOpenTracker: (Int) -> Unit,
    onOpenPriorityCenter: () -> Unit,
    onOpenPurchaseDetail: (String) -> Unit
) {
    val purchasesState by viewModel.purchasesState.collectAsStateWithLifecycle()
    val sortedActivePurchases by viewModel.sortedActivePurchasesState.collectAsStateWithLifecycle()
    val priorityReport by viewModel.priorityCenterState.collectAsStateWithLifecycle()
    val vaultColors = LocalVaultColors.current
    val purchases = (purchasesState as? UiState.Success)?.data.orEmpty()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("All") }

    val categories = listOf("All") + purchases.map { it.category }.distinct().sorted()
    val matchesFilter: (PurchaseRecord) -> Boolean = { p ->
        val matchesCategory = selectedCategory == "All" || p.category == selectedCategory
        val q = searchQuery.trim()
        val matchesQuery = q.isEmpty() ||
            p.productName.contains(q, ignoreCase = true) ||
            p.merchantName.contains(q, ignoreCase = true) ||
            p.category.contains(q, ignoreCase = true) ||
            p.invoiceNumber.contains(q, ignoreCase = true)
        matchesCategory && matchesQuery
    }
    val filteredActive = sortedActivePurchases.filter(matchesFilter)
    val completedPurchases = purchases.filter { it.completed && matchesFilter(it) }

    val activeOnly = purchases.filter { !it.completed }
    val totalValue = purchases.sumOf { it.purchasePriceInr }
    val activeWarranties = activeOnly.count {
        val st = DateAndCurrencyUtils.warrantyStatus(it)
        st == WarrantyStatus.ACTIVE || st == WarrantyStatus.EXPIRING_SOON
    }
    val expiredWarranties = activeOnly.count {
        DateAndCurrencyUtils.warrantyStatus(it) == WarrantyStatus.EXPIRED
    }
    val upcomingReturns = activeOnly.filter {
        val st = DateAndCurrencyUtils.returnStatus(it)
        st == ReturnStatus.ELIGIBLE || st == ReturnStatus.CLOSING_SOON
    }
    val upcomingWarrantyExpiries = activeOnly.filter {
        DateAndCurrencyUtils.warrantyStatus(it) == WarrantyStatus.EXPIRING_SOON
    }

    VaultAtmosphereBackground(
        accentColor = vaultColors.purchaseAccent,
        secondaryAccent = vaultColors.goldAccent
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("purchase_vault_dashboard"),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 16.dp,
                bottom = 16.dp + bottomContentPadding
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                GlassVaultCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .vaultSharedBounds("module_card_purchase"),
                    accentBorder = vaultColors.purchaseAccent.copy(alpha = 0.72f),
                    containerBrush = Brush.linearGradient(
                        colors = listOf(
                            vaultColors.purchaseContainer.copy(alpha = if (vaultColors.isDark) 0.78f else 0.85f),
                            vaultColors.glassSurface,
                            vaultColors.glassSheenEnd
                        )
                    )
                ) {
                    StatusPill(label = "PURCHASE VAULT", color = vaultColors.purchaseAccent)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = DateAndCurrencyUtils.formatInr(totalValue),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Total value across ${purchases.size} stored receipts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        VaultGradientButton(
                            onClick = onScanReceipt,
                            colors = vaultColors.purchaseButtonGradient,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("purchase_scan_button")
                        ) {
                            Icon(Icons.Outlined.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Scan Receipt", maxLines = 1)
                        }
                        VaultGlassOutlinedButton(
                            onClick = onAddReceipt,
                            colors = vaultColors.purchaseButtonGradient,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("purchase_add_button")
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Manual")
                        }
                    }
                }
            }

            // Stats 2x2 Grid
            item {
                VaultAnimatedEntrance(delayMillis = 70) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricStatTile(
                            label = "Total Receipts",
                            value = purchases.size.toString(),
                            icon = Icons.Outlined.ReceiptLong,
                            accent = vaultColors.purchaseAccent,
                            modifier = Modifier.weight(1f),
                            onClick = onViewAllReceipts
                        )
                        MetricStatTile(
                            label = "Active Warranties",
                            value = activeWarranties.toString(),
                            icon = Icons.Outlined.VerifiedUser,
                            accent = vaultColors.statusValid,
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenTracker(1) }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricStatTile(
                            label = "Return Deadlines",
                            value = upcomingReturns.size.toString(),
                            icon = Icons.Outlined.EventAvailable,
                            accent = vaultColors.statusWarning,
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenTracker(0) }
                        )
                        MetricStatTile(
                            label = "Expired Warranties",
                            value = expiredWarranties.toString(),
                            icon = Icons.Outlined.History,
                            accent = vaultColors.statusExpired,
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenTracker(1) }
                        )
                    }
                }
                }
            }

            // Tracker & Library Shortcuts
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassVaultCard(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_tracker_card"),
                        accentBorder = vaultColors.goldAccent.copy(alpha = 0.4f),
                        onClick = { onOpenTracker(0) }
                    ) {
                        Icon(Icons.Outlined.Shield, contentDescription = null, tint = vaultColors.goldAccent)
                        Spacer(Modifier.height(6.dp))
                        Text("Return & Warranty", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Track deadlines",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    GlassVaultCard(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_all_receipts_card"),
                        accentBorder = vaultColors.purchaseAccent.copy(alpha = 0.4f),
                        onClick = onViewAllReceipts
                    ) {
                        Icon(Icons.Outlined.ReceiptLong, contentDescription = null, tint = vaultColors.purchaseAccent)
                        Spacer(Modifier.height(6.dp))
                        Text("All Receipts", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Filter & sort library",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Upcoming Deadlines Section
            if (upcomingReturns.isNotEmpty() || upcomingWarrantyExpiries.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Upcoming Deadlines", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { onOpenTracker(0) }) {
                            Text("Open Tracker", color = vaultColors.purchaseAccent)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        upcomingReturns.take(2).forEach { p ->
                            val days = DateAndCurrencyUtils.daysFromNow(p.returnDeadlineMillis!!)
                            DeadlineMiniRow(
                                title = p.productName,
                                subtitle = "Return by ${DateAndCurrencyUtils.formatDate(p.returnDeadlineMillis)}",
                                badge = DateAndCurrencyUtils.formatCountdown(days),
                                color = vaultColors.statusWarning,
                                onClick = { onOpenPurchaseDetail(p.id) }
                            )
                        }
                        upcomingWarrantyExpiries.take(2).forEach { p ->
                            val days = DateAndCurrencyUtils.daysFromNow(p.warrantyExpiryMillis!!)
                            DeadlineMiniRow(
                                title = p.productName,
                                subtitle = "Warranty ends ${DateAndCurrencyUtils.formatDate(p.warrantyExpiryMillis)}",
                                badge = DateAndCurrencyUtils.formatCountdown(days),
                                color = vaultColors.purchaseAccent,
                                onClick = { onOpenPurchaseDetail(p.id) }
                            )
                        }
                    }
                }
            }

            // Search & Filter
            item {
                VaultGlassSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Search product, category, store, or invoice...",
                    accentColors = vaultColors.purchaseButtonGradient,
                    testTag = "purchase_dashboard_search"
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = vaultColors.purchaseAccent.copy(alpha = 0.22f),
                                selectedLabelColor = vaultColors.purchaseAccent
                            )
                        )
                    }
                }
            }

            // Priority Center shortcut banner
            item {
                GlassVaultCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("purchase_priority_shortcut_card"),
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
                                Text("Priority Ranked Queue", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Returns, warranties & claim follow-ups auto-sorted by priority",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text("Open", color = vaultColors.goldAccent, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Priority Receipts", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onViewAllReceipts) {
                        Text("View All (${purchases.size})", color = vaultColors.purchaseAccent)
                    }
                }
            }

            if (filteredActive.isEmpty() && completedPurchases.isEmpty()) {
                item {
                    VaultEmptyState(
                        icon = Icons.Outlined.ReceiptLong,
                        title = if (purchases.isEmpty()) "No receipts saved yet" else "No matching receipts",
                        subtitle = if (purchases.isEmpty()) {
                            "Scan a receipt or upload a PDF invoice to start tracking warranties and returns."
                        } else {
                            "Try clearing your search or selecting All categories."
                        },
                        accent = vaultColors.purchaseAccent,
                        actionLabel = "Scan Receipt",
                        onAction = onScanReceipt
                    )
                }
            } else {
                items(filteredActive.take(10), key = { it.id }) { purchase ->
                    val priorityItem = priorityReport.purchasePriorityMap[purchase.id]
                    PurchaseListCard(
                        purchase = purchase,
                        priorityItem = priorityItem,
                        onClick = { onOpenPurchaseDetail(purchase.id) }
                    )
                }

                if (completedPurchases.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = vaultColors.statusValid,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Completed History (${completedPurchases.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(completedPurchases.take(6), key = { "completed_${it.id}" }) { purchase ->
                        PurchaseListCard(
                            purchase = purchase,
                            priorityItem = priorityReport.purchasePriorityMap[purchase.id],
                            onClick = { onOpenPurchaseDetail(purchase.id) }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun DeadlineMiniRow(
    title: String,
    subtitle: String,
    badge: String,
    color: Color,
    onClick: () -> Unit
) {
    GlassVaultCard(
        modifier = Modifier.fillMaxWidth(),
        accentBorder = color.copy(alpha = 0.4f),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(8.dp))
            StatusPill(label = badge, color = color)
        }
    }
}

@Composable
fun PurchaseListCard(
    purchase: PurchaseRecord,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    priorityItem: PriorityItem? = null
) {
    val vaultColors = LocalVaultColors.current
    val wStatus = DateAndCurrencyUtils.warrantyStatus(purchase)
    val rStatus = DateAndCurrencyUtils.returnStatus(purchase)
    val resolvedPriority = priorityItem ?: PriorityEngine.Default.evaluatePurchase(purchase)

    GlassVaultCard(
        modifier = modifier
            .fillMaxWidth()
            .vaultSharedBounds("purchase_card_${purchase.id}"),
        accentBorder = if (resolvedPriority.isOverdue) {
            vaultColors.statusExpired.copy(alpha = 0.58f)
        } else {
            vaultColors.purchaseAccent.copy(alpha = 0.42f)
        },
        onClick = onClick
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
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(vaultColors.purchaseAccent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ReceiptLong,
                        contentDescription = null,
                        tint = vaultColors.purchaseAccent
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = purchase.productName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${purchase.merchantName} • ${DateAndCurrencyUtils.formatDate(purchase.purchaseDateMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = DateAndCurrencyUtils.formatInr(purchase.purchasePriceInr),
                style = MaterialTheme.typography.titleSmall,
                color = vaultColors.purchaseAccent,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (purchase.completed) {
                StatusPill(label = "COMPLETED", color = vaultColors.statusValid)
            } else {
                PriorityTierBadge(tier = resolvedPriority.priorityTier)
                if (resolvedPriority.isOverdue) {
                    StatusPill(label = resolvedPriority.priorityStatusLabel, color = vaultColors.statusExpired)
                }
            }
            if (purchase.warrantyClaimActive && !purchase.completed) {
                StatusPill(label = "CLAIM FOLLOW-UP", color = vaultColors.goldAccent)
            }
            if (purchase.invoiceNumber.isNotBlank()) {
                StatusPill(label = purchase.invoiceNumber, color = vaultColors.goldAccent)
            }
            when (rStatus) {
                ReturnStatus.CLOSING_SOON -> StatusPill(
                    label = "Return ${DateAndCurrencyUtils.formatCountdown(DateAndCurrencyUtils.daysFromNow(purchase.returnDeadlineMillis!!))}",
                    color = vaultColors.statusWarning
                )
                ReturnStatus.ELIGIBLE -> StatusPill(label = "Return Open", color = vaultColors.statusValid)
                else -> {}
            }
            when (wStatus) {
                WarrantyStatus.ACTIVE -> StatusPill(label = "Warranty Active", color = vaultColors.statusValid)
                WarrantyStatus.EXPIRING_SOON -> StatusPill(label = "Warranty Soon", color = vaultColors.statusWarning)
                WarrantyStatus.EXPIRED -> StatusPill(label = "Warranty Ended", color = vaultColors.statusExpired)
                WarrantyStatus.NOT_TRACKED -> {}
            }
        }
    }
}
