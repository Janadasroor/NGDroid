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

package com.jnd.ngdroid.ui.share

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

/**
 * In-app share sheet: lists apps that can handle the share intent and
 * launches the picked one explicitly.
 *
 * Why not the system chooser: on API 26-28 the system sheet pushes
 * Direct Share *contacts* first — on a fresh device that opens the SMS
 * app's empty "Select conversation" picker, a dead end. Querying
 * activities directly yields apps only, identical on every API level.
 */
data class ShareTarget(
    val label: String,
    val icon: Drawable,
    val packageName: String,
    val activityName: String
)

/** Resolve share targets for [mimeType] (apps only, no contact targets). Pure w.r.t. UI. */
fun queryShareTargets(
    context: Context,
    mimeType: String,
    uri: Uri,
    subject: String = ""
): List<ShareTarget> {
    val probe = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_STREAM, uri)
    }
    val pm = context.packageManager
    @Suppress("DEPRECATION")
    val infos: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= 33) {
        pm.queryIntentActivities(
            probe,
            PackageManager.ResolveInfoFlags.of(
                PackageManager.MATCH_DEFAULT_ONLY.toLong()
            )
        )
    } else {
        pm.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
    }
    return infos
        .filter { it.activityInfo != null && it.activityInfo.packageName != context.packageName }
        .mapNotNull { info ->
            val ai = info.activityInfo
            runCatching {
                ShareTarget(
                    label = info.loadLabel(pm).toString(),
                    icon = info.loadIcon(pm),
                    packageName = ai.packageName,
                    activityName = ai.name
                )
            }.getOrNull()
        }
        .sortedBy { it.label.lowercase() }
}

/** Prepared share: file uri + resolved app targets, shown in [ShareSheetDialog]. */
data class ShareOffer(
    val uri: Uri,
    val mimeType: String,
    val subject: String,
    val targets: List<ShareTarget>
)
/** Fire an explicit share to one picked target (direct grant, no chooser). */
fun launchShareTarget(
    context: Context,
    target: ShareTarget,
    uri: Uri,
    mimeType: String,
    subject: String = ""
) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_STREAM, uri)
        component = ComponentName(target.packageName, target.activityName)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
        clipData = android.content.ClipData.newUri(context.contentResolver, "shared-file", uri)
    }
    context.startActivity(intent)
}

@Composable
fun ShareSheetDialog(
    title: String,
    targets: List<ShareTarget>,
    onPick: (ShareTarget) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (targets.isEmpty()) {
                Text(
                    "No installed apps can share this file type.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(targets, key = { it.packageName + it.activityName }) { target ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(target) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val bitmap = remember(target) {
                                runCatching { target.icon.toBitmap(96, 96).asImageBitmap() }.getOrNull()
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    target.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    target.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
