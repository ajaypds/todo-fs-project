package com.example.todoflow.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.RandomAccessFile

/** What the UI shows for one model file that is being (or was being) downloaded. */
sealed interface DownloadState {
    data class Running(val downloaded: Long, val total: Long) : DownloadState
    data class Failed(val message: String, val downloaded: Long) : DownloadState
}

/**
 * Downloads models into the [ModelStore] over Wi-Fi or mobile data. Downloads can be interrupted
 * and resumed (HTTP range requests continue the `.part` file), and run in the app scope so they keep
 * going while the user uses other screens.
 */
class ModelDownloader(
    private val client: OkHttpClient,
    private val store: ModelStore,
    private val scope: CoroutineScope,
    private val tokenProvider: () -> String,
    private val onInstalled: (InstalledModel) -> Unit = {}
) {
    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()

    @Synchronized
    fun start(fileName: String, url: String, approxBytes: Long = 0L) {
        val name = ModelStore.sanitize(fileName)
        if (jobs[name]?.isActive == true) return
        val needed = (approxBytes - store.partialBytes(name)).coerceAtLeast(0L)
        if (needed > 0 && store.freeBytes() < needed + SAFETY_MARGIN) {
            setState(name, DownloadState.Failed("Not enough free storage (needs about ${needed / 1_000_000} MB)", store.partialBytes(name)))
            return
        }
        setState(name, DownloadState.Running(store.partialBytes(name), approxBytes))
        jobs[name] = scope.launch {
            try {
                download(name, url, approxBytes)
                clear(name)
                onInstalled(InstalledModel(name, store.fileFor(name).length()))
            } catch (e: CancellationException) {
                clear(name)
                throw e
            } catch (e: Exception) {
                setState(name, DownloadState.Failed(e.message ?: "Download failed", store.partialBytes(name)))
            }
        }
    }

    /** Stops the download; the part already downloaded is kept so it can be resumed later. */
    @Synchronized
    fun cancel(fileName: String) {
        val name = ModelStore.sanitize(fileName)
        jobs.remove(name)?.cancel()
        clear(name)
    }

    fun dismissError(fileName: String) = clear(ModelStore.sanitize(fileName))

    private fun setState(name: String, state: DownloadState) = _states.update { it + (name to state) }

    private fun clear(name: String) = _states.update { it - name }

    private suspend fun download(name: String, url: String, approxBytes: Long) {
        val part = store.partFor(name)
        var have = if (part.exists()) part.length() else 0L

        val request = Request.Builder().url(url).apply {
            if (have > 0) header("Range", "bytes=$have-")
            val token = tokenProvider()
            // The token is only for Hugging Face; OkHttp drops it again when a redirect leaves that host.
            if (token.isNotBlank() && url.contains("huggingface.co")) header("Authorization", "Bearer $token")
        }.build()

        client.newCall(request).execute().use { response ->
            when (response.code) {
                200 -> have = 0L // the server ignored the range: start over
                206 -> Unit
                416 -> { // our partial file is already the whole file (or garbage): restart cleanly
                    part.delete()
                    throw IOException("Download state was out of date, tap Download to try again")
                }
                401, 403 -> throw IOException("Access denied. Gemma models need a Hugging Face token from an account that accepted the licence")
                404 -> throw IOException("Model file not found at that address")
                else -> throw IOException("Download failed (HTTP ${response.code})")
            }
            val body = response.body ?: throw IOException("Empty response")
            val contentLength = body.contentLength()
            val total = if (contentLength >= 0) have + contentLength else approxBytes

            RandomAccessFile(part, "rw").use { file ->
                if (response.code == 200) file.setLength(0)
                file.seek(have)
                val buffer = ByteArray(1 shl 16)
                var written = have
                var lastReport = 0L
                body.byteStream().use { input ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        file.write(buffer, 0, n)
                        written += n
                        val now = System.nanoTime()
                        if (now - lastReport > REPORT_INTERVAL_NS) {
                            lastReport = now
                            setState(name, DownloadState.Running(written, total))
                        }
                    }
                }
                if (contentLength >= 0 && written != total) {
                    throw IOException("Download interrupted, tap Download to resume")
                }
            }
        }
        val target = store.fileFor(name)
        target.delete()
        if (!part.renameTo(target)) throw IOException("Could not save the model")
    }

    companion object {
        private const val SAFETY_MARGIN = 200_000_000L
        private const val REPORT_INTERVAL_NS = 250_000_000L
    }
}
