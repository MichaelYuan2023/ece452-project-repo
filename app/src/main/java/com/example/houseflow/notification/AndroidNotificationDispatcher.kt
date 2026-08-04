package com.example.houseflow.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.houseflow.MainActivity
import com.example.houseflow.R
import com.example.houseflow.model.AssignmentStatus
import com.example.houseflow.model.BulletinPost
import com.example.houseflow.model.Chore
import com.example.houseflow.model.ChoreAssignment
import com.example.houseflow.model.TradeRequest
import com.example.houseflow.model.TradeStatus

// Tracks, per user, which pickup/bulletin/trade ids have already been
// notified about on this device, so re-loading household data doesn't
// re-notify for the same item. The very first load for a user "baselines"
// (remembers everything currently present without notifying) so signing
// into a household with existing history doesn't fire a notification storm.
class AndroidNotificationDispatcher(private val context: Context) : NotificationDispatcher {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("notification_seen_ids", Context.MODE_PRIVATE)
    private val manager = NotificationManagerCompat.from(context)

    override fun onAssignmentsRefreshed(
        currentUserId: String,
        assignments: List<ChoreAssignment>,
        choresById: Map<String, Chore>
    ) {
        val available = assignments.filter {
            it.status == AssignmentStatus.AVAILABLE && it.assignedToRoommateId == currentUserId
        }
        diffAndNotify(seenKey("pickup", currentUserId), available.map { it.id }.toSet()) { newId ->
            val assignment = available.find { it.id == newId } ?: return@diffAndNotify
            val choreName = choresById[assignment.choreId]?.name ?: "A chore"
            postNotification(
                id = newId,
                channelId = NotificationChannels.HOUSEHOLD_ACTIVITY,
                title = "New chore available",
                text = "$choreName is up for pickup"
            )
        }
    }

    override fun onBulletinPostsRefreshed(
        currentUserId: String,
        currentUserDisplayName: String,
        posts: List<BulletinPost>
    ) {
        val fromOthers = posts.filter { it.authorName != currentUserDisplayName }
        diffAndNotify(seenKey("bulletin", currentUserId), fromOthers.map { it.id }.toSet()) { newId ->
            val post = fromOthers.find { it.id == newId } ?: return@diffAndNotify
            postNotification(
                id = newId,
                channelId = NotificationChannels.HOUSEHOLD_ACTIVITY,
                title = "New bulletin post: ${post.title}",
                text = "${post.authorName}: ${post.message}"
            )
        }
    }

    override fun onTradeRequestsRefreshed(
        currentUserId: String,
        requests: List<TradeRequest>,
        roommateNameById: Map<String, String>
    ) {
        val incoming = requests.filter {
            it.toUserId == currentUserId && it.status == TradeStatus.PENDING
        }
        diffAndNotify(seenKey("trade", currentUserId), incoming.map { it.id }.toSet()) { newId ->
            val request = incoming.find { it.id == newId } ?: return@diffAndNotify
            val fromName = roommateNameById[request.fromUserId] ?: "A roommate"
            postNotification(
                id = newId,
                channelId = NotificationChannels.HOUSEHOLD_ACTIVITY,
                title = "Trade request from $fromName",
                text = request.reason.ifBlank { "Wants you to take over a chore" }
            )
        }
    }

    override fun notifyDueSoon(assignmentId: String, choreName: String) {
        val key = "notified_duesoon_$assignmentId"
        if (prefs.getBoolean(key, false)) return
        prefs.edit().putBoolean(key, true).apply()
        postNotification(
            id = "duesoon_$assignmentId",
            channelId = NotificationChannels.CHORE_REMINDERS,
            title = "Due soon: $choreName",
            text = "This chore is due within the hour"
        )
    }

    override fun notifyOverdue(assignmentId: String, choreName: String) {
        postNotification(
            id = "overdue_$assignmentId",
            channelId = NotificationChannels.CHORE_REMINDERS,
            title = "Overdue: $choreName",
            text = "This chore was missed"
        )
    }

    private fun seenKey(kind: String, userId: String) = "seen_${kind}_$userId"

    // ids currently present entirely replace the stored set on every call: an
    // id that's no longer present (claimed, resolved, deleted) doesn't need
    // to be remembered, and every UUID is unique per creation so there's no
    // risk of a stale claimed/resolved item being mistaken for new later.
    private fun diffAndNotify(key: String, currentIds: Set<String>, onNew: (String) -> Unit) {
        val seen = prefs.getStringSet(key, null)
        if (seen == null) {
            prefs.edit().putStringSet(key, currentIds).apply()
            return
        }
        (currentIds - seen).forEach(onNew)
        prefs.edit().putStringSet(key, currentIds).apply()
    }

    private fun postNotification(id: String, channelId: String, title: String, text: String) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val openAppIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)
            .build()
        manager.notify(id.hashCode(), notification)
    }
}
