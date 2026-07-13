package com.example.houseflow.data.repository

import com.example.houseflow.model.User

// Stores the authenticated identities (keyed by Firebase uid), separate from
// household membership (Roommate). Backed by Room.
interface UserRepository {
    suspend fun getUser(uid: String): User?
    suspend fun upsertUser(user: User)
    suspend fun getUsers(): List<User>
    suspend fun incrementCompletedCount(uid: String)
}
