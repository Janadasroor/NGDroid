/*
 * Copyright 2026 Janada Sroor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
