package com.charlsemilbarquin.frameandfact.domain.game

import kotlin.random.Random

enum class GameSetupError {
    INVALID_PARTICIPANT_COUNT,
    DUPLICATE_PARTICIPANT_ID,
    EMPTY_PARTICIPANT_NAME,
    NO_CATEGORIES_SELECTED,
    UNKNOWN_CATEGORY,
    DUPLICATE_QUESTION_ID,
    DUPLICATE_CAMERA_PROMPT_ID,
    INSUFFICIENT_ELIGIBLE_QUESTIONS,
    INSUFFICIENT_CAMERA_PROMPTS,
    UNKNOWN_CAMERA_PROMPT_CATEGORY,
    INVALID_STARTING_PARTICIPANT
}

class GameSetupException(val reason: GameSetupError) : IllegalArgumentException(reason.name)

object GameFactory {
    const val ROUND_COUNT = 8
    const val TRIVIA_ROUND_COUNT = 6
    const val CAMERA_ROUND_COUNT = 2
    const val POINTS_PER_SUCCESS = 10
    const val MAX_SCORE = 80

    fun create(
        id: GameId,
        participants: List<Participant>,
        categories: List<Category>,
        selectedCategoryIds: Set<CategoryId>,
        availableQuestions: List<TriviaQuestion>,
        availableCameraPrompts: List<CameraPrompt>,
        random: Random = Random.Default,
        startingParticipantId: ParticipantId? = null,
        mode: GameMode = if (participants.size == 1) GameMode.SOLO else GameMode.MULTIPLAYER,
        recentlyEncounteredQuestionIds: Set<QuestionId> = emptySet()
    ): GameSession {
        val participantCountIsValid = when (mode) {
            GameMode.SOLO -> participants.size == 1
            GameMode.MULTIPLAYER -> participants.size in 2..6
        }
        if (!participantCountIsValid) fail(GameSetupError.INVALID_PARTICIPANT_COUNT)
        if (participants.map { it.id }.distinct().size != participants.size) {
            fail(GameSetupError.DUPLICATE_PARTICIPANT_ID)
        }
        if (participants.any { it.displayName.isBlank() }) fail(GameSetupError.EMPTY_PARTICIPANT_NAME)
        val normalizedParticipants = participants.map { it.copy(displayName = it.displayName.trim()) }

        if (selectedCategoryIds.isEmpty()) fail(GameSetupError.NO_CATEGORIES_SELECTED)
        val knownCategoryIds = categories.map { it.id }.toSet()
        if (!knownCategoryIds.containsAll(selectedCategoryIds)) fail(GameSetupError.UNKNOWN_CATEGORY)
        if (availableQuestions.map { it.id }.distinct().size != availableQuestions.size) {
            fail(GameSetupError.DUPLICATE_QUESTION_ID)
        }

        val eligibleQuestions = availableQuestions.filter { it.categoryId in selectedCategoryIds }
        if (eligibleQuestions.size < TRIVIA_ROUND_COUNT) {
            fail(GameSetupError.INSUFFICIENT_ELIGIBLE_QUESTIONS)
        }
        if (availableCameraPrompts.map { it.id }.distinct().size != availableCameraPrompts.size) {
            fail(GameSetupError.DUPLICATE_CAMERA_PROMPT_ID)
        }
        if (availableCameraPrompts.any { it.categoryId !in knownCategoryIds }) {
            fail(GameSetupError.UNKNOWN_CAMERA_PROMPT_CATEGORY)
        }
        if (availableCameraPrompts.size < CAMERA_ROUND_COUNT) {
            fail(GameSetupError.INSUFFICIENT_CAMERA_PROMPTS)
        }

        val starter = startingParticipantId ?: normalizedParticipants[random.nextInt(normalizedParticipants.size)].id
        val starterIndex = normalizedParticipants.indexOfFirst { it.id == starter }
        if (starterIndex < 0) fail(GameSetupError.INVALID_STARTING_PARTICIPANT)

        val selectedQuestions = TriviaQuestionSelector.select(
            eligibleQuestions = eligibleQuestions,
            random = random,
            recentlyEncounteredQuestionIds = recentlyEncounteredQuestionIds,
            count = TRIVIA_ROUND_COUNT
        )
        // Camera prompts are intentionally drawn from the full prompt bank, independent of trivia categories.
        val selectedCameraPrompts = availableCameraPrompts.shuffled(random).take(CAMERA_ROUND_COUNT)
        val rounds = mutableListOf<GameRound>()
        var triviaCursor = starterIndex
        var cameraCursor = starterIndex

        for (roundNumber in 1..ROUND_COUNT) {
            val roundId = RoundId(roundNumber)
            if (roundNumber in TRIVIA_ROUNDS) {
                val participant = normalizedParticipants[triviaCursor]
                val question = selectedQuestions[rounds.count { it.type == RoundType.TRIVIA }]
                rounds += GameRound(roundId, RoundType.TRIVIA, participant.id, questionId = question.id)
                triviaCursor = (triviaCursor + 1) % normalizedParticipants.size
            } else {
                val owner = normalizedParticipants[cameraCursor]
                val judge = if (normalizedParticipants.size == 1) null
                else normalizedParticipants[(cameraCursor + 1) % normalizedParticipants.size]
                val cameraPrompt = selectedCameraPrompts[rounds.count { it.type == RoundType.FIND_AND_FRAME }]
                rounds += GameRound(
                    roundId,
                    RoundType.FIND_AND_FRAME,
                    owner.id,
                    judgeId = judge?.id,
                    cameraPromptId = cameraPrompt.id
                )
                cameraCursor = (cameraCursor + 1) % normalizedParticipants.size
            }
        }

        return GameSession(
            id = id,
            participants = normalizedParticipants,
            selectedCategoryIds = selectedCategoryIds.toSet(),
            startingParticipantId = starter,
            rounds = rounds,
            selectedQuestions = selectedQuestions,
            mode = mode
        )
    }

    private val TRIVIA_ROUNDS = setOf(1, 2, 3, 5, 6, 7)

    private fun fail(error: GameSetupError): Nothing = throw GameSetupException(error)
}

