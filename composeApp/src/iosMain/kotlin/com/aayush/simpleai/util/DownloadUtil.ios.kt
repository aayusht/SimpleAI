package com.aayush.simpleai.util

import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.*

class IosDownloadProvider : DownloadProvider {
    override fun getDownloadFolder(): Path {
        val paths = NSFileManager.defaultManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
        val documentsURL = paths.first() as NSURL
        return documentsURL.path!!.toPath()
    }
}
