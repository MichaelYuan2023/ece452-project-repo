package com.example.houseflow.util

import com.example.houseflow.model.AssignmentStatus
import com.example.houseflow.model.BusyBlock
import com.example.houseflow.model.Chore
import com.example.houseflow.model.ChoreAssignment
import com.example.houseflow.model.Roommate
import java.util.UUID

object AssignmentAlgorithm {

    fun assignAll(
        chores: List<Chore>,
        roommates: List<Roommate>,
        busyBlocksByRoommate: Map<String, List<BusyBlock>>,
        history: List<ChoreAssignment>,
        weekStart: Long
    ): List<ChoreAssignment> {
        // Build up a running list of new assignments so workload penalties apply
        // even within a single run (e.g. if two chores get assigned to the same person).
        val newAssignments = mutableListOf<ChoreAssignment>()

        for (chore in chores) {
            val allHistory = history + newAssignments
            val assignment = assignOne(chore, roommates, busyBlocksByRoommate, allHistory, weekStart)
            newAssignments.add(assignment)
        }
        return newAssignments
    }

    fun assignOne(
        chore: Chore,
        roommates: List<Roommate>,
        busyBlocksByRoommate: Map<String, List<BusyBlock>>,
        history: List<ChoreAssignment>,
        weekStart: Long
    ): ChoreAssignment {
        val scores = roommates.associateWith { r ->
            score(r, chore, busyBlocksByRoommate[r.id] ?: emptyList(), history, weekStart)
        }
        val best = scores.maxByOrNull { it.value }!!
        val winner = best.key
        val winnerBlocks = busyBlocksByRoommate[winner.id] ?: emptyList()
        val isBusy = isBusyAt(winnerBlocks, chore.dueDayOfWeek, chore.dueHour)

        val reason = buildReason(winner, chore, winnerBlocks, history, weekStart, isBusy)

        return ChoreAssignment(
            id = UUID.randomUUID().toString(),
            choreId = chore.id,
            householdId = chore.householdId,
            assignedToRoommateId = winner.id,
            weekStart = weekStart,
            status = AssignmentStatus.PENDING,
            reason = reason,
            hasConflict = isBusy
        )
    }

    // -----------------------------------------------------------------------
    // Scoring — higher is better. Tweak the penalty values here to change
    // how the algorithm prioritizes candidates.
    // -----------------------------------------------------------------------
    private fun score(
        roommate: Roommate,
        chore: Chore,
        blocks: List<BusyBlock>,
        history: List<ChoreAssignment>,
        weekStart: Long
    ): Int {
        var points = 100

        // Availability penalty: busy at the due time
        if (isBusyAt(blocks, chore.dueDayOfWeek, chore.dueHour)) points -= 30

        // Recent assignment penalty: -10 per assignment in the past 2 weeks
        val twoWeeksAgo = weekStart - 14L * 24 * 3600 * 1000
        val recentCount = history.count {
            it.assignedToRoommateId == roommate.id && it.weekStart >= twoWeeksAgo
        }
        points -= recentCount * 10

        // Weekly workload penalty: -5 per chore already assigned this week
        val thisWeekCount = history.count {
            it.assignedToRoommateId == roommate.id && it.weekStart == weekStart
        }
        points -= thisWeekCount * 5

        // Repeated chore penalty: -15 if they had this exact chore last week
        val lastWeek = weekStart - 7L * 24 * 3600 * 1000
        val hadItLastWeek = history.any {
            it.assignedToRoommateId == roommate.id &&
            it.choreId == chore.id &&
            it.weekStart == lastWeek
        }
        if (hadItLastWeek) points -= 15

        return points
    }

    // True if the given hour falls inside any of the roommate's busy blocks on that day.
    private fun isBusyAt(blocks: List<BusyBlock>, dayOfWeek: Int, hour: Int): Boolean =
        blocks.any { it.dayOfWeek == dayOfWeek && hour >= it.startHour && hour < it.endHour }

    private val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    private fun buildReason(
        winner: Roommate,
        chore: Chore,
        blocks: List<BusyBlock>,
        history: List<ChoreAssignment>,
        weekStart: Long,
        isBusy: Boolean
    ): String {
        val day = DAYS.getOrElse(chore.dueDayOfWeek) { "?" }
        val time = "%02d:00".format(chore.dueHour)
        val thisWeekCount = history.count {
            it.assignedToRoommateId == winner.id && it.weekStart == weekStart
        }
        return when {
            isBusy -> "Assigned to ${winner.name} — conflict: busy $day $time, but fewest tasks this week"
            thisWeekCount == 0 -> "Assigned to ${winner.name} — free $day $time, no other chores this week"
            else -> "Assigned to ${winner.name} — free $day $time, lightest workload ($thisWeekCount chore(s) this week)"
        }
    }
}
