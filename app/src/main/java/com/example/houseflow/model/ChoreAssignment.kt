package com.example.houseflow.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AssignmentStatus { AVAILABLE, PENDING, COMPLETED, MISSED }

@Entity(tableName = "assignments")
data class ChoreAssignment(
    @PrimaryKey val id: String,
    val choreId: String,
    val householdId: String,
    val assignedToRoommateId: String, // while AVAILABLE this is the recommended roommate, not an assignee
    val weekStart: Long, // epoch ms of that week's Monday at midnight
    val status: AssignmentStatus,
    val reason: String, // human-readable explanation shown on dashboard
    val hasConflict: Boolean // true if assigned despite being busy
)
