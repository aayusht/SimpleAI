package com.aayush.simpleai.util

import android.content.Context

class AndroidLlmEngineProvider(private val context: Context) : LlmEngineProvider {
    override fun createEngine(modelPath: String, backend: LlmBackend): LlmEngine {
        val cacheDir = context.getExternalFilesDir(null)?.absolutePath
            ?.takeIf { modelPath.startsWith("/data/local/tmp") }
        
        val config = LlmEngineConfig(
            modelPath = modelPath,
            backend = backend,
            maxNumTokens = 4096,
            cacheDir = cacheDir
        )
        
        return createLlmEngine(config)
    }
}
