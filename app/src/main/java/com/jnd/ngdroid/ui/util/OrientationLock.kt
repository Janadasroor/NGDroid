package com.jnd.ngdroid.ui.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Freezes screen rotation to the current orientation while composed.
 * Call as the first statement inside a dialog so rotation is locked only
 * while the popup is showing; [DisposableEffect] restores free rotation
 * ([ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED]) on dismiss.
 */
@Composable
fun LockOrientationWhileShown() {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        if (activity != null) {
            val landscape = activity.resources.configuration.orientation ==
                Configuration.ORIENTATION_LANDSCAPE
            activity.requestedOrientation = if (landscape) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}
