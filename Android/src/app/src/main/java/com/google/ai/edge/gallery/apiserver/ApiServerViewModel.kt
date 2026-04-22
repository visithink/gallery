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

import android.content.Context
import android.net.wifi.WifiManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.EMPTY_MODEL
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.runtime.LlmModelHelper
import com.google.ai.edge.gallery.runtime.runtimeHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ApiServerViewModel : ViewModel() {
    private var apiServer: ApiServer? = null
    private var modelHelper: LlmModelHelper? = null
    private var currentModel: Model? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _port = MutableStateFlow(8000)
    val port: StateFlow<Int> = _port.asStateFlow()

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

private val _statusMessage = MutableStateFlow("")
  val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

  fun initialize(
    modelHelper: LlmModelHelper?,
    model: Model?,
    port: Int = 8000,
  ) {
    this.modelHelper = modelHelper
    this.currentModel = model
    _port.value = port
  }

  fun refreshFromModelManager(modelManagerViewModel: com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel) {
    val uiState = modelManagerViewModel.uiState.value
    val model = uiState.selectedModel
    val isInit = uiState.isModelInitialized(model)
    if (model != EMPTY_MODEL && isInit) {
      initialize(modelHelper = model.runtimeHelper, model = model, port = _port.value)
      return
    }
    val anyInitializedModel = uiState.modelInitializationStatus.entries
      .filter { it.value.status == com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatusType.INITIALIZED }
      .keys
      .firstOrNull()
      ?.let { modelName -> uiState.modelDownloadStatus[modelName]?.model }
    if (anyInitializedModel != null) {
      initialize(modelHelper = anyInitializedModel.runtimeHelper, model = anyInitializedModel, port = _port.value)
    }
  }

  fun startServer(context: Context) {
        if (_isRunning.value) return

        viewModelScope.launch {
            try {
                if (apiServer == null) {
                    apiServer = ApiServer(
                        port = _port.value,
                        getModelHelper = { modelHelper },
                        getCurrentModel = { currentModel },
                    )
                }

                val success = apiServer!!.start()
                if (success) {
                    _isRunning.value = true
                    _statusMessage.value = "Server running"

                    val ipAddress = getLocalIpAddress(context)
                    _serverUrl.value = "http://$ipAddress:${_port.value}"
                } else {
                    _statusMessage.value = "Failed to start server"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun stopServer() {
        apiServer?.stop()
        _isRunning.value = false
        _serverUrl.value = ""
        _statusMessage.value = "Server stopped"
    }

    fun setPort(newPort: Int) {
        if (_isRunning.value) {
            stopServer()
        }
        _port.value = newPort
    }

    private fun getLocalIpAddress(context: Context): String {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val ipAddress = wifiInfo.ipAddress
        return String.format(
            "%d.%d.%d.%d",
            ipAddress and 0xff,
            ipAddress shr 8 and 0xff,
            ipAddress shr 16 and 0xff,
            ipAddress shr 24 and 0xff
        )
    }

    override fun onCleared() {
        super.onCleared()
        apiServer?.stop()
    }
}
