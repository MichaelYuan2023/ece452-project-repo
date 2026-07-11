package com.example.houseflow.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.houseflow.ui.viewmodel.AppViewModel
import kotlinx.coroutines.launch

// onAuthenticated defaults to a no-op: successful auth flips the ViewModel's
// session state, which drives navigation away from this screen automatically.
@Composable
fun AuthScreen(vm: AppViewModel, onAuthenticated: () -> Unit = {}) {
    var isSignUp by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Sign-up additionally requires a non-blank display name.
    val canSubmit = email.isNotBlank() &&
        password.isNotBlank() &&
        (!isSignUp || name.isNotBlank()) &&
        !loading

    fun submit() {
        error = null
        loading = true
        scope.launch {
            val result = if (isSignUp) {
                vm.signUp(name, email, password)
            } else {
                vm.signIn(email, password)
            }
            loading = false
            result
                .onSuccess { onAuthenticated() }
                .onFailure { error = it.localizedMessage ?: "Authentication failed. Please try again." }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Welcome to HouseFlow", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                if (isSignUp) "Create an account to get started." else "Sign in to continue.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(32.dp))

            if (isSignUp) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display name") },
                    singleLine = true,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                enabled = !loading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                enabled = !loading,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { submit() },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                enabled = canSubmit
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(if (isSignUp) "Sign Up" else "Sign In")
                }
            }
            Spacer(Modifier.height(8.dp))

            TextButton(
                onClick = {
                    isSignUp = !isSignUp
                    error = null
                },
                enabled = !loading
            ) {
                Text(
                    if (isSignUp) "Already have an account? Sign in"
                    else "New here? Create an account"
                )
            }
        }
    }
}
