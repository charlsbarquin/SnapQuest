package com.charlsemilbarquin.frameandfact.feature.gameplay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.charlsemilbarquin.frameandfact.domain.game.ApprovedCategories
import com.charlsemilbarquin.frameandfact.domain.game.ParticipantKind
import com.charlsemilbarquin.frameandfact.domain.game.CameraPrompt
import com.charlsemilbarquin.frameandfact.domain.game.CompletedGame
import com.charlsemilbarquin.frameandfact.domain.game.GameEvent
import com.charlsemilbarquin.frameandfact.domain.game.GameId
import com.charlsemilbarquin.frameandfact.domain.game.GameMode
import com.charlsemilbarquin.frameandfact.domain.game.GameRound
import com.charlsemilbarquin.frameandfact.domain.game.GameStatus
import com.charlsemilbarquin.frameandfact.domain.game.Participant
import com.charlsemilbarquin.frameandfact.domain.game.RoundId
import com.charlsemilbarquin.frameandfact.domain.game.RoundOutcome
import com.charlsemilbarquin.frameandfact.domain.game.RoundType
import com.charlsemilbarquin.frameandfact.R
import com.charlsemilbarquin.frameandfact.feature.camera.CameraPhotoStore
import com.charlsemilbarquin.frameandfact.feature.camera.CameraRoundOutcomeScreen
import com.charlsemilbarquin.frameandfact.feature.camera.FindAndFrameScreen
import com.charlsemilbarquin.frameandfact.feature.history.CompletionSaveUiState
import com.charlsemilbarquin.frameandfact.ui.theme.FrameFactSpacing

@Composable
fun TriviaGameplayScreen(
    state: TriviaGameplayState,
    cameraPrompts: List<CameraPrompt>,
    photoStore: CameraPhotoStore,
    onStateChange: (TriviaGameplayState) -> Unit,
    onCameraEvent: (GameId, RoundId, GameEvent) -> Boolean,
    onCancelGame: () -> Unit,
    onFinishGame: () -> Unit,
    completionSaveState: CompletionSaveUiState,
    onRetrySave: () -> Unit,
    onPlayAgain: () -> Unit,
    onOpenHistory: () -> Unit,
    onReportQuestion: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    when {
        state.session.status == GameStatus.CANCELLED -> MessageScreen("This game was cancelled.", modifier)
        state.resolvedRoundId != null && state.displayedRound?.type == RoundType.FIND_AND_FRAME &&
            state.displayedOutcome != null -> CameraRoundOutcomeScreen(
                state = state,
                round = state.displayedRound!!,
                onContinue = { onStateChange(TriviaGameplayReducer.continueAfterFeedback(state)) },
                modifier = modifier
            )
        state.session.status == GameStatus.COMPLETED -> CompletedGameScreen(
            completedGame = state.session.completedGameOrNull(),
            saveState = completionSaveState,
            onRetrySave = onRetrySave,
            onFinishGame = onFinishGame,
            onPlayAgain = onPlayAgain,
            onOpenHistory = onOpenHistory,
            modifier = modifier
        )
        state.displayedRound == null -> MessageScreen("There are no remaining rounds.", modifier)
        state.displayedRound?.type == RoundType.FIND_AND_FRAME -> {
            val round = state.displayedRound!!
            FindAndFrameScreen(
                state = state,
                round = round,
                prompt = cameraPrompts.firstOrNull { it.id == round.cameraPromptId },
                photoStore = photoStore,
                onCameraEvent = onCameraEvent,
                onCancelGame = onCancelGame,
                modifier = modifier
            )
        }
        state.displayedQuestion == null -> MessageScreen(
            "The current trivia question could not be found. Cancel the game and return to setup.",
            modifier
        )
        else -> TriviaRoundScreen(state, state.displayedRound!!, onStateChange, onCancelGame, onReportQuestion, modifier)
    }
}

@Composable
private fun TriviaRoundScreen(
    state: TriviaGameplayState,
    round: GameRound,
    onStateChange: (TriviaGameplayState) -> Unit,
    onCancelGame: () -> Unit,
    onReportQuestion: (String) -> Unit,
    modifier: Modifier
) {
    val question = state.displayedQuestion ?: return
    val participant = state.session.participants.first { it.id == round.participantId }
    val category = ApprovedCategories.all.firstOrNull { it.id == question.categoryId }?.name.orEmpty()
    val resolved = state.displayedOutcome != null

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = FrameFactSpacing.screen, vertical = FrameFactSpacing.standard),
        verticalArrangement = Arrangement.spacedBy(FrameFactSpacing.standard)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Round ${round.id.number} of 8", style = MaterialTheme.typography.titleMedium)
        }
        LinearProgressIndicator(
            progress = { round.id.number / 8f },
            modifier = Modifier.fillMaxWidth().height(4.dp).semantics {
                contentDescription = "Round ${round.id.number} of 8"
            }
        )
        if (state.session.mode == GameMode.SOLO) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${participantLabel(participant)} · ${participant.displayName}", style = MaterialTheme.typography.bodyMedium)
                Text("Score ${state.scoreFor(round)} / 80", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Scoreboard(state, activeParticipant = participant)
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "$category · ${question.difficulty.name.lowercase().replaceFirstChar(Char::uppercase)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(question.prompt, style = MaterialTheme.typography.headlineSmall)
        }

        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            question.choices.forEach { choice ->
                val chosen = state.selectedChoiceId == choice.id
                val correct = resolved && choice.id == question.correctChoiceId
                val choiceContainer = when {
                    resolved && correct -> MaterialTheme.colorScheme.secondaryContainer
                    resolved && chosen && !correct -> MaterialTheme.colorScheme.errorContainer
                    chosen -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = choiceContainer),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = chosen,
                            enabled = !resolved,
                            role = Role.RadioButton,
                            onClick = { onStateChange(TriviaGameplayReducer.selectChoice(state, choice.id)) }
                        )
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = chosen, onClick = null, enabled = !resolved)
                        Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = when {
                                    correct -> "${choice.text} — correct answer"
                                    resolved && chosen -> "${choice.text} — your selected answer"
                                    else -> choice.text
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (resolved && correct) Text("Correct answer", style = MaterialTheme.typography.labelSmall)
                            else if (resolved && chosen) Text("Your answer · incorrect", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        if (resolved) {
            OutcomeFeedback(state, question.correctChoiceId, onReportQuestion)
            Button(
                onClick = { onStateChange(TriviaGameplayReducer.continueAfterFeedback(state)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("Continue")
            }
        } else {
            Button(
                onClick = { onStateChange(TriviaGameplayReducer.submitAnswer(state)) },
                enabled = state.selectedChoiceId != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("Submit answer")
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { onStateChange(TriviaGameplayReducer.skipRound(state)) },
                    modifier = Modifier.weight(1f).heightIn(min = 42.dp),
                    shape = RoundedCornerShape(22.dp)
                ) { Text("Skip round · 0 points", style = MaterialTheme.typography.labelLarge) }
                TextButton(onClick = onCancelGame, modifier = Modifier.weight(1f)) { Text("Cancel Game") }
            }
        }
    }
}

@Composable
private fun OutcomeFeedback(
    state: TriviaGameplayState,
    correctChoiceId: com.charlsemilbarquin.frameandfact.domain.game.ChoiceId,
    onReportQuestion: (String) -> Unit
) {
    val outcome = state.displayedOutcome ?: return
    val (title, color) = when (outcome) {
        RoundOutcome.TRIVIA_CORRECT -> "Correct answer. 10 points awarded." to MaterialTheme.colorScheme.secondary
        RoundOutcome.TRIVIA_INCORRECT -> "Incorrect answer. 0 points." to MaterialTheme.colorScheme.error
        RoundOutcome.TRIVIA_SKIPPED -> "Round skipped. 0 points." to MaterialTheme.colorScheme.onSurface
        else -> "Round resolved." to MaterialTheme.colorScheme.onSurface
    }
    val question = state.displayedQuestion ?: return
    val correctText = question.choices.firstOrNull { it.id == correctChoiceId }?.text.orEmpty()
    Card(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (outcome == RoundOutcome.TRIVIA_CORRECT) "✓ $title" else if (outcome == RoundOutcome.TRIVIA_INCORRECT) "! $title" else title, style = MaterialTheme.typography.titleMedium, color = color)
            Text("Correct answer: $correctText", style = MaterialTheme.typography.bodyLarge)
            question.explanation?.takeIf(String::isNotBlank)?.let { explanation ->
                Text(explanation, style = MaterialTheme.typography.bodyLarge)
            }
            TextButton(onClick = { onReportQuestion(question.id.value) }) { Text("Report a content issue") }
        }
    }
}

@Composable
internal fun Scoreboard(state: TriviaGameplayState, activeParticipant: Participant) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        state.session.participants.forEach { participant ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (participant.id == activeParticipant.id) "Current turn · ${participant.displayName}" else participant.displayName,
                    style = if (participant.id == activeParticipant.id) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall
                )
                Text("${state.session.scoreFor(participant.id)} points", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CompletedGameScreen(
    completedGame: CompletedGame?,
    saveState: CompletionSaveUiState,
    onRetrySave: () -> Unit,
    onFinishGame: () -> Unit,
    onPlayAgain: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier
) {
    if (completedGame == null) {
        MessageScreen("Game complete.", modifier)
        return
    }
    val winnerIds = completedGame.winners.toSet()
    val winnerNames = completedGame.participants.filter { it.id in winnerIds }.joinToString { it.displayName }
    val solo = completedGame.mode == GameMode.SOLO
    val soloScore = completedGame.scores.singleOrNull()
    val successfulRounds = completedGame.outcomes.values.count {
        it == RoundOutcome.TRIVIA_CORRECT || it == RoundOutcome.CAMERA_ACCEPTED
    }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(FrameFactSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(FrameFactSpacing.contentGap)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_nav_scores),
                contentDescription = "Trophy",
                modifier = Modifier.size(56.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.tertiary)
            )
            Text("Game complete", style = MaterialTheme.typography.headlineLarge)
            Text(
                if (solo) "Well played, ${completedGame.participants.single().displayName}!"
                else if (completedGame.winners.size > 1) "It's a tie between $winnerNames."
                else "Winner: $winnerNames.",
                style = MaterialTheme.typography.titleLarge
            )
        }
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = if (solo) Alignment.CenterHorizontally else Alignment.Start
            ) {
                if (solo && soloScore != null) {
                    Text("${soloScore.total}", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("of ${completedGame.rounds.size * 10} points", style = MaterialTheme.typography.titleLarge)
                    Text("Final score", style = MaterialTheme.typography.titleMedium)
                    Text("Successful rounds: $successfulRounds of ${completedGame.rounds.size}")
                    Text("Unsuccessful or skipped: ${completedGame.rounds.size - successfulRounds}")
                    Text("Correct trivia: ${soloScore.correctTriviaRounds} of 6")
                    Text("Accepted camera challenges: ${soloScore.acceptedCameraRounds} of 2")
                } else {
                    completedGame.scores.forEach { score ->
                        val participant = completedGame.participants.first { it.id == score.participantId }
                        Text("${participant.displayName}: ${score.total} points", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        if (solo) {
            Text("Round breakdown", style = MaterialTheme.typography.titleLarge)
            completedGame.rounds.sortedBy { it.id.number }.forEach { round ->
                val outcome = completedGame.outcomes[round.id]
                Text("Round ${round.id.number}: ${outcome?.resultLabel().orEmpty()}", style = MaterialTheme.typography.bodyLarge)
            }
        }
        when (saveState) {
            CompletionSaveUiState.NotStarted, CompletionSaveUiState.Saving -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.CircularProgressIndicator()
                    Text("Saving this completed game to History…")
                }
            }
            CompletionSaveUiState.Saved -> {
                Text("This completed game is saved in History.")
                if (solo) {
                    Button(onClick = onPlayAgain, modifier = Modifier.fillMaxWidth()) { Text("Play Again") }
                    OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) { Text("History") }
                    OutlinedButton(onClick = onFinishGame, modifier = Modifier.fillMaxWidth()) { Text("Return Home") }
                } else {
                    Button(onClick = onFinishGame, modifier = Modifier.fillMaxWidth()) { Text("Finish Game") }
                }
            }
            is CompletionSaveUiState.Failed -> {
                Text(saveState.message, color = MaterialTheme.colorScheme.error)
                Button(onClick = onRetrySave, modifier = Modifier.fillMaxWidth()) { Text("Retry Save") }
                if (solo) {
                    OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) { Text("History") }
                }
                OutlinedButton(onClick = onFinishGame, modifier = Modifier.fillMaxWidth()) {
                    Text("Return Home Without Saving")
                }
            }
        }
    }
}

private fun RoundOutcome.resultLabel(): String = when (this) {
    RoundOutcome.TRIVIA_CORRECT -> "Correct · 10 points"
    RoundOutcome.TRIVIA_INCORRECT -> "Incorrect · 0 points"
    RoundOutcome.TRIVIA_SKIPPED -> "Skipped · 0 points"
    RoundOutcome.CAMERA_ACCEPTED -> "Accepted · 10 points"
    RoundOutcome.CAMERA_REJECTED -> "Rejected · 0 points"
    RoundOutcome.CAMERA_SKIPPED -> "Skipped · 0 points"
}

private fun participantLabel(participant: Participant) =
    if (participant.kind == ParticipantKind.TEAM) "Team" else "Player"

@Composable
private fun MessageScreen(message: String, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(FrameFactSpacing.screen), verticalArrangement = Arrangement.Center) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}
