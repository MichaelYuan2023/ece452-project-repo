package com.example.houseflow.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.houseflow.data.AppContainer
import com.example.houseflow.model.AssignmentStatus
import com.example.houseflow.util.ChoreDueTime

// Runs periodically in the background (see NotificationScheduler), independent
// of any ViewModel or open screen. Covers the two triggers whose data
// (due date) exists regardless of what else happens in the app: due-soon and
// overdue chore reminders for whoever is currently signed in on this device.
class ChoreReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val dueSoonWindowMs = 60 * 60 * 1000L // 1 hour

    override suspend fun doWork(): Result {
        val uid = AppContainer.authRepository.currentUser?.uid ?: return Result.success()
        val user = AppContainer.userRepository.getUser(uid) ?: return Result.success()
        val householdId = user.activeHouseholdId ?: return Result.success()

        val chores = AppContainer.choreRepository.getChores(householdId)
        val choresById = chores.associateBy { it.id }
        val assignments = AppContainer.choreRepository.getAssignments(householdId)
        val myPending = assignments.filter {
            it.status == AssignmentStatus.PENDING && it.assignedToRoommateId == uid
        }

        val now = System.currentTimeMillis()
        val dispatcher = AppContainer.notificationDispatcher
        for (assignment in myPending) {
            val chore = choresById[assignment.choreId] ?: continue
            val dueAt = ChoreDueTime.computeDueAt(assignment, chore)
            when {
                now > dueAt -> {
                    AppContainer.choreRepository.updateAssignmentStatus(assignment.id, AssignmentStatus.MISSED)
                    dispatcher.notifyOverdue(assignment.id, chore.name)
                }
                dueAt - now <= dueSoonWindowMs -> {
                    dispatcher.notifyDueSoon(assignment.id, chore.name)
                }
            }
        }
        return Result.success()
    }
}
