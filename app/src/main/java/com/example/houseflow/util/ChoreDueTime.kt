package com.example.houseflow.util

import com.example.houseflow.model.Chore
import com.example.houseflow.model.ChoreAssignment
import com.example.houseflow.model.ChoreFrequency

// Shared by AppViewModel.refreshOverdue() and ChoreReminderWorker so the two
// due-time computations can't drift apart.
object ChoreDueTime {
    private const val HOUR_MS = 3_600_000L
    private const val DAY_MS = 24 * HOUR_MS

    fun computeDueAt(assignment: ChoreAssignment, chore: Chore): Long =
        if (chore.frequency == ChoreFrequency.WEEKLY) {
            assignment.weekStart + chore.dueDayOfWeek * DAY_MS + chore.dueHour * HOUR_MS
        } else {
            assignment.weekStart + chore.dueHour * HOUR_MS
        }
}
