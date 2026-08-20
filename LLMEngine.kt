package com.jarvis.assistant.engine

import android.content.Context
import com.google.ai.edge.litertlm.LiteRtLm
import com.google.ai.edge.litertlm.LiteRtLmOptions
import com.jarvis.assistant.data.JarvisConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class LLMEngine(private val context: Context) {

    private var litertLm: LiteRtLm? = null
    private var isInitialized = false
    private val _outputText = MutableStateFlow("")
    val outputText: StateFlow<String> = _outputText
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating
    private val _isModelLoaded = MutableStateFlow(false)
    val isModelLoaded: StateFlow<Boolean> = _isModelLoaded
    private var generationThread: Thread? = null
    private var isClosed = false

    suspend fun initialize(modelPath: String): Result<Unit> {
        return try {
            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                return Result.failure(Exception("Model file not found: $modelPath"))
            }

            val options = LiteRtLmOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(1024)
                .setTemperature(0.7f)
                .setNumThreads(4)
                .setEnableGpu(true)
                .build()

            litertLm = LiteRtLm.createFromOptions(context, options)
            isInitialized = true
            _isModelLoaded.value = true
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun generate(
        prompt: String,
        systemPrompt: String = JarvisConfig.SYSTEM_PROMPT,
        onComplete: (() -> Unit)? = null
    ) {
        if (isClosed || litertLm == null || !isInitialized) {
            _outputText.value = "Error: Model not loaded"
            return
        }
        stopGeneration()
        _isGenerating.value = true
        _outputText.value = ""

        val fullPrompt = String.format(JarvisConfig.DEEPSEEK_CHAT_TEMPLATE, "$systemPrompt\n\nUser: $prompt")

        generationThread = Thread {
            try {
                val iterator = litertLm?.generate(fullPrompt)
                val output = StringBuilder()
                iterator?.use { iter ->
                    while (iter.hasNext() && !Thread.currentThread().isInterrupted) {
                        output.append(iter.next())
                        _outputText.value = output.toString()
                    }
                }
                _isGenerating.value = false
                onComplete?.invoke()
            } catch (e: Exception) {
                _outputText.value = "Error: ${e.message}"
                _isGenerating.value = false
            }
        }
        generationThread?.start()
    }

    fun generateWithCustomSystem(
        userInput: String,
        systemPrompt: String,
        onComplete: ((String) -> Unit)? = null
    ) {
        if (isClosed || litertLm == null || !isInitialized) {
            onComplete?.invoke("Error: Model not loaded")
            return
        }
        stopGeneration()
        _isGenerating.value = true
        _outputText.value = ""

        val fullPrompt = String.format(JarvisConfig.DEEPSEEK_CHAT_TEMPLATE, "$systemPrompt\n\nUser: $userInput")

        generationThread = Thread {
            try {
                val iterator = litertLm?.generate(fullPrompt)
                val output = StringBuilder()
                iterator?.use { iter ->
                    while (iter.hasNext() && !Thread.currentThread().isInterrupted) {
                        output.append(iter.next())
                        _outputText.value = output.toString()
                    }
                }
                _isGenerating.value = false
                onComplete?.invoke(_outputText.value)
            } catch (e: Exception) {
                _isGenerating.value = false
                onComplete?.invoke("Error: ${e.message}")
            }
        }
        generationThread?.start()
    }

    fun stopGeneration() {
        generationThread?.interrupt()
        generationThread = null
        _isGenerating.value = false
    }

    fun clearOutput() {
        _outputText.value = ""
    }

    fun close() {
        if (isClosed) return
        isClosed = true
        stopGeneration()
        litertLm?.close()
        litertLm = null
        isInitialized = false
        _isModelLoaded.value = false
    }
}
