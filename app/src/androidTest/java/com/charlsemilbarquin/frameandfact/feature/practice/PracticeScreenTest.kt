package com.charlsemilbarquin.frameandfact.feature.practice

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.charlsemilbarquin.frameandfact.domain.game.CategoryId
import com.charlsemilbarquin.frameandfact.domain.game.ChoiceId
import com.charlsemilbarquin.frameandfact.domain.game.Difficulty
import com.charlsemilbarquin.frameandfact.domain.game.QuestionId
import com.charlsemilbarquin.frameandfact.domain.game.TriviaChoice
import com.charlsemilbarquin.frameandfact.domain.game.TriviaQuestion
import com.charlsemilbarquin.frameandfact.ui.navigation.HomeScreen
import com.charlsemilbarquin.frameandfact.ui.theme.FrameFactTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PracticeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun homeScreenHasPracticeEntryPoint() {
        var practiceOpens = 0
        var quickGamesRequested = 0
        var statisticsOpened = 0
        compose.setContent {
            FrameFactTheme {
                HomeScreen(
                    onMultiplayer = {}, onSoloPlayer = {}, onPractice = { practiceOpens++ }, onQuickPlay = { quickGamesRequested++ },
                    onQuickTrivia = {}, onCategoryPractice = {}, onHistory = {}, onStatistics = { statisticsOpened++ }, onShortHistory = {}, onHelp = {}, onSettings = {}
                )
            }
        }

        compose.onNodeWithText("SnapQuest").assertIsDisplayed()
        compose.onNodeWithText("See It. Think It. Prove It.").assertIsDisplayed()
        compose.onNodeWithText("Practice").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, practiceOpens)
        compose.onNodeWithText("Start Game").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, quickGamesRequested)
        compose.onNodeWithText("Quick Trivia").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Category Practice").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("High Scores").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, statisticsOpened)
    }

    @Test
    fun setupShowsMatchingPoolAndDisablesFixedCountWhenPoolIsTooSmall() {
        compose.setContent { FrameFactTheme { PracticeScreen(questions = questions(4), onExit = {}) } }

        compose.onNodeWithText("Questions matching filters: 4").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("5 questions (not enough available)").assertIsDisplayed()
        compose.onNodeWithText("Only 4 question(s) match. Choose All available or adjust the filters.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Start Practice").assertIsNotEnabled()
        compose.onNodeWithText("All available questions").performScrollTo().performClick()
        compose.onNodeWithText("Start Practice").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Question 1 of 4").assertIsDisplayed()
    }

    @Test
    fun filtersUpdateMatchingCountAndQuestionDifficulty() {
        compose.setContent { FrameFactTheme { PracticeScreen(questions = questions(18), onExit = {}) } }

        compose.onNodeWithText("Questions matching filters: 18").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Science").performScrollTo().performClick()
        compose.onNodeWithText("Questions matching filters: 9").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Hard").performScrollTo().performClick()
        compose.onNodeWithText("Questions matching filters: 9").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Start Practice").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Science · Hard").assertIsDisplayed()
    }

    @Test
    fun noMatchingQuestionsDisablesStartWithHelpfulMessage() {
        compose.setContent { FrameFactTheme { PracticeScreen(questions = questions(6), onExit = {}) } }

        compose.onNodeWithText("Technology").performScrollTo().performClick()
        compose.onNodeWithText("Questions matching filters: 0").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("No questions match these filters. Choose another category or difficulty.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Start Practice").assertIsNotEnabled()
    }

    @Test
    fun submitsOneAnswerShowsFeedbackAndCompletesResultsAndIncorrectReview() {
        compose.setContent { FrameFactTheme { PracticeScreen(questions = questions(12), onExit = {}) } }

        compose.onNodeWithText("Start Practice").performScrollTo().performClick()
        compose.onNodeWithText("Question 1 of 5").assertIsDisplayed()
        compose.onNodeWithText("A").assertIsDisplayed()
        compose.onNodeWithText("B").assertIsDisplayed()
        compose.onNodeWithText("C").assertIsDisplayed()
        compose.onNodeWithText("D").assertIsDisplayed()
        compose.onNodeWithText("Correct answer: A").assertDoesNotExist()
        compose.onNodeWithText("Submit Answer").assertIsNotEnabled()
        compose.onNodeWithText("B").performClick()
        compose.onNodeWithText("Submit Answer").performClick()
        compose.onNodeWithText("Incorrect").assertIsDisplayed()
        compose.onNodeWithText("Correct answer: A").assertIsDisplayed()
        compose.onNodeWithText("Saved explanation.").assertIsDisplayed()
        compose.onNodeWithText("Next Question").performClick()

        compose.onNodeWithText("B").performClick()
        compose.onNodeWithText("Submit Answer").performClick()
        compose.onNodeWithText("Incorrect").assertIsDisplayed()
        compose.onNodeWithText("Next Question").performClick()
        repeat(2) {
            compose.onNodeWithText("A").performClick()
            compose.onNodeWithText("Submit Answer").performClick()
            compose.onNodeWithText("Correct").assertIsDisplayed()
            compose.onNodeWithText("Next Question").performClick()
        }
        compose.onNodeWithText("A").performClick()
        compose.onNodeWithText("Submit Answer").performClick()
        compose.onNodeWithText("Finish Practice").performClick()

        compose.onNodeWithText("Questions answered: 5").assertIsDisplayed()
        compose.onNodeWithText("Correct: 3").assertIsDisplayed()
        compose.onNodeWithText("Incorrect: 2").assertIsDisplayed()
        compose.onNodeWithText("Accuracy: 60%").assertIsDisplayed()
        compose.onNodeWithText("Review Incorrect Answers").performClick()
        compose.onNodeWithText("1 of 2").assertIsDisplayed()
        compose.onNodeWithText("Your answer (incorrect): B").assertIsDisplayed()
        compose.onNodeWithText("Correct answer: A").assertIsDisplayed()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("2 of 2").assertIsDisplayed()
        compose.onNodeWithText("Previous").performClick()
        compose.onNodeWithText("1 of 2").assertIsDisplayed()
        compose.onNodeWithText("Back to Results").performClick()
        compose.onNodeWithText("Practice Results").assertIsDisplayed()
    }

    @Test
    fun exitConfirmationCanBeCanceledWithoutLosingProgress() {
        var exits = 0
        compose.setContent { FrameFactTheme { PracticeScreen(questions = questions(6), onExit = { exits++ }) } }

        compose.onNodeWithText("Start Practice").performScrollTo().performClick()
        compose.onNodeWithText("Exit Practice").performClick()
        compose.onNodeWithText("Exit practice?").assertIsDisplayed()
        compose.onNodeWithText("Keep practicing").performClick()
        compose.onNodeWithText("Question 1 of 5").assertIsDisplayed()
        assertEquals(0, exits)

        compose.onNodeWithText("Exit Practice").performClick()
        compose.onNodeWithText("Exit practice").performClick()
        assertEquals(1, exits)
    }

    @Test
    fun allCorrectResultsOfferAnHonestEmptyReviewAndPracticeAgainReturnsToSetup() {
        compose.setContent { FrameFactTheme(darkTheme = true) { PracticeScreen(questions = questions(6), onExit = {}) } }

        compose.onNodeWithText("Start Practice").performScrollTo().performClick()
        repeat(5) { index ->
            compose.onNodeWithText("A").performClick()
            compose.onNodeWithText("Submit Answer").performClick()
            compose.onNodeWithText("Correct").assertIsDisplayed()
            compose.onNodeWithText(if (index == 4) "Finish Practice" else "Next Question").performClick()
        }
        compose.onNodeWithText("You got every question right. There are no incorrect answers to review.").assertIsDisplayed()
        compose.onNodeWithText("Review Incorrect Answers").performClick()
        compose.onNodeWithText("Review Incorrect Answers").assertIsDisplayed()
        compose.onNodeWithText("Back to Results").performClick()
        compose.onNodeWithText("Practice Again").performClick()
        compose.onNodeWithText("Questions matching filters: 6").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Practice").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Start Practice").performScrollTo().performClick()
        compose.onNodeWithText("Question 1 of 5").assertIsDisplayed()
    }

    @Test
    fun missingExplanationIsNotInventedAndLightThemeRenders() {
        val bank = questions(5).map { it.copy(explanation = null) }
        compose.setContent { FrameFactTheme(darkTheme = false) { PracticeScreen(questions = bank, onExit = {}) } }

        compose.onNodeWithText("Start Practice").performScrollTo().performClick()
        compose.onNodeWithText("A").performClick()
        compose.onNodeWithText("Submit Answer").performClick()
        compose.onNodeWithText("No explanation is available for this question.").assertIsDisplayed()
    }

    private fun questions(count: Int): List<TriviaQuestion> = (1..count).map { number ->
        val category = if (number % 2 == 0) CategoryId("science") else CategoryId("history")
        val difficulty = if (number % 2 == 0) Difficulty.HARD else Difficulty.EASY
        TriviaQuestion(
            id = QuestionId("practice-$number"), categoryId = category, difficulty = difficulty,
            prompt = "Practice question $number?",
            choices = listOf("A", "B", "C", "D").mapIndexed { index, text ->
                TriviaChoice(ChoiceId("practice-${number}-choice-$index"), text)
            },
            correctChoiceId = ChoiceId("practice-${number}-choice-0"),
            explanation = "Saved explanation."
        )
    }
}
