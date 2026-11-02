package com.charlsemilbarquin.frameandfact.feature.practice

import com.charlsemilbarquin.frameandfact.domain.game.CategoryId
import com.charlsemilbarquin.frameandfact.domain.game.ChoiceId
import com.charlsemilbarquin.frameandfact.domain.game.Difficulty
import com.charlsemilbarquin.frameandfact.domain.game.TriviaQuestion
import kotlin.random.Random

enum class PracticeQuestionCount(val fixedCount: Int?, val label: String) {
    FIVE(5, "5 questions"),
    TEN(10, "10 questions"),
    FIFTEEN(15, "15 questions"),
    ALL_AVAILABLE(null, "All available questions")
}

data class PracticeConfiguration(
    val categoryId: CategoryId? = null,
    val difficulty: Difficulty? = null,
    val questionCount: PracticeQuestionCount = PracticeQuestionCount.FIVE
) {
    fun matchingQuestions(questions: List<TriviaQuestion>): List<TriviaQuestion> =
        questions.asSequence()
            .filter { categoryId == null || it.categoryId == categoryId }
            .filter { difficulty == null || it.difficulty == difficulty }
            .distinctBy { it.id }
            .toList()

    fun requestedCount(availableCount: Int): Int = questionCount.fixedCount ?: availableCount

    fun canStart(availableCount: Int): Boolean =
        availableCount > 0 && (questionCount.fixedCount?.let { availableCount >= it } ?: true)

    fun shortageMessage(availableCount: Int): String? {
        val fixedCount = questionCount.fixedCount ?: return null
        return if (availableCount < fixedCount) {
            "Only $availableCount question(s) match. Choose All available or adjust the filters."
        } else null
    }
}

data class PracticeAnswer(
    val question: TriviaQuestion,
    val selectedChoiceId: ChoiceId,
    val wasCorrect: Boolean
) {
    val selectedAnswer: String?
        get() = question.choices.firstOrNull { it.id == selectedChoiceId }?.text
    val correctAnswer: String?
        get() = question.choices.firstOrNull { it.id == question.correctChoiceId }?.text
}

data class PracticeSession(
    val configuration: PracticeConfiguration,
    val questions: List<TriviaQuestion>,
    val index: Int = 0,
    val selectedChoiceId: ChoiceId? = null,
    val answers: List<PracticeAnswer> = emptyList(),
    val showingFeedback: Boolean = false
) {
    val currentQuestion: TriviaQuestion? get() = questions.getOrNull(index)
    val isFinished: Boolean get() = index >= questions.size
    val isLastQuestion: Boolean get() = questions.isNotEmpty() && index == questions.lastIndex
    val incorrectAnswers: List<PracticeAnswer> get() = answers.filterNot { it.wasCorrect }
    val correctCount: Int get() = answers.count { it.wasCorrect }
    val incorrectCount: Int get() = incorrectAnswers.size
    val answeredCount: Int get() = answers.size
    val accuracyPercent: Int get() = if (answeredCount == 0) 0 else correctCount * 100 / answeredCount
}

/** Selects a fresh, unique question set and fairly spreads available categories and difficulties. */
object PracticeQuestionSelector {
    fun availableQuestions(
        questions: List<TriviaQuestion>,
        configuration: PracticeConfiguration
    ): List<TriviaQuestion> = configuration.matchingQuestions(questions)

    fun select(
        questions: List<TriviaQuestion>,
        configuration: PracticeConfiguration,
        random: Random = Random.Default
    ): List<TriviaQuestion>? {
        val remaining = availableQuestions(questions, configuration).toMutableList()
        val target = configuration.requestedCount(remaining.size)
        if (!configuration.canStart(remaining.size) || target <= 0) return null

        val selected = ArrayList<TriviaQuestion>(target)
        val categoryCounts = mutableMapOf<CategoryId, Int>()
        val difficultyCounts = mutableMapOf<Difficulty, Int>()
        repeat(target) {
            val availableCategories = remaining.map { it.categoryId }.distinct()
            val availableDifficulties = remaining.map { it.difficulty }.distinct()
            val lowestCategoryCount = if (configuration.categoryId == null) {
                availableCategories.minOf { categoryCounts[it] ?: 0 }
            } else null
            val lowestDifficultyCount = if (configuration.difficulty == null) {
                availableDifficulties.minOf { difficultyCounts[it] ?: 0 }
            } else null

            fun balanceScore(question: TriviaQuestion): Int =
                (if (lowestCategoryCount == null) 0 else (categoryCounts[question.categoryId] ?: 0) - lowestCategoryCount) +
                    (if (lowestDifficultyCount == null) 0 else (difficultyCounts[question.difficulty] ?: 0) - lowestDifficultyCount)

            val bestScore = remaining.minOf(::balanceScore)
            val candidates = remaining.indices.filter { balanceScore(remaining[it]) == bestScore }
            val chosenIndex = candidates[random.nextInt(candidates.size)]
            val chosen = remaining.removeAt(chosenIndex)
            selected += chosen
            categoryCounts[chosen.categoryId] = (categoryCounts[chosen.categoryId] ?: 0) + 1
            difficultyCounts[chosen.difficulty] = (difficultyCounts[chosen.difficulty] ?: 0) + 1
        }
        return selected
    }

    fun createSession(
        questions: List<TriviaQuestion>,
        configuration: PracticeConfiguration,
        random: Random = Random.Default
    ): PracticeSession? = select(questions, configuration, random)?.let {
        PracticeSession(configuration = configuration, questions = it)
    }
}

object PracticeReducer {
    fun selectChoice(state: PracticeSession, choiceId: ChoiceId): PracticeSession {
        if (state.showingFeedback || state.isFinished) return state
        val question = state.currentQuestion ?: return state
        if (question.choices.none { it.id == choiceId }) return state
        return state.copy(selectedChoiceId = choiceId)
    }

    fun submit(state: PracticeSession): PracticeSession {
        if (state.showingFeedback || state.isFinished) return state
        val question = state.currentQuestion ?: return state
        val selected = state.selectedChoiceId ?: return state
        if (question.choices.none { it.id == selected } || state.answers.any { it.question.id == question.id }) return state
        val answer = PracticeAnswer(question, selected, selected == question.correctChoiceId)
        return state.copy(answers = state.answers + answer, showingFeedback = true)
    }

    fun continueAfterFeedback(state: PracticeSession): PracticeSession =
        if (!state.showingFeedback) state
        else state.copy(index = state.index + 1, selectedChoiceId = null, showingFeedback = false)
}
