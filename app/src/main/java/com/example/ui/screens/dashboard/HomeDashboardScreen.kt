package com.example.ui.screens.dashboard

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.model.ExpiryStatus
import com.example.model.MedicineRecord
import com.example.model.PriorityTier
import com.example.model.PurchaseRecord
import com.example.model.ReturnStatus
import com.example.model.UiState
import com.example.model.VaultModule
import com.example.model.VaultNotification
import com.example.model.WarrantyStatus
import com.example.notifications.ExpiringSoonReport
import com.example.notifications.VaultReminderScheduler
import com.example.priority.PriorityCenterReport
import com.example.priority.PriorityItem
import com.example.ui.components.GlassVaultCard
import com.example.ui.components.MetricStatTile
import com.example.ui.components.PriorityRecordCard
import com.example.ui.components.StatusPill
import com.example.ui.components.VaultAnimatedEntrance
import com.example.ui.components.VaultAtmosphereBackground
import com.example.ui.components.VaultEmptyState
import com.example.ui.components.VaultGlassOutlinedButton
import com.example.ui.components.VaultGlassSearchBar
import com.example.ui.components.VaultGradientButton
import com.example.ui.components.VaultoraLogoBadge
import com.example.ui.components.tierColor
import com.example.ui.components.vaultSharedBounds
import com.example.ui.theme.LocalVaultColors
import com.example.util.DateAndCurrencyUtils
import com.example.viewmodel.VaultViewModel
import java.util.Calendar

private enum class DashboardSearchFilter(val label: String) {
    ALL("All"),
    RECEIPTS("Receipts"),
    WARRANTIES("Warranties"),
    MEDICINES("Medicines")
}

@Composable
fun HomeDashboardScreen(
    viewModel: VaultViewModel,
    bottomContentPadding: Dp = 0.dp,
    familyGroupName: String? = null,
    familyMemberCount: Int = 0,
    familySharedCount: Int = 0,
    onOpenFamilyVault: () -> Unit = {},
    onOpenPurchaseVault: () -> Unit,
    onOpenMedicineVault: () -> Unit,
    onScanReceipt: () -> Unit,
    onAddReceipt: () -> Unit,
    onAddMedicine: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenPriorityCenter: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenPurchaseDetail: (String) -> Unit,
    onOpenMedicineDetail: (String) -> Unit
) {
    val context = LocalContext.current
    val profileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val purchasesState by viewModel.purchasesState.collectAsStateWithLifecycle()
    val medicinesState by viewModel.medicinesState.collectAsStateWithLifecycle()
    val notifications by viewModel.notificationsState.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadNotificationsCount.collectAsStateWithLifecycle()
    val expiringSoonReport by viewModel.expiringSoon7DaysState.collectAsStateWithLifecycle()
    val priorityReport by viewModel.priorityCenterState.collectAsStateWithLifecycle()
    val vaultColors = LocalVaultColors.current

    val profile = (profileState as? UiState.Success)?.data
    val purchases = (purchasesState as? UiState.Success)?.data.orEmpty()
    val medicines = (medicinesState as? UiState.Success)?.data.orEmpty()

    var hasPushPermission by remember {
        mutableStateOf(VaultReminderScheduler.hasNotificationPermission(context))
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPushPermission = granted
        if (granted) {
            VaultReminderScheduler.checkAndNotifyExpiringSoon(
                context = context,
                purchases = purchases,
                medicines = medicines,
                profile = profile,
                forceAlert = true
            )
            viewModel.showActionMessage("Push expiry alerts enabled.")
        } else {
            viewModel.showActionMessage("Notification permission denied in system settings.")
        }
    }

    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    val userName = profile?.fullName?.takeIf { it.isNotBlank() } ?: "Vault Owner"

    val activeWarranties = purchases.count {
        val status = DateAndCurrencyUtils.warrantyStatus(it)
        status == WarrantyStatus.ACTIVE || status == WarrantyStatus.EXPIRING_SOON
    }
    val upcomingReturns = purchases.count {
        val status = DateAndCurrencyUtils.returnStatus(it)
        status == ReturnStatus.ELIGIBLE || status == ReturnStatus.CLOSING_SOON
    }
    val expiringSoonMeds = medicines.count {
        DateAndCurrencyUtils.medicineExpiryStatus(it) == ExpiryStatus.EXPIRING_SOON
    }
    val expiredMeds = medicines.count {
        DateAndCurrencyUtils.medicineExpiryStatus(it) == ExpiryStatus.EXPIRED
    }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchFilter by rememberSaveable { mutableStateOf(DashboardSearchFilter.ALL) }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }

    val availableCategories = remember(purchases) {
        purchases.map { it.category.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    val trimmedQuery = searchQuery.trim()
    val isSearchActive = trimmedQuery.isNotEmpty() ||
        searchFilter != DashboardSearchFilter.ALL ||
        selectedCategory != null

    val matchingPurchases = remember(purchases, trimmedQuery, searchFilter, selectedCategory) {
        if (searchFilter == DashboardSearchFilter.MEDICINES) {
            emptyList()
        } else {
            purchases.filter { p ->
                val hasWarranty = p.warrantyExpiryMillis != null || (p.warrantyDurationMonths ?: 0L) > 0L
                val wStatus = DateAndCurrencyUtils.warrantyStatus(p)
                val matchesScope = searchFilter != DashboardSearchFilter.WARRANTIES || hasWarranty
                val matchesCat = selectedCategory == null || p.category.equals(selectedCategory, ignoreCase = true)
                val matchesQuery = trimmedQuery.isEmpty() ||
                    p.productName.contains(trimmedQuery, ignoreCase = true) ||
                    p.brand.contains(trimmedQuery, ignoreCase = true) ||
                    p.merchantName.contains(trimmedQuery, ignoreCase = true) ||
                    p.category.contains(trimmedQuery, ignoreCase = true) ||
                    p.invoiceNumber.contains(trimmedQuery, ignoreCase = true) ||
                    (p.notes?.contains(trimmedQuery, ignoreCase = true) == true) ||
                    ("receipt purchase invoice".contains(trimmedQuery, ignoreCase = true) && trimmedQuery.length >= 3) ||
                    ("warranty".contains(trimmedQuery, ignoreCase = true) && trimmedQuery.length >= 3 && hasWarranty) ||
                    (wStatus.name.replace('_', ' ').contains(trimmedQuery, ignoreCase = true) && trimmedQuery.length >= 3)
                matchesScope && matchesCat && matchesQuery
            }
        }
    }

    val matchingMedicines = remember(medicines, trimmedQuery, searchFilter, selectedCategory) {
        if (searchFilter == DashboardSearchFilter.RECEIPTS ||
            searchFilter == DashboardSearchFilter.WARRANTIES ||
            selectedCategory != null
        ) {
            emptyList()
        } else {
            medicines.filter { m ->
                val expStatus = DateAndCurrencyUtils.medicineExpiryStatus(m)
                trimmedQuery.isEmpty() ||
                    m.medicineName.contains(trimmedQuery, ignoreCase = true) ||
                    m.batchNumber.contains(trimmedQuery, ignoreCase = true) ||
                    (m.storageNote?.contains(trimmedQuery, ignoreCase = true) == true) ||
                    ("medicine meds pharmacy tablet".contains(trimmedQuery, ignoreCase = true) && trimmedQuery.length >= 3) ||
                    (expStatus.name.replace('_', ' ').contains(trimmedQuery, ignoreCase = true) && trimmedQuery.length >= 3)
            }
        }
    }

    VaultAtmosphereBackground(
        accentColor = vaultColors.purchaseAccent,
        secondaryAccent = vaultColors.medicineAccent
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("home_dashboard_screen"),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 16.dp,
                bottom = 16.dp + bottomContentPadding
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        VaultoraLogoBadge(size = 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "$greeting,",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = userName,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onOpenSearch,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("home_search_button")
                        ) {
                            Icon(Icons.Outlined.Search, contentDescription = "Global Search")
                        }
                        IconButton(
                            onClick = onOpenNotifications,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("home_notifications_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadCount > 0) {
                                        Badge(containerColor = vaultColors.statusWarning) {
                                            Text(
                                                text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                                                color = Color.Black
                                            )
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Outlined.Notifications, contentDescription = "Notifications")
                            }
                        }
                        Surface(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onOpenProfile)
                                .testTag("home_profile_button"),
                            shape = CircleShape,
                            color = vaultColors.goldContainer,
                            border = BorderStroke(1.dp, vaultColors.goldAccent.copy(alpha = 0.5f))
                        ) {
                            if (!profile?.photoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = profile?.photoUrl,
                                    contentDescription = "Profile",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.Person,
                                        contentDescription = "Profile",
                                        tint = vaultColors.goldAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Top Dashboard Search Bar (Receipts, Warranties & Medicines by name or category)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    VaultGlassSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholder = "Search receipts, warranties, medicines or category...",
                        accentColors = vaultColors.brandButtonGradient,
                        trailingAction = {
                            IconButton(
                                onClick = onOpenSearch,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("dashboard_open_unified_search")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Tune,
                                    contentDescription = "Open full search",
                                    tint = vaultColors.goldAccent,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        },
                        testTag = "home_dashboard_search_input"
                    )

                    // Quick filter & category chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DashboardSearchFilter.entries.forEach { filterOption ->
                            val isSelected = searchFilter == filterOption && selectedCategory == null
                            val chipAccent = when (filterOption) {
                                DashboardSearchFilter.ALL -> vaultColors.goldAccent
                                DashboardSearchFilter.RECEIPTS -> vaultColors.purchaseAccent
                                DashboardSearchFilter.WARRANTIES -> MaterialTheme.colorScheme.primary
                                DashboardSearchFilter.MEDICINES -> vaultColors.medicineAccent
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    searchFilter = filterOption
                                    selectedCategory = null
                                },
                                label = { Text(filterOption.label, maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = chipAccent.copy(alpha = 0.22f),
                                    selectedLabelColor = chipAccent
                                ),
                                modifier = Modifier.testTag("home_filter_${filterOption.name.lowercase()}")
                            )
                        }

                        availableCategories.forEach { category ->
                            val isCatSelected = selectedCategory == category
                            FilterChip(
                                selected = isCatSelected,
                                onClick = {
                                    if (isCatSelected) {
                                        selectedCategory = null
                                    } else {
                                        selectedCategory = category
                                        if (searchFilter == DashboardSearchFilter.MEDICINES) {
                                            searchFilter = DashboardSearchFilter.ALL
                                        }
                                    }
                                },
                                label = { Text(category, maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = vaultColors.purchaseAccent.copy(alpha = 0.22f),
                                    selectedLabelColor = vaultColors.purchaseAccent
                                ),
                                modifier = Modifier.testTag("home_category_chip_$category")
                            )
                        }
                    }

                    // Live Instant Results right under the top search bar
                    AnimatedVisibility(
                        visible = isSearchActive,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        val totalFound = matchingPurchases.size + matchingMedicines.size
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_search_results_section")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$totalFound matching ${if (totalFound == 1) "item" else "items"}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = vaultColors.goldAccent
                                )
                                TextButton(
                                    onClick = {
                                        searchQuery = ""
                                        searchFilter = DashboardSearchFilter.ALL
                                        selectedCategory = null
                                    }
                                ) {
                                    Text("Reset", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            if (totalFound == 0) {
                                VaultEmptyState(
                                    icon = Icons.Outlined.Search,
                                    title = "No matching items",
                                    subtitle = "Try searching by product name, category (e.g. Electronics), warranty, or medicine name.",
                                    accent = vaultColors.goldAccent,
                                    actionLabel = "Clear Search",
                                    onAction = {
                                        searchQuery = ""
                                        searchFilter = DashboardSearchFilter.ALL
                                        selectedCategory = null
                                    }
                                )
                            } else {
                                matchingPurchases.take(6).forEach { purchase ->
                                    DashboardPurchaseSearchResultCard(
                                        productName = purchase.productName,
                                        merchantName = purchase.merchantName,
                                        category = purchase.category,
                                        priceLabel = DateAndCurrencyUtils.formatInr(purchase.purchasePriceInr),
                                        warrantyLabel = purchase.warrantyExpiryMillis?.let { exp ->
                                            val wStatus = DateAndCurrencyUtils.warrantyStatus(purchase)
                                            val prefix = when (wStatus) {
                                                WarrantyStatus.EXPIRED -> "Warranty Expired"
                                                WarrantyStatus.EXPIRING_SOON -> "Warranty ${DateAndCurrencyUtils.formatCountdown(DateAndCurrencyUtils.daysFromNow(exp))}"
                                                else -> "Warranty ${DateAndCurrencyUtils.formatDate(exp)}"
                                            }
                                            prefix
                                        },
                                        warrantyExpired = DateAndCurrencyUtils.warrantyStatus(purchase) == WarrantyStatus.EXPIRED,
                                        modifier = Modifier.vaultSharedBounds("purchase_card_${purchase.id}"),
                                        onClick = { onOpenPurchaseDetail(purchase.id) }
                                    )
                                }

                                matchingMedicines.take(6).forEach { med ->
                                    val medStatus = DateAndCurrencyUtils.medicineExpiryStatus(med)
                                    val medColor = when (medStatus) {
                                        ExpiryStatus.EXPIRED -> vaultColors.statusExpired
                                        ExpiryStatus.EXPIRING_SOON -> vaultColors.statusWarning
                                        ExpiryStatus.VALID -> vaultColors.medicineAccent
                                    }
                                    val medTag = when (medStatus) {
                                        ExpiryStatus.EXPIRED -> "EXPIRED"
                                        ExpiryStatus.EXPIRING_SOON -> "SOON • ${DateAndCurrencyUtils.formatCountdown(DateAndCurrencyUtils.daysFromNow(med.expiryDateMillis))}"
                                        ExpiryStatus.VALID -> "EXP ${DateAndCurrencyUtils.formatDate(med.expiryDateMillis)}"
                                    }
                                    DashboardMedicineSearchResultCard(
                                        medicineName = med.medicineName,
                                        batch = med.batchNumber.ifBlank { "N/A" },
                                        expiryTag = medTag,
                                        statusColor = medColor,
                                        modifier = Modifier.vaultSharedBounds("medicine_card_${med.id}"),
                                        onClick = { onOpenMedicineDetail(med.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Error / Loading banner if Firestore encountered an issue
            if (purchasesState is UiState.Loading || medicinesState is UiState.Loading) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Syncing your vaults...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 7-Day Expiring Soon Alert Banner (Medicines & Warranties)
            if (expiringSoonReport.hasAlerts) {
                item {
                    VaultAnimatedEntrance(delayMillis = 25) {
                        ExpiringSoon7DayBanner(
                            report = expiringSoonReport,
                            hasPushPermission = hasPushPermission,
                            onEnablePush = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onOpenNotifications = onOpenNotifications,
                            onOpenItem = { item ->
                                if (item.module == VaultModule.MEDICINE) {
                                    onOpenMedicineDetail(item.id)
                                } else {
                                    onOpenPurchaseDetail(item.id)
                                }
                            }
                        )
                    }
                }
            }

            // Smart Priority Engine - Top Priority Center Card
            item {
                DashboardPriorityCenterSection(
                    report = priorityReport,
                    onOpenPriorityCenter = onOpenPriorityCenter,
                    onOpenItem = { item ->
                        if (item.module == VaultModule.PURCHASE) {
                            onOpenPurchaseDetail(item.id)
                        } else {
                            onOpenMedicineDetail(item.id)
                        }
                    },
                    onCompleteItem = { item ->
                        if (item.module == VaultModule.PURCHASE) {
                            viewModel.setPurchaseCompleted(item.id, true)
                        } else {
                            viewModel.setMedicineCompleted(item.id, true)
                        }
                    }
                )
            }

            // Overview KPI 2x2 Grid
            item {
                VaultAnimatedEntrance(delayMillis = 40) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Vault Overview",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricStatTile(
                            label = "Stored Purchases",
                            value = purchases.size.toString(),
                            icon = Icons.Outlined.ReceiptLong,
                            accent = vaultColors.purchaseAccent,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenPurchaseVault
                        )
                        MetricStatTile(
                            label = "Active Warranties",
                            value = activeWarranties.toString(),
                            icon = Icons.Outlined.Shield,
                            accent = vaultColors.goldAccent,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenPurchaseVault
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricStatTile(
                            label = "Upcoming Returns",
                            value = upcomingReturns.toString(),
                            icon = Icons.Outlined.EventAvailable,
                            accent = vaultColors.statusWarning,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenPurchaseVault
                        )
                        MetricStatTile(
                            label = "Stored Medicines",
                            value = medicines.size.toString(),
                            icon = Icons.Outlined.Medication,
                            accent = vaultColors.medicineAccent,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenMedicineVault
                        )
                    }
                }
                }
            }

            // Quick Actions
            item {
                VaultAnimatedEntrance(delayMillis = 100) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Quick Actions",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickActionButton(
                            label = "Scan Receipt",
                            icon = Icons.Outlined.DocumentScanner,
                            gradientColors = vaultColors.purchaseButtonGradient,
                            onClick = onScanReceipt,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_scan_receipt")
                        )
                        QuickActionButton(
                            label = "Add Receipt",
                            icon = Icons.Outlined.Add,
                            gradientColors = vaultColors.goldButtonGradient,
                            onClick = onAddReceipt,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_add_receipt")
                        )
                        QuickActionButton(
                            label = "Add Medicine",
                            icon = Icons.Outlined.Medication,
                            gradientColors = vaultColors.medicineButtonGradient,
                            onClick = onAddMedicine,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_add_medicine")
                        )
                    }
                }
                }
            }

            // Purchase Vault Module Card (Purple Glass Identity)
            item {
                val totalValue = purchases.sumOf { it.purchasePriceInr }
                VaultAnimatedEntrance(delayMillis = 160) {
                    GlassVaultCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .vaultSharedBounds("module_card_purchase")
                            .testTag("dashboard_purchase_vault_card"),
                        accentBorder = vaultColors.purchaseAccent.copy(alpha = 0.72f),
                        containerBrush = Brush.linearGradient(
                            colors = listOf(
                                vaultColors.purchaseContainer.copy(alpha = if (vaultColors.isDark) 0.78f else 0.85f),
                                vaultColors.glassSurface,
                                vaultColors.glassSheenEnd
                            )
                        ),
                        onClick = onOpenPurchaseVault
                    ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            StatusPill(label = "MODULE A • PURCHASES", color = vaultColors.purchaseAccent)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Purchase Vault",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Total tracked value: ${DateAndCurrencyUtils.formatInr(totalValue)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(vaultColors.purchaseAccent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ReceiptLong,
                                contentDescription = "Purchase Vault",
                                tint = vaultColors.purchaseAccent,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ModuleMetricPair("Receipts", purchases.size.toString())
                        ModuleMetricPair("Warranties", activeWarranties.toString())
                        ModuleMetricPair("Returns", upcomingReturns.toString())
                    }

                    Spacer(Modifier.height(14.dp))

                    VaultGradientButton(
                        onClick = onOpenPurchaseVault,
                        colors = vaultColors.purchaseButtonGradient,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("open_purchase_vault_button")
                    ) {
                        Text("Open Purchase Vault")
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    }
                }
            }

            // Medicine Vault Module Card (Green Glass Identity)
            item {
                VaultAnimatedEntrance(delayMillis = 220) {
                    GlassVaultCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .vaultSharedBounds("module_card_medicine")
                            .testTag("dashboard_medicine_vault_card"),
                        accentBorder = vaultColors.medicineAccent.copy(alpha = 0.72f),
                        containerBrush = Brush.linearGradient(
                            colors = listOf(
                                vaultColors.medicineContainer.copy(alpha = if (vaultColors.isDark) 0.78f else 0.85f),
                                vaultColors.glassSurface,
                                vaultColors.medicineAccent.copy(alpha = 0.12f)
                            )
                        ),
                        onClick = onOpenMedicineVault
                    ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            StatusPill(label = "MODULE B • MEDICINES", color = vaultColors.medicineAccent)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Medicine Vault",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Storage & expiry tracking only",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(vaultColors.medicineAccent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Medication,
                                contentDescription = "Medicine Vault",
                                tint = vaultColors.medicineAccent,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ModuleMetricPair("Medicines", medicines.size.toString())
                        ModuleMetricPair("Expiring Soon", expiringSoonMeds.toString())
                        ModuleMetricPair("Expired", expiredMeds.toString())
                    }

                    Spacer(Modifier.height(14.dp))

                    VaultGradientButton(
                        onClick = onOpenMedicineVault,
                        colors = vaultColors.medicineButtonGradient,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("open_medicine_vault_button")
                    ) {
                        Text("Open Medicine Vault")
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    }
                }
            }

            // Optional Family Vault Card
            item {
                VaultAnimatedEntrance(delayMillis = 250) {
                    GlassVaultCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dashboard_family_vault_card"),
                        accentBorder = vaultColors.goldAccent.copy(alpha = 0.55f),
                        onClick = onOpenFamilyVault
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                StatusPill(label = "OPTIONAL • FAMILY VAULT", color = vaultColors.goldAccent)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = familyGroupName ?: "Family Vault Sharing",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = if (familyGroupName != null) {
                                        "$familyMemberCount member(s) • $familySharedCount shared record(s)"
                                    } else {
                                        "Share selected receipts, warranties & medicines via invite code"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(vaultColors.goldContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Group,
                                    contentDescription = "Family Vault",
                                    tint = vaultColors.goldAccent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Upcoming Alerts across both modules
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Upcoming Alerts",
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(onClick = onOpenNotifications) {
                        Text("See all", color = vaultColors.goldAccent)
                    }
                }

                if (notifications.isEmpty()) {
                    VaultEmptyState(
                        icon = Icons.Outlined.Notifications,
                        title = "No urgent deadlines",
                        subtitle = "Return windows, warranty expiries, and medicine alerts will surface here automatically.",
                        accent = vaultColors.goldAccent
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(notifications.take(6), key = { it.alertKey }) { alert ->
                            DashboardAlertCard(
                                alert = alert,
                                onClick = {
                                    if (alert.module == VaultModule.PURCHASE) {
                                        onOpenPurchaseDetail(alert.targetId)
                                    } else {
                                        onOpenMedicineDetail(alert.targetId)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Recent Activity
            item {
                Text(
                    text = "Recent Activity",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            val recentCombined = buildRecentActivity(
                purchases = purchases,
                medicines = medicines,
                purchaseAccent = vaultColors.purchaseAccent,
                medicineAccent = vaultColors.medicineAccent
            )
            if (recentCombined.isEmpty()) {
                item {
                    VaultEmptyState(
                        icon = Icons.Outlined.ReceiptLong,
                        title = "Your vault is ready",
                        subtitle = "Scan a receipt or add a medicine entry to start building your encrypted archive.",
                        accent = vaultColors.purchaseAccent,
                        actionLabel = "Scan First Receipt",
                        onAction = onScanReceipt
                    )
                }
            } else {
                items(recentCombined.take(6), key = { it.key }) { activity ->
                    val sharedKey = if (activity.isPurchase) "purchase_card_${activity.id}" else "medicine_card_${activity.id}"
                    GlassVaultCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .vaultSharedBounds(sharedKey),
                        accentBorder = activity.accent.copy(alpha = 0.38f),
                        onClick = {
                            if (activity.isPurchase) onOpenPurchaseDetail(activity.id)
                            else onOpenMedicineDetail(activity.id)
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(activity.accent.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (activity.isPurchase) Icons.Outlined.ReceiptLong else Icons.Outlined.Medication,
                                        contentDescription = null,
                                        tint = activity.accent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = activity.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = activity.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            StatusPill(label = activity.tag, color = activity.accent)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun QuickActionButton(
    label: String,
    icon: ImageVector,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColors = if (gradientColors.size >= 2) gradientColors else listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary
    )
    GlassVaultCard(
        modifier = modifier.minimumInteractiveComponentSize(),
        accentBorder = activeColors[1].copy(alpha = 0.58f),
        onClick = onClick
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(colors = activeColors))
                    .border(1.dp, Color.White.copy(alpha = 0.52f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DashboardPurchaseSearchResultCard(
    productName: String,
    merchantName: String,
    category: String,
    priceLabel: String,
    warrantyLabel: String?,
    warrantyExpired: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val vaultColors = LocalVaultColors.current
    GlassVaultCard(
        modifier = modifier.fillMaxWidth(),
        accentBorder = vaultColors.purchaseAccent.copy(alpha = 0.52f),
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
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                colors = vaultColors.purchaseButtonGradient.map { it.copy(alpha = 0.24f) }
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ReceiptLong,
                        contentDescription = null,
                        tint = vaultColors.purchaseAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = productName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$merchantName • $category",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = priceLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = vaultColors.purchaseAccent
                )
                if (!warrantyLabel.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    StatusPill(
                        label = warrantyLabel,
                        color = if (warrantyExpired) vaultColors.statusExpired else vaultColors.statusValid
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardMedicineSearchResultCard(
    medicineName: String,
    batch: String,
    expiryTag: String,
    statusColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val vaultColors = LocalVaultColors.current
    GlassVaultCard(
        modifier = modifier.fillMaxWidth(),
        accentBorder = statusColor.copy(alpha = 0.52f),
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
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                colors = vaultColors.medicineButtonGradient.map { it.copy(alpha = 0.24f) }
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Medication,
                        contentDescription = null,
                        tint = vaultColors.medicineAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = medicineName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Medicine • Batch $batch",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            StatusPill(label = expiryTag, color = statusColor)
        }
    }
}

@Composable
private fun ModuleMetricPair(label: String, value: String) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DashboardAlertCard(
    alert: VaultNotification,
    onClick: () -> Unit
) {
    val vaultColors = LocalVaultColors.current
    val accent = when {
        alert.daysDelta < 0L -> vaultColors.statusExpired
        alert.module == VaultModule.PURCHASE -> vaultColors.purchaseAccent
        else -> vaultColors.medicineAccent
    }
    GlassVaultCard(
        modifier = Modifier.width(248.dp),
        accentBorder = accent.copy(alpha = 0.45f),
        onClick = onClick
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            StatusPill(
                label = if (alert.module == VaultModule.PURCHASE) "PURCHASE" else "MEDICINE",
                color = accent
            )
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = if (alert.daysDelta < 0L) vaultColors.statusExpired else vaultColors.statusWarning,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = alert.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = alert.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private data class RecentActivityRow(
    val key: String,
    val id: String,
    val isPurchase: Boolean,
    val title: String,
    val subtitle: String,
    val tag: String,
    val accent: Color,
    val sortTime: Long
)

private fun buildRecentActivity(
    purchases: List<PurchaseRecord>,
    medicines: List<MedicineRecord>,
    purchaseAccent: Color,
    medicineAccent: Color
): List<RecentActivityRow> {
    val pRows = purchases.map { p ->
        RecentActivityRow(
            key = "p_${p.id}",
            id = p.id,
            isPurchase = true,
            title = p.productName,
            subtitle = "${p.merchantName} • ${DateAndCurrencyUtils.formatDate(p.purchaseDateMillis)}",
            tag = DateAndCurrencyUtils.formatInr(p.purchasePriceInr),
            accent = purchaseAccent,
            sortTime = p.createdAt?.toDate()?.time ?: p.purchaseDateMillis
        )
    }
    val mRows = medicines.map { m ->
        val status = DateAndCurrencyUtils.medicineExpiryStatus(m)
        val label = when (status) {
            ExpiryStatus.EXPIRED -> "EXPIRED"
            ExpiryStatus.EXPIRING_SOON -> "SOON"
            ExpiryStatus.VALID -> "VALID"
        }
        RecentActivityRow(
            key = "m_${m.id}",
            id = m.id,
            isPurchase = false,
            title = m.medicineName,
            subtitle = "Batch ${m.batchNumber.ifBlank { "N/A" }} • Exp ${DateAndCurrencyUtils.formatDate(m.expiryDateMillis)}",
            tag = label,
            accent = medicineAccent,
            sortTime = m.createdAt?.toDate()?.time ?: m.expiryDateMillis
        )
    }
    return (pRows + mRows).sortedByDescending { it.sortTime }
}

@Composable
private fun ExpiringSoon7DayBanner(
    report: ExpiringSoonReport,
    hasPushPermission: Boolean,
    onEnablePush: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenItem: (com.example.notifications.ExpiringSoonItem) -> Unit
) {
    val vaultColors = LocalVaultColors.current
    val medCount = report.medicineItems.size
    val warrantyCount = report.warrantyItems.size
    val summaryText = buildList {
        if (medCount > 0) add("$medCount ${if (medCount == 1) "medicine" else "medicines"}")
        if (warrantyCount > 0) add("$warrantyCount ${if (warrantyCount == 1) "warranty" else "warranties"}")
    }.joinToString(" • ")

    GlassVaultCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_expiring_soon_banner"),
        accentBorder = vaultColors.statusWarning.copy(alpha = 0.78f),
        containerBrush = Brush.linearGradient(
            colors = listOf(
                vaultColors.statusWarning.copy(alpha = if (vaultColors.isDark) 0.22f else 0.16f),
                vaultColors.glassSurface,
                vaultColors.goldContainer.copy(alpha = 0.4f)
            )
        ),
        onClick = onOpenNotifications
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
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(vaultColors.statusWarning.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = "Expiring Soon",
                        tint = vaultColors.statusWarning,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Expiring Soon (≤7 Days)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = vaultColors.statusWarning
                    )
                    Text(
                        text = "$summaryText nearing expiration",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            StatusPill(
                label = "${report.items.size} URGENT",
                color = vaultColors.statusWarning
            )
        }

        Spacer(Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            report.items.take(3).forEach { item ->
                val moduleColor = if (item.module == VaultModule.MEDICINE) {
                    vaultColors.medicineAccent
                } else {
                    vaultColors.purchaseAccent
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = vaultColors.glassSurface,
                    border = BorderStroke(1.dp, moduleColor.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenItem(item) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (item.module == VaultModule.MEDICINE) {
                                    Icons.Outlined.Medication
                                } else {
                                    Icons.Outlined.Shield
                                },
                                contentDescription = null,
                                tint = moduleColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = DateAndCurrencyUtils.formatCountdown(item.daysRemaining),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (item.daysRemaining <= 1L) vaultColors.statusExpired else vaultColors.statusWarning
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!hasPushPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                VaultGlassOutlinedButton(
                    onClick = onEnablePush,
                    colors = vaultColors.goldButtonGradient,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("enable_push_alerts_button")
                ) {
                    Text("Enable Push Alerts", maxLines = 1)
                }
            }
            VaultGradientButton(
                onClick = onOpenNotifications,
                colors = vaultColors.goldButtonGradient,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("review_expiring_soon_button")
            ) {
                Text("Review Alerts", maxLines = 1)
            }
        }
    }
}

@Composable
private fun DashboardPriorityCenterSection(
    report: PriorityCenterReport,
    onOpenPriorityCenter: () -> Unit,
    onOpenItem: (PriorityItem) -> Unit,
    onCompleteItem: (PriorityItem) -> Unit
) {
    val vaultColors = LocalVaultColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_priority_center_section")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
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
                Column {
                    Text(
                        text = "Priority Center",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Most important deadlines always on top",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(
                onClick = onOpenPriorityCenter,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("home_open_priority_center")
            ) {
                Text("Open All (${report.activeCount})", color = vaultColors.goldAccent)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PriorityTier.entries.forEach { tier ->
                StatusPill(
                    label = "${tier.label} ${report.countForTier(tier)}",
                    color = tierColor(tier, vaultColors)
                )
            }
        }

        if (report.activeItems.isEmpty()) {
            GlassVaultCard(
                modifier = Modifier.fillMaxWidth(),
                accentBorder = vaultColors.goldAccent.copy(alpha = 0.38f),
                onClick = onOpenPriorityCenter
            ) {
                Text(
                    text = "Everything is up to date",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Active receipts, warranties, and medicines are ranked automatically here offline & online.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            report.activeItems.take(3).forEach { item ->
                PriorityRecordCard(
                    item = item,
                    onOpen = { onOpenItem(item) },
                    onToggleComplete = { onCompleteItem(item) }
                )
            }
            VaultGlassOutlinedButton(
                onClick = onOpenPriorityCenter,
                colors = vaultColors.brandButtonGradient,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("home_view_priority_center_button")
            ) {
                Text("Open Priority Center")
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}


