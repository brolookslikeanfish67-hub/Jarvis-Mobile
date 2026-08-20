package com.jarvis.assistant.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.jarvis.assistant.data.LaunchCommand

/**
 * Parses AI output to detect and execute app launch commands.
 * Format: "COMMAND:com.package.name"
 */
object CommandParser {

    private const val COMMAND_PREFIX = "COMMAND:"

    /**
     * Scans the AI response for a COMMAND: prefix.
     * @return LaunchCommand if found, else null.
     */
    fun parseLaunchCommand(response: String): LaunchCommand? {
        val lines = response.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith(COMMAND_PREFIX)) {
                val packageName = trimmed.removePrefix(COMMAND_PREFIX).trim()
                if (packageName.isNotEmpty()) {
                    return LaunchCommand(packageName)
                }
            }
        }
        return null
    }

    /**
     * Checks if an app is installed on the device.
     */
    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Launches an app by package name.
     * @return true if successful, false otherwise.
     */
    fun launchApp(context: Context, packageName: String): Boolean {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                context.startActivity(launchIntent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
