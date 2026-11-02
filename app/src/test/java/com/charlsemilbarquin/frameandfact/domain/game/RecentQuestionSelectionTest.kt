package com.charlsemilbarquin.frameandfact.domain.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RecentQuestionSelectionTest {
    @Test
    fun prefersUnseenQuestionsWithinEachDifficultyAndFallsBackWhenPoolIsExhausted() {
        val category = ApprovedCategories.all.first()
        val participant = Participant(ParticipantId("p1"), "Player", ParticipantKind.PLAYER)
        val questions = Difficulty.entries.flatMap { difficulty ->
            (1..6).map { n ->
                TriviaQuestion(
                    id = QuestionId("${difficulty.name}-$n"), categoryId = category.id,
                    difficulty = difficulty, prompt = "$difficulty question $n",
                    choices = listOf(TriviaChoice(ChoiceId("a-$n"), "Answer")),
                    correctChoiceId = ChoiceId("a-$n")
                )
            }
        }
        val recentlySeen = Difficulty.entries.flatMap { difficulty ->
            (1..4).map { n -> QuestionId("${difficulty.name}-$n") }
        }.toSet()
        val prompts = (1..2).map { n ->
            CameraPrompt(CameraPromptId("camera-$n"), category.id, "Prompt", "Safety", "Judge")
        }

        val freshSession = GameFactory.create(
            GameId("fresh"), listOf(participant), ApprovedCategories.all, setOf(category.id),
            questions, prompts, random = Random(3), mode = GameMode.SOLO,
            recentlyEncounteredQuestionIds = recentlySeen
        )
        assertEquals(6, freshSession.selectedQuestions.size)
        assertTrue(freshSession.selectedQuestions.none { it.id in recentlySeen })
        assertEquals(6, freshSession.selectedQuestions.map { it.id }.distinct().size)

        val exhaustedSession = GameFactory.create(
            GameId("exhausted"), listOf(participant), ApprovedCategories.all, setOf(category.id),
            questions, prompts, random = Random(4), mode = GameMode.SOLO,
            recentlyEncounteredQuestionIds = questions.map { it.id }.toSet()
        )
        assertEquals(6, exhaustedSession.selectedQuestions.size)
        assertEquals(6, exhaustedSession.selectedQuestions.map { it.id }.distinct().size)
    }
}
