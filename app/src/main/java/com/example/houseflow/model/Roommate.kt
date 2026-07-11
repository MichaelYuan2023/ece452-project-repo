package com.example.houseflow.model

import androidx.room.Entity

// A household-scoped membership: links an authenticated User (userId = Firebase
// uid) to a household. displayName is denormalized from the User for convenient
// display in lists and cards. The person's identity lives in [User]; this is
// only the "X is a member of household Y" relationship.
@Entity(tableName = "memberships", primaryKeys = ["userId", "householdId"])
data class Roommate(
    val userId: String,
    val householdId: String,
    val displayName: String
)
