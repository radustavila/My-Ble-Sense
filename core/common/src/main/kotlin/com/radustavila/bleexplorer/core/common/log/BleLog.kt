package com.radustavila.bleexplorer.core.common.log

import android.util.Log

/**
 * Thin logging facade for the BLE stack.
 *
 * Verbose payload logging is compiled out of release builds: advertising payloads and hardware
 * addresses are personally identifiable and must not end up in production logs.
 */
object BleLog {
    private const val TAG = "BleExplorer"

    var isDebug: Boolean = false

    fun d(message: String) {
        if (isDebug) Log.d(TAG, message)
    }

    fun i(message: String) {
        Log.i(TAG, message)
    }

    fun w(message: String, throwable: Throwable? = null) {
        Log.w(TAG, message, throwable)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
    }
}
