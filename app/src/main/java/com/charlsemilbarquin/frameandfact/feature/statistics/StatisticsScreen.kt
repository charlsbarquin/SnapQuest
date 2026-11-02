package com.charlsemilbarquin.frameandfact.feature.statistics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.charlsemilbarquin.frameandfact.data.history.AchievementProgress
import com.charlsemilbarquin.frameandfact.data.history.CompletedGameHistoryRecord
import com.charlsemilbarquin.frameandfact.data.history.FullGameStatisticsCalculator
import com.charlsemilbarquin.frameandfact.data.history.LocalPlayerProfile
import com.charlsemilbarquin.frameandfact.data.history.LocalStatisticsCalculator
import com.charlsemilbarquin.frameandfact.data.history.UnlockedAchievement
import com.charlsemilbarquin.frameandfact.feature.history.HistoryUiState
import com.charlsemilbarquin.frameandfact.ui.components.SnapQuestEmptyState
import com.charlsemilbarquin.frameandfact.ui.components.SnapQuestSectionHeader
import com.charlsemilbarquin.frameandfact.ui.theme.FrameFactSpacing
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun StatisticsScreen(
    historyState: HistoryUiState,
    profiles: List<LocalPlayerProfile>,
    achievements: Map<String, List<UnlockedAchievement>>,
    onRetry: () -> Unit,
    onManageProfiles: () -> Unit,
    onStartPlaying: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedPeriod by rememberSaveable { mutableIntStateOf(0) }
    val games = (historyState as? HistoryUiState.Populated)?.games.orEmpty()
    val rows = remember(games, profiles, selectedPeriod) {
        leaderboardRows(games, profiles, selectedPeriod == 1)
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(FrameFactSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(FrameFactSpacing.contentGap)
    ) {
        Text("Scores", style = MaterialTheme.typography.headlineMedium)
        SnapQuestSectionHeader("Leaderboard")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = selectedPeriod == 0, onClick = { selectedPeriod = 0 }, label = { Text("Overall") })
            FilterChip(selected = selectedPeriod == 1, onClick = { selectedPeriod = 1 }, label = { Text("This Week") })
        }

        when (historyState) {
            HistoryUiState.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Text("Loading scores…", style = MaterialTheme.typography.bodyMedium)
            }
            HistoryUiState.Empty -> SnapQuestEmptyState(
                title = if (selectedPeriod == 0) "No Scores Yet" else "No Scores This Week",
                message = "Play a game to see your high scores here.",
                supportingContent = {
                    Button(onClick = onStartPlaying) { Text("Start Playing") }
                }
            )
            is HistoryUiState.Error -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(historyState.message, color = MaterialTheme.colorScheme.error)
                Button(onClick = onRetry) { Text("Retry") }
            }
            is HistoryUiState.Populated -> if (rows.isEmpty()) {
                SnapQuestEmptyState(
                    title = "No Scores This Week",
                    message = "Completed-game scores will appear here.",
                    supportingContent = {
                        if (selectedPeriod == 1) {
                            OutlinedButton(onClick = { selectedPeriod = 0 }) { Text("View Overall") }
                        }
                    }
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEach { LeaderboardRowCard(it) }
                }
                Text(
                    "Linked profiles combine their saved results. Unlinked participants remain separate History results.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SnapQuestSectionHeader("Personal bests and achievements")
        if (profiles.isEmpty()) {
            Text(
                "Link a saved profile before a game to track personal results.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val populatedGames = games
            profiles.forEach { profile ->
                ProfileStatisticsCard(
                    profile = profile,
                    games = populatedGames,
                    unlockedAchievements = achievements[profile.profileId].orEmpty()
                )
            }
        }
        OutlinedButton(onClick = onManageProfiles, modifier = Modifier.fillMaxWidth()) { Text("Manage profiles") }

        if (historyState is HistoryUiState.Populated) {
            SnapQuestSectionHeader("Game statistics")
            OverallStatisticsCard(games)
        }
    }
}

private data class LeaderboardRow(
    val rank: Int,
    val name: String,
    val detail: String,
    val points: Int
)

private data class ResultSource(
    val game: CompletedGameHistoryRecord,
    val displayName: String,
    val profileId: String?,
    val participantId: String,
    val points: Int
)

private fun leaderboardRows(
    games: List<CompletedGameHistoryRecord>,
    profiles: List<LocalPlayerProfile>,
    thisWeekOnly: Boolean
): List<LeaderboardRow> {
    val zone = ZoneId.systemDefault()
    val weekStart = LocalDate.now(zone)
        .with(DayOfWeek.MONDAY)
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()
    val eligibleGames = games.filter { !thisWeekOnly || it.completedAtEpochMillis >= weekStart }
    val existingProfileIds = profiles.mapTo(HashSet()) { it.profileId }
    val sources = eligibleGames.flatMap { game ->
        game.participants.map { participant ->
            ResultSource(
                game = game,
                displayName = participant.displayName,
                profileId = participant.profileId?.takeIf(existingProfileIds::contains),
                participantId = participant.participantId,
                points = participant.score
            )
        }
    }
    val grouped = sources.groupBy { source ->
        source.profileId?.let { "profile:$it" } ?: "result:${source.game.gameId}:${source.participantId}"
    }
    val profileName = profiles.associate { it.profileId to it.displayName }
    val sorted = grouped.map { (key, entries) ->
        val first = entries.first()
        val linkedProfileId = first.profileId
        val name = linkedProfileId?.let(profileName::get) ?: first.displayName
        val points = if (linkedProfileId == null) first.points else entries.sumOf { it.points }
        val detail = if (linkedProfileId != null) {
            "Linked profile · ${entries.size} ${if (entries.size == 1) "game" else "games"}"
        } else {
            val mode = if (first.game.isSoloGame) "Solo" else "Multiplayer"
            val date = Instant.ofEpochMilli(first.game.completedAtEpochMillis).atZone(zone).toLocalDate()
                .format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
            "$mode · $date"
        }
        key to (name to (detail to points))
    }.sortedWith(compareByDescending<Pair<String, Pair<String, Pair<String, Int>>>> { it.second.second.second }
        .thenBy { it.second.first.lowercase(Locale.ROOT) })

    var previousPoints: Int? = null
    var currentRank = 0
    return sorted.mapIndexed { index, (key, values) ->
        val points = values.second.second
        if (points != previousPoints) currentRank = index + 1
        previousPoints = points
        LeaderboardRow(currentRank, values.first, values.second.first, points)
    }
}

@Composable
private fun LeaderboardRowCard(row: LeaderboardRow) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(row.rank.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(row.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(row.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Text(
                text = "${row.points}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { contentDescription = "${row.points} points" }
            )
        }
    }
}

@Composable
private fun OverallStatisticsCard(games: List<CompletedGameHistoryRecord>) {
    val stats = LocalStatisticsCalculator.calculate(games)
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Overall", style = MaterialTheme.typography.titleSmall)
            Text("Completed games: ${stats.completedGames} · rounds: ${stats.totalCompletedRounds}")
            Text("Total points: ${stats.totalPoints} · average game: ${stats.averageGameScore?.oneDecimal() ?: "—"} / 80")
            Text("Highest: ${stats.highestGameScore?.let { "$it / 80" } ?: "—"} · lowest: ${stats.lowestGameScore?.let { "$it / 80" } ?: "—"}")
            Text("Solo: ${stats.soloGames} · Multiplayer: ${stats.multiplayerGames}")
            Text("Multiplayer results: ${stats.multiplayerWins} wins · ${stats.multiplayerTies} ties · ${stats.multiplayerLosses} losses")
            Text("Successful rounds: ${stats.successfulRounds} / ${stats.totalCompletedRounds} (${stats.overallSuccessRate?.percentText() ?: "—"})")
            Text("Trivia accuracy: ${stats.triviaAccuracy.correct} / ${stats.triviaAccuracy.answered} (${stats.triviaAccuracy.percent?.percentText() ?: "—"})")
            Text("Camera success: ${stats.cameraAccuracy.correct} / ${stats.cameraAccuracy.answered} (${stats.cameraAccuracy.percent?.percentText() ?: "—"})")
            if (stats.categoryAccuracy.isNotEmpty()) {
                Text("Trivia accuracy by category", style = MaterialTheme.typography.titleSmall)
                stats.categoryAccuracy.toSortedMap().forEach { (categoryId, accuracy) ->
                    Text("${categoryId.toDisplayName()}: ${accuracy.correct} / ${accuracy.answered} (${accuracy.percent?.percentText() ?: "—"})")
                }
            }
            Text(
                "Standard versus Category-Focused totals are not shown because History does not save an explicit category-mode field.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProfileStatisticsCard(
    profile: LocalPlayerProfile,
    games: List<CompletedGameHistoryRecord>,
    unlockedAchievements: List<UnlockedAchievement>
) {
    val stats = FullGameStatisticsCalculator.forProfile(profile.profileId, games)
    val bests = LocalStatisticsCalculator.personalBests(profile.profileId, games)
    val progress = LocalStatisticsCalculator.achievementProgress(profile.profileId, games, unlockedAchievements)
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(profile.displayName, style = MaterialTheme.typography.titleSmall)
            Text("Linked games: ${stats.completedGames} · total points: ${stats.totalPoints}")
            Text("Best overall: ${bests.highestScore?.let { "$it / 80" } ?: "No linked games yet"}")
            Text("Best Solo: ${bests.highestSoloScore?.let { "$it / 80" } ?: "—"} · Multiplayer: ${bests.highestMultiplayerScore?.let { "$it / 80" } ?: "—"}")
            Text("Most successful rounds: ${bests.mostSuccessfulRounds?.let { "$it / 8" } ?: "—"}")
            Text("Best trivia accuracy: ${bests.bestTriviaAccuracy?.let { "${it.correct} / ${it.answered} (${it.percent?.percentText() ?: "—"})" } ?: "—"}")
            if (bests.bestCategoryAccuracy.isNotEmpty()) {
                Text("Best category accuracy", style = MaterialTheme.typography.titleSmall)
                bests.bestCategoryAccuracy.toSortedMap().forEach { (categoryId, best) ->
                    Text("${categoryId.toDisplayName()}: ${best.correct} / ${best.answered} (${best.accuracy?.percentText() ?: "—"})")
                }
            }
            Text("Achievements", style = MaterialTheme.typography.titleSmall)
            progress.forEach { AchievementRow(it) }
        }
    }
}

@Composable
private fun AchievementRow(progress: AchievementProgress) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${progress.achievement.title} · ${if (progress.isUnlocked) "Unlocked" else "Locked"}",
                style = MaterialTheme.typography.titleSmall,
                color = if (progress.isUnlocked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
            )
            Text("${progress.displayProgress} / ${progress.target}", style = MaterialTheme.typography.bodySmall)
        }
        if (!progress.isUnlocked) Text(progress.achievement.description, style = MaterialTheme.typography.bodySmall)
        LinearProgressIndicator(
            progress = { progress.displayProgress.toFloat() / progress.target.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth(),
            color = if (progress.isUnlocked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
        )
    }
}

private fun Double.oneDecimal(): String = String.format(Locale.ROOT, "%.1f", this)
private fun Double.percentText(): String = String.format(Locale.ROOT, "%.1f%%", this)
private fun String.toDisplayName(): String = split('_').joinToString(" ") { word ->
    word.replaceFirstChar { char -> char.uppercase() }
}
