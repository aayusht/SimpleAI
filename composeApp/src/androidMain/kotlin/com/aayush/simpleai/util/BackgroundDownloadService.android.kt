package com.aayush.simpleai.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.aayush.simpleai.MainActivity
import com.aayush.simpleai.service.DownloadForegroundService
import kotlinx.coroutines.flow.StateFlow
import java.io.File

actual class BackgroundDownloadService(private val context: Context) {

    actual fun startDownload(url: String, outputPath: String) {
        val intent = Intent(context, DownloadForegroundService::class.java).apply {
            action = DownloadForegroundService.ACTION_START_DOWNLOAD
            putExtra(DownloadForegroundService.EXTRA_URL, url)
            putExtra(DownloadForegroundService.EXTRA_OUTPUT_PATH, outputPath)
        }
        context.startForegroundService(intent)
    }

    actual fun cancelDownload() {
        val intent = Intent(context, DownloadForegroundService::class.java).apply {
            action = DownloadForegroundService.ACTION_CANCEL_DOWNLOAD
        }
        context.startService(intent)
    }

    actual fun observeDownloadState(): StateFlow<DownloadState> {
        return DownloadForegroundService.downloadState
    }

    actual fun checkForPartialDownload(outputPath: String, expectedTotalBytes: Long): Boolean {
        val tempFile = File("$outputPath.tmp")
        if (tempFile.exists() && tempFile.length() > 0) {
            val downloadedBytes = tempFile.length()
            DownloadForegroundService.setPartialDownloadState(
                DownloadState.Downloading(
                    receivedBytes = downloadedBytes,
                    totalBytes = expectedTotalBytes,
                    bytesPerSecond = 0,
                    remainingMs = 0
                )
            )
            return true
        }
        return false
    }

    actual fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                val activity = context as? MainActivity
                if (activity != null) {
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        101
                    )
                }
            }
        }
    }
}
