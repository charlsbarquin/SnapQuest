package com.charlsemilbarquin.frameandfact.domain.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {
    private val participants = listOf(
        Participant(ParticipantId("p1"), "Player 1", ParticipantKind.PLAYER),
        Participant(ParticipantId("p2"), "Player 2", ParticipantKind.PLAYER),
        Participant(ParticipantId("p3"), "Team 3", ParticipantKind.TEAM)
    )
    private val categories = ApprovedCategories.all
    private val categoryIds = categories.map { it.id }.toSet()
    private val questions = categories.flatMapIndexed { categoryIndex, category ->
        (1..8).map { questionIndex ->
            TriviaQuestion(
                id = QuestionId("q${categoryIndex}_$questionIndex"),
                categoryId = category.id,
                difficulty = Difficulty.entries[(questionIndex - 1) % Difficulty.entries.size],
                prompt = "Question $categoryIndex-$questionIndex",
                choices = listOf(
                    TriviaChoice(ChoiceId("right"), "Right"),
                    TriviaChoice(ChoiceId("wrong"), "Wrong"),
                    TriviaChoice(ChoiceId("other"), "Other"),
                    TriviaChoice(ChoiceId("last"), "Last")
                ),
                correctChoiceId = ChoiceId("right")
            )
        }
    }
    private val cameraPrompts = categories.flatMap { category ->
        (1..3).map { index ->
            CameraPrompt(
                id = CameraPromptId("${category.id.value}_$index"),
                categoryId = category.id,
                prompt = "Find a safe object, prompt $index",
                safetyNote = "Do not photograph people or private information.",
                judgingGuidance = "The judge checks whether the photo matches the prompt."
            )
        }
    }

    private fun newSession(
        selectedCategories: Set<CategoryId> = categoryIds,
        participants: List<Participant> = this.participants,
        questions: List<TriviaQuestion> = this.questions,
        starter: ParticipantId = ParticipantId("p2"),
        mode: GameMode = if (participants.size == 1) GameMode.SOLO else GameMode.MULTIPLAYER
    ) = GameFactory.create(
        id = GameId("game-1"), participants, categories, selectedCategories, questions,
        availableCameraPrompts = cameraPrompts,
        random = Random(17), startingParticipantId = starter, mode = mode
    )

    private fun applied(session: GameSession, event: GameEvent): GameSession {
        val result = GameEngine.apply(session, GameAction.RoundEvent(event))
        assertTrue("Expected action to apply, got $result", result is GameActionResult.Applied)
        return (result as GameActionResult.Applied).session
    }

    private fun finishSuccessfully(session: GameSession): GameSession {
        var current = session
        while (current.status == GameStatus.ACTIVE) {
            val round = current.currentRound!!
            current = when (round.type) {
                RoundType.TRIVIA -> {
                    val question = current.selectedQuestions.single { it.id == round.questionId }
                    applied(current, GameEvent.TriviaAnswer(round.id, question.correctChoiceId))
                }
                RoundType.FIND_AND_FRAME -> applied(current, GameEvent.CameraAccepted(round.id))
            }
        }
        return current
    }

    @Test
    fun createsExactEightRoundSequenceAndQuestionCount() {
        val session = newSession()

        assertEquals(
            listOf(
                RoundType.TRIVIA, RoundType.TRIVIA, RoundType.TRIVIA, RoundType.FIND_AND_FRAME,
                RoundType.TRIVIA, RoundType.TRIVIA, RoundType.TRIVIA, RoundType.FIND_AND_FRAME
            ),
            session.rounds.map { it.type }
        )
        assertEquals(8, session.rounds.size)
        assertEquals(6, session.selectedQuestions.size)
        assertEquals(2, session.rounds.count { it.type == RoundType.FIND_AND_FRAME })
        assertEquals(80, GameFactory.MAX_SCORE)
    }

    @Test
    fun selectsSixUniqueQuestionsOnlyFromSelectedCategoriesAcrossDifficulties() {
        val selected = setOf(CategoryId("science"), CategoryId("history"))
        val session = newSession(selectedCategories = selected)

        assertEquals(6, session.selectedQuestions.map { it.id }.toSet().size)
        assertTrue(session.selectedQuestions.all { it.categoryId in selected })
        assertEquals(Difficulty.entries.toSet(), session.selectedQuestions.map { it.difficulty }.toSet())
    }

    @Test
    fun cameraPromptsAreUniqueAndIndependentOfTriviaCategories() {
        val otherCategoryPrompts = cameraPrompts.filter { it.categoryId == CategoryId("sports") }
        val session = GameFactory.create(
            id = GameId("independent-prompts"),
            participants = participants,
            categories = categories,
            selectedCategoryIds = setOf(CategoryId("mathematics")),
            availableQuestions = questions.filter { it.categoryId == CategoryId("mathematics") },
            availableCameraPrompts = otherCategoryPrompts,
            random = Random(4),
            startingParticipantId = ParticipantId("p1")
        )
        val assigned = session.rounds.filter { it.type == RoundType.FIND_AND_FRAME }

        assertEquals(2, assigned.map { it.cameraPromptId }.toSet().size)
        assertTrue(assigned.all { round -> otherCategoryPrompts.any { it.id == round.cameraPromptId } })
    }

    @Test
    fun rejectsFewerThanSixEligibleQuestions() {
        val eligible = questions.filter { it.categoryId == CategoryId("science") }.take(5)
        val error = try {
            newSession(setOf(CategoryId("science")), questions = eligible)
            null
        } catch (exception: GameSetupException) {
            exception.reason
        }

        assertEquals(GameSetupError.INSUFFICIENT_ELIGIBLE_QUESTIONS, error)
    }

    @Test
    fun rejectsDuplicateQuestionIdsAndInvalidParticipantSetup() {
        val duplicate = questions + questions.first()
        assertEquals(
            GameSetupError.DUPLICATE_QUESTION_ID,
            runCatching { newSession(questions = duplicate) }.exceptionOrNull().let { (it as GameSetupException).reason }
        )
        assertEquals(
            GameSetupError.INVALID_PARTICIPANT_COUNT,
            runCatching {
                newSession(participants = participants.take(1), mode = GameMode.MULTIPLAYER)
            }.exceptionOrNull().let { (it as GameSetupException).reason }
        )
    }

    @Test
    fun soloSessionUsesOneParticipantAndHasNoJudges() {
        val player = participants.first()
        val session = newSession(participants = listOf(player), starter = player.id, mode = GameMode.SOLO)
        val cameraRounds = session.rounds.filter { it.type == RoundType.FIND_AND_FRAME }

        assertEquals(GameMode.SOLO, session.mode)
        assertEquals(1, session.participants.size)
        assertEquals(8, session.rounds.size)
        assertEquals(6, session.selectedQuestions.map { it.id }.distinct().size)
        assertTrue(cameraRounds.all { it.participantId == player.id && it.judgeId == null })
    }

    @Test
    fun soloCameraRejectionCannotResolveTheRoundOrAdvance() {
        val player = participants.first()
        var session = newSession(participants = listOf(player), starter = player.id, mode = GameMode.SOLO)
        repeat(3) { session = applied(session, GameEvent.SkipTrivia(session.currentRound!!.id)) }
        val camera = session.currentRound!!

        val rejected = GameEngine.apply(
            session,
            GameAction.RoundEvent(GameEvent.CameraRejected(camera.id))
        ) as GameActionResult.Rejected

        assertEquals(RejectionReason.WRONG_EVENT_FOR_ROUND, rejected.reason)
        assertEquals(session, rejected.session)
        assertEquals(camera.id, rejected.session.currentRound?.id)
        assertEquals(0, rejected.session.scoreFor(player.id))
    }

    @Test
    fun triviaAndCameraRotationsAreSeparateAndCameraJudgesAreDifferent() {
        val session = newSession()
        val triviaOwners = session.rounds.filter { it.type == RoundType.TRIVIA }.map { it.participantId }
        val cameraRounds = session.rounds.filter { it.type == RoundType.FIND_AND_FRAME }

        assertEquals(
            listOf(ParticipantId("p2"), ParticipantId("p3"), ParticipantId("p1"), ParticipantId("p2"), ParticipantId("p3"), ParticipantId("p1")),
            triviaOwners
        )
        assertEquals(ParticipantId("p2"), cameraRounds[0].participantId)
        assertEquals(ParticipantId("p3"), cameraRounds[1].participantId)
        assertNotEquals(cameraRounds[0].participantId, cameraRounds[1].participantId)
        cameraRounds.forEach { assertNotEquals(it.participantId, it.judgeId) }
        assertEquals(ParticipantId("p3"), cameraRounds[0].judgeId)
        assertEquals(ParticipantId("p1"), cameraRounds[1].judgeId)
    }

    @Test
    fun correctAnswersAndAcceptedCameraRoundsAwardTenEachForMaximumEighty() {
        val completed = finishSuccessfully(newSession())

        assertEquals(GameStatus.COMPLETED, completed.status)
        assertEquals(80, completed.scoreBreakdown.sumOf { it.total })
        assertEquals(80, completed.completedGameOrNull()!!.totalScore)
        assertTrue(completed.completedGameOrNull()!!.highestScore <= 80)
        assertEquals(8, completed.outcomes.size)
    }

    @Test
    fun incorrectRejectedAndSkippedOutcomesAwardZero() {
        var session = newSession()
        val firstTrivia = session.currentRound!!
        session = applied(session, GameEvent.TriviaAnswer(firstTrivia.id, ChoiceId("wrong")))
        assertEquals(0, session.scoreFor(firstTrivia.participantId))

        session = applied(session, GameEvent.SkipTrivia(session.currentRound!!.id))
        session = applied(session, GameEvent.SkipTrivia(session.currentRound!!.id))
        val camera = session.currentRound!!
        session = applied(session, GameEvent.CameraRejected(camera.id))
        assertEquals(0, session.scoreFor(camera.participantId))
        session = applied(session, GameEvent.SkipTrivia(session.currentRound!!.id))
        session = applied(session, GameEvent.SkipTrivia(session.currentRound!!.id))
        session = applied(session, GameEvent.SkipTrivia(session.currentRound!!.id))
        val secondCamera = session.currentRound!!
        session = applied(session, GameEvent.SkipCameraRound(secondCamera.id))

        assertEquals(GameStatus.COMPLETED, session.status)
        assertTrue(session.scoreBreakdown.all { it.total == 0 })
    }

    @Test
    fun answerAwardsPointsToRoundParticipantAndRejectsInvalidChoice() {
        val session = newSession()
        val round = session.currentRound!!
        val invalid = GameEngine.apply(
            session,
            GameAction.RoundEvent(GameEvent.TriviaAnswer(round.id, ChoiceId("not-in-question")))
        ) as GameActionResult.Rejected
        assertEquals(RejectionReason.INVALID_ANSWER_CHOICE, invalid.reason)
        assertEquals(session, invalid.session)

        val question = session.selectedQuestions.single { it.id == round.questionId }
        val scored = applied(session, GameEvent.TriviaAnswer(round.id, question.correctChoiceId))
        assertEquals(10, scored.scoreFor(round.participantId))
        assertEquals(0, scored.scoreFor(ParticipantId("p1")))
    }

    @Test
    fun duplicateOutcomeCannotScoreOrAdvanceTwice() {
        val session = newSession()
        val round = session.currentRound!!
        val question = session.selectedQuestions.single { it.id == round.questionId }
        val afterFirst = applied(session, GameEvent.TriviaAnswer(round.id, question.correctChoiceId))
        val duplicate = GameEngine.apply(
            afterFirst,
            GameAction.RoundEvent(GameEvent.TriviaAnswer(round.id, question.correctChoiceId))
        ) as GameActionResult.Rejected

        assertEquals(RejectionReason.ROUND_NOT_CURRENT, duplicate.reason)
        assertEquals(afterFirst, duplicate.session)
        assertEquals(10, duplicate.session.scoreFor(round.participantId))
    }

    @Test
    fun cancelPhotoLeavesCameraRoundAndScoresUnchanged() {
        var session = newSession()
        repeat(3) { session = applied(session, GameEvent.SkipTrivia(session.currentRound!!.id)) }
        val before = session
        val result = GameEngine.apply(
            session,
            GameAction.RoundEvent(GameEvent.CancelPhoto(session.currentRound!!.id))
        ) as GameActionResult.Applied

        assertEquals(GameChange.PHOTO_CANCELLED, result.change)
        assertEquals(before, result.session)
        assertEquals(RoundId(4), result.session.currentRound!!.id)
        assertNull(result.session.completedGameOrNull())
    }

    @Test
    fun skippingCameraRoundRecordsZeroAndAdvancesOnce() {
        var session = newSession()
        repeat(3) { session = applied(session, GameEvent.SkipTrivia(session.currentRound!!.id)) }
        val camera = session.currentRound!!
        session = applied(session, GameEvent.SkipCameraRound(camera.id))

        assertEquals(RoundId(5), session.currentRound!!.id)
        assertEquals(0, session.scoreFor(camera.participantId))
        assertEquals(RoundOutcome.CAMERA_SKIPPED, session.outcomes[camera.id])
    }

    @Test
    fun acceptingCameraSubmissionScoresTheRoundOwnerAndRoundFourAdvancesToTriviaFive() {
        var session = newSession()
        repeat(3) { session = applied(session, GameEvent.SkipTrivia(session.currentRound!!.id)) }
        val camera = session.currentRound!!
        val owner = camera.participantId
        val judge = camera.judgeId!!
        session = applied(session, GameEvent.CameraAccepted(camera.id))

        assertEquals(RoundOutcome.CAMERA_ACCEPTED, session.outcomes[camera.id])
        assertEquals(10, session.scoreFor(owner))
        assertEquals(0, session.scoreFor(judge))
        assertEquals(RoundId(5), session.currentRound!!.id)
        assertEquals(RoundType.TRIVIA, session.currentRound!!.type)
    }

    @Test
    fun rejectingCameraSubmissionAwardsZeroAndDuplicateJudgeActionsAreRejected() {
        var session = newSession()
        repeat(3) { session = applied(session, GameEvent.SkipTrivia(session.currentRound!!.id)) }
        val camera = session.currentRound!!
        val rejected = applied(session, GameEvent.CameraRejected(camera.id))
        val duplicate = GameEngine.apply(
            rejected,
            GameAction.RoundEvent(GameEvent.CameraAccepted(camera.id))
        ) as GameActionResult.Rejected

        assertEquals(0, rejected.scoreFor(camera.participantId))
        assertEquals(RejectionReason.ROUND_NOT_CURRENT, duplicate.reason)
        assertEquals(rejected, duplicate.session)
    }

    @Test
    fun staleCameraEventCannotResolveTheNextCameraRound() {
        var session = newSession()
        repeat(3) { session = applied(session, GameEvent.SkipTrivia(session.currentRound!!.id)) }
        val firstCamera = session.currentRound!!
        session = applied(session, GameEvent.CameraAccepted(firstCamera.id))
        repeat(3) { session = applied(session, GameEvent.SkipTrivia(session.currentRound!!.id)) }
        val secondCamera = session.currentRound!!
        val stale = GameEngine.apply(
            session,
            GameAction.RoundEvent(GameEvent.CameraRejected(firstCamera.id))
        ) as GameActionResult.Rejected

        assertEquals(RoundId(8), secondCamera.id)
        assertEquals(RejectionReason.ROUND_NOT_CURRENT, stale.reason)
        assertEquals(session, stale.session)
    }

    @Test
    fun finalCameraSkipCompletesGameAndSkippingBothCameraRoundsStillFinishes() {
        var session = newSession()
        while (session.status == GameStatus.ACTIVE) {
            val current = session.currentRound!!
            val event = if (current.type == RoundType.FIND_AND_FRAME) {
                GameEvent.SkipCameraRound(current.id)
            } else {
                GameEvent.SkipTrivia(current.id)
            }
            session = applied(session, event)
        }

        assertEquals(GameStatus.COMPLETED, session.status)
        assertEquals(8, session.outcomes.size)
        assertEquals(RoundOutcome.CAMERA_SKIPPED, session.outcomes[RoundId(4)])
        assertEquals(RoundOutcome.CAMERA_SKIPPED, session.outcomes[RoundId(8)])
        assertNull(session.currentRound)
        assertNotEquals(null, session.completedGameOrNull())
        assertTrue(session.scoreBreakdown.all { it.total == 0 })
    }

    @Test
    fun cancelGameDoesNotAdvanceOrCreateCompletedRecord() {
        val session = newSession()
        val cancelled = (GameEngine.apply(session, GameAction.CancelGameConfirmed) as GameActionResult.Applied).session

        assertEquals(GameStatus.CANCELLED, cancelled.status)
        assertEquals(session.currentRound, cancelled.rounds.firstOrNull { it.id !in cancelled.outcomes })
        assertEquals(session.outcomes, cancelled.outcomes)
        assertNull(cancelled.currentRound)
        assertNull(cancelled.completedGameOrNull())
    }

    @Test
    fun wrongEventTypeIsRejectedWithoutChangingSession() {
        val session = newSession()
        val result = GameEngine.apply(
            session,
            GameAction.RoundEvent(GameEvent.CameraAccepted(session.currentRound!!.id))
        ) as GameActionResult.Rejected

        assertEquals(RejectionReason.WRONG_EVENT_FOR_ROUND, result.reason)
        assertEquals(session, result.session)
    }

    @Test
    fun tieRemainsATieWithMoreThanOneWinner() {
        val twoParticipants = participants.take(2)
        var session = newSession(participants = twoParticipants, starter = twoParticipants.first().id)
        session = finishSuccessfully(session)

        assertEquals(40, session.completedGameOrNull()!!.highestScore)
        assertEquals(2, session.completedGameOrNull()!!.winners.size)
    }

    @Test
    fun completedOrCancelledSessionRejectsFurtherEvents() {
        val completed = finishSuccessfully(newSession())
        val completeResult = GameEngine.apply(
            completed,
            GameAction.RoundEvent(GameEvent.SkipTrivia(RoundId(1)))
        ) as GameActionResult.Rejected
        assertEquals(RejectionReason.GAME_NOT_ACTIVE, completeResult.reason)

        val cancelled = (GameEngine.apply(newSession(), GameAction.CancelGameConfirmed) as GameActionResult.Applied).session
        val cancelledResult = GameEngine.apply(cancelled, GameAction.CancelGameConfirmed) as GameActionResult.Rejected
        assertEquals(RejectionReason.GAME_NOT_ACTIVE, cancelledResult.reason)
        assertFalse(cancelled.status == GameStatus.COMPLETED)
    }
}
