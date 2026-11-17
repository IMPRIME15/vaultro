package com.example.ui.screens.shared

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.AlertKind
import com.example.model.UiState
import com.example.model.VaultModule
import com.example.model.VaultNotification
import com.example.notifications.VaultReminderScheduler
import com.example.ui.components.GlassVaultCard
import com.example.ui.components.PriorityTierBadge
import com.example.ui.components.StatusPill
import com.example.ui.components.VaultAnimatedEntrance
import com.example.ui.components.VaultAtmosphereBackground
import com.example.ui.components.VaultEmptyState
import com.example.ui.components.VaultGlassOutlinedButton
import com.example.ui.components.VaultGradientButton
import com.example.ui.components.VaultTopBar
import com.example.ui.theme.LocalVaultColors
import com.example.util.DateAndCurrencyUtils
import com.example.viewmodel.VaultViewModel

private enum class NotificationFilter(val label: String) {
    ALL("All"),
    EXPIRING_7D("≤7 Days Urgent"),
    PURCHASE("Purchase Alerts"),
    MEDICINE("Medicine Alerts")
}

@Composable
fun NotificationCenterScreen(
    viewModel: VaultViewModel,
    onBack: () -> Unit,
    onOpenPurchaseDetail: (String) -> Unit,
    onOpenMedicineDetail: (String) -> Unit,
    onOpenPriorityCenter: () -> Unit,
    onOpenPreferences: () -> Unit
) {
    val context = LocalContext.current
    val notifications by viewModel.notificationsState.collectAsStateWithLifecycle()
    val expiringSoonReport by viewModel.expiringSoon7DaysState.collectAsStateWithLifecycle()
    val profileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val purchasesState by viewModel.purchasesState.collectAsStateWithLifecycle()
    val medicinesState by viewModel.medicinesState.collectAsStateWithLifecycle()

    val profile = (profileState as? UiState.Success)?.data
    val purchases = (purchasesState as? UiState.Success)?.data.orEmpty()
    val medicines = (medicinesState as? UiState.Success)?.data.orEmpty()

    val vaultColors = LocalVaultColors.current
    var filter by rememberSaveable { mutableStateOf(NotificationFilter.ALL) }
    var lastCheckedMillis by remember {
        mutableStateOf(VaultReminderScheduler.getLastCheckedMillis(context))
    }

    val runExpiryCheck: () -> Unit = {
        val report = VaultReminderScheduler.checkAndNotifyExpiringSoon(
            context = context,
            purchases = purchases,
            medicines = medicines,
            profile = profile,
            forceAlert = true
        )
        lastCheckedMillis = report.evaluatedAtMillis
        if (report.hasAlerts) {
            val medCount = report.medicineItems.size
            val warrantyCount = report.warrantyItems.size
            viewModel.showActionMessage(
                "Expiring Soon alert sent: $medCount medicine(s) & $warrantyCount warranty(s) within 7 days."
            )
        } else {
            viewModel.showActionMessage("Checked ${medicines.size} medicines & ${purchases.size} purchases — none expire within 7 days.")
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            runExpiryCheck()
        } else {
            viewModel.showActionMessage("Notification permission denied — alerts remain visible here in-app.")
        }
    }

    val filteredNotifications = when (filter) {
        NotificationFilter.ALL -> notifications
        NotificationFilter.EXPIRING_7D -> notifications.filter { it.isUrgent7Days }
        NotificationFilter.PURCHASE -> notifications.filter { it.module == VaultModule.PURCHASE }
        NotificationFilter.MEDICINE -> notifications.filter { it.module == VaultModule.MEDICINE }
    }

    val purchaseAlerts = filteredNotifications.filter { it.module == VaultModule.PURCHASE }
    val medicineAlerts = filteredNotifications.filter { it.module == VaultModule.MEDICINE }

    val showPurchases = filter != NotificationFilter.MEDICINE
    val showMedicines = filter != NotificationFilter.PURCHASE

    VaultAtmosphereBackground(
        accentColor = vaultColors.goldAccent,
        secondaryAccent = vaultColors.purchaseAccent
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                VaultTopBar(
                    title = "Notification Center",
                    subtitle = "${notifications.count { !it.isRead }} unread • ${expiringSoonReport.items.size} within 7d",
                    onBack = onBack,
                    actions = {
                        IconButton(
                            onClick = { viewModel.markAllNotificationsRead() },
                            modifier = Modifier.testTag("mark_all_read_button")
                        ) {
                            Icon(Icons.Outlined.DoneAll, contentDescription = "Mark all as read")
                        }
                        IconButton(
                            onClick = onOpenPreferences,
                            modifier = Modifier.testTag("notification_preferences_button")
                        ) {
                            Icon(Icons.Outlined.Tune, contentDescription = "Notification preferences")
                        }
                    }
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("notification_center_screen"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 7-Day Expiring Soon Scanner Card
                item {
                    GlassVaultCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("expiring_soon_7day_card"),
                        accentBorder = if (expiringSoonReport.hasAlerts) {
                            vaultColors.statusWarning.copy(alpha = 0.7f)
                        } else {
                            vaultColors.medicineAccent.copy(alpha = 0.45f)
                        },
                        containerBrush = Brush.linearGradient(
                            colors = listOf(
                                if (expiringSoonReport.hasAlerts) {
                                    vaultColors.statusWarning.copy(alpha = if (vaultColors.isDark) 0.18f else 0.13f)
                                } else {
                                    vaultColors.medicineContainer.copy(alpha = 0.45f)
                                },
                                vaultColors.glassSurface
                            )
                        )
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
                                        .background(
                                            (if (expiringSoonReport.hasAlerts) vaultColors.statusWarning else vaultColors.medicineAccent)
                                                .copy(alpha = 0.18f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (expiringSoonReport.hasAlerts) Icons.Outlined.WarningAmber else Icons.Outlined.NotificationsActive,
                                        contentDescription = "7-Day Expiring Soon Monitor",
                                        tint = if (expiringSoonReport.hasAlerts) vaultColors.statusWarning else vaultColors.medicineAccent
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "7-Day Expiring Soon Check",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val summary = if (expiringSoonReport.hasAlerts) {
                                        "${expiringSoonReport.medicineItems.size} medicine(s) • ${expiringSoonReport.warrantyItems.size} warranty(s) expire ≤7d"
                                    } else {
                                        "No medicines or warranties expire within 7 days"
                                    }
                                    Text(
                                        text = summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (lastCheckedMillis > 0L) {
                                        Text(
                                            text = "Last checked: ${DateAndCurrencyUtils.formatDate(lastCheckedMillis)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        VaultGradientButton(
                            onClick = {
                                if (VaultReminderScheduler.hasNotificationPermission(context)) {
                                    runExpiryCheck()
                                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    runExpiryCheck()
                                }
                            },
                            colors = vaultColors.goldButtonGradient,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("check_expiring_soon_button")
                        ) {
                            Icon(Icons.Outlined.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Check Expiring Soon Now")
                        }
                        Spacer(Modifier.height(8.dp))
                        VaultGlassOutlinedButton(
                            onClick = onOpenPriorityCenter,
                            colors = vaultColors.purchaseButtonGradient,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("notifications_open_priority_center")
                        ) {
                            Text("Open Smart Priority Center")
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NotificationFilter.entries.forEach { option ->
                            val chipColor = when (option) {
                                NotificationFilter.ALL -> vaultColors.goldAccent
                                NotificationFilter.EXPIRING_7D -> vaultColors.statusWarning
                                NotificationFilter.PURCHASE -> vaultColors.purchaseAccent
                                NotificationFilter.MEDICINE -> vaultColors.medicineAccent
                            }
                            FilterChip(
                                selected = filter == option,
                                onClick = { filter = option },
                                label = { Text(option.label, maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = chipColor.copy(alpha = 0.22f),
                                    selectedLabelColor = chipColor
                                ),
                                modifier = Modifier.testTag("notif_filter_${option.name.lowercase()}")
                            )
                        }
                    }
                }

                if (filteredNotifications.isEmpty()) {
                    item {
                        VaultEmptyState(
                            icon = Icons.Outlined.Notifications,
                            title = "All clear",
                            subtitle = if (filter == NotificationFilter.EXPIRING_7D) {
                                "No medicines or warranties expire within the next 7 days."
                            } else {
                                "No upcoming return deadlines, warranty expiries, or medicine alerts right now."
                            },
                            accent = vaultColors.goldAccent,
                            actionLabel = "Configure Preferences",
                            onAction = onOpenPreferences
                        )
                    }
                } else {
                    if (showPurchases && purchaseAlerts.isNotEmpty()) {
                        item {
                            Text(
                                text = "Purchase Alerts (${purchaseAlerts.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = vaultColors.purchaseAccent
                            )
                        }
                        items(purchaseAlerts, key = { it.alertKey }) { alert ->
                            VaultAnimatedEntrance(delayMillis = 40) {
                                NotificationRowCard(
                                    alert = alert,
                                    onOpen = {
                                        viewModel.markNotificationRead(alert.alertKey)
                                        onOpenPurchaseDetail(alert.targetId)
                                    },
                                    onMarkRead = { viewModel.markNotificationRead(alert.alertKey) }
                                )
                            }
                        }
                    }

                    if (showMedicines && medicineAlerts.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Medicine Alerts (${medicineAlerts.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = vaultColors.medicineAccent
                            )
                        }
                        items(medicineAlerts, key = { it.alertKey }) { alert ->
                            VaultAnimatedEntrance(delayMillis = 60) {
                                NotificationRowCard(
                                    alert = alert,
                                    onOpen = {
                                        viewModel.markNotificationRead(alert.alertKey)
                                        onOpenMedicineDetail(alert.targetId)
                                    },
                                    onMarkRead = { viewModel.markNotificationRead(alert.alertKey) }
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
private fun NotificationRowCard(
    alert: VaultNotification,
    onOpen: () -> Unit,
    onMarkRead: () -> Unit
) {
    val vaultColors = LocalVaultColors.current
    val accent = when (alert.kind) {
        AlertKind.RETURN_APPROACHING -> vaultColors.statusWarning
        AlertKind.WARRANTY_APPROACHING -> if (alert.isUrgent7Days) vaultColors.statusWarning else vaultColors.purchaseAccent
        AlertKind.MEDICINE_EXPIRING_SOON -> if (alert.isUrgent7Days) vaultColors.statusWarning else vaultColors.medicineAccent
        AlertKind.MEDICINE_EXPIRED -> vaultColors.statusExpired
    }
    GlassVaultCard(
        modifier = Modifier.fillMaxWidth(),
        accentBorder = if (alert.isRead) vaultColors.glassBorder else accent.copy(alpha = 0.6f),
        onClick = onOpen
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
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
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (alert.module == VaultModule.PURCHASE) Icons.Outlined.ReceiptLong else Icons.Outlined.Medication,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!alert.isRead) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(accent)
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = alert.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (alert.isRead) FontWeight.Normal else FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = alert.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PriorityTierBadge(tier = alert.priorityTier)
                StatusPill(
                    label = if (alert.module == VaultModule.PURCHASE) "PURCHASE" else "MEDICINE",
                    color = if (alert.module == VaultModule.PURCHASE) vaultColors.purchaseAccent else vaultColors.medicineAccent
                )
                if (alert.isUrgent7Days) {
                    StatusPill(
                        label = if (alert.daysDelta == 0L) "EXPIRES TODAY" else "≤7 DAYS",
                        color = if (alert.daysDelta <= 1L) vaultColors.statusExpired else vaultColors.statusWarning
                    )
                }
            }
            if (!alert.isRead) {
                TextButton(onClick = onMarkRead) {
                    Text("Mark as read", color = vaultColors.goldAccent)
                }
            } else {
                Text(
                    text = "Read",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
