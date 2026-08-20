package com.jarvis.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.data.JarvisConfig
import com.jarvis.assistant.data.JarvisResponse
import com.jarvis.assistant.engine.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    val llmEngine = LLMEngine(context)
    val speechEngine = SpeechEngine(context)
    val rizzEngine = RizzEngine(context, llmEngine)
    val downloader = ModelDownloader(context)

    private val _uiState = MutableStateFlow<JarvisResponse>(JarvisResponse.Idle)
    val uiState: StateFlow<JarvisResponse> = _uiState.asStateFlow()

    private val _isRizzMode = MutableStateFlow(false)
    val isRizzMode: StateFlow<Boolean> = _isRizzMode.asStateFlow()

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript

    private val _llmOutput = MutableStateFlow("")
    val llmOutput: StateFlow<String> = _llmOutput

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress

    private val _downloadStatus = MutableStateFlow<ModelDownloader.DownloadStatus>(ModelDownloader.DownloadStatus.Idle)
    val downloadStatus: StateFlow<ModelDownloader.DownloadStatus> = _downloadStatus

    init {
        speechEngine.initialize()
        checkAndDownloadModel()
    }

    private fun checkAndDownloadModel() {
        viewModelScope.launch {
            val modelFile = File(context.filesDir, JarvisConfig.MODEL_FILE_NAME)
            if (modelFile.exists() && modelFile.length() > 0) {
                // Model already present -> load it
                loadModel(modelFile.absolutePath)
            } else {
                // Start download
                _uiState.value = JarvisResponse.Text(" Downloading DeepSeek model (~1GB). Please wait...")
                val result = downloader.downloadModel(modelFile)
                if (result.isSuccess) {
                    loadModel(modelFile.absolutePath)
                } else {
                    _uiState.value = JarvisResponse.Error("Download failed: ${result.exceptionOrNull()?.message}")
                }
            }
        }

        // Observe downloader progress for UI
        viewModelScope.launch {
            downloader.progress.collect { progress ->
                _downloadProgress.value = progress
            }
        }
        viewModelScope.launch {
            downloader.status.collect { status ->
                _downloadStatus.value = status
                when (status) {
                    is ModelDownloader.DownloadStatus.Progress -> {
                        _uiState.value = JarvisResponse.Text(" Downloading: ${status.percent}% (${status.downloaded / 1024 / 1024} MB / ${status.total / 1024 / 1024} MB)")
                    }
                    is ModelDownloader.DownloadStatus.Error -> {
                        _uiState.value = JarvisResponse.Error("Download error: ${status.message}")
                    }
                    ModelDownloader.DownloadStatus.Completed -> {
                        _uiState.value = JarvisResponse.Text(" Download complete! Loading model...")
                    }
                    else -> {}
                }
            }
        }
    }

    private suspend fun loadModel(modelPath: String) {
        val result = llmEngine.initialize(modelPath)
        if (result.isSuccess) {
            _uiState.value = JarvisResponse.Text("Jarvis ready. How can I help?")
        } else {
            _uiState.value = JarvisResponse.Error("Failed to load model: ${result.exceptionOrNull()?.message}")
        }
    }

    fun processVoiceInput() {
        if (speechEngine.isListening.value) {
            speechEngine.stopListening()
            return
        }
        speechEngine.startListening { transcript ->
            _transcript.value = transcript
            if (transcript.isNotEmpty()) processUserInput(transcript)
        }
    }

    fun processUserInput(text: String) {
        if (!llmEngine.isModelLoaded.value) {
            _uiState.value = JarvisResponse.Error("Model not loaded. Please wait.")
            return
        }
        _uiState.value = JarvisResponse.Loading
        llmEngine.clearOutput()

        val systemPrompt = if (_isRizzMode.value) JarvisConfig.RIZZ_SYSTEM_PROMPT else JarvisConfig.SYSTEM_PROMPT
        llmEngine.generate(text, systemPrompt) {
            val response = llmEngine.outputText.value
            val command = CommandParser.parseLaunchCommand(response)
            if (command != null) {
                val launched = CommandParser.launchApp(context, command.packageName)
                _uiState.value = if (launched) {
                    JarvisResponse.Command(command.packageName, response)
                } else {
                    JarvisResponse.Error("App not found: ${command.packageName}")
                }
            } else {
                _uiState.value = JarvisResponse.Text(response)
            }
            _llmOutput.value = response
        }
    }

    fun generateRizz(userDescription: String) {
        if (!llmEngine.isModelLoaded.value) {
            _uiState.value = JarvisResponse.Error("Model not loaded. Please wait.")
            return
        }
        _uiState.value = JarvisResponse.Loading
        _isRizzMode.value = true
        rizzEngine.generateOpener(userDescription) { opener, success ->
            _uiState.value = if (success) {
                JarvisResponse.Rizz(opener, true)
            } else {
                JarvisResponse.Error("Failed to generate or launch Tinder")
            }
            _llmOutput.value = opener
        }
    }

    fun toggleRizzMode() {
        _isRizzMode.value = !_isRizzMode.value
        _uiState.value = if (_isRizzMode.value) {
            JarvisResponse.Text(" Rizz Mode activated! Describe your match.")
        } else {
            JarvisResponse.Text(" Jarvis mode activated.")
        }
    }

    fun clearConversation() {
        llmEngine.clearOutput()
        _llmOutput.value = ""
        _uiState.value = JarvisResponse.Idle
    }

    override fun onCleared() {
        super.onCleared()
        speechEngine.destroy()
        llmEngine.close()
    }
}
