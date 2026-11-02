package com.charlsemilbarquin.frameandfact.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.charlsemilbarquin.frameandfact.data.history.CompletedGameHistoryRecord
import com.charlsemilbarquin.frameandfact.data.history.HistoryRoundResult
import com.charlsemilbarquin.frameandfact.domain.game.RoundOutcome
import com.charlsemilbarquin.frameandfact.domain.game.RoundType
import java.text.DateFormat
import java.util.Date
import com.charlsemilbarquin.frameandfact.ui.theme.FrameFactSpacing
import com.charlsemilbarquin.frameandfact.ui.components.SnapQuestEmptyState
import com.charlsemilbarquin.frameandfact.ui.components.SnapQuestSectionHeader

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onOpenGame: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().padding(FrameFactSpacing.screen), verticalArrangement = Arrangement.spacedBy(FrameFactSpacing.contentGap)) {
        Text("Completed games", style = MaterialTheme.typography.headlineMedium)
        when (state) {
            HistoryUiState.Loading -> Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.Center
            ) { CircularProgressIndicator() }
            HistoryUiState.Empty -> SnapQuestEmptyState(
                title = "No completed games saved yet",
                message = "Finish a game to see its results here.",
                iconDescription = "Empty game history",
                iconRes = null
            )
            is HistoryUiState.Error -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.message, style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onRetry) { Text("Retry") }
            }
            is HistoryUiState.Populated -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.games, key = { it.gameId }) { game ->
                    HistoryGameCard(game, onClick = { onOpenGame(game.gameId) })
                }
            }
        }
    }
}

@Composable
private fun HistoryGameCard(game: CompletedGameHistoryRecord, onClick: () -> Unit) {
    val winnerText = if (game.isTie) {
        "Tie: ${game.winners.joinToString { it.displayName }}"
    } else {
        "Winner: ${game.winners.firstOrNull()?.displayName.orEmpty()}"
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "${formatDate(game.completedAtEpochMillis)}, $winnerText. View game details."
            }
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(formatDate(game.completedAtEpochMillis), style = MaterialTheme.typography.titleMedium)
            Text(if (game.isSoloGame) "Solo Player" else "Multiplayer", style = MaterialTheme.typography.labelLarge)
            Text(winnerText, style = MaterialTheme.typography.bodyLarge)
            game.participants.forEach { participant ->
                Text("${participant.displayName}: ${participant.score} points", style = MaterialTheme.typography.bodyMedium)
            }
            Text("${game.rounds.size} rounds", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun HistoryGameDetailScreen(
    game: CompletedGameHistoryRecord?,
    onRetry: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    onReviewIncorrectAnswers: () -> Unit = {}
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(FrameFactSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(FrameFactSpacing.standard)
    ) {
        when {
            isLoading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            game == null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This saved game could not be loaded.", style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onRetry) { Text("Retry") }
            }
            else -> {
                Text("Game details", style = MaterialTheme.typography.headlineMedium)
                Text(formatDate(game.completedAtEpochMillis), style = MaterialTheme.typography.titleMedium)
                Text(if (game.isSoloGame) "Solo Player game" else "Multiplayer game", style = MaterialTheme.typography.titleMedium)
                Text(game.winnerLabel(), style = MaterialTheme.typography.titleLarge)
                Text("Categories: ${game.selectedCategories.joinToString().ifEmpty { "Unknown" }}")
                Text("Difficulties used: ${game.difficultyLevels.joinToString().ifEmpty { "Unknown" }}")
                Text("Rules version ${game.rulesVersion}", style = MaterialTheme.typography.labelMedium)

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SnapQuestSectionHeader("Final ranking")
                        game.participants.sortedWith(compareBy({ it.rank }, { it.displayName })).forEach { participant ->
                            val label = if (participant.kind.name == "TEAM") "Team" else "Player"
                            Text("${participant.rank}. $label ${participant.displayName}: ${participant.score} points")
                            Text("${participant.correctTriviaRounds} correct trivia · ${participant.acceptedCameraRounds} accepted camera",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                val reviewData = game.incorrectAnswerReviewData()
                if (reviewData.items.isNotEmpty()) {
                    Button(onClick = onReviewIncorrectAnswers, modifier = Modifier.fillMaxWidth()) {
                        Text("Review incorrect answers (${reviewData.items.size})")
                    }
                    if (reviewData.unavailableAnswerCount > 0) {
                        Text(
                            "${reviewData.unavailableAnswerCount} incorrect answer(s) have incomplete saved details.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                SnapQuestSectionHeader("Round outcomes")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    game.rounds.forEach { round -> HistoryRoundCard(round, game) }
                }
            }
        }
    }
}

@Composable
private fun HistoryRoundCard(round: HistoryRoundResult, game: CompletedGameHistoryRecord) {
    val participant = game.participants.firstOrNull { it.participantId == round.participantId }
    val judge = round.judgeId?.let { id -> game.participants.firstOrNull { it.participantId == id } }
    val roundLabel = if (round.type == RoundType.TRIVIA) "Trivia" else "Find & Frame"
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Round ${round.number}: $roundLabel", style = MaterialTheme.typography.titleSmall)
            Text("${participant?.displayName ?: "Participant"} — ${round.outcome.displayLabel()}")
            if (judge != null) Text("Judge: ${judge.displayName}", style = MaterialTheme.typography.bodySmall)
            if (round.type == RoundType.TRIVIA && round.questionText != null) {
                Text(round.questionText, style = MaterialTheme.typography.bodyMedium)
                round.selectedAnswer?.let { Text("Your answer: $it", style = MaterialTheme.typography.bodySmall) }
                round.correctAnswer?.let { Text("Correct answer: $it", style = MaterialTheme.typography.bodySmall) }
                round.explanation?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                val category = round.categoryId?.replace('_', ' ')?.replaceFirstChar(Char::uppercase)
                val difficulty = round.difficulty?.lowercase()?.replaceFirstChar(Char::uppercase)
                if (category != null || difficulty != null) {
                    Text(listOfNotNull(category, difficulty).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (round.outcome == RoundOutcome.TRIVIA_INCORRECT &&
                    (round.selectedAnswer.isNullOrBlank() || round.correctAnswer.isNullOrBlank())
                ) {
                    Text(
                        "Review details are unavailable because this answer snapshot is incomplete.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (round.type == RoundType.TRIVIA) {
                Text(
                    if (round.outcome == RoundOutcome.TRIVIA_INCORRECT) {
                        "Review details are unavailable for this older game because its question and answer snapshot is incomplete."
                    } else {
                        "Question details were not saved for this older game."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun CompletedGameHistoryRecord.winnerLabel(): String = if (isTie) {
    "Tie: ${winners.joinToString { it.displayName }}"
} else {
    "Winner: ${winners.firstOrNull()?.displayName.orEmpty()}"
}

private fun RoundOutcome.displayLabel(): String = when (this) {
    RoundOutcome.TRIVIA_CORRECT -> "Correct · 10 points"
    RoundOutcome.TRIVIA_INCORRECT -> "Incorrect · 0 points"
    RoundOutcome.TRIVIA_SKIPPED -> "Skipped · 0 points"
    RoundOutcome.CAMERA_ACCEPTED -> "Accepted · 10 points"
    RoundOutcome.CAMERA_REJECTED -> "Rejected · 0 points"
    RoundOutcome.CAMERA_SKIPPED -> "Skipped · 0 points"
}

private fun formatDate(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))
