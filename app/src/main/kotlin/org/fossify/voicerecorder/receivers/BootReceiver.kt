package org.fossify.voicerecorder.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.voicerecorder.helpers.SchedulerUtils
import java.util.Calendar

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            
            // 1. Always ensure the Alarm is set for the future (persistence)
            SchedulerUtils.scheduleDailyRecord(context)

            // 2. CHECK FOR MISSED RECORDING
            val now = Calendar.getInstance()
            val targetTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 6)
                set(Calendar.MINUTE, 0)
            }

            // If "Now" is past 6:00 AM... AND we haven't recorded today yet...
            if (now.after(targetTime) && !SchedulerUtils.hasRecordedToday(context)) {
                // ...Start recording immediately! (Catch-up)
                SchedulerUtils.startRecordingService(context)
            }
        }
    }
}

