package com.jnd.ngdroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import coil3.SingletonImageLoader
import com.jnd.ngdroid.ui.MainScreen
import com.jnd.ngdroid.ui.assistant.newChatImageLoader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Chat image transport: OkHttp fetcher + browser UA (Wikimedia etc.
        // 403 bot UAs; the markdown coil3 module brings no network fetcher).
        SingletonImageLoader.setSafe { ctx -> newChatImageLoader(ctx) }
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
