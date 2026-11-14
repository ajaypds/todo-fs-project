package com.example.todoflow.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException

class ModelStoreAndDownloaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun store() = ModelStore(tmp.newFolder("models"))

    @Test
    fun importCopiesTheFileAndListsIt() {
        val store = store()
        val data = ByteArray(200_000) { (it % 251).toByte() }
        val progress = mutableListOf<Long>()
        val model = store.import(ByteArrayInputStream(data), "My-Model.litertlm") { progress += it }

        assertEquals("My-Model.litertlm", model.fileName)
        assertEquals(data.size.toLong(), model.sizeBytes)
        assertEquals(listOf("My-Model.litertlm"), store.list().map { it.fileName })
        assertEquals(data.size.toLong(), progress.last())
        assertTrue(store.fileFor("My-Model.litertlm").readBytes().contentEquals(data))
    }

    @Test
    fun importRejectsOtherFileTypesAndPathTricks() {
        val store = store()
        try {
            store.import(ByteArrayInputStream(ByteArray(10)), "notes.txt")
            fail("a .txt file must be rejected")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains(".litertlm"))
        }
        // A path in the name never escapes the models folder
        val model = store.import(ByteArrayInputStream(ByteArray(10)), "../../evil.litertlm")
        assertEquals("evil.litertlm", model.fileName)
        assertEquals(store.dir, store.fileFor(model.fileName).parentFile)
    }

    @Test
    fun aFailedImportLeavesNothingBehind() {
        val store = store()
        val broken = object : java.io.InputStream() {
            var n = 0
            override fun read(): Int = if (n++ < 5) 1 else throw IOException("disk unplugged")
        }
        try {
            store.import(broken, "x.litertlm")
            fail()
        } catch (_: IOException) {
        }
        assertTrue(store.list().isEmpty())
        assertEquals(0L, store.partialBytes("x.litertlm"))
    }

    @Test
    fun deleteRemovesTheModelAndItsPartialDownload() {
        val store = store()
        store.import(ByteArrayInputStream(ByteArray(10)), "a.litertlm")
        store.partFor("a.litertlm").writeBytes(ByteArray(5))
        assertTrue(store.delete("a.litertlm"))
        assertTrue(store.list().isEmpty())
        assertFalse(store.partFor("a.litertlm").exists())
    }

    // ---- downloader ----------------------------------------------------------------------------------

    private class FakeServer(val body: ByteArray) : Interceptor {
        val requests = mutableListOf<okhttp3.Request>()
        var failWith: Int? = null
        var truncateAt: Int? = null

        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            requests += req
            failWith?.let { return reply(req, it, ByteArray(0)) }
            val range = req.header("Range")?.removePrefix("bytes=")?.removeSuffix("-")?.toIntOrNull()
            val slice = if (range != null) body.copyOfRange(range, body.size) else body
            val sent = truncateAt?.let { slice.copyOf(minOf(it, slice.size)) } ?: slice
            // The announced length is the full slice; the body is cut short when truncateAt is set
            val body = object : ResponseBody() {
                override fun contentType() = "application/octet-stream".toMediaType()
                override fun contentLength() = slice.size.toLong()
                override fun source() = Buffer().write(sent)
            }
            return Response.Builder().request(req).protocol(Protocol.HTTP_1_1)
                .code(if (range != null) 206 else 200).message("x").body(body).build()
        }

        private fun reply(req: okhttp3.Request, code: Int, data: ByteArray) = Response.Builder()
            .request(req).protocol(Protocol.HTTP_1_1).code(code).message("x")
            .body(data.toResponseBody("application/octet-stream".toMediaType())).build()
    }

    private fun downloader(store: ModelStore, server: FakeServer, token: String = "") = ModelDownloader(
        client = OkHttpClient.Builder().addInterceptor(server).build(),
        store = store,
        // Unconfined: the whole (blocking) download runs inside start(), so tests need no waiting
        scope = CoroutineScope(Job() + Dispatchers.Unconfined),
        tokenProvider = { token }
    )

    @Test
    fun downloadsAModelAndInstallsIt() {
        val store = store()
        val data = ByteArray(300_000) { it.toByte() }
        val d = downloader(store, FakeServer(data))
        d.start("m.litertlm", "https://example.com/m.litertlm", approxBytes = data.size.toLong())

        assertTrue(store.fileFor("m.litertlm").readBytes().contentEquals(data))
        assertFalse(store.partFor("m.litertlm").exists())
        assertNull(d.states.value["m.litertlm"])
    }

    @Test
    fun anInterruptedDownloadKeepsItsPartAndResumesWithARangeRequest() {
        val store = store()
        val data = ByteArray(300_000) { (it * 7).toByte() }
        val server = FakeServer(data).apply { truncateAt = 100_000 }
        val d = downloader(store, server)

        d.start("m.litertlm", "https://example.com/m.litertlm")
        val failed = d.states.value["m.litertlm"] as DownloadState.Failed
        assertEquals(100_000L, failed.downloaded)
        assertEquals(100_000L, store.partialBytes("m.litertlm"))
        assertFalse(store.isInstalled("m.litertlm"))

        server.truncateAt = null
        d.start("m.litertlm", "https://example.com/m.litertlm")
        assertEquals("bytes=100000-", server.requests.last().header("Range"))
        assertTrue(store.fileFor("m.litertlm").readBytes().contentEquals(data))
    }

    @Test
    fun explainsAccessDeniedAndMissingFiles() {
        val store = store()
        val server = FakeServer(ByteArray(1))
        val d = downloader(store, server)

        server.failWith = 403
        d.start("g.litertlm", "https://huggingface.co/x/g.litertlm")
        assertTrue((d.states.value["g.litertlm"] as DownloadState.Failed).message.contains("Hugging Face token"))

        server.failWith = 404
        d.start("g.litertlm", "https://huggingface.co/x/g.litertlm")
        assertTrue((d.states.value["g.litertlm"] as DownloadState.Failed).message.contains("not found"))
    }

    @Test
    fun theTokenIsOnlySentToHuggingFace() {
        val store = store()
        val server = FakeServer(ByteArray(10))
        val d = downloader(store, server, token = "hf_secret")
        d.start("a.litertlm", "https://huggingface.co/x/a.litertlm")
        d.start("b.litertlm", "https://example.com/b.litertlm")

        assertEquals("Bearer hf_secret", server.requests[0].header("Authorization"))
        assertNull(server.requests[1].header("Authorization"))
    }

    @Test
    fun refusesToStartWithoutEnoughFreeSpace() {
        val store = store()
        val d = downloader(store, FakeServer(ByteArray(1)))
        d.start("huge.litertlm", "https://example.com/huge.litertlm", approxBytes = Long.MAX_VALUE / 2)
        assertTrue((d.states.value["huge.litertlm"] as DownloadState.Failed).message.contains("free storage"))
    }
}
