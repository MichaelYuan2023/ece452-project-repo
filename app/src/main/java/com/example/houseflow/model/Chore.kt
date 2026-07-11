package com.example.houseflow.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ChoreFrequency { DAILY, WEEKLY, EVERY_N_DAYS, ONE_TIME }

// dueDayOfWeek: 0=Monday … 6=Sunday; dueHour: 0–23
@Entity(tableName = "chores")
data class Chore(
    @PrimaryKey val id: String,
    val householdId: String,
    val createdByRoommateId: String,
    val name: String,
    val description: String,
    val frequency: ChoreFrequency,
    val effortScore: Int, // 1–5
    val dueDayOfWeek: Int,
    val dueHour: Int,
    val isTimeSensitive: Boolean,
    val intervalDays: Int? = null // only used when frequency == EVERY_N_DAYS
)
