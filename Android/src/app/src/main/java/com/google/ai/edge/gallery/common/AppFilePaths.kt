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

package com.google.ai.edge.gallery.common

import android.content.Context
import android.os.Environment
import java.io.File

fun resolveAppFilesDir(context: Context): File {
  val externalCandidates =
    context.getExternalFilesDirs(null).filterNotNull().sortedWith(
      compareBy<File> {
          try {
            if (Environment.isExternalStorageRemovable(it)) 1 else 0
          } catch (_: Exception) {
            1
          }
        }
        .thenBy { if (it.absolutePath.contains("/storage/emulated/")) 0 else 1 }
    )

  externalCandidates.firstOrNull { dir ->
    val state = try {
      Environment.getExternalStorageState(dir)
    } catch (_: Exception) {
      null
    }
    state == Environment.MEDIA_MOUNTED && (dir.exists() || dir.mkdirs())
  }?.let { return it }

  context.getExternalFilesDir(null)?.let { return it }
  return context.filesDir
}