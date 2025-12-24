package org.fossify.voicerecorder.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.voicerecorder.helpers.SchedulerUtils
import java.util.Calendar

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        val action = intent.action ?: return

        if (
            action == "android.intent.action.BOOT_COMPLETED" ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {

            // 1. Always reschedule the alarm after reboot
            SchedulerUtils.scheduleDailyRecord(context)

            // 2. Catch-up if we missed today's 6:00 AM
            val now = Calendar.getInstance()

            val targetTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 6)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }

            if (now.after(targetTime) && !SchedulerUtils.hasRecordedToday(context)) {
                SchedulerUtils.startRecordingService(context)
            }
        }
    }
}
