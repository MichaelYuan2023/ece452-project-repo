package com.example.houseflow

import com.example.houseflow.model.AssignmentStatus
import com.example.houseflow.model.Chore
import com.example.houseflow.model.ChoreAssignment
import com.example.houseflow.model.ChoreFrequency
import com.example.houseflow.util.ChoreDueTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ChoreDueTimeTest {

    private val weekStart = 1_000_000_000L // arbitrary Monday-midnight epoch ms

    private fun chore(frequency: ChoreFrequency, dueDayOfWeek: Int, dueHour: Int) = Chore(
        id = "c1",
        householdId = "h1",
        createdByRoommateId = "a",
        name = "Chore",
        description = "",
        frequency = frequency,
        effortScore = 1,
        dueDayOfWeek = dueDayOfWeek,
        dueHour = dueHour,
        isTimeSensitive = false
    )

    private fun assignment() = ChoreAssignment(
        id = "a1",
        choreId = "c1",
        householdId = "h1",
        assignedToRoommateId = "u1",
        weekStart = weekStart,
        status = AssignmentStatus.PENDING,
        reason = "",
        hasConflict = false
    )

    @Test
    fun `weekly chore due time accounts for day of week and hour`() {
        val chore = chore(ChoreFrequency.WEEKLY, dueDayOfWeek = 2, dueHour = 18)
        val expected = weekStart + 2 * 86_400_000L + 18 * 3_600_000L
        assertEquals(expected, ChoreDueTime.computeDueAt(assignment(), chore))
    }

    @Test
    fun `daily chore due time ignores day of week`() {
        val chore = chore(ChoreFrequency.DAILY, dueDayOfWeek = 5, dueHour = 9)
        val expected = weekStart + 9 * 3_600_000L
        assertEquals(expected, ChoreDueTime.computeDueAt(assignment(), chore))
    }
}
