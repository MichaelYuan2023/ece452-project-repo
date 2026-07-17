package com.example.houseflow.data.repository

import com.example.houseflow.model.AssignmentStatus
import com.example.houseflow.model.Chore
import com.example.houseflow.model.ChoreAssignment
import com.example.houseflow.model.TradeRequest
import com.example.houseflow.model.TradeStatus

// Backed by Room. All calls are suspend so they run off the main thread.
interface ChoreRepository {
    suspend fun getChores(householdId: String): List<Chore>
    suspend fun addChore(chore: Chore)
    suspend fun updateChore(chore: Chore)
    suspend fun deleteChore(choreId: String)
    suspend fun getAssignments(householdId: String): List<ChoreAssignment>
    suspend fun addAssignment(assignment: ChoreAssignment)
    suspend fun updateAssignment(assignment: ChoreAssignment)
    suspend fun updateAssignmentStatus(assignmentId: String, status: AssignmentStatus)
    suspend fun getCompletedCount(userId: String): Int
    suspend fun deleteStaleAvailable(cutoff: Long)
    suspend fun getTradeRequests(householdId: String): List<TradeRequest>
    suspend fun addTradeRequest(request: TradeRequest)
    // Returns false if the request was already resolved (or cancelled).
    suspend fun resolveTradeRequest(requestId: String, status: TradeStatus): Boolean
    suspend fun deleteTradeRequest(requestId: String)
}
