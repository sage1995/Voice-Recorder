package org.fossify.voicerecorder.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.voicerecorder.helpers.SchedulerUtils

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action == Intent.ACTION_BOOT_COMPLETED || 
            action == "android.intent.action.QUICKBOOT_POWERON") {

            // 1. Ensure the daily 6:00 AM alarm is still scheduled
            SchedulerUtils.scheduleDailyRecord(context)

            // 2. FORCE start the recording immediately upon every reboot.
            // Even if it is 4 AM, 9 AM, or 10 PM.
            // The Service will handle preventing duplicate recordings if one is already running.
            SchedulerUtils.startRecordingService(context)
        }
    }
}

