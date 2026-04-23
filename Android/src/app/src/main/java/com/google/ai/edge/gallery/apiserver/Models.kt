/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.apiserver

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class ChatMessage(
    val role: String,
    val content: JsonElement? = null,
    val name: String? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    @SerialName("tool_calls") val toolCalls: JsonElement? = null,
)

@Serializable
data class ChatCompletionRequest(
    val model: String = "default",
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    @SerialName("top_p") val topP: Double = 0.95,
    @SerialName("top_k") val topK: Int = 40,
    @SerialName("max_tokens") val maxTokens: Int = 1024,
    val stream: Boolean = false,
    val tools: JsonElement? = null,
    @SerialName("tool_choice") val toolChoice: JsonElement? = null,
    @SerialName("parallel_tool_calls") val parallelToolCalls: Boolean? = null,
    @SerialName("response_format") val responseFormat: JsonElement? = null,
)

@Serializable
data class CompletionRequest(
    val model: String = "default",
    val prompt: String,
    val temperature: Double = 0.7,
    @SerialName("top_p") val topP: Double = 0.95,
    @SerialName("top_k") val topK: Int = 40,
    @SerialName("max_tokens") val maxTokens: Int = 1024,
    val stream: Boolean = false,
)

@Serializable
data class ChatCompletionChoice(
    val index: Int = 0,
    val message: ChatMessage,
    @SerialName("finish_reason") val finishReason: String = "stop",
)

@Serializable
data class ChatCompletionChunk(
    val id: String,
    @SerialName("object") val objectType: String = "chat.completion.chunk",
    val created: Long,
    val model: String,
    val choices: List<ChatCompletionDelta>,
)

@Serializable
data class ChatCompletionDelta(
    val index: Int = 0,
    val delta: ChatDelta,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class ChatDelta(
    val content: String? = null,
    val role: String? = null,
)

@Serializable
data class CompletionChoice(
    val index: Int = 0,
    val text: String,
    @SerialName("finish_reason") val finishReason: String = "stop",
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
)

@Serializable
data class ChatCompletionResponse(
    val id: String,
    @SerialName("object") val objectType: String = "chat.completion",
    val created: Long,
    val model: String,
    val choices: List<ChatCompletionChoice>,
    val usage: Usage = Usage(),
)

@Serializable
data class CompletionResponse(
    val id: String,
    @SerialName("object") val objectType: String = "text_completion",
    val created: Long,
    val model: String,
    val choices: List<CompletionChoice>,
    val usage: Usage = Usage(),
)

@Serializable
data class ModelInfo(
    val id: String,
    @SerialName("object") val objectType: String = "model",
    val created: Long = 0,
    @SerialName("owned_by") val ownedBy: String = "google",
)

@Serializable
data class ModelList(
    @SerialName("object") val objectType: String = "list",
    val data: List<ModelInfo>,
)

@Serializable
data class ErrorResponse(
    val error: ErrorDetail,
)

@Serializable
data class ErrorDetail(
    val message: String,
    val type: String = "invalid_request_error",
    val code: String? = null,
)

fun ChatMessage.extractTextContent(): String =
    when (val value = content) {
        null,
        JsonNull -> ""
        is JsonPrimitive -> value.contentOrNull ?: value.toString()
        is JsonObject -> value["text"].asTextContent()
        is JsonArray ->
            value.joinToString(separator = "") { item ->
                when (item) {
                    is JsonPrimitive -> item.contentOrNull ?: ""
                    is JsonObject -> {
                        val type = (item["type"] as? JsonPrimitive)?.contentOrNull
                        when (type) {
                            "text",
                            "input_text" -> item["text"].asTextContent()
                            else -> ""
                        }
                    }
                    else -> ""
                }
            }
        else -> value.toString()
    }

private fun JsonElement?.asTextContent(): String =
    when (this) {
        null,
        JsonNull -> ""
        is JsonPrimitive -> contentOrNull ?: toString()
        is JsonObject -> (this["value"] as? JsonPrimitive)?.contentOrNull ?: ""
        else -> toString()
    }
