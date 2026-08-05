package com.example.houseflow

import com.example.houseflow.model.Chore
import com.example.houseflow.model.ChoreFrequency
import com.example.houseflow.util.PointsPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class PointsPolicyTest {

    private val week = 7L * 24 * 3600 * 1000

    private fun chore(effort: Int, timeSensitive: Boolean) = Chore(
        id = "c",
        householdId = "h",
        createdByRoommateId = "a",
        name = "Chore",
        description = "",
        frequency = ChoreFrequency.WEEKLY,
        effortScore = effort,
        dueDayOfWeek = 0,
        dueHour = 10,
        isTimeSensitive = timeSensitive
    )

    @Test
    fun `points scale with effort`() {
        assertEquals(10, PointsPolicy.pointsFor(chore(1, false)))
        assertEquals(50, PointsPolicy.pointsFor(chore(5, false)))
    }

    @Test
    fun `time-sensitive chores earn a bonus`() {
        assertEquals(35, PointsPolicy.pointsFor(chore(3, true)))
        assertEquals(30, PointsPolicy.pointsFor(chore(3, false)))
    }

    @Test
    fun `levels start at one and advance every hundred points`() {
        assertEquals(1, PointsPolicy.levelFor(0))
        assertEquals(1, PointsPolicy.levelFor(99))
        assertEquals(2, PointsPolicy.levelFor(100))
        assertEquals(3, PointsPolicy.levelFor(250))
    }

    @Test
    fun `points into level wraps at the level size`() {
        assertEquals(0, PointsPolicy.pointsIntoLevel(0))
        assertEquals(99, PointsPolicy.pointsIntoLevel(99))
        assertEquals(0, PointsPolicy.pointsIntoLevel(100))
        assertEquals(50, PointsPolicy.pointsIntoLevel(250))
    }

    @Test
    fun `no weeks with points means no streak`() {
        assertEquals(0, PointsPolicy.streak(emptySet(), 10 * week, week))
    }

    @Test
    fun `consecutive weeks including this week count`() {
        val current = 10 * week
        val weeks = setOf(current, current - week, current - 2 * week)
        assertEquals(3, PointsPolicy.streak(weeks, current, week))
    }

    @Test
    fun `streak holds when this week is empty but last week had points`() {
        val current = 10 * week
        val weeks = setOf(current - week, current - 2 * week)
        assertEquals(2, PointsPolicy.streak(weeks, current, week))
    }

    @Test
    fun `a gap breaks the streak`() {
        val current = 10 * week
        // this week + two weeks ago, but not last week -> streak is only this week
        val weeks = setOf(current, current - 2 * week)
        assertEquals(1, PointsPolicy.streak(weeks, current, week))
    }
}
