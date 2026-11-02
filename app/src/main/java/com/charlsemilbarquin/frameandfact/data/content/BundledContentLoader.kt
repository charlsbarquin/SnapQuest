package com.charlsemilbarquin.frameandfact.data.content

import android.content.res.AssetManager
import com.google.gson.JsonParseException
import java.io.FileNotFoundException
import java.io.IOException

fun interface ContentAssetReader {
    fun read(path: String): String
}

class ContentAssetMissingException(path: String, cause: Throwable? = null) : IOException("Missing content asset: $path", cause)

enum class ContentLoadFailure { MISSING_ASSET, MALFORMED_JSON, INVALID_CONTENT }

class ContentLoadException(
    val failure: ContentLoadFailure,
    message: String,
    val issues: List<ContentIssue> = emptyList(),
    cause: Throwable? = null
) : IOException(message, cause)

class BundledContentLoader(private val reader: ContentAssetReader) {
    fun load(): ContentBank {
        val triviaJson = read(TRIVIA_ASSET)
        val cameraJson = read(CAMERA_ASSET)
        val bank = try {
            ContentBank(
                triviaQuestions = ContentJsonCodec.parseTriviaQuestions(triviaJson),
                cameraPrompts = ContentJsonCodec.parseCameraPrompts(cameraJson)
            )
        } catch (error: ContentFormatException) {
            throw ContentLoadException(ContentLoadFailure.MALFORMED_JSON, error.message ?: "Invalid content JSON", cause = error)
        } catch (error: JsonParseException) {
            throw ContentLoadException(ContentLoadFailure.MALFORMED_JSON, error.message ?: "Invalid content JSON", cause = error)
        }

        val issues = ContentValidator.validate(bank)
        if (issues.isNotEmpty()) {
            throw ContentLoadException(
                ContentLoadFailure.INVALID_CONTENT,
                "Bundled content failed validation (${issues.size} issue(s)): ${issues.take(5).joinToString()}",
                issues
            )
        }
        return bank
    }

    private fun read(path: String): String = try {
        reader.read(path)
    } catch (error: ContentAssetMissingException) {
        throw ContentLoadException(ContentLoadFailure.MISSING_ASSET, error.message ?: "Missing asset $path", cause = error)
    } catch (error: FileNotFoundException) {
        throw ContentLoadException(ContentLoadFailure.MISSING_ASSET, "Missing content asset: $path", cause = error)
    } catch (error: IOException) {
        throw ContentLoadException(ContentLoadFailure.MISSING_ASSET, "Could not read content asset $path: ${error.message}", cause = error)
    }

    companion object {
        const val TRIVIA_ASSET = "content/trivia_questions.json"
        const val CAMERA_ASSET = "content/camera_prompts.json"
    }
}

class AndroidAssetContentReader(private val assets: AssetManager) : ContentAssetReader {
    override fun read(path: String): String = try {
        assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
    } catch (error: FileNotFoundException) {
        throw ContentAssetMissingException(path, error)
    }
}

