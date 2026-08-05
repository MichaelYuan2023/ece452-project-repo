package com.example.houseflow.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// An immutable ledger row: a completed assignment earned [points] for [userId]
// in the week identified by [weekStart]. The ledger is the single source of
// truth for the HF-13 gamification totals — weekly points, all-time points,
// levels, and streaks are all derived from it, so there is no separate mutable
// counter to drift.
//
// The primary key is intentionally the *assignment id*: awarding is an
// INSERT-OR-IGNORE keyed on the assignment, so completing (or replaying a
// completion of) the same assignment can never double-award.
@Entity(tableName = "points_entries")
data class PointsEntry(
    @PrimaryKey val id: String,   // == the source ChoreAssignment.id
    val householdId: String,
    val userId: String,           // who earned the points
    val choreName: String,        // denormalized for the recent-activity feed
    val points: Int,
    val weekStart: Long,          // epoch ms of the week the points count toward
    val awardedAt: Long           // wall-clock time awarded, for activity ordering
)
