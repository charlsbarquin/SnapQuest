package com.charlsemilbarquin.frameandfact.feature.help

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.charlsemilbarquin.frameandfact.ui.theme.FrameFactSpacing

private data class TutorialPage(val title: String, val body: String)

@Composable
fun InteractiveTutorialScreen(
    onComplete: () -> Unit,
    saveError: String?,
    isSaving: Boolean,
    modifier: Modifier = Modifier
) {
    val pages = remember {
        listOf(
            TutorialPage("Choose how to play", "Solo Player uses one player. Multiplayer is pass-and-play for two to six players or teams, with trivia turns rotating separately from camera turns."),
            TutorialPage("Full Game rounds", "A Full Game lasts eight rounds: Trivia in rounds 1–3 and 5–7, then Find & Frame in rounds 4 and 8. Questions do not repeat within a game."),
            TutorialPage("Scoring", "Each correct trivia answer or accepted camera submission earns 10 points. Incorrect, rejected, and skipped rounds earn zero. The maximum Full Game score is 80."),
            TutorialPage("Camera challenges", "Take a photo for the prompt, review or retake it, then accept it yourself in Solo or let another participant judge it in Multiplayer. Cancel Photo leaves the round open; Skip Round advances for zero points; Cancel Game ends the unfinished game."),
            TutorialPage("Results and History", "Completed Full Games are saved locally. Results show scores and round outcomes. Temporary photos are not saved in History and are cleaned up after camera play."),
            TutorialPage("More ways to learn", "Practice is unscored. Quick Play starts a full eight-round game. Quick Trivia and Category Practice are shorter trivia-only modes with separately labeled summaries.")
        )
    }
    var index by remember { mutableIntStateOf(0) }
    val page = pages[index]
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(FrameFactSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(FrameFactSpacing.contentGap)
    ) {
        Text("Getting started", style = MaterialTheme.typography.headlineMedium)
        Text("Step ${index + 1} of ${pages.size}", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(
            progress = { (index + 1).toFloat() / pages.size },
            modifier = Modifier.fillMaxWidth()
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(page.title, style = MaterialTheme.typography.titleLarge)
                Text(page.body, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (saveError != null) Text(saveError, color = MaterialTheme.colorScheme.error)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onComplete, enabled = !isSaving, modifier = Modifier.weight(1f)) { Text("Skip") }
            Button(onClick = {
                if (index == pages.lastIndex) onComplete() else index++
            }, enabled = !isSaving, modifier = Modifier.weight(1f)) {
                Text(if (isSaving) "Saving…" else if (index == pages.lastIndex) "Finish" else "Next")
            }
        }
    }
}
