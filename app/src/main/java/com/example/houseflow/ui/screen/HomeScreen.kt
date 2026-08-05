package com.example.houseflow.ui.screen

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.houseflow.model.AssignmentStatus
import com.example.houseflow.ui.components.Avatar
import com.example.houseflow.ui.components.EmptyState
import com.example.houseflow.ui.components.HFCard
import com.example.houseflow.ui.components.IconChip
import com.example.houseflow.ui.components.Pill
import com.example.houseflow.ui.components.ScreenHeader
import com.example.houseflow.ui.components.SectionHeader
import com.example.houseflow.ui.theme.Gold
import com.example.houseflow.util.ExpenseMath
import com.example.houseflow.ui.viewmodel.AppViewModel

private val HOME_DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

@Composable
fun HomeScreen(
    vm: AppViewModel,
    onOpenScoreboard: () -> Unit,
    onOpenChores: () -> Unit,
    onOpenExpenses: () -> Unit
) {
    val currentUser by vm.currentUser.collectAsState()
    val household by vm.household.collectAsState()
    val summary by vm.myPointsSummary.collectAsState()
    val leaderboard by vm.leaderboard.collectAsState()
    val assignments by vm.assignments.collectAsState()
    val chores by vm.chores.collectAsState()
    val pairBalances by vm.myPairBalances.collectAsState()

    val myUid = currentUser?.uid
    val myChores = assignments.filter {
        it.assignedToRoommateId == myUid &&
            it.weekStart >= vm.weekStart &&
            it.status == AssignmentStatus.PENDING
    }
    val topThree = leaderboard.take(3)
    val owedByMeCents = pairBalances.filter { it.netCents < 0 }.sumOf { -it.netCents }
    val owedToMeCents = pairBalances.filter { it.netCents > 0 }.sumOf { it.netCents }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Hi, ${currentUser?.displayName ?: "there"}",
            subtitle = household?.let { "${it.name} · this week" }
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Points snapshot
            item {
                HFCard(onClick = onOpenScoreboard) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconChip(icon = Icons.Default.EmojiEvents, tint = Gold)
                        Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                            Text(
                                "Points this week",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text("${summary.weeklyPoints}", style = MaterialTheme.typography.headlineMedium)
                        }
                        Pill("Level ${summary.level}", tint = Gold)
                    }
                    Spacer(Modifier.height(14.dp))
                    val progress = if (summary.pointsForNextLevel > 0)
                        summary.pointsIntoLevel.toFloat() / summary.pointsForNextLevel else 0f
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(MaterialTheme.shapes.extraSmall),
                        color = Gold,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        buildString {
                            append("${summary.pointsIntoLevel}/${summary.pointsForNextLevel} to level ${summary.level + 1}")
                            if (summary.streakWeeks > 0) append("  ·  ${summary.streakWeeks} wk streak")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Mini leaderboard
            item {
                SectionHeader("Leaderboard", actionLabel = "See all", onAction = onOpenScoreboard)
            }
            if (topThree.isEmpty() || topThree.all { it.weeklyPoints == 0 && it.allTimePoints == 0 }) {
                item {
                    HFCard { EmptyState(Icons.Default.EmojiEvents, "No points yet", "Complete chores to get on the board.") }
                }
            } else {
                item {
                    HFCard(onClick = onOpenScoreboard) {
                        topThree.forEachIndexed { i, entry ->
                            if (i > 0) Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${entry.rank}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                                Avatar(entry.displayName, size = 32.dp)
                                Text(
                                    if (entry.userId == myUid) "${entry.displayName} (you)" else entry.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 12.dp).weight(1f)
                                )
                                Text(
                                    "${entry.weeklyPoints} pts",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Chores this week
            item {
                SectionHeader("Your chores this week", actionLabel = "All chores", onAction = onOpenChores)
            }
            if (myChores.isEmpty()) {
                item {
                    HFCard { EmptyState(Icons.Default.Checklist, "Nothing due", "You have no chores assigned this week.") }
                }
            } else {
                items(myChores.take(4), key = { it.id }) { assignment ->
                    val chore = chores.find { it.id == assignment.choreId }
                    HFCard(onClick = onOpenChores) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconChip(icon = Icons.Default.Checklist, tint = MaterialTheme.colorScheme.primary, size = 36.dp)
                            Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                                Text(chore?.name ?: "Chore", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    chore?.let { "Due ${HOME_DAYS[it.dueDayOfWeek]} at ${"%02d:00".format(it.dueHour)}" } ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (assignment.hasConflict) Pill("conflict", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Balance snapshot
            item {
                SectionHeader("Expenses", actionLabel = "Open", onAction = onOpenExpenses)
            }
            item {
                // Mirrors the Expenses summary: what you owe leads, and the two
                // sides are never netted against each other, so this can't read
                // "All settled up" while a debt to one roommate is outstanding.
                val settled = owedByMeCents == 0 && owedToMeCents == 0
                val accent = when {
                    settled -> MaterialTheme.colorScheme.onSurfaceVariant
                    owedByMeCents > 0 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.secondary
                }
                HFCard(onClick = onOpenExpenses) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconChip(icon = Icons.Default.Payments, tint = accent)
                        Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                            Text(
                                when {
                                    settled -> "All settled up"
                                    owedByMeCents > 0 -> "You owe"
                                    else -> "You're owed"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                ExpenseMath.formatCents(
                                    if (owedByMeCents > 0) owedByMeCents else owedToMeCents
                                ),
                                style = MaterialTheme.typography.headlineSmall,
                                color = accent,
                                fontWeight = FontWeight.SemiBold
                            )
                            // Surfaced only when both sides are live, so the
                            // headline figure is never the whole story.
                            if (owedByMeCents > 0 && owedToMeCents > 0) {
                                Text(
                                    "you're owed ${ExpenseMath.formatCents(owedToMeCents)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}
