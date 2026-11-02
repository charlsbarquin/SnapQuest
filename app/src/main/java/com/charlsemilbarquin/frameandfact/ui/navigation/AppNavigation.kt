package com.charlsemilbarquin.frameandfact.ui.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import com.charlsemilbarquin.frameandfact.data.content.AndroidAssetContentReader
import com.charlsemilbarquin.frameandfact.data.content.BundledContentLoader
import com.charlsemilbarquin.frameandfact.data.history.CompletedGameHistoryRecord
import com.charlsemilbarquin.frameandfact.data.history.CompletedGameHistorySaver
import com.charlsemilbarquin.frameandfact.data.history.CompletedGameSaveAttempt
import com.charlsemilbarquin.frameandfact.data.history.GameHistoryRepository
import com.charlsemilbarquin.frameandfact.data.history.RoomGameHistoryRepository
import com.charlsemilbarquin.frameandfact.data.history.QuestionIssueType
import com.charlsemilbarquin.frameandfact.data.history.QuestionReport
import com.charlsemilbarquin.frameandfact.data.history.RoomQuestionReportRepository
import com.charlsemilbarquin.frameandfact.data.history.LocalPlayerProfile
import com.charlsemilbarquin.frameandfact.data.history.RoomLocalPlayerProfileRepository
import com.charlsemilbarquin.frameandfact.data.history.FullGameStatisticsCalculator
import com.charlsemilbarquin.frameandfact.data.history.UnlockedAchievement
import com.charlsemilbarquin.frameandfact.data.history.ShortGameSummary
import com.charlsemilbarquin.frameandfact.data.history.ShortGameSummaryRepository
import com.charlsemilbarquin.frameandfact.data.history.RoomShortGameSummaryRepository
import com.charlsemilbarquin.frameandfact.data.local.history.GameHistoryDatabase
import com.charlsemilbarquin.frameandfact.data.settings.AppSettingsRepository
import com.charlsemilbarquin.frameandfact.data.settings.SettingsReadState
import com.charlsemilbarquin.frameandfact.data.settings.ThemePreference
import com.charlsemilbarquin.frameandfact.data.settings.ThemeWriteResult
import com.charlsemilbarquin.frameandfact.data.settings.TutorialWriteResult
import com.charlsemilbarquin.frameandfact.feature.setup.GameSetupDraft
import com.charlsemilbarquin.frameandfact.feature.setup.CategorySelection
import com.charlsemilbarquin.frameandfact.feature.setup.GameSetupScreen
import com.charlsemilbarquin.frameandfact.feature.setup.GameSetupSessionCreator
import com.charlsemilbarquin.frameandfact.feature.setup.SessionCreationResult
import com.charlsemilbarquin.frameandfact.feature.setup.SetupContentState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.charlsemilbarquin.frameandfact.ui.theme.FrameFactSpacing
import com.charlsemilbarquin.frameandfact.ui.components.SnapQuestWordmark
import com.charlsemilbarquin.frameandfact.ui.components.SnapQuestBackButton
import com.charlsemilbarquin.frameandfact.ui.components.SnapQuestMark
import com.charlsemilbarquin.frameandfact.ui.components.SnapQuestSectionHeader
import com.charlsemilbarquin.frameandfact.R
import com.charlsemilbarquin.frameandfact.domain.game.GameAction
import com.charlsemilbarquin.frameandfact.domain.game.GameActionResult
import com.charlsemilbarquin.frameandfact.domain.game.GameChange
import com.charlsemilbarquin.frameandfact.domain.game.GameEngine
import com.charlsemilbarquin.frameandfact.domain.game.GameMode
import com.charlsemilbarquin.frameandfact.domain.game.GameStatus
import com.charlsemilbarquin.frameandfact.domain.game.GameEvent
import com.charlsemilbarquin.frameandfact.domain.game.RoundId
import com.charlsemilbarquin.frameandfact.domain.game.RoundType
import com.charlsemilbarquin.frameandfact.domain.game.ShortSessionType
import com.charlsemilbarquin.frameandfact.feature.camera.CameraPhotoStore
import com.charlsemilbarquin.frameandfact.feature.gameplay.TriviaGameplayReducer
import com.charlsemilbarquin.frameandfact.feature.gameplay.TriviaGameplayScreen
import com.charlsemilbarquin.frameandfact.feature.gameplay.TriviaGameplayState
import com.charlsemilbarquin.frameandfact.feature.history.CompletionSaveUiState
import com.charlsemilbarquin.frameandfact.feature.history.HistoryScreen
import com.charlsemilbarquin.frameandfact.feature.history.HistoryGameDetailScreen
import com.charlsemilbarquin.frameandfact.feature.history.IncorrectAnswerReviewScreen
import com.charlsemilbarquin.frameandfact.feature.history.HistoryUiState
import com.charlsemilbarquin.frameandfact.feature.history.historyUiState
import com.charlsemilbarquin.frameandfact.feature.practice.PracticeScreen
import com.charlsemilbarquin.frameandfact.feature.reports.QuestionReportsScreen
import com.charlsemilbarquin.frameandfact.feature.profile.PlayerProfilesScreen
import com.charlsemilbarquin.frameandfact.feature.statistics.StatisticsScreen
import com.charlsemilbarquin.frameandfact.feature.shortplay.ShortTriviaScreen
import com.charlsemilbarquin.frameandfact.feature.history.ShortGameHistoryScreen
import com.charlsemilbarquin.frameandfact.feature.help.RulesHelpScreen
import com.charlsemilbarquin.frameandfact.feature.help.InteractiveTutorialScreen
import com.charlsemilbarquin.frameandfact.feature.settings.ClearHistoryUiState
import com.charlsemilbarquin.frameandfact.feature.settings.SettingsScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    settingsRepository: AppSettingsRepository,
    settingsReadState: SettingsReadState,
    navigationViewModel: AppNavigationViewModel
) {
    val backStack = navigationViewModel.backStack
    var setupDraft by navigationViewModel.setupDraft
    var contentState by navigationViewModel.contentState
    var createdSession by navigationViewModel.createdSession
    var formMessage by navigationViewModel.formMessage
    var gameplayState by navigationViewModel.gameplayState
    var showCancelConfirmation by navigationViewModel.showCancelConfirmation
    var contentReloadToken by remember { mutableStateOf(0) }
    var historyReloadToken by remember { mutableStateOf(0) }
    var historyState by remember { mutableStateOf<HistoryUiState>(HistoryUiState.Loading) }
    var completionSaveState by navigationViewModel.completionSaveState
    var completionRecord by navigationViewModel.completionRecord
    var selectedHistoryGameId by remember { mutableStateOf<String?>(null) }
    var selectedReportQuestionId by remember { mutableStateOf<String?>(null) }
    var questionReports by remember { mutableStateOf<List<QuestionReport>>(emptyList()) }
    var profiles by remember { mutableStateOf<List<LocalPlayerProfile>>(emptyList()) }
    var profileHistory by remember { mutableStateOf<List<CompletedGameHistoryRecord>>(emptyList()) }
    var profileAchievements by remember { mutableStateOf<Map<String, List<UnlockedAchievement>>>(emptyMap()) }
    var shortGameSummaries by remember { mutableStateOf<List<ShortGameSummary>>(emptyList()) }
    var recentlyEncounteredQuestionIds by navigationViewModel.recentlyEncounteredQuestionIds
    var quickPlayRequested by navigationViewModel.quickPlayRequested
    var themeWriteResult by remember { mutableStateOf<ThemeWriteResult?>(null) }
    var isThemeSaving by remember { mutableStateOf(false) }
    var isTutorialSaving by remember { mutableStateOf(false) }
    var tutorialSaveError by remember { mutableStateOf<String?>(null) }
    var automaticTutorialChecked by remember { mutableStateOf(false) }
    var clearHistoryState by remember { mutableStateOf<ClearHistoryUiState>(ClearHistoryUiState.Idle) }
    val context = LocalContext.current
    val photoStore = remember(context.cacheDir) { CameraPhotoStore(context.cacheDir) }
    val historyRepository: GameHistoryRepository = remember(context.applicationContext) {
        RoomGameHistoryRepository(GameHistoryDatabase.getInstance(context.applicationContext))
    }
    val completionSaver = remember(historyRepository) { CompletedGameHistorySaver(historyRepository) }
    val questionReportRepository = remember(context.applicationContext) {
        RoomQuestionReportRepository(GameHistoryDatabase.getInstance(context.applicationContext))
    }
    val profileRepository = remember(context.applicationContext) {
        RoomLocalPlayerProfileRepository(GameHistoryDatabase.getInstance(context.applicationContext))
    }
    val shortGameSummaryRepository: ShortGameSummaryRepository = remember(context.applicationContext) {
        RoomShortGameSummaryRepository(GameHistoryDatabase.getInstance(context.applicationContext))
    }
    val clearHistoryGate = remember { AtomicBoolean(false) }
    val coroutineScope = rememberCoroutineScope()
    val currentRoute = backStack.last()
    val canNavigateBack = backStack.size > 1

    fun navigateTo(route: AppRoute) {
        if (route != currentRoute) backStack.add(route)
    }

    fun navigateBack() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    fun navigateToRoot(route: AppRoute) {
        backStack.clear()
        backStack.add(route)
    }

    fun finishCompletedGame() {
        if (gameplayState?.session?.status == GameStatus.COMPLETED) {
            gameplayState = null
            createdSession = null
            backStack.clear()
            backStack.add(AppRoute.HOME)
            coroutineScope.launch(Dispatchers.IO) { photoStore.deleteAllCaptures() }
        }
    }

    fun createQuickPlay() {
        val draft = GameSetupDraft.quickPlay()
        when (val result = GameSetupSessionCreator.create(
            draft = draft,
            contentState = contentState,
            recentlyEncounteredQuestionIds = recentlyEncounteredQuestionIds
        )) {
            is SessionCreationResult.Created -> {
                setupDraft = draft
                createdSession = result.session
                gameplayState = TriviaGameplayState(result.session)
                completionSaveState = CompletionSaveUiState.NotStarted
                completionRecord = null
                quickPlayRequested = false
                navigateTo(AppRoute.GAMEPLAY)
                coroutineScope.launch(Dispatchers.IO) { photoStore.deleteAllCaptures() }
            }
            is SessionCreationResult.Invalid -> {
                quickPlayRequested = false
                setupDraft = draft
                formMessage = result.issues.joinToString("\n") { it.message }
                navigateTo(AppRoute.SETUP)
            }
            is SessionCreationResult.ContentUnavailable -> {
                if (contentState is SetupContentState.Loading) return
                quickPlayRequested = false
                setupDraft = draft
                formMessage = result.message
                navigateTo(AppRoute.SETUP)
            }
            is SessionCreationResult.FactoryRejected -> {
                quickPlayRequested = false
                setupDraft = draft
                formMessage = result.message
                navigateTo(AppRoute.SETUP)
            }
        }
    }

    fun playAgainSolo() {
        val current = gameplayState?.session ?: return
        if (current.mode != GameMode.SOLO || current.status != GameStatus.COMPLETED) return
        when (val result = GameSetupSessionCreator.create(setupDraft, contentState)) {
            is SessionCreationResult.Created -> {
                val freshSession = result.session
                createdSession = freshSession
                gameplayState = TriviaGameplayState(freshSession)
                completionSaveState = CompletionSaveUiState.NotStarted
                completionRecord = null
                coroutineScope.launch(Dispatchers.IO) { photoStore.deleteAllCaptures() }
            }
            is SessionCreationResult.Invalid -> {
                formMessage = result.issues.joinToString("\n") { it.message }
                navigateTo(AppRoute.SETUP)
            }
            is SessionCreationResult.ContentUnavailable -> {
                formMessage = result.message
                navigateTo(AppRoute.SETUP)
            }
            is SessionCreationResult.FactoryRejected -> {
                formMessage = result.message
                navigateTo(AppRoute.SETUP)
            }
        }
    }

    fun requestBack() {
        if (currentRoute == AppRoute.GAMEPLAY) {
            when (gameplayState?.session?.status) {
                GameStatus.ACTIVE -> showCancelConfirmation = true
                GameStatus.COMPLETED -> {
                    if (completionSaveState == CompletionSaveUiState.Saved) finishCompletedGame()
                }
                else -> navigateBack()
            }
        } else {
            navigateBack()
        }
    }

    fun finishTutorial() {
        if (isTutorialSaving) return
        tutorialSaveError = null
        coroutineScope.launch {
            isTutorialSaving = true
            try {
                when (val result = withContext(Dispatchers.IO) { settingsRepository.markTutorialCompleted() }) {
                    TutorialWriteResult.Saved -> {
                        if (backStack.lastOrNull() == AppRoute.TUTORIAL) backStack.removeAt(backStack.lastIndex)
                    }
                    is TutorialWriteResult.Failed -> tutorialSaveError = result.message
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                tutorialSaveError = "Tutorial completion could not be saved. Please try again."
            } finally {
                isTutorialSaving = false
            }
        }
    }

    fun confirmCancelGame() {
        val session = gameplayState?.session ?: return
        val result = GameEngine.apply(session, GameAction.CancelGameConfirmed)
        if (result is GameActionResult.Applied && result.change == GameChange.GAME_CANCELLED) {
            gameplayState = null
            createdSession = null
            showCancelConfirmation = false
            backStack.clear()
            backStack.add(AppRoute.HOME)
            coroutineScope.launch(Dispatchers.IO) { photoStore.deleteAllCaptures() }
        }
    }

    fun showSaveAttempt(attempt: CompletedGameSaveAttempt) {
        when (attempt) {
            is CompletedGameSaveAttempt.Saved -> {
                completionRecord = attempt.record
                completionSaveState = CompletionSaveUiState.Saved
            }
            is CompletedGameSaveAttempt.Failed -> {
                completionRecord = attempt.record
                completionSaveState = CompletionSaveUiState.Failed(
                    "This game could not be saved to History. Your score is unchanged. Retry the save or return home without saving."
                )
            }
            CompletedGameSaveAttempt.NotCompleted -> {
                completionSaveState = CompletionSaveUiState.Failed(
                    "The completed game summary was incomplete, so it was not saved."
                )
            }
        }
    }

    fun persistCompletedRecord(record: CompletedGameHistoryRecord) {
        completionSaveState = CompletionSaveUiState.Saving
        coroutineScope.launch {
            showSaveAttempt(withContext(Dispatchers.IO) { completionSaver.retry(record) })
        }
    }

    fun updateGameplayState(updated: TriviaGameplayState) {
        val previous = gameplayState
        gameplayState = updated
        if (previous?.session?.id == updated.session.id &&
            previous.session.status != GameStatus.COMPLETED && updated.session.status == GameStatus.COMPLETED
        ) {
            completionSaveState = CompletionSaveUiState.Saving
            coroutineScope.launch {
                showSaveAttempt(withContext(Dispatchers.IO) { completionSaver.saveIfCompleted(updated.session) })
            }
        }
    }

    fun retryCompletedRecordSave() {
        completionRecord?.let(::persistCompletedRecord)
    }

    fun updateTheme(theme: ThemePreference) {
        if (isThemeSaving) return
        isThemeSaving = true
        themeWriteResult = null
        coroutineScope.launch {
            themeWriteResult = withContext(Dispatchers.IO) { settingsRepository.setTheme(theme) }
            isThemeSaving = false
        }
    }

    fun clearSavedHistory() {
        if (currentRoute != AppRoute.SETTINGS || !clearHistoryGate.compareAndSet(false, true)) return
        clearHistoryState = ClearHistoryUiState.Deleting
        coroutineScope.launch {
            try {
                val removed = withContext(Dispatchers.IO) { historyRepository.clearHistory() }
                clearHistoryState = ClearHistoryUiState.Deleted(removed)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                clearHistoryState = ClearHistoryUiState.Failed(
                    "History could not be cleared. Your saved records are still available. Please try again."
                )
            } finally {
                clearHistoryGate.set(false)
            }
        }
    }

    fun applyCameraEvent(gameId: com.charlsemilbarquin.frameandfact.domain.game.GameId, roundId: RoundId, event: GameEvent): Boolean {
        val current = gameplayState ?: return false
        val currentRound = current.session.currentRound ?: return false
        if (current.session.id != gameId || currentRound.id != roundId ||
            currentRound.type != RoundType.FIND_AND_FRAME || event.roundId != roundId
        ) return false

        val updated = when (event) {
            is GameEvent.CameraAccepted -> TriviaGameplayReducer.resolveCameraRound(current, accepted = true)
            is GameEvent.CameraRejected -> TriviaGameplayReducer.resolveCameraRound(current, accepted = false)
            is GameEvent.SkipCameraRound -> TriviaGameplayReducer.skipCameraRound(current)
            is GameEvent.CancelPhoto -> TriviaGameplayReducer.cancelPhoto(current)
            else -> return false
        }
        updateGameplayState(updated)
        if (updated.session.status == GameStatus.COMPLETED) {
            coroutineScope.launch(Dispatchers.IO) { photoStore.deleteAllCaptures() }
        }
        return true
    }

    LaunchedEffect(navigationViewModel) {
        if (navigationViewModel.claimStartupPhotoCleanup()) {
            withContext(Dispatchers.IO) { photoStore.cleanupStaleCaptures() }
        }
    }

    LaunchedEffect(historyRepository) {
        recentlyEncounteredQuestionIds = runCatching {
            withContext(Dispatchers.IO) { historyRepository.recentlyEncounteredQuestionIds(limit = 12) }
        }.getOrDefault(emptySet())
    }

    LaunchedEffect(contentReloadToken) {
        if (contentReloadToken == 0 && contentState is SetupContentState.Available) return@LaunchedEffect
        contentState = SetupContentState.Loading
        contentState = try {
            SetupContentState.Available(withContext(Dispatchers.IO) {
                BundledContentLoader(AndroidAssetContentReader(context.assets)).load()
            })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            SetupContentState.Unavailable(
                "Offline game content could not be loaded or validated. ${error.message ?: "Please retry."}"
            )
        }
    }

    LaunchedEffect(quickPlayRequested, contentState) {
        if (quickPlayRequested) {
            when (contentState) {
                is SetupContentState.Available -> createQuickPlay()
                is SetupContentState.Unavailable -> {
                    quickPlayRequested = false
                    setupDraft = GameSetupDraft.quickPlay()
                    formMessage = (contentState as SetupContentState.Unavailable).message
                    navigateTo(AppRoute.SETUP)
                }
                SetupContentState.Loading -> Unit
            }
        }
    }

    LaunchedEffect(currentRoute, historyReloadToken) {
        if (currentRoute == AppRoute.HISTORY || currentRoute == AppRoute.HISTORY_DETAIL ||
            currentRoute == AppRoute.STATISTICS ||
            currentRoute == AppRoute.INCORRECT_ANSWER_REVIEW
        ) {
            historyState = HistoryUiState.Loading
            try {
                historyRepository.observeCompletedGames().collect { games ->
                    historyState = historyUiState(games)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                historyState = HistoryUiState.Error("Saved game history could not be loaded. Please retry.")
            }
        }
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute == AppRoute.QUESTION_REPORTS) {
            questionReportRepository.observeReports().collect { questionReports = it }
        }
        if (currentRoute == AppRoute.SHORT_HISTORY) {
            shortGameSummaryRepository.observeSummaries().collect { shortGameSummaries = it }
        }
    }

    LaunchedEffect(settingsReadState, currentRoute) {
        val ready = settingsReadState as? SettingsReadState.Ready ?: return@LaunchedEffect
        if (!automaticTutorialChecked && currentRoute == AppRoute.HOME) {
            automaticTutorialChecked = true
            if (!ready.settings.tutorialCompleted) backStack.add(AppRoute.TUTORIAL)
        }
    }

    LaunchedEffect(profileRepository) {
        profileRepository.observeProfiles().collect { profiles = it }
    }

    LaunchedEffect(currentRoute, profiles) {
        if (currentRoute == AppRoute.PROFILES || currentRoute == AppRoute.STATISTICS) {
            profileHistory = withContext(Dispatchers.IO) { historyRepository.observeCompletedGames().first() }
            profileAchievements = withContext(Dispatchers.IO) {
                profiles.associate { profile ->
                    profile.profileId to profileRepository.observeAchievements(profile.profileId).first()
                }
            }
        }
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute == AppRoute.SETTINGS) {
            clearHistoryState = ClearHistoryUiState.Idle
            themeWriteResult = null
        }
    }

    BackHandler(enabled = canNavigateBack, onBack = ::requestBack)

    val immersiveCameraRound = currentRoute == AppRoute.GAMEPLAY &&
        gameplayState?.displayedRound?.type == RoundType.FIND_AND_FRAME
    val isMainTab = currentRoute == AppRoute.HOME || currentRoute == AppRoute.STATISTICS || currentRoute == AppRoute.SETTINGS
    val showTopBar = !isMainTab && !immersiveCameraRound && currentRoute != AppRoute.GAMEPLAY
    val showBottomBar = currentRoute == AppRoute.HOME || currentRoute == AppRoute.STATISTICS || currentRoute == AppRoute.SETTINGS
    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SnapQuestMark(Modifier.size(26.dp))
                            Text(currentRoute.title, style = MaterialTheme.typography.titleLarge)
                        }
                    },
                    navigationIcon = {
                        if (canNavigateBack && currentRoute != AppRoute.PRACTICE) {
                            SnapQuestBackButton(onClick = ::requestBack)
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                SnapQuestBottomBar(
                    selected = currentRoute,
                    onHome = { navigateToRoot(AppRoute.HOME) },
                    onScores = { historyReloadToken++; navigateToRoot(AppRoute.STATISTICS) },
                    onSettings = { navigateToRoot(AppRoute.SETTINGS) }
                )
            }
        }
    ) { contentPadding ->
        Box(Modifier.fillMaxSize().padding(contentPadding)) {
            val pageWidth = if (immersiveCameraRound) Modifier.fillMaxSize()
            else Modifier.widthIn(max = 760.dp).fillMaxSize()
            Box(Modifier.align(Alignment.TopCenter).then(pageWidth)) {
        when (currentRoute) {
            AppRoute.HOME -> HomeScreen(
                modifier = Modifier.fillMaxSize().then(
                    if (automaticTutorialChecked) Modifier.testTag("home-ready") else Modifier
                ),
                onMultiplayer = {
                    setupDraft = GameSetupDraft.create(GameMode.MULTIPLAYER)
                    createdSession = null
                    formMessage = null
                    navigateTo(AppRoute.SETUP)
                },
                onSoloPlayer = {
                    setupDraft = GameSetupDraft.create(GameMode.SOLO)
                    createdSession = null
                    formMessage = null
                    navigateTo(AppRoute.SETUP)
                },
                onPractice = { navigateTo(AppRoute.PRACTICE) },
                onQuickPlay = {
                    when (contentState) {
                        is SetupContentState.Available -> createQuickPlay()
                        SetupContentState.Loading -> quickPlayRequested = true
                        is SetupContentState.Unavailable -> {
                            setupDraft = GameSetupDraft.quickPlay()
                            formMessage = (contentState as SetupContentState.Unavailable).message
                            navigateTo(AppRoute.SETUP)
                        }
                    }
                },
                onQuickTrivia = { navigateTo(AppRoute.QUICK_PLAY) },
                onCategoryPractice = { navigateTo(AppRoute.CATEGORY_FOCUSED) },
                onHistory = { navigateTo(AppRoute.HISTORY) },
                onStatistics = { historyReloadToken++; navigateTo(AppRoute.STATISTICS) },
                onShortHistory = { navigateTo(AppRoute.SHORT_HISTORY) },
                onHelp = { navigateTo(AppRoute.HELP) },
                onSettings = { navigateTo(AppRoute.SETTINGS) },
                onTutorial = { tutorialSaveError = null; navigateTo(AppRoute.TUTORIAL) }
            )
            AppRoute.SETUP -> GameSetupScreen(
                draft = setupDraft,
                contentState = contentState,
                session = createdSession,
                formMessage = formMessage,
                onDraftChange = { setupDraft = it; createdSession = null; formMessage = null },
                onCreateGame = {
                    when (val result = GameSetupSessionCreator.create(
                        setupDraft, contentState,
                        recentlyEncounteredQuestionIds = recentlyEncounteredQuestionIds,
                        validProfileIds = profiles.mapTo(mutableSetOf()) { it.profileId }
                    )) {
                        is SessionCreationResult.Created -> { createdSession = result.session; formMessage = null }
                        is SessionCreationResult.Invalid -> formMessage = result.issues.joinToString("\n") { it.message }
                        is SessionCreationResult.ContentUnavailable -> formMessage = result.message
                        is SessionCreationResult.FactoryRejected -> formMessage = result.message
                    }
                },
                onStartGame = {
                    val session = createdSession
                    if (session != null && session.status == GameStatus.ACTIVE) {
                        gameplayState = TriviaGameplayState(session)
                        completionSaveState = CompletionSaveUiState.NotStarted
                        completionRecord = null
                        navigateTo(AppRoute.GAMEPLAY)
                    }
                },
                onEditSetup = { createdSession = null; formMessage = null },
                onRetryContent = { contentReloadToken++ },
                profiles = profiles,
                modifier = Modifier.fillMaxSize()
            )
            AppRoute.PRACTICE -> {
                when (val practiceContent = contentState) {
                    SetupContentState.Loading -> Column(
                        Modifier.fillMaxSize().padding(FrameFactSpacing.screen),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Offline practice questions are loading.")
                        OutlinedButton(onClick = ::navigateBack) { Text("Back") }
                    }
                    is SetupContentState.Unavailable -> Column(
                        Modifier.fillMaxSize().padding(FrameFactSpacing.screen),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Practice questions could not be loaded. ${practiceContent.message}")
                        Button(onClick = { contentReloadToken++ }) { Text("Retry") }
                        OutlinedButton(onClick = ::navigateBack) { Text("Back") }
                    }
                    is SetupContentState.Available -> PracticeScreen(questions = practiceContent.bank.triviaQuestions, onExit = ::navigateBack,
                        modifier = Modifier.fillMaxSize())
                }
            }
            AppRoute.QUICK_PLAY, AppRoute.CATEGORY_FOCUSED -> {
                val bank = (contentState as? SetupContentState.Available)?.bank
                if (bank == null) {
                    Column(Modifier.fillMaxSize().padding(FrameFactSpacing.screen),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Offline question content is loading.")
                        OutlinedButton(onClick = { navigateBack() }) { Text("Back") }
                    }
                } else {
                    val type = if (currentRoute == AppRoute.QUICK_PLAY) ShortSessionType.QUICK_PLAY else ShortSessionType.CATEGORY_FOCUSED
                    ShortTriviaScreen(
                        type = type,
                        questions = bank.triviaQuestions,
                        onCompleted = { summary -> withContext(Dispatchers.IO) { shortGameSummaryRepository.save(summary) } },
                        onExit = ::navigateBack,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            AppRoute.GAMEPLAY -> gameplayState?.let { state ->
                TriviaGameplayScreen(
                    state = state,
                    cameraPrompts = (contentState as? SetupContentState.Available)?.bank?.cameraPrompts.orEmpty(),
                    photoStore = photoStore,
                    onStateChange = ::updateGameplayState,
                    onCameraEvent = ::applyCameraEvent,
                    onCancelGame = ::requestBack,
                    onFinishGame = ::finishCompletedGame,
                    completionSaveState = completionSaveState,
                    onRetrySave = ::retryCompletedRecordSave,
                    onPlayAgain = ::playAgainSolo,
                    onOpenHistory = { historyReloadToken++; navigateTo(AppRoute.HISTORY) },
                    onReportQuestion = { id -> selectedReportQuestionId = id; navigateTo(AppRoute.QUESTION_REPORTS) },
                    modifier = Modifier.fillMaxSize()
                )
            }
            AppRoute.HISTORY -> HistoryScreen(
                state = historyState,
                onOpenGame = { gameId -> selectedHistoryGameId = gameId; navigateTo(AppRoute.HISTORY_DETAIL) },
                onRetry = { historyReloadToken++ },
                modifier = Modifier.fillMaxSize()
            )
            AppRoute.HISTORY_DETAIL -> {
                val game = (historyState as? HistoryUiState.Populated)?.games
                    ?.firstOrNull { it.gameId == selectedHistoryGameId }
                HistoryGameDetailScreen(
                    game = game,
                    onRetry = { historyReloadToken++ },
                    isLoading = historyState == HistoryUiState.Loading,
                    modifier = Modifier.fillMaxSize(),
                    onReviewIncorrectAnswers = { navigateTo(AppRoute.INCORRECT_ANSWER_REVIEW) }
                )
            }
            AppRoute.INCORRECT_ANSWER_REVIEW -> {
                val game = (historyState as? HistoryUiState.Populated)?.games
                    ?.firstOrNull { it.gameId == selectedHistoryGameId }
                IncorrectAnswerReviewScreen(
                    game = game,
                    isLoading = historyState == HistoryUiState.Loading,
                    loadError = (historyState as? HistoryUiState.Error)?.message,
                    onBack = ::navigateBack,
                    onRetry = { historyReloadToken++ },
                    modifier = Modifier.fillMaxSize()
                )
            }
            AppRoute.QUESTION_REPORTS -> QuestionReportsScreen(
                questionId = selectedReportQuestionId,
                reports = questionReports,
                onSave = { issueType: QuestionIssueType, note: String? ->
                    selectedReportQuestionId?.let { id ->
                        withContext(Dispatchers.IO) { questionReportRepository.addReport(id, issueType, note) }
                    }
                },
                onDelete = { reportId -> coroutineScope.launch(Dispatchers.IO) { questionReportRepository.deleteReport(reportId) } },
                onBack = { selectedReportQuestionId = null; navigateBack() },
                modifier = Modifier.fillMaxSize()
            )
            AppRoute.PROFILES -> PlayerProfilesScreen(
                profiles = profiles,
                statistics = profiles.associate { profile ->
                    profile.profileId to FullGameStatisticsCalculator.forProfile(profile.profileId, profileHistory)
                },
                achievements = profileAchievements,
                onCreate = { name -> coroutineScope.launch(Dispatchers.IO) { profileRepository.createProfile(name) } },
                onRename = { profileId, name -> coroutineScope.launch(Dispatchers.IO) { profileRepository.renameProfile(profileId, name) } },
                onDelete = { profileId -> coroutineScope.launch(Dispatchers.IO) { profileRepository.deleteProfile(profileId) } },
                onBack = ::navigateBack,
                modifier = Modifier.fillMaxSize()
            )
            AppRoute.STATISTICS -> StatisticsScreen(
                historyState = historyState,
                profiles = profiles,
                achievements = profileAchievements,
                onRetry = { historyReloadToken++ },
                onManageProfiles = { navigateTo(AppRoute.PROFILES) },
                onStartPlaying = { navigateToRoot(AppRoute.HOME) },
                modifier = Modifier.fillMaxSize()
            )
            AppRoute.SHORT_HISTORY -> ShortGameHistoryScreen(
                summaries = shortGameSummaries,
                modifier = Modifier.fillMaxSize()
            )
            AppRoute.HELP -> RulesHelpScreen(Modifier.fillMaxSize())
            AppRoute.TUTORIAL -> InteractiveTutorialScreen(
                onComplete = ::finishTutorial,
                saveError = tutorialSaveError,
                isSaving = isTutorialSaving,
                modifier = Modifier.fillMaxSize()
            )
            AppRoute.SETTINGS -> SettingsScreen(
                settingsState = settingsReadState,
                themeWriteResult = themeWriteResult,
                isThemeSaving = isThemeSaving,
                clearHistoryState = clearHistoryState,
                onThemeSelected = ::updateTheme,
                onConfirmClearHistory = ::clearSavedHistory,
                onManageQuestionReports = { selectedReportQuestionId = null; navigateTo(AppRoute.QUESTION_REPORTS) },
                onManageProfiles = { navigateTo(AppRoute.PROFILES) },
                onReplayTutorial = { tutorialSaveError = null; navigateTo(AppRoute.TUTORIAL) },
                modifier = Modifier.fillMaxSize()
            )
        }
            }
        }
    }

    if (showCancelConfirmation) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmation = false },
            title = { Text("Cancel this game?") },
            text = { Text("Your current game progress will be discarded. No points will be awarded for unfinished rounds.") },
            confirmButton = {
                TextButton(onClick = ::confirmCancelGame) { Text("Cancel Game") }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirmation = false }) { Text("Keep playing") }
            }
        )
    }
}

@Composable
internal fun HomeScreen(
    modifier: Modifier = Modifier,
    onMultiplayer: () -> Unit,
    onSoloPlayer: () -> Unit,
    onPractice: () -> Unit,
    onQuickPlay: () -> Unit,
    onQuickTrivia: () -> Unit,
    onCategoryPractice: () -> Unit,
    onHistory: () -> Unit,
    onStatistics: () -> Unit,
    onShortHistory: () -> Unit,
    onHelp: () -> Unit,
    onSettings: () -> Unit,
    onTutorial: () -> Unit = {}
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = FrameFactSpacing.screen, vertical = FrameFactSpacing.compact),
        verticalArrangement = Arrangement.spacedBy(FrameFactSpacing.spacious)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            SnapQuestWordmark(markSize = 28.dp, textStyle = MaterialTheme.typography.titleLarge)
            Text(
                "See It. Think It. Prove It.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(Modifier.fillMaxWidth().height(156.dp)) {
                Image(
                    painter = painterResource(R.drawable.snapquest_home_hero),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.04f), Color.Black.copy(alpha = 0.78f))
                        )
                    )
                )
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Find. Identify. Score!", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text("Put your observation skills to the test.", style = MaterialTheme.typography.bodySmall, color = Color.White)
                    Button(
                        onClick = onQuickPlay,
                        modifier = Modifier.height(44.dp),
                        shape = RoundedCornerShape(22.dp)
                    ) { Text("Start Game") }
                }
            }
        }

        HomeSection(title = "Quick access") {
            Row(horizontalArrangement = Arrangement.spacedBy(FrameFactSpacing.compact)) {
                HomeActionCard("Tutorial", "Learn the basics", onTutorial, Modifier.weight(1f), R.drawable.ic_tutorial)
                HomeActionCard("High Scores", "See your best", onStatistics, Modifier.weight(1f), R.drawable.ic_nav_scores)
            }
        }

        HomeSection(title = "Game modes") {
            Row(horizontalArrangement = Arrangement.spacedBy(FrameFactSpacing.compact)) {
                HomeActionCard("Solo Player", "Play a full game", onSoloPlayer, Modifier.weight(1f), R.drawable.ic_person)
                HomeActionCard("Multiplayer", "Pass and play", onMultiplayer, Modifier.weight(1f), R.drawable.ic_groups)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(FrameFactSpacing.compact)) {
                HomeActionCard("Practice", "Unscored play", onPractice, Modifier.weight(1f), R.drawable.ic_school)
                HomeActionCard("Quick Trivia", "Short mixed play", onQuickTrivia, Modifier.weight(1f), R.drawable.ic_tutorial)
            }
            HomeActionCard("Category Practice", "Focus on one category", onCategoryPractice, iconRes = R.drawable.ic_category)
        }

        HomeSection(title = "Your progress") {
            Row(horizontalArrangement = Arrangement.spacedBy(FrameFactSpacing.compact)) {
                HomeActionCard("History", "Completed games", onHistory, Modifier.weight(1f), R.drawable.ic_nav_scores)
                HomeActionCard("Short history", "Quick and category sessions", onShortHistory, Modifier.weight(1f), R.drawable.ic_nav_scores)
            }
            HomeActionCard("Rules & Help", "Learn how to play", onHelp, iconRes = R.drawable.ic_tutorial)
        }
    }
}

@Composable
private fun SnapQuestBottomBar(
    selected: AppRoute,
    onHome: () -> Unit,
    onScores: () -> Unit,
    onSettings: () -> Unit
) {
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer
    )
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        NavigationBarItem(
            selected = selected == AppRoute.HOME,
            onClick = onHome,
            icon = {
                Image(
                    painterResource(R.drawable.ic_nav_home), null, Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(if (selected == AppRoute.HOME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                )
            },
            label = { Text("Home") },
            colors = itemColors
        )
        NavigationBarItem(
            selected = selected == AppRoute.STATISTICS,
            onClick = onScores,
            icon = {
                Image(
                    painterResource(R.drawable.ic_nav_scores), null, Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(if (selected == AppRoute.STATISTICS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                )
            },
            label = { Text("Scores") },
            colors = itemColors
        )
        NavigationBarItem(
            selected = selected == AppRoute.SETTINGS,
            onClick = onSettings,
            icon = {
                Image(
                    painterResource(R.drawable.ic_nav_settings), null, Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(if (selected == AppRoute.SETTINGS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                )
            },
            label = { Text("Settings") },
            colors = itemColors
        )
    }
}

@Composable
private fun HomeSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(FrameFactSpacing.standard)) {
        SnapQuestSectionHeader(title)
        content()
    }
}

@Composable
private fun HomeActionCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconRes: Int? = null
) {
    Card(
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (iconRes != null) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
    }
}

