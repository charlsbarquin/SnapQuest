package com.charlsemilbarquin.frameandfact.feature.practice

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.charlsemilbarquin.frameandfact.domain.game.ApprovedCategories
import com.charlsemilbarquin.frameandfact.domain.game.ChoiceId
import com.charlsemilbarquin.frameandfact.domain.game.Difficulty
import com.charlsemilbarquin.frameandfact.domain.game.TriviaQuestion
import com.charlsemilbarquin.frameandfact.ui.theme.FrameFactSpacing

private enum class PracticeScreenMode { QUESTION, RESULTS, REVIEW }

@Composable
fun PracticeScreen(
    questions: List<TriviaQuestion>,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var configuration by remember(questions) { mutableStateOf(PracticeConfiguration()) }
    var session by remember(questions) { mutableStateOf<PracticeSession?>(null) }
    var screenMode by remember(questions) { mutableStateOf(PracticeScreenMode.QUESTION) }
    var reviewIndex by remember(questions) { mutableIntStateOf(0) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    var setupError by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = showExitConfirmation) { showExitConfirmation = false }
    BackHandler(enabled = session != null && !showExitConfirmation) { showExitConfirmation = true }

    val startedSession = session
    val scrollState = rememberScrollState()
    LaunchedEffect(startedSession == null, startedSession?.index, screenMode) {
        scrollState.scrollTo(0)
    }
    val requestExit = {
        if (startedSession == null) onExit() else showExitConfirmation = true
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(scrollState).padding(FrameFactSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(FrameFactSpacing.contentGap)
    ) {
        when {
            startedSession == null -> PracticeSetup(
                questions = questions,
                configuration = configuration,
                onConfigurationChange = { configuration = it; setupError = null },
                setupError = setupError,
                onStart = {
                    val newSession = PracticeQuestionSelector.createSession(questions, configuration)
                    if (newSession == null) {
                        setupError = "Practice could not start with this selection. Adjust your filters or choose All available."
                    } else {
                        session = newSession
                        screenMode = PracticeScreenMode.QUESTION
                        reviewIndex = 0
                    }
                },
                onBack = onExit
            )
            screenMode == PracticeScreenMode.REVIEW -> PracticeIncorrectReview(
                answers = startedSession.incorrectAnswers,
                index = reviewIndex,
                onIndexChange = { reviewIndex = it },
                onBackToResults = { screenMode = PracticeScreenMode.RESULTS },
                onExit = requestExit
            )
            startedSession.isFinished || screenMode == PracticeScreenMode.RESULTS -> PracticeResults(
                session = startedSession,
                onReviewIncorrectAnswers = { reviewIndex = 0; screenMode = PracticeScreenMode.REVIEW },
                onPracticeAgain = {
                    session = null
                    screenMode = PracticeScreenMode.QUESTION
                    reviewIndex = 0
                },
                onExit = requestExit
            )
            else -> PracticeQuestion(
                session = startedSession,
                onSelectChoice = { choiceId -> session = PracticeReducer.selectChoice(startedSession, choiceId) },
                onSubmit = { session = PracticeReducer.submit(startedSession) },
                onContinue = {
                    session = PracticeReducer.continueAfterFeedback(startedSession)
                    if (startedSession.isLastQuestion) screenMode = PracticeScreenMode.RESULTS
                },
                onExit = requestExit
            )
        }
    }

    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("Exit practice?") },
            text = { Text("Your current practice progress will be discarded. It will not be saved to Game History.") },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirmation = false
                    session = null
                    screenMode = PracticeScreenMode.QUESTION
                    onExit()
                }) { Text("Exit practice") }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) { Text("Keep practicing") }
            }
        )
    }
}

@Composable
private fun PracticeSetup(
    questions: List<TriviaQuestion>,
    configuration: PracticeConfiguration,
    onConfigurationChange: (PracticeConfiguration) -> Unit,
    setupError: String?,
    onStart: () -> Unit,
    onBack: () -> Unit
) {
    val available = PracticeQuestionSelector.availableQuestions(questions, configuration)
    Text("Practice", style = MaterialTheme.typography.headlineMedium)
    Text("Practice is unscored and is not saved to Full Game History.", style = MaterialTheme.typography.bodyMedium)

    Text("Category", style = MaterialTheme.typography.titleMedium)
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        PracticeRadioRow(
            label = "All Categories",
            selected = configuration.categoryId == null,
            onClick = { onConfigurationChange(configuration.copy(categoryId = null)) }
        )
        ApprovedCategories.all.forEach { category ->
            PracticeRadioRow(
                label = category.name,
                selected = configuration.categoryId == category.id,
                onClick = { onConfigurationChange(configuration.copy(categoryId = category.id)) }
            )
        }
    }

    Text("Difficulty", style = MaterialTheme.typography.titleMedium)
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        PracticeRadioRow(
            label = "All Difficulties",
            selected = configuration.difficulty == null,
            onClick = { onConfigurationChange(configuration.copy(difficulty = null)) }
        )
        Difficulty.entries.forEach { level ->
            PracticeRadioRow(
                label = level.displayName(),
                selected = configuration.difficulty == level,
                onClick = { onConfigurationChange(configuration.copy(difficulty = level)) }
            )
        }
    }

    Text("Number of questions", style = MaterialTheme.typography.titleMedium)
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        PracticeQuestionCount.entries.forEach { count ->
            val fixedCount = count.fixedCount
            val enabled = if (fixedCount == null) available.isNotEmpty() else available.size >= fixedCount
            PracticeRadioRow(
                label = if (fixedCount != null && available.size < fixedCount) {
                    "${count.label} (not enough available)"
                } else count.label,
                selected = configuration.questionCount == count,
                enabled = enabled,
                onClick = { onConfigurationChange(configuration.copy(questionCount = count)) }
            )
        }
    }

    Text("Questions matching filters: ${available.size}", style = MaterialTheme.typography.bodyLarge)
    configuration.shortageMessage(available.size)?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
    if (available.isEmpty()) {
        Text("No questions match these filters. Choose another category or difficulty.", style = MaterialTheme.typography.bodyMedium)
    }
    setupError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
    Button(
        onClick = onStart,
        enabled = configuration.canStart(available.size),
        modifier = Modifier.fillMaxWidth()
    ) { Text("Start Practice") }
    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
}

@Composable
private fun PracticeQuestion(
    session: PracticeSession,
    onSelectChoice: (ChoiceId) -> Unit,
    onSubmit: () -> Unit,
    onContinue: () -> Unit,
    onExit: () -> Unit
) {
    val question = session.currentQuestion ?: return
    val category = ApprovedCategories.all.firstOrNull { it.id == question.categoryId }?.name ?: question.categoryId.value
    Text("Practice", style = MaterialTheme.typography.headlineMedium)
    Text("Question ${session.index + 1} of ${session.questions.size}", style = MaterialTheme.typography.labelLarge)
    Text("$category · ${question.difficulty.displayName()}", style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Card(Modifier.fillMaxWidth()) {
        Text(question.prompt, Modifier.padding(20.dp), style = MaterialTheme.typography.headlineSmall)
    }
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        question.choices.forEach { choice ->
            val isSelected = session.selectedChoiceId == choice.id
            val correct = choice.id == question.correctChoiceId
            val answerLabel = when {
                !session.showingFeedback -> choice.text
                correct && isSelected -> "${choice.text} — correct answer and your selection"
                correct -> "${choice.text} — correct answer"
                isSelected -> "${choice.text} — your incorrect selection"
                else -> choice.text
            }
            Row(
                modifier = Modifier.fillMaxWidth()
                    .selectable(
                        selected = isSelected,
                        enabled = !session.showingFeedback,
                        role = Role.RadioButton,
                        onClick = { onSelectChoice(choice.id) }
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = isSelected, onClick = null, enabled = !session.showingFeedback)
                Text(answerLabel, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }

    if (session.showingFeedback) {
        val answer = session.answers.lastOrNull()
        val correctText = question.choices.firstOrNull { it.id == question.correctChoiceId }?.text
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (answer?.wasCorrect == true) "Correct" else "Incorrect",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (answer?.wasCorrect == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                correctText?.let { Text("Correct answer: $it", style = MaterialTheme.typography.bodyLarge) }
                Text("Explanation", style = MaterialTheme.typography.titleMedium)
                Text(question.explanation?.takeIf(String::isNotBlank) ?: "No explanation is available for this question.",
                    style = MaterialTheme.typography.bodyLarge)
            }
        }
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text(if (session.isLastQuestion) "Finish Practice" else "Next Question")
        }
    } else {
        Button(onClick = onSubmit, enabled = session.selectedChoiceId != null, modifier = Modifier.fillMaxWidth()) {
            Text("Submit Answer")
        }
    }
    OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("Exit Practice") }
}

@Composable
private fun PracticeResults(
    session: PracticeSession,
    onReviewIncorrectAnswers: () -> Unit,
    onPracticeAgain: () -> Unit,
    onExit: () -> Unit
) {
    Text("Practice Results", style = MaterialTheme.typography.headlineMedium)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Questions answered: ${session.answeredCount}", style = MaterialTheme.typography.bodyLarge)
            Text("Correct: ${session.correctCount}", style = MaterialTheme.typography.bodyLarge)
            Text("Incorrect: ${session.incorrectCount}", style = MaterialTheme.typography.bodyLarge)
            Text("Accuracy: ${session.accuracyPercent}%", style = MaterialTheme.typography.titleLarge)
        }
    }
    if (session.incorrectAnswers.isEmpty()) {
        Text("You got every question right. There are no incorrect answers to review.", style = MaterialTheme.typography.bodyLarge)
    } else {
        Text("Incorrect answers", style = MaterialTheme.typography.titleLarge)
        session.incorrectAnswers.forEach { answer ->
            PracticeIncorrectAnswerCard(answer)
        }
    }
    Button(onClick = onReviewIncorrectAnswers, modifier = Modifier.fillMaxWidth()) { Text("Review Incorrect Answers") }
    OutlinedButton(onClick = onPracticeAgain, modifier = Modifier.fillMaxWidth()) { Text("Practice Again") }
    OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("Return Home") }
}

@Composable
private fun PracticeIncorrectReview(
    answers: List<PracticeAnswer>,
    index: Int,
    onIndexChange: (Int) -> Unit,
    onBackToResults: () -> Unit,
    onExit: () -> Unit
) {
    Text("Review Incorrect Answers", style = MaterialTheme.typography.headlineMedium)
    if (answers.isEmpty()) {
        Text("You got every question right. There are no incorrect answers to review.", style = MaterialTheme.typography.bodyLarge)
    } else {
        val safeIndex = index.coerceIn(answers.indices)
        val answer = answers[safeIndex]
        Text("${safeIndex + 1} of ${answers.size}", style = MaterialTheme.typography.titleMedium)
        PracticeIncorrectAnswerCard(answer)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { onIndexChange(safeIndex - 1) }, enabled = safeIndex > 0, modifier = Modifier.weight(1f)
            ) { Text("Previous") }
            OutlinedButton(
                onClick = { onIndexChange(safeIndex + 1) }, enabled = safeIndex < answers.lastIndex, modifier = Modifier.weight(1f)
            ) { Text("Next") }
        }
    }
    OutlinedButton(onClick = onBackToResults, modifier = Modifier.fillMaxWidth()) { Text("Back to Results") }
    OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("Exit Practice") }
}

@Composable
private fun PracticeIncorrectAnswerCard(answer: PracticeAnswer) {
    val question = answer.question
    val category = ApprovedCategories.all.firstOrNull { it.id == question.categoryId }?.name ?: question.categoryId.value
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$category · ${question.difficulty.displayName()}", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(question.prompt, style = MaterialTheme.typography.titleLarge)
            Text("Your answer (incorrect): ${answer.selectedAnswer ?: "Unavailable"}", style = MaterialTheme.typography.bodyLarge)
            Text("Correct answer: ${answer.correctAnswer ?: "Unavailable"}", style = MaterialTheme.typography.bodyLarge)
            Text("Explanation", style = MaterialTheme.typography.titleMedium)
            Text(question.explanation?.takeIf(String::isNotBlank) ?: "No explanation is available for this question.",
                style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun PracticeRadioRow(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun Difficulty.displayName(): String = name.lowercase().replaceFirstChar(Char::uppercase)

