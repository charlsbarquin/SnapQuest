package com.charlsemilbarquin.frameandfact.feature.history

import com.charlsemilbarquin.frameandfact.data.history.CompletedGameHistoryRecord

sealed interface CompletionSaveUiState {
    data object NotStarted : CompletionSaveUiState
    data object Saving : CompletionSaveUiState
    data object Saved : CompletionSaveUiState
    data class Failed(val message: String) : CompletionSaveUiState
}

sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data object Empty : HistoryUiState
    data class Populated(val games: List<CompletedGameHistoryRecord>) : HistoryUiState
    data class Error(val message: String) : HistoryUiState
}

fun historyUiState(games: List<CompletedGameHistoryRecord>): HistoryUiState =
    if (games.isEmpty()) HistoryUiState.Empty else HistoryUiState.Populated(games)
