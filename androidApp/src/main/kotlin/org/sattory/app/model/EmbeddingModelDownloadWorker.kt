package org.sattory.app.model

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.system.Os
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class EmbeddingModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        setForeground(createForegroundInfo(0L, null))
        val target = EmbeddingModelFileSpec.file(applicationContext)
        val partial = EmbeddingModelFileSpec.partialFile(applicationContext)
        try {
            target.parentFile?.mkdirs()
            partial.delete()
            setProgress(stageData(STAGE_VERIFYING))
            if (GgufFileVerifier.isValid(target)) return@withContext Result.success(stageData(STAGE_READY))
            target.delete()
            download(partial)
            moveAtomically(partial, target)
            Result.success(stageData(STAGE_READY))
        } catch (error: IOException) {
            partial.delete()
            if (runAttemptCount < MAX_ATTEMPTS - 1) {
                Result.retry()
            } else {
                Result.failure(workDataOf(KEY_ERROR to (error.message ?: "Falha ao baixar o modelo de embeddings")))
            }
        } catch (error: Throwable) {
            partial.delete()
            throw error
        }
    }

    private suspend fun download(partial: File) {
        val connection = (URL(EmbeddingModelFileSpec.DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept-Encoding", "identity")
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) throw IOException("Servidor respondeu HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong.takeIf { it > 0 }
            var downloaded = 0L
            var lastReportedBytes = 0L
            var lastReportedAt = 0L
            BufferedInputStream(connection.inputStream, BUFFER_SIZE).use { input ->
                FileOutputStream(partial).use { fileOutput ->
                    BufferedOutputStream(fileOutput, BUFFER_SIZE).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloaded += count
                            val now = System.currentTimeMillis()
                            if (downloaded - lastReportedBytes >= PROGRESS_STEP_BYTES && now - lastReportedAt >= PROGRESS_INTERVAL_MS) {
                                reportProgress(downloaded, total)
                                lastReportedBytes = downloaded
                                lastReportedAt = now
                            }
                        }
                        output.flush()
                        fileOutput.fd.sync()
                    }
                }
            }
            if (downloaded == 0L || !GgufFileVerifier.isValid(partial)) {
                throw IOException("Arquivo de embeddings baixado não é um GGUF válido")
            }
            reportProgress(downloaded, total)
            setProgress(stageData(STAGE_VERIFYING))
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun reportProgress(downloaded: Long, total: Long?) {
        setProgress(
            workDataOf(
                KEY_STAGE to STAGE_DOWNLOADING,
                KEY_DOWNLOADED_BYTES to downloaded,
                KEY_TOTAL_BYTES to (total ?: -1L),
            ),
        )
        setForeground(createForegroundInfo(downloaded, total))
    }

    private fun moveAtomically(source: File, target: File) {
        try {
            Os.rename(source.absolutePath, target.absolutePath)
        } catch (error: Exception) {
            throw IOException("Nao foi possivel finalizar o arquivo do modelo de embeddings", error)
        }
    }

    private fun createForegroundInfo(downloaded: Long, total: Long?): ForegroundInfo {
        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Download do modelo de embeddings", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val progress = total?.takeIf { it > 0 }?.let { ((downloaded * 100) / it).toInt() }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Preparando RAG local")
            .setContentText(progress?.let { "Download: $it%" } ?: "Baixando embeddings...")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress ?: 0, progress == null)
            .build()
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        return ForegroundInfo(NOTIFICATION_ID, notification, serviceType)
    }

    private fun stageData(stage: String): Data = workDataOf(KEY_STAGE to stage)

    companion object {
        const val UNIQUE_WORK_NAME = "download-e5-embedding-model"
        const val KEY_STAGE = "stage"
        const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_ERROR = "error"
        const val STAGE_DOWNLOADING = "downloading"
        const val STAGE_VERIFYING = "verifying"
        const val STAGE_READY = "ready"

        private const val CHANNEL_ID = "embedding_model_download"
        private const val NOTIFICATION_ID = 4108
        private const val BUFFER_SIZE = 1024 * 1024
        private const val PROGRESS_STEP_BYTES = 2L * 1024 * 1024
        private const val PROGRESS_INTERVAL_MS = 500L
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val MAX_ATTEMPTS = 3
    }
}
