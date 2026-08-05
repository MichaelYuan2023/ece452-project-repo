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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val pairBalances by vm.myPairBalances.collectAsState()
    val roommates by vm.roommates.collectAsState()
    val currentUser by vm.currentUser.collectAsState()
    val currentUserRole by vm.currentUserRole.collectAsState()

    val myUid = currentUser?.uid
    var showAdd by remember { mutableStateOf(false) }
    var settleWith by remember { mutableStateOf<String?>(null) } // preselected counterparty userId, or "" for none

    // Gross totals, deliberately not netted against each other: owing Bob $15
    // while Cara owes you $15 is two open debts, not "all settled up".
    val owedByMeCents = pairBalances.filter { it.netCents < 0 }.sumOf { -it.netCents }
    val owedToMeCents = pairBalances.filter { it.netCents > 0 }.sumOf { it.netCents }
    val anyoneToSettleWith = pairBalances.any { it.netCents != 0 }

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
            item { SummaryCard(owedByMeCents = owedByMeCents, owedToMeCents = owedToMeCents) }

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
                    if (roommates.size > 1 && anyoneToSettleWith) {
                        TextButton(onClick = { settleWith = "" }) { Text("Settle up") }
                    }
                }
            }
            items(pairBalances, key = { it.userId }) { pair ->
                BalanceRow(
                    name = pair.displayName,
                    netCents = pair.netCents,
                    canSettle = pair.netCents != 0,
                    onSettle = { settleWith = pair.userId }
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
        // Only offer counterparties there is something to settle with, so the
        // dialog can never be opened on a pairing that is already square.
        val others = pairBalances.filter { it.netCents != 0 }
        if (others.isNotEmpty() && myUid != null) {
            SettleUpDialog(
                myUserId = myUid,
                others = others,
                preselectedUserId = preselected.ifEmpty { null },
                onDismiss = { settleWith = null },
                onConfirm = { fromUserId, toUserId, amountCents ->
                    vm.settleUp(fromUserId, toUserId, amountCents)
                    settleWith = null
                }
            )
        } else {
            settleWith = null
        }
    }
}

// Shows what you owe and what you are owed side by side rather than a single
// netted figure. The two can be nonzero at once — and netting them was what let
// a household with real outstanding debts read as "all settled up".
@Composable
private fun SummaryCard(owedByMeCents: Int, owedToMeCents: Int) {
    val settled = owedByMeCents == 0 && owedToMeCents == 0
    // Lead with what's owed, since that's the side you can act on.
    val accent = when {
        settled -> MaterialTheme.colorScheme.onSurfaceVariant
        owedByMeCents > 0 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.10f)),
        elevation = CardDefaults.cardElevation(0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        if (settled) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "You're all settled up",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("$0.00", style = MaterialTheme.typography.displaySmall, color = accent)
            }
        } else {
            Row(modifier = Modifier.padding(16.dp)) {
                SummaryFigure(
                    label = "You owe",
                    cents = owedByMeCents,
                    accent = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                SummaryFigure(
                    label = "You're owed",
                    cents = owedToMeCents,
                    accent = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SummaryFigure(label: String, cents: Int, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            ExpenseMath.formatCents(cents),
            style = MaterialTheme.typography.headlineMedium,
            // A zero side is muted so the side that needs attention stands out.
            color = if (cents == 0) MaterialTheme.colorScheme.onSurfaceVariant else accent
        )
    }
}

@Composable
private fun BalanceRow(name: String, netCents: Int, canSettle: Boolean, onSettle: () -> Unit) {
    // Phrased against you specifically — these are pairwise, not household-wide.
    val label = when {
        netCents > 0 -> "owes you ${ExpenseMath.formatCents(netCents)}"
        netCents < 0 -> "you owe ${ExpenseMath.formatCents(-netCents)}"
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
            if (canSettle) {
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
    myUserId: String,
    others: List<ExpenseMath.PairBalance>,
    preselectedUserId: String?,
    onDismiss: () -> Unit,
    onConfirm: (fromUserId: String, toUserId: String, amountCents: Int) -> Unit
) {
    val names = others.map { it.displayName }
    var index by remember {
        mutableIntStateOf(
            others.indexOfFirst { it.userId == preselectedUserId }.coerceAtLeast(0)
        )
    }
    val other = others[index]
    // Who pays whom comes from what the two of you owe each other, never
    // assumed — a payment recorded backwards moves you both further apart.
    val direction = ExpenseMath.settleDirectionForPair(myUserId, other.userId, other.netCents)
    val iAmPaying = direction?.fromUserId == myUserId

    // Prefill the full settleable amount; re-prefill when the counterparty changes.
    var amount by remember { mutableStateOf("") }
    LaunchedEffect(direction) {
        amount = direction?.let { ExpenseMath.formatCents(it.maxCents).removePrefix("$") } ?: ""
    }

    val amountCents = ExpenseMath.parseAmountToCents(amount)
    val overMax = amountCents != null && direction != null && amountCents > direction.maxCents
    val canSave = amountCents != null && direction != null && !overMax

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record a payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    when {
                        direction == null -> "You and ${other.displayName} have nothing to settle."
                        iAmPaying -> "Record money you paid ${other.displayName} to settle up."
                        else -> "Record money ${other.displayName} paid you to settle up."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SimpleDropdown(if (iAmPaying) "Paid to" else "Paid by", names, index) { index = it }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    prefix = { Text("$") },
                    isError = (amount.isNotBlank() && amountCents == null) || overMax,
                    supportingText = when {
                        overMax -> {
                            { Text("At most ${ExpenseMath.formatCents(direction!!.maxCents)} can be settled here") }
                        }
                        direction != null -> {
                            { Text("Up to ${ExpenseMath.formatCents(direction.maxCents)}") }
                        }
                        else -> null
                    },
                    enabled = direction != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    if (canSave) onConfirm(direction!!.fromUserId, direction.toUserId, amountCents!!)
                }
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.height(0.dp))
                Text(" Record")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
