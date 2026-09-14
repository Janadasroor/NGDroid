package com.jnd.ngdroid.ui.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Legacy storage permission (WRITE_EXTERNAL_STORAGE) is only meaningful on
 * API 26-28: scoped storage needs no permission on 29+, and the manifest
 * caps the declaration with maxSdkVersion="28". The SAF upload picker,
 * FileProvider shares and app-private files never need it on any version —
 * only DownloadManager saves into public Pictures/Downloads do.
 */

/** True on API 26-28 where public-dir saves need a runtime grant. */
fun needsLegacyStoragePermission(): Boolean = Build.VERSION.SDK_INT <= 28

/** True when a public-dir save may proceed (granted, or not needed on 29+). */
fun hasLegacyStorageGrant(context: Context): Boolean =
    !needsLegacyStoragePermission() ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

/**
 * Permission launcher for user-initiated public saves (chat downloads, image
 * save). Runs [onGranted] after the grant; toasts when denied.
 */
@Composable
fun rememberLegacyStorageLauncher(
    onGranted: () -> Unit
): ManagedActivityResultLauncher<String, Boolean> {
    val context = LocalContext.current
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                onGranted()
            } else {
                Toast.makeText(context, "Storage permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    )
}
