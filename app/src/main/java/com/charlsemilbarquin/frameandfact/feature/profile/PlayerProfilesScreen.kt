package com.charlsemilbarquin.frameandfact.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.charlsemilbarquin.frameandfact.data.history.LocalPlayerProfile
import com.charlsemilbarquin.frameandfact.data.history.FullGameStatistics
import com.charlsemilbarquin.frameandfact.data.history.UnlockedAchievement
import com.charlsemilbarquin.frameandfact.ui.theme.FrameFactSpacing
import com.charlsemilbarquin.frameandfact.ui.components.SnapQuestSectionHeader

@Composable
fun PlayerProfilesScreen(
    profiles: List<LocalPlayerProfile>,
    statistics: Map<String, FullGameStatistics> = emptyMap(),
    achievements: Map<String, List<UnlockedAchievement>> = emptyMap(),
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var newName by remember { mutableStateOf("") }
    var profileToDelete by remember { mutableStateOf<LocalPlayerProfile?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(FrameFactSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(FrameFactSpacing.standard)) {
        Text("Player profiles", style = MaterialTheme.typography.headlineMedium)
        Text("Profiles are local. Names are never matched automatically; link a profile explicitly in Solo or player setup.",
            style = MaterialTheme.typography.bodyLarge)
        Text("Deleting a profile removes its profile-specific achievements and unlinks it from History. Saved names and scores remain in History.",
            style = MaterialTheme.typography.bodyMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SnapQuestSectionHeader("Create profile")
                OutlinedTextField(value = newName, onValueChange = { newName = it.take(40) },
                    label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { onCreate(newName.trim()); newName = ""; notice = "Profile created." },
                    enabled = newName.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Create") }
            }
        }
        SnapQuestSectionHeader("Saved profiles (${profiles.size})")
        profiles.forEach { profile ->
            var editedName by remember(profile.profileId, profile.displayName) { mutableStateOf(profile.displayName) }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${profile.displayName} · Full Game progress", style = MaterialTheme.typography.titleLarge)
                    statistics[profile.profileId]?.let { stats ->
                        Text("${stats.completedGames} games · ${stats.totalPoints} total points · best ${stats.highestScore}/80")
                        Text("Solo ${stats.soloGames} · Multiplayer ${stats.multiplayerGames} · average ${"%.1f".format(stats.averageScore)} points")
                        Text("Trivia ${stats.correctTriviaAnswers} correct, ${stats.incorrectTriviaAnswers} incorrect · Camera ${stats.acceptedCameraRounds} accepted, ${stats.skippedCameraRounds} skipped")
                        if (stats.correctByCategory.isNotEmpty()) {
                            Text("Correct by category: ${stats.correctByCategory.entries.joinToString { (id, count) -> "${id.replace('_', ' ')} $count" }}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    val unlocked = achievements[profile.profileId].orEmpty()
                    Text(if (unlocked.isEmpty()) "No Full Game achievements yet." else
                        "Achievements: ${unlocked.joinToString { it.achievement.title }}",
                        style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(value = editedName, onValueChange = { editedName = it.take(40) },
                        label = { Text("${profile.displayName} — display name") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onRename(profile.profileId, editedName.trim()); notice = "Profile updated." },
                            enabled = editedName.isNotBlank() && editedName.trim() != profile.displayName) { Text("Save name") }
                        OutlinedButton(onClick = { profileToDelete = profile }) { Text("Delete") }
                    }
                }
            }
        }
        notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }

    profileToDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            title = { Text("Delete ${profile.displayName}?") },
            text = { Text("This deletes the profile and its achievements. Historical names and results remain, with the profile link removed.") },
            confirmButton = {
                TextButton(onClick = { onDelete(profile.profileId); profileToDelete = null }) { Text("Delete profile") }
            },
            dismissButton = { TextButton(onClick = { profileToDelete = null }) { Text("Keep profile") } }
        )
    }
}
