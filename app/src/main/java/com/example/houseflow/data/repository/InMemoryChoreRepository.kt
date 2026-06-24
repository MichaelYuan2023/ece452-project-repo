package com.example.houseflow.data.repository

import com.example.houseflow.model.AssignmentStatus
import com.example.houseflow.model.Chore
import com.example.houseflow.model.ChoreAssignment
import com.example.houseflow.model.ChoreFrequency

// Migration seam: replace these mutable lists with Room DAOs.
class InMemoryChoreRepository : ChoreRepository {

    // Pre-seeded chores so the demo feels lived-in from the start.
    private val chores = mutableListOf(
        Chore(
            id = "chore-garbage", householdId = "household-1",
            createdByRoommateId = "r-maya", name = "Take out garbage",
            description = "Bring bins to the curb", frequency = ChoreFrequency.WEEKLY,
            effortScore = 1, dueDayOfWeek = 1, dueHour = 20, isTimeSensitive = true
        ),
        Chore(
            id = "chore-bathroom", householdId = "household-1",
            createdByRoommateId = "r-jake", name = "Clean bathroom",
            description = "Scrub toilet, sink, and shower", frequency = ChoreFrequency.WEEKLY,
            effortScore = 4, dueDayOfWeek = 6, dueHour = 12, isTimeSensitive = false
        ),
        Chore(
            id = "chore-vacuum", householdId = "household-1",
            createdByRoommateId = "r-priya", name = "Vacuum living room",
            description = "Vacuum floors and under couch", frequency = ChoreFrequency.WEEKLY,
            effortScore = 3, dueDayOfWeek = 5, dueHour = 14, isTimeSensitive = false
        ),
        Chore(
            id = "chore-kitchen", householdId = "household-1",
            createdByRoommateId = "r-maya", name = "Kitchen cleanup",
            description = "Wipe counters, do dishes, take out compost", frequency = ChoreFrequency.WEEKLY,
            effortScore = 3, dueDayOfWeek = 3, dueHour = 21, isTimeSensitive = false
        ),
        Chore(
            id = "chore-groceries", householdId = "household-1",
            createdByRoommateId = "r-jake", name = "Buy shared supplies",
            description = "Toilet paper, dish soap, garbage bags", frequency = ChoreFrequency.WEEKLY,
            effortScore = 2, dueDayOfWeek = 4, dueHour = 18, isTimeSensitive = false
        )
    )
    private val assignments = mutableListOf<ChoreAssignment>()

    override fun getChores(householdId: String): List<Chore> =
        chores.filter { it.householdId == householdId }

    override fun addChore(chore: Chore) {
        chores.add(chore)
    }

    override fun updateChore(chore: Chore) {
        val idx = chores.indexOfFirst { it.id == chore.id }
        if (idx != -1) chores[idx] = chore
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

    override fun updateAssignment(assignment: ChoreAssignment) {
        val idx = assignments.indexOfFirst { it.id == assignment.id }
        if (idx != -1) assignments[idx] = assignment
    }

    override fun updateAssignmentStatus(assignmentId: String, status: AssignmentStatus) {
        val idx = assignments.indexOfFirst { it.id == assignmentId }
        if (idx != -1) assignments[idx] = assignments[idx].copy(status = status)
    }
}
