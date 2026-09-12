package com.jnd.ngdroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.jnd.ngdroid.ui.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Oreo (26-27) has no dynamic contrast: keep decor fitting off via
        // enableEdgeToEdge but pin legacy bar colors in Theme.NGDroid.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            // Single MaterialTheme lives in MainScreen via NGDroidTheme (accent-driven).
            MainScreen()
        }
    }
}
