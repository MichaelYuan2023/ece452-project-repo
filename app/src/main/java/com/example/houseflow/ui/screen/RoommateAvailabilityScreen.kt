package com.example.houseflow.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.houseflow.model.BlockType
import com.example.houseflow.model.BusyBlock
import com.example.houseflow.model.Roommate
import com.example.houseflow.ui.viewmodel.AppViewModel

private val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

// Fixed colors so the legend stays consistent across light/dark theme.
private fun typeColor(type: BlockType): Color = when (type) {
    BlockType.CLASS -> Color(0xFF7E57C2) // purple
    BlockType.WORK -> Color(0xFF42A5F5)  // blue
    BlockType.CLUB -> Color(0xFF66BB6A)  // green
    BlockType.OTHER -> Color(0xFFFFA726) // orange
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoommateAvailabilityScreen(vm: AppViewModel) {
    val roommates by vm.roommates.collectAsState()
    val blocksByRoommate by vm.householdBusyBlocks.collectAsState()

    // Compute one shared hour window across the whole household so every
    // roommate's calendar lines up vertically. Default to a daytime window
    // and expand it to cover any block that falls outside.
    val allBlocks = blocksByRoommate.values.flatten()
    val startHour = minOf(8, allBlocks.minOfOrNull { it.startHour } ?: 8)
    val endHour = maxOf(22, allBlocks.maxOfOrNull { it.endHour } ?: 22)

    Scaffold(
        topBar = { TopAppBar(title = { Text("Roommate Availability") }) }
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
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Spacer(Modifier.height(8.dp))
                    TypeLegend()
                }
                items(roommates, key = { it.id }) { roommate ->
                    RoommateCalendarCard(
                        roommate = roommate,
                        blocks = blocksByRoommate[roommate.id] ?: emptyList(),
                        startHour = startHour,
                        endHour = endHour
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun TypeLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BlockType.entries.forEach { type ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(typeColor(type))
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    type.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun RoommateCalendarCard(
    roommate: Roommate,
    blocks: List<BusyBlock>,
    startHour: Int,
    endHour: Int
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(roommate.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            if (blocks.isEmpty()) {
                Text(
                    "Fully available — no busy blocks this week.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                WeeklyAvailabilityGrid(blocks, startHour, endHour)
            }
        }
    }
}

// A compact 7-day × hourly grid. Each row is one hour; a coloured cell means
// the roommate is busy during that hour, blank means they're free.
@Composable
private fun WeeklyAvailabilityGrid(blocks: List<BusyBlock>, startHour: Int, endHour: Int) {
    val gridLine = MaterialTheme.colorScheme.surfaceVariant

    Column {
        // Day header row
        Row {
            HourLabelCell("")
            DAY_LABELS.forEach { day ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        day,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))

        // One row per hour in the shared window.
        for (hour in startHour until endHour) {
            Row(
                modifier = Modifier.fillMaxWidth().height(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HourLabelCell("%02d".format(hour))
                for (day in 0..6) {
                    val block = blockAt(blocks, day, hour)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .padding(0.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(block?.let { typeColor(it.type) } ?: gridLine.copy(alpha = 0.4f))
                    )
                }
            }
        }
    }
}

@Composable
private fun HourLabelCell(text: String) {
    Box(modifier = Modifier.width(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// The block covering [hour, hour+1) on the given day, if any. Matches the
// availability semantics used by the assignment algorithm (end is exclusive).
private fun blockAt(blocks: List<BusyBlock>, dayOfWeek: Int, hour: Int): BusyBlock? =
    blocks.firstOrNull { it.dayOfWeek == dayOfWeek && hour >= it.startHour && hour < it.endHour }
