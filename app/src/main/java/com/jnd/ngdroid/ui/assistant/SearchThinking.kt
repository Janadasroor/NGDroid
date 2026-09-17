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

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.jnd.ngdroid.ui.theme.LocalButtonShape
import com.jnd.ngdroid.ui.theme.LocalDialogShape
import com.jnd.ngdroid.ui.util.LockOrientationWhileShown
import kotlin.math.abs

private fun colorForHost(host: String): Color {
    val hue = (abs(host.lowercase().hashCode()) % 360).toFloat()
    return Color.hsl(hue, 0.45f, 0.55f)
}

fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        Toast.makeText(context, "Can't open link: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

/** Single round site icon: favicon with initial-letter fallback. Tap opens the host dialog. */
@Composable
fun SiteIcon(
    host: String,
    size: Dp = 22.dp,
    onClick: ((String) -> Unit)? = null
) {
    val clean = host.lowercase().removePrefix("www.")
    val initial = clean.firstOrNull()?.uppercase().orEmpty()
    val clickableMod = if (onClick != null) {
        Modifier.clickable { onClick(clean) }
    } else Modifier
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(colorForHost(clean))
            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
            .then(clickableMod)
    ) {
        SubcomposeAsyncImage(
            model = faviconUrl(clean),
            contentDescription = "Open $clean",
            modifier = Modifier.size(size * 0.62f).clip(CircleShape),
            loading = {
                Text(
                    initial,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            },
            error = {
                Text(
                    initial,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        )
    }
}

/**
 * Overlapping stack of site icons (`+N` overflow). Each icon is tappable and
 * opens that site's page dialog.
 */
@Composable
fun StackedSiteIcons(
    hosts: List<String>,
    modifier: Modifier = Modifier,
    iconSize: Dp = 22.dp,
    onHostClick: ((String) -> Unit)? = null
) {
    val distinct = hosts.map { it.lowercase().removePrefix("www.") }.filter { it.isNotEmpty() }.distinct()
    if (distinct.isEmpty()) return
    val shown = distinct.take(5)
    val extra = distinct.size - shown.size
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((-8).dp)
    ) {
        shown.forEach { SiteIcon(it, iconSize, onClick = onHostClick) }
        if (extra > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(iconSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
            ) {
                Text(
                    "+$extra",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun hostLine(hosts: List<String>, max: Int = 3): String {
    val distinct = hosts.distinct().take(max)
    return distinct.joinToString(" • ")
}

/** Pages from one host: dialog opened by tapping a site icon. */
@Composable
fun HostPagesDialog(
    host: String,
    pages: List<HostPage>,
    onDismiss: () -> Unit,
    onOpenPage: (String) -> Unit = {}
) {
    LockOrientationWhileShown()
    val clean = host.lowercase().removePrefix("www.")
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = LocalDialogShape.current,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SiteIcon(clean, 30.dp)
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text(
                        clean,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${pages.size} page${if (pages.size == 1) "" else "s"} in this chat",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            if (pages.isEmpty()) {
                Text(
                    "No page URLs were captured for this site yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(pages, key = { it.url }) { page ->
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onOpenPage(page.url) }
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Text(
                                page.title.ifBlank { page.url },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                page.url,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, shape = LocalButtonShape.current) { Text("Close") }
        },
        dismissButton = {
            TextButton(
                onClick = { onOpenPage("https://$clean") },
                shape = LocalButtonShape.current
            ) { Text("Open site") }
        }
    )
}

private fun toolIcon(kind: ToolKind?): ImageVector = when (kind) {
    ToolKind.VALIDATE -> Icons.Default.CheckCircle
    ToolKind.APPLY, ToolKind.RUN -> Icons.Default.PlayArrow
    ToolKind.GENERATE -> Icons.Default.Build
    ToolKind.SEARCH -> Icons.Default.Search
    ToolKind.READ -> Icons.AutoMirrored.Filled.MenuBook
    ToolKind.DOWNLOAD -> Icons.Default.Download
    null -> Icons.Default.Info
}

/**
 * One activity-timeline row: icon + friendly title + supporting detail.
 * Failures use the error tone (red icon + title); successes use the primary
 * tone; everything else stays neutral.
 */
@Composable
fun ToolStepRow(text: String) {
    val kind = toolStepKind(text)
    val title = toolStepTitle(text)
    val detail = toolStepDetail(text)
    val error = isErrorStep(text)
    val success = !error && (title == "Netlist valid" || title == "Applied to editor" ||
        title.startsWith("Saved ") || title.startsWith("Found "))
    val iconTint = when {
        error -> MaterialTheme.colorScheme.error
        success -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            if (error) Icons.Default.Error else toolIcon(kind),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(16.dp).padding(top = 1.dp)
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (error || success) FontWeight.SemiBold else FontWeight.Medium,
                color = if (error) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface
            )
            if (detail.isNotBlank()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (error) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Expanded search detail: found/read groups with tappable icons and pages. */
@Composable
fun SearchSummaryBlock(
    summary: SearchSummary,
    onHostClick: (String) -> Unit = {},
    onOpenPage: (String) -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (summary.foundTotal > 0) {
            val hosts = (summary.foundHosts +
                summary.foundSamples.map { it.host }).distinct()
            Row(verticalAlignment = Alignment.CenterVertically) {
                StackedSiteIcons(hosts, iconSize = 20.dp, onHostClick = onHostClick)
                Text(
                    "Found ${summary.foundTotal} page${if (summary.foundTotal == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = if (hosts.isEmpty()) 0.dp else 10.dp)
                )
            }
            if (hosts.isNotEmpty()) {
                Text(
                    hostLine(hosts, 4),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            summary.foundSamples.take(4).forEach { page ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { if (page.url.isNotBlank()) onOpenPage(page.url) }
                        .padding(vertical = 1.dp)
                ) {
                    SiteIcon(page.host, 18.dp, onClick = onHostClick)
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            page.title.ifBlank { page.host },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            page.host,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        val readOk = summary.readOk
        val readFailed = summary.readPages.filter { !it.ok }
        if (readOk.isNotEmpty()) {
            val hosts = readOk.map { it.host }.filter { it.isNotEmpty() }.distinct()
            Row(verticalAlignment = Alignment.CenterVertically) {
                StackedSiteIcons(hosts, iconSize = 20.dp, onHostClick = onHostClick)
                Text(
                    "Read ${readOk.size} page${if (readOk.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = if (hosts.isEmpty()) 0.dp else 10.dp)
                )
            }
            readOk.take(4).forEach { page ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { if (page.url.isNotBlank()) onOpenPage(page.url) }
                        .padding(vertical = 1.dp)
                ) {
                    if (page.host.isNotEmpty()) SiteIcon(page.host, 18.dp, onClick = onHostClick)
                    Text(
                        page.url.ifBlank { page.host },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
        if (readFailed.isNotEmpty()) {
            val hosts = readFailed.map { it.host }.filter { it.isNotEmpty() }.distinct()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    "Couldn't read ${readFailed.size} page${if (readFailed.size == 1) "" else "s"}" +
                        if (hosts.isNotEmpty()) " • ${hosts.take(3).joinToString(" • ")}" else "",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
        if (summary.imagesTotal > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StackedSiteIcons(summary.imageHosts, iconSize = 20.dp, onHostClick = onHostClick)
                Text(
                    "${summary.imagesTotal} image${if (summary.imagesTotal == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = if (summary.imageHosts.isEmpty()) 0.dp else 10.dp)
                )
            }
        }
    }
}
