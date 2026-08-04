package com.example.houseflow.notification

import com.example.houseflow.model.BulletinPost
import com.example.houseflow.model.Chore
import com.example.houseflow.model.ChoreAssignment
import com.example.houseflow.model.TradeRequest

// Event-driven half of the notifications feature: fires local notifications
// when AppViewModel loads/refreshes household data and finds items the
// current user hasn't seen yet on this device (new pickup, new bulletin
// post, incoming trade request). The due-soon/overdue half runs separately
// on a schedule via ChoreReminderWorker, independent of any ViewModel.
//
// Migration seam: swap for a fake/no-op in tests, or a version that also
// fans out to a backend, without touching AppViewModel.
interface NotificationDispatcher {
    fun onAssignmentsRefreshed(
        currentUserId: String,
        assignments: List<ChoreAssignment>,
        choresById: Map<String, Chore>
    )

    fun onBulletinPostsRefreshed(
        currentUserId: String,
        currentUserDisplayName: String,
        posts: List<BulletinPost>
    )

    fun onTradeRequestsRefreshed(
        currentUserId: String,
        requests: List<TradeRequest>,
        roommateNameById: Map<String, String>
    )

    // Called by ChoreReminderWorker, on its own schedule, independent of any
    // ViewModel. Due-soon is deduped internally (fires once per assignment
    // per due window); overdue has no dedup because the caller only reaches
    // it once per assignment, at the PENDING -> MISSED transition.
    fun notifyDueSoon(assignmentId: String, choreName: String)
    fun notifyOverdue(assignmentId: String, choreName: String)
}
