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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import com.jnd.ngdroid.ui.theme.LocalDialogShape
import com.jnd.ngdroid.ui.util.LockOrientationWhileShown

/** Horizontal thumbnail strip under a chat message; tap opens [ImageViewerDialog]. */
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

private class ImageZoom {
    var scale by mutableStateOf(1f)
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
                    TextButton(onClick = { openInBrowser(context, url) }) {
                        Text("Open", color = Color.White)
                    }
                    TextButton(onClick = { shareImageLink(context, url) }) {
                        Text("Share", color = Color.White)
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

private fun shareImageLink(context: Context, url: String) {
    try {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        context.startActivity(Intent.createChooser(share, "Share image"))
    } catch (e: Exception) {
        Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
