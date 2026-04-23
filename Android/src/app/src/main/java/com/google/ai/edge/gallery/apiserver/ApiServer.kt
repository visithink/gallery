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

import android.util.Log
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.runtime.LlmModelHelper
import com.google.ai.edge.gallery.runtime.ResultListener
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.response.respondBytesWriter
import io.ktor.utils.io.writeFully
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class ApiServer(
    private val port: Int = 8000,
    private val getModelHelper: () -> LlmModelHelper?,
    private val getCurrentModel: () -> Model?,
    private val getAvailableModels: () -> List<Model>,
) {
    private var server: EmbeddedServer<*, *>? = null
    private var isRunning = false
    private val requestCounter = AtomicInteger(0)

    val status: Boolean
        get() = isRunning

    val serverPort: Int
        get() = port

    fun start(): Boolean {
        if (isRunning) return true

        return try {
            val serverInstance = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                module(this)
            }
            serverInstance.start(wait = false)
            server = serverInstance
            isRunning = true
            Log.d(TAG, "API server started on port $port")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start API server", e)
            false
        }
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
        isRunning = false
        Log.d(TAG, "API server stopped")
    }

    private fun module(application: Application) {
        application.install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            })
        }

        application.routing {
            get("/health") {
                call.respond(mapOf("status" to "healthy", "model" to (getCurrentModel()?.name ?: "none")))
            }

            get("/v1/models") {
                val models = getAvailableModels()
                call.respond(
                    ModelList(
                        objectType = "list",
                        data = models.map { model -> ModelInfo(id = model.name) }
                    )
                )
            }

            get("/v1/compression/config") {
                call.respond(
                    mapOf(
                        "environment" to CompressionConfig.currentEnvironment.name,
                        "isCompressionEnabled" to CompressionConfig.isCompressionEnabled,
                        "maxTokenLimit" to CompressionConfig.maxTokenLimit,
                        "preserveRecentTurns" to CompressionConfig.preserveRecentTurns,
                        "summaryMaxTokens" to CompressionConfig.summaryMaxTokens,
                        "enableDetailedLogging" to CompressionConfig.enableDetailedLogging
                    )
                )
            }

            post("/v1/compression/config") {
                val newEnv = call.receive< Map<String, String>>()
                newEnv["environment"]?.let { env ->
                    val environment = try {
                        CompressionConfig.Environment.valueOf(env)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(ErrorDetail("Invalid environment: $env")))
                        return@post
                    }
                    CompressionConfig.setEnvironment(environment)
                }
                newEnv["maxTokens"]?.toIntOrNull()?.let { tokens ->
                    CompressionConfig.updateConfig(maxTokens = tokens)
                }
                newEnv["recentTurns"]?.toIntOrNull()?.let { turns ->
                    CompressionConfig.updateConfig(recentTurns = turns)
                }
                newEnv["summaryTokens"]?.toIntOrNull()?.let { tokens ->
                    CompressionConfig.updateConfig(summaryTokens = tokens)
                }
                call.respond(mapOf("status" to "Configuration updated"))
            }

            get("/v1/compression/logs") {
                val count = call.request.queryParameters["count"]?.toIntOrNull() ?: 10
                val logs = CompressionLogger.getRecentLogs(count)
                call.respond(mapOf("logs" to logs))
            }

            get("/v1/compression/statistics") {
                val stats = CompressionLogger.getStatistics()
                call.respond(stats)
            }

            post("/v1/compression/validate") {
                val testData = call.receive<ValidationInput>()
                val result = CompressionValidator.runValidation(
                    originalContent = testData.originalContent,
                    compressedContent = testData.compressedContent,
                    summaryText = testData.summaryText,
                    originalTaskScore = testData.originalTaskScore,
                    compressedTaskScore = testData.compressedTaskScore
                )
                call.respond(result)
            }

            post("/v1/chat/completions") {
                val model = getCurrentModel()
                val modelHelper = getModelHelper()

                if (model == null || modelHelper == null) {
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        ErrorResponse(ErrorDetail("No model loaded"))
                    )
                    return@post
                }

                val request =
                    try {
                        call.receive<ChatCompletionRequest>()
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse(
                                ErrorDetail(
                                    message =
                                        "Invalid chat completion request: ${e.message ?: "malformed JSON"}"
                                )
                            )
                        )
                        return@post
                    }
                val requestId = "chatcmpl-${System.currentTimeMillis()}"

                if (request.stream) {
                    handleStreamingChat(call, request, requestId, model.name, modelHelper, model)
                } else {
                    handleChatCompletion(call, request, requestId, model.name, modelHelper, model)
                }
            }

            post("/v1/completions") {
                val model = getCurrentModel()
                val modelHelper = getModelHelper()

                if (model == null || modelHelper == null) {
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        ErrorResponse(ErrorDetail("No model loaded"))
                    )
                    return@post
                }

                val request = call.receive<CompletionRequest>()
                val requestId = "cmpl-${System.currentTimeMillis()}"

                handleCompletion(call, request, requestId, model.name, modelHelper, model)
            }
        }
    }

    private suspend fun handleChatCompletion(
        call: ApplicationCall,
        request: ChatCompletionRequest,
        requestId: String,
        modelName: String,
        modelHelper: LlmModelHelper,
        model: Model,
    ) {
        val compressionResult = if (CompressionConfig.isCompressionEnabled) {
            ContextCompressor.compress(modelHelper, model, request.messages)
        } else {
            val allContent = request.messages.joinToString("\n") { it.extractTextContent() }
            val originalLength = SemanticSummarizer.estimateTokenCount(allContent)
            ContextCompressor.CompressionResult(
                originalLength = originalLength,
                compressedLength = originalLength,
                compressedContent = allContent,
                summaryText = null,
                droppedRatio = 0f
            )
        }

        val prompt = compressionResult.compressedContent

        if (compressionResult.droppedRatio > 0.1f) {
            Log.d(TAG, "Context compressed: ${compressionResult.originalLength} → ${compressionResult.compressedLength} tokens (${"%.1f".format(compressionResult.droppedRatio * 100)}% dropped)")
        }

        val result = withContext(Dispatchers.IO) {
            runInference(modelHelper, model, prompt)
        }

        call.respond(
            ChatCompletionResponse(
                id = requestId,
                objectType = "chat.completion",
                created = System.currentTimeMillis() / 1000,
                model = modelName,
                choices = listOf(
                    ChatCompletionChoice(
                        message = ChatMessage(role = "assistant", content = JsonPrimitive(result))
                    )
                )
            )
        )
    }

    private suspend fun handleStreamingChat(
        call: ApplicationCall,
        request: ChatCompletionRequest,
        requestId: String,
        modelName: String,
        modelHelper: LlmModelHelper,
        model: Model,
    ) {
        val compressionResult = if (CompressionConfig.isCompressionEnabled) {
            ContextCompressor.compress(modelHelper, model, request.messages)
        } else {
            val allContent = request.messages.joinToString("\n") { it.extractTextContent() }
            val originalLength = SemanticSummarizer.estimateTokenCount(allContent)
            ContextCompressor.CompressionResult(
                originalLength = originalLength,
                compressedLength = originalLength,
                compressedContent = allContent,
                summaryText = null,
                droppedRatio = 0f
            )
        }

        val prompt = compressionResult.compressedContent

        if (compressionResult.droppedRatio > 0.1f) {
            Log.d(TAG, "Streaming: Context compressed: ${compressionResult.originalLength} → ${compressionResult.compressedLength} tokens")
        }

        call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
            val flow = streamInference(modelHelper, model, prompt)
            flow.collect { chunk ->
                val event = ChatCompletionChunk(
                    id = requestId,
                    objectType = "chat.completion.chunk",
                    created = System.currentTimeMillis() / 1000,
                    model = modelName,
                    choices = listOf(
                        ChatCompletionDelta(
                            delta = ChatDelta(content = chunk)
                        )
                    )
                )
                val data = "data: ${Json.encodeToString(event)}\n\n"
                writeFully(data.toByteArray())
                flush()
            }
            writeFully("data: [DONE]\n\n".toByteArray())
            flush()
        }
    }

    private suspend fun handleCompletion(
        call: ApplicationCall,
        request: CompletionRequest,
        requestId: String,
        modelName: String,
        modelHelper: LlmModelHelper,
        model: Model,
    ) {
        val result = withContext(Dispatchers.IO) {
            runInference(modelHelper, model, request.prompt)
        }

        call.respond(
            CompletionResponse(
                id = requestId,
                objectType = "text_completion",
                created = System.currentTimeMillis() / 1000,
                model = modelName,
                choices = listOf(
                    CompletionChoice(text = result)
                )
            )
        )
    }

    private suspend fun runInference(
        modelHelper: LlmModelHelper,
        model: Model,
        input: String,
    ): String = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        val fullResult = StringBuilder()
        modelHelper.runInference(
            model = model,
            input = input,
            resultListener = { partialResult, done, _ ->
                fullResult.append(partialResult)
                if (done && !continuation.isCompleted) {
                    continuation.resumeWith(Result.success(fullResult.toString()))
                }
            },
            cleanUpListener = {},
            onError = { error ->
                if (!continuation.isCompleted) {
                    continuation.resumeWith(Result.failure(Exception(error)))
                }
            }
        )

        continuation.invokeOnCancellation {
            modelHelper.stopResponse(model)
        }
    }

    private fun streamInference(
        modelHelper: LlmModelHelper,
        model: Model,
        input: String,
    ): Flow<String> = callbackFlow {
        modelHelper.runInference(
            model = model,
            input = input,
            resultListener = { partialResult, done, _ ->
                if (partialResult.isNotEmpty()) {
                    trySend(partialResult)
                }
                if (done) {
                    close()
                }
            },
            cleanUpListener = {},
            onError = { error ->
                trySend("Error: $error")
                close(Exception(error))
            }
        )
        awaitClose {
            modelHelper.stopResponse(model)
        }
    }

    private fun buildChatPrompt(request: ChatCompletionRequest): String {
        val lastUserMessage = request.messages.lastOrNull { it.role == "user" }?.extractTextContent()?.trim()
        if (lastUserMessage.isNullOrEmpty()) {
            Log.w(TAG, "No user message found in request")
            return ""
        }

        val toolContext = buildToolContext(request.messages)
        val prompt = if (toolContext.isNotEmpty()) {
            "$lastUserMessage\n\n[Tool context: $toolContext]"
        } else {
            lastUserMessage
        }

        Log.d(TAG, "Built prompt (${prompt.length} chars):\n$prompt")
        return prompt
    }

    private fun buildToolContext(messages: List<ChatMessage>): String {
        val toolMessages = messages.filter { it.role == "tool" }
        if (toolMessages.isEmpty()) return ""

        return toolMessages.mapNotNull { msg ->
            val content = msg.extractTextContent().trim()
            if (content.isNotEmpty()) {
                val toolId = msg.toolCallId ?: "unknown"
                "$toolId: $content"
            } else null
        }.joinToString("; ")
    }

    private fun extractToolCallsString(toolCalls: JsonElement?): String {
        if (toolCalls == null) return ""
        return try {
            when (toolCalls) {
                is JsonArray -> {
                    toolCalls.mapNotNull { item ->
                        val obj = item as? JsonObject
                        val function = obj?.get("function") as? JsonObject
                        val name = function?.get("name")?.let {
                            if (it is JsonPrimitive) it.contentOrNull else null
                        } ?: return@mapNotNull null
                        val args = function?.get("arguments")?.let {
                            if (it is JsonPrimitive) it.contentOrNull else it?.toString()
                        } ?: ""
                        "$name($args)"
                    }.joinToString("; ")
                }
                is JsonObject -> {
                    val function = toolCalls.get("function") as? JsonObject
                    val name = function?.get("name")?.let {
                        if (it is JsonPrimitive) it.contentOrNull else null
                    } ?: return ""
                    val args = function?.get("arguments")?.let {
                        if (it is JsonPrimitive) it.contentOrNull else it?.toString()
                    } ?: ""
                    "$name($args)"
                }
                else -> toolCalls.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse tool calls: ${e.message}")
            toolCalls.toString()
        }
    }

    companion object {
        private const val TAG = "ApiServer"
    }
}

@kotlinx.serialization.Serializable
data class ValidationInput(
    val originalContent: String,
    val compressedContent: String,
    val summaryText: String? = null,
    val originalTaskScore: Float? = null,
    val compressedTaskScore: Float? = null
)
