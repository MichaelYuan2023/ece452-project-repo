package com.example.houseflow.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.houseflow.ui.screen.CreateAccountScreen
import com.example.houseflow.ui.screen.JoinHouseholdScreen
import com.example.houseflow.ui.screen.MainScreen
import com.example.houseflow.ui.viewmodel.AppViewModel

object Routes {
    const val CREATE_ACCOUNT = "create_account"
    const val JOIN_HOUSEHOLD = "join_household"
    const val MAIN = "main"
}

@Composable
fun AppNavGraph(navController: NavHostController) {
    val vm: AppViewModel = viewModel(factory = AppViewModel.Factory)

    NavHost(navController = navController, startDestination = Routes.CREATE_ACCOUNT) {
        composable(Routes.CREATE_ACCOUNT) {
            CreateAccountScreen(vm) {
                navController.navigate(Routes.JOIN_HOUSEHOLD)
            }
        }
        composable(Routes.JOIN_HOUSEHOLD) {
            JoinHouseholdScreen(vm) {
                navController.navigate(Routes.MAIN) {
                    popUpTo(Routes.CREATE_ACCOUNT) { inclusive = true }
                }
            }
        }
        composable(Routes.MAIN) {
            MainScreen(vm)
        }
    }
}
