package com.aayush.simpleai.llm

import com.aayush.simpleai.util.EnginePtr

class ToolRegistry(toolDefinitions: List<ToolDefinition>) {
    private val tools = toolDefinitions.associateBy { it.name }
    val toolList: List<ToolDefinition> = toolDefinitions.toList()

    suspend fun execute(name: String, args: Map<String, Any?>, engine: EnginePtr): String {
        val tool = tools[name] ?: return "Error: Tool not found: $name"
        return try {
            tool.handle(args, engine)
        } catch (e: Exception) {
            "Error: ${e.message ?: e.toString()}"
        }
    }

    fun getOpenApiToolsJson(): String {
        if (toolList.isEmpty()) {
            return "[]"
        }
        val toolEntries =
            toolList.joinToString(separator = ",") { tool ->
                buildToolJson(tool)
            }
        return "[$toolEntries]"
    }

    private fun buildToolJson(tool: ToolDefinition): String {
        val name = jsonEscape(tool.name)
        val description = jsonEscape(tool.description)

        val parametersJson =
            if (tool.parameters.isEmpty()) {
                ""
            } else {
                val properties = tool.parameters.joinToString(separator = ",") { param ->
                    val propPieces = mutableListOf<String>()
                    propPieces.add("\"type\":\"${param.type.jsonType}\"")
                    if (param.description.isNotBlank()) {
                        propPieces.add("\"description\":\"${jsonEscape(param.description)}\"")
                    }
                    "\"${jsonEscape(param.name)}\":{${propPieces.joinToString(",")}}"
                }

                val requiredParams = tool.parameters.filter { it.required }.map { it.name }
                val requiredJson = if (requiredParams.isEmpty()) {
                    ""
                } else {
                    val requiredList = requiredParams.joinToString(separator = ",") {
                        "\"${jsonEscape(it)}\""
                    }
                    ",\"required\":[$requiredList]"
                }

                ",\"parameters\":{\"type\":\"object\",\"properties\":{$properties}$requiredJson}"
            }

        return "{\"type\":\"function\",\"function\":{\"name\":\"$name\",\"description\":\"$description\"$parametersJson}}"
    }
}
