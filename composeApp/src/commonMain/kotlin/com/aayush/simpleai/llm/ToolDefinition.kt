package com.aayush.simpleai.llm

import com.aayush.simpleai.util.EnginePtr

sealed class ToolParamType<T>(val pyType: String, val pyExample: String) {
    abstract fun extract(value: Any?): T?

    data object STRING : ToolParamType<String>(pyType = "str", pyExample = "'string value'") {
        override fun extract(value: Any?): String? = value as? String
    }

    data object INTEGER : ToolParamType<Int>(pyType = "int", pyExample = "42") {
        override fun extract(value: Any?): Int? = (value as? Number)?.toInt()
    }

    data object NUMBER : ToolParamType<Double>(pyType = "float", pyExample = "6.7") {
        override fun extract(value: Any?): Double? = (value as? Number)?.toDouble()
    }

    data object BOOLEAN : ToolParamType<Boolean>(pyType = "bool", pyExample = "True") {
        override fun extract(value: Any?): Boolean? = value as? Boolean
    }

//    data object ARRAY : ToolParamType<List<Any?>>("array") {
//        override fun extract(value: Any?): List<Any?>? = value as? List<Any?>
//    }
//
//    data object OBJECT : ToolParamType<Map<String, Any?>>("object") {
//        override fun extract(value: Any?): Map<String, Any?>? = value as? Map<String, Any?>
//    }
}

sealed class ToolParameter<T>(
    val name: String,
    val type: ToolParamType<T>,
    val description: String = "",
    val required: Boolean = true,
) {

    class OptionalToolParameter<T>(
        name: String,
        type: ToolParamType<T>,
        description: String = "",
    ) : ToolParameter<T>(name, type, description, false) {
        fun get(args: Map<String, Any?>): T? {
            return type.extract(args[name])
        }
    }

    class Required<T>(
        name: String,
        type: ToolParamType<T>,
        description: String = "",
    ) : ToolParameter<T>(name, type, description, true) {
        fun get(args: Map<String, Any?>): T {
            return type.extract(args[name])
                ?: throw IllegalArgumentException("Missing required parameter: $name")
        }
    }
}

sealed class ToolDefinition(
    val name: String,
    val description: String,
) {
    open val parameters: List<ToolParameter<*>> = emptyList()

    val pythonDocString: String
        get() = buildString {

            // Function signature: def name(param1: type, param2: type) -> dict:
            append("def ${name}(")
            append(parameters.joinToString(separator = ", ") { "${it.name}: ${it.type.pyType}"})
            append(") -> dict:\n")

            // Docstring
            append("    \"\"\"${description.trim()}")

            // Args section (if there are parameters with descriptions)
            val paramsWithDesc = parameters.filter { it.description.isNotBlank() }
            if (paramsWithDesc.isNotEmpty()) {
                append("\n\n    Args:\n")
                for (param in paramsWithDesc) {
                    append("        ${param.name}: ${param.description.trim()}\n")
                }
                append("    ")
            }

            append("\"\"\"\n    ...")
        }


    abstract suspend fun handle(args: Map<String, Any?>, engine: EnginePtr): String

    class WebSearchTool : ToolDefinition(
        name = "web_search",
        description = """
            Search for any needed information and receive a summary of results.
            When referencing links, use the following format: 
            [text to display](https://www.example.com)
            """.trim(),
    ) {
        private val queryParam = ToolParameter.Required(
            name = "query",
            type = ToolParamType.STRING,
            description = "Information you would like to query the web for"
        )

        override val parameters = listOf(queryParam)

        override suspend fun handle(args: Map<String, Any?>, engine: EnginePtr): String {
            val query: String = queryParam.get(args)
            return webSearch(query, engine)
        }
    }
}
