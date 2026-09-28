package com.radustavila.bleexplorer.core.bluetooth.mapper

/**
 * Bluetooth SIG 16-bit UUIDs are transported inside the 128-bit base UUID
 * `0000xxxx-0000-1000-8000-00805F9B34FB`. Showing the short form ("180D") is how the spec and
 * every BLE tool present them.
 */
fun String.shortenBluetoothUuid(): String {
    val uuid = lowercase()
    if (!uuid.endsWith(BASE_UUID_SUFFIX) || uuid.length != FULL_UUID_LENGTH) return uppercase()
    val is16Bit = uuid.startsWith("0000")
    val significant = if (is16Bit) uuid.substring(4, 8) else uuid.substring(0, 8)
    return significant.uppercase()
}

private const val BASE_UUID_SUFFIX = "-0000-1000-8000-00805f9b34fb"
private const val FULL_UUID_LENGTH = 36
