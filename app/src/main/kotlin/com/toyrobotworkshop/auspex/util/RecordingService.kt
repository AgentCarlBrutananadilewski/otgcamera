package com.toyrobotworkshop.auspex.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Build
import androidx.core.app.NotificationCompat
import com.toyrobotworkshop.auspex.R
import com.toyrobotworkshop.auspex.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Foreground service for background video recording.
 *
 * Keeps the recording alive when the app is in the background or the screen is off.
 */
@AndroidEntryPoint
class RecordingService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "recording_channel"

        fun startIntent(context: Context, outputPath: String) =
            Intent(context, RecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_OUTPUT_PATH, outputPath)
            }

        fun stopIntent(context: Context) =
            Intent(context, RecordingService::class.java).apply {
                action = ACTION_STOP
            }

        private const val ACTION_START = "ACTION_START"
        private const val ACTION_STOP = "ACTION_STOP"
        private const val EXTRA_OUTPUT_PATH = "output_path"
    }

    private val binder = LocalBinder()
    private var outputPath: String? = null

    inner class LocalBinder : Binder() {
        fun getService(): RecordingService = this@RecordingService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                outputPath = intent.getStringExtra(EXTRA_OUTPUT_PATH)
                createNotificationChannel()
                val notification = buildNotification()
                startForeground(NOTIFICATION_ID, notification)
                // TODO: Start actual recording via MediaCodec
            }
            ACTION_STOP -> {
                // TODO: Stop recording and finalize file
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.recording_service_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Active video recording"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.recording_service_title))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                PendingIntent.getService(
                    this, 1, stopIntent(this),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
    }
}
