package com.aayush.simpleai.llm

sealed class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: List<ToolParameter> = emptyList(),
) {

    abstract fun handle(args: Map<String, Any?>): String

    enum class ToolParamType(val jsonType: String) {
        STRING("string"),
        INTEGER("integer"),
        NUMBER("number"),
        BOOLEAN("boolean"),
        ARRAY("array"),
        OBJECT("object")
    }

    data class ToolParameter(
        val name: String,
        val type: ToolParamType = ToolParamType.STRING,
        val description: String = "",
        val required: Boolean = true
    )

    class SampleWeatherSearchTool : ToolDefinition(
        name = "weather_search",
        description = "Search for weather at current location",
        parameters = listOf(
            ToolParameter(
                name = "location",
                type = ToolParamType.STRING,
                description = "Location to search for, eg \"San Francisco\".",
                required = true
            )
        ),
    ) {
        override fun handle(args: Map<String, Any?>): String {
            val location = args["location"] as? String
                ?: throw IllegalArgumentException("Invalid or missing location")
            return handle(location)
        }

        private fun handle(location: String): String {
            return "It's cold and rainy today, with a high of 20F and " +
                    "1 inch of rain predicted today with a 90% chance"
        }
    }
}

