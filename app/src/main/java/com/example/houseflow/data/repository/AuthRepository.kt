package com.example.houseflow.data.repository

import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow

// Migration seam: swap FirebaseAuthRepository for a fake in tests, or another
// auth provider, without touching the ViewModel.
interface AuthRepository {
    // The signed-in user right now, or null. Available synchronously at launch
    // so the nav graph can restore the session without waiting for a flow.
    val currentUser: FirebaseUser?

    // Emits the current user whenever auth state changes (sign-in, sign-out,
    // token refresh). Emits the current value immediately on collection.
    fun authState(): Flow<FirebaseUser?>

    suspend fun signIn(email: String, password: String): Result<FirebaseUser>

    suspend fun signUp(displayName: String, email: String, password: String): Result<FirebaseUser>

    fun signOut()
}
