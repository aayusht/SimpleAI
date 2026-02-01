package com.aayush.simpleai.llm

import com.aayush.simpleai.util.EnginePtr

class ToolRegistry(toolDefinitions: List<ToolDefinition>) {
    private val tools = toolDefinitions.associateBy { it.name }
    val toolList: List<ToolDefinition> = toolDefinitions.toList()

    suspend fun execute(name: String, args: Map<String, Any?>): String {
        val tool = tools[name] ?: return "Error: Tool not found: $name"
        return try {
            tool.handle(args)
        } catch (e: Exception) {
            "Error: ${e.message ?: e.toString()}"
        }
    }
}