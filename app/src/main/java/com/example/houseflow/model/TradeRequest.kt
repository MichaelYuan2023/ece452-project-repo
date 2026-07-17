package com.example.houseflow.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TradeStatus { PENDING, ACCEPTED, DENIED }

// A direct request from the current owner of a claimed assignment to a
// specific roommate, asking them to take it over.
@Entity(tableName = "trade_requests")
data class TradeRequest(
    @PrimaryKey val id: String,
    val assignmentId: String,
    val householdId: String,
    val fromUserId: String,
    val toUserId: String,
    val reason: String,
    val status: TradeStatus,
    val createdAt: Long
)
