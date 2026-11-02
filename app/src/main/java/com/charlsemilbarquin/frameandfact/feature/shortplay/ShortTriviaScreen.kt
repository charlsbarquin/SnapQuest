package com.charlsemilbarquin.frameandfact.feature.shortplay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.charlsemilbarquin.frameandfact.data.history.ShortGameSummary
import com.charlsemilbarquin.frameandfact.data.history.toCompletedSummary
import com.charlsemilbarquin.frameandfact.domain.game.ApprovedCategories
import com.charlsemilbarquin.frameandfact.domain.game.Difficulty
import com.charlsemilbarquin.frameandfact.domain.game.GameId
import com.charlsemilbarquin.frameandfact.domain.game.GameMode
import com.charlsemilbarquin.frameandfact.domain.game.Participant
import com.charlsemilbarquin.frameandfact.domain.game.ParticipantId
import com.charlsemilbarquin.frameandfact.domain.game.ParticipantKind
import com.charlsemilbarquin.frameandfact.domain.game.ShortSessionType
import com.charlsemilbarquin.frameandfact.domain.game.ShortTriviaReducer
import com.charlsemilbarquin.frameandfact.domain.game.ShortTriviaSession
import com.charlsemilbarquin.frameandfact.domain.game.ShortTriviaSessionFactory
import com.charlsemilbarquin.frameandfact.domain.game.TriviaQuestion
import java.util.UUID
import kotlinx.coroutines.CancellationException
import com.charlsemilbarquin.frameandfact.ui.theme.FrameFactSpacing

@Composable
fun ShortTriviaScreen(
    type: ShortSessionType,
    questions: List<TriviaQuestion>,
    onCompleted: suspend (ShortGameSummary) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var gameMode by remember { mutableStateOf(GameMode.SOLO) }
    var names by remember { mutableStateOf(listOf("Player")) }
    var categoryId by remember { mutableStateOf(ApprovedCategories.all.first().id) }
    var difficulty by remember { mutableStateOf(Difficulty.EASY) }
    var questionCount by remember(type) { mutableStateOf(if (type == ShortSessionType.QUICK_PLAY) 5 else 5) }
    var session by remember { mutableStateOf<ShortTriviaSession?>(null) }
    var lastSavedSessionId by remember { mutableStateOf<String?>(null) }
    var saveAttempt by remember { mutableStateOf(0) }
    var saveError by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val eligible = if (type == ShortSessionType.CATEGORY_FOCUSED) {
        questions.filter { it.categoryId == categoryId && it.difficulty == difficulty }
    } else questions
    val countOptions = if (type == ShortSessionType.QUICK_PLAY) listOf(5, 8, 10) else listOf(3, 5, eligible.size)
    val validCounts = countOptions.filter { it > 0 && it <= eligible.size }.distinct()
    LaunchedEffect(type, eligible.size) {
        if (questionCount !in validCounts && validCounts.isNotEmpty()) {
            questionCount = validCounts.lastOrNull { it <= 5 } ?: validCounts.first()
        }
    }

    val current = session
    LaunchedEffect(current == null, current?.index, current?.isComplete) {
        scrollState.scrollTo(0)
    }
    LaunchedEffect(current?.isComplete, saveAttempt) {
        current?.takeIf { it.isComplete }?.let { completed ->
            if (lastSavedSessionId != completed.id.value) {
                val summary = completed.toCompletedSummary(System.currentTimeMillis())
                if (summary != null) {
                    try {
                        onCompleted(summary)
                        lastSavedSessionId = completed.id.value
                        saveError = false
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        saveError = true
                    }
                }
            }
        }
    }

    Column(modifier.fillMaxSize().verticalScroll(scrollState).padding(FrameFactSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(FrameFactSpacing.standard)) {
        val title = if (type == ShortSessionType.QUICK_PLAY) "Quick Trivia" else "Category Practice"
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text("Trivia only · 10 points per correct answer · no timer", style = MaterialTheme.typography.bodyLarge)
        Text("Results are saved separately and do not count toward Full Game statistics or achievements.",
            style = MaterialTheme.typography.bodyMedium)

        when {
            current == null -> {
                Text("Players", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = gameMode == GameMode.SOLO, onClick = {
                        gameMode = GameMode.SOLO; names = listOf(names.firstOrNull()?.ifBlank { "Player" } ?: "Player")
                    }, label = { Text("Solo") })
                    FilterChip(selected = gameMode == GameMode.MULTIPLAYER, onClick = {
                        gameMode = GameMode.MULTIPLAYER
                        if (names.size < 2) names = listOf("Player 1", "Player 2")
                    }, label = { Text("Pass and play") })
                }
                names.forEachIndexed { index, name ->
                    OutlinedTextField(value = name, onValueChange = { value ->
                        names = names.toMutableList().also { it[index] = value.take(30) }
                    }, label = { Text(if (gameMode == GameMode.SOLO) "Player name" else "Player ${index + 1} name") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                if (gameMode == GameMode.MULTIPLAYER) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = { names = names + "Player ${names.size + 1}" }, enabled = names.size < 6) { Text("Add player") }
                        TextButton(onClick = { if (names.size > 2) names = names.dropLast(1) }, enabled = names.size > 2) { Text("Remove last") }
                    }
                }
                if (type == ShortSessionType.CATEGORY_FOCUSED) {
                    Text("Category", style = MaterialTheme.typography.titleLarge)
                    ApprovedCategories.all.forEach { category ->
                        Row(Modifier.fillMaxWidth().selectable(categoryId == category.id, role = Role.RadioButton) {
                            categoryId = category.id
                        }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = categoryId == category.id, onClick = { categoryId = category.id })
                            Text(category.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Text("Difficulty", style = MaterialTheme.typography.titleLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Difficulty.entries.forEach { level ->
                            FilterChip(selected = difficulty == level, onClick = { difficulty = level },
                                label = { Text(level.name.lowercase().replaceFirstChar(Char::uppercase)) })
                        }
                    }
                }
                Text("Question count", style = MaterialTheme.typography.titleLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    validCounts.forEach { count ->
                        FilterChip(selected = questionCount == count, onClick = { questionCount = count }, label = { Text("$count") })
                    }
                }
                Button(onClick = {
                    val participants = names.mapIndexed { index, name ->
                        Participant(ParticipantId("short-player-$index"), name.trim(), ParticipantKind.PLAYER)
                    }
                    session = ShortTriviaSessionFactory.create(
                        id = GameId("short-${UUID.randomUUID()}"), type = type,
                        participants = participants, availableQuestions = questions,
                        questionCount = questionCount,
                        categoryId = if (type == ShortSessionType.CATEGORY_FOCUSED) categoryId else null,
                        difficulty = if (type == ShortSessionType.CATEGORY_FOCUSED) difficulty else null
                    )
                }, enabled = names.all { it.isNotBlank() } && names.size in 1..6 && eligible.size >= questionCount,
                    modifier = Modifier.fillMaxWidth()) { Text("Start $title") }
                OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("Back") }
            }
            current.isComplete -> {
                Text("Session complete · maximum ${current.maxScore} points", style = MaterialTheme.typography.titleLarge)
                current.participants.forEach { participant ->
                    val correct = current.answers.count { it.participantId == participant.id && it.correct }
                    val assigned = current.questions.indices.count { it % current.participants.size == current.participants.indexOf(participant) }
                    Text("${participant.displayName}: ${current.scoreFor(participant.id)}/${assigned * 10} · $correct correct")
                }
                Text("Saved as ${if (type == ShortSessionType.QUICK_PLAY) "Quick Trivia" else "Category Practice"}; Full Game history and achievements are unchanged.")
                if (saveError) {
                    Text("This summary could not be saved.", color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { saveAttempt++ }, modifier = Modifier.fillMaxWidth()) { Text("Retry saving summary") }
                } else Text("Short session summary saved on this device.", color = MaterialTheme.colorScheme.primary)
                Button(onClick = {
                    session = null
                    lastSavedSessionId = null
                    saveError = false
                }, modifier = Modifier.fillMaxWidth()) { Text("Play again") }
                OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            }
            else -> ShortQuestionView(current, onStateChange = { session = it }, onExit = onExit)
        }
    }
}

@Composable
private fun ShortQuestionView(state: ShortTriviaSession, onStateChange: (ShortTriviaSession) -> Unit, onExit: () -> Unit) {
    val question = state.currentQuestion ?: return
    val participant = state.currentParticipant ?: return
    Text("Question ${state.index + 1} of ${state.questions.size} · ${participant.displayName}'s turn",
        style = MaterialTheme.typography.titleMedium)
    Text("Score: ${state.scoreFor(participant.id)} points", style = MaterialTheme.typography.bodyLarge)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(question.prompt, style = MaterialTheme.typography.titleLarge)
            question.choices.forEach { choice ->
                Row(Modifier.fillMaxWidth().selectable(
                    selected = state.selectedChoiceId == choice.id,
                    enabled = !state.showingFeedback,
                    role = Role.RadioButton,
                    onClick = { onStateChange(ShortTriviaReducer.selectChoice(state, choice.id)) }
                ), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(state.selectedChoiceId == choice.id,
                        onClick = { onStateChange(ShortTriviaReducer.selectChoice(state, choice.id)) },
                        enabled = !state.showingFeedback)
                    Text(choice.text, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
    if (state.showingFeedback) {
        val answer = state.answers.last()
        Text(if (answer.selectedChoiceId == null) "Skipped · 0 points" else if (answer.correct) "Correct · 10 points" else "Incorrect · 0 points",
            style = MaterialTheme.typography.titleMedium)
        Text("Correct answer: ${question.choices.first { it.id == question.correctChoiceId }.text}", style = MaterialTheme.typography.bodyLarge)
        question.explanation?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Button(onClick = { onStateChange(ShortTriviaReducer.continueAfterFeedback(state)) }, modifier = Modifier.fillMaxWidth()) {
            Text("Continue")
        }
    } else {
        Button(onClick = { onStateChange(ShortTriviaReducer.submit(state)) }, enabled = state.selectedChoiceId != null,
            modifier = Modifier.fillMaxWidth()) { Text("Submit answer") }
        OutlinedButton(onClick = { onStateChange(ShortTriviaReducer.skip(state)) }, modifier = Modifier.fillMaxWidth()) {
            Text("Skip question · 0 points")
        }
    }
    TextButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("Leave session") }
}
