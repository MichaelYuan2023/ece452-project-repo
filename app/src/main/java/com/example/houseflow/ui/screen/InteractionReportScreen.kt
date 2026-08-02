package com.example.houseflow.ui.screen

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.houseflow.model.AssignmentStatus
import com.example.houseflow.model.ChoreAssignment
import com.example.houseflow.model.HouseholdRole
import com.example.houseflow.model.Roommate
import com.example.houseflow.model.TradeRequest
import com.example.houseflow.model.TradeStatus
import com.example.houseflow.ui.viewmodel.AppViewModel

// Per-roommate rollup of chore and trade activity, purely a display-layer
// transform over already-loaded ViewModel state (no new persistence).
private data class RoommateInteractionSummary(
    val roommate: Roommate,
    val completedChores: Int,
    val missedChores: Int,
    val pendingChores: Int,
    val tradesSent: Int,
    val tradesReceived: Int,
    val tradesAccepted: Int,
    val tradesDenied: Int
)

private fun buildSummaries(
    roommates: List<Roommate>,
    assignments: List<ChoreAssignment>,
    trades: List<TradeRequest>
): List<RoommateInteractionSummary> = roommates.map { roommate ->
    val mine = assignments.filter { it.assignedToRoommateId == roommate.userId }
    val sent = trades.filter { it.fromUserId == roommate.userId }
    val received = trades.filter { it.toUserId == roommate.userId }
    RoommateInteractionSummary(
        roommate = roommate,
        completedChores = mine.count { it.status == AssignmentStatus.COMPLETED },
        missedChores = mine.count { it.status == AssignmentStatus.MISSED },
        pendingChores = mine.count { it.status == AssignmentStatus.PENDING },
        tradesSent = sent.size,
        tradesReceived = received.size,
        tradesAccepted = sent.count { it.status == TradeStatus.ACCEPTED },
        tradesDenied = sent.count { it.status == TradeStatus.DENIED }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractionReportScreen(vm: AppViewModel, onBack: () -> Unit) {
    val roommates by vm.roommates.collectAsState()
    val assignments by vm.assignments.collectAsState()
    val trades by vm.tradeRequests.collectAsState()

    val summaries = buildSummaries(roommates, assignments, trades)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Interaction Reports") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (summaries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No roommates yet — reports will appear once your household has activity.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp)
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { HouseholdSummaryCard(summaries) }
            items(summaries, key = { it.roommate.userId }) { summary ->
                RoommateReportCard(summary)
            }
        }
    }
}

@Composable
private fun HouseholdSummaryCard(summaries: List<RoommateInteractionSummary>) {
    val totalCompleted = summaries.sumOf { it.completedChores }
    val totalMissed = summaries.sumOf { it.missedChores }
    val totalTrades = summaries.sumOf { it.tradesSent }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Household Overview",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatColumn("Completed", totalCompleted)
                StatColumn("Missed", totalMissed)
                StatColumn("Trades", totalTrades)
                StatColumn("Roommates", summaries.size)
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun RoommateReportCard(summary: RoommateInteractionSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        summary.roommate.displayName.first().uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(summary.roommate.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        roleLabel(summary.roommate.role),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Chores",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InteractionChip("${summary.completedChores} completed")
                InteractionChip("${summary.missedChores} missed")
                InteractionChip("${summary.pendingChores} pending")
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Trades",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InteractionChip("${summary.tradesSent} sent")
                InteractionChip("${summary.tradesReceived} received")
                InteractionChip("${summary.tradesAccepted} accepted")
                InteractionChip("${summary.tradesDenied} denied")
            }
        }
    }
}

@Composable
private fun InteractionChip(label: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

private fun roleLabel(role: HouseholdRole): String = when (role) {
    HouseholdRole.CREATOR -> "Creator"
    HouseholdRole.ADMIN -> "Admin"
    HouseholdRole.MEMBER -> "Member"
}
