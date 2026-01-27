package com.aayush.simpleai.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aayush.simpleai.MainActivity
import com.aayush.simpleai.R
import com.aayush.simpleai.util.DownloadState
import com.aayush.simpleai.util.createDownloadClient
import com.aayush.simpleai.util.downloadFile
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath

class DownloadForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private var httpClient: HttpClient? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        httpClient = createDownloadClient()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val outputPath = intent.getStringExtra(EXTRA_OUTPUT_PATH) ?: return START_NOT_STICKY
                startDownload(url, outputPath)
            }
            ACTION_CANCEL_DOWNLOAD -> {
                cancelDownload()
            }
        }
        return START_NOT_STICKY
    }

    private fun startDownload(url: String, outputPath: String) {
        val notification = createNotification(0, "Starting download...")
        startForeground(NOTIFICATION_ID, notification)

        _downloadState.value = DownloadState.Starting

        downloadJob = serviceScope.launch {
            try {
                downloadFile(
                    client = httpClient!!,
                    fileUrl = url,
                    outputPath = outputPath.toPath(),
                    onProgressUpdate = { state ->
                        _downloadState.value = state
                        updateNotification(state)

                        if (state.isFinal) {
                            stopSelf()
                        }
                    }
                )
            } catch (e: Exception) {
                _downloadState.value = DownloadState.Error(e.message ?: "Unknown error")
                stopSelf()
            }
        }
    }

    private fun cancelDownload() {
        downloadJob?.cancel()
        _downloadState.value = DownloadState.Error("Download cancelled")
        stopSelf()
    }

    private fun updateNotification(state: DownloadState) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        when (state) {
            is DownloadState.Downloading -> {
                val progress = (state.progress * 100).toInt()
                val speedMbps = state.bytesPerSecond / 1024f / 1024f
                val text = "%.1f MB / %.1f MB (%.1f MB/s)".format(
                    state.receivedBytes / 1024f / 1024f,
                    state.totalBytes / 1024f / 1024f,
                    speedMbps
                )
                val notification = createNotification(progress, text)
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
            is DownloadState.Completed -> {
                val notification = createNotification(100, "Download complete")
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
            is DownloadState.Error -> {
                val notification = createNotification(0, "Download failed: ${state.message}")
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
            else -> { /* No notification update needed */ }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Model Download",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows download progress for AI model"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(progress: Int, contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val cancelIntent = Intent(this, DownloadForegroundService::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading AI Model")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        downloadJob?.cancel()
        httpClient?.close()
        serviceScope.cancel()
    }

    companion object {
        const val CHANNEL_ID = "download_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START_DOWNLOAD = "com.aayush.simpleai.START_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.aayush.simpleai.CANCEL_DOWNLOAD"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_OUTPUT_PATH = "extra_output_path"

        private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.NotStarted)
        val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

        fun resetState() {
            _downloadState.value = DownloadState.NotStarted
        }

        fun setPartialDownloadState(state: DownloadState) {
            _downloadState.value = state
        }
    }
}
