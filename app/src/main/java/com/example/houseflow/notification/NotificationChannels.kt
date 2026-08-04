package com.example.houseflow.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val CHORE_REMINDERS = "chore_reminders"
    const val HOUSEHOLD_ACTIVITY = "household_activity"

    // Safe to call every app start — creating an already-existing channel is a no-op.
    fun createAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHORE_REMINDERS,
                "Chore Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Due-soon and overdue chore reminders" }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                HOUSEHOLD_ACTIVITY,
                "Household Activity",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "New pickups, bulletin posts, and trade requests" }
        )
    }
}
