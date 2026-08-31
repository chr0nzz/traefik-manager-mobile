package dev.chr0nzz.traefikmanager.push

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.chr0nzz.traefikmanager.data.store.PreferencesStore

class PushSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun pushChannels(): PushChannels

        fun preferencesStore(): PreferencesStore
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        val endpoint = inputData.getString(KEY_ENDPOINT).orEmpty()
        val failure = inputData.getString(KEY_FAILURE)

        if (failure != null) {
            deps.preferencesStore().setPushError(failure)
            deps.preferencesStore().setPushEnabled(false)
            return Result.success()
        }

        return if (endpoint.isBlank()) {
            runCatching { deps.pushChannels().remove() }
            deps.preferencesStore().setPushEnabled(false)
            deps.preferencesStore().setPushEndpoint("")
            Result.success()
        } else {
            runCatching { deps.pushChannels().sync(endpoint) }.fold(
                onSuccess = { channel ->
                    deps.preferencesStore().setPushError(
                        if (channel == null) "This server is older than 1.12.0, so it has no channels to push through." else "",
                    )
                    Result.success()
                },
                onFailure = { if (runAttemptCount < 5) Result.retry() else Result.failure() },
            )
        }
    }

    companion object {
        private const val KEY_ENDPOINT = "endpoint"
        private const val KEY_FAILURE = "failure"
        private const val WORK = "push-sync"

        fun sync(context: Context, endpoint: String) =
            enqueue(context, Data.Builder().putString(KEY_ENDPOINT, endpoint).build())

        fun remove(context: Context) = enqueue(context, Data.EMPTY)

        fun failed(context: Context, reason: String) =
            enqueue(context, Data.Builder().putString(KEY_FAILURE, reason).build())

        private fun enqueue(context: Context, data: Data) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<PushSyncWorker>().setInputData(data).build(),
            )
        }
    }
}
