package com.example.ui.screens.shared

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.UiState
import com.example.ui.components.StatusPill
import com.example.ui.components.VaultAnimatedEntrance
import com.example.ui.components.VaultAtmosphereBackground
import com.example.ui.components.VaultEmptyState
import com.example.ui.components.VaultGlassSearchBar
import com.example.ui.screens.medicine.MedicineListCard
import com.example.ui.screens.purchase.PurchaseListCard
import com.example.ui.theme.LocalVaultColors
import com.example.util.DateAndCurrencyUtils
import com.example.viewmodel.VaultViewModel

private enum class SearchScope(val label: String) {
    ALL("All Vaults"),
    PURCHASE("Receipts"),
    WARRANTIES("Warranties"),
    MEDICINE("Medicines")
}

@Composable
fun GlobalSearchScreen(
    viewModel: VaultViewModel,
    bottomContentPadding: Dp = 0.dp,
    onOpenPurchaseDetail: (String) -> Unit,
    onOpenMedicineDetail: (String) -> Unit
) {
    val purchasesState by viewModel.purchasesState.collectAsStateWithLifecycle()
    val medicinesState by viewModel.medicinesState.collectAsStateWithLifecycle()
    val vaultColors = LocalVaultColors.current

    val purchases = (purchasesState as? UiState.Success)?.data.orEmpty()
    val medicines = (medicinesState as? UiState.Success)?.data.orEmpty()

    var query by rememberSaveable { mutableStateOf("") }
    var scopeFilter by rememberSaveable { mutableStateOf(SearchScope.ALL) }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }

    val categories = remember(purchases) {
        purchases.map { it.category.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    val q = query.trim()
    val matchingPurchases = if (scopeFilter == SearchScope.MEDICINE) emptyList() else {
        purchases.filter { p ->
            val hasWarranty = p.warrantyExpiryMillis != null || (p.warrantyDurationMonths ?: 0L) > 0L
            val wStatus = DateAndCurrencyUtils.warrantyStatus(p)
            val matchesScope = scopeFilter != SearchScope.WARRANTIES || hasWarranty
            val matchesCat = selectedCategory == null || p.category.equals(selectedCategory, ignoreCase = true)
            val matchesQuery = q.isEmpty() ||
                p.productName.contains(q, ignoreCase = true) ||
                p.merchantName.contains(q, ignoreCase = true) ||
                p.invoiceNumber.contains(q, ignoreCase = true) ||
                p.category.contains(q, ignoreCase = true) ||
                (p.notes?.contains(q, ignoreCase = true) == true) ||
                ("warranty".contains(q, ignoreCase = true) && q.length >= 3 && hasWarranty) ||
                (wStatus.name.replace('_', ' ').contains(q, ignoreCase = true) && q.length >= 3)
            matchesScope && matchesCat && matchesQuery
        }
    }
    val matchingMedicines = if (scopeFilter == SearchScope.PURCHASE ||
        scopeFilter == SearchScope.WARRANTIES ||
        selectedCategory != null
    ) emptyList() else {
        medicines.filter { m ->
            q.isEmpty() ||
                m.medicineName.contains(q, ignoreCase = true) ||
                m.batchNumber.contains(q, ignoreCase = true) ||
                (m.storageNote?.contains(q, ignoreCase = true) == true) ||
                ("medicine meds".contains(q, ignoreCase = true) && q.length >= 3)
        }
    }

    VaultAtmosphereBackground(
        accentColor = vaultColors.purchaseAccent,
        secondaryAccent = vaultColors.medicineAccent
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("global_search_screen"),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 16.dp,
                bottom = 16.dp + bottomContentPadding
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                VaultAnimatedEntrance(delayMillis = 30) {
                    Column {
                        Text(
                            text = "Unified Search",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Search receipts, warranties, categories, medicines & batch numbers",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        VaultGlassSearchBar(
                            query = query,
                            onQueryChange = { query = it },
                            placeholder = "Try \"MacBook\", \"Electronics\", \"Warranty\", or \"Dolo\"",
                            accentColors = vaultColors.brandButtonGradient,
                            testTag = "global_search_input"
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SearchScope.entries.forEach { scopeOption ->
                                val chipColor = when (scopeOption) {
                                    SearchScope.ALL -> vaultColors.goldAccent
                                    SearchScope.PURCHASE -> vaultColors.purchaseAccent
                                    SearchScope.WARRANTIES -> MaterialTheme.colorScheme.primary
                                    SearchScope.MEDICINE -> vaultColors.medicineAccent
                                }
                                FilterChip(
                                    selected = scopeFilter == scopeOption && selectedCategory == null,
                                    onClick = {
                                        scopeFilter = scopeOption
                                        selectedCategory = null
                                    },
                                    label = { Text(scopeOption.label, maxLines = 1) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = chipColor.copy(alpha = 0.22f),
                                        selectedLabelColor = chipColor
                                    )
                                )
                            }
                            categories.forEach { cat ->
                                val catSelected = selectedCategory == cat
                                FilterChip(
                                    selected = catSelected,
                                    onClick = {
                                        if (catSelected) {
                                            selectedCategory = null
                                        } else {
                                            selectedCategory = cat
                                            if (scopeFilter == SearchScope.MEDICINE) {
                                                scopeFilter = SearchScope.ALL
                                            }
                                        }
                                    },
                                    label = { Text(cat, maxLines = 1) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = vaultColors.purchaseAccent.copy(alpha = 0.22f),
                                        selectedLabelColor = vaultColors.purchaseAccent
                                    )
                                )
                            }
                        }
                    }
                }
            }

            if (matchingPurchases.isEmpty() && matchingMedicines.isEmpty()) {
                item {
                    VaultEmptyState(
                        icon = Icons.Outlined.Search,
                        title = "No matching records",
                        subtitle = "Search across receipts, warranties, and medicines by name, category, merchant, invoice #, or batch #.",
                        accent = vaultColors.goldAccent
                    )
                }
            } else {
                if (matchingPurchases.isNotEmpty()) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatusPill(label = "PURCHASE VAULT", color = vaultColors.purchaseAccent)
                            Text(
                                text = "${matchingPurchases.size} results",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(matchingPurchases, key = { "sp_${it.id}" }) { p ->
                        PurchaseListCard(
                            purchase = p,
                            onClick = { onOpenPurchaseDetail(p.id) }
                        )
                    }
                }

                if (matchingMedicines.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatusPill(label = "MEDICINE VAULT", color = vaultColors.medicineAccent)
                            Text(
                                text = "${matchingMedicines.size} results",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(matchingMedicines, key = { "sm_${it.id}" }) { m ->
                        MedicineListCard(
                            medicine = m,
                            onClick = { onOpenMedicineDetail(m.id) }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
