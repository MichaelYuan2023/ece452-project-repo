package com.example.houseflow

import com.example.houseflow.model.Chore
import com.example.houseflow.model.ChoreFrequency
import com.example.houseflow.util.ChoreScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChoreSchedulerTest {

    private val day = 86_400_000L
    private val weekStart = 1_000_000_000L      // arbitrary Monday-midnight epoch ms
    private val today = weekStart + 2 * day     // pretend "today" is Wednesday
    private val now = today + 10 * 3_600_000L    // Wednesday, 10:00

    private fun chore(
        frequency: ChoreFrequency,
        dueDayOfWeek: Int = 0,
        intervalDays: Int? = null
    ) = Chore(
        id = "c1",
        householdId = "h1",
        createdByRoommateId = "a",
        name = "Chore",
        description = "",
        frequency = frequency,
        effortScore = 1,
        dueDayOfWeek = dueDayOfWeek,
        dueHour = 10,
        isTimeSensitive = false,
        intervalDays = intervalDays
    )

    private fun anchor(chore: Chore, existing: Set<Long>, currentNow: Long = now) =
        ChoreScheduler.anchorToPost(chore, existing, weekStart, today, currentNow)

    // --- WEEKLY --------------------------------------------------------------

    @Test
    fun `weekly posts this week's occurrence when none exists`() {
        assertEquals(weekStart, anchor(chore(ChoreFrequency.WEEKLY), emptySet()))
    }

    @Test
    fun `weekly does not respawn once this week's occurrence exists`() {
        // Covers the old bug where completing a chore instantly re-posted it.
        assertNull(anchor(chore(ChoreFrequency.WEEKLY), setOf(weekStart)))
    }

    // --- DAILY ---------------------------------------------------------------

    @Test
    fun `daily posts today's occurrence, anchored at today's midnight`() {
        assertEquals(today, anchor(chore(ChoreFrequency.DAILY), emptySet()))
    }

    @Test
    fun `daily does not repost once today's occurrence exists`() {
        assertNull(anchor(chore(ChoreFrequency.DAILY), setOf(today)))
    }

    // --- ONE_TIME ------------------------------------------------------------

    @Test
    fun `one-time posts once at its due day`() {
        val c = chore(ChoreFrequency.ONE_TIME, dueDayOfWeek = 3)
        assertEquals(weekStart + 3 * day, anchor(c, emptySet()))
    }

    @Test
    fun `one-time never reappears once any occurrence has existed`() {
        val c = chore(ChoreFrequency.ONE_TIME, dueDayOfWeek = 3)
        // Even an occurrence anchored elsewhere blocks a one-time from returning.
        assertNull(anchor(c, setOf(weekStart)))
        assertNull(anchor(c, setOf(weekStart + 3 * day)))
    }

    // --- EVERY_N_DAYS --------------------------------------------------------

    @Test
    fun `every-n-days posts the first occurrence when its due day is still ahead`() {
        // Due Saturday (day 5), now is Wednesday -> first occurrence not started yet.
        val c = chore(ChoreFrequency.EVERY_N_DAYS, dueDayOfWeek = 5, intervalDays = 3)
        assertEquals(weekStart + 5 * day, anchor(c, emptySet()))
    }

    @Test
    fun `every-n-days advances to the latest interval boundary that has started`() {
        // First = Monday, interval 3 days; now is 7 days in -> floor(7/3)=2 -> +6 days.
        val c = chore(ChoreFrequency.EVERY_N_DAYS, dueDayOfWeek = 0, intervalDays = 3)
        val later = weekStart + 7 * day
        assertEquals(weekStart + 6 * day, anchor(c, emptySet(), currentNow = later))
    }

    @Test
    fun `every-n-days does not repost the current interval's occurrence`() {
        val c = chore(ChoreFrequency.EVERY_N_DAYS, dueDayOfWeek = 0, intervalDays = 3)
        val later = weekStart + 7 * day
        assertNull(anchor(c, setOf(weekStart + 6 * day), currentNow = later))
    }

    @Test
    fun `every-n-days defaults a missing interval to 2 days`() {
        // First = Monday, no intervalDays -> defaults to 2; now 5 days in -> floor(5/2)=2 -> +4 days.
        val c = chore(ChoreFrequency.EVERY_N_DAYS, dueDayOfWeek = 0, intervalDays = null)
        val later = weekStart + 5 * day
        assertEquals(weekStart + 4 * day, anchor(c, emptySet(), currentNow = later))
    }
}
