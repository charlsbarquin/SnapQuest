package com.charlsemilbarquin.frameandfact.feature.setup

import com.charlsemilbarquin.frameandfact.data.content.ContentBank
import com.charlsemilbarquin.frameandfact.domain.game.ApprovedCategories
import com.charlsemilbarquin.frameandfact.domain.game.CategoryId
import com.charlsemilbarquin.frameandfact.domain.game.GameFactory
import com.charlsemilbarquin.frameandfact.domain.game.GameId
import com.charlsemilbarquin.frameandfact.domain.game.GameMode
import com.charlsemilbarquin.frameandfact.domain.game.GameSession
import com.charlsemilbarquin.frameandfact.domain.game.GameSetupException
import com.charlsemilbarquin.frameandfact.domain.game.Participant
import com.charlsemilbarquin.frameandfact.domain.game.ParticipantId
import com.charlsemilbarquin.frameandfact.domain.game.ParticipantKind
import java.util.UUID
import kotlin.random.Random

sealed interface CategorySelection {
    data class Single(val categoryId: CategoryId) : CategorySelection
    data class Selected(val categoryIds: Set<CategoryId>) : CategorySelection
    data object Mixed : CategorySelection

    fun categoryIds(): Set<CategoryId> = when (this) {
        is Single -> setOf(categoryId)
        is Selected -> categoryIds
        Mixed -> ApprovedCategories.all.map { it.id }.toSet()
    }
}

enum class GameCategoryMode { STANDARD, CATEGORY_FOCUSED }

data class ParticipantDraft(
    val id: ParticipantId,
    val name: String,
    val profileId: String? = null
)

data class GameSetupDraft(
    val mode: GameMode = GameMode.MULTIPLAYER,
    val participantKind: ParticipantKind = ParticipantKind.PLAYER,
    val participants: List<ParticipantDraft> = List(if (mode == GameMode.SOLO) 1 else 2) {
        ParticipantDraft(newParticipantId(), if (mode == GameMode.SOLO) DEFAULT_SOLO_PLAYER_NAME else "")
    },
    val categorySelection: CategorySelection = CategorySelection.Mixed,
    val categoryMode: GameCategoryMode = GameCategoryMode.STANDARD,
    val focusedCategoryId: CategoryId? = null
) {
    companion object {
        const val DEFAULT_SOLO_PLAYER_NAME = "Player"
        const val MIN_PARTICIPANTS = 2
        const val MAX_PARTICIPANTS = 6
        private fun newParticipantId() = ParticipantId("participant-${UUID.randomUUID()}")

        fun create(mode: GameMode = GameMode.MULTIPLAYER) = GameSetupDraft(mode = mode)

        fun quickPlay() = create(GameMode.SOLO).copy(categorySelection = CategorySelection.Mixed)
    }

    fun addParticipant(): GameSetupDraft =
        if (mode == GameMode.SOLO || participants.size >= MAX_PARTICIPANTS) this
        else copy(participants = participants + ParticipantDraft(newParticipantId(), ""))

    fun removeParticipant(id: ParticipantId): GameSetupDraft =
        if (mode == GameMode.SOLO || participants.size <= MIN_PARTICIPANTS) this
        else copy(participants = participants.filterNot { it.id == id })

    fun updateParticipantName(id: ParticipantId, name: String): GameSetupDraft =
        copy(participants = participants.map { if (it.id == id) it.copy(name = name) else it })

    fun updateParticipantProfile(id: ParticipantId, profileId: String?): GameSetupDraft =
        copy(participants = participants.map { if (it.id == id) it.copy(profileId = profileId) else it })

    fun updateSelectedCategories(categoryIds: Set<CategoryId>): GameSetupDraft =
        copy(categorySelection = CategorySelection.Selected(categoryIds))

    fun updateSingleCategory(categoryId: CategoryId): GameSetupDraft =
        copy(categorySelection = CategorySelection.Single(categoryId))

    fun activeCategoryIds(): Set<CategoryId> = when (categoryMode) {
        GameCategoryMode.STANDARD -> categorySelection.categoryIds()
        GameCategoryMode.CATEGORY_FOCUSED -> focusedCategoryId?.let(::setOf).orEmpty()
    }

}

enum class SetupIssueCode {
    PARTICIPANT_COUNT,
    DUPLICATE_PARTICIPANT_ID,
    EMPTY_PARTICIPANT_NAME,
    EMPTY_CATEGORY_SELECTION,
    UNKNOWN_CATEGORY,
    TOO_FEW_QUESTIONS
}

data class SetupIssue(val code: SetupIssueCode, val message: String)

object GameSetupValidator {
    fun validate(draft: GameSetupDraft, content: ContentBank): List<SetupIssue> = buildList {
        val validParticipantCount = when (draft.mode) {
            GameMode.SOLO -> draft.participants.size == 1
            GameMode.MULTIPLAYER -> draft.participants.size in GameSetupDraft.MIN_PARTICIPANTS..GameSetupDraft.MAX_PARTICIPANTS
        }
        if (!validParticipantCount) {
            add(SetupIssue(SetupIssueCode.PARTICIPANT_COUNT, if (draft.mode == GameMode.SOLO) {
                "Solo Player mode requires one player."
            } else "Enter 2 to 6 players or teams."))
        }
        if (draft.participants.map { it.id }.distinct().size != draft.participants.size) {
            add(SetupIssue(SetupIssueCode.DUPLICATE_PARTICIPANT_ID, "Participant IDs must be unique."))
        }
        if (draft.mode == GameMode.MULTIPLAYER && draft.participants.any { it.name.isBlank() }) {
            add(SetupIssue(SetupIssueCode.EMPTY_PARTICIPANT_NAME, "Enter a name for every player or team."))
        }

        val selectedCategoryIds = draft.activeCategoryIds()
        val knownCategoryIds = ApprovedCategories.all.map { it.id }.toSet()
        if (selectedCategoryIds.isEmpty()) {
            add(SetupIssue(
                SetupIssueCode.EMPTY_CATEGORY_SELECTION,
                if (draft.categoryMode == GameCategoryMode.CATEGORY_FOCUSED) "Select one category for all six trivia rounds."
                else "Select at least one trivia category."
            ))
        }
        if (!knownCategoryIds.containsAll(selectedCategoryIds)) {
            add(SetupIssue(SetupIssueCode.UNKNOWN_CATEGORY, "The selection contains an unknown category."))
        }

        val eligibleCount = content.triviaQuestions.count { it.categoryId in selectedCategoryIds }
        if (eligibleCount < GameFactory.TRIVIA_ROUND_COUNT) {
            add(
                SetupIssue(
                    SetupIssueCode.TOO_FEW_QUESTIONS,
                    "These categories have $eligibleCount eligible questions. Select categories with at least 6."
                )
            )
        }
    }
}

sealed interface SetupContentState {
    data object Loading : SetupContentState
    data class Available(val bank: ContentBank) : SetupContentState
    data class Unavailable(val message: String) : SetupContentState
}

sealed interface SessionCreationResult {
    data class Created(val session: GameSession) : SessionCreationResult
    data class Invalid(val issues: List<SetupIssue>) : SessionCreationResult
    data class ContentUnavailable(val message: String) : SessionCreationResult
    data class FactoryRejected(val message: String) : SessionCreationResult
}

object GameSetupSessionCreator {
    fun create(
        draft: GameSetupDraft,
        contentState: SetupContentState,
        random: Random = Random.Default,
        gameId: GameId = GameId("game-${UUID.randomUUID()}"),
        recentlyEncounteredQuestionIds: Set<com.charlsemilbarquin.frameandfact.domain.game.QuestionId> = emptySet(),
        validProfileIds: Set<String> = emptySet()
    ): SessionCreationResult {
        val content = when (contentState) {
            SetupContentState.Loading -> return SessionCreationResult.ContentUnavailable("Question content is still loading.")
            is SetupContentState.Unavailable -> return SessionCreationResult.ContentUnavailable(contentState.message)
            is SetupContentState.Available -> contentState.bank
        }
        val issues = GameSetupValidator.validate(draft, content)
        if (issues.isNotEmpty()) return SessionCreationResult.Invalid(issues)

        return try {
            SessionCreationResult.Created(
                GameFactory.create(
                    id = gameId,
                    participants = draft.participants.map {
                        val name = if (draft.mode == GameMode.SOLO && it.name.isBlank()) {
                            GameSetupDraft.DEFAULT_SOLO_PLAYER_NAME
                        } else it.name.trim()
                        val kind = if (draft.mode == GameMode.SOLO) ParticipantKind.PLAYER else draft.participantKind
                        Participant(
                            it.id, name, kind,
                            profileId = it.profileId?.takeIf { profileId -> kind == ParticipantKind.PLAYER && profileId in validProfileIds }
                        )
                    },
                    categories = ApprovedCategories.all,
                    selectedCategoryIds = draft.activeCategoryIds(),
                    availableQuestions = content.triviaQuestions,
                    availableCameraPrompts = content.cameraPrompts,
                    random = random,
                    mode = draft.mode,
                    recentlyEncounteredQuestionIds = recentlyEncounteredQuestionIds
                )
            )
        } catch (error: GameSetupException) {
            SessionCreationResult.FactoryRejected(
                "The game could not be prepared (${error.reason.name.lowercase().replace('_', ' ')})."
            )
        }
    }
}
