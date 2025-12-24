package org.fossify.voicerecorder.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.voicerecorder.helpers.SchedulerUtils
import java.util.Calendar

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action == Intent.ACTION_BOOT_COMPLETED || 
            action == "android.intent.action.QUICKBOOT_POWERON") {

            // 1. Ensure the alarm is set for tomorrow (or next 6 AM)
            SchedulerUtils.scheduleDailyRecord(context)

            // 2. Check if we missed today's recording (e.g., booted at 8:00 AM)
            val now = Calendar.getInstance()
            val targetTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 6)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }

            // If it is currently AFTER 6:00 AM AND we haven't recorded today yet
            if (now.after(targetTime) && !SchedulerUtils.hasRecordedToday(context)) {
                SchedulerUtils.startRecordingService(context)
            }
        }
    }
}
