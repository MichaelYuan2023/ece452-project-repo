package com.example.houseflow.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.houseflow.ui.components.Avatar
import com.example.houseflow.ui.components.HFCard
import com.example.houseflow.ui.components.IconChip
import com.example.houseflow.ui.components.ScreenHeader
import com.example.houseflow.ui.theme.Gold
import com.example.houseflow.ui.viewmodel.AppViewModel

@Composable
fun MoreScreen(
    vm: AppViewModel,
    onOpenScoreboard: () -> Unit,
    onOpenRoommates: () -> Unit,
    onOpenReports: () -> Unit,
    onSignOut: () -> Unit
) {
    val currentUser by vm.currentUser.collectAsState()
    val household by vm.household.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "More")
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Account header
            currentUser?.let { user ->
                HFCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(user.displayName, size = 48.dp)
                        Column(modifier = Modifier.padding(start = 16.dp)) {
                            Text(user.displayName, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                user.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            MoreRow(
                icon = Icons.Default.EmojiEvents,
                tint = Gold,
                title = "Scoreboard",
                subtitle = "Weekly points and leaderboard",
                onClick = onOpenScoreboard
            )
            MoreRow(
                icon = Icons.Default.Group,
                tint = MaterialTheme.colorScheme.primary,
                title = "Roommates",
                subtitle = "Household members and availability",
                onClick = onOpenRoommates
            )
            MoreRow(
                icon = Icons.Default.Assessment,
                tint = MaterialTheme.colorScheme.secondary,
                title = "Interaction Reports",
                subtitle = "Chore and trade activity by roommate",
                onClick = onOpenReports
            )
            MoreRow(
                icon = Icons.Default.Home,
                tint = MaterialTheme.colorScheme.tertiary,
                title = "Households",
                subtitle = household?.let { "Currently in ${it.name} — switch, join, or create" }
                    ?: "Join or create a household",
                onClick = { vm.openHouseholdSwitcher() }
            )
            MoreRow(
                icon = Icons.AutoMirrored.Filled.Logout,
                tint = MaterialTheme.colorScheme.error,
                title = "Sign Out",
                subtitle = null,
                onClick = onSignOut
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun MoreRow(
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    HFCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(icon = icon, tint = tint)
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
