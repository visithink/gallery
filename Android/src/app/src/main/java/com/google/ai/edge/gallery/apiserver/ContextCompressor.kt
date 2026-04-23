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

object ContextCompressor {
    private const val TAG = "ContextCompressor"

    data class CompressionResult(
        val originalLength: Int,
        val compressedLength: Int,
        val compressedContent: String,
        val summaryText: String?,
        val droppedRatio: Float
    )

    suspend fun compress(
        modelHelper: LlmModelHelper,
        model: Model,
        messages: List<ChatMessage>
    ): CompressionResult {
        if (!CompressionConfig.isCompressionEnabled) {
            val allContent = messages.joinToString("\n") { it.extractTextContent() }
            val originalLength = SemanticSummarizer.estimateTokenCount(allContent)
            return CompressionResult(
                originalLength = originalLength,
                compressedLength = originalLength,
                compressedContent = allContent,
                summaryText = null,
                droppedRatio = 0f
            )
        }

        val systemPrompt = extractSystemPrompt(messages)
        val conversationHistory = extractConversationHistory(messages)
        val currentQuestion = extractCurrentQuestion(messages)

        val originalContent = messages.joinToString("\n") { it.extractTextContent() }
        val originalLength = SemanticSummarizer.estimateTokenCount(originalContent)

        Log.d(TAG, "Original content: $originalLength tokens")
        Log.d(TAG, "System prompt: ${SemanticSummarizer.estimateTokenCount(systemPrompt)} tokens")
        Log.d(TAG, "Current question: ${SemanticSummarizer.estimateTokenCount(currentQuestion)} tokens")

        val recentTurns = extractRecentTurns(conversationHistory, CompressionConfig.preserveRecentTurns)
        val recentTurnsTokens = SemanticSummarizer.estimateTokenCount(recentTurns)

        val availableForHistory = CompressionConfig.maxTokenLimit -
            SemanticSummarizer.estimateTokenCount(systemPrompt) -
            SemanticSummarizer.estimateTokenCount(currentQuestion) - 100

        val compressedContent: String
        val summaryText: String?

        if (recentTurnsTokens <= availableForHistory) {
            compressedContent = buildCompressedContent(systemPrompt, recentTurns, currentQuestion)
            summaryText = null
            Log.d(TAG, "No summarization needed, using recent turns directly")
        } else {
            Log.d(TAG, "Summarization needed, available tokens: $availableForHistory")
            val historyToSummarize = extractHistoryForSummary(conversationHistory, recentTurns)
            summaryText = SemanticSummarizer.summarize(modelHelper, model, historyToSummarize)

            val summarizedHistory = """
                [历史摘要]
                $summaryText

                [最近对话]
                $recentTurns
            """.trimIndent()

            compressedContent = buildCompressedContent(systemPrompt, summarizedHistory, currentQuestion)
        }

        val compressedLength = SemanticSummarizer.estimateTokenCount(compressedContent)
        val droppedRatio = if (originalLength > 0) {
            (originalLength - compressedLength).toFloat() / originalLength
        } else 0f

        CompressionLogger.log(
            originalLength = originalLength,
            compressedLength = compressedLength,
            summaryText = summaryText,
            preservedContent = compressedContent.take(500)
        )

        return CompressionResult(
            originalLength = originalLength,
            compressedLength = compressedLength,
            compressedContent = compressedContent,
            summaryText = summaryText,
            droppedRatio = droppedRatio
        )
    }

    fun extractSystemPrompt(messages: List<ChatMessage>): String {
        return messages
            .filter { it.role == "system" }
            .joinToString("\n") { it.extractTextContent() }
            .ifEmpty { "" }
    }

    fun extractConversationHistory(messages: List<ChatMessage>): String {
        val nonSystemMessages = messages.filter { it.role != "system" }
        val conversationMessages = mutableListOf<ChatMessage>()

        for (msg in nonSystemMessages) {
            if (msg.role == "user" || msg.role == "assistant") {
                conversationMessages.add(msg)
            }
        }

        return conversationMessages.joinToString("\n") { msg ->
            val roleLabel = if (msg.role == "user") "用户" else "助手"
            "$roleLabel: ${msg.extractTextContent()}"
        }
    }

    fun extractCurrentQuestion(messages: List<ChatMessage>): String {
        val lastUserMessage = messages.lastOrNull { it.role == "user" }
        return lastUserMessage?.extractTextContent()?.trim() ?: ""
    }

    fun extractRecentTurns(conversationHistory: String, numTurns: Int): String {
        val lines = conversationHistory.split("\n").filter { it.isNotBlank() }
        val turns = mutableListOf<String>()
        var currentTurn = StringBuilder()

        for (line in lines) {
            currentTurn.appendLine(line)
            if (line.startsWith("用户:")) {
                if (currentTurn.isNotEmpty()) {
                    turns.add(currentTurn.toString().trim())
                    currentTurn = StringBuilder()
                }
            }
        }

        if (currentTurn.isNotEmpty()) {
            turns.add(currentTurn.toString().trim())
        }

        val recentTurnsList = turns.takeLast(numTurns * 2)
        return recentTurnsList.joinToString("\n")
    }

    fun extractHistoryForSummary(conversationHistory: String, recentTurns: String): String {
        val historyLines = conversationHistory.split("\n").filter { it.isNotBlank() }
        val recentLines = recentTurns.split("\n").filter { it.isNotBlank() }

        val historyWithoutRecent = historyLines.dropLast(recentLines.size.coerceAtLeast(0))
        return historyWithoutRecent.joinToString("\n")
    }

    fun buildCompressedContent(
        systemPrompt: String,
        conversationHistory: String,
        currentQuestion: String
    ): String {
        return buildString {
            if (systemPrompt.isNotEmpty()) {
                appendLine("[系统提示]")
                appendLine(systemPrompt)
                appendLine()
            }
            if (conversationHistory.isNotEmpty()) {
                appendLine("[对话历史]")
                appendLine(conversationHistory)
                appendLine()
            }
            appendLine("[当前问题]")
            append(currentQuestion)
        }
    }

    fun quickCompress(messages: List<ChatMessage>): CompressionResult {
        val allContent = messages.joinToString("\n") { it.extractTextContent() }
        val originalLength = SemanticSummarizer.estimateTokenCount(allContent)

        val currentQuestion = extractCurrentQuestion(messages)
        val recentTurns = extractRecentTurns(extractConversationHistory(messages), CompressionConfig.preserveRecentTurns)
        val systemPrompt = extractSystemPrompt(messages)

        val compressedContent = buildCompressedContent(systemPrompt, recentTurns, currentQuestion)
        val compressedLength = SemanticSummarizer.estimateTokenCount(compressedContent)

        return CompressionResult(
            originalLength = originalLength,
            compressedLength = compressedLength,
            compressedContent = compressedContent,
            summaryText = null,
            droppedRatio = if (originalLength > 0) (originalLength - compressedLength).toFloat() / originalLength else 0f
        )
    }
}