package com.jnd.ngdroid.agent

/**
 * Single source of truth for host file locations and default ids.
 * Replaces scattered "Downloads/NGDroid" / "assistant_files" / "spiceagent-01"
 * literals so a rename touches one file.
 */
object HostDefaults {
    /** User-visible download label, e.g. `Downloads/NGDroid/x.pdf`. */
    const val DOWNLOAD_DIR_LABEL = "Downloads/NGDroid"

    /** Subdir under public Downloads / DownloadManager destination. */
    const val DOWNLOAD_SUBDIR = "NGDroid"

    /** MediaStore relative path (API 29+ uses singular `Download/`). */
    const val DOWNLOAD_RELATIVE_PATH = "Download/NGDroid"

    /** App-private file store dir under filesDir. */
    const val FILE_STORE_DIR = "assistant_files"

    /** Free-tier gateway session default. */
    const val DEFAULT_SESSION_ID = "spiceagent-01"

    fun displayPath(fileName: String): String = "$DOWNLOAD_DIR_LABEL/$fileName"

    /** Case-insensitive check for a saved-file mention in model text. */
    fun containsSavedFileMention(lowerText: String): Boolean =
        DOWNLOAD_DIR_LABEL.lowercase() in lowerText || FILE_STORE_DIR in lowerText
}
