package com.example.houseflow.model

import androidx.room.Entity

// CREATOR is set once at household creation and is otherwise immutable — no one
// can promote/demote a CREATOR, and no one can be promoted to CREATOR. ADMIN can
// promote MEMBER -> ADMIN but cannot demote anyone. Only CREATOR can demote
// ADMIN -> MEMBER. See AppViewModel.promoteToAdmin/demoteToMember for enforcement.
enum class HouseholdRole { CREATOR, ADMIN, MEMBER }

// A household-scoped membership: links an authenticated User (userId = Firebase
// uid) to a household. displayName is denormalized from the User for convenient
// display in lists and cards. The person's identity lives in [User]; this is
// only the "X is a member of household Y" relationship.
@Entity(tableName = "memberships", primaryKeys = ["userId", "householdId"])
data class Roommate(
    val userId: String,
    val householdId: String,
    val displayName: String,
    val role: HouseholdRole = HouseholdRole.MEMBER
)
