package com.example.houseflow.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.houseflow.model.AssignmentStatus
import com.example.houseflow.model.BlockType
import com.example.houseflow.model.BusyBlock
import com.example.houseflow.model.Roommate
import com.example.houseflow.ui.viewmodel.AppViewModel

private val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val FULL_DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

private fun typeColor(type: BlockType): Color = when (type) {
    BlockType.CLASS -> Color(0xFF7E57C2)
    BlockType.WORK -> Color(0xFF42A5F5)
    BlockType.CLUB -> Color(0xFF66BB6A)
    BlockType.OTHER -> Color(0xFFFFA726)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoommateAvailabilityScreen(vm: AppViewModel) {
    val roommates by vm.roommates.collectAsState()
    val blocksByRoommate by vm.householdBusyBlocks.collectAsState()
    val assignments by vm.assignments.collectAsState()
    val chores by vm.chores.collectAsState()
    var selectedRoommate by remember { mutableStateOf<Roommate?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Roommates") }) }
    ) { padding ->
        if (roommates.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No roommates yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(Modifier.height(8.dp)) }
                items(roommates, key = { it.id }) { roommate ->
                    RoommateListItem(
                        roommate = roommate,
                        onClick = { selectedRoommate = roommate }
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }

    // Popup card when a roommate is tapped
    selectedRoommate?.let { roommate ->
        val blocks = blocksByRoommate[roommate.id] ?: emptyList()
        val roommateAssignments = assignments.filter {
            it.assignedToRoommateId == roommate.id && it.weekStart == vm.weekStart
        }

        RoommateProfileDialog(
            roommate = roommate,
            blocks = blocks,
            assignedChoreNames = roommateAssignments.mapNotNull { a ->
                val chore = chores.find { it.id == a.choreId }
                if (chore != null) {
                    val status = when (a.status) {
                        AssignmentStatus.COMPLETED -> " ✓"
                        AssignmentStatus.MISSED -> " ✗"
                        else -> ""
                    }
                    "${chore.name}$status"
                } else null
            },
            onDismiss = { selectedRoommate = null }
        )
    }
}

@Composable
private fun RoommateListItem(roommate: Roommate, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar circle with initial
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = roommate.name.first().uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(roommate.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tap to view schedule & chores",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RoommateProfileDialog(
    roommate: Roommate,
    blocks: List<BusyBlock>,
    assignedChoreNames: List<String>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = roommate.name.first().uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(roommate.name)
            }
        },
        text = {
            Column {
                // Schedule section
                Text(
                    "Schedule",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))

                if (blocks.isEmpty()) {
                    Text(
                        "Fully available — no busy blocks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // Compact mini calendar grid
                    MiniWeeklyGrid(blocks)
                    Spacer(Modifier.height(4.dp))
                    // Legend
                    MiniTypeLegend()
                }

                Spacer(Modifier.height(16.dp))

                // Chores section
                Text(
                    "This Week's Chores",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))

                if (assignedChoreNames.isEmpty()) {
                    Text(
                        "No chores assigned this week.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    assignedChoreNames.forEach { name ->
                        Text(
                            "• $name",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun MiniWeeklyGrid(blocks: List<BusyBlock>) {
    val startHour = minOf(8, blocks.minOfOrNull { it.startHour } ?: 8)
    val endHour = maxOf(22, blocks.maxOfOrNull { it.endHour } ?: 22)

    Column {
        // Day header
        Row {
            Box(modifier = Modifier.width(24.dp))
            DAY_LABELS.forEach { day ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        day,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 9.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))

        // Grid rows
        for (hour in startHour until endHour) {
            Row(
                modifier = Modifier.fillMaxWidth().height(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "%02d".format(hour),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                for (day in 0..6) {
                    val block = blocks.firstOrNull {
                        it.dayOfWeek == day && hour >= it.startHour && hour < it.endHour
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .padding(0.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                block?.let { typeColor(it.type) }
                                    ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniTypeLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BlockType.entries.forEach { type ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(typeColor(type))
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    type.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp
                )
            }
        }
    }
}
