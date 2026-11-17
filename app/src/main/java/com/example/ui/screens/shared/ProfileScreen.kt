package com.example.ui.screens.shared

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.model.UiState
import com.example.ui.components.GlassVaultCard
import com.example.ui.components.MetricStatTile
import com.example.ui.components.ProvideSafeTextToolbar
import com.example.ui.components.StatusPill
import com.example.ui.components.VaultAnimatedEntrance
import com.example.ui.components.VaultAtmosphereBackground
import com.example.ui.components.VaultGlassOutlinedButton
import com.example.ui.components.VaultGradientButton
import com.example.ui.components.VaultTopBar
import com.example.ui.theme.LocalVaultColors
import com.example.util.DateAndCurrencyUtils
import com.example.viewmodel.VaultViewModel

@Composable
fun ProfileScreen(
    viewModel: VaultViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val profileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val purchasesState by viewModel.purchasesState.collectAsStateWithLifecycle()
    val medicinesState by viewModel.medicinesState.collectAsStateWithLifecycle()
    val vaultColors = LocalVaultColors.current

    val profile = (profileState as? UiState.Success)?.data
    val purchases = (purchasesState as? UiState.Success)?.data.orEmpty()
    val medicines = (medicinesState as? UiState.Success)?.data.orEmpty()

    var showEditDialog by remember { mutableStateOf(false) }
    var editedName by remember(profile?.fullName) { mutableStateOf(profile?.fullName.orEmpty()) }

    VaultAtmosphereBackground(
        accentColor = vaultColors.goldAccent,
        secondaryAccent = vaultColors.purchaseAccent
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                VaultTopBar(
                    title = "Profile",
                    subtitle = "VAULTORA Account",
                    onBack = onBack
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("profile_screen"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Avatar & Identity Card
                item {
                    VaultAnimatedEntrance(delayMillis = 20) {
                    GlassVaultCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentBorder = vaultColors.goldAccent.copy(alpha = 0.45f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                modifier = Modifier.size(92.dp),
                                shape = CircleShape,
                                color = vaultColors.goldContainer,
                                border = BorderStroke(2.dp, vaultColors.goldAccent)
                            ) {
                                if (!profile?.photoUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = profile?.photoUrl,
                                        contentDescription = "User avatar",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(vaultColors.goldContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Person,
                                            contentDescription = "Avatar",
                                            tint = vaultColors.goldAccent,
                                            modifier = Modifier.size(42.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = profile?.fullName?.ifBlank { "Vault Owner" } ?: "Vault Owner",
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Text(
                                text = profile?.email.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                            StatusPill(label = "GOOGLE VERIFIED VAULT", color = vaultColors.statusValid)
                            Spacer(Modifier.height(16.dp))

                            VaultGradientButton(
                                onClick = {
                                    editedName = profile?.fullName.orEmpty()
                                    showEditDialog = true
                                },
                                colors = vaultColors.brandButtonGradient,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("edit_profile_button")
                            ) {
                                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Edit Profile", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    }
                }

                // Vault Summary Stats
                item {
                    VaultAnimatedEntrance(delayMillis = 70) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricStatTile(
                            label = "Receipts",
                            value = purchases.size.toString(),
                            icon = Icons.Outlined.ReceiptLong,
                            accent = vaultColors.purchaseAccent,
                            modifier = Modifier.weight(1f)
                        )
                        MetricStatTile(
                            label = "Medicines",
                            value = medicines.size.toString(),
                            icon = Icons.Outlined.Medication,
                            accent = vaultColors.medicineAccent,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    }
                }

                // Account Information
                item {
                    VaultAnimatedEntrance(delayMillis = 120) {
                    GlassVaultCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = vaultColors.goldAccent)
                            Spacer(Modifier.width(8.dp))
                            Text("Account Information", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = vaultColors.glassBorder)
                        Spacer(Modifier.height(12.dp))

                        ProfileInfoRow("Vault ID", viewModel.currentUserId.take(12) + "...")
                        Spacer(Modifier.height(8.dp))
                        ProfileInfoRow("Tracked Value", DateAndCurrencyUtils.formatInr(purchases.sumOf { it.purchasePriceInr }))
                        Spacer(Modifier.height(8.dp))
                        ProfileInfoRow("Theme", profile?.resolvedTheme()?.name ?: "DARK")
                        Spacer(Modifier.height(8.dp))
                        ProfileInfoRow(
                            "Member Since",
                            DateAndCurrencyUtils.formatDate(profile?.createdAt?.toDate()?.time)
                        )
                    }
                    }
                }

                item {
                    VaultGlassOutlinedButton(
                        onClick = onOpenSettings,
                        colors = vaultColors.brandButtonGradient,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("profile_open_settings_button")
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open Vault Settings")
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Profile Name") },
            text = {
                ProvideSafeTextToolbar {
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        label = { Text("Full Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_profile_name_input")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editedName.isNotBlank()) {
                            viewModel.updateProfileName(editedName.trim())
                            showEditDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
