package com.charlsemilbarquin.frameandfact.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.charlsemilbarquin.frameandfact.R
import com.charlsemilbarquin.frameandfact.ui.components.SnapQuestSectionHeader
import com.charlsemilbarquin.frameandfact.data.settings.AppSettings
import com.charlsemilbarquin.frameandfact.data.settings.SettingsReadState
import com.charlsemilbarquin.frameandfact.data.settings.ThemePreference
import com.charlsemilbarquin.frameandfact.data.settings.ThemeWriteResult

sealed interface ClearHistoryUiState {
    data object Idle : ClearHistoryUiState
    data object Deleting : ClearHistoryUiState
    data class Deleted(val removedGames: Int) : ClearHistoryUiState
    data class Failed(val message: String) : ClearHistoryUiState
}

@Composable
fun SettingsScreen(
    settingsState: SettingsReadState,
    themeWriteResult: ThemeWriteResult?,
    isThemeSaving: Boolean,
    clearHistoryState: ClearHistoryUiState,
    onThemeSelected: (ThemePreference) -> Unit,
    onConfirmClearHistory: () -> Unit,
    onManageQuestionReports: () -> Unit = {},
    onManageProfiles: () -> Unit = {},
    onReplayTutorial: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showThemePicker by rememberSaveable { mutableStateOf(false) }
    var showPrivacyDetails by rememberSaveable { mutableStateOf(false) }
    var showClearConfirmation by rememberSaveable { mutableStateOf(false) }
    val settings = (settingsState as? SettingsReadState.Ready)?.settings ?: AppSettings.DEFAULT

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        SettingsSection(title = "Appearance") {
            SettingsActionRow(
                title = "App Theme",
                subtitle = settings.theme.label(),
                iconRes = R.drawable.ic_nav_settings,
                onClick = { showThemePicker = true }
            )
            if (settingsState is SettingsReadState.Loading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator()
                    Text("Loading saved settings", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (settingsState is SettingsReadState.Error) {
                Text(settingsState.message, color = MaterialTheme.colorScheme.error)
            }
            when (themeWriteResult) {
                ThemeWriteResult.Saved -> Text("Theme preference saved.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                is ThemeWriteResult.Failed -> Text(themeWriteResult.message, color = MaterialTheme.colorScheme.error)
                null -> Unit
            }
        }

        SettingsSection(title = "Your SnapQuest") {
            SettingsActionRow("Player profiles", "Manage local names and linked results", R.drawable.ic_person, onManageProfiles)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsActionRow("Question reports", "Review reports saved on this device", R.drawable.ic_reports, onManageQuestionReports)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsActionRow("Replay tutorial", "Review the game basics", R.drawable.ic_tutorial, onReplayTutorial)
        }

        SettingsSection(title = "Completed-game history") {
            Text(
                "Remove saved completed-game records. An active game is not affected.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { showClearConfirmation = true },
                enabled = clearHistoryState != ClearHistoryUiState.Deleting,
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                if (clearHistoryState == ClearHistoryUiState.Deleting) CircularProgressIndicator()
                else Text("Clear History")
            }
            when (val state = clearHistoryState) {
                ClearHistoryUiState.Idle -> Unit
                ClearHistoryUiState.Deleting -> Text("Clearing saved history…", style = MaterialTheme.typography.bodySmall)
                is ClearHistoryUiState.Deleted -> Text(
                    if (state.removedGames == 0) "There were no saved game records to remove."
                    else "Removed ${state.removedGames} saved game ${if (state.removedGames == 1) "record" else "records"}.",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
                is ClearHistoryUiState.Failed -> Text(state.message, color = MaterialTheme.colorScheme.error)
            }
        }

        SettingsSection(title = "Privacy") {
            SettingsActionRow(
                title = "Offline & local storage",
                subtitle = "Your game data stays on this device",
                iconRes = R.drawable.ic_lock,
                onClick = { showPrivacyDetails = true }
            )
        }
    }

    if (showThemePicker) {
        AlertDialog(
            onDismissRequest = { if (!isThemeSaving) showThemePicker = false },
            title = { Text("App Theme") },
            text = {
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ThemePreference.entries.forEach { preference ->
                        Row(
                            modifier = Modifier.fillMaxWidth().selectable(
                                selected = settings.theme == preference,
                                enabled = !isThemeSaving,
                                role = Role.RadioButton,
                                onClick = { onThemeSelected(preference) }
                            ).testTag("theme-option-${preference.name}").padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = settings.theme == preference, onClick = null, enabled = !isThemeSaving)
                            Text(preference.label(), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemePicker = false }, enabled = !isThemeSaving) { Text("Done") }
            }
        )
    }

    if (showPrivacyDetails) {
        AlertDialog(
            onDismissRequest = { showPrivacyDetails = false },
            title = { Text("Offline & local storage") },
            text = {
                Text(
                    "Completed-game history and this theme preference are stored locally and excluded from system backup. " +
                        "SnapQuest does not require an account or synchronize data to a cloud service. " +
                        "Camera access is requested only when you start a Find & Frame camera. Captures are kept temporarily " +
                        "in app-private storage and deleted at round or game end or during stale-file cleanup; photos are not attached to history."
                )
            },
            confirmButton = { TextButton(onClick = { showPrivacyDetails = false }) { Text("Done") } }
        )
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear saved history?") },
            text = { Text("This permanently removes all completed-game records stored in local history. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirmation = false
                    onConfirmClearHistory()
                }) { Text("Delete History") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) { Text("Keep History") }
            }
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SnapQuestSectionHeader(title)
        content()
    }
}

@Composable
private fun SettingsActionRow(title: String, subtitle: String, iconRes: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.primary)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun ThemePreference.label(): String = when (this) {
    ThemePreference.SYSTEM_DEFAULT -> "System default"
    ThemePreference.LIGHT -> "Light"
    ThemePreference.DARK -> "Dark"
}
