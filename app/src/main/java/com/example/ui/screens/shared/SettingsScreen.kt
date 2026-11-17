package com.example.ui.screens.shared

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.auth.signOutVaultora
import com.example.model.ThemePreference
import com.example.model.UiState
import com.example.notifications.VaultReminderScheduler
import com.example.ui.components.GlassVaultCard
import com.example.ui.components.VaultAnimatedEntrance
import com.example.ui.components.VaultAtmosphereBackground
import com.example.ui.components.VaultGlassOutlinedButton
import com.example.ui.components.VaultoraLogoBadge
import com.example.ui.theme.LocalVaultColors
import com.example.util.ExportUtils
import com.example.viewmodel.VaultViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: VaultViewModel,
    bottomContentPadding: Dp = 0.dp,
    familyGroupName: String? = null,
    onOpenFamilyVault: () -> Unit = {},
    onOpenProfile: () -> Unit,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val vaultColors = LocalVaultColors.current

    val profileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val purchasesState by viewModel.purchasesState.collectAsStateWithLifecycle()
    val medicinesState by viewModel.medicinesState.collectAsStateWithLifecycle()

    val expiringSoonReport by viewModel.expiringSoon7DaysState.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsStateWithLifecycle()

    val profile = (profileState as? UiState.Success)?.data
    val purchases = (purchasesState as? UiState.Success)?.data.orEmpty()
    val medicines = (medicinesState as? UiState.Success)?.data.orEmpty()

    var showPasswordDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var pendingExpiringSoonScan by remember { mutableStateOf(false) }

    val runExpiringSoonScan: () -> Unit = {
        val report = VaultReminderScheduler.checkAndNotifyExpiringSoon(
            context = context,
            purchases = purchases,
            medicines = medicines,
            profile = profile,
            forceAlert = true
        )
        val msg = if (report.hasAlerts) {
            "Expiring Soon alert sent (${report.medicineItems.size} medicines, ${report.warrantyItems.size} warranties ≤7d)."
        } else {
            "All clear — no medicines or warranties expire within 7 days."
        }
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (pendingExpiringSoonScan) {
                pendingExpiringSoonScan = false
                runExpiringSoonScan()
            } else {
                VaultReminderScheduler.sendImmediateTestReminder(context)
            }
        } else {
            pendingExpiringSoonScan = false
            scope.launch { snackbarHostState.showSnackbar("Notification permission denied in system settings.") }
        }
    }

    VaultAtmosphereBackground(
        accentColor = vaultColors.goldAccent,
        secondaryAccent = vaultColors.purchaseAccent
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("settings_screen"),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 16.dp,
                    bottom = 16.dp + bottomContentPadding
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    VaultAnimatedEntrance(delayMillis = 20) {
                        Column {
                            Text("Settings", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                text = "Account, alerts, security, exports & appearance",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Account Section
                item {
                    VaultAnimatedEntrance(delayMillis = 70) {
                        GlassVaultCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Account", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(10.dp))
                        SettingsActionRow(
                            icon = Icons.Outlined.Person,
                            title = "Account Details",
                            subtitle = profile?.email ?: "View profile & vault overview",
                            onClick = onOpenProfile,
                            testTag = "settings_profile_row"
                        )
                        HorizontalDivider(color = vaultColors.glassBorder, modifier = Modifier.padding(vertical = 8.dp))
                        SettingsActionRow(
                            icon = Icons.Outlined.Group,
                            title = "Family Vault Sharing",
                            subtitle = familyGroupName?.let { "Joined: $it" } ?: "Create or join a private family vault",
                            onClick = onOpenFamilyVault,
                            testTag = "settings_family_vault_row"
                        )
                        HorizontalDivider(color = vaultColors.glassBorder, modifier = Modifier.padding(vertical = 8.dp))
                        SettingsActionRow(
                            icon = Icons.Outlined.Lock,
                            title = "Change Password",
                            subtitle = "Managed via Google Account security",
                            onClick = { showPasswordDialog = true }
                        )
                    }
                    }
                }

                // Notifications Section
                item {
                    GlassVaultCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Notifications & Reminder Timing", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Purchase Reminders", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = "Return windows & warranty expiry alerts",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = profile?.purchaseRemindersEnabled ?: true,
                                onCheckedChange = {
                                    viewModel.updateSettings(mapOf("purchaseRemindersEnabled" to it))
                                }
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Medicine Expiry Reminders", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = "Alerts before stored medicines expire",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = profile?.medicineRemindersEnabled ?: true,
                                onCheckedChange = {
                                    viewModel.updateSettings(mapOf("medicineRemindersEnabled" to it))
                                }
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        Text("Return Alert Lead Time", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1L, 3L, 7L).forEach { days ->
                                FilterChip(
                                    selected = (profile?.returnReminderDaysBefore ?: 3L) == days,
                                    onClick = { viewModel.updateSettings(mapOf("returnReminderDaysBefore" to days)) },
                                    label = { Text("${days}d before") }
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                        Text("Warranty & Medicine Lead Time", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(7L, 14L, 30L).forEach { days ->
                                FilterChip(
                                    selected = (profile?.medicineReminderDaysBefore ?: 14L) == days,
                                    onClick = {
                                        viewModel.updateSettings(
                                            mapOf(
                                                "warrantyReminderDaysBefore" to days,
                                                "medicineReminderDaysBefore" to days
                                            )
                                        )
                                    },
                                    label = { Text("${days}d before") }
                                )
                            }
                        }

                        HorizontalDivider(color = vaultColors.glassBorder, modifier = Modifier.padding(vertical = 12.dp))
                        Text("Smart Priority Notifications", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Reduce alert overload using tiered scheduling & grouped summaries",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        PriorityNotificationSwitchRow(
                            title = "Critical Priority Alerts",
                            subtitle = "Timely individual notifications for immediate deadlines",
                            checked = profile?.priorityCriticalAlertsEnabled ?: true,
                            onCheckedChange = {
                                viewModel.updateSettings(mapOf("priorityCriticalAlertsEnabled" to it))
                            },
                            testTag = "switch_priority_critical_alerts"
                        )
                        PriorityNotificationSwitchRow(
                            title = "High Priority Reminders",
                            subtitle = "Individual or grouped alerts for important deadlines",
                            checked = profile?.priorityHighAlertsEnabled ?: true,
                            onCheckedChange = {
                                viewModel.updateSettings(mapOf("priorityHighAlertsEnabled" to it))
                            },
                            testTag = "switch_priority_high_alerts"
                        )
                        PriorityNotificationSwitchRow(
                            title = "Medium Grouped Summary",
                            subtitle = "Throttled single digest for approaching deadlines",
                            checked = profile?.priorityMediumDigestEnabled ?: true,
                            onCheckedChange = {
                                viewModel.updateSettings(mapOf("priorityMediumDigestEnabled" to it))
                            },
                            testTag = "switch_priority_medium_digest"
                        )
                        PriorityNotificationSwitchRow(
                            title = "Mute Low Priority Alerts",
                            subtitle = "Suppress unnecessary alerts for low-priority records",
                            checked = profile?.priorityLowMuted ?: true,
                            onCheckedChange = {
                                viewModel.updateSettings(mapOf("priorityLowMuted" to it))
                            },
                            testTag = "switch_priority_low_muted"
                        )

                        Spacer(Modifier.height(8.dp))
                        SettingsActionRow(
                            icon = Icons.Outlined.NotificationsActive,
                            title = "Check Expiring Soon (≤7 Days)",
                            subtitle = if (expiringSoonReport.hasAlerts) {
                                "${expiringSoonReport.items.size} item(s) expiring within 7 days"
                            } else {
                                "Scan stored medicines & warranties against today's date"
                            },
                            onClick = {
                                if (VaultReminderScheduler.hasNotificationPermission(context)) {
                                    runExpiringSoonScan()
                                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    pendingExpiringSoonScan = true
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    runExpiringSoonScan()
                                }
                            },
                            testTag = "settings_check_expiring_soon"
                        )
                        HorizontalDivider(color = vaultColors.glassBorder, modifier = Modifier.padding(vertical = 8.dp))
                        SettingsActionRow(
                            icon = Icons.Outlined.NotificationsActive,
                            title = "Send Test Alert",
                            subtitle = "Verify Android notification delivery",
                            onClick = {
                                if (VaultReminderScheduler.hasNotificationPermission(context)) {
                                    VaultReminderScheduler.sendImmediateTestReminder(context)
                                    scope.launch { snackbarHostState.showSnackbar("Test notification dispatched") }
                                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    pendingExpiringSoonScan = false
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        )
                    }
                }

                // Privacy & Security
                item {
                    GlassVaultCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Privacy & Security", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.Security, contentDescription = null, tint = vaultColors.goldAccent)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("App Security Shield", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        text = "Require fresh unlock on vault launch",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = profile?.appLockEnabled ?: false,
                                onCheckedChange = {
                                    viewModel.updateSettings(mapOf("appLockEnabled" to it))
                                }
                            )
                        }
                        HorizontalDivider(color = vaultColors.glassBorder, modifier = Modifier.padding(vertical = 8.dp))
                        SettingsActionRow(
                            icon = Icons.Outlined.PrivacyTip,
                            title = "Privacy Information",
                            subtitle = "Zero-trust per-user Firestore isolation",
                            onClick = { showPrivacyDialog = true }
                        )
                    }
                }

                // Data Management
                item {
                    GlassVaultCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Data Management & Backup", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(10.dp))
                        SettingsActionRow(
                            icon = when {
                                !isOnline -> Icons.Outlined.CloudOff
                                pendingSyncCount > 0 -> Icons.Outlined.CloudSync
                                else -> Icons.Outlined.CloudDone
                            },
                            title = "Offline Mode & Cloud Auto-Sync",
                            subtitle = when {
                                !isOnline && pendingSyncCount > 0 ->
                                    "Offline • $pendingSyncCount local change(s) queued to sync when online"
                                !isOnline ->
                                    "Offline • Local device cache active; edits auto-sync online"
                                pendingSyncCount > 0 ->
                                    "Online • Syncing $pendingSyncCount change(s) to Firestore..."
                                else ->
                                    "Online • All device & cloud vault records synced"
                            },
                            onClick = { viewModel.syncNow() },
                            testTag = "settings_cloud_sync_status"
                        )
                        HorizontalDivider(color = vaultColors.glassBorder, modifier = Modifier.padding(vertical = 8.dp))
                        SettingsActionRow(
                            icon = Icons.Outlined.ReceiptLong,
                            title = "Export Purchase Records",
                            subtitle = "Share ${purchases.size} receipts as JSON",
                            onClick = {
                                ExportUtils.exportPurchasesAndShare(context, purchases)
                                    .onSuccess { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                                    .onFailure { err -> scope.launch { snackbarHostState.showSnackbar(err.localizedMessage ?: "Export failed") } }
                            },
                            testTag = "export_purchases_button"
                        )
                        HorizontalDivider(color = vaultColors.glassBorder, modifier = Modifier.padding(vertical = 8.dp))
                        SettingsActionRow(
                            icon = Icons.Outlined.Medication,
                            title = "Export Medicine Records",
                            subtitle = "Share ${medicines.size} medicines as JSON",
                            onClick = {
                                ExportUtils.exportMedicinesAndShare(context, medicines)
                                    .onSuccess { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                                    .onFailure { err -> scope.launch { snackbarHostState.showSnackbar(err.localizedMessage ?: "Export failed") } }
                            },
                            testTag = "export_medicines_button"
                        )
                        HorizontalDivider(color = vaultColors.glassBorder, modifier = Modifier.padding(vertical = 8.dp))
                        SettingsActionRow(
                            icon = Icons.Outlined.Restore,
                            title = "Add Sample Vault Records",
                            subtitle = "Populate demo receipts & medicines in cloud backup",
                            onClick = {
                                viewModel.loadSampleVaultData()
                                scope.launch { snackbarHostState.showSnackbar("Sample records added to your vault.") }
                            }
                        )
                    }
                }

                // Appearance
                item {
                    GlassVaultCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.DarkMode, contentDescription = null, tint = vaultColors.purchaseAccent)
                            Spacer(Modifier.width(8.dp))
                            Text("Appearance", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(10.dp))
                        val currentTheme = profile?.resolvedTheme() ?: ThemePreference.DARK
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThemePreference.entries.forEach { pref ->
                                FilterChip(
                                    selected = currentTheme == pref,
                                    onClick = { viewModel.updateThemePreference(pref) },
                                    label = {
                                        Text(
                                            when (pref) {
                                                ThemePreference.DARK -> "Obsidian Dark"
                                                ThemePreference.LIGHT -> "Alabaster Light"
                                                ThemePreference.SYSTEM -> "System"
                                            }
                                        )
                                    },
                                    modifier = Modifier.testTag("theme_chip_${pref.name.lowercase()}")
                                )
                            }
                        }
                    }
                }

                // Support
                item {
                    GlassVaultCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Support", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(10.dp))
                        SettingsActionRow(
                            icon = Icons.AutoMirrored.Outlined.HelpOutline,
                            title = "Help & Guides",
                            subtitle = "Scanning receipts, warranties & medicine reminders",
                            onClick = { showHelpDialog = true }
                        )
                        HorizontalDivider(color = vaultColors.glassBorder, modifier = Modifier.padding(vertical = 8.dp))
                        SettingsActionRow(
                            icon = Icons.Outlined.Email,
                            title = "Contact Support",
                            subtitle = "support@vaultora.app",
                            onClick = {
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:support@vaultora.app")
                                    putExtra(Intent.EXTRA_SUBJECT, "VAULTORA Support Request")
                                }
                                try {
                                    context.startActivity(Intent.createChooser(intent, "Contact Support"))
                                } catch (_: Exception) {
                                    scope.launch { snackbarHostState.showSnackbar("No email app available.") }
                                }
                            }
                        )
                        HorizontalDivider(color = vaultColors.glassBorder, modifier = Modifier.padding(vertical = 8.dp))
                        SettingsActionRow(
                            icon = Icons.Outlined.Info,
                            title = "About VAULTORA",
                            subtitle = "Version 1.0 • Personal Digital Vault",
                            onClick = { showAboutDialog = true },
                            testTag = "about_vaultora_row"
                        )
                    }
                }

                // Logout
                item {
                    VaultGlassOutlinedButton(
                        onClick = { showLogoutDialog = true },
                        colors = vaultColors.dangerButtonGradient,
                        contentColor = vaultColors.statusExpired,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("settings_logout_button")
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sign Out", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("Password Management") },
            text = { Text("Your VAULTORA vault authenticates with Google Sign-In. Password and 2-Step Verification settings are managed securely in your Google Account.") },
            confirmButton = { TextButton(onClick = { showPasswordDialog = false }) { Text("Close") } }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy & Vault Isolation") },
            text = {
                Text(
                    "All purchase receipts and medicine records are stored in your private Firestore partition scoped strictly to your authenticated UID. " +
                        "AI receipt scanning processes only the document you explicitly select. Medicine Vault tracks storage and expiry dates only."
                )
            },
            confirmButton = { TextButton(onClick = { showPrivacyDialog = false }) { Text("Understood") } }
        )
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("VAULTORA Help") },
            text = {
                Text(
                    "1. Purchase Vault: Scan physical receipts or upload PDFs/images, track return deadlines in Return Tracker, and draft warranty claim emails.\n\n" +
                        "2. Medicine Vault: Store medicine photos, batch numbers, and expiry dates with automated lead-time alerts."
                )
            },
            confirmButton = { TextButton(onClick = { showHelpDialog = false }) { Text("Got it") } }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            icon = { VaultoraLogoBadge(size = 56.dp) },
            title = { Text("VAULTORA") },
            text = { Text("VAULTORA v1.0\nIntelligent personal digital vault for purchase invoices, warranties, return deadlines, and medicine expiry tracking.") },
            confirmButton = { TextButton(onClick = { showAboutDialog = false }) { Text("Close") } }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Sign Out of VAULTORA?") },
            text = { Text("Your encrypted cloud records stay safely synced to your Google account.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        signOutVaultora(credentialManager, scope, onSignOutComplete = onSignedOut)
                    }
                ) {
                    Text("Sign Out", color = vaultColors.statusExpired)
                }
            },
            dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PriorityNotificationSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
