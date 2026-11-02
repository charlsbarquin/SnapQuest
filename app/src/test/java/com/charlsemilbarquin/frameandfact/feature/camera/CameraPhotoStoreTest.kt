package com.charlsemilbarquin.frameandfact.feature.camera

import java.io.File
import java.nio.file.Files
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraPhotoStoreTest {
    @Test fun captureUsesDedicatedCacheDirectoryAndDeletesSafely() = withTempCache { cache ->
        val store = CameraPhotoStore(cache)
        val photo = store.createCaptureFile()
        val captureDirectory = requireNotNull(photo.parentFile)
        val unrelated = File(captureDirectory, "keep-this.txt").apply { writeText("unrelated cache data") }
        val outsideCapture = File(cache, "capture-${UUID.randomUUID()}.jpg").apply { writeText("outside") }

        assertTrue(photo.canonicalPath.startsWith(cache.canonicalPath))
        assertNotEquals(cache.canonicalPath, captureDirectory.canonicalPath)
        assertFalse(store.deleteCapture(outsideCapture))
        assertTrue(outsideCapture.exists())
        assertTrue(store.deleteCapture(photo))
        assertFalse(photo.exists())
        assertTrue(unrelated.exists())
    }

    @Test fun staleCleanupRemovesOwnedCapturesButPreservesUnrelatedFilesAndDirectories() = withTempCache { cache ->
        val store = CameraPhotoStore(cache)
        val oldCapture = store.createCaptureFile()
        val unrelated = File(oldCapture.parentFile, "not-a-capture.dat").apply { writeText("keep") }
        val nested = File(oldCapture.parentFile, "nested").apply { mkdir() }
        File(nested, "keep.txt").writeText("nested keep")

        CameraPhotoStore(cache).cleanupStaleCaptures()

        assertFalse(oldCapture.exists())
        assertTrue(unrelated.exists())
        assertTrue(File(nested, "keep.txt").exists())
    }

    @Test fun gameCleanupDeletesAllTemporaryPhotosWithoutRecursiveDeletion() = withTempCache { cache ->
        val store = CameraPhotoStore(cache)
        val first = store.createCaptureFile()
        val second = store.createCaptureFile()
        val unrelated = File(first.parentFile, "keep.txt").apply { writeText("keep") }

        store.deleteAllCaptures()

        assertFalse(first.exists())
        assertFalse(second.exists())
        assertTrue(unrelated.exists())
    }

    private inline fun withTempCache(block: (File) -> Unit) {
        val cache = Files.createTempDirectory("frameandfact-cache-").toFile()
        try {
            block(cache)
        } finally {
            cache.deleteRecursively()
        }
    }
}
