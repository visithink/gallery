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
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.runtime.LlmModelHelper
import com.google.ai.edge.gallery.runtime.runtimeHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ApiServerViewModel : ViewModel() {
    private var apiServer: ApiServer? = null
    private var modelHelper: LlmModelHelper? = null
    private var currentModel: Model? = null
  private var availableModels: List<Model> = emptyList()

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
    availableModels: List<Model> = listOfNotNull(model),
    port: Int = 8000,
  ) {
    this.modelHelper = modelHelper
    this.currentModel = model
    this.availableModels = availableModels
    _port.value = port
  }

  fun refreshFromModelManager(modelManagerViewModel: com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel) {
    val uiState = modelManagerViewModel.uiState.value
    val model = uiState.selectedModel
    val isInit = uiState.isModelInitialized(model)
    val initializedModels = modelManagerViewModel.getAllModels().filter { candidate ->
      uiState.isModelInitialized(candidate)
    }
    if (model != EMPTY_MODEL && isInit) {
      initialize(
        modelHelper = model.runtimeHelper,
        model = model,
        availableModels = initializedModels,
        port = _port.value,
      )
      return
    }
    val initializedModelName: String? = uiState.modelInitializationStatus.entries
      .firstOrNull { it.value.status == com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatusType.INITIALIZED }
      ?.key
    val initializedModel: Model? = initializedModelName?.let { name ->
      modelManagerViewModel.getAllModels().find { it.name == name }
    }
    if (initializedModel != null) {
      initialize(
        modelHelper = initializedModel.runtimeHelper,
        model = initializedModel,
        availableModels = initializedModels,
        port = _port.value,
      )
    } else {
      initialize(modelHelper = null, model = null, availableModels = initializedModels, port = _port.value)
    }
  }

  private fun findTaskForModel(
    modelManagerViewModel: com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel,
    model: Model,
  ): Task? {
    return modelManagerViewModel.uiState.value.tasks.firstOrNull { task ->
      task.models.any { candidate -> candidate.name == model.name }
    }
  }

  private suspend fun ensureModelReady(
    context: Context,
    modelManagerViewModel: com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel,
  ): Boolean {
    refreshFromModelManager(modelManagerViewModel)
    if (currentModel != null && modelHelper != null && availableModels.isNotEmpty()) {
      return true
    }

    val selectedModel = modelManagerViewModel.uiState.value.selectedModel
    val candidateModel = when {
      selectedModel != EMPTY_MODEL -> selectedModel
      else -> modelManagerViewModel.getAllDownloadedModels().firstOrNull()
    }

    if (candidateModel == null) {
      _statusMessage.value = "No downloaded model available"
      return false
    }

    val task = findTaskForModel(modelManagerViewModel, candidateModel)
    if (task == null) {
      _statusMessage.value = "Cannot resolve task for model ${candidateModel.name}"
      return false
    }

    _statusMessage.value = "Preparing model ${candidateModel.name}..."
    modelManagerViewModel.initializeModel(context = context, task = task, model = candidateModel)

    val finalStatus = modelManagerViewModel.uiState
      .map { uiState -> uiState.modelInitializationStatus[candidateModel.name]?.status }
      .first { status ->
        status == com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatusType.INITIALIZED ||
          status == com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatusType.ERROR
      }

    if (finalStatus != com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatusType.INITIALIZED) {
      _statusMessage.value = "Failed to initialize model ${candidateModel.name}"
      return false
    }

    refreshFromModelManager(modelManagerViewModel)
    return currentModel != null && modelHelper != null && availableModels.isNotEmpty()
  }

  fun startServer(
    context: Context,
    modelManagerViewModel: com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel,
  ) {
        if (_isRunning.value) return

        viewModelScope.launch {
            try {
                if (!ensureModelReady(context = context, modelManagerViewModel = modelManagerViewModel)) {
                    return@launch
                }

                if (apiServer == null) {
                    apiServer = ApiServer(
                        port = _port.value,
                        getModelHelper = { modelHelper },
                        getCurrentModel = { currentModel },
                      getAvailableModels = { availableModels },
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

  fun startServer(context: Context) {
    if (_isRunning.value) return

    viewModelScope.launch {
      try {
        if (currentModel == null || modelHelper == null) {
          _statusMessage.value = "No model loaded"
          return@launch
        }

        if (apiServer == null) {
          apiServer = ApiServer(
            port = _port.value,
            getModelHelper = { modelHelper },
            getCurrentModel = { currentModel },
            getAvailableModels = { availableModels },
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
