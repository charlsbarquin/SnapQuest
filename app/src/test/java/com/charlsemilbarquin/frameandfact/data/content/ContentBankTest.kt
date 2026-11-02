package com.charlsemilbarquin.frameandfact.data.content

import com.charlsemilbarquin.frameandfact.domain.game.ApprovedCategories
import com.charlsemilbarquin.frameandfact.domain.game.CameraPrompt
import com.charlsemilbarquin.frameandfact.domain.game.CameraPromptId
import com.charlsemilbarquin.frameandfact.domain.game.CategoryId
import com.charlsemilbarquin.frameandfact.domain.game.ChoiceId
import com.charlsemilbarquin.frameandfact.domain.game.Difficulty
import com.charlsemilbarquin.frameandfact.domain.game.GameFactory
import com.charlsemilbarquin.frameandfact.domain.game.GameId
import com.charlsemilbarquin.frameandfact.domain.game.Participant
import com.charlsemilbarquin.frameandfact.domain.game.ParticipantId
import com.charlsemilbarquin.frameandfact.domain.game.ParticipantKind
import com.charlsemilbarquin.frameandfact.domain.game.QuestionId
import com.charlsemilbarquin.frameandfact.domain.game.TriviaChoice
import com.charlsemilbarquin.frameandfact.domain.game.TriviaQuestion
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentBankTest {
    private fun asset(path: String): String {
        val file = Path.of(System.getProperty("user.dir"), "src", "main", "assets", path).toFile()
        if (!file.isFile) throw ContentAssetMissingException(path)
        return Files.readString(file.toPath())
    }

    private fun loadBundled() = BundledContentLoader(::asset).load()

    private fun sampleQuestion(
        id: String = "sample",
        category: String = "science",
        difficulty: Difficulty = Difficulty.EASY
    ) = TriviaQuestion(
        id = QuestionId(id),
        categoryId = CategoryId(category),
        difficulty = difficulty,
        prompt = "Which sample answer is correct?",
        choices = listOf(
            TriviaChoice(ChoiceId("a"), "Alpha"),
            TriviaChoice(ChoiceId("b"), "Beta"),
            TriviaChoice(ChoiceId("c"), "Gamma"),
            TriviaChoice(ChoiceId("d"), "Delta")
        ),
        correctChoiceId = ChoiceId("a"),
        explanation = "The sample answer is Alpha.",
        sourceTitle = "Example source",
        sourceUrl = "https://example.org/source",
        verificationDate = "2026-10-30"
    )

    private fun samplePrompt(id: String = "prompt", category: String = "science") = CameraPrompt(
        id = CameraPromptId(id),
        categoryId = CategoryId(category),
        prompt = "Find a safe object with a visible circle.",
        safetyNote = "Do not photograph people or private information.",
        judgingGuidance = "The judge checks for a visible circle."
    )

    private fun issueText(issues: List<ContentIssue>) = issues.joinToString("\n")

    @Test
    fun validJsonParsesIntoExistingDomainModels() {
        val questions = ContentJsonCodec.parseTriviaQuestions(
            """{"schemaVersion":1,"questions":[{"id":"q1","categoryId":"science","type":"multiple_choice","difficulty":"easy","prompt":"Which?","choices":[{"id":"a","text":"A"},{"id":"b","text":"B"},{"id":"c","text":"C"},{"id":"d","text":"D"}],"correctChoiceId":"a","explanation":"Reason","sourceTitle":"Source","sourceUrl":"https://example.org","verificationDate":"2026-10-30"}]}"""
        )
        val prompts = ContentJsonCodec.parseCameraPrompts(
            """{"schemaVersion":1,"prompts":[{"id":"p1","categoryId":"arts_culture","prompt":"Find art","safetyNote":"Stay safe","judgingGuidance":"Check the image","hint":"Look for a handmade detail."}]}"""
        )

        assertEquals(QuestionId("q1"), questions.single().id)
        assertEquals(Difficulty.EASY, questions.single().difficulty)
        assertEquals(ChoiceId("a"), questions.single().correctChoiceId)
        assertEquals(CameraPromptId("p1"), prompts.single().id)
        assertEquals("Look for a handmade detail.", prompts.single().hint)
    }

    @Test
    fun cameraHintMayBeOmittedButInvalidOptionalTypesAreRejected() {
        val missingHint = ContentJsonCodec.parseCameraPrompts(
            """{"schemaVersion":1,"prompts":[{"id":"p1","categoryId":"arts_culture","prompt":"Find art","safetyNote":"Stay safe","judgingGuidance":"Check the image"}]}"""
        ).single()
        assertEquals(null, missingHint.hint)

        val error = runCatching {
            ContentJsonCodec.parseCameraPrompts(
                """{"schemaVersion":1,"prompts":[{"id":"p1","categoryId":"arts_culture","prompt":"Find art","safetyNote":"Stay safe","judgingGuidance":"Check the image","hint":42}]}"""
            )
        }.exceptionOrNull() as ContentFormatException
        assertTrue(error.message!!.contains("prompts[0].hint must be a string"))
    }

    @Test
    fun missingAssetAndMalformedJsonHaveClearFailureReasons() {
        val missing = runCatching {
            BundledContentLoader { throw ContentAssetMissingException(it) }.load()
        }.exceptionOrNull() as ContentLoadException
        assertEquals(ContentLoadFailure.MISSING_ASSET, missing.failure)
        assertTrue(missing.message!!.contains("content/trivia_questions.json"))

        val malformed = runCatching {
            BundledContentLoader { "{" }.load()
        }.exceptionOrNull() as ContentLoadException
        assertEquals(ContentLoadFailure.MALFORMED_JSON, malformed.failure)
        assertTrue(malformed.message!!.contains("JSON"))
    }

    @Test
    fun parserReportsMissingRequiredFieldsWithRecordPath() {
        val error = runCatching {
            ContentJsonCodec.parseTriviaQuestions("""{"schemaVersion":1,"questions":[{"id":"q1"}]}""")
        }.exceptionOrNull() as ContentFormatException

        assertTrue(error.message!!.contains("questions[0].choices is required"))
    }

    @Test
    fun completeBundledBankParsesAndHasExactApprovedCountsAndDistribution() {
        val bank = loadBundled()

        assertEquals(144, bank.triviaQuestions.size)
        assertEquals(24, bank.cameraPrompts.size)
        assertTrue(ContentValidator.validate(bank).isEmpty())
        assertTrue(bank.cameraPrompts.all { !it.hint.isNullOrBlank() })
        ApprovedCategories.all.forEach { category ->
            val categoryQuestions = bank.triviaQuestions.filter { it.categoryId == category.id }
            assertEquals(18, categoryQuestions.size)
            Difficulty.entries.forEach { difficulty ->
                assertEquals(6, categoryQuestions.count { it.difficulty == difficulty })
            }
            assertEquals(3, bank.cameraPrompts.count { it.categoryId == category.id })
        }
    }

    @Test
    fun duplicateQuestionIdsAreReported() {
        val bank = loadBundled()
        val rows = bank.triviaQuestions.toMutableList()
        rows[1] = rows[1].copy(id = rows[0].id)

        val issues = ContentValidator.validate(bank.copy(triviaQuestions = rows))
        assertTrue(issueText(issues).contains("Duplicate ID '${rows[0].id.value}'"))
    }

    @Test
    fun duplicateCameraPromptIdsAreReported() {
        val bank = loadBundled()
        val rows = bank.cameraPrompts.toMutableList()
        rows[1] = rows[1].copy(id = rows[0].id)

        assertTrue(issueText(ContentValidator.validate(bank.copy(cameraPrompts = rows))).contains("Duplicate ID '${rows[0].id.value}'"))
    }

    @Test
    fun blankFieldsUnknownCategoriesAndMalformedSourceMetadataAreReported() {
        val bank = loadBundled()
        val questions = bank.triviaQuestions.toMutableList()
        questions[0] = questions[0].copy(
            prompt = " ",
            categoryId = CategoryId("not_a_category"),
            sourceTitle = "",
            sourceUrl = "not a URL",
            verificationDate = "not-a-date"
        )
        val prompts = bank.cameraPrompts.toMutableList()
        prompts[0] = prompts[0].copy(prompt = "", safetyNote = "", judgingGuidance = "")
        val errors = issueText(ContentValidator.validate(bank.copy(triviaQuestions = questions, cameraPrompts = prompts)))

        assertTrue(errors.contains("questions[0].prompt: Must not be blank"))
        assertTrue(errors.contains("questions[0].categoryId: Unknown category"))
        assertTrue(errors.contains("questions[0].sourceTitle: Required source title"))
        assertTrue(errors.contains("questions[0].sourceUrl"))
        assertTrue(errors.contains("questions[0].verificationDate"))
        assertTrue(errors.contains("prompts[0].prompt: Must not be blank"))
        assertTrue(errors.contains("prompts[0].safetyNote: Required safety note"))
        assertTrue(errors.contains("prompts[0].judgingGuidance: Required judging guidance"))
    }

    @Test
    fun explanationsAreRequiredAndCannotBeBlank() {
        val bank = loadBundled()
        val rows = bank.triviaQuestions.toMutableList()
        rows[0] = rows[0].copy(explanation = null)
        rows[1] = rows[1].copy(explanation = "  ")

        val issues = issueText(ContentValidator.validate(bank.copy(triviaQuestions = rows)))

        assertTrue(issues.contains("questions[0].explanation: A concise explanation is required"))
        assertTrue(issues.contains("questions[1].explanation: A concise explanation is required"))
    }

    @Test
    fun incorrectBankAndDifficultyCountsAreReported() {
        val bank = loadBundled()
        val missingOne = bank.copy(triviaQuestions = bank.triviaQuestions.dropLast(1))
        assertTrue(issueText(ContentValidator.validate(missingOne)).contains("Expected 144 records, found 143"))
        val fewerPrompts = bank.copy(cameraPrompts = bank.cameraPrompts.dropLast(1))
        assertTrue(issueText(ContentValidator.validate(fewerPrompts)).contains("Expected 24 records, found 23"))

        val changed = bank.triviaQuestions.toMutableList()
        changed[0] = changed[0].copy(difficulty = Difficulty.HARD)
        val distributionErrors = issueText(ContentValidator.validate(bank.copy(triviaQuestions = changed)))
        assertTrue(distributionErrors.contains("Expected 6 questions, found 5"))
    }

    @Test
    fun invalidChoiceCountDuplicateChoicesAndWrongAnswerReferenceAreReported() {
        val bank = loadBundled()
        val rows = bank.triviaQuestions.toMutableList()
        val original = rows[0]
        rows[0] = original.copy(choices = original.choices.take(3), correctChoiceId = ChoiceId("missing"))
        rows[1] = rows[1].copy(choices = rows[1].choices.mapIndexed { index, choice ->
            if (index == 1) choice.copy(id = rows[1].choices[0].id, text = rows[1].choices[0].text) else choice
        })
        val errors = issueText(ContentValidator.validate(bank.copy(triviaQuestions = rows)))

        assertTrue(errors.contains("questions[0].choices: Expected exactly four choices, found 3"))
        assertTrue(errors.contains("questions[0].correctChoiceId: Must reference exactly one existing choice"))
        assertTrue(errors.contains("questions[1].choices[1].id: Duplicate ID"))
        assertTrue(errors.contains("questions[1].choices[1].text: Duplicate choice text"))
    }

    @Test
    fun triviaIsSelectableAcrossEveryDifficultyAndFilteredByCategory() {
        val bank = loadBundled()
        val science = bank.triviaQuestions.filter { it.categoryId == CategoryId("science") }
        val balancedSix = Difficulty.entries.flatMap { difficulty -> science.filter { it.difficulty == difficulty }.take(2) }
        val prompts = bank.cameraPrompts
        val people = listOf(
            Participant(ParticipantId("a"), "A", ParticipantKind.PLAYER),
            Participant(ParticipantId("b"), "B", ParticipantKind.TEAM)
        )
        val session = GameFactory.create(
            id = GameId("test-content-selection"), participants = people, categories = ApprovedCategories.all,
            selectedCategoryIds = setOf(CategoryId("science")), availableQuestions = bank.triviaQuestions,
            availableCameraPrompts = prompts, random = Random(41), startingParticipantId = people.first().id
        )

        assertEquals(Difficulty.entries.toSet(), GameFactory.create(
            id = GameId("difficulty-eligibility"), participants = people, categories = ApprovedCategories.all,
            selectedCategoryIds = setOf(CategoryId("science")), availableQuestions = balancedSix,
            availableCameraPrompts = prompts, random = Random(2), startingParticipantId = people.first().id
        ).selectedQuestions.map { it.difficulty }.toSet())
        assertTrue(session.selectedQuestions.all { it.categoryId == CategoryId("science") })
        assertEquals(6, session.selectedQuestions.map { it.id }.toSet().size)
    }

    @Test
    fun fullCategorySelectionAlwaysIncludesEveryEligibleDifficulty() {
        val bank = loadBundled()
        val science = bank.triviaQuestions.filter { it.categoryId == CategoryId("science") }
        val people = listOf(
            Participant(ParticipantId("a"), "A", ParticipantKind.PLAYER),
            Participant(ParticipantId("b"), "B", ParticipantKind.PLAYER)
        )

        repeat(100) { seed ->
            val session = GameFactory.create(
                id = GameId("balanced-selection-$seed"),
                participants = people,
                categories = ApprovedCategories.all,
                selectedCategoryIds = setOf(CategoryId("science")),
                availableQuestions = science,
                availableCameraPrompts = bank.cameraPrompts,
                random = Random(seed),
                startingParticipantId = people.first().id
            )

            assertEquals(Difficulty.entries.toSet(), session.selectedQuestions.map { it.difficulty }.toSet())
            assertEquals(6, session.selectedQuestions.map { it.id }.toSet().size)
        }
    }

    @Test
    fun allCategoryStandardGamesSpreadQuestionsAcrossCategoriesAndAreSeedReproducible() {
        val bank = loadBundled()
        val players = listOf(Participant(ParticipantId("solo"), "Solo", ParticipantKind.PLAYER))
        fun session(seed: Int) = GameFactory.create(
            id = GameId("category-distribution-$seed"), participants = players,
            categories = ApprovedCategories.all,
            selectedCategoryIds = ApprovedCategories.all.map { it.id }.toSet(),
            availableQuestions = bank.triviaQuestions, availableCameraPrompts = bank.cameraPrompts,
            random = Random(seed), startingParticipantId = players.single().id
        )

        val first = session(811)
        val sameSeed = session(811)
        assertEquals(first.selectedQuestions.map { it.id }, sameSeed.selectedQuestions.map { it.id })
        assertEquals(6, first.selectedQuestions.map { it.id }.toSet().size)
        assertTrue(first.selectedQuestions.groupingBy { it.categoryId }.eachCount().values.all { it <= 1 })
        assertEquals(2, first.selectedQuestions.groupingBy { it.difficulty }.eachCount().values.minOrNull())
        assertEquals(2, first.selectedQuestions.groupingBy { it.difficulty }.eachCount().values.maxOrNull())
    }

    @Test
    fun cameraPromptsAreChosenIndependentlyAndWithoutReplacement() {
        val bank = loadBundled()
        val people = listOf(
            Participant(ParticipantId("a"), "A", ParticipantKind.PLAYER),
            Participant(ParticipantId("b"), "B", ParticipantKind.PLAYER)
        )
        val onlyArtsPrompts = bank.cameraPrompts.filter { it.categoryId == CategoryId("arts_culture") }
        val session = GameFactory.create(
            id = GameId("independent-camera-prompts"), participants = people, categories = ApprovedCategories.all,
            selectedCategoryIds = setOf(CategoryId("mathematics")),
            availableQuestions = bank.triviaQuestions, availableCameraPrompts = onlyArtsPrompts,
            random = Random(3), startingParticipantId = people.first().id
        )
        val cameraRounds = session.rounds.filter { it.cameraPromptId != null }

        assertEquals(2, cameraRounds.size)
        assertEquals(2, cameraRounds.map { it.cameraPromptId }.toSet().size)
        assertTrue(cameraRounds.all { round -> onlyArtsPrompts.any { it.id == round.cameraPromptId } })
        assertFalse(cameraRounds.any { round -> onlyArtsPrompts.single { it.id == round.cameraPromptId }.categoryId == CategoryId("mathematics") })
    }

    @Test
    fun factoryStillRejectsInsufficientEligibleTriviaQuestions() {
        val bank = loadBundled()
        val science = bank.triviaQuestions.filter { it.categoryId == CategoryId("science") }.take(5)
        val people = listOf(
            Participant(ParticipantId("a"), "A", ParticipantKind.PLAYER),
            Participant(ParticipantId("b"), "B", ParticipantKind.PLAYER)
        )
        val failure = runCatching {
            GameFactory.create(
                id = GameId("too-few"), participants = people, categories = ApprovedCategories.all,
                selectedCategoryIds = setOf(CategoryId("science")), availableQuestions = science,
                availableCameraPrompts = bank.cameraPrompts, random = Random(1)
            )
        }.exceptionOrNull()

        assertTrue(failure is com.charlsemilbarquin.frameandfact.domain.game.GameSetupException)
    }
}
