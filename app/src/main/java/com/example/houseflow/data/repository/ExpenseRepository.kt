package com.example.houseflow.data.repository

import com.example.houseflow.model.Expense
import com.example.houseflow.model.ExpenseShare
import com.example.houseflow.model.Settlement

// The HF-15 shared-expenses store. Backed by Room; all calls are suspend.
interface ExpenseRepository {
    // Persists the expense together with its per-participant shares.
    suspend fun addExpense(expense: Expense, shares: List<ExpenseShare>)
    suspend fun getExpenses(householdId: String): List<Expense>
    suspend fun getShares(householdId: String): List<ExpenseShare>
    // Removes the expense and its shares (balances recompute from what remains).
    suspend fun deleteExpense(expenseId: String)

    suspend fun getSettlements(householdId: String): List<Settlement>
    suspend fun addSettlement(settlement: Settlement)
    suspend fun deleteSettlement(settlementId: String)
}
