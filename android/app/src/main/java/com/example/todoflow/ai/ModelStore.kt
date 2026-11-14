package com.example.todoflow.ai

import java.io.File
import java.io.IOException
import java.io.InputStream

data class InstalledModel(val fileName: String, val sizeBytes: Long) {
    val displayName: String get() = ModelCatalog.displayName(fileName)
}

/** The models stored on this device (one `.litertlm` file each) inside the app's private storage. */
class ModelStore(val dir: File) {

    init {
        dir.mkdirs()
    }

    fun list(): List<InstalledModel> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(EXTENSION) }
            ?.sortedBy { it.name.lowercase() }
            ?.map { InstalledModel(it.name, it.length()) }
            ?: emptyList()

    fun fileFor(fileName: String): File = File(dir, sanitize(fileName))

    fun partFor(fileName: String): File = File(dir, sanitize(fileName) + PART)

    /** Bytes already downloaded for an interrupted download (0 if none). */
    fun partialBytes(fileName: String): Long = partFor(fileName).takeIf { it.exists() }?.length() ?: 0L

    fun isInstalled(fileName: String): Boolean = fileFor(fileName).isFile

    fun freeBytes(): Long = dir.usableSpace

    /** Deletes the model and any half-finished download of it. */
    fun delete(fileName: String): Boolean {
        partFor(fileName).delete()
        return fileFor(fileName).delete()
    }

    /**
     * Copies a model chosen by the user (from a file picker) into the store. The copy is written to a
     * temporary file first, so an interrupted import never leaves a broken model behind.
     */
    fun import(input: InputStream, displayFileName: String, onProgress: (Long) -> Unit = {}): InstalledModel {
        val name = sanitize(displayFileName)
        if (!name.endsWith(EXTENSION)) throw IOException("Choose a .litertlm model file")
        val part = partFor(name)
        part.delete()
        try {
            part.outputStream().use { out ->
                val buffer = ByteArray(1 shl 16)
                var total = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    total += n
                    onProgress(total)
                }
            }
            val target = fileFor(name)
            target.delete()
            if (!part.renameTo(target)) throw IOException("Could not save the model")
            return InstalledModel(target.name, target.length())
        } catch (e: Exception) {
            part.delete()
            throw e
        }
    }

    companion object {
        const val EXTENSION = ".litertlm"
        const val PART = ".part"

        /** Keeps only a plain file name: no folders, no odd characters. */
        fun sanitize(name: String): String =
            name.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().ifEmpty { "model$EXTENSION" }
    }
}
