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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.stringResource
import com.google.ai.edge.gallery.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiServerScreen(
    viewModel: ApiServerViewModel = viewModel(),
    modelHelper: Any?,
    currentModel: Any?,
) {
    val context = LocalContext.current
    val isRunning by viewModel.isRunning.collectAsState()
    val port by viewModel.port.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    LaunchedEffect(modelHelper, currentModel) {
        viewModel.initialize(
            modelHelper = modelHelper as? com.google.ai.edge.gallery.runtime.LlmModelHelper,
            model = currentModel as? com.google.ai.edge.gallery.data.Model,
            availableModels = listOfNotNull(currentModel as? com.google.ai.edge.gallery.data.Model),
            port = port,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.api_server)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Server Status",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    if (isRunning) "Running" else "Stopped",
                                    color = if (isRunning) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    }
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isRunning) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                    contentDescription = null,
                                    tint = if (isRunning) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    }
                                )
                            }
                        )
                    }

                    if (statusMessage.isNotEmpty()) {
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (isRunning && serverUrl.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SelectionContainer {
                                Text(
                                    text = serverUrl,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                            IconButton(
                                onClick = {
                                    val clip = ClipData.newPlainText(context.getString(R.string.api_url), serverUrl)
                                    clipboardManager.setPrimaryClip(clip)
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, "Copy URL")
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Configuration",
                        style = MaterialTheme.typography.titleMedium,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.port))
                        OutlinedTextField(
                            value = port.toString(),
                            onValueChange = { newValue ->
                                newValue.toIntOrNull()?.let { viewModel.setPort(it) }
                            },
                            modifier = Modifier.width(100.dp),
                            singleLine = true,
                            enabled = !isRunning,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { viewModel.startServer(context) },
                            enabled = !isRunning,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.start))
                        }

                        Button(
                            onClick = { viewModel.stopServer() },
                            enabled = isRunning,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.stop))
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "API Endpoints",
                        style = MaterialTheme.typography.titleMedium,
                    )

                    val baseUrl = if (serverUrl.isNotEmpty()) serverUrl else "http://<device-ip>:$port"

                    ApiEndpointRow("Chat Completion", "POST $baseUrl/v1/chat/completions")
                    ApiEndpointRow("Text Completion", "POST $baseUrl/v1/completions")
                    ApiEndpointRow("List Models", "GET $baseUrl/v1/models")
                    ApiEndpointRow("Health Check", "GET $baseUrl/health")
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Usage Example",
                        style = MaterialTheme.typography.titleMedium,
                    )

                    val exampleCode = """
curl ${if (serverUrl.isNotEmpty()) serverUrl else "http://<device-ip>:$port"}/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma",
    "messages": [{"role": "user", "content": "Hello!"}]
  }'
                    """.trimIndent()

                    SelectionContainer {
                        Text(
                            text = exampleCode,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiEndpointRow(name: String, endpoint: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
        )
        SelectionContainer {
            Text(
                text = endpoint,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
