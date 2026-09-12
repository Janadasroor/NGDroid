package com.jnd.ngdroid.engine

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Helper for XSPICE mixed-signal and event-driven digital code model support in NGDroid.
 *
 * XSPICE extends SPICE3/ngspice with code models (A-devices) for event-driven digital gates,
 * analog code models (gain, summer, limit, hysteresis, etc.), and ADC/DAC interface bridges.
 */
object XSpiceManager {
    private const val TAG = "XSpiceManager"

    /**
     * Scans netlist text to determine whether it utilizes XSPICE code model features.
     * XSPICE devices start with 'A' or 'a' (e.g. A1 [1 2] [3 4] d_and) or specify XSPICE .model definitions.
     */
    fun containsXSpiceDevices(netlist: String): Boolean {
        if (netlist.isBlank()) return false
        val lines = netlist.lines().map { it.trim() }
        for (line in lines) {
            if (line.startsWith("*") || line.startsWith(";")) continue
            if (line.startsWith("A", ignoreCase = true) || line.startsWith("a", ignoreCase = true)) {
                return true
            }
            if (line.startsWith(".model", ignoreCase = true)) {
                val lower = line.lowercase()
                if (lower.contains("d_and") || lower.contains("d_or") || lower.contains("d_nand") ||
                    lower.contains("d_nor") || lower.contains("d_xor") || lower.contains("d_xnor") ||
                    lower.contains("d_invert") || lower.contains("d_buffer") || lower.contains("d_adc") ||
                    lower.contains("d_dac") || lower.contains("d_dff") || lower.contains("d_jkff") ||
                    lower.contains("gain") || lower.contains("limit") || lower.contains("hysteresis")
                ) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Loads dynamic .cm (Code Model) shared objects from assets or app cache directory into ngspice.
     * Call this after [NativeNgSpice.nativeInit].
     */
    fun loadCodeModelsFromAssets(context: Context): Int {
        if (!NativeNgSpice.isNativeAvailable) return 0
        var loaded = 0
        try {
            val cmDir = File(context.codeCacheDir, "codemodels")
            if (!cmDir.exists()) cmDir.mkdirs()

            val assetManager = context.assets
            val assetFiles = assetManager.list("codemodels") ?: emptyArray()

            for (fileName in assetFiles) {
                if (fileName.endsWith(".cm") || fileName.endsWith(".so")) {
                    val targetFile = File(cmDir, fileName)
                    if (!targetFile.exists()) {
                        assetManager.open("codemodels/$fileName").use { input ->
                            targetFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    val cmd = "codemodel ${targetFile.absolutePath}"
                    val ok = NativeNgSpice.nativeCommand(cmd)
                    if (ok) {
                        loaded++
                        Log.i(TAG, "Loaded XSPICE code model: ${targetFile.name}")
                    } else {
                        Log.w(TAG, "Failed to load XSPICE code model: ${targetFile.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading XSPICE code models", e)
        }
        return loaded
    }
}
