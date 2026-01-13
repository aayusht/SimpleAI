package com.aayush.simpleai.util

import android.content.Context
import okio.Path
import okio.Path.Companion.toPath

class AndroidDownloadProvider(private val context: Context) : DownloadProvider {
    override fun getDownloadFolder(): Path {
        return context.getExternalFilesDir(null)?.absolutePath?.toPath() 
            ?: context.filesDir.absolutePath.toPath()
    }
}
