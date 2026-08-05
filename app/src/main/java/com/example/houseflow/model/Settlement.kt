package com.example.houseflow.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// A recorded repayment from one roommate to another (e.g. an e-transfer to pay
// off a debt). Settlements offset the net balances derived from expenses, so
// recording one moves both parties back toward zero. Amount is integer cents.
@Entity(tableName = "settlements")
data class Settlement(
    @PrimaryKey val id: String,
    val householdId: String,
    val fromUserId: String,   // paid the money (their debt decreases)
    val toUserId: String,     // received it (what they're owed decreases)
    val amountCents: Int,     // always > 0
    val createdAt: Long
)
