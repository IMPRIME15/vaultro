package com.example.ui.screens.purchase

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.model.AttachmentType
import com.example.model.ImportanceLevel
import com.example.model.PurchaseRecord
import com.example.model.UiState
import com.example.notifications.VaultReminderScheduler
import com.example.ui.components.GlassVaultCard
import com.example.ui.components.ImportanceSelectorRow
import com.example.ui.components.StatusPill
import com.example.ui.components.VaultAtmosphereBackground
import com.example.ui.components.VaultDatePickerField
import com.example.ui.components.VaultGlassOutlinedButton
import com.example.ui.components.VaultGradientButton
import com.example.ui.components.VaultTopBar
import com.example.ui.theme.LocalVaultColors
import com.example.util.DateAndCurrencyUtils
import com.example.util.FileAttachmentUtils
import com.example.viewmodel.ReceiptScanState
import com.example.viewmodel.ReceiptScannerViewModel
import com.example.viewmodel.VaultViewModel
import kotlinx.coroutines.launch

@Composable
fun AddScanReceiptScreen(
    viewModel: VaultViewModel,
    editingPurchaseId: String? = null,
    autoScanMode: Boolean = false,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onOpenExistingPurchase: ((String) -> Unit)? = null,
    scannerViewModel: ReceiptScannerViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vaultColors = LocalVaultColors.current

    val purchasesState by viewModel.purchasesState.collectAsStateWithLifecycle()
    val profileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val scanState by scannerViewModel.scanState.collectAsStateWithLifecycle()

    val allPurchases = (purchasesState as? UiState.Success)?.data.orEmpty()
    val existingPurchase = remember(allPurchases, editingPurchaseId) {
        if (editingPurchaseId.isNullOrBlank()) null
        else allPurchases.firstOrNull { it.id == editingPurchaseId }
    }
    val profile = (profileState as? UiState.Success)?.data

    var initialized by rememberSaveable(editingPurchaseId) { mutableStateOf(false) }
    var productName by rememberSaveable { mutableStateOf("") }
    var brand by rememberSaveable { mutableStateOf("") }
    var merchantName by rememberSaveable { mutableStateOf("") }
    var priceText by rememberSaveable { mutableStateOf("") }
    var purchaseDateMillis by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var invoiceNumber by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Electronics") }
    var returnDeadlineMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var warrantyMonthsText by rememberSaveable { mutableStateOf("") }
    var warrantyExpiryMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var notes by rememberSaveable { mutableStateOf("") }
    var returnReminderSet by rememberSaveable { mutableStateOf(true) }
    var warrantyReminderSet by rememberSaveable { mutableStateOf(true) }
    var importanceLevel by rememberSaveable { mutableStateOf("") }
    var attachmentUri by rememberSaveable { mutableStateOf<String?>(null) }
    var attachmentType by rememberSaveable { mutableStateOf(AttachmentType.NONE.name) }
    var pdfPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var autoSaveAfterOcr by rememberSaveable(autoScanMode, editingPurchaseId) {
        mutableStateOf(autoScanMode && editingPurchaseId.isNullOrBlank())
    }
    var showRawOcrText by rememberSaveable { mutableStateOf(false) }
    var autoSavedUri by rememberSaveable { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(existingPurchase) {
        if (existingPurchase != null && !initialized) {
            productName = existingPurchase.productName
            brand = existingPurchase.brand
            merchantName = existingPurchase.merchantName
            priceText = if (existingPurchase.purchasePriceInr % 1.0 == 0.0) {
                existingPurchase.purchasePriceInr.toLong().toString()
            } else {
                existingPurchase.purchasePriceInr.toString()
            }
            purchaseDateMillis = existingPurchase.purchaseDateMillis
            invoiceNumber = existingPurchase.invoiceNumber
            category = existingPurchase.category
            returnDeadlineMillis = existingPurchase.returnDeadlineMillis
            warrantyMonthsText = existingPurchase.warrantyDurationMonths?.toString().orEmpty()
            warrantyExpiryMillis = existingPurchase.warrantyExpiryMillis
            notes = existingPurchase.notes.orEmpty()
            returnReminderSet = existingPurchase.returnReminderSet
            warrantyReminderSet = existingPurchase.warrantyReminderSet
            importanceLevel = existingPurchase.importanceLevel
            attachmentUri = existingPurchase.attachmentUri
            attachmentType = existingPurchase.attachmentType
            initialized = true
        }
    }

    LaunchedEffect(attachmentUri, attachmentType) {
        if (attachmentUri != null && attachmentType == AttachmentType.PDF.name) {
            pdfPreviewBitmap = FileAttachmentUtils.renderPdfPagesToBitmaps(context, attachmentUri!!, 1).firstOrNull()
        } else {
            pdfPreviewBitmap = null
        }
    }

    // Populate form when OCR / AI scan completes, and auto-save to Firestore only when high confidence & no duplicates
    LaunchedEffect(scanState) {
        if (scanState is ReceiptScanState.Success) {
            val result = scanState as ReceiptScanState.Success
            val data = result.extracted
            val diagnostics = result.diagnostics
            val resolvedVendor = data.merchantName.trim()
            val resolvedProduct = data.productName.ifBlank {
                if (resolvedVendor.isNotBlank()) "Purchase at $resolvedVendor" else ""
            }
            if (resolvedProduct.isNotBlank()) productName = resolvedProduct
            if (data.brand.isNotBlank()) brand = data.brand.trim()
            if (resolvedVendor.isNotBlank()) merchantName = resolvedVendor
            if (data.purchasePriceInr != null) {
                priceText = if (data.purchasePriceInr % 1.0 == 0.0) {
                    data.purchasePriceInr.toLong().toString()
                } else {
                    data.purchasePriceInr.toString()
                }
            }
            if (data.purchaseDateMillis != null) purchaseDateMillis = data.purchaseDateMillis
            if (data.invoiceNumber.isNotBlank()) invoiceNumber = data.invoiceNumber
            if (data.category.isNotBlank()) category = data.category
            // Never invent return or warranty dates when null
            returnDeadlineMillis = data.returnDeadlineMillis
            warrantyMonthsText = data.warrantyDurationMonths?.toString().orEmpty()
            warrantyExpiryMillis = data.warrantyExpiryMillis

            val currentUri = attachmentUri
            if (autoSaveAfterOcr &&
                !diagnostics.requiresManualReview &&
                existingPurchase == null &&
                currentUri != null &&
                currentUri != autoSavedUri &&
                resolvedVendor.isNotBlank() &&
                data.purchasePriceInr != null &&
                data.purchasePriceInr > 0.0
            ) {
                autoSavedUri = currentUri
                isSaving = true
                formError = null
                val record = PurchaseRecord(
                    userId = viewModel.currentUserId,
                    productName = resolvedProduct.ifBlank { "Purchase at $resolvedVendor" },
                    brand = data.brand.trim(),
                    merchantName = resolvedVendor,
                    purchasePriceInr = data.purchasePriceInr,
                    purchaseDateMillis = data.purchaseDateMillis ?: purchaseDateMillis,
                    invoiceNumber = data.invoiceNumber.trim(),
                    category = data.category.ifBlank { "General" },
                    attachmentUri = currentUri,
                    attachmentType = attachmentType,
                    returnDeadlineMillis = data.returnDeadlineMillis,
                    warrantyDurationMonths = data.warrantyDurationMonths,
                    warrantyExpiryMillis = data.warrantyExpiryMillis,
                    notes = notes.trim().takeIf { it.isNotBlank() },
                    returnReminderSet = returnReminderSet,
                    warrantyReminderSet = warrantyReminderSet,
                    importanceLevel = importanceLevel
                )
                val saveRes = viewModel.savePurchase(record)
                isSaving = false
                saveRes.onSuccess { savedId ->
                    VaultReminderScheduler.schedulePurchaseReminders(
                        context = context,
                        purchase = record.copy(id = savedId),
                        returnLeadDays = profile?.returnReminderDaysBefore ?: 3L,
                        warrantyLeadDays = profile?.warrantyReminderDaysBefore ?: 14L
                    )
                    scannerViewModel.resetState()
                    onSaved(savedId)
                }.onFailure { err ->
                    formError = err.localizedMessage ?: "OCR succeeded, but saving to Firestore failed."
                    snackbarHostState.showSnackbar(formError!!)
                }
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val path = pendingCameraPath
        if (success && path != null) {
            scope.launch {
                val savedUri = FileAttachmentUtils.copyFilePathToInternalStorage(context, path, "jpg")
                if (savedUri != null) {
                    attachmentUri = savedUri
                    attachmentType = AttachmentType.IMAGE.name
                    scannerViewModel.scanAttachedDocument(
                        context = context,
                        uriString = savedUri,
                        attachmentType = AttachmentType.IMAGE,
                        existingPurchases = allPurchases,
                        editingPurchaseId = editingPurchaseId
                    )
                }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val (uri, path) = FileAttachmentUtils.createCameraImageUri(context)
            pendingCameraPath = path
            cameraLauncher.launch(uri)
        } else {
            formError = "Camera permission is required to photograph physical receipts. You can also upload an image or PDF."
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val savedUri = FileAttachmentUtils.copyUriToInternalStorage(context, uri, "jpg")
                if (savedUri != null) {
                    attachmentUri = savedUri
                    attachmentType = AttachmentType.IMAGE.name
                    scannerViewModel.scanAttachedDocument(
                        context = context,
                        uriString = savedUri,
                        attachmentType = AttachmentType.IMAGE,
                        existingPurchases = allPurchases,
                        editingPurchaseId = editingPurchaseId
                    )
                }
            }
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val savedUri = FileAttachmentUtils.copyUriToInternalStorage(context, uri, "pdf")
                if (savedUri != null) {
                    attachmentUri = savedUri
                    attachmentType = AttachmentType.PDF.name
                    scannerViewModel.scanAttachedDocument(
                        context = context,
                        uriString = savedUri,
                        attachmentType = AttachmentType.PDF,
                        existingPurchases = allPurchases,
                        editingPurchaseId = editingPurchaseId
                    )
                }
            }
        }
    }

    val categories = listOf("Electronics", "Home Appliances", "Kitchen", "Fashion", "Health", "General")

    val launchReceiptCamera = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            val (uri, path) = FileAttachmentUtils.createCameraImageUri(context)
            pendingCameraPath = path
            cameraLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
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
                    title = when {
                        existingPurchase != null -> "Edit Receipt"
                        autoScanMode -> "Receipt OCR Scanner"
                        else -> "Add Purchase Receipt"
                    },
                    subtitle = "Purchase Vault",
                    onBack = onBack
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("add_scan_receipt_screen"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Capture / Upload Card
                item {
                    GlassVaultCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentBorder = vaultColors.purchaseAccent.copy(alpha = 0.45f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.DocumentScanner,
                                contentDescription = null,
                                tint = vaultColors.goldAccent
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Camera Receipt OCR Scanner",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Capture a photo of any receipt to automatically extract Vendor, Date & Total Amount using OCR and save it to Firestore.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(14.dp))

                        VaultGradientButton(
                            onClick = launchReceiptCamera,
                            colors = vaultColors.purchaseButtonGradient,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("capture_receipt_photo_button")
                        ) {
                            Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Scan Receipt", fontWeight = FontWeight.Bold)
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            VaultGlassOutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                colors = vaultColors.brandButtonGradient,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("upload_receipt_image_button")
                            ) {
                                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Upload Image", maxLines = 1)
                            }
                            VaultGlassOutlinedButton(
                                onClick = {
                                    pdfPickerLauncher.launch(arrayOf("application/pdf"))
                                },
                                colors = vaultColors.goldButtonGradient,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("upload_receipt_pdf_button")
                            ) {
                                Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Upload PDF", maxLines = 1)
                            }
                        }

                        if (existingPurchase == null) {
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Auto-save to Firestore after OCR",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Saves automatically when vendor & total are detected",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = autoSaveAfterOcr,
                                    onCheckedChange = { autoSaveAfterOcr = it },
                                    modifier = Modifier.testTag("auto_save_ocr_switch")
                                )
                            }
                        }

                        // Preview attached image or PDF first page
                        if (attachmentUri != null) {
                            Spacer(Modifier.height(14.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF0C0F16))
                            ) {
                                if (attachmentType == AttachmentType.PDF.name && pdfPreviewBitmap != null) {
                                    Image(
                                        bitmap = pdfPreviewBitmap!!.asImageBitmap(),
                                        contentDescription = "PDF invoice preview",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    AsyncImage(
                                        model = attachmentUri,
                                        contentDescription = "Receipt attachment preview",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                StatusPill(
                                    label = if (attachmentType == AttachmentType.PDF.name) "PDF ATTACHED" else "IMAGE ATTACHED",
                                    color = vaultColors.purchaseAccent,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                )
                            }
                        }

                        // Scan progress / result status
                        when (val s = scanState) {
                            is ReceiptScanState.Scanning -> {
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = vaultColors.goldAccent,
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = s.statusText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = vaultColors.goldAccent
                                    )
                                }
                            }
                            is ReceiptScanState.Success -> {
                                Spacer(Modifier.height(12.dp))
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = vaultColors.statusValid.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, vaultColors.statusValid.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    Icons.Outlined.CheckCircle,
                                                    contentDescription = null,
                                                    tint = vaultColors.statusValid,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = if (s.usedAiRefinement) {
                                                        "OCR + AI extracted receipt fields"
                                                    } else {
                                                        "On-device OCR extracted receipt fields"
                                                    },
                                                    style = MaterialTheme.typography.titleSmall
                                                )
                                            }
                                            if (s.rawOcrText.isNotBlank()) {
                                                TextButton(onClick = { showRawOcrText = !showRawOcrText }) {
                                                    Text(
                                                        text = if (showRawOcrText) "Hide OCR" else "View OCR",
                                                        color = vaultColors.goldAccent
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = "Shop: ${s.extracted.merchantName.ifBlank { "Not detected" }}  •  " +
                                                "Brand: ${s.extracted.brand.ifBlank { "Not listed" }}  •  " +
                                                "Date: ${DateAndCurrencyUtils.formatDate(s.extracted.purchaseDateMillis)}  •  " +
                                                "Total: ${s.extracted.purchasePriceInr?.let { DateAndCurrencyUtils.formatInr(it) } ?: "Not detected"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        AnimatedVisibility(visible = showRawOcrText && s.rawOcrText.isNotBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = Color.Black.copy(alpha = 0.25f),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 8.dp)
                                            ) {
                                                Text(
                                                    text = s.rawOcrText,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(10.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // AI Auto-Detection Engine Diagnostics: Unclear receipt, missing fields, duplicate uploads
                                val diag = s.diagnostics
                                if (diag.isUnclearReceipt) {
                                    Spacer(Modifier.height(10.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = vaultColors.statusExpired.copy(alpha = 0.14f),
                                        border = BorderStroke(1.dp, vaultColors.statusExpired.copy(alpha = 0.45f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("unclear_receipt_warning_banner")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.WarningAmber,
                                                contentDescription = "Unclear receipt",
                                                tint = vaultColors.statusExpired,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Unclear Receipt Detected",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = vaultColors.statusExpired
                                                )
                                                Text(
                                                    text = diag.clarityReason ?: "Some invoice text was unclear. Please retake photo or confirm extracted fields below.",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                }

                                if (diag.missingCriticalFields.isNotEmpty() || diag.missingOptionalFields.isNotEmpty()) {
                                    Spacer(Modifier.height(10.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = vaultColors.statusWarning.copy(alpha = 0.13f),
                                        border = BorderStroke(1.dp, vaultColors.statusWarning.copy(alpha = 0.42f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("missing_fields_warning_banner")
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                text = "Missing Information (Not Invented)",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = vaultColors.statusWarning
                                            )
                                            if (diag.missingCriticalFields.isNotEmpty()) {
                                                Text(
                                                    text = "Required to confirm: ${diag.missingCriticalFields.joinToString(", ")}",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                            if (diag.missingOptionalFields.isNotEmpty()) {
                                                Text(
                                                    text = "Not found on receipt: ${diag.missingOptionalFields.joinToString(", ")}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }

                                if (diag.duplicateMatches.isNotEmpty()) {
                                    Spacer(Modifier.height(10.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = vaultColors.goldAccent.copy(alpha = 0.14f),
                                        border = BorderStroke(1.dp, vaultColors.goldAccent.copy(alpha = 0.45f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("duplicate_receipt_warning_banner")
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = "Possible Duplicate Upload",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = vaultColors.goldAccent
                                            )
                                            diag.duplicateMatches.forEach { dup ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "${dup.productName} (${dup.merchantName}, ${dup.purchaseDateLabel}) — ${dup.reason}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    if (onOpenExistingPurchase != null) {
                                                        TextButton(onClick = { onOpenExistingPurchase(dup.existingPurchaseId) }) {
                                                            Text("View", color = vaultColors.goldAccent)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            is ReceiptScanState.Error -> {
                                Spacer(Modifier.height(12.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = vaultColors.statusExpired.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, vaultColors.statusExpired.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = s.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                            ReceiptScanState.Idle -> {}
                        }
                    }
                }

                // Editable Confirmation Form
                item {
                    val liveDestinations = remember(warrantyMonthsText, warrantyExpiryMillis, returnDeadlineMillis) {
                        buildList {
                            add("Purchase Vault")
                            if (warrantyExpiryMillis != null || (warrantyMonthsText.toLongOrNull() ?: 0L) > 0L) {
                                add("Warranty Tracking")
                            }
                            if (returnDeadlineMillis != null) {
                                add("Return Tracking")
                            }
                        }
                    }
                    GlassVaultCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Confirm Extracted Receipt Details",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            liveDestinations.forEach { dest ->
                                StatusPill(label = "AUTO → ${dest.uppercase()}", color = vaultColors.purchaseAccent)
                            }
                        }
                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = productName,
                            onValueChange = { productName = it; formError = null },
                            label = { Text("Product name *") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("receipt_product_name_input")
                        )

                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = brand,
                            onValueChange = { brand = it },
                            label = { Text("Brand / Manufacturer (Optional)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("receipt_brand_input")
                        )

                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = merchantName,
                            onValueChange = { merchantName = it; formError = null },
                            label = { Text("Shop / Merchant / Store name *") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("receipt_merchant_input")
                        )

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = priceText,
                                onValueChange = { priceText = it; formError = null },
                                label = { Text("Total Amount (₹) *") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("receipt_price_input")
                            )
                            OutlinedTextField(
                                value = invoiceNumber,
                                onValueChange = { invoiceNumber = it },
                                label = { Text("Invoice No.") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("receipt_invoice_input")
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = "Category",
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
                            categories.forEach { cat ->
                                FilterChip(
                                    selected = category == cat,
                                    onClick = { category = cat },
                                    label = { Text(cat) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = vaultColors.purchaseAccent.copy(alpha = 0.22f),
                                        selectedLabelColor = vaultColors.purchaseAccent
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        VaultDatePickerField(
                            label = "Purchase Date",
                            selectedMillis = purchaseDateMillis,
                            onDateSelected = { millis ->
                                if (millis != null) {
                                    purchaseDateMillis = millis
                                    val m = warrantyMonthsText.toIntOrNull()
                                    if (m != null && m > 0) {
                                        warrantyExpiryMillis = DateAndCurrencyUtils.addMonthsMillis(millis, m)
                                    }
                                }
                            },
                            accentColor = vaultColors.purchaseAccent,
                            testTag = "receipt_purchase_date_picker"
                        )

                        Spacer(Modifier.height(10.dp))

                        VaultDatePickerField(
                            label = "Return Deadline (Optional)",
                            selectedMillis = returnDeadlineMillis,
                            onDateSelected = { returnDeadlineMillis = it },
                            optional = true,
                            accentColor = vaultColors.statusWarning,
                            testTag = "receipt_return_date_picker"
                        )

                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = warrantyMonthsText,
                            onValueChange = { input ->
                                warrantyMonthsText = input.filter { it.isDigit() }
                                val m = warrantyMonthsText.toIntOrNull()
                                if (m != null && m > 0) {
                                    warrantyExpiryMillis = DateAndCurrencyUtils.addMonthsMillis(purchaseDateMillis, m)
                                }
                            },
                            label = { Text("Warranty duration (months, optional)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("receipt_warranty_months_input")
                        )

                        Spacer(Modifier.height(10.dp))

                        VaultDatePickerField(
                            label = "Warranty Expiry Date (Optional)",
                            selectedMillis = warrantyExpiryMillis,
                            onDateSelected = { warrantyExpiryMillis = it },
                            optional = true,
                            accentColor = vaultColors.statusValid,
                            testTag = "receipt_warranty_date_picker"
                        )

                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Notes / Serial No. (Optional)") },
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(12.dp))

                        ImportanceSelectorRow(
                            selected = ImportanceLevel.fromRawOrNull(importanceLevel),
                            onSelect = { importanceLevel = it?.name ?: "" },
                            accentColor = vaultColors.purchaseAccent,
                            tagPrefix = "receipt_form_importance"
                        )

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Return deadline reminder", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = returnReminderSet,
                                onCheckedChange = { returnReminderSet = it }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Warranty expiry reminder", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = warrantyReminderSet,
                                onCheckedChange = { warrantyReminderSet = it }
                            )
                        }

                        if (!formError.isNullOrBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = formError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = vaultColors.statusExpired
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        VaultGradientButton(
                            onClick = {
                                val parsedPrice = priceText.trim().toDoubleOrNull()
                                when {
                                    productName.isBlank() -> formError = "Please enter the product name."
                                    merchantName.isBlank() -> formError = "Please enter the merchant or store name."
                                    parsedPrice == null || parsedPrice < 0.0 -> formError = "Please enter a valid purchase price in ₹."
                                    else -> {
                                        isSaving = true
                                        formError = null
                                        if ((returnReminderSet && returnDeadlineMillis != null) ||
                                            (warrantyReminderSet && warrantyExpiryMillis != null)
                                        ) {
                                            if (!VaultReminderScheduler.hasNotificationPermission(context) &&
                                                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
                                            ) {
                                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        }
                                        val record = PurchaseRecord(
                                            id = existingPurchase?.id.orEmpty(),
                                            userId = viewModel.currentUserId,
                                            productName = productName.trim(),
                                            brand = brand.trim(),
                                            merchantName = merchantName.trim(),
                                            purchasePriceInr = parsedPrice,
                                            purchaseDateMillis = purchaseDateMillis,
                                            invoiceNumber = invoiceNumber.trim(),
                                            category = category,
                                            attachmentUri = attachmentUri,
                                            attachmentType = attachmentType,
                                            returnDeadlineMillis = returnDeadlineMillis,
                                            warrantyDurationMonths = warrantyMonthsText.toLongOrNull(),
                                            warrantyExpiryMillis = warrantyExpiryMillis,
                                            notes = notes.trim().takeIf { it.isNotBlank() },
                                            returnReminderSet = returnReminderSet,
                                            warrantyReminderSet = warrantyReminderSet,
                                            importanceLevel = importanceLevel,
                                            completed = existingPurchase?.completed ?: false,
                                            completedAtMillis = existingPurchase?.completedAtMillis,
                                            warrantyClaimActive = existingPurchase?.warrantyClaimActive ?: false,
                                            warrantyClaimFollowUpMillis = existingPurchase?.warrantyClaimFollowUpMillis,
                                            claimStatus = existingPurchase?.claimStatus ?: com.example.model.WarrantyClaimStatus.NOT_SUBMITTED.name,
                                            claimNotes = existingPurchase?.claimNotes.orEmpty(),
                                            claimIssueSummary = existingPurchase?.claimIssueSummary.orEmpty(),
                                            claimUpdatedAtMillis = existingPurchase?.claimUpdatedAtMillis
                                        )
                                        scope.launch {
                                            val result = viewModel.savePurchase(record)
                                            isSaving = false
                                            result.onSuccess { savedId ->
                                                VaultReminderScheduler.schedulePurchaseReminders(
                                                    context = context,
                                                    purchase = record.copy(id = savedId),
                                                    returnLeadDays = profile?.returnReminderDaysBefore ?: 3L,
                                                    warrantyLeadDays = profile?.warrantyReminderDaysBefore ?: 14L
                                                )
                                                scannerViewModel.resetState()
                                                onSaved(savedId)
                                            }.onFailure { err ->
                                                formError = err.localizedMessage ?: "Failed to save purchase."
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = !isSaving,
                            colors = vaultColors.purchaseButtonGradient,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("save_receipt_button")
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = if (existingPurchase != null) "Update Receipt" else "Save to Purchase Vault",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
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
