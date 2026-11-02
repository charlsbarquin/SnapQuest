package com.charlsemilbarquin.frameandfact.feature.camera

import java.io.File
import java.io.IOException
import java.util.UUID

/** Stores short-lived captures only in a dedicated app-private cache subdirectory. */
class CameraPhotoStore(cacheDirectory: File) {
    private val cacheRoot = cacheDirectory.canonicalFile
    private val captureDirectory = File(cacheRoot, DIRECTORY_NAME)

    @Synchronized
    @Throws(IOException::class)
    fun createCaptureFile(): File {
        ensureCaptureDirectory()
        val file = File(captureDirectory, "capture-${UUID.randomUUID()}.jpg")
        if (!file.createNewFile()) throw IOException("Unable to create a temporary photo file")
        return file
    }

    @Synchronized
    fun deleteCapture(file: File?): Boolean {
        if (file == null || !isOwnedCapture(file)) return false
        return file.delete() || !file.exists()
    }

    /** Removes only files created by this store; unrelated cache files and directories are untouched. */
    @Synchronized
    fun deleteAllCaptures() {
        val directory = captureDirectory.canonicalFile
        if (directory.parentFile != cacheRoot || !directory.isDirectory) return
        directory.listFiles()?.forEach { file ->
            if (isOwnedCapture(file)) file.delete()
        }
        directory.delete() // Succeeds only when empty; never recursively deletes other data.
    }

    /** Called on app entry because unfinished games are not restored after process death. */
    fun cleanupStaleCaptures() = deleteAllCaptures()

    private fun ensureCaptureDirectory() {
        val directory = captureDirectory.canonicalFile
        if (directory.parentFile != cacheRoot) throw IOException("Unsafe temporary photo directory")
        if (!directory.exists() && !directory.mkdirs()) throw IOException("Unable to prepare temporary photo storage")
        if (!directory.isDirectory) throw IOException("Temporary photo path is not a directory")
    }

    private fun isOwnedCapture(file: File): Boolean {
        val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return false
        return canonical.parentFile == captureDirectory.canonicalFile && CAPTURE_NAME.matches(canonical.name)
    }

    companion object {
        private const val DIRECTORY_NAME = "frameandfact-temporary-photos"
        private val CAPTURE_NAME = Regex("capture-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg")
    }
}
