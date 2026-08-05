package com.example.houseflow.util

import com.example.houseflow.model.Chore

// Pure, deterministic point math for the HF-13 gamification system. Kept free of
// Android/Room dependencies so it can be unit-tested in isolation (see
// PointsPolicyTest), matching AssignmentAlgorithm / ChoreDueTime.
object PointsPolicy {
    const val POINTS_PER_EFFORT = 10
    const val TIME_SENSITIVE_BONUS = 5
    const val POINTS_PER_LEVEL = 100

    // Points earned for completing [chore]: effort-scaled, with a small bonus for
    // time-sensitive chores (getting them done on time matters more).
    fun pointsFor(chore: Chore): Int =
        chore.effortScore * POINTS_PER_EFFORT + if (chore.isTimeSensitive) TIME_SENSITIVE_BONUS else 0

    // Level 1 starts at 0 points; every POINTS_PER_LEVEL points is a new level.
    fun levelFor(allTimePoints: Int): Int = allTimePoints / POINTS_PER_LEVEL + 1

    // Points accumulated within the current level (0 .. POINTS_PER_LEVEL-1).
    fun pointsIntoLevel(allTimePoints: Int): Int = allTimePoints % POINTS_PER_LEVEL

    // Consecutive-week streak: number of weeks in an unbroken run ending at the
    // current week (or the previous week, so an early-in-the-week view with no
    // points yet doesn't reset a streak) in which the user earned >= 1 point.
    // [weeksWithPoints] is the set of weekStart values the user has points in;
    // [currentWeekStart] is this week's Monday; [weekMillis] is 7 days in ms.
    fun streak(weeksWithPoints: Set<Long>, currentWeekStart: Long, weekMillis: Long): Int {
        if (weeksWithPoints.isEmpty()) return 0
        // Anchor at the current week if it has points, otherwise last week so the
        // streak "holds" until this week actually lapses.
        var cursor = if (currentWeekStart in weeksWithPoints) currentWeekStart
        else currentWeekStart - weekMillis
        var count = 0
        while (cursor in weeksWithPoints) {
            count++
            cursor -= weekMillis
        }
        return count
    }
}
