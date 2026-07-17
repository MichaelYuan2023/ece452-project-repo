package com.example.houseflow

import com.example.houseflow.model.AssignmentStatus
import com.example.houseflow.model.Chore
import com.example.houseflow.model.ChoreAssignment
import com.example.houseflow.model.ChoreFrequency
import com.example.houseflow.model.Roommate
import com.example.houseflow.util.AssignmentAlgorithm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssignmentAlgorithmTest {

    private val weekStart = 1_000_000L

    private fun roommate(id: String) = Roommate(id, "h1", id)

    private fun chore(id: String, effort: Int) = Chore(
        id = id,
        householdId = "h1",
        createdByRoommateId = "a",
        name = id,
        description = "",
        frequency = ChoreFrequency.WEEKLY,
        effortScore = effort,
        dueDayOfWeek = 0,
        dueHour = 10,
        isTimeSensitive = false
    )

    private fun assignment(choreId: String, userId: String) = ChoreAssignment(
        id = "$choreId-$userId",
        choreId = choreId,
        householdId = "h1",
        assignedToRoommateId = userId,
        weekStart = weekStart,
        status = AssignmentStatus.PENDING,
        reason = "",
        hasConflict = false
    )

    @Test
    fun `posts chore as available with recommendation reason`() {
        val result = AssignmentAlgorithm.assignOne(
            chore("dishes", 2),
            listOf(roommate("a"), roommate("b")),
            emptyMap(),
            emptyList(),
            weekStart,
            emptyMap()
        )
        assertEquals(AssignmentStatus.AVAILABLE, result.status)
        assertTrue(result.reason.startsWith("Recommended for"))
    }

    @Test
    fun `effort-weighted workload recommends the less loaded roommate`() {
        // a holds one effort-5 chore, b one effort-1 chore; a plain count-based
        // penalty would tie and pick a (first in list)
        val history = listOf(assignment("heavy", "a"), assignment("light", "b"))
        val efforts = mapOf("heavy" to 5, "light" to 1)

        val result = AssignmentAlgorithm.assignOne(
            chore("new", 2),
            listOf(roommate("a"), roommate("b")),
            emptyMap(),
            history,
            weekStart,
            efforts
        )
        assertEquals("b", result.assignedToRoommateId)
    }
}
