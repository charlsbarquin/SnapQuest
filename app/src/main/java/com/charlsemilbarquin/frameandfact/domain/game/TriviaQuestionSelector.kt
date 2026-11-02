package com.charlsemilbarquin.frameandfact.domain.game

import kotlin.random.Random

/** Selects unique questions while spreading a full-bank game across categories and difficulty. */
object TriviaQuestionSelector {
    fun select(
        eligibleQuestions: List<TriviaQuestion>,
        random: Random = Random.Default,
        recentlyEncounteredQuestionIds: Set<QuestionId> = emptySet(),
        count: Int = GameFactory.TRIVIA_ROUND_COUNT
    ): List<TriviaQuestion> {
        require(count >= 0)
        require(eligibleQuestions.map { it.id }.distinct().size == eligibleQuestions.size) {
            "Eligible trivia question IDs must be unique."
        }
        require(eligibleQuestions.size >= count) { "Not enough unique eligible questions." }
        if (count == 0) return emptyList()

        val remaining = eligibleQuestions.shuffled(random).toMutableList()
        val selected = mutableListOf<TriviaQuestion>()
        val categoryCounts = mutableMapOf<CategoryId, Int>()

        while (selected.size < count) {
            val leastUsedDifficultyCount = Difficulty.entries
                .filter { difficulty -> remaining.any { it.difficulty == difficulty } }
                .minOfOrNull { difficulty -> selected.count { it.difficulty == difficulty } }
                ?: error("No eligible question remains before selection is complete.")
            val difficultyChoices = Difficulty.entries.filter { difficulty ->
                remaining.any { it.difficulty == difficulty } &&
                    selected.count { it.difficulty == difficulty } == leastUsedDifficultyCount
            }
            val difficulty = difficultyChoices.random(random)
            val difficultyPool = remaining.filter { it.difficulty == difficulty }
            val leastUsedCategoryCount = difficultyPool.minOf { categoryCounts[it.categoryId] ?: 0 }
            val categoryChoices = difficultyPool
                .filter { (categoryCounts[it.categoryId] ?: 0) == leastUsedCategoryCount }
                .map { it.categoryId }
                .distinct()
                .shuffled(random)
            val categoryId = categoryChoices.first()
            val categoryPool = difficultyPool.filter { it.categoryId == categoryId }
            val unseen = categoryPool.filter { it.id !in recentlyEncounteredQuestionIds }
            val chosen = (if (unseen.isNotEmpty()) unseen else categoryPool).random(random)

            selected += chosen
            remaining.remove(chosen)
            categoryCounts[chosen.categoryId] = (categoryCounts[chosen.categoryId] ?: 0) + 1
        }
        return selected.shuffled(random)
    }
}
