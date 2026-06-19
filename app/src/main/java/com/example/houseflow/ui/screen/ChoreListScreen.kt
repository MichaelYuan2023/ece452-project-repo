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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.unit.dp
import com.example.houseflow.model.AssignmentStatus
import com.example.houseflow.model.Chore
import com.example.houseflow.model.ChoreFrequency
import com.example.houseflow.ui.viewmodel.AppViewModel
import java.util.UUID

private val DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val HOURS = (0..23).map { h -> "%02d:00".format(h) }
private val FREQUENCIES = ChoreFrequency.entries.map { it.name }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoreListScreen(vm: AppViewModel) {
    val chores by vm.chores.collectAsState()
    val assignments by vm.assignments.collectAsState()
    val currentUser by vm.currentUser.collectAsState()
    val household by vm.household.collectAsState()
    val assignmentsRun by vm.assignmentsRun.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var editingChore by remember { mutableStateOf<Chore?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Chores") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add chore")
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

            Button(
                onClick = { vm.runAssignments() },
                modifier = Modifier.fillMaxWidth(),
                enabled = chores.isNotEmpty() && !assignmentsRun
            ) {
                Text(if (assignmentsRun) "Assignments done — see Dashboard" else "Run Fair Assignment")
            }

            Spacer(Modifier.height(12.dp))

            if (chores.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No chores yet. Tap + to add one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(chores, key = { it.id }) { chore ->
                        val completedCount = assignments.count {
                            it.choreId == chore.id && it.status == AssignmentStatus.COMPLETED
                        }
                        ChoreRow(
                            chore = chore,
                            completedCount = completedCount,
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
                createdByRoommateId = currentUser?.id ?: "",
                onDismiss = { showDialog = false },
                onConfirm = { chore ->
                    vm.addChore(chore)
                    showDialog = false
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
private fun ChoreRow(chore: Chore, completedCount: Int, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
                    "Effort: ${chore.effortScore}/5  ·  ${chore.frequency.name.lowercase()}",
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    "Completed ${completedCount}×",
                    style = MaterialTheme.typography.labelSmall
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
                SimpleDropdown("Frequency", FREQUENCIES, selectedFrequency.ordinal) {
                    selectedFrequency = ChoreFrequency.entries[it]
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
                    if (name.isNotBlank()) {
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
                                isTimeSensitive = timeSensitive
                            )
                        )
                    }
                }
            ) { Text(if (existing == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
