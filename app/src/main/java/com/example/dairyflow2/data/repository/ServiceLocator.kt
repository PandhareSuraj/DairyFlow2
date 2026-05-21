package com.example.dairyflow2.data.repository

import android.content.Context
import com.example.dairyflow2.data.local.DairyDatabase
import com.example.dairyflow2.data.remote.SupabaseRestClient
import com.example.dairyflow2.notification.AppNotifier

object ServiceLocator {
    @Volatile private var repository: DairyRepository? = null

    fun repository(context: Context): DairyRepository =
        repository ?: synchronized(this) {
            repository ?: DairyRepository(
                context = context.applicationContext,
                dao = DairyDatabase.get(context).dao(),
                remote = SupabaseRestClient(),
                sessionStore = SessionStore(context.applicationContext),
                notifier = AppNotifier(context.applicationContext),
            ).also { repository = it }
        }
}

