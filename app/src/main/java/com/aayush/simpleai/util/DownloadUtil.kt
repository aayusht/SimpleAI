package com.aayush.simpleai.util

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.text.substringAfter

private const val TMP_FILE_EXT = "tmp"

sealed class DownloadState {
    object NotStarted : DownloadState()
    data class Downloading(
        val receivedBytes: Long,
        val totalBytes: Long,
        val bytesPerSecond: Long = 0,
        val remainingMs: Long = 0
    ) : DownloadState() {
        val progress: Float
            get() = if (totalBytes > 0) receivedBytes.toFloat() / totalBytes else 0f
    }
    object Completed : DownloadState()
    data class Error(val message: String) : DownloadState()
}

internal suspend fun downloadFile(
    context: Context,
    fileUrl: String,
    outputFile: File,
    onProgressUpdate: (DownloadState) -> Unit
) {
    val url = URL(fileUrl)
    val connection = url.openConnection() as HttpURLConnection

    try {
        // Prepare temp file for download
        val outputTmpFile = File(outputFile.parentFile, "${outputFile.name}.$TMP_FILE_EXT")
        val existingBytes = if (outputTmpFile.exists()) outputTmpFile.length() else 0L

        // Support resume if partial download exists
        if (existingBytes > 0) {
            connection.setRequestProperty("Range", "bytes=${existingBytes}-")
        }

        connection.connect()

        if (connection.responseCode != HttpURLConnection.HTTP_OK &&
            connection.responseCode != HttpURLConnection.HTTP_PARTIAL
        ) {
            throw IOException("HTTP error code: ${connection.responseCode}")
        }

        // Calculate total bytes
        var downloadedBytes = 0L
        var totalBytes = connection.contentLengthLong

        val contentRange = connection.getHeaderField("Content-Range")
        if (contentRange != null) {
            // Parse Content-Range header (e.g., "bytes 21010-47021/47022")
            val rangeParts = contentRange.substringAfter("bytes ").split("/")
            val byteRange = rangeParts[0].split("-")
            val startByte = byteRange[0].toLong()
            totalBytes = rangeParts[1].toLong()
            downloadedBytes = startByte
        } else if (existingBytes > 0) {
            // Server doesn't support range, restart download
            outputTmpFile.delete()
        }

        onProgressUpdate(DownloadState.Downloading(downloadedBytes, totalBytes))

        val inputStream = connection.inputStream
        val outputStream = FileOutputStream(outputTmpFile, contentRange != null)

        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var bytesRead: Int
        var lastProgressUpdate = 0L
        var deltaBytes = 0L

        // Buffers for calculating download rate
        val bytesReadSizeBuffer = mutableListOf<Long>()
        val bytesReadLatencyBuffer = mutableListOf<Long>()

        inputStream.use { input ->
            outputStream.use { output ->
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    deltaBytes += bytesRead

                    // Report progress every 200ms
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastProgressUpdate > 200) {
                        var bytesPerMs = 0f
                        if (lastProgressUpdate != 0L) {
                            // Keep last 5 measurements for smoothing
                            if (bytesReadSizeBuffer.size >= 5) {
                                bytesReadSizeBuffer.removeAt(0)
                            }
                            bytesReadSizeBuffer.add(deltaBytes)

                            if (bytesReadLatencyBuffer.size >= 5) {
                                bytesReadLatencyBuffer.removeAt(0)
                            }
                            bytesReadLatencyBuffer.add(currentTime - lastProgressUpdate)

                            deltaBytes = 0L
                            bytesPerMs = bytesReadSizeBuffer.sum().toFloat() / bytesReadLatencyBuffer.sum()
                        }

                        // Calculate remaining time
                        val remainingMs = if (bytesPerMs > 0f && totalBytes > 0L) {
                            ((totalBytes - downloadedBytes) / bytesPerMs).toLong()
                        } else {
                            0L
                        }

                        onProgressUpdate(
                            DownloadState.Downloading(
                                receivedBytes = downloadedBytes,
                                totalBytes = totalBytes,
                                bytesPerSecond = (bytesPerMs * 1000).toLong(),
                                remainingMs = remainingMs
                            )
                        )

                        lastProgressUpdate = currentTime
                    }
                }
            }
        }

        // Rename temp file to final file
        if (outputFile.exists()) {
            outputFile.delete()
        }
        if (!outputTmpFile.renameTo(outputFile)) {
            throw IOException("Failed to rename temp file to final file")
        }

        onProgressUpdate(DownloadState.Completed)

    } finally {
        connection.disconnect()
    }
}