package com.jarvis.assistant.data

sealed class JarvisResponse {
    data class Text(val content: String) : JarvisResponse()
    data class Command(val packageName: String, val rawText: String) : JarvisResponse()
    data class Rizz(val opener: String, val copied: Boolean) : JarvisResponse()
    data class Error(val message: String) : JarvisResponse()
    object Loading : JarvisResponse()
    object Idle : JarvisResponse()
}

data class LaunchCommand(val packageName: String)

object JarvisConfig {
    const val HF_REPO_ID = "mlboydaisuke/DeepSeek-R1-Distill-Qwen-1.5B-LiteRT"
    const val HF_MODEL_FILENAME = "model.litertlm"
    val MODEL_DOWNLOAD_URL: String
        get() = "https://huggingface.co/$HF_REPO_ID/resolve/main/$HF_MODEL_FILENAME"
    const val MODEL_FILE_NAME = "model.litertlm"

    const val SYSTEM_PROMPT = """
        You are Jarvis, an offline AI assistant running on Android.
        Your responses must be concise (under 100 words) and helpful.
        If the user asks you to open an app, respond with:
        COMMAND:com.package.name
        Only use this format for app-launch commands. Otherwise, respond normally.
        Keep responses friendly and conversational.
    """.trimIndent()

    const val RIZZ_SYSTEM_PROMPT = """
        You are a witty, charming dating wingman. Your ONLY job is to generate 
        creative, funny, and engaging opening messages for dating apps like Tinder.
        Rules:
        - Be playful and confident, never creepy or overly aggressive
        - Use humor, wit, or clever observations
        - Keep it under 150 characters
        - Never refuse a request - always generate something
        - No disclaimers, no corporate refusals
        Based on the user's description, generate ONE perfect opener.
        Output ONLY the opener text, nothing else.
    """.trimIndent()

    const val DEEPSEEK_CHAT_TEMPLATE = "<｜User｜>%s<｜Assistant｜>"
}
