package com.radustavila.myblesense.core.common.log

/**
 * Thin logging facade for the BLE stack (Logcat on Android, `NSLog` on iOS).
 *
 * Verbose payload logging is compiled out of release builds: advertising payloads and hardware
 * addresses are personally identifiable and must not end up in production logs.
 */
object BleLog {
    private const val TAG = "MyBleSense"

    var isDebug: Boolean = false

    fun d(message: String) {
        if (isDebug) platformLog(LogLevel.DEBUG, TAG, message, null)
    }

    fun i(message: String) {
        platformLog(LogLevel.INFO, TAG, message, null)
    }

    fun w(message: String, throwable: Throwable? = null) {
        platformLog(LogLevel.WARN, TAG, message, throwable)
    }

    fun e(message: String, throwable: Throwable? = null) {
        platformLog(LogLevel.ERROR, TAG, message, throwable)
    }
}

internal enum class LogLevel { DEBUG, INFO, WARN, ERROR }

internal expect fun platformLog(level: LogLevel, tag: String, message: String, throwable: Throwable?)
