package com.aayush.simpleai.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

// This stuff does not work whatsoever

private const val TAG = "JinjaPatchUtil"
private const val JINJA_MACROS_ASSET = "jinja_macros.txt"
private const val PATCH_MARKER = "{# --- MACRO DEFINITION --- #}"

// Max bytes to read when searching for markers (100KB should be plenty)
private const val HEADER_SEARCH_LIMIT = 100_000

// Markers to find the injection point in the litertlm file
private val BOS_TOKEN_MARKERS = listOf("{{ bos_token }}", "{{- bos_token }}")

/**
 * Reads only the first N bytes of a file as raw bytes.
 * Memory-efficient for large files when we only need to check the header.
 */
private fun readFileHeaderBytes(file: File, maxBytes: Int = HEADER_SEARCH_LIMIT): ByteArray {
    val buffer = ByteArray(maxBytes)
    FileInputStream(file).use { input ->
        val bytesRead = input.read(buffer)
        if (bytesRead <= 0) return ByteArray(0)
        return buffer.copyOf(bytesRead)
    }
}

/**
 * Finds the index of a byte pattern within a byte array.
 * Returns -1 if not found.
 */
private fun ByteArray.indexOf(pattern: ByteArray): Int {
    if (pattern.isEmpty()) return 0
    if (pattern.size > this.size) return -1
    
    outer@ for (i in 0..(this.size - pattern.size)) {
        for (j in pattern.indices) {
            if (this[i + j] != pattern[j]) continue@outer
        }
        return i
    }
    return -1
}

/**
 * Patches the litertlm model file to include Jinja macro definitions for tool/function support.
 * The macros are injected after the first occurrence of `{{ bos_token }}` or `{{- bos_token }}`.
 * 
 * Uses streaming and raw bytes to avoid corrupting the binary file format.
 *
 * @param context Android context to access assets
 * @param modelFile The litertlm model file to patch
 * @return true if patching was successful or file was already patched, false otherwise
 */
fun patchLitertlmFile(context: Context, modelFile: File): Boolean {
    if (!modelFile.exists()) {
        Log.e(TAG, "Model file does not exist: ${modelFile.absolutePath}")
        return false
    }

    try {
        // Read only the header bytes to find markers (memory efficient)
        val headerBytes = readFileHeaderBytes(modelFile)
        val patchMarkerBytes = PATCH_MARKER.toByteArray(Charsets.UTF_8)

        // Check if already patched
        if (headerBytes.indexOf(patchMarkerBytes) != -1) {
            Log.d(TAG, "Model file already patched")
            return true
        }

        // Find the injection point (after bos_token marker)
        var injectionIndex = -1
        var foundMarker: String? = null

        for (marker in BOS_TOKEN_MARKERS) {
            val markerBytes = marker.toByteArray(Charsets.UTF_8)
            val markerIndex = headerBytes.indexOf(markerBytes)
            if (markerIndex != -1) {
                // Inject after the marker
                injectionIndex = markerIndex + markerBytes.size
                foundMarker = marker
                break
            }
        }

        if (injectionIndex == -1) {
            throw Exception("Failed to find injection point in model file (searched first $HEADER_SEARCH_LIMIT bytes)")
        }

        Log.d(TAG, "Found marker '$foundMarker' at byte index $injectionIndex, injecting macros")

        // Load the Jinja macros from assets as bytes
        val jinjaMacros = context.assets.open(JINJA_MACROS_ASSET).bufferedReader().use { it.readText() }
        val injectionPayload = "\n$jinjaMacros\n"
        val injectionBytes = injectionPayload.toByteArray(Charsets.UTF_8)

        // Use a temp file to write the patched content
        val tempFile = File(modelFile.parentFile, "${modelFile.name}.patching")

        try {
            FileOutputStream(tempFile).use { output ->
                FileInputStream(modelFile).use { input ->
                    // Copy the header up to injection point (raw bytes)
                    var remaining = injectionIndex
                    val copyBuffer = ByteArray(minOf(DEFAULT_BUFFER_SIZE, remaining))
                    while (remaining > 0) {
                        val toRead = minOf(copyBuffer.size, remaining)
                        val read = input.read(copyBuffer, 0, toRead)
                        if (read == -1) break
                        output.write(copyBuffer, 0, read)
                        remaining -= read
                    }

                    // Write the injection payload (macros)
                    output.write(injectionBytes)

                    // Stream the rest of the file (raw bytes)
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }

            // Replace original with patched file
            if (modelFile.exists()) {
                modelFile.delete()
            }
            if (!tempFile.renameTo(modelFile)) {
                throw IOException("Failed to rename patched temp file to original")
            }

            Log.d(TAG, "Successfully patched model file with Jinja macros")
            return true

        } catch (e: Exception) {
            // Clean up temp file on failure
            tempFile.delete()
            throw e
        }

    } catch (e: Exception) {
        Log.e(TAG, "Failed to patch model file", e)
        return false
    }
}

/**
 * Checks if a litertlm model file needs patching (i.e., contains tool usage but no macro definitions).
 * Only reads the first N bytes of the file to be memory efficient.
 * Works with raw bytes to avoid corrupting binary data.
 */
fun needsJinjaPatch(modelFile: File): Boolean {
    if (!modelFile.exists()) return false

    val headerBytes = readFileHeaderBytes(modelFile)

    // Convert search patterns to bytes
    val toolRef1 = "format_function_declaration".toByteArray(Charsets.UTF_8)
    val toolRef2 = "<start_function_declaration>".toByteArray(Charsets.UTF_8)
    val patchMarkerBytes = PATCH_MARKER.toByteArray(Charsets.UTF_8)

    // Needs patching if it has tool references but no macro definitions
    val hasToolReferences = headerBytes.indexOf(toolRef1) != -1 || 
            headerBytes.indexOf(toolRef2) != -1
    val hasMacroDefinitions = headerBytes.indexOf(patchMarkerBytes) != -1

    return hasToolReferences && !hasMacroDefinitions
}

