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

package com.jnd.ngdroid.ui.assistant

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import com.jnd.ngdroid.agent.HttpClients
import com.jnd.ngdroid.ui.theme.LocalDialogShape
import com.jnd.ngdroid.ui.util.LockOrientationWhileShown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** Horizontal thumbnail strip for bare image URLs; tap opens [ImageViewerDialog]. */
@Composable
fun ChatImageStrip(
    urls: List<String>,
    onOpen: (String) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        urls.forEach { url ->
            AsyncImage(
                model = url,
                contentDescription = "Chat image — tap to enlarge",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpen(url) }
            )
        }
    }
}

/**
 * Full-width in-place chat image lifted out of the markdown prose (inline
 * placeholders overlap surrounding text). Tap opens [ImageViewerDialog].
 */
@Composable
fun ChatImageCard(
    url: String,
    alt: String,
    onOpen: (String) -> Unit
) {
    AsyncImage(
        model = url,
        contentDescription = alt.ifBlank { "Chat image — tap to enlarge" },
        contentScale = ContentScale.FillWidth,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .clickable { onOpen(url) }
    )
}

private class ImageZoom {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
}

/** Pinch-zoom / pan / double-tap image with save, open and share actions. */
@Composable
fun ImageViewerDialog(
    url: String,
    onDismiss: () -> Unit
) {
    LockOrientationWhileShown()
    val context = LocalContext.current
    val zoom = remember(url) { ImageZoom() }
    val scope = rememberCoroutineScope()
    var sharing by remember(url) { mutableStateOf(false) }
    // API 26-28: DownloadManager into public Pictures needs the legacy
    // runtime grant; queue the tap and run it after the user grants.
    var pendingSave by remember(url) { mutableStateOf(false) }
    val storageLauncher = rememberLegacyStorageLauncher {
        if (pendingSave) {
            pendingSave = false
            saveChatImage(context, url)
        }
    }
    fun saveWithPermission() {
        if (hasLegacyStorageGrant(context)) {
            saveChatImage(context, url)
        } else {
            pendingSave = true
            storageLauncher.launch(
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = LocalDialogShape.current,
            color = Color.Black,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.85f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { saveWithPermission() }) {
                        Text("Save", color = Color.White)
                    }
                    TextButton(
                        onClick = {
                            if (!sharing) {
                                sharing = true
                                shareImageFile(context, scope, url) { sharing = false }
                            }
                        },
                        enabled = !sharing
                    ) {
                        Text(if (sharing) "Sharing…" else "Share", color = Color.White)
                    }
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(url) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (zoom.scale > 1.2f) {
                                        zoom.scale = 1f
                                        zoom.offset = Offset.Zero
                                    } else {
                                        zoom.scale = 2.5f
                                    }
                                }
                            )
                        }
                        .pointerInput(url) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                val next = (zoom.scale * gestureZoom).coerceIn(1f, 6f)
                                zoom.scale = next
                                zoom.offset = if (next <= 1f) Offset.Zero else zoom.offset + pan
                            }
                        }
                ) {
                    SubcomposeAsyncImage(
                        model = url,
                        contentDescription = "Full-screen chat image",
                        contentScale = if (zoom.scale > 1f) ContentScale.FillWidth else ContentScale.Fit,
                        loading = {
                            CircularProgressIndicator(color = Color.White)
                        },
                        error = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Couldn't load this image.",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(Modifier.height(8.dp))
                                TextButton(onClick = { openInBrowser(context, url) }) {
                                    Text("Open in browser instead")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White)
                            .graphicsLayer(
                                scaleX = zoom.scale,
                                scaleY = zoom.scale,
                                translationX = zoom.offset.x,
                                translationY = zoom.offset.y
                            )
                    )
                }
                Text(
                    text = hostOf(url),
                    color = Color(0xFFB0B0B0),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 14.dp)
                )
            }
        }
    }
}

private fun hostOf(url: String): String = runCatching {
    Uri.parse(url).host.orEmpty()
}.getOrDefault("")

private fun fileNameOf(url: String): String {
    val last = url.substringAfterLast('/').substringBefore('?').substringBefore('#').trim()
    if (last.isNotBlank() && '.' in last && last.length <= 80 &&
        last.all { it.isLetterOrDigit() || it in ".-_()" }
    ) {
        return last
    }
    val ext = url.substringBefore('?').substringAfterLast('.', "jpg").lowercase()
        .takeIf { it in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp") } ?: "jpg"
    return "ngdroid-image.$ext"
}

private fun saveChatImage(context: Context, url: String) {
    try {
        val name = fileNameOf(url)
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(name)
            .setDescription("NGDroid chat image")
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_PICTURES,
                "NGDroid/$name"
            )
            .addRequestHeader(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
            )
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        Toast.makeText(context, "Downloading to Pictures/NGDroid…", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun openInBrowser(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        Toast.makeText(context, "Can't open link: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

/**
 * Share the actual image bytes (not just the link): download with a browser
 * UA (Wikimedia etc. 403 bot UAs) into `cache/shared_images/` and share via
 * FileProvider (`shared_images` cache-path). Falls back to a link share when
 * the download fails, so Share never dead-ends.
 */
private fun shareImageFile(
    context: Context,
    scope: CoroutineScope,
    url: String,
    onDone: () -> Unit
) {
    scope.launch(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", HttpClients.BROWSER_USER_AGENT)
                .get()
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                val bytes = resp.body?.bytes() ?: throw IllegalStateException("empty image")
                val contentType = resp.header("Content-Type")?.substringBefore(';')?.trim()
                val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
                val file = File(dir, fileNameOf(url))
                file.writeBytes(bytes)
                val mime = mimeTypeOf(contentType, file.name)
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                withContext(Dispatchers.Main) {
                    try {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = mime
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_TEXT, url)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(share, "Share image"))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        onDone()
                    }
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                try {
                    val fallback = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, url)
                    }
                    context.startActivity(Intent.createChooser(fallback, "Share image link"))
                    Toast.makeText(context, "Image download failed — shared link instead", Toast.LENGTH_SHORT).show()
                } catch (inner: Exception) {
                    Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    onDone()
                }
            }
        }
    }
}

private fun mimeTypeOf(contentType: String?, fileName: String): String {
    if (!contentType.isNullOrBlank() && '/' in contentType) return contentType
    return when (fileName.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        else -> "image/*"
    }
}
