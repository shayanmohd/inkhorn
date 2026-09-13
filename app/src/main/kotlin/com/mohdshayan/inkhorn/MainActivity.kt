package com.mohdshayan.inkhorn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohdshayan.inkhorn.data.prefs.Settings
import com.mohdshayan.inkhorn.di.ServiceLocator
import com.mohdshayan.inkhorn.ui.nav.AppNav
import com.mohdshayan.inkhorn.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by ServiceLocator.appPrefs.settings.collectAsStateWithLifecycle(initialValue = null)
            AppTheme(choice = (settings ?: Settings()).theme) {
                AppNav(settings)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ServiceLocator.repository.onForeground()
    }

    override fun onStop() {
        super.onStop()
        // A rotation stops and starts the activity; only a real trip to the background pauses the game.
        if (!isChangingConfigurations) ServiceLocator.repository.onBackground()
    }
}
