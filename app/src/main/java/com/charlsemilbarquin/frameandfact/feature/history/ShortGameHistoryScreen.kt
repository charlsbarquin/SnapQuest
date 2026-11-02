package com.charlsemilbarquin.frameandfact.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.charlsemilbarquin.frameandfact.data.history.ShortGameSummary
import com.charlsemilbarquin.frameandfact.domain.game.ShortSessionType
import java.text.DateFormat
import java.util.Date
import com.charlsemilbarquin.frameandfact.ui.theme.FrameFactSpacing
import com.charlsemilbarquin.frameandfact.ui.components.SnapQuestEmptyState
import com.charlsemilbarquin.frameandfact.ui.components.SnapQuestSectionHeader

@Composable
fun ShortGameHistoryScreen(summaries: List<ShortGameSummary>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(FrameFactSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(FrameFactSpacing.standard)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(FrameFactSpacing.standard)) {
                Text("Short session summaries", style = MaterialTheme.typography.headlineMedium)
                Text("These Quick Trivia and Category Practice results are stored separately from Full Game History and achievements.",
                    style = MaterialTheme.typography.bodyMedium)
                if (summaries.isNotEmpty()) SnapQuestSectionHeader("Saved sessions (${summaries.size})")
            }
        }
        if (summaries.isEmpty()) {
            item {
                SnapQuestEmptyState(
                    title = "No short sessions yet",
                    message = "Complete Quick Trivia or Category Practice to see a summary here.",
                    iconDescription = "No short session scores",
                    iconRes = null
                )
            }
        }
        itemsIndexed(summaries) { _, summary ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (summary.sessionType == ShortSessionType.QUICK_PLAY) "Quick Trivia" else "Category Practice",
                        style = MaterialTheme.typography.titleLarge)
                    Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(Date(summary.completedAtEpochMillis)))
                    Text("${summary.questionCount} questions${summary.categoryId?.let { " · ${it.replace('_', ' ')}" }.orEmpty()}${summary.difficulty?.let { " · ${it.lowercase().replaceFirstChar(Char::uppercase)}" }.orEmpty()}")
                    summary.participants.forEach { participant ->
                        Text("${participant.displayName}: ${participant.score}/${participant.maximumScore} points · ${participant.correctAnswers} correct · ${participant.incorrectOrSkippedAnswers} incorrect or skipped")
                    }
                }
            }
        }
    }
}
