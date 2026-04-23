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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SemanticSummarizer {
    private const val TAG = "SemanticSummarizer"

    suspend fun summarize(
        modelHelper: LlmModelHelper,
        model: Model,
        conversationHistory: String,
        maxTokens: Int = CompressionConfig.summaryMaxTokens
    ): String = withContext(Dispatchers.IO) {
        if (conversationHistory.isBlank()) {
            return@withContext ""
        }

        val estimatedTokens = estimateTokenCount(conversationHistory)
        Log.d(TAG, "Summarizing $estimatedTokens tokens, max output: $maxTokens tokens")

        val summaryPrompt = buildSummaryPrompt(conversationHistory, maxTokens)

        val summary = try {
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                val fullResult = StringBuilder()
                modelHelper.runInference(
                    model = model,
                    input = summaryPrompt,
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
        } catch (e: Exception) {
            Log.e(TAG, "Summary generation exception: ${e.message}")
            ""
        }

        val trimmedSummary = summary.trim()
        Log.d(TAG, "Generated summary (${trimmedSummary.length} chars)")

        if (trimmedSummary.length > maxTokens * 4) {
            return@withContext trimmedSummary.take(maxTokens * 4)
        }

        trimmedSummary
    }

    private fun buildSummaryPrompt(history: String, maxTokens: Int): String {
        return """
请对以下对话历史进行关键信息摘要，生成不超过${maxTokens}个token的简洁摘要。

要求：
1. 提取关键事实、决策、用户偏好和重要上下文
2. 保留可能影响后续回答的重要信息
3. 使用简洁的语言，避免冗余
4. 摘要应该能够代替原始对话提供足够的上下文

对话历史：
$history

请直接输出摘要，不要包含"摘要："等前缀。
        """.trimIndent()
    }

    fun extractKeyInformation(conversationHistory: String): List<String> {
        val keyInfoPatterns = listOf(
            Regex("""用户\s*(希望|想要|需要|要求|要求|说|表示|提到)[:：]\s*(.+)"""),
            Regex("""(重要|关键|必须|一定|绝对)[:：]\s*(.+)"""),
            Regex("""(之前|曾经|过去|已经)[:：]\s*(.+)"""),
            Regex("""(记住|记住|别忘|不要忘记)[:：]\s*(.+)"""),
        )

        val keyInfos = mutableListOf<String>()

        for (pattern in keyInfoPatterns) {
            keyInfos.addAll(pattern.findAll(conversationHistory).map { it.value })
        }

        return keyInfos.distinct().take(10)
    }

    fun estimateTokenCount(text: String): Int {
        return (text.length / 4).coerceAtLeast(1)
    }

    fun truncateToTokenLimit(text: String, maxTokens: Int): String {
        val estimatedTokens = estimateTokenCount(text)
        if (estimatedTokens <= maxTokens) {
            return text
        }

        val charsToKeep = maxTokens * 4
        return text.take(charsToKeep)
    }
}