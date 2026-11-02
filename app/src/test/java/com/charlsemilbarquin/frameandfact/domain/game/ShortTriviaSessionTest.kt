package com.charlsemilbarquin.frameandfact.domain.game

import com.charlsemilbarquin.frameandfact.data.history.toCompletedSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShortTriviaSessionTest {
    private val category = ApprovedCategories.all.first()
    private fun questions() = (1..6).map { n ->
        TriviaQuestion(
            QuestionId("q$n"), category.id, Difficulty.EASY, "Question $n?",
            listOf(TriviaChoice(ChoiceId("c$n"), "Correct"), TriviaChoice(ChoiceId("w$n"), "Wrong")),
            ChoiceId("c$n"), explanation = "Explanation $n"
        )
    }

    @Test
    fun passAndPlayRotatesTriviaTurnsScoresOnceAndCreatesAnIndependentSummary() {
        val participants = listOf(
            Participant(ParticipantId("p1"), "Alex", ParticipantKind.PLAYER),
            Participant(ParticipantId("p2"), "Sam", ParticipantKind.PLAYER)
        )
        var session = ShortTriviaSessionFactory.create(
            GameId("short-test"), ShortSessionType.QUICK_PLAY, participants, questions(), 5, random = Random(7)
        )!!
        assertEquals(5, session.questions.map { it.id }.distinct().size)
        assertEquals("p1", session.currentParticipant?.id?.value)
        assertNull(session.toCompletedSummary(100L))

        val firstChoice = session.currentQuestion!!.correctChoiceId
        session = ShortTriviaReducer.submit(ShortTriviaReducer.selectChoice(session, firstChoice))
        assertEquals(10, session.scoreFor(ParticipantId("p1")))
        assertSame(session, ShortTriviaReducer.submit(session))
        session = ShortTriviaReducer.continueAfterFeedback(session)
        assertEquals("p2", session.currentParticipant?.id?.value)
        session = ShortTriviaReducer.continueAfterFeedback(ShortTriviaReducer.skip(session))
        assertEquals(0, session.scoreFor(ParticipantId("p2")))

        while (!session.isComplete) {
            val correct = session.currentQuestion!!.correctChoiceId
            session = ShortTriviaReducer.continueAfterFeedback(
                ShortTriviaReducer.submit(ShortTriviaReducer.selectChoice(session, correct))
            )
        }
        val summary = session.toCompletedSummary(100L)!!
        assertEquals(ShortSessionType.QUICK_PLAY, summary.sessionType)
        assertEquals(50, summary.participants.sumOf { it.maximumScore })
        assertEquals(40, summary.participants.sumOf { it.score })
        assertEquals(1, summary.participants.sumOf { it.incorrectOrSkippedAnswers })
        assertFalse(summary.participants.any { it.maximumScore > 30 })
    }

    @Test
    fun categoryFocusedModeUsesOnlyTheSelectedCategoryAndDifficulty() {
        val bank = questions() + questions().map { it.copy(id = QuestionId("hard-${it.id.value}"), difficulty = Difficulty.HARD) }
        val session = ShortTriviaSessionFactory.create(
            GameId("focus"), ShortSessionType.CATEGORY_FOCUSED,
            listOf(Participant(ParticipantId("p"), "Player", ParticipantKind.PLAYER)),
            bank, 5, categoryId = category.id, difficulty = Difficulty.HARD, random = Random(2)
        )!!
        assertTrue(session.questions.all { it.categoryId == category.id && it.difficulty == Difficulty.HARD })
        assertNull(ShortTriviaSessionFactory.create(
            GameId("too-many"), ShortSessionType.CATEGORY_FOCUSED, session.participants,
            bank, 7, categoryId = category.id, difficulty = Difficulty.HARD
        ))
    }
}
