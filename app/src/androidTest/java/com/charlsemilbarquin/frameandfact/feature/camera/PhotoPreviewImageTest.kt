package com.charlsemilbarquin.frameandfact.feature.camera

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoPreviewImageTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun reviewPhotoCanBeDisplayedAndReplaced() {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val firstPhoto = createTestPhoto(cache)
        val replacementPhoto = createTestPhoto(cache)
        val currentPhoto = mutableStateOf(firstPhoto)
        val visible = mutableStateOf(true)

        try {
            compose.setContent {
                if (visible.value) PhotoPreviewImage(currentPhoto.value, onLoaded = {})
            }
            assertCapturedPhotoDisplayed()

            compose.runOnIdle { currentPhoto.value = replacementPhoto }
            assertCapturedPhotoDisplayed()
        } finally {
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            firstPhoto.delete()
            replacementPhoto.delete()
        }
    }

    @Test
    fun missingPhotoShowsRecoverableMessage() {
        val missingPhoto = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "frame-fact-missing-review-photo.jpg"
        )
        missingPhoto.delete()

        compose.setContent { PhotoPreviewImage(missingPhoto, onLoaded = {}) }

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(
                "The captured photo could not be displayed. Retake it or skip the round."
            ).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("The captured photo could not be displayed. Retake it or skip the round.")
            .assertIsDisplayed()
    }

    private fun assertCapturedPhotoDisplayed() {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithContentDescription("Captured Find & Frame challenge photo")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Captured Find & Frame challenge photo")
            .assertIsDisplayed()
    }

    private fun createTestPhoto(directory: File): File {
        val file = File.createTempFile("frame-fact-review-", ".jpg", directory)
        val bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
            }
        } finally {
            bitmap.recycle()
        }
        return file
    }
}
