package com.example.houseflow.model

enum class ChoreFrequency { DAILY, WEEKLY }

// dueDayOfWeek: 0=Monday … 6=Sunday; dueHour: 0–23
data class Chore(
    val id: String,
    val householdId: String,
    val createdByRoommateId: String,
    val name: String,
    val description: String,
    val frequency: ChoreFrequency,
    val effortScore: Int, // 1–5
    val dueDayOfWeek: Int,
    val dueHour: Int,
    val isTimeSensitive: Boolean
)
