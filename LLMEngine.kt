package com.jarvis.mobile

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

class LLMEngine(private val context: Context) {

    private var llmInference: LlmInference? = null
    private val modelPath = "${context.filesDir.absolutePath}/DeepSeek-R1-Distill-Llama-8B-abliterated.Q4_K_M.gguf"

    init {
        initializeEngine()
    }

    /**
     * Initializes the local MediaPipe GenAI engine using the downloaded DeepSeek model weights.
     */
    private fun initializeEngine() {
        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            // Fallback checking the app assets folder if not copied to internal filesDir yet
            val assetsFile = File(context.assets.list("")?.find { it.contains("DeepSeek") } ?: "")
            if (assetsFile.name.isEmpty()) return
        }

        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelFilePath(modelPath)
            .setMaxTokens(2048)
            .setTemperature(0.6f)
            .build()

        llmInference = LlmInference.createFromOptions(context, options)
    }

    /**
     * Generates a streaming text response from DeepSeek and checks for intent commands.
     * @param prompt The incoming user text or voice transcription.
     * @param viewModelReference Pass the MainViewModel instance to trigger external actions.
     */
    fun generateResponseStream(prompt: String, viewModelReference: Any): Flow<String> = flow {
        val inference = llmInference ?: {
            emit("Jarvis Error: Local DeepSeek engine is not initialized.")
            return@flow
        }()

        // Format the system prompt to explicitly guide DeepSeek's capability routing
        val structuredPrompt = """
            System: You are Jarvis, a helpful assistant. If the user wants to lookup real-time information, search online, or check something on the web, you MUST start your response exactly with the phrase "search for " followed by their search query. Otherwise, answer normally.
            User: $prompt
            Assistant:
        """.trimIndent()

        val fullResponseBuilder = StringBuilder()

        try {
            // Collect the streamed response token chunks from MediaPipe
            inference.generateResponseAsync(structuredPrompt)
            
            // Note: Depending on your exact MediaPipe version, if using the callback listener,
            // accumulate the tokens here. This example assumes an iterative response listener wrapper:
            val resultStream = inference.generateResponse(structuredPrompt)
            
            for (chunk in resultStream.split(" ")) {
                val token = "$chunk "
                fullResponseBuilder.append(token)
                emit(token) // Streams text straight to the UI text display
            }

            // --- INTENT LAUNCHER HOOK ---
            val finalCleanOutput = fullResponseBuilder.toString().trim()
            val evaluationText = finalCleanOutput.lowercase()

            if (evaluationText.startsWith("search for")) {
                // Strip the command trigger to isolate the core search topic
                val webSearchTopic = finalCleanOutput
                    .replace("search for", "", ignoreCase = true)
                    .trim()

                if (webSearchTopic.isNotEmpty()) {
                    // Uses reflection or direct casting to safely trigger your MainViewModel logic
                    val method = viewModelReference.javaClass.getMethod(
                        "launchDuckDuckGoSearch", 
                        Context::class.java, 
                        String::class.java
                    )
                    method.invoke(viewModelReference, context, webSearchTopic)
                }
            }

        } catch (e: Exception) {
            emit("\n[Inference Error: ${e.localizedMessage}]")
        }
    }.flowOn(Dispatchers.Default) // Keeps heavy local AI processing completely off the UI Main Thread
}
