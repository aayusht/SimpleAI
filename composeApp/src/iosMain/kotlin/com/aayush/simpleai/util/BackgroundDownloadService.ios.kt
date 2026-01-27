package com.aayush.simpleai.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.*
import platform.darwin.NSObject
import kotlin.time.Clock

actual class BackgroundDownloadService {
    
    actual fun startDownload(url: String, outputPath: String) {
        IosDownloadManagerHolder.instance.startDownload(url, outputPath)
    }

    actual fun cancelDownload() {
        IosDownloadManagerHolder.instance.cancelDownload()
    }

    actual fun observeDownloadState(): StateFlow<DownloadState> {
        return IosDownloadManagerHolder.downloadState
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun checkForPartialDownload(outputPath: String, expectedTotalBytes: Long): Boolean {
        val tempPath = "$outputPath.tmp"
        val fileManager = NSFileManager.defaultManager
        
        if (fileManager.fileExistsAtPath(tempPath)) {
            val fileAttributes = fileManager.attributesOfItemAtPath(tempPath, null)
            val downloadedBytes = (fileAttributes?.get(NSFileSize) as? NSNumber)?.longValue ?: 0L
            
            if (downloadedBytes > 0) {
                IosDownloadManagerHolder.updateState(
                    DownloadState.Downloading(
                        receivedBytes = downloadedBytes,
                        totalBytes = expectedTotalBytes,
                        bytesPerSecond = 0,
                        remainingMs = 0
                    )
                )
                return true
            }
        }
        return false
    }
}

/**
 * Singleton holder for download state and manager instance.
 * This is separate from the NSObject subclass to avoid Kotlin/Native limitations.
 */
object IosDownloadManagerHolder {
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.NotStarted)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()
    
    var outputPath: String? = null
    var totalBytesExpected: Long = 0L
    
    // For calculating download speed
    var lastProgressUpdate: Long = 0L
    var lastBytesWritten: Long = 0L
    val bytesReadSizeBuffer = mutableListOf<Long>()
    val bytesReadLatencyBuffer = mutableListOf<Long>()
    
    var backgroundCompletionHandler: (() -> Unit)? = null
    
    val instance: IosBackgroundDownloadManager by lazy { IosBackgroundDownloadManager() }
    
    fun updateState(state: DownloadState) {
        _downloadState.value = state
    }
    
    fun handleBackgroundSessionCompletion(completionHandler: () -> Unit) {
        backgroundCompletionHandler = completionHandler
    }
    
    fun resetSpeedBuffers() {
        lastProgressUpdate = 0L
        lastBytesWritten = 0L
        bytesReadSizeBuffer.clear()
        bytesReadLatencyBuffer.clear()
    }
}

/**
 * URLSession delegate for handling background downloads.
 * This is a regular class (not object) to avoid Kotlin/Native limitations with Obj-C inheritance.
 */
@OptIn(ExperimentalForeignApi::class)
class IosBackgroundDownloadManager : NSObject(), NSURLSessionDownloadDelegateProtocol {
    
    private val sessionIdentifier = "com.aayush.simpleai.backgroundDownload"
    private var backgroundSession: NSURLSession? = null
    private var currentDownloadTask: NSURLSessionDownloadTask? = null
    
    init {
        createBackgroundSession()
    }
    
    private fun createBackgroundSession() {
        val config = NSURLSessionConfiguration.backgroundSessionConfigurationWithIdentifier(sessionIdentifier)
        config.sessionSendsLaunchEvents = true
        config.discretionary = false
        config.allowsCellularAccess = true
        
        backgroundSession = NSURLSession.sessionWithConfiguration(
            configuration = config,
            delegate = this,
            delegateQueue = NSOperationQueue.mainQueue
        )
    }
    
    fun startDownload(url: String, destination: String) {
        IosDownloadManagerHolder.outputPath = destination
        IosDownloadManagerHolder.updateState(DownloadState.Starting)
        IosDownloadManagerHolder.resetSpeedBuffers()
        
        val nsUrl = NSURL.URLWithString(url) ?: run {
            IosDownloadManagerHolder.updateState(DownloadState.Error("Invalid URL"))
            return
        }
        
        val request = NSMutableURLRequest.requestWithURL(nsUrl)
        
        // Check for existing temp file to support resume
        val tempPath = "$destination.tmp"
        val fileManager = NSFileManager.defaultManager
        
        if (fileManager.fileExistsAtPath(tempPath)) {
            val fileAttributes = fileManager.attributesOfItemAtPath(tempPath, null)
            val existingBytes = (fileAttributes?.get(NSFileSize) as? NSNumber)?.longValue ?: 0L
            
            if (existingBytes > 0) {
                request.setValue("bytes=$existingBytes-", forHTTPHeaderField = "Range")
            }
        }
        
        currentDownloadTask = backgroundSession?.downloadTaskWithRequest(request)
        currentDownloadTask?.resume()
    }
    
    fun cancelDownload() {
        currentDownloadTask?.cancel()
        currentDownloadTask = null
        IosDownloadManagerHolder.updateState(DownloadState.Error("Download cancelled"))
    }
    
    // MARK: - NSURLSessionDownloadDelegate
    
    override fun URLSession(
        session: NSURLSession,
        downloadTask: NSURLSessionDownloadTask,
        didWriteData: Long,
        totalBytesWritten: Long,
        totalBytesExpectedToWrite: Long
    ) {
        IosDownloadManagerHolder.totalBytesExpected = totalBytesExpectedToWrite
        
        val currentTime = Clock.System.now().toEpochMilliseconds()
        val deltaBytes = totalBytesWritten - IosDownloadManagerHolder.lastBytesWritten
        
        var bytesPerMs = 0f
        if (IosDownloadManagerHolder.lastProgressUpdate != 0L && currentTime - IosDownloadManagerHolder.lastProgressUpdate > 200) {
            val sizeBuffer = IosDownloadManagerHolder.bytesReadSizeBuffer
            val latencyBuffer = IosDownloadManagerHolder.bytesReadLatencyBuffer
            
            if (sizeBuffer.size >= 5) sizeBuffer.removeAt(0)
            sizeBuffer.add(deltaBytes)
            
            if (latencyBuffer.size >= 5) latencyBuffer.removeAt(0)
            latencyBuffer.add(currentTime - IosDownloadManagerHolder.lastProgressUpdate)
            
            bytesPerMs = if (latencyBuffer.sum() > 0) {
                sizeBuffer.sum().toFloat() / latencyBuffer.sum()
            } else 0f
            
            IosDownloadManagerHolder.lastBytesWritten = totalBytesWritten
            IosDownloadManagerHolder.lastProgressUpdate = currentTime
        } else if (IosDownloadManagerHolder.lastProgressUpdate == 0L) {
            IosDownloadManagerHolder.lastProgressUpdate = currentTime
            IosDownloadManagerHolder.lastBytesWritten = totalBytesWritten
        }
        
        val remainingMs = if (bytesPerMs > 0f && totalBytesExpectedToWrite > 0L) {
            ((totalBytesExpectedToWrite - totalBytesWritten) / bytesPerMs).toLong()
        } else {
            0L
        }
        
        IosDownloadManagerHolder.updateState(
            DownloadState.Downloading(
                receivedBytes = totalBytesWritten,
                totalBytes = totalBytesExpectedToWrite,
                bytesPerSecond = (bytesPerMs * 1000).toLong(),
                remainingMs = remainingMs
            )
        )
    }
    
    override fun URLSession(
        session: NSURLSession,
        downloadTask: NSURLSessionDownloadTask,
        didFinishDownloadingToURL: NSURL
    ) {
        val destination = IosDownloadManagerHolder.outputPath ?: return
        val fileManager = NSFileManager.defaultManager
        val destinationUrl = NSURL.fileURLWithPath(destination)
        
        // Remove existing file if present
        if (fileManager.fileExistsAtPath(destination)) {
            fileManager.removeItemAtPath(destination, null)
        }
        
        // Move downloaded file to destination
        val success = fileManager.moveItemAtURL(didFinishDownloadingToURL, destinationUrl, null)
        
        if (success) {
            IosDownloadManagerHolder.updateState(DownloadState.Completed)
        } else {
            IosDownloadManagerHolder.updateState(DownloadState.Error("Failed to move downloaded file"))
        }
        
        // Clean up temp file if exists
        val tempPath = "$destination.tmp"
        if (fileManager.fileExistsAtPath(tempPath)) {
            fileManager.removeItemAtPath(tempPath, null)
        }
        
        currentDownloadTask = null
    }
    
    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didCompleteWithError: NSError?
    ) {
        didCompleteWithError?.let { error ->
            if (error.code != NSURLErrorCancelled) {
                IosDownloadManagerHolder.updateState(DownloadState.Error(error.localizedDescription))
            }
        }
        currentDownloadTask = null
    }
    
    override fun URLSessionDidFinishEventsForBackgroundURLSession(session: NSURLSession) {
        IosDownloadManagerHolder.backgroundCompletionHandler?.invoke()
        IosDownloadManagerHolder.backgroundCompletionHandler = null
    }
}
