package com.example.houseflow

import android.app.Application
import com.example.houseflow.data.AppContainer

class HouseflowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Build the Room database and wire the repositories before any screen
        // or ViewModel is created.
        AppContainer.init(this)
    }
}
