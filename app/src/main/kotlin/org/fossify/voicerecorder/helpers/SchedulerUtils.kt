package org.fossify.voicerecorder.helpers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.fossify.voicerecorder.receivers.DailyRecordReceiver
import org.fossify.voicerecorder.services.RecorderService
import java.util.Calendar

object SchedulerUtils {

    private const val PREFS_NAME = "RecorderSchedulerPrefs"
    private const val KEY_LAST_RECORD_DATE = "last_record_date"
    
    // Define a specific action for starting
    const val ACTION_START_RECORDING = "org.fossify.voicerecorder.action.START_RECORDING"

    fun scheduleDailyRecord(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DailyRecordReceiver::class.java)
        
        val pendingIntent = PendingIntent.getBroadcast(
            context, 1001, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }

        // Logic Check:
        // If it is currently 8:00 AM, the calendar time (6:00 AM) is in the past.
        // Usually, we add 1 day. 
        // BUT, if we add 1 day, we rely on the BootReceiver to handle the "Catch up" for today.
        if (calendar.timeInMillis <= now) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        try {
            // Using setAlarmClock is much more reliable for exact timing than setExactAndAllowWhileIdle
            // It ensures the phone wakes up even from deep sleep/Doze.
            val alarmClockInfo = AlarmManager.AlarmClockInfo(calendar.timeInMillis, pendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun startRecordingService(context: Context) {
        // FIX: Use a specific START action, NOT TOGGLE_PAUSE
        val serviceIntent = Intent(context, RecorderService::class.java).apply {
            action = ACTION_START_RECORDING
        }
        
        try {
            context.startForegroundService(serviceIntent)
            saveLastRunDate(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hasRecordedToday(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastRun = prefs.getLong(KEY_LAST_RECORD_DATE, 0)
        
        if (lastRun == 0L) return false

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
