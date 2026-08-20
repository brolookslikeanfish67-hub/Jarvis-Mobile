package com.jarvis.assistant.engine

import android.content.Context
import com.jarvis.assistant.data.JarvisConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class ModelDownloader(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress

    private val _status = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)
    val status: StateFlow<DownloadStatus> = _status

    private var isCancelled = false

    sealed class DownloadStatus {
        object Idle : DownloadStatus()
        object Connecting : DownloadStatus()
        object Downloading : DownloadStatus()
        data class Progress(val percent: Int, val downloaded: Long, val total: Long) : DownloadStatus()
        object Completed : DownloadStatus()
        data class Error(val message: String) : DownloadStatus()
    }

    suspend fun downloadModel(destFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        isCancelled = false
        try {
            // Check if file already exists and has size > 0
            if (destFile.exists() && destFile.length() > 0) {
                _status.value = DownloadStatus.Completed
                _progress.value = 100
                return@withContext Result.success(Unit)
            }

            _status.value = DownloadStatus.Connecting
            _progress.value = 0

            // Determine if we have a partial download
            val existingLength = if (destFile.exists()) destFile.length() else 0L
            val request = Request.Builder()
                .url(JarvisConfig.MODEL_DOWNLOAD_URL)
                .addHeader("Range", "bytes=$existingLength-")  // resume
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val error = when (response.code) {
                    416 -> "Cannot resume, restarting download"
                    else -> "HTTP ${response.code}: ${response.message}"
                }
                // If resume fails, retry without Range header (full download)
                if (response.code == 416) {
                    // Delete partial file and restart
                    destFile.delete()
                    return@withContext downloadFullFile(destFile)
                } else {
                    _status.value = DownloadStatus.Error(error)
                    return@withContext Result.failure(IOException(error))
                }
            }

            val totalBytes = response.body?.contentLength() ?: -1L
            if (totalBytes < 0) {
                _status.value = DownloadStatus.Error("Unable to determine file size")
                return@withContext Result.failure(IOException("Unknown content length"))
            }

            _status.value = DownloadStatus.Downloading

            // Write to file, appending if resume
            val fos = FileOutputStream(destFile, true)
            val input = response.body?.byteStream()
            if (input == null) {
                fos.close()
                _status.value = DownloadStatus.Error("No response body")
                return@withContext Result.failure(IOException("Empty response"))
            }

            val buffer = ByteArray(JarvisConfig.DOWNLOAD_BUFFER_SIZE)
            var downloaded = existingLength
            var bytesRead: Int

            try {
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    if (isCancelled) {
                        fos.close()
                        input.close()
                        _status.value = DownloadStatus.Error("Download cancelled")
                        return@withContext Result.failure(IOException("Cancelled"))
                    }
                    fos.write(buffer, 0, bytesRead)
                    downloaded += bytesRead

                    val percent = (downloaded * 100 / totalBytes).toInt()
                    _progress.value = percent.coerceIn(0, 100)
                    _status.value = DownloadStatus.Progress(percent, downloaded, totalBytes)
                }
            } catch (e: Exception) {
                fos.close()
                input.close()
                _status.value = DownloadStatus.Error(e.message ?: "Download failed")
                return@withContext Result.failure(e)
            }

            fos.close()
            input.close()

            // Verify file size
            if (destFile.length() != totalBytes) {
                _status.value = DownloadStatus.Error("File size mismatch")
                return@withContext Result.failure(IOException("Incomplete download"))
            }

            _status.value = DownloadStatus.Completed
            _progress.value = 100
            Result.success(Unit)

        } catch (e: Exception) {
            _status.value = DownloadStatus.Error(e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    private suspend fun downloadFullFile(destFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(JarvisConfig.MODEL_DOWNLOAD_URL)
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                _status.value = DownloadStatus.Error("HTTP ${response.code}")
                return@withContext Result.failure(IOException("Download failed"))
            }
            val totalBytes = response.body?.contentLength() ?: -1L
            if (totalBytes < 0) {
                _status.value = DownloadStatus.Error("Unknown file size")
                return@withContext Result.failure(IOException("Unknown size"))
            }

            _status.value = DownloadStatus.Downloading
            val fos = FileOutputStream(destFile)
            val input = response.body?.byteStream()
            if (input == null) {
                fos.close()
                _status.value = DownloadStatus.Error("No response body")
                return@withContext Result.failure(IOException("Empty"))
            }

            val buffer = ByteArray(JarvisConfig.DOWNLOAD_BUFFER_SIZE)
            var downloaded = 0L
            var bytesRead: Int

            while (input.read(buffer).also { bytesRead = it } != -1) {
                if (isCancelled) {
                    fos.close()
                    input.close()
                    _status.value = DownloadStatus.Error("Cancelled")
                    return@withContext Result.failure(IOException("Cancelled"))
                }
                fos.write(buffer, 0, bytesRead)
                downloaded += bytesRead
                val percent = (downloaded * 100 / totalBytes).toInt()
                _progress.value = percent.coerceIn(0, 100)
                _status.value = DownloadStatus.Progress(percent, downloaded, totalBytes)
            }

            fos.close()
            input.close()
            if (destFile.length() != totalBytes) {
                _status.value = DownloadStatus.Error("Size mismatch")
                return@withContext Result.failure(IOException("Incomplete"))
            }

            _status.value = DownloadStatus.Completed
            _progress.value = 100
            Result.success(Unit)
        } catch (e: Exception) {
            _status.value = DownloadStatus.Error(e.message ?: "Download error")
            Result.failure(e)
        }
    }

    fun cancel() {
        isCancelled = true
    }
}
