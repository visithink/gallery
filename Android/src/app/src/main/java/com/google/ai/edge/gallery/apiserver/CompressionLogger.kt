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

data class CompressionLog(
    val originalLength: Int,
    val compressedLength: Int,
    val droppedRatio: Float,
    val summaryText: String?,
    val preservedContent: String,
    val timestamp: Long = System.currentTimeMillis()
)

object CompressionLogger {
    private const val TAG = "CompressionLogger"
    private val logs = mutableListOf<CompressionLog>()
    private const val MAX_LOG_SIZE = 100

    fun log(
        originalLength: Int,
        compressedLength: Int,
        summaryText: String? = null,
        preservedContent: String
    ) {
        val droppedRatio = if (originalLength > 0) {
            (originalLength - compressedLength).toFloat() / originalLength
        } else 0f

        val compressionLog = CompressionLog(
            originalLength = originalLength,
            compressedLength = compressedLength,
            droppedRatio = droppedRatio,
            summaryText = summaryText,
            preservedContent = preservedContent
        )

        logs.add(compressionLog)
        if (logs.size > MAX_LOG_SIZE) {
            logs.removeAt(0)
        }

        if (CompressionConfig.enableDetailedLogging) {
            Log.d(TAG, buildLogMessage(compressionLog))
        }
    }

    private fun buildLogMessage(log: CompressionLog): String {
        return buildString {
            appendLine("=== Context Compression Log ===")
            appendLine("Original length: ${log.originalLength} tokens")
            appendLine("Compressed length: ${log.compressedLength} tokens")
            appendLine("Dropped ratio: ${"%.2f".format(log.droppedRatio * 100)}%")
            if (log.summaryText != null) {
                appendLine("Summary (${log.summaryText.length} chars): ${log.summaryText.take(500)}...")
            }
            appendLine("Preserved content preview: ${log.preservedContent.take(200)}...")
            appendLine("Timestamp: ${log.timestamp}")
            appendLine("==============================")
        }
    }

    fun getRecentLogs(count: Int = 10): List<CompressionLog> {
        return logs.takeLast(count).reversed()
    }

    fun getStatistics(): CompressionStatistics {
        if (logs.isEmpty()) {
            return CompressionStatistics(0, 0.0, 0.0, 0.0)
        }
        val avgDroppedRatio = logs.map { it.droppedRatio }.average()
        val avgOriginal = logs.map { it.originalLength }.average()
        val avgCompressed = logs.map { it.compressedLength }.average()
        return CompressionStatistics(
            totalCompressions = logs.size,
            avgOriginalLength = avgOriginal,
            avgCompressedLength = avgCompressed,
            avgDroppedRatio = avgDroppedRatio
        )
    }

    fun clearLogs() {
        logs.clear()
        Log.d(TAG, "Compression logs cleared")
    }
}

data class CompressionStatistics(
    val totalCompressions: Int,
    val avgOriginalLength: Double,
    val avgCompressedLength: Double,
    val avgDroppedRatio: Double
)