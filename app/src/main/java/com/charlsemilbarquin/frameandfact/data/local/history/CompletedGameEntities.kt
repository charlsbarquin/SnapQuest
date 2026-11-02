package com.charlsemilbarquin.frameandfact.data.local.history

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.charlsemilbarquin.frameandfact.data.history.CompletedGameHistoryRecord
import com.charlsemilbarquin.frameandfact.data.history.HistoryParticipantResult
import com.charlsemilbarquin.frameandfact.data.history.HistoryRoundResult
import com.charlsemilbarquin.frameandfact.domain.game.ParticipantKind
import com.charlsemilbarquin.frameandfact.domain.game.RoundOutcome
import com.charlsemilbarquin.frameandfact.domain.game.RoundType

@Entity(tableName = "completed_games")
data class CompletedGameEntity(
    @PrimaryKey val gameId: String,
    val completedAtEpochMillis: Long,
    val rulesVersion: Int,
    val selectedCategories: String,
    val difficultyLevels: String
)

@Entity(
    tableName = "completed_game_participants",
    primaryKeys = ["gameId", "participantId"],
    foreignKeys = [
        ForeignKey(
            entity = CompletedGameEntity::class,
            parentColumns = ["gameId"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PlayerProfileEntity::class,
            parentColumns = ["profileId"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["gameId", "participantOrder"], unique = true),
        Index(value = ["profileId"])
    ]
)
data class CompletedGameParticipantEntity(
    val gameId: String,
    val participantId: String,
    val participantOrder: Int,
    val displayName: String,
    val participantKind: String,
    val score: Int,
    val correctTriviaRounds: Int,
    val acceptedCameraRounds: Int,
    val rank: Int,
    val profileId: String? = null
)

@Entity(
    tableName = "completed_game_rounds",
    primaryKeys = ["gameId", "roundNumber"],
    foreignKeys = [
        ForeignKey(
            entity = CompletedGameEntity::class,
            parentColumns = ["gameId"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CompletedGameParticipantEntity::class,
            parentColumns = ["gameId", "participantId"],
            childColumns = ["gameId", "participantId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CompletedGameParticipantEntity::class,
            parentColumns = ["gameId", "participantId"],
            childColumns = ["gameId", "judgeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("gameId", "participantId"), Index("gameId", "judgeId")]
)
data class CompletedGameRoundEntity(
    val gameId: String,
    val roundNumber: Int,
    val roundType: String,
    val participantId: String,
    val judgeId: String?,
    val outcome: String,
    val questionId: String? = null,
    val questionText: String? = null,
    val selectedAnswer: String? = null,
    val correctAnswer: String? = null,
    val explanation: String? = null,
    val categoryId: String? = null,
    val difficulty: String? = null
)

data class CompletedGameWithDetails(
    @Embedded val game: CompletedGameEntity,
    @Relation(parentColumn = "gameId", entityColumn = "gameId")
    val participants: List<CompletedGameParticipantEntity>,
    @Relation(parentColumn = "gameId", entityColumn = "gameId")
    val rounds: List<CompletedGameRoundEntity>
)

@Entity(tableName = "player_profiles")
data class PlayerProfileEntity(
    @PrimaryKey val profileId: String,
    val displayName: String,
    val createdAtEpochMillis: Long
)

@Entity(
    tableName = "profile_achievements",
    primaryKeys = ["profileId", "achievementId"],
    foreignKeys = [ForeignKey(
        entity = PlayerProfileEntity::class,
        parentColumns = ["profileId"],
        childColumns = ["profileId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("profileId")]
)
data class ProfileAchievementEntity(
    val profileId: String,
    val achievementId: String,
    val unlockedAtEpochMillis: Long
)

fun CompletedGameHistoryRecord.toEntities(): Triple<CompletedGameEntity, List<CompletedGameParticipantEntity>, List<CompletedGameRoundEntity>> =
    Triple(
        CompletedGameEntity(
            gameId = gameId,
            completedAtEpochMillis = completedAtEpochMillis,
            rulesVersion = rulesVersion,
            selectedCategories = selectedCategories.joinToString("|"),
            difficultyLevels = difficultyLevels.joinToString("|")
        ),
        participants.mapIndexed { index, participant ->
            CompletedGameParticipantEntity(
                gameId = gameId,
                participantId = participant.participantId,
                participantOrder = index,
                displayName = participant.displayName,
                participantKind = participant.kind.name,
                score = participant.score,
                correctTriviaRounds = participant.correctTriviaRounds,
                acceptedCameraRounds = participant.acceptedCameraRounds,
                rank = participant.rank,
                profileId = participant.profileId
            )
        },
        rounds.map { round ->
            CompletedGameRoundEntity(
                gameId = gameId,
                roundNumber = round.number,
                roundType = round.type.name,
                participantId = round.participantId,
                judgeId = round.judgeId,
                outcome = round.outcome.name,
                questionId = round.questionId,
                questionText = round.questionText,
                selectedAnswer = round.selectedAnswer,
                correctAnswer = round.correctAnswer,
                explanation = round.explanation,
                categoryId = round.categoryId,
                difficulty = round.difficulty
            )
        }
    )

fun CompletedGameWithDetails.toRecord(): CompletedGameHistoryRecord = CompletedGameHistoryRecord(
    gameId = game.gameId,
    completedAtEpochMillis = game.completedAtEpochMillis,
    rulesVersion = game.rulesVersion,
    selectedCategories = game.selectedCategories.split('|').filter(String::isNotEmpty),
    difficultyLevels = game.difficultyLevels.split('|').filter(String::isNotEmpty),
    participants = participants.sortedBy { it.participantOrder }.map {
        HistoryParticipantResult(
            participantId = it.participantId,
            displayName = it.displayName,
            kind = ParticipantKind.valueOf(it.participantKind),
            score = it.score,
            correctTriviaRounds = it.correctTriviaRounds,
            acceptedCameraRounds = it.acceptedCameraRounds,
            rank = it.rank,
            profileId = it.profileId
        )
    },
    rounds = rounds.sortedBy { it.roundNumber }.map {
        HistoryRoundResult(
            number = it.roundNumber,
            type = RoundType.valueOf(it.roundType),
            participantId = it.participantId,
            judgeId = it.judgeId,
            outcome = RoundOutcome.valueOf(it.outcome),
            questionId = it.questionId,
            questionText = it.questionText,
            selectedAnswer = it.selectedAnswer,
            correctAnswer = it.correctAnswer,
            explanation = it.explanation,
            categoryId = it.categoryId,
            difficulty = it.difficulty
        )
    }
)
