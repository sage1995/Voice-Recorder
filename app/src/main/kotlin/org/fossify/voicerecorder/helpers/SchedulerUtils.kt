package org.fossify.voicerecorder.helpers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import org.fossify.voicerecorder.receivers.DailyRecordReceiver
import org.fossify.voicerecorder.services.RecorderService
import java.util.Calendar

object SchedulerUtils {

    private const val PREFS_NAME = "RecorderSchedulerPrefs"
    private const val KEY_LAST_RECORD_DATE = "last_record_date"

    // 1. The Main function to Schedule the 6:00 AM Alarm
    fun scheduleDailyRecord(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DailyRecordReceiver::class.java)
        
        // FLAG_IMMUTABLE is required for Android 12+
        val pendingIntent = PendingIntent.getBroadcast(
            context, 1001, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Set target time to 6:00 AM
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }

        // If 6:00 AM has already passed TODAY, schedule for TOMORROW
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        // Schedule exact alarm (Requires SCHEDULE_EXACT_ALARM permission)
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } catch (e: SecurityException) {
            // Handle permission not granted (Android 13+)
            e.printStackTrace()
        }
    }

    // 2. Helper to start the service immediately
    fun startRecordingService(context: Context) {
        val serviceIntent = Intent(context, RecorderService::class.java).apply {
            action = "org.fossify.voicerecorder.action.TOGGLE_PAUSE"
        }
        // In Android 8+ (Oreo), we must use startForegroundService
        context.startForegroundService(serviceIntent)
        
        // Save today's date so we don't double-record if phone reboots again
        saveLastRunDate(context)
    }

    // 3. Check if we already recorded today
    fun hasRecordedToday(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastRun = prefs.getLong(KEY_LAST_RECORD_DATE, 0)
        
        val lastDate = Calendar.getInstance().apply { timeInMillis = lastRun }
        val today = Calendar.getInstance()

        return lastDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
               lastDate.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    }

    private fun saveLastRunDate(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_RECORD_DATE, System.currentTimeMillis()).apply()
    }
}
