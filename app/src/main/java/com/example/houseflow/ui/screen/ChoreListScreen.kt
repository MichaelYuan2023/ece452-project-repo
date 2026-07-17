package com.example.houseflow.ui.screen

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.houseflow.model.AssignmentStatus
import com.example.houseflow.model.Chore
import com.example.houseflow.model.ChoreFrequency
import com.example.houseflow.model.HouseholdRole
import com.example.houseflow.model.TradeStatus
import com.example.houseflow.ui.viewmodel.AppViewModel
import java.util.UUID

private val DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val HOURS = (0..23).map { h -> "%02d:00".format(h) }
private val FREQUENCY_LABELS = listOf("Daily", "Weekly", "Every N days", "One-time")

private fun Chore.frequencyLabel(): String = when (frequency) {
    ChoreFrequency.DAILY -> "daily"
    ChoreFrequency.WEEKLY -> "weekly"
    ChoreFrequency.EVERY_N_DAYS -> "every ${intervalDays ?: "?"} days"
    ChoreFrequency.ONE_TIME -> "one-time"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoreListScreen(vm: AppViewModel) {
    val chores by vm.chores.collectAsState()
    val assignments by vm.assignments.collectAsState()
    val currentUser by vm.currentUser.collectAsState()
    val household by vm.household.collectAsState()
    val roommates by vm.roommates.collectAsState()
    val assignmentsRun by vm.assignmentsRun.collectAsState()
    val currentUserRole by vm.currentUserRole.collectAsState()
    val canManageChores = currentUserRole == HouseholdRole.CREATOR || currentUserRole == HouseholdRole.ADMIN
    val tradeRequests by vm.tradeRequests.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var editingChore by remember { mutableStateOf<Chore?>(null) }
    var tradingAssignmentId by remember { mutableStateOf<String?>(null) }

    // Current user's assignments this week
    val myAssignments = assignments.filter {
        it.assignedToRoommateId == currentUser?.uid &&
            it.weekStart >= vm.weekStart &&
            it.status != AssignmentStatus.AVAILABLE
    }
    // Unclaimed chores anyone can pick up (no weekStart filter: daily/every-N
    // slots and next-occurrence posts have mid-week or future weekStarts)
    val openChores = assignments.filter { it.status == AssignmentStatus.AVAILABLE }
    val pendingTrades = tradeRequests.filter { it.status == TradeStatus.PENDING }
    val incomingTrades = pendingTrades.filter { it.toUserId == currentUser?.uid }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Chores") }) },
        floatingActionButton = {
            if (canManageChores) {
                FloatingActionButton(
                    onClick = { showDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add chore")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            if (!canManageChores) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        "Your role (Member) does not have permission to create chores. Please ask a household admin to do so.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // Run Assignment button
            Button(
                onClick = { vm.runAssignments() },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                enabled = chores.isNotEmpty() && !assignmentsRun
            ) {
                Text(if (assignmentsRun) "Chores posted for this week ✓" else "Post Chores for Pickup")
            }

            Spacer(Modifier.height(16.dp))

            if (chores.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No chores yet. Tap + to add one.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Trade requests sent to me
                    if (incomingTrades.isNotEmpty()) {
                        item {
                            Text(
                                "Incoming Trade Requests",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        items(incomingTrades, key = { it.id }) { request ->
                            val assignment = assignments.find { it.id == request.assignmentId }
                            val chore = chores.find { it.id == assignment?.choreId }
                            TradeRequestCard(
                                choreName = chore?.name ?: "Unknown",
                                dueDay = chore?.dueDayOfWeek?.let { DAYS[it] } ?: "",
                                dueHour = chore?.dueHour ?: 0,
                                fromName = roommates
                                    .find { it.userId == request.fromUserId }
                                    ?.displayName ?: "?",
                                reason = request.reason,
                                onAccept = { vm.respondToTrade(request.id, accept = true) },
                                onDeny = { vm.respondToTrade(request.id, accept = false) }
                            )
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }

                    // Unclaimed chores anyone can pick up
                    if (openChores.isNotEmpty()) {
                        item {
                            Text(
                                "Open for Pickup",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        items(openChores, key = { it.id }) { assignment ->
                            val chore = chores.find { it.id == assignment.choreId }
                            OpenChoreCard(
                                choreName = chore?.name ?: "Unknown",
                                dueDay = chore?.dueDayOfWeek?.let { DAYS[it] } ?: "",
                                dueHour = chore?.dueHour ?: 0,
                                reason = assignment.reason,
                                recommendedName = roommates
                                    .find { it.userId == assignment.assignedToRoommateId }
                                    ?.displayName ?: "?",
                                isRecommendedMe = assignment.assignedToRoommateId == currentUser?.uid,
                                onPickUp = { vm.claimAssignment(assignment.id) }
                            )
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }

                    // My assignments section
                    if (myAssignments.isNotEmpty()) {
                        item {
                            Text(
                                "Your Chores This Week",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        items(myAssignments, key = { it.id }) { assignment ->
                            val chore = chores.find { it.id == assignment.choreId }
                            val outgoingTrade = pendingTrades.find {
                                it.assignmentId == assignment.id && it.fromUserId == currentUser?.uid
                            }
                            MyAssignmentCard(
                                choreName = chore?.name ?: "Unknown",
                                dueDay = chore?.dueDayOfWeek?.let { DAYS[it] } ?: "",
                                dueHour = chore?.dueHour ?: 0,
                                reason = assignment.reason,
                                status = assignment.status,
                                hasConflict = assignment.hasConflict,
                                pendingTradeToName = outgoingTrade?.let { t ->
                                    roommates.find { it.userId == t.toUserId }?.displayName ?: "?"
                                },
                                onMarkComplete = { vm.markComplete(assignment.id) },
                                onTrade = { tradingAssignmentId = assignment.id },
                                onCancelTrade = { outgoingTrade?.let { vm.cancelTradeRequest(it.id) } }
                            )
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }

                    if (openChores.isNotEmpty() || myAssignments.isNotEmpty()) {
                        item {
                            Text(
                                "All Household Chores",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                    }

                    // All chores list
                    items(chores, key = { it.id }) { chore ->
                        val completedCount = assignments.count {
                            it.choreId == chore.id && it.status == AssignmentStatus.COMPLETED
                        }
                        val assignedTo = assignments
                            .find {
                                it.choreId == chore.id &&
                                    it.weekStart >= vm.weekStart &&
                                    it.status != AssignmentStatus.AVAILABLE
                            }
                            ?.let { a -> roommates.find { it.userId == a.assignedToRoommateId }?.displayName }

                        ChoreRow(
                            chore = chore,
                            completedCount = completedCount,
                            assignedTo = assignedTo,
                            canManage = canManageChores,
                            onEdit = { editingChore = chore },
                            onDelete = { vm.deleteChore(chore.id) }
                        )
                    }
                }
            }
        }

        if (showDialog) {
            CreateChoreDialog(
                householdId = household?.id ?: "",
                createdByRoommateId = currentUser?.uid ?: "",
                onDismiss = { showDialog = false },
                onConfirm = { chore ->
                    vm.addChore(chore)
                    showDialog = false
                }
            )
        }

        tradingAssignmentId?.let { assignmentId ->
            val others = roommates.filter { it.userId != currentUser?.uid }
            TradeRequestDialog(
                roommateNames = others.map { it.displayName },
                onDismiss = { tradingAssignmentId = null },
                onConfirm = { index, reason ->
                    vm.requestTrade(assignmentId, others[index].userId, reason)
                    tradingAssignmentId = null
                }
            )
        }

        editingChore?.let { chore ->
            CreateChoreDialog(
                householdId = chore.householdId,
                createdByRoommateId = chore.createdByRoommateId,
                existing = chore,
                onDismiss = { editingChore = null },
                onConfirm = { updated ->
                    vm.updateChore(updated)
                    editingChore = null
                }
            )
        }
    }
}

@Composable
private fun MyAssignmentCard(
    choreName: String,
    dueDay: String,
    dueHour: Int,
    reason: String,
    status: AssignmentStatus,
    hasConflict: Boolean,
    pendingTradeToName: String?,
    onMarkComplete: () -> Unit,
    onTrade: () -> Unit,
    onCancelTrade: () -> Unit
) {
    val containerColor = when {
        status == AssignmentStatus.COMPLETED -> MaterialTheme.colorScheme.secondaryContainer
        status == AssignmentStatus.MISSED -> MaterialTheme.colorScheme.errorContainer
        hasConflict -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(choreName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Due: $dueDay at ${"%02d:00".format(dueHour)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val statusLabel = when (status) {
                    AssignmentStatus.AVAILABLE -> "Open"
                    AssignmentStatus.PENDING -> "Pending"
                    AssignmentStatus.COMPLETED -> "Done ✓"
                    AssignmentStatus.MISSED -> "Missed"
                }
                val statusColor = when (status) {
                    AssignmentStatus.COMPLETED -> MaterialTheme.colorScheme.secondary
                    AssignmentStatus.MISSED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = statusColor.copy(alpha = 0.12f),
                    contentColor = statusColor
                ) {
                    Text(
                        statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                reason,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (hasConflict) {
                Text(
                    "⚠ Assigned despite schedule conflict",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (status == AssignmentStatus.PENDING) {
                Spacer(Modifier.height(8.dp))
                if (pendingTradeToName != null) {
                    Button(
                        onClick = onMarkComplete,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text("Complete")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Trade pending → $pendingTradeToName",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(onClick = onCancelTrade) { Text("Cancel") }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onMarkComplete,
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text("Complete")
                        }
                        OutlinedButton(
                            onClick = onTrade,
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text("Trade")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OpenChoreCard(
    choreName: String,
    dueDay: String,
    dueHour: Int,
    reason: String,
    recommendedName: String,
    isRecommendedMe: Boolean,
    onPickUp: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(choreName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Due: $dueDay at ${"%02d:00".format(dueHour)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        if (isRecommendedMe) "Recommended: You" else "Recommended: $recommendedName",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isRecommendedMe) FontWeight.Bold else FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                reason,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onPickUp,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small
            ) {
                Text("Pick up")
            }
        }
    }
}

@Composable
private fun TradeRequestCard(
    choreName: String,
    dueDay: String,
    dueHour: Int,
    fromName: String,
    reason: String,
    onAccept: () -> Unit,
    onDeny: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(choreName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Due: $dueDay at ${"%02d:00".format(dueHour)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.tertiary
                ) {
                    Text(
                        "From: $fromName",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }

            if (reason.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "\"$reason\"",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text("Accept")
                }
                OutlinedButton(
                    onClick = onDeny,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text("Deny")
                }
            }
        }
    }
}

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
private fun ChoreRow(
    chore: Chore,
    completedCount: Int,
    assignedTo: String?,
    canManage: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(chore.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "Due: ${DAYS[chore.dueDayOfWeek]} at ${"%02d:00".format(chore.dueHour)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Effort: ${chore.effortScore}/5  ·  ${chore.frequencyLabel()}",
                    style = MaterialTheme.typography.labelSmall
                )
                if (assignedTo != null) {
                    Text(
                        "Assigned to: $assignedTo",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    "Completed ${completedCount}×",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (canManage) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit chore")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete chore")
                }
            }
        }
    }
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
