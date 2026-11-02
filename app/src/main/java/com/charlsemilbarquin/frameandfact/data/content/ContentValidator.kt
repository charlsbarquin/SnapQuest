package com.charlsemilbarquin.frameandfact.data.content

import com.charlsemilbarquin.frameandfact.domain.game.ApprovedCategories
import com.charlsemilbarquin.frameandfact.domain.game.CameraPrompt
import com.charlsemilbarquin.frameandfact.domain.game.CategoryId
import com.charlsemilbarquin.frameandfact.domain.game.Difficulty
import com.charlsemilbarquin.frameandfact.domain.game.TriviaQuestion
import com.charlsemilbarquin.frameandfact.domain.game.TriviaQuestionType
import java.net.URI
import java.net.URISyntaxException
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale

data class ContentBank(
    val triviaQuestions: List<TriviaQuestion>,
    val cameraPrompts: List<CameraPrompt>
)

data class ContentIssue(val path: String, val message: String) {
    override fun toString(): String = "$path: $message"
}

object ContentValidator {
    const val EXPECTED_QUESTION_COUNT = 144
    const val EXPECTED_CAMERA_PROMPT_COUNT = 24
    const val QUESTIONS_PER_CATEGORY = 18
    const val QUESTIONS_PER_CATEGORY_DIFFICULTY = 6
    const val PROMPTS_PER_CATEGORY = 3

    private val categoryIds = ApprovedCategories.all.map { it.id }.toSet()

    fun validate(bank: ContentBank): List<ContentIssue> {
        val issues = mutableListOf<ContentIssue>()
        val questions = bank.triviaQuestions
        val prompts = bank.cameraPrompts

        if (ApprovedCategories.all.size != 8 || categoryIds.size != 8) {
            issues += ContentIssue("categories", "The approved category catalog must contain eight unique categories")
        }
        if (questions.size != EXPECTED_QUESTION_COUNT) {
            issues += ContentIssue("questions", "Expected $EXPECTED_QUESTION_COUNT records, found ${questions.size}")
        }
        if (prompts.size != EXPECTED_CAMERA_PROMPT_COUNT) {
            issues += ContentIssue("prompts", "Expected $EXPECTED_CAMERA_PROMPT_COUNT records, found ${prompts.size}")
        }

        questions.forEachIndexed { index, question -> validateQuestion(question, index, issues) }
        checkUniqueIds(questions.map { it.id.value }, "questions", issues)
        val normalizedQuestionText = questions.map { normalize(it.prompt) }
        normalizedQuestionText.withIndex().groupBy { it.value }.filterValues { it.size > 1 }.forEach { (text, rows) ->
            rows.forEach { issues += ContentIssue("questions[${it.index}].prompt", "Duplicate normalized question text '$text'") }
        }

        ApprovedCategories.all.forEach { category ->
            val categoryQuestions = questions.filter { it.categoryId == category.id }
            if (categoryQuestions.size != QUESTIONS_PER_CATEGORY) {
                issues += ContentIssue(
                    "questions.${category.id.value}",
                    "Expected $QUESTIONS_PER_CATEGORY questions, found ${categoryQuestions.size}"
                )
            }
            Difficulty.entries.forEach { difficulty ->
                val count = categoryQuestions.count { it.difficulty == difficulty }
                if (count != QUESTIONS_PER_CATEGORY_DIFFICULTY) {
                    issues += ContentIssue(
                        "questions.${category.id.value}.${difficulty.name.lowercase(Locale.ROOT)}",
                        "Expected $QUESTIONS_PER_CATEGORY_DIFFICULTY questions, found $count"
                    )
                }
            }
        }

        prompts.forEachIndexed { index, prompt -> validateCameraPrompt(prompt, index, issues) }
        checkUniqueIds(prompts.map { it.id.value }, "prompts", issues)
        ApprovedCategories.all.forEach { category ->
            val count = prompts.count { it.categoryId == category.id }
            if (count != PROMPTS_PER_CATEGORY) {
                issues += ContentIssue(
                    "prompts.${category.id.value}",
                    "Expected approximately $PROMPTS_PER_CATEGORY prompts (exactly three for this 24-item balanced bank), found $count"
                )
            }
        }
        return issues
    }

    private fun validateQuestion(question: TriviaQuestion, index: Int, issues: MutableList<ContentIssue>) {
        val path = "questions[$index]"
        if (question.id.value.isBlank()) issues += ContentIssue("$path.id", "Must not be blank")
        if (question.prompt.isBlank()) issues += ContentIssue("$path.prompt", "Must not be blank")
        if (question.categoryId !in categoryIds) issues += ContentIssue("$path.categoryId", "Unknown category '${question.categoryId.value}'")
        if (question.type != TriviaQuestionType.MULTIPLE_CHOICE) issues += ContentIssue("$path.type", "Only multiple_choice is supported")
        if (question.choices.size != 4) issues += ContentIssue("$path.choices", "Expected exactly four choices, found ${question.choices.size}")
        question.choices.forEachIndexed { choiceIndex, choice ->
            if (choice.id.value.isBlank()) issues += ContentIssue("$path.choices[$choiceIndex].id", "Must not be blank")
            if (choice.text.isBlank()) issues += ContentIssue("$path.choices[$choiceIndex].text", "Must not be blank")
        }
        checkUniqueIds(question.choices.map { it.id.value }, "$path.choices", issues)
        question.choices.map { normalize(it.text) }.withIndex().groupBy { it.value }.filterValues { it.size > 1 }
            .forEach { (text, rows) -> rows.forEach { issues += ContentIssue("$path.choices[${it.index}].text", "Duplicate choice text '$text'") } }
        if (question.correctChoiceId.value.isBlank() || question.choices.count { it.id == question.correctChoiceId } != 1) {
            issues += ContentIssue("$path.correctChoiceId", "Must reference exactly one existing choice")
        }
        if (question.explanation.isNullOrBlank()) {
            issues += ContentIssue("$path.explanation", "A concise explanation is required")
        }
        if (question.sourceTitle.isBlank()) issues += ContentIssue("$path.sourceTitle", "Required source title is missing")
        validateSourceUrl(question.sourceUrl, "$path.sourceUrl", issues)
        try {
            LocalDate.parse(question.verificationDate)
        } catch (_: DateTimeParseException) {
            issues += ContentIssue("$path.verificationDate", "Must be a valid ISO date (YYYY-MM-DD)")
        }
    }

    private fun validateCameraPrompt(prompt: CameraPrompt, index: Int, issues: MutableList<ContentIssue>) {
        val path = "prompts[$index]"
        if (prompt.id.value.isBlank()) issues += ContentIssue("$path.id", "Must not be blank")
        if (prompt.categoryId !in categoryIds) issues += ContentIssue("$path.categoryId", "Unknown category '${prompt.categoryId.value}'")
        if (prompt.prompt.isBlank()) issues += ContentIssue("$path.prompt", "Must not be blank")
        if (prompt.safetyNote.isBlank()) issues += ContentIssue("$path.safetyNote", "Required safety note is missing")
        if (prompt.judgingGuidance.isBlank()) issues += ContentIssue("$path.judgingGuidance", "Required judging guidance is missing")
        if (prompt.hint != null && prompt.hint.isBlank()) issues += ContentIssue("$path.hint", "Hint must not be blank when provided")
    }

    private fun validateSourceUrl(url: String, path: String, issues: MutableList<ContentIssue>) {
        try {
            val uri = URI(url)
            if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) {
                issues += ContentIssue(path, "Must be an absolute HTTPS source URL")
            }
        } catch (_: URISyntaxException) {
            issues += ContentIssue(path, "Must be a valid absolute HTTPS source URL")
        }
        if (url.isBlank()) issues += ContentIssue(path, "Required source URL is missing")
    }

    private fun checkUniqueIds(ids: List<String>, path: String, issues: MutableList<ContentIssue>) {
        ids.withIndex().groupBy { it.value }.filterValues { it.size > 1 }.forEach { (id, rows) ->
            rows.forEach { issues += ContentIssue("$path[${it.index}].id", "Duplicate ID '$id'") }
        }
    }

    private fun normalize(value: String): String = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
}

