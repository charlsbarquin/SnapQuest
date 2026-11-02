package com.charlsemilbarquin.frameandfact.feature.setup

import com.charlsemilbarquin.frameandfact.data.content.BundledContentLoader
import com.charlsemilbarquin.frameandfact.data.content.ContentAssetMissingException
import com.charlsemilbarquin.frameandfact.data.content.ContentBank
import com.charlsemilbarquin.frameandfact.domain.game.ApprovedCategories
import com.charlsemilbarquin.frameandfact.domain.game.CategoryId
import com.charlsemilbarquin.frameandfact.domain.game.Difficulty
import com.charlsemilbarquin.frameandfact.domain.game.GameEngine
import com.charlsemilbarquin.frameandfact.domain.game.GameEvent
import com.charlsemilbarquin.frameandfact.domain.game.GameId
import com.charlsemilbarquin.frameandfact.domain.game.GameAction
import com.charlsemilbarquin.frameandfact.domain.game.GameActionResult
import com.charlsemilbarquin.frameandfact.domain.game.GameMode
import com.charlsemilbarquin.frameandfact.domain.game.ParticipantKind
import com.charlsemilbarquin.frameandfact.domain.game.RoundType
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSetupTest {
    private fun readAsset(path: String): String {
        val file = Path.of(System.getProperty("user.dir"), "src", "main", "assets", path).toFile()
        if (!file.isFile) throw ContentAssetMissingException(path)
        return Files.readString(file.toPath())
    }

    private val bank: ContentBank by lazy { BundledContentLoader(::readAsset).load() }
    private fun readyDraft(count: Int = 2) = GameSetupDraft.create().let { initial ->
        var draft = initial
        repeat(count - 2) { draft = draft.addParticipant() }
        draft.copy(participants = draft.participants.mapIndexed { index, row -> row.copy(name = "Player ${index + 1}") })
    }

    @Test fun twoAndSixParticipantsAreAccepted() {
        assertTrue(GameSetupValidator.validate(readyDraft(2), bank).isEmpty())
        assertTrue(GameSetupValidator.validate(readyDraft(6), bank).isEmpty())
    }

    @Test fun soloSetupDefaultsNameAndAcceptsBlankAsDefaultAndCustomNames() {
        val initial = GameSetupDraft.create(GameMode.SOLO)
        assertEquals(1, initial.participants.size)
        assertEquals("Player", initial.participants.single().name)
        assertTrue(GameSetupValidator.validate(initial, bank).isEmpty())

        val blank = initial.updateParticipantName(initial.participants.single().id, " \t ")
        val createdBlank = GameSetupSessionCreator.create(
            blank, SetupContentState.Available(bank), random = Random(3), gameId = GameId("solo-default")
        ) as SessionCreationResult.Created
        assertEquals("Player", createdBlank.session.participants.single().displayName)
        assertEquals(GameMode.SOLO, createdBlank.session.mode)

        val custom = initial.updateParticipantName(initial.participants.single().id, "  Ada  ")
        val createdCustom = GameSetupSessionCreator.create(
            custom, SetupContentState.Available(bank), random = Random(4), gameId = GameId("solo-custom")
        ) as SessionCreationResult.Created
        assertEquals("Ada", createdCustom.session.participants.single().displayName)
    }

    @Test fun profileLinksRequireAnExplicitExistingProfileIdAndArePlayerOnly() {
        val solo = GameSetupDraft.create(GameMode.SOLO)
        val linkedDraft = solo.copy(participants = solo.participants.map { it.copy(name = "Alex", profileId = "profile-1234") })
        val linked = GameSetupSessionCreator.create(
            linkedDraft, SetupContentState.Available(bank), random = Random(31),
            validProfileIds = setOf("profile-1234")
        ) as SessionCreationResult.Created
        assertEquals("profile-1234", linked.session.participants.single().profileId)

        val unlinked = GameSetupSessionCreator.create(
            linkedDraft, SetupContentState.Available(bank), random = Random(32), validProfileIds = emptySet()
        ) as SessionCreationResult.Created
        assertEquals(null, unlinked.session.participants.single().profileId)

        val teams = readyDraft().copy(
            participantKind = ParticipantKind.TEAM,
            participants = readyDraft().participants.map { it.copy(profileId = "profile-1234") }
        )
        val teamSession = GameSetupSessionCreator.create(
            teams, SetupContentState.Available(bank), random = Random(33),
            validProfileIds = setOf("profile-1234")
        ) as SessionCreationResult.Created
        assertTrue(teamSession.session.participants.all { it.profileId == null })
    }

    @Test fun soloCategorySelectionAndMixedCategoriesUseExistingQuestionRules() {
        val draft = GameSetupDraft.create(GameMode.SOLO)
        val science = draft.updateSingleCategory(CategoryId("science"))
        val mixed = draft.copy(categorySelection = CategorySelection.Mixed)

        assertEquals(18, eligibleCount(science))
        assertEquals(144, eligibleCount(mixed))
        assertEquals(6, (GameSetupSessionCreator.create(
            science, SetupContentState.Available(bank), random = Random(5)
        ) as SessionCreationResult.Created).session.selectedQuestions.size)
        assertEquals(6, (GameSetupSessionCreator.create(
            mixed, SetupContentState.Available(bank), random = Random(6)
        ) as SessionCreationResult.Created).session.selectedQuestions.size)
    }

    @Test fun categoryFocusedModeUsesExactlySixUniqueQuestionsFromOneCategory() {
        val draft = GameSetupDraft.quickPlay().copy(
            categoryMode = GameCategoryMode.CATEGORY_FOCUSED,
            focusedCategoryId = CategoryId("science")
        )
        assertTrue(GameSetupValidator.validate(draft, bank).isEmpty())
        val solo = GameSetupSessionCreator.create(
            draft, SetupContentState.Available(bank), random = Random(501), gameId = GameId("focused-solo")
        ) as SessionCreationResult.Created
        assertEquals(GameMode.SOLO, solo.session.mode)
        assertEquals(setOf(CategoryId("science")), solo.session.selectedCategoryIds)
        assertEquals(6, solo.session.selectedQuestions.size)
        assertEquals(6, solo.session.selectedQuestions.map { it.id }.toSet().size)
        assertTrue(solo.session.selectedQuestions.all { it.categoryId == CategoryId("science") })
        assertEquals(listOf(4, 8), solo.session.rounds.filter { it.type == RoundType.FIND_AND_FRAME }.map { it.id.number })

        val multiplayerDraft = readyDraft().copy(
            categoryMode = GameCategoryMode.CATEGORY_FOCUSED,
            focusedCategoryId = CategoryId("science")
        )
        val multiplayer = GameSetupSessionCreator.create(
            multiplayerDraft, SetupContentState.Available(bank), random = Random(502), gameId = GameId("focused-multi")
        ) as SessionCreationResult.Created
        assertEquals(multiplayerDraft.participants.map { it.id }.toSet(), multiplayer.session.rounds
            .filter { it.type == RoundType.TRIVIA }.map { it.participantId }.toSet())
        assertTrue(multiplayer.session.selectedQuestions.all { it.categoryId == CategoryId("science") })
    }

    @Test fun categoryFocusedModeRejectsFewerThanSixQuestionsAndStandardModeRestoresAllCategories() {
        val unselected = GameSetupDraft.quickPlay().copy(categoryMode = GameCategoryMode.CATEGORY_FOCUSED)
        assertTrue(GameSetupValidator.validate(unselected, bank).any { it.code == SetupIssueCode.EMPTY_CATEGORY_SELECTION })
        val draft = GameSetupDraft.quickPlay().copy(
            categoryMode = GameCategoryMode.CATEGORY_FOCUSED,
            focusedCategoryId = CategoryId("science")
        )
        val tooFewBank = bank.copy(triviaQuestions = bank.triviaQuestions.filter { it.categoryId == draft.focusedCategoryId }.take(5))
        assertTrue(GameSetupValidator.validate(draft, tooFewBank).any { it.code == SetupIssueCode.TOO_FEW_QUESTIONS })

        val standard = draft.copy(categoryMode = GameCategoryMode.STANDARD)
        assertEquals(ApprovedCategories.all.map { it.id }.toSet(), standard.activeCategoryIds())
        assertTrue(GameSetupValidator.validate(standard, bank).isEmpty())
    }

    @Test fun quickPlayUsesFreshSoloAllCategoryStandardSessions() {
        val draft = GameSetupDraft.quickPlay()
        assertEquals(GameMode.SOLO, draft.mode)
        assertEquals(GameCategoryMode.STANDARD, draft.categoryMode)
        assertEquals(ApprovedCategories.all.map { it.id }.toSet(), draft.activeCategoryIds())
        val first = GameSetupSessionCreator.create(draft, SetupContentState.Available(bank), random = Random(600))
            as SessionCreationResult.Created
        val second = GameSetupSessionCreator.create(draft, SetupContentState.Available(bank), random = Random(601))
            as SessionCreationResult.Created
        assertEquals(8, first.session.rounds.size)
        assertEquals(8, second.session.rounds.size)
        assertTrue(first.session.id != second.session.id)
        assertTrue(first.session.selectedQuestions.map { it.id }.toSet() != second.session.selectedQuestions.map { it.id }.toSet())
        assertEquals(2, first.session.rounds.count { it.type == RoundType.FIND_AND_FRAME })
        assertEquals(2, second.session.rounds.count { it.type == RoundType.FIND_AND_FRAME })
    }

    @Test fun fewerThanTwoAndMoreThanSixParticipantsAreRejected() {
        assertTrue(GameSetupValidator.validate(readyDraft().copy(participants = emptyList()), bank)
            .any { it.code == SetupIssueCode.PARTICIPANT_COUNT })
        assertTrue(GameSetupValidator.validate(readyDraft().copy(participants = List(7) { index ->
            readyDraft().participants.first().copy(name = "Player $index")
        }), bank).any { it.code == SetupIssueCode.PARTICIPANT_COUNT })
    }

    @Test fun blankNamesAreRejectedButDuplicateNamesAreAllowed() {
        val draft = readyDraft()
        assertTrue(GameSetupValidator.validate(draft.updateParticipantName(draft.participants.first().id, "  "), bank)
            .any { it.code == SetupIssueCode.EMPTY_PARTICIPANT_NAME })
        val duplicateNames = draft.copy(participants = draft.participants.map { it.copy(name = "Same name") })
        assertFalse(GameSetupValidator.validate(duplicateNames, bank).any { it.code == SetupIssueCode.EMPTY_PARTICIPANT_NAME })
    }

    @Test fun singleSubsetAndMixedSelectionsCalculateEligibleQuestionCounts() {
        val draft = readyDraft()
        val science = CategoryId("science")
        val history = CategoryId("history")
        val single = draft.updateSingleCategory(science)
        val subset = draft.updateSelectedCategories(setOf(science, history))
        val mixed = draft.copy(categorySelection = CategorySelection.Mixed)

        assertEquals(18, eligibleCount(single))
        assertEquals(36, eligibleCount(subset))
        assertEquals(144, eligibleCount(mixed))
        assertEquals(setOf(science), single.categorySelection.categoryIds())
        assertEquals(setOf(science, history), subset.categorySelection.categoryIds())
        assertEquals(ApprovedCategories.all.map { it.id }.toSet(), mixed.categorySelection.categoryIds())
    }

    @Test fun fewerThanSixQuestionsBlocksCreationAndSixAllowCreationAcrossDifficulties() {
        val draft = readyDraft().updateSingleCategory(CategoryId("science"))
        val scienceQuestions = bank.triviaQuestions.filter { it.categoryId == CategoryId("science") }
        val tooFewBank = bank.copy(triviaQuestions = scienceQuestions.take(5))
        assertTrue(GameSetupSessionCreator.create(draft, SetupContentState.Available(tooFewBank)) is SessionCreationResult.Invalid)

        val sixAcrossLevels = Difficulty.entries.flatMap { difficulty ->
            scienceQuestions.filter { it.difficulty == difficulty }.take(2)
        }
        val eligibleBank = bank.copy(triviaQuestions = sixAcrossLevels)
        val created = GameSetupSessionCreator.create(
            draft, SetupContentState.Available(eligibleBank), random = Random(4), gameId = GameId("setup-test")
        ) as SessionCreationResult.Created
        assertEquals(Difficulty.entries.toSet(), created.session.selectedQuestions.map { it.difficulty }.toSet())
        assertEquals(6, created.session.selectedQuestions.map { it.id }.toSet().size)
        assertEquals(8, created.session.rounds.size)
        assertEquals(RoundType.TRIVIA, created.session.currentRound?.type)
        assertEquals(2, created.session.rounds.count { it.type == RoundType.FIND_AND_FRAME })
    }

    @Test fun loadingOrUnavailableContentNeverCreatesFallbackSession() {
        val draft = readyDraft()
        assertTrue(GameSetupSessionCreator.create(draft, SetupContentState.Loading) is SessionCreationResult.ContentUnavailable)
        assertTrue(GameSetupSessionCreator.create(
            draft, SetupContentState.Unavailable("Bundled content failed validation")
        ) is SessionCreationResult.ContentUnavailable)
    }

    @Test fun createdSessionUsesDomainEngineForGameProgression() {
        val draft = readyDraft().copy(participantKind = ParticipantKind.TEAM)
        val created = GameSetupSessionCreator.create(
            draft, SetupContentState.Available(bank), random = Random(9), gameId = GameId("engine-setup-test")
        ) as SessionCreationResult.Created
        val session = created.session
        val firstTriviaParticipant = session.currentRound!!.participantId
        val question = session.selectedQuestions.single { it.id == session.currentRound!!.questionId }
        val result = GameEngine.apply(
            session,
            GameAction.RoundEvent(GameEvent.TriviaAnswer(session.currentRound!!.id, question.correctChoiceId))
        ) as GameActionResult.Applied

        assertEquals(10, result.session.scoreFor(firstTriviaParticipant))
        assertEquals(1, result.session.outcomes.size)
        assertTrue(result.session.participants.all { it.kind == ParticipantKind.TEAM })
    }

    private fun eligibleCount(draft: GameSetupDraft) = bank.triviaQuestions.count {
        it.categoryId in draft.categorySelection.categoryIds()
    }
}
