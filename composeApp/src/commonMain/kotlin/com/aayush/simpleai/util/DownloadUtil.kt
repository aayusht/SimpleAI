package com.aayush.simpleai.util

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import okio.use
import kotlin.time.Clock

sealed class DownloadState {
    data object NotStarted : DownloadState()
    data class Downloading(
        val receivedBytes: Long,
        val totalBytes: Long,
        val bytesPerSecond: Long = 0,
        val remainingMs: Long = 0
    ) : DownloadState() {
        val progress: Float
            get() = if (totalBytes > 0) receivedBytes.toFloat() / totalBytes else 0f

        val progressString: String
            get() = "${(progress * 100).toInt()}.${((progress * 100) % 1 * 100).toInt()}"
    }
    data object Completed : DownloadState()
    data class Error(val message: String) : DownloadState()
}

/**
 * Creates a default HttpClient for downloading.
 */
fun createDownloadClient(): HttpClient = HttpClient()

/**
 * Downloads a file from [fileUrl] to [outputPath] using Okio and Ktor.
 * Replicates the logic from the Android reference implementation.
 */
suspend fun downloadFile(
    client: HttpClient,
    fileUrl: String,
    outputPath: Path,
    onProgressUpdate: (DownloadState) -> Unit
) {
    val tempPath = "$outputPath.tmp".toPath()
    val fileSystem = FileSystem.SYSTEM
    
    try {
        val existingBytes = if (fileSystem.exists(tempPath)) {
            fileSystem.metadata(tempPath).size ?: 0L
        } else {
            0L
        }

        client.prepareGet(fileUrl) {
            if (existingBytes > 0) {
                header(HttpHeaders.Range, "bytes=$existingBytes-")
            }
        }.execute { response ->
            if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.PartialContent) {
                throw Exception("HTTP error code: ${response.status}")
            }

            val contentRange = response.headers[HttpHeaders.ContentRange]
            val isResuming = response.status == HttpStatusCode.PartialContent && contentRange != null
            
            var totalBytes = response.contentLength() ?: -1L
            var downloadedBytes = 0L

            if (isResuming) {
                // Content-Range: bytes 21010-47021/47022
                val totalStr = contentRange.substringAfterLast('/')
                totalBytes = totalStr.toLongOrNull() ?: totalBytes
                downloadedBytes = existingBytes
            } else if (existingBytes > 0) {
                fileSystem.delete(tempPath)
            }

            onProgressUpdate(DownloadState.Downloading(downloadedBytes, totalBytes))

            val channel = response.bodyAsChannel()
            
            val rawSink = if (isResuming) {
                fileSystem.appendingSink(tempPath, mustExist = true)
            } else {
                fileSystem.sink(tempPath)
            }

            rawSink.buffer().use { sink ->
                val buffer = ByteArray(8192)
                var lastProgressUpdate = 0L
                var deltaBytes = 0L
                
                val bytesReadSizeBuffer = mutableListOf<Long>()
                val bytesReadLatencyBuffer = mutableListOf<Long>()

                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buffer)
                    if (read <= 0) break
                    
                    sink.write(buffer, 0, read)
                    downloadedBytes += read
                    deltaBytes += read

                    val currentTime = Clock.System.now().toEpochMilliseconds()
                    if (currentTime - lastProgressUpdate > 200) {
                        var bytesPerMs = 0f
                        if (lastProgressUpdate != 0L) {
                            if (bytesReadSizeBuffer.size >= 5) bytesReadSizeBuffer.removeAt(0)
                            bytesReadSizeBuffer.add(deltaBytes)

                            if (bytesReadLatencyBuffer.size >= 5) bytesReadLatencyBuffer.removeAt(0)
                            bytesReadLatencyBuffer.add(currentTime - lastProgressUpdate)

                            deltaBytes = 0L
                            bytesPerMs = if (bytesReadLatencyBuffer.sum() > 0) {
                                bytesReadSizeBuffer.sum().toFloat() / bytesReadLatencyBuffer.sum()
                            } else 0f
                        }

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

        if (fileSystem.exists(outputPath)) {
            fileSystem.delete(outputPath)
        }
        fileSystem.atomicMove(tempPath, outputPath)
        onProgressUpdate(DownloadState.Completed)

    } catch (e: Exception) {
        onProgressUpdate(DownloadState.Error(e.message ?: "Unknown error"))
    }
}

/**
 * Provider for platform-specific download directory.
 */
interface DownloadProvider {
    fun getDownloadFolder(): Path
}
