package com.charlsemilbarquin.frameandfact.feature.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.core.content.ContextCompat
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.charlsemilbarquin.frameandfact.MainActivity
import com.charlsemilbarquin.frameandfact.feature.gameplay.TriviaGameplayReducer
import com.charlsemilbarquin.frameandfact.ui.navigation.AppNavigationViewModel
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FindAndFrameCameraJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var startedTestGame = false
    private var cameraFilesBeforeTest = emptySet<String>()

    @Before
    fun snapshotExistingOwnedCaptures() {
        val cacheRoot = compose.activity.cacheDir.canonicalFile
        val captureDirectory = java.io.File(cacheRoot, "frameandfact-temporary-photos").canonicalFile
        cameraFilesBeforeTest = if (captureDirectory.parentFile == cacheRoot) {
            captureDirectory.listFiles().orEmpty()
                .filter { CAMERA_CAPTURE_NAME.matches(it.name) }
                .mapTo(mutableSetOf()) { it.name }
        } else emptySet()
    }

    @After
    fun cleanUpOnlyTestOwnedState() {
        if (startedTestGame) {
            compose.activityRule.scenario.onActivity { activity ->
                ViewModelProvider(activity)[AppNavigationViewModel::class.java].resetAfterLogout()
            }
            compose.waitForIdle()
        }
        val cacheRoot = compose.activity.cacheDir.canonicalFile
        val captureDirectory = java.io.File(cacheRoot, "frameandfact-temporary-photos").canonicalFile
        if (captureDirectory.parentFile == cacheRoot && captureDirectory.isDirectory) {
            captureDirectory.listFiles().orEmpty().forEach { photo ->
                if (CAMERA_CAPTURE_NAME.matches(photo.name) && photo.name !in cameraFilesBeforeTest) {
                    CameraPhotoStore(cacheRoot).deleteCapture(photo)
                }
            }
            if (captureDirectory.listFiles().isNullOrEmpty()) captureDirectory.delete()
        }
    }

    @Test
    fun liveCameraCaptureRetakeAndSoloAcceptanceAdvanceToTrivia() {
        assertCameraPermissionWasGrantedManually()
        requireExistingSignedInHomeSession()
        compose.onNodeWithText("Solo Player").performClick()
        waitForText("Solo Player setup")
        waitForEnabledText("Create game session")
        compose.onNodeWithText("Create game session").performScrollTo().performClick()
        startedTestGame = true
        waitForText("Game session ready")
        compose.onNodeWithText("Start game").performClick()
        waitForText("Round 1 of 8")

        val navigationState = ViewModelProvider(compose.activity)[AppNavigationViewModel::class.java]
        compose.runOnIdle {
            var gameplay = requireNotNull(navigationState.gameplayState.value)
            repeat(3) {
                gameplay = TriviaGameplayReducer.continueAfterFeedback(TriviaGameplayReducer.skipRound(gameplay))
            }
            navigationState.gameplayState.value = gameplay
        }
        waitForText("Round 4 of 8 — Find & Frame")
        compose.onNodeWithText("Start Camera").performClick()

        waitForEnabledCapture()
        compose.onNodeWithContentDescription(LIVE_PREVIEW).assertIsDisplayed()
        captureAndWaitForReview()
        waitForEnabledText("Use This Photo")
        compose.onNodeWithContentDescription("Captured Find & Frame challenge photo").assertIsDisplayed()
        compose.onNodeWithText("Retake").performClick()

        waitForEnabledCapture()
        captureAndWaitForReview()
        waitForEnabledText("Use This Photo")
        compose.onNodeWithText("Use This Photo").performClick()
        waitForText("Round 4 resolved")
        compose.onNodeWithText("Continue").performClick()
        waitForText("Round 5 of 8")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Cancel Game").assertIsDisplayed().performClick()
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithTag("home-ready").fetchSemanticsNodes().isNotEmpty()
        }
        startedTestGame = false
    }

    private fun requireExistingSignedInHomeSession() {
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithTag("home-ready").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag("login-submit").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty()
        }
        assumeTrue(
            "Physical camera test needs an already signed-in Home screen; sign in manually first. " +
                "The test will not create or modify a production account.",
            compose.onAllNodesWithTag("home-ready").fetchSemanticsNodes().isNotEmpty()
        )
    }

    private fun captureAndWaitForReview() {
        waitForEnabledCapture()
        compose.onNodeWithContentDescription("Capture Photo").performClick()
        waitForText("Review your photo")
        waitForEnabledText("Retake")
    }

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private fun waitForEnabledCapture() {
        val timeout = runCatching {
            compose.waitUntil(timeoutMillis = 30_000) {
                val nodes = compose.onAllNodesWithContentDescription("Capture Photo").fetchSemanticsNodes()
                nodes.any { !it.config.contains(SemanticsProperties.Disabled) }
            }
        }.exceptionOrNull()
        if (timeout != null) {
            throw AssertionError(cameraReadinessDiagnostics(), timeout)
        }
    }

    private fun assertCameraPermissionWasGrantedManually() {
        val granted = ContextCompat.checkSelfPermission(compose.activity, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        val targetPackage = compose.activity.packageName
        val targetUid = compose.activity.applicationInfo.uid
        val targetUserId = targetUid / 100_000
        check(granted) {
            "Camera journey requires CAMERA permission to be manually allowed for the SnapQuest app instance " +
                "running this test; permissionState=DENIED, targetPackage=$targetPackage, " +
                "targetUid=$targetUid, targetUserId=$targetUserId. Verify Android Settings for this exact " +
                "profile. This test does not request or change permissions."
        }
    }

    private fun cameraReadinessDiagnostics(): String {
        val permissionGranted = ContextCompat.checkSelfPermission(compose.activity, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        val captureNodes = compose.onAllNodesWithContentDescription("Capture Photo").fetchSemanticsNodes()
        val visibleSignals = listOf(
            "Allow camera for this round?",
            "Waiting for camera permission…",
            "Camera access was not granted",
            "Opening camera…",
            "Camera connected. Waiting for live preview…",
            "The camera could not be opened.",
            "The camera opened, but no live preview appeared."
        ).filter { signal ->
            compose.onAllNodesWithText(signal, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        val previewNodes = compose.onAllNodesWithContentDescription(LIVE_PREVIEW).fetchSemanticsNodes().size
        val disabledCaptureNodes = captureNodes.count { it.config.contains(SemanticsProperties.Disabled) }
        return "Camera readiness timed out. permissionGranted=$permissionGranted, " +
            "activityLifecycle=${compose.activity.lifecycle.currentState}, " +
            "captureNodes=${captureNodes.size}, disabledCaptureNodes=$disabledCaptureNodes, " +
            "livePreviewNodes=$previewNodes, visibleCameraSignals=${visibleSignals.ifEmpty { listOf("none") }}. " +
            "CameraX transition details are available under the SnapQuestCamera debug log tag."
    }

    private fun waitForEnabledText(text: String) {
        compose.waitUntil(timeoutMillis = 30_000) {
            val nodes = compose.onAllNodesWithText(text).fetchSemanticsNodes()
            nodes.any { !it.config.contains(SemanticsProperties.Disabled) }
        }
    }

    private companion object {
        const val LIVE_PREVIEW = "Live camera preview for the Find & Frame challenge"
        val CAMERA_CAPTURE_NAME = Regex("capture-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg")
    }
}




