package com.example.houseflow.util

import java.util.Calendar

// The Monday-at-midnight anchor that assignments are keyed to. Shared by
// AppViewModel and the demo seeder so a seeded row lands in exactly the week
// the app considers current — if the two drifted, seeded chores would look like
// stale history and get swept up by deleteStaleAvailable().
object WeekStart {

    fun current(): Long {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // Today as the app's day index: 0=Monday … 6=Sunday.
    fun todayIndex(): Int = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7
}
