package com.charlsemilbarquin.frameandfact.data.content

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonParseException
import com.charlsemilbarquin.frameandfact.domain.game.CameraPrompt
import com.charlsemilbarquin.frameandfact.domain.game.CameraPromptId
import com.charlsemilbarquin.frameandfact.domain.game.CategoryId
import com.charlsemilbarquin.frameandfact.domain.game.ChoiceId
import com.charlsemilbarquin.frameandfact.domain.game.Difficulty
import com.charlsemilbarquin.frameandfact.domain.game.QuestionId
import com.charlsemilbarquin.frameandfact.domain.game.TriviaChoice
import com.charlsemilbarquin.frameandfact.domain.game.TriviaQuestion
import com.charlsemilbarquin.frameandfact.domain.game.TriviaQuestionType

class ContentFormatException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

object ContentJsonCodec {
    fun parseTriviaQuestions(json: String): List<TriviaQuestion> {
        val rows = parseRows(json, "questions")
        return rows.mapIndexed { index, element ->
            val path = "questions[$index]"
            val row = element.asObject(path)
            val choices = row.requiredArray("choices", path).mapIndexed { choiceIndex, choice ->
                val choicePath = "$path.choices[$choiceIndex]"
                val choiceObject = choice.asObject(choicePath)
                TriviaChoice(
                    id = ChoiceId(choiceObject.requiredString("id", choicePath)),
                    text = choiceObject.requiredString("text", choicePath)
                )
            }
            val difficulty = when (val value = row.requiredString("difficulty", path)) {
                "easy" -> Difficulty.EASY
                "medium" -> Difficulty.MEDIUM
                "hard" -> Difficulty.HARD
                else -> throw ContentFormatException("$path.difficulty has unsupported value '$value'")
            }
            val type = when (val value = row.requiredString("type", path)) {
                "multiple_choice" -> TriviaQuestionType.MULTIPLE_CHOICE
                else -> throw ContentFormatException("$path.type has unsupported value '$value'")
            }
            TriviaQuestion(
                id = QuestionId(row.requiredString("id", path)),
                categoryId = CategoryId(row.requiredString("categoryId", path)),
                difficulty = difficulty,
                prompt = row.requiredString("prompt", path),
                choices = choices,
                correctChoiceId = ChoiceId(row.requiredString("correctChoiceId", path)),
                explanation = row.optionalString("explanation", path),
                sourceTitle = row.requiredString("sourceTitle", path),
                sourceUrl = row.requiredString("sourceUrl", path),
                verificationDate = row.requiredString("verificationDate", path),
                type = type
            )
        }
    }

    fun parseCameraPrompts(json: String): List<CameraPrompt> =
        parseRows(json, "prompts").mapIndexed { index, element ->
            val path = "prompts[$index]"
            val row = element.asObject(path)
            CameraPrompt(
                id = CameraPromptId(row.requiredString("id", path)),
                categoryId = CategoryId(row.requiredString("categoryId", path)),
                prompt = row.requiredString("prompt", path),
                safetyNote = row.requiredString("safetyNote", path),
                judgingGuidance = row.requiredString("judgingGuidance", path),
                hint = row.optionalString("hint", path)
            )
        }

    private fun parseRows(json: String, arrayName: String): JsonArray {
        val root = try {
            JsonParser.parseString(json)
        } catch (error: JsonParseException) {
            throw ContentFormatException("JSON syntax error: ${error.message}", error)
        }
        val document = root.asObject("root")
        val schemaVersion = document.get("schemaVersion")
        if (schemaVersion == null || !schemaVersion.isJsonPrimitive || !schemaVersion.asJsonPrimitive.isNumber) {
            throw ContentFormatException("schemaVersion is required and must be a number")
        }
        if (schemaVersion.asInt != 1) throw ContentFormatException("Unsupported schemaVersion ${schemaVersion.asString}")
        return document.requiredArray(arrayName, "root")
    }

    private fun JsonElement.asObject(path: String): JsonObject {
        if (!isJsonObject) throw ContentFormatException("$path must be an object")
        return asJsonObject
    }

    private fun JsonObject.requiredArray(name: String, path: String): JsonArray {
        val value = get(name) ?: throw ContentFormatException("$path.$name is required")
        if (!value.isJsonArray) throw ContentFormatException("$path.$name must be an array")
        return value.asJsonArray
    }

    private fun JsonObject.requiredString(name: String, path: String): String {
        val value = get(name) ?: throw ContentFormatException("$path.$name is required")
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) {
            throw ContentFormatException("$path.$name must be a string")
        }
        return value.asString
    }

    private fun JsonObject.optionalString(name: String, path: String): String? {
        val value = get(name) ?: return null
        if (value.isJsonNull) return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) {
            throw ContentFormatException("$path.$name must be a string when provided")
        }
        return value.asString
    }
}

