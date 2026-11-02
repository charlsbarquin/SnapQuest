package com.charlsemilbarquin.frameandfact.feature.camera

import java.io.File

enum class CameraStage {
    PROMPT,
    PERMISSION_EXPLANATION,
    REQUESTING_PERMISSION,
    PERMISSION_DENIED,
    INITIALIZING,
    PREVIEW_BOUND,
    PREVIEW,
    CAPTURING,
    CAMERA_ERROR,
    REVIEW,
    JUDGING
}

data class CameraRoundUiState(
    val stage: CameraStage = CameraStage.PROMPT,
    val photoFile: File? = null,
    val message: String? = null,
    val permissionPermanentlyDenied: Boolean = false
)

/** Local camera-screen transitions; none of these transitions changes game outcomes. */
object CameraRoundUiReducer {
    fun explainPermission(state: CameraRoundUiState) = state.copy(
        stage = CameraStage.PERMISSION_EXPLANATION,
        message = null
    )

    fun requestingPermission(state: CameraRoundUiState) = state.copy(stage = CameraStage.REQUESTING_PERMISSION)

    fun permissionGranted(state: CameraRoundUiState) = state.copy(
        stage = CameraStage.INITIALIZING,
        message = null,
        permissionPermanentlyDenied = false
    )

    fun permissionDenied(state: CameraRoundUiState, permanentlyDenied: Boolean) = state.copy(
        stage = CameraStage.PERMISSION_DENIED,
        message = "Camera access was not granted. You can skip this round without affecting the rest of the game.",
        permissionPermanentlyDenied = permanentlyDenied
    )

    /** Binding succeeded, but PreviewView has not reported a visible stream yet. */
    fun previewBindingCompleted(state: CameraRoundUiState) =
        if (state.stage == CameraStage.INITIALIZING || state.stage == CameraStage.PREVIEW_BOUND) {
            state.copy(stage = CameraStage.PREVIEW_BOUND, message = null)
        } else state

    /** Called only after PreviewView reports a streaming preview, not when use cases merely bind. */
    fun previewStreamStarted(state: CameraRoundUiState) =
        if (state.stage in setOf(CameraStage.INITIALIZING, CameraStage.PREVIEW_BOUND, CameraStage.PREVIEW)) {
            state.copy(stage = CameraStage.PREVIEW, message = null)
        } else state

    /** Hide capture readiness if CameraX reports that the preview stream has stopped. */
    fun previewStreamStopped(state: CameraRoundUiState) =
        if (state.stage == CameraStage.PREVIEW) state.copy(stage = CameraStage.PREVIEW_BOUND) else state

    fun cameraFailed(state: CameraRoundUiState, message: String) = state.copy(
        stage = CameraStage.CAMERA_ERROR,
        message = message,
        photoFile = null
    )

    fun captureStarted(state: CameraRoundUiState): CameraRoundUiState =
        if (state.stage == CameraStage.PREVIEW) state.copy(stage = CameraStage.CAPTURING, message = null) else state

    fun captureSucceeded(state: CameraRoundUiState, file: File): CameraRoundUiState =
        if (state.stage == CameraStage.CAPTURING) state.copy(stage = CameraStage.REVIEW, photoFile = file) else state

    fun captureFailed(state: CameraRoundUiState, message: String) = state.copy(
        stage = CameraStage.CAMERA_ERROR,
        message = message,
        photoFile = null
    )

    fun retryCamera(state: CameraRoundUiState) = state.copy(
        stage = CameraStage.INITIALIZING,
        message = null,
        photoFile = null
    )

    fun retryPermission(state: CameraRoundUiState) = state.copy(
        stage = CameraStage.PERMISSION_EXPLANATION,
        message = null
    )

    fun retake(state: CameraRoundUiState): CameraRoundUiState =
        if (state.stage == CameraStage.REVIEW) state.copy(stage = CameraStage.INITIALIZING, photoFile = null) else state

    /** A solo retry discards only this photo and returns to the active challenge. */
    fun retryAfterSoloRejection(state: CameraRoundUiState): CameraRoundUiState =
        if (state.stage == CameraStage.REVIEW) returnToPrompt(state) else state

    fun submitForJudging(state: CameraRoundUiState): CameraRoundUiState =
        if (state.stage == CameraStage.REVIEW && state.photoFile != null) {
            state.copy(stage = CameraStage.JUDGING)
        } else state

    fun cancelPhoto(state: CameraRoundUiState): CameraRoundUiState =
        if (state.stage == CameraStage.REVIEW) state.copy(stage = CameraStage.PROMPT, photoFile = null) else state

    fun returnToPrompt(state: CameraRoundUiState) = state.copy(
        stage = CameraStage.PROMPT,
        photoFile = null,
        message = null
    )
}
