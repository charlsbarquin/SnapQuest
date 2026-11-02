package com.charlsemilbarquin.frameandfact.data.settings

enum class ThemePreference {
    SYSTEM_DEFAULT,
    LIGHT,
    DARK;

    fun resolveDarkTheme(systemIsDark: Boolean): Boolean = when (this) {
        SYSTEM_DEFAULT -> systemIsDark
        LIGHT -> false
        DARK -> true
    }
}

data class AppSettings(
    val theme: ThemePreference = ThemePreference.SYSTEM_DEFAULT,
    val tutorialCompleted: Boolean = false
) {
    companion object {
        val DEFAULT = AppSettings()
    }
}

sealed interface SettingsReadState {
    data object Loading : SettingsReadState
    data class Ready(val settings: AppSettings) : SettingsReadState
    data class Error(val message: String) : SettingsReadState
}

sealed interface ThemeWriteResult {
    data object Saved : ThemeWriteResult
    data class Failed(val message: String) : ThemeWriteResult
}

sealed interface TutorialWriteResult {
    data object Saved : TutorialWriteResult
    data class Failed(val message: String) : TutorialWriteResult
}
