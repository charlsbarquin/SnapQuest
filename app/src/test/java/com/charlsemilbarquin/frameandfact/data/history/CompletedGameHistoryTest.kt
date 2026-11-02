package com.charlsemilbarquin.frameandfact.data.history

import com.charlsemilbarquin.frameandfact.domain.game.ApprovedCategories
import com.charlsemilbarquin.frameandfact.domain.game.CameraPrompt
import com.charlsemilbarquin.frameandfact.domain.game.CameraPromptId
import com.charlsemilbarquin.frameandfact.domain.game.CategoryId
import com.charlsemilbarquin.frameandfact.domain.game.ChoiceId
import com.charlsemilbarquin.frameandfact.domain.game.Difficulty
import com.charlsemilbarquin.frameandfact.domain.game.GameAction
import com.charlsemilbarquin.frameandfact.domain.game.GameActionResult
import com.charlsemilbarquin.frameandfact.domain.game.GameEngine
import com.charlsemilbarquin.frameandfact.domain.game.GameEvent
import com.charlsemilbarquin.frameandfact.domain.game.GameFactory
import com.charlsemilbarquin.frameandfact.domain.game.GameId
import com.charlsemilbarquin.frameandfact.domain.game.GameMode
import com.charlsemilbarquin.frameandfact.domain.game.GameSession
import com.charlsemilbarquin.frameandfact.domain.game.GameStatus
import com.charlsemilbarquin.frameandfact.domain.game.Participant
import com.charlsemilbarquin.frameandfact.domain.game.ParticipantId
import com.charlsemilbarquin.frameandfact.domain.game.ParticipantKind
import com.charlsemilbarquin.frameandfact.domain.game.RoundOutcome
import com.charlsemilbarquin.frameandfact.domain.game.RoundType
import com.charlsemilbarquin.frameandfact.domain.game.TriviaChoice
import com.charlsemilbarquin.frameandfact.domain.game.TriviaQuestion
import com.charlsemilbarquin.frameandfact.feature.history.HistoryUiState
import com.charlsemilbarquin.frameandfact.feature.history.historyUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CompletedGameHistoryTest {
    @Test
    fun mapperPreservesNamesScoresTiesCategoriesDifficultiesAndRoundOutcomes() {
        val game = finishAllSuccessfulRounds(newSession())
        val record = CompletedGameHistoryMapper.fromSession(game, 1_777_777_000_000L)!!

        assertEquals(1_777_777_000_000L, record.completedAtEpochMillis)
        assertEquals(listOf("General Knowledge", "Science"), record.selectedCategories)
        assertEquals(listOf("EASY", "HARD", "MEDIUM"), record.difficultyLevels)
        assertEquals(listOf("Ada", "Grace"), record.participants.map { it.displayName })
        assertEquals(listOf(40, 40), record.participants.map { it.score })
        assertTrue(record.isTie)
        assertEquals(listOf(1, 1), record.participants.map { it.rank })
        assertEquals(8, record.rounds.size)
        assertEquals(RoundOutcome.CAMERA_ACCEPTED, record.rounds.last().outcome)
        assertEquals("ada", record.rounds.last().judgeId)
        val triviaReview = record.rounds.first { it.type == RoundType.TRIVIA }
        val selectedQuestion = game.selectedQuestions.first { it.id.value == triviaReview.questionId }
        assertEquals(selectedQuestion.prompt, triviaReview.questionText)
        assertEquals("Correct", triviaReview.selectedAnswer)
        assertEquals("Correct", triviaReview.correctAnswer)
        assertEquals(selectedQuestion.categoryId.value, triviaReview.categoryId)
        assertEquals(selectedQuestion.difficulty.name, triviaReview.difficulty)
    }

    @Test
    fun mapperSnapshotsTheActualIncorrectChoiceAndQuestionExplanation() {
        var session = newSession()
        val firstTrivia = session.currentRound!!
        val question = session.selectedQuestions.first { it.id == firstTrivia.questionId }
        val incorrectChoice = question.choices.first { it.id != question.correctChoiceId }
        session = apply(session, GameEvent.TriviaAnswer(firstTrivia.id, incorrectChoice.id))
        while (session.currentRound != null) {
            val round = session.currentRound!!
            val event = if (round.questionId != null) {
                val remainingQuestion = session.selectedQuestions.first { it.id == round.questionId }
                GameEvent.TriviaAnswer(round.id, remainingQuestion.correctChoiceId)
            } else GameEvent.CameraAccepted(round.id)
            session = apply(session, event)
        }

        val snapshot = CompletedGameHistoryMapper.fromSession(session, 1_777_777_000_000L)!!
            .rounds.first { it.number == firstTrivia.id.number }

        assertEquals(RoundOutcome.TRIVIA_INCORRECT, snapshot.outcome)
        assertEquals(question.prompt, snapshot.questionText)
        assertEquals(incorrectChoice.text, snapshot.selectedAnswer)
        assertEquals("Correct", snapshot.correctAnswer)
        assertEquals(question.explanation, snapshot.explanation)
        assertEquals(question.categoryId.value, snapshot.categoryId)
        assertEquals(question.difficulty.name, snapshot.difficulty)
    }

    @Test
    fun mapperRejectsActiveCanceledAndIncompleteSessions() {
        val active = newSession()
        assertEquals(null, CompletedGameHistoryMapper.fromSession(active, 100L))
        val canceled = active.copy(status = GameStatus.CANCELLED)
        assertEquals(null, CompletedGameHistoryMapper.fromSession(canceled, 100L))

        val fullGame = finishAllSuccessfulRounds(active)
        val incomplete = fullGame.copy(outcomes = fullGame.outcomes - fullGame.rounds.last().id)
        assertEquals(null, CompletedGameHistoryMapper.fromSession(incomplete, 100L))
    }

    @Test
    fun soloCompletionMapsOnePlayerAndCameraRoundsWithoutJudges() {
        var session = newSoloSession()
        while (session.currentRound != null) {
            val round = session.currentRound!!
            val event = if (round.questionId != null) {
                val question = session.selectedQuestions.first { it.id == round.questionId }
                GameEvent.TriviaAnswer(round.id, question.correctChoiceId)
            } else GameEvent.CameraAccepted(round.id)
            session = apply(session, event)
        }

        val record = CompletedGameHistoryMapper.fromSession(session, 700L)!!
        assertEquals(GameMode.SOLO, session.mode)
        assertTrue(record.isSoloGame)
        assertEquals("Solo", record.participants.single().displayName)
        assertEquals(80, record.participants.single().score)
        assertTrue(record.rounds.filter { it.type == RoundType.FIND_AND_FRAME }.all { it.judgeId == null })
    }

    @Test
    fun saverOnlyWritesCompletedGamesAndCoalescesDuplicateCompletionCallbacks() = runBlocking {
        val repository = FakeHistoryRepository()
        val saver = CompletedGameHistorySaver(repository) { 99L }
        val completed = finishAllSuccessfulRounds(newSession())
        val engineStateBeforeSave = completed

        assertEquals(CompletedGameSaveAttempt.NotCompleted, saver.saveIfCompleted(newSession()))
        assertEquals(
            CompletedGameSaveAttempt.NotCompleted,
            saver.saveIfCompleted(newSession().copy(status = GameStatus.CANCELLED))
        )
        assertEquals(0, repository.saveCalls)
        val first = saver.saveIfCompleted(completed) as CompletedGameSaveAttempt.Saved
        val repeated = saver.saveIfCompleted(completed) as CompletedGameSaveAttempt.Saved

        assertEquals(1, repository.saveCalls)
        assertEquals(first.record, repeated.record)
        assertEquals(engineStateBeforeSave, completed)
        assertEquals(40, completed.scoreFor(ParticipantId("ada")))
        assertEquals(GameStatus.COMPLETED, completed.status)
    }

    @Test
    fun failedSaveIsSurfacedAndCanBeRetriedWithoutChangingTheGame() = runBlocking {
        val repository = FakeHistoryRepository(failNextSave = true)
        val saver = CompletedGameHistorySaver(repository) { 123L }
        val completed = finishAllSuccessfulRounds(newSession())

        val failed = saver.saveIfCompleted(completed) as CompletedGameSaveAttempt.Failed
        assertEquals(GameStatus.COMPLETED, completed.status)
        assertEquals(40, completed.scoreFor(ParticipantId("ada")))
        assertEquals(0, repository.records.size)

        val retried = saver.retry(failed.record) as CompletedGameSaveAttempt.Saved
        assertEquals(failed.record, retried.record)
        assertEquals(2, repository.saveCalls)
        assertEquals(1, repository.records.size)
    }

    @Test
    fun historyStateTreatsNoRowsAsEmptyAndSavedRowsAsPopulated() {
        assertEquals(HistoryUiState.Empty, historyUiState(emptyList()))
        val record = CompletedGameHistoryMapper.fromSession(finishAllSuccessfulRounds(newSession()), 4L)!!
        assertTrue(historyUiState(listOf(record)) is HistoryUiState.Populated)
    }

    @Test
    fun finalCameraRoundCompletionProducesOneSavedSnapshotWithoutAdvancingAgain() = runBlocking {
        var session = newSession()
        session.rounds.dropLast(1).forEach { round ->
            val event = if (round.questionId != null) {
                val question = session.selectedQuestions.first { it.id == round.questionId }
                GameEvent.TriviaAnswer(round.id, question.correctChoiceId)
            } else GameEvent.CameraAccepted(round.id)
            session = apply(session, event)
        }
        assertEquals(8, session.currentRound?.id?.number)
        val lastCamera = session.currentRound!!
        session = apply(session, GameEvent.CameraAccepted(lastCamera.id))
        val repository = FakeHistoryRepository()
        val saver = CompletedGameHistorySaver(repository) { 500L }
        val snapshot = (saver.saveIfCompleted(session) as CompletedGameSaveAttempt.Saved).record
        val duplicate = saver.saveIfCompleted(session) as CompletedGameSaveAttempt.Saved

        assertEquals(GameStatus.COMPLETED, session.status)
        assertEquals(8, snapshot.rounds.size)
        assertEquals(RoundOutcome.CAMERA_ACCEPTED, snapshot.rounds.last().outcome)
        assertEquals(snapshot, duplicate.record)
        assertEquals(1, repository.saveCalls)
        assertEquals(null, session.currentRound)
    }

    private fun newSession(): GameSession {
        val categories = ApprovedCategories.all.take(2)
        val people = listOf(
            Participant(ParticipantId("ada"), "Ada", ParticipantKind.PLAYER),
            Participant(ParticipantId("grace"), "Grace", ParticipantKind.TEAM)
        )
        val questions = (0 until 6).map { index ->
            val category = categories[index % categories.size]
            TriviaQuestion(
                id = com.charlsemilbarquin.frameandfact.domain.game.QuestionId("q$index"),
                categoryId = category.id,
                difficulty = Difficulty.entries[index % Difficulty.entries.size],
                prompt = "Question $index?",
                choices = listOf(
                    TriviaChoice(ChoiceId("correct$index"), "Correct"),
                    TriviaChoice(ChoiceId("incorrect$index"), "Incorrect")
                ),
                correctChoiceId = ChoiceId("correct$index"),
                explanation = "Explanation $index"
            )
        }
        val prompts = (0 until 2).map { index ->
            CameraPrompt(CameraPromptId("camera$index"), categories[index].id, "Prompt", "Safe", "Judge")
        }
        return GameFactory.create(
            id = GameId("history-game"), participants = people, categories = categories,
            selectedCategoryIds = categories.map { it.id }.toSet(), availableQuestions = questions,
            availableCameraPrompts = prompts, random = Random(5), startingParticipantId = people.first().id
        )
    }

    private fun newSoloSession(): GameSession {
        val categories = ApprovedCategories.all.take(2)
        val player = Participant(ParticipantId("solo"), "Solo", ParticipantKind.PLAYER)
        val questions = (0 until 6).map { index ->
            val category = categories[index % categories.size]
            TriviaQuestion(
                id = com.charlsemilbarquin.frameandfact.domain.game.QuestionId("solo-q$index"),
                categoryId = category.id,
                difficulty = Difficulty.entries[index % Difficulty.entries.size],
                prompt = "Solo question $index?",
                choices = listOf(TriviaChoice(ChoiceId("solo-correct$index"), "Correct")),
                correctChoiceId = ChoiceId("solo-correct$index")
            )
        }
        val prompts = (0 until 2).map { index ->
            CameraPrompt(CameraPromptId("solo-camera$index"), categories[index].id, "Prompt", "Safe", "Self assess")
        }
        return GameFactory.create(
            id = GameId("solo-history-game"), participants = listOf(player), categories = categories,
            selectedCategoryIds = categories.map { it.id }.toSet(), availableQuestions = questions,
            availableCameraPrompts = prompts, random = Random(9), startingParticipantId = player.id,
            mode = GameMode.SOLO
        )
    }

    private fun finishAllSuccessfulRounds(start: GameSession): GameSession {
        var session = start
        while (session.currentRound != null) {
            val round = session.currentRound!!
            val event = if (round.questionId != null) {
                val question = session.selectedQuestions.first { it.id == round.questionId }
                GameEvent.TriviaAnswer(round.id, question.correctChoiceId)
            } else GameEvent.CameraAccepted(round.id)
            session = apply(session, event)
        }
        return session
    }

    private fun apply(session: GameSession, event: GameEvent): GameSession =
        (GameEngine.apply(session, GameAction.RoundEvent(event)) as GameActionResult.Applied).session

    private class FakeHistoryRepository(
        private var failNextSave: Boolean = false
    ) : GameHistoryRepository {
        val records = linkedMapOf<String, CompletedGameHistoryRecord>()
        var saveCalls = 0

        override fun observeCompletedGames(): Flow<List<CompletedGameHistoryRecord>> = emptyFlow()
        override suspend fun findCompletedGame(gameId: String): CompletedGameHistoryRecord? = records[gameId]
        override suspend fun saveCompletedGame(record: CompletedGameHistoryRecord): SaveHistoryResult {
            saveCalls++
            if (failNextSave) {
                failNextSave = false
                error("database unavailable")
            }
            return if (records.putIfAbsent(record.gameId, record) == null) SaveHistoryResult.Inserted
            else SaveHistoryResult.AlreadyPresent
        }

        override suspend fun clearHistory(): Int {
            val existing = records.size
            records.clear()
            return existing
        }
    }
}
