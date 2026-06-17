package com.example.houseflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.example.houseflow.ui.navigation.AppNavGraph
import com.example.houseflow.ui.theme.HouseFlowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HouseFlowTheme {
                val navController = rememberNavController()
                AppNavGraph(navController)
            }
        }
    }
}
