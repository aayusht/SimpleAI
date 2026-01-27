package com.aayush.simpleai.util

import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-specific background download service that can continue
 * downloading even when the app is backgrounded.
 */
expect class BackgroundDownloadService {
    /**
     * Starts a background download from [url] to [outputPath].
     * The download will continue even if the app is backgrounded.
     */
    fun startDownload(url: String, outputPath: String)

    /**
     * Cancels the current download if one is in progress.
     */
    fun cancelDownload()

    /**
     * Returns a StateFlow that emits the current download state.
     * Observers can collect this to receive progress updates.
     */
    fun observeDownloadState(): StateFlow<DownloadState>

    /**
     * Checks if there's a partial download from a previous session.
     * If found, updates the download state to reflect the partial progress.
     * Returns true if a partial download was detected.
     */
    fun checkForPartialDownload(outputPath: String, expectedTotalBytes: Long): Boolean
}
