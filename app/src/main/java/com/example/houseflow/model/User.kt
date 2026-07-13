package com.example.houseflow.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// The authenticated identity. uid is the Firebase Auth uid and is the single
// source of truth for a person across the app; every household membership
// (Roommate) and every id-bearing record references this uid.
@Entity(tableName = "users")
data class User(
    @PrimaryKey val uid: String,
    val email: String,
    val displayName: String,
    val completedChoreCount: Int = 0,
    // The household the user was last active in, so sign-in can resume there
    // instead of always landing on the household selection screen.
    val activeHouseholdId: String? = null
)
