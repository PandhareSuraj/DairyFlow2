package com.example.dairyflow2.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.dairyflow2.core.hasNetworkConnection
import com.example.dairyflow2.data.repository.ServiceLocator

class OfflineSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (!applicationContext.hasNetworkConnection()) return Result.retry()
        return runCatching {
            ServiceLocator.repository(applicationContext).syncPendingMutations()
        }.fold(
            onSuccess = { synced -> if (synced) Result.success() else Result.retry() },
            onFailure = { Result.retry() },
        )
    }
}

