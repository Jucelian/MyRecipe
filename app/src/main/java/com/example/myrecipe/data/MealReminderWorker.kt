package com.example.myrecipe.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.work.*
import com.example.myrecipe.MainActivity
import com.example.myrecipe.R
import com.example.myrecipe.network.RetrofitClient
import java.util.*
import java.util.concurrent.TimeUnit

class MealReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val masterKey = MasterKey.Builder(applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val sharedPreferences = EncryptedSharedPreferences.create(
            applicationContext,
            "secure_auth_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        val owner = sharedPreferences.getString("saved_username", "") ?: return Result.success()
        if (owner.isBlank()) return Result.success()

        val database = AppDatabase.getDatabase(applicationContext)
        val mealPlanDao = database.mealPlanDao()

        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val todaysMeals = mealPlanDao.getMealPlansByDateSync(owner, today)

        if (todaysMeals.isNotEmpty()) {
            val message = todaysMeals.joinToString(", ") { plan -> "${plan.mealType}: ${plan.recipeTitle}" }
            sendNotification(message)
        }

        return Result.success()
    }

    @Suppress("MissingPermission")
    private fun sendNotification(message: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "meal_reminders"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Meal Reminders", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.chefmate_logo)
            .setContentTitle("Today's Meal Plan")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    applicationContext,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        notificationManager.notify(1, notification)
    }

    companion object {
        private const val REMINDER_WORK_NAME = "meal_reminder_work"

        fun scheduleDailyReminder(context: Context) {
            val calendar = Calendar.getInstance()
            val now = calendar.timeInMillis
            
            // Set target time to 8:00 AM
            calendar.set(Calendar.HOUR_OF_DAY, 8)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            
            if (calendar.timeInMillis <= now) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            
            val initialDelay = calendar.timeInMillis - now
            
            val reminderRequest = PeriodicWorkRequestBuilder<MealReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
                .addTag(REMINDER_WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                REMINDER_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                reminderRequest
            )
        }
    }
}
