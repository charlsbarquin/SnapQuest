package com.charlsemilbarquin.frameandfact.domain.game

import kotlin.random.Random

enum class ShortSessionType { QUICK_PLAY, CATEGORY_FOCUSED }

data class ShortTriviaAnswer(
    val participantId: ParticipantId,
    val question: TriviaQuestion,
    val selectedChoiceId: ChoiceId?,
    val correct: Boolean
)

data class ShortTriviaSession(
    val id: GameId,
    val type: ShortSessionType,
    val participants: List<Participant>,
    val questions: List<TriviaQuestion>,
    val answers: List<ShortTriviaAnswer> = emptyList(),
    val index: Int = 0,
    val selectedChoiceId: ChoiceId? = null,
    val showingFeedback: Boolean = false
) {
    val isComplete: Boolean get() = index >= questions.size
    val currentQuestion: TriviaQuestion? get() = questions.getOrNull(index)
    val currentParticipant: Participant? get() = participants.getOrNull(index % participants.size)
    val maxScore: Int get() = questions.size * GameFactory.POINTS_PER_SUCCESS
    fun scoreFor(participantId: ParticipantId): Int = answers.count { it.participantId == participantId && it.correct } * 10
}

object ShortTriviaSessionFactory {
    fun create(
        id: GameId,
        type: ShortSessionType,
        participants: List<Participant>,
        availableQuestions: List<TriviaQuestion>,
        questionCount: Int,
        categoryId: CategoryId? = null,
        difficulty: Difficulty? = null,
        random: Random = Random.Default
    ): ShortTriviaSession? {
        require(questionCount > 0)
        if (participants.isEmpty() || participants.size > 6 || participants.map { it.id }.distinct().size != participants.size) return null
        if (participants.any { it.displayName.isBlank() }) return null
        val eligible = availableQuestions.filter { question ->
            (categoryId == null || question.categoryId == categoryId) &&
                (difficulty == null || question.difficulty == difficulty)
        }
        if (eligible.size < questionCount || eligible.map { it.id }.distinct().size != eligible.size) return null
        return ShortTriviaSession(id, type, participants, eligible.shuffled(random).take(questionCount))
    }
}

object ShortTriviaReducer {
    fun selectChoice(state: ShortTriviaSession, choiceId: ChoiceId): ShortTriviaSession {
        if (state.isComplete || state.showingFeedback) return state
        val question = state.currentQuestion ?: return state
        return if (question.choices.any { it.id == choiceId }) state.copy(selectedChoiceId = choiceId) else state
    }

    fun submit(state: ShortTriviaSession): ShortTriviaSession = resolve(state, state.selectedChoiceId)

    fun skip(state: ShortTriviaSession): ShortTriviaSession = resolve(state, null)

    private fun resolve(state: ShortTriviaSession, choiceId: ChoiceId?): ShortTriviaSession {
        if (state.isComplete || state.showingFeedback) return state
        val question = state.currentQuestion ?: return state
        val participant = state.currentParticipant ?: return state
        if (choiceId != null && question.choices.none { it.id == choiceId }) return state
        return state.copy(
            answers = state.answers + ShortTriviaAnswer(participant.id, question, choiceId, choiceId == question.correctChoiceId),
            showingFeedback = true
        )
    }

    fun continueAfterFeedback(state: ShortTriviaSession): ShortTriviaSession =
        if (!state.showingFeedback) state
        else state.copy(index = state.index + 1, selectedChoiceId = null, showingFeedback = false)
}
