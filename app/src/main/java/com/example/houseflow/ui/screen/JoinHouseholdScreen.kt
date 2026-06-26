package com.example.houseflow.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.houseflow.ui.viewmodel.AppViewModel

@Composable
fun JoinHouseholdScreen(vm: AppViewModel, onJoined: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Join Your Household", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text("Enter the invite code your household shared with you.")
            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = code,
                onValueChange = {
                    code = it
                    showError = false
                },
                label = { Text("Invite code") },
                singleLine = true,
                isError = showError,
                supportingText = if (showError) {
                    { Text("Invalid code. Try DEMO123.") }
                } else null,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    val joined = vm.joinHousehold(code)
                    if (joined) onJoined() else showError = true
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                enabled = code.isNotBlank()
            ) {
                Text("Join Household")
            }
        }
    }
}
