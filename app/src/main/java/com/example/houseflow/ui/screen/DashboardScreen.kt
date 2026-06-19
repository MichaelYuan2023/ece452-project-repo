package com.example.houseflow.ui.screen

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.houseflow.model.AssignmentStatus
import com.example.houseflow.model.ChoreAssignment
import com.example.houseflow.ui.viewmodel.AppViewModel

private val DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(vm: AppViewModel) {
    val assignments by vm.assignments.collectAsState()
    val chores by vm.chores.collectAsState()
    val roommates by vm.roommates.collectAsState()
    val currentUser by vm.currentUser.collectAsState()

    val completedCount = assignments.count {
        it.assignedToRoommateId == currentUser?.id &&
            it.status == AssignmentStatus.COMPLETED &&
            it.weekStart == vm.weekStart
    }

    LaunchedEffect(Unit) { vm.refreshOverdue() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("This Week") }) }
    ) { padding ->
        if (assignments.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No assignments yet. Add chores and tap 'Run Fair Assignment'.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Text(
                            text = "Your completed chores this week: $completedCount",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                items(assignments, key = { it.id }) { assignment ->
                    val chore = chores.find { it.id == assignment.choreId }
                    val assignee = roommates.find { it.id == assignment.assignedToRoommateId }
                    val isMyChore = assignment.assignedToRoommateId == currentUser?.id

                    AssignmentCard(
                        assignment = assignment,
                        choreName = chore?.name ?: "Unknown chore",
                        dueDay = chore?.dueDayOfWeek?.let { DAYS[it] } ?: "",
                        dueHour = chore?.dueHour ?: 0,
                        assigneeName = assignee?.name ?: "Unknown",
                        isMyChore = isMyChore,
                        onMarkComplete = { vm.markComplete(assignment.id) },
                        onSwap = { vm.swapAssignment(assignment.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AssignmentCard(
    assignment: ChoreAssignment,
    choreName: String,
    dueDay: String,
    dueHour: Int,
    assigneeName: String,
    isMyChore: Boolean,
    onMarkComplete: () -> Unit,
    onSwap: () -> Unit
) {
    val containerColor = when {
        assignment.status == AssignmentStatus.MISSED -> MaterialTheme.colorScheme.errorContainer
        assignment.hasConflict -> MaterialTheme.colorScheme.errorContainer
        assignment.status == AssignmentStatus.COMPLETED -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(choreName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Due: $dueDay at ${"%02d:00".format(dueHour)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Assigned to: $assigneeName",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                StatusBadge(assignment.status)
            }

            Spacer(Modifier.height(6.dp))
            Text(
                assignment.reason,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (assignment.hasConflict) {
                Text(
                    "⚠ Conflict: assigned despite busy schedule",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (isMyChore && assignment.status == AssignmentStatus.PENDING) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onMarkComplete, modifier = Modifier.fillMaxWidth()) {
                    Text("Mark Complete")
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = onSwap, modifier = Modifier.fillMaxWidth()) {
                    Text("Can't do this")
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: AssignmentStatus) {
    val (label, color) = when (status) {
        AssignmentStatus.PENDING -> "Pending" to MaterialTheme.colorScheme.onSurfaceVariant
        AssignmentStatus.COMPLETED -> "Done" to MaterialTheme.colorScheme.primary
        AssignmentStatus.MISSED -> "Missed" to MaterialTheme.colorScheme.error
    }
    Text(label, style = MaterialTheme.typography.labelSmall, color = color)
}
