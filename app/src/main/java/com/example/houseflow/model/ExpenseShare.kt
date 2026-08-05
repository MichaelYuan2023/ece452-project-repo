package com.example.houseflow.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// One participant's portion of an [Expense]. Stored explicitly (rather than
// recomputed) so non-equal splits can be introduced later without changing how
// balances are read. [householdId] is denormalized from the parent expense so
// all shares for a household can be queried directly.
//
// The primary key "<expenseId>:<userId>" guarantees one share per participant
// per expense.
@Entity(tableName = "expense_shares")
data class ExpenseShare(
    @PrimaryKey val id: String,   // "<expenseId>:<userId>"
    val expenseId: String,
    val householdId: String,
    val userId: String,
    val shareCents: Int
)
