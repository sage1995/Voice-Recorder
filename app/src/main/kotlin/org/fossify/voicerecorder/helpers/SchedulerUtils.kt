package org.fossify.voicerecorder.helpers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.fossify.voicerecorder.receivers.DailyRecordReceiver
import org.fossify.voicerecorder.services.RecorderService
import java.util.Calendar

object SchedulerUtils {

    // 1. Schedule the 6:00 AM Alarm
    fun scheduleDailyRecord(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DailyRecordReceiver::class.java)
        
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

        // If it is already past 6:00 AM, schedule for TOMORROW
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        // Use AlarmManager.RTC_WAKEUP to wake the phone up if it's dozing
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                     alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    // Fallback if permission is missing (essential for Android 13/14)
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    // 2. Helper to start the service immediately
    fun startRecordingService(context: Context) {
        val serviceIntent = Intent(context, RecorderService::class.java)
        // We do NOT use TOGGLE_PAUSE here. We want the default start action.
        // serviceIntent.action = "org.fossify.voicerecorder.action.TOGGLE_PAUSE" 
        
        // Android 8+ requires startForegroundService
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
