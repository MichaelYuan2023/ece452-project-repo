package com.example.houseflow.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.houseflow.model.Expense
import com.example.houseflow.model.Roommate
import com.example.houseflow.util.ExpenseMath
import com.example.houseflow.ui.viewmodel.AppViewModel

// HF-15 — Shared expenses. Ships functional-first here; HF-14 restyles it against
// the shared component library.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(vm: AppViewModel, onBack: (() -> Unit)? = null) {
    val expenses by vm.expenses.collectAsState()
    val shares by vm.expenseShares.collectAsState()
    val balances by vm.balances.collectAsState()
    val roommates by vm.roommates.collectAsState()
    val currentUser by vm.currentUser.collectAsState()
    val currentUserRole by vm.currentUserRole.collectAsState()

    val myUid = currentUser?.uid
    var showAdd by remember { mutableStateOf(false) }
    var settleWith by remember { mutableStateOf<String?>(null) } // preselected counterparty userId, or "" for none

    val myBalance = balances.find { it.userId == myUid }?.netCents ?: 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expenses") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add expense")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            item { SummaryCard(myBalance) }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Balances",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (roommates.size > 1) {
                        TextButton(onClick = { settleWith = "" }) { Text("Settle up") }
                    }
                }
            }
            items(balances, key = { it.userId }) { balance ->
                BalanceRow(
                    name = if (balance.userId == myUid) "${balance.displayName} (you)" else balance.displayName,
                    netCents = balance.netCents,
                    canSettle = balance.userId != myUid,
                    onSettle = { settleWith = balance.userId }
                )
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Recent expenses",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (expenses.isEmpty()) {
                item { EmptyExpensesCard() }
            } else {
                items(expenses, key = { it.id }) { expense ->
                    val payerName = roommates.find { it.userId == expense.paidByUserId }?.displayName ?: "?"
                    val participantCount = shares.count { it.expenseId == expense.id }
                    val myShare = shares.find { it.expenseId == expense.id && it.userId == myUid }?.shareCents
                    val canDelete = expense.createdByUserId == myUid ||
                        currentUserRole == com.example.houseflow.model.HouseholdRole.CREATOR ||
                        currentUserRole == com.example.houseflow.model.HouseholdRole.ADMIN
                    ExpenseCard(
                        expense = expense,
                        payerName = payerName,
                        participantCount = participantCount,
                        myShareCents = myShare,
                        paidByMe = expense.paidByUserId == myUid,
                        canDelete = canDelete,
                        onDelete = { vm.deleteExpense(expense.id) }
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    if (showAdd && myUid != null) {
        AddExpenseDialog(
            roommates = roommates,
            currentUserId = myUid,
            onDismiss = { showAdd = false },
            onConfirm = { desc, amountCents, paidBy, participants ->
                vm.addExpense(desc, amountCents, paidBy, participants)
                showAdd = false
            }
        )
    }

    settleWith?.let { preselected ->
        val others = roommates.filter { it.userId != myUid }
        if (others.isNotEmpty() && myUid != null) {
            SettleUpDialog(
                others = others,
                preselectedUserId = preselected.ifEmpty { null },
                onDismiss = { settleWith = null },
                onConfirm = { toUserId, amountCents ->
                    vm.settleUp(myUid, toUserId, amountCents)
                    settleWith = null
                }
            )
        } else {
            settleWith = null
        }
    }
}

@Composable
private fun SummaryCard(myNetCents: Int) {
    val owed = myNetCents > 0
    val settled = myNetCents == 0
    val accent = when {
        settled -> MaterialTheme.colorScheme.onSurfaceVariant
        owed -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.error
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.10f)),
        elevation = CardDefaults.cardElevation(0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                when {
                    settled -> "You're all settled up"
                    owed -> "You're owed"
                    else -> "You owe"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!settled) {
                Text(
                    ExpenseMath.formatCents(kotlin.math.abs(myNetCents)),
                    style = MaterialTheme.typography.displaySmall,
                    color = accent
                )
            } else {
                Text(
                    "$0.00",
                    style = MaterialTheme.typography.displaySmall,
                    color = accent
                )
            }
        }
    }
}

@Composable
private fun BalanceRow(name: String, netCents: Int, canSettle: Boolean, onSettle: () -> Unit) {
    val label = when {
        netCents > 0 -> "owed ${ExpenseMath.formatCents(netCents)}"
        netCents < 0 -> "owes ${ExpenseMath.formatCents(-netCents)}"
        else -> "settled"
    }
    val color = when {
        netCents > 0 -> MaterialTheme.colorScheme.secondary
        netCents < 0 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(50),
                color = color.copy(alpha = 0.12f),
                contentColor = color
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            if (canSettle && netCents != 0) {
                TextButton(onClick = onSettle) { Text("Settle") }
            }
        }
    }
}

@Composable
private fun ExpenseCard(
    expense: Expense,
    payerName: String,
    participantCount: Int,
    myShareCents: Int?,
    paidByMe: Boolean,
    canDelete: Boolean,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(expense.description.ifBlank { "Expense" }, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${if (paidByMe) "You" else payerName} paid ${ExpenseMath.formatCents(expense.amountCents)} · split $participantCount way${if (participantCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (myShareCents != null) {
                    Text(
                        "Your share: ${ExpenseMath.formatCents(myShareCents)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete expense")
                }
            }
        }
    }
}

@Composable
private fun EmptyExpensesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Payments,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text("No expenses yet", style = MaterialTheme.typography.titleSmall)
            Text(
                "Tap + to log a shared cost and split it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExpenseDialog(
    roommates: List<Roommate>,
    currentUserId: String,
    onDismiss: () -> Unit,
    onConfirm: (description: String, amountCents: Int, paidByUserId: String, participantIds: List<String>) -> Unit
) {
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    val payerNames = roommates.map { it.displayName }
    var payerIndex by remember {
        mutableIntStateOf(roommates.indexOfFirst { it.userId == currentUserId }.coerceAtLeast(0))
    }
    // Participants default to everyone.
    val selected = remember { mutableStateListOf<String>().apply { addAll(roommates.map { it.userId }) } }

    val amountCents = ExpenseMath.parseAmountToCents(amount)
    val canSave = description.isNotBlank() && amountCents != null && selected.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    prefix = { Text("$") },
                    isError = amount.isNotBlank() && amountCents == null,
                    supportingText = if (amount.isNotBlank() && amountCents == null) {
                        { Text("Enter a valid amount") }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (roommates.isNotEmpty()) {
                    SimpleDropdown("Paid by", payerNames, payerIndex) { payerIndex = it }
                }
                Text("Split between", style = MaterialTheme.typography.labelMedium)
                roommates.forEach { r ->
                    val checked = selected.contains(r.userId)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (checked) selected.remove(r.userId) else selected.add(r.userId)
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                if (checked) selected.remove(r.userId) else selected.add(r.userId)
                            }
                        )
                        Text(r.displayName)
                    }
                }
                if (amountCents != null && selected.isNotEmpty()) {
                    Text(
                        "Each pays about ${ExpenseMath.formatCents(amountCents / selected.size)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    if (canSave) {
                        onConfirm(
                            description.trim(),
                            amountCents!!,
                            roommates[payerIndex].userId,
                            selected.toList()
                        )
                    }
                }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SettleUpDialog(
    others: List<Roommate>,
    preselectedUserId: String?,
    onDismiss: () -> Unit,
    onConfirm: (toUserId: String, amountCents: Int) -> Unit
) {
    val names = others.map { it.displayName }
    var index by remember {
        mutableIntStateOf(
            others.indexOfFirst { it.userId == preselectedUserId }.coerceAtLeast(0)
        )
    }
    var amount by remember { mutableStateOf("") }
    val amountCents = ExpenseMath.parseAmountToCents(amount)
    val canSave = amountCents != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record a payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Record money you paid a roommate to settle up.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SimpleDropdown("Paid to", names, index) { index = it }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    prefix = { Text("$") },
                    isError = amount.isNotBlank() && amountCents == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = { if (canSave) onConfirm(others[index].userId, amountCents!!) }
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.height(0.dp))
                Text(" Record")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
