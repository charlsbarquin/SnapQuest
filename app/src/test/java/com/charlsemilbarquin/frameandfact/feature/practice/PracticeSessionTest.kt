package com.charlsemilbarquin.frameandfact.feature.practice

import com.charlsemilbarquin.frameandfact.domain.game.CategoryId
import com.charlsemilbarquin.frameandfact.domain.game.ChoiceId
import com.charlsemilbarquin.frameandfact.domain.game.Difficulty
import com.charlsemilbarquin.frameandfact.domain.game.QuestionId
import com.charlsemilbarquin.frameandfact.domain.game.TriviaChoice
import com.charlsemilbarquin.frameandfact.domain.game.TriviaQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PracticeSessionTest {
    private val categories = listOf(CategoryId("science"), CategoryId("history"), CategoryId("sports"))
    private val bank = buildList {
        var number = 1
        categories.forEach { category ->
            Difficulty.entries.forEach { difficulty ->
                repeat(6) { add(question(number++, category, difficulty)) }
            }
        }
    }

    @Test
    fun filteringCountsAllAndSelectedCategoriesDifficultiesAndCombinations() {
        assertEquals(54, PracticeQuestionSelector.availableQuestions(bank, PracticeConfiguration()).size)
        assertEquals(18, PracticeQuestionSelector.availableQuestions(
            bank, PracticeConfiguration(categoryId = categories.first())
        ).size)
        assertEquals(18, PracticeQuestionSelector.availableQuestions(
            bank, PracticeConfiguration(difficulty = Difficulty.EASY)
        ).size)
        assertEquals(6, PracticeQuestionSelector.availableQuestions(
            bank, PracticeConfiguration(categories.first(), Difficulty.EASY)
        ).size)
        assertEquals(0, PracticeQuestionSelector.availableQuestions(
            bank, PracticeConfiguration(CategoryId("unknown"), Difficulty.HARD)
        ).size)
    }

    @Test
    fun fixedQuestionCountsAndAllAvailableUseTheFilteredPoolSize() {
        listOf(
            PracticeQuestionCount.FIVE to 5,
            PracticeQuestionCount.TEN to 10,
            PracticeQuestionCount.FIFTEEN to 15,
            PracticeQuestionCount.ALL_AVAILABLE to 54
        ).forEach { (count, expected) ->
            val selected = PracticeQuestionSelector.select(bank, PracticeConfiguration(questionCount = count), Random(15))!!
            assertEquals(expected, selected.size)
        }
    }

    @Test
    fun selectionIsDeterministicUniqueAndRespectsBothFilters() {
        val config = PracticeConfiguration(categories[1], Difficulty.HARD, PracticeQuestionCount.FIVE)
        val first = PracticeQuestionSelector.select(bank, config, Random(220))!!
        val second = PracticeQuestionSelector.select(bank, config, Random(220))!!
        assertEquals(first.map { it.id }, second.map { it.id })
        assertEquals(5, first.map { it.id }.distinct().size)
        assertTrue(first.all { it.categoryId == categories[1] && it.difficulty == Difficulty.HARD })
    }

    @Test
    fun allDifficultyAndCategorySelectionBalancesAvailableDimensions() {
        val selected = PracticeQuestionSelector.select(
            bank,
            PracticeConfiguration(questionCount = PracticeQuestionCount.FIFTEEN),
            Random(8)
        )!!
        val categoryCounts = selected.groupingBy { it.categoryId }.eachCount().values
        val difficultyCounts = selected.groupingBy { it.difficulty }.eachCount().values
        assertTrue(categoryCounts.max() - categoryCounts.min() <= 1)
        assertTrue(difficultyCounts.max() - difficultyCounts.min() <= 1)
    }

    @Test
    fun undersizedPoolDoesNotRepeatAndAllAvailableIsAnAlternative() {
        val smallBank = bank.take(4)
        val five = PracticeConfiguration(questionCount = PracticeQuestionCount.FIVE)
        assertFalse(five.canStart(4))
        assertTrue(five.shortageMessage(4)?.contains("Only 4") == true)
        assertNull(PracticeQuestionSelector.createSession(smallBank, five, Random(1)))
        val all = PracticeConfiguration(questionCount = PracticeQuestionCount.ALL_AVAILABLE)
        val session = PracticeQuestionSelector.createSession(smallBank, all, Random(1))!!
        assertEquals(4, session.questions.size)
        assertEquals(4, session.questions.map { it.id }.distinct().size)
    }

    @Test
    fun submittingCorrectAndIncorrectAnswersRecordsAnswerAndPreventsDuplicates() {
        val question = bank.first()
        val session = PracticeQuestionSelector.createSession(
            listOf(question), PracticeConfiguration(questionCount = PracticeQuestionCount.ALL_AVAILABLE), Random(1)
        )!!
        val correctId = question.correctChoiceId
        val correct = PracticeReducer.submit(PracticeReducer.selectChoice(session, correctId))
        assertTrue(correct.showingFeedback)
        assertEquals(1, correct.answers.size)
        assertTrue(correct.answers.single().wasCorrect)
        assertEquals(question.choices.first { it.id == correctId }.text, correct.answers.single().correctAnswer)
        assertSame(correct, PracticeReducer.submit(correct))

        val wrongId = question.choices.first { it.id != correctId }.id
        val incorrect = PracticeReducer.submit(PracticeReducer.selectChoice(session, wrongId))
        assertFalse(incorrect.answers.single().wasCorrect)
        assertEquals(question.choices.first { it.id == wrongId }.text, incorrect.answers.single().selectedAnswer)
        assertEquals(question.explanation, incorrect.answers.single().question.explanation)
    }

    @Test
    fun missingOrInvalidSelectionCannotBeSubmittedAndMissingExplanationRemainsAbsent() {
        val question = bank.first().copy(explanation = null)
        val session = PracticeQuestionSelector.createSession(
            listOf(question), PracticeConfiguration(questionCount = PracticeQuestionCount.ALL_AVAILABLE), Random(3)
        )!!
        assertSame(session, PracticeReducer.submit(session))
        assertSame(session, PracticeReducer.selectChoice(session, ChoiceId("not-a-choice")))
        val submitted = PracticeReducer.submit(PracticeReducer.selectChoice(session, question.correctChoiceId))
        assertNull(submitted.answers.single().question.explanation)
    }

    @Test
    fun nextQuestionResetsSelectionAndFeedbackAndFinishedSessionIsSafe() {
        val session = PracticeQuestionSelector.createSession(bank, PracticeConfiguration(questionCount = PracticeQuestionCount.FIVE), Random(10))!!
        val first = session.currentQuestion!!
        val submitted = PracticeReducer.submit(PracticeReducer.selectChoice(session, first.correctChoiceId))
        val next = PracticeReducer.continueAfterFeedback(submitted)
        assertEquals(1, next.index)
        assertNull(next.selectedChoiceId)
        assertFalse(next.showingFeedback)
        assertFalse(next.isFinished)
        val finished = next.copy(index = next.questions.size)
        assertTrue(finished.isFinished)
        assertSame(finished, PracticeReducer.submit(finished))
    }

    @Test
    fun accuracyAndIncorrectReviewAreSafeForEmptyAllCorrectAndAllIncorrectResults() {
        val empty = PracticeSession(PracticeConfiguration(), emptyList())
        assertEquals(0, empty.accuracyPercent)
        assertEquals(0, empty.answeredCount)

        val questions = bank.take(3)
        val initial = PracticeSession(PracticeConfiguration(), questions)
        val allCorrect = answerAll(initial) { it.correctChoiceId }
        assertEquals(3, allCorrect.correctCount)
        assertEquals(0, allCorrect.incorrectCount)
        assertEquals(100, allCorrect.accuracyPercent)
        assertTrue(allCorrect.incorrectAnswers.isEmpty())

        val allWrong = answerAll(initial) { q -> q.choices.first { it.id != q.correctChoiceId }.id }
        assertEquals(0, allWrong.correctCount)
        assertEquals(3, allWrong.incorrectCount)
        assertEquals(0, allWrong.accuracyPercent)
        assertEquals(questions.map { it.id }, allWrong.incorrectAnswers.map { it.question.id })
    }

    @Test
    fun practiceAgainCanCreateANewSelectionWithTheSameRetainedConfiguration() {
        val config = PracticeConfiguration(categories[0], Difficulty.EASY, PracticeQuestionCount.FIVE)
        val first = PracticeQuestionSelector.createSession(bank, config, Random(1))!!
        val again = PracticeQuestionSelector.createSession(bank, config, Random(2))!!
        assertEquals(config, again.configuration)
        assertTrue(first !== again)
        assertEquals(5, again.questions.size)
        assertTrue(again.questions.all { it.categoryId == categories[0] && it.difficulty == Difficulty.EASY })
        assertTrue(again.answers.isEmpty())
        assertEquals(5, again.questions.map { it.id }.distinct().size)
    }

    private fun answerAll(
        initial: PracticeSession,
        choice: (TriviaQuestion) -> ChoiceId
    ): PracticeSession {
        var state = initial
        while (!state.isFinished) {
            val current = state.currentQuestion!!
            state = PracticeReducer.submit(PracticeReducer.selectChoice(state, choice(current)))
            state = PracticeReducer.continueAfterFeedback(state)
        }
        return state
    }

    private fun question(number: Int, category: CategoryId, difficulty: Difficulty) = TriviaQuestion(
        id = QuestionId("q$number"), categoryId = category, difficulty = difficulty,
        prompt = "Question $number?", choices = (1..4).map { choiceIndex ->
            TriviaChoice(ChoiceId("q${number}c$choiceIndex"), "Choice $choiceIndex")
        }, correctChoiceId = ChoiceId("q${number}c1"), explanation = "Explanation $number."
    )
}
