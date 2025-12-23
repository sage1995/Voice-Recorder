package org.fossify.voicerecorder.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.voicerecorder.helpers.SchedulerUtils

class DailyRecordReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 1. Start the recording
        SchedulerUtils.startRecordingService(context)
        
        // 2. Schedule the next one for tomorrow
        SchedulerUtils.scheduleDailyRecord(context)
    }
}
