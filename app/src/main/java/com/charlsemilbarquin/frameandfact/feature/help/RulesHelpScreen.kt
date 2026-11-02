package com.charlsemilbarquin.frameandfact.feature.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.charlsemilbarquin.frameandfact.ui.theme.FrameFactSpacing
import com.charlsemilbarquin.frameandfact.ui.components.SnapQuestSectionHeader

@Composable
fun RulesHelpScreen(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(FrameFactSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(FrameFactSpacing.standard)) {
        Text("Rules & Help", style = MaterialTheme.typography.headlineMedium)
        HelpCard("Full Game", listOf(
            "A Full Game has eight rounds: Trivia in rounds 1–3 and 5–7, with Find & Frame in rounds 4 and 8.",
            "A correct trivia answer or accepted camera submission earns 10 points. Incorrect, rejected, and skipped rounds earn zero. The maximum is 80 points.",
            "Trivia questions are not repeated within a game. There is no timer or difficulty selector in Full Game."
        ))
        HelpCard("Solo and Multiplayer", listOf(
            "Solo Player completes the same eight rounds and decides whether their own camera submission meets the prompt.",
            "Multiplayer is pass-and-play for two to six players or teams. Trivia turns and camera-player turns rotate independently.",
            "In Multiplayer, another participant manually judges a camera submission. SnapQuest never judges photos automatically. Ties remain ties."
        ))
        HelpCard("Find & Frame", listOf(
            "Take a photo for the prompt using the in-app camera. The photo stays in app-private temporary storage and is not added to History.",
            "Cancel Photo closes the current capture or preview without resolving the round. Retaking a photo does not award points.",
            "Skip Round records zero points and advances. Cancel Game ends an unfinished Full Game without saving it as completed."
        ))
        HelpCard("Other modes", listOf(
            "Practice is unscored, can repeat missed questions, and is not saved in Full Game History.",
            "Quick Play starts a full eight-round Solo game using all categories. Quick Trivia and Category Practice are shorter trivia-only sessions.",
            "Quick Trivia and Category Practice results are stored separately from Full Game history. Category-Focused Mode is available in game setup and keeps the standard camera rounds."
        ))
        HelpCard("Offline and privacy", listOf(
            "The bundled question and camera-prompt bank works offline. Saved History, profiles, question reports, and settings stay on this device.",
            "Photos are temporary and cleaned up after the camera round or when stale captures are found. Exported JSON files are saved only when you choose a destination."
        ))
    }
}

@Composable
private fun HelpCard(title: String, points: List<String>) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SnapQuestSectionHeader(title)
            points.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
        }
    }
}
