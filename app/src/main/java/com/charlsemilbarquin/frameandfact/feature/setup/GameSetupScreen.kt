package com.charlsemilbarquin.frameandfact.feature.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.charlsemilbarquin.frameandfact.domain.game.ApprovedCategories
import com.charlsemilbarquin.frameandfact.domain.game.GameMode
import com.charlsemilbarquin.frameandfact.domain.game.GameSession
import com.charlsemilbarquin.frameandfact.domain.game.ParticipantKind
import com.charlsemilbarquin.frameandfact.data.history.LocalPlayerProfile
import com.charlsemilbarquin.frameandfact.ui.theme.FrameFactSpacing
import com.charlsemilbarquin.frameandfact.ui.components.SnapQuestSectionHeader

@Composable
fun GameSetupScreen(
    draft: GameSetupDraft,
    contentState: SetupContentState,
    session: GameSession?,
    formMessage: String?,
    onDraftChange: (GameSetupDraft) -> Unit,
    onCreateGame: () -> Unit,
    onStartGame: () -> Unit,
    onEditSetup: () -> Unit,
    onRetryContent: () -> Unit,
    profiles: List<LocalPlayerProfile> = emptyList(),
    modifier: Modifier = Modifier
) {
    if (session != null) {
        SessionReadyScreen(session, onStartGame, onEditSetup, modifier)
        return
    }

    val bank = (contentState as? SetupContentState.Available)?.bank
    val selectedCategories = draft.activeCategoryIds()
    val eligibleCount = bank?.triviaQuestions?.count { it.categoryId in selectedCategories }
    val formIssues = bank?.let { GameSetupValidator.validate(draft, it) }.orEmpty()
    val formCanCreate = contentState is SetupContentState.Available && formIssues.isEmpty()

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(FrameFactSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(FrameFactSpacing.contentGap)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (draft.mode == GameMode.SOLO) "Solo Player setup" else "Set up your game", style = MaterialTheme.typography.headlineMedium)
            Text(
                if (draft.mode == GameMode.SOLO) "Choose a name and trivia categories for your game."
                else "Choose players or teams and the trivia categories for this game.",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (draft.mode == GameMode.SOLO) {
                val player = draft.participants.firstOrNull()
                SnapQuestSectionHeader("Your display name")
                if (player != null) {
                    OutlinedTextField(
                        value = player.name,
                        onValueChange = { name -> onDraftChange(draft.updateParticipantName(player.id, name)) },
                        label = { Text("Player name (optional)") },
                        supportingText = { Text("Leave blank to use ${GameSetupDraft.DEFAULT_SOLO_PLAYER_NAME}.") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    ProfileLinkPicker(
                        profiles = profiles,
                        selectedProfileId = player.profileId,
                        onSelect = { id -> onDraftChange(draft.updateParticipantProfile(player.id, id)) }
                    )
                }
            } else {
                SnapQuestSectionHeader("Who is playing?")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KindChip("Players", ParticipantKind.PLAYER == draft.participantKind) {
                        onDraftChange(draft.copy(participantKind = ParticipantKind.PLAYER))
                    }
                    KindChip("Teams", ParticipantKind.TEAM == draft.participantKind) {
                        onDraftChange(draft.copy(participantKind = ParticipantKind.TEAM))
                    }
                }
                Text(
                    "${draft.participants.size} of ${GameSetupDraft.MAX_PARTICIPANTS} participants",
                    style = MaterialTheme.typography.bodyMedium
                )
                draft.participants.forEachIndexed { index, participant ->
                    key(participant.id.value) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = participant.name,
                                onValueChange = { name ->
                                    onDraftChange(draft.updateParticipantName(participant.id, name))
                                },
                                label = {
                                    Text("${if (draft.participantKind == ParticipantKind.PLAYER) "Player" else "Team"} ${index + 1} name")
                                },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            if (draft.participants.size > GameSetupDraft.MIN_PARTICIPANTS) {
                                TextButton(
                                    onClick = { onDraftChange(draft.removeParticipant(participant.id)) },
                                    modifier = Modifier.semantics {
                                        contentDescription = "Remove ${participant.name.ifBlank { "unnamed participant ${index + 1}" }}"
                                    }
                                ) {
                                    Text("Remove")
                                }
                            }
                        }
                        if (draft.participantKind == ParticipantKind.PLAYER) {
                            ProfileLinkPicker(
                                profiles = profiles,
                                selectedProfileId = participant.profileId,
                                onSelect = { id -> onDraftChange(draft.updateParticipantProfile(participant.id, id)) }
                            )
                        }
                    }
                }
                TextButton(
                    onClick = { onDraftChange(draft.addParticipant()) },
                    enabled = draft.participants.size < GameSetupDraft.MAX_PARTICIPANTS
                ) {
                    Text("Add ${if (draft.participantKind == ParticipantKind.PLAYER) "player" else "team"}")
                }
            }
        }
        }

        Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SnapQuestSectionHeader("Game category mode")
            Text("Difficulty is handled internally; there is no difficulty setting.", style = MaterialTheme.typography.bodyMedium)

            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SelectionModeRow(
                    title = "Standard Mode",
                    selected = draft.categoryMode == GameCategoryMode.STANDARD,
                    onClick = { onDraftChange(draft.copy(categoryMode = GameCategoryMode.STANDARD)) }
                )
                SelectionModeRow(
                    title = "Category-Focused Mode",
                    selected = draft.categoryMode == GameCategoryMode.CATEGORY_FOCUSED,
                    onClick = { onDraftChange(draft.copy(categoryMode = GameCategoryMode.CATEGORY_FOCUSED)) }
                )
            }

            if (draft.categoryMode == GameCategoryMode.CATEGORY_FOCUSED) {
                Text("Choose one category for all six trivia rounds.", style = MaterialTheme.typography.bodyMedium)
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ApprovedCategories.all.forEach { category ->
                        SelectionModeRow(
                            title = category.name,
                            selected = draft.focusedCategoryId == category.id,
                            onClick = { onDraftChange(draft.copy(focusedCategoryId = category.id)) },
                            nested = true
                        )
                    }
                }
                if (draft.focusedCategoryId == null) {
                    Text("Select a category to enable game creation.", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text("Standard Mode can use all categories or your selected category set.", style = MaterialTheme.typography.bodyMedium)
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SelectionModeRow(
                        title = "All categories",
                        selected = draft.categorySelection is CategorySelection.Mixed,
                        onClick = { onDraftChange(draft.copy(categorySelection = CategorySelection.Mixed)) }
                    )
                    SelectionModeRow(
                        title = "Choose categories",
                        selected = draft.categorySelection is CategorySelection.Selected,
                        onClick = {
                            val ids = draft.categorySelection.categoryIds()
                            onDraftChange(draft.updateSelectedCategories(ids))
                        }
                    )
                }
                if (draft.categorySelection is CategorySelection.Selected) {
                    ApprovedCategories.all.forEach { category ->
                        val selectedIds = (draft.categorySelection as? CategorySelection.Selected)?.categoryIds.orEmpty()
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = category.id in selectedIds,
                                onCheckedChange = { checked ->
                                    val updated = if (checked) selectedIds + category.id else selectedIds - category.id
                                    onDraftChange(draft.updateSelectedCategories(updated))
                                }
                            )
                            Text(category.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                } else {
                    Text("All eight categories are included.", style = MaterialTheme.typography.bodyLarge)
                }
            }

            when (contentState) {
                SetupContentState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                    Text("Loading offline question bank…")
                }
                is SetupContentState.Unavailable -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(contentState.message, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetryContent) { Text("Retry content load") }
                }
                is SetupContentState.Available -> {
                    Text(
                        "Eligible questions: ${eligibleCount ?: 0}. At least 6 are needed to start.",
                        color = if ((eligibleCount ?: 0) < 6) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        }

        if (formIssues.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                formIssues.forEach { issue ->
                    Text(issue.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (formMessage != null) {
            Text(formMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Button(
            onClick = onCreateGame,
            enabled = formCanCreate,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Create game session")
        }
        Text(
            "The session will be prepared for an eight-round game. Gameplay is not part of this setup screen.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ProfileLinkPicker(
    profiles: List<LocalPlayerProfile>,
    selectedProfileId: String?,
    onSelect: (String?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Saved profile (optional)", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = selectedProfileId == null, onClick = { onSelect(null) }, label = { Text("None") })
            profiles.forEach { profile ->
                FilterChip(
                    selected = selectedProfileId == profile.profileId,
                    onClick = { onSelect(profile.profileId) },
                    label = { Text("${profile.displayName} · ${profile.profileId.takeLast(4)}") }
                )
            }
        }
        if (profiles.isEmpty()) Text("Create a profile in Settings to link player statistics.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun KindChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun SelectionModeRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    nested: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(start = if (nested) 24.dp else 0.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(title, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SessionReadyScreen(
    session: GameSession,
    onStartGame: () -> Unit,
    onEditSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(FrameFactSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(FrameFactSpacing.contentGap),
        horizontalAlignment = Alignment.Start
    ) {
        Text("Game session ready", style = MaterialTheme.typography.headlineMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (session.mode == GameMode.SOLO) "Solo player: ${session.participants.first().displayName}"
                else "${session.participants.size} ${if (session.participants.first().kind == ParticipantKind.PLAYER) "players" else "teams"}",
                style = MaterialTheme.typography.titleMedium)
                Text("${session.selectedCategoryIds.size} trivia categories")
                Text("${session.selectedQuestions.size} unique trivia questions selected")
                Text("${session.rounds.count { it.cameraPromptId != null }} Find & Frame prompts assigned")
            }
        }
        Text(
            if (session.mode == GameMode.SOLO) "You will play all six trivia rounds and self-assess both camera challenges."
            else "Trivia gameplay is ready for pass-and-play.",
            style = MaterialTheme.typography.bodyLarge
        )
        Button(onClick = onStartGame) { Text("Start game") }
        Button(onClick = onEditSetup) { Text("Edit setup") }
    }
}

