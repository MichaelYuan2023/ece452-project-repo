package com.example.houseflow.util

import com.example.houseflow.model.Chore
import com.example.houseflow.model.ChoreFrequency

// Decides when a chore's next occurrence should appear on the pickup board.
// Pure and deterministic (no Android/Room deps) so it can be unit-tested in
// isolation, matching AssignmentAlgorithm / ChoreDueTime / PointsPolicy.
//
// The rule: a chore has at most one occurrence per period, and it only comes
// back when its next period arrives — so completing a chore never respawns it
// on the same day / week / interval.
object ChoreScheduler {
    private const val DAY_MS = 24L * 3600 * 1000

    // The weekStart anchor of the occurrence that should be posted for [chore]
    // right now, or null if none is needed — either an occurrence already exists
    // for the current period, or it's a one-time chore that has already been
    // posted at some point.
    //
    // [existingAnchors] are the weekStart values of the chore's existing
    // occurrences (any status). [weekStart] is this week's Monday-midnight,
    // [todayMidnight] is today's local midnight, [now] is the current epoch ms.
    // Every anchor returned is >= weekStart, so it survives the stale-AVAILABLE
    // sweep that deletes unclaimed occurrences from past weeks.
    fun anchorToPost(
        chore: Chore,
        existingAnchors: Set<Long>,
        weekStart: Long,
        todayMidnight: Long,
        now: Long
    ): Long? {
        val target = when (chore.frequency) {
            // Posted once, ever. Any existing occurrence (in any period) means
            // it's been handled — never resurrect it.
            ChoreFrequency.ONE_TIME ->
                if (existingAnchors.isNotEmpty()) return null
                else weekStart + chore.dueDayOfWeek * DAY_MS

            ChoreFrequency.WEEKLY -> weekStart

            ChoreFrequency.DAILY -> todayMidnight

            // Anchored at the chore's first due day, then every N days after. The
            // current occurrence is the latest interval boundary that has started.
            ChoreFrequency.EVERY_N_DAYS -> {
                val interval = (chore.intervalDays ?: 2).coerceAtLeast(2) * DAY_MS
                val first = weekStart + chore.dueDayOfWeek * DAY_MS
                if (now < first) first else first + ((now - first) / interval) * interval
            }
        }
        return if (target in existingAnchors) null else target
    }
}
