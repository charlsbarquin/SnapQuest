package com.charlsemilbarquin.frameandfact.domain.game

data class ParticipantId(val value: String)
data class CategoryId(val value: String)
data class QuestionId(val value: String)
data class ChoiceId(val value: String)
data class CameraPromptId(val value: String)
data class GameId(val value: String)
data class RoundId(val number: Int)

enum class ParticipantKind { PLAYER, TEAM }
enum class GameMode { MULTIPLAYER, SOLO }

data class Participant(
    val id: ParticipantId,
    val displayName: String,
    val kind: ParticipantKind,
    /** Optional stable local profile link; a matching display name never implies a link. */
    val profileId: String? = null
)

data class Category(val id: CategoryId, val name: String)

object ApprovedCategories {
    val all = listOf(
        Category(CategoryId("general_knowledge"), "General Knowledge"),
        Category(CategoryId("science"), "Science"),
        Category(CategoryId("technology"), "Technology"),
        Category(CategoryId("history"), "History"),
        Category(CategoryId("geography"), "Geography"),
        Category(CategoryId("mathematics"), "Mathematics"),
        Category(CategoryId("sports"), "Sports"),
        Category(CategoryId("arts_culture"), "Arts & Culture")
    )
}

enum class Difficulty { EASY, MEDIUM, HARD }
enum class TriviaQuestionType { MULTIPLE_CHOICE }

data class TriviaChoice(val id: ChoiceId, val text: String)

data class TriviaQuestion(
    val id: QuestionId,
    val categoryId: CategoryId,
    val difficulty: Difficulty,
    val prompt: String,
    val choices: List<TriviaChoice>,
    val correctChoiceId: ChoiceId,
    val explanation: String? = null,
    val sourceTitle: String = "",
    val sourceUrl: String = "",
    val verificationDate: String = "",
    val type: TriviaQuestionType = TriviaQuestionType.MULTIPLE_CHOICE
)

data class CameraPrompt(
    val id: CameraPromptId,
    val categoryId: CategoryId,
    val prompt: String,
    val safetyNote: String,
    val judgingGuidance: String,
    val hint: String? = null
)

enum class RoundType { TRIVIA, FIND_AND_FRAME }

data class GameRound(
    val id: RoundId,
    val type: RoundType,
    /** Trivia player for a trivia round; camera owner for a camera round. */
    val participantId: ParticipantId,
    val questionId: QuestionId? = null,
    val judgeId: ParticipantId? = null,
    val cameraPromptId: CameraPromptId? = null
)

enum class RoundOutcome(val points: Int) {
    TRIVIA_CORRECT(10),
    TRIVIA_INCORRECT(0),
    TRIVIA_SKIPPED(0),
    CAMERA_ACCEPTED(10),
    CAMERA_REJECTED(0),
    CAMERA_SKIPPED(0)
}

enum class GameStatus { ACTIVE, COMPLETED, CANCELLED }

data class ParticipantScore(
    val participantId: ParticipantId,
    val correctTriviaRounds: Int,
    val acceptedCameraRounds: Int
) {
    val total: Int get() = (correctTriviaRounds + acceptedCameraRounds) * 10
}

data class GameSession(
    val id: GameId,
    val participants: List<Participant>,
    val selectedCategoryIds: Set<CategoryId>,
    val startingParticipantId: ParticipantId,
    val rounds: List<GameRound>,
    val selectedQuestions: List<TriviaQuestion>,
    val outcomes: Map<RoundId, RoundOutcome> = emptyMap(),
    val status: GameStatus = GameStatus.ACTIVE,
    val mode: GameMode = if (participants.size == 1) GameMode.SOLO else GameMode.MULTIPLAYER,
    /** Submitted trivia choices are retained so completed History can review the actual answer. */
    val submittedChoicesByRound: Map<RoundId, ChoiceId> = emptyMap()
) {
    val currentRound: GameRound?
        get() = if (status == GameStatus.ACTIVE) rounds.firstOrNull { it.id !in outcomes } else null

    val scoreBreakdown: List<ParticipantScore>
        get() = participants.map { participant ->
            val successfulRounds = rounds
                .filter { it.participantId == participant.id }
                .mapNotNull { round -> outcomes[round.id]?.let { round to it } }
            ParticipantScore(
                participantId = participant.id,
                correctTriviaRounds = successfulRounds.count { it.second == RoundOutcome.TRIVIA_CORRECT },
                acceptedCameraRounds = successfulRounds.count { it.second == RoundOutcome.CAMERA_ACCEPTED }
            )
        }

    fun scoreFor(participantId: ParticipantId): Int =
        scoreBreakdown.firstOrNull { it.participantId == participantId }?.total ?: 0

    fun completedGameOrNull(): CompletedGame? =
        if (status == GameStatus.COMPLETED) {
            CompletedGame(id, participants, rounds, outcomes, scoreBreakdown, mode, selectedQuestions, submittedChoicesByRound)
        } else {
            null
        }
}

data class CompletedGame(
    val id: GameId,
    val participants: List<Participant>,
    val rounds: List<GameRound>,
    val outcomes: Map<RoundId, RoundOutcome>,
    val scores: List<ParticipantScore>,
    val mode: GameMode = if (participants.size == 1) GameMode.SOLO else GameMode.MULTIPLAYER,
    val selectedQuestions: List<TriviaQuestion> = emptyList(),
    val submittedChoicesByRound: Map<RoundId, ChoiceId> = emptyMap()
) {
    val totalScore: Int get() = scores.sumOf { it.total }
    val highestScore: Int get() = scores.maxOfOrNull { it.total } ?: 0
    val winners: List<ParticipantId> get() = scores.filter { it.total == highestScore }.map { it.participantId }
}

