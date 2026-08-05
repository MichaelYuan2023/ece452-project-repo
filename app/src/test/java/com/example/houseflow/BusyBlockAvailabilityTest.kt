package com.example.houseflow

import com.example.houseflow.model.BlockType
import com.example.houseflow.model.BusyBlock
import com.example.houseflow.model.Recurrence
import com.example.houseflow.util.AssignmentAlgorithm
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class BusyBlockAvailabilityTest {

    private fun weekly(day: Int, start: Int, end: Int) = BusyBlock(
        id = "w", roommateId = "u", dayOfWeek = day, startHour = start, endHour = end,
        title = "Class", type = BlockType.CLASS, recurrence = Recurrence.WEEKLY, date = null
    )

    private fun oneTime(dateMillis: Long, day: Int, start: Int, end: Int) = BusyBlock(
        id = "o", roommateId = "u", dayOfWeek = day, startHour = start, endHour = end,
        title = "Dentist", type = BlockType.OTHER, recurrence = Recurrence.ONE_TIME, date = dateMillis
    )

    private fun midnight(year: Int, month0: Int, day: Int): Long {
        val c = Calendar.getInstance()
        c.set(year, month0, day, 0, 0, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    @Test
    fun `weekly block matches its weekday regardless of date`() {
        val blocks = listOf(weekly(day = 2, start = 9, end = 12)) // Wednesday
        // busy Wednesday 10:00
        assertTrue(AssignmentAlgorithm.isBusyAt(blocks, dayOfWeek = 2, hour = 10, date = null))
        // not busy Wednesday 13:00 (end exclusive at 12)
        assertFalse(AssignmentAlgorithm.isBusyAt(blocks, dayOfWeek = 2, hour = 12, date = null))
        // not busy Thursday
        assertFalse(AssignmentAlgorithm.isBusyAt(blocks, dayOfWeek = 3, hour = 10, date = null))
    }

    @Test
    fun `one-time event only matches on its exact date`() {
        val d = midnight(2026, Calendar.AUGUST, 12)
        val nextWeekSameWeekday = d + 7L * 24 * 3600 * 1000
        val dow = ((Calendar.getInstance().apply { timeInMillis = d }.get(Calendar.DAY_OF_WEEK)) + 5) % 7
        val blocks = listOf(oneTime(d, day = dow, start = 14, end = 15))

        // Busy on the event's date at 14:00.
        assertTrue(AssignmentAlgorithm.isBusyAt(blocks, dayOfWeek = dow, hour = 14, date = d))
        // NOT busy on the same weekday a week later.
        assertFalse(AssignmentAlgorithm.isBusyAt(blocks, dayOfWeek = dow, hour = 14, date = nextWeekSameWeekday))
        // NOT busy if no date context is provided.
        assertFalse(AssignmentAlgorithm.isBusyAt(blocks, dayOfWeek = dow, hour = 14, date = null))
    }

    @Test
    fun `hour range is start-inclusive end-exclusive`() {
        val blocks = listOf(weekly(day = 0, start = 9, end = 17))
        assertTrue(AssignmentAlgorithm.isBusyAt(blocks, 0, 9))
        assertTrue(AssignmentAlgorithm.isBusyAt(blocks, 0, 16))
        assertFalse(AssignmentAlgorithm.isBusyAt(blocks, 0, 17))
        assertFalse(AssignmentAlgorithm.isBusyAt(blocks, 0, 8))
    }
}
