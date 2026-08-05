package com.example.houseflow.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.houseflow.ui.viewmodel.AppViewModel

private enum class Tab { HOME, SCHEDULE, CHORES, EXPENSES, BULLETIN, MORE }

// Full-screen destinations reached from Home links / the More hub, layered on
// top of the tab shell and dismissed with the system/back-arrow (BackHandler).
private enum class SubScreen { SCOREBOARD, ROOMMATES, REPORTS }

@Composable
fun MainScreen(vm: AppViewModel, onSignOut: () -> Unit) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var sub by remember { mutableStateOf<SubScreen?>(null) }

    // Notification runtime permission (Android 13+).
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op: notifications simply won't show if denied */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Sub-screen overlay takes over the whole surface and owns back navigation.
    if (sub != null) {
        BackHandler { sub = null }
        val dismiss = { sub = null }
        when (sub) {
            SubScreen.SCOREBOARD -> ScoreboardScreen(vm, onBack = dismiss)
            SubScreen.ROOMMATES -> RoommateAvailabilityScreen(vm, onBack = dismiss)
            SubScreen.REPORTS -> InteractionReportScreen(vm, onBack = dismiss)
            null -> Unit
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline
                )
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    val navColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    NavigationBarItem(
                        selected = tab == Tab.HOME,
                        onClick = { tab = Tab.HOME },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("Home", style = MaterialTheme.typography.labelSmall) },
                        colors = navColors
                    )
                    NavigationBarItem(
                        selected = tab == Tab.SCHEDULE,
                        onClick = { tab = Tab.SCHEDULE },
                        icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                        label = { Text("Schedule", style = MaterialTheme.typography.labelSmall) },
                        colors = navColors
                    )
                    NavigationBarItem(
                        selected = tab == Tab.CHORES,
                        onClick = { tab = Tab.CHORES },
                        icon = { Icon(Icons.Default.Checklist, contentDescription = null) },
                        label = { Text("Chores", style = MaterialTheme.typography.labelSmall) },
                        colors = navColors
                    )
                    NavigationBarItem(
                        selected = tab == Tab.EXPENSES,
                        onClick = { tab = Tab.EXPENSES },
                        icon = { Icon(Icons.Default.Payments, contentDescription = null) },
                        label = { Text("Expenses", style = MaterialTheme.typography.labelSmall) },
                        colors = navColors
                    )
                    NavigationBarItem(
                        selected = tab == Tab.BULLETIN,
                        onClick = { tab = Tab.BULLETIN },
                        icon = { Icon(Icons.Default.Campaign, contentDescription = null) },
                        label = { Text("Bulletin", style = MaterialTheme.typography.labelSmall) },
                        colors = navColors
                    )
                    NavigationBarItem(
                        selected = tab == Tab.MORE,
                        onClick = { tab = Tab.MORE },
                        icon = { Icon(Icons.Default.Menu, contentDescription = null) },
                        label = { Text("More", style = MaterialTheme.typography.labelSmall) },
                        colors = navColors
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (tab) {
                Tab.HOME -> HomeScreen(
                    vm,
                    onOpenScoreboard = { sub = SubScreen.SCOREBOARD },
                    onOpenChores = { tab = Tab.CHORES },
                    onOpenExpenses = { tab = Tab.EXPENSES }
                )
                Tab.SCHEDULE -> AvailabilityScreen(vm)
                Tab.CHORES -> ChoreListScreen(vm)
                Tab.EXPENSES -> ExpensesScreen(vm)
                Tab.BULLETIN -> DashboardScreen(vm)
                Tab.MORE -> MoreScreen(
                    vm,
                    onOpenScoreboard = { sub = SubScreen.SCOREBOARD },
                    onOpenRoommates = { sub = SubScreen.ROOMMATES },
                    onOpenReports = { sub = SubScreen.REPORTS },
                    onSignOut = onSignOut
                )
            }
        }
    }
}
