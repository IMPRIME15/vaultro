package com.example.ui.screens.medicine

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.model.ImportanceLevel
import com.example.model.MedicineRecord
import com.example.model.UiState
import com.example.notifications.VaultReminderScheduler
import com.example.ui.components.GlassVaultCard
import com.example.ui.components.ImportanceSelectorRow
import com.example.ui.components.VaultAtmosphereBackground
import com.example.ui.components.VaultDatePickerField
import com.example.ui.components.VaultGlassOutlinedButton
import com.example.ui.components.VaultGradientButton
import com.example.ui.components.VaultTopBar
import com.example.ui.theme.LocalVaultColors
import com.example.util.FileAttachmentUtils
import com.example.viewmodel.VaultViewModel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@Composable
fun AddEditMedicineScreen(
    viewModel: VaultViewModel,
    editingMedicineId: String? = null,
    onBack: () -> Unit,
    onSaved: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vaultColors = LocalVaultColors.current

    val medicinesState by viewModel.medicinesState.collectAsStateWithLifecycle()
    val existingMedicine = remember(medicinesState, editingMedicineId) {
        if (editingMedicineId.isNullOrBlank()) null
        else (medicinesState as? UiState.Success)?.data?.firstOrNull { it.id == editingMedicineId }
    }

    var initialized by rememberSaveable(editingMedicineId) { mutableStateOf(false) }
    var medicineName by rememberSaveable { mutableStateOf("") }
    var batchNumber by rememberSaveable { mutableStateOf("") }
    var expiryDateMillis by rememberSaveable {
        mutableStateOf(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(90))
    }
    var purchaseDateMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var quantity by rememberSaveable { mutableStateOf("") }
    var storageNote by rememberSaveable { mutableStateOf("") }
    var photoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var reminderEnabled by rememberSaveable { mutableStateOf(true) }
    var reminderDaysText by rememberSaveable { mutableStateOf("14") }
    var importanceLevel by rememberSaveable { mutableStateOf("") }

    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(existingMedicine) {
        if (existingMedicine != null && !initialized) {
            medicineName = existingMedicine.medicineName
            batchNumber = existingMedicine.batchNumber
            expiryDateMillis = existingMedicine.expiryDateMillis
            purchaseDateMillis = existingMedicine.purchaseDateMillis
            quantity = existingMedicine.quantity.orEmpty()
            storageNote = existingMedicine.storageNote.orEmpty()
            photoUri = existingMedicine.photoUri
            reminderEnabled = existingMedicine.reminderEnabled
            reminderDaysText = existingMedicine.reminderDaysBefore.toString()
            importanceLevel = existingMedicine.importanceLevel
            initialized = true
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
                if (savedUri != null) photoUri = savedUri
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
            formError = "Camera permission is required to photograph medicine packaging."
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val savedUri = FileAttachmentUtils.copyUriToInternalStorage(context, uri, "jpg")
                if (savedUri != null) photoUri = savedUri
            }
        }
    }

    VaultAtmosphereBackground(
        accentColor = vaultColors.medicineAccent,
        secondaryAccent = vaultColors.goldAccent
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                VaultTopBar(
                    title = if (existingMedicine != null) "Edit Medicine" else "Add Medicine",
                    subtitle = "Medicine Vault",
                    onBack = onBack
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("add_edit_medicine_screen"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Medicine Photo Card
                item {
                    GlassVaultCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentBorder = vaultColors.medicineAccent.copy(alpha = 0.45f)
                    ) {
                        Text("Medicine Photograph", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(vaultColors.medicineAccent.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (photoUri != null) {
                                AsyncImage(
                                    model = photoUri,
                                    contentDescription = "Medicine photo preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Outlined.Medication,
                                        contentDescription = null,
                                        tint = vaultColors.medicineAccent,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "Add a box or strip photograph",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            VaultGlassOutlinedButton(
                                onClick = {
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
                                },
                                colors = vaultColors.medicineButtonGradient,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("medicine_camera_button")
                            ) {
                                Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Take Photo")
                            }
                            VaultGlassOutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                colors = vaultColors.brandButtonGradient,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("medicine_gallery_button")
                            ) {
                                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Choose Image")
                            }
                        }
                    }
                }

                // Form Fields
                item {
                    GlassVaultCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Medicine Storage Details", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = medicineName,
                            onValueChange = { medicineName = it; formError = null },
                            label = { Text("Medicine name *") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("medicine_name_input")
                        )

                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = batchNumber,
                            onValueChange = { batchNumber = it },
                            label = { Text("Batch number") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("medicine_batch_input")
                        )

                        Spacer(Modifier.height(12.dp))

                        VaultDatePickerField(
                            label = "Expiry Date *",
                            selectedMillis = expiryDateMillis,
                            onDateSelected = { millis -> if (millis != null) expiryDateMillis = millis },
                            accentColor = vaultColors.medicineAccent,
                            testTag = "medicine_expiry_date_picker"
                        )

                        Spacer(Modifier.height(10.dp))

                        VaultDatePickerField(
                            label = "Purchase Date (Optional)",
                            selectedMillis = purchaseDateMillis,
                            onDateSelected = { purchaseDateMillis = it },
                            optional = true,
                            accentColor = vaultColors.goldAccent,
                            testTag = "medicine_purchase_date_picker"
                        )

                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = quantity,
                            onValueChange = { quantity = it },
                            label = { Text("Quantity (Optional, e.g. 2 strips)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("medicine_quantity_input")
                        )

                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = storageNote,
                            onValueChange = { storageNote = it },
                            label = { Text("Storage shelf / note (Optional)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(12.dp))

                        ImportanceSelectorRow(
                            selected = ImportanceLevel.fromRawOrNull(importanceLevel),
                            onSelect = { importanceLevel = it?.name ?: "" },
                            accentColor = vaultColors.medicineAccent,
                            tagPrefix = "medicine_form_importance"
                        )

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Expiry reminder alert", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = reminderEnabled,
                                onCheckedChange = { reminderEnabled = it }
                            )
                        }

                        if (reminderEnabled) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = reminderDaysText,
                                onValueChange = { reminderDaysText = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Remind me days before expiry (1–90)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
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
                                if (medicineName.isBlank()) {
                                    formError = "Please enter the medicine name."
                                    return@VaultGradientButton
                                }
                                isSaving = true
                                formError = null
                                if (reminderEnabled &&
                                    !VaultReminderScheduler.hasNotificationPermission(context) &&
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                ) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                val leadDays = (reminderDaysText.toLongOrNull() ?: 14L).coerceIn(1L, 90L)
                                val record = MedicineRecord(
                                    id = existingMedicine?.id.orEmpty(),
                                    userId = viewModel.currentUserId,
                                    medicineName = medicineName.trim(),
                                    batchNumber = batchNumber.trim(),
                                    expiryDateMillis = expiryDateMillis,
                                    purchaseDateMillis = purchaseDateMillis,
                                    quantity = quantity.trim().takeIf { it.isNotBlank() },
                                    photoUri = photoUri,
                                    storageNote = storageNote.trim().takeIf { it.isNotBlank() },
                                    reminderEnabled = reminderEnabled,
                                    reminderDaysBefore = leadDays,
                                    importanceLevel = importanceLevel,
                                    completed = existingMedicine?.completed ?: false,
                                    completedAtMillis = existingMedicine?.completedAtMillis
                                )
                                scope.launch {
                                    viewModel.saveMedicine(record)
                                        .onSuccess { savedId ->
                                            isSaving = false
                                            VaultReminderScheduler.scheduleMedicineReminder(
                                                context = context,
                                                medicine = record.copy(id = savedId)
                                            )
                                            onSaved(savedId)
                                        }
                                        .onFailure { err ->
                                            isSaving = false
                                            formError = err.localizedMessage ?: "Failed to save medicine."
                                        }
                                }
                            },
                            enabled = !isSaving,
                            colors = vaultColors.medicineButtonGradient,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("save_medicine_button")
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = if (existingMedicine != null) "Update Medicine" else "Save to Medicine Vault",
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
