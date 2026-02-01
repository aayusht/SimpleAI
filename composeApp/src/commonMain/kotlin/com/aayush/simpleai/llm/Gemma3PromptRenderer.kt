package com.aayush.simpleai.llm

import com.aayush.simpleai.util.NativeInputData


private const val GEMMA3_START_OF_TURN = "<start_of_turn>"
private const val GEMMA3_END_OF_TURN = "<end_of_turn>"

// Soft tokens that act as placeholders for media content.
// The native layer replaces these with actual preprocessed media data.
private const val IMAGE_SOFT_TOKEN = "<image_soft_token>"
private const val AUDIO_SOFT_TOKEN = "<audio_soft_token>"

data class Prompt(
    val text: String,
    val images: List<ByteArray>,
    val audio: List<ByteArray>,
) {
    val nativeInputs: Array<NativeInputData>
        get() = buildList {
            add(NativeInputData(text = text))
            addAll(images.map { NativeInputData(imageBytes = it) })
            addAll(audio.map { NativeInputData(audioBytes = it) })
        }.toTypedArray()
}

internal fun renderFullPrompt(
    systemPrompt: String?,
    toolRegistry: ToolRegistry,
    messages: List<Message>,
) = Prompt(
    text = renderGemma3Turns(
        systemPrompt = systemPrompt,
        toolRegistry = toolRegistry,
        messages = messages,
    ),
    images = messages.flatMap { it.imageContent.map(Message.Content.Bytes::bytes) },
    audio = messages.flatMap { it.audioContent.map(Message.Content.Bytes::bytes) },
)

internal fun renderIncrementalPrompt(newMessages: List<Message>) = Prompt(
    text = renderGemma3Turns(
        systemPrompt = null,
        toolRegistry = ToolRegistry(emptyList()),
        messages = newMessages,
    ),
    images = newMessages.flatMap { it.imageContent.map(Message.Content.Bytes::bytes) },
    audio = newMessages.flatMap { it.audioContent.map(Message.Content.Bytes::bytes) },
)

private fun buildToolResponseJson(responses: List<ToolResponse>): String {
    if (responses.isEmpty()) return "[]"
    val items = responses.joinToString(separator = ",") { response ->
        val escaped = jsonEscape(value = response.response)
        val name = jsonEscape(value = response.name)
        "{\"type\":\"tool_response\",\"name\":\"$name\",\"response\":\"$escaped\"}"
    }
    return "[$items]"
}

private fun renderGemma3Turns(
    systemPrompt: String?,
    toolRegistry: ToolRegistry,
    messages: List<Message>,
) = buildString {

    // Part 1: Tools definition + my own stuff
    //{%- if tools %}
    //    {{- '<start_of_turn>system\n' }}
    //    {%- for tool in tools %}
    //        {{- tool | trim }}
    //        {{- "\n\n" }}
    //    {%- endfor %}
    //    {{- '<end_of_turn>\n'}}
    //{%- endif %}
    val tools = toolRegistry.toolList
    if (tools.isNotEmpty()) {
        append("${GEMMA3_START_OF_TURN}system\n")
        append("""
If and only if you need more info to answer the user, to execute a tool, output the following json, with included backticks:
```tool_code
print(default_api.web_search(query="example query"))
```
You will receive a response from the tool, and then can answer the question accordingly after receiving context.
Verify information with search tools when asked for info, but don't make tool calls if information isn't required!
eg if they're just saying hi or thanking you or asking about very simple facts.
I repeat, only use tool calls when the immediately prior user message needs factual context.
It's likely that if you've already finished replying to a message, a tool call is not likely.

Tools may return empty results or error messages, in which case just mention that the tools couldn't work.
If the result was necessary to answer the question, just apologize. Don't make up info based on the results.

Tools spec: ${tools.joinToString(separator = "\n\n") { it.pythonDocString }}
        """.trimIndent())
        append("${GEMMA3_END_OF_TURN}\n")
    }

    // Part 2: Add in system prompt and remove if it was sent as a message (it shouldn't be)
    //{%- if messages[0]['role'] == 'system' -%}
    //    {%- if messages[0]['content'] is string -%}
    //        {%- set first_user_prefix = messages[0]['content'] + '\n\n' -%}
    //    {%- else -%}
    //        {%- set first_user_prefix = messages[0]['content'][0]['text'] + '\n\n' -%}
    //    {%- endif -%}
    //    {%- set loop_messages = messages[1:] -%}
    //{%- else -%}
    //    {%- set first_user_prefix = "" -%}
    //    {%- set loop_messages = messages -%}
    //{%- endif -%}
    val firstUserPrefix: String
    val loopMessages: List<Message>

    if (messages.firstOrNull()?.role == Role.SYSTEM) {
        val sys = messages.first().fullText.text.trim()
        firstUserPrefix = "${sys.ifEmpty { systemPrompt.orEmpty().trim() }}\n\n"
        loopMessages = messages.drop(1)
    } else {
        firstUserPrefix = systemPrompt.orEmpty()
        loopMessages = messages
    }

    for ((idx, message) in loopMessages.withIndex()) {
        //    {%- if (message['role'] == 'assistant') -%}
        //        {%- set role = "model" -%}
        //    {%- elif (message['role'] == 'tool') -%}
        //        {%- set is_tool = True -%}
        //        {%- set role = "user" -%}
        //    {%- else -%}
        //        {%- set role = message['role'] -%}
        //    {%- endif -%}
        //    {{ '<start_of_turn>' + role + '\n' + (first_user_prefix if loop.first else "") }}
        val isTool = message.role == Role.TOOL
        val isFirst = idx == 0
        val role = when (message.role) {
            Role.SYSTEM -> "system"
            Role.USER -> "user"
            Role.ASSISTANT -> "model"
            Role.TOOL -> "user"
        }
        append("$GEMMA3_START_OF_TURN$role${if (isFirst) firstUserPrefix else ""}\n")

        //    {%- if is_tool -%}
        //        {{ '```tool_outputs\n' }}
        //    {%- endif -%}
        //    {%- if 'content' in message -%}
        //        {%- if message['content'] is string -%}
        //            {{ message['content'] | trim }}
        //        {%- elif message['content'] is iterable -%}
        //            {%- for item in message['content'] -%}
        //                {%- if item['type'] == 'audio' -%}
        //                    {{ '<audio_soft_token>' }}
        //                {%- elif item['type'] == 'image' -%}
        //                    {{ '<image_soft_token>' }}
        //                {%- elif item['type'] == 'text' -%}
        //                    {{ item['text'] | trim }}
        //                {%- endif -%}
        //                {%- if is_tool -%}
        //                    {{ '\n' }}
        //                {%- endif -%}
        //            {%- endfor -%}
        //        {%- else -%}
        //            {{ raise_exception("Invalid content type") }}
        //        {%- endif -%}
        //    {%- endif -%}
        //    {%- if is_tool -%}
        //        {{ '```' }}
        //        {%- set is_tool = False -%}
        //    {%- endif -%}
        if (isTool) {
            // kind of messing with jinja here, we don't have a content array
            append("```tool_outputs\n")
            append(buildToolResponseJson(responses = message.toolResponses))
            append("```")
        } else {

            // forcing order as audio, image, text
            repeat(times = message.imageContent.size) {
                append(IMAGE_SOFT_TOKEN)
            }
            repeat(times = message.audioContent.size) {
                append(AUDIO_SOFT_TOKEN)
            }
            append(message.fullText.text.trim())
        }

        //    {%- if 'tool_calls' in message -%}
        //        {{- '```tool_code\n' -}}
        //        {%- for tool_call in message['tool_calls'] -%}
        //            {%- if 'function' in tool_call -%}
        //                {%- set tool_call = tool_call['function'] -%}
        //            {%- endif -%}
        //            {{-  tool_call['name'] + '(' -}}
        //            {%- if 'arguments' in tool_call -%}
        //                {%- for key in tool_call['arguments'] -%}
        //                    {{- key + '=' + tool_call['arguments'][key] -}}
        //                    {% if not loop.last %}
        //                        {{- ', ' -}}
        //                    {% endif %}
        //                {%- endfor %}
        //            {{- ')\n' -}}
        //            {%- endif -%}
        //        {%- endfor -%}
        //        {{- '```' -}}
        //    {%- endif -%}
        // unnecessary, this assumes we have to reconstruct full text from visible text and tool calls

        //    {{ '<end_of_turn>\n' }}
        append("${GEMMA3_END_OF_TURN}\n")
    }

    //{%- if add_generation_prompt -%}
    //    {{'<start_of_turn>model\n'}}
    //{%- endif -%}
    if (loopMessages.isEmpty() || loopMessages.last().role != Role.ASSISTANT) {
        append("${GEMMA3_START_OF_TURN}model\n")
    }
}