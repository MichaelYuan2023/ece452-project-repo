package com.example.houseflow.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "households")
data class Household(
    @PrimaryKey val id: String,
    val name: String,
    val inviteCode: String // hardcoded "DEMO123" in seed data
)
