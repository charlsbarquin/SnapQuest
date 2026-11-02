package com.charlsemilbarquin.frameandfact.feature.camera

import com.charlsemilbarquin.frameandfact.domain.game.CameraPromptId
import com.charlsemilbarquin.frameandfact.domain.game.CategoryId
import com.charlsemilbarquin.frameandfact.domain.game.GameId
import com.charlsemilbarquin.frameandfact.domain.game.GameRound
import com.charlsemilbarquin.frameandfact.domain.game.GameSession
import com.charlsemilbarquin.frameandfact.domain.game.Participant
import com.charlsemilbarquin.frameandfact.domain.game.ParticipantId
import com.charlsemilbarquin.frameandfact.domain.game.ParticipantKind
import com.charlsemilbarquin.frameandfact.domain.game.RoundId
import com.charlsemilbarquin.frameandfact.domain.game.RoundType
import com.charlsemilbarquin.frameandfact.feature.gameplay.TriviaGameplayReducer
import com.charlsemilbarquin.frameandfact.feature.gameplay.TriviaGameplayState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraRoundUiStateTest {
    private val owner = Participant(ParticipantId("owner"), "Owner", ParticipantKind.PLAYER)
    private val judge = Participant(ParticipantId("judge"), "Judge", ParticipantKind.PLAYER)
    private val cameraRound = GameRound(
        id = RoundId(4),
        type = RoundType.FIND_AND_FRAME,
        participantId = owner.id,
        judgeId = judge.id,
        cameraPromptId = CameraPromptId("prompt-4")
    )
    private val session = GameSession(
        id = GameId("camera-ui"),
        participants = listOf(owner, judge),
        selectedCategoryIds = setOf(CategoryId("science")),
        startingParticipantId = owner.id,
        rounds = listOf(cameraRound),
        selectedQuestions = emptyList()
    )

    @Test fun permissionDenialDoesNotResolveOrAdvanceCameraRound() {
        val gameplay = TriviaGameplayState(session)
        val denied = CameraRoundUiReducer.permissionDenied(CameraRoundUiState(), permanentlyDenied = false)

        assertEquals(CameraStage.PERMISSION_DENIED, denied.stage)
        assertTrue(denied.message!!.contains("skip this round"))
        assertSame(session, gameplay.session)
        assertEquals(emptyMap<RoundId, com.charlsemilbarquin.frameandfact.domain.game.RoundOutcome>(), gameplay.session.outcomes)
        assertEquals(RoundId(4), gameplay.session.currentRound?.id)
    }

    @Test fun cameraInitializationFailureDoesNotResolveOrScoreTheRound() {
        val gameplay = TriviaGameplayState(session)
        val error = CameraRoundUiReducer.cameraFailed(CameraRoundUiState(CameraStage.INITIALIZING), "Camera unavailable")

        assertEquals(CameraStage.CAMERA_ERROR, error.stage)
        assertEquals("Camera unavailable", error.message)
        assertSame(session, gameplay.session)
        assertEquals(0, gameplay.session.outcomes.size)
        assertEquals(0, gameplay.session.scoreFor(owner.id))
    }

    @Test fun retakeDeletesReviewStateWithoutChangingTheGameSession() {
        val file = File("temporary-photo.jpg")
        val gameplay = TriviaGameplayState(session)
        val captured = CameraRoundUiReducer.captureSucceeded(
            CameraRoundUiState(CameraStage.CAPTURING),
            file
        )
        val retake = CameraRoundUiReducer.retake(captured)

        assertEquals(CameraStage.REVIEW, captured.stage)
        assertSame(file, captured.photoFile)
        assertEquals(CameraStage.INITIALIZING, retake.stage)
        assertNull(retake.photoFile)
        assertSame(session, gameplay.session)
        assertEquals(RoundId(4), gameplay.session.currentRound?.id)
        assertTrue(gameplay.session.outcomes.isEmpty())
    }

    @Test fun soloTryAgainDiscardsOnlyThePhotoAndKeepsTheChallengeActive() {
        val file = File("solo-photo.jpg")
        val review = CameraRoundUiState(stage = CameraStage.REVIEW, photoFile = file)
        val retry = CameraRoundUiReducer.retryAfterSoloRejection(review)

        assertEquals(CameraStage.PROMPT, retry.stage)
        assertNull(retry.photoFile)
        assertEquals(CameraStage.REVIEW, review.stage)
    }

    @Test fun cancelPhotoReturnsToPromptAndUsesEngineWithoutConsumingTheRound() {
        val gameplay = TriviaGameplayState(session)
        val captured = CameraRoundUiReducer.captureSucceeded(
            CameraRoundUiState(CameraStage.CAPTURING),
            File("temporary-photo.jpg")
        )
        val reviewCancelled = CameraRoundUiReducer.cancelPhoto(captured)
        val gameUnchanged = TriviaGameplayReducer.cancelPhoto(gameplay)

        assertEquals(CameraStage.PROMPT, reviewCancelled.stage)
        assertNull(reviewCancelled.photoFile)
        assertEquals(session, gameUnchanged.session)
        assertEquals(RoundId(4), gameUnchanged.session.currentRound?.id)
        assertTrue(gameUnchanged.session.outcomes.isEmpty())
    }

    @Test fun submitPhotoMovesToJudgeWithoutRecordingAnOutcome() {
        val file = File("temporary-photo.jpg")
        val review = CameraRoundUiState(CameraStage.REVIEW, file)
        val judging = CameraRoundUiReducer.submitForJudging(review)

        assertEquals(CameraStage.JUDGING, judging.stage)
        assertSame(file, judging.photoFile)
        assertTrue(session.outcomes.isEmpty())
    }

    @Test fun secondCaptureCannotStartWhileFirstCaptureIsInProgress() {
        val preview = CameraRoundUiState(CameraStage.PREVIEW)
        val capturing = CameraRoundUiReducer.captureStarted(preview)
        val duplicateStart = CameraRoundUiReducer.captureStarted(capturing)

        assertEquals(CameraStage.CAPTURING, capturing.stage)
        assertSame(capturing, duplicateStart)
    }

    @Test fun bindingAndStreamStateRemainDistinctAndStreamLossResetsReadiness() {
        val initializing = CameraRoundUiState(CameraStage.INITIALIZING)

        val bound = CameraRoundUiReducer.previewBindingCompleted(initializing)
        assertEquals(CameraStage.PREVIEW_BOUND, bound.stage)
        val streaming = CameraRoundUiReducer.previewStreamStarted(bound)
        assertEquals(CameraStage.PREVIEW, streaming.stage)
        val stopped = CameraRoundUiReducer.previewStreamStopped(streaming)
        assertEquals(CameraStage.PREVIEW_BOUND, stopped.stage)
        assertEquals(CameraStage.INITIALIZING, CameraRoundUiReducer.previewStreamStopped(initializing).stage)

        val recovered = CameraRoundUiReducer.previewStreamStarted(stopped)
        assertEquals(CameraStage.PREVIEW, recovered.stage)
    }

    @Test fun aNewCameraPermissionGrantStartsNotReady() {
        val freshCameraUi = CameraRoundUiReducer.permissionGranted(CameraRoundUiState())

        assertEquals(CameraStage.INITIALIZING, freshCameraUi.stage)
        assertNull(freshCameraUi.photoFile)
    }

    @Test fun retryAfterPreviewFailureReturnsToFreshInitialization() {
        val failed = CameraRoundUiReducer.cameraFailed(
            CameraRoundUiState(CameraStage.PREVIEW_BOUND),
            "The preview did not start."
        )

        val retried = CameraRoundUiReducer.retryCamera(failed)

        assertEquals(CameraStage.INITIALIZING, retried.stage)
        assertNull(retried.message)
        assertNull(retried.photoFile)
    }
}
