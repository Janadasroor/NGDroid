package com.jnd.ngdroid.engine

import android.os.Build
import android.util.Log

interface NgSpiceCallback {
    fun onLog(msg: String)
    fun onInitData(scaleName: String, title: String, name: String, type: String, vecNames: Array<String>)
    fun onData(vecNames: Array<String>, values: DoubleArray)
    fun onStatus(status: String)
}

object NativeNgSpice {
    private const val TAG = "NativeNgSpice"

    @Volatile
    var isBridgeLoaded: Boolean = false
        private set

    @Volatile
    var lastInitError: String? = null
        private set

    /** ABIs the current device supports (for Oreo bug reports, incl. 32-bit checks). */
    val deviceAbis: List<String> = Build.SUPPORTED_ABIS.toList()

    init {
        try {
            System.loadLibrary("ngspice_jni")
            isBridgeLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            isBridgeLoaded = false
            lastInitError = "ngspice_jni missing for ABIs ${deviceAbis.joinToString()}: ${e.message}"
            Log.e(TAG, "JNI bridge load failed", e)
        }
    }

    /** True when a real ngspice backend initialized (false → built-in engine mode). */
    @Volatile
    var isNativeAvailable: Boolean = false
        private set

    fun markInitResult(success: Boolean, detail: String? = null) {
        isNativeAvailable = success
        if (!success) {
            lastInitError = detail ?: "nativeInit returned false (ABIs ${deviceAbis.joinToString()})"
            Log.w(TAG, lastInitError!!)
        } else {
            lastInitError = null
        }
    }

    external fun nativeInit(callback: NgSpiceCallback): Boolean
    /**
     * Point the process CWD at an app-private dir so libngspice's implicit
     * temp files (new*.plt/.data, cider.log, ...) never land in the project
     * root or sandbox root. Safe to call before [nativeInit].
     */
    external fun nativeSetWorkDir(path: String): Boolean
    external fun nativeRunNetlist(netlist: Array<String>): Boolean
    /** True while ngspice's background thread still runs (bg_run is async). */
    external fun nativeIsRunning(): Boolean
    external fun nativeHalt(): Boolean
    external fun nativeResume(): Boolean
    external fun nativeCommand(cmd: String): Boolean
    external fun nativeGetVectorData(vecName: String): DoubleArray?
}
