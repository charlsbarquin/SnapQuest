package com.charlsemilbarquin.frameandfact.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.charlsemilbarquin.frameandfact.data.history.CompletedGameHistoryRecord
import com.charlsemilbarquin.frameandfact.ui.theme.FrameFactSpacing
import com.charlsemilbarquin.frameandfact.ui.components.SnapQuestSectionHeader

@Composable
fun IncorrectAnswerReviewScreen(
    game: CompletedGameHistoryRecord?,
    isLoading: Boolean,
    loadError: String? = null,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reviewData = remember(game?.gameId, game?.rounds) { game?.incorrectAnswerReviewData() }
    var currentIndex by remember(game?.gameId) { mutableIntStateOf(0) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(FrameFactSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(FrameFactSpacing.contentGap)
    ) {
        Text("Review incorrect answers", style = MaterialTheme.typography.headlineMedium)
        when {
            isLoading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            loadError != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(loadError, style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onRetry) { Text("Retry") }
            }
            game == null -> Text("This saved game could not be loaded.", style = MaterialTheme.typography.bodyLarge)
            reviewData == null || reviewData.incorrectAnswerCount == 0 -> Text(
                "This game has no incorrect trivia answers to review.",
                style = MaterialTheme.typography.bodyLarge
            )
            reviewData.items.isEmpty() -> Text(
                "Review details are unavailable for this older game because its question and answer snapshot is incomplete.",
                style = MaterialTheme.typography.bodyLarge
            )
            else -> {
                if (reviewData.unavailableAnswerCount > 0) {
                    Text(
                        "${reviewData.unavailableAnswerCount} incorrect answer(s) in this game do not have enough saved detail to review.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                val state = IncorrectAnswerReviewState(reviewData, currentIndex.coerceIn(reviewData.items.indices))
                val item = state.currentItem ?: return@Column
                Text("${state.positionLabel} incorrect answers", style = MaterialTheme.typography.titleMedium)
                item.category?.let { category ->
                    Text(
                        listOfNotNull(category, item.difficulty).joinToString(" · "),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } ?: item.difficulty?.let { difficulty ->
                    Text(difficulty, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Round ${item.roundNumber}", style = MaterialTheme.typography.labelLarge)
                        Text(item.questionText, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Answer choices were not saved with this game.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (game.isSoloGame) "Your answer (incorrect): ${item.selectedAnswer}"
                            else "${item.participantName}'s answer (incorrect): ${item.selectedAnswer}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text("Correct answer: ${item.correctAnswer}", style = MaterialTheme.typography.bodyLarge)
                        SnapQuestSectionHeader("Explanation")
                        Text(
                            item.explanation ?: "No explanation was saved for this question.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { currentIndex = state.previous().currentIndex },
                        enabled = currentIndex > 0,
                        modifier = Modifier.weight(1f)
                    ) { Text("Previous answer") }
                    OutlinedButton(
                        onClick = { currentIndex = state.next().currentIndex },
                        enabled = currentIndex < reviewData.items.lastIndex,
                        modifier = Modifier.weight(1f)
                    ) { Text("Next answer") }
                }
            }
        }
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
            Text("Back to game details")
        }
    }
}
