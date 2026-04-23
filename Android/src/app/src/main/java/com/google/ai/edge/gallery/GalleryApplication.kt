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

package com.google.ai.edge.gallery

import android.app.Application
import android.app.Activity
import android.app.AlertDialog
import android.os.Build
import android.os.Process
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.data.DataStoreRepository
import com.google.ai.edge.gallery.ui.theme.ThemeSettings
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class GalleryApplication : Application() {

  companion object {
    @Volatile var isFirebaseAvailable: Boolean = false
      private set

    fun markFirebaseUnavailable() {
      isFirebaseAvailable = false
    }

    private val crashedActivities = mutableSetOf<String>()
    private val lock = Any()

    fun isActivityCrashed(activityName: String): Boolean {
      synchronized(lock) {
        return crashedActivities.contains(activityName)
      }
    }

    fun markActivityCrashed(activityName: String) {
      synchronized(lock) {
        crashedActivities.add(activityName)
      }
    }

    fun removeActivityCrash(activityName: String) {
      synchronized(lock) {
        crashedActivities.remove(activityName)
      }
    }
  }

  @Inject lateinit var dataStoreRepository: DataStoreRepository

  override fun onCreate() {
    super.onCreate()

    // Disable Netty native transport for Android compatibility
    System.setProperty("io.netty.transport.noNative", "true")
    System.setProperty("io.netty.noUnsafe", "true")

    // Load saved theme.
    ThemeSettings.themeOverride.value = dataStoreRepository.readTheme()

    isFirebaseAvailable = FirebaseApp.initializeApp(this) != null

    // Set default uncaught exception handler to prevent silent crashes
    setupGlobalExceptionHandler()
  }

  private fun setupGlobalExceptionHandler() {
    val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      try {
        // Log the error
        android.util.Log.e("GalleryApplication", "Uncaught exception on thread ${thread.name}", throwable)

        // Show error dialog and attempt graceful exit
        showCrashErrorDialog(thread, throwable)
      } catch (e: Exception) {
        // Last resort: call the original handler
        defaultHandler?.uncaughtException(thread, throwable)
      }
    }
  }

  private fun showCrashErrorDialog(thread: Thread, throwable: Throwable?) {
    try {
      val errorMessage = throwable?.message ?: getString(R.string.app_error_unknown)
      val title = getString(R.string.app_error_title)
      val message = getString(R.string.app_error_message, errorMessage)
      val okText = getString(R.string.ok)
      val restartText = getString(R.string.restart)

      // Try to show dialog on main thread
      android.os.Handler(mainLooper).post {
        try {
          val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton(okText) { _, _ ->
              // Gracefully exit
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                finishAndRemoveTask()
              }
              Process.killProcess(Process.myPid())
              System.exit(0)
            }
            .setNegativeButton(restartText) { _, _ ->
              // Attempt to restart
              try {
                val packageManager = packageManager
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
              } catch (e: Exception) {
                // If restart fails, just exit
                Process.killProcess(Process.myPid())
                System.exit(0)
              }
            }
            .create()

          dialog.setOnDismissListener {
            try {
              Process.killProcess(Process.myPid())
              System.exit(0)
            } catch (e: Exception) {
              // Ignore
            }
          }

          dialog.show()
        } catch (e: Exception) {
          // Dialog failed, use original handler
          defaultHandler?.uncaughtException(thread, throwable)
        }
      }
    } catch (e: Exception) {
      defaultHandler?.uncaughtException(thread, throwable)
    }
  }
}
