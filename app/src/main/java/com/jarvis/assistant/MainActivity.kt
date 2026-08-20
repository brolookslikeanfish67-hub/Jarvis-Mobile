package com.jarvis.assistant

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.jarvis.assistant.data.JarvisResponse
import com.jarvis.assistant.engine.ModelDownloader
import com.jarvis.assistant.ui.theme.JarvisAssistantTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JarvisAssistantTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0A0A0A)
                ) {
                    JarvisApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun JarvisApp(
    viewModel: MainViewModel = viewModel()
) {
    // Collect UI states
    val uiState by viewModel.uiState.collectAsState()
    val isRizzMode by viewModel.isRizzMode.collectAsState()
    val isListening by viewModel.speechEngine.isListening.collectAsState()
    val transcript by viewModel.transcript.collectAsState()
    val llmOutput by viewModel.llmOutput.collectAsState()
    val isModelLoaded by viewModel.llmEngine.isModelLoaded.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val downloadStatus by viewModel.downloadStatus.collectAsState()

    // Permission handling
    val recordPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    LaunchedEffect(Unit) {
        if (!recordPermission.status.isGranted) {
            recordPermission.launchPermissionRequest()
        }
    }

    // Message history
    val messages = remember { mutableStateListOf<Message>() }

    // Add new messages when UI state changes
    LaunchedEffect(uiState) {
        when (uiState) {
            is JarvisResponse.Text -> {
                messages.add(Message.Assistant((uiState as JarvisResponse.Text).content))
            }
            is JarvisResponse.Command -> {
                val cmd = uiState as JarvisResponse.Command
                messages.add(Message.Assistant(" Launching app: ${cmd.packageName}"))
            }
            is JarvisResponse.Rizz -> {
                val rizz = uiState as JarvisResponse.Rizz
                messages.add(Message.Assistant(" ${rizz.opener}\n\n Copied to clipboard! Opening Tinder..."))
            }
            is JarvisResponse.Error -> {
                messages.add(Message.Assistant(" ${uiState.message}"))
            }
            JarvisResponse.Loading -> {
                messages.add(Message.Assistant(" Thinking..."))
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // ===== Top Bar =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isRizzMode) "🎯 Rizz Mode" else "🤖 Jarvis",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Row {
                // Rizz mode toggle
                IconButton(
                    onClick = { viewModel.toggleRizzMode() },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isRizzMode) Color(0xFFE91E63) else Color.Transparent)
                ) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = "Rizz Mode",
                        tint = if (isRizzMode) Color.White else Color.Gray
                    )
                }

                // Clear chat
                IconButton(onClick = {
                    viewModel.clearConversation()
                    messages.clear()
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear", tint = Color.Gray)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ===== Model Status / Download Progress =====
        when {
            downloadStatus is ModelDownloader.DownloadStatus.Downloading || downloadStatus is ModelDownloader.DownloadStatus.Connecting -> {
                val status = downloadStatus as? ModelDownloader.DownloadStatus.Progress
                val percent = status?.percent ?: downloadProgress
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFF6200EE)
                )
                Text(
                    text = " Downloading model: $percent% (${status?.downloaded?.div(1024*1024) ?: 0} MB / ${status?.total?.div(1024*1024) ?: "..."} MB)",
                    color = Color.Yellow,
                    fontSize = 12.sp
                )
            }
            downloadStatus is ModelDownloader.DownloadStatus.Error -> {
                Text(
                    text = " Download error: ${(downloadStatus as ModelDownloader.DownloadStatus.Error).message}",
                    color = Color.Red,
                    fontSize = 12.sp
                )
            }
            else -> {
                Text(
                    text = if (isModelLoaded) " Model ready" else " Loading DeepSeek model...",
                    color = if (isModelLoaded) Color.Green else Color.Yellow,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ===== Message List =====
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A), RoundedCornerShape(12.dp))
                .padding(12.dp),
            reverseLayout = false
        ) {
            items(messages) { message ->
                MessageBubble(message)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Live streaming output
            if (llmOutput.isNotEmpty() && uiState !is JarvisResponse.Loading) {
                item {
                    MessageBubble(Message.Assistant(llmOutput))
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ===== Rizz Input (only in Rizz Mode) =====
        if (isRizzMode) {
            var rizzInput by remember { mutableStateOf("") }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = rizzInput,
                    onValueChange = { rizzInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Describe your match...", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFE91E63),
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                Button(
                    onClick = {
                        if (rizzInput.isNotBlank()) {
                            viewModel.generateRizz(rizzInput)
                            rizzInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = "Generate Rizz")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ===== Bottom Control Bar =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            var isTextMode by remember { mutableStateOf(false) }

            // Toggle between voice and text input
            IconButton(
                onClick = { isTextMode = !isTextMode },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF2A2A2A))
            ) {
                Icon(
                    if (isTextMode) Icons.Default.Keyboard else Icons.Default.Mic,
                    contentDescription = "Toggle input mode",
                    tint = Color.White
                )
            }

            if (isTextMode) {
                // Text input mode
                var textInput by remember { mutableStateOf("") }
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type your message...", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF6200EE),
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                viewModel.processUserInput(textInput)
                                textInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE)),
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send")
                    }
                }
            } else {
                // Voice input mode
                Button(
                    onClick = {
                        if (recordPermission.status.isGranted) {
                            viewModel.processVoiceInput()
                        } else {
                            recordPermission.launchPermissionRequest()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isListening) Color.Red else Color(0xFF6200EE)
                    ),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(
                        if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isListening) "Stop" else "Listen",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isListening) "Listening..." else "Tap to Speak",
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ===== Live Transcript =====
        if (transcript.isNotEmpty() && isListening) {
            Text(
                text = " $transcript",
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ===== Message Models =====
sealed class Message {
    data class User(val text: String) : Message()
    data class Assistant(val text: String) : Message()
}

// ===== Message Bubble UI =====
@Composable
fun MessageBubble(message: Message) {
    val (text, isUser) = when (message) {
        is Message.User -> message.text to true
        is Message.Assistant -> message.text to false
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) Color(0xFF6200EE) else Color(0xFF2A2A2A),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 15.sp,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}
