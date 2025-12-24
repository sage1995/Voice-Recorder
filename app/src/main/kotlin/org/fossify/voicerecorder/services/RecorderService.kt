package org.fossify.voicerecorder.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.UserManager
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.voicerecorder.BuildConfig
import org.fossify.voicerecorder.R
import org.fossify.voicerecorder.activities.SplashActivity
import org.fossify.voicerecorder.extensions.config
import org.fossify.voicerecorder.extensions.updateWidgets
import org.fossify.voicerecorder.helpers.*
import org.fossify.voicerecorder.models.Events
import org.fossify.voicerecorder.recorder.MediaRecorderWrapper
import org.fossify.voicerecorder.recorder.Mp3Recorder
import org.fossify.voicerecorder.recorder.Recorder
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.util.Timer
import java.util.TimerTask

class RecorderService : Service() {
    companion object {
        var isRunning = false
        private const val AMPLITUDE_UPDATE_MS = 75L
    }

    private var recordingPath = ""
    private var resultUri: Uri? = null
    private var duration = 0
    private var status = RECORDING_STOPPED
    private var durationTimer = Timer()
    private var amplitudeTimer = Timer()
    private var recorder: Recorder? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. Immediately start foreground to prevent ANR or System Kill
        startForegroundServiceCompat()

        when (intent?.action) {
            GET_RECORDER_INFO -> broadcastRecorderInfo()
            STOP_AMPLITUDE_UPDATE -> amplitudeTimer.cancel()
            TOGGLE_PAUSE -> togglePause()
            CANCEL_RECORDING -> cancelRecording()
            else -> startRecording()
        }

        return START_STICKY
    }

    private fun startForegroundServiceCompat() {
        val notification = showNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(RECORDER_RUNNING_NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(RECORDER_RUNNING_NOTIF_ID, notification)
        }
    }

    private fun startRecording() {
        if (status == RECORDING_RUNNING) return
        isRunning = true
        updateWidgets(true)

        // 2. Check if phone is unlocked. If NOT unlocked (Boot), we MUST use safe internal storage.
        val userManager = getSystemService(Context.USER_SERVICE) as UserManager
        val isUserUnlocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) userManager.isUserUnlocked else true
        
        // If locked, force safe fallback. If unlocked, try standard path first.
        val forceSafeStorage = !isUserUnlocked

        try {
            setupRecorderEngine(useSafeFallback = forceSafeStorage)
            beginRecordingSequence()
        } catch (e: Exception) {
            // If the first attempt failed, try the fallback (if we didn't already)
            if (!forceSafeStorage) {
                try {
                    setupRecorderEngine(useSafeFallback = true)
                    beginRecordingSequence()
                } catch (fatal: Exception) {
                    cleanupAndStop()
                }
            } else {
                cleanupAndStop()
            }
        }
    }

    private fun setupRecorderEngine(useSafeFallback: Boolean) {
        val extension = config.getExtension()

        if (useSafeFallback) {
            // SAFE STORAGE: Device Protected Storage (Works before Unlock)
            // We use getExternalFilesDir because it usually maps to a writable partition even in BFU (Before First Unlock) state on modern Android
            val secureFolder = getExternalFilesDir(null) ?: filesDir
            if (!secureFolder.exists()) secureFolder.mkdirs()

            val safeFile = File(secureFolder, "AutoRecord_${getCurrentFormattedDateTime()}.$extension")
            recordingPath = safeFile.absolutePath

            recorder = if (recordMp3()) Mp3Recorder(this) else MediaRecorderWrapper(this)
            recorder?.setOutputFile(recordingPath)

            resultUri = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.provider", safeFile)
        } else {
            // STANDARD STORAGE: User configured folder (Only works after Unlock)
            val defaultFolder = File(config.saveRecordingsFolder)
            if (!defaultFolder.exists()) defaultFolder.mkdir()

            recordingPath = "${defaultFolder.absolutePath}/${getCurrentFormattedDateTime()}.$extension"
            recorder = if (recordMp3()) Mp3Recorder(this) else MediaRecorderWrapper(this)

            if (isRPlus()) {
                val fileUri = createDocumentUriUsingFirstParentTreeUri(recordingPath)
                createSAFFileSdk30(recordingPath)
                resultUri = fileUri
                contentResolver.openFileDescriptor(fileUri, "w")!!.use { recorder?.setOutputFile(it) }
            } else {
                recorder?.setOutputFile(recordingPath)
            }
        }
        recorder?.prepare()
    }

    private fun beginRecordingSequence() {
        try {
            recorder?.start()
            duration = 0
            status = RECORDING_RUNNING
            broadcastRecorderInfo()

            durationTimer.cancel()
            durationTimer = Timer()
            durationTimer.scheduleAtFixedRate(getDurationUpdateTask(), 1000, 1000)

            startAmplitudeUpdates()

            // Update notification to indicate success
            startForegroundServiceCompat()
        } catch (e: Exception) {
            // If start() fails (e.g., Background Privacy restrictions), kill the service immediately
            // so the user doesn't see a "Recording" notification that does nothing.
            e.printStackTrace()
            cleanupAndStop()
        }
    }

    private fun cleanupAndStop() {
        stopRecording()
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        isRunning = false
        updateWidgets(false)
    }

    // --- Helper Methods ---

    private fun stopRecording() {
        durationTimer.cancel()
        amplitudeTimer.cancel()
        status = RECORDING_STOPPED

        recorder?.apply {
            try {
                stop()
                release()
            } catch (e: Exception) { e.printStackTrace() }

            ensureBackgroundThread {
                scanRecording()
                EventBus.getDefault().post(Events.RecordingCompleted())
            }
        }
        recorder = null
    }

    private fun getDurationUpdateTask() = object : TimerTask() {
        override fun run() {
            if (status == RECORDING_RUNNING) {
                duration++
                broadcastDuration()
            }
        }
    }

    private fun showNotification(): Notification {
        val channelId = "simple_recorder"
        val label = getString(R.string.app_name)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        NotificationChannel(channelId, label, NotificationManager.IMPORTANCE_LOW).apply {
            setSound(null, null)
            notificationManager.createNotificationChannel(this)
        }

        var text = getString(R.string.recording)
        if (status == RECORDING_PAUSED) text += " (${getString(R.string.paused)})"

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(label)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_graphic_eq_vector)
            .setContentIntent(getOpenAppIntent())
            .setOngoing(true)
            .setSilent(true)
            // Add Microphone Service Type for Android 14 compatibility within the builder as well if needed by libraries
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun getOpenAppIntent(): PendingIntent {
        val intent = getLaunchIntent() ?: Intent(this, SplashActivity::class.java)
        return PendingIntent.getActivity(this, RECORDER_RUNNING_NOTIF_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun scanRecording() {
        MediaScannerConnection.scanFile(this, arrayOf(recordingPath), arrayOf(recordingPath.getMimeType())) { _, uri ->
            if (uri != null) {
                EventBus.getDefault().post(Events.RecordingSaved(resultUri ?: uri))
            }
        }
    }

    private fun broadcastDuration() = EventBus.getDefault().post(Events.RecordingDuration(duration))
    private fun broadcastStatus() = EventBus.getDefault().post(Events.RecordingStatus(status))
    private fun recordMp3() = config.extension == EXTENSION_MP3

    private fun startAmplitudeUpdates() {
        amplitudeTimer.cancel()
        amplitudeTimer = Timer()
        amplitudeTimer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                recorder?.let { EventBus.getDefault().post(Events.RecordingAmplitude(it.getMaxAmplitude())) }
            }
        }, 0, AMPLITUDE_UPDATE_MS)
    }

    private fun togglePause() {
        try {
            if (status == RECORDING_RUNNING) {
                recorder?.pause()
                status = RECORDING_PAUSED
            } else if (status == RECORDING_PAUSED) {
                recorder?.resume()
                status = RECORDING_RUNNING
            }
            broadcastStatus()
            startForegroundServiceCompat()
        } catch (e: Exception) { showErrorToast(e) }
    }

    private fun cancelRecording() {
        durationTimer.cancel()
        amplitudeTimer.cancel()
        status = RECORDING_STOPPED
        recorder?.apply { try { stop(); release() } catch (e: Exception) {} }
        recorder = null
        File(recordingPath).delete()
        EventBus.getDefault().post(Events.RecordingCompleted())
        stopSelf()
    }
}
