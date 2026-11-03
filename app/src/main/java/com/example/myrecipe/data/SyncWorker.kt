package com.example.myrecipe.data

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.myrecipe.network.RetrofitClient
import java.util.concurrent.TimeUnit

class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val owner = inputData.getString(KEY_OWNER) ?: return Result.failure()
        
        Log.d("SyncWorker", "Starting background sync for $owner")
        
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = RecipeRepository(
            database.recipeDao(),
            database.categoryDao(),
            database.shoppingDao(),
            database.mealPlanDao(),
            RetrofitClient.instance
        )

        return try {
            repository.refreshData(owner)
            Log.d("SyncWorker", "Background sync completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Background sync failed: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        const val KEY_OWNER = "owner"
        private const val SYNC_WORK_NAME = "recipe_sync_work"

        fun startPeriodicSync(context: Context, owner: String) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_OWNER to owner))
                .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                SYNC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                syncRequest
            )
        }

        fun stopSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK_NAME)
        }
    }
}
