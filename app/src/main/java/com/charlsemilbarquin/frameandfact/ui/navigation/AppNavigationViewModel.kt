package com.charlsemilbarquin.frameandfact.ui.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.charlsemilbarquin.frameandfact.data.history.CompletedGameHistoryRecord
import com.charlsemilbarquin.frameandfact.domain.game.GameSession
import com.charlsemilbarquin.frameandfact.domain.game.QuestionId
import com.charlsemilbarquin.frameandfact.feature.gameplay.TriviaGameplayState
import com.charlsemilbarquin.frameandfact.feature.history.CompletionSaveUiState
import com.charlsemilbarquin.frameandfact.feature.setup.GameSetupDraft
import com.charlsemilbarquin.frameandfact.feature.setup.SetupContentState

internal enum class AppRoute(val title: String) {
    HOME("SnapQuest"),
    SETUP("Game Setup"),
    PRACTICE("Practice"),
    QUESTION_REPORTS("Question Reports"),
    PROFILES("Player Profiles"),
    QUICK_PLAY("Quick Trivia"),
    CATEGORY_FOCUSED("Category Practice"),
    SHORT_HISTORY("Short Session History"),
    HELP("Rules & Help"),
    TUTORIAL("Getting Started"),
    GAMEPLAY("SnapQuest"),
    HISTORY("Game History"),
    STATISTICS("Statistics"),
    HISTORY_DETAIL("Game Details"),
    INCORRECT_ANSWER_REVIEW("Review Incorrect Answers"),
    SETTINGS("Settings")
}

/** Keeps the active navigation and game state alive while MainActivity is recreated for rotation. */
class AppNavigationViewModel : ViewModel() {
    internal val backStack = mutableStateListOf(AppRoute.HOME)
    internal val setupDraft = mutableStateOf(GameSetupDraft.create())
    internal val contentState = mutableStateOf<SetupContentState>(SetupContentState.Loading)
    internal val createdSession = mutableStateOf<GameSession?>(null)
    internal val formMessage = mutableStateOf<String?>(null)
    internal val gameplayState = mutableStateOf<TriviaGameplayState?>(null)
    internal val showCancelConfirmation = mutableStateOf(false)
    internal val completionSaveState = mutableStateOf<CompletionSaveUiState>(CompletionSaveUiState.NotStarted)
    internal val completionRecord = mutableStateOf<CompletedGameHistoryRecord?>(null)
    internal val recentlyEncounteredQuestionIds = mutableStateOf<Set<QuestionId>>(emptySet())
    internal val quickPlayRequested = mutableStateOf(false)

    private var startupPhotoCleanupClaimed = false

    /** Photo cleanup is an app-entry operation; do not delete a reviewed photo during rotation. */
    internal fun claimStartupPhotoCleanup(): Boolean {
        if (startupPhotoCleanupClaimed) return false
        startupPhotoCleanupClaimed = true
        return true
    }
}
