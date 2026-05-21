package com.example.dairyflow2

import android.app.Application
import com.example.dairyflow2.notification.AppNotifier

class DairyFlowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppNotifier(this).ensureChannels()
    }
}

