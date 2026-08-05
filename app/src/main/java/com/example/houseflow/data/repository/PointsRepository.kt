package com.example.houseflow.data.repository

import com.example.houseflow.model.PointsEntry

// The HF-13 gamification ledger. Backed by Room; all calls are suspend.
interface PointsRepository {
    // Idempotent — awarding the same assignment twice is a no-op (PK = assignment id).
    suspend fun award(entry: PointsEntry)
    suspend fun getEntries(householdId: String): List<PointsEntry>
    // Cascade cleanup when a chore is deleted.
    suspend fun deleteForChore(choreId: String)
}
