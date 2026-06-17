package com.example.houseflow.data.repository

import com.example.houseflow.model.AssignmentStatus
import com.example.houseflow.model.Chore
import com.example.houseflow.model.ChoreAssignment

// Migration seam: replace these mutable lists with Room DAOs.
class InMemoryChoreRepository : ChoreRepository {

    private val chores = mutableListOf<Chore>()
    private val assignments = mutableListOf<ChoreAssignment>()

    override fun getChores(householdId: String): List<Chore> =
        chores.filter { it.householdId == householdId }

    override fun addChore(chore: Chore) {
        chores.add(chore)
    }

    override fun deleteChore(choreId: String) {
        chores.removeAll { it.id == choreId }
        assignments.removeAll { it.choreId == choreId }
    }

    override fun getAssignments(householdId: String): List<ChoreAssignment> =
        assignments.filter { it.householdId == householdId }

    override fun addAssignment(assignment: ChoreAssignment) {
        assignments.add(assignment)
    }

    override fun updateAssignmentStatus(assignmentId: String, status: AssignmentStatus) {
        val idx = assignments.indexOfFirst { it.id == assignmentId }
        if (idx != -1) assignments[idx] = assignments[idx].copy(status = status)
    }
}
