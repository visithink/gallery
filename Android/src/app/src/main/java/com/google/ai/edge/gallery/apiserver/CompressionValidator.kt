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
import kotlinx.serialization.Serializable

object CompressionValidator {
    private const val TAG = "CompressionValidator"

    @Serializable
    data class ValidationResult(
        val informationRetentionRate: Float,
        val downstreamTaskMetricDrop: Float,
        val passed: Boolean,
        val details: String
    )

    @Serializable
    data class TestCase(
        val name: String,
        val originalContent: String,
        val expectedKeyInfo: List<String>,
        val compressedContent: String,
        val summaryText: String?
    )

    private val testCases = mutableListOf<TestCase>()

    fun addTestCase(testCase: TestCase) {
        testCases.add(testCase)
        if (testCases.size > MAX_TEST_CASES) {
            testCases.removeAt(0)
        }
    }

    fun validateInformationRetention(
        originalContent: String,
        compressedContent: String,
        summaryText: String?
    ): Float {
        val originalKeyInfos = extractKeyInformation(originalContent)
        val compressedKeyInfos = extractKeyInformation(compressedContent)
        val summaryKeyInfos = summaryText?.let { extractKeyInformation(it) } ?: emptyList()

        val allRetrievedInfos = (compressedKeyInfos + summaryKeyInfos).distinct()

        if (originalKeyInfos.isEmpty()) {
            return 1.0f
        }

        var retainedCount = 0
        for (originalInfo in originalKeyInfos) {
            for (retrievedInfo in allRetrievedInfos) {
                if (similarity(originalInfo, retrievedInfo) >= SIMILARITY_THRESHOLD) {
                    retainedCount++
                    break
                }
            }
        }

        return retainedCount.toFloat() / originalKeyInfos.size.toFloat()
    }

    fun validateDownstreamTaskMetric(
        originalScore: Float,
        compressedScore: Float
    ): Float {
        return originalScore - compressedScore
    }

    fun runValidation(
        originalContent: String,
        compressedContent: String,
        summaryText: String?,
        originalTaskScore: Float? = null,
        compressedTaskScore: Float? = null
    ): ValidationResult {
        val retentionRate = validateInformationRetention(originalContent, compressedContent, summaryText)
        val retentionPercent = retentionRate * 100

        var metricDrop = 0f
        if (originalTaskScore != null && compressedTaskScore != null) {
            metricDrop = validateDownstreamTaskMetric(originalTaskScore, compressedTaskScore)
        }

        val retentionPassed = retentionPercent >= MIN_RETENTION_RATE
        val metricDropPassed = metricDrop <= MAX_METRIC_DROP

        val passed = retentionPassed && metricDropPassed

        val details = buildString {
            appendLine("=== Compression Validation Results ===")
            appendLine("Information Retention Rate: ${"%.2f".format(retentionPercent)}%")
            appendLine("  Required: >= ${MIN_RETENTION_RATE}%")
            appendLine("  Status: ${if (retentionPassed) "PASSED" else "FAILED"}")
            if (originalTaskScore != null && compressedTaskScore != null) {
                appendLine("Downstream Task Metric Drop: ${"%.2f".format(metricDrop)}")
                appendLine("  Required: <= ${MAX_METRIC_DROP}")
                appendLine("  Status: ${if (metricDropPassed) "PASSED" else "FAILED"}")
            }
            appendLine("Overall: ${if (passed) "PASSED" else "FAILED"}")
            appendLine("=====================================")
        }

        Log.d(TAG, details)

        return ValidationResult(
            informationRetentionRate = retentionPercent,
            downstreamTaskMetricDrop = metricDrop,
            passed = passed,
            details = details
        )
    }

    private fun extractKeyInformation(text: String): List<String> {
        val keyPatterns = listOf(
            Regex("""(用户|客人|客户)\s*(希望|想要|需要|要求)[:：]\s*(.+)"""),
            Regex("""(重要|关键|必须|一定|绝对|不能)[:：]\s*(.+)"""),
            Regex("""(之前|曾经|过去|已经|之前)[:：]\s*(.+)"""),
            Regex("""(记住|别忘|不要忘记|一定要)[:：]\s*(.+)"""),
            Regex("""(偏好|喜欢|讨厌|不想|不喜欢)[:：]\s*(.+)"""),
            Regex("""(\d+)\s*(个|次|条|杯|份|元|美元|人民币)"""),
        )

        val keyInfos = mutableListOf<String>()
        for (pattern in keyPatterns) {
            keyInfos.addAll(pattern.findAll(text).map { it.value })
        }

        return keyInfos.distinct()
    }

    private fun similarity(str1: String, str2: String): Float {
        val s1 = str1.lowercase().replace(Regex("""\s+"""), "")
        val s2 = str2.lowercase().replace(Regex("""\s+"""), "")

        if (s1 == s2) return 1.0f
        if (s1.isEmpty() || s2.isEmpty()) return 0.0f

        val distance = levenshteinDistance(s1, s2)
        val maxLen = maxOf(s1.length, s2.length)
        return 1.0f - (distance.toFloat() / maxLen.toFloat())
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }

        return dp[s1.length][s2.length]
    }

    fun getValidationStatistics(): ValidationStatistics {
        if (testCases.isEmpty()) {
            return ValidationStatistics(0, 0f, 0f, 0)
        }

        val avgRetention = testCases.map { case ->
            validateInformationRetention(case.originalContent, case.compressedContent, case.summaryText)
        }.average().toFloat() * 100

        return ValidationStatistics(
            totalTests = testCases.size,
            avgRetentionRate = avgRetention,
            avgMetricDrop = 0f,
            passedTests = 0
        )
    }

    fun clearTestCases() {
        testCases.clear()
    }

    private const val MIN_RETENTION_RATE = 95f
    private const val MAX_METRIC_DROP = 2f
    private const val SIMILARITY_THRESHOLD = 0.8f
    private const val MAX_TEST_CASES = 50
}

data class ValidationStatistics(
    val totalTests: Int,
    val avgRetentionRate: Float,
    val avgMetricDrop: Float,
    val passedTests: Int
)