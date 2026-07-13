package com.example.houseflow.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.houseflow.ui.screen.AuthScreen
import com.example.houseflow.ui.screen.HouseholdSelectionScreen
import com.example.houseflow.ui.screen.MainScreen
import com.example.houseflow.ui.viewmodel.AppViewModel
import com.example.houseflow.ui.viewmodel.SessionState

// Top-level gating is driven purely by session state rather than a NavController:
// auth, household membership, and sign-out all just change state, and the right
// screen is shown. (Tab navigation within MainScreen is handled there.) The
// household switcher is an extra flag layered on top of IN_HOUSEHOLD so it can
// be reached from Settings without disrupting the signed-in session.
@Composable
fun AppNavGraph() {
    val vm: AppViewModel = viewModel(factory = AppViewModel.Factory)
    val session by vm.sessionState.collectAsState()
    val showSwitcher by vm.showHouseholdSwitcher.collectAsState()

    when {
        session == SessionState.LOADING -> LoadingScreen()
        session == SessionState.SIGNED_OUT -> AuthScreen(vm)
        session == SessionState.NEEDS_HOUSEHOLD -> HouseholdSelectionScreen(vm, onSignOut = { vm.signOut() })
        showSwitcher -> HouseholdSelectionScreen(
            vm,
            onBack = { vm.closeHouseholdSwitcher() },
            onSignOut = { vm.signOut() }
        )
        else -> MainScreen(vm, onSignOut = { vm.signOut() })
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}
