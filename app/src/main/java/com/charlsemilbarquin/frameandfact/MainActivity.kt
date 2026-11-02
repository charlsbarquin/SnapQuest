package com.charlsemilbarquin.frameandfact

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.charlsemilbarquin.frameandfact.data.settings.AppSettings
import com.charlsemilbarquin.frameandfact.data.settings.AppSettingsRepository
import com.charlsemilbarquin.frameandfact.data.settings.SettingsReadState
import com.charlsemilbarquin.frameandfact.data.settings.createAppSettingsRepository
import com.charlsemilbarquin.frameandfact.ui.navigation.AppNavigation
import com.charlsemilbarquin.frameandfact.ui.navigation.AppNavigationViewModel
import com.charlsemilbarquin.frameandfact.ui.theme.FrameFactTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val navigationViewModel = ViewModelProvider(this)[AppNavigationViewModel::class.java]
        setContent {
            val appContext = LocalContext.current.applicationContext
            val view = LocalView.current
            val settingsRepository: AppSettingsRepository = remember(appContext) {
                createAppSettingsRepository(appContext)
            }
            val settingsState by settingsRepository.settings.collectAsState(
                initial = SettingsReadState.Loading
            )
            val settings = (settingsState as? SettingsReadState.Ready)?.settings ?: AppSettings.DEFAULT
            val systemIsDark = isSystemInDarkTheme()
            val darkTheme = settings.theme.resolveDarkTheme(systemIsDark)
            if (!view.isInEditMode) {
                SideEffect {
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                }
            }
            FrameFactTheme(darkTheme = darkTheme) {
                AppNavigation(settingsRepository, settingsState, navigationViewModel)
            }
        }
    }
}

