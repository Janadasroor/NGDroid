package com.jnd.ngdroid.ui.assistant

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File

data class ChatFileLink(val fileName: String, val url: String)

private val downloadExtensions = setOf(
    "pdf", "zip", "csv", "txt", "md", "json", "xml",
    "doc", "docx", "xls", "xlsx", "ppt", "pptx",
    "rar", "7z", "mp4", "lib", "mod", "cir", "net"
)

private fun cleanFileUrl(raw: String): String? {
    var url = raw.trim().trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '\'', '"')
    if (!url.startsWith("http://") && !url.startsWith("https://")) return null
    val ext = url.substringBefore('?').substringBefore('#').substringAfterLast('.', "")
        .lowercase()
    if (ext !in downloadExtensions) return null
    return url
}

private fun fileNameOf(url: String): String {
    val last = url.substringAfterLast('/').substringBefore('?').substringBefore('#').trim()
    if (last.isNotBlank() && last.length <= 80 &&
        last.all { it.isLetterOrDigit() || it in ".-_()+ " }
    ) {
        return last
    }
    val ext = url.substringBefore('?').substringAfterLast('.', "bin").lowercase()
        .takeIf { it.length in 2..5 && it.all { c -> c.isLetterOrDigit() } } ?: "bin"
    return "ngdroid-file.$ext"
}

/**
 * Collect downloadable doc/archive/data links: markdown `[label](url)` first,
 * then bare http(s) URLs. Deduped, order-preserving, capped. Pure.
 */
fun extractFileLinks(text: String, maxFiles: Int = 4): List<ChatFileLink> {
    if (maxFiles <= 0) return emptyList()
    val out = mutableListOf<ChatFileLink>()
    // Markdown links (any label): [datasheet](https://.../x.pdf).
    val mdRe = Regex("""\[[^\]]*]\(([^)\s]+)(?:\s+["'][^"']*["'])?\)""")
    for (m in mdRe.findAll(text)) {
        val url = cleanFileUrl(m.groupValues[1]) ?: continue
        if (out.none { it.url == url }) {
            out.add(ChatFileLink(fileNameOf(url), url))
            if (out.size >= maxFiles) return out
        }
    }
    val bareRe = Regex("""https?://[^\s)<>\]"'"]+""")
    for (m in bareRe.findAll(text)) {
        val url = cleanFileUrl(m.value) ?: continue
        if (out.none { it.url == url }) {
            out.add(ChatFileLink(fileNameOf(url), url))
            if (out.size >= maxFiles) break
        }
    }
    return out
}

/**
 * Local files the agent saved: `Downloads/NGDroid/<name>` mentions.
 * Pure; resolved against app files by the card.
 */
fun extractLocalFileNames(text: String, maxFiles: Int = 4): List<String> {
    if (maxFiles <= 0) return emptyList()
    val re = Regex("""Downloads/NGDroid/([A-Za-z0-9._()+ \-]{1,80})""")
    val out = mutableListOf<String>()
    for (m in re.findAll(text)) {
        val name = m.groupValues[1].trim().trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '\'', '"')
        if (name.isNotBlank() && name !in out) {
            out.add(name)
            if (out.size >= maxFiles) break
        }
    }
    return out
}

private fun hostOf(url: String): String = runCatching {
    Uri.parse(url).host.orEmpty()
}.getOrDefault("")

private fun enqueueDownload(context: Context, url: String, fileName: String) {
    try {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setDescription("NGDroid download")
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "NGDroid/$fileName"
            )
            .addRequestHeader(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
            )
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        Toast.makeText(context, "Downloading to Downloads/NGDroid…", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun localFile(context: Context, name: String): File =
    File(File(context.filesDir, "assistant_files"), name)

private fun openLocalFile(context: Context, name: String) {
    try {
        val file = localFile(context, name)
        if (!file.exists()) {
            Toast.makeText(context, "File not on this device yet", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val mime = context.contentResolver.getType(uri) ?: "*/*"
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(view, "Open $name"))
    } catch (e: Exception) {
        Toast.makeText(context, "Can't open file: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

/** Download card for one remote doc/archive link. */
@Composable
fun FileDownloadCard(link: ChatFileLink) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // API 26-28: DownloadManager into public Downloads needs the legacy
    // runtime grant; queue the tap and run it after the user grants.
    var pendingDownload by androidx.compose.runtime.remember(link.url) {
        androidx.compose.runtime.mutableStateOf(false)
    }
    val storageLauncher = rememberLegacyStorageLauncher {
        if (pendingDownload) {
            pendingDownload = false
            enqueueDownload(context, link.url, link.fileName)
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    link.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    hostOf(link.url),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = {
                if (hasLegacyStorageGrant(context)) {
                    enqueueDownload(context, link.url, link.fileName)
                } else {
                    pendingDownload = true
                    storageLauncher.launch(
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                }
            }) {
                Icon(Icons.Default.Download, contentDescription = "Download ${link.fileName}")
            }
        }
    }
}

/** Card for an agent-saved local file with open + share actions. */
@Composable
fun LocalFileCard(fileName: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(
                Icons.Default.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Downloads/NGDroid/$fileName",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { openLocalFile(context, fileName) }) {
                Icon(Icons.Default.FolderOpen, contentDescription = "Open $fileName")
            }
            IconButton(
                onClick = {
                    try {
                        val file = localFile(context, fileName)
                        if (!file.exists()) {
                            Toast.makeText(context, "File not on this device yet", Toast.LENGTH_SHORT).show()
                            return@IconButton
                        }
                        val uri = FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", file
                        )
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = context.contentResolver.getType(uri) ?: "*/*"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(share, "Share $fileName"))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            ) {
                Icon(Icons.Default.Description, contentDescription = "Share $fileName")
            }
        }
    }
}

/** Stack of file cards shown under a finished assistant message. */
@Composable
fun ChatFileCards(
    remote: List<ChatFileLink>,
    local: List<String>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        remote.forEach { FileDownloadCard(it) }
        local.forEach { LocalFileCard(it) }
    }
}
