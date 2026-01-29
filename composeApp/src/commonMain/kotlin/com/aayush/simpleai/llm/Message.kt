package com.aayush.simpleai.llm

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock

/**
 * Custom serializer for ByteArray that uses Base64 encoding.
 * This is much more compact than the default array-of-integers serialization.
 */
@OptIn(ExperimentalEncodingApi::class)
object ByteArrayAsBase64Serializer : KSerializer<ByteArray> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ByteArrayAsBase64", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ByteArray) {
        encoder.encodeString(Base64.encode(value))
    }

    override fun deserialize(decoder: Decoder): ByteArray {
        val base64String = decoder.decodeString()
        return if (base64String.isEmpty()) ByteArray(0) else Base64.decode(base64String)
    }
}

@Serializable
data class Message(
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val role: Role,
    val fullText: Content.Text = Content.Text(""),
    val visibleText: String = "",
    val imageContent: List<Content.Bytes> = emptyList(),
    val audioContent: List<Content.Bytes> = emptyList(),
    val isLoading: Boolean = false,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolResponses: List<ToolResponse> = emptyList()
) {

    @Serializable
    sealed class Content {

        @Serializable
        data class Text(val text: String) : Content()

        @Serializable
        data class Bytes(
            @Serializable(with = ByteArrayAsBase64Serializer::class)
            val bytes: ByteArray
        ) : Content() {
            override fun equals(other: Any?): Boolean =
                (other as? Bytes)?.bytes?.contentEquals(bytes) ?: false

            override fun hashCode(): Int = bytes.contentHashCode()

            override fun toString(): String =
                "Bytes(size=${bytes.size}, hash=${bytes.contentHashCode()})"
        }
    }

    companion object {
        fun system(text: String) = Message(role = Role.SYSTEM, fullText = Content.Text(text))
        fun user(text: String) =
            Message(role = Role.USER, fullText = Content.Text(text), visibleText = text)
        
        /**
         * Creates a user message with text and optional image/audio content.
         * Content items are ordered: text first, then images, then audio.
         */
        fun user(
            text: String,
            images: List<ByteArray> = emptyList(),
            audio: List<ByteArray> = emptyList()
        ): Message =Message(
            role = Role.USER,
            fullText = Content.Text(text),
            imageContent = images.map(Content::Bytes),
            audioContent = audio.map(Content::Bytes),
            visibleText = text
        )

        fun assistant(
            fullText: String = "",
            visibleText: String = "",
            toolCalls: List<ToolCall> = emptyList(),
            isLoading: Boolean = false,
        ) = Message(
            role = Role.ASSISTANT,
            fullText = Content.Text(fullText),
            visibleText = visibleText,
            toolCalls = toolCalls,
            isLoading = isLoading,
        )

        fun toolResponses(responses: List<ToolResponse>) = Message(
            role = Role.TOOL,
            toolResponses = responses,
        )
    }
}

@Serializable
data class ToolCall(
    val name: String,
    val argumentStyle: ArgumentStyle,
    val argumentsString: String? = null,
) {
    val arguments: Map<String, Any?>
        get() = when (argumentStyle) {
            ArgumentStyle.JSON -> argumentsString
                ?.let { parseArgumentsFromJson(jsonContent = it) } ?: emptyMap()
            ArgumentStyle.PYTHON -> argumentsString
                ?.let { parsePythonStyleArguments(argsString = it) } ?: emptyMap()
        }

    @Serializable
    enum class ArgumentStyle {
        JSON,
        PYTHON
    }
}

@Serializable
data class ToolResponse(
    val name: String,
    val response: String
)
