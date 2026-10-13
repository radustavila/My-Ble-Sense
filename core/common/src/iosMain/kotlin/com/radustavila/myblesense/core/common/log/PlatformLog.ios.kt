package com.radustavila.myblesense.core.common.log

import platform.Foundation.NSLog

internal actual fun platformLog(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
    val suffix = throwable?.let { " (${it::class.simpleName}: ${it.message})" }.orEmpty()
    // NSLog takes a format string, and Kotlin strings cannot be passed as its `%@` arguments:
    // '%' is escaped instead, since payloads may contain it.
    NSLog("[$tag] ${level.name.first()} $message$suffix".replace("%", "%%"))
}
