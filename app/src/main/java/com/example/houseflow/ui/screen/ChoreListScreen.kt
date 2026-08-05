package com.example.houseflow.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.houseflow.model.AssignmentStatus
import com.example.houseflow.model.Chore
import com.example.houseflow.model.ChoreAssignment
import com.example.houseflow.model.ChoreFrequency
import com.example.houseflow.model.HouseholdRole
import com.example.houseflow.model.Roommate
import com.example.houseflow.model.TradeStatus
import com.example.houseflow.ui.viewmodel.AppViewModel
import java.util.UUID

private val DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val HOURS = (0..23).map { h -> "%02d:00".format(h) }
private val FREQUENCY_LABELS = listOf("Daily", "Weekly", "Every N days", "One-time")

// A short, frequency-aware "when is this due" line shown on every card/row.
private fun dueText(chore: Chore): String {
    val time = "%02d:00".format(chore.dueHour)
    return when (chore.frequency) {
        ChoreFrequency.DAILY -> "Daily · $time"
        ChoreFrequency.WEEKLY -> "${DAYS[chore.dueDayOfWeek]} · $time"
        ChoreFrequency.EVERY_N_DAYS -> "Every ${chore.intervalDays ?: "?"} days · $time"
        ChoreFrequency.ONE_TIME -> "${DAYS[chore.dueDayOfWeek]} · $time"
    }
}

/**
 * The Chores tab. Shows the household's pickup board — a single, focused list of
 * what needs doing right now:
 *   1. Trade requests sent to you (need a response)
 *   2. Your chores (claimed by you, still to do)
 *   3. Open for pickup (unclaimed, with a suggested roommate)
 *   4. Done this week (completed / missed)
 *
 * Chore *definitions* (creating, editing, deleting the recurring chores
 * themselves) live in a separate admin-only [ManageChoresPanel], so the board
 * only ever shows actual, actionable occurrences.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoreListScreen(vm: AppViewModel) {
    val chores by vm.chores.collectAsState()
    val assignments by vm.assignments.collectAsState()
    val currentUser by vm.currentUser.collectAsState()
    val household by vm.household.collectAsState()
    val roommates by vm.roommates.collectAsState()
    val currentUserRole by vm.currentUserRole.collectAsState()
    val tradeRequests by vm.tradeRequests.collectAsState()

    val canManageChores = currentUserRole == HouseholdRole.CREATOR || currentUserRole == HouseholdRole.ADMIN
    val me = currentUser?.uid

    var showManage by remember { mutableStateOf(false) }
    var tradingAssignmentId by remember { mutableStateOf<String?>(null) }

    // Chore management is a separate full-screen panel so the board isn't a
    // jumble of definitions and occurrences.
    if (showManage) {
        ManageChoresPanel(
            chores = chores,
            assignments = assignments,
            roommates = roommates,
            weekStart = vm.weekStart,
            householdId = household?.id ?: "",
            currentUserId = me ?: "",
            onBack = { showManage = false },
            onAdd = { vm.addChore(it) },
            onEdit = { vm.updateChore(it) },
            onDelete = { vm.deleteChore(it) }
        )
        return
    }

    // --- Board sections ---
    val myChores = assignments.filter {
        it.assignedToRoommateId == me && it.status == AssignmentStatus.PENDING
    }
    val openChores = assignments.filter { it.status == AssignmentStatus.AVAILABLE }
    val doneThisWeek = assignments.filter {
        it.weekStart >= vm.weekStart &&
            (it.status == AssignmentStatus.COMPLETED || it.status == AssignmentStatus.MISSED)
    }
    val pendingTrades = tradeRequests.filter { it.status == TradeStatus.PENDING }
    val incomingTrades = pendingTrades.filter { it.toUserId == me }

    val boardEmpty = incomingTrades.isEmpty() && myChores.isEmpty() &&
        openChores.isEmpty() && doneThisWeek.isEmpty()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Chores") },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    if (canManageChores) {
                        TextButton(onClick = { showManage = true }) { Text("Manage") }
                    }
                }
            )
        }
    ) { padding ->
        if (boardEmpty) {
            EmptyBoard(
                hasChores = chores.isNotEmpty(),
                canManage = canManageChores,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                if (incomingTrades.isNotEmpty()) {
                    item { SectionHeader("Trade requests for you", incomingTrades.size) }
                    items(incomingTrades, key = { it.id }) { request ->
                        val assignment = assignments.find { it.id == request.assignmentId }
                        val chore = chores.find { it.id == assignment?.choreId }
                        TradeRequestCard(
                            choreName = chore?.name ?: "Unknown chore",
                            due = chore?.let { dueText(it) } ?: "",
                            fromName = roommates.find { it.userId == request.fromUserId }?.displayName
                                ?: "A roommate",
                            reason = request.reason,
                            onAccept = { vm.respondToTrade(request.id, accept = true) },
                            onDeny = { vm.respondToTrade(request.id, accept = false) }
                        )
                    }
                }

                if (myChores.isNotEmpty()) {
                    item { SectionHeader("Your chores", myChores.size) }
                    items(myChores, key = { it.id }) { assignment ->
                        val chore = chores.find { it.id == assignment.choreId }
                        val outgoing = pendingTrades.find {
                            it.assignmentId == assignment.id && it.fromUserId == me
                        }
                        MyChoreCard(
                            choreName = chore?.name ?: "Unknown chore",
                            due = chore?.let { dueText(it) } ?: "",
                            reason = assignment.reason,
                            hasConflict = assignment.hasConflict,
                            pendingTradeToName = outgoing?.let { t ->
                                roommates.find { it.userId == t.toUserId }?.displayName ?: "a roommate"
                            },
                            onComplete = { vm.markComplete(assignment.id) },
                            onTrade = { tradingAssignmentId = assignment.id },
                            onCancelTrade = { outgoing?.let { vm.cancelTradeRequest(it.id) } }
                        )
                    }
                }

                if (openChores.isNotEmpty()) {
                    item { SectionHeader("Open for pickup", openChores.size) }
                    items(openChores, key = { it.id }) { assignment ->
                        val chore = chores.find { it.id == assignment.choreId }
                        OpenChoreCard(
                            choreName = chore?.name ?: "Unknown chore",
                            due = chore?.let { dueText(it) } ?: "",
                            reason = assignment.reason,
                            suggestedName = roommates
                                .find { it.userId == assignment.assignedToRoommateId }?.displayName
                                ?: "someone",
                            suggestedIsMe = assignment.assignedToRoommateId == me,
                            onPickUp = { vm.claimAssignment(assignment.id) }
                        )
                    }
                }

                if (doneThisWeek.isNotEmpty()) {
                    item { SectionHeader("Done this week", doneThisWeek.size) }
                    items(doneThisWeek, key = { it.id }) { assignment ->
                        val chore = chores.find { it.id == assignment.choreId }
                        DoneChoreCard(
                            choreName = chore?.name ?: "Unknown chore",
                            due = chore?.let { dueText(it) } ?: "",
                            doneByName = roommates
                                .find { it.userId == assignment.assignedToRoommateId }?.displayName
                                ?: "someone",
                            missed = assignment.status == AssignmentStatus.MISSED
                        )
                    }
                }
            }
        }
    }

    tradingAssignmentId?.let { assignmentId ->
        val others = roommates.filter { it.userId != me }
        TradeRequestDialog(
            roommateNames = others.map { it.displayName },
            onDismiss = { tradingAssignmentId = null },
            onConfirm = { index, reason ->
                vm.requestTrade(assignmentId, others[index].userId, reason)
                tradingAssignmentId = null
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Board cards — all share one visual language via BoardCard + CardHeader.
// ---------------------------------------------------------------------------

@Composable
private fun BoardCard(
    containerColor: Color,
    borderColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(0.dp),
        shape = MaterialTheme.shapes.large,
        border = borderColor?.let { BorderStroke(1.dp, it) }
            ?: BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun CardHeader(title: String, due: String, chip: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (due.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    due,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        chip()
    }
}

@Composable
private fun StatusChip(label: String, color: Color, strong: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
        contentColor = color
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ReasonText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun OpenChoreCard(
    choreName: String,
    due: String,
    reason: String,
    suggestedName: String,
    suggestedIsMe: Boolean,
    onPickUp: () -> Unit
) {
    BoardCard(
        containerColor = MaterialTheme.colorScheme.surface,
        borderColor = MaterialTheme.colorScheme.outline
    ) {
        CardHeader(choreName, due) {
            StatusChip(
                label = if (suggestedIsMe) "Suggested for you" else "Suggested: $suggestedName",
                color = MaterialTheme.colorScheme.primary,
                strong = suggestedIsMe
            )
        }
        if (reason.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            ReasonText(reason)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onPickUp,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) { Text("Pick up") }
    }
}

@Composable
private fun MyChoreCard(
    choreName: String,
    due: String,
    reason: String,
    hasConflict: Boolean,
    pendingTradeToName: String?,
    onComplete: () -> Unit,
    onTrade: () -> Unit,
    onCancelTrade: () -> Unit
) {
    BoardCard(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)) {
        CardHeader(choreName, due) {
            StatusChip("Yours", MaterialTheme.colorScheme.primary)
        }
        if (reason.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            ReasonText(reason)
        }
        if (hasConflict) {
            Spacer(Modifier.height(6.dp))
            Text(
                "⚠ You're busy at the due time",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(Modifier.height(12.dp))
        if (pendingTradeToName != null) {
            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) { Text("Complete") }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Trade offered to $pendingTradeToName",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onCancelTrade) { Text("Cancel") }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onComplete,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium
                ) { Text("Complete") }
                OutlinedButton(
                    onClick = onTrade,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium
                ) { Text("Trade") }
            }
        }
    }
}

@Composable
private fun TradeRequestCard(
    choreName: String,
    due: String,
    fromName: String,
    reason: String,
    onAccept: () -> Unit,
    onDeny: () -> Unit
) {
    BoardCard(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)) {
        CardHeader(choreName, due) {
            StatusChip("From $fromName", MaterialTheme.colorScheme.tertiary)
        }
        if (reason.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            ReasonText("\"$reason\"")
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onAccept,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium
            ) { Text("Accept") }
            OutlinedButton(
                onClick = onDeny,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium
            ) { Text("Deny") }
        }
    }
}

@Composable
private fun DoneChoreCard(
    choreName: String,
    due: String,
    doneByName: String,
    missed: Boolean
) {
    val container = if (missed) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    }
    BoardCard(containerColor = container) {
        CardHeader(choreName, due) {
            if (missed) {
                StatusChip("Missed", MaterialTheme.colorScheme.error)
            } else {
                StatusChip("Done ✓", MaterialTheme.colorScheme.secondary)
            }
        }
        Spacer(Modifier.height(4.dp))
        ReasonText(if (missed) "Was assigned to $doneByName" else "Completed by $doneByName")
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(6.dp))
        Text(
            "($count)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyBoard(hasChores: Boolean, canManage: Boolean, modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                if (hasChores) "You're all caught up" else "No chores yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = when {
                    hasChores ->
                        "Nothing to pick up right now. New chores appear here when they're due."
                    canManage -> "Tap Manage to add your household's first chore."
                    else -> "Ask a household admin to add some chores."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Manage chores — admin-only panel for the chore *definitions* themselves.
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageChoresPanel(
    chores: List<Chore>,
    assignments: List<ChoreAssignment>,
    roommates: List<Roommate>,
    weekStart: Long,
    householdId: String,
    currentUserId: String,
    onBack: () -> Unit,
    onAdd: (Chore) -> Unit,
    onEdit: (Chore) -> Unit,
    onDelete: (String) -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Chore?>(null) }
    var deleting by remember { mutableStateOf<Chore?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage chores") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreate = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add chore")
            }
        }
    ) { padding ->
        if (chores.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No chores yet. Tap + to add one.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(chores, key = { it.id }) { chore ->
                    val current = assignments
                        .filter { it.choreId == chore.id && it.weekStart >= weekStart }
                        .maxByOrNull { it.weekStart }
                    ManageChoreRow(
                        chore = chore,
                        statusLine = currentStatusLine(current, roommates),
                        onEdit = { editing = chore },
                        onDelete = { deleting = chore }
                    )
                }
            }
        }
    }

    if (showCreate) {
        CreateChoreDialog(
            householdId = householdId,
            createdByRoommateId = currentUserId,
            onDismiss = { showCreate = false },
            onConfirm = { chore ->
                onAdd(chore)
                showCreate = false
            }
        )
    }

    editing?.let { chore ->
        CreateChoreDialog(
            householdId = chore.householdId,
            createdByRoommateId = chore.createdByRoommateId,
            existing = chore,
            onDismiss = { editing = null },
            onConfirm = { updated ->
                onEdit(updated)
                editing = null
            }
        )
    }

    deleting?.let { chore ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete chore?") },
            text = {
                Text("Delete \"${chore.name}\"? This removes it and all of its assignments for everyone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(chore.id)
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

// Plain (non-composable) description of a chore's current-period occurrence,
// shown under each definition in the manage panel.
private fun currentStatusLine(current: ChoreAssignment?, roommates: List<Roommate>): String {
    if (current == null) return "Not scheduled yet"
    val who = roommates.find { it.userId == current.assignedToRoommateId }?.displayName ?: "someone"
    return when (current.status) {
        AssignmentStatus.AVAILABLE -> "Open · suggested for $who"
        AssignmentStatus.PENDING -> "Claimed by $who"
        AssignmentStatus.COMPLETED -> "Done by $who this period"
        AssignmentStatus.MISSED -> "Missed by $who"
    }
}

@Composable
private fun ManageChoreRow(
    chore: Chore,
    statusLine: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    BoardCard(
        containerColor = MaterialTheme.colorScheme.surface,
        borderColor = MaterialTheme.colorScheme.outline
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(chore.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    dueText(chore),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Effort ${chore.effortScore}/5",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    statusLine,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit chore")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete chore")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------

@Composable
private fun TradeRequestDialog(
    roommateNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (roommateIndex: Int, reason: String) -> Unit
) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Request Trade") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (roommateNames.isEmpty()) {
                    Text("No other roommates to trade with.")
                } else {
                    SimpleDropdown("Trade with", roommateNames, selectedIndex) { selectedIndex = it }
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Reason") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedIndex, reason) },
                enabled = roommateNames.isNotEmpty() && reason.isNotBlank()
            ) { Text("Send") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CreateChoreDialog(
    householdId: String,
    createdByRoommateId: String,
    existing: Chore? = null,
    onDismiss: () -> Unit,
    onConfirm: (Chore) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var selectedDay by remember { mutableIntStateOf(existing?.dueDayOfWeek ?: 0) }
    var selectedHour by remember { mutableIntStateOf(existing?.dueHour ?: 10) }
    var effortScore by remember { mutableFloatStateOf(existing?.effortScore?.toFloat() ?: 2f) }
    var timeSensitive by remember { mutableStateOf(existing?.isTimeSensitive ?: false) }
    var selectedFrequency by remember { mutableStateOf(existing?.frequency ?: ChoreFrequency.WEEKLY) }
    var intervalDays by remember { mutableStateOf(existing?.intervalDays?.toString() ?: "") }

    val intervalValid = selectedFrequency != ChoreFrequency.EVERY_N_DAYS ||
        (intervalDays.toIntOrNull() ?: 0) >= 2
    val canSave = name.isNotBlank() && intervalValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New Chore" else "Edit Chore") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Chore name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                SimpleDropdown("Due day", DAYS, selectedDay) { selectedDay = it }
                SimpleDropdown("Due time", HOURS, selectedHour) { selectedHour = it }
                SimpleDropdown("Frequency", FREQUENCY_LABELS, selectedFrequency.ordinal) {
                    selectedFrequency = ChoreFrequency.entries[it]
                }

                if (selectedFrequency == ChoreFrequency.EVERY_N_DAYS) {
                    val showError = intervalDays.isNotEmpty() && (intervalDays.toIntOrNull() ?: 0) < 2
                    OutlinedTextField(
                        value = intervalDays,
                        onValueChange = { v -> intervalDays = v.filter { it.isDigit() } },
                        label = { Text("Repeat every N days") },
                        supportingText = if (showError) {
                            { Text("Must be at least 2") }
                        } else null,
                        isError = showError,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Column {
                    Text("Effort: ${effortScore.toInt()}/5", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = effortScore,
                        onValueChange = { effortScore = it },
                        valueRange = 1f..5f,
                        steps = 3
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Time-sensitive", modifier = Modifier.weight(1f))
                    Switch(checked = timeSensitive, onCheckedChange = { timeSensitive = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (canSave) {
                        onConfirm(
                            Chore(
                                id = existing?.id ?: UUID.randomUUID().toString(),
                                householdId = householdId,
                                createdByRoommateId = createdByRoommateId,
                                name = name.trim(),
                                description = description.trim(),
                                frequency = selectedFrequency,
                                effortScore = effortScore.toInt(),
                                dueDayOfWeek = selectedDay,
                                dueHour = selectedHour,
                                isTimeSensitive = timeSensitive,
                                intervalDays = if (selectedFrequency == ChoreFrequency.EVERY_N_DAYS)
                                    intervalDays.toIntOrNull() else null
                            )
                        )
                    }
                },
                enabled = canSave
            ) { Text(if (existing == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
