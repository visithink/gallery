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
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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

                val request = call.receive<ChatCompletionRequest>()
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
        val lastUserMessage = request.messages.lastOrNull { it.role == "user" }?.content ?: ""

        val result = withContext(Dispatchers.IO) {
            runInference(modelHelper, model, lastUserMessage)
        }

        call.respond(
            ChatCompletionResponse(
                id = requestId,
                objectType = "chat.completion",
                created = System.currentTimeMillis() / 1000,
                model = modelName,
                choices = listOf(
                    ChatCompletionChoice(
                        message = ChatMessage(role = "assistant", content = result)
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
        val lastUserMessage = request.messages.lastOrNull { it.role == "user" }?.content ?: ""

        call.respondText(
            contentType = ContentType.Text.EventStream,
        ) {
            val flow = streamInference(modelHelper, model, lastUserMessage)
            val sb = StringBuilder()

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
                sb.append("data: ${Json.encodeToString(event)}\n\n")
            }

            sb.append("data: [DONE]\n\n")
            sb.toString()
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

    private fun runInference(
        modelHelper: LlmModelHelper,
        model: Model,
        input: String,
    ): String {
        var result = ""
        var completed = false
        val lock = Object()

        modelHelper.runInference(
            model = model,
            input = input,
            resultListener = { partialResult, done, _ ->
                result = partialResult
                if (done) {
                    synchronized(lock) {
                        completed = true
                        lock.notifyAll()
                    }
                }
            },
            cleanUpListener = {},
            onError = { error ->
                result = "Error: $error"
                synchronized(lock) {
                    completed = true
                    lock.notifyAll()
                }
            }
        )

        synchronized(lock) {
            while (!completed) {
                lock.wait()
            }
        }

        return result
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
                close()
            }
        )
    }

    companion object {
        private const val TAG = "ApiServer"
    }
}
