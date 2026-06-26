package org.sattory.app.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class DesktopModelRepository(
    private val files: DesktopModelFiles,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ModelRepository {
    private val downloadState = MutableStateFlow<ModelDownloadState>(ModelDownloadState.NotScheduled)
    private var downloadJob: Job? = null

    override fun observeDownloadState(): Flow<ModelDownloadState> = downloadState.asStateFlow()

    override fun requestDownload(policy: ModelDownloadRequestPolicy) {
        if (policy == ModelDownloadRequestPolicy.Keep && downloadJob?.isActive == true) return
        if (policy == ModelDownloadRequestPolicy.Replace) downloadJob?.cancel()
        downloadJob = scope.launch { prepareModel() }
    }

    override suspend fun isModelAvailable(): Boolean = withContext(Dispatchers.IO) {
        val file = files.modelFile
        file.isFile && DesktopSha256.of(file) == ModelArtifactSpec.SHA256
    }

    private suspend fun prepareModel() = withContext(Dispatchers.IO) {
        try {
            files.modelDirectory.mkdirs()
            downloadState.value = ModelDownloadState.Running(ModelDownloadProgress(ModelDownloadStage.Verifying))

            if (isModelAvailable()) {
                downloadState.value = ModelDownloadState.Succeeded
                return@withContext
            }

            files.modelFile.delete()
            files.partialModelFile.delete()

            downloadAndVerify()
            moveIntoPlace(files.partialModelFile, files.modelFile)
            downloadState.value = ModelDownloadState.Succeeded
        } catch (error: Throwable) {
            files.partialModelFile.delete()
            if (error is kotlinx.coroutines.CancellationException) {
                downloadState.value = ModelDownloadState.Cancelled
                throw error
            }
            downloadState.value = ModelDownloadState.Failed(error.message ?: "Nao foi possivel preparar o modelo local")
        }
    }

    private fun moveIntoPlace(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: IOException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private suspend fun downloadAndVerify() {
        val connection = (URL(ModelArtifactSpec.DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
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
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            var lastReportedBytes = 0L
            var lastReportedAt = 0L

            BufferedInputStream(connection.inputStream, BUFFER_SIZE).use { input ->
                FileOutputStream(files.partialModelFile).use { fileOutput ->
                    BufferedOutputStream(fileOutput, BUFFER_SIZE).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
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

            reportProgress(downloaded, total)
            downloadState.value = ModelDownloadState.Running(ModelDownloadProgress(ModelDownloadStage.Verifying))
            val actualHash = DesktopSha256.toHex(digest.digest())
            if (actualHash != ModelArtifactSpec.SHA256) throw IOException("SHA-256 invalido; o download sera descartado")
        } finally {
            connection.disconnect()
        }
    }

    private fun reportProgress(downloaded: Long, total: Long?) {
        downloadState.value = ModelDownloadState.Running(
            ModelDownloadProgress(ModelDownloadStage.Downloading, downloaded, total),
        )
    }

    private companion object {
        const val BUFFER_SIZE = 1024 * 1024
        const val PROGRESS_STEP_BYTES = 4L * 1024 * 1024
        const val PROGRESS_INTERVAL_MS = 500L
        const val CONNECT_TIMEOUT_MS = 30_000
        const val READ_TIMEOUT_MS = 60_000
    }
}
