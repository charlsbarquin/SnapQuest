package com.charlsemilbarquin.frameandfact.ui.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import com.charlsemilbarquin.frameandfact.data.history.CompletedGameHistoryRecord
import com.charlsemilbarquin.frameandfact.domain.game.GameSession
import com.charlsemilbarquin.frameandfact.domain.game.QuestionId
import com.charlsemilbarquin.frameandfact.domain.game.RoundId
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
    SETTINGS("Settings"),
    ABOUT("About SnapQuest")
}

/** Keeps the active navigation and game state alive while MainActivity is recreated for rotation. */
class AppNavigationViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
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
    internal fun resetAfterLogout() {
        backStack.clear()
        backStack.add(AppRoute.HOME)
        setupDraft.value = GameSetupDraft.create()
        createdSession.value = null
        formMessage.value = null
        gameplayState.value = null
        showCancelConfirmation.value = false
        completionSaveState.value = CompletionSaveUiState.NotStarted
        completionRecord.value = null
        savedStateHandle.remove<String>(SAVED_GAME_JSON_KEY)
        savedStateHandle.remove<String>(SAVED_GAME_ACCOUNT_KEY)
    }

    /** Stores only the structured game state, never a captured camera image or credentials. */
    internal fun persistGameplayState(accountId: String, state: TriviaGameplayState?) {
        if (state == null || state.session.status == com.charlsemilbarquin.frameandfact.domain.game.GameStatus.CANCELLED) {
            if (savedStateHandle.get<String>(SAVED_GAME_ACCOUNT_KEY) == accountId) {
                savedStateHandle.remove<String>(SAVED_GAME_JSON_KEY)
                savedStateHandle.remove<String>(SAVED_GAME_ACCOUNT_KEY)
            }
            return
        }
        savedStateHandle[SAVED_GAME_ACCOUNT_KEY] = accountId
        savedStateHandle[SAVED_GAME_JSON_KEY] = GAME_STATE_GSON.toJson(state)
    }

    /** Restores a saved game only for the account that started it. */
    internal fun restoreGameplayState(accountId: String) {
        if (savedStateHandle.get<String>(SAVED_GAME_ACCOUNT_KEY) != accountId) {
            savedStateHandle.remove<String>(SAVED_GAME_JSON_KEY)
            savedStateHandle.remove<String>(SAVED_GAME_ACCOUNT_KEY)
            return
        }
        val json = savedStateHandle.get<String>(SAVED_GAME_JSON_KEY) ?: return
        val restored = runCatching { GAME_STATE_GSON.fromJson(json, TriviaGameplayState::class.java) }.getOrNull()
        if (restored == null || restored.session.status == com.charlsemilbarquin.frameandfact.domain.game.GameStatus.CANCELLED) {
            savedStateHandle.remove<String>(SAVED_GAME_JSON_KEY)
            savedStateHandle.remove<String>(SAVED_GAME_ACCOUNT_KEY)
            return
        }
        gameplayState.value = restored
        createdSession.value = restored.session
        backStack.clear()
        backStack.add(AppRoute.HOME)
        backStack.add(AppRoute.GAMEPLAY)
    }

    /** Photo cleanup is an app-entry operation; do not delete a reviewed photo during rotation. */
    internal fun claimStartupPhotoCleanup(): Boolean {
        if (startupPhotoCleanupClaimed) return false
        startupPhotoCleanupClaimed = true
        return true
    }

    internal companion object {
        const val SAVED_GAME_JSON_KEY = "snapquest_saved_game_json"
        const val SAVED_GAME_ACCOUNT_KEY = "snapquest_saved_game_account"
        private val GAME_STATE_GSON: Gson = GsonBuilder()
            .enableComplexMapKeySerialization()
            .registerTypeAdapter(RoundId::class.java, object : TypeAdapter<RoundId>() {
                override fun write(out: JsonWriter, value: RoundId?) {
                    if (value == null) out.nullValue() else out.value(value.number)
                }

                override fun read(input: JsonReader): RoundId? = when (input.peek()) {
                    JsonToken.NULL -> { input.nextNull(); null }
                    JsonToken.NUMBER, JsonToken.STRING -> RoundId(input.nextString().toInt())
                    else -> error("Invalid saved round identifier")
                }
            })
            .create()
    }
}
