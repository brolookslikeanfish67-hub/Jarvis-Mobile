package com.jarvis.mobile // Make sure this matches your project's actual package name

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

class JarvisIntentLauncher(private val context: Context) {

    /**
     * Processes text from the local DeepSeek engine. 
     * If the phrase contains search keywords, it launches DuckDuckGo.
     * Otherwise, it defaults to launching standard local Android apps.
     */
    fun processJarvisCommand(rawModelOutput: String) {
        val command = rawModelOutput.trim().lowercase()

        // Check if the user is asking to look something up online
        if (command.startsWith("search for") || command.startsWith("google") || command.startsWith("duckduckgo")) {
            // Clean up the text to extract just the core search query
            val cleanQuery = rawModelOutput
                .replace("search for", "", ignoreCase = true)
                .replace("google", "", ignoreCase = true)
                .replace("duckduckgo", "", ignoreCase = true)
                .trim()

            if (cleanQuery.isNotEmpty()) {
                launchDuckDuckGoSearch(cleanQuery)
            } else {
                Toast.makeText(context, "Jarvis: What would you like me to search for?", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Fallback to the default Jarvis-Mobile local app launcher behavior
            launchLocalAndroidApp(rawModelOutput)
        }
    }

    /**
     * Formats the query text and safely opens the external web browser to DuckDuckGo results.
     */
    private fun launchDuckDuckGoSearch(searchQuery: String) {
        try {
            // Encode spaces and special characters for a clean web URL structure
            val encodedQuery = java.net.URLEncoder.encode(searchQuery, "UTF-8")
            val webUri = Uri.parse("https://duckduckgo.com")

            // Create a general VIEW action intent to open an external web browser
            val searchIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                // Ensuring the task opens neatly on top of Jarvis
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(searchIntent)

        } catch (e: Exception) {
            Toast.makeText(context, "Jarvis failed to launch search: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Baseline Jarvis-Mobile placeholder method for switching apps locally.
     */
    private fun launchLocalAndroidApp(appName: String) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(appName)
        if (launchIntent != null) {
            context.startActivity(launchIntent)
        } else {
            // If it's not a clear app package, treat it as general creative text output
            Toast.makeText(context, "Processing text: $appName", Toast.LENGTH_SHORT).show()
        }
    }
}
