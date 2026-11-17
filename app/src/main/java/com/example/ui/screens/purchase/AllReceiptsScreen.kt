package com.example.ui.screens.purchase

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.UiState
import com.example.ui.components.VaultAnimatedEntrance
import com.example.ui.components.VaultAtmosphereBackground
import com.example.ui.components.VaultEmptyState
import com.example.ui.components.VaultGlassSearchBar
import com.example.ui.components.VaultGradientButton
import com.example.ui.components.VaultTopBar
import com.example.ui.theme.LocalVaultColors
import com.example.viewmodel.VaultViewModel
import java.util.concurrent.TimeUnit

private enum class DateFilter(val label: String) {
    ALL("All Dates"),
    LAST_30("30 Days"),
    LAST_90("90 Days"),
    LAST_YEAR("1 Year")
}

private enum class PriceFilter(val label: String) {
    ALL("All Prices"),
    UNDER_5K("Under ₹5k"),
    MID_5K_25K("₹5k–₹25k"),
    OVER_25K("₹25k+")
}

private enum class ReceiptSort(val label: String) {
    RECENT("Newest"),
    OLDEST("Oldest"),
    PRICE_HIGH("Price ↓"),
    PRICE_LOW("Price ↑")
}

@Composable
fun AllReceiptsScreen(
    viewModel: VaultViewModel,
    onBack: () -> Unit,
    onAddReceipt: () -> Unit,
    onOpenPurchaseDetail: (String) -> Unit
) {
    val purchasesState by viewModel.purchasesState.collectAsStateWithLifecycle()
    val vaultColors = LocalVaultColors.current
    val purchases = (purchasesState as? UiState.Success)?.data.orEmpty()

    var query by rememberSaveable { mutableStateOf("") }
    var dateFilter by rememberSaveable { mutableStateOf(DateFilter.ALL) }
    var priceFilter by rememberSaveable { mutableStateOf(PriceFilter.ALL) }
    var sortOrder by rememberSaveable { mutableStateOf(ReceiptSort.RECENT) }

    val now = System.currentTimeMillis()
    val filtered = purchases.filter { p ->
        val q = query.trim()
        val matchesQuery = q.isEmpty() ||
            p.productName.contains(q, ignoreCase = true) ||
            p.merchantName.contains(q, ignoreCase = true) ||
            p.invoiceNumber.contains(q, ignoreCase = true) ||
            p.category.contains(q, ignoreCase = true)

        val ageDays = TimeUnit.MILLISECONDS.toDays(now - p.purchaseDateMillis)
        val matchesDate = when (dateFilter) {
            DateFilter.ALL -> true
            DateFilter.LAST_30 -> ageDays <= 30L
            DateFilter.LAST_90 -> ageDays <= 90L
            DateFilter.LAST_YEAR -> ageDays <= 365L
        }

        val matchesPrice = when (priceFilter) {
            PriceFilter.ALL -> true
            PriceFilter.UNDER_5K -> p.purchasePriceInr < 5_000.0
            PriceFilter.MID_5K_25K -> p.purchasePriceInr in 5_000.0..25_000.0
            PriceFilter.OVER_25K -> p.purchasePriceInr > 25_000.0
        }

        matchesQuery && matchesDate && matchesPrice
    }.let { list ->
        when (sortOrder) {
            ReceiptSort.RECENT -> list.sortedByDescending { it.purchaseDateMillis }
            ReceiptSort.OLDEST -> list.sortedBy { it.purchaseDateMillis }
            ReceiptSort.PRICE_HIGH -> list.sortedByDescending { it.purchasePriceInr }
            ReceiptSort.PRICE_LOW -> list.sortedBy { it.purchasePriceInr }
        }
    }

    VaultAtmosphereBackground(
        accentColor = vaultColors.purchaseAccent,
        secondaryAccent = vaultColors.goldAccent
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                VaultTopBar(
                    title = "All Receipts",
                    subtitle = "${filtered.size} of ${purchases.size} invoices",
                    onBack = onBack
                )
            },
            floatingActionButton = {
                VaultGradientButton(
                    onClick = onAddReceipt,
                    colors = vaultColors.purchaseButtonGradient,
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                    modifier = Modifier.testTag("all_receipts_fab")
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add Receipt", modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Receipt")
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("all_receipts_screen"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    VaultAnimatedEntrance(delayMillis = 20) {
                        Column {
                            VaultGlassSearchBar(
                                query = query,
                                onQueryChange = { query = it },
                                placeholder = "Search product, category, merchant, or invoice #",
                                accentColors = vaultColors.purchaseButtonGradient,
                                testTag = "all_receipts_search_input"
                            )

                    Spacer(Modifier.height(10.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DateFilter.entries.forEach { df ->
                                FilterChip(
                                    selected = dateFilter == df,
                                    onClick = { dateFilter = df },
                                    label = { Text(df.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = vaultColors.purchaseAccent.copy(alpha = 0.22f),
                                        selectedLabelColor = vaultColors.purchaseAccent
                                    )
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PriceFilter.entries.forEach { pf ->
                                FilterChip(
                                    selected = priceFilter == pf,
                                    onClick = { priceFilter = pf },
                                    label = { Text(pf.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = vaultColors.goldAccent.copy(alpha = 0.2f),
                                        selectedLabelColor = vaultColors.goldAccent
                                    )
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReceiptSort.entries.forEach { sort ->
                                FilterChip(
                                    selected = sortOrder == sort,
                                    onClick = { sortOrder = sort },
                                    label = { Text("Sort: ${sort.label}") }
                                )
                            }
                        }
                        }
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    item {
                        VaultEmptyState(
                            icon = Icons.Outlined.ReceiptLong,
                            title = "No receipts match your filters",
                            subtitle = "Adjust date, price, or search terms to view stored purchases.",
                            accent = vaultColors.purchaseAccent,
                            actionLabel = "Reset Filters",
                            onAction = {
                                query = ""
                                dateFilter = DateFilter.ALL
                                priceFilter = PriceFilter.ALL
                            }
                        )
                    }
                } else {
                    items(filtered, key = { it.id }) { purchase ->
                        PurchaseListCard(
                            purchase = purchase,
                            onClick = { onOpenPurchaseDetail(purchase.id) }
                        )
                    }
                }

                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }
}
