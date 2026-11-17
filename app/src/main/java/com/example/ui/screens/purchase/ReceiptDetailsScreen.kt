package com.example.ui.screens.purchase

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.model.AttachmentType
import com.example.model.ReturnStatus
import com.example.model.UiState
import com.example.model.WarrantyStatus
import com.example.notifications.VaultReminderScheduler
import com.example.ui.components.GlassVaultCard
import com.example.ui.components.ImportanceSelectorRow
import com.example.ui.components.PriorityTierBadge
import com.example.ui.components.StatusPill
import com.example.ui.components.VaultAnimatedEntrance
import com.example.ui.components.VaultAtmosphereBackground
import com.example.ui.components.VaultDatePickerField
import com.example.ui.components.VaultEmptyState
import com.example.ui.components.VaultGlassOutlinedButton
import com.example.ui.components.VaultGradientButton
import com.example.ui.components.VaultTopBar
import com.example.ui.components.tierColor
import com.example.ui.components.vaultSharedBounds
import com.example.ui.theme.LocalVaultColors
import com.example.util.DateAndCurrencyUtils
import com.example.util.FileAttachmentUtils
import com.example.viewmodel.VaultViewModel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@Composable
fun ReceiptDetailsScreen(
    purchaseId: String,
    viewModel: VaultViewModel,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onTrackReturn: () -> Unit,
    onTrackWarranty: () -> Unit,
    onOpenWarrantyClaim: (String) -> Unit,
    isSharedWithFamily: Boolean = false,
    onToggleFamilyShare: ((com.example.model.PurchaseRecord) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vaultColors = LocalVaultColors.current
    val snackbarHostState = remember { SnackbarHostState() }

    val purchasesState by viewModel.purchasesState.collectAsStateWithLifecycle()
    val profileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val pendingPurchaseIds by viewModel.pendingPurchaseIds.collectAsStateWithLifecycle()
    val priorityReport by viewModel.priorityCenterState.collectAsStateWithLifecycle()
    val purchase = (purchasesState as? UiState.Success)?.data?.firstOrNull { it.id == purchaseId }
    val priorityItem = purchase?.let { priorityReport.purchasePriorityMap[it.id] }
    val profile = (profileState as? UiState.Success)?.data
    val isPendingSync = purchase != null && purchase.id in pendingPurchaseIds

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showInvoiceViewer by remember { mutableStateOf(false) }
    var pdfBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && purchase != null) {
            VaultReminderScheduler.schedulePurchaseReminders(
                context = context,
                purchase = purchase,
                returnLeadDays = profile?.returnReminderDaysBefore ?: 3L,
                warrantyLeadDays = profile?.warrantyReminderDaysBefore ?: 14L
            )
            scope.launch { snackbarHostState.showSnackbar("Reminders scheduled for ${purchase.productName}") }
        }
    }

    LaunchedEffect(purchase?.attachmentUri, purchase?.attachmentType) {
        if (purchase?.attachmentUri != null && purchase.attachmentType == AttachmentType.PDF.name) {
            pdfBitmaps = FileAttachmentUtils.renderPdfPagesToBitmaps(context, purchase.attachmentUri, maxPages = 2)
        } else {
            pdfBitmaps = emptyList()
        }
    }

    VaultAtmosphereBackground(
        accentColor = vaultColors.purchaseAccent,
        secondaryAccent = vaultColors.goldAccent
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                VaultTopBar(
                    title = "Receipt Details",
                    subtitle = purchase?.merchantName ?: "Purchase Vault",
                    onBack = onBack,
                    actions = {
                        if (purchase != null) {
                            IconButton(
                                onClick = { onEdit(purchase.id) },
                                modifier = Modifier.testTag("edit_receipt_button")
                            ) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Edit receipt")
                            }
                            IconButton(
                                onClick = { showDeleteDialog = true },
                                modifier = Modifier.testTag("delete_receipt_button")
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Delete receipt",
                                    tint = vaultColors.statusExpired
                                )
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            if (purchase == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    VaultEmptyState(
                        icon = Icons.Outlined.Description,
                        title = "Receipt not found",
                        subtitle = "This purchase entry may have been removed.",
                        accent = vaultColors.purchaseAccent,
                        actionLabel = "Go Back",
                        onAction = onBack
                    )
                }
            } else {
                val rStatus = DateAndCurrencyUtils.returnStatus(purchase)
                val wStatus = DateAndCurrencyUtils.warrantyStatus(purchase)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .testTag("receipt_details_screen"),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Hero Summary Card
                    item {
                        GlassVaultCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .vaultSharedBounds("purchase_card_${purchase.id}"),
                            accentBorder = vaultColors.purchaseAccent.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    StatusPill(label = purchase.category.uppercase(), color = vaultColors.purchaseAccent)
                                    if (purchase.completed) {
                                        StatusPill(label = "COMPLETED", color = vaultColors.statusValid)
                                    } else if (priorityItem != null) {
                                        PriorityTierBadge(tier = priorityItem.priorityTier)
                                        if (priorityItem.isOverdue) {
                                            StatusPill(label = "OVERDUE", color = vaultColors.statusExpired)
                                        }
                                    }
                                    if (isPendingSync) {
                                        StatusPill(label = "PENDING SYNC", color = vaultColors.statusWarning)
                                    }
                                }
                                if (purchase.invoiceNumber.isNotBlank()) {
                                    StatusPill(label = purchase.invoiceNumber, color = vaultColors.goldAccent)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = purchase.productName,
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Text(
                                text = buildString {
                                    if (purchase.brand.isNotBlank()) {
                                        append(purchase.brand)
                                        append(" • ")
                                    }
                                    append(purchase.merchantName)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val claimStatus = purchase.resolvedClaimStatus()
                            if (claimStatus != com.example.model.WarrantyClaimStatus.NOT_SUBMITTED) {
                                Spacer(Modifier.height(6.dp))
                                StatusPill(
                                    label = "CLAIM STATUS: ${claimStatus.label.uppercase()}",
                                    color = vaultColors.goldAccent
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = DateAndCurrencyUtils.formatInr(purchase.purchasePriceInr),
                                style = MaterialTheme.typography.headlineLarge,
                                color = vaultColors.purchaseAccent,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = vaultColors.glassBorder)
                            Spacer(Modifier.height(12.dp))

                            DetailLine("Purchase Date", DateAndCurrencyUtils.formatDate(purchase.purchaseDateMillis))
                            Spacer(Modifier.height(8.dp))
                            DetailLine(
                                "Return Deadline",
                                purchase.returnDeadlineMillis?.let {
                                    "${DateAndCurrencyUtils.formatDate(it)} (${DateAndCurrencyUtils.formatCountdown(DateAndCurrencyUtils.daysFromNow(it))})"
                                } ?: "Not specified"
                            )
                            Spacer(Modifier.height(8.dp))
                            DetailLine(
                                "Warranty Expiry",
                                purchase.warrantyExpiryMillis?.let {
                                    "${DateAndCurrencyUtils.formatDate(it)} (${DateAndCurrencyUtils.formatCountdown(DateAndCurrencyUtils.daysFromNow(it))})"
                                } ?: "Not specified"
                            )
                            if (!purchase.notes.isNullOrBlank()) {
                                Spacer(Modifier.height(8.dp))
                                DetailLine("Notes", purchase.notes)
                            }
                        }
                    }

                    // Smart Priority & Completion Controls
                    item {
                        VaultAnimatedEntrance(delayMillis = 30) {
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
                                            tint = vaultColors.purchaseAccent
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Smart Priority & Claim", style = MaterialTheme.typography.titleMedium)
                                    }
                                    if (priorityItem != null) {
                                        StatusPill(
                                            label = priorityItem.priorityStatusLabel,
                                            color = if (purchase.completed) vaultColors.statusValid else tierColor(priorityItem.priorityTier, vaultColors)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                ImportanceSelectorRow(
                                    selected = purchase.resolvedImportanceOrNull(),
                                    onSelect = { level -> viewModel.updatePurchaseImportance(purchase.id, level) },
                                    accentColor = vaultColors.purchaseAccent,
                                    tagPrefix = "receipt_detail_importance"
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Warranty Claim Follow-Up", style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            text = "Prioritize active warranty service follow-ups",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = purchase.warrantyClaimActive,
                                        onCheckedChange = { checked ->
                                            val defaultFollowUp = purchase.warrantyClaimFollowUpMillis
                                                ?: (System.currentTimeMillis() + TimeUnit.DAYS.toMillis(3L))
                                            viewModel.updateWarrantyClaimFollowUp(
                                                purchaseId = purchase.id,
                                                active = checked,
                                                followUpMillis = if (checked) defaultFollowUp else null,
                                                clearFollowUp = !checked
                                            )
                                        },
                                        modifier = Modifier.testTag("receipt_detail_claim_followup_switch")
                                    )
                                }
                                if (purchase.warrantyClaimActive) {
                                    Spacer(Modifier.height(10.dp))
                                    VaultDatePickerField(
                                        label = "Claim Follow-Up Deadline",
                                        selectedMillis = purchase.warrantyClaimFollowUpMillis,
                                        onDateSelected = { millis ->
                                            viewModel.updateWarrantyClaimFollowUp(
                                                purchaseId = purchase.id,
                                                active = true,
                                                followUpMillis = millis,
                                                clearFollowUp = millis == null
                                            )
                                        },
                                        optional = true,
                                        accentColor = vaultColors.goldAccent,
                                        testTag = "receipt_detail_claim_followup_date"
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                VaultGlassOutlinedButton(
                                    onClick = { viewModel.setPurchaseCompleted(purchase.id, !purchase.completed) },
                                    colors = if (purchase.completed) vaultColors.goldButtonGradient else vaultColors.purchaseButtonGradient,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("receipt_toggle_completed_button")
                                ) {
                                    Icon(
                                        imageVector = if (purchase.completed) Icons.Outlined.Replay else Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (purchase.completed) "Reopen to Active Priority" else "Mark Completed")
                                }
                            }
                        }
                    }

                    // Original Invoice Preview
                    item {
                        VaultAnimatedEntrance(delayMillis = 50) {
                        GlassVaultCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Original Invoice Preview", style = MaterialTheme.typography.titleMedium)
                                TextButton(
                                    onClick = { showInvoiceViewer = true },
                                    modifier = Modifier.testTag("view_invoice_button")
                                ) {
                                    Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("View Invoice", color = vaultColors.purchaseAccent)
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(170.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF0C0F16))
                                    .clickable { showInvoiceViewer = true },
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    purchase.attachmentUri != null && purchase.attachmentType == AttachmentType.PDF.name && pdfBitmaps.isNotEmpty() -> {
                                        Image(
                                            bitmap = pdfBitmaps.first().asImageBitmap(),
                                            contentDescription = "PDF invoice preview",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    purchase.attachmentUri != null && purchase.attachmentType == AttachmentType.IMAGE.name -> {
                                        AsyncImage(
                                            model = purchase.attachmentUri,
                                            contentDescription = "Receipt photo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    else -> {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(16.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Description,
                                                contentDescription = null,
                                                tint = vaultColors.purchaseAccent,
                                                modifier = Modifier.size(36.dp)
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                text = "Digital Receipt Summary Ready",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "Tap to inspect formatted invoice record",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        }
                    }

                    // Reminder Controls
                    item {
                        VaultAnimatedEntrance(delayMillis = 100) {
                        GlassVaultCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = vaultColors.goldAccent)
                                Spacer(Modifier.width(8.dp))
                                Text("Reminder Preferences", style = MaterialTheme.typography.titleMedium)
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Return deadline alert", style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = purchase.returnReminderSet,
                                    onCheckedChange = { checked ->
                                        val updated = purchase.copy(returnReminderSet = checked)
                                        scope.launch {
                                            viewModel.savePurchase(updated)
                                            if (checked) {
                                                if (!VaultReminderScheduler.hasNotificationPermission(context) &&
                                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                                ) {
                                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                } else {
                                                    VaultReminderScheduler.schedulePurchaseReminders(
                                                        context,
                                                        updated,
                                                        profile?.returnReminderDaysBefore ?: 3L,
                                                        profile?.warrantyReminderDaysBefore ?: 14L
                                                    )
                                                    snackbarHostState.showSnackbar("Return reminder updated")
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Warranty expiry alert", style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = purchase.warrantyReminderSet,
                                    onCheckedChange = { checked ->
                                        val updated = purchase.copy(warrantyReminderSet = checked)
                                        scope.launch {
                                            viewModel.savePurchase(updated)
                                            if (checked) {
                                                if (!VaultReminderScheduler.hasNotificationPermission(context) &&
                                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                                ) {
                                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                } else {
                                                    VaultReminderScheduler.schedulePurchaseReminders(
                                                        context,
                                                        updated,
                                                        profile?.returnReminderDaysBefore ?: 3L,
                                                        profile?.warrantyReminderDaysBefore ?: 14L
                                                    )
                                                    snackbarHostState.showSnackbar("Warranty reminder updated")
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        }
                    }

                    // Actions: Track Return, Track Warranty, Warranty Claim Assistant
                    item {
                        VaultAnimatedEntrance(delayMillis = 150) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                VaultGlassOutlinedButton(
                                    onClick = onTrackReturn,
                                    colors = vaultColors.purchaseButtonGradient,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("track_return_button")
                                ) {
                                    Icon(Icons.Outlined.EventAvailable, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (rStatus == ReturnStatus.NOT_TRACKED) "Return Tracker" else "Track Return",
                                        maxLines = 1
                                    )
                                }
                                VaultGlassOutlinedButton(
                                    onClick = onTrackWarranty,
                                    colors = vaultColors.brandButtonGradient,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("track_warranty_button")
                                ) {
                                    Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (wStatus == WarrantyStatus.NOT_TRACKED) "Warranty Tracker" else "Track Warranty",
                                        maxLines = 1
                                    )
                                }
                            }

                            if (onToggleFamilyShare != null) {
                                VaultGlassOutlinedButton(
                                    onClick = { onToggleFamilyShare(purchase) },
                                    colors = if (isSharedWithFamily) vaultColors.goldButtonGradient else vaultColors.brandButtonGradient,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("receipt_family_share_button")
                                ) {
                                    Icon(Icons.Outlined.Group, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (isSharedWithFamily) "Shared in Family Vault (Tap to Manage)" else "Share to Family Vault")
                                }
                            }

                            VaultGradientButton(
                                onClick = { onOpenWarrantyClaim(purchase.id) },
                                colors = vaultColors.purchaseButtonGradient,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("open_warranty_claim_button")
                            ) {
                                Icon(Icons.Outlined.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Prepare Warranty Claim")
                            }
                        }
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    if (showDeleteDialog && purchase != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Receipt?") },
            text = { Text("Remove \"${purchase.productName}\" from your Purchase Vault? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        scope.launch {
                            viewModel.deletePurchase(purchase.id).onSuccess {
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

    if (showInvoiceViewer && purchase != null) {
        Dialog(onDismissRequest = { showInvoiceViewer = false }) {
            GlassVaultCard(
                modifier = Modifier.fillMaxWidth(),
                accentBorder = vaultColors.purchaseAccent
            ) {
                Text("Invoice Preview", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                when {
                    purchase.attachmentUri != null && purchase.attachmentType == AttachmentType.PDF.name && pdfBitmaps.isNotEmpty() -> {
                        Image(
                            bitmap = pdfBitmaps.first().asImageBitmap(),
                            contentDescription = "Full PDF Invoice",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(360.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                    purchase.attachmentUri != null && purchase.attachmentType == AttachmentType.IMAGE.name -> {
                        AsyncImage(
                            model = purchase.attachmentUri,
                            contentDescription = "Full Receipt Image",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(360.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            DetailLine("Merchant", purchase.merchantName)
                            DetailLine("Product", purchase.productName)
                            DetailLine("Invoice No.", purchase.invoiceNumber.ifBlank { "N/A" })
                            DetailLine("Amount", DateAndCurrencyUtils.formatInr(purchase.purchasePriceInr))
                            DetailLine("Date", DateAndCurrencyUtils.formatDate(purchase.purchaseDateMillis))
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                VaultGradientButton(
                    onClick = { showInvoiceViewer = false },
                    colors = vaultColors.purchaseButtonGradient,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
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
