package com.example.houseflow

import android.app.Application
import com.example.houseflow.data.AppContainer
import com.example.houseflow.notification.NotificationChannels
import com.example.houseflow.notification.NotificationScheduler

class HouseflowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Build the Room database and wire the repositories before any screen
        // or ViewModel is created.
        AppContainer.init(this)
        NotificationChannels.createAll(this)
        NotificationScheduler.scheduleChoreReminders(this)
    }
}
