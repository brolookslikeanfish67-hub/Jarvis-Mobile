package com.jarvis.assistant.engine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.jarvis.assistant.data.JarvisConfig

/**
 * Rizz Engine – generates witty dating openers using the DeepSeek model.
 * 
 * Workflow:
 * 1. Feeds user description + special system prompt to LLM.
 * 2. Cleans the response (removes <think> tags).
 * 3. Copies the opener to the Android clipboard.
 * 4. Launches Tinder (com.cardify.tinder).
 */
class RizzEngine(
    private val context: Context,
    private val llmEngine: LLMEngine
) {

    companion object {
        private const val TINDER_PACKAGE = "com.cardify.tinder"
    }

    private val clipboardManager: ClipboardManager by lazy {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    /**
     * Generates an opener, copies it, and launches Tinder.
     * @param userDescription Description of the match or situation.
     * @param onResult Callback (openerText, successFlag).
     */
    fun generateOpener(
        userDescription: String,
        onResult: (String, Boolean) -> Unit
    ) {
        llmEngine.generateWithCustomSystem(
            userInput = userDescription,
            systemPrompt = JarvisConfig.RIZZ_SYSTEM_PROMPT
        ) { generatedText ->
            // Handle errors or fallback
            val rawOpener = if (generatedText.startsWith("Error:")) {
                generateFallbackOpener(userDescription)
            } else {
                // DeepSeek sometimes wraps output in <think> tags – clean it
                cleanResponse(generatedText).trim()
            }

            // 1. Copy to clipboard
            val copied = copyToClipboard(rawOpener)

            // 2. Launch Tinder
            val launched = CommandParser.launchApp(context, TINDER_PACKAGE)

            // Success only if both copy and launch succeeded
            onResult(rawOpener, copied && launched)
        }
    }

    /**
     * Generates an opener without launching Tinder (just copies).
     */
    fun generateOnly(
        userDescription: String,
        onResult: (String) -> Unit
    ) {
        llmEngine.generateWithCustomSystem(
            userInput = userDescription,
            systemPrompt = JarvisConfig.RIZZ_SYSTEM_PROMPT
        ) { generatedText ->
            val opener = if (generatedText.startsWith("Error:")) {
                generateFallbackOpener(userDescription)
            } else {
                cleanResponse(generatedText).trim()
            }
            copyToClipboard(opener)
            onResult(opener)
        }
    }

    /**
     * Copies text to the system clipboard.
     */
    private fun copyToClipboard(text: String): Boolean {
        return try {
            val clip = ClipData.newPlainText("Jarvis Rizz", text)
            clipboardManager.setPrimaryClip(clip)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Removes DeepSeek's <think> reasoning blocks from the response.
     */
    private fun cleanResponse(text: String): String {
        val thinkPattern = Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL)
        return thinkPattern.replace(text, "").trim()
    }

    /**
     * Hardcoded fallback openers if the model fails.
     */
    private fun generateFallbackOpener(description: String): String {
        val openers = listOf(
            "you look like if an dog was an cat block me now i dont care.",
            "Okay, I have to ask: what's the most spontaneous thing you've done this month?",
            "You look like you have the best playlist in the room. Prove me wrong?",
            "Two truths and a lie — go. I'll guess which is the lie.",
            "If we were in a rom-com, this would be the meet-cute. Just saying."
        )
        return openers.random()
    }
}
