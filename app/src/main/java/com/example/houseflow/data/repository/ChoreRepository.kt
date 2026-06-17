package com.example.houseflow.data.repository

import com.example.houseflow.model.AssignmentStatus
import com.example.houseflow.model.Chore
import com.example.houseflow.model.ChoreAssignment

// Migration seam: swap InMemoryChoreRepository for a Room-backed implementation.
interface ChoreRepository {
    fun getChores(householdId: String): List<Chore>
    fun addChore(chore: Chore)
    fun deleteChore(choreId: String)
    fun getAssignments(householdId: String): List<ChoreAssignment>
    fun addAssignment(assignment: ChoreAssignment)
    fun updateAssignmentStatus(assignmentId: String, status: AssignmentStatus)
}
