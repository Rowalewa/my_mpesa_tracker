package com.example.my_mpesa_tracker.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.my_mpesa_tracker.data.model.MpesaTransaction
import com.example.my_mpesa_tracker.data.model.TransactionType
import com.example.my_mpesa_tracker.util.label

// ── Transactions: search, filter, and the list ──────────────────────────────

@Composable
fun TransactionsScreen(vm: DashboardViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    var showTransactionDetail by remember { mutableStateOf<MpesaTransaction?>(null) }

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MpesaGreen)
        }
        return
    }

    showTransactionDetail?.let { tx ->
        TransactionDetailDialog(tx = tx, onDismiss = { showTransactionDetail = null })
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(SurfaceDark),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SearchBar(
                query = state.searchQuery,
                onQueryChange = vm::setSearch
            )
        }

        item {
            CategoryFilterRow(
                selected = state.selectedCategory,
                onSelect = vm::setCategory
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Transactions (${state.filteredTransactions.size})",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                if (state.searchQuery.isNotBlank() || state.selectedCategory != null) {
                    Text(
                        "Clear filters",
                        color = MpesaGreen,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable {
                            vm.setSearch("")
                            vm.setCategory(null)
                        }
                    )
                }
            }
        }

        if (state.filteredTransactions.isEmpty()) {
            item {
                if (state.allTransactions.isEmpty()) {
                    EmptyTransactionsState()
                } else if (state.searchQuery.isNotBlank()) {
                    EmptySearchState(query = state.searchQuery)
                } else {
                    EmptyPeriodState(period = state.dateLabel)
                }
            }
        } else {
            items(state.filteredTransactions) { tx ->
                TransactionRow(tx = tx, onClick = { showTransactionDetail = tx })
            }
        }
    }
}

@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    var localQuery by remember { mutableStateOf(query) }
    val focusManager = LocalFocusManager.current

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        OutlinedTextField(
            value = localQuery,
            onValueChange = {
                localQuery = it
                onQueryChange(it)
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search by name, amount, code...", color = TextSecondary, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MpesaGreen,
                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = MpesaGreen,
                focusedContainerColor = CardDark,
                unfocusedContainerColor = CardDark
            ),
            textStyle = LocalTextStyle.current.copy(
                textDirection = TextDirection.Ltr,
                textAlign = androidx.compose.ui.text.style.TextAlign.Start
            ),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
fun CategoryFilterRow(selected: TransactionType?, onSelect: (TransactionType?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("All", fontSize = 12.sp, color = if (selected == null) Color.White else TextSecondary) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MpesaGreen,
                    containerColor = CardDark
                )
            )
        }
        items(TransactionType.entries) { type ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelect(if (selected == type) null else type) },
                label = {
                    Text(
                        "${type.emoji()} ${type.label()}",
                        fontSize = 12.sp,
                        color = if (selected == type) Color.White else TextSecondary
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MpesaGreen,
                    containerColor = CardDark
                )
            )
        }
    }
}
