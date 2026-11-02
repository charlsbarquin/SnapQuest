package com.charlsemilbarquin.frameandfact.feature.camera

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.Settings
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import com.charlsemilbarquin.frameandfact.domain.game.CameraPrompt
import com.charlsemilbarquin.frameandfact.domain.game.GameEvent
import com.charlsemilbarquin.frameandfact.domain.game.GameId
import com.charlsemilbarquin.frameandfact.domain.game.GameMode
import com.charlsemilbarquin.frameandfact.domain.game.GameRound
import com.charlsemilbarquin.frameandfact.domain.game.GameSession
import com.charlsemilbarquin.frameandfact.domain.game.Participant
import com.charlsemilbarquin.frameandfact.domain.game.RoundId
import com.charlsemilbarquin.frameandfact.domain.game.RoundOutcome
import com.charlsemilbarquin.frameandfact.ui.theme.FrameFactSpacing
import com.charlsemilbarquin.frameandfact.feature.gameplay.Scoreboard
import com.charlsemilbarquin.frameandfact.feature.gameplay.TriviaGameplayState
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FindAndFrameScreen(
    state: TriviaGameplayState,
    round: GameRound,
    prompt: CameraPrompt?,
    photoStore: CameraPhotoStore,
    onCameraEvent: (GameId, RoundId, GameEvent) -> Boolean,
    onCancelGame: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val session = state.session
    val owner = session.participants.first { it.id == round.participantId }
    val judge = session.participants.firstOrNull { it.id == round.judgeId }
    var cameraUi by remember(session.id, round.id) { mutableStateOf(CameraRoundUiState()) }
    var permissionRequested by remember(session.id, round.id) { mutableStateOf(false) }
    var returningFromSettings by remember(session.id, round.id) { mutableStateOf(false) }
    var resolving by remember(session.id, round.id) { mutableStateOf(false) }
    var gameActionError by remember(session.id, round.id) { mutableStateOf<String?>(null) }
    var photoLoaded by remember(cameraUi.photoFile) { mutableStateOf(false) }
    var hintVisible by remember(prompt?.id) { mutableStateOf(false) }
    val latestCameraUi by rememberUpdatedState(cameraUi)

    DisposableEffect(session.id, round.id) {
        onDispose { photoStore.deleteCapture(latestCameraUi.photoFile) }
    }

    DisposableEffect(activity, session.id, round.id) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                if (returningFromSettings) {
                    returningFromSettings = false
                    cameraUi = if (granted) {
                        CameraRoundUiReducer.permissionGranted(cameraUi)
                    } else {
                        CameraRoundUiReducer.permissionDenied(cameraUi, permanentlyDenied = true)
                    }
                } else if (!granted && cameraUi.stage in setOf(
                        CameraStage.INITIALIZING, CameraStage.PREVIEW_BOUND, CameraStage.PREVIEW, CameraStage.CAPTURING
                    )
                ) {
                    cameraUi = CameraRoundUiReducer.permissionDenied(cameraUi, permanentlyDenied = true)
                }
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraUi = if (granted) {
            CameraRoundUiReducer.permissionGranted(cameraUi)
        } else {
            val canAskAgain = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
            } ?: false
            CameraRoundUiReducer.permissionDenied(cameraUi, permanentlyDenied = permissionRequested && !canAskAgain)
        }
    }

    fun startCamera() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        cameraUi = if (granted) CameraRoundUiReducer.permissionGranted(cameraUi)
        else CameraRoundUiReducer.explainPermission(cameraUi)
    }

    fun skipRound() {
        if (resolving) return
        resolving = true
        val applied = onCameraEvent(session.id, round.id, GameEvent.SkipCameraRound(round.id))
        if (!applied) {
            resolving = false
            gameActionError = "This round is no longer current. The game state was not changed."
            return
        }
        photoStore.deleteCapture(cameraUi.photoFile)
        cameraUi = CameraRoundUiReducer.returnToPrompt(cameraUi)
        gameActionError = null
    }

    fun resolveJudging(accepted: Boolean) {
        if (resolving || cameraUi.stage != CameraStage.JUDGING) return
        resolving = true
        val event = if (accepted) GameEvent.CameraAccepted(round.id) else GameEvent.CameraRejected(round.id)
        if (!onCameraEvent(session.id, round.id, event)) {
            resolving = false
            gameActionError = "The camera outcome was not applied because this is no longer the current round."
            return
        }
        photoStore.deleteCapture(cameraUi.photoFile)
        cameraUi = CameraRoundUiReducer.returnToPrompt(cameraUi)
        gameActionError = null
    }

    fun acceptSoloPhoto() {
        if (session.mode != GameMode.SOLO || resolving || cameraUi.stage != CameraStage.REVIEW || !photoLoaded) return
        resolving = true
        if (!onCameraEvent(session.id, round.id, GameEvent.CameraAccepted(round.id))) {
            resolving = false
            gameActionError = "The photo was not accepted because this is no longer the current round."
            return
        }
        photoStore.deleteCapture(cameraUi.photoFile)
        cameraUi = CameraRoundUiReducer.returnToPrompt(cameraUi)
        gameActionError = null
    }

    fun retrySoloPhoto() {
        if (session.mode != GameMode.SOLO || resolving || cameraUi.stage != CameraStage.REVIEW) return
        photoStore.deleteCapture(cameraUi.photoFile)
        cameraUi = CameraRoundUiReducer.retryAfterSoloRejection(cameraUi)
        gameActionError = null
    }

    val liveCamera = cameraUi.stage in setOf(
        CameraStage.INITIALIZING, CameraStage.PREVIEW_BOUND, CameraStage.PREVIEW, CameraStage.CAPTURING
    )
    if (liveCamera) {
        Box(modifier.fillMaxSize().background(Color.Black)) {
            CameraPreviewPane(
                photoStore = photoStore,
                captureEnabled = cameraUi.stage == CameraStage.PREVIEW,
                modifier = Modifier.fillMaxSize(),
                onPreviewBindingCompleted = { cameraUi = CameraRoundUiReducer.previewBindingCompleted(cameraUi) },
                onPreviewStreamStarted = { cameraUi = CameraRoundUiReducer.previewStreamStarted(cameraUi) },
                onPreviewStreamStopped = { cameraUi = CameraRoundUiReducer.previewStreamStopped(cameraUi) },
                onCaptureStarted = { cameraUi = CameraRoundUiReducer.captureStarted(cameraUi) },
                onPhotoCaptured = { file -> cameraUi = CameraRoundUiReducer.captureSucceeded(cameraUi, file) },
                onCameraError = { message -> cameraUi = CameraRoundUiReducer.cameraFailed(cameraUi, message) }
            )
            Card(
                Modifier.align(if (landscape) Alignment.TopStart else Alignment.TopCenter)
                    .widthIn(max = if (landscape) 340.dp else 600.dp)
                    .fillMaxWidth()
                    .heightIn(max = if (landscape) 170.dp else 240.dp)
                    .padding(12.dp)
            ) {
                Column(
                    Modifier.padding(14.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Round ${round.id.number} of 8 · Find & Frame", style = MaterialTheme.typography.titleMedium)
                    Text(prompt?.prompt ?: "Camera prompt unavailable", style = MaterialTheme.typography.titleLarge)
                    Text("${owner.displayName} · ${state.session.scoreFor(round.participantId)} points", style = MaterialTheme.typography.bodyMedium)
                    prompt?.let { Text(it.safetyNote, style = MaterialTheme.typography.bodySmall) }
                    if (!prompt?.hint.isNullOrBlank()) {
                        TextButton(onClick = { hintVisible = !hintVisible }) { Text(if (hintVisible) "Hide hint" else "Show hint") }
                        if (hintVisible) Text("Hint: ${prompt?.hint}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (cameraUi.stage in setOf(CameraStage.INITIALIZING, CameraStage.PREVIEW_BOUND, CameraStage.CAPTURING)) {
                Row(
                    Modifier.align(Alignment.Center).background(Color(0xCC071A33), MaterialTheme.shapes.medium).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator()
                    val progressText = when (cameraUi.stage) {
                        CameraStage.CAPTURING -> "Capturing photo…"
                        CameraStage.PREVIEW_BOUND -> "Camera connected. Waiting for live preview…"
                        else -> "Opening camera…"
                    }
                    Text(progressText, color = Color.White)
                }
            }
            Column(
                Modifier.align(if (landscape) Alignment.BottomEnd else Alignment.BottomCenter)
                    .widthIn(max = if (landscape) 300.dp else 600.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                gameActionError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = ::skipRound, enabled = !resolving) { Text("Skip Round · 0 points") }
                    TextButton(onClick = onCancelGame) { Text("Cancel Game") }
                }
            }
        }
    } else if (cameraUi.stage == CameraStage.REVIEW) {
        val file = cameraUi.photoFile
        Box(modifier.fillMaxSize().background(Color.Black)) {
            if (file != null) {
                PhotoPreviewImage(file, onLoaded = { photoLoaded = it }, modifier = Modifier.fillMaxSize(), fullScreen = true)
            }
            Card(
                Modifier.align(if (landscape) Alignment.TopStart else Alignment.TopCenter)
                    .widthIn(max = if (landscape) 480.dp else 720.dp)
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Review your photo", style = MaterialTheme.typography.titleLarge)
                    Text(prompt?.prompt.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                    Text("${owner.displayName} · ${state.session.scoreFor(round.participantId)} points", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Column(
                Modifier.align(Alignment.BottomCenter).widthIn(max = if (landscape) 760.dp else 720.dp)
                    .fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (landscape) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { cameraUi = CameraRoundUiReducer.retake(cameraUi); photoStore.deleteCapture(file) },
                            enabled = file != null && !resolving,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) { Text("Retake") }
                        Button(
                            onClick = {
                                if (session.mode == GameMode.SOLO) acceptSoloPhoto()
                                else cameraUi = CameraRoundUiReducer.submitForJudging(cameraUi)
                            },
                            enabled = file != null && photoLoaded && !resolving,
                            modifier = Modifier.weight(1.5f).heightIn(min = 48.dp)
                        ) { Text(if (session.mode == GameMode.SOLO) "Use This Photo" else "Submit Photo to Judge") }
                        if (session.mode == GameMode.SOLO) {
                            OutlinedButton(
                                onClick = ::retrySoloPhoto,
                                enabled = file != null && photoLoaded && !resolving,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) { Text("Try again") }
                        }
                    }
                } else {
                    Button(
                        onClick = { cameraUi = CameraRoundUiReducer.retake(cameraUi); photoStore.deleteCapture(file) },
                        enabled = file != null && !resolving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text("Retake") }
                    Button(
                        onClick = {
                            if (session.mode == GameMode.SOLO) acceptSoloPhoto()
                            else cameraUi = CameraRoundUiReducer.submitForJudging(cameraUi)
                        },
                        enabled = file != null && photoLoaded && !resolving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text(if (session.mode == GameMode.SOLO) "Use This Photo" else "Submit Photo to Judge") }
                    if (session.mode == GameMode.SOLO) {
                        OutlinedButton(onClick = ::retrySoloPhoto, enabled = file != null && photoLoaded && !resolving, modifier = Modifier.fillMaxWidth()) {
                            Text("Try again")
                        }
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            if (!resolving && onCameraEvent(session.id, round.id, GameEvent.CancelPhoto(round.id))) {
                                photoStore.deleteCapture(file)
                                cameraUi = CameraRoundUiReducer.cancelPhoto(cameraUi)
                                gameActionError = null
                            } else if (!resolving) {
                                gameActionError = "This photo no longer belongs to the current round. The game state was not changed."
                            }
                        },
                        enabled = file != null && !resolving
                    ) { Text("Cancel Photo") }
                    TextButton(onClick = ::skipRound, enabled = !resolving) { Text("Skip Round") }
                    TextButton(onClick = onCancelGame) { Text("Cancel Game") }
                }
                gameActionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    } else {
        Column(
            modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(FrameFactSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Round ${round.id.number} of 8 — Find & Frame", style = MaterialTheme.typography.headlineMedium)
            CameraPromptCard(prompt, owner, judge, round, state)
            Scoreboard(state, activeParticipant = owner)

            when (cameraUi.stage) {
            CameraStage.PROMPT -> {
                Button(onClick = ::startCamera, enabled = prompt != null, modifier = Modifier.fillMaxWidth()) {
                    Text("Start Camera")
                }
                if (prompt == null) {
                    Text(
                        "The assigned camera prompt is unavailable. The round has not been changed; skip it or cancel the game.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            CameraStage.PERMISSION_EXPLANATION -> AlertDialog(
                onDismissRequest = { cameraUi = CameraRoundUiReducer.returnToPrompt(cameraUi) },
                title = { Text("Allow camera for this round?") },
                text = {
                    Text("The camera is used only after you start this Find & Frame round. The photo stays in temporary app-private storage and is deleted when this round ends or the game is cancelled.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        permissionRequested = true
                        cameraUi = CameraRoundUiReducer.requestingPermission(cameraUi)
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }) { Text("Continue") }
                },
                dismissButton = {
                    TextButton(onClick = { cameraUi = CameraRoundUiReducer.returnToPrompt(cameraUi) }) { Text("Not now") }
                }
            )
            CameraStage.REQUESTING_PERMISSION -> {
                CircularProgressIndicator()
                Text("Waiting for camera permission…")
            }
            CameraStage.PERMISSION_DENIED -> {
                Text(cameraUi.message.orEmpty(), color = MaterialTheme.colorScheme.error)
                if (cameraUi.permissionPermanentlyDenied) {
                    Text("To use the camera, open Android Settings and allow Camera access for SnapQuest.")
                    Button(onClick = {
                        returningFromSettings = true
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                            )
                        }.onFailure {
                            returningFromSettings = false
                            cameraUi = CameraRoundUiReducer.cameraFailed(
                                cameraUi,
                                "Android Settings could not be opened. You can still skip this round."
                            )
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Open App Settings") }
                } else {
                    OutlinedButton(
                        onClick = { cameraUi = CameraRoundUiReducer.retryPermission(cameraUi) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Try Camera Permission Again") }
                }
            }
            CameraStage.INITIALIZING, CameraStage.PREVIEW_BOUND, CameraStage.PREVIEW, CameraStage.CAPTURING -> Unit
            CameraStage.CAMERA_ERROR -> {
                Text(cameraUi.message ?: "The camera is unavailable.", color = MaterialTheme.colorScheme.error)
                Button(onClick = { cameraUi = CameraRoundUiReducer.retryCamera(cameraUi) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Try Camera Again")
                }
            }
            CameraStage.REVIEW -> Unit
            CameraStage.JUDGING -> {
        Text("Pass the device to ${judge?.displayName ?: "the assigned judge"}.", style = MaterialTheme.typography.titleLarge)
                Text("Judge the submitted photo against the challenge. No automatic image evaluation is used.")
                cameraUi.photoFile?.let { PhotoPreviewImage(it, onLoaded = {}) }
                Button(onClick = { resolveJudging(accepted = true) }, enabled = !resolving, modifier = Modifier.fillMaxWidth()) {
                    Text("Accept — award 10 points")
                }
                OutlinedButton(onClick = { resolveJudging(accepted = false) }, enabled = !resolving, modifier = Modifier.fillMaxWidth()) {
                    Text("Does Not Match — 0 points")
                }
            }
        }

            gameActionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            if (cameraUi.stage != CameraStage.PERMISSION_EXPLANATION) {
                OutlinedButton(onClick = ::skipRound, enabled = !resolving, modifier = Modifier.fillMaxWidth()) {
                    Text("Skip Round — 0 points")
                }
            }
            TextButton(onClick = onCancelGame, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Cancel Game")
            }
        }
    }
}

@Composable
private fun CameraPromptCard(
    prompt: CameraPrompt?,
    owner: Participant,
    judge: Participant?,
    round: GameRound,
    state: TriviaGameplayState
) {
    var hintVisible by remember(prompt?.id) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (state.session.mode == GameMode.SOLO) "Player: ${owner.displayName}"
                else "Camera player: ${owner.displayName}", style = MaterialTheme.typography.titleMedium)
            Text("Current score: ${state.session.scoreFor(round.participantId)} points")
            if (judge != null) Text("Judge: ${judge.displayName}")
            else Text("Review your photo and decide whether it matches. The app does not check images automatically.")
            if (prompt != null) {
                Text(prompt.prompt, style = MaterialTheme.typography.headlineSmall)
                Text(prompt.safetyNote, style = MaterialTheme.typography.bodyMedium)
                if (!prompt.hint.isNullOrBlank()) {
                    TextButton(onClick = { hintVisible = !hintVisible }) {
                        Text(if (hintVisible) "Hide hint" else "Show hint")
                    }
                    if (hintVisible) {
                        Text("Hint: ${prompt.hint}", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPreviewPane(
    photoStore: CameraPhotoStore,
    captureEnabled: Boolean,
    modifier: Modifier = Modifier,
    onPreviewBindingCompleted: () -> Unit,
    onPreviewStreamStarted: () -> Unit,
    onPreviewStreamStopped: () -> Unit,
    onCaptureStarted: () -> Unit,
    onPhotoCaptured: (File) -> Unit,
    onCameraError: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val captureState = remember { mutableStateOf<ImageCapture?>(null) }
    val readyState = remember { mutableStateOf(false) }
    val busyState = remember { mutableStateOf(false) }
    val disposed = remember { AtomicBoolean(false) }
    val latestOnBindingCompleted by rememberUpdatedState(onPreviewBindingCompleted)
    val latestOnStreamStarted by rememberUpdatedState(onPreviewStreamStarted)
    val latestOnStreamStopped by rememberUpdatedState(onPreviewStreamStopped)
    val latestOnPhotoCaptured by rememberUpdatedState(onPhotoCaptured)
    val latestOnError by rememberUpdatedState(onCameraError)
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // CameraX can then use STREAMING as its supported visible-preview readiness signal.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val providerFuture = remember(context) { ProcessCameraProvider.getInstance(context) }
    val orientation = LocalConfiguration.current.orientation

    LaunchedEffect(orientation, previewView) {
        captureState.value?.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
    }

    DisposableEffect(activity, previewView, providerFuture) {
        disposed.set(false)
        var boundProvider: ProcessCameraProvider? = null
        val timeoutHandler = Handler(Looper.getMainLooper())
        val streamTimeout = Runnable {
            if (!disposed.get() && !readyState.value) {
                latestOnError("The camera opened, but no live preview appeared. Try again or skip this round.")
            }
        }
        val lifecycleOwner = activity
        val streamObserver = Observer<PreviewView.StreamState> { streamState ->
            if (!disposed.get()) {
                when (streamState) {
                    PreviewView.StreamState.STREAMING -> {
                        timeoutHandler.removeCallbacks(streamTimeout)
                        readyState.value = true
                        latestOnStreamStarted()
                    }
                    PreviewView.StreamState.IDLE -> {
                        val wasReady = readyState.value
                        readyState.value = false
                        if (wasReady) {
                            latestOnStreamStopped()
                            timeoutHandler.postDelayed(streamTimeout, PREVIEW_STREAM_TIMEOUT_MS)
                        }
                    }
                }
            }
        }
        if (lifecycleOwner != null) {
            previewView.previewStreamState.observe(lifecycleOwner, streamObserver)
        }
        val listener = Runnable {
            if (disposed.get()) return@Runnable
            try {
                val provider = providerFuture.get()
                boundProvider = provider
                val selector = when {
                    provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                    provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> throw IllegalStateException("No usable camera")
                }
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
                    .build()
                provider.unbindAll()
                provider.bindToLifecycle(
                    activity ?: throw IllegalStateException("No activity lifecycle available"),
                    selector,
                    preview,
                    imageCapture
                )
                if (disposed.get()) {
                    provider.unbindAll()
                } else {
                    captureState.value = imageCapture
                    latestOnBindingCompleted()
                    if (!readyState.value) {
                        timeoutHandler.postDelayed(streamTimeout, PREVIEW_STREAM_TIMEOUT_MS)
                    }
                }
            } catch (_: Exception) {
                boundProvider?.unbindAll()
                if (!disposed.get()) {
                    latestOnError("The camera could not be opened. Check whether another app is using it, then retry or skip this round.")
                }
            }
        }
        providerFuture.addListener(listener, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed.set(true)
            timeoutHandler.removeCallbacks(streamTimeout)
            if (lifecycleOwner != null) {
                previewView.previewStreamState.removeObserver(streamObserver)
            }
            boundProvider?.unbindAll()
            captureState.value = null
            readyState.value = false
        }
    }

    val scope = rememberCoroutineScope()
    Box(modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
                .semantics { contentDescription = "Live camera preview for the Find & Frame challenge" }
        )
        Canvas(
            Modifier.align(Alignment.Center)
                .fillMaxWidth(0.84f)
                .aspectRatio(if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 1.45f else 0.78f)
        ) {
            val stroke = 3.dp.toPx()
            val corner = minOf(size.width, size.height) * 0.16f
            val inset = stroke / 2f
            val color = androidx.compose.ui.graphics.Color.White
            // Four open corners echo the SnapQuest camera-frame mark without obstructing the preview.
            drawLine(color, androidx.compose.ui.geometry.Offset(inset, inset), androidx.compose.ui.geometry.Offset(corner, inset), stroke)
            drawLine(color, androidx.compose.ui.geometry.Offset(inset, inset), androidx.compose.ui.geometry.Offset(inset, corner), stroke)
            drawLine(color, androidx.compose.ui.geometry.Offset(size.width - inset, inset), androidx.compose.ui.geometry.Offset(size.width - corner, inset), stroke)
            drawLine(color, androidx.compose.ui.geometry.Offset(size.width - inset, inset), androidx.compose.ui.geometry.Offset(size.width - inset, corner), stroke)
            drawLine(color, androidx.compose.ui.geometry.Offset(inset, size.height - inset), androidx.compose.ui.geometry.Offset(corner, size.height - inset), stroke)
            drawLine(color, androidx.compose.ui.geometry.Offset(inset, size.height - inset), androidx.compose.ui.geometry.Offset(inset, size.height - corner), stroke)
            drawLine(color, androidx.compose.ui.geometry.Offset(size.width - inset, size.height - inset), androidx.compose.ui.geometry.Offset(size.width - corner, size.height - inset), stroke)
            drawLine(color, androidx.compose.ui.geometry.Offset(size.width - inset, size.height - inset), androidx.compose.ui.geometry.Offset(size.width - inset, size.height - corner), stroke)
        }
        Button(
            onClick = {
                val imageCapture = captureState.value ?: return@Button
                if (busyState.value || !readyState.value) return@Button
                busyState.value = true
                onCaptureStarted()
                scope.launch {
                    val file = try {
                        withContext(Dispatchers.IO) { photoStore.createCaptureFile() }
                    } catch (_: Exception) {
                        busyState.value = false
                        latestOnError("Temporary photo storage is unavailable. You can retry or skip this round.")
                        return@launch
                    }
                    if (disposed.get()) {
                        photoStore.deleteCapture(file)
                        return@launch
                    }
                    val options = ImageCapture.OutputFileOptions.Builder(file).build()
                    try {
                        imageCapture.takePicture(
                            options,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    busyState.value = false
                                    if (disposed.get()) photoStore.deleteCapture(file)
                                    else latestOnPhotoCaptured(file)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    busyState.value = false
                                    photoStore.deleteCapture(file)
                                    if (!disposed.get()) {
                                        latestOnError("The photo could not be captured. Retry or skip this round.")
                                    }
                                }
                            }
                        )
                    } catch (_: Exception) {
                        busyState.value = false
                        photoStore.deleteCapture(file)
                        if (!disposed.get()) latestOnError("The photo could not be captured. Retry or skip this round.")
                    }
                }
            },
            enabled = captureEnabled && readyState.value && !busyState.value,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 82.dp).size(76.dp)
                .semantics { contentDescription = if (busyState.value) "Capturing photo" else "Capture Photo" },
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp)
        ) {
            Canvas(Modifier.size(40.dp)) {
                val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                val outerRadius = size.minDimension * 0.42f
                drawCircle(
                    color = androidx.compose.ui.graphics.Color.White,
                    radius = outerRadius,
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )
                drawCircle(
                    color = androidx.compose.ui.graphics.Color.White,
                    radius = outerRadius * 0.58f,
                    center = center
                )
            }
        }
    }
}

// This is only a failure fallback. Capture readiness itself is driven by PreviewView's stream state.
private const val PREVIEW_STREAM_TIMEOUT_MS = 12_000L

@Composable
internal fun PhotoPreviewImage(
    file: File,
    onLoaded: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    fullScreen: Boolean = false
) {
    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember(file) { mutableStateOf(false) }
    val latestOnLoaded by rememberUpdatedState(onLoaded)
    LaunchedEffect(file) {
        val decoded = withContext(Dispatchers.IO) { decodePhoto(file) }
        bitmap = decoded
        loadFailed = decoded == null
        latestOnLoaded(decoded != null)
    }
    // Compose owns the Image painter while it is in use; do not recycle its backing bitmap on disposal.
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "Captured Find & Frame challenge photo",
            contentScale = ContentScale.Fit,
            modifier = if (fullScreen) modifier.fillMaxSize()
            else modifier.fillMaxWidth().heightIn(max = if (landscape) 220.dp else 320.dp)
        )
    } else if (loadFailed) {
        Text("The captured photo could not be displayed. Retake it or skip the round.", color = MaterialTheme.colorScheme.error)
    } else {
        Box(
            if (fullScreen) modifier.fillMaxSize() else modifier.fillMaxWidth().height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

private fun decodePhoto(file: File): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > 1600) sampleSize *= 2
    val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        ?: return null
    val orientation = ExifInterface(file.absolutePath).getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL
    )
    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (degrees == 0f) bitmap else {
        val matrix = Matrix().apply { postRotate(degrees) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { rotated ->
            if (rotated !== bitmap) bitmap.recycle()
        }
    }
}.getOrNull()

@Composable
fun CameraRoundOutcomeScreen(
    state: TriviaGameplayState,
    round: GameRound,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val participant = state.session.participants.first { it.id == round.participantId }
    val outcome = state.displayedOutcome
    val (message, points) = when (outcome) {
        RoundOutcome.CAMERA_ACCEPTED -> (if (state.session.mode == GameMode.SOLO) "Accepted by you" else "Accepted by the judge") to 10
        RoundOutcome.CAMERA_REJECTED -> "Does not match" to 0
        RoundOutcome.CAMERA_SKIPPED -> "Camera round skipped" to 0
        else -> "Camera round resolved" to 0
    }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(FrameFactSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(FrameFactSpacing.contentGap)
    ) {
        Text("Round ${round.id.number} resolved", style = MaterialTheme.typography.headlineMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message, style = MaterialTheme.typography.titleLarge)
                Text("${participant.displayName} earned $points points for this round.", style = MaterialTheme.typography.bodyLarge)
                Text("The temporary photo has been deleted.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Scoreboard(state, activeParticipant = participant)
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
    }
}

private fun Context.findActivity(): ComponentActivity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return current as? ComponentActivity
}
