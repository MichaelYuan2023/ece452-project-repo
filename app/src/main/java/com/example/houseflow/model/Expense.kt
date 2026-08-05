package com.example.houseflow.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// How an expense's total is divided among its participants. Only EQUAL is used
// in v1; the enum + per-participant [ExpenseShare] rows leave room for CUSTOM /
// PERCENTAGE splits later without a schema change to Expense itself.
enum class SplitType { EQUAL }

// A shared household cost that one roommate paid and that is split among a set
// of participants. Money is stored as integer cents ([amountCents]) to avoid
// floating-point rounding error.
@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey val id: String,
    val householdId: String,
    val paidByUserId: String,     // the roommate who fronted the money
    val createdByUserId: String,  // who logged it — used for delete permission
    val description: String,
    val amountCents: Int,         // always > 0
    val splitType: SplitType,
    val createdAt: Long
)
