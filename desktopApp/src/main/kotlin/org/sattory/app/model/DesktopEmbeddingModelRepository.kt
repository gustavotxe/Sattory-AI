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
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class DesktopEmbeddingModelRepository(
    private val files: DesktopEmbeddingModelFiles,
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
        DesktopGgufFileVerifier.isValid(files.modelFile)
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
            download()
            moveIntoPlace()
            downloadState.value = ModelDownloadState.Succeeded
        } catch (error: Throwable) {
            files.partialModelFile.delete()
            if (error is kotlinx.coroutines.CancellationException) {
                downloadState.value = ModelDownloadState.Cancelled
                throw error
            }
            downloadState.value = ModelDownloadState.Failed(
                error.message ?: "Nao foi possivel preparar o modelo de embeddings",
            )
        }
    }

    private fun moveIntoPlace() {
        try {
            Files.move(
                files.partialModelFile.toPath(),
                files.modelFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: IOException) {
            Files.move(
                files.partialModelFile.toPath(),
                files.modelFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private suspend fun download() {
        val connection = (URL(EmbeddingModelArtifactSpec.DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
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
                FileOutputStream(files.partialModelFile).use { fileOutput ->
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
            if (downloaded == 0L || !DesktopGgufFileVerifier.isValid(files.partialModelFile)) {
                throw IOException("Arquivo de embeddings baixado não é um GGUF válido")
            }
            reportProgress(downloaded, total)
            downloadState.value = ModelDownloadState.Running(ModelDownloadProgress(ModelDownloadStage.Verifying))
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
        const val PROGRESS_STEP_BYTES = 2L * 1024 * 1024
        const val PROGRESS_INTERVAL_MS = 500L
        const val CONNECT_TIMEOUT_MS = 30_000
        const val READ_TIMEOUT_MS = 60_000
    }
}
