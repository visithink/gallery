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

object CompressionConfig {
    private const val TAG = "CompressionConfig"

    enum class Environment {
        DEVELOPMENT,
        PRODUCTION
    }

    var currentEnvironment: Environment = Environment.DEVELOPMENT
        private set

    var isCompressionEnabled: Boolean = true
        private set

    var maxTokenLimit: Int = 2048
        private set

    var preserveRecentTurns: Int = 3
        private set

    var summaryMaxTokens: Int = 200
        private set

    var enableDetailedLogging: Boolean = true
        private set

    fun setEnvironment(env: Environment) {
        currentEnvironment = env
        when (env) {
            Environment.DEVELOPMENT -> {
                isCompressionEnabled = true
                enableDetailedLogging = true
                maxTokenLimit = 2048
                preserveRecentTurns = 3
                summaryMaxTokens = 200
                Log.d(TAG, "Environment set to DEVELOPMENT: compression enabled, detailed logging ON")
            }
            Environment.PRODUCTION -> {
                isCompressionEnabled = true
                enableDetailedLogging = false
                maxTokenLimit = 2048
                preserveRecentTurns = 3
                summaryMaxTokens = 200
                Log.d(TAG, "Environment set to PRODUCTION: compression enabled, detailed logging OFF")
            }
        }
    }

    fun updateConfig(
        compressionEnabled: Boolean = isCompressionEnabled,
        maxTokens: Int = maxTokenLimit,
        recentTurns: Int = preserveRecentTurns,
        summaryTokens: Int = summaryMaxTokens,
        detailedLogging: Boolean = enableDetailedLogging
    ) {
        isCompressionEnabled = compressionEnabled
        maxTokenLimit = maxTokens.coerceIn(512, 8192)
        preserveRecentTurns = recentTurns.coerceIn(1, 10)
        summaryMaxTokens = summaryTokens.coerceIn(50, 2048)
        enableDetailedLogging = detailedLogging
        Log.d(TAG, "Config updated: enabled=$compressionEnabled, maxTokens=$maxTokenLimit, recentTurns=$preserveRecentTurns, summaryTokens=$summaryMaxTokens")
    }

    init {
        setEnvironment(Environment.DEVELOPMENT)
    }
}