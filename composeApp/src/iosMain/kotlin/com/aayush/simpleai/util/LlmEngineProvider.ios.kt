package com.aayush.simpleai.util

class IosLlmEngineProvider : LlmEngineProvider {
    override fun createEngine(modelPath: String, backend: LlmBackend): LlmEngine {
        val config = LlmEngineConfig(
            modelPath = modelPath,
            backend = backend,
            maxNumTokens = 4096,
            cacheDir = null
        )
        
        return createLlmEngine(config)
    }
}
