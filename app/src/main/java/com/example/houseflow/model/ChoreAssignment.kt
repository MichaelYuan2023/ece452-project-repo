package com.example.houseflow.model

enum class AssignmentStatus { PENDING, COMPLETED, MISSED }

data class ChoreAssignment(
    val id: String,
    val choreId: String,
    val householdId: String,
    val assignedToRoommateId: String,
    val weekStart: Long, // epoch ms of that week's Monday at midnight
    val status: AssignmentStatus,
    val reason: String, // human-readable explanation shown on dashboard
    val hasConflict: Boolean // true if assigned despite being busy
)
