package com.charlsemilbarquin.frameandfact.feature.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.charlsemilbarquin.frameandfact.ui.components.SnapQuestMark

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SnapQuestMark(Modifier.padding(bottom = 4.dp))
                Text("SnapQuest", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    "An offline trivia and camera-challenge game. Play solo or pass the device between friends for trivia and Find & Frame rounds.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        AboutSection("Purpose") {
            Text("SnapQuest brings multiple-choice trivia and creative camera prompts into one local game. Its questions, prompts, gameplay records, and settings are available without an internet connection.")
        }
        AboutSection("Features") {
            Text("• Solo and pass-and-play Multiplayer (2–6 players or teams)\n• Trivia Challenges and Find & Frame camera challenges\n• Quick Trivia, Category Practice, and unscored Practice\n• Device-local sign-in accounts with no online verification or sync\n• Account-specific player profiles, game history, statistics, and reports\n• Incorrect Answer Review\n• Light, Dark, and System themes")
        }
        AboutSection("Full Game rules") {
            Text("A Full Game has eight rounds: trivia in rounds 1–3 and 5–7, and Find & Frame in rounds 4 and 8. Each successful round earns 10 points; incorrect, skipped, or rejected rounds earn 0. The maximum score is 80. There is no timer, and tied scores remain ties. Camera rounds use player judging; SnapQuest does not automatically analyze photos.")
        }
        AboutSection("Offline play and technology") {
            Text("Trivia questions and camera prompts are bundled with the app. Game records and preferences stay on this device. The camera is used only for Find & Frame challenges. Built with Kotlin, Jetpack Compose, Material 3, CameraX, Room, Preferences DataStore, Gson, Gradle Kotlin DSL, and KSP.")
        }
        AboutSection("Developer") {
            Text("Charls Emil C. Barquin", style = MaterialTheme.typography.titleMedium)
            Text("Application developer, identified in the repository author metadata.")
            Text("SnapQuest is an offline trivia and camera-challenge game developed as an Android application project.")
        }
    }
}

@Composable
private fun AboutSection(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}
