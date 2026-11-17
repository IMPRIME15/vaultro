package com.example.ui.screens.purchase

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.model.AttachmentType
import com.example.model.UiState
import com.example.model.WarrantyClaimStatus
import com.example.ui.components.GlassVaultCard
import com.example.ui.components.StatusPill
import com.example.ui.components.VaultAtmosphereBackground
import com.example.ui.components.VaultDatePickerField
import com.example.ui.components.VaultEmptyState
import com.example.ui.components.VaultGlassOutlinedButton
import com.example.ui.components.VaultGradientButton
import com.example.ui.components.VaultTopBar
import com.example.ui.theme.LocalVaultColors
import com.example.util.DateAndCurrencyUtils
import com.example.util.FileAttachmentUtils
import com.example.viewmodel.ClaimDraftState
import com.example.viewmodel.VaultViewModel
import com.example.viewmodel.WarrantyClaimViewModel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@Composable
fun WarrantyClaimAssistantScreen(
    purchaseId: String,
    vaultViewModel: VaultViewModel,
    onBack: () -> Unit,
    claimViewModel: WarrantyClaimViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val vaultColors = LocalVaultColors.current

    val purchasesState by vaultViewModel.purchasesState.collectAsStateWithLifecycle()
    val profileState by vaultViewModel.userProfileState.collectAsStateWithLifecycle()
    val draftState by claimViewModel.draftState.collectAsStateWithLifecycle()
    val subject by claimViewModel.subject.collectAsStateWithLifecycle()
    val body by claimViewModel.body.collectAsStateWithLifecycle()

    val purchase = (purchasesState as? UiState.Success)?.data?.firstOrNull { it.id == purchaseId }
    val customerName = (profileState as? UiState.Success)?.data?.fullName ?: "Customer"

    var initializedFromPurchase by rememberSaveable(purchaseId) { mutableStateOf(false) }
    var issueDescription by rememberSaveable(purchaseId) { mutableStateOf("") }
    var selectedClaimStatusName by rememberSaveable(purchaseId) {
        mutableStateOf(WarrantyClaimStatus.NOT_SUBMITTED.name)
    }
    var claimNotes by rememberSaveable(purchaseId) { mutableStateOf("") }
    var showReceiptDialog by remember { mutableStateOf(false) }
    var pdfBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }

    LaunchedEffect(purchase) {
        if (purchase != null && !initializedFromPurchase) {
            selectedClaimStatusName = purchase.resolvedClaimStatus().name
            claimNotes = purchase.claimNotes
            if (issueDescription.isBlank() && purchase.claimIssueSummary.isNotBlank()) {
                issueDescription = purchase.claimIssueSummary
            }
            initializedFromPurchase = true
        }
    }

    LaunchedEffect(purchase?.attachmentUri, purchase?.attachmentType) {
        if (purchase?.attachmentUri != null && purchase.attachmentType == AttachmentType.PDF.name) {
            pdfBitmaps = FileAttachmentUtils.renderPdfPagesToBitmaps(context, purchase.attachmentUri, maxPages = 1)
        } else {
            pdfBitmaps = emptyList()
        }
    }

    val quickIssues = listOf(
        "Device stopped powering on",
        "Battery draining unusually fast",
        "Screen / display malfunction",
        "Hardware port or button defect"
    )

    VaultAtmosphereBackground(
        accentColor = vaultColors.purchaseAccent,
        secondaryAccent = vaultColors.goldAccent
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                VaultTopBar(
                    title = "Warranty Claim Ready Mode",
                    subtitle = purchase?.productName ?: "Purchase Vault",
                    onBack = onBack
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
                        icon = Icons.Outlined.Shield,
                        title = "Receipt not found",
                        subtitle = "Select a saved purchase to prepare a warranty claim package.",
                        accent = vaultColors.purchaseAccent,
                        actionLabel = "Go Back",
                        onAction = onBack
                    )
                }
            } else {
                val activeStatus = WarrantyClaimStatus.fromRaw(selectedClaimStatusName)
                val statusColor = claimStatusColor(activeStatus, vaultColors)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .testTag("warranty_claim_assistant_screen"),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Organized Warranty Claim Ready Package
                    item {
                        GlassVaultCard(
                            modifier = Modifier.fillMaxWidth(),
                            accentBorder = vaultColors.purchaseAccent.copy(alpha = 0.45f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.Inventory2,
                                        contentDescription = null,
                                        tint = vaultColors.purchaseAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    StatusPill(label = "CLAIM READY PACKAGE", color = vaultColors.purchaseAccent)
                                }
                                StatusPill(label = activeStatus.label.uppercase(), color = statusColor)
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(purchase.productName, style = MaterialTheme.typography.titleMedium)
                            if (purchase.brand.isNotBlank()) {
                                Text(
                                    text = "Brand: ${purchase.brand}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = vaultColors.goldAccent
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(color = vaultColors.glassBorder)
                            Spacer(Modifier.height(8.dp))

                            ClaimPackageRow("Shop / Merchant", purchase.merchantName)
                            ClaimPackageRow("Purchase Date", DateAndCurrencyUtils.formatDate(purchase.purchaseDateMillis))
                            ClaimPackageRow("Amount Paid", DateAndCurrencyUtils.formatInr(purchase.purchasePriceInr))
                            ClaimPackageRow("Invoice No.", purchase.invoiceNumber.ifBlank { "Not listed" })
                            ClaimPackageRow(
                                "Warranty Coverage",
                                buildString {
                                    purchase.warrantyDurationMonths?.let { append("${it} months") }
                                    if (purchase.warrantyExpiryMillis != null) {
                                        if (isNotEmpty()) append(" • ")
                                        append("Until ${DateAndCurrencyUtils.formatDate(purchase.warrantyExpiryMillis)}")
                                    }
                                    if (isEmpty()) append("Not recorded")
                                }
                            )
                            ClaimPackageRow(
                                "Original Receipt",
                                when (purchase.resolvedAttachmentType()) {
                                    AttachmentType.IMAGE -> "Receipt Photo Attached"
                                    AttachmentType.PDF -> "PDF Invoice Attached"
                                    AttachmentType.NONE -> "Digital Invoice Summary"
                                }
                            )

                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                VaultGlassOutlinedButton(
                                    onClick = { showReceiptDialog = true },
                                    colors = vaultColors.brandButtonGradient,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("claim_preview_receipt_button")
                                ) {
                                    Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Receipt Proof", maxLines = 1)
                                }
                                VaultGlassOutlinedButton(
                                    onClick = {
                                        val pkgText = buildClaimPackageText(
                                            purchase = purchase,
                                            issueDescription = issueDescription,
                                            status = activeStatus,
                                            claimNotes = claimNotes,
                                            emailSubject = subject,
                                            emailBody = body
                                        )
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("VAULTORA Warranty Claim Package", pkgText))
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Complete claim package copied to clipboard")
                                        }
                                    },
                                    colors = vaultColors.purchaseButtonGradient,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("copy_claim_package_button")
                                ) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Copy Package", maxLines = 1)
                                }
                            }
                        }
                    }

                    // Manual Claim Status Tracking & Notes
                    item {
                        GlassVaultCard(
                            modifier = Modifier.fillMaxWidth(),
                            accentBorder = statusColor.copy(alpha = 0.45f)
                        ) {
                            Text("Claim Status Tracking", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Track your manual claim progress. VAULTORA never pretends a claim has been auto-submitted.",
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
                                WarrantyClaimStatus.entries.forEach { st ->
                                    val stColor = claimStatusColor(st, vaultColors)
                                    FilterChip(
                                        selected = activeStatus == st,
                                        onClick = {
                                            selectedClaimStatusName = st.name
                                            vaultViewModel.updateWarrantyClaimDetails(
                                                purchaseId = purchase.id,
                                                claimStatus = st,
                                                claimNotes = claimNotes,
                                                claimIssueSummary = issueDescription
                                            )
                                        },
                                        label = { Text(st.label, maxLines = 1) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = stColor.copy(alpha = 0.22f),
                                            selectedLabelColor = stColor
                                        ),
                                        modifier = Modifier.testTag("claim_status_chip_${st.name.lowercase()}")
                                    )
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = claimNotes,
                                onValueChange = { claimNotes = it },
                                label = { Text("Claim Notes / Ticket or Case ID (Optional)") },
                                minLines = 2,
                                maxLines = 4,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("warranty_claim_notes_input")
                            )
                            Spacer(Modifier.height(10.dp))
                            VaultGlassOutlinedButton(
                                onClick = {
                                    vaultViewModel.updateWarrantyClaimDetails(
                                        purchaseId = purchase.id,
                                        claimStatus = activeStatus,
                                        claimNotes = claimNotes,
                                        claimIssueSummary = issueDescription
                                    )
                                },
                                colors = vaultColors.goldButtonGradient,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("save_claim_status_notes_button")
                            ) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Save Claim Status & Notes")
                            }

                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = vaultColors.glassBorder)
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
                                    Icon(
                                        imageVector = Icons.Outlined.EventAvailable,
                                        contentDescription = null,
                                        tint = vaultColors.goldAccent
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text("Follow-Up Reminder in Priority Center", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "Set a target date to follow up on this claim",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = purchase.warrantyClaimActive,
                                    onCheckedChange = { checked ->
                                        val suggestedDate = purchase.warrantyClaimFollowUpMillis
                                            ?: (System.currentTimeMillis() + TimeUnit.DAYS.toMillis(3L))
                                        vaultViewModel.updateWarrantyClaimFollowUp(
                                            purchaseId = purchase.id,
                                            active = checked,
                                            followUpMillis = if (checked) suggestedDate else null,
                                            clearFollowUp = !checked
                                        )
                                    },
                                    modifier = Modifier.testTag("warranty_claim_followup_switch")
                                )
                            }
                            if (purchase.warrantyClaimActive) {
                                Spacer(Modifier.height(10.dp))
                                VaultDatePickerField(
                                    label = "Claim Follow-Up Target Date",
                                    selectedMillis = purchase.warrantyClaimFollowUpMillis,
                                    onDateSelected = { millis ->
                                        vaultViewModel.updateWarrantyClaimFollowUp(
                                            purchaseId = purchase.id,
                                            active = true,
                                            followUpMillis = millis,
                                            clearFollowUp = millis == null
                                        )
                                    },
                                    optional = true,
                                    accentColor = vaultColors.goldAccent,
                                    testTag = "warranty_claim_followup_date"
                                )
                            }
                        }
                    }

                    // Issue Description & AI Email Drafting
                    item {
                        GlassVaultCard(modifier = Modifier.fillMaxWidth()) {
                            Text("Describe the Product Problem", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                quickIssues.forEach { preset ->
                                    AssistChip(
                                        onClick = { issueDescription = preset },
                                        label = { Text(preset) }
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = issueDescription,
                                onValueChange = { issueDescription = it },
                                label = { Text("What went wrong with the product?") },
                                minLines = 3,
                                maxLines = 5,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("warranty_issue_input")
                            )
                            Spacer(Modifier.height(14.dp))
                            VaultGradientButton(
                                onClick = {
                                    vaultViewModel.updateWarrantyClaimDetails(
                                        purchaseId = purchase.id,
                                        claimStatus = activeStatus,
                                        claimNotes = claimNotes,
                                        claimIssueSummary = issueDescription
                                    )
                                    claimViewModel.generateClaimEmail(
                                        purchase = purchase,
                                        issueDescription = issueDescription,
                                        customerName = customerName
                                    )
                                },
                                enabled = draftState !is ClaimDraftState.Loading && draftState !is ClaimDraftState.Streaming,
                                colors = vaultColors.purchaseButtonGradient,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("generate_claim_email_button")
                            ) {
                                if (draftState is ClaimDraftState.Loading || draftState is ClaimDraftState.Streaming) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Drafting with AI...")
                                } else {
                                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Generate Claim Email")
                                }
                            }
                        }
                    }

                    // Error banner if AI fails
                    if (draftState is ClaimDraftState.Error) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = vaultColors.statusExpired.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, vaultColors.statusExpired.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = (draftState as ClaimDraftState.Error).message,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }

                    // Editable Email Preview & Manual Send Actions
                    item {
                        GlassVaultCard(modifier = Modifier.fillMaxWidth()) {
                            Text("Editable Claim Email", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Manual submission only: review, edit, and copy or open in your email app. Claims are never sent automatically.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = subject,
                                onValueChange = claimViewModel::updateSubject,
                                label = { Text("Email Subject") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("warranty_email_subject_input")
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = body,
                                onValueChange = claimViewModel::updateBody,
                                label = { Text("Email Body") },
                                minLines = 7,
                                maxLines = 14,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("warranty_email_body_input")
                            )
                            Spacer(Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                VaultGlassOutlinedButton(
                                    onClick = {
                                        val fullEmail = "Subject: $subject\n\n$body"
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Warranty Claim Email", fullEmail))
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Email copied to clipboard")
                                        }
                                    },
                                    enabled = subject.isNotBlank() || body.isNotBlank(),
                                    colors = vaultColors.purchaseButtonGradient,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("copy_claim_email_button")
                                ) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Copy Email")
                                }
                                VaultGradientButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                                            data = Uri.parse("mailto:")
                                            putExtra(Intent.EXTRA_SUBJECT, subject)
                                            putExtra(Intent.EXTRA_TEXT, body)
                                        }
                                        try {
                                            context.startActivity(Intent.createChooser(intent, "Open Email App"))
                                        } catch (_: Exception) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("No email app found on device.")
                                            }
                                        }
                                    },
                                    enabled = subject.isNotBlank() || body.isNotBlank(),
                                    colors = vaultColors.brandButtonGradient,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("open_email_app_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Open Email")
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    if (showReceiptDialog && purchase != null) {
        Dialog(onDismissRequest = { showReceiptDialog = false }) {
            GlassVaultCard(
                modifier = Modifier.fillMaxWidth(),
                accentBorder = vaultColors.purchaseAccent
            ) {
                Text("Receipt & Warranty Proof", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                when {
                    purchase.attachmentUri != null && purchase.attachmentType == AttachmentType.PDF.name && pdfBitmaps.isNotEmpty() -> {
                        Image(
                            bitmap = pdfBitmaps.first().asImageBitmap(),
                            contentDescription = "PDF Receipt Proof",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                    purchase.attachmentUri != null && purchase.attachmentType == AttachmentType.IMAGE.name -> {
                        AsyncImage(
                            model = purchase.attachmentUri,
                            contentDescription = "Receipt Proof Photo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                    else -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = vaultColors.glassSurface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Outlined.Description, contentDescription = null, tint = vaultColors.purchaseAccent)
                                ClaimPackageRow("Product", purchase.productName)
                                if (purchase.brand.isNotBlank()) ClaimPackageRow("Brand", purchase.brand)
                                ClaimPackageRow("Shop", purchase.merchantName)
                                ClaimPackageRow("Invoice #", purchase.invoiceNumber.ifBlank { "N/A" })
                                ClaimPackageRow("Amount", DateAndCurrencyUtils.formatInr(purchase.purchasePriceInr))
                                ClaimPackageRow("Date", DateAndCurrencyUtils.formatDate(purchase.purchaseDateMillis))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                VaultGradientButton(
                    onClick = { showReceiptDialog = false },
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
private fun ClaimPackageRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
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
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.58f)
        )
    }
}

private fun claimStatusColor(status: WarrantyClaimStatus, vaultColors: com.example.ui.theme.VaultExtendedColors): Color =
    when (status) {
        WarrantyClaimStatus.NOT_SUBMITTED -> vaultColors.purchaseAccent
        WarrantyClaimStatus.SUBMITTED -> vaultColors.goldAccent
        WarrantyClaimStatus.UNDER_REVIEW -> vaultColors.statusWarning
        WarrantyClaimStatus.RESOLVED -> vaultColors.statusValid
        WarrantyClaimStatus.REJECTED -> vaultColors.statusExpired
    }

private fun buildClaimPackageText(
    purchase: com.example.model.PurchaseRecord,
    issueDescription: String,
    status: WarrantyClaimStatus,
    claimNotes: String,
    emailSubject: String,
    emailBody: String
): String = buildString {
    appendLine("=== VAULTORA WARRANTY CLAIM PACKAGE ===")
    appendLine("Product: ${purchase.productName}")
    if (purchase.brand.isNotBlank()) appendLine("Brand: ${purchase.brand}")
    appendLine("Shop / Merchant: ${purchase.merchantName}")
    appendLine("Purchase Date: ${DateAndCurrencyUtils.formatDate(purchase.purchaseDateMillis)}")
    appendLine("Amount: ${DateAndCurrencyUtils.formatInr(purchase.purchasePriceInr)}")
    appendLine("Invoice No.: ${purchase.invoiceNumber.ifBlank { "N/A" }}")
    if (purchase.warrantyDurationMonths != null) {
        appendLine("Warranty Duration: ${purchase.warrantyDurationMonths} months")
    }
    if (purchase.warrantyExpiryMillis != null) {
        appendLine("Warranty Expiry: ${DateAndCurrencyUtils.formatDate(purchase.warrantyExpiryMillis)}")
    }
    appendLine("Claim Status: ${status.label}")
    if (issueDescription.isNotBlank()) appendLine("Reported Problem: $issueDescription")
    if (claimNotes.isNotBlank()) appendLine("Claim Notes: $claimNotes")
    if (emailSubject.isNotBlank() || emailBody.isNotBlank()) {
        appendLine()
        appendLine("--- Drafted Email ---")
        appendLine("Subject: $emailSubject")
        appendLine()
        appendLine(emailBody)
    }
}

